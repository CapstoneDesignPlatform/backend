package com.capdi.backend.domain.announcement.dto;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ClientAnnouncementListResponse {

    @JsonProperty("current_announcement")
    private CurrentAnnouncementDto currentAnnouncement;

    @JsonProperty("past_announcements")
    private List<PastAnnouncementDto> pastAnnouncements;

    @Getter
    @Builder
    public static class CurrentAnnouncementDto {

        private Long id;

        @JsonProperty("announcement_code")
        private String announcementCode;

        @JsonProperty("registered_at")
        private LocalDate registeredAt;

        private String industry;
        private String purpose;

        @JsonProperty("business_owner_type")
        private String businessOwnerType;

        @JsonProperty("required_license")
        private String requiredLicense;

        @JsonProperty("capital_scale")
        private BigDecimal capitalScale;

        private String status;

        @JsonProperty("progress_step")
        private int progressStep;

        @JsonProperty("progress_step_label")
        private String progressStepLabel;

        private List<BidExpertDto> bids;

        public static CurrentAnnouncementDto from(
                Announcement announcement,
                List<Bid> bids,
                Map<Long, ExpertProfile> expertProfileMap) {

            List<BidExpertDto> bidDtos = bids.stream()
                    .map(bid -> BidExpertDto.from(bid, expertProfileMap.get(bid.getExpertUser().getId())))
                    .toList();

            AnnouncementProgressStepEnum step = announcement.getProgressStep();

            return CurrentAnnouncementDto.builder()
                    .id(announcement.getId())
                    .announcementCode(announcement.getAnnouncementCode())
                    .registeredAt(announcement.getCreatedAt().toLocalDate())
                    .industry(announcement.getIndustry().getLabel())
                    .purpose(announcement.getPurpose().getLabel())
                    .businessOwnerType(announcement.getBusinessOwnerType().getLabel())
                    .requiredLicense(announcement.getRequiredLicense())
                    .capitalScale(announcement.getCapitalScale())
                    .status(announcement.getStatus().name())
                    .progressStep(step.getStepNumber())
                    .progressStepLabel(step.getLabel())
                    .bids(bidDtos)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class BidExpertDto {

        @JsonProperty("bid_id")
        private Long bidId;

        @JsonProperty("expert_name")
        private String expertName;

        @JsonProperty("business_name")
        private String businessName;

        @JsonProperty("bid_amount")
        private BigDecimal bidAmount;

        @JsonProperty("final_amount")
        private BigDecimal finalAmount;

        private String status;

        public static BidExpertDto from(Bid bid, ExpertProfile profile) {
            return BidExpertDto.builder()
                    .bidId(bid.getId())
                    .expertName(bid.getExpertUser().getName())
                    .businessName(profile != null ? profile.getBusinessName() : null)
                    .bidAmount(bid.getBidAmount())
                    .finalAmount(bid.getFinalAmount())
                    .status(bid.getStatus().name())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class PastAnnouncementDto {

        private Long id;

        @JsonProperty("registered_at")
        private LocalDate registeredAt;

        private String industry;

        @JsonProperty("business_owner_type")
        private String businessOwnerType;

        private String purpose;

        @JsonProperty("required_license")
        private String requiredLicense;

        @JsonProperty("capital_scale")
        private BigDecimal capitalScale;

        private String status;

        @JsonProperty("selected_bid")
        private SelectedBidDto selectedBid;

        public static PastAnnouncementDto from(Announcement announcement, Bid selectedBid, ExpertProfile profile) {
            return PastAnnouncementDto.builder()
                    .id(announcement.getId())
                    .registeredAt(announcement.getCreatedAt().toLocalDate())
                    .industry(announcement.getIndustry().getLabel())
                    .businessOwnerType(announcement.getBusinessOwnerType().getLabel())
                    .purpose(announcement.getPurpose().getLabel())
                    .requiredLicense(announcement.getRequiredLicense())
                    .capitalScale(announcement.getCapitalScale())
                    .status(announcement.getStatus().name())
                    .selectedBid(selectedBid != null ? SelectedBidDto.from(selectedBid, profile) : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class SelectedBidDto {

        @JsonProperty("expert_name")
        private String expertName;

        @JsonProperty("business_name")
        private String businessName;

        private String phone;
        private String email;

        @JsonProperty("bid_amount")
        private BigDecimal bidAmount;

        @JsonProperty("final_amount")
        private BigDecimal finalAmount;

        private String result;

        public static SelectedBidDto from(Bid bid, ExpertProfile profile) {
            return SelectedBidDto.builder()
                    .expertName(bid.getExpertUser().getName())
                    .businessName(profile != null ? profile.getBusinessName() : null)
                    .phone(bid.getExpertUser().getPhone())
                    .email(bid.getExpertUser().getEmail())
                    .bidAmount(bid.getBidAmount())
                    .finalAmount(bid.getFinalAmount())
                    .result(BidStatusEnum.SELECTED.name())
                    .build();
        }
    }
}
