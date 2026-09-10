package com.subject77.docshare.modules.permission.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionType permissionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PermissionLevel permissionLevel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity grantedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    private DocumentEntity document;

    @Column(name = "document_file_hash")
    private String documentFileHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private FolderEntity folder;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", length = 32)
    private RecipientType recipientType;

    @Column(name = "target_user_email")
    private String targetUserEmail;

    @Column(name = "target_org_code")
    private String targetOrgCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_group_id")
    private com.subject77.docshare.modules.organization.entities.GroupEntity targetGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collaboration_id")
    private CollaborationEntity collaboration;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "grantor_authority_tier", length = 32)
    private AuthorityTier grantorAuthorityTier;

    private String shareLinkToken;

    private String passwordHash;

    @Column(name = "note", length = 1000)
    private String note;

    private Instant validFrom;

    private Instant expiresAt;

    @Column(name = "record_hash")
    private String recordHash;

    @Column(name = "blockchain_tx_hash")
    private String blockchainTxHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "blockchain_status")
    private BlockchainStatus blockchainStatus;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean revoked = false;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean suspended = false;

    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_user_id")
    private UserEntity revokedBy;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)
            createdAt = Instant.now();
        if (blockchainStatus == null)
            blockchainStatus = BlockchainStatus.PENDING;
    }
}
