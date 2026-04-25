package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpertCertificateRepository extends JpaRepository<ExpertCertificate, Long> {

    List<ExpertCertificate> findByExpertProfileId(Long expertProfileId);
}
