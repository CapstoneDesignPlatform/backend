package com.capdi.backend.domain.expert.entity;

import java.util.Locale;

public enum MimeTypeEnum {
    PDF,
    JPG,
    PNG;

    public static MimeTypeEnum from(String contentType, String originalName) {
        if (contentType != null) {
            return switch (contentType.toLowerCase(Locale.ROOT)) {
                case "application/pdf" -> PDF;
                case "image/jpeg", "image/jpg" -> JPG;
                case "image/png" -> PNG;
                default -> fromFileName(originalName);
            };
        }

        return fromFileName(originalName);
    }

    private static MimeTypeEnum fromFileName(String originalName) {
        if (originalName == null) {
            throw new IllegalArgumentException("Unsupported mime type");
        }

        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (lowerName.endsWith(".pdf")) {
            return PDF;
        }
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) {
            return JPG;
        }
        if (lowerName.endsWith(".png")) {
            return PNG;
        }

        throw new IllegalArgumentException("Unsupported mime type");
    }
}
