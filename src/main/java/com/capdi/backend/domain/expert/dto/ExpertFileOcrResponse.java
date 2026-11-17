package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.OcrStatusEnum;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExpertFileOcrResponse {
    private OcrStatusEnum status;
    private long revision;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorCode;
    private String text;

    public static ExpertFileOcrResponse from(ExpertFile file) {
        return ExpertFileOcrResponse.builder().status(file.getOcrStatus()).revision(file.getOcrRevision())
                .startedAt(file.getOcrStartedAt()).finishedAt(file.getOcrFinishedAt())
                .errorCode(file.getOcrErrorCode())
                .text(file.getOcrStatus() == OcrStatusEnum.SUCCEEDED ? file.getOcrRawText() : null).build();
    }
}
