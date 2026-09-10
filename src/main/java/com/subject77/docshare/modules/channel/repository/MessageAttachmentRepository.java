package com.subject77.docshare.modules.channel.repository;

import com.subject77.docshare.modules.channel.entities.MessageAttachmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageAttachmentRepository extends JpaRepository<MessageAttachmentEntity, Long> {

    List<MessageAttachmentEntity> findByMessage_Id(Long messageId);

    List<MessageAttachmentEntity> findByMessage_IdIn(List<Long> messageIds);

    Optional<MessageAttachmentEntity> findByMessage_IdAndDocument_Id(Long messageId, Long documentId);

    boolean existsByMessage_IdAndDocument_Id(Long messageId, Long documentId);

    void deleteByMessage_IdAndDocument_Id(Long messageId, Long documentId);

    void deleteByMessage_Id(Long messageId);
}
