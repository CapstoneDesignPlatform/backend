package com.capdi.backend.domain.payment.controller;
import com.capdi.backend.domain.payment.service.*;
import com.capdi.backend.domain.payment.dto.SettlementProfileReviewRequest;
import com.capdi.backend.domain.payment.entity.FinancialAuditEvent;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.time.LocalDateTime;
@RestController @RequiredArgsConstructor
public class FinancialCompletionController {
 private final SettlementProfileReviewService profiles;private final InternalTaxExecutionPersistenceService taxes;
 private final InternalTaxExecutionService taxExecution;private final InternalTaxService documents;private final InternalPayoutPersistenceService payouts;
 public record Revision(@Min(0) @JsonProperty("expected_version") long expectedVersion,@jakarta.validation.constraints.NotNull @Valid com.capdi.backend.domain.payment.dto.InternalTaxRequests.Draft draft){}
 public record Version(@Min(0) @JsonProperty("expected_version") long expectedVersion){}
 public record Event(Long id,Long actor_id,String code,String state_code,String reason_code,LocalDateTime created_at){
  static Event from(FinancialAuditEvent e){return new Event(e.getId(),e.getActorId(),e.getCode(),e.getStateCode(),e.getReasonCode(),e.getCreatedAt());}
 }
 @PostMapping("/admin/settlement-profiles/{id}/reviews") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<SettlementProfileReviewService.View>> review(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody SettlementProfileReviewRequest body){return ResponseEntity.ok(ApiResponse.ok(profiles.review(u.getUserId(),id,key,body)));}
 @GetMapping("/admin/settlement-profiles/{id}/reviews") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<List<SettlementProfileReviewService.View>>> adminProfiles(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(profiles.history(u.getUserId(),id,true)));}
 @GetMapping("/expert/me/settlement-profiles/{id}/reviews") @PreAuthorize("hasRole('EXPERT')")
 public ResponseEntity<ApiResponse<List<SettlementProfileReviewService.View>>> expertProfiles(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(profiles.history(u.getUserId(),id,false)));}
 @PostMapping("/admin/tax-documents/{id}/execution-requests") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalTaxExecutionPersistenceService.View>> prepare(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Version body){return ResponseEntity.ok(ApiResponse.ok(taxes.prepare(u.getUserId(),id,key,body.expectedVersion())));}
 @PostMapping("/admin/tax-documents/{id}/revisions") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalTaxService.View>> revise(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Revision body){return ResponseEntity.ok(ApiResponse.ok(documents.revise(u.getUserId(),id,key,body.expectedVersion(),body.draft())));}
 @PostMapping("/admin/tax-documents/{id}/snapshot-refreshes") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalTaxService.View>> refresh(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id,@Valid @RequestBody Version body){return ResponseEntity.ok(ApiResponse.ok(documents.refreshSnapshot(u.getUserId(),id,body.expectedVersion())));}
 @GetMapping("/admin/tax-document-executions/{id}") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalTaxExecutionPersistenceService.View>> read(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(taxes.read(u.getUserId(),id)));}
 @PostMapping("/admin/tax-document-executions/{id}/executions") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalTaxExecutionPersistenceService.View>> execute(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(taxExecution.execute(u.getUserId(),id,false)));}
 @PostMapping("/admin/tax-document-executions/{id}/reconciliations") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<InternalTaxExecutionPersistenceService.View>> reconcile(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(taxExecution.execute(u.getUserId(),id,true)));}
 @GetMapping("/admin/tax-document-executions/{id}/events") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<List<Event>>> taxEvents(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(taxes.history(u.getUserId(),id).stream().map(Event::from).toList()));}
 @GetMapping("/admin/tax-documents/{id}/events") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<List<Event>>> documentEvents(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(documents.history(u.getUserId(),id).stream().map(Event::from).toList()));}
 @GetMapping("/admin/internal-payouts/{id}/events") @PreAuthorize("hasRole('ADMIN')")
 public ResponseEntity<ApiResponse<List<InternalPayoutPersistenceService.Event>>> payoutEvents(@AuthenticationPrincipal CustomUserDetails u,@PathVariable Long id){return ResponseEntity.ok(ApiResponse.ok(payouts.history(u.getUserId(),id)));}
}
