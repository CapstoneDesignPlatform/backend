package com.capdi.backend.domain.jobpost.dto;

import com.capdi.backend.domain.jobpost.entity.JobPost;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class JobPostResponse {

    /**
     * 공고 응답 DTO
     * 공고 조회 API에서 반환되는 데이터 구조
     */

    private Long id;
    private Long companyId;
    private String companyName;
    private String title;
    private String description;
    private Long budget;
    private LocalDate deadline;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static JobPostResponse from(JobPost jobPost) {
        return JobPostResponse.builder()
                .id(jobPost.getId())
                .companyId(jobPost.getCompany().getId())
                .companyName(jobPost.getCompany().getName())
                .title(jobPost.getTitle())
                .description(jobPost.getDescription())
                .budget(jobPost.getBudget())
                .deadline(jobPost.getDeadline())
                .status(jobPost.getStatus())
                .createdAt(jobPost.getCreatedAt())
                .updatedAt(jobPost.getUpdatedAt())
                .build();
    }
}