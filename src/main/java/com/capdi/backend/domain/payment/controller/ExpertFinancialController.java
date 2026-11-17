package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.dto.*;
import com.capdi.backend.domain.payment.service.FinancialWorkflowPersistenceService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @PreAuthorize("hasRole('EXPERT')") @RequestMapping("/expert/me")
public class ExpertFinancialController {
 private final FinancialWorkflowPersistenceService service;
 @PostMapping("/settlement-accounts")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> account(@AuthenticationPrincipal CustomUserDetails u,@Valid @RequestBody FinancialWorkflowRequests.Account body){return ResponseEntity.ok(ApiResponse.ok(service.saveAccount(u.getUserId(),body)));}
 @GetMapping("/settlement-accounts/{accountId}")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> read(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long accountId){return ResponseEntity.ok(ApiResponse.ok(service.readAccount(u.getUserId(),accountId)));}
 @PostMapping("/partner-enrollments")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> enroll(@AuthenticationPrincipal CustomUserDetails u,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody FinancialWorkflowRequests.Enrollment body){return ResponseEntity.ok(ApiResponse.ok(service.preparePartner(u.getUserId(),body,key)));}
 @GetMapping("/partner-enrollments/{operationId}")
 public ResponseEntity<ApiResponse<FinancialWorkflowResponse>> status(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long operationId){return ResponseEntity.ok(ApiResponse.ok(service.readPartner(u.getUserId(),operationId)));}
 @GetMapping("/settlement-accounts")
 public ResponseEntity<ApiResponse<java.util.List<FinancialWorkflowResponse>>> history(@AuthenticationPrincipal CustomUserDetails u){return ResponseEntity.ok(ApiResponse.ok(service.accountHistory(u.getUserId())));}
}
