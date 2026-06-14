package com.capdi.backend.domain.admin.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminLoginResponse {

    private String accessToken;
    private String name;

    public static AdminLoginResponse of(String accessToken, String name) {
        return AdminLoginResponse.builder()
                .accessToken(accessToken)
                .name(name)
                .build();
    }
}
