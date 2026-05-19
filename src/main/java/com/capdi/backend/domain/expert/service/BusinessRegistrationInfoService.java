package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoCreateRequest;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoResponse;
import com.capdi.backend.domain.expert.dto.BusinessRegistrationInfoUpdateRequest;
import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
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
public class BusinessRegistrationInfoService {

    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;
    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertFileRepository expertFileRepository;
    private final ExpertFileService expertFileService;

    @Transactional
    public BusinessRegistrationInfoResponse createMyBusinessRegistrationInfo(
            Long loginUserId,
            BusinessRegistrationInfoCreateRequest request
    ) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        ExpertFile file = getOwnedBusinessRegistrationFile(request.getFileId(), expertProfile);

        BusinessRegistrationInfo businessRegistrationInfo = BusinessRegistrationInfo.create(
                expertProfile,
                file,
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getCompanyName()
        );

        BusinessRegistrationInfo savedInfo = businessRegistrationInfoRepository.save(businessRegistrationInfo);
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
                request.getBusinessNumber(),
                request.getRepresentativeName(),
                request.getCompanyName()
        );

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
                .findById(businessRegistrationInfoId)
                .orElseThrow(() -> new CustomException(ErrorCode.BUSINESS_REGISTRATION_INFO_NOT_FOUND));

        if (!businessRegistrationInfo.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return businessRegistrationInfo;
    }

    private ExpertFile getOwnedBusinessRegistrationFile(Long fileId, ExpertProfile expertProfile) {
        ExpertFile file = expertFileRepository.findById(fileId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!file.getExpertProfile().getId().equals(expertProfile.getId())) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (file.getFileType() != FileTypeEnum.BUSINESS_REGISTRATION) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        return file;
    }
}
