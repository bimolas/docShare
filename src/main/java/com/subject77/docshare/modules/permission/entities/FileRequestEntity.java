package com.subject77.docshare.modules.permission.entities;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a request to have files uploaded into a destination folder.
 *
 * <h3>Recipient model</h3>
 * <ul>
 *   <li>{@link FileRequestRecipientType#USER} — {@code recipientUser} is set;
 *       {@code recipientGroup} is {@code null}.</li>
 *   <li>{@link FileRequestRecipientType#TEAM} — {@code recipientGroup} is set;
 *       {@code recipientUser} is {@code null}.  Upload eligibility is resolved
 *       <em>dynamically</em> from current group membership at upload time — no
 *       membership snapshot is taken at creation time.  Any active member of
 *       the group who also holds an ACTIVE organisation membership may upload
 *       while the request remains PENDING.</li>
 * </ul>
 *
 * <h3>Security invariant</h3>
 * Neither USER nor TEAM recipients are ever granted a
 * {@code PermissionEntity} for the destination folder as a side-effect of
 * this request.  Upload remains strictly upload-only.
 */
@Entity
@Table(name = "file_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id", referencedColumnName = "id", nullable = false)
    private UserEntity requester;

    // ── Recipient discriminator ──────────────────────────────────────────────

    /**
     * Whether this request targets a single user or a team/group.
     * Defaults to {@link FileRequestRecipientType#USER} for backward
     * compatibility with records created before team support was added.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_type", nullable = false)
    @Builder.Default
    private FileRequestRecipientType recipientType = FileRequestRecipientType.USER;

    /**
     * Populated when {@code recipientType == USER}; {@code null} for TEAM requests.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", referencedColumnName = "id", nullable = true)
    private UserEntity recipientUser;

    /**
     * Populated when {@code recipientType == TEAM}; {@code null} for USER requests.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_group_id", referencedColumnName = "id", nullable = true)
    private GroupEntity recipientGroup;

    // ── Destination ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_folder_id", referencedColumnName = "id", nullable = false)
    private FolderEntity destinationFolder;

    // ── Content ──────────────────────────────────────────────────────────────

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "note", length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private FileRequestStatus status = FileRequestStatus.PENDING;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Builder.Default
    @OneToMany(mappedBy = "fileRequest", cascade = CascadeType.ALL)
    private List<DocumentEntity> uploadedDocuments = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ── Lifecycle hooks ──────────────────────────────────────────────────────

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
            status = FileRequestStatus.PENDING;
        }
        if (recipientType == null) {
            recipientType = FileRequestRecipientType.USER;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Convenience accessor retained for backward compatibility with code that
     * only deals with USER-type requests.  Returns {@code null} for TEAM requests.
     *
     * @deprecated Prefer {@link #getRecipientUser()} with an explicit
     *             {@code recipientType} check.
     */
    @Deprecated
    public UserEntity getRecipient() {
        return recipientUser;
    }

    /**
     * Convenience setter retained for backward compatibility.
     *
     * @deprecated Prefer {@link #setRecipientUser(UserEntity)}.
     */
    @Deprecated
    public void setRecipient(UserEntity user) {
        this.recipientUser = user;
    }
}
