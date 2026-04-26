package com.capdi.backend.domain.jobpost.entity;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.client.entity.ClientInfo;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private ClientInfo clientInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "announcement_code", referencedColumnName = "announcement_code", nullable = false)
    private Announcement announcement;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false, length = 100)
    private LicenseTypeEnum licenseType;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 100)
    private WorkTypeEnum workType;

    @Enumerated(EnumType.STRING)
    @Column(name = "client_type", nullable = false, length = 50)
    private ClientTypeEnum clientType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal budget;

    @Column(length = 100)
    private String region;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "bid_deadline")
    private LocalDateTime bidDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private JobPostStatusEnum status = JobPostStatusEnum.OPEN;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;

        if (this.status == null) {
            this.status = JobPostStatusEnum.OPEN;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
