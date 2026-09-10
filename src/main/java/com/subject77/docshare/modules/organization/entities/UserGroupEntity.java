package com.subject77.docshare.modules.organization.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "user_groups", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "group_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGroupEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", referencedColumnName = "id", nullable = false)
    private GroupEntity group;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private GroupMemberRole role = GroupMemberRole.MEMBER;

    @Column(nullable = false, updatable = false)
    private Instant joinedAt;

    @PrePersist
    protected void onJoin() {
        if (joinedAt == null) {
            joinedAt = Instant.now();
        }
        if (role == null) {
            role = GroupMemberRole.MEMBER;
        }
    }
}
