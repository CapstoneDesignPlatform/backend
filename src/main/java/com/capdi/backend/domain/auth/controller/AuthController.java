package com.capdi.backend.domain.auth.controller;

import com.capdi.backend.domain.auth.dto.AuthTokens;
import com.capdi.backend.domain.auth.dto.KakaoLoginRequest;
import com.capdi.backend.domain.auth.dto.KakaoLoginResponse;
import com.capdi.backend.domain.auth.dto.KakaoSignupRequest;
import com.capdi.backend.domain.auth.dto.LoginRequest;
import com.capdi.backend.domain.auth.dto.LoginResponse;
import com.capdi.backend.domain.auth.dto.SignupRequest;
import com.capdi.backend.domain.auth.dto.TokenResponse;
import com.capdi.backend.domain.auth.service.AuthService;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.response.ApiResponse;
import com.capdi.backend.global.jwt.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "CLIENT 또는 EXPERT (ADMIN은 DB 직접 삽입)")
    public ResponseEntity<ApiResponse<Void>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다."));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthTokens tokens = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("로그인 성공.", LoginResponse.of(tokens)));
    }

    @PostMapping("/kakao/login")
    @Operation(summary = "카카오 로그인", description = "인가 코드로 로그인. 미가입이면 registered=false와 signupToken(10분) 반환")
    public ResponseEntity<ApiResponse<KakaoLoginResponse>> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("카카오 로그인 성공.", authService.kakaoLogin(request)));
    }

    @PostMapping("/kakao/signup")
    @Operation(summary = "카카오 회원가입", description = "signupToken과 직접 입력한 회원 정보로 가입 후 바로 로그인 토큰 발급")
    public ResponseEntity<ApiResponse<LoginResponse>> kakaoSignup(@Valid @RequestBody KakaoSignupRequest request) {
        AuthTokens tokens = authService.kakaoSignup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다.", LoginResponse.of(tokens)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 갱신")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        AuthTokens tokens = authService.refresh(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok("토큰 갱신 성공.", TokenResponse.of(tokens)));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails != null) {
            authService.logout(userDetails.getUserId());
        }
        return ResponseEntity.ok(ApiResponse.ok("로그아웃 성공."));
    }
}
