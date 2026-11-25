package com.capdi.backend.domain.auth.dto;

import com.capdi.backend.domain.user.entity.UserTypeEnum;
import jakarta.validation.constraints.*;

/** 카카오 가입: SignupRequest와 같은 입력 검증, 비밀번호 대신 가입 대기 토큰. */
public record KakaoSignupRequest(
        @NotBlank(message = "가입 정보가 만료되었습니다. 카카오 로그인을 다시 진행해주세요.")
        String signupToken,

        @NotNull(message = "역할을 선택해주세요.")
        UserTypeEnum userType,

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(max = 50, message = "이름은 50자 이하로 입력해주세요.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 100, message = "이메일은 100자 이하로 입력해주세요.")
        String email,

        @NotBlank(message = "연락처를 입력해주세요.")
        @Pattern(regexp = "^\\d{10,11}$", message = "연락처는 숫자 10~11자리로 입력해주세요.")
        String phone,

        @Size(max = 100, message = "업체명은 100자 이하로 입력해주세요.")
        String businessName
) {}
