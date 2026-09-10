package com.subject77.docshare.modules.workspace.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceTeamAssignmentRequestDto {

    @NotNull(message = "Team ID is required")
    private Long teamId;

    private Long folderId; // Optional: if null, defaults to workspace root folder

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    @Builder.Default
    private PermissionType permissionType = PermissionType.ALLOW;
}
