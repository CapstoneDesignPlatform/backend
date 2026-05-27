package com.capdi.backend.domain.announcement.service;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.dto.AnnouncementCreateResponse;
import com.capdi.backend.domain.announcement.dto.AnnouncementDetailResponse;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse.CurrentAnnouncementDto;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse.PastAnnouncementDto;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final BidRepository bidRepository;
    private final ExpertProfileRepository expertProfileRepository;

    private static final int MAX_RETRY_COUNT = 3;

    @Lazy
    @Autowired
    private AnnouncementService self;

    public AnnouncementCreateResponse createAnnouncement(AnnouncementCreateRequest request, Long userId) {
        ClientInfo clientInfo = findClientInfo(request.getClientInfoId());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return generateAndSave(request, user, clientInfo);
    }

    public AnnouncementCreateResponse createGuestAnnouncement(AnnouncementCreateRequest request) {
        ClientInfo clientInfo = findClientInfo(request.getClientInfoId());
        return generateAndSave(request, null, clientInfo);
    }

    private AnnouncementCreateResponse generateAndSave(
            AnnouncementCreateRequest request, User user, ClientInfo clientInfo) {

        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            try {
                String code = generateAnnouncementCode(clientInfo.getContact());
                return self.saveAnnouncement(request, user, clientInfo, code);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_RETRY_COUNT - 1) {
                    throw new CustomException(ErrorCode.CODE_GENERATION_FAILED);
                }
            }
        }
        throw new CustomException(ErrorCode.CODE_GENERATION_FAILED);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AnnouncementCreateResponse saveAnnouncement(
            AnnouncementCreateRequest request, User user, ClientInfo clientInfo, String code) {

        Announcement announcement = Announcement.builder()
                .user(user)
                .clientInfo(clientInfo)
                .announcementCode(code)
                .industry(request.getIndustry())
                .purpose(request.getPurpose())
                .businessOwnerType(request.getBusinessOwnerType())
                .category(request.getCategory())
                .currentIndustry(request.getCurrentIndustryStatus())
                .currentIndustryDetail(request.getCurrentIndustryDetail())
                .currentLicense(request.getHeldLicense())
                .jobType(request.getPurpose().toJobType())
                .requiredLicense(request.getRequiredLicense())
                .capital(request.getCapital())
                .capitalScale(request.getCapitalScale())
                .diagnosisReason(request.getDiagnosisReason())
                .diagnosisReasonDetail(request.getDiagnosisReasonDetail())
                .build();

        announcementRepository.saveAndFlush(announcement);
        return AnnouncementCreateResponse.of(code);
    }

    private String generateAnnouncementCode(String contact) {
        String digits = contact.replaceAll("[^0-9]", "");
        char c1 = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
        char c2 = (char) ('A' + ThreadLocalRandom.current().nextInt(26));
        return digits + c1 + c2;
    }

    public ClientAnnouncementListResponse getClientAnnouncements(Long userId) {
        ClientInfo clientInfo = clientRepository.findByUser_Id(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));

        List<Announcement> all = announcementRepository
                .findAllByClientInfoOrderByCreatedAtDesc(clientInfo);

        Announcement current = all.stream()
                .filter(a -> a.getStatus() == AnnouncementStatusEnum.ACTIVE)
                .findFirst()
                .orElse(null);

        List<Announcement> past = all.stream()
                .filter(a -> a.getStatus() != AnnouncementStatusEnum.ACTIVE)
                .toList();

        CurrentAnnouncementDto currentDto = buildCurrentDto(current);
        List<PastAnnouncementDto> pastDtos = past.stream()
                .map(this::buildPastDto)
                .toList();

        return ClientAnnouncementListResponse.builder()
                .currentAnnouncement(currentDto)
                .pastAnnouncements(pastDtos)
                .build();
    }

    private CurrentAnnouncementDto buildCurrentDto(Announcement announcement) {
        if (announcement == null) return null;

        List<Bid> bids = bidRepository.findAllByAnnouncement(announcement);

        List<Long> expertUserIds = bids.stream()
                .map(b -> b.getExpertUser().getId())
                .toList();

        Map<Long, ExpertProfile> profileMap = expertProfileRepository
                .findByUserIdIn(expertUserIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return CurrentAnnouncementDto.from(announcement, bids, profileMap);
    }

    private PastAnnouncementDto buildPastDto(Announcement announcement) {
        List<Bid> bids = bidRepository.findAllByAnnouncement(announcement);

        Bid selectedBid = bids.stream()
                .filter(b -> b.getStatus() == BidStatusEnum.SELECTED)
                .findFirst()
                .orElse(null);

        ExpertProfile profile = null;
        if (selectedBid != null) {
            profile = expertProfileRepository
                    .findByUserId(selectedBid.getExpertUser().getId())
                    .orElse(null);
        }

        return PastAnnouncementDto.from(announcement, selectedBid, profile);
    }

    public AnnouncementDetailResponse getAnnouncementByCode(String announcementCode) {
        Announcement announcement = announcementRepository.findByAnnouncementCode(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        List<Bid> bids = bidRepository.findAllByAnnouncement(announcement);

        List<Long> expertUserIds = bids.stream()
                .map(b -> b.getExpertUser().getId())
                .toList();

        Map<Long, ExpertProfile> profileMap = expertProfileRepository
                .findByUserIdIn(expertUserIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return AnnouncementDetailResponse.from(announcement, bids, profileMap);
    }

    private ClientInfo findClientInfo(Long clientInfoId) {
        return clientRepository.findById(clientInfoId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));
    }
}
