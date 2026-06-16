package com.capdi.backend.domain.bid.dto;

import com.capdi.backend.domain.bid.entity.Bid;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ExpertSelectResponse {

    @JsonProperty("bid_id")
    private Long bidId;

    @JsonProperty("expert_name")
    private String expertName;

    @JsonProperty("bid_amount")
    private BigDecimal bidAmount;

    @JsonProperty("final_amount")
    private BigDecimal finalAmount;

    @JsonProperty("announcement_code")
    private String announcementCode;

    public static ExpertSelectResponse from(Bid bid) {
        return ExpertSelectResponse.builder()
                .bidId(bid.getId())
                .expertName(bid.getExpertUser().getName())
                .bidAmount(bid.getBidAmount())
                .finalAmount(bid.getFinalAmount())
                .announcementCode(bid.getAnnouncement().getAnnouncementCode())
                .build();
    }
}
