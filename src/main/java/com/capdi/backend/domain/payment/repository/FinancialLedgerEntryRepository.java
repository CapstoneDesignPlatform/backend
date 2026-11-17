package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.FinancialLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialLedgerEntryRepository extends JpaRepository<FinancialLedgerEntry,Long> {java.util.List<FinancialLedgerEntry> findByPaymentId(Long payment);}
