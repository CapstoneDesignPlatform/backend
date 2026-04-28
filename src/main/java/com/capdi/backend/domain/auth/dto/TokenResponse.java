package com.capdi.backend.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TokenResponse {

    private String accessToken;
    private String refreshToken;
    private String role;

    public static TokenResponse of(AuthTokens tokens) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), tokens.role());
    }
}
