package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 전문가 목록 조회 응답 DTO
 * 목록 화면에서 필요한 최소 정보만 내려준다.
 */
@Getter
@Builder
public class AdminExpertSummaryResponse {

    private Long userId;
    private Long expertProfileId;
    private String email;
    private String expertField;
    private String verificationStatus;
    private String createdAt;

    public static AdminExpertSummaryResponse from(ExpertProfile expertProfile) {
        return AdminExpertSummaryResponse.builder()
                .userId(expertProfile.getUser().getId())
                .expertProfileId(expertProfile.getId())
                .email(expertProfile.getUser().getEmail())
                .expertField(expertProfile.getSpecialty())
                .verificationStatus(expertProfile.getVerificationStatus().name())
                .createdAt(expertProfile.getCreatedAt() != null
                        ? expertProfile.getCreatedAt().toString()
                        : null)
                .build();
    }
}
