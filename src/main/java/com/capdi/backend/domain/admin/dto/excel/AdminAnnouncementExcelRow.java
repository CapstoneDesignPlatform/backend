package com.capdi.backend.domain.admin.dto.excel;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminAnnouncementExcelRow {

    private String announcementCode;
    private String title;
    private String industry;
    private String companyName;
    private String clientName;
    private String clientType;
    private String status;
    private long bidCount;
    private LocalDateTime createdAt;
}
