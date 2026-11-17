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
public class ExpertSelectRequest {

    @NotNull(message = "최종 금액은 필수입니다.")
    @Positive(message = "최종 금액은 양수여야 합니다.")
    @Digits(integer = 13, fraction = 2, message = "최종 금액은 정수 13자리, 소수 2자리까지 입력할 수 있습니다.")
    @JsonProperty("final_amount")
    private BigDecimal finalAmount;
}
