package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionRequestDto {

    @NotNull(message = "Permission type is required")
    private PermissionType permissionType;

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    @NotNull(message = "Recipient type is required")
    private RecipientType recipientType;

    private Long documentId;
    private String documentFileHash;
    private Long folderId;
    private String targetUserEmail;
    private String targetOrgCode;
    private Long targetGroupId;
    private String passwordHash;
    private Instant expiresAt;
    private Long collaborationId;
    private Long workspaceId;
    private String note;
}
