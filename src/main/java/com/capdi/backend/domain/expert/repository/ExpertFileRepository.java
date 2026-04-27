package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpertFileRepository extends JpaRepository<ExpertFile, Long> {
}