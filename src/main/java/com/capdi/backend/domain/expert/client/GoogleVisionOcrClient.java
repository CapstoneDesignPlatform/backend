package com.capdi.backend.domain.expert.client;

import com.capdi.backend.domain.expert.entity.MimeTypeEnum;
import com.capdi.backend.global.exception.*;
import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class GoogleVisionOcrClient {
    private final VisionClientFactory factory;
    private static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final long TOTAL_NANOS = java.time.Duration.ofSeconds(60).toNanos();
    private static final Feature FEATURE = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();

    public String requestOcr(String filePath, MimeTypeEnum mimeType) {
        long started = System.nanoTime();
        try {
            Path path = Path.of(filePath);
            if (Files.size(path) > MAX_BYTES) throw new IOException("OCR input exceeds limit");
            ByteString content;
            try (var input = Files.newInputStream(path)) { content = ByteString.copyFrom(input.readNBytes((int) MAX_BYTES + 1)); }
            if (content.isEmpty() || content.size() > MAX_BYTES) throw new IOException("Invalid OCR input");
            int pages = mimeType == MimeTypeEnum.PDF ? pdfPages(content.toByteArray()) : 0;
            try (ImageAnnotatorClient client = factory.create()) {
                if (mimeType != MimeTypeEnum.PDF) {
                    var request = AnnotateImageRequest.newBuilder()
                            .setImage(Image.newBuilder().setContent(content)).addFeatures(FEATURE).build();
                    var response = client.batchAnnotateImagesCallable().call(
                            BatchAnnotateImagesRequest.newBuilder().addRequests(request).build(), context(started));
                    context(started);
                    if (response.getResponsesCount() != 1) fail();
                    if (response.getResponses(0).hasError()) fail(response.getResponses(0).getError());
                    return response.getResponses(0).getFullTextAnnotation().getText();
                }
                List<String> text = new ArrayList<>();
                for (int first = 1; first <= pages; first += 5) {
                    List<Integer> requested = new ArrayList<>();
                    for (int page = first; page <= Math.min(pages, first + 4); page++) requested.add(page);
                    var request = AnnotateFileRequest.newBuilder()
                            .setInputConfig(InputConfig.newBuilder().setMimeType("application/pdf").setContent(content))
                            .addFeatures(FEATURE).addAllPages(requested).build();
                    var response = client.batchAnnotateFilesCallable().call(
                            BatchAnnotateFilesRequest.newBuilder().addRequests(request).build(), context(started));
                    context(started);
                    if (response.getResponsesCount() != 1) fail();
                    var file = response.getResponses(0);
                    if (file.hasError()) fail(file.getError());
                    if (file.getTotalPages() != pages || file.getResponsesCount() != requested.size()) fail();
                    Map<Integer, String> byPage = new HashMap<>();
                    for (var page : file.getResponsesList()) {
                        int number = page.getContext().getPageNumber();
                        if (page.hasError()) fail(page.getError());
                        if (!requested.contains(number)
                                || byPage.putIfAbsent(number, page.getFullTextAnnotation().getText()) != null) fail();
                    }
                    for (int page : requested) {
                        if (!byPage.containsKey(page)) fail();
                        text.add(byPage.get(page));
                    }
                }
                return String.join("\n\n", text);
            }
        } catch (DeadlineExceededException e) {
            throw new CustomException(ErrorCode.OCR_TIMEOUT);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            throw new CustomException(ErrorCode.OCR_FAILED);
        }
    }

    private com.google.api.gax.grpc.GrpcCallContext context(long started) {
        long remaining = TOTAL_NANOS - (System.nanoTime() - started);
        if (remaining <= 0) throw new CustomException(ErrorCode.OCR_TIMEOUT);
        return com.google.api.gax.grpc.GrpcCallContext.createDefault().withTimeoutDuration(
                java.time.Duration.ofNanos(Math.min(remaining, java.time.Duration.ofSeconds(15).toNanos())));
    }

    private int pdfPages(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            int count = document.getNumberOfPages();
            if (document.isEncrypted() || count < 1 || count > 50) throw new IOException("Invalid OCR PDF");
            return count;
        }
    }
    private void fail() { throw new CustomException(ErrorCode.OCR_FAILED); }

    private void fail(com.google.rpc.Status error) {
        // Only the stable code is interpreted; provider text may contain sensitive data.
        throw new CustomException(error.getCode() == com.google.rpc.Code.DEADLINE_EXCEEDED_VALUE
                ? ErrorCode.OCR_TIMEOUT : ErrorCode.OCR_FAILED);
    }
}
