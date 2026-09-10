package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedItemDto {
    private Long permissionId;
    private String resourceType; // "FOLDER" or "DOCUMENT"
    private Long resourceId;
    private String name;
    private String fileHash;
    private String grantedByUserId;
    private String grantedByUserEmail;
    private PermissionType permissionType;
    private PermissionLevel permissionLevel;
    private Instant grantedAt;
    private Instant expiresAt;
    private String note;

    // Phase 4B.2.3: Shared With Me metadata enrichment
    private String grantedByName;
    private String sourceType; // "PERSONAL" or "ORGANIZATION"
    private String ownerOrgCode;
    private String ownerOrgName;
    private Long workspaceId;
    private String workspaceName;
    private Long fileSize;
    private String contentType;
    private Boolean external;

    // Phase 6A.3: Recipient & Lifecycle Metadata for Shared By Me discovery
    private String recipientType; // "USER", "GROUP", "ORGANIZATION", "COLLABORATION"
    private String targetUserEmail;
    private String targetUserName;
    private Long targetGroupId;
    private String targetGroupName;
    private String targetOrgCode;
    private String targetOrgName;
    private Long collaborationId;
    private String collaborationName;
    private String status; // "ACTIVE", "SUSPENDED", "REVOKED", "EXPIRED"
    private Boolean revoked;
    private Boolean suspended;
    private Instant revokedAt;

    // Phase 7: Explicit-Share Source & Multi-Grant Attribution Enrichment
    private String sourceLabel;         // "DIRECT SHARE", "TEAM SHARE", "ORGANIZATION SHARE", "COLLABORATION SHARE"
    private java.util.List<String> sourceLabels;   // All distinct applicable source labels when multi-granted
    private String sourceDescription;   // Human-readable explanation: "Shared with team 'Backend Devs' by Alice"
    private Integer matchingGrantCount; // Total matching grants conferring access to this resource
    private java.util.List<Long> matchedPermissionIds; // All permission IDs conferring access
}
