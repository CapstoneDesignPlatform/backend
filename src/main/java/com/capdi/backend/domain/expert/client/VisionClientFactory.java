package com.capdi.backend.domain.expert.client;

import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.ImageAnnotatorSettings;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class VisionClientFactory {
    public ImageAnnotatorClient create() throws IOException {
        // Lazy credentials: context startup does not contact Vision or require an ADC key.
        var settings = ImageAnnotatorSettings.newBuilder();
        settings.batchAnnotateImagesSettings().setRetryableCodes(java.util.Set.of());
        settings.batchAnnotateFilesSettings().setRetryableCodes(java.util.Set.of());
        return ImageAnnotatorClient.create(settings.build());
    }
}
