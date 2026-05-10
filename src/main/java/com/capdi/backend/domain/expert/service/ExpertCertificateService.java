package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertCertificateCreateRequest;
import com.capdi.backend.domain.expert.dto.ExpertCertificateResponse;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
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

    @Transactional
    public ExpertCertificateResponse createMyCertificate(Long loginUserId, ExpertCertificateCreateRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile file = getOwnedFile(request.getFileId(), expertProfile);
        ExpertCertificate expertCertificate = createCertificate(request, expertProfile, file);

        ExpertCertificate savedCertificate = expertCertificateRepository.save(expertCertificate);

        return ExpertCertificateResponse.from(savedCertificate);
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
                    request.getOwnerName()
            );
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
    }

    private ExpertFile getOwnedFile(Long fileId, ExpertProfile expertProfile) {
        ExpertFile file = expertFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!file.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return file;
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}
