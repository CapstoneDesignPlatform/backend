package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertFileDownloadUrlResponse;
import com.capdi.backend.domain.expert.dto.ExpertFileUploadResponse;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.entity.MimeTypeEnum;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.io.IOException;
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
            System.getProperty("user.dir") + "/uploads/files"; // 파일 업로드 성공시, 업로드 디렉터리 생김 .

    private final ExpertFileRepository expertFileRepository;
    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertCertificateRepository expertCertificateRepository;
    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;

    @Transactional
    public ExpertFileUploadResponse uploadFile(Long loginUserId, MultipartFile file, String purpose) {
        validateUploadRequest(file, purpose);

        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        MimeTypeEnum mimeType = parseMimeType(file);

        try {
            Path uploadDir = Paths.get(UPLOAD_DIR);
            Files.createDirectories(uploadDir);

            String originalName = file.getOriginalFilename();
            String storedName = UUID.randomUUID() + "_" + originalName;
            Path savePath = uploadDir.resolve(storedName).toAbsolutePath().normalize();

            file.transferTo(savePath.toFile());

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

            return ExpertFileUploadResponse.from(savedFile);

        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
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

        parseFileType(purpose);
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
