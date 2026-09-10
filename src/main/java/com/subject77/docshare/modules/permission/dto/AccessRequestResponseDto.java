package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequestResponseDto {

    private Long id;
    private String requesterUserId;
    private String requesterUserEmail;
    private String requesterUserName;
    private String targetType; // DOCUMENT or FOLDER
    private Long documentId;
    private String documentFileHash;
    private String documentName;
    private Long folderId;
    private String folderName;
    private PermissionLevel requestedPermissionLevel;
    private AccessRequestStatus status;
    private String reason;
    private String processedByUserId;
    private String processedByUserEmail;
    private Instant processedAt;
    private String reviewerNote;
    private String cancellationReason;
    private Long grantedPermissionId;
    private PermissionLevel grantedPermissionLevel;
    private String reviewerAuthorityExplanation;
    private Long workspaceId;
    private String workspaceName;
    private String orgCode;
    private Instant createdAt;
    private Instant updatedAt;
}
