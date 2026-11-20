package com.capdi.backend.domain.bid.controller;

import com.capdi.backend.domain.bid.dto.BidCreateRequest;
import com.capdi.backend.domain.bid.dto.BidResponse;
import com.capdi.backend.domain.bid.dto.BidUpdateRequest;
import com.capdi.backend.domain.bid.dto.ExpertSelectRequest;
import com.capdi.backend.domain.bid.dto.ExpertSelectResponse;
import com.capdi.backend.domain.bid.dto.MyBidResponse;
import com.capdi.backend.domain.bid.service.BidService;
import com.capdi.backend.domain.bid.service.SelectionCancellationService;
import com.capdi.backend.domain.bid.dto.SelectionCancelRequest;
import com.capdi.backend.domain.bid.dto.SelectionCancelResponse;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import com.capdi.backend.global.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;
    private final SelectionCancellationService cancellationService;

    @PatchMapping("/announcements/{announcementCode}/bids/{bidId}/cancel-selection")
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "전문가 선정 취소", description = "결제 시도 전의 선정을 취소하고 해당 회차의 입찰을 복구합니다.")
    public ResponseEntity<ApiResponse<SelectionCancelResponse>> cancelSelection(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String announcementCode, @PathVariable Long bidId,
            @RequestBody @Valid SelectionCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("전문가 선정이 취소되었습니다.",
                cancellationService.cancel(userDetails.getUserId(),announcementCode,bidId,request)));
    }

    @PatchMapping("/announcements/guest/{announcementCode}/bids/{bidId}/cancel-selection")
    @Operation(summary = "비회원 전문가 선정 취소")
    public ResponseEntity<ApiResponse<SelectionCancelResponse>> cancelGuestSelection(
            @PathVariable String announcementCode, @PathVariable Long bidId,
            @RequestBody @Valid SelectionCancelRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("전문가 선정이 취소되었습니다.",
                cancellationService.cancelGuest(announcementCode,bidId,request)));
    }

    @GetMapping("/expert/me/bids")
    public ResponseEntity<ApiResponse<PageResponse<MyBidResponse>>> getMyBids(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "submitted_at_desc") String sort
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                bidService.getMyBids(userDetails.getUserId(), status, page, size, sort)
        ));
    }

    @PatchMapping("/announcements/{announcementCode}/bids/{bidId}/select")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<ApiResponse<ExpertSelectResponse>> selectExpert(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String announcementCode,
            @PathVariable Long bidId,
            @RequestBody @Valid ExpertSelectRequest request
    ) {
        ExpertSelectResponse response = bidService.selectExpert(
                userDetails.getUserId(), announcementCode, bidId, request);
        return ResponseEntity.ok(ApiResponse.ok("전문가가 선택되었습니다.", response));
    }

    @PatchMapping("/announcements/guest/{announcementCode}/bids/{bidId}/select")
    @Operation(summary = "비회원 전문가 최종 선정", description = "마감된 비회원 공고에서 전문가를 선정하고 최종 금액을 확정합니다.")
    public ResponseEntity<ApiResponse<ExpertSelectResponse>> selectGuestExpert(
            @PathVariable String announcementCode,
            @PathVariable Long bidId,
            @RequestBody @Valid ExpertSelectRequest request) {
        ExpertSelectResponse response = bidService.selectGuestExpert(announcementCode, bidId, request);
        return ResponseEntity.ok(ApiResponse.ok("전문가가 선택되었습니다.", response));
    }

    @PatchMapping("/expert/job-posts/{announcementCode}/bids")
    @PreAuthorize("hasRole('EXPERT')")
    @Operation(summary = "본인 입찰 금액 수정", description = "승인된 전문가가 마감 전 공고의 본인 대기 중 입찰 금액을 수정합니다.")
    public ResponseEntity<ApiResponse<BidResponse>> updateBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String announcementCode,
            @RequestBody @Valid BidUpdateRequest request
    ) {
        BidResponse response = bidService.updateBid(userDetails.getUserId(), announcementCode, request);
        return ResponseEntity.ok(ApiResponse.ok("입찰 금액이 수정되었습니다.", response));
    }

    @PostMapping("/expert/job-posts/{announcementCode}/bids")
    public ResponseEntity<ApiResponse<BidResponse>> createBid(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable String announcementCode,
            @RequestBody @Valid BidCreateRequest request
    ) {
        BidResponse response = bidService.createBid(userDetails.getUserId(), announcementCode, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("입찰이 등록되었습니다.", response));
    }
}
