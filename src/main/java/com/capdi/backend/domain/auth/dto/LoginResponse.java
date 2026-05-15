package com.capdi.backend.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    private String role;
    private Long clientInfoId;

    public static LoginResponse of(AuthTokens tokens) {
        return new LoginResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.role(),
                tokens.clientInfoId()
        );
    }
}
