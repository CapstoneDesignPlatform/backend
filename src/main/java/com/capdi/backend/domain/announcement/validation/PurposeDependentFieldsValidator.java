package com.capdi.backend.domain.announcement.validation;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.entity.CurrentIndustryStatusEnum;
import com.capdi.backend.domain.announcement.entity.DiagnosisReasonEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.util.StringUtils;

public class PurposeDependentFieldsValidator
        implements ConstraintValidator<ValidPurposeDependentFields, AnnouncementCreateRequest> {

    @Override
    public boolean isValid(AnnouncementCreateRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        context.disableDefaultConstraintViolation();

        if (requiresCurrentIndustryDetail(request.getCurrentIndustryStatus())
                && !StringUtils.hasText(request.getCurrentIndustryDetail())) {

            context.buildConstraintViolationWithTemplate("건설업 관련/비 건설업 관련 선택 시 상세 내용은 필수입니다.")
                    .addPropertyNode("currentIndustryDetail")
                    .addConstraintViolation();

            valid = false;
        }

        if (request.getDiagnosisReason() == DiagnosisReasonEnum.ETC
                && !StringUtils.hasText(request.getDiagnosisReasonDetail())) {

            context.buildConstraintViolationWithTemplate("진단 사유가 기타인 경우 상세 내용은 필수입니다.")
                    .addPropertyNode("diagnosisReasonDetail")
                    .addConstraintViolation();

            valid = false;
        }

        return valid;
    }

    private boolean requiresCurrentIndustryDetail(CurrentIndustryStatusEnum status) {
        return status == CurrentIndustryStatusEnum.CONSTRUCTION_RELATED
                || status == CurrentIndustryStatusEnum.NON_CONSTRUCTION_RELATED;
    }
}
