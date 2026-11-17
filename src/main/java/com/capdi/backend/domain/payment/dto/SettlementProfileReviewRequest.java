package com.capdi.backend.domain.payment.dto;
import com.capdi.backend.domain.payment.entity.SettlementProfileReview;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
public record SettlementProfileReviewRequest(
 @Min(1) @JsonProperty("profile_revision") long profileRevision,
 @Positive @JsonProperty("expected_review_id") Long expectedReviewId,
 @NotNull SettlementProfileReview.Decision decision,@NotNull SettlementProfileReview.Documents documents,
 @NotBlank @Pattern(regexp="[A-Z0-9_]{1,100}") @JsonProperty("reason_code") String reasonCode) {}
