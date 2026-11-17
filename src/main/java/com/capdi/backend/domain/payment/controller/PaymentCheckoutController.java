package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.dto.PaymentCheckoutResponse;
import com.capdi.backend.domain.payment.service.PaymentCheckoutService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('CLIENT')")
@RequestMapping("/payments/{paymentId}/attempts/{attemptId}/checkout")
public class PaymentCheckoutController {
    private final PaymentCheckoutService service;
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentCheckoutResponse>> read(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long paymentId,@PathVariable Long attemptId) {
        return ResponseEntity.ok(ApiResponse.ok(service.read(user.getUserId(),paymentId,attemptId)));
    }
}
