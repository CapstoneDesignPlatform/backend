package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.client.GoogleVisionOcrClient;
import com.capdi.backend.domain.expert.client.NtsHttpTransport;
import com.capdi.backend.domain.expert.entity.BusinessVerification;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.internal.BasicAttributeMapping;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.Types;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/** Full production entity scan + real Flyway MySQL schema, never an existing Compose database. */
@Tag("mysql-integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.location=classpath:/application.yml", "spring.profiles.active=issue52-schema-validation",
        "server.address=127.0.0.1", "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false", "spring.flyway.validate-on-migrate=true",
        "nts.business.enabled=false"
})
class FullSchemaValidationMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi52_full_schema_test").withUsername("schema52_test")
            .withPassword(UUID.randomUUID().toString()).withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withLabel("capdi.test.scope", "issue52-full-schema-validation");
    @TempDir static Path uploads;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("file.storage.upload-dir", () -> uploads.toString());
        registry.add("jwt.secret", () -> UUID.randomUUID() + "-schema-test-only");
    }
    @Autowired ApplicationContext context;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @MockitoBean GoogleVisionOcrClient vision;
    @MockitoBean NtsHttpTransport nts;

    @Test void fullApplicationContextValidatesEveryEntityAgainstFlywayV1ThroughV16() throws Exception {
        assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(context.getBeansOfType(Repository.class)).hasSize(14);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        var discovered = scanner.findCandidateComponents("com.capdi.backend").stream()
                .map(definition -> definition.getBeanClassName()).toList();
        var mapped = entityManagerFactory.getMetamodel().getEntities().stream()
                .map(type -> type.getJavaType().getName()).toList();
        assertThat(discovered).isNotEmpty();assertThat(mapped).containsExactlyInAnyOrderElementsOf(discovered);
        assertThat(Arrays.stream(flyway.info().applied()).map(info -> info.getVersion().getVersion()).toList())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 16).mapToObj(String::valueOf).toList());
        flyway.validate();
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("capdi52_full_schema_test");
            assertThat(connection.getMetaData().getURL()).startsWith(MYSQL.getJdbcUrl());
        }
        verifyNoInteractions(vision, nts);
    }

    @Test void sha256HasCharJdbcMappingAndExistingChar64Column() throws Exception {
        var sessionFactory = entityManagerFactory.unwrap(SessionFactoryImplementor.class);
        var mapping = (BasicAttributeMapping) sessionFactory.getMappingMetamodel()
                .getEntityDescriptor(BusinessVerification.class).findAttributeMapping("fileSha256");
        assertThat(mapping.getJdbcMapping().getJdbcType().getJdbcTypeCode()).isEqualTo(Types.CHAR);
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
                "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'expert_business_verifications' AND COLUMN_NAME = 'file_sha256'")) {
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();assertThat(result.getString(1)).isEqualTo("char");
                assertThat(result.getInt(2)).isEqualTo(64);assertThat(result.next()).isFalse();
            }
        }
    }
}
