package com.capdi.backend.domain.user.controller;

import com.capdi.backend.domain.user.dto.UserResponse;
import com.capdi.backend.domain.user.service.UserService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Tag(name = "User", description = "유저 API")
public class UserController {

    private final UserService userService;

    @GetMapping("/myInfo")
    @Operation(summary = "내 정보 조회", description = "로그인한 유저의 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResponse response = userService.getMyInfo(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
