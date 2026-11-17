package com.capdi.backend.domain.bid.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@NoArgsConstructor
public class BidCreateRequest {

    @NotNull(message = "입찰 금액은 필수입니다.")
    @Positive(message = "입찰 금액은 0보다 커야 합니다.")
    @Digits(integer = 13, fraction = 2, message = "입찰 금액은 정수 13자리, 소수 2자리까지 입력할 수 있습니다.")
    @JsonProperty("bid_amount")
    private BigDecimal bidAmount;
}
