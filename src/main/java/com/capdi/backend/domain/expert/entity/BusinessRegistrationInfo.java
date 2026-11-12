package com.capdi.backend.domain.expert.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

@Entity
@Table(name = "expert_business_registration_infos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class BusinessRegistrationInfo extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_profile_id", nullable = false)
    private ExpertProfile expertProfile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private ExpertFile file;

    @Column(name = "business_number", nullable = false, length = 30)
    private String businessNumber;

    @Column(name = "representative_name", nullable = false, length = 100)
    private String representativeName;

    @Column(name = "company_name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "opening_date")
    private java.time.LocalDate openingDate;

    @Column(name = "input_revision", nullable = false)
    private long inputRevision;
    @Column(name = "confirmed_input_revision")
    private Long confirmedInputRevision;
    @Column(name = "confirmed_file_id")
    private Long confirmedFileId;
    @Column(name = "confirmed_ocr_revision")
    private Long confirmedOcrRevision;

    private void inputChanged() {
        inputRevision++;
        confirmedInputRevision = null;
        confirmedFileId = null;
        confirmedOcrRevision = null;
    }

    public void markConfirmed(Long fileId, long ocrRevision) {
        confirmedInputRevision = inputRevision;
        confirmedFileId = fileId;
        confirmedOcrRevision = ocrRevision;
    }

    public boolean isConfirmedFor(Long fileId, long ocrRevision) {
        return java.util.Objects.equals(confirmedInputRevision, inputRevision)
                && java.util.Objects.equals(confirmedFileId, fileId)
                && java.util.Objects.equals(confirmedOcrRevision, ocrRevision);
    }

    public void updateOpeningDate(java.time.LocalDate openingDate) {
        if (!java.util.Objects.equals(this.openingDate, openingDate)) inputChanged();
        this.openingDate = openingDate;
    }

    public void confirm(String number, String representative, String company, java.time.LocalDate date) {
        if (!java.util.Objects.equals(businessNumber, number) || !java.util.Objects.equals(representativeName, representative)
                || !java.util.Objects.equals(companyName, company) || !java.util.Objects.equals(openingDate, date)) inputChanged();
        this.businessNumber = number;
        this.representativeName = representative;
        this.companyName = company;
        this.openingDate = date;
    }

    public static BusinessRegistrationInfo create(
            ExpertProfile expertProfile,
            ExpertFile file,
            String businessNumber,
            String representativeName,
            String companyName
    ) {
        return BusinessRegistrationInfo.builder()
                .expertProfile(expertProfile)
                .file(file)
                .businessNumber(businessNumber)
                .representativeName(representativeName)
                .companyName(companyName)
                .build();
    }

    public void update(
            ExpertFile file,
            String businessNumber,
            String representativeName,
            String companyName
    ) {
        if (!java.util.Objects.equals(this.file.getId(), file.getId()) || !java.util.Objects.equals(this.businessNumber, businessNumber)
                || !java.util.Objects.equals(this.representativeName, representativeName)
                || !java.util.Objects.equals(this.companyName, companyName)) inputChanged();
        this.file = file;
        this.businessNumber = businessNumber;
        this.representativeName = representativeName;
        this.companyName = companyName;
    }
}
