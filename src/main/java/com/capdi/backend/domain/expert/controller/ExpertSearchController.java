package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertSearchDetailResponse;
import com.capdi.backend.domain.expert.dto.ExpertSearchItemResponse;
import com.capdi.backend.domain.expert.dto.ExpertSearchRequest;
import com.capdi.backend.domain.expert.service.ExpertSearchService;
import com.capdi.backend.global.response.ApiResponse;
import com.capdi.backend.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/experts")
public class ExpertSearchController {

    private final ExpertSearchService expertSearchService;

    @GetMapping
    public ApiResponse<PageResponse<ExpertSearchItemResponse>> search(
            @Valid @ModelAttribute ExpertSearchRequest request
    ) {
        return ApiResponse.ok(expertSearchService.search(request));
    }

    @GetMapping("/{expertUserId}")
    public ApiResponse<ExpertSearchDetailResponse> getDetail(@PathVariable Long expertUserId) {
        return ApiResponse.ok(expertSearchService.getDetail(expertUserId));
    }
}
