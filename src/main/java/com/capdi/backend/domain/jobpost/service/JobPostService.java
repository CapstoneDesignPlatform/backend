package com.capdi.backend.domain.jobpost.service;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.domain.bid.entity.Bid;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.jobpost.dto.JobPostDetailResponse;
import com.capdi.backend.domain.jobpost.dto.JobPostResponse;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostService {

    private final AnnouncementRepository announcementRepository;
    private final BidRepository bidRepository;
    private final ExpertProfileRepository expertProfileRepository;

    public PageResponse<JobPostResponse> getExpertJobPosts(Long loginUserId, int page, int size, String sort) {
        validateApprovedExpert(loginUserId);

        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                resolveSort(sort)
        );
        Page<Announcement> announcementPage =
                announcementRepository.findAllByStatus(AnnouncementStatusEnum.ACTIVE, pageRequest);

        List<JobPostResponse> items = announcementPage.getContent().stream()
                .map(announcement -> JobPostResponse.from(
                        announcement,
                        bidRepository.countByAnnouncement(announcement),
                        bidRepository.existsByAnnouncementAndExpertUserId(announcement, loginUserId)
                ))
                .toList();

        return PageResponse.from(announcementPage, items);
    }

    public JobPostDetailResponse getExpertJobPostDetail(Long loginUserId, String announcementCode) {
        validateApprovedExpert(loginUserId);

        Announcement announcement = announcementRepository.findByAnnouncementCode(announcementCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND));
        Bid myBid = bidRepository.findByAnnouncementAndExpertUserId(announcement, loginUserId)
                .orElse(null);

        return JobPostDetailResponse.from(announcement, bidRepository.countByAnnouncement(announcement), myBid);
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
        if ("posted_at_asc".equals(sort)) {
            return Sort.by(Sort.Direction.ASC, "createdAt");
        }
        return Sort.by(Sort.Direction.DESC, "createdAt");
    }

    private void validateApprovedExpert(Long loginUserId) {
        ExpertProfile expertProfile = expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));

        if (expertProfile.getVerificationStatus() != VerificationStatusEnum.APPROVED) {
            throw new CustomException(ErrorCode.EXPERT_NOT_APPROVED);
        }
    }
}
