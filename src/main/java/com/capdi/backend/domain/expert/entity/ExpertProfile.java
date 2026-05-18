package com.capdi.backend.domain.expert.entity;

import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "expert_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExpertProfile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String specialty;

    @Column(name = "business_name", length = 100)
    private String businessName;

    @Column(name = "experience_years", nullable = false)
    private Integer experienceYears;

    @Column(name = "portfolio_description", columnDefinition = "TEXT")
    private String portfolioDescription;

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    @Builder.Default
    private VerificationStatusEnum verificationStatus = VerificationStatusEnum.NOT_SUBMITTED;

    @Column(name = "selected_count", nullable = false)
    @Builder.Default
    private Integer selectedCount = 0;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    public static ExpertProfile createDefault(User user) {
        return ExpertProfile.builder()
                .user(user)
                .specialty("")
                .businessName("")
                .experienceYears(0)
                .portfolioDescription("")
                .isVerified(false)
                .verificationStatus(VerificationStatusEnum.NOT_SUBMITTED)
                .selectedCount(0)
                .build();
    }

    public void updateProfile(String businessName, String specialty, String portfolioDescription) {
        if (businessName != null) {
            this.businessName = businessName;
        }

        if (specialty != null) {
            this.specialty = specialty;
        }

        if (portfolioDescription != null) {
            this.portfolioDescription = portfolioDescription;
        }
    }
    public void applyVerification(String licenseType, String companyName, String portfolio) {
        this.verificationStatus = VerificationStatusEnum.PENDING;
        this.isVerified = false;
        this.verifiedAt = null;

        if (licenseType != null) {
            this.specialty = licenseType;
        }

        if (companyName != null) {
            this.businessName = companyName;
        }

        if (portfolio != null) {
            this.portfolioDescription = portfolio;
        }
    }

    public void updateVerificationStatus(VerificationStatusEnum verificationStatus) {
        this.verificationStatus = verificationStatus;
        this.isVerified = verificationStatus == VerificationStatusEnum.APPROVED;
        this.verifiedAt = this.isVerified ? LocalDateTime.now() : null;
    }
}
