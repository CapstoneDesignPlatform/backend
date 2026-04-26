package com.capdi.backend.domain.jobpost.controller;

import com.capdi.backend.domain.announcement.dto.AnnouncementResponse;
import com.capdi.backend.domain.jobpost.service.JobPostService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert/job-posts")
public class ExpertJobPostController {

    private final JobPostService jobPostService;

    // 전문가가 잡포스트의 리스트ID로 연결된 공고(Announcement) 상세 정보를 조회한다.
    @GetMapping("/{jobPostId}")
    public ApiResponse<AnnouncementResponse> getAnnouncementDetail(
            @PathVariable("jobPostId") Long jobPostId
    ) {
        return ApiResponse.ok(jobPostService.getAnnouncementDetailByJobPostId(jobPostId));
    }
}
