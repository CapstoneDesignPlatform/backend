package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.service.FinancialReviewService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class PaymentFinancialReviewController {
    private final FinancialReviewService service;
    @PostMapping("/payments/{paymentId}/refund-requests") @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> refund(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody FinancialReviewRequests.Refund body) {
        return ResponseEntity.ok(ApiResponse.ok(service.requestRefund(user.getUserId(),paymentId,key,body.amount())));
    }
    @GetMapping("/payments/{paymentId}/refund-requests/{refundId}") @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> refundRead(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@PathVariable Long refundId) {
        return ResponseEntity.ok(ApiResponse.ok(service.readRefund(user.getUserId(),paymentId,refundId)));
    }
    @PutMapping("/expert/me/seller-draft") @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> seller(@AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody FinancialReviewRequests.Seller body) {
        return ResponseEntity.ok(ApiResponse.ok(service.saveSeller(user.getUserId(),body.businessInfoId())));
    }
    @GetMapping("/expert/me/seller-draft") @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> sellerRead(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.ok(service.readSeller(user.getUserId())));
    }
    @PostMapping("/admin/payments/{paymentId}/settlement-drafts") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> settlement(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(service.createSettlement(user.getUserId(),paymentId)));
    }
    @GetMapping("/admin/settlement-drafts/{settlementId}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> settlementRead(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long settlementId) {
        return ResponseEntity.ok(ApiResponse.ok(service.readSettlement(user.getUserId(),settlementId)));
    }
    @PostMapping("/admin/settlement-drafts/{settlementId}/tax-invoice-drafts") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> tax(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long settlementId,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody FinancialReviewRequests.Tax body) {
        return ResponseEntity.ok(ApiResponse.ok(service.createTax(user.getUserId(),settlementId,key,body.kind(),body.originalDraftId())));
    }
    @GetMapping("/admin/tax-invoice-drafts/{invoiceId}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<FinancialReviewResponse>> taxRead(@AuthenticationPrincipal CustomUserDetails user,@PathVariable Long invoiceId) {
        return ResponseEntity.ok(ApiResponse.ok(service.readTax(user.getUserId(),invoiceId)));
    }
    @PostMapping("/admin/settlement-drafts/{settlementId}/payout-approvals") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> payout(@AuthenticationPrincipal CustomUserDetails user,@PathVariable Long settlementId) {
        service.approvePayout(user.getUserId(),settlementId);return ResponseEntity.ok(ApiResponse.ok(null));
    }
    @PostMapping("/admin/tax-invoice-drafts/{invoiceId}/issue") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> issue(@AuthenticationPrincipal CustomUserDetails user,@PathVariable Long invoiceId) {
        service.issueTax(user.getUserId(),invoiceId);return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
