package com.capdi.backend.domain.payment.dto;

import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class PaymentResponse {
    @JsonProperty("payment_id") private Long paymentId;
    @JsonProperty("order_id") private String orderId;
    @JsonProperty("announcement_code") private String announcementCode;
    @JsonProperty("bid_id") private Long bidId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    @JsonProperty("created_at") private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder().paymentId(payment.getId()).orderId(payment.getOrderId())
                .announcementCode(payment.getAnnouncementCode()).bidId(payment.getBidId())
                .amount(payment.getAmount()).currency(payment.getCurrency()).status(payment.getStatus())
                .createdAt(payment.getCreatedAt()).build();
    }
}
