package com.capdi.backend.domain.payment.dto;

import com.capdi.backend.domain.payment.entity.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentOrderRequest {
    @NotNull @Positive @JsonProperty("bid_id")
    private Long bidId;
    @NotNull
    private PaymentMethod method;
}
