package com.capdi.backend.domain.subscription.model;

import com.capdi.backend.domain.expert.entity.MembershipGradeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SubscriptionModels {
    private SubscriptionModels() { }

    public enum Status { REGISTERING, PENDING_PAYMENT, ACTIVE, FAILED, EXPIRED }
    public enum PaymentStatus { REGISTERING, READY, PROCESSING, PAID, FAILED, UNKNOWN }

    public record Plan(long id, MembershipGradeEnum grade, int version, BigDecimal amount,
                       String currency, int periodMonths, boolean active) { }

    public record Subscription(long id, long expertProfileId, long planId, String registrationId,
                               String customerRef, String provider, LocalDateTime registrationExpiresAt,
                               String encryptedBillingKey, Status status, LocalDateTime periodStart,
                               LocalDateTime periodEnd, LocalDateTime nextBillingAt, boolean autoRenew) {
        @Override public String toString() { return "Subscription[id=" + id + ", status=" + status + "]"; }
    }

    public record Payment(long id, long subscriptionId, long expertUserId, String orderId,
                          String requestHash, MembershipGradeEnum grade, int planVersion,
                          BigDecimal amount, String currency, int periodMonths, PaymentStatus status,
                          String provider, String providerPaymentId, String failureCode,
                          LocalDateTime paidAt, LocalDateTime periodStart, LocalDateTime periodEnd) { }

    public record Prepared(Payment payment, Subscription subscription, boolean execute) { }
}
