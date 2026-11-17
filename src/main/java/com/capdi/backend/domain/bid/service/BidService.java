package com.capdi.backend.domain.bid.service;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.bid.dto.BidCreateRequest;
import com.capdi.backend.domain.bid.dto.BidResponse;
import com.capdi.backend.domain.bid.dto.ExpertSelectRequest;
import com.capdi.backend.domain.bid.dto.ExpertSelectResponse;
import com.capdi.backend.domain.bid.dto.MyBidResponse;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.entity.BidStatusEnum;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BidService {

    private final BidRepository bidRepository;
    private final AnnouncementRepository announcementRepository;
    private final ExpertProfileRepository expertProfileRepository;

    @Transactional
    public BidResponse createBid(Long loginUserId, String announcementCode, BidCreateRequest request) {
        // Lock before snapshot reads so a waiting bid sees committed company-info edits.
        Announcement announcement = announcementRepository.findByAnnouncementCodeWithLock(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        ExpertProfile expertProfile = expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));

        if (expertProfile.getVerificationStatus() != VerificationStatusEnum.APPROVED) {
            throw new CustomException(ErrorCode.EXPERT_NOT_APPROVED);
        }

        if (announcement.getStatus() != AnnouncementStatusEnum.ACTIVE) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_CLOSED);
        }

        if (!announcement.getClientInfo().isComplete()) {
            throw new CustomException(ErrorCode.CLIENT_INFO_INCOMPLETE);
        }

        // A locking read sees bids committed while this request waited for the announcement lock.
        if (bidRepository.findByAnnouncementAndExpertUserIdWithLock(announcement, loginUserId).isPresent()) {
            throw new CustomException(ErrorCode.BID_ALREADY_EXISTS);
        }

        Bid bid = Bid.create(announcement, expertProfile.getUser(), request.getBidAmount());
        Bid savedBid = bidRepository.save(bid);
        long totalBidCount = bidRepository.countByAnnouncementCodeWithLock(announcementCode);

        return BidResponse.from(savedBid, totalBidCount);
    }

    public PageResponse<MyBidResponse> getMyBids(
            Long loginUserId,
            String status,
            int page,
            int size,
            String sort
    ) {
        BidStatusEnum bidStatus = parseStatus(status);
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                resolveSort(sort)
        );
        Page<Bid> bidPage = bidStatus == null
                ? bidRepository.findByExpertUserId(loginUserId, pageRequest)
                : bidRepository.findByExpertUserIdAndStatus(loginUserId, bidStatus, pageRequest);

        List<Bid> bids = bidPage.getContent();
        if (bids.isEmpty()) {
            return PageResponse.from(bidPage, List.of());
        }

        List<Long> announcementIds = bids.stream()
                .map(b -> b.getAnnouncement().getId())
                .distinct()
                .toList();
        Map<Long, Long> countMap = bidRepository.countsByAnnouncementIds(announcementIds)
                .stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));

        List<MyBidResponse> items = bids.stream()
                .map(bid -> MyBidResponse.from(bid, countMap.getOrDefault(bid.getAnnouncement().getId(), 0L)))
                .toList();

        return PageResponse.from(bidPage, items);
    }

    @Transactional
    public ExpertSelectResponse selectExpert(Long userId, String announcementCode, Long bidId, ExpertSelectRequest request) {
        Announcement announcement = announcementRepository
                .findByAnnouncementCodeWithLock(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        if (!announcement.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_FORBIDDEN);
        }

        return selectLockedAnnouncement(announcement, bidId, request);
    }

    @Transactional
    public ExpertSelectResponse selectGuestExpert(String announcementCode, Long bidId, ExpertSelectRequest request) {
        Announcement announcement = announcementRepository.findByAnnouncementCodeWithLock(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));

        if (!announcement.isGuest()) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_FORBIDDEN);
        }

        return selectLockedAnnouncement(announcement, bidId, request);
    }

    private ExpertSelectResponse selectLockedAnnouncement(
            Announcement announcement, Long bidId, ExpertSelectRequest request) {
        if (announcement.getStatus() != AnnouncementStatusEnum.CLOSED
                || announcement.getProgressStep() != AnnouncementProgressStepEnum.STEP_3_EXPERT_SELECTION) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_NOT_IN_SELECTION_STEP);
        }

        Bid selectedBid = bidRepository.findByIdAndAnnouncement(bidId, announcement)
                .orElseThrow(() -> new CustomException(ErrorCode.BID_NOT_FOUND));

        if (selectedBid.getStatus() != BidStatusEnum.PENDING) {
            throw new CustomException(ErrorCode.BID_ALREADY_PROCESSED);
        }

        ExpertProfile selectedExpert = expertProfileRepository.findByUserId(selectedBid.getExpertUser().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));

        if (selectedExpert.getVerificationStatus() != VerificationStatusEnum.APPROVED
                || expertProfileRepository.incrementSelectedCountIfApproved(
                    selectedExpert.getId(), VerificationStatusEnum.APPROVED, LocalDateTime.now()) != 1) {
            throw new CustomException(ErrorCode.EXPERT_NOT_APPROVED);
        }

        selectedBid.select(request.getFinalAmount());

        bidRepository.rejectAllPendingExcept(announcement, bidId, BidStatusEnum.REJECTED, BidStatusEnum.PENDING);

        announcement.updateProgressStep(AnnouncementProgressStepEnum.STEP_4_DIAGNOSIS_STARTED);

        return ExpertSelectResponse.from(selectedBid);
    }

    private BidStatusEnum parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return BidStatusEnum.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1) - 1;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private Sort resolveSort(String sort) {
        if ("submitted_at_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "submittedAt");
        }
        return Sort.by(Sort.Direction.DESC, "submittedAt");
    }
}
