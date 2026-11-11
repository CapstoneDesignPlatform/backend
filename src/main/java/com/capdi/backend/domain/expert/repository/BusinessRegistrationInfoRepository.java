package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessRegistrationInfoRepository extends JpaRepository<BusinessRegistrationInfo, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from BusinessRegistrationInfo b where b.id = :id")
    java.util.Optional<BusinessRegistrationInfo> findForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    List<BusinessRegistrationInfo> findByExpertProfileId(Long expertProfileId);

    boolean existsByFile_Id(Long fileId);
}
