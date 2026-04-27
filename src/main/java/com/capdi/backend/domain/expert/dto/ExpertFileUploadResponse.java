package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ExpertFileUploadResponse {

    private FileDto file;

    public static ExpertFileUploadResponse from(ExpertFile expertFile) {
        return ExpertFileUploadResponse.builder()
                .file(FileDto.from(expertFile))
                .build();
    }

    @Getter
    @Builder
    public static class FileDto {
        private Long id;

        @JsonProperty("original_name")
        private String originalName;

        @JsonProperty("stored_name")
        private String storedName;

        @JsonProperty("mime_type")
        private String mimeType;

        private Long size;

        private String purpose;

        @JsonProperty("created_at")
        private LocalDateTime createdAt;

        public static FileDto from(ExpertFile expertFile) {
            return FileDto.builder()
                    .id(expertFile.getId())
                    .originalName(expertFile.getOriginalName())
                    .storedName(expertFile.getStoredName())
                    .mimeType(expertFile.getMimeType())
                    .size(expertFile.getFileSize())
                    .purpose(expertFile.getFileType().name())
                    .createdAt(expertFile.getCreatedAt())
                    .build();
        }
    }
}