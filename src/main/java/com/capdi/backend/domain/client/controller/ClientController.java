package com.capdi.backend.domain.client.controller;

import com.capdi.backend.domain.client.dto.ClientInfoRequest;
import com.capdi.backend.domain.client.dto.ClientInfoResponse;
import com.capdi.backend.domain.client.service.ClientService;
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
@RequestMapping("/client-info")
@Tag(name = "ClientInfo", description = "기업 정보 API")
public class ClientController {

    private final ClientService clientService;

    @PostMapping("/guest")
    @Operation(summary = "비회원 기업 정보 저장", description = "비회원이 기업 정보를 저장합니다.")
    public ResponseEntity<ApiResponse<ClientInfoResponse>> saveGuestClientInfo(
            @RequestBody @Valid ClientInfoRequest request) {
        ClientInfoResponse response = clientService.saveGuestClientInfo(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("기업 정보가 저장되었습니다.", response));
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "회원 기업 정보 저장", description = "CLIENT 회원이 기업 정보를 최초 저장합니다.")
    public ResponseEntity<ApiResponse<ClientInfoResponse>> saveClientInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ClientInfoRequest request) {
        ClientInfoResponse response = clientService.saveClientInfo(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("기업 정보가 저장되었습니다.", response));
    }

    @PutMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "회원 기업 정보 수정", description = "CLIENT 회원이 기업 정보를 수정합니다.")
    public ResponseEntity<ApiResponse<ClientInfoResponse>> updateClientInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ClientInfoRequest request) {
        ClientInfoResponse response = clientService.updateClientInfo(userDetails.getUserId(), request);
        return ResponseEntity.ok(ApiResponse.ok("기업 정보가 수정되었습니다.", response));
    }

    @GetMapping
    @PreAuthorize("hasRole('CLIENT')")
    @Operation(summary = "회원 기업 정보 조회", description = "CLIENT 회원의 기업 정보를 조회합니다.")
    public ResponseEntity<ApiResponse<ClientInfoResponse>> getClientInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ClientInfoResponse response = clientService.getClientInfo(userDetails.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
