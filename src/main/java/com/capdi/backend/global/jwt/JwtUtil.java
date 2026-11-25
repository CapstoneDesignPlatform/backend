package com.capdi.backend.global.jwt;

import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    static final long SIGNUP_TOKEN_TTL_MS = 600_000;
    private static final String TYPE_CLAIM = "typ";
    private static final String KAKAO_SIGNUP_TYPE = "KAKAO_SIGNUP";

    private final SecretKey secretKey;
    // 가입 대기 토큰 전용 키: access 토큰 검증(validateToken)을 절대 통과하지 못하게 분리
    private final SecretKey signupKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.signupKey = deriveSignupKey(secret);
        this.expirationMs = expirationMs;
    }

    static SecretKey deriveSignupKey(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((secret + ":kakao-signup").getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public String generateKakaoSignupToken(String kakaoId) {
        return Jwts.builder()
                .subject(kakaoId)
                .claim(TYPE_CLAIM, KAKAO_SIGNUP_TYPE)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + SIGNUP_TOKEN_TTL_MS))
                .signWith(signupKey)
                .compact();
    }

    public String getKakaoIdFromSignupToken(String token) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(signupKey).build().parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        if (!KAKAO_SIGNUP_TYPE.equals(claims.get(TYPE_CLAIM, String.class)) || claims.getSubject() == null) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
        return claims.getSubject();
    }

    public String generateToken(Long userId, UserTypeEnum userType) {
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", userType.name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public Long getUserIdIgnoringExpiry(String token) {
        return Long.parseLong(parseClaimsIgnoringExpiry(token).getSubject());
    }

    public String getRoleIgnoringExpiry(String token) {
        return parseClaimsIgnoringExpiry(token).get("role", String.class);
    }

    private Claims parseClaimsIgnoringExpiry(String token) {
        try {
            return parseClaims(token);
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}