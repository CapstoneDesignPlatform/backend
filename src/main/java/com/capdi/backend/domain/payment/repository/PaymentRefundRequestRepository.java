package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.PaymentRefundRequest;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentRefundRequestRepository extends JpaRepository<PaymentRefundRequest,Long> { java.util.Optional<PaymentRefundRequest> findByPaymentIdAndRequestKeyHash(Long payment,String key);
    java.util.List<PaymentRefundRequest> findByPaymentId(Long payment); }
