package com.subject77.docshare.modules.audit.dto;

import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Immutable command object representing a governance audit logging intent.
 * Encapsulates point-in-time actor, resource, and organizational scope
 * without positional argument ambiguity.
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AuditPermissionChangeCommand {
    private String action;
    private String targetType; // DOCUMENT, FOLDER, WORKSPACE, ORGANIZATION, COLLABORATION, etc.
    private Long targetId;
    private String targetFileHash;
    private Long folderId;
    private Long workspaceId;
    private Long groupId;
    private Long collaborationId;
    private SpaceType spaceType;
    private String orgCode;
    private UserEntity grantedBy;
    private String actorUserId;
    private String grantedToEmail;
    private String grantedToOrgCode;
    private PermissionLevel permissionLevel;
    private String previousValue;
    private String newValue;
    private String reason;
    private String details;
    private String recordHash;
    private BlockchainStatus blockchainStatus;
    private String blockchainTxHash;
    private Instant validFrom;
    private Instant expiresAt;
    private Instant timestamp;
}
