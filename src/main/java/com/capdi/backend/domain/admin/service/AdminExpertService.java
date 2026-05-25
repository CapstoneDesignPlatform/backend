package com.capdi.backend.domain.admin.service;

import com.capdi.backend.domain.admin.dto.AdminExpertBusinessInfoResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertCertificateResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertDetailResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertSummaryResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertVerificationStatusRequest;
import com.capdi.backend.domain.admin.entity.AdminActionTypeEnum;
import com.capdi.backend.domain.admin.entity.AdminLog;
import com.capdi.backend.domain.admin.entity.AdminTargetTypeEnum;
import com.capdi.backend.domain.admin.repository.AdminLogRepository;
import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertCertificate;
import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import com.capdi.backend.domain.expert.repository.BusinessRegistrationInfoRepository;
import com.capdi.backend.domain.expert.repository.ExpertCertificateRepository;
import com.capdi.backend.domain.expert.repository.ExpertProfileRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 관리자용 전문가 관리 서비스
 * 전문가 조회 및 승인 상태 변경에 대한 로직을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminExpertService {

    private final ExpertProfileRepository expertProfileRepository;
    private final ExpertCertificateRepository expertCertificateRepository;
    private final BusinessRegistrationInfoRepository businessRegistrationInfoRepository;
    private final AdminLogRepository adminLogRepository;

    /**
     * 전문가 목록 조회
     * expert_profiles 테이블 전체를 페이지네이션으로 조회한다.
     */
    public Page<AdminExpertSummaryResponse> getExperts(Pageable pageable) {
        return expertProfileRepository.findAll(pageable)
                .map(AdminExpertSummaryResponse::from);
    }

    /**
     * 전문가 상세 조회
     * users.id를 기준으로 ExpertProfile을 찾는다.
     */
    public AdminExpertDetailResponse getExpert(Long userId) {
        ExpertProfile expertProfile = getExpertProfileByUserId(userId);
        return AdminExpertDetailResponse.from(expertProfile);
    }

    /**
     * 전문가 자격증 목록 조회
     * 먼저 userId로 ExpertProfile을 찾고,
     * 해당 ExpertProfile에 연결된 자격증 목록을 조회한다.
     */
    public List<AdminExpertCertificateResponse> getCertificates(Long userId) {
        ExpertProfile expertProfile = getExpertProfileByUserId(userId);
        List<ExpertCertificate> certificates =
                expertCertificateRepository.findByExpertProfileId(expertProfile.getId());

        return certificates.stream()
                .map(AdminExpertCertificateResponse::from)
                .toList();
    }

    /**
     * 전문가 사업자등록 정보 조회
     * 사업자등록 정보는 전문가 프로필 기준으로 조회한다.
     */
    public AdminExpertBusinessInfoResponse getBusinessRegistrationInfo(Long userId) {
        ExpertProfile expertProfile = getExpertProfileByUserId(userId);
        BusinessRegistrationInfo businessInfo =
                businessRegistrationInfoRepository.findByExpertProfileId(expertProfile.getId())
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new CustomException(ErrorCode.BUSINESS_REGISTRATION_INFO_NOT_FOUND));

        return AdminExpertBusinessInfoResponse.from(businessInfo);
    }

    /**
     * 전문가 승인/반려 상태 변경
     * 쓰기 작업이므로 @Transactional을 별도로 선언한다.
     * 현재 반려 사유는 expert profile에 저장하고 상태 변경 이력은 admin log에 기록한다.
     */
    @Transactional
    public AdminExpertDetailResponse updateVerificationStatus(
            Long userId,
            AdminExpertVerificationStatusRequest request
    ) {
        ExpertProfile expertProfile = getExpertProfileByUserId(userId);
        VerificationStatusEnum beforeStatus = expertProfile.getVerificationStatus();

        expertProfile.updateVerificationStatus(request.getVerificationStatus(), request.getRejectReason());
        saveVerificationStatusChangeLog(expertProfile, beforeStatus, request);

        return AdminExpertDetailResponse.from(expertProfile);
    }

    /**
     * 공통 조회 메서드
     * users.id 기준으로 전문가 프로필을 조회한다.
     * 조회 결과가 없으면 CustomException을 발생시킨다.
     */
    private ExpertProfile getExpertProfileByUserId(Long userId) {
        return expertProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.EXPERT_PROFILE_NOT_FOUND));
    }

    private void saveVerificationStatusChangeLog(
            ExpertProfile expertProfile,
            VerificationStatusEnum beforeStatus,
            AdminExpertVerificationStatusRequest request
    ) {
        String description = buildVerificationStatusChangeDescription(beforeStatus, request);

        adminLogRepository.save(AdminLog.builder()
                .adminId(null)
                .actionType(AdminActionTypeEnum.EXPERT_VERIFICATION_STATUS_UPDATE)
                .targetType(AdminTargetTypeEnum.EXPERT_PROFILE)
                .targetId(expertProfile.getId())
                .description(description)
                .build());
    }

    private String buildVerificationStatusChangeDescription(
            VerificationStatusEnum beforeStatus,
            AdminExpertVerificationStatusRequest request
    ) {
        String description = "verificationStatus: " + beforeStatus + " -> " + request.getVerificationStatus();

        if (request.getRejectReason() != null && !request.getRejectReason().isBlank()) {
            return description + ", rejectReason: " + request.getRejectReason();
        }

        return description;
    }
}
