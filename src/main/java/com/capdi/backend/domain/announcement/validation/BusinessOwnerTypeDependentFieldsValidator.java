package com.capdi.backend.domain.announcement.validation;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.entity.BusinessOwnerTypeEnum;
import com.capdi.backend.domain.announcement.entity.CurrentIndustryStatusEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class BusinessOwnerTypeDependentFieldsValidator
        implements ConstraintValidator<ValidBusinessOwnerTypeDependentFields, AnnouncementCreateRequest> {

    @Override
    public boolean isValid(AnnouncementCreateRequest request, ConstraintValidatorContext context) {
        if (request == null || request.getBusinessOwnerType() == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();

        if (request.getBusinessOwnerType() == BusinessOwnerTypeEnum.STARTUP) {
            return validateStartup(request, context);
        } else {
            return validateNonStartup(request, context);
        }
    }

    private boolean validateStartup(AnnouncementCreateRequest req, ConstraintValidatorContext ctx) {
        boolean valid = true;

        if (req.getCapitalScale() != null) {
            ctx.buildConstraintViolationWithTemplate("창업예정 시 자본규모는 입력할 수 없습니다.")
                    .addPropertyNode("capitalScale")
                    .addConstraintViolation();
            valid = false;
        }
        if (req.getCurrentIndustryStatus() != CurrentIndustryStatusEnum.NONE) {
            ctx.buildConstraintViolationWithTemplate("창업예정 시 현재 업종은 NONE이어야 합니다.")
                    .addPropertyNode("currentIndustryStatus")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }

    private boolean validateNonStartup(AnnouncementCreateRequest req, ConstraintValidatorContext ctx) {
        if (req.getCapitalScale() == null) {
            ctx.buildConstraintViolationWithTemplate("자본규모는 필수입니다.")
                    .addPropertyNode("capitalScale")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }
}
