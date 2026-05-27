package com.capdi.backend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AdminLoginRequest {

    @NotNull(message = "관리자 ID는 필수입니다.")
    private Long adminId;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;
}
