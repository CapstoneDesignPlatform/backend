package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertProfile;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpertVerificationStatusResponseTest {

    @Test
    void mapsNotSubmittedStatusToNotApplied() {
        ExpertProfile profile = expertProfile(VerificationStatusEnum.NOT_SUBMITTED);

        ExpertVerificationStatusResponse.VerificationRequestDto response =
                ExpertVerificationStatusResponse.from(profile, List.of(), null).getVerificationRequest();

        assertThat(response.getVerificationStatus()).isEqualTo("NOT_APPLIED");
        assertThat(response.getRejectedReason()).isNull();
    }

    @Test
    void returnsRejectedStatusAndReason() {
        ExpertProfile profile = expertProfile(VerificationStatusEnum.PENDING);

        profile.updateVerificationStatus(VerificationStatusEnum.REJECTED, "사업자등록증을 확인할 수 없습니다.");

        ExpertVerificationStatusResponse.VerificationRequestDto response =
                ExpertVerificationStatusResponse.from(profile, List.of(), null).getVerificationRequest();

        assertThat(response.getVerificationStatus()).isEqualTo("REJECTED");
        assertThat(response.getStatus()).isFalse();
        assertThat(response.getRejectedReason()).isEqualTo("사업자등록증을 확인할 수 없습니다.");
        assertThat(response.getReviewedAt()).isNotNull();
    }

    @Test
    void clearsRejectedReasonWhenRejectedExpertIsApproved() {
        ExpertProfile profile = expertProfile(VerificationStatusEnum.PENDING);
        profile.updateVerificationStatus(VerificationStatusEnum.REJECTED, "서류를 다시 제출해주세요.");

        profile.updateVerificationStatus(VerificationStatusEnum.APPROVED, null);

        ExpertVerificationStatusResponse.VerificationRequestDto response =
                ExpertVerificationStatusResponse.from(profile, List.of(), null).getVerificationRequest();

        assertThat(response.getVerificationStatus()).isEqualTo("APPROVED");
        assertThat(response.getStatus()).isTrue();
        assertThat(response.getRejectedReason()).isNull();
        assertThat(response.getReviewedAt()).isNotNull();
    }

    @Test
    void clearsRejectedReasonWhenRejectedExpertReapplies() {
        ExpertProfile profile = expertProfile(VerificationStatusEnum.PENDING);
        profile.updateVerificationStatus(VerificationStatusEnum.REJECTED, "서류를 다시 제출해주세요.");

        profile.applyVerification("세무사");

        ExpertVerificationStatusResponse.VerificationRequestDto response =
                ExpertVerificationStatusResponse.from(profile, List.of(), null).getVerificationRequest();

        assertThat(response.getVerificationStatus()).isEqualTo("PENDING");
        assertThat(response.getRejectedReason()).isNull();
        assertThat(response.getReviewedAt()).isNull();
    }

    private ExpertProfile expertProfile(VerificationStatusEnum status) {
        return ExpertProfile.builder()
                .specialty("")
                .businessName("")
                .experienceYears(0)
                .isVerified(status == VerificationStatusEnum.APPROVED)
                .verificationStatus(status)
                .selectedCount(0)
                .build();
    }
}
