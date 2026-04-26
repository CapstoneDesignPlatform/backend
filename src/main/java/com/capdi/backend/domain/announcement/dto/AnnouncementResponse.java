package com.capdi.backend.domain.announcement.dto;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.entity.BusinessOwnerTypeEnum;
import com.capdi.backend.domain.announcement.entity.JobTypeEnum;
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
    private String industry;
    private String purpose;
    private BusinessOwnerTypeEnum businessOwnerType;
    private String category;
    private String currentIndustry;
    private String currentLicense;
    private JobTypeEnum jobType;
    private String requiredLicense;
    private BigDecimal assetScale;
    private LocalDateTime deadline;
    private String diagnosisReason;
    private String diagnosisReasonDetail;
    private AnnouncementStatusEnum status;
    private LocalDateTime createdAt;

    public static AnnouncementResponse from(Announcement announcement) {
        return AnnouncementResponse.builder()
                .id(announcement.getId())
                .userId(announcement.getUser() == null ? null : announcement.getUser().getId())
                .clientInfoId(announcement.getClientInfo() == null ? null : announcement.getClientInfo().getId())
                .announcementCode(announcement.getAnnouncementCode())
                .industry(announcement.getIndustry())
                .purpose(announcement.getPurpose())
                .businessOwnerType(announcement.getBusinessOwnerType())
                .category(announcement.getCategory())
                .currentIndustry(announcement.getCurrentIndustry())
                .currentLicense(announcement.getCurrentLicense())
                .jobType(announcement.getJobType())
                .requiredLicense(announcement.getRequiredLicense())
                .assetScale(announcement.getAssetScale())
                .deadline(announcement.getDeadline())
                .diagnosisReason(announcement.getDiagnosisReason())
                .diagnosisReasonDetail(announcement.getDiagnosisReasonDetail())
                .status(announcement.getStatus())
                .createdAt(announcement.getCreatedAt())
                .build();
    }
}
