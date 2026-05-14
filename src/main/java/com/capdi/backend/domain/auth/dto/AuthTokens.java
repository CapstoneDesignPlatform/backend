package com.capdi.backend.domain.auth.dto;

public record AuthTokens(String accessToken, String refreshToken, String role) {}
