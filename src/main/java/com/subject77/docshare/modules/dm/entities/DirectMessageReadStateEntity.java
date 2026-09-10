package com.subject77.docshare.modules.dm.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "dm_thread_read_states", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"thread_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageReadStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id", nullable = false)
    private DirectMessageThreadEntity thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    @Column(name = "last_read_at", nullable = false)
    private Instant lastReadAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        updatedAt = Instant.now();
        if (lastReadAt == null) {
            lastReadAt = Instant.now();
        }
    }
}
