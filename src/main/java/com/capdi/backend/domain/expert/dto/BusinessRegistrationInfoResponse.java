package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BusinessRegistrationInfoResponse {

    private Long id;

    @JsonProperty("file_id")
    private Long fileId;

    @JsonProperty("business_number")
    private String businessNumber;

    @JsonProperty("representative_name")
    private String representativeName;

    @JsonProperty("company_name")
    private String companyName;

    public static BusinessRegistrationInfoResponse from(BusinessRegistrationInfo businessRegistrationInfo) {
        return BusinessRegistrationInfoResponse.builder()
                .id(businessRegistrationInfo.getId())
                .fileId(businessRegistrationInfo.getFile().getId())
                .businessNumber(businessRegistrationInfo.getBusinessNumber())
                .representativeName(businessRegistrationInfo.getRepresentativeName())
                .companyName(businessRegistrationInfo.getCompanyName())
                .build();
    }
}
