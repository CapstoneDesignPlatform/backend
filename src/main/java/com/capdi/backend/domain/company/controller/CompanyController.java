package com.capdi.backend.domain.company.controller;

import com.capdi.backend.domain.company.dto.CompanyCreateRequest;
import com.capdi.backend.domain.company.dto.CompanyResponse;
import com.capdi.backend.domain.company.service.CompanyService;
import com.capdi.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyService companyService;

    /**
     * 회사 생성 API
     * POST /companies
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(@RequestBody CompanyCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("회사가 등록되었습니다.", companyService.createCompany(request)));
    }

    /**
     * 회사 목록 조회 API
     * GET /companies
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getCompanies() {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCompanies()));
    }

    /**
     * 회사 단건 조회 API
     * GET /companies/{companyId}
     */
    @GetMapping("/{companyId}")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(ApiResponse.ok(companyService.getCompany(companyId)));
    }
}