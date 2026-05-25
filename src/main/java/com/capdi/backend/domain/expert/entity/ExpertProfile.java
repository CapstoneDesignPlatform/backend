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

    public static final String DEFAULT_REJECTED_REASON =
            "제출 서류를 확인할 수 없어 인증이 반려되었습니다. 제출 정보를 확인한 후 다시 신청해주세요.";

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

    @Column(name = "rejected_reason", length = 500)
    private String rejectedReason;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public static ExpertProfile createDefault(User user, String businessName) {
        return ExpertProfile.builder()
                .user(user)
                .specialty("")
                .businessName(businessName)
                .experienceYears(0)
                .portfolioDescription("")
                .isVerified(false)
                .verificationStatus(VerificationStatusEnum.NOT_SUBMITTED)
                .selectedCount(0)
                .build();
    }

    public void updateBusinessName(String businessName) {
        if (businessName != null) {
            this.businessName = businessName;
        }
    }
    public void applyVerification(String specialty) {
        this.verificationStatus = VerificationStatusEnum.PENDING;
        this.isVerified = false;
        this.verifiedAt = null;
        this.rejectedReason = null;
        this.reviewedAt = null;

        if (specialty != null) {
            this.specialty = specialty;
        }
    }

    public void updateVerificationStatus(VerificationStatusEnum verificationStatus, String rejectedReason) {
        LocalDateTime now = LocalDateTime.now();

        this.verificationStatus = verificationStatus;
        this.isVerified = verificationStatus == VerificationStatusEnum.APPROVED;
        this.verifiedAt = this.isVerified ? now : null;
        this.reviewedAt = isReviewed(verificationStatus) ? now : null;
        this.rejectedReason = verificationStatus == VerificationStatusEnum.REJECTED
                ? resolveRejectedReason(rejectedReason)
                : null;
    }

    private String resolveRejectedReason(String rejectedReason) {
        return rejectedReason == null || rejectedReason.isBlank()
                ? DEFAULT_REJECTED_REASON
                : rejectedReason;
    }

    private boolean isReviewed(VerificationStatusEnum verificationStatus) {
        return verificationStatus == VerificationStatusEnum.APPROVED
                || verificationStatus == VerificationStatusEnum.REJECTED;
    }
}
