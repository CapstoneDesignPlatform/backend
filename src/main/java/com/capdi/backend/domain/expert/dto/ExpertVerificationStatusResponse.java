package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ExpertVerificationStatusResponse {

    @JsonProperty("verification_request")
    private VerificationRequestDto verificationRequest;

    public static ExpertVerificationStatusResponse from(ExpertProfile expertProfile) {
        boolean verified = expertProfile.getIsVerified();
        boolean applied = expertProfile.getSpecialty() != null && !expertProfile.getSpecialty().isBlank();

        String status;
        if (verified) {
            status = "APPROVED";
        } else if (applied) {
            status = "PENDING";
        } else {
            status = "NOT_APPLIED";
        }

        return ExpertVerificationStatusResponse.builder()
                .verificationRequest(VerificationRequestDto.builder()
                        .id(applied ? expertProfile.getId() : null)
                        .status(status)
                        .licenseType(applied ? expertProfile.getSpecialty() : null)
                        .licenseNumber(null)
                        .issueDate(null)
                        .companyName(applied ? expertProfile.getBusinessName() : null)
                        .portfolio(applied ? expertProfile.getPortfolioDescription() : null)
                        .certificates(List.of())
                        .businessLicense(null)
                        .submittedAt(applied ? expertProfile.getUpdatedAt() : null)
                        .reviewedAt(verified ? expertProfile.getVerifiedAt() : null)
                        .rejectedReason(null)
                        .build())
                .build();
    }

    @Getter
    @Builder
    public static class VerificationRequestDto {

        private Long id;

        private String status;

        @JsonProperty("license_type")
        private String licenseType;

        @JsonProperty("license_number")
        private String licenseNumber;

        @JsonProperty("issue_date")
        private LocalDate issueDate;

        @JsonProperty("company_name")
        private String companyName;

        private String portfolio;

        private List<Object> certificates;

        @JsonProperty("business_license")
        private Object businessLicense;

        @JsonProperty("submitted_at")
        private LocalDateTime submittedAt;

        @JsonProperty("reviewed_at")
        private LocalDateTime reviewedAt;

        @JsonProperty("rejected_reason")
        private String rejectedReason;
    }
}