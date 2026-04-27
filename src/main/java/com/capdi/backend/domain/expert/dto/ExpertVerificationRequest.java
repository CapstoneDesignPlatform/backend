package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

@Getter
public class ExpertVerificationRequest {

    @NotBlank(message = "자격증 유형은 필수입니다.")
    @JsonProperty("license_type")
    private String licenseType;

    @JsonProperty("license_number")
    private String licenseNumber;

    @JsonProperty("issue_date")
    private LocalDate issueDate;

    @NotBlank(message = "회사명은 필수입니다.")
    @JsonProperty("company_name")
    private String companyName;

    private String portfolio;
}