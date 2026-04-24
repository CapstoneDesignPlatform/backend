package com.capdi.backend.domain.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class CompanyCreateRequest {

    @NotBlank(message = "회사명은 필수입니다.")
    @Size(max = 100, message = "회사명은 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "사업자등록번호는 필수입니다.")
    @Pattern(regexp = "\\d{10}", message = "사업자등록번호는 10자리 숫자여야 합니다.")
    private String businessNumber;

    @NotBlank(message = "담당자명은 필수입니다.")
    @Size(max = 50, message = "담당자명은 50자 이하여야 합니다.")
    private String contactName;

    @NotBlank(message = "담당자 연락처는 필수입니다.")
    @Size(max = 20, message = "연락처는 20자 이하여야 합니다.")
    private String contactPhone;

    private String address;
}