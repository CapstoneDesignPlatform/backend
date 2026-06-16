package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ExpertVerificationStatusResponse {

    @JsonProperty("verification_request")
    private VerificationRequestDto verificationRequest;

    public static ExpertVerificationStatusResponse from(
            ExpertProfile expertProfile,
            List<ExpertCertificate> certificates,
            BusinessRegistrationInfo businessRegistrationInfo
    ) {
        boolean applied = expertProfile.getVerificationStatus() != VerificationStatusEnum.NOT_SUBMITTED;

        return ExpertVerificationStatusResponse.builder()
                .verificationRequest(VerificationRequestDto.builder()
                        .id(applied ? expertProfile.getId() : null)
                        .status(expertProfile.getIsVerified())
                        .verificationStatus(toApiVerificationStatus(expertProfile.getVerificationStatus()))
                        .specialty(hasText(expertProfile.getSpecialty()) ? expertProfile.getSpecialty() : null)
                        .companyName(hasText(expertProfile.getBusinessName()) ? expertProfile.getBusinessName() : null)
                        .certificates(certificates.stream()
                                .map(ExpertCertificateResponse::from)
                                .toList())
                        .businessRegistrationInfo(businessRegistrationInfo != null
                                ? BusinessRegistrationInfoResponse.from(businessRegistrationInfo)
                                : null)
                        .submittedAt(applied ? expertProfile.getUpdatedAt() : null)
                        .reviewedAt(expertProfile.getReviewedAt())
                        .rejectedReason(expertProfile.getRejectedReason())
                        .build())
                .build();
    }

    private static String toApiVerificationStatus(VerificationStatusEnum verificationStatus) {
        return verificationStatus == VerificationStatusEnum.NOT_SUBMITTED
                ? "NOT_APPLIED"
                : verificationStatus.name();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @G```etter
    @Builder
    public static class VerificationRequestDto {

        private Long id;

        private Boolean status;

        @JsonProperty("verification_status")
        private String verificationStatus;

        private String specialty;

        @JsonProperty("company_name")
        private String companyName;

        private List<ExpertCertificateResponse> certificates;

        @JsonProperty("business_registration_info")
        private BusinessRegistrationInfoResponse businessRegistrationInfo;

        @JsonProperty("submitted_at")
        private LocalDateTime submittedAt;

        @JsonProperty("reviewed_at")
        private LocalDateTime reviewedAt;

        @JsonProperty("rejected_reason")
        private String rejectedReason;
    }
}
