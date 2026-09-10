package com.subject77.docshare.modules.audit.dto;

import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionAuditLogResponse {
    private Long id;
    private String action;
    private com.subject77.docshare.modules.audit.entities.AuditEventType canonicalAction;
    private String targetType;
    private Long targetId;
    private String targetFileHash;
    private Long folderId;
    private SpaceType spaceType;
    private String orgCode;
    private String grantedByEmail;
    private String grantedToEmail;
    private String grantedToOrgCode;
    private PermissionLevel permissionLevel;
    private String previousValue;
    private String newValue;
    private String reason;
    private String details;
    private Instant validFrom;
    private Instant expiresAt;
    private Instant timestamp;
    private String actorUserId;
    private Long workspaceId;
    private Long groupId;
    private Long collaborationId;
}
