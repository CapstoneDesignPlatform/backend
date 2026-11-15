package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.FinancialAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface FinancialAuditEventRepository extends JpaRepository<FinancialAuditEvent,Long>{
 List<FinancialAuditEvent> findByDocumentIdOrderByIdAsc(Long document);
 List<FinancialAuditEvent> findByPayoutIdOrderByIdAsc(Long payout);
 List<FinancialAuditEvent> findByTaxExecutionIdOrderByIdAsc(Long execution);
 List<FinancialAuditEvent> findByProfileIdOrderByIdAsc(Long profile);
}
