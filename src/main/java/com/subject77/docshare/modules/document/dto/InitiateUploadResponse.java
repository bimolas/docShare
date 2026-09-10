package com.subject77.docshare.modules.document.dto;

import com.subject77.docshare.modules.document.entities.DocumentStatus;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiateUploadResponse {

    private Long documentId;
    private String objectKey;
    private String s3Bucket;
    private String presignedUploadUrl;
    private Instant expiresAt;
    private DocumentStatus status;
}
