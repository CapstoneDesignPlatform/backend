package com.capdi.backend.domain.admin.dto;

import com.capdi.backend.domain.announcement.entity.AnnouncementStatusEnum;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class AdminAnnouncementStatusRequest {

    @NotNull(message = "변경할 상태값은 필수입니다.")
    private AnnouncementStatusEnum status;
}
