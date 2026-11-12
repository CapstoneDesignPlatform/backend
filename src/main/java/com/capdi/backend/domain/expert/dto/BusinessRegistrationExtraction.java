package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
public class BusinessRegistrationExtraction {
    @JsonProperty("business_number")
    private Field<String> businessNumber;
    @JsonProperty("representative_name")
    private Field<String> representativeName;
    @JsonProperty("company_name")
    private Field<String> companyName;
    @JsonProperty("opening_date")
    private Field<LocalDate> openingDate;

    public enum Status { CANDIDATE, MISSING, INVALID, NEEDS_CONFIRMATION }

    @Getter
    @Builder
    public static class Field<T> {
        private Status status;
        private T value;
        private List<Candidate<T>> candidates;
    }

    @Getter
    @Builder
    public static class Candidate<T> {
        private T value;
        private String raw;
        private boolean valid;
        private String reason;
        private String label;
        private String evidence;
        @JsonProperty("line_number")
        private int lineNumber;
        @JsonProperty("page_number")
        private Integer pageNumber;
    }
}
