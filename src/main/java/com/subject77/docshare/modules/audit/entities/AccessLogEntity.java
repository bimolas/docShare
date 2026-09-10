package com.subject77.docshare.modules.audit.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

import com.subject77.docshare.modules.folder.entities.FolderEntity;

@Entity
@Table(name = "access_logs", indexes = {
    @Index(name = "idx_access_logs_org_time", columnList = "orgCode, timestamp"),
    @Index(name = "idx_access_logs_ws_time", columnList = "workspace_id, timestamp"),
    @Index(name = "idx_access_logs_doc_time", columnList = "documentId, timestamp"),
    @Index(name = "idx_access_logs_actor_time", columnList = "actor_user_id, timestamp"),
    @Index(name = "idx_access_logs_folder_time", columnList = "folder_id, timestamp"),
    @Index(name = "idx_access_logs_group_time", columnList = "group_id, timestamp")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(nullable = false)
    private Instant timestamp;

    private Long documentId;

    @Column(name = "document_file_hash")
    private String documentFileHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", foreignKey = @ForeignKey(name = "fkg7u3e43tja6uf5aikhncpwqsm"))
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private FolderEntity folder;

    @Column(name = "actor_user_id")
    private String actorUserId;

    @Column(name = "workspace_id")
    private Long workspaceId;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private Long resourceId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "collaboration_id")
    private Long collaborationId;

    private String accessorEmail;

    private String orgCode;

    private String ipAddress;

    private String userAgent;

    @Column(length = 2048)
    private String details; // JSON or text summary of change (e.g. metadata diff, version number)

    private String txHash;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null)
            timestamp = Instant.now();
    }
}
