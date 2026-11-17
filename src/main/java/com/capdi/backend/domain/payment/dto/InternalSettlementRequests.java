package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public final class InternalSettlementRequests {
 private InternalSettlementRequests(){}
 public record Profile(@NotNull @JsonProperty("business_info_id") Long businessInfoId,
  @NotNull @JsonProperty("business_type") ExpertSettlementProfile.BusinessType businessType,
  @NotBlank @Pattern(regexp="virtual_[A-Za-z0-9_-]{8,80}") @JsonProperty("virtual_token") String virtualToken,
  @Min(0) @JsonProperty("expected_revision") long expectedRevision){}
 public record Request(@Min(0) @JsonProperty("balance_version") long balanceVersion,
  @Min(1) @JsonProperty("profile_revision") long profileRevision){}
 public record Decision(@NotNull SettlementReview.State decision,
  @Min(0) @JsonProperty("expected_version") long expectedVersion,
  @NotBlank @Pattern(regexp="[A-Z0-9_]{1,100}") @JsonProperty("reason_code") String reasonCode){}
}
