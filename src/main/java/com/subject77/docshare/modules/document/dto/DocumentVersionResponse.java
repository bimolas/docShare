package com.subject77.docshare.modules.document.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentVersionResponse {
    private Long id;
    private Long documentId;
    private Integer versionNumber;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String checksumSha256;
    private String uploadedByEmail;
    private Instant uploadedAt;
    private boolean isCurrent;
}
