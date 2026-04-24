package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * ExpertProfile 데이터 접근 Repository
 * 기본 CRUD 기능은 JpaRepository에서 제공된다.
 */
public interface ExpertProfileRepository extends JpaRepository<ExpertProfile, Long> {
}