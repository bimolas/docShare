package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.MessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.Instant;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    List<MessageEntity> findByChannel_IdAndDeletedAtIsNull(Long channelId);

    Page<MessageEntity> findByChannel_IdAndParentMessageIsNull(Long channelId, Pageable pageable);

    Page<MessageEntity> findByChannel_IdAndParentMessage_Id(Long channelId, Long parentMessageId, Pageable pageable);

    long countByParentMessage_Id(Long parentMessageId);

    /**
     * Count messages in a channel sent after {@code after} (exclusive) by authors other than the
     * requesting user, excluding soft-deleted messages.  Used to calculate unread count when a
     * read-state cursor exists.
     */
    long countByChannel_IdAndCreatedAtAfterAndDeletedAtIsNullAndAuthor_IdNot(
            Long channelId, Instant after, String authorId);

    /**
     * Count all non-deleted messages in a channel authored by someone other than the requesting
     * user.  Used when the user has never marked the channel as read (no cursor row exists).
     */
    long countByChannel_IdAndDeletedAtIsNullAndAuthor_IdNot(Long channelId, String authorId);
}
