package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.FinancialTaxInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
public interface FinancialTaxInvoiceRepository extends JpaRepository<FinancialTaxInvoice,Long> { java.util.Optional<FinancialTaxInvoice> findByIssuanceScopeKey(String key);java.util.Optional<FinancialTaxInvoice> findByDraftId(Long draft);java.util.Optional<FinancialTaxInvoice> findByInternalDocumentId(Long document);}
