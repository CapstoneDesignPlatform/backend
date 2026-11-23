package com.capdi.backend.domain.auth.repository;

import com.capdi.backend.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByUserId(Long userId);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByUserId(Long userId);

    /** 사용자당 1행 정책: 행이 있으면 해시와 만료 시각만 덮어쓴다. 동시 로그인에도 중복 행이 생기지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO refresh_tokens (user_id, token_hash, expires_at) "
            + "VALUES (:userId, :tokenHash, :expiresAt) AS new "
            + "ON DUPLICATE KEY UPDATE token_hash = new.token_hash, expires_at = new.expires_at",
            nativeQuery = true)
    int upsertForUser(@Param("userId") Long userId,
                      @Param("tokenHash") String tokenHash,
                      @Param("expiresAt") LocalDateTime expiresAt);

    /** 유효한 기존 해시일 때만 교체한다. 반환값이 1이 아니면 이미 교체됐거나 만료된 토큰이다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.tokenHash = :newHash, r.expiresAt = :newExpiresAt "
            + "WHERE r.tokenHash = :oldHash AND r.expiresAt > :now")
    int rotate(@Param("oldHash") String oldHash,
               @Param("newHash") String newHash,
               @Param("newExpiresAt") LocalDateTime newExpiresAt,
               @Param("now") LocalDateTime now);
}
