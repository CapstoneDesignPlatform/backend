package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoCreateRequest;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoResponse;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest;
import com.capdi.backend.domain.expert.service.BusinessRegistrationInfoService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert/me/business-registration-info")
public class BusinessRegistrationInfoController {

    private final BusinessRegistrationInfoService businessRegistrationInfoService;

    @PostMapping
    public ResponseEntity<ApiResponse<BusinessRegistrationInfoResponse>> createMyBusinessRegistrationInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid BusinessRegistrationInfoCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                businessRegistrationInfoService.createMyBusinessRegistrationInfo(userDetails.getUserId(), request)
        ));
    }

    @PatchMapping("/{businessRegistrationInfoId}")
    public ResponseEntity<ApiResponse<BusinessRegistrationInfoResponse>> updateMyBusinessRegistrationInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long businessRegistrationInfoId,
            @RequestBody @Valid BusinessRegistrationInfoUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                businessRegistrationInfoService.updateMyBusinessRegistrationInfo(
                        userDetails.getUserId(),
                        businessRegistrationInfoId,
                        request
                )
        ));
    }
}
