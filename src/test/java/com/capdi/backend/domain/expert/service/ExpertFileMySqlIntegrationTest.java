package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertCertificateUpdateRequest;
import com.capdi.backend.domain.expert.entity.CertificateTypeCodeEnum;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.user.entity.User;
import com.capdi.backend.domain.user.entity.UserTypeEnum;
import com.capdi.backend.domain.user.repository.UserRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.jwt.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("mysql-integration")
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=none"
})
class ExpertFileMySqlIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("capdi_issue50_test")
            .withUsername("issue50_test")
            .withPassword(UUID.randomUUID().toString())
            .withTmpFs(Map.of("/var/lib/mysql", "rw"))
            .withLabel("capdi.test.scope", "issue50-isolated");

    @TempDir
    static Path root;

    @DynamicPropertySource
    static void isolatedProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("file.storage.upload-dir", () -> root.toString());
    }

    @Autowired ExpertFileService service;
    @Autowired ExpertCertificateService certificateService;
    @Autowired ExpertFileRepository files;
    @Autowired ExpertCertificateRepository certificates;
    @Autowired ExpertProfileRepository profiles;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager manager;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper mapper;
    Long userId;
    TransactionTemplate transaction;
    byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @BeforeEach
    void createFixtureInIsolatedDatabase() throws Exception {
        assertThat(manager).isInstanceOf(JpaTransactionManager.class);
        try (var connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("capdi_issue50_test");
            assertThat(connection.getMetaData().getURL()).startsWith(MYSQL.getJdbcUrl());
        }
        transaction = new TransactionTemplate(manager);
        userId = transaction.execute(status -> {
            User user = users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT)
                    .name("test expert").email(UUID.randomUUID() + "@example.test").phone("01000000000").build());
            profiles.saveAndFlush(ExpertProfile.createDefault(user, "test company"));
            return user.getId();
        });
    }

    Long upload() {
        return service.uploadFile(userId, new MockMultipartFile("file", "sample.png", "image/png", png),
                "CERTIFICATE").getFile().getId();
    }

    Path filePath(Long id) {
        return Path.of(files.findById(id).orElseThrow().getFilePath());
    }

    long physicalCount() throws Exception {
        try (var entries = Files.list(root)) {
            return entries.count();
        }
    }

    @Test
    void flywayAppliesAllTwelveMigrations() {
        assertThat(MYSQL.getContainerInfo().getHostConfig().getTmpFs()).containsKey("/var/lib/mysql");
        assertThat(MYSQL.getContainerInfo().getConfig().getLabels()).doesNotContainKey("com.docker.compose.project");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).hasSize(12);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("12");
        flyway.validate();
        System.out.println("ISOLATED_MYSQL container=" + MYSQL.getContainerId() + " url=" + MYSQL.getJdbcUrl()
                + " transactionManager=" + manager.getClass().getSimpleName() + " migrations=12");
    }

    @Test
    void actualJpaCommitKeepsFileAndMetadata() throws Exception {
        Long id = upload(); // Service proxy owns and commits the real JPA transaction.
        assertThat(files.findById(id)).isPresent();
        assertThat(Files.readAllBytes(filePath(id))).isEqualTo(png);
        assertThat(service.downloadFile(userId, id).getContentAsByteArray()).isEqualTo(png);
    }

    @Test
    void rollbackAfterServiceReturnsDeletesOnlyNewFile() throws Exception {
        Long oldId = upload();
        Path oldPath = filePath(oldId);
        long before = physicalCount();
        AtomicLong newId = new AtomicLong();
        transaction.executeWithoutResult(status -> {
            newId.set(upload());
            status.setRollbackOnly();
        });
        assertThat(files.findById(newId.get())).isEmpty();
        assertThat(Files.readAllBytes(oldPath)).isEqualTo(png);
        assertThat(files.findById(oldId)).isPresent();
        assertThat(physicalCount()).isEqualTo(before);
    }

    @Test
    void realDatabaseInsertFailureCleansOnlyNewFile() throws Exception {
        Long oldId = upload();
        Path oldPath = filePath(oldId);
        long before = physicalCount();
        long rows = files.count();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE files ADD CONSTRAINT issue50_reject_file CHECK (original_name <> 'reject.png')");
        try {
            assertThatThrownBy(() -> service.uploadFile(userId,
                    new MockMultipartFile("file", "reject.png", "image/png", png), "CERTIFICATE"))
                    .isInstanceOf(RuntimeException.class)
                    .hasRootCauseInstanceOf(java.sql.SQLException.class)
                    .hasStackTraceContaining("issue50_reject_file");
        } finally {
            jdbc.execute("ALTER TABLE files DROP CHECK issue50_reject_file");
        }
        assertThat(files.count()).isEqualTo(rows);
        assertThat(physicalCount()).isEqualTo(before);
        assertThat(Files.readAllBytes(oldPath)).isEqualTo(png);
    }

    @Test
    void actualCertificateReplacementRollbackRestoresOldReferenceAndFile() throws Exception {
        Long oldId = upload();
        Path oldPath = filePath(oldId);
        long before = physicalCount();
        Long certificateId = transaction.execute(status -> certificates.saveAndFlush(ExpertCertificate.create(
                profiles.findByUserId(userId).orElseThrow(), files.findById(oldId).orElseThrow(),
                CertificateTypeCodeEnum.TAX_ACCOUNTANT.getLabel(), "test-number",
                LocalDate.of(2025, 1, 1), LocalDate.of(2030, 1, 1), "test expert")).getId());
        AtomicLong newId = new AtomicLong();
        ExpertCertificateUpdateRequest request = mapper.readValue("""
                {"certificate_name":"세무사", "certificate_number":"new-number",
                 "issue_date":"2025-01-01", "expiry_date":"2030-01-01", "owner_name":"test expert"}
                """, ExpertCertificateUpdateRequest.class);
        transaction.executeWithoutResult(status -> {
            newId.set(upload());
            org.springframework.test.util.ReflectionTestUtils.setField(request, "fileId", newId.get());
            certificateService.updateMyCertificate(userId, certificateId, request);
            status.setRollbackOnly();
        });
        Long restored = transaction.execute(status -> certificates.findById(certificateId).orElseThrow().getFile().getId());
        assertThat(restored).isEqualTo(oldId);
        assertThat(files.findById(oldId)).isPresent();
        assertThat(files.findById(newId.get())).isEmpty();
        assertThat(Files.readAllBytes(oldPath)).isEqualTo(png);
        assertThat(physicalCount()).isEqualTo(before);
    }

    @Test
    void realJpaFlushFailureRollsBackNewFileAndKeepsCommittedFile() throws Exception {
        Long oldId = upload();
        Path oldPath = filePath(oldId);
        long before = physicalCount();
        long rows = files.count();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            Long newId = upload();
            var newFile = files.findById(newId).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(newFile, "originalName", null);
            files.flush();
        })).isInstanceOf(RuntimeException.class);
        assertThat(files.count()).isEqualTo(rows);
        assertThat(physicalCount()).isEqualTo(before);
        assertThat(Files.readAllBytes(oldPath)).isEqualTo(png);
    }

    @Test
    void authenticatedMultipartApiKeepsResponseStructure() throws Exception {
        mvc.perform(multipart("/api/files").file(new MockMultipartFile("file", "api.png", "image/png", png))
                        .contextPath("/api").param("purpose", "CERTIFICATE")
                        .header("Authorization", "Bearer " + jwt.generateToken(userId, UserTypeEnum.EXPERT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.data.file.id").isNumber())
                .andExpect(jsonPath("$.data.file.original_name").value("api.png"))
                .andExpect(jsonPath("$.data.file.stored_name").isString())
                .andExpect(jsonPath("$.data.file.mime_type").value("PNG"))
                .andExpect(jsonPath("$.data.file.size").value(png.length))
                .andExpect(jsonPath("$.data.file.purpose").value("CERTIFICATE"));
    }

    @Test
    void realPdfSecurityRejectsOpenActionBeforeStorageAndAcceptsSafePdf() throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            Long id = service.uploadFile(userId, new MockMultipartFile("file", "safe.pdf", "application/pdf",
                    output.toByteArray()), "CERTIFICATE").getFile().getId();
            assertThat(filePath(id)).exists();
            long before = physicalCount();
            long rows = files.count();
            document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("alert(1)"));
            output.reset();
            document.save(output);
            assertThatThrownBy(() -> service.uploadFile(userId,
                    new MockMultipartFile("file", "blocked.pdf", "application/pdf", output.toByteArray()),
                    "CERTIFICATE")).isInstanceOf(CustomException.class);
            assertThat(physicalCount()).isEqualTo(before);
            assertThat(files.count()).isEqualTo(rows);
        }
    }
}
