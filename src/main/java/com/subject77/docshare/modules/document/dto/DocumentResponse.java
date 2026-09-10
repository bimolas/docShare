package com.subject77.docshare.modules.document.dto;

import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.SpaceType;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    private Long id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String fileHash;
    private String s3Bucket;
    private String objectKey;
    private DocumentStatus status;
    private SpaceType spaceType;
    private String ownerOrgCode;
    private String uploadedByUserId;
    private String uploadedByUserEmail;
    private Long folderId;
    private String folderName;
    private Instant uploadedAt;
    private Integer version;
    private String accessStatus;
    private Boolean isLocked;
    private String requestStatus;
    private Boolean canRequestAccess;
    private Long pendingRequestId;
}
