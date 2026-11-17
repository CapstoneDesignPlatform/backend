package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.FinancialOperationAudit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialOperationAuditRepository extends JpaRepository<FinancialOperationAudit,Long>{ java.util.List<FinancialOperationAudit> findByOperationIdOrderByIdAsc(Long operation);}
