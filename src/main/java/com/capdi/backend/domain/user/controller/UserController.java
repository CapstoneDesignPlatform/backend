package com.capdi.backend.domain.user.controller;

import com.capdi.backend.domain.user.dto.UserCreateRequest;
import com.capdi.backend.domain.user.dto.UserResponse;
import com.capdi.backend.domain.user.service.UserService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    /**
     * 사용자 생성 API
     * POST /users
     */
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> createUser(@RequestBody UserCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("사용자가 생성되었습니다.", userService.createUser(request)));
    }

    /**
     * 사용자 목록 조회 API
     * GET /users
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsers() {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUsers()));
    }

    /**
     * 사용자 단건 조회 API
     * GET /users/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getUser(userId)));
    }
}