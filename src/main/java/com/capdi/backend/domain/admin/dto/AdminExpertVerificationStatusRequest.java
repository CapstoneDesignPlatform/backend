package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 관리자 전문가 승인 상태 변경 요청 DTO
 *
 * verificationStatus 값은 Enum으로 받아 오타나 허용되지 않는 상태값을 방지한다.
 */
@Getter
public class AdminExpertVerificationStatusRequest {

    @NotNull(message = "승인 상태는 필수입니다.")
    private VerificationStatusEnum verificationStatus;

    /**
     * REJECTED 상태에서 전문가에게 제공할 최신 반려 사유.
     */
    private String rejectReason;
}
