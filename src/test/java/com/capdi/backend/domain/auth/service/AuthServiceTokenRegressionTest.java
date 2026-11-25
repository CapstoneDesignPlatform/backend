package com.capdi.backend.domain.auth.service;

import com.capdi.backend.domain.auth.client.KakaoOidcClient;
import com.capdi.backend.domain.auth.dto.*;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTokenRegressionTest {
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final ExpertProfileProvisioningService profiles = mock(ExpertProfileProvisioningService.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final KakaoOidcClient kakao = mock(KakaoOidcClient.class);
    private final JwtUtil jwt = new JwtUtil("isolated-auth-regression-secret-at-least-32-bytes", 60000);
    private final AuthService service = new AuthService(users, refreshTokens, clients, profiles, passwords, jwt, kakao);
    private final User expert = User.builder().id(72L).userType(UserTypeEnum.EXPERT)
            .email("expert@example.test").password("encoded-password").build();

    @BeforeEach
    void configureExpiry() {
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 3600000L);
    }

    @Test
    void passwordLoginStoresOnlyHashAndReturnsUsableAccessToken() {
        when(users.findByEmail("expert@example.test")).thenReturn(Optional.of(expert));
        when(passwords.matches("password", "encoded-password")).thenReturn(true);
        LoginRequest request = new ObjectMapper().convertValue(
                Map.of("email", "EXPERT@example.test", "password", "password"), LoginRequest.class);

        AuthTokens result = service.login(request);

        assertIssuedTokens(result.accessToken(), result.refreshToken());
    }

    @Test
    void registeredKakaoLoginUsesSameHashedRefreshTokenStorage() {
        when(kakao.fetchVerifiedSubject("code", "verifier", "nonce")).thenReturn("kakao-72");
        when(users.findBySocialProviderAndSocialId(SocialProviderEnum.KAKAO, "kakao-72"))
                .thenReturn(Optional.of(expert));

        KakaoLoginResponse result = service.kakaoLogin(new KakaoLoginRequest("code", "verifier", "nonce"));

        assertThat(result.registered()).isTrue();
        assertThat(result.signupToken()).isNull();
        assertIssuedTokens(result.accessToken(), result.refreshToken());
    }

    @Test
    void unregisteredKakaoLoginReturnsOnlySignupTokenWithoutCreatingSession() {
        when(kakao.fetchVerifiedSubject("code", "verifier", "nonce")).thenReturn("new-kakao");
        when(users.findBySocialProviderAndSocialId(SocialProviderEnum.KAKAO, "new-kakao"))
                .thenReturn(Optional.empty());

        KakaoLoginResponse result = service.kakaoLogin(new KakaoLoginRequest("code", "verifier", "nonce"));

        assertThat(result.registered()).isFalse();
        assertThat(result.accessToken()).isNull();
        assertThat(result.refreshToken()).isNull();
        assertThat(jwt.getKakaoIdFromSignupToken(result.signupToken())).isEqualTo("new-kakao");
        assertThat(jwt.validateToken(result.signupToken())).isFalse();
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void kakaoSignupProvisionsExpertAndIssuesHashedRefreshToken() {
        when(users.saveAndFlush(any(User.class))).thenReturn(expert);

        AuthTokens result = service.kakaoSignup(new KakaoSignupRequest(
                jwt.generateKakaoSignupToken("new-kakao"), UserTypeEnum.EXPERT,
                "Expert", "EXPERT@example.test", "01000000000", " Company "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getSocialProvider()).isEqualTo(SocialProviderEnum.KAKAO);
        assertThat(saved.getValue().getSocialId()).isEqualTo("new-kakao");
        assertThat(saved.getValue().getEmail()).isEqualTo("expert@example.test");
        assertThat(saved.getValue().getPassword()).isNull();
        verify(profiles).createDefaultProfile(expert, "Company");
        assertIssuedTokens(result.accessToken(), result.refreshToken());
    }

    @Test
    void refreshReturnsExactlyTheTokenWhoseHashWasAtomicallyRotated() {
        String raw = "previous-refresh-token";
        String oldHash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokens.findByTokenHash(oldHash)).thenReturn(Optional.of(stored(raw, LocalDateTime.now().plusHours(1))));
        when(users.findById(expert.getId())).thenReturn(Optional.of(expert));
        when(refreshTokens.rotate(eq(oldHash), anyString(), any(), any())).thenReturn(1);

        AuthTokens result = service.refresh(raw);

        assertThat(result.refreshToken()).isNotEqualTo(raw);
        verify(refreshTokens).rotate(eq(oldHash), eq(RefreshTokenHasher.sha256Hex(result.refreshToken())), any(), any());
        assertThat(jwt.getUserId(result.accessToken())).isEqualTo(expert.getId());
        verify(refreshTokens, never()).upsertForUser(anyLong(), anyString(), any());
        verify(refreshTokens, never()).deleteByUserId(anyLong());
    }

    @Test
    void refreshRejectsRotationLostToAnotherRequestWithoutIssuingNewSession() {
        String raw = "racing-refresh-token";
        when(refreshTokens.findByTokenHash(RefreshTokenHasher.sha256Hex(raw)))
                .thenReturn(Optional.of(stored(raw, LocalDateTime.now().plusHours(1))));
        when(users.findById(expert.getId())).thenReturn(Optional.of(expert));
        when(refreshTokens.rotate(anyString(), anyString(), any(), any())).thenReturn(0);

        CustomException failure = catchThrowableOfType(() -> service.refresh(raw), CustomException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        verify(refreshTokens, never()).upsertForUser(anyLong(), anyString(), any());
    }

    @Test
    void reusedOrRevokedRefreshTokenIsRejected() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        CustomException failure = catchThrowableOfType(() -> service.refresh("revoked-token"), CustomException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        verify(refreshTokens, never()).rotate(anyString(), anyString(), any(), any());
        verify(refreshTokens, never()).upsertForUser(anyLong(), anyString(), any());
    }

    @Test
    void expiredRefreshTokenDeletesOnlyItsHash() {
        String raw = "expired-token";
        String hash = RefreshTokenHasher.sha256Hex(raw);
        when(refreshTokens.findByTokenHash(hash)).thenReturn(Optional.of(stored(raw, LocalDateTime.now().minusHours(1))));

        CustomException failure = catchThrowableOfType(() -> service.refresh(raw), CustomException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);
        verify(refreshTokens).deleteByTokenHash(hash);
        verify(refreshTokens, never()).deleteByUserId(anyLong());
        verify(refreshTokens, never()).rotate(anyString(), anyString(), any(), any());
    }

    @Test
    void logoutCanRevokeByRawTokenWithoutAccessToken() {
        service.logout(null, "logout-token");

        verify(refreshTokens).deleteByTokenHash(RefreshTokenHasher.sha256Hex("logout-token"));
        verify(refreshTokens, never()).deleteByUserId(anyLong());
    }

    @Test
    void authenticatedLogoutCanRevokeByUserWithoutRefreshBody() {
        service.logout(expert.getId(), null);

        verify(refreshTokens).deleteByUserId(expert.getId());
        verify(refreshTokens, never()).deleteByTokenHash(anyString());
    }

    private RefreshToken stored(String raw, LocalDateTime expiry) {
        return RefreshToken.builder().userId(expert.getId())
                .tokenHash(RefreshTokenHasher.sha256Hex(raw)).expiresAt(expiry).build();
    }

    private void assertIssuedTokens(String access, String rawRefresh) {
        assertThat(jwt.validateToken(access)).isTrue();
        assertThat(jwt.getUserId(access)).isEqualTo(expert.getId());
        assertThat(jwt.getRole(access)).isEqualTo("EXPERT");
        assertThat(rawRefresh).matches("[A-Za-z0-9_-]{43}");
        ArgumentCaptor<LocalDateTime> expiry = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refreshTokens).upsertForUser(eq(expert.getId()), eq(RefreshTokenHasher.sha256Hex(rawRefresh)), expiry.capture());
        assertThat(expiry.getValue()).isAfter(LocalDateTime.now().plusMinutes(59));
        verify(refreshTokens, never()).save(any());
        verify(refreshTokens, never()).deleteByUserId(anyLong());
    }
}
