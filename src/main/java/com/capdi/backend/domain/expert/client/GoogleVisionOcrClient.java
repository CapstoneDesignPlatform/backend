package com.capdi.backend.domain.expert.client;

import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@Slf4j
public class GoogleVisionOcrClient {

    public String requestOcr(String filePath) {
        try {
            ByteString fileBytes = ByteString.readFrom(
                    Files.newInputStream(Path.of(filePath))
            );

            Image image = Image.newBuilder()
                    .setContent(fileBytes)
                    .build();

            Feature feature = Feature.newBuilder()
                    .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
                    .build();

            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .setImage(image)
                    .addFeatures(feature)
                    .build();

            try (ImageAnnotatorClient client = ImageAnnotatorClient.create()) {
                BatchAnnotateImagesResponse response =
                        client.batchAnnotateImages(List.of(request));

                AnnotateImageResponse imageResponse = response.getResponses(0);

                if (imageResponse.hasError()) {
                    log.error("Google Vision OCR 오류: {}",
                            imageResponse.getError().getMessage());
                    throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
                }

                return imageResponse.getFullTextAnnotation().getText();
            }

        } catch (Exception e) {
            log.error("Google Vision OCR failed. filePath={}, errorType={}, message={}",
                    filePath,
                    e.getClass().getName(),
                    e.getMessage(),
                    e
            );
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}