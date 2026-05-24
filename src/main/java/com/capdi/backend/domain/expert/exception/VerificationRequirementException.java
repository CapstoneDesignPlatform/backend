package com.capdi.backend.domain.expert.exception;

import com.capdi.backend.domain.expert.dto.VerificationRequirementErrorResponse;
import lombok.Getter;

@Getter
public class VerificationRequirementException extends RuntimeException {

    private final VerificationRequirementErrorResponse response;

    public VerificationRequirementException(VerificationRequirementErrorResponse response) {
        super("인증 신청 조건을 확인해주세요.");
        this.response = response;
    }
}
