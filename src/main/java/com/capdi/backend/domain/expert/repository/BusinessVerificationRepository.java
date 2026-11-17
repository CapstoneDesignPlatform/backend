package com.capdi.backend.domain.expert.repository;

import com.capdi.backend.domain.expert.entity.BusinessVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface BusinessVerificationRepository extends JpaRepository<BusinessVerification, Long> {
    List<BusinessVerification> findByBusinessRegistrationInfoIdAndExecutionStatus(Long infoId, BusinessVerification.ExecutionStatus status);
    Optional<BusinessVerification> findFirstByBusinessRegistrationInfoIdOrderByIdDesc(Long infoId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from BusinessVerification v where v.executionId = :executionId and v.businessRegistrationInfoId = :infoId")
    Optional<BusinessVerification> findForUpdate(@Param("infoId") Long infoId, @Param("executionId") String executionId);
}
