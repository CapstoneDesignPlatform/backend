package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpertFileRepository extends JpaRepository<ExpertFile, Long> {

    List<ExpertFile> findByExpertProfileId(Long expertProfileId);
}
