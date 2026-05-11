package com.capdi.backend.domain.auth.dto;

import com.capdi.backend.domain.user.entity.UserTypeEnum;
import jakarta.validation.constraints.*;
import lombok.Getter;

@Getter
public class SignupRequest {

    @NotNull(message = "역할을 선택해주세요.")
    private UserTypeEnum userType;

    @NotBlank(message = "이름을 입력해주세요.")
    @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
    private String name;

    @NotBlank(message = "이메일을 입력해주세요.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요.")
    private String email;

    @NotBlank(message = "연락처를 입력해주세요.")
    @Pattern(regexp = "^\\d{10,11}$", message = "연락처는 숫자 10~11자리로 입력해주세요.")
    private String phone;

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]).{8,}$", message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
    @Size(min = 8, max = 255, message = "비밀번호는 8자 이상이어야 합니다.")
    private String password;
}