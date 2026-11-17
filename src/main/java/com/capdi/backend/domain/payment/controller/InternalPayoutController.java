package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.service.*;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class InternalPayoutController {
 private final InternalPayoutPersistenceService persistence;private final InternalPayoutService service;
 @PostMapping("/admin/payments/{paymentId}/settlement-reviews/{reviewId}/payout-reservations") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalPayoutPersistenceService.View>> reserve(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@PathVariable Long reviewId){return ResponseEntity.ok(ApiResponse.ok(persistence.reserve(u.getUserId(),paymentId,reviewId)));}
 @PostMapping("/admin/internal-payouts/{id}/executions") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalPayoutPersistenceService.View>> execute(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(service.execute(u.getUserId(),id,false)));}
 @PostMapping("/admin/internal-payouts/{id}/reconciliations") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalPayoutPersistenceService.View>> reconcile(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(service.execute(u.getUserId(),id,true)));}
 @PostMapping("/admin/internal-payouts/{id}/cancellations") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalPayoutPersistenceService.View>> cancel(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(persistence.cancel(u.getUserId(),id)));}
 @GetMapping("/admin/payments/{paymentId}/internal-payouts") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<java.util.List<InternalPayoutPersistenceService.View>>> admin(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ResponseEntity.ok(ApiResponse.ok(persistence.list(u.getUserId(),paymentId,true)));}
 @GetMapping("/expert/me/settlements/{paymentId}/payouts") @PreAuthorize("hasRole('EXPERT')")
 public ResponseEntity<ApiResponse<java.util.List<InternalPayoutPersistenceService.View>>> expert(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ResponseEntity.ok(ApiResponse.ok(persistence.list(u.getUserId(),paymentId,false)));}
}
