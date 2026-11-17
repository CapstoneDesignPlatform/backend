package com.capdi.backend.domain.announcement.dto;

import com.capdi.backend.domain.client.dto.ClientInfoRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class GuestAnnouncementCreateRequest {

    @Valid
    @NotNull(message = "기업 정보는 필수입니다.")
    @Schema(description = "공고와 함께 새로 저장할 비회원 기업 정보")
    private ClientInfoRequest clientInfo;

    @Valid
    @NotNull(message = "공고 정보는 필수입니다.")
    private AnnouncementCreateRequest announcement;
}
