package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.*;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult.*;
import com.capdi.backend.domain.expert.entity.*;
import com.capdi.backend.domain.expert.entity.BusinessVerification.*;
import com.capdi.backend.domain.expert.repository.*;
import com.capdi.backend.domain.expert.util.BusinessInputValidator;
import com.capdi.backend.global.exception.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessVerificationPersistenceService {
    private final BusinessRegistrationInfoRepository infos;
    private final ExpertProfileRepository profiles;
    private final BusinessVerificationRepository history;
    private final ExpertFileLockService fileLock;
    private final EntityManager em;
    private final BusinessInputValidator validator;

    public record Snapshot(Long infoId, Long fileId, long ocr, long input, String path,
            String number, String representative, String company, LocalDate opening, boolean confirmed) {
        @Override public String toString() { return "VerificationSnapshot[redacted]"; }
        boolean sameSource(Snapshot other) {
            return fileId.equals(other.fileId) && ocr == other.ocr && input == other.input
                    && Objects.equals(path, other.path) && confirmed && other.confirmed;
        }
    }
    public record Attempt(String token, Kind kind, Snapshot source, String sha) {
        @Override public String toString() { return "VerificationAttempt[redacted]"; }
    }

    private Snapshot current(Long user, Long id) {
        var profile = profiles.findByUserId(user).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        var info = infos.findForUpdate(id).orElseThrow(() -> new CustomException(ErrorCode.BUSINESS_REGISTRATION_INFO_NOT_FOUND));
        em.refresh(info, LockModeType.PESSIMISTIC_WRITE);
        if (!info.getExpertProfile().getId().equals(profile.getId())) throw new CustomException(ErrorCode.FORBIDDEN);
        var file = fileLock.lockCurrent(info.getFile().getId());
        if (!file.getExpertProfile().getId().equals(profile.getId())) throw new CustomException(ErrorCode.FORBIDDEN);
        if (file.getFileType() != FileTypeEnum.BUSINESS_REGISTRATION) throw new CustomException(ErrorCode.INVALID_INPUT);
        return new Snapshot(id, file.getId(), file.getOcrRevision(), info.getInputRevision(), file.getFilePath(),
                info.getBusinessNumber(), info.getRepresentativeName(), info.getCompanyName(), info.getOpeningDate(),
                file.getOcrStatus() == OcrStatusEnum.SUCCEEDED && info.isConfirmedFor(file.getId(), file.getOcrRevision()));
    }

    private void check(Snapshot s, BusinessVerificationRequest request) {
        if (!Objects.equals(s.fileId(), request.getFileId()) || !Objects.equals(s.ocr(), request.getOcrRevision())
                || !Objects.equals(s.input(), request.getInputRevision())) throw new CustomException(ErrorCode.OCR_RESULT_STALE);
        if (!s.confirmed()) throw new CustomException(ErrorCode.NTS_CONFIRMATION_REQUIRED);
        validator.number(s.number()); validator.name(s.representative()); validator.name(s.company()); validator.date(s.opening());
        if (request.getKind() == null) throw new CustomException(ErrorCode.INVALID_INPUT);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Snapshot prepare(Long user, Long id, BusinessVerificationRequest request) {
        var s = current(user, id); check(s, request); return s;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Snapshot observe(Long user, Long id) { return current(user, id); }

    private boolean matches(BusinessVerification row, Snapshot s, String sha) {
        return s.confirmed() && row.getFileId().equals(s.fileId()) && row.getOcrRevision() == s.ocr()
                && row.getInputRevision() == s.input() && row.getFileSha256().equals(sha);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Attempt start(Long user, Long id, BusinessVerificationRequest request, Snapshot observed, String sha) {
        var s = current(user, id); check(s, request);
        if (!s.sameSource(observed)) throw new CustomException(ErrorCode.OCR_RESULT_STALE);
        if (sha == null) throw new CustomException(ErrorCode.NTS_FILE_UNAVAILABLE);
        for (var row : history.findByBusinessRegistrationInfoIdAndExecutionStatus(id, ExecutionStatus.RUNNING)) {
            em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
            if (row.expired()) row.finish(NtsBusinessResult.unavailable(Failure.EXECUTION_EXPIRED), false, matches(row, s, sha));
            else if (row.getExecutionStatus() == ExecutionStatus.RUNNING && row.getKind() == request.getKind() && matches(row, s, sha))
                throw new CustomException(ErrorCode.NTS_ALREADY_RUNNING);
        }
        var row = history.saveAndFlush(BusinessVerification.start(id, request.getKind(), s.fileId(), sha, s.ocr(), s.input()));
        return new Attempt(row.getExecutionId(), row.getKind(), s, sha);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessVerificationResponse complete(Long user, Long id, Attempt attempt, String sha, NtsBusinessResult result, boolean lookup) {
        var s = current(user, id);
        var row = history.findForUpdate(id, attempt.token()).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        boolean valid = matches(row, s, sha) && attempt.source().sameSource(s);
        row.finish(row.expired() ? NtsBusinessResult.unavailable(Failure.EXECUTION_EXPIRED) : result, lookup, valid);
        history.flush();
        return BusinessVerificationResponse.from(row, valid);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storageFailed(Long user, Long id, Attempt attempt) {
        var s = current(user, id);
        var row = history.findForUpdate(id, attempt.token()).orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        row.finish(NtsBusinessResult.unavailable(Failure.RESULT_STORAGE_FAILED), false, matches(row, s, attempt.sha()));
        history.flush();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BusinessVerificationResponse latest(Long user, Long id, Snapshot observed, String sha) {
        var s = current(user, id);
        var row = history.findFirstByBusinessRegistrationInfoIdOrderByIdDesc(id)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        em.refresh(row, LockModeType.PESSIMISTIC_WRITE);
        boolean valid = observed.sameSource(s) && matches(row, s, sha);
        if (row.expired()) row.finish(NtsBusinessResult.unavailable(Failure.EXECUTION_EXPIRED), false, valid);
        return BusinessVerificationResponse.from(row, valid);
    }
}
