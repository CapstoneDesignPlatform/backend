package com.capdi.backend.domain.payment.repository;

import com.capdi.backend.domain.payment.entity.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
}
