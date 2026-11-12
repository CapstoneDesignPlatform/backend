package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ExpertSearchItemResponse(
        @JsonProperty("expert_user_id") Long expertUserId,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("representative_name") String representativeName,
        @JsonProperty("certificate_types") List<ExpertCertificateTypeResponse> certificateTypes
) {
}
