package com.capdi.backend.domain.announcement.entity;

import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "announcement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_info_id", nullable = false)
    private ClientInfo clientInfo;

    @Column(name = "announcement_code", nullable = false, unique = true, length = 50)
    private String announcementCode;

    @Column(nullable = false, length = 100)
    private String industry;

    @Column(nullable = false, length = 100)
    private String purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_owner_type", nullable = false, length = 50)
    private BusinessOwnerTypeEnum businessOwnerType;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "current_industry", length = 100)
    private String currentIndustry;

    @Column(name = "current_license", length = 100)
    private String currentLicense;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private JobTypeEnum jobType;

    @Column(name = "required_license", columnDefinition = "TEXT")
    private String requiredLicense;

    @Column(name = "asset_scale", precision = 15, scale = 2)
    private BigDecimal assetScale;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(name = "diagnosis_reason", nullable = false, length = 50)
    private String diagnosisReason;

    @Column(name = "diagnosis_reason_detail", columnDefinition = "TEXT")
    private String diagnosisReasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementStatusEnum status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
