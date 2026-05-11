package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.bid.entity.Bid;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class AdminAnnouncementDetailResponse {

    private Long id;
    private String announcementCode;
    private String companyName;
    private String clientName;
    private String clientContact;
    private String purpose;
    private String industry;
    private String businessOwnerType;
    private AnnouncementStatusEnum status;
    private LocalDateTime createdAt;
    private List<BidSummary> bids;

    @Getter
    @Builder
    public static class BidSummary {
        private Long bidId;
        private String expertName;
        private String expertCompany;
        private String expertContact;
        private BigDecimal bidAmount;
        private String bidStatus;
        private LocalDateTime submittedAt;

        public static BidSummary from(Bid bid) {
            return BidSummary.builder()
                    .bidId(bid.getId())
                    .expertName(bid.getExpertUser().getName())
                    .expertCompany(null)  // ExpertProfile 연관관계 확인 후 팀원과 협의
                    .expertContact(bid.getExpertUser().getPhone())
                    .bidAmount(bid.getBidAmount())
                    .bidStatus(bid.getStatus().name())
                    .submittedAt(bid.getSubmittedAt())
                    .build();
        }
    }

    public static AdminAnnouncementDetailResponse from(Announcement announcement, List<Bid> bids) {
        return AdminAnnouncementDetailResponse.builder()
                .id(announcement.getId())
                .announcementCode(announcement.getAnnouncementCode())
                .companyName(announcement.getClientInfo().getCompanyName())
                .clientName(announcement.getClientInfo().getRepresentativeName())
                .clientContact(announcement.getClientInfo().getContact())
                .purpose(announcement.getPurpose().name())
                .industry(announcement.getIndustry().name())
                .businessOwnerType(announcement.getBusinessOwnerType().name())
                .status(announcement.getStatus())
                .createdAt(announcement.getCreatedAt())
                .bids(bids.stream().map(BidSummary::from).toList())
                .build();
    }
}
