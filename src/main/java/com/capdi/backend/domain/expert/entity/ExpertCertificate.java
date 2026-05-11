package com.capdi.backend.domain.expert.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "expert_certificates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExpertCertificate extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_profile_id", nullable = false)
    private ExpertProfile expertProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private ExpertFile file;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(name = "certificate_name", nullable = false, length = 100)
    private String certificateName;

    @Column(name = "certificate_number", nullable = false, length = 100)
    private String certificateNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "expired_at")
    private LocalDate expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type_code", nullable = false, length = 30)
    private CertificateTypeCodeEnum certificateTypeCode;

    public static ExpertCertificate create(
            ExpertProfile expertProfile,
            ExpertFile file,
            String certificateName,
            String certificateNumber,
            LocalDate issueDate,
            String ownerName
    ) {
        CertificateTypeCodeEnum certificateTypeCode = CertificateTypeCodeEnum.fromLabel(certificateName);

        return ExpertCertificate.builder()
                .expertProfile(expertProfile)
                .file(file)
                .ownerName(ownerName)
                .certificateName(certificateTypeCode.getLabel())
                .certificateNumber(certificateNumber)
                .issueDate(issueDate)
                .expiredAt(certificateTypeCode.calculateExpiredAt(issueDate))
                .certificateTypeCode(certificateTypeCode)
                .build();
    }
}
