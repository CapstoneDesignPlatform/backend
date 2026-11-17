package com.capdi.backend.domain.payment.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.math.BigDecimal;

/** Shared draft projection: null amounts have no finalized tax or payout meaning. */
@Getter @Builder
public class FinancialReviewResponse {
    private Long id;
    private Long version;
    private String type;
    private String state;
    @JsonProperty("payment_id") private Long paymentId;
    private BigDecimal gross;
    @JsonProperty("platform_amount") private BigDecimal platformAmount;
    @JsonProperty("expert_amount") private BigDecimal expertAmount;
    @JsonProperty("snapshot_current") private Boolean snapshotCurrent;
    @JsonProperty("external_executed") private boolean externalExecuted;
}
