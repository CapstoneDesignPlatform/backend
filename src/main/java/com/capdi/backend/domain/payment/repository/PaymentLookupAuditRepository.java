package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.PaymentLookupAudit;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentLookupAuditRepository extends JpaRepository<PaymentLookupAudit,Long>{}
