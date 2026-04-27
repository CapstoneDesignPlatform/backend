package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExpertProfileResponse {

    private UserDto user;

    @JsonProperty("expert_profile")
    private ExpertProfileDto expertProfile;

    public static ExpertProfileResponse from(ExpertProfile expertProfile) {
        return ExpertProfileResponse.builder()
                .user(UserDto.from(expertProfile.getUser()))
                .expertProfile(ExpertProfileDto.from(expertProfile))
                .build();
    }

    @Getter
    @Builder
    public static class UserDto {
        private Long id;
        private String name;
        private String email;
        private String phone;

        public static UserDto from(com.capdi.backend.domain.user.entity.User user) {
            return UserDto.builder()
                    .id(user.getId())
                    .name(user.getName())
                    .email(user.getEmail())
                    .phone(user.getPhone())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ExpertProfileDto {
        private Long id;

        @JsonProperty("user_id")
        private Long userId;

        @JsonProperty("company_name")
        private String companyName;

        @JsonProperty("license_type")
        private String licenseType;

        @JsonProperty("expertise_areas")
        private List<String> expertiseAreas;

        private String portfolio;

        @JsonProperty("verification_status")
        private String verificationStatus;

        @JsonProperty("is_verified")
        private Boolean isVerified;

        private ExpertStatsDto stats;

        public static ExpertProfileDto from(ExpertProfile expertProfile) {
            return ExpertProfileDto.builder()
                    .id(expertProfile.getId())
                    .userId(expertProfile.getUser().getId())
                    .companyName(expertProfile.getBusinessName())
                    .licenseType(expertProfile.getSpecialty())
                    .expertiseAreas(List.of(expertProfile.getSpecialty()))
                    .portfolio(expertProfile.getPortfolioDescription())
                    .verificationStatus(expertProfile.getIsVerified() ? "APPROVED" : "NOT_APPLIED")
                    .isVerified(expertProfile.getIsVerified())
                    .stats(ExpertStatsDto.from(expertProfile))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ExpertStatsDto {
        @JsonProperty("active_bids")
        private Integer activeBids;

        @JsonProperty("won_projects")
        private Integer wonProjects;

        @JsonProperty("completed_projects")
        private Integer completedProjects;

        @JsonProperty("total_earned")
        private Integer totalEarned;

        public static ExpertStatsDto from(ExpertProfile expertProfile) {
            return ExpertStatsDto.builder()
                    .activeBids(0)
                    .wonProjects(expertProfile.getSelectedCount())
                    .completedProjects(0)
                    .totalEarned(0)
                    .build();
        }
    }
}