package com.capdi.backend.domain.payment.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_idempotency_keys", uniqueConstraints =
        @UniqueConstraint(name = "uk_payment_idempotency_client_key", columnNames = {"client_user_id", "key_hash"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentIdempotencyKey extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "client_user_id", nullable = false, updatable = false)
    private Long clientUserId;
    @Column(name = "key_hash", nullable = false, length = 64, updatable = false)
    private String keyHash;
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    public static PaymentIdempotencyKey bind(Long userId, String keyHash, String requestHash, Payment payment) {
        PaymentIdempotencyKey binding = new PaymentIdempotencyKey();
        binding.clientUserId = userId;
        binding.keyHash = keyHash;
        binding.requestHash = requestHash;
        binding.payment = payment;
        return binding;
    }
}
