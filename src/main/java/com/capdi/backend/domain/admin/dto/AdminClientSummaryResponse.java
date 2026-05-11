package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminClientSummaryResponse {

    private Long userId;
    private String name;
    private String email;
    private String companyName;
    private String businessNumber;
    private LocalDateTime createdAt;

    public static AdminClientSummaryResponse from(User user, ClientInfo clientInfo) {
        return AdminClientSummaryResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .companyName(clientInfo != null ? clientInfo.getCompanyName() : null)
                .businessNumber(clientInfo != null ? clientInfo.getBusinessNumber() : null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
