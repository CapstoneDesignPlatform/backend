package com.capdi.backend.domain.admin.dto.excel;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminExpertExcelRow {

    private String name;
    private String email;
    private String phone;
    private String specialty;
    private LocalDateTime appliedAt;
    private String verificationStatus;
    private LocalDateTime verifiedAt;
    private String documentSubmitted;
}
