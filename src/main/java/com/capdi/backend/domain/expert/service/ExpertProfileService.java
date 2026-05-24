package com.capdi.backend.domain.expert.service;

import com.capdi.backend.domain.expert.dto.ExpertProfileResponse;
import com.capdi.backend.domain.expert.dto.ExpertProfileUpdateRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationRequest;
import com.capdi.backend.domain.expert.dto.ExpertVerificationStatusResponse;
import com.capdi.backend.domain.expert.dto.VerificationRequirementErrorResponse;
import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.CertificateTypeCodeEnum;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.FileTypeEnum;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.capdi.backend.domain.expert.exception.VerificationRequirementException;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpertProfileService {

    private static final String REQUIREMENT_ERROR_CODE = "VERIFICATION_REQUIREMENT_NOT_MET";
    private static final String CERTIFICATE_REQUIRED = "EXPERT_CERTIFICATE_REQUIRED";
    private static final String INSUFFICIENT_CERTIFICATE_REQUIREMENT = "INSUFFICIENT_CERTIFICATE_REQUIREMENT";
    private static final String BUSINESS_REGISTRATION_REQUIRED = "BUSINESS_REGISTRATION_REQUIRED";
    private static final String INVALID_CERTIFICATE_FILE = "INVALID_CERTIFICATE_FILE";
    private static final String INVALID_BUSINESS_REGISTRATION_FILE = "INVALID_BUSINESS_REGISTRATION_FILE";

    private static final Map<String, SpecialtyRequirement> SPECIALTY_REQUIREMENTS = Map.of(
            "세무사", new SpecialtyRequirement(
                    "세무사",
                    CertificateTypeCodeEnum.TAX_ACCOUNTANT,
                    1,
                    "세무사 인증에는 세무사 등록증이 1개 이상 필요합니다."
            ),
            "전문경영진단", new SpecialtyRequirement(
                    "전문경영진단",
                    CertificateTypeCodeEnum.MANAGEMENT_CONSULTANT,
                    2,
                    "전문경영진단 인증에는 경영지도사 등록증 2개 이상이 필요합니다."
            ),
            "경영지도사(재무관리)", new SpecialtyRequirement(
                    "경영지도사(재무관리)",
                    CertificateTypeCodeEnum.MANAGEMENT_CONSULTANT,
                    1,
                    "경영지도사(재무관리) 인증에는 경영지도사 등록증이 필요합니다."
            )
    );

    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertCertificateRepository expertCertificateRepository;
    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;

    public ExpertProfileResponse getMyProfile(Long loginUserId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        return ExpertProfileResponse.from(expertProfile);
    }

    @Transactional
    public ExpertProfileResponse updateMyProfile(Long loginUserId, ExpertProfileUpdateRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);

        expertProfile.getUser().updateBasicInfo(
                request.getName(),
                request.getPhone()
        );

        expertProfile.updateBusinessName(request.getCompanyName());

        return ExpertProfileResponse.from(expertProfile);
    }

    public ExpertVerificationStatusResponse getMyVerificationStatus(Long loginUserId) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        List<ExpertCertificate> certificates =
                expertCertificateRepository.findByExpertProfileId(expertProfile.getId());
        BusinessRegistrationInfo businessRegistrationInfo =
                businessRegistrationInfoRepository.findByExpertProfileId(expertProfile.getId())
                        .stream()
                        .max(Comparator.comparing(BusinessRegistrationInfo::getId))
                        .orElse(null);

        return ExpertVerificationStatusResponse.from(
                expertProfile,
                certificates,
                businessRegistrationInfo
        );
    }

    @Transactional
    public void applyVerification(Long loginUserId, ExpertVerificationRequest request) {
        ExpertProfile expertProfile = getMyExpertProfile(loginUserId);
        SpecialtyRequirement requirement = getSpecialtyRequirement(request.getSpecialty());

        validateVerificationStatus(expertProfile);
        validateVerificationRequirements(expertProfile, requirement);

        expertProfile.applyVerification(requirement.specialty());
    }

    private ExpertProfile getMyExpertProfile(Long loginUserId) {
        return expertProfileRepository.findByUserId(loginUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
    }

    private SpecialtyRequirement getSpecialtyRequirement(String specialty) {
        SpecialtyRequirement requirement = SPECIALTY_REQUIREMENTS.get(specialty.trim());
        if (requirement == null) {
            throw new CustomException(ErrorCode.INVALID_EXPERT_SPECIALTY);
        }

        return requirement;
    }

    private void validateVerificationStatus(ExpertProfile expertProfile) {
        if (expertProfile.getVerificationStatus() == VerificationStatusEnum.PENDING) {
            throw new CustomException(ErrorCode.ALREADY_VERIFICATION_PENDING);
        }

        if (expertProfile.getVerificationStatus() == VerificationStatusEnum.APPROVED) {
            throw new CustomException(ErrorCode.ALREADY_VERIFIED);
        }
    }

    private void validateVerificationRequirements(
            ExpertProfile expertProfile,
            SpecialtyRequirement requirement
    ) {
        List<VerificationRequirementErrorResponse.ErrorItem> errors = new ArrayList<>();
        List<ExpertCertificate> certificates =
                expertCertificateRepository.findByExpertProfileId(expertProfile.getId());
        BusinessRegistrationInfo businessRegistrationInfo =
                businessRegistrationInfoRepository.findByExpertProfileId(expertProfile.getId())
                        .stream()
                        .max(Comparator.comparing(BusinessRegistrationInfo::getId))
                        .orElse(null);

        validateCertificates(expertProfile, requirement, certificates, errors);
        validateBusinessRegistrationInfo(expertProfile, businessRegistrationInfo, errors);

        if (!errors.isEmpty()) {
            throw new VerificationRequirementException(
                    VerificationRequirementErrorResponse.builder()
                            .code(REQUIREMENT_ERROR_CODE)
                            .errors(errors)
                            .build()
            );
        }
    }

    private void validateCertificates(
            ExpertProfile expertProfile,
            SpecialtyRequirement requirement,
            List<ExpertCertificate> certificates,
            List<VerificationRequirementErrorResponse.ErrorItem> errors
    ) {
        if (certificates.isEmpty()) {
            errors.add(error(
                    "certificates",
                    CERTIFICATE_REQUIRED,
                    requirement.errorMessage()
            ));
            return;
        }

        long matchingCertificateCount = certificates.stream()
                .filter(certificate -> certificate.getCertificateTypeCode() == requirement.certificateTypeCode())
                .count();

        if (matchingCertificateCount < requirement.requiredCount()) {
            errors.add(error(
                    "certificates",
                    INSUFFICIENT_CERTIFICATE_REQUIREMENT,
                    requirement.errorMessage()
            ));
        }

        for (int i = 0; i < certificates.size(); i++) {
            ExpertCertificate certificate = certificates.get(i);
            ExpertFile file = certificate.getFile();

            if (file == null
                    || file.getExpertProfile() == null
                    || !file.getExpertProfile().getId().equals(expertProfile.getId())
                    || file.getFileType() != FileTypeEnum.CERTIFICATE) {
                errors.add(error(
                        "certificates[" + i + "].file_id",
                        INVALID_CERTIFICATE_FILE,
                        "자격증 파일이 없거나 현재 전문가의 자격증 파일이 아닙니다."
                ));
            }
        }
    }

    private void validateBusinessRegistrationInfo(
            ExpertProfile expertProfile,
            BusinessRegistrationInfo businessRegistrationInfo,
            List<VerificationRequirementErrorResponse.ErrorItem> errors
    ) {
        if (businessRegistrationInfo == null) {
            errors.add(error(
                    "business_registration_info",
                    BUSINESS_REGISTRATION_REQUIRED,
                    "사업자등록 정보가 필요합니다."
            ));
            return;
        }

        ExpertFile file = businessRegistrationInfo.getFile();
        if (file == null
                || file.getExpertProfile() == null
                || !file.getExpertProfile().getId().equals(expertProfile.getId())
                || file.getFileType() != FileTypeEnum.BUSINESS_REGISTRATION) {
            errors.add(error(
                    "business_registration_info.file_id",
                    INVALID_BUSINESS_REGISTRATION_FILE,
                    "사업자등록 파일이 없거나 현재 전문가의 사업자등록 파일이 아닙니다."
            ));
        }
    }

    private VerificationRequirementErrorResponse.ErrorItem error(
            String field,
            String code,
            String message
    ) {
        return VerificationRequirementErrorResponse.ErrorItem.builder()
                .field(field)
                .code(code)
                .message(message)
                .build();
    }

    private record SpecialtyRequirement(
            String specialty,
            CertificateTypeCodeEnum certificateTypeCode,
            int requiredCount,
            String errorMessage
    ) {
    }
}
