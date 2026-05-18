package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 전문가 상세 조회 응답 DTO
 * 상세 화면에서 전문가 기본 정보와 승인 상태를 확인하기 위해 사용한다.
 */
@Getter
@Builder
public class AdminExpertDetailResponse {

    private Long userId;
    private Long expertProfileId;
    private String email;
    private String expertField;
    private String verificationStatus;
    private String createdAt;
    private String updatedAt;

    public static AdminExpertDetailResponse from(ExpertProfile expertProfile) {
        return AdminExpertDetailResponse.builder()
                .userId(expertProfile.getUser().getId())
                .expertProfileId(expertProfile.getId())
                .email(expertProfile.getUser().getEmail())
                .expertField(expertProfile.getSpecialty())
                .verificationStatus(expertProfile.getVerificationStatus().name())
                .createdAt(expertProfile.getCreatedAt() != null
                        ? expertProfile.getCreatedAt().toString()
                        : null)
                .updatedAt(expertProfile.getUpdatedAt() != null
                        ? expertProfile.getUpdatedAt().toString()
                        : null)
                .build();
    }
}
