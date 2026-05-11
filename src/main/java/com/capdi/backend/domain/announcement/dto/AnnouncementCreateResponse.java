package com.capdi.backend.domain.announcement.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor(staticName = "of")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AnnouncementCreateResponse {

    private String announcementCode;
}
