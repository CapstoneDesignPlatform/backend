package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.service.*;
import com.capdi.backend.domain.payment.gateway.FinancialGateway;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/admin") @PreAuthorize("hasRole('ADMIN')")
public class FinancialExecutionController {
 private final PaymentExecutionService paymentExecution;
 private final FinancialWorkflowPersistenceService persistence;private final FinancialWorkflowService execution;private final FinancialGateway gateway;
 @PostMapping("/payments/{paymentId}/settlements")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> initialize(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ok(persistence.settlement(u.getUserId(),paymentId));}
 @GetMapping("/payments/{paymentId}/settlement")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> balance(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ok(persistence.readBalance(u.getUserId(),paymentId));}
 @PostMapping("/refunds/{refundId}/decisions")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> decideRefund(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long refundId,@Valid @RequestBody FinancialWorkflowRequests.RefundDecision body){return ok(persistence.decideRefund(u.getUserId(),refundId,body));}
 @PostMapping("/refunds/{refundId}/attempts")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> refund(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long refundId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody FinancialWorkflowRequests.Execute body){return ok(persistence.prepareRefund(u.getUserId(),refundId,body.approvalId(),key));}
 @PostMapping("/payments/{paymentId}/payout-approvals")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> approve(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@Valid @RequestBody FinancialWorkflowRequests.PayoutDecision body){return ok(persistence.decidePayout(u.getUserId(),paymentId,body));}
 @GetMapping("/payments/{paymentId}/payout-approvals")
 public ResponseEntity<ApiResponse<List<FinancialWorkflowResponse>>> approvals(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){boolean payoutAvailable=gateway.canSubmit(FinancialGateway.Kind.PAYOUT);return ResponseEntity.ok(ApiResponse.ok(persistence.approvalHistory(u.getUserId(),paymentId).stream().map(a->FinancialWorkflowResponse.approval(a,payoutAvailable)).toList()));}
 @PostMapping("/payments/{paymentId}/payouts")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> payout(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody FinancialWorkflowRequests.Execute body){return ok(persistence.preparePayout(u.getUserId(),paymentId,body.approvalId(),key));}
 @GetMapping("/financial-operations/{operationId}")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> read(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long operationId){return ok(persistence.readAdmin(u.getUserId(),operationId));}
 @PostMapping("/financial-operations/{operationId}/execute")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> execute(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long operationId){return ok(execution.execute(u.getUserId(),operationId));}
 @PostMapping("/financial-operations/{operationId}/reconciliations")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> reconcile(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long operationId){persistence.manualLookup(u.getUserId(),operationId);return ok(execution.reconcile(u.getUserId(),operationId));}
 @PostMapping("/financial-operations/{operationId}/holds")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> hold(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long operationId){return ok(persistence.cancelReservation(u.getUserId(),operationId));}
 @PostMapping("/billing-party-snapshots")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> party(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody FinancialWorkflowRequests.Party body){return ok(persistence.saveParty(u.getUserId(),body));}
 @PostMapping("/tax-invoice-drafts/{draftId}/invoices")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> invoice(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long draftId,@Valid @RequestBody FinancialWorkflowRequests.Invoice body){return ok(persistence.prepareInvoice(u.getUserId(),draftId,body));}
 @PostMapping("/financial-tax-invoices/{invoiceId}/attempts")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> taxAttempt(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long invoiceId,@RequestHeader(value="Idempotency-Key",required=false) String key){return ok(persistence.prepareInvoiceOperation(u.getUserId(),invoiceId,key));}
 @GetMapping("/financial-tax-invoices/{invoiceId}")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> taxRead(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long invoiceId){return ok(persistence.readInvoice(u.getUserId(),invoiceId));}
 @GetMapping("/financial-operations/{operationId}/audits")
 public ResponseEntity<ApiResponse<List<FinancialHistoryResponse.Audit>>> audits(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long operationId){return ResponseEntity.ok(ApiResponse.ok(persistence.auditHistory(u.getUserId(),operationId)));}
 @GetMapping("/payments/{paymentId}/ledger")
 public ResponseEntity<ApiResponse<List<FinancialHistoryResponse.Ledger>>> ledger(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ResponseEntity.ok(ApiResponse.ok(persistence.ledgerHistory(u.getUserId(),paymentId)));}
 @PostMapping("/payments/{paymentId}/attempts/{attemptId}/reconciliations")
 public ResponseEntity<ApiResponse<PaymentAttemptResponse>> paymentLookup(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@PathVariable Long attemptId){
  return ResponseEntity.ok(ApiResponse.ok(paymentExecution.reconcileInternal(persistence.paymentLookupTarget(u.getUserId(),paymentId,attemptId))));
 }
 private ResponseEntity<ApiResponse<FinancialWorkflowResponse>> ok(FinancialWorkflowResponse value){return ResponseEntity.ok(ApiResponse.ok(value));}
}
