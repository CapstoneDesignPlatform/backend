package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.dto.ExpertProfileUpdateRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationStatusResponse;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertProfileService {

    private final ExpertProfileRepository expertProfileRepository;

    public ExpertProfileResponse getMyProfile(Long loginUserId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        return ExpertProfileResponse.from(expertProfile);
    }

    @Transactional
    public ExpertProfileResponse updateMyProfile(Long loginUserId, ExpertProfileUpdateRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);

        String specialty = null;
        if (request.getExpertiseAreas() != null && !request.getExpertiseAreas().isEmpty()) {
            specialty = request.getExpertiseAreas().get(0);
        }

        expertProfile.getUser().updateBasicInfo(
                request.getName(),
                request.getPhone()
        );

        expertProfile.updateProfile(
                request.getCompanyName(),
                specialty,
                request.getPortfolio()
        );

        return ExpertProfileResponse.from(expertProfile);
    }

    public ExpertVerificationStatusResponse getMyVerificationStatus(Long loginUserId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        return ExpertVerificationStatusResponse.from(expertProfile);
    }

    @Transactional
    public void applyVerification(Long loginUserId, ExpertVerificationRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);

        expertProfile.applyVerification(
                request.getLicenseType(),
                request.getCompanyName(),
                request.getPortfolio()
        );
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}
