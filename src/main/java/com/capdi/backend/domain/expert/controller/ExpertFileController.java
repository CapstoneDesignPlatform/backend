package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertFileDownloadUrlResponse;
import com.capdi.backend.domain.expert.dto.ExpertFileUploadResponse;
import com.capdi.backend.domain.expert.service.ExpertFileService;
import com.capdi.backend.global.jwt.CustomUserDetails;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class ExpertFileController {

    private final ExpertFileService expertFileService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExpertFileUploadResponse>> uploadFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                expertFileService.uploadFile(userDetails.getUserId(), file, purpose)
        ));
    }

    @PostMapping("/{fileId}/ocr")
    public ResponseEntity<ApiResponse<Void>> runOcr(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long fileId
    ) {
        expertFileService.runOcr(userDetails.getUserId(), fileId);

        return ResponseEntity.ok(ApiResponse.ok(
                "OCR 검증이 완료되었습니다.",
                null
        ));
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<ApiResponse<ExpertFileDownloadUrlResponse>> getDownloadUrl(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long fileId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                expertFileService.getDownloadUrl(userDetails.getUserId(), fileId)
        ));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long fileId
    ) {
        Resource resource = expertFileService.downloadFile(userDetails.getUserId(), fileId);

        String encodedFileName = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }
}