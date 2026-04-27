package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.dto.ExpertProfileUpdateRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationStatusResponse;
import com.capdi.backend.domain.expert.service.ExpertProfileService;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/expert/me")
public class ExpertProfileController {

    private final ExpertProfileService expertProfileService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.getMyProfile()));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateMyProfile(
            @RequestBody @Valid ExpertProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.updateMyProfile(request)));
    }

    @GetMapping("/verification-status")
    public ResponseEntity<ApiResponse<ExpertVerificationStatusResponse>> getMyVerificationStatus() {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.getMyVerificationStatus()));
    }

    @PostMapping("/verification-requests")
    public ResponseEntity<ApiResponse<Void>> applyVerification(
            @RequestBody @Valid ExpertVerificationRequest request
    ) {
        expertProfileService.applyVerification(request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}