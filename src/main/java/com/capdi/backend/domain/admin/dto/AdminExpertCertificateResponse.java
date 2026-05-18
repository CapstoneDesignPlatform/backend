package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 전문가 자격증 조회 응답 DTO
 * 전문가가 제출한 자격증/증빙 서류 정보를 관리자에게 보여주기 위해 사용한다.
 */
@Getter
@Builder
public class AdminExpertCertificateResponse {

    private Long certificateId;
    private Long fileId;
    private String ownerName;
    private String certificateTypeCode;
    private String certificateNumber;
    private String issueDate;
    private String createdAt;

    public static AdminExpertCertificateResponse from(ExpertCertificate certificate) {
        return AdminExpertCertificateResponse.builder()
                .certificateId(certificate.getId())

                // 자격증 파일 다운로드를 위해 files.id를 함께 내려준다.
                .fileId(certificate.getFile().getId())

                .ownerName(certificate.getOwnerName())
                .certificateTypeCode(String.valueOf(certificate.getCertificateTypeCode()))
                .certificateNumber(certificate.getCertificateNumber())
                .issueDate(certificate.getIssueDate() != null
                        ? certificate.getIssueDate().toString()
                        : null)
                .createdAt(certificate.getCreatedAt() != null
                        ? certificate.getCreatedAt().toString()
                        : null)
                .build();
    }
}