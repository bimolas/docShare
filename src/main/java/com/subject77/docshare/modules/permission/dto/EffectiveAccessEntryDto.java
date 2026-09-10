package com.subject77.docshare.modules.permission.dto;

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
public class EffectiveAccessEntryDto {

    private Long permissionId;
    private String principalType; // "USER", "TEAM", "ORGANIZATION", "COLLABORATION"
    private String principalId;   // userId, groupId, orgCode, collaborationId
    private String displayName;   // user name/email, team name, org name/code, collaboration name
    private String email;         // target user email if principalType == "USER"
    private PermissionLevel permissionLevel; // VIEW, DOWNLOAD, EDIT
    private PermissionType permissionType;   // ALLOW, EXCLUDE
    private String source;        // "DIRECT", "TEAM", "ORGANIZATION", "WORKSPACE", "COLLABORATION", "INHERITED", "EXCLUDED"
    private Long inheritedFromResourceId;
    private String inheritedFromResourceName;
    private Instant expiresAt;
    private boolean external;
    private String status;        // "ACTIVE", "EXPIRED", "REVOKED"
}
