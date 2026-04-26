package com.capdi.backend.domain.jobpost.service;

import com.capdi.backend.domain.announcement.dto.AnnouncementResponse;
import com.capdi.backend.domain.announcement.entity.Announcement;
import com.capdi.backend.domain.jobpost.entity.JobPost;
import com.capdi.backend.domain.jobpost.repository.JobPostRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobPostService {

    private final JobPostRepository jobPostRepository;

    public AnnouncementResponse getAnnouncementDetailByJobPostId(Long jobPostId) {
        JobPost jobPost = jobPostRepository.findById(jobPostId)
                .orElseThrow(() -> new CustomException(ErrorCode.JOB_POST_NOT_FOUND));

        Announcement announcement = jobPost.getAnnouncement();
        if (announcement == null) {
            throw new CustomException(ErrorCode.ANNOUNCEMENT_NOT_FOUND);
        }

        return AnnouncementResponse.from(announcement);
    }
}
