package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.client.GoogleVisionOcrClient;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.expert.util.FileVerificationUtil;
import com.capdi.backend.domain.expert.util.PdfSecurityValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpertFileStoragePathTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void usesExistingWorkingDirectoryWhenEnvironmentVariableIsAbsent() throws IOException {
        contextRunner(Map.of()).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(ReflectionTestUtils.getField(context.getBean(ExpertFileStorage.class), "uploadDir"))
                    .isEqualTo(Path.of(System.getProperty("user.dir"), "uploads/files").toAbsolutePath().normalize());
        });
    }

    @Test
    void uploadsToEnvironmentConfiguredDirectory() throws IOException {
        Path configuredRoot = temporaryDirectory.resolve("configured/uploads");
        contextRunner(Map.of("FILE_STORAGE_UPLOAD_DIR", configuredRoot.toString())).run(context -> {
            assertThat(context).hasNotFailed();
            ExpertProfile profile = ExpertProfile.builder().id(1L).build();
            when(context.getBean(ExpertProfileRepository.class).findByUserId(10L))
                    .thenReturn(Optional.of(profile));
            when(context.getBean(ExpertFileRepository.class).save(any(ExpertFile.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            byte[] png = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};

            TransactionSynchronizationManager.initSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                context.getBean(ExpertFileService.class).uploadFile(10L,
                        new MockMultipartFile("file", "sample.png", "image/png", png), "CERTIFICATE");
                TransactionSynchronizationManager.getSynchronizations().forEach(
                        synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            } finally {
                TransactionSynchronizationManager.clear();
            }

            org.mockito.ArgumentCaptor<ExpertFile> saved =
                    org.mockito.ArgumentCaptor.forClass(ExpertFile.class);
            org.mockito.Mockito.verify(context.getBean(ExpertFileRepository.class)).save(saved.capture());
            Path savedPath = Path.of(saved.getValue().getFilePath());
            assertThat(savedPath.getParent()).isEqualTo(configuredRoot.toAbsolutePath().normalize());
            assertThat(Files.readAllBytes(savedPath)).isEqualTo(png);
            try (var files = Files.list(configuredRoot)) {
                assertThat(files.count()).isEqualTo(1);
            }
        });
    }

    @Test
    void readsAndDeletesLegacyAbsolutePathWithoutRebasingToNewRoot() throws IOException {
        Path configuredRoot = temporaryDirectory.resolve("new-root");
        Path legacyPath = temporaryDirectory.resolve("legacy-file.png");
        byte[] content = {1, 2, 3};
        Files.write(legacyPath, content);
        contextRunner(Map.of("FILE_STORAGE_UPLOAD_DIR", configuredRoot.toString())).run(context -> {
            ExpertProfile profile = ExpertProfile.builder().id(1L).build();
            ExpertFile legacyFile = ExpertFile.builder().id(2L).expertProfile(profile)
                    .filePath(legacyPath.toAbsolutePath().toString()).build();
            when(context.getBean(ExpertProfileRepository.class).findByUserId(10L))
                    .thenReturn(Optional.of(profile));
            when(context.getBean(ExpertFileRepository.class).findById(2L))
                    .thenReturn(Optional.of(legacyFile));
            ExpertFileService service = context.getBean(ExpertFileService.class);

            try (var input = service.downloadFile(10L, 2L).getInputStream()) {
                assertThat(input.readAllBytes()).isEqualTo(content);
            }
            TransactionSynchronizationManager.initSynchronization();
            try {
                service.deleteReplacedFileIfUnused(legacyFile);
                assertThat(legacyPath).exists();
                for (TransactionSynchronization synchronization :
                        TransactionSynchronizationManager.getSynchronizations()) {
                    synchronization.afterCommit();
                }
                assertThat(legacyPath).doesNotExist();
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            assertThat(legacyFile.getFilePath()).isEqualTo(legacyPath.toAbsolutePath().toString());
            assertThat(configuredRoot).doesNotExist();
        });
    }

    private ApplicationContextRunner contextRunner(Map<String, Object> environmentVariables) throws IOException {
        var yamlSources = new YamlPropertySourceLoader()
                .load("application-yaml", new ClassPathResource("application.yml"));
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource("systemEnvironment", environmentVariables));
                    // Host JVM settings must not override the two configuration cases under test.
                    sources.addFirst(new MapPropertySource("path-test", Map.of(
                            "file.storage.upload-dir", yamlSources.get(0).getProperty("file.storage.upload-dir"))));
                })
                .withBean(ExpertFileRepository.class, () -> mock(ExpertFileRepository.class))
                .withBean(ExpertProfileRepository.class, () -> mock(ExpertProfileRepository.class))
                .withBean(ExpertCertificateRepository.class, () -> mock(ExpertCertificateRepository.class))
                .withBean(BusinessRegistrationInfoRepository.class, () -> mock(BusinessRegistrationInfoRepository.class))
                .withBean(GoogleVisionOcrClient.class, () -> mock(GoogleVisionOcrClient.class))
                .withBean(FileVerificationUtil.class, () -> mock(FileVerificationUtil.class))
                .withBean(PdfSecurityValidator.class, () -> mock(PdfSecurityValidator.class))
                .withBean(ExpertFileStorage.class)
                .withBean(ExpertFileService.class);
    }
}
