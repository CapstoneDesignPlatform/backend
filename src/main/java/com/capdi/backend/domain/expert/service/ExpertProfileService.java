package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertProfileCreateRequest;
import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertProfileService {

    private final ExpertProfileRepository expertProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public ExpertProfileResponse createExpertProfile(ExpertProfileCreateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ExpertProfile expertProfile = ExpertProfile.builder()
                .user(user)
                .officeName(request.getOfficeName())
                .specialty(request.getSpecialty())
                .careerYears(request.getCareerYears())
                .introduction(request.getIntroduction())
                .build();

        return ExpertProfileResponse.from(expertProfileRepository.save(expertProfile));
    }

    public List<ExpertProfileResponse> getExpertProfiles() {
        return expertProfileRepository.findAll()
                .stream()
                .map(ExpertProfileResponse::from)
                .toList();
    }

    public ExpertProfileResponse getExpertProfile(Long expertProfileId) {
        return expertProfileRepository.findById(expertProfileId)
                .map(ExpertProfileResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}