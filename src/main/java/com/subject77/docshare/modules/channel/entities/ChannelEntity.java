package com.subject77.docshare.modules.channel.entities;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "channels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelStatus status;

    @Builder.Default
    @Column(name = "allow_external", nullable = false)
    private boolean allowExternal = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_code", referencedColumnName = "org_code")
    private OrganizationEntity organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", referencedColumnName = "id")
    private GroupEntity team;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_workspace_id", referencedColumnName = "id")
    private ProjectWorkspaceEntity projectWorkspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collaboration_id", referencedColumnName = "id")
    private CollaborationEntity collaboration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", referencedColumnName = "id", nullable = false)
    private UserEntity createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
        if (status == null) {
            status = ChannelStatus.ACTIVE;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
