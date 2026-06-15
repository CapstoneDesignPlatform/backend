package com.capdi.backend.domain.admin.dto.excel;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminClientExcelRow {

    private String name;
    private String companyName;
    private String businessNumber;
    private String contact;
    private String email;
    private String address;
    private LocalDateTime createdAt;
}
