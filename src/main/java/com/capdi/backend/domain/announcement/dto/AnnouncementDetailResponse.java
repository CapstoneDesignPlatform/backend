package com.capdi.backend.domain.announcement.dto;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import com.capdi.backend.domain.client.entity.ClientInfo;
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
public class AnnouncementDetailResponse {

    @JsonProperty("announcement_code")
    private String announcementCode;

    @JsonProperty("registered_at")
    private LocalDate registeredAt;

    private String industry;

    @JsonProperty("business_owner_type")
    private String businessOwnerType;

    private String purpose;

    @JsonProperty("required_license")
    private String requiredLicense;

    @JsonProperty("current_industry")
    private String currentIndustry;

    @JsonProperty("capital_scale")
    private BigDecimal capitalScale;

    private String status;

    @JsonProperty("progress_step")
    private int progressStep;

    @JsonProperty("progress_step_label")
    private String progressStepLabel;

    private List<BidDto> bids;

    @JsonProperty("client_info")
    private ClientInfoDto clientInfo;

    public static AnnouncementDetailResponse from(
            Announcement announcement,
            List<Bid> bids,
            Map<Long, ExpertProfile> expertProfileMap) {

        AnnouncementProgressStepEnum step = announcement.getProgressStep();
        boolean isSelectionStep = step == AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION;
        boolean showContact = step.ordinal() >= AnnouncementProgressStepEnum.STEP_4_DIAGNOSIS_STARTED.ordinal();

        List<BidDto> bidDtos = bids.stream()
                .filter(b -> isSelectionStep || b.getStatus() == BidStatusEnum.SELECTED)
                .map(b -> BidDto.from(b, expertProfileMap.get(b.getExpertUser().getId()), showContact))
                .toList();

        return AnnouncementDetailResponse.builder()
                .announcementCode(announcement.getAnnouncementCode())
                .registeredAt(announcement.getCreatedAt().toLocalDate())
                .industry(announcement.getIndustry().getLabel())
                .businessOwnerType(announcement.getBusinessOwnerType().getLabel())
                .purpose(announcement.getPurpose().getLabel())
                .requiredLicense(announcement.getRequiredLicense())
                .currentIndustry(announcement.getCurrentIndustry() != null
                        ? announcement.getCurrentIndustry().getLabel() : null)
                .capitalScale(announcement.getCapitalScale())
                .status(announcement.getStatus().name())
                .progressStep(step.getStepNumber())
                .progressStepLabel(step.getLabel())
                .bids(bidDtos)
                .clientInfo(ClientInfoDto.from(announcement.getClientInfo()))
                .build();
    }

    @Getter
    @Builder
    public static class BidDto {

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

        private String phone;

        private String email;

        public static BidDto from(Bid bid, ExpertProfile profile, boolean includeContact) {
            return BidDto.builder()
                    .bidId(bid.getId())
                    .expertName(bid.getExpertUser().getName())
                    .businessName(profile != null ? profile.getBusinessName() : null)
                    .bidAmount(bid.getBidAmount())
                    .finalAmount(bid.getFinalAmount())
                    .status(bid.getStatus().name())
                    .phone(includeContact ? bid.getExpertUser().getPhone() : null)
                    .email(includeContact ? bid.getExpertUser().getEmail() : null)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ClientInfoDto {

        @JsonProperty("company_name")
        private String companyName;

        @JsonProperty("business_number")
        private String businessNumber;

        @JsonProperty("representative_name")
        private String representativeName;

        private String address;
        private String email;
        private String contact;

        @JsonProperty("founded_date")
        private LocalDate foundedDate;

        private BigDecimal capital;

        public static ClientInfoDto from(ClientInfo clientInfo) {
            return ClientInfoDto.builder()
                    .companyName(clientInfo.getCompanyName())
                    .businessNumber(clientInfo.getBusinessNumber())
                    .representativeName(clientInfo.getRepresentativeName())
                    .address(clientInfo.getAddress())
                    .email(clientInfo.getEmail())
                    .contact(clientInfo.getContact())
                    .foundedDate(clientInfo.getFoundedDate())
                    .capital(clientInfo.getCapital())
                    .build();
        }
    }
}
