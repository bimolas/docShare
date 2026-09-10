package com.subject77.docshare.modules.document.dto;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewUrlResponse {
    private Long documentId;
    private String previewUrl;
    private Instant expiresAt;
    private String fileName;
    private String contentType;
}
