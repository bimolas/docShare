package com.subject77.docshare.modules.collaboration.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "collaboration_participants", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"collaboration_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaborationParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collaboration_id", nullable = false)
    private CollaborationEntity collaboration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(name = "collaboration_role", nullable = false)
    @Builder.Default
    private CollaborationRole collaborationRole = CollaborationRole.MEMBER;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (collaborationRole == null) {
            collaborationRole = CollaborationRole.MEMBER;
        }
    }
}
