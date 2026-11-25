package com.capdi.backend.domain.auth.dto;

/** registered=true면 로그인 토큰, false면 회원가입 화면에서 쓸 signupToken만 채워진다. */
public record KakaoLoginResponse(
        boolean registered,
        String accessToken,
        String refreshToken,
        String role,
        Long clientInfoId,
        String signupToken
) {
    public static KakaoLoginResponse registered(AuthTokens tokens) {
        return new KakaoLoginResponse(true, tokens.accessToken(), tokens.refreshToken(), tokens.role(),
                tokens.clientInfoId(), null);
    }

    public static KakaoLoginResponse signupRequired(String signupToken) {
        return new KakaoLoginResponse(false, null, null, null, null, signupToken);
    }
}
