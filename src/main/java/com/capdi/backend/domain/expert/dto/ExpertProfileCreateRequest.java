package com.capdi.backend.domain.expert.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ExpertProfileCreateRequest {

    @NotNull(message = "사용자 ID는 필수입니다.")
    private Long userId;

    @Size(max = 100, message = "사무소명은 100자 이하여야 합니다.")
    private String officeName;

    @Size(max = 100, message = "전문 분야는 100자 이하여야 합니다.")
    private String specialty;

    @Min(value = 0, message = "경력 연수는 0 이상이어야 합니다.")
    private Integer careerYears;

    private String introduction;
}