package com.capdi.backend.domain.bid.dto;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class MyBidResponse {

    private Long id;

    @JsonProperty("announcement_id")
    private Long announcementId;

    @JsonProperty("announcement_code")
    private String announcementCode;

    @JsonProperty("job_post_title")
    private String jobPostTitle;

    @JsonProperty("bid_amount")
    private BigDecimal bidAmount;

    private String status;

    @JsonProperty("submitted_at")
    private LocalDateTime submittedAt;

    @JsonProperty("total_bid_count")
    private long totalBidCount;

    @JsonProperty("job_post_deadline")
    private LocalDateTime jobPostDeadline;

    @JsonProperty("client_contact")
    private ClientContactDto clientContact;

    public static MyBidResponse from(Bid bid, long totalBidCount) {
        Announcement announcement = bid.getAnnouncement();

        return MyBidResponse.builder()
                .id(bid.getId())
                .announcementId(announcement.getId())
                .announcementCode(announcement.getAnnouncementCode())
                .jobPostTitle(announcement.getDisplayTitle())
                .bidAmount(bid.getBidAmount())
                .status(bid.getStatus().name())
                .submittedAt(bid.getSubmittedAt())
                .totalBidCount(totalBidCount)
                .jobPostDeadline(null)
                .clientContact(bid.getStatus() == BidStatusEnum.SELECTED
                        ? ClientContactDto.from(announcement.getClientInfo())
                        : null)
                .build();
    }

    @Getter
    @Builder
    public static class ClientContactDto {

        private String name;
        private String phone;
        private String email;

        public static ClientContactDto from(ClientInfo clientInfo) {
            return ClientContactDto.builder()
                    .name(clientInfo.getRepresentativeName())
                    .phone(clientInfo.getContact())
                    .email(clientInfo.getEmail())
                    .build();
        }
    }
}
