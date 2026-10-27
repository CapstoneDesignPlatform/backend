package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.client.GoogleVisionOcrClient;
import com.capdi.backend.domain.expert.dto.ExpertFileDownloadUrlResponse;
import com.capdi.backend.domain.expert.dto.ExpertFileUploadResponse;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.entity.MimeTypeEnum;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import com.capdi.backend.domain.expert.util.FileVerificationUtil;
import com.capdi.backend.domain.expert.util.PdfSecurityValidator;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ExpertFileService {

    private static final String UPLOAD_DIR =
            System.getProperty("user.dir") + "/uploads/files";

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    private final ExpertFileRepository expertFileRepository;
    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertCertificateRepository expertCertificateRepository;
    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;
    private final GoogleVisionOcrClient googleVisionOcrClient;
    private final FileVerificationUtil fileVerificationUtil;
    private final PdfSecurityValidator pdfSecurityValidator;

    @Transactional
    public ExpertFileUploadResponse uploadFile(Long loginUserId, MultipartFile file, String purpose) {

        log.info("[FILE UPLOAD] START filename={}, contentType={}, size={}, purpose={}",
                file != null ? file.getOriginalFilename() : null,
                file != null ? file.getContentType() : null,
                file != null ? file.getSize() : null,
                purpose);

        validateUploadRequest(file, purpose);
        log.info("[FILE UPLOAD] 1. validateUploadRequest PASS");

        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        log.info("[FILE UPLOAD] 2. getMyExpertProfile PASS");

        MimeTypeEnum mimeType = parseMimeType(file);
        log.info("[FILE UPLOAD] 3. parseMimeType PASS: {}", mimeType);

        validateFileSignature(file, mimeType);
        log.info("[FILE UPLOAD] 4. validateFileSignature PASS");

        if (mimeType == MimeTypeEnum.PDF) {
            pdfSecurityValidator.validate(file);
            log.info("[FILE UPLOAD] 5. PdfSecurityValidator PASS");
        }

        try {
            Path uploadDir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadDir);

            String originalName = file.getOriginalFilename();
            String storedName = UUID.randomUUID() + "_" + originalName;
            Path savePath = uploadDir.resolve(storedName).toAbsolutePath().normalize();

            file.transferTo(savePath.toFile());

            log.info("[FILE UPLOAD] 6. Physical file save PASS: {}", savePath);

            ExpertFile expertFile = ExpertFile.builder()
                    .expertProfile(expertProfile)
                    .originalName(originalName)
                    .storedName(storedName)
                    .filePath(savePath.toString())
                    .fileSize(file.getSize())
                    .mimeType(mimeType)
                    .fileType(parseFileType(purpose))
                    .build();

            ExpertFile savedFile = expertFileRepository.save(expertFile);

            log.info("[FILE UPLOAD] 7. DB save PASS: fileId={}", savedFile.getId());

            return ExpertFileUploadResponse.from(savedFile);

        } catch (Exception e) {

            log.error("[FILE UPLOAD] SAVE FAILED type={}, message={}",
                    e.getClass().getName(),
                    e.getMessage(),
                    e);

            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
    @Transactional
    public void runOcr(Long loginUserId, Long fileId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile expertFile = getOwnedFile(fileId, expertProfile);

        try {
            String ocrResult = googleVisionOcrClient.requestOcr(
                    expertFile.getFilePath()
            );

            expertFile.completeOcr(ocrResult);

        } catch (Exception e) {
            log.error("OCR failed. fileId={}, path={}, errorType={}, message={}",
                    fileId,
                    expertFile.getFilePath(),
                    e.getClass().getName(),
                    e.getMessage(),
                    e
            );
            expertFile.failOcr("OCR processing failed.");
        }
    }

    public ExpertFileDownloadUrlResponse getDownloadUrl(Long loginUserId, Long fileId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile expertFile = getOwnedFile(fileId, expertProfile);
        return ExpertFileDownloadUrlResponse.from(expertFile);
    }

    public Resource downloadFile(Long loginUserId, Long fileId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile expertFile = getOwnedFile(fileId, expertProfile);

        try {
            Path filePath = Paths.get(expertFile.getFilePath()).toAbsolutePath().normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new CustomException(ErrorCode.NOT_FOUND);
            }

            return resource;

        } catch (MalformedURLException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Transactional
    public void deleteReplacedFileIfUnused(ExpertFile replacedFile) {
        if (isReferenced(replacedFile.getId())) {
            return;
        }

        expertFileRepository.delete(replacedFile);
        deletePhysicalFileAfterCommit(replacedFile);
    }

    private void validateUploadRequest(MultipartFile file, String purpose) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        String originalFilename = file.getOriginalFilename();

        if (originalFilename == null || originalFilename.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (originalFilename.contains("..")
                || originalFilename.contains("/")
                || originalFilename.contains("\\")) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        parseFileType(purpose);
    }
    private void validateFileSignature(MultipartFile file, MimeTypeEnum mimeType) {
        try {
            byte[] header = file.getInputStream().readNBytes(8);

            boolean valid = switch (mimeType) {
                case PDF -> header.length >= 4
                        && header[0] == 0x25
                        && header[1] == 0x50
                        && header[2] == 0x44
                        && header[3] == 0x46;

                case JPG -> header.length >= 3
                        && (header[0] & 0xFF) == 0xFF
                        && (header[1] & 0xFF) == 0xD8
                        && (header[2] & 0xFF) == 0xFF;

                case PNG -> header.length >= 8
                        && (header[0] & 0xFF) == 0x89
                        && header[1] == 0x50
                        && header[2] == 0x4E
                        && header[3] == 0x47
                        && header[4] == 0x0D
                        && header[5] == 0x0A
                        && header[6] == 0x1A
                        && header[7] == 0x0A;
            };

            if (!valid) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }

        } catch (IOException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private FileTypeEnum parseFileType(String purpose) {
        try {
            return FileTypeEnum.from(purpose);
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private MimeTypeEnum parseMimeType(MultipartFile file) {
        try {
            return MimeTypeEnum.from(file.getContentType(), file.getOriginalFilename());
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private ExpertFile getExpertFile(Long fileId) {
        return expertFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private ExpertFile getOwnedFile(Long fileId, ExpertProfile expertProfile) {
        ExpertFile expertFile = getExpertFile(fileId);

        if (!expertFile.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return expertFile;
    }

    private boolean isReferenced(Long fileId) {
        return expertCertificateRepository.existsByFile_Id(fileId)
                || businessRegistrationInfoRepository.existsByFile_Id(fileId);
    }

    private void deletePhysicalFileAfterCommit(ExpertFile expertFile) {
        Runnable deletePhysicalFile = () -> {
            try {
                Files.deleteIfExists(Path.of(expertFile.getFilePath()));
            } catch (IOException e) {
                log.warn("Failed to delete replaced file from disk. fileId={}, path={}",
                        expertFile.getId(), expertFile.getFilePath(), e);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deletePhysicalFile.run();
                }
            });
            return;
        }

        deletePhysicalFile.run();
    }
}