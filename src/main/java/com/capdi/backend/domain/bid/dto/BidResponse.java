package com.capdi.backend.domain.bid.dto;

import com.capdi.backend.domain.bid.entity.Bid;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class BidResponse {

    private Long id;

    @JsonProperty("announcement_code")
    private String announcementCode;

    @JsonProperty("bid_amount")
    private BigDecimal bidAmount;

    private String status;

    @JsonProperty("submitted_at")
    private LocalDateTime submittedAt;

    @JsonProperty("total_bid_count")
    private long totalBidCount;

    public static BidResponse from(Bid bid, long totalBidCount) {
        return BidResponse.builder()
                .id(bid.getId())
                .announcementCode(bid.getAnnouncement().getAnnouncementCode())
                .bidAmount(bid.getBidAmount())
                .status(bid.getStatus().name())
                .submittedAt(bid.getSubmittedAt())
                .totalBidCount(totalBidCount)
                .build();
    }
}
