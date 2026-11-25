package com.capdi.backend.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record KakaoLoginRequest(
        @NotBlank(message = "인가 코드가 없습니다.")
        @Size(max = 512, message = "인가 코드가 올바르지 않습니다.")
        String code,

        // RFC 7636 4.1: unreserved 문자 43~128자
        @NotBlank(message = "code_verifier가 없습니다.")
        @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}", message = "code_verifier 형식이 올바르지 않습니다.")
        String codeVerifier,

        @NotBlank(message = "nonce가 없습니다.")
        @Size(min = 16, max = 128, message = "nonce 형식이 올바르지 않습니다.")
        String nonce
) {}
