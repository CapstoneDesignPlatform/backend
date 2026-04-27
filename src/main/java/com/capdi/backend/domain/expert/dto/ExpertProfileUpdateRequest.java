package com.capdi.backend.domain.expert.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Getter
@NoArgsConstructor
public class ExpertProfileUpdateRequest {

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phone;

    @NotBlank(message = "회사명은 필수입니다.")
    @JsonProperty("company_name")
    private String companyName;

    @JsonProperty("expertise_areas")
    private List<String> expertiseAreas;

    private String portfolio;
}