package com.capdi.backend.domain.company.service;

import com.capdi.backend.domain.company.dto.CompanyCreateRequest;
import com.capdi.backend.domain.company.dto.CompanyResponse;
import com.capdi.backend.domain.company.entity.Company;
import com.capdi.backend.domain.company.repository.CompanyRepository;
import com.capdi.backend.global.exception.CustomException;
import com.capdi.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyService {

    private final CompanyRepository companyRepository;

    @Transactional
    public CompanyResponse createCompany(CompanyCreateRequest request) {
        Company company = Company.builder()
                .name(request.getName())
                .businessNumber(request.getBusinessNumber())
                .contactName(request.getContactName())
                .contactPhone(request.getContactPhone())
                .address(request.getAddress())
                .build();

        return CompanyResponse.from(companyRepository.save(company));
    }

    public List<CompanyResponse> getCompanies() {
        return companyRepository.findAll()
                .stream()
                .map(CompanyResponse::from)
                .toList();
    }

    public CompanyResponse getCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .map(CompanyResponse::from)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));
    }
}