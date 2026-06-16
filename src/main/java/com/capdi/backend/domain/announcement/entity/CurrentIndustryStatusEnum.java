package com.capdi.backend.domain.announcement.entity;

public enum CurrentIndustryStatusEnum {
    CONSTRUCTION_RELATED("건설업 관련"),
    NON_CONSTRUCTION_RELATED("비건설업 관련"),
    NONE("없음");

    private final String label;

    CurrentIndustryStatusEnum(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
