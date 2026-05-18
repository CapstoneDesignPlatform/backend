package com.capdi.backend.domain.admin.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 50)
    private AdminActionTypeEnum actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 50)
    private AdminTargetTypeEnum targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder
    private AdminLog(
            Long adminId,
            AdminActionTypeEnum actionType,
            AdminTargetTypeEnum targetType,
            Long targetId,
            String description
    ) {
        this.adminId = adminId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.description = description;
    }
}
