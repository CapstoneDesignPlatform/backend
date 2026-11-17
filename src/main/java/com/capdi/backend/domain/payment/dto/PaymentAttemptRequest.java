package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.*;
@Getter @NoArgsConstructor
public class PaymentAttemptRequest { @NotNull private PaymentMethod method; }
