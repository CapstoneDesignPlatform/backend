package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.CertificateTypeCodeEnum;

public record ExpertCertificateTypeRow(Long profileId, CertificateTypeCodeEnum type) {
}
