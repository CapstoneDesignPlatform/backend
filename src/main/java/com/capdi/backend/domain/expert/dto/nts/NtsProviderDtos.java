package com.capdi.backend.domain.expert.dto.nts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class NtsProviderDtos {
    private NtsProviderDtos() { }

    public record ValidationRequest(List<Business> businesses) {
        @Override public String toString() { return "NtsValidationRequest[redacted]"; }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Business(@JsonProperty("b_no") String number, @JsonProperty("start_dt") String openingDate,
                           @JsonProperty("p_nm") String representative, @JsonProperty("b_nm") String company) {
        @Override public String toString() { return "NtsBusiness[redacted]"; }
    }
    public record StatusRequest(@JsonProperty("b_no") List<String> numbers) {
        @Override public String toString() { return "NtsStatusRequest[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ValidationResponse(@JsonProperty("status_code") String code, @JsonProperty("request_cnt") Integer requests,
                                     @JsonProperty("valid_cnt") Integer validCount, List<Validation> data) {
        @Override public String toString() { return "NtsValidationResponse[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Validation(@JsonProperty("b_no") String number, String valid, BusinessStatus status) {
        @Override public String toString() { return "NtsValidation[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StatusResponse(@JsonProperty("status_code") String code, @JsonProperty("request_cnt") Integer requests,
                                 @JsonProperty("match_cnt") Integer matches, List<BusinessStatus> data) {
        @Override public String toString() { return "NtsStatusResponse[redacted]"; }
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BusinessStatus(@JsonProperty("b_no") String number, @JsonProperty("b_stt_cd") String state,
                                 @JsonProperty("tax_type") String taxType) {
        @Override public String toString() { return "NtsBusinessStatus[redacted]"; }
    }
}
