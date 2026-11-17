package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.CertificateTypeCodeEnum;

public record ExpertCertificateTypeResponse(String code, String name) {

    public static ExpertCertificateTypeResponse from(CertificateTypeCodeEnum type) {
        return new ExpertCertificateTypeResponse(type.name(), type.getLabel());
    }
}
