package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.service.InternalTaxService;
import com.capdi.backend.domain.payment.dto.InternalTaxRequests;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/admin")
public class InternalTaxController {
 private final InternalTaxService service;
 @PostMapping("/payments/{paymentId}/tax-documents")
 public ResponseEntity<ApiResponse<InternalTaxService.View>> create(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody InternalTaxRequests.Draft body){return ResponseEntity.ok(ApiResponse.ok(service.create(u.getUserId(),paymentId,key,body)));}
 @GetMapping("/payments/{paymentId}/tax-documents")
 public ResponseEntity<ApiResponse<java.util.List<InternalTaxService.View>>> list(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long paymentId){return ResponseEntity.ok(ApiResponse.ok(service.list(u.getUserId(),paymentId)));}
 @GetMapping("/tax-documents/{id}")
 public ResponseEntity<ApiResponse<InternalTaxService.View>> read(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(service.read(u.getUserId(),id)));}
 @PostMapping("/tax-documents/{id}/reviews")
 public ResponseEntity<ApiResponse<InternalTaxService.View>> review(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody InternalTaxRequests.Review body){return ResponseEntity.ok(ApiResponse.ok(service.review(u.getUserId(),id,body)));}
}
