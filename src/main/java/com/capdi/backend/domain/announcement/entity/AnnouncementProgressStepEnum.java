package com.capdi.backend.domain.announcement.entity;

public enum AnnouncementProgressStepEnum {
    STEP_1_REGISTERED("필요 정보 등록"),
    STEP_2_BID_CLOSED("마감"),
    STEP_3_EXPERT_SELECTION("전문가 선택"),
    STEP_4_DIAGNOSIS_STARTED("진단시작"),
    STEP_5_ASSOCIATION_REVIEW("협회 경유"),
    STEP_6_REPORT_SENT("작성 완료 및 발송"),
    STEP_7_COMPLETED("수령");

    private final String label;

    AnnouncementProgressStepEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public int getStepNumber() {
        return this.ordinal() + 1;
    }
}
