package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoCreateRequest;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoResponse;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtractionResponse;
import com.capdi.backend.domain.expert.service.BusinessRegistrationInfoService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    @org.springframework.web.bind.annotation.PutMapping("/{businessRegistrationInfoId}/confirmation")
    public ResponseEntity<ApiResponse<BusinessRegistrationInfoResponse>> confirm(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable Long businessRegistrationInfoId,
            @RequestBody @Valid com.capdi.backend.domain.expert.dto.BusinessConfirmationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(businessRegistrationInfoService.confirmMyBusinessInformation(
                userDetails.getUserId(), businessRegistrationInfoId, request)));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody() {
        // Do not log parser exceptions containing submitted personal data.
        return ResponseEntity.badRequest().body(ApiResponse.fail("입력값이 올바르지 않습니다."));
    }

    @GetMapping("/{businessRegistrationInfoId}/ocr-extraction")
    public ResponseEntity<ApiResponse<BusinessRegistrationExtractionResponse>> getOcrExtraction(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long businessRegistrationInfoId) {
        return ResponseEntity.ok(ApiResponse.ok(businessRegistrationInfoService.getOcrExtraction(
                userDetails.getUserId(), businessRegistrationInfoId)));
    }

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
