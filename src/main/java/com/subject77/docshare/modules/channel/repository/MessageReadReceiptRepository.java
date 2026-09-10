package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.MessageReadReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceiptEntity, Long> {

    Optional<MessageReadReceiptEntity> findByMessage_IdAndUser_Id(Long messageId, String userId);

    boolean existsByMessage_IdAndUser_Id(Long messageId, String userId);

    List<MessageReadReceiptEntity> findByMessage_Id(Long messageId);

    List<MessageReadReceiptEntity> findByMessage_IdIn(List<Long> messageIds);

    long countByMessage_Id(Long messageId);
}
