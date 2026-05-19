package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ExpertCertificateUpdateRequest {

    @NotBlank(message = "자격증 이름은 필수입니다.")
    @JsonProperty("certificate_name")
    private String certificateName;

    @NotBlank(message = "자격증 번호는 필수입니다.")
    @JsonProperty("certificate_number")
    private String certificateNumber;

    @NotNull(message = "발급일은 필수입니다.")
    @JsonProperty("issue_date")
    private LocalDate issueDate;

    @NotBlank(message = "자격증 주인 이름은 필수입니다.")
    @JsonProperty("owner_name")
    private String ownerName;

    @NotNull(message = "파일 ID는 필수입니다.")
    @JsonProperty("file_id")
    private Long fileId;
}
