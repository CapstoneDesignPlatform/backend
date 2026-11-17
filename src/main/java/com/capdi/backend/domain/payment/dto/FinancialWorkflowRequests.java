package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class FinancialWorkflowRequests {
 private FinancialWorkflowRequests(){}
 public record Account(@NotBlank @Pattern(regexp="[A-Z0-9_]{2,40}") String bank,
   @NotBlank @Pattern(regexp="[0-9]{6,30}") String number,@NotBlank @Size(max=100) String holder,
   @NotBlank @Size(max=100) String contactName,@NotBlank @Email @Size(max=200) String contactEmail,
   @NotBlank @Pattern(regexp="[0-9+ -]{8,30}") String contactPhone) {
  @Override public String toString(){return "Account[REDACTED]";}
 }
 public record Enrollment(@JsonProperty("account_id") @NotNull @Positive Long accountId,
   @JsonProperty("expected_seller_version") @NotNull @PositiveOrZero Long expectedSellerVersion){}
 public record RefundDecision(@NotNull FinancialApproval.Decision decision,
   @JsonProperty("expected_refund_version") @NotNull @PositiveOrZero Long expectedRefundVersion,
   @JsonProperty("reason_code") @NotBlank @Pattern(regexp="[A-Z0-9_]{1,100}") String reason){}
 public record PayoutDecision(@NotNull FinancialApproval.Decision decision,
   @JsonProperty("expected_balance_version") @NotNull @PositiveOrZero Long expectedBalanceVersion,
   @JsonProperty("account_id") @Positive Long accountId,
   @JsonProperty("expected_account_revision") @Positive Long expectedAccountRevision,
   @NotNull @Positive @Digits(integer=13,fraction=0) BigDecimal amount,
   @JsonProperty("reason_code") @NotBlank @Pattern(regexp="[A-Z0-9_]{1,100}") String reason){}
 public record Execute(@JsonProperty("approval_id") @NotNull @Positive Long approvalId){}
 public record Party(@NotNull BillingPartySnapshot.Role role,
   @JsonProperty("subject_user_id") @Positive Long subjectUserId,
   @NotBlank @Pattern(regexp="[0-9]{10}") String brn,@NotBlank @Size(max=100) String name,
   @NotBlank @Size(max=100) String representativeName,@NotBlank @Email @Size(max=200) String email) {
  @Override public String toString(){return "Party[REDACTED]";}
 }
 public record Invoice(@JsonProperty("supplier_snapshot_id") @NotNull @Positive Long supplierSnapshotId,
   @JsonProperty("recipient_snapshot_id") @NotNull @Positive Long recipientSnapshotId,
   @NotNull @Digits(integer=13,fraction=0) BigDecimal supply,
   @NotNull @Digits(integer=13,fraction=0) BigDecimal vat,
   @NotNull FinancialTaxInvoice.Taxation taxation,
   @JsonProperty("write_date") @NotNull @PastOrPresent LocalDate writeDate,
   @JsonProperty("original_invoice_id") @Positive Long originalInvoiceId,
   FinancialTaxInvoice.Modification modification){}
}
