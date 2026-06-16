package com.capdi.backend.domain.announcement.controller;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.dto.AnnouncementCreateResponse;
import com.capdi.backend.domain.announcement.dto.AnnouncementDetailResponse;
import com.capdi.backend.domain.announcement.dto.ClientAnnouncementListResponse;
import com.capdi.backend.domain.announcement.service.AnnouncementService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/announcements")
@Tag(name = "Announcement", description = "의뢰 공고 API")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "회원 공고 등록")
    public ResponseEntity<ApiResponse<AnnouncementCreateResponse>> createAnnouncement(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid AnnouncementCreateRequest request) {

        AnnouncementCreateResponse response =
                announcementService.createAnnouncement(request, userDetails.getUserId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("의뢰가 성공적으로 등록되었습니다.", response));
    }

    @PostMapping("/guest")
    @Operation(summary = "비회원 공고 등록")
    public ResponseEntity<ApiResponse<AnnouncementCreateResponse>> createGuestAnnouncement(
            @RequestBody @Valid AnnouncementCreateRequest request) {

        AnnouncementCreateResponse response =
                announcementService.createGuestAnnouncement(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("의뢰가 성공적으로 등록되었습니다.", response));
    }

    @GetMapping("/{announcementCode}")
    @Operation(summary = "의뢰 코드로 공고 조회", description = "회원/비회원 모두 접근 가능합니다.")
    public ResponseEntity<ApiResponse<AnnouncementDetailResponse>> getAnnouncementByCode(
            @PathVariable String announcementCode) {

        AnnouncementDetailResponse response =
                announcementService.getAnnouncementByCode(announcementCode);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{announcementCode}/close")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "공고 마감", description = "기업정보가 완성된 경우에만 마감 가능합니다.")
    public ResponseEntity<ApiResponse<Void>> closeAnnouncement(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String announcementCode) {

        announcementService.closeAnnouncement(userDetails.getUserId(), announcementCode);
        return ResponseEntity.ok(ApiResponse.ok("공고가 마감되었습니다.", null));
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "마이페이지 의뢰 목록 조회", description = "현재 진행중인 의뢰 1건과 과거 의뢰 내역을 반환합니다.")
    public ResponseEntity<ApiResponse<ClientAnnouncementListResponse>> getClientAnnouncements(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        ClientAnnouncementListResponse response =
                announcementService.getClientAnnouncements(userDetails.getUserId());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
