package com.capdi.backend.domain.auth.client;

import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KakaoOidcClient {

    private static final String ISSUER = "https://kauth.kakao.com";
    private static final String TOKEN_URI = ISSUER + "/oauth/token";
    private static final String JWKS_URI = ISSUER + "/.well-known/jwks.json";

    private final RestTemplate restTemplate;
    private final NimbusJwtDecoder decoder;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public KakaoOidcClient(RestTemplateBuilder builder,
                           @Value("${kakao.client-id}") String clientId,
                           @Value("${kakao.client-secret}") String clientSecret,
                           @Value("${kakao.redirect-uri}") String redirectUri) {
        this.restTemplate = builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(3)).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        // 기본 RS256 전용, JWKS는 기본 캐시(5분)로 재사용
        this.decoder = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).restOperations(restTemplate).build();
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(clientId))));
    }

    public String fetchVerifiedSubject(String code, String codeVerifier, String nonce) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret) || !StringUtils.hasText(redirectUri)) {
            log.warn("Kakao login is not configured");
            throw new CustomException(ErrorCode.KAKAO_UNAVAILABLE);
        }

        Jwt jwt;
        try {
            jwt = decoder.decode(requestIdToken(code, codeVerifier));
        } catch (BadJwtException e) {
            log.warn("Kakao id_token rejected: {}", e.getMessage());
            throw new CustomException(ErrorCode.KAKAO_LOGIN_FAILED);
        } catch (JwtException e) {
            log.warn("Kakao id_token could not be verified: {}", e.getMessage());
            throw new CustomException(ErrorCode.KAKAO_UNAVAILABLE);
        }

        if (!nonce.equals(jwt.getClaimAsString("nonce"))) {
            log.warn("Kakao id_token nonce mismatch");
            throw new CustomException(ErrorCode.KAKAO_LOGIN_FAILED);
        }
        return jwt.getSubject();
    }

    private String requestIdToken(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        form.add("code_verifier", codeVerifier);

        Map<?, ?> body;
        try {
            body = restTemplate.postForObject(TOKEN_URI, form, Map.class);
        } catch (HttpClientErrorException e) {
            // error/error_code로 재사용 code(KOE320)와 설정 오류(KOE006 등)를 구분. error_description은 code 값을 담을 수 있어 남기지 않는다
            Map<?, ?> error = kakaoErrorBody(e);
            log.warn("Kakao token request rejected: {} error={} error_code={}",
                    e.getStatusCode(), error.get("error"), error.get("error_code"));
            throw new CustomException(ErrorCode.KAKAO_LOGIN_FAILED);
        } catch (RestClientException e) {
            log.warn("Kakao token request failed: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.KAKAO_UNAVAILABLE);
        }

        Object idToken = body == null ? null : body.get("id_token");
        if (!(idToken instanceof String token) || token.isBlank()) {
            log.warn("Kakao token response has no id_token (is OpenID Connect enabled?)");
            throw new CustomException(ErrorCode.KAKAO_LOGIN_FAILED);
        }
        return token;
    }

    private static Map<?, ?> kakaoErrorBody(HttpClientErrorException e) {
        try {
            Map<?, ?> body = e.getResponseBodyAs(Map.class);
            return body == null ? Map.of() : body;
        } catch (RuntimeException notJson) {
            return Map.of();
        }
    }
}
