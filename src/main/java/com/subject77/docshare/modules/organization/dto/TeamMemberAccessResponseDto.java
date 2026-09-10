package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberAccessResponseDto {
    private Long permissionId;
    private Long teamId;
    private String teamName;
    private String userEmail;
    private String userName;
    private Long resourceId;
    private String resourceType; // "FOLDER" or "DOCUMENT"
    private String resourceName;
    private String resourcePath;
    private PermissionLevel permissionLevel;
    private AuthorityTier grantorAuthorityTier;
    private String grantedByUserId;
    private String grantedByUserEmail;
    private Instant grantedAt;
    private boolean isMigrated;
}
