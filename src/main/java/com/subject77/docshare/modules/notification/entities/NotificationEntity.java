package com.subject77.docshare.modules.notification.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
    name = "notifications",
    indexes = {
        @Index(name = "idx_noti_recipient", columnList = "recipient_id"),
        @Index(name = "idx_noti_recipient_status", columnList = "recipient_id, status"),
        @Index(name = "idx_noti_recipient_created", columnList = "recipient_id, created_at"),
        @Index(name = "idx_noti_recipient_deleted", columnList = "recipient_id, deleted_at"),
        @Index(name = "idx_noti_idempotency", columnList = "idempotency_key")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_notifications_idempotency_key", columnNames = {"idempotency_key"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", referencedColumnName = "id", nullable = false)
    private UserEntity recipient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", referencedColumnName = "id")
    private UserEntity actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    @Builder.Default
    private NotificationCategory category = NotificationCategory.SYSTEM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    @Builder.Default
    private NotificationPriority priority = NotificationPriority.MEDIUM;

    public NotificationCategory getCategory() {
        return category != null ? category : NotificationCategory.SYSTEM;
    }

    public NotificationPriority getPriority() {
        return priority != null ? priority : NotificationPriority.MEDIUM;
    }

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private NotificationSourceType sourceType;

    @Column(name = "source_id")
    private String sourceId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = NotificationStatus.UNREAD;
        }
        if (category == null) {
            category = NotificationCategory.SYSTEM;
        }
        if (priority == null) {
            priority = NotificationPriority.MEDIUM;
        }
    }
}

