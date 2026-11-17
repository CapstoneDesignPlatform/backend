package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Builder
public class PaymentAttemptResponse {
    @JsonProperty("attempt_id") private Long attemptId;
    @JsonProperty("payment_id") private Long paymentId;
    @JsonProperty("attempt_key") private String attemptKey;
    private PaymentAttemptStatus status;
    @JsonProperty("payment_status") private PaymentStatus paymentStatus;
    private PaymentMethod method;
    private PaymentProcessingFailure failure;
    @JsonProperty("created_at") private LocalDateTime createdAt;
    public static PaymentAttemptResponse from(PaymentAttempt a, Payment p) {
        return PaymentAttemptResponse.builder().attemptId(a.getId()).paymentId(p.getId()).attemptKey(a.getAttemptKey())
                .status(a.getStatus()).paymentStatus(p.getStatus()).method(a.getMethod())
                .failure(a.getFailure()).createdAt(a.getCreatedAt()).build();
    }
}
