package com.capdi.backend.domain.expert.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class VerificationRequirementErrorResponse {

    private String code;
    private List<ErrorItem> errors;

    @Getter
    @Builder
    public static class ErrorItem {

        private String field;
        private String code;
        private String message;
    }
}
