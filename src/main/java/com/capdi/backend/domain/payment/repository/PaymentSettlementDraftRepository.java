package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.PaymentSettlementDraft;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentSettlementDraftRepository extends JpaRepository<PaymentSettlementDraft,Long> { java.util.Optional<PaymentSettlementDraft> findByPaymentId(Long payment); }
