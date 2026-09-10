package com.subject77.docshare.modules.policy.entities;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "workspace_policies", uniqueConstraints = {
    @UniqueConstraint(name = "uk_workspace_policy_org_code", columnNames = {"org_code"}),
    @UniqueConstraint(name = "uk_workspace_policy_user_id", columnNames = {"user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspacePolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_type", nullable = false)
    private WorkspaceType workspaceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = true, unique = true)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_code", referencedColumnName = "org_code", nullable = true, unique = true)
    private OrganizationEntity organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_sharing_mode", nullable = false)
    private ExternalSharingMode externalSharingMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "discoverability_mode", nullable = false)
    private DiscoverabilityMode discoverabilityMode;

    @Builder.Default
    @Column(name = "allow_access_requests", nullable = false)
    private Boolean allowAccessRequests = true;

    @Builder.Default
    @Column(name = "require_request_reason", nullable = false)
    private Boolean requireRequestReason = false;

    @Builder.Default
    @Column(name = "allow_external_access_requests", nullable = false)
    private Boolean allowExternalAccessRequests = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "reviewer_scope_policy", nullable = false)
    @Builder.Default
    private ReviewerScopePolicy reviewerScopePolicy = ReviewerScopePolicy.RESOURCE_AND_WORKSPACE_MANAGERS;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }

        if (workspaceType == null) {
            if (user != null && organization == null) {
                workspaceType = WorkspaceType.PERSONAL;
            } else if (organization != null && user == null) {
                workspaceType = WorkspaceType.ORGANIZATION;
            }
        }

        validateWorkspaceAssociation();
        applyDefaultModes();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        validateWorkspaceAssociation();
        applyDefaultModes();
    }

    private void validateWorkspaceAssociation() {
        if (workspaceType == WorkspaceType.PERSONAL) {
            if (user == null || organization != null) {
                throw new IllegalStateException("Personal workspace policy must be associated with a User and no Organization.");
            }
        } else if (workspaceType == WorkspaceType.ORGANIZATION) {
            if (organization == null || user != null) {
                throw new IllegalStateException("Organization workspace policy must be associated with an Organization and no User.");
            }
        } else {
            throw new IllegalStateException("Workspace policy must have a valid WorkspaceType (PERSONAL or ORGANIZATION).");
        }
    }

    private void applyDefaultModes() {
        if (allowAccessRequests == null) {
            allowAccessRequests = true;
        }
        if (requireRequestReason == null) {
            requireRequestReason = false;
        }
        if (allowExternalAccessRequests == null) {
            allowExternalAccessRequests = true;
        }

        if (workspaceType == WorkspaceType.PERSONAL) {
            if (externalSharingMode == null) {
                externalSharingMode = ExternalSharingMode.OFF;
            }
            if (discoverabilityMode == null) {
                discoverabilityMode = DiscoverabilityMode.PRIVATE;
            }
        } else if (workspaceType == WorkspaceType.ORGANIZATION) {
            if (externalSharingMode == null) {
                externalSharingMode = ExternalSharingMode.OFF;
            }
            if (discoverabilityMode == null) {
                discoverabilityMode = DiscoverabilityMode.DISCOVERABLE;
            }
        }
    }
}
