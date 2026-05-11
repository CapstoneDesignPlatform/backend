package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminAnnouncementSummaryResponse {

    private Long id;
    private String announcementCode;
    private String companyName;
    private String clientName;
    private String purpose;
    private String industry;
    private AnnouncementStatusEnum status;
    private int bidCount;
    private LocalDateTime createdAt;

    public static AdminAnnouncementSummaryResponse from(Announcement announcement, int bidCount) {
        return AdminAnnouncementSummaryResponse.builder()
                .id(announcement.getId())
                .announcementCode(announcement.getAnnouncementCode())
                .companyName(announcement.getClientInfo().getCompanyName())
                .clientName(announcement.getClientInfo().getRepresentativeName())
                .purpose(announcement.getPurpose().name())
                .industry(announcement.getIndustry().name())
                .status(announcement.getStatus())
                .bidCount(bidCount)
                .createdAt(announcement.getCreatedAt())
                .build();
    }
}
