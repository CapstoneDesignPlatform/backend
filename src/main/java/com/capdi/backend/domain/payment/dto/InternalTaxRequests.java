package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
public final class InternalTaxRequests {
 private InternalTaxRequests(){}
 public record Item(@NotBlank @Pattern(regexp="[A-Z0-9_]{1,50}") @JsonProperty("item_code") String itemCode,
  @NotNull @Digits(integer=13,fraction=0) BigDecimal supply,@NotNull @Digits(integer=13,fraction=0) BigDecimal vat){}
 public record Draft(@NotNull PaymentTaxInvoiceDraft.Kind kind,
  @NotNull @JsonProperty("supplier_snapshot_id") Long supplierSnapshotId,@NotNull @JsonProperty("recipient_snapshot_id") Long recipientSnapshotId,
  @JsonProperty("original_document_id") Long originalDocumentId,FinancialTaxInvoice.Modification modification,
  @NotNull FinancialTaxInvoice.Taxation taxation,@NotNull @JsonProperty("write_date") LocalDate writeDate,
  @NotNull @Digits(integer=13,fraction=0) BigDecimal supply,@NotNull @Digits(integer=13,fraction=0) BigDecimal vat,
  @NotNull @Digits(integer=13,fraction=0) BigDecimal total,@NotEmpty @Size(max=100) List<@Valid Item> items){}
 public record Review(boolean approve,@Min(0) @JsonProperty("expected_version") long expectedVersion,
  @NotBlank @Pattern(regexp="[A-Z0-9_]{1,100}") @JsonProperty("reason_code") String reasonCode){}
}
