package com.capdi.backend.domain.expert.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "certificate_name", nullable = false, length = 100)
    private String certificateName;

    @Column(name = "certificate_number", length = 100)
    private String certificateNumber;

    @Column(name = "registration_period", length = 100)
    private String registrationPeriod;

    @Column(name = "expired_at")
    private LocalDate expiredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "certificate_type_code", nullable = false, length = 30)
    private CertificateTypeCodeEnum certificateTypeCode;
}
