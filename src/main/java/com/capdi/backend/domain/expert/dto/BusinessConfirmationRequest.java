package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class BusinessConfirmationRequest {
    @NotNull @Positive @JsonProperty("file_id")
    private Long fileId;
    @NotNull @Positive @JsonProperty("ocr_revision")
    private Long ocrRevision;
    @NotBlank @Size(max = 30) @JsonProperty("business_number")
    private String businessNumber;
    @NotBlank @Size(max = 100) @JsonProperty("representative_name")
    private String representativeName;
    @NotBlank @Size(max = 100) @JsonProperty("company_name")
    private String companyName;
    @NotNull @JsonProperty("opening_date")
    @JsonFormat(pattern = "uuuu-MM-dd", lenient = OptBoolean.FALSE)
    private LocalDate openingDate;
}
