package com.capdi.backend.domain.client.dto;

import com.capdi.backend.domain.client.entity.ClientInfo;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ClientInfoResponse {

    private Long id;
    private Long userId;
    private String companyName;
    private String businessNumber;
    private String representativeName;
    private String contact;
    private String email;
    private String address;
    private String website;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ClientInfoResponse from(ClientInfo clientInfo) {
        return ClientInfoResponse.builder()
                .id(clientInfo.getId())
                .userId(clientInfo.getUser() != null ? clientInfo.getUser().getId() : null)
                .companyName(clientInfo.getCompanyName())
                .businessNumber(clientInfo.getBusinessNumber())
                .representativeName(clientInfo.getRepresentativeName())
                .contact(clientInfo.getContact())
                .email(clientInfo.getEmail())
                .address(clientInfo.getAddress())
                .website(clientInfo.getWebsite())
                .description(clientInfo.getDescription())
                .createdAt(clientInfo.getCreatedAt())
                .updatedAt(clientInfo.getUpdatedAt())
                .build();
    }
}
