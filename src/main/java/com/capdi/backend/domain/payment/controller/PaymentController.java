package com.capdi.backend.domain.payment.controller;

import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.service.PaymentOrderService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENT')")
public class PaymentController {
    private final PaymentOrderService service;

    @PostMapping("/announcements/{announcementCode}/payments")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String announcementCode,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentOrderRequest request) {
        var result = service.create(user.getUserId(), announcementCode, idempotencyKey, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(result.response()));
    }

    @GetMapping("/payments/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> get(
            @AuthenticationPrincipal CustomUserDetails user, @PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.ok(service.get(user.getUserId(), paymentId)));
    }
}
