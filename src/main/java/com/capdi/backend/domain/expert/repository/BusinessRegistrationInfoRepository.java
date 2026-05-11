package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessRegistrationInfoRepository extends JpaRepository<BusinessRegistrationInfo, Long> {

    List<BusinessRegistrationInfo> findByExpertProfileId(Long expertProfileId);
}
