package com.capdi.backend.domain.expert.entity;

import java.time.LocalDateTime;

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
import jakarta.persistence.Lob;

@Entity
@Table(name = "files")
@org.hibernate.annotations.DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExpertFile extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_profile_id", nullable = false)
    private ExpertProfile expertProfile;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "mime_type", nullable = false, length = 30)
    private MimeTypeEnum mimeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 30)
    private FileTypeEnum fileType;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "verification_status", nullable = false, length = 30)
    private FileVerificationStatusEnum verificationStatus = FileVerificationStatusEnum.PENDING;

    @Lob
    @Column(name = "ocr_raw_text", columnDefinition = "LONGTEXT")
    private String ocrRawText;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "ocr_status", nullable = false, length = 20)
    @Builder.Default
    private OcrStatusEnum ocrStatus = OcrStatusEnum.NOT_RUN;
    @Column(name = "ocr_run_token", length = 36)
    private String ocrRunToken;
    @Column(name = "ocr_started_at")
    private LocalDateTime ocrStartedAt;
    @Column(name = "ocr_finished_at")
    private LocalDateTime ocrFinishedAt;
    @Column(name = "ocr_revision", nullable = false)
    @Builder.Default
    private long ocrRevision = 0;
    @Column(name = "ocr_error_code", length = 50)
    private String ocrErrorCode;

    public void startOcr(String token, LocalDateTime now) {
        ocrStatus = OcrStatusEnum.RUNNING;
        ocrRunToken = token;
        ocrStartedAt = now;
        ocrFinishedAt = null;
        ocrErrorCode = null;
        ocrRevision++;
        // An earlier successful attempt is not valid input for this execution.
        ocrRawText = null;
    }

    public void finishOcr(String text, OcrStatusEnum status, String errorCode) {
        ocrRawText = status == OcrStatusEnum.SUCCEEDED ? text : null;
        ocrStatus = status;
        ocrErrorCode = errorCode;
        ocrFinishedAt = LocalDateTime.now();
        ocrRunToken = null;
    }

    public void approveVerification() {
        this.verificationStatus = FileVerificationStatusEnum.APPROVED;
        this.rejectReason = null;
    }

    public void rejectVerification(String rejectReason) {
        this.verificationStatus = FileVerificationStatusEnum.REJECTED;
        this.rejectReason = rejectReason;
    }
}
