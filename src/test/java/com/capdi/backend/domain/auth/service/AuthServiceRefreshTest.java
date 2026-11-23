package com.capdi.backend.domain.auth.service;

import com.capdi.backend.domain.auth.dto.AuthTokens;
import com.capdi.backend.domain.auth.dto.LoginRequest;
import com.capdi.backend.domain.auth.entity.RefreshToken;
import com.capdi.backend.domain.auth.repository.RefreshTokenRepository;
import com.capdi.backend.domain.client.entity.ClientInfo;
import com.capdi.backend.domain.client.repository.ClientRepository;
import com.capdi.backend.domain.expert.service.ExpertProfileProvisioningService;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.jwt.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.capdi.backend.domain.auth.service.RefreshTokenHasher.sha256Hex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AuthServiceRefreshTest {

    UserRepository users = mock(UserRepository.class);
    RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    ClientRepository clients = mock(ClientRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    JwtUtil jwt = new JwtUtil(UUID.randomUUID() + "-auth-service-test-only", 60000);
    AuthService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, tokens, clients, mock(ExpertProfileProvisioningService.class), encoder, jwt);
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 604800000L);
        user = User.builder().userType(UserTypeEnum.CLIENT).name("client").email("client@example.test")
                .phone("01000000000").password("encoded").build();
        ReflectionTestUtils.setField(user, "id", 7L);
        ClientInfo clientInfo = mock(ClientInfo.class);
        when(clientInfo.getId()).thenReturn(55L);
        when(clients.findByUser_Id(7L)).thenReturn(Optional.of(clientInfo));
        when(users.findById(7L)).thenReturn(Optional.of(user));
    }

    RefreshToken stored(String raw, LocalDateTime expiresAt) {
        return RefreshToken.builder().id(1L).userId(7L).tokenHash(sha256Hex(raw)).expiresAt(expiresAt).build();
    }

    @Test
    void refreshRotatesAndReturnsNewTokensWithClientInfoId() {
        when(tokens.findByTokenHash(sha256Hex("raw-1"))).thenReturn(Optional.of(stored("raw-1", LocalDateTime.now().plusDays(1))));
        when(tokens.rotate(anyString(), anyString(), any(), any())).thenReturn(1);

        AuthTokens result = service.refresh("raw-1");

        assertThat(result.refreshToken()).isNotEqualTo("raw-1").hasSize(43);
        verify(tokens).rotate(eq(sha256Hex("raw-1")), eq(sha256Hex(result.refreshToken())), any(), any());
        assertThat(result.clientInfoId()).isEqualTo(55L);
        assertThat(jwt.getUserId(result.accessToken())).isEqualTo(7L);
    }

    @Test
    void refreshWithUnknownTokenThrowsNotFound() {
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        CustomException failure = catchThrowableOfType(() -> service.refresh("unknown"), CustomException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
        verify(tokens, never()).rotate(anyString(), anyString(), any(), any());
    }

    @Test
    void refreshWithExpiredTokenDeletesAndThrowsExpired() {
        RefreshToken expired = stored("raw-1", LocalDateTime.now().minusSeconds(1));
        when(tokens.findByTokenHash(sha256Hex("raw-1"))).thenReturn(Optional.of(expired));

        CustomException failure = catchThrowableOfType(() -> service.refresh("raw-1"), CustomException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_EXPIRED);
        verify(tokens).delete(expired);
    }

    @Test
    void refreshWhenRotateLosesRaceThrowsNotFound() {
        when(tokens.findByTokenHash(sha256Hex("raw-1"))).thenReturn(Optional.of(stored("raw-1", LocalDateTime.now().plusDays(1))));
        when(tokens.rotate(anyString(), anyString(), any(), any())).thenReturn(0);

        CustomException failure = catchThrowableOfType(() -> service.refresh("raw-1"), CustomException.class);

        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    @Test
    void loginStoresOnlyHashNeverRawToken() {
        LoginRequest request = new LoginRequest();
        ReflectionTestUtils.setField(request, "email", "client@example.test");
        ReflectionTestUtils.setField(request, "password", "pw");
        when(users.findByEmail("client@example.test")).thenReturn(Optional.of(user));
        when(encoder.matches("pw", "encoded")).thenReturn(true);

        AuthTokens result = service.login(request);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(tokens).upsertForUser(eq(7L), hash.capture(), any());
        assertThat(hash.getValue()).isEqualTo(sha256Hex(result.refreshToken())).isNotEqualTo(result.refreshToken());
        verify(tokens, never()).deleteByUserId(anyLong());
    }

    @Test
    void logoutWithRefreshTokenDeletesByHash() {
        service.logout(null, "raw-1");

        verify(tokens).deleteByTokenHash(sha256Hex("raw-1"));
        verify(tokens, never()).deleteByUserId(anyLong());
    }

    @Test
    void logoutWithUserIdDeletesByUserId() {
        service.logout(7L, null);

        verify(tokens).deleteByUserId(7L);
        verify(tokens, never()).deleteByTokenHash(anyString());
    }
}
