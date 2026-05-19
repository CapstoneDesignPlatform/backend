package com.capdi.backend.domain.expert.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class ExpertVerificationRequest {

    @NotBlank(message = "전문가 유형은 필수입니다.")
    @Size(max = 100, message = "전문가 유형은 100자 이하로 입력해주세요.")
    private String specialty;
}
