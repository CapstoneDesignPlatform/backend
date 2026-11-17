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

    @JsonProperty("opening_date")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.time.LocalDate openingDate;

    @JsonProperty("input_revision")
    private long inputRevision;

    @JsonProperty("ocr_revision")
    private long ocrRevision;

    @JsonProperty("confirmation_current")
    private boolean confirmationCurrent;

    public static BusinessRegistrationInfoResponse from(BusinessRegistrationInfo businessRegistrationInfo) {
        return BusinessRegistrationInfoResponse.builder()
                .id(businessRegistrationInfo.getId())
                .fileId(businessRegistrationInfo.getFile().getId())
                .businessNumber(businessRegistrationInfo.getBusinessNumber())
                .representativeName(businessRegistrationInfo.getRepresentativeName())
                .companyName(businessRegistrationInfo.getCompanyName())
                .openingDate(businessRegistrationInfo.getOpeningDate())
                .inputRevision(businessRegistrationInfo.getInputRevision())
                .ocrRevision(businessRegistrationInfo.getFile().getOcrRevision())
                .confirmationCurrent(businessRegistrationInfo.getFile().getOcrStatus()
                        == com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED
                        && businessRegistrationInfo.isConfirmedFor(businessRegistrationInfo.getFile().getId(),
                                businessRegistrationInfo.getFile().getOcrRevision()))
                .build();
    }
}
