package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertFileDownloadUrlResponse;
import com.capdi.backend.domain.expert.dto.ExpertFileUploadResponse;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertFileService {

    private static final String UPLOAD_DIR =
            System.getProperty("user.dir") + "/uploads/expert-verification";

    private final ExpertFileRepository expertFileRepository;
    private final ExpertProfileRepository expertProfileRepository;

    @Transactional
    public ExpertFileUploadResponse uploadFile(MultipartFile file, String purpose) {
        validateUploadRequest(file, purpose);

        ExpertProfile expertProfile = getMyExpertProfile();

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
                    .mimeType(file.getContentType())
                    .fileType(FileTypeEnum.valueOf(purpose))
                    .build();

            ExpertFile savedFile = expertFileRepository.save(expertFile);

            return ExpertFileUploadResponse.from(savedFile);

        } catch (Exception e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public ExpertFileDownloadUrlResponse getDownloadUrl(Long fileId) {
        ExpertFile expertFile = getExpertFile(fileId);
        return ExpertFileDownloadUrlResponse.from(expertFile);
    }

    public Resource downloadFile(Long fileId) {
        ExpertFile expertFile = getExpertFile(fileId);

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

    private void validateUploadRequest(MultipartFile file, String purpose) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        if (!"EXPERT_VERIFICATION".equals(purpose)) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private ExpertProfile getMyExpertProfile() {
        Long loginUserId = getLoginUserId();

        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private ExpertFile getExpertFile(Long fileId) {
        return expertFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private Long getLoginUserId() {
        return 1L; // TODO: JWT 적용 후 auth context에서 가져오기
    }
}