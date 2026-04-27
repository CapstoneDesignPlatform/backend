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

    public ExpertProfileResponse getMyProfile() {
        ExpertProfile expertProfile = getMyExpertProfile();
        return ExpertProfileResponse.from(expertProfile);
    }

    @Transactional
    public ExpertProfileResponse updateMyProfile(ExpertProfileUpdateRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile();

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

    public ExpertVerificationStatusResponse getMyVerificationStatus() {
        ExpertProfile expertProfile = getMyExpertProfile();
        return ExpertVerificationStatusResponse.from(expertProfile);
    }

    @Transactional
    public void applyVerification(ExpertVerificationRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile();

        expertProfile.applyVerification(
                request.getLicenseType(),
                request.getCompanyName(),
                request.getPortfolio()
        );
    }

    private ExpertProfile getMyExpertProfile() {
        Long loginUserId = getLoginUserId();

        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private Long getLoginUserId() {
        return 1L; // TODO: JWT 적용 후 auth context에서 가져오기
    }
}