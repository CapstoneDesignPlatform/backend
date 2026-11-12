package com.capdi.backend.domain.expert.client;

import com.capdi.backend.domain.expert.entity.MimeTypeEnum;
import com.capdi.backend.global.exception.*;
import com.google.api.gax.rpc.*;
import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import com.google.rpc.Status;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class GoogleVisionOcrClientTest {
    @TempDir Path root;
    VisionClientFactory factory;
    ImageAnnotatorClient sdk;
    UnaryCallable<BatchAnnotateFilesRequest, BatchAnnotateFilesResponse> files;
    UnaryCallable<BatchAnnotateImagesRequest, BatchAnnotateImagesResponse> images;
    GoogleVisionOcrClient client;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() throws Exception {
        factory = mock(VisionClientFactory.class);
        sdk = mock(ImageAnnotatorClient.class);
        files = mock(UnaryCallable.class);
        images = mock(UnaryCallable.class);
        when(factory.create()).thenReturn(sdk);
        when(sdk.batchAnnotateFilesCallable()).thenReturn(files);
        when(sdk.batchAnnotateImagesCallable()).thenReturn(images);
        client = new GoogleVisionOcrClient(factory);
    }

    Path pdf(int pages) throws Exception {
        Path path = root.resolve("sample.pdf");
        try (var document = new PDDocument()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            document.save(path.toFile());
        }
        return path;
    }
    AnnotateImageResponse page(int page, String text) {
        return AnnotateImageResponse.newBuilder().setContext(ImageAnnotationContext.newBuilder().setPageNumber(page))
                .setFullTextAnnotation(TextAnnotation.newBuilder().setText(text)).build();
    }
    BatchAnnotateFilesResponse response(int total, List<AnnotateImageResponse> pages) {
        return BatchAnnotateFilesResponse.newBuilder().addResponses(AnnotateFileResponse.newBuilder()
                .setTotalPages(total).addAllResponses(pages)).build();
    }
    @ParameterizedTest @ValueSource(ints = {1, 5, 6, 50})
    void pdfUsesExplicitBatchesAndMergesEveryPageInOrder(int count) throws Exception {
        List<Integer> requested = new ArrayList<>();
        Path path = pdf(count);
        when(files.call(any(), any())).thenAnswer(invocation -> {
            BatchAnnotateFilesRequest batch = invocation.getArgument(0);
            assertThat(batch.getRequestsCount()).isEqualTo(1);
            var request = batch.getRequests(0);
            assertThat(request.getInputConfig().getMimeType()).isEqualTo("application/pdf");
            assertThat(request.getInputConfig().getContent()).isEqualTo(ByteString.copyFrom(Files.readAllBytes(path)));
            assertThat(request.getPagesCount()).isBetween(1, 5);
            requested.addAll(request.getPagesList());
            List<AnnotateImageResponse> results = new ArrayList<>();
            for (int number : request.getPagesList()) results.add(page(number, "page-" + number));
            Collections.reverse(results); // Provider response order is not trusted.
            return response(count, results);
        });
        List<String> expected = new ArrayList<>();
        for (int i = 1; i <= count; i++) expected.add("page-" + i);
        assertThat(client.requestOcr(path.toString(), MimeTypeEnum.PDF)).isEqualTo(String.join("\n\n", expected));
        assertThat(requested).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, count).boxed().toList());
        verify(files, times((count + 4) / 5)).call(any(), any());
        verify(sdk).close();
        verifyNoInteractions(images);
    }
    @ParameterizedTest @ValueSource(strings = {"JPG", "PNG"})
    void imagesKeepImageApi(String type) throws Exception {
        Path path = root.resolve("image"); Files.write(path, new byte[]{1,2,3});
        when(images.call(any(), any())).thenReturn(BatchAnnotateImagesResponse.newBuilder()
                .addResponses(page(0, "image text")).build());
        assertThat(client.requestOcr(path.toString(), MimeTypeEnum.valueOf(type))).isEqualTo("image text");
        verifyNoInteractions(files); verify(sdk).close();
    }
    @ParameterizedTest @ValueSource(strings = {"missing", "duplicate", "foreign", "page-error", "file-error", "wrong-total"})
    void rejectsIncompleteOrInvalidPdfResponses(String problem) throws Exception {
        var result = switch (problem) {
            case "missing" -> response(2, List.of(page(1,"a")));
            case "duplicate" -> response(2, List.of(page(1,"a"),page(1,"b")));
            case "foreign" -> response(2, List.of(page(1,"a"),page(3,"b")));
            case "page-error" -> response(2, List.of(page(1,"a"), AnnotateImageResponse.newBuilder()
                    .setError(Status.newBuilder().setCode(13)).build()));
            case "file-error" -> BatchAnnotateFilesResponse.newBuilder().addResponses(AnnotateFileResponse.newBuilder()
                    .setError(Status.newBuilder().setCode(13))).build();
            default -> response(3, List.of(page(1,"a"),page(2,"b")));
        };
        when(files.call(any(),any())).thenReturn(result);
        assertThatThrownBy(() -> client.requestOcr(pdf(2).toString(), MimeTypeEnum.PDF))
                .isInstanceOfSatisfying(CustomException.class, e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OCR_FAILED));
        verify(sdk).close();
    }
    @Test void timeoutIsDistinctAndClosesClient() throws Exception {
        when(files.call(any(),any())).thenThrow(new DeadlineExceededException("test", null,
                com.google.api.gax.grpc.GrpcStatusCode.of(io.grpc.Status.Code.DEADLINE_EXCEEDED), false));
        Path path = pdf(1);
        assertThatThrownBy(() -> client.requestOcr(path.toString(), MimeTypeEnum.PDF))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OCR_TIMEOUT));
        verify(sdk).close();
    }
    @Test void emptyTextIsReturnedForServiceClassification() throws Exception {
        when(files.call(any(),any())).thenReturn(response(1,List.of(page(1,""))));
        assertThat(client.requestOcr(pdf(1).toString(),MimeTypeEnum.PDF)).isEmpty();
    }
    @Test void refusesPdfAboveFiftyPagesBeforeProvider() throws Exception {
        assertThatThrownBy(() -> client.requestOcr(pdf(51).toString(),MimeTypeEnum.PDF)).isInstanceOf(CustomException.class);
        verifyNoInteractions(factory);
    }

    @Test void laterBatchFailureNeverReturnsPartialText() throws Exception {
        when(files.call(any(),any())).thenReturn(response(6,List.of(page(1,"a"),page(2,"b"),page(3,"c"),page(4,"d"),page(5,"e"))))
                .thenThrow(new RuntimeException("provider failure"));
        assertThatThrownBy(() -> client.requestOcr(pdf(6).toString(),MimeTypeEnum.PDF))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OCR_FAILED));
        verify(sdk).close();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"IMAGE,4", "IMAGE,13", "FILE,4", "FILE,13", "PAGE,4", "PAGE,13"})
    void classifiesInBandErrorWithoutExposingProviderMessage(String level, int code) throws Exception {
        Status error = Status.newBuilder().setCode(code).setMessage("sensitive-provider-detail").build();
        Path path;
        MimeTypeEnum mime;
        if (level.equals("IMAGE")) {
            path = root.resolve("image.png"); Files.write(path,new byte[]{1,2,3}); mime = MimeTypeEnum.PNG;
            when(images.call(any(),any())).thenReturn(BatchAnnotateImagesResponse.newBuilder()
                    .addResponses(AnnotateImageResponse.newBuilder().setError(error)).build());
        } else {
            path = pdf(1); mime = MimeTypeEnum.PDF;
            when(files.call(any(),any())).thenReturn(level.equals("FILE")
                    ? BatchAnnotateFilesResponse.newBuilder().addResponses(AnnotateFileResponse.newBuilder().setError(error)).build()
                    : response(1,List.of(AnnotateImageResponse.newBuilder().setError(error).build())));
        }
        assertThatThrownBy(() -> client.requestOcr(path.toString(),mime))
                .isInstanceOfSatisfying(CustomException.class,e -> {
                    assertThat(e.getErrorCode()).isEqualTo(code == 4 ? ErrorCode.OCR_TIMEOUT : ErrorCode.OCR_FAILED);
                    assertThat(e.getMessage()).doesNotContain("sensitive-provider-detail");
                });
        verify(sdk).close();
    }
}
