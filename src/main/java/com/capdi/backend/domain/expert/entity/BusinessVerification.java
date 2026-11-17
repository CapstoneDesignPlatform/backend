package com.capdi.backend.domain.expert.entity;

import com.capdi.backend.domain.expert.dto.NtsBusinessResult;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult.*;
import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expert_business_verifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessVerification extends BaseTimeEntity {
    public enum Kind { VALIDATE, STATUS }
    public enum ExecutionStatus { RUNNING, SUCCEEDED, FAILED }
    public enum Validity { CURRENT, STALE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "business_registration_info_id", nullable = false)
    private Long businessRegistrationInfoId;
    @Column(name = "execution_id", nullable = false, length = 36, unique = true)
    private String executionId;
    @Enumerated(EnumType.STRING) @Column(name = "execution_status", nullable = false, length = 20)
    private ExecutionStatus executionStatus;
    @Enumerated(EnumType.STRING) @Column(name = "verification_kind", nullable = false, length = 20)
    private Kind kind;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Authenticity authenticity;
    @Enumerated(EnumType.STRING) @Column(name = "business_state", nullable = false, length = 30)
    private BusinessState businessState;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40)
    private Failure failure;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private Validity validity;
    @Column(name = "file_id", nullable = false)
    private Long fileId;
    @Column(name = "file_sha256", nullable = false, length = 64)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    private String fileSha256;
    @Column(name = "ocr_revision", nullable = false)
    private long ocrRevision;
    @Column(name = "input_revision", nullable = false)
    private long inputRevision;
    @Column(name = "status_lookup_performed", nullable = false)
    private boolean statusLookupPerformed;
    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static BusinessVerification start(Long infoId, Kind kind, Long fileId, String sha, long ocr, long input) {
        var row = new BusinessVerification();
        row.businessRegistrationInfoId = infoId;
        row.executionId = UUID.randomUUID().toString();
        row.executionStatus = ExecutionStatus.RUNNING;
        row.kind = kind;
        row.fileId = fileId;
        row.fileSha256 = sha;
        row.ocrRevision = ocr;
        row.inputRevision = input;
        row.requestedAt = LocalDateTime.now();
        row.authenticity = Authenticity.UNAVAILABLE;
        row.businessState = BusinessState.UNKNOWN;
        row.failure = Failure.NONE;
        row.validity = Validity.CURRENT;
        return row;
    }

    public boolean expired() {
        return executionStatus == ExecutionStatus.RUNNING && requestedAt.isBefore(LocalDateTime.now().minusMinutes(5));
    }

    public void finish(NtsBusinessResult result, boolean statusLookup, boolean current) {
        if (executionStatus != ExecutionStatus.RUNNING) return;
        executionStatus = result.failure() == Failure.NONE && result.authenticity() != Authenticity.UNAVAILABLE
                ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
        authenticity = result.authenticity();
        businessState = result.businessState();
        failure = result.failure();
        statusLookupPerformed = statusLookup;
        validity = current ? Validity.CURRENT : Validity.STALE;
        completedAt = LocalDateTime.now();
    }
}
