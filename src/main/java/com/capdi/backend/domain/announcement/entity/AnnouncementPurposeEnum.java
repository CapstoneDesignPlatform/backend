package com.capdi.backend.domain.announcement.entity;

public enum AnnouncementPurposeEnum {
    REQUIRED_LICENSE("필요 면허"),
    STATUS_INVESTIGATION("실태 조사"),
    ETC("기타");

    private final String label;

    AnnouncementPurposeEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public JobTypeEnum toJobType() {
        return switch (this) {
            case REQUIRED_LICENSE -> JobTypeEnum.LICENSE;
            case STATUS_INVESTIGATION -> JobTypeEnum.SURVEY;
            case ETC -> JobTypeEnum.ETC;
        };
    }
}
