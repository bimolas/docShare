package com.subject77.docshare.modules.dm.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "user_message_privacy_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMessagePrivacySettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "allow_internal_dms", nullable = false)
    @Builder.Default
    private boolean allowInternalDms = true;

    @Column(name = "allow_external_dms", nullable = false)
    @Builder.Default
    private boolean allowExternalDms = true;

    @Column(name = "read_receipts_enabled", nullable = false)
    @Builder.Default
    private boolean readReceiptsEnabled = true;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
