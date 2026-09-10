package com.subject77.docshare.modules.audit.entities;

import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "permission_audit_logs", indexes = {
    @Index(name = "idx_perm_audit_org_time", columnList = "orgCode, timestamp"),
    @Index(name = "idx_perm_audit_ws_time", columnList = "workspace_id, timestamp"),
    @Index(name = "idx_perm_audit_target_time", columnList = "target_type, targetId, timestamp"),
    @Index(name = "idx_perm_audit_actor_time", columnList = "actor_user_id, timestamp"),
    @Index(name = "idx_perm_audit_folder_time", columnList = "folder_id, timestamp"),
    @Index(name = "idx_perm_audit_group_time", columnList = "group_id, timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String action; // e.g., GRANTED_USER, GRANTED_ORG, CREATED_SHARE_LINK, REVOKED, EXPIRED

    @Column(name = "target_type", nullable = false)
    private String targetType; // DOCUMENT or FOLDER

    private Long targetId;

    private String targetFileHash;

    private Long folderId;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "collaboration_id")
    private Long collaborationId;

    @Enumerated(EnumType.STRING)
    private SpaceType spaceType;

    private String orgCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id", referencedColumnName = "id", nullable = true)
    private UserEntity grantedBy;

    private String grantedToEmail;

    private String grantedToOrgCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private PermissionLevel permissionLevel;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "reason")
    private String reason;

    @Column(name = "details", length = 2048)
    private String details;

    private Instant validFrom;

    private Instant expiresAt;

    @Column(name = "record_hash")
    private String recordHash;

    @Column(name = "blockchain_tx_hash")
    private String blockchainTxHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "blockchain_status")
    private com.subject77.docshare.modules.permission.entities.BlockchainStatus blockchainStatus;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
        if (actorUserId == null && grantedBy != null) {
            actorUserId = grantedBy.getId();
        }
    }
}
