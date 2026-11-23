package com.capdi.backend.domain.auth;

import com.capdi.backend.domain.auth.repository.RefreshTokenRepository;
import com.capdi.backend.domain.auth.service.RefreshTokenHasher;
import com.capdi.backend.domain.expert.client.GoogleVisionOcrClient;
import com.capdi.backend.domain.expert.client.NtsHttpTransport;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real MySQL + Flyway: V28 data migration, per-user upsert, and conditional rotation. */
@Tag("mysql-integration")
@Testcontainers
@SpringBootTest(properties = {
        "spring.config.location=classpath:/application.yml", "spring.profiles.active=refresh-token-mysql-test",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false", "nts.business.enabled=false"
})
class RefreshTokenMySqlIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_refresh_token_test").withUsername("refresh_test")
            .withPassword(UUID.randomUUID().toString()).withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withLabel("capdi.test.scope", "refresh-token-hardening");
    @TempDir static Path uploads;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("file.storage.upload-dir", () -> uploads.toString());
        registry.add("jwt.secret", () -> UUID.randomUUID() + "-refresh-test-only");
    }

    @Autowired RefreshTokenRepository tokens;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @MockitoBean GoogleVisionOcrClient vision;
    @MockitoBean NtsHttpTransport nts;
    TransactionTemplate tx;
    Long userId;

    @BeforeEach void createUser() {
        tx = new TransactionTemplate(manager);
        userId = tx.execute(status -> users.saveAndFlush(User.builder().userType(UserTypeEnum.CLIENT)
                .name("refresh test").email(UUID.randomUUID() + "@example.test").phone("01000000000").build()).getId());
    }

    int rowsForUser(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", Integer.class, id);
    }

    @Test void v28HashesExistingTokensAndDedupesUsers() {
        String db = "v28_check";
        var root = new DriverManagerDataSource("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/",
                "root", MYSQL.getPassword());
        new JdbcTemplate(root).execute("CREATE DATABASE " + db);
        var target = new DriverManagerDataSource("jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306) + "/" + db,
                "root", MYSQL.getPassword());
        Flyway.configure().dataSource(target).locations("classpath:db/migration").target("27").load().migrate();
        var legacy = new JdbcTemplate(target);
        String insertUser = "INSERT INTO users (user_type, name, email, phone, created_at, updated_at) VALUES ('CLIENT', ?, ?, '01000000000', NOW(6), NOW(6))";
        legacy.update(insertUser, "u1", "u1@example.test");
        legacy.update(insertUser, "u2", "u2@example.test");
        Long u1 = legacy.queryForObject("SELECT id FROM users WHERE email = 'u1@example.test'", Long.class);
        Long u2 = legacy.queryForObject("SELECT id FROM users WHERE email = 'u2@example.test'", Long.class);
        String insertToken = "INSERT INTO refresh_tokens (user_id, token, expires_at) VALUES (?, ?, NOW(6) + INTERVAL 7 DAY)";
        legacy.update(insertToken, u1, "old-a");
        legacy.update(insertToken, u1, "plain-b");
        legacy.update(insertToken, u2, "plain-c");

        Flyway.configure().dataSource(target).locations("classpath:db/migration").load().migrate();

        assertThat(legacy.queryForList("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, u1))
                .containsExactly(RefreshTokenHasher.sha256Hex("plain-b"));
        assertThat(legacy.queryForList("SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, u2))
                .containsExactly(RefreshTokenHasher.sha256Hex("plain-c"));
        assertThat(legacy.queryForList("SELECT DISTINCT INDEX_NAME FROM information_schema.statistics "
                + "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = 'refresh_tokens' AND NON_UNIQUE = 0", String.class, db))
                .contains("uk_refresh_tokens_token_hash", "uk_refresh_tokens_user_id");
    }

    @Test void upsertKeepsSingleRowPerUser() {
        LocalDateTime expires = LocalDateTime.now().plusDays(7);
        tx.executeWithoutResult(s -> tokens.upsertForUser(userId, "h1", expires));
        tx.executeWithoutResult(s -> tokens.upsertForUser(userId, "h2", expires));

        assertThat(rowsForUser(userId)).isEqualTo(1);
        assertThat(tokens.findByTokenHash("h2")).isPresent();
        assertThat(tokens.findByTokenHash("h1")).isEmpty();
    }

    @Test void rotateSucceedsOnceForValidToken() {
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(s -> tokens.upsertForUser(userId, "r1", now.plusDays(7)));

        Integer updated = tx.execute(s -> tokens.rotate("r1", "r2", now.plusDays(7), now));

        assertThat(updated).isEqualTo(1);
        assertThat(tokens.findByTokenHash("r2")).isPresent();
    }

    @Test void rotateRejectsOldHashAfterRotation() {
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(s -> tokens.upsertForUser(userId, "o1", now.plusDays(7)));
        tx.execute(s -> tokens.rotate("o1", "o2", now.plusDays(7), now));

        Integer reused = tx.execute(s -> tokens.rotate("o1", "o3", now.plusDays(7), now));

        assertThat(reused).isZero();
        assertThat(tokens.findByTokenHash("o2")).isPresent();
    }

    @Test void rotateRejectsExpiredToken() {
        LocalDateTime now = LocalDateTime.now();
        tx.executeWithoutResult(s -> tokens.upsertForUser(userId, "e1", now.minusSeconds(1)));

        Integer updated = tx.execute(s -> tokens.rotate("e1", "e2", now.plusDays(7), now));

        assertThat(updated).isZero();
    }
}
