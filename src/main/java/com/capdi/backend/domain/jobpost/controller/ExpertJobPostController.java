package com.capdi.backend.domain.jobpost.controller;

import com.capdi.backend.domain.jobpost.dto.JobPostDetailResponse;
import com.capdi.backend.domain.jobpost.dto.JobPostResponse;
import com.capdi.backend.domain.jobpost.service.JobPostService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import com.capdi.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert/job-posts")
public class ExpertJobPostController {

    private final JobPostService jobPostService;

    // 전문가가 입찰 가능한 잡포스트 목록을 페이지 단위로 조회한다.
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<JobPostResponse>>> getExpertJobPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "posted_at_desc") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jobPostService.getExpertJobPosts(userDetails.getUserId(), page, size, sort)));
    }

    // 전문가가 공고 코드로 공고(Announcement) 상세 정보를 조회한다.
    @GetMapping("/{announcementCode}")
    public ResponseEntity<ApiResponse<JobPostDetailResponse>> getJobPostDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String announcementCode
    ) {
        return ResponseEntity.ok(ApiResponse.ok(jobPostService.getExpertJobPostDetail(userDetails.getUserId(), announcementCode)));
    }
}
