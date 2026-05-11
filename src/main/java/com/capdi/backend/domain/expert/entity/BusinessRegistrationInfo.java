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
}
