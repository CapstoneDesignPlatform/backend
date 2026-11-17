package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.math.BigDecimal;

/** Browser SDK parameters, not a payment success response. Contains no provider secret. */
@Getter @Builder
public class PaymentCheckoutResponse {
    @JsonProperty("store_id") private String storeId;
    @JsonProperty("channel_key") private String channelKey;
    @JsonProperty("payment_id") private String paymentId;
    @JsonProperty("order_name") private String orderName;
    @JsonProperty("total_amount") private BigDecimal totalAmount;
    private String currency;
    @JsonProperty("pay_method") private PaymentMethod payMethod;
    private String environment;
}
