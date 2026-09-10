package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.MessageReactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReactionRepository extends JpaRepository<MessageReactionEntity, Long> {

    List<MessageReactionEntity> findByMessage_Id(Long messageId);

    Optional<MessageReactionEntity> findByMessage_IdAndUser_IdAndReaction(Long messageId, String userId, String reaction);

    boolean existsByMessage_IdAndUser_IdAndReaction(Long messageId, String userId, String reaction);

    void deleteByMessage_IdAndUser_IdAndReaction(Long messageId, String userId, String reaction);
}
