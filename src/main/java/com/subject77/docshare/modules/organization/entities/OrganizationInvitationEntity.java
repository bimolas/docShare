package com.subject77.docshare.modules.organization.entities;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "organization_invitations", indexes = {
        @Index(name = "idx_org_inv_token", columnList = "token", unique = true),
        @Index(name = "idx_org_inv_org_status", columnList = "org_code, status"),
        @Index(name = "idx_org_inv_email_status", columnList = "invitee_email, status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_code", nullable = false)
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inviter_id", nullable = false)
    private UserEntity inviter;

    @Column(name = "invitee_email", nullable = false)
    private String inviteeEmail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invitee_id")
    private UserEntity invitee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationInvitationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(length = 1000)
    private String note;

    // Optional Onboarding Assignments
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_group_id")
    private GroupEntity targetGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_group_role")
    private GroupMemberRole targetGroupRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_workspace_id")
    private ProjectWorkspaceEntity targetWorkspace;

    @Column(name = "target_folder_id")
    private Long targetFolderId;

    @Column(name = "target_document_hash")
    private String targetDocumentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "onboarding_permission_level")
    private PermissionLevel onboardingPermissionLevel;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = OrganizationInvitationStatus.PENDING;
        }
        if (role == null) {
            role = Role.ROLE_USER;
        }
        if (inviteeEmail != null) {
            inviteeEmail = inviteeEmail.trim().toLowerCase();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
