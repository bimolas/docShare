package com.subject77.docshare.modules.notification.repository;

import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    @Query("SELECT n FROM NotificationEntity n WHERE n.recipient.id = :recipientId AND n.deletedAt IS NULL")
    Page<NotificationEntity> findByRecipient_Id(@Param("recipientId") String recipientId, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.recipient.id = :recipientId AND n.status = :status AND n.deletedAt IS NULL")
    Page<NotificationEntity> findByRecipient_IdAndStatus(@Param("recipientId") String recipientId, @Param("status") NotificationStatus status, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.recipient.id = :recipientId AND n.sourceType = :sourceType AND n.deletedAt IS NULL")
    Page<NotificationEntity> findByRecipient_IdAndSourceType(@Param("recipientId") String recipientId, @Param("sourceType") NotificationSourceType sourceType, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.recipient.id = :recipientId AND n.status = :status AND n.sourceType = :sourceType AND n.deletedAt IS NULL")
    Page<NotificationEntity> findByRecipient_IdAndStatusAndSourceType(@Param("recipientId") String recipientId, @Param("status") NotificationStatus status, @Param("sourceType") NotificationSourceType sourceType, Pageable pageable);

    @Query("SELECT n FROM NotificationEntity n WHERE n.recipient.id = :recipientId AND n.deletedAt IS NULL " +
           "AND (:status IS NULL OR n.status = :status) " +
           "AND (:sourceType IS NULL OR n.sourceType = :sourceType) " +
           "AND (:category IS NULL OR n.category = :category)")
    Page<NotificationEntity> findFiltered(
            @Param("recipientId") String recipientId,
            @Param("status") NotificationStatus status,
            @Param("sourceType") NotificationSourceType sourceType,
            @Param("category") NotificationCategory category,
            Pageable pageable);

    @Query("SELECT COUNT(n) FROM NotificationEntity n WHERE n.recipient.id = :recipientId AND n.status = :status AND n.deletedAt IS NULL")
    long countByRecipient_IdAndStatus(@Param("recipientId") String recipientId, @Param("status") NotificationStatus status);

    @Query("SELECT n FROM NotificationEntity n WHERE n.id = :id AND n.recipient.id = :recipientId AND n.deletedAt IS NULL")
    Optional<NotificationEntity> findByIdAndRecipient_Id(@Param("id") Long id, @Param("recipientId") String recipientId);

    Optional<NotificationEntity> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.status = 'READ', n.readAt = :readAt WHERE n.recipient.id = :recipientId AND n.status = 'UNREAD' AND n.deletedAt IS NULL")
    int markAllAsRead(@Param("recipientId") String recipientId, @Param("readAt") Instant readAt);
}

