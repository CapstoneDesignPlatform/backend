package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExpertSearchDetailResponse(
        @JsonProperty("expert_user_id") Long expertUserId,
        String name,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("representative_name") String representativeName,
        String phone,
        @JsonProperty("certificate_types") List<ExpertCertificateTypeResponse> certificateTypes,
        @JsonProperty("portfolio_description") String portfolioDescription
) {
}
