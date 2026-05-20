package com.capdi.backend.domain.announcement.validation;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.entity.AnnouncementPurposeEnum;
import com.capdi.backend.domain.announcement.entity.CurrentIndustryStatusEnum;
import com.capdi.backend.domain.announcement.entity.DiagnosisReasonEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class PurposeDependentFieldsValidator
        implements ConstraintValidator<ValidPurposeDependentFields, AnnouncementCreateRequest> {

    @Override
    public boolean isValid(AnnouncementCreateRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getPurpose() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        return switch (request.getPurpose()) {
            case REQUIRED_LICENSE -> validateRequiredLicense(request, context);
            case STATUS_INVESTIGATION -> validateStatusInvestigation(request, context);
            case ETC -> validateEtc(request, context);
        };
    }

    private boolean validateRequiredLicense(AnnouncementCreateRequest req, ConstraintValidatorContext ctx) {
        boolean valid = true;

        if (req.getCategory() == null) {
            addViolation(ctx, "category", "필요 면허 의뢰 시 신규/추가 구분은 필수입니다.");
            valid = false;
        }
        if (!StringUtils.hasText(req.getRequiredLicense())) {
            addViolation(ctx, "requiredLicense", "필요 면허 의뢰 시 필요 면허는 필수입니다.");
            valid = false;
        }
        if (req.getCurrentIndustryStatus() == null) {
            addViolation(ctx, "currentIndustryStatus", "필요 면허 의뢰 시 현재 업종은 필수입니다.");
            valid = false;
        }
        if (requiresCurrentIndustryDetail(req.getCurrentIndustryStatus())
                && !StringUtils.hasText(req.getCurrentIndustryDetail())) {
            addViolation(ctx, "currentIndustryDetail", "건설업 관련/비 건설업 관련 선택 시 상세 내용은 필수입니다.");
            valid = false;
        }
        if (req.getCurrentIndustryStatus() == CurrentIndustryStatusEnum.NONE
                && StringUtils.hasText(req.getCurrentIndustryDetail())) {
            addViolation(ctx, "currentIndustryDetail", "현재 업종이 없음인 경우 업종 상세는 입력할 수 없습니다.");
            valid = false;
        }

        if (StringUtils.hasText(req.getHeldLicense())) {
            addViolation(ctx, "heldLicense", "필요 면허 의뢰 시 보유 면허는 입력할 수 없습니다.");
            valid = false;
        }
        if (req.getDiagnosisReason() != null) {
            addViolation(ctx, "diagnosisReason", "필요 면허 의뢰 시 진단 사유는 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getDiagnosisReasonDetail())) {
            addViolation(ctx, "diagnosisReasonDetail", "필요 면허 의뢰 시 진단 사유 상세는 입력할 수 없습니다.");
            valid = false;
        }

        return valid;
    }

    private boolean validateStatusInvestigation(AnnouncementCreateRequest req, ConstraintValidatorContext ctx) {
        boolean valid = true;

        if (!StringUtils.hasText(req.getHeldLicense())) {
            addViolation(ctx, "heldLicense", "실태 조사 의뢰 시 보유 면허는 필수입니다.");
            valid = false;
        }

        if (req.getCategory() != null) {
            addViolation(ctx, "category", "실태 조사 의뢰 시 신규/추가 구분은 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getRequiredLicense())) {
            addViolation(ctx, "requiredLicense", "실태 조사 의뢰 시 필요 면허는 입력할 수 없습니다.");
            valid = false;
        }
        if (req.getCurrentIndustryStatus() != null
                && req.getCurrentIndustryStatus() != CurrentIndustryStatusEnum.NONE) {
            addViolation(ctx, "currentIndustryStatus", "실태 조사 의뢰 시 현재 업종은 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getCurrentIndustryDetail())) {
            addViolation(ctx, "currentIndustryDetail", "실태 조사 의뢰 시 현재 업종 상세는 입력할 수 없습니다.");
            valid = false;
        }
        if (req.getDiagnosisReason() != null) {
            addViolation(ctx, "diagnosisReason", "실태 조사 의뢰 시 진단 사유는 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getDiagnosisReasonDetail())) {
            addViolation(ctx, "diagnosisReasonDetail", "실태 조사 의뢰 시 진단 사유 상세는 입력할 수 없습니다.");
            valid = false;
        }

        return valid;
    }

    private boolean validateEtc(AnnouncementCreateRequest req, ConstraintValidatorContext ctx) {
        boolean valid = true;

        if (req.getDiagnosisReason() == null) {
            addViolation(ctx, "diagnosisReason", "기타 의뢰 시 진단 사유는 필수입니다.");
            valid = false;
        }
        if (req.getDiagnosisReason() == DiagnosisReasonEnum.ETC
                && !StringUtils.hasText(req.getDiagnosisReasonDetail())) {
            addViolation(ctx, "diagnosisReasonDetail", "진단 사유가 기타인 경우 상세 내용은 필수입니다.");
            valid = false;
        }

        if (req.getCategory() != null) {
            addViolation(ctx, "category", "기타 의뢰 시 신규/추가 구분은 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getRequiredLicense())) {
            addViolation(ctx, "requiredLicense", "기타 의뢰 시 필요 면허는 입력할 수 없습니다.");
            valid = false;
        }
        if (req.getCurrentIndustryStatus() != null
                && req.getCurrentIndustryStatus() != CurrentIndustryStatusEnum.NONE) {
            addViolation(ctx, "currentIndustryStatus", "기타 의뢰 시 현재 업종은 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getCurrentIndustryDetail())) {
            addViolation(ctx, "currentIndustryDetail", "기타 의뢰 시 현재 업종 상세는 입력할 수 없습니다.");
            valid = false;
        }
        if (StringUtils.hasText(req.getHeldLicense())) {
            addViolation(ctx, "heldLicense", "기타 의뢰 시 보유 면허는 입력할 수 없습니다.");
            valid = false;
        }

        return valid;
    }

    private void addViolation(ConstraintValidatorContext ctx, String field, String message) {
        ctx.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }

    private boolean requiresCurrentIndustryDetail(CurrentIndustryStatusEnum status) {
        return status == CurrentIndustryStatusEnum.CONSTRUCTION_RELATED
                || status == CurrentIndustryStatusEnum.NON_CONSTRUCTION_RELATED;
    }
}
