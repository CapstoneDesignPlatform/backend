package com.capdi.backend.domain.user.dto;

import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {

    private Long userId;
    private UserTypeEnum userType;
    private String name;
    private String email;
    private String phone;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userId(user.getId())
                .userType(user.getUserType())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .build();
    }
}
