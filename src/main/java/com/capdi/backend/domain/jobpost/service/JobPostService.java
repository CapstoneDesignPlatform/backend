package com.capdi.backend.domain.jobpost.service;

import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.bid.repository.BidRepository;
import com.capdi.backend.domain.jobpost.dto.JobPostDetailResponse;
import com.capdi.backend.domain.jobpost.dto.JobPostResponse;
import com.capdi.backend.domain.jobpost.entity.JobPost;
import com.capdi.backend.domain.jobpost.repository.JobPostRepository;
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

    private final JobPostRepository jobPostRepository;
    private final BidRepository bidRepository;

    public PageResponse<JobPostResponse> getExpertJobPosts(int page, int size, String sort) {
        PageRequest pageRequest = PageRequest.of(
                normalizePage(page),
                normalizeSize(size),
                resolveSort(sort)
        );
        Page<JobPost> jobPostPage = jobPostRepository.findAll(pageRequest);

        List<JobPostResponse> items = jobPostPage.getContent().stream()
                .map(jobPost -> JobPostResponse.from(jobPost, countBids(jobPost), false))
                .toList();

        return PageResponse.from(jobPostPage, items);
    }

    public JobPostDetailResponse getExpertJobPostDetail(Long jobPostId) {
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POST_NOT_FOUND));

        Announcement announcement = jobPost.getAnnouncement();
        if (announcement == null) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }

        return JobPostDetailResponse.from(jobPost, countBids(jobPost));
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

    private long countBids(JobPost jobPost) {
        Announcement announcement = jobPost.getAnnouncement();
        if (announcement == null || announcement.getId() == null) {
            return 0L;
        }
        return bidRepository.countByAnnouncementId(announcement.getId());
    }
}
