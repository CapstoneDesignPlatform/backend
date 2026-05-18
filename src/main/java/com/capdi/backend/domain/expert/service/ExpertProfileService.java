package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.dto.ExpertProfileUpdateRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationStatusResponse;
import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertProfileService {

    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertCertificateRepository expertCertificateRepository;
    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;

    public ExpertProfileResponse getMyProfile(Long loginUserId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        return ExpertProfileResponse.from(expertProfile);
    }

    @Transactional
    public ExpertProfileResponse updateMyProfile(Long loginUserId, ExpertProfileUpdateRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);

        expertProfile.getUser().updateBasicInfo(
                request.getName(),
                request.getPhone()
        );

        expertProfile.updateBusinessName(request.getCompanyName());

        return ExpertProfileResponse.from(expertProfile);
    }

    public ExpertVerificationStatusResponse getMyVerificationStatus(Long loginUserId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        List<ExpertCertificate> certificates =
                expertCertificateRepository.findByExpertProfileId(expertProfile.getId());
        BusinessRegistrationInfo businessRegistrationInfo =
                businessRegistrationInfoRepository.findByExpertProfileId(expertProfile.getId())
                        .stream()
                        .max(Comparator.comparing(BusinessRegistrationInfo::getId))
                        .orElse(null);

        return ExpertVerificationStatusResponse.from(
                expertProfile,
                certificates,
                businessRegistrationInfo
        );
    }

    @Transactional
    public void applyVerification(Long loginUserId, ExpertVerificationRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);

        expertProfile.applyVerification(request.getSpecialty());
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}
