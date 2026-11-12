package com.capdi.backend.domain.expert.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpertSearchRequest {

    @NotNull
    private ExpertSearchCategory category = ExpertSearchCategory.ALL;

    @Size(max = 100, message = "검색어는 100자 이하로 입력해주세요.")
    private String keyword;

    @NotNull
    @Min(value = 1, message = "페이지는 1 이상이어야 합니다.")
    // JPA pagination uses an int offset: (page - 1) * 20 must not overflow.
    @Max(value = 107374183, message = "조회 가능한 페이지 범위를 초과했습니다.")
    private Integer page = 1;
}
