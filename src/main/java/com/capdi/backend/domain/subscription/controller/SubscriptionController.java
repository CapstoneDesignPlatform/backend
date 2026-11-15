package com.capdi.backend.domain.subscription.controller;

import com.capdi.backend.domain.subscription.dto.SubscriptionDtos.*;
import com.capdi.backend.domain.subscription.model.SubscriptionModels.PaymentStatus;
import com.capdi.backend.domain.subscription.service.SubscriptionService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SubscriptionController {
    private final SubscriptionService service;

    @GetMapping("/subscription-plans")
    public ApiResponse<List<PlanResponse>> plans() { return ApiResponse.ok(service.plans()); }

    @PostMapping("/expert/me/subscription-registration")
    @PreAuthorize("hasRole('EXPERT')")
    public ApiResponse<RegistrationResponse> prepare(@AuthenticationPrincipal CustomUserDetails user,
                                                     @Valid @RequestBody RegistrationRequest request) {
        return ApiResponse.ok(service.prepare(user.getUserId(), request.planId()));
    }

    @PostMapping("/expert/me/subscriptions")
    @PreAuthorize("hasRole('EXPERT')")
    public ResponseEntity<ApiResponse<PaymentResponse>> create(@AuthenticationPrincipal CustomUserDetails user,
            @RequestHeader(value = "Idempotency-Key", required = false) String key,
            @Valid @RequestBody CreateRequest request) {
        CreationResult result = service.create(user.getUserId(), key, request);
        boolean pending = result.payment().status() != PaymentStatus.PAID && result.payment().status() != PaymentStatus.FAILED;
        HttpStatus status = pending ? HttpStatus.ACCEPTED : result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.ok(result.payment()));
    }

    @GetMapping("/expert/me/subscription")
    @PreAuthorize("hasRole('EXPERT')")
    public ApiResponse<CurrentResponse> current(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.ok(service.current(user.getUserId()));
    }

    @GetMapping("/expert/me/subscription/payments")
    @PreAuthorize("hasRole('EXPERT')")
    public ApiResponse<List<PaymentResponse>> payments(@AuthenticationPrincipal CustomUserDetails user,
                                                      @RequestParam(defaultValue = "1") int page) {
        return ApiResponse.ok(service.payments(user.getUserId(), page));
    }

    @GetMapping("/expert/me/subscription/payments/{orderId}")
    @PreAuthorize("hasRole('EXPERT')")
    public ApiResponse<PaymentResponse> payment(@AuthenticationPrincipal CustomUserDetails user, @PathVariable String orderId) {
        return ApiResponse.ok(service.payment(user.getUserId(), orderId));
    }
}
