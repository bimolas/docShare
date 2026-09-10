package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkShareWithExclusionsDto {

    @NotNull(message = "Folder ID is required")
    private Long folderId;

    private String targetUserEmail;

    @NotNull(message = "Recipient type is required")
    private RecipientType recipientType;
    private String targetOrgCode;
    private Long targetGroupId;

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    private List<Long> excludedDocumentIds;
    private List<Long> excludedSubfolderIds;

    private Instant expiresAt;
    private String note;
}
