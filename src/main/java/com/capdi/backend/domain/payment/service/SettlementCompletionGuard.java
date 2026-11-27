package com.capdi.backend.domain.payment.service;

import com.capdi.backend.domain.announcement.entity.AnnouncementProgressStepEnum;
import com.capdi.backend.domain.announcement.repository.AnnouncementRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Requires recorded service receipt before settlement or tax-document work can begin. */
@Component
@RequiredArgsConstructor
public class SettlementCompletionGuard {
    private final AnnouncementRepository announcements;

    public void requireCompleted(Long announcementId) {
        var announcement = announcements.findById(announcementId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
        if (announcement.getProgressStep() != AnnouncementProgressStepEnum.STEP_7_COMPLETED) {
            throw new CustomException(ErrorCode.PAYMENT_EXECUTION_CONFLICT);
        }
    }
}
