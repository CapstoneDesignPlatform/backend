package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import lombok.Builder;
import lombok.Getter;

/**
 * 관리자 전문가 사업자등록 정보 조회 응답 DTO
 * 전문가 검증 시 사업자번호, 대표자명, 상호명,
 * 첨부 파일 정보를 확인하기 위해 사용한다.
 */
@Getter
@Builder
public class AdminExpertBusinessInfoResponse {

    private Long businessRegistrationInfoId;
    private Long fileId;
    private String businessNumber;
    private String representativeName;
    private String companyName;
    private String createdAt;
    private String updatedAt;

    public static AdminExpertBusinessInfoResponse from(BusinessRegistrationInfo businessInfo) {
        return AdminExpertBusinessInfoResponse.builder()
                .businessRegistrationInfoId(businessInfo.getId())

                // 사업자등록증 파일 다운로드를 위해 files.id를 함께 내려준다.
                .fileId(businessInfo.getFile().getId())

                .businessNumber(businessInfo.getBusinessNumber())
                .representativeName(businessInfo.getRepresentativeName())
                .companyName(businessInfo.getCompanyName())
                .createdAt(businessInfo.getCreatedAt() != null
                        ? businessInfo.getCreatedAt().toString()
                        : null)
                .updatedAt(businessInfo.getUpdatedAt() != null
                        ? businessInfo.getUpdatedAt().toString()
                        : null)
                .build();
    }
}