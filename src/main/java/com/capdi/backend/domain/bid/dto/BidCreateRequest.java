package com.capdi.backend.domain.bid.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;

@Getter
public class BidCreateRequest {

    @NotNull(message = "전문가 프로필 ID는 필수입니다.")
    private Long expertProfileId;

    @NotNull(message = "공고 ID는 필수입니다.")
    private Long jobPostId;

    @NotNull(message = "제안 금액은 필수입니다.")
    @Positive(message = "제안 금액은 0보다 커야 합니다.")
    private Long proposedPrice;

    @NotBlank(message = "제안 내용은 필수입니다.")
    private String proposal;
}