package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.*;
import com.capdi.backend.domain.expert.service.BusinessVerificationService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/expert/me/business-registration-info/{id}/verifications")
public class BusinessVerificationController {
    private final BusinessVerificationService service;
    @PostMapping
    public ResponseEntity<ApiResponse<BusinessVerificationResponse>> execute(@AuthenticationPrincipal CustomUserDetails user,
            @PathVariable Long id, @Valid @RequestBody BusinessVerificationRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.execute(user.getUserId(), id, request)));
    }
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<BusinessVerificationResponse>> latest(@AuthenticationPrincipal CustomUserDetails user, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(service.latest(user.getUserId(), id)));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> invalidBody() {
        return ResponseEntity.badRequest().body(ApiResponse.fail("입력값이 올바르지 않습니다."));
    }
}
