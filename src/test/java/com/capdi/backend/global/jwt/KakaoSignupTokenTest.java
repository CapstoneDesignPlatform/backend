package com.capdi.backend.global.jwt;

import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.*;

class KakaoSignupTokenTest {
    private static final String SECRET = "isolated-kakao-signup-secret-at-least-32-bytes";
    private final JwtUtil jwt = new JwtUtil(SECRET, 60000);

    @Test
    void signupTokenCannotAuthenticateAsAccessToken() {
        String token = jwt.generateKakaoSignupToken("kakao-72");
        assertThat(jwt.getKakaoIdFromSignupToken(token)).isEqualTo("kakao-72");
        assertThat(jwt.validateToken(token)).isFalse();
    }

    @Test
    void accessTokenCannotAuthorizeKakaoSignup() {
        String token = jwt.generateToken(72L, UserTypeEnum.EXPERT);
        CustomException failure = catchThrowableOfType(() -> jwt.getKakaoIdFromSignupToken(token), CustomException.class);
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void expiredSignupTokenRetainsExpiredTokenError() {
        String token = Jwts.builder().subject("kakao-72").claim("typ", "KAKAO_SIGNUP")
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(JwtUtil.deriveSignupKey(SECRET)).compact();
        CustomException failure = catchThrowableOfType(() -> jwt.getKakaoIdFromSignupToken(token), CustomException.class);
        assertThat(failure.getErrorCode()).isEqualTo(ErrorCode.EXPIRED_TOKEN);
    }
}
