package com.capdi.backend.domain.announcement.entity;

import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "announcement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Announcement extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;  // 비회원이면 null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_info_id", nullable = false)
    private ClientInfo clientInfo;

    @Column(name = "announcement_code", nullable = false, unique = true, length = 50)
    private String announcementCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IndustryTypeEnum industry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AnnouncementPurposeEnum purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "business_owner_type", nullable = false, length = 50)
    private BusinessOwnerTypeEnum businessOwnerType;

    // 필요 면허 탭 전용 (실태조사는 null)
    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private AnnouncementCategoryEnum category;

    // 필요 면허 탭 전용
    @Enumerated(EnumType.STRING)
    @Column(name = "current_industry", length = 50)
    private CurrentIndustryStatusEnum currentIndustry;

    // 건설업관련/비건설업관련 선택 시 상세 내용
    @Column(name = "current_industry_detail", columnDefinition = "TEXT")
    private String currentIndustryDetail;

    // 실태 조사 탭 전용 (보유 면허)
    @Column(name = "current_license", length = 100)
    private String currentLicense;

    // purpose → jobType 자동 매핑
    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private JobTypeEnum jobType;

    // 필요 면허 탭 전용 (필요한 면허)
    @Column(name = "required_license", columnDefinition = "TEXT")
    private String requiredLicense;

    @Column(name = "asset_scale", precision = 15, scale = 2)
    private BigDecimal assetScale;  // 억원 단위

    // 기타 탭 전용
    @Enumerated(EnumType.STRING)
    @Column(name = "diagnosis_reason", length = 50)
    private DiagnosisReasonEnum diagnosisReason;

    @Column(name = "diagnosis_reason_detail", columnDefinition = "TEXT")
    private String diagnosisReasonDetail;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnnouncementStatusEnum status = AnnouncementStatusEnum.ACTIVE;
}
