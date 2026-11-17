package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.BusinessVerification.Kind;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessVerificationRequest {
    @NotNull private Kind kind = Kind.VALIDATE;
    @NotNull @Positive @JsonProperty("file_id") private Long fileId;
    @NotNull @Positive @JsonProperty("ocr_revision") private Long ocrRevision;
    @NotNull @PositiveOrZero @JsonProperty("input_revision") private Long inputRevision;
}
