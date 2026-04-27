package com.capdi.backend.domain.expert.controller;

import com.capdi.backend.domain.expert.dto.ExpertFileDownloadUrlResponse;
import com.capdi.backend.domain.expert.dto.ExpertFileUploadResponse;
import com.capdi.backend.domain.expert.service.ExpertFileService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/files")
public class ExpertFileController {

    private final ExpertFileService expertFileService;

    @PostMapping
    public ResponseEntity<ApiResponse<ExpertFileUploadResponse>> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam("purpose") String purpose
    ) {
        return ResponseEntity.ok(ApiResponse.ok(expertFileService.uploadFile(file, purpose)));
    }

    @GetMapping("/{fileId}/download-url")
    public ResponseEntity<ApiResponse<ExpertFileDownloadUrlResponse>> getDownloadUrl(
            @PathVariable Long fileId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(expertFileService.getDownloadUrl(fileId)));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable Long fileId
    ) {
        Resource resource = expertFileService.downloadFile(fileId);

        String encodedFileName = URLEncoder.encode(resource.getFilename(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .body(resource);
    }
}