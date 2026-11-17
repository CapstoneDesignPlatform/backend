package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.BusinessVerification;
import com.capdi.backend.domain.expert.entity.BusinessVerification.*;
import com.capdi.backend.domain.expert.dto.NtsBusinessResult.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class BusinessVerificationResponse {
    @JsonProperty("execution_id") private String executionId;
    @JsonProperty("execution_status") private ExecutionStatus executionStatus;
    private Kind kind;
    private Authenticity authenticity;
    @JsonProperty("business_state") private BusinessState businessState;
    private Failure failure;
    private Validity validity;
    @JsonProperty("file_id") private Long fileId;
    @JsonProperty("file_sha256") private String fileSha256;
    @JsonProperty("ocr_revision") private long ocrRevision;
    @JsonProperty("input_revision") private long inputRevision;
    @JsonProperty("status_lookup_performed") private boolean statusLookupPerformed;
    @JsonProperty("requested_at") private LocalDateTime requestedAt;
    @JsonProperty("completed_at") private LocalDateTime completedAt;

    public static BusinessVerificationResponse from(BusinessVerification row, boolean current) {
        return BusinessVerificationResponse.builder().executionId(row.getExecutionId()).executionStatus(row.getExecutionStatus())
                .kind(row.getKind()).authenticity(row.getAuthenticity()).businessState(row.getBusinessState()).failure(row.getFailure())
                .validity(current && row.getValidity() == Validity.CURRENT ? Validity.CURRENT : Validity.STALE)
                .fileId(row.getFileId()).fileSha256(row.getFileSha256()).ocrRevision(row.getOcrRevision())
                .inputRevision(row.getInputRevision()).statusLookupPerformed(row.isStatusLookupPerformed())
                .requestedAt(row.getRequestedAt()).completedAt(row.getCompletedAt()).build();
    }
}
