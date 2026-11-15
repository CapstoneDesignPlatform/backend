package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class FinancialHistoryResponse {
 private FinancialHistoryResponse(){}
 public record Audit(Long id,@JsonProperty("actor_id") Long actorId,String action,@JsonProperty("state_code") String stateCode,
   @JsonProperty("created_at") LocalDateTime createdAt){
  public static Audit from(FinancialOperationAudit a){return new Audit(a.getId(),a.getActorId(),a.getAction(),a.getStateCode(),a.getCreatedAt());}
 }
 public record Ledger(Long id,@JsonProperty("operation_id") Long operationId,@JsonProperty("movement_key") String movementKey,
   FinancialLedgerEntry.Bucket bucket,@JsonProperty("signed_amount") BigDecimal signedAmount,@JsonProperty("created_at") LocalDateTime createdAt) {
  public static Ledger from(FinancialLedgerEntry l){return new Ledger(l.getId(),l.getOperationId(),l.getMovementKey(),l.getBucket(),l.getSignedAmount(),l.getCreatedAt());}
 }
}
