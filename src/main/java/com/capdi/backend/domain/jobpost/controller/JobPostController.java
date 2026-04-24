package com.capdi.backend.domain.jobpost.controller;

import com.capdi.backend.domain.jobpost.dto.JobPostCreateRequest;
import com.capdi.backend.domain.jobpost.dto.JobPostResponse;
import com.capdi.backend.domain.jobpost.service.JobPostService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/job-posts")
public class JobPostController {

    private final JobPostService jobPostService;

    /**
     * 공고 목록 조회 API
     * GET /job-posts
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<JobPostResponse>>> getJobPosts() {
        return ResponseEntity.ok(ApiResponse.ok(jobPostService.getJobPosts()));
    }

    /**
     * 공고 상세 조회 API
     * GET /job-posts/{jobPostId}
     */
    @GetMapping("/{jobPostId}")
    public ResponseEntity<ApiResponse<JobPostResponse>> getJobPost(@PathVariable Long jobPostId) {
        return ResponseEntity.ok(ApiResponse.ok(jobPostService.getJobPost(jobPostId)));
    }

    /**
     * 공고 등록 API
     * POST /job-posts
     */
    @PostMapping
    public ResponseEntity<ApiResponse<JobPostResponse>> createJobPost(@RequestBody JobPostCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("공고가 등록되었습니다.", jobPostService.createJobPost(request)));
    }
}