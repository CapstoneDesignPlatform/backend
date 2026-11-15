package com.capdi.backend.domain.payment.controller;

import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.service.PaymentExecutionService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('CLIENT')")
@RequestMapping("/payments/{paymentId}/attempts")
public class PaymentAttemptController {
    private final PaymentExecutionService service;
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentAttemptResponse>> prepare(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@RequestHeader(value="Idempotency-Key",required=false) String key,
            @Valid @RequestBody PaymentAttemptRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.prepare(user.getUserId(),paymentId,key,request.getMethod())));
    }
    @PostMapping("/{attemptId}/confirmations")
    public ResponseEntity<ApiResponse<PaymentAttemptResponse>> confirm(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@PathVariable Long attemptId,@Valid @RequestBody PaymentConfirmationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.confirm(user.getUserId(),paymentId,attemptId,request.getConfirmationReference())));
    }
    @PostMapping("/{attemptId}/reconciliations")
    public ResponseEntity<ApiResponse<PaymentAttemptResponse>> reconcile(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@PathVariable Long attemptId) {
        return ResponseEntity.ok(ApiResponse.ok(service.reconcile(user.getUserId(),paymentId,attemptId)));
    }
    @GetMapping("/{attemptId}")
    public ResponseEntity<ApiResponse<PaymentAttemptResponse>> read(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@PathVariable Long attemptId) {
        return ResponseEntity.ok(ApiResponse.ok(service.read(user.getUserId(),paymentId,attemptId)));
    }
}
