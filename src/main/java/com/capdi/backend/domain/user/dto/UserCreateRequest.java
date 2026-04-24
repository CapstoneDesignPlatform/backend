package com.capdi.backend.domain.user.dto;

import lombok.Getter;

/**
 * 사용자 생성 요청 DTO
 */
@Getter
public class UserCreateRequest {

    private String email;
    private String passwordHash;
    private String name;
    private String phone;
    private String role;
    private String status;
}