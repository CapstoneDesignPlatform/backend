package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ExpertCertificateResponse {

    private Long id;

    @JsonProperty("file_id")
    private Long fileId;

    @JsonProperty("certificate_name")
    private String certificateName;

    @JsonProperty("certificate_type_code")
    private String certificateTypeCode;

    @JsonProperty("certificate_number")
    private String certificateNumber;

    @JsonProperty("issue_date")
    private LocalDate issueDate;

    @JsonProperty("expired_at")
    private LocalDate expiredAt;

    @JsonProperty("owner_name")
    private String ownerName;

    public static ExpertCertificateResponse from(ExpertCertificate expertCertificate) {
        return ExpertCertificateResponse.builder()
                .id(expertCertificate.getId())
                .fileId(expertCertificate.getFile().getId())
                .certificateName(expertCertificate.getCertificateName())
                .certificateTypeCode(expertCertificate.getCertificateTypeCode().name())
                .certificateNumber(expertCertificate.getCertificateNumber())
                .issueDate(expertCertificate.getIssueDate())
                .expiredAt(expertCertificate.getExpiredAt())
                .ownerName(expertCertificate.getOwnerName())
                .build();
    }
}
