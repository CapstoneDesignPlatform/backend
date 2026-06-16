package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertCertificateCreateRequest;
import com.capdi.backend.domain.expert.dto.ExpertCertificateResponse;
import com.capdi.backend.domain.expert.dto.ExpertCertificateUpdateRequest;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertFileRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertCertificateService {

    private final ExpertCertificateRepository expertCertificateRepository;
    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertFileRepository expertFileRepository;
    private final ExpertFileService expertFileService;

    @Transactional
    public ExpertCertificateResponse createMyCertificate(Long loginUserId, ExpertCertificateCreateRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile file = getOwnedCertificateFile(request.getFileId(), expertProfile);
        ExpertCertificate expertCertificate = createCertificate(request, expertProfile, file);

        ExpertCertificate savedCertificate = expertCertificateRepository.save(expertCertificate);

        return ExpertCertificateResponse.from(savedCertificate);
    }

    @Transactional
    public ExpertCertificateResponse updateMyCertificate(
            Long loginUserId,
            Long certificateId,
            ExpertCertificateUpdateRequest request
    ) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertCertificate expertCertificate = getOwnedCertificate(certificateId, expertProfile);
        ExpertFile previousFile = expertCertificate.getFile();
        ExpertFile newFile = getOwnedCertificateFile(request.getFileId(), expertProfile);

        updateCertificate(expertCertificate, request, newFile);

        if (!previousFile.getId().equals(newFile.getId())) {
            expertCertificateRepository.flush();
            expertFileService.deleteReplacedFileIfUnused(previousFile);
        }

        return ExpertCertificateResponse.from(expertCertificate);
    }

    private ExpertCertificate createCertificate(
            ExpertCertificateCreateRequest request,
            ExpertProfile expertProfile,
            ExpertFile file
    ) {
        try {
            return ExpertCertificate.create(
                    expertProfile,
                    file,
                    request.getCertificateName(),
                    request.getCertificateNumber(),
                    request.getIssueDate(),
                    request.getExpiryDate(),
                    request.getOwnerName()
            );
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private void updateCertificate(
            ExpertCertificate expertCertificate,
            ExpertCertificateUpdateRequest request,
            ExpertFile file
    ) {
        try {
            expertCertificate.update(
                    file,
                    request.getCertificateName(),
                    request.getCertificateNumber(),
                    request.getIssueDate(),
                    request.getExpiryDate(),
                    request.getOwnerName()
            );
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private ExpertCertificate getOwnedCertificate(Long certificateId, ExpertProfile expertProfile) {
        ExpertCertificate expertCertificate = expertCertificateRepository.findById(certificateId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_CERTIFICATE_NOT_FOUND));

        if (!expertCertificate.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return expertCertificate;
    }

    private ExpertFile getOwnedCertificateFile(Long fileId, ExpertProfile expertProfile) {
        ExpertFile file = expertFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!file.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (file.getFileType() != FileTypeEnum.CERTIFICATE) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return file;
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}
