package com.capdi.backend.domain.expert.entity;

import java.time.LocalDate;
import java.util.Arrays;

public enum CertificateTypeCodeEnum {
    CERTIFIED_PUBLIC_ACCOUNTANT("\uacf5\uc778\ud68c\uacc4\uc0ac"),
    TAX_ACCOUNTANT("\uc138\ubb34\uc0ac"),
    MANAGEMENT_CONSULTANT("\uacbd\uc601\uc9c0\ub3c4\uc0ac");

    private final String label;

    CertificateTypeCodeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public LocalDate calculateExpiredAt(LocalDate issueDate) {
        if (this == MANAGEMENT_CONSULTANT) {
            return issueDate.plusYears(5);
        }

        return null;
    }

    public static CertificateTypeCodeEnum fromLabel(String label) {
        String normalizedLabel = label == null ? null : label.trim();

        return Arrays.stream(values())
                .filter(certificateTypeCode -> certificateTypeCode.label.equals(normalizedLabel))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported certificate name"));
    }
}
