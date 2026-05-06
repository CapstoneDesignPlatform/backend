package com.capdi.backend.domain.announcement.dto;

import com.capdi.backend.domain.announcement.entity.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class AnnouncementCreateRequest {

    //공통 필드
    @NotNull(message = "기업 정보 ID는 필수입니다.")
    private Long clientInfoId;

    @NotNull(message = "업종은 필수입니다.")
    private IndustryTypeEnum industry;

    @NotNull(message = "의뢰 목적은 필수입니다.")
    private AnnouncementPurposeEnum purpose;

    @NotNull(message = "사업자 유형은 필수입니다.")
    private BusinessOwnerTypeEnum businessOwnerType;

    private BigDecimal assetSize; //억원 단위(창업 예정이면 null)

    //필요 면허 탭 전용 (purpose = REQUIRED_LICENSE)
    private AnnouncementCategoryEnum category;              // 신규 | 추가
    private String requiredLicense;                         // 필요한 면허
    private CurrentIndustryStatusEnum currentIndustryStatus; // 현재 업종
    private String currentIndustryDetail;                   // 건설업관련/비건설업관련 선택 시 상세 내용

    //실태 조사 탭 전용 (purpose = STATUS_INVESTIGATION)
    private String heldLicense;                      // 보유 면허

    //기타 탭 전용 (purpose = ETC)
    private DiagnosisReasonEnum diagnosisReason;     // 진단 사유
    private String diagnosisReasonDetail;            // 진단 사유 기타 선택 시 상세 내용
}
