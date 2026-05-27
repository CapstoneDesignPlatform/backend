package com.capdi.backend.domain.admin.controller;

import com.capdi.backend.domain.admin.dto.AdminLoginRequest;
import com.capdi.backend.domain.admin.dto.AdminLoginResponse;
import com.capdi.backend.domain.admin.service.AdminAuthService;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AdminLoginResponse>> login(
            @RequestBody @Valid AdminLoginRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok("관리자 로그인 성공", adminAuthService.login(request)));
    }
}
