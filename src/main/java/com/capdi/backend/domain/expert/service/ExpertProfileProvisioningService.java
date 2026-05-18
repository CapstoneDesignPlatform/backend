package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertProfileProvisioningService {

    private final ExpertProfileRepository expertProfileRepository;

    @Transactional
    public void createDefaultProfile(User user, String businessName) {
        if (expertProfileRepository.findByUserId(user.getId()).isPresent()) {
            return;
        }

        expertProfileRepository.save(ExpertProfile.createDefault(user, businessName));
    }
}
