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
import jakarta.persistence.Lob;

@Entity
@Table(name = "files")
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

    public void completeOcr(String ocrRawText) {
        this.ocrRawText = ocrRawText;
        this.verificationStatus = FileVerificationStatusEnum.OCR_COMPLETED;
        this.rejectReason = null;
    }

    public void failOcr(String rejectReason) {
        this.verificationStatus = FileVerificationStatusEnum.FAILED;
        this.rejectReason = rejectReason;
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
