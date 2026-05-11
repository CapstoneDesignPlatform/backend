package com.capdi.backend.domain.jobpost.dto;

import com.capdi.backend.domain.announcement.entity.*;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.jobpost.entity.JobPost;
import com.capdi.backend.domain.jobpost.entity.JobPostStatusEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class JobPostResponse {

    private Long id;

    @JsonProperty("company_id")
    private Long companyId;

    @JsonProperty("company_name")
    private String companyName;

    private String title;
    private IndustryTypeEnum industry;

    @JsonProperty("job_type")
    private JobTypeEnum jobType;

    @JsonProperty("job_type_label")
    private String jobTypeLabel;

    @JsonProperty("business_type")
    private BusinessOwnerTypeEnum businessType;

    private String classification;

    @JsonProperty("required_license")
    private String requiredLicense;

    @JsonProperty("current_industry")
    private CurrentIndustryStatusEnum currentIndustry;

    @JsonProperty("current_license")
    private String currentLicense;

    private DiagnosisReasonEnum reason;

    @JsonProperty("asset_scale_label")
    private BigDecimal assetScaleLabel;

    @JsonProperty("bid_count")
    private long bidCount;

    @JsonProperty("posted_at")
    private LocalDateTime postedAt;

    @JsonProperty("is_new")
    private boolean isNew;

    private JobPostStatusEnum status;

    @JsonProperty("has_my_bid")
    private boolean hasMyBid;

    public static JobPostResponse from(JobPost jobPost, long bidCount, boolean hasMyBid) {
        Announcement announcement = jobPost.getAnnouncement();
        ClientInfo clientInfo = jobPost.getClientInfo();

        return JobPostResponse.builder()
                .id(jobPost.getId())
                .companyId(clientInfo == null ? null : clientInfo.getId())
                .companyName(clientInfo == null ? null : clientInfo.getCompanyName())
                .title(jobPost.getTitle())
                .industry(announcement == null ? null : announcement.getIndustry())
                .jobType(announcement == null ? null : announcement.getJobType())
                .jobTypeLabel(resolveJobTypeLabel(announcement == null ? null : announcement.getJobType()))
                .businessType(announcement == null ? null : announcement.getBusinessOwnerType())
                .classification(announcement == null
                        ? jobPost.getCategory()
                        : (announcement.getCategory() == null ? null : announcement.getCategory().name()))
                .requiredLicense(announcement == null ? null : announcement.getRequiredLicense())
                .currentIndustry(announcement == null ? null : announcement.getCurrentIndustry())
                .currentLicense(announcement == null ? null : announcement.getCurrentLicense())
                .reason(announcement == null ? null : announcement.getDiagnosisReason())
                .assetScaleLabel(announcement == null ? null : announcement.getAssetScale())
                .bidCount(bidCount)
                .postedAt(jobPost.getCreatedAt())
                .isNew(isNew(jobPost.getCreatedAt()))
                .status(jobPost.getStatus())
                .hasMyBid(hasMyBid)
                .build();
    }

    private static String resolveJobTypeLabel(JobTypeEnum jobType) {
        if (jobType == null) {
            return null;
        }

        return switch (jobType) {
            case LICENSE -> "필요 면허";
            case SURVEY -> "실태 조사";
            case PERIODIC_REPORT -> "주기적 신고";
            case ETC -> "기타";
        };
    }

    private static boolean isNew(LocalDateTime postedAt) {
        return postedAt != null && postedAt.isAfter(LocalDateTime.now().minusDays(3));
    }
}
