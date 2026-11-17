package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.MimeTypeEnum;
import com.capdi.backend.domain.expert.entity.OcrStatusEnum;
import com.capdi.backend.domain.expert.dto.ExpertFileOcrResponse;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertOcrPersistenceService {
    private final ExpertFileRepository files;
    private final ExpertFileLockService fileLock;
    private static final int LEASE_MINUTES = 5;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Attempt start(Long userId, Long fileId) {
        ExpertFile file = owned(userId, fileId);
        LocalDateTime now = LocalDateTime.now();
        if (file.getOcrStatus() == OcrStatusEnum.RUNNING && file.getOcrStartedAt() != null
                && file.getOcrStartedAt().isAfter(now.minusMinutes(LEASE_MINUTES))) {
            throw new CustomException(ErrorCode.OCR_ALREADY_RUNNING);
        }
        String token = UUID.randomUUID().toString();
        file.startOcr(token, now);
        return new Attempt(fileId, token, file.getFilePath(), file.getMimeType(), file.getOcrRevision());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long userId, Attempt attempt, String text, OcrStatusEnum status, ErrorCode error) {
        ExpertFile file = owned(userId, attempt.fileId());
        if (!attempt.token().equals(file.getOcrRunToken()) || attempt.revision() != file.getOcrRevision()
                || !attempt.path().equals(file.getFilePath())) {
            throw new CustomException(ErrorCode.OCR_RESULT_STALE);
        }
        file.finishOcr(text, status, error == null ? null : error.name());
    }

    public ExpertFileOcrResponse status(Long userId, Long fileId) {
        ExpertFile file = files.findById(fileId).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (!file.getExpertProfile().getUser().getId().equals(userId)) throw new CustomException(ErrorCode.FORBIDDEN);
        return ExpertFileOcrResponse.from(file);
    }

    private ExpertFile owned(Long userId, Long fileId) {
        ExpertFile file = fileLock.lockCurrent(fileId);
        if (!file.getExpertProfile().getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        return file;
    }
    public record Attempt(Long fileId, String token, String path, MimeTypeEnum mimeType, long revision) { }
}
