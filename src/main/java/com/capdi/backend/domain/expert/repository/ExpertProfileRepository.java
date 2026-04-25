package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Long> {
}
