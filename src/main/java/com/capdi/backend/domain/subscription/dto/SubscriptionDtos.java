package com.capdi.backend.domain.subscription.dto;

import com.capdi.backend.domain.expert.entity.MembershipGradeEnum;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class SubscriptionDtos {
    private SubscriptionDtos() { }

    public record RegistrationRequest(@NotNull @Positive @JsonProperty("plan_id") Long planId) { }

    public record CreateRequest(@NotNull @Positive @JsonProperty("plan_id") Long planId,
                                @NotBlank @Size(max = 36) @JsonProperty("registration_id") String registrationId,
                                @NotBlank @Size(max = 4096) @JsonProperty("registration_token") String registrationToken) {
        @Override public String toString() { return "CreateRequest[redacted]"; }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PlanResponse(long planId, MembershipGradeEnum grade, int version, BigDecimal amount,
                               String currency, int periodMonths) {
        public static PlanResponse from(Plan p) {
            return new PlanResponse(p.id(), p.grade(), p.version(), p.amount(), p.currency(), p.periodMonths());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegistrationResponse(String registrationId, String customerRef, String provider,
                                       LocalDateTime expiresAt, PlanResponse plan) { }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record PaymentResponse(String orderId, long subscriptionId, MembershipGradeEnum grade,
                                  int planVersion, BigDecimal amount, String currency, PaymentStatus status,
                                  String failureCode, LocalDateTime paidAt, LocalDateTime periodStart, LocalDateTime periodEnd) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.orderId(), p.subscriptionId(), p.grade(), p.planVersion(), p.amount(),
                    p.currency(), p.status(), p.failureCode(), p.paidAt(), p.periodStart(), p.periodEnd());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CurrentResponse(MembershipGradeEnum membershipGrade, Long subscriptionId, Status status,
                                  LocalDateTime periodStart, LocalDateTime periodEnd,
                                  LocalDateTime nextBillingAt, boolean autoRenew) { }

    public record CreationResult(PaymentResponse payment, boolean created) { }
}
