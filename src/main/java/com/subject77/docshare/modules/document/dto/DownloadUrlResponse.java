package com.subject77.docshare.modules.document.dto;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadUrlResponse {
    private Long documentId;
    private String downloadUrl;
    private Instant expiresAt;
    private String fileName;
}
