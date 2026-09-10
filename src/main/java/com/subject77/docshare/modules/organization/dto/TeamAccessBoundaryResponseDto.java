package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamAccessBoundaryResponseDto {
    private Long permissionId;
    private Long teamId;
    private String teamName;
    private Long workspaceId;
    private String workspaceName;
    private Long folderId;
    private String folderName;
    private String folderPath;
    private PermissionLevel ceilingLevel;
    private String allocatedByUserId;
    private String allocatedByUserEmail;
    private Instant allocatedAt;
}
