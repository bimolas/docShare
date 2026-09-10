package com.subject77.docshare.modules.workspace.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a scoped Workspace Manager assignment.
 * A user assigned here may administer folder permissions ONLY within
 * the workspace's root-folder hierarchy.
 * This does NOT use the global Role enum.
 */
@Entity
@Table(
    name = "workspace_managers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_workspace_manager_workspace_user",
                          columnNames = {"workspace_id", "user_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceManagerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private ProjectWorkspaceEntity workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
