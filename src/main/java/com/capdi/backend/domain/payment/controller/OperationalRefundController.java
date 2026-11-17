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
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('CLIENT')")
public class OperationalRefundController {
 private final FinancialReviewService service;
 private final com.capdi.backend.domain.payment.service.FinancialWorkflowPersistenceService persistence;
 @PostMapping("/payments/{paymentId}/refunds")
 public ResponseEntity<ApiResponse<FinancialReviewResponse>> request(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody FinancialReviewRequests.Refund body){return ResponseEntity.ok(ApiResponse.ok(service.requestOperationalRefund(u.getUserId(),paymentId,key,body.amount())));}
 @GetMapping("/payments/{paymentId}/refunds/{refundId}")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> status(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@PathVariable Long refundId){return ResponseEntity.ok(ApiResponse.ok(persistence.readClientRefund(u.getUserId(),paymentId,refundId)));}
}
