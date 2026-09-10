package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponseDto {
    private Long id;
    private PermissionType permissionType;
    private PermissionLevel permissionLevel;
    private RecipientType recipientType;
    private String grantedByUserId;
    private Instant createdAt;
    private Long documentId;
    private String documentFileHash;
    private Long folderId;
    private String folderName;
    private String subspaceType;
    private String orgName;
    private String documentName;
    private String targetUserEmail;
    private String targetOrgCode;
    private Long targetGroupId;
    private Long collaborationId;
    private Long workspaceId;
    private com.subject77.docshare.modules.permission.entities.AuthorityTier grantorAuthorityTier;
    private String shareLinkToken;
    private String passwordHash;
    private Instant validFrom;
    private Instant expiresAt;
    private String recordHash;
    private String blockchainTxHash;
    private BlockchainStatus blockchainStatus;
    private boolean revoked;
    private boolean suspended;
    private Instant revokedAt;
    private String revokedByUserId;
    private String note;
}
