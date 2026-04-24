package com.capdi.backend.domain.bid.dto;

import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BidResponse {

    private Long id;
    private Long expertProfileId;
    private Long jobPostId;
    private Long proposedPrice;
    private String proposal;
    private BidStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BidResponse from(Bid bid) {
        return BidResponse.builder()
                .id(bid.getId())
                .expertProfileId(bid.getExpertProfile().getId())
                .jobPostId(bid.getJobPost().getId())
                .proposedPrice(bid.getProposedPrice())
                .proposal(bid.getProposal())
                .status(bid.getStatus())
                .createdAt(bid.getCreatedAt())
                .updatedAt(bid.getUpdatedAt())
                .build();
    }
}