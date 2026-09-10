package com.subject77.docshare.modules.workspace.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceUserAssignmentRequestDto {

    @NotBlank(message = "Target user email is required")
    @Email(message = "Invalid target user email format")
    private String userEmail;

    private Long folderId;

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    @Builder.Default
    private PermissionType permissionType = PermissionType.ALLOW;
}
