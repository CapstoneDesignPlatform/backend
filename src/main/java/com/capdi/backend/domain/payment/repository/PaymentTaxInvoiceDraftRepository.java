package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.PaymentTaxInvoiceDraft;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentTaxInvoiceDraftRepository extends JpaRepository<PaymentTaxInvoiceDraft,Long> { java.util.Optional<PaymentTaxInvoiceDraft> findBySettlementIdAndRequestKeyHash(Long settlement,String key);
    java.util.List<PaymentTaxInvoiceDraft> findBySettlementId(Long settlement); }
