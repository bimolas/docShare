package com.subject77.docshare.modules.dm.repository;

import com.subject77.docshare.modules.dm.entities.DirectMessageReadReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DirectMessageReadReceiptRepository extends JpaRepository<DirectMessageReadReceiptEntity, Long> {

    Optional<DirectMessageReadReceiptEntity> findByMessage_IdAndUser_Id(Long messageId, String userId);

    boolean existsByMessage_IdAndUser_Id(Long messageId, String userId);

    List<DirectMessageReadReceiptEntity> findByMessage_Id(Long messageId);

    List<DirectMessageReadReceiptEntity> findByMessage_IdIn(List<Long> messageIds);

    void deleteByUser_Id(String userId);
}
