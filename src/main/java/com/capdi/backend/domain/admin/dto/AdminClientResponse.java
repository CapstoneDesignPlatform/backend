package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminClientResponse {

    private Long userId;
    private String name;
    private String email;
    private String phone;
    private String companyName;
    private String businessNumber;
    private String contact;
    private String address;
    private String website;
    private String description;
    private LocalDateTime createdAt;

    public static AdminClientResponse from(User user, ClientInfo clientInfo) {
        return AdminClientResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .companyName(clientInfo != null ? clientInfo.getCompanyName() : null)
                .businessNumber(clientInfo != null ? clientInfo.getBusinessNumber() : null)
                .contact(clientInfo != null ? clientInfo.getContact() : null)
                .address(clientInfo != null ? clientInfo.getAddress() : null)
                .website(clientInfo != null ? clientInfo.getWebsite() : null)
                .description(clientInfo != null ? clientInfo.getDescription() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
