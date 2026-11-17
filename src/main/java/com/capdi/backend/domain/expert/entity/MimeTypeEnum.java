package com.capdi.backend.domain.expert.entity;

import java.util.Locale;

public enum MimeTypeEnum {

    JPG("image/jpeg"),
    PNG("image/png"),
    PDF("application/pdf");

    private final String mimeType;

    MimeTypeEnum(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public static MimeTypeEnum from(String contentType, String originalName) {

        if (originalName == null) {
            throw new IllegalArgumentException("파일 형식을 확인할 수 없습니다.");
        }

        MimeTypeEnum extensionMime = fromFileName(originalName);

        if (contentType == null || contentType.isBlank()) {
            return extensionMime;
        }

        String normalizedContentType =
                contentType.toLowerCase(Locale.ROOT);

        return switch (normalizedContentType) {

            case "application/pdf" -> {
                if (extensionMime != PDF) {
                    throw new IllegalArgumentException(
                            "파일 확장자와 Content-Type이 일치하지 않습니다."
                    );
                }
                yield PDF;
            }

            case "image/jpeg", "image/jpg" -> {
                if (extensionMime != JPG) {
                    throw new IllegalArgumentException(
                            "파일 확장자와 Content-Type이 일치하지 않습니다."
                    );
                }
                yield JPG;
            }

            case "image/png" -> {
                if (extensionMime != PNG) {
                    throw new IllegalArgumentException(
                            "파일 확장자와 Content-Type이 일치하지 않습니다."
                    );
                }
                yield PNG;
            }

            case "application/octet-stream" -> extensionMime;

            default -> throw new IllegalArgumentException(
                    "지원하지 않는 Content-Type입니다."
            );
        };
    }

    private static MimeTypeEnum fromFileName(String originalName) {

        String lower = originalName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".pdf")) {
            return PDF;
        }

        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return JPG;
        }

        if (lower.endsWith(".png")) {
            return PNG;
        }

        throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
    }
}