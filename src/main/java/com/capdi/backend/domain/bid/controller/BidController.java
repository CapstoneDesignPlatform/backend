package com.capdi.backend.domain.bid.controller;

import com.capdi.backend.domain.bid.dto.BidCreateRequest;
import com.capdi.backend.domain.bid.dto.BidResponse;
import com.capdi.backend.domain.bid.dto.MyBidResponse;
import com.capdi.backend.domain.bid.service.BidService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import com.capdi.backend.global.response.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

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
