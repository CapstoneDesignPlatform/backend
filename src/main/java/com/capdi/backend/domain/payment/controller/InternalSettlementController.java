package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.dto.InternalSettlementRequests;
import com.capdi.backend.domain.payment.service.InternalSettlementService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor
public class InternalSettlementController {
 private final InternalSettlementService service;
 private <T> ResponseEntity<ApiResponse<T>> ok(T value){return ResponseEntity.ok(ApiResponse.ok(value));}
 @PostMapping("/expert/me/settlement-profiles") @PreAuthorize("hasRole('EXPERT')")
 public ResponseEntity<ApiResponse<InternalSettlementService.ProfileView>> profile(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody InternalSettlementRequests.Profile b){return ok(service.saveProfile(u.getUserId(),b));}
 @GetMapping("/expert/me/settlement-profiles") @PreAuthorize("hasRole('EXPERT')")
 public ResponseEntity<ApiResponse<java.util.List<InternalSettlementService.ProfileView>>> profiles(@AuthenticationPrincipal CustomUserDetails u){return ok(service.profileHistory(u.getUserId()));}
 @GetMapping("/expert/me/settlements") @PreAuthorize("hasRole('EXPERT')")
 public ResponseEntity<ApiResponse<java.util.List<InternalSettlementService.BalanceView>>> expertList(@AuthenticationPrincipal CustomUserDetails u,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(service.list(u.getUserId(),false,page,size));}
 @GetMapping("/expert/me/settlements/{paymentId}") @PreAuthorize("hasRole('EXPERT')")
 public ResponseEntity<ApiResponse<InternalSettlementService.BalanceView>> expertRead(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ok(service.read(u.getUserId(),paymentId,false));}
 @GetMapping("/admin/settlements") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<java.util.List<InternalSettlementService.BalanceView>>> adminList(@AuthenticationPrincipal CustomUserDetails u,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ok(service.list(u.getUserId(),true,page,size));}
 @PostMapping("/admin/payments/{paymentId}/settlement-reviews") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalSettlementService.ReviewView>> request(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody InternalSettlementRequests.Request b){return ok(service.request(u.getUserId(),paymentId,key,b));}
 @GetMapping("/admin/payments/{paymentId}/settlement-reviews") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<java.util.List<InternalSettlementService.ReviewView>>> history(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ok(service.history(u.getUserId(),paymentId));}
 @PostMapping("/admin/payments/{paymentId}/settlement-reviews/{reviewId}/decisions") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalSettlementService.ReviewView>> decide(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@PathVariable Long reviewId,@Valid @RequestBody InternalSettlementRequests.Decision b){return ok(service.decide(u.getUserId(),paymentId,reviewId,b));}
}
