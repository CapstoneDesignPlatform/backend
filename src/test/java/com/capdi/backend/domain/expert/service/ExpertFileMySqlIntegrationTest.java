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
    void flywayAppliesAllMigrations() {
        assertThat(MYSQL.getContainerInfo().getHostConfig().getTmpFs()).containsKey("/var/lib/mysql");
        assertThat(MYSQL.getContainerInfo().getConfig().getLabels()).doesNotContainKey("com.docker.compose.project");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied()).hasSize(16);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");
        flyway.validate();
        System.out.println("ISOLATED_MYSQL container=" + MYSQL.getContainerId() + " url=" + MYSQL.getJdbcUrl()
                + " transactionManager=" + manager.getClass().getSimpleName() + " migrations=16");
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

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.capdi.backend.domain.expert.client.GoogleVisionOcrClient vision;
    @Autowired ExpertOcrPersistenceService ocrPersistence;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"APPROVED", "REJECTED"})
    void ocrSuccessRunsOutsideCallerTransactionAndPreservesApproval(String approval) {
        Long id = upload();
        transaction.executeWithoutResult(tx -> {
            if (approval.equals("APPROVED")) files.findById(id).orElseThrow().approveVerification();
            else files.findById(id).orElseThrow().rejectVerification("review reason");
            profiles.findByUserId(userId).orElseThrow().updateVerificationStatus(
                    com.capdi.backend.domain.expert.entity.VerificationStatusEnum.valueOf(approval), "review reason");
        });
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                    assertThat(files.findById(id).orElseThrow().getOcrStatus())
                            .isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.RUNNING);
                    return "fresh OCR";
                });
        // A caller's transaction must be suspended, not inherited by the external client.
        transaction.executeWithoutResult(tx -> service.runOcr(userId, id));
        var file = files.findById(id).orElseThrow();
        assertThat(file.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED);
        assertThat(file.getOcrRawText()).isEqualTo("fresh OCR");
        assertThat(file.getVerificationStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.FileVerificationStatusEnum.valueOf(approval));
        assertThat(profiles.findByUserId(userId).orElseThrow().getVerificationStatus())
                .isEqualTo(com.capdi.backend.domain.expert.entity.VerificationStatusEnum.valueOf(approval));
    }

    @Test
    void ocrRerunEmptyAndFailureCommitStateAndNeverReuseOldText() {
        Long id = upload();
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn("old text", "  ")
                .thenThrow(new CustomException(com.capdi.backend.global.exception.ErrorCode.OCR_TIMEOUT));
        service.runOcr(userId, id);
        assertThatThrownBy(() -> service.runOcr(userId,id)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_EMPTY));
        var empty = files.findById(id).orElseThrow();
        assertThat(empty.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.EMPTY);
        assertThat(empty.getOcrRawText()).isNull();
        assertThatThrownBy(() -> service.runOcr(userId,id)).isInstanceOfSatisfying(CustomException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_TIMEOUT));
        var failed = files.findById(id).orElseThrow();
        assertThat(failed.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.FAILED);
        assertThat(failed.getOcrRawText()).isNull();
        assertThat(failed.getOcrRevision()).isEqualTo(3);
        assertThat(failed.getOcrRunToken()).isNull();
    }

    @Test
    void concurrentRequestIsRejectedWhileFirstRuns() throws Exception {
        Long id = upload();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    entered.countDown();
                    assertThat(release.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    return "first result";
                });
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> service.runOcr(userId,id));
            try {
                assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> service.runOcr(userId,id)).isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_ALREADY_RUNNING));
            } finally { release.countDown(); }
            first.get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(files.findById(id).orElseThrow().getOcrRawText()).isEqualTo("first result");
        org.mockito.Mockito.verify(vision,org.mockito.Mockito.times(1))
                .requestOcr(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expiredAttemptCannotOverwriteNewAttemptAndForeignOwnerCannotStart() {
        Long id = upload();
        var first = ocrPersistence.start(userId,id);
        new JdbcTemplate(dataSource).update("UPDATE files SET ocr_started_at = ? WHERE id = ?",
                java.time.LocalDateTime.now().minusMinutes(6), id);
        var second = ocrPersistence.start(userId,id);
        assertThatThrownBy(() -> ocrPersistence.finish(userId,first,"stale",
                com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_RESULT_STALE));
        ocrPersistence.finish(userId,second,"current",com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        assertThat(files.findById(id).orElseThrow().getOcrRawText()).isEqualTo("current");
        assertThatThrownBy(() -> ocrPersistence.start(userId + 100000,id))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.capdi.backend.global.exception.ErrorCode.FORBIDDEN));
    }

    @Test
    void ocrApiKeepsSuccessEnvelopeAndReportsProviderFailure() throws Exception {
        Long id = upload();
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any()))
                .thenReturn("success text").thenThrow(new RuntimeException("provider unavailable"));
        String token = "Bearer " + jwt.generateToken(userId,UserTypeEnum.EXPERT);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization",token))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.success").value(false));
        assertThat(files.findById(id).orElseThrow().getOcrStatus())
                .isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.FAILED);
    }

    @Test
    void ocrStatusApiExposesOnlyOwnedCurrentSuccessfulTextAndEmptyIs422() throws Exception {
        Long id = upload();
        String token = "Bearer " + jwt.generateToken(userId,UserTypeEnum.EXPERT);
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any()))
                .thenReturn("first result", "");
        service.runOcr(userId,id);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.text").value("first result"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization",token))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("EMPTY"))
                .andExpect(jsonPath("$.data.text").doesNotExist());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization","Bearer " + jwt.generateToken(userId+100000,UserTypeEnum.EXPERT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void legacyVerificationUpdateDoesNotOverwriteConcurrentOcrColumns() {
        Long id = upload();
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any()))
                .thenReturn("concurrent text");
        transaction.executeWithoutResult(tx -> {
            var previouslyLoaded = files.findById(id).orElseThrow();
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> service.runOcr(userId,id)).get(10,java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { throw new RuntimeException(e); }
            previouslyLoaded.approveVerification();
            files.flush();
        });
        var actual = files.findById(id).orElseThrow();
        assertThat(actual.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED);
        assertThat(actual.getOcrRawText()).isEqualTo("concurrent text");
    }

    @Autowired BusinessRegistrationInfoService businessService;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,RUNNING", "false,SUCCEEDED", "false,FAILED",
            "true,RUNNING", "true,SUCCEEDED", "true,FAILED"})
    void businessSaveRefreshesOldRevisionWithoutChangingFileReview(boolean update, String nextStatus) throws Exception {
        Long id = service.uploadFile(userId,new MockMultipartFile("file","business.png","image/png",png),
                "BUSINESS_REGISTRATION").getFile().getId();
        var first = ocrPersistence.start(userId,id);
        ocrPersistence.finish(userId,first,"1234567891 OldOwner OldCompany",
                com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        String json = "{\"file_id\":"+id+",\"business_number\":\"1234567891\","
                +"\"representative_name\":\"OldOwner\",\"company_name\":\"OldCompany\"}";
        var create = mapper.readValue(json, com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoCreateRequest.class);
        var change = mapper.readValue(json, com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest.class);
        Long infoId = update ? businessService.createMyBusinessRegistrationInfo(userId,create).getId() : null;
        transaction.executeWithoutResult(tx -> {
            // Transaction A has a real first-level-cache snapshot from revision 1.
            var cached = files.findById(id).orElseThrow();
            assertThat(cached.getOcrRevision()).isEqualTo(1);
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    // Transaction B publishes a later attempt while A remains open.
                    var second = ocrPersistence.start(userId,id);
                    if (!nextStatus.equals("RUNNING")) ocrPersistence.finish(userId,second,"different text",
                            com.capdi.backend.domain.expert.entity.OcrStatusEnum.valueOf(nextStatus),
                            nextStatus.equals("FAILED") ? com.capdi.backend.global.exception.ErrorCode.OCR_FAILED : null);
                }).get(10,java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { throw new RuntimeException(e); }
            assertThat(cached.getOcrRevision()).isEqualTo(1); // Prove the stale cache precondition.
            if (update) businessService.updateMyBusinessRegistrationInfo(userId,infoId,change);
            else businessService.createMyBusinessRegistrationInfo(userId,create);
            // The save still refreshes and locks the source, even though OCR no longer decides review status.
            assertThat(cached.getOcrRevision()).isEqualTo(2);
        });
        var current = files.findById(id).orElseThrow();
        assertThat(current.getOcrRevision()).isEqualTo(2);
        assertThat(current.getVerificationStatus())
                .isEqualTo(com.capdi.backend.domain.expert.entity.FileVerificationStatusEnum.PENDING);
        assertThat(current.getOcrStatus().name()).isEqualTo(nextStatus);
    }

    @Test
    void osivRequestCannotFinishUsingAnOldManagedTokenAfterLeaseHandover() throws Exception {
        Long id = upload();
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> {
                    // This is an actual MockMvc request with the OSIV EntityManager bound.
                    var holder = (org.springframework.orm.jpa.EntityManagerHolder)
                            org.springframework.transaction.support.TransactionSynchronizationManager.getResource(entityManagerFactory);
                    assertThat(holder).isNotNull();
                    var cached = files.findById(id).orElseThrow();
                    assertThat(holder.getEntityManager().contains(cached)).isTrue();
                    String oldToken = cached.getOcrRunToken();
                    try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                        executor.submit(() -> {
                            new JdbcTemplate(dataSource).update("UPDATE files SET ocr_started_at=? WHERE id=?",
                                    java.time.LocalDateTime.now().minusMinutes(6),id);
                            var replacement = ocrPersistence.start(userId,id);
                            ocrPersistence.finish(userId,replacement,"replacement result",
                                    com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
                        }).get(10,java.util.concurrent.TimeUnit.SECONDS);
                    }
                    assertThat(cached.getOcrRunToken()).isEqualTo(oldToken);
                    return "obsolete result";
                });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        var actual = files.findById(id).orElseThrow();
        assertThat(actual.getOcrRevision()).isEqualTo(2);
        assertThat(actual.getOcrRawText()).isEqualTo("replacement result");
    }

    @Test
    void businessSaveHoldsCurrentFileLockWithoutAutomaticApproval() throws Exception {
        Long id = service.uploadFile(userId,new MockMultipartFile("file","lock.png","image/png",png),
                "BUSINESS_REGISTRATION").getFile().getId();
        var initial = ocrPersistence.start(userId,id);
        ocrPersistence.finish(userId,initial,"1234567891 Owner Company",
                com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        var request = mapper.readValue("{\"file_id\":"+id+",\"business_number\":\"1234567891\","
                +"\"representative_name\":\"Owner\",\"company_name\":\"Company\"}",
                com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoCreateRequest.class);
        var entered = new java.util.concurrent.CountDownLatch(1);
        var pending = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<ExpertOcrPersistenceService.Attempt>>();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(tx -> {
                businessService.createMyBusinessRegistrationInfo(userId,request);
                assertThat(files.findById(id).orElseThrow().getVerificationStatus())
                        .isEqualTo(com.capdi.backend.domain.expert.entity.FileVerificationStatusEnum.PENDING);
                pending.set(executor.submit(() -> { entered.countDown(); return ocrPersistence.start(userId,id); }));
                try {
                    assertThat(entered.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> pending.get().get(250,java.util.concurrent.TimeUnit.MILLISECONDS))
                            .isInstanceOf(java.util.concurrent.TimeoutException.class);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }); // Source ownership/revision lock is released only after the input save commits.
            var next = pending.get().get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(next.revision()).isEqualTo(2);
            ocrPersistence.finish(userId,next,null,com.capdi.backend.domain.expert.entity.OcrStatusEnum.FAILED,
                    com.capdi.backend.global.exception.ErrorCode.OCR_FAILED);
        }
    }

    @Test
    void startRefreshesRunningStateEvenWithOsivStyleCachedNotRunEntity() throws Exception {
        Long id = upload();
        var em = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagerFactory,
                new org.springframework.orm.jpa.EntityManagerHolder(em));
        try {
            var cached = files.findById(id).orElseThrow();
            assertThat(cached.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.NOT_RUN);
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> ocrPersistence.start(userId,id)).get(10,java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(cached.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.NOT_RUN);
            assertThatThrownBy(() -> ocrPersistence.start(userId,id)).isInstanceOfSatisfying(CustomException.class,
                    e -> assertThat(e.getErrorCode()).isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_ALREADY_RUNNING));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory);
            em.close();
        }
        assertThat(files.findById(id).orElseThrow().getOcrRevision()).isEqualTo(1);
    }

    @Test
    void timeoutHttp504CommitsFailureWithoutLeakingProviderDetails() throws Exception {
        Long id = upload();
        org.mockito.Mockito.when(vision.requestOcr(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any()))
                .thenThrow(new CustomException(com.capdi.backend.global.exception.ErrorCode.OCR_TIMEOUT));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/files/"+id+"/ocr")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT)))
                .andExpect(status().isGatewayTimeout()).andExpect(jsonPath("$.success").value(false));
        var actual = files.findById(id).orElseThrow();
        assertThat(actual.getOcrStatus()).isEqualTo(com.capdi.backend.domain.expert.entity.OcrStatusEnum.FAILED);
        assertThat(actual.getOcrErrorCode()).isEqualTo("OCR_TIMEOUT");
        assertThat(actual.getOcrRawText()).isNull();
        assertThat(actual.getOcrRunToken()).isNull();
    }

    @Autowired com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository businessInfos;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.capdi.backend.domain.expert.util.BusinessRegistrationExtractor extraction;

    private static final String EXTRACTABLE = "\uC0AC\uC5C5\uC790\uB4F1\uB85D\uBC88\uD638: 123-45-67891\n"
            + "\uB300\uD45C\uC790: OcrOwner\n\uC0C1\uD638: OcrCompany\n\uAC1C\uC5C5\uC5F0\uC6D4\uC77C: 2024-02-29";
    private record ExtractionFixture(Long infoId, Long fileId) { }

    private ExtractionFixture extractionFixture(String purpose) {
        Long fileId = service.uploadFile(userId,new MockMultipartFile("file","extract.png","image/png",png),purpose)
                .getFile().getId();
        Long infoId = transaction.execute(tx -> businessInfos.saveAndFlush(
                com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo.create(
                        profiles.findByUserId(userId).orElseThrow(),files.findById(fileId).orElseThrow(),
                        "9876543215","InputOwner","InputCompany")).getId());
        return new ExtractionFixture(infoId,fileId);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.capdi.backend.domain.expert.entity.OcrStatusEnum.class)
    void extractionApiSeparatesInputAndOnlyConsumesSuccessfulOcr(com.capdi.backend.domain.expert.entity.OcrStatusEnum state) throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        if (state != com.capdi.backend.domain.expert.entity.OcrStatusEnum.NOT_RUN) {
            var attempt = ocrPersistence.start(userId,fixture.fileId());
            if (state != com.capdi.backend.domain.expert.entity.OcrStatusEnum.RUNNING) {
                ocrPersistence.finish(userId,attempt,EXTRACTABLE,state,null);
            }
        }
        if (state != com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED) {
            // Even an inconsistent historic text column must not be used in non-success states.
            new JdbcTemplate(dataSource).update("UPDATE files SET ocr_raw_text=? WHERE id=?",EXTRACTABLE,fixture.fileId());
        }
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/expert/me/business-registration-info/"+fixture.infoId()+"/ocr-extraction")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user_input.business_number").value("9876543215"))
                .andExpect(jsonPath("$.data.user_input.representative_name").value("InputOwner"))
                .andExpect(jsonPath("$.data.reference_only").value(true))
                .andExpect(jsonPath("$.data.requires_user_confirmation").value(true))
                .andExpect(jsonPath("$.data.page_information_available").value(false))
                .andExpect(jsonPath("$.data.ocr_status").value(state.name()));
        if (state == com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED) {
            response.andExpect(jsonPath("$.data.extraction_status").value("AVAILABLE"))
                    .andExpect(jsonPath("$.data.extracted.business_number.value").value("1234567891"))
                    .andExpect(jsonPath("$.data.extracted.representative_name.value").value("OcrOwner"))
                    .andExpect(jsonPath("$.data.extracted.opening_date.value").value("2024-02-29"))
                    .andExpect(jsonPath("$.data.extracted.business_number.candidates[0].page_number").doesNotExist());
        } else {
            response.andExpect(jsonPath("$.data.extraction_status").value("OCR_NOT_SUCCEEDED"))
                    .andExpect(jsonPath("$.data.extracted.business_number.status").value("MISSING"))
                    .andExpect(jsonPath("$.data.extracted.business_number.candidates").isEmpty());
        }
        assertThat(files.findById(fixture.fileId()).orElseThrow().getVerificationStatus())
                .isEqualTo(com.capdi.backend.domain.expert.entity.FileVerificationStatusEnum.PENDING);
        assertThat(profiles.findByUserId(userId).orElseThrow().getVerificationStatus())
                .isEqualTo(com.capdi.backend.domain.expert.entity.VerificationStatusEnum.NOT_SUBMITTED);
    }

    @Test
    void extractionApiRejectsOtherExpertAndWrongFilePurpose() throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        Long other = transaction.execute(tx -> {
            var user = users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT).name("Other")
                    .email(UUID.randomUUID()+"@example.test").phone("01000000001").build());
            profiles.saveAndFlush(ExpertProfile.createDefault(user,"OtherCompany"));
            return user.getId();
        });
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/expert/me/business-registration-info/"+fixture.infoId()+"/ocr-extraction")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(other,UserTypeEnum.EXPERT)))
                .andExpect(status().isForbidden());
        var wrong = extractionFixture("CERTIFICATE");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/expert/me/business-registration-info/"+wrong.infoId()+"/ocr-extraction")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT)))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                        "/api/expert/me/business-registration-info/"+Long.MAX_VALUE+"/ocr-extraction")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT)))
                .andExpect(status().isNotFound());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RUNNING","SUCCEEDED"})
    void extractionRefreshesCachedOcrAfterConcurrentRerun(String state) {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        var first = ocrPersistence.start(userId,fixture.fileId());
        ocrPersistence.finish(userId,first,EXTRACTABLE,com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        transaction.executeWithoutResult(tx -> {
            var cached = files.findById(fixture.fileId()).orElseThrow();
            assertThat(cached.getOcrRevision()).isEqualTo(1);
            try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    var second = ocrPersistence.start(userId,fixture.fileId());
                    if (state.equals("SUCCEEDED")) ocrPersistence.finish(userId,second,
                            EXTRACTABLE.replace("123-45-67891","987-65-43215"),
                            com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
                }).get(10,java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) { throw new RuntimeException(e); }
            assertThat(cached.getOcrRevision()).isEqualTo(1);
            var result = businessService.getOcrExtraction(userId,fixture.infoId());
            assertThat(result.getOcrRevision()).isEqualTo(2);
            assertThat(result.getExtracted().getBusinessNumber().getValue())
                    .isEqualTo(state.equals("SUCCEEDED") ? "9876543215" : null);
        });
    }

    @Test
    void extractionLocksSnapshotAgainstRerunAndFileReplacement() throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        var first = ocrPersistence.start(userId,fixture.fileId());
        ocrPersistence.finish(userId,first,EXTRACTABLE,com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        Long newFileId = service.uploadFile(userId,new MockMultipartFile("file","replacement.png","image/png",png),
                "BUSINESS_REGISTRATION").getFile().getId();
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        org.mockito.Mockito.doAnswer(call -> {
            entered.countDown();
            assertThat(release.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return call.callRealMethod();
        }).when(extraction).extract(org.mockito.ArgumentMatchers.any());
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(3)) {
            var reading = executor.submit(() -> businessService.getOcrExtraction(userId,fixture.infoId()));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var attempting = new java.util.concurrent.CountDownLatch(2);
            var rerun = executor.submit(() -> { attempting.countDown(); return ocrPersistence.start(userId,fixture.fileId()); });
            var change = mapper.readValue("{\"file_id\":"+newFileId+",\"business_number\":\"9876543215\","
                            +"\"representative_name\":\"NewOwner\",\"company_name\":\"NewCompany\"}",
                    com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest.class);
            var replacing = executor.submit(() -> { attempting.countDown(); return businessService.updateMyBusinessRegistrationInfo(userId,fixture.infoId(),change); });
            try {
                assertThat(attempting.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> rerun.get(250,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThatThrownBy(() -> replacing.get(250,java.util.concurrent.TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
            } finally { release.countDown(); }
            var snapshot = reading.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(snapshot.getFileId()).isEqualTo(fixture.fileId());
            assertThat(snapshot.getOcrRevision()).isEqualTo(1);
            assertThat(snapshot.getUserInput().getCompanyName()).isEqualTo("InputCompany");
            // Rerun and replacement may race after the read commits. Deletion of the old file is legitimate.
            try { rerun.get(10,java.util.concurrent.TimeUnit.SECONDS); }
            catch (java.util.concurrent.ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(CustomException.class);
            }
            replacing.get(10,java.util.concurrent.TimeUnit.SECONDS);
            var latest = businessService.getOcrExtraction(userId,fixture.infoId());
            assertThat(latest.getFileId()).isEqualTo(newFileId);
            assertThat(latest.getExtractionStatus()).isEqualTo("OCR_NOT_SUCCEEDED");
            assertThat(latest.getExtracted().getBusinessNumber().getValue()).isNull();
        } finally { release.countDown(); }
    }

    @Autowired BusinessVerificationService verificationService;
    @Autowired BusinessVerificationPersistenceService verificationPersistence;
    @Autowired VerificationFileHasher verificationHasher;
    @Autowired com.capdi.backend.domain.expert.repository.BusinessVerificationRepository verificationHistory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.capdi.backend.domain.expert.client.NtsBusinessClient ntsClient;

    private ExtractionFixture confirmedFixture() throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        var attempt = ocrPersistence.start(userId, fixture.fileId());
        ocrPersistence.finish(userId, attempt, EXTRACTABLE, com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED, null);
        businessService.confirmMyBusinessInformation(userId, fixture.infoId(), confirmation(fixture, attempt.revision()));
        return fixture;
    }

    private com.capdi.backend.domain.expert.dto.BusinessVerificationRequest verificationRequest(ExtractionFixture f, String kind) throws Exception {
        var info = businessInfos.findById(f.infoId()).orElseThrow();
        var file = files.findById(f.fileId()).orElseThrow();
        return mapper.readValue("{\"kind\":\""+kind+"\",\"file_id\":"+f.fileId()+",\"ocr_revision\":"+file.getOcrRevision()
                +",\"input_revision\":"+info.getInputRevision()+"}", com.capdi.backend.domain.expert.dto.BusinessVerificationRequest.class);
    }

    private com.capdi.backend.domain.expert.dto.NtsBusinessResult match() {
        return new com.capdi.backend.domain.expert.dto.NtsBusinessResult(
                com.capdi.backend.domain.expert.dto.NtsBusinessResult.Authenticity.MATCH,
                com.capdi.backend.domain.expert.dto.NtsBusinessResult.BusinessState.ACTIVE,
                com.capdi.backend.domain.expert.dto.NtsBusinessResult.Failure.NONE);
    }

    private void mockMatch() {
        org.mockito.Mockito.when(ntsClient.validate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return match();
        });
    }

    @Test
    void verificationCommitsHistoryAndApiContractWithoutChangingApprovals() throws Exception {
        var f = confirmedFixture(); mockMatch();
        var oldFile = files.findById(f.fileId()).orElseThrow().getVerificationStatus();
        var oldProfile = profiles.findByUserId(userId).orElseThrow().getVerificationStatus();
        var request = verificationRequest(f,"VALIDATE");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/expert/me/business-registration-info/"+f.infoId()+"/verifications").contextPath("/api")
                .header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT))
                .contentType("application/json").content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authenticity").value("MATCH"))
                .andExpect(jsonPath("$.data.execution_status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.validity").value("CURRENT"));
        var latest = verificationService.latest(userId,f.infoId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                "/api/expert/me/business-registration-info/"+f.infoId()+"/verifications/latest").contextPath("/api")
                .header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.execution_id").value(latest.getExecutionId()))
                .andExpect(jsonPath("$.data.business_state").value("ACTIVE"));
        assertThat(latest.getFileSha256()).isEqualTo(verificationHasher.hash(files.findById(f.fileId()).orElseThrow().getFilePath()));
        assertThat(latest.isStatusLookupPerformed()).isFalse();
        assertThat(files.findById(f.fileId()).orElseThrow().getVerificationStatus()).isEqualTo(oldFile);
        assertThat(profiles.findByUserId(userId).orElseThrow().getVerificationStatus()).isEqualTo(oldProfile);
        org.mockito.Mockito.verify(ntsClient,org.mockito.Mockito.never()).lookupStatus(org.mockito.ArgumentMatchers.anyString());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=com.capdi.backend.domain.expert.dto.NtsBusinessResult.BusinessState.class,
            names={"ACTIVE","SUSPENDED","CLOSED","NOT_FOUND_OR_DELETED"})
    void mismatchUsesStatusFallbackAndStatusOnlyRemainsDistinct(com.capdi.backend.domain.expert.dto.NtsBusinessResult.BusinessState state) throws Exception {
        var f = confirmedFixture();
        org.mockito.Mockito.when(ntsClient.validate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new com.capdi.backend.domain.expert.dto.NtsBusinessResult(com.capdi.backend.domain.expert.dto.NtsBusinessResult.Authenticity.NOT_CONFIRMED,
                        com.capdi.backend.domain.expert.dto.NtsBusinessResult.BusinessState.UNKNOWN,com.capdi.backend.domain.expert.dto.NtsBusinessResult.Failure.NONE));
        org.mockito.Mockito.when(ntsClient.lookupStatus(org.mockito.ArgumentMatchers.anyString())).thenReturn(
                new com.capdi.backend.domain.expert.dto.NtsBusinessResult(com.capdi.backend.domain.expert.dto.NtsBusinessResult.Authenticity.NOT_REQUESTED,
                        state,com.capdi.backend.domain.expert.dto.NtsBusinessResult.Failure.NONE));
        var result = verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE"));
        assertThat(result.getAuthenticity().name()).isEqualTo("NOT_CONFIRMED");
        assertThat(result.getBusinessState()).isEqualTo(state);
        assertThat(result.isStatusLookupPerformed()).isTrue();
        var statusResult = verificationService.execute(userId,f.infoId(),verificationRequest(f,"STATUS"));
        assertThat(statusResult.getAuthenticity().name()).isEqualTo("NOT_REQUESTED");
        assertThat(statusResult.getExecutionId()).isNotEqualTo(result.getExecutionId());
        assertThat(verificationHistory.findAll().stream().filter(r -> r.getBusinessRegistrationInfoId().equals(f.infoId()))).hasSize(2);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value=com.capdi.backend.domain.expert.dto.NtsBusinessResult.Failure.class,names={"TIMEOUT","NETWORK","DISABLED"})
    void providerFailureIsPersistedSeparatelyFromMismatch(com.capdi.backend.domain.expert.dto.NtsBusinessResult.Failure failure) throws Exception {
        var f = confirmedFixture();
        org.mockito.Mockito.when(ntsClient.validate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenReturn(
                com.capdi.backend.domain.expert.dto.NtsBusinessResult.unavailable(failure));
        var response = verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE"));
        assertThat(response.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(response.getAuthenticity().name()).isEqualTo("UNAVAILABLE");
        assertThat(verificationService.latest(userId,f.infoId()).getFailure()).isEqualTo(failure);
    }

    @Test
    void duplicateRunningIsRejectedAndExpiredExecutionCannotOverwriteReplacement() throws Exception {
        var f = confirmedFixture(); var request = verificationRequest(f,"VALIDATE");
        var observed = verificationPersistence.prepare(userId,f.infoId(),request);
        var sha = verificationHasher.hash(observed.path());
        var old = verificationPersistence.start(userId,f.infoId(),request,observed,sha);
        assertThatThrownBy(() -> verificationPersistence.start(userId,f.infoId(),request,observed,sha))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_ALREADY_RUNNING"));
        // Bind a real OSIV persistence context containing the old RUNNING entity.
        var em = entityManagerFactory.createEntityManager();
        org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagerFactory,new org.springframework.orm.jpa.EntityManagerHolder(em));
        try {
            var rowId = verificationHistory.findFirstByBusinessRegistrationInfoIdOrderByIdDesc(f.infoId()).orElseThrow().getId();
            em.find(com.capdi.backend.domain.expert.entity.BusinessVerification.class,rowId);
            new JdbcTemplate(dataSource).update("UPDATE expert_business_verifications SET requested_at=DATE_SUB(NOW(), INTERVAL 6 MINUTE) WHERE id=?",rowId);
            var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
            BusinessVerificationPersistenceService.Attempt replacement;
            try {
                replacement = pool.submit(() -> {
                    var next = verificationPersistence.start(userId,f.infoId(),request,observed,sha);
                    verificationPersistence.complete(userId,f.infoId(),next,sha,match(),false);
                    return next;
                }).get(10,java.util.concurrent.TimeUnit.SECONDS);
            } finally { pool.shutdownNow(); }
            var oldResponse = verificationPersistence.complete(userId,f.infoId(),old,sha,match(),false);
            assertThat(oldResponse.getExecutionStatus().name()).isEqualTo("FAILED");
            assertThat(oldResponse.getFailure().name()).isEqualTo("EXECUTION_EXPIRED");
            assertThat(verificationService.latest(userId,f.infoId()).getExecutionId()).isEqualTo(replacement.token());
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagerFactory); em.close();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"INPUT","OCR","FILE","BYTES"})
    void changingMaterialWhileProviderRunsMakesResultStale(String change) throws Exception {
        var f = confirmedFixture(); var request = verificationRequest(f,"VALIDATE");
        org.mockito.Mockito.when(ntsClient.validate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (change.equals("OCR")) ocrPersistence.start(userId,f.fileId());
            if (change.equals("INPUT")) {
                var confirm = confirmation(f,request.getOcrRevision());
                org.springframework.test.util.ReflectionTestUtils.setField(confirm,"representativeName","ChangedOwner");
                businessService.confirmMyBusinessInformation(userId,f.infoId(),confirm);
            }
            if (change.equals("FILE")) {
                Long next = service.uploadFile(userId,new MockMultipartFile("file","new.png","image/png",png),"BUSINESS_REGISTRATION").getFile().getId();
                transaction.executeWithoutResult(tx -> {
                    var info = businessInfos.findById(f.infoId()).orElseThrow();
                    info.update(files.findById(next).orElseThrow(),info.getBusinessNumber(),info.getRepresentativeName(),info.getCompanyName());
                });
            }
            if (change.equals("BYTES")) Files.write(Path.of(files.findById(f.fileId()).orElseThrow().getFilePath()),new byte[]{1,2,3});
            return match();
        });
        var result = verificationService.execute(userId,f.infoId(),request);
        assertThat(result.getValidity().name()).isEqualTo("STALE");
        assertThat(verificationService.latest(userId,f.infoId()).getValidity().name()).isEqualTo("STALE");
    }

    @Test
    void realConcurrentDuplicateAndOlderCompletionNeverReplaceLatestRequest() throws Exception {
        var f = confirmedFixture(); var oldRequest = verificationRequest(f,"VALIDATE");
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(ntsClient.validate(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString())).thenAnswer(call -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            if (calls.incrementAndGet() == 1) {
                entered.countDown(); assertThat(release.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            }
            return match();
        });
        var pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> verificationService.execute(userId,f.infoId(),oldRequest));
            assertThat(entered.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(verificationService.latest(userId,f.infoId()).getExecutionStatus().name()).isEqualTo("RUNNING");
            assertThatThrownBy(() -> verificationService.execute(userId,f.infoId(),oldRequest))
                    .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_ALREADY_RUNNING"));
            var confirm = confirmation(f,oldRequest.getOcrRevision());
            org.springframework.test.util.ReflectionTestUtils.setField(confirm,"companyName","UpdatedCompany");
            businessService.confirmMyBusinessInformation(userId,f.infoId(),confirm);
            var second = verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE"));
            release.countDown();
            assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS).getValidity().name()).isEqualTo("STALE");
            assertThat(verificationService.latest(userId,f.infoId()).getExecutionId()).isEqualTo(second.getExecutionId());
            assertThat(verificationService.latest(userId,f.infoId()).getValidity().name()).isEqualTo("CURRENT");
        } finally { release.countDown(); pool.shutdownNow(); }
    }

    @Test
    void historyDoesNotPreventOldFileDeletionButPreventsInformationDeletion() throws Exception {
        var f = confirmedFixture(); mockMatch();
        verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE"));
        Long next = service.uploadFile(userId,new MockMultipartFile("file","next.png","image/png",png),"BUSINESS_REGISTRATION").getFile().getId();
        transaction.executeWithoutResult(tx -> {
            var info = businessInfos.findById(f.infoId()).orElseThrow();
            info.update(files.findById(next).orElseThrow(),info.getBusinessNumber(),info.getRepresentativeName(),info.getCompanyName());
            businessInfos.flush(); service.deleteReplacedFileIfUnused(files.findById(f.fileId()).orElseThrow());
        });
        assertThat(files.existsById(f.fileId())).isFalse();
        assertThat(verificationService.latest(userId,f.infoId()).getFileId()).isEqualTo(f.fileId());
        assertThat(verificationService.latest(userId,f.infoId()).getValidity().name()).isEqualTo("STALE");
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> { businessInfos.deleteById(f.infoId()); businessInfos.flush(); }))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void databaseCompletionFailureIsRecordedAndRetryPreservesHistory() throws Exception {
        var f = confirmedFixture(); mockMatch();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("ALTER TABLE expert_business_verifications ADD CONSTRAINT test_reject_match CHECK (business_registration_info_id <> "+f.infoId()+" OR authenticity <> 'MATCH')");
        try {
            assertThatThrownBy(() -> verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE")))
                    .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_RESULT_STORAGE_FAILED"));
            assertThat(verificationService.latest(userId,f.infoId()).getFailure().name()).isEqualTo("RESULT_STORAGE_FAILED");
        } finally { jdbc.execute("ALTER TABLE expert_business_verifications DROP CHECK test_reject_match"); }
        var next = verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE"));
        assertThat(next.getExecutionStatus().name()).isEqualTo("SUCCEEDED");
        assertThat(verificationHistory.findAll().stream().filter(r -> r.getBusinessRegistrationInfoId().equals(f.infoId()))).hasSize(2);
    }

    @Test
    void verificationRejectsUnconfirmedOutdatedForeignAndMissingFiles() throws Exception {
        var f = confirmedFixture(); mockMatch();
        var request = verificationRequest(f,"VALIDATE");
        Long stranger = transaction.execute(tx -> {
            var user = users.saveAndFlush(User.builder().userType(UserTypeEnum.EXPERT).name("stranger")
                    .email(UUID.randomUUID()+"@example.test").phone("0").build());
            profiles.saveAndFlush(ExpertProfile.createDefault(user,"other")); return user.getId();
        });
        assertThatThrownBy(() -> verificationService.execute(stranger,f.infoId(),request))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("FORBIDDEN"));
        assertThatThrownBy(() -> verificationService.latest(stranger,f.infoId()))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("FORBIDDEN"));
        Files.delete(Path.of(files.findById(f.fileId()).orElseThrow().getFilePath()));
        assertThatThrownBy(() -> verificationService.execute(userId,f.infoId(),request))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_FILE_UNAVAILABLE"));
        var confirm = confirmation(f,request.getOcrRevision());
        org.springframework.test.util.ReflectionTestUtils.setField(confirm,"companyName","Changed");
        businessService.confirmMyBusinessInformation(userId,f.infoId(),confirm);
        assertThatThrownBy(() -> verificationService.execute(userId,f.infoId(),request))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("OCR_RESULT_STALE"));
        transaction.executeWithoutResult(tx -> businessInfos.findById(f.infoId()).orElseThrow().updateOpeningDate(LocalDate.of(2020,1,1)));
        assertThatThrownBy(() -> verificationService.execute(userId,f.infoId(),verificationRequest(f,"VALIDATE")))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_CONFIRMATION_REQUIRED"));
    }

    @Test
    void abandonedRunningExecutionExpiresOnLatestWithoutProviderRetry() throws Exception {
        var f = confirmedFixture(); var request = verificationRequest(f,"VALIDATE");
        var observed = verificationPersistence.prepare(userId,f.infoId(),request);
        var attempt = verificationPersistence.start(userId,f.infoId(),request,observed,verificationHasher.hash(observed.path()));
        new JdbcTemplate(dataSource).update("UPDATE expert_business_verifications SET requested_at=DATE_SUB(NOW(), INTERVAL 6 MINUTE) WHERE execution_id=?",attempt.token());
        var result = verificationService.latest(userId,f.infoId());
        assertThat(result.getExecutionStatus().name()).isEqualTo("FAILED");
        assertThat(result.getFailure().name()).isEqualTo("EXECUTION_EXPIRED");
        org.mockito.Mockito.verifyNoInteractions(ntsClient);
    }

    @Test
    void callerTransactionIsSuspendedDuringProviderCallAndHistorySurvivesCallerRollback() throws Exception {
        var f = confirmedFixture(); mockMatch();
        var request = verificationRequest(f,"VALIDATE");
        var result = transaction.execute(tx -> {
            var response = verificationService.execute(userId,f.infoId(),request);
            tx.setRollbackOnly(); return response;
        });
        assertThat(verificationService.latest(userId,f.infoId()).getExecutionId()).isEqualTo(result.getExecutionId());
        assertThat(result.getExecutionStatus().name()).isEqualTo("SUCCEEDED");
    }

    @Test
    void verificationRequiresOwnedBusinessPurposeAndSuccessfulConfirmedOcr() throws Exception {
        var certificate = extractionFixture("CERTIFICATE");
        assertThatThrownBy(() -> verificationService.execute(userId,certificate.infoId(),verificationRequest(certificate,"VALIDATE")))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("INVALID_INPUT"));
        var unconfirmed = extractionFixture("BUSINESS_REGISTRATION");
        var attempt = ocrPersistence.start(userId,unconfirmed.fileId());
        ocrPersistence.finish(userId,attempt,EXTRACTABLE,com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        assertThatThrownBy(() -> verificationService.execute(userId,unconfirmed.infoId(),verificationRequest(unconfirmed,"VALIDATE")))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_CONFIRMATION_REQUIRED"));
        var confirmed = confirmedFixture();
        var next = ocrPersistence.start(userId,confirmed.fileId());
        assertThatThrownBy(() -> verificationService.execute(userId,confirmed.infoId(),verificationRequest(confirmed,"VALIDATE")))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode().name()).isEqualTo("NTS_CONFIRMATION_REQUIRED"));
        org.mockito.Mockito.verifyNoInteractions(ntsClient);
    }

    private com.capdi.backend.domain.expert.dto.BusinessConfirmationRequest confirmation(ExtractionFixture fixture, long revision) throws Exception {
        return mapper.readValue("{\"file_id\":"+fixture.fileId()+",\"ocr_revision\":"+revision
                +",\"business_number\":\"987-65-43215\",\"representative_name\":\" ConfirmedOwner \","
                +"\"company_name\":\"ConfirmedCompany\",\"opening_date\":\"2024-02-29\"}",
                com.capdi.backend.domain.expert.dto.BusinessConfirmationRequest.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"APPROVED","REJECTED"})
    void confirmationStoresNormalizedUserValuesAndDateWithoutChangingApprovals(String approval) throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        var attempt = ocrPersistence.start(userId,fixture.fileId());
        ocrPersistence.finish(userId,attempt,EXTRACTABLE,com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        transaction.executeWithoutResult(tx -> {
            var file = files.findById(fixture.fileId()).orElseThrow();
            if (approval.equals("APPROVED")) file.approveVerification(); else file.rejectVerification("existing reason");
            profiles.findByUserId(userId).orElseThrow().updateVerificationStatus(
                    com.capdi.backend.domain.expert.entity.VerificationStatusEnum.valueOf(approval),"existing reason");
        });
        var request = confirmation(fixture,attempt.revision());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/expert/me/business-registration-info/"+fixture.infoId()+"/confirmation")
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.opening_date").value("2024-02-29"))
                .andExpect(jsonPath("$.data.business_number").value("9876543215"));
        var stored = businessInfos.findById(fixture.infoId()).orElseThrow();
        assertThat(stored.getOpeningDate()).isEqualTo(LocalDate.of(2024,2,29));
        assertThat(stored.getRepresentativeName()).isEqualTo("ConfirmedOwner");
        var extract = businessService.getOcrExtraction(userId,fixture.infoId());
        assertThat(extract.getExtracted().getBusinessNumber().getValue()).isEqualTo("1234567891");
        assertThat(extract.getUserInput().getBusinessNumber()).isEqualTo("9876543215");
        assertThat(files.findById(fixture.fileId()).orElseThrow().getVerificationStatus().name()).isEqualTo(approval);
        assertThat(profiles.findByUserId(userId).orElseThrow().getVerificationStatus().name()).isEqualTo(approval);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RUNNING","SUCCEEDED","FAILED","EMPTY"})
    void confirmationRejectsOldRevisionAfterRerun(String state) throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        var first = ocrPersistence.start(userId,fixture.fileId());
        ocrPersistence.finish(userId,first,EXTRACTABLE,com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        var request = confirmation(fixture,first.revision());
        var second = ocrPersistence.start(userId,fixture.fileId());
        if (!state.equals("RUNNING")) ocrPersistence.finish(userId,second,EXTRACTABLE,
                com.capdi.backend.domain.expert.entity.OcrStatusEnum.valueOf(state),null);
        assertThatThrownBy(() -> businessService.confirmMyBusinessInformation(userId,fixture.infoId(),request))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_RESULT_STALE));
        assertThat(businessInfos.findById(fixture.infoId()).orElseThrow().getOpeningDate()).isNull();
    }

    @Test
    void confirmationRejectsOldFileAndInvalidInputWithoutSaving() throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        var first = ocrPersistence.start(userId,fixture.fileId());
        ocrPersistence.finish(userId,first,EXTRACTABLE,com.capdi.backend.domain.expert.entity.OcrStatusEnum.SUCCEEDED,null);
        var invalid = confirmation(fixture,first.revision());
        org.springframework.test.util.ReflectionTestUtils.setField(invalid,"businessNumber","1234567890");
        assertThatThrownBy(() -> businessService.confirmMyBusinessInformation(userId,fixture.infoId(),invalid))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.capdi.backend.global.exception.ErrorCode.INVALID_INPUT));
        assertThat(businessInfos.findById(fixture.infoId()).orElseThrow().getOpeningDate()).isNull();
        var oldRequest = confirmation(fixture,first.revision());
        Long replacement = service.uploadFile(userId,new MockMultipartFile("file","confirm-new.png","image/png",png),
                "BUSINESS_REGISTRATION").getFile().getId();
        transaction.executeWithoutResult(tx -> businessInfos.findById(fixture.infoId()).orElseThrow().update(
                files.findById(replacement).orElseThrow(),"9876543215","CurrentOwner","CurrentCompany"));
        assertThatThrownBy(() -> businessService.confirmMyBusinessInformation(userId,fixture.infoId(),oldRequest))
                .isInstanceOfSatisfying(CustomException.class,e -> assertThat(e.getErrorCode())
                        .isEqualTo(com.capdi.backend.global.exception.ErrorCode.OCR_RESULT_STALE));
        assertThat(businessInfos.findById(fixture.infoId()).orElseThrow().getRepresentativeName()).isEqualTo("CurrentOwner");
    }

    @Test
    void legacyDateOmissionIsCompatibleAndInvalidCalendarDateIsSafe400() throws Exception {
        var fixture = extractionFixture("BUSINESS_REGISTRATION");
        assertThat(businessInfos.findById(fixture.infoId()).orElseThrow().getOpeningDate()).isNull();
        String json = "{\"file_id\":"+fixture.fileId()+",\"business_number\":\"9876543215\","
                +"\"representative_name\":\"LegacyOwner\",\"company_name\":\"LegacyCompany\",\"opening_date\":\"2024-02-29\"}";
        var update = mapper.readValue(json,com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest.class);
        businessService.updateMyBusinessRegistrationInfo(userId,fixture.infoId(),update);
        String oldJson = json.replace(",\"opening_date\":\"2024-02-29\"","");
        businessService.updateMyBusinessRegistrationInfo(userId,fixture.infoId(),
                mapper.readValue(oldJson,com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest.class));
        assertThat(businessInfos.findById(fixture.infoId()).orElseThrow().getOpeningDate()).isEqualTo(LocalDate.of(2024,2,29));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                        "/api/expert/me/business-registration-info/"+fixture.infoId())
                .contextPath("/api").header("Authorization","Bearer "+jwt.generateToken(userId,UserTypeEnum.EXPERT))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json.replace("2024-02-29","2023-02-29")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void v15UpgradesRowsCreatedAtV14WithoutInventingOpeningDate() throws Exception {
        // A second disposable DB proves actual upgrade compatibility, not only fresh-schema insertion.
        try (var legacy = new MySQLContainer<>("mysql:8.4").withDatabaseName("capdi_issue52_legacy_test")
                .withUsername("legacy_test").withPassword(UUID.randomUUID().toString())
                .withTmpFs(Map.of("/var/lib/mysql","rw")).withLabel("capdi.test.scope","issue52-legacy-isolated")) {
            legacy.start();
            var v14 = Flyway.configure().dataSource(legacy.getJdbcUrl(),legacy.getUsername(),legacy.getPassword()).target("14").load();
            v14.migrate();
            var source = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    legacy.getJdbcUrl(),legacy.getUsername(),legacy.getPassword());
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO users(id,user_type,name,email,phone,created_at,updated_at) VALUES(1,'EXPERT','legacy','legacy@example.test','0',NOW(),NOW())");
            jdbc.update("INSERT INTO expert_profiles(id,user_id,specialty,experience_years) VALUES(1,1,'',0)");
            jdbc.update("INSERT INTO files(id,expert_profile_id,original_name,stored_name,file_path,file_size,mime_type,file_type,verification_status,created_at,updated_at) VALUES(1,1,'legacy.png','legacy.png','/test/legacy.png',1,'PNG','BUSINESS_REGISTRATION','APPROVED',NOW(),NOW())");
            jdbc.update("INSERT INTO expert_business_registration_infos(id,expert_profile_id,file_id,business_number,representative_name,company_name,created_at,updated_at) VALUES(1,1,1,'1234567891','LegacyOwner','LegacyCompany',NOW(),NOW())");
            var latest = Flyway.configure().dataSource(legacy.getJdbcUrl(),legacy.getUsername(),legacy.getPassword()).load();
            latest.migrate(); latest.validate();
            assertThat(latest.info().current().getVersion().getVersion()).isEqualTo("16");
            var row = jdbc.queryForMap("SELECT business_number,opening_date FROM expert_business_registration_infos WHERE id=1");
            assertThat(row.get("business_number")).isEqualTo("1234567891");
            assertThat(row.get("opening_date")).isNull();
            assertThat(jdbc.queryForObject("SELECT input_revision FROM expert_business_registration_infos WHERE id=1", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT confirmed_input_revision FROM expert_business_registration_infos WHERE id=1", Long.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT verification_status FROM files WHERE id=1",String.class)).isEqualTo("APPROVED");
        }
    }
}
