package com.subject77.docshare.modules.dm.repository;

import com.subject77.docshare.modules.dm.entities.DirectMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessageEntity, Long> {

    Page<DirectMessageEntity> findByThread_IdOrderByCreatedAtDesc(Long threadId, Pageable pageable);

    java.util.List<DirectMessageEntity> findByThread_Id(Long threadId);

    Optional<DirectMessageEntity> findTopByThread_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long threadId);

    Optional<DirectMessageEntity> findTopByThread_IdOrderByCreatedAtDesc(Long threadId);

    long countByThread_IdAndCreatedAtAfterAndDeletedAtIsNullAndSender_IdNot(Long threadId, Instant lastReadAt, String currentUserId);

    long countByThread_IdAndDeletedAtIsNullAndSender_IdNot(Long threadId, String currentUserId);
}
