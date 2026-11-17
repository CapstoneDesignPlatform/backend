package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.PaymentTaxInvoiceDraft;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public final class FinancialReviewRequests {
    private FinancialReviewRequests(){}
    public record Refund(@NotNull @Positive @Digits(integer=13,fraction=2) java.math.BigDecimal amount){}
    public record Seller(@JsonProperty("business_registration_info_id") @NotNull @Positive Long businessInfoId){}
    public record Tax(@NotNull PaymentTaxInvoiceDraft.Kind kind,
                      @JsonProperty("original_draft_id") @Positive Long originalDraftId){}
}
