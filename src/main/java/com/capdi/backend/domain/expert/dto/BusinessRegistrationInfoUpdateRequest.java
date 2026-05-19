package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessRegistrationInfoUpdateRequest {

    @NotNull(message = "파일 ID는 필수입니다.")
    @JsonProperty("file_id")
    private Long fileId;

    @NotBlank(message = "사업자 번호는 필수입니다.")
    @Size(max = 30, message = "사업자 번호는 30자 이하로 입력해주세요.")
    @JsonProperty("business_number")
    private String businessNumber;

    @NotBlank(message = "대표자명은 필수입니다.")
    @Size(max = 100, message = "대표자명은 100자 이하로 입력해주세요.")
    @JsonProperty("representative_name")
    private String representativeName;

    @NotBlank(message = "업체명은 필수입니다.")
    @Size(max = 100, message = "업체명은 100자 이하로 입력해주세요.")
    @JsonProperty("company_name")
    private String companyName;
}
