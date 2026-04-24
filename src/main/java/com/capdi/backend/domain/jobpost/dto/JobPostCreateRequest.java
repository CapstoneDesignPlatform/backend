package com.capdi.backend.domain.jobpost.dto;

import lombok.Getter;

import java.time.LocalDate;

/**
 * 공고 등록 요청 DTO
 */
@Getter
public class JobPostCreateRequest {

    private Long companyId;
    private String title;
    private String description;
    private Long budget;
    private LocalDate deadline;
}