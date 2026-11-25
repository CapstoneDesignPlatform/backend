package com.capdi.backend.domain.auth.service;

import com.capdi.backend.domain.auth.client.KakaoOidcClient;
import com.capdi.backend.domain.auth.dto.AuthTokens;
import com.capdi.backend.domain.auth.dto.KakaoLoginRequest;
import com.capdi.backend.domain.auth.dto.KakaoLoginResponse;
import com.capdi.backend.domain.auth.dto.KakaoSignupRequest;
import com.capdi.backend.domain.auth.dto.LoginRequest;
import com.capdi.backend.domain.auth.dto.SignupRequest;
import com.capdi.backend.domain.auth.entity.RefreshToken;
import com.capdi.backend.domain.auth.repository.RefreshTokenRepository;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.expert.service.ExpertProfileProvisioningService;
import com.capdi.backend.domain.user.entity.SocialProviderEnum;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
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
    private final KakaoOidcClient kakaoOidcClient;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpirationMs;

    @Transactional
    public void signup(SignupRequest request) {
        String businessName = validateSignupRole(request.getUserType(), request.getBusinessName());
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

        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.SOCIAL_LOGIN_ONLY);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        return issueTokens(user);
    }

    /**
     * 클라이언트가 보낸 refresh 토큰 원문을 검증하고 새 토큰으로 교체한다.
     * 교체는 조건부 UPDATE로 원자적으로 처리되어, 같은 토큰으로 동시에 요청하면 하나만 성공한다.
     */
    // 만료 토큰 삭제가 REFRESH_TOKEN_EXPIRED 예외로 롤백되지 않도록 CustomException은 커밋한다.
    // 이 메서드에서 CustomException 전에 일어나는 쓰기는 그 삭제뿐이다.
    @Transactional(noRollbackFor = CustomException.class)
    public AuthTokens refresh(String rawRefreshToken) {
        LocalDateTime now = LocalDateTime.now();
        String oldHash = RefreshTokenHasher.sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(oldHash)
                .orElseThrow(() -> new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        if (!stored.getExpiresAt().isAfter(now)) {
            // id가 아닌 해시 조건으로 지운다. 그 사이 로그인으로 같은 행에 새 토큰이 써졌으면 건드리지 않고,
            // 동시 요청이 먼저 지웠으면 0건 삭제로 끝난다(엔티티 delete는 이 경우 낙관적 락 예외로 500).
            refreshTokenRepository.deleteByTokenHash(oldHash);
            throw new CustomException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        String newRawToken = RefreshTokenHasher.newRawToken();
        int rotated = refreshTokenRepository.rotate(
                oldHash, RefreshTokenHasher.sha256Hex(newRawToken), refreshExpiresAt(now), now);
        if (rotated != 1) {
            throw new CustomException(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        }

        return issueTokens(user, newRawToken);
    }

    /** refresh 토큰이 있으면 그 토큰을, 인증된 사용자가 있으면 그 사용자의 토큰을 폐기한다. */
    @Transactional
    public void logout(Long userId, String rawRefreshToken) {
        if (StringUtils.hasText(rawRefreshToken)) {
            refreshTokenRepository.deleteByTokenHash(RefreshTokenHasher.sha256Hex(rawRefreshToken));
        }
        if (userId != null) {
            refreshTokenRepository.deleteByUserId(userId);
        }
    }

    // shortcut: 카카오 HTTP 호출 동안 트랜잭션(DB 커넥션)을 잡고 있음, 동시 카카오 로그인이 커넥션 풀을 위협하면 호출을 트랜잭션 밖으로 분리
    @Transactional
    public KakaoLoginResponse kakaoLogin(KakaoLoginRequest request) {
        long start = System.nanoTime();
        String kakaoId = kakaoOidcClient.fetchVerifiedSubject(request.code(), request.codeVerifier(), request.nonce());
        long kakaoDone = System.nanoTime();
        KakaoLoginResponse response = userRepository.findBySocialProviderAndSocialId(SocialProviderEnum.KAKAO, kakaoId)
                .map(user -> KakaoLoginResponse.registered(issueTokens(user)))
                .orElseGet(() -> KakaoLoginResponse.signupRequired(jwtUtil.generateKakaoSignupToken(kakaoId)));
        long end = System.nanoTime();
        // db는 커밋 직전까지: 커밋 시간은 메서드 반환 뒤라 포함되지 않음
        log.info("Kakao login timing: kakao={}ms db={}ms total={}ms registered={}",
                (kakaoDone - start) / 1_000_000, (end - kakaoDone) / 1_000_000, (end - start) / 1_000_000,
                response.registered());
        return response;
    }

    @Transactional
    public AuthTokens kakaoSignup(KakaoSignupRequest request) {
        String kakaoId = jwtUtil.getKakaoIdFromSignupToken(request.signupToken());
        String businessName = validateSignupRole(request.userType(), request.businessName());
        if (userRepository.existsBySocialProviderAndSocialId(SocialProviderEnum.KAKAO, kakaoId)) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
        }
        String email = request.email().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user;
        try {
            user = userRepository.saveAndFlush(User.builder()
                    .userType(request.userType())
                    .name(request.name())
                    .email(email)
                    .phone(request.phone())
                    .socialProvider(SocialProviderEnum.KAKAO)
                    .socialId(kakaoId)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 같은 카카오 계정의 동시 가입: 위 중복 검사를 둘 다 통과한 경우 유니크 제약이 막는다
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_EXISTS);
        }

        if (user.getUserType() == UserTypeEnum.EXPERT) {
            expertProfileProvisioningService.createDefaultProfile(user, businessName);
        }
        return issueTokens(user);
    }

    private LocalDateTime refreshExpiresAt(LocalDateTime now) {
        return now.plusSeconds(refreshExpirationMs / 1000);
    }

    // ADMIN 가입 차단, EXPERT 업체명 필수. EXPERT면 trim한 업체명을 돌려준다.
    private String validateSignupRole(UserTypeEnum userType, String businessName) {
        if (userType == UserTypeEnum.ADMIN) {
            throw new CustomException(ErrorCode.INVALID_SIGNUP_ROLE);
        }
        if (userType == UserTypeEnum.EXPERT && !StringUtils.hasText(businessName)) {
            throw new CustomException(ErrorCode.EXPERT_BUSINESS_NAME_REQUIRED);
        }
        return userType == UserTypeEnum.EXPERT ? businessName.trim() : businessName;
    }

    private AuthTokens issueTokens(User user) {
        return issueTokens(user, createAndSaveRefreshToken(user.getId()));
    }

    private AuthTokens issueTokens(User user, String rawRefreshToken) {
        String accessToken = jwtUtil.generateToken(user.getId(), user.getUserType());

        Long clientInfoId = null;
        if (user.getUserType() == UserTypeEnum.CLIENT) {
            clientInfoId = clientRepository.findByUser_Id(user.getId())
                    .map(c -> c.getId())
                    .orElse(null);
        }

        return new AuthTokens(accessToken, rawRefreshToken, user.getUserType().name(), clientInfoId);
    }

    private String createAndSaveRefreshToken(Long userId) {
        String token = RefreshTokenHasher.newRawToken();
        refreshTokenRepository.upsertForUser(
                userId, RefreshTokenHasher.sha256Hex(token), refreshExpiresAt(LocalDateTime.now()));

        return token;
    }
}
