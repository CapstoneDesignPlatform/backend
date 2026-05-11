package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.dto.ExpertProfileUpdateRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationStatusResponse;
import com.capdi.backend.domain.expert.service.ExpertProfileService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert/me")
public class ExpertProfileController {

    private final ExpertProfileService expertProfileService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.getMyProfile(userDetails.getUserId())));
    }

    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<ExpertProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ExpertProfileUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.updateMyProfile(userDetails.getUserId(), request)));
    }

    @GetMapping("/verification-status")
    public ResponseEntity<ApiResponse<ExpertVerificationStatusResponse>> getMyVerificationStatus(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        return ResponseEntity.ok(ApiResponse.ok(expertProfileService.getMyVerificationStatus(userDetails.getUserId())));
    }

    @PostMapping("/verification-requests")
    public ResponseEntity<ApiResponse<Void>> applyVerification(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ExpertVerificationRequest request
    ) {
        expertProfileService.applyVerification(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
