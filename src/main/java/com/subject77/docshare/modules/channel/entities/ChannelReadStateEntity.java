package com.subject77.docshare.modules.channel.entities;

import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Tracks the per-user "last read" position within a channel.
 *
 * <p>A row exists only once a user has explicitly read (or has been implicitly
 * marked as having read) at least one message in the channel.  The absence of
 * a row is equivalent to "never read" and means all messages in the channel are
 * unread for that user.</p>
 *
 * <p>Unread count is computed on demand:
 * {@code count(messages where createdAt > lastReadAt and author != currentUser)}</p>
 */
@Entity
@Table(name = "channel_read_states", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"channel_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelReadStateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private ChannelEntity channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private UserEntity user;

    /**
     * The timestamp up to which the user has read messages in this channel.
     * Any message with {@code createdAt > lastReadAt} (authored by someone
     * other than this user) is considered unread.
     */
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
