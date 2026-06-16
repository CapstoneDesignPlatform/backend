package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Long> {

    Optional<ExpertProfile> findByUserId(Long userId);
    List<ExpertProfile> findByUserIdIn(Collection<Long> userIds);
}