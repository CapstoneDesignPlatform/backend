package com.capdi.backend.domain.payment.repository;
import com.capdi.backend.domain.payment.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt,Long> {
    Optional<PaymentAttempt> findByPaymentIdAndRequestKeyHash(Long paymentId,String hash);
    Optional<PaymentAttempt> findByAttemptKey(String key);
    Optional<PaymentAttempt> findFirstByPaymentIdOrderByAttemptNumberDesc(Long paymentId);
    List<PaymentAttempt> findByPaymentIdAndStatusIn(Long paymentId,Collection<PaymentAttemptStatus> statuses);
}
