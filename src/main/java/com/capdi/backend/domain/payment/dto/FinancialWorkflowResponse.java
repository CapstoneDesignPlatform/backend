package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.*;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.math.BigDecimal;
import java.time.*;

@Getter @Builder
public class FinancialWorkflowResponse {
 private Long id;
 private String type;
 @JsonProperty("provider_environment") private String providerEnvironment;
 private String state;
 private Long version;
 @JsonProperty("submit_claimed") private Boolean submitClaimed;
 @JsonProperty("approval_decision") private FinancialApproval.Decision approvalDecision;
 @JsonProperty("reason_code") private String reasonCode;
 @JsonProperty("admin_user_id") private Long adminUserId;
 @JsonProperty("balance_version") private Long balanceVersion;
 @JsonProperty("payment_id") private Long paymentId;
 @JsonProperty("provider_id") private String providerId;
 @JsonProperty("failure_code") private String failureCode;
 @JsonProperty("lookup_count") private Integer lookupCount;
 @JsonProperty("next_lookup_at") private Instant nextLookupAt;
 @JsonProperty("nts_state") private FinancialGateway.NtsState ntsState;
 @JsonProperty("account_revision") private Long accountRevision;
 @JsonProperty("masked_number") private String maskedNumber;
 @JsonProperty("kyc_verified") private Boolean kycVerified;
 private BigDecimal gross,refunded,net,platform,expert,reserved,paid,available,recoverable,amount,supply,vat,total;
 @JsonProperty("legally_issued") private Boolean legallyIssued;
 private Boolean held,issued;
 @JsonProperty("original_invoice_id") private Long originalInvoiceId;
 @JsonProperty("created_at") private LocalDateTime createdAt;
 public static FinancialWorkflowResponse operation(FinancialOperation o) {
  return builder().id(o.getId()).type(o.getKind().name()).state(o.getState().name()).version(o.getVersion())
   .paymentId(o.getPaymentId()).submitClaimed(o.isSubmitClaimed()).providerEnvironment(o.getProviderEnvironment()).providerId(o.getExternalId()).failureCode(o.getFailureCode()).lookupCount(o.getLookupCount())
   .nextLookupAt(o.getNextLookupAt()).ntsState(o.getNtsState()).createdAt(o.getCreatedAt()).build();
 }
 public static FinancialWorkflowResponse account(ExpertSettlementAccount a) {
  return builder().id(a.getId()).type("SETTLEMENT_ACCOUNT").state(a.getState().name()).version(a.getVersion())
   .accountRevision(a.getAccountRevision()).maskedNumber(a.getMaskedNumber()).kycVerified(a.isKycVerified()).createdAt(a.getCreatedAt()).build();
 }
 public static FinancialWorkflowResponse balance(SettlementBalance b) {
  return builder().id(b.getId()).type("SETTLEMENT_BALANCE").paymentId(b.getPaymentId()).version(b.getVersion())
   .gross(b.getGross()).refunded(b.getRefunded()).net(b.net()).platform(b.platform()).expert(b.expert())
   .reserved(b.getReserved()).paid(b.getPaid()).available(b.available()).recoverable(b.getRecoverable()).held(b.isHeld()).build();
 }
 public static FinancialWorkflowResponse approval(FinancialApproval a) {
  return builder().id(a.getId()).type(a.getKind().name()+"_APPROVAL").state(a.getDecision().name()).version(a.getVersion())
   .paymentId(a.getPaymentId()).amount(a.getAmount()).accountRevision(a.getAccountRevision()).createdAt(a.getCreatedAt()).reasonCode(a.getReasonCode()).adminUserId(a.getAdminUserId()).balanceVersion(a.getBalanceVersion()).approvalDecision(a.getDecision()).build();
 }
 public static FinancialWorkflowResponse invoice(FinancialTaxInvoice i) {
  return builder().id(i.getId()).type("TAX_INVOICE").version(i.getVersion()).paymentId(i.getPaymentId()).providerId(i.getExternalId())
   .originalInvoiceId(i.getOriginalInvoiceId()).issued(i.isIssued()).providerEnvironment(i.getProviderEnvironment()).legallyIssued(i.isIssued() && "PRODUCTION".equals(i.getProviderEnvironment())).ntsState(i.getNtsState()).supply(i.getSupply()).vat(i.getVat()).total(i.getTotal()).build();
 }
}
