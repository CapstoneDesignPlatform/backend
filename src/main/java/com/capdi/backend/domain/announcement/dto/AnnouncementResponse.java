package com.capdi.backend.domain.announcement.dto;

import com.capdi.backend.domain.announcement.entity.*;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnnouncementResponse {

    private Long id;
    private Long userId;
    private Long clientInfoId;
    private String announcementCode;
    private IndustryTypeEnum industry;
    private AnnouncementPurposeEnum purpose;
    private BusinessOwnerTypeEnum businessOwnerType;
    private AnnouncementCategoryEnum category;
    private CurrentIndustryStatusEnum currentIndustry;
    private String currentIndustryDetail;
    private String currentLicense;
    private JobTypeEnum jobType;
    private String requiredLicense;
    private BigDecimal assetScale;
    private DiagnosisReasonEnum diagnosisReason;
    private String diagnosisReasonDetail;
    private AnnouncementStatusEnum status;
    private LocalDateTime createdAt;

    public static AnnouncementResponse from(Announcement announcement) {
        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .userId(announcement.getUser() == null ? null : announcement.getUser().getId())
                .clientInfoId(announcement.getClientInfo().getId())
                .announcementCode(announcement.getAnnouncementCode())
                .industry(announcement.getIndustry())
                .purpose(announcement.getPurpose())
                .businessOwnerType(announcement.getBusinessOwnerType())
                .category(announcement.getCategory())
                .currentIndustry(announcement.getCurrentIndustry())
                .currentIndustryDetail(announcement.getCurrentIndustryDetail())
                .currentLicense(announcement.getCurrentLicense())
                .jobType(announcement.getJobType())
                .requiredLicense(announcement.getRequiredLicense())
                .assetScale(announcement.getAssetScale())
                .diagnosisReason(announcement.getDiagnosisReason())
                .diagnosisReasonDetail(announcement.getDiagnosisReasonDetail())
                .status(announcement.getStatus())
                .createdAt(announcement.getCreatedAt())
                .build();
    }
}
