package com.capdi.backend.domain.payment.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @NoArgsConstructor
public class PaymentConfirmationRequest {
    @NotBlank @Size(max=200) @Pattern(regexp="[A-Za-z0-9._:-]+")
    @JsonProperty("confirmation_reference")
    private String confirmationReference;
}
