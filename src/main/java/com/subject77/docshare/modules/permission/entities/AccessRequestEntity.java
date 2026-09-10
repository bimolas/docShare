package com.subject77.docshare.modules.permission.entities;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "access_requests", indexes = {
    @Index(name = "idx_access_req_requester_doc", columnList = "requester_id, document_id"),
    @Index(name = "idx_access_req_requester_folder", columnList = "requester_id, folder_id"),
    @Index(name = "idx_access_req_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccessRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", referencedColumnName = "id", nullable = false)
    private UserEntity requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", referencedColumnName = "id")
    private DocumentEntity document;

    @Column(name = "document_file_hash")
    private String documentFileHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", referencedColumnName = "id")
    private FolderEntity folder;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_permission_level", nullable = false)
    private PermissionLevel requestedPermissionLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    @Column(name = "reason", length = 1000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by_user_id", referencedColumnName = "id")
    private UserEntity processedBy;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_permission_id", referencedColumnName = "id")
    private PermissionEntity grantedPermission;

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
        if (status == null) {
            status = AccessRequestStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
