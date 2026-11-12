package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.client.GoogleVisionOcrClient;
import com.capdi.backend.domain.expert.controller.ExpertFileController;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.expert.util.FileVerificationUtil;
import com.capdi.backend.domain.expert.util.PdfSecurityValidator;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import com.capdi.backend.global.exception.GlobalExceptionHandler;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExpertFileRollbackTest {
    @TempDir
    Path root;
    ExpertFileRepository files;
    ExpertProfileRepository profiles;
    ExpertCertificateRepository certificates;
    BusinessRegistrationInfoRepository businesses;
    ExpertFileStorage storage;
    PdfSecurityValidator pdfValidator;
    ExpertFileService service;
    JdbcTemplate jdbc;
    TransactionTemplate transaction;
    byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

    @BeforeEach
    void setUp() {
        files = mock(ExpertFileRepository.class);
        profiles = mock(ExpertProfileRepository.class);
        certificates = mock(ExpertCertificateRepository.class);
        businesses = mock(BusinessRegistrationInfoRepository.class);
        storage = spy(new ExpertFileStorage(root.toString()));
        pdfValidator = mock(PdfSecurityValidator.class);
        service = new ExpertFileService(files, profiles, certificates, businesses,
                mock(GoogleVisionOcrClient.class), new FileVerificationUtil(), pdfValidator, storage, mock(ExpertOcrPersistenceService.class));
        when(profiles.findByUserId(10L)).thenReturn(Optional.of(ExpertProfile.builder().id(1L).build()));
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID(), "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        // A real isolated JDBC transaction; repository entities remain mocked.
        dataSource.setUrl(dataSource.getUrl() + ";DB_CLOSE_DELAY=-1");
        jdbc.execute("create table test_files (path varchar(1000) primary key)");
        transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        when(files.save(any())).thenAnswer(invocation -> {
            ExpertFile file = invocation.getArgument(0);
            jdbc.update("insert into test_files(path) values (?)", file.getFilePath());
            return file;
        });
    }

    void upload() {
        service.uploadFile(10L, new MockMultipartFile("file", "sample.png", "image/png", png), "CERTIFICATE");
    }

    @AfterEach
    void closeIsolatedDatabase() {
        jdbc.execute("shutdown");
    }

    long fileCount() throws IOException {
        try (var paths = Files.list(root)) {
            return paths.count();
        }
    }

    @Test
    void commitKeepsFileAndDatabaseRow() throws IOException {
        transaction.executeWithoutResult(status -> upload());
        assertThat(fileCount()).isEqualTo(1);
        String path = jdbc.queryForObject("select path from test_files", String.class);
        assertThat(Files.readAllBytes(Path.of(path))).isEqualTo(png);
    }

    @Test
    void rollbackAfterServiceReturnsRemovesNewFileAndRow() throws IOException {
        transaction.executeWithoutResult(status -> {
            upload();
            status.setRollbackOnly();
        });
        assertThat(fileCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from test_files", Integer.class)).isZero();
    }

    @Test
    void saveFailureRemovesFileAndPreservesOriginalException() throws IOException {
        var failure = new DataIntegrityViolationException("save failure");
        doThrow(failure).when(files).save(any());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> upload())).isSameAs(failure);
        assertThat(fileCount()).isZero();
    }

    @Test
    void flushFailureRollsBackRowAndRemovesFile() throws IOException {
        var failure = new DataIntegrityViolationException("flush failure");
        doThrow(failure).when(files).flush();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> upload())).isSameAs(failure);
        assertThat(fileCount()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from test_files", Integer.class)).isZero();
    }

    @Test
    void failureBeforeCommitAfterServiceReturnsRemovesFile() throws IOException {
        var failure = new IllegalStateException("before commit");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            upload();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw failure;
                }
            });
        })).isSameAs(failure);
        assertThat(fileCount()).isZero();
    }

    @Test
    void unknownCompletionRetainsFile() throws IOException {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            upload();
            TransactionSynchronizationManager.getSynchronizations().forEach(
                    sync -> sync.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));
            assertThat(fileCount()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    void writeFailureCleansPartialFileAndUsesStorageError() throws IOException {
        // First stream is signature validation. The second fails after writing partial bytes.
        MockMultipartFile broken = new MockMultipartFile("file", "sample.png", "image/png", png) {
            int opens;
            @Override
            public InputStream getInputStream() throws IOException {
                if (++opens == 1) {
                    return super.getInputStream();
                }
                return new InputStream() {
                    int reads;
                    @Override
                    public int read() throws IOException {
                        if (++reads > 4) {
                            throw new IOException("disk/source failure");
                        }
                        return 1;
                    }
                };
            }
        };
        assertThatThrownBy(() -> transaction.executeWithoutResult(status ->
                service.uploadFile(10L, broken, "CERTIFICATE")))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_STORAGE_FAILED));
        assertThat(fileCount()).isZero();
        verify(files, never()).save(any());
    }

    @Test
    void cleanupFailureDoesNotReplaceDatabaseFailure() throws IOException {
        var failure = new DataIntegrityViolationException("original");
        doThrow(failure).when(files).save(any());
        // A non-empty directory cannot be deleted as a file, on Windows and Unix.
        doAnswer(invocation -> {
            Path path = invocation.getArgument(1);
            Files.delete(path);
            Files.createDirectory(path);
            Files.write(path.resolve("child"), png);
            return null;
        }).when(storage).write(any(), any());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> upload())).isSameAs(failure);
        assertThat(fileCount()).isEqualTo(1);
    }

    @Test
    void replacementRollbackKeepsOldFileAndDeletesNewFile() throws IOException {
        Path oldPath = Files.write(root.resolve("old.png"), png);
        ExpertFile old = ExpertFile.builder().id(50L).filePath(oldPath.toString()).build();
        transaction.executeWithoutResult(status -> {
            upload();
            service.deleteReplacedFileIfUnused(old);
            status.setRollbackOnly();
        });
        assertThat(oldPath).exists();
        assertThat(fileCount()).isEqualTo(1);
    }

    @Test
    void customValidationFailureIsNotRemapped() throws IOException {
        var failure = new CustomException(ErrorCode.INVALID_INPUT);
        doThrow(failure).when(pdfValidator).validate(any());
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> service.uploadFile(10L,
                new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[]{0x25, 0x50, 0x44, 0x46}),
                "CERTIFICATE"))).isSameAs(failure);
        assertThat(fileCount()).isZero();
    }

    @Test
    void uploadWithoutTransactionFailsBeforeCreatingFile() throws IOException {
        assertThatThrownBy(this::upload).isInstanceOfSatisfying(CustomException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR));
        assertThat(fileCount()).isZero();
    }

    @Test
    void createFailureDoesNotDeleteExistingData() throws IOException {
        var failure = new IOException("directory failure");
        doThrow(failure).when(storage).create(any());
        Path old = Files.write(root.resolve("existing.png"), png);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> upload()))
                .isInstanceOfSatisfying(CustomException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_STORAGE_FAILED));
        assertThat(Files.readAllBytes(old)).isEqualTo(png);
        assertThat(fileCount()).isEqualTo(1);
    }

    @Test
    void realPdfValidationStillAcceptsSafeDocumentAndRejectsOpenAction() throws IOException {
        PdfSecurityValidator realValidator = new PdfSecurityValidator();
        try (var document = new PDDocument();
             var output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            realValidator.validate(new MockMultipartFile("file", "safe.pdf", "application/pdf", output.toByteArray()));
            document.getDocumentCatalog().setOpenAction(
                    new PDActionJavaScript("alert(1)"));
            output.reset();
            document.save(output);
            assertThatThrownBy(() -> realValidator.validate(
                    new MockMultipartFile("file", "unsafe.pdf", "application/pdf", output.toByteArray())))
                    .isInstanceOfSatisfying(CustomException.class,
                            exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT));
        }
    }

    @Test
    void apiEnvelopeAndUploadFieldsRemainCompatible() {
        var response = transaction.execute(status -> new ExpertFileController(service,
                org.mockito.Mockito.mock(BusinessRegistrationInfoService.class))
                .uploadFile(new CustomUserDetails(10L, "EXPERT"),
                        new MockMultipartFile("file", "sample.png", "image/png", png), "CERTIFICATE"));
        var json = new ObjectMapper().findAndRegisterModules()
                .valueToTree(response.getBody());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.get("success").asBoolean()).isTrue();
        assertThat(json.has("message")).isTrue();
        assertThat(json.at("/data/file/original_name").asText()).isEqualTo("sample.png");
        assertThat(json.at("/data/file/mime_type").asText()).isEqualTo("PNG");
        assertThat(json.at("/data/file/purpose").asText()).isEqualTo("CERTIFICATE");
        assertThat(json.at("/data/file/size").asInt()).isEqualTo(png.length);
        var error = new GlobalExceptionHandler()
                .handleCustomException(new CustomException(ErrorCode.FILE_STORAGE_FAILED));
        var errorJson = new ObjectMapper().valueToTree(error.getBody());
        assertThat(error.getStatusCode().value()).isEqualTo(500);
        assertThat(errorJson.get("success").asBoolean()).isFalse();
        assertThat(errorJson.has("data")).isFalse();
        assertThat(errorJson.get("message").asText()).isEqualTo(ErrorCode.FILE_STORAGE_FAILED.getMessage());
    }
}
