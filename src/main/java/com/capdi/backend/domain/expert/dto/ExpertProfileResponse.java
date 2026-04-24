package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ExpertProfileResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String officeName;
    private String specialty;
    private Integer careerYears;
    private String introduction;
    private VerificationStatus verificationStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ExpertProfileResponse from(ExpertProfile expertProfile) {
        return ExpertProfileResponse.builder()
                .id(expertProfile.getId())
                .userId(expertProfile.getUser().getId())
                .userName(expertProfile.getUser().getName())
                .officeName(expertProfile.getOfficeName())
                .specialty(expertProfile.getSpecialty())
                .careerYears(expertProfile.getCareerYears())
                .introduction(expertProfile.getIntroduction())
                .verificationStatus(expertProfile.getVerificationStatus())
                .createdAt(expertProfile.getCreatedAt())
                .updatedAt(expertProfile.getUpdatedAt())
                .build();
    }
}