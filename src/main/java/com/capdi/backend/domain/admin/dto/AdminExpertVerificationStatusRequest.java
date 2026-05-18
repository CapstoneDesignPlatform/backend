package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 관리자 전문가 승인 상태 변경 요청 DTO
 *
 * 현재 ExpertProfile에는 verificationStatus Enum 컬럼이 없고,
 * isVerified Boolean 컬럼만 존재한다.
 *
 * verificationStatus 값은 Enum으로 받아 오타나 허용되지 않는 상태값을 방지한다.
 */
@Getter
public class AdminExpertVerificationStatusRequest {

    @NotNull(message = "승인 상태는 필수입니다.")
    private VerificationStatusEnum verificationStatus;

    /**
     * 반려 사유
     * 현재 DB에 별도 컬럼이 없다면 바로 저장하지 않고,
     * 추후 admin_logs.description에 기록하는 방식으로 확장 가능하다.
     */
    private String rejectReason;
}
