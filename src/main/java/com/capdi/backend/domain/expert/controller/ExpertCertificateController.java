package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertCertificateCreateRequest;
import com.capdi.backend.domain.expert.dto.ExpertCertificateResponse;
import com.capdi.backend.domain.expert.service.ExpertCertificateService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/expert/me/certificates")
public class ExpertCertificateController {

    private final ExpertCertificateService expertCertificateService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExpertCertificateResponse>> createMyCertificate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ExpertCertificateCreateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                expertCertificateService.createMyCertificate(userDetails.getUserId(), request)
        ));
    }
}
