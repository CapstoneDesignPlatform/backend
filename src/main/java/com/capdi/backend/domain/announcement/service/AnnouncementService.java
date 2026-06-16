package com.capdi.backend.domain.announcement.service;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.dto.AnnouncementCreateResponse;
import com.capdi.backend.domain.announcement.dto.AnnouncementDetailResponse;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse.CurrentAnnouncementDto;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse.PastAnnouncementDto;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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
    private final AnnouncementSaverService announcementSaverService;

    private static final int MAX_RETRY_COUNT = 3;

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
                return announcementSaverService.save(request, user, clientInfo, code);
            } catch (DataIntegrityViolationException e) {
                if (attempt == MAX_RETRY_COUNT - 1) {
                    throw new CustomException(ErrorCode.CODE_GENERATION_FAILED);
                }
            }
        }
        throw new CustomException(ErrorCode.CODE_GENERATION_FAILED);
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
                .filter(a -> a.getStatus() != AnnouncementStatusEnum.CANCELLED
                        && a.getProgressStep() != AnnouncementProgressStepEnum.STEP_7_COMPLETED)
                .findFirst()
                .orElse(null);

        List<Announcement> past = current == null ? all
                : all.stream().filter(a -> !a.getId().equals(current.getId())).toList();

        CurrentAnnouncementDto currentDto = buildCurrentDto(current);
        List<PastAnnouncementDto> pastDtos = buildPastDtos(past);

        return ClientAnnouncementListResponse.builder()
                .currentAnnouncement(currentDto)
                .pastAnnouncements(pastDtos)
                .build();
    }

    private CurrentAnnouncementDto buildCurrentDto(Announcement announcement) {
        if (announcement == null) return null;

        List<Bid> bids = bidRepository.findAllByAnnouncement(announcement);
        return CurrentAnnouncementDto.from(announcement, bids, buildProfileMap(bids));
    }

    private Map<Long, ExpertProfile> buildProfileMap(List<Bid> bids) {
        List<Long> expertUserIds = bids.stream()
                .map(b -> b.getExpertUser().getId())
                .toList();
        return expertProfileRepository.findByUserIdIn(expertUserIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));
    }

    private List<PastAnnouncementDto> buildPastDtos(List<Announcement> past) {
        if (past.isEmpty()) return List.of();

        List<Bid> selectedBids = bidRepository.findAllByAnnouncementInAndStatus(past, BidStatusEnum.SELECTED);

        Map<Long, Bid> selectedBidMap = selectedBids.stream()
                .collect(Collectors.toMap(
                        b -> b.getAnnouncement().getId(),
                        b -> b,
                        (a, b) -> a
                ));

        // 선정된 bid의 전문가 프로필 한 번에 조회
        List<Long> selectedExpertIds = selectedBidMap.values().stream()
                .map(b -> b.getExpertUser().getId())
                .toList();

        Map<Long, ExpertProfile> profileMap = expertProfileRepository
                .findByUserIdIn(selectedExpertIds)
                .stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), p -> p));

        return past.stream()
                .map(announcement -> {
                    Bid selectedBid = selectedBidMap.get(announcement.getId());
                    ExpertProfile profile = selectedBid != null
                            ? profileMap.get(selectedBid.getExpertUser().getId())
                            : null;
                    return PastAnnouncementDto.from(announcement, selectedBid, profile);
                })
                .toList();
    }

    @Transactional
    public void closeAnnouncement(Long userId, String announcementCode) {
        Announcement announcement = announcementRepository
                .findByAnnouncementCodeWithLock(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        if (!announcement.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_FORBIDDEN);
        }

        if (announcement.getProgressStep() != AnnouncementProgressStepEnum.STEP_2_BID_CLOSED) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_NOT_CLOSEABLE);
        }

        if (!announcement.getClientInfo().isComplete()) {
            throw new CustomException(ErrorCode.CLIENT_INFO_INCOMPLETE);
        }

        if (bidRepository.countByAnnouncement(announcement) == 0) {
            throw new CustomException(ErrorCode.NO_BIDS_TO_CLOSE);
        }

        announcement.updateStatus(AnnouncementStatusEnum.CLOSED);
        announcement.updateProgressStep(AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION);
    }

    public AnnouncementDetailResponse getAnnouncementByCode(String announcementCode) {
        Announcement announcement = announcementRepository.findByAnnouncementCode(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        List<Bid> bids = bidRepository.findAllByAnnouncement(announcement);
        return AnnouncementDetailResponse.from(announcement, bids, buildProfileMap(bids));
    }

    private ClientInfo findClientInfo(Long clientInfoId) {
        return clientRepository.findById(clientInfoId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLIENT_INFO_NOT_FOUND));
    }
}
