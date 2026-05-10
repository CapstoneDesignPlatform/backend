package com.capdi.backend.domain.expert.entity;

import java.util.Arrays;

public enum FileTypeEnum {
    CERTIFICATE,
    BUSINESS_REGISTRATION;

    public static FileTypeEnum from(String value) {
        String normalizedValue = value == null ? null : value.trim();

        return Arrays.stream(values())
                .filter(fileType -> fileType.name().equals(normalizedValue))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported file type"));
    }
}
