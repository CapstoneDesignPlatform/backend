package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertProfileCreateRequest;
import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.service.ExpertProfileService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert-profiles")
public class ExpertProfileController {

    private final ExpertProfileService expertProfileService;

    /**
     * 전문가 프로필 생성 API
     * POST /expert-profiles
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> createExpertProfile(@RequestBody ExpertProfileCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("전문가 프로필이 생성되었습니다.", expertProfileService.createExpertProfile(request)));
    }

    /**
     * 전문가 프로필 목록 조회 API
     * GET /expert-profiles
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ExpertProfileResponse>>> getExpertProfiles() {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.getExpertProfiles()));
    }

    /**
     * 전문가 프로필 단건 조회 API
     * GET /expert-profiles/{expertProfileId}
     */
    @GetMapping("/{expertProfileId}")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> getExpertProfile(@PathVariable Long expertProfileId) {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.getExpertProfile(expertProfileId)));
    }
}