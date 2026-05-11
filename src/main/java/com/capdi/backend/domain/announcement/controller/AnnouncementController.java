package com.capdi.backend.domain.announcement.controller;

import com.capdi.backend.domain.announcement.dto.AnnouncementCreateRequest;
import com.capdi.backend.domain.announcement.dto.AnnouncementCreateResponse;
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
}
