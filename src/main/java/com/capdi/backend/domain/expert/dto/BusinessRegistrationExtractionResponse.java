package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.BusinessRegistrationInfo;
import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.capdi.backend.domain.expert.entity.OcrStatusEnum;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class BusinessRegistrationExtractionResponse {
    @JsonProperty("business_registration_info_id")
    private Long businessRegistrationInfoId;
    @JsonProperty("file_id")
    private Long fileId;
    @JsonProperty("ocr_revision")
    private long ocrRevision;
    @JsonProperty("ocr_status")
    private OcrStatusEnum ocrStatus;
    @JsonProperty("snapshot_at")
    private LocalDateTime snapshotAt;
    @JsonProperty("extraction_status")
    private String extractionStatus;
    @JsonProperty("user_input")
    private BusinessRegistrationInfoResponse userInput;
    private BusinessRegistrationExtraction extracted;
    @JsonProperty("reference_only")
    private boolean referenceOnly;
    @JsonProperty("requires_user_confirmation")
    private boolean requiresUserConfirmation;
    @JsonProperty("page_information_available")
    private boolean pageInformationAvailable;
    private List<String> limitations;

    public static BusinessRegistrationExtractionResponse from(BusinessRegistrationInfo info, ExpertFile file,
                                                               BusinessRegistrationExtraction extracted) {
        String state = file.getOcrStatus() != OcrStatusEnum.SUCCEEDED ? "OCR_NOT_SUCCEEDED"
                : file.getOcrRawText() == null || file.getOcrRawText().isBlank() ? "OCR_TEXT_UNAVAILABLE" : "AVAILABLE";
        return BusinessRegistrationExtractionResponse.builder().businessRegistrationInfoId(info == null ? null : info.getId())
                .fileId(file.getId()).ocrRevision(file.getOcrRevision()).ocrStatus(file.getOcrStatus())
                .snapshotAt(LocalDateTime.now()).extractionStatus(state)
                .userInput(info == null ? null : BusinessRegistrationInfoResponse.from(info))
                .extracted(extracted).referenceOnly(true).requiresUserConfirmation(true).pageInformationAvailable(false)
                .limitations(List.of("PAGE_METADATA_NOT_STORED", "REVISION_SNAPSHOT_ONLY")).build();
    }
}
