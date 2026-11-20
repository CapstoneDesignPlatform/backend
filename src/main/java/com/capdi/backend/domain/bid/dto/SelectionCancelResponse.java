package com.capdi.backend.domain.bid.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SelectionCancelResponse(
        @JsonProperty("announcement_code") String announcementCode,
        @JsonProperty("bid_id") Long bidId,
        @JsonProperty("selection_round") long selectionRound,
        @JsonProperty("bid_status") String bidStatus,
        @JsonProperty("progress_step") int progressStep) { }
