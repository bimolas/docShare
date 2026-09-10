package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAccessSummaryDto {
    private Long userId;
    private String email;
    private String fullName;
    private PermissionLevel effectiveAccessLevel;
    private String accessSource; // e.g., "OWNER", "DIRECT", "INHERITED", "ORGANIZATION"
    private Long inheritedFromFolderId;
    private String inheritedFromFolderName;
}

