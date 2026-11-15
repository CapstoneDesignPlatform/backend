package com.capdi.backend.domain.payment.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "payment_attempts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_attempt_key", columnNames = "attempt_key"),
        @UniqueConstraint(name = "uk_payment_attempt_number", columnNames = {"payment_id", "attempt_number"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAttempt extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;
    @Column(name = "attempt_key", nullable = false, length = 36, updatable = false)
    private String attemptKey;
    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentAttemptStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private PaymentProvider provider;
    @Version @Column(nullable = false)
    private long version;

    public static PaymentAttempt prepare(Payment payment, int number) {
        if (payment == null || payment.getId() == null || number < 1) {
            throw new IllegalArgumentException("Invalid payment attempt");
        }
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.payment = payment;
        attempt.attemptKey = UUID.randomUUID().toString();
        attempt.attemptNumber = number;
        attempt.status = PaymentAttemptStatus.PREPARED;
        attempt.provider = PaymentProvider.DISABLED;
        return attempt;
    }
}
