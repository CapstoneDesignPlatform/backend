package com.capdi.backend.domain.admin.controller;

import com.capdi.backend.domain.admin.dto.AdminExpertBusinessInfoResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertCertificateResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertDetailResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertSummaryResponse;
import com.capdi.backend.domain.admin.dto.AdminExpertVerificationStatusRequest;
import com.capdi.backend.domain.admin.service.AdminExpertService;
import com.capdi.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자용 전문가 관리 API 컨트롤러
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/experts")
@Tag(name = "Admin Expert", description = "관리자 전문가 관리 API")
public class AdminExpertController {
    private final AdminExpertService adminExpertService;

    /**
     * 전문가 목록 조회
     */
    @GetMapping
    @Operation(summary = "전문가 목록 조회", description = "관리자 페이지에서 전문가 목록을 조회한다.")

    public ResponseEntity<ApiResponse<Page<AdminExpertSummaryResponse>>> getExperts(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok("전문가 목록 조회에 성공했습니다.", adminExpertService.getExperts(pageable))
        );
    }

    /**
     * 전문가 상세 조회
     */
    @GetMapping("/{userId}")
    @Operation(summary = "전문가 상세 조회", description = "특정 전문가의 상세 정보를 조회한다.")

    public ResponseEntity<ApiResponse<AdminExpertDetailResponse>> getExpert(@PathVariable Long userId) {
        return ResponseEntity.ok(
                ApiResponse.ok("전문가 상세 조회에 성공했습니다.", adminExpertService.getExpert(userId))
        );
    }

    /**
     * 전문가 자격증 목록 조회
     */
    @GetMapping("/{userId}/certificates")
    @Operation(summary = "전문가 자격증 조회", description = "전문가의 자격증 목록을 조회한다.")

    public ResponseEntity<ApiResponse<List<AdminExpertCertificateResponse>>> getCertificates(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok("전문가 자격증 조회에 성공했습니다.", adminExpertService.getCertificates(userId))
        );
    }

    /**
     * 전문가 사업자등록 정보 조회
     */
    @GetMapping("/{userId}/business-registration-info")
    @Operation(summary = "전문가 사업자등록 정보 조회", description = "전문가의 사업자등록 정보를 조회한다.")

    public ResponseEntity<ApiResponse<AdminExpertBusinessInfoResponse>> getBusinessRegistrationInfo(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "전문가 사업자등록 정보 조회에 성공했습니다.",
                        adminExpertService.getBusinessRegistrationInfo(userId)
                )
        );
    }

    /**
     * 전문가 승인 상태 변경
     */
    @PatchMapping("/{userId}/verification-status")
    @Operation(summary = "전문가 승인 상태 변경", description = "관리자가 전문가 승인 상태를 변경한다.")

    public ResponseEntity<ApiResponse<AdminExpertDetailResponse>> updateVerificationStatus(
            @PathVariable Long userId,
            @RequestBody @Valid AdminExpertVerificationStatusRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        "전문가 승인 상태 변경에 성공했습니다.",
                        adminExpertService.updateVerificationStatus(userId, request)
                )
        );
    }
}
