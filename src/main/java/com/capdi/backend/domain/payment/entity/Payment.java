package com.capdi.backend.domain.payment.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_bid", columnNames = "bid_id"),
        @UniqueConstraint(name = "uk_payments_order", columnNames = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false, length = 40, updatable = false)
    private String orderId;
    // Immutable financial references; SQL FKs prohibit deleting their source rows.
    @Column(name = "bid_id", nullable = false, updatable = false)
    private Long bidId;
    @Column(name = "announcement_id", nullable = false, updatable = false)
    private Long announcementId;
    @Column(name = "announcement_code", nullable = false, length = 50, updatable = false)
    private String announcementCode;
    @Column(name = "client_user_id", nullable = false, updatable = false)
    private Long clientUserId;
    @Column(name = "expert_profile_id", nullable = false, updatable = false)
    private Long expertProfileId;
    @Column(nullable = false, precision = 15, scale = 2, updatable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private PaymentMethod method;
    @Column(name = "fee_policy_version", nullable = false, length = 50, updatable = false)
    private String feePolicyVersion;
    @Column(name = "platform_fee_rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal platformFeeRate;
    @Column(name = "expert_share_rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal expertShareRate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;
    @Version @Column(nullable = false)
    private long version;

    public static Payment create(Long bidId, Long announcementId, String announcementCode, Long clientUserId,
                                 Long expertProfileId, BigDecimal amount, PaymentMethod method) {
        if (bidId == null || announcementId == null || announcementCode == null || clientUserId == null
                || expertProfileId == null || method == null || amount == null || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0 || amount.compareTo(new BigDecimal("9999999999999")) > 0) {
            throw new IllegalArgumentException("Invalid payment snapshot");
        }
        Payment payment = new Payment();
        payment.orderId = "pay_" + UUID.randomUUID();
        payment.bidId = bidId;
        payment.announcementId = announcementId;
        payment.announcementCode = announcementCode;
        payment.clientUserId = clientUserId;
        payment.expertProfileId = expertProfileId;
        payment.amount = amount.setScale(2);
        payment.currency = "KRW";
        payment.method = method;
        // Allocation ratios only. Neither VAT nor PG charges are deducted.
        payment.feePolicyVersion = "BASE_ALLOCATION_10_90_V1";
        payment.platformFeeRate = new BigDecimal("0.1000");
        payment.expertShareRate = new BigDecimal("0.9000");
        payment.status = PaymentStatus.CREATED;
        return payment;
    }
}
