package com.capdi.backend.domain.admin.controller;

import com.capdi.backend.domain.admin.service.AdminExcelService;
import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import com.capdi.backend.domain.expert.entity.VerificationStatusEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/admin/excel")
@RequiredArgsConstructor
@Tag(name = "Admin Excel", description = "관리자 Excel 다운로드 API")
public class AdminExcelController {

    // Excel 다운로드 응답에 사용하는 MIME 타입
    private static final MediaType EXCEL_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final AdminExcelService adminExcelService;

    // 의뢰현황 목록 Excel 다운로드
    @GetMapping("/announcements")
    @Operation(
            summary = "의뢰현황 목록 Excel 다운로드",
            description = "관리자 의뢰현황 목록 데이터를 Excel 파일로 다운로드합니다."
    )
    public ResponseEntity<byte[]> downloadAnnouncements(
            @Parameter(description = "의뢰 상태 필터")
            @RequestParam(required = false) AnnouncementStatusEnum status,
            @Parameter(description = "의뢰코드, 기업명, 의뢰인 검색어")
            @RequestParam(required = false) String keyword
    ) {
        byte[] file = adminExcelService.createAnnouncementExcel(status, keyword);
        return excelResponse("admin-announcements.xlsx", file);
    }

    // 의뢰 상세 Excel 다운로드
    @GetMapping("/announcements/{announcementCode}")
    @Operation(
            summary = "의뢰 상세 Excel 다운로드",
            description = "의뢰 상세 페이지의 기본 정보, 기업/의뢰인 정보, 입찰 목록을 Excel 파일로 다운로드합니다."
    )
    public ResponseEntity<byte[]> downloadAnnouncementDetail(
            @Parameter(description = "의뢰코드")
            @PathVariable String announcementCode
    ) {
        byte[] file = adminExcelService.createAnnouncementDetailExcel(announcementCode);
        return excelResponse("admin-announcement-" + announcementCode + ".xlsx", file);
    }

    // 전문가 관리 목록 Excel 다운로드
    @GetMapping("/experts")
    @Operation(
            summary = "전문가 관리 목록 Excel 다운로드",
            description = "관리자 전문가 관리 목록 데이터를 Excel 파일로 다운로드합니다."
    )
    public ResponseEntity<byte[]> downloadExperts(
            @Parameter(description = "전문가 승인 상태 필터")
            @RequestParam(required = false) VerificationStatusEnum status,
            @Parameter(description = "이름, 이메일, 연락처, 전문분야 검색어")
            @RequestParam(required = false) String keyword
    ) {
        byte[] file = adminExcelService.createExpertExcel(status, keyword);
        return excelResponse("admin-experts.xlsx", file);
    }

    // 전문가 상세 Excel 다운로드
    @GetMapping("/experts/{userId}")
    @Operation(
            summary = "전문가 상세 Excel 다운로드",
            description = "전문가 상세 페이지의 기본 정보, 사업자등록 정보, 자격증, 첨부 서류 목록을 Excel 파일로 다운로드합니다."
    )
    public ResponseEntity<byte[]> downloadExpertDetail(
            @Parameter(description = "전문가 사용자 ID")
            @PathVariable Long userId
    ) {
        byte[] file = adminExcelService.createExpertDetailExcel(userId);
        return excelResponse("admin-expert-" + userId + ".xlsx", file);
    }

    // 의뢰인 관리 목록 Excel 다운로드
    @GetMapping("/clients")
    @Operation(
            summary = "의뢰인 관리 목록 Excel 다운로드",
            description = "관리자 의뢰인 관리 목록 데이터를 Excel 파일로 다운로드합니다."
    )
    public ResponseEntity<byte[]> downloadClients(
            @Parameter(description = "이름, 이메일, 연락처, 업체명, 사업자번호 검색어")
            @RequestParam(required = false) String keyword
    ) {
        byte[] file = adminExcelService.createClientExcel(keyword);
        return excelResponse("admin-clients.xlsx", file);
    }

    // 브라우저가 파일로 인식하도록 다운로드 헤더를 설정한다.
    private ResponseEntity<byte[]> excelResponse(String filename, byte[] file) {
        ContentDisposition contentDisposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(EXCEL_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(file);
    }
}
