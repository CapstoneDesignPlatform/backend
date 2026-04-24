package com.capdi.backend.domain.bid.controller;

import com.capdi.backend.domain.bid.dto.BidCreateRequest;
import com.capdi.backend.domain.bid.dto.BidResponse;
import com.capdi.backend.domain.bid.service.BidService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class BidController {

    private final BidService bidService;

    /**
     * 입찰 등록 API
     * POST /bids
     */
    @PostMapping("/bids")
    public ResponseEntity<ApiResponse<BidResponse>> createBid(@RequestBody BidCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("입찰이 등록되었습니다.", bidService.createBid(request)));
    }

    /**
     * 특정 전문가의 내 입찰 목록 조회 API
     * GET /experts/{expertProfileId}/bids
     */
    @GetMapping("/experts/{expertProfileId}/bids")
    public ResponseEntity<ApiResponse<List<BidResponse>>> getBidsByExpertProfile(@PathVariable Long expertProfileId) {
        return ResponseEntity.ok(ApiResponse.ok(bidService.getBidsByExpertProfile(expertProfileId)));
    }
}