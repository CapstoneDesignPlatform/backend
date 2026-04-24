package com.capdi.backend.domain.company.dto;

import com.capdi.backend.domain.company.entity.Company;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 회사 응답 DTO
 */
@Getter
@Builder
public class CompanyResponse {

    private Long id;
    private String name;
    private String businessNumber;
    private String contactName;
    private String contactPhone;
    private String address;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static CompanyResponse from(Company company) {
        return CompanyResponse.builder()
                .id(company.getId())
                .name(company.getName())
                .businessNumber(company.getBusinessNumber())
                .contactName(company.getContactName())
                .contactPhone(company.getContactPhone())
                .address(company.getAddress())
                .createdAt(company.getCreatedAt())
                .updatedAt(company.getUpdatedAt())
                .build();
    }
}