package com.capdi.backend.domain.expert.util;

import org.springframework.stereotype.Component;

@Component
public class FileVerificationUtil {

    public boolean verifyBusinessRegistration(
            String ocrText,
            String businessNumber,
            String representativeName,
            String companyName
    ) {
        if (ocrText == null || ocrText.isBlank()) {
            return false;
        }

        String normalizedText = normalize(ocrText);
        String normalizedNumberText = normalizeBusinessNumber(ocrText);

        boolean hasBusinessNumber =
                normalizedNumberText.contains(normalizeBusinessNumber(businessNumber));

        boolean hasRepresentativeName =
                normalizedText.contains(normalize(representativeName));

        boolean hasCompanyName =
                normalizedText.contains(normalize(companyName));

        return hasBusinessNumber && hasRepresentativeName && hasCompanyName;
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text.replaceAll("\\s+", "")
                .replaceAll("[()]", "")
                .trim()
                .toLowerCase();
    }

    private String normalizeBusinessNumber(String text) {
        if (text == null) {
            return "";
        }

        return text.replaceAll("[^0-9]", "");
    }
}