package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.SettlementAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SettlementAdjustmentRepository extends JpaRepository<SettlementAdjustment,Long> {boolean existsByOperationId(Long operation);}
