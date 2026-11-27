package com.capdi.backend.domain.payment.entity;

import com.capdi.backend.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payout_compatibility_links")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayoutCompatibilityLink extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;
    @Column(name = "review_id", nullable = false, unique = true, updatable = false)
    private Long reviewId;
    @Column(name = "approval_id", nullable = false, unique = true, updatable = false)
    private Long approvalId;
    @Column(name = "operation_id", unique = true)
    private Long operationId;

    public static PayoutCompatibilityLink create(Long paymentId, Long reviewId, Long approvalId, Long operationId) {
        var link = new PayoutCompatibilityLink();
        link.paymentId = paymentId;
        link.reviewId = reviewId;
        link.approvalId = approvalId;
        link.operationId = operationId;
        return link;
    }

    public void attachOperation(Long operationId) {
        if (this.operationId != null && !this.operationId.equals(operationId))
            throw new IllegalStateException("Payout compatibility identity cannot be replaced");
        this.operationId = operationId;
    }
}
