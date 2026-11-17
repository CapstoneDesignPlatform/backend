package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpertFileLockService {
    private final ExpertFileRepository files;
    private final EntityManager entityManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public ExpertFile lockCurrent(Long fileId) {
        ExpertFile file = files.findForOcr(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        // A locking query alone does not replace an OSIV/first-level-cache snapshot.
        // Refresh under the same row lock before reading status, token, or revision.
        entityManager.refresh(file, LockModeType.PESSIMISTIC_WRITE);
        return file;
    }
}
