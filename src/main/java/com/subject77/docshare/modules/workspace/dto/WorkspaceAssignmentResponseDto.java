package com.subject77.docshare.modules.workspace.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAssignmentResponseDto {

    private Long permissionId;
    private Long workspaceId;
    private Long folderId;
    private String folderName;
    private Long teamId;
    private String teamName;
    private String userEmail;
    private PermissionLevel permissionLevel;
    private PermissionType permissionType;
    private String grantedByUserId;
    private String grantedByUserEmail;
    private Instant grantedAt;
}
