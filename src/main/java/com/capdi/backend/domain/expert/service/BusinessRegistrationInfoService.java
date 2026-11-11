package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.entity.OcrStatusEnum;

import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoCreateRequest;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoResponse;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationExtractionResponse;
import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.domain.expert.util.BusinessRegistrationExtractor;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BusinessRegistrationInfoService {

    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;
    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertFileLockService fileLock;
    private final ExpertFileService expertFileService;
    private final BusinessRegistrationExtractor extractor;
    private final jakarta.persistence.EntityManager entityManager;
    private final com.capdi.backend.domain.expert.util.BusinessInputValidator inputValidator;

    // File-only snapshot: no business-info row is needed or created. Lock/refresh prevents stale OSIV reads.
    @Transactional
    public BusinessRegistrationExtractionResponse getOcrExtractionByFile(Long userId, Long fileId) {
        ExpertProfile profile = getMyExpertProfile(userId);
        ExpertFile file = getOwnedBusinessRegistrationFile(fileId, profile);
        String text = file.getOcrStatus() == OcrStatusEnum.SUCCEEDED ? file.getOcrRawText() : null;
        return BusinessRegistrationExtractionResponse.from(null, file, extractor.extract(text));
    }

    @Transactional
    public BusinessRegistrationInfoResponse confirmMyBusinessInformation(Long userId, Long infoId,
            com.capdi.backend.domain.expert.dto.BusinessConfirmationRequest request) {
        ExpertProfile profile = getMyExpertProfile(userId);
        BusinessRegistrationInfo info = getOwnedBusinessRegistrationInfo(infoId, profile);
        if (!info.getFile().getId().equals(request.getFileId())) throw new CustomException(ErrorCode.OCR_RESULT_STALE);
        ExpertFile file = getOwnedBusinessRegistrationFile(request.getFileId(), profile);
        if (request.getOcrRevision() == null || file.getOcrRevision() != request.getOcrRevision()
                || file.getOcrStatus() != OcrStatusEnum.SUCCEEDED) throw new CustomException(ErrorCode.OCR_RESULT_STALE);
        info.confirm(inputValidator.number(request.getBusinessNumber()), inputValidator.name(request.getRepresentativeName()),
                inputValidator.name(request.getCompanyName()), inputValidator.date(request.getOpeningDate()));
        info.markConfirmed(file.getId(), file.getOcrRevision());
        // Confirmation records user input only, never legacy file or administrator approval.
        return BusinessRegistrationInfoResponse.from(info);
    }

    // GET performs no data mutation, but uses a write-capable transaction for row locks.
    @Transactional
    public BusinessRegistrationExtractionResponse getOcrExtraction(Long loginUserId, Long infoId) {
        ExpertProfile profile = getMyExpertProfile(loginUserId);
        // Lock order for existing records is business-info, then file (also used by update).
        BusinessRegistrationInfo info = getOwnedBusinessRegistrationInfo(infoId, profile);
        ExpertFile file = getOwnedBusinessRegistrationFile(info.getFile().getId(), profile);
        String text = file.getOcrStatus() == OcrStatusEnum.SUCCEEDED ? file.getOcrRawText() : null;
        return BusinessRegistrationExtractionResponse.from(info, file, extractor.extract(text));
    }

    @Transactional
    public BusinessRegistrationInfoResponse createMyBusinessRegistrationInfo(
            Long loginUserId,
            BusinessRegistrationInfoCreateRequest request
    ) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile file = getOwnedBusinessRegistrationFile(request.getFileId(), expertProfile);

        // Editable input and OCR candidates are not file-review decisions.
        BusinessRegistrationInfo businessRegistrationInfo = BusinessRegistrationInfo.create(
                expertProfile,
                file,
                inputValidator.number(request.getBusinessNumber()),
                inputValidator.name(request.getRepresentativeName()),
                inputValidator.name(request.getCompanyName())
        );

        BusinessRegistrationInfo savedInfo = businessRegistrationInfoRepository.save(businessRegistrationInfo);
        if (request.getOpeningDate() != null) savedInfo.updateOpeningDate(inputValidator.date(request.getOpeningDate()));
        return BusinessRegistrationInfoResponse.from(savedInfo);
    }

    @Transactional
    public BusinessRegistrationInfoResponse updateMyBusinessRegistrationInfo(
            Long loginUserId,
            Long businessRegistrationInfoId,
            BusinessRegistrationInfoUpdateRequest request
    ) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        BusinessRegistrationInfo businessRegistrationInfo =
                getOwnedBusinessRegistrationInfo(businessRegistrationInfoId, expertProfile);
        ExpertFile previousFile = businessRegistrationInfo.getFile();
        ExpertFile newFile = getOwnedBusinessRegistrationFile(request.getFileId(), expertProfile);

        businessRegistrationInfo.update(
                newFile,
                inputValidator.number(request.getBusinessNumber()),
                inputValidator.name(request.getRepresentativeName()),
                inputValidator.name(request.getCompanyName())
        );

        if (request.getOpeningDate() != null) businessRegistrationInfo.updateOpeningDate(inputValidator.date(request.getOpeningDate()));

        if (!previousFile.getId().equals(newFile.getId())) {
            businessRegistrationInfoRepository.flush();
            expertFileService.deleteReplacedFileIfUnused(previousFile);
        }

        return BusinessRegistrationInfoResponse.from(businessRegistrationInfo);
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }

    private BusinessRegistrationInfo getOwnedBusinessRegistrationInfo(
            Long businessRegistrationInfoId,
            ExpertProfile expertProfile
    ) {
        BusinessRegistrationInfo businessRegistrationInfo = businessRegistrationInfoRepository
                .findForUpdate(businessRegistrationInfoId)
                .orElseThrow(() -> new CustomException(ErrorCode.BUSINESS_REGISTRATION_INFO_NOT_FOUND));

        // OSIV may already hold the old file association or user input in its first-level cache.
        entityManager.refresh(businessRegistrationInfo, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);

        if (!businessRegistrationInfo.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return businessRegistrationInfo;
    }

    private ExpertFile getOwnedBusinessRegistrationFile(Long fileId, ExpertProfile expertProfile) {
        // Use the latest OCR revision and hold its row lock through comparison and commit.
        // OCR start/finish must acquire the same lock and cannot change this revision mid-save.
        ExpertFile file = fileLock.lockCurrent(fileId);

        if (!file.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (file.getFileType() != FileTypeEnum.BUSINESS_REGISTRATION) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return file;
    }
}
