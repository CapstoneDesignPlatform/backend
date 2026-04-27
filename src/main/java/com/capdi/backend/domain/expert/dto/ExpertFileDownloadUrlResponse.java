package com.capdi.backend.domain.expert.dto;

import com.capdi.backend.domain.expert.entity.ExpertFile;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ExpertFileDownloadUrlResponse {

    private FileDto file;

    public static ExpertFileDownloadUrlResponse from(ExpertFile expertFile) {
        return ExpertFileDownloadUrlResponse.builder()
                .file(FileDto.from(expertFile))
                .build();
    }

    @Getter
    @Builder
    public static class FileDto {
        private Long id;

        @JsonProperty("original_name")
        private String originalName;

        @JsonProperty("mime_type")
        private String mimeType;

        private Long size;

        @JsonProperty("download_url")
        private String downloadUrl;

        @JsonProperty("expires_at")
        private LocalDateTime expiresAt;

        public static FileDto from(ExpertFile expertFile) {
            return FileDto.builder()
                    .id(expertFile.getId())
                    .originalName(expertFile.getOriginalName())
                    .mimeType(expertFile.getMimeType())
                    .size(expertFile.getFileSize())
                    .downloadUrl("http://localhost:8081/api/files/" + expertFile.getId() + "/download")
                    .expiresAt(null)
                    .build();
        }
    }
}