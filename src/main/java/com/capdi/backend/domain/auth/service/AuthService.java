package com.capdi.backend.domain.auth.service;

import com.capdi.backend.domain.auth.dto.AuthTokens;
import com.capdi.backend.domain.auth.dto.LoginRequest;
import com.capdi.backend.domain.auth.dto.SignupRequest;
import com.capdi.backend.domain.auth.entity.RefreshToken;
import com.capdi.backend.domain.auth.repository.RefreshTokenRepository;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.expert.service.ExpertProfileProvisioningService;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ClientRepository clientRepository;
    private final ExpertProfileProvisioningService expertProfileProvisioningService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Transactional
    public void signup(SignupRequest request) {
        if (request.getUserType() == UserTypeEnum.ADMIN) {
            throw new CustomException(ErrorCode.INVALID_SIGNUP_ROLE);
        }
        String businessName = request.getBusinessName();
        if (request.getUserType() == UserTypeEnum.EXPERT && !StringUtils.hasText(businessName)) {
            throw new CustomException(ErrorCode.EXPERT_BUSINESS_NAME_REQUIRED);
        }
        if (request.getUserType() == UserTypeEnum.EXPERT) {
            businessName = businessName.trim();
        }
        String email = request.getEmail().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.builder()
                .userType(request.getUserType())
                .name(request.getName())
                .email(email)
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();
        User savedUser = userRepository.save(user);

        if (savedUser.getUserType() == UserTypeEnum.EXPERT) {
            expertProfileProvisioningService.createDefaultProfile(savedUser, businessName);
        }
    }

    @Transactional
    public AuthTokens login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        refreshTokenRepository.deleteByUserId(user.getId());
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUserType());
        String refreshToken = createAndSaveRefreshToken(user.getId());

        Long clientInfoId = null;
        if (user.getUserType() == UserTypeEnum.CLIENT) {
            clientInfoId = clientRepository.findByUser_Id(user.getId())
                    .map(c -> c.getId())
                    .orElse(null);
        }

        return new AuthTokens(accessToken, refreshToken, user.getUserType().name(), clientInfoId);
    }

    @Transactional
    public AuthTokens refresh(Long userId) {
        RefreshToken stored = refreshTokenRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (stored.isExpired()) {
            refreshTokenRepository.delete(stored);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        refreshTokenRepository.delete(stored);

        String newAccessToken = jwtUtil.generateToken(user.getId(), user.getUserType());
        String newRefreshToken = createAndSaveRefreshToken(user.getId());

        return new AuthTokens(newAccessToken, newRefreshToken, user.getUserType().name(), null);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    private String createAndSaveRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000);

        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .token(token)
                .expiresAt(expiresAt)
                .build());

        return token;
    }
}
