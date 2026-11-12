package com.capdi.backend.domain.client.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ClientInfoRequest {

    @NotBlank(message = "대표자명은 필수입니다.")
    @Size(max = 50, message = "대표자명은 50자까지 입력할 수 있습니다.")
    private String representativeName;

    @NotBlank(message = "연락처는 필수입니다.")
    @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "연락처 형식이 올바르지 않습니다.")
    private String contact;

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 100, message = "이메일은 100자까지 입력할 수 있습니다.")
    private String email;

    @Size(max = 100, message = "회사명은 100자까지 입력할 수 있습니다.")
    private String companyName;

    @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{5}$", message = "사업자등록번호 형식이 올바르지 않습니다.")
    private String businessNumber;

    @Size(max = 255, message = "주소는 255자까지 입력할 수 있습니다.")
    private String address;

    @Size(max = 255, message = "홈페이지 주소는 255자까지 입력할 수 있습니다.")
    private String website;

    private String description;
}
