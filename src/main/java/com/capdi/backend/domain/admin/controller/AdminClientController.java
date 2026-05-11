package com.capdi.backend.domain.admin.controller;

import com.capdi.backend.domain.admin.dto.*;
import com.capdi.backend.domain.admin.service.AdminClientService;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminClientController {

    private final AdminClientService adminClientService;

    // 의뢰인 목록 조회
    @GetMapping("/clients")
    public ResponseEntity<ApiResponse<Page<AdminClientSummaryResponse>>> getClientList(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok("의뢰인 목록 조회 성공", adminClientService.getClientList(pageable)));
    }

    // 의뢰인 상세 조회
    @GetMapping("/clients/{userId}")
    public ResponseEntity<ApiResponse<AdminClientResponse>> getClientDetail(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok("의뢰인 상세 조회 성공", adminClientService.getClientDetail(userId)));
    }

    // 특정 의뢰인의 공고 목록 조회
    @GetMapping("/clients/{userId}/announcements")
    public ResponseEntity<ApiResponse<List<AdminAnnouncementSummaryResponse>>> getAnnouncementsByClient(
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.ok("의뢰인 공고 목록 조회 성공", adminClientService.getAnnouncementsByClient(userId)));
    }

    // 전체 공고 목록 조회
    @GetMapping("/announcements")
    public ResponseEntity<ApiResponse<Page<AdminAnnouncementSummaryResponse>>> getAnnouncementList(
            @RequestParam(required = false) AnnouncementStatusEnum status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.ok("공고 목록 조회 성공", adminClientService.getAnnouncementList(status, pageable)));
    }

    // 공고 상세 조회 (입찰 리스트 포함)
    @GetMapping("/announcements/{announcementCode}")
    public ResponseEntity<ApiResponse<AdminAnnouncementDetailResponse>> getAnnouncementDetail(
            @PathVariable String announcementCode
    ) {
        return ResponseEntity.ok(ApiResponse.ok("공고 상세 조회 성공", adminClientService.getAnnouncementDetail(announcementCode)));
    }

    // 공고 상태 변경
    @PatchMapping("/announcements/{announcementCode}/status")
    public ResponseEntity<ApiResponse<Void>> updateAnnouncementStatus(
            @PathVariable String announcementCode,
            @RequestBody @Valid AdminAnnouncementStatusRequest request
    ) {
        adminClientService.updateAnnouncementStatus(announcementCode, request.getStatus());
        return ResponseEntity.ok(ApiResponse.ok("공고 상태 변경 성공", null));
    }
}
