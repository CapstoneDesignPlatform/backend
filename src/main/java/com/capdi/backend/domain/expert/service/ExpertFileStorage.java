package com.capdi.backend.domain.expert.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Component
@Slf4j
public class ExpertFileStorage {

    private final Path uploadDir;

    public ExpertFileStorage(@Value("${file.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public Path create(String originalName) throws IOException {
        Files.createDirectories(uploadDir);
        Path path = uploadDir.resolve(UUID.randomUUID() + "_" + originalName).normalize();
        if (!path.getParent().equals(uploadDir)) {
            throw new IOException("Invalid storage path");
        }
        // CREATE_NEW semantics: only files created by this request may be cleaned up.
        return Files.createFile(path);
    }

    public void write(MultipartFile file, Path path) throws IOException {
        try (var input = file.getInputStream()) {
            Files.copy(input, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | RuntimeException e) {
            log.error("Failed to clean uploaded file. path={}", path, e);
        }
    }
}
