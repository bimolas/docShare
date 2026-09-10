package com.subject77.docshare.modules.document.dto;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponseDto {
    private Long id;
    private String fileHash;
    private String fileName;
    private String contentType;
    private String s3Bucket;
    private String objectKey;
    private Long fileSize;
    private Long folderId;
    private String ownerOrgCode;
    private String uploadedByUserId;
    private Instant uploadedAt;
    private Integer version;
    private Instant lastModifiedAt;
    private String lastModifiedByUserId;
    private String lastModifiedByOrgCode;
}
