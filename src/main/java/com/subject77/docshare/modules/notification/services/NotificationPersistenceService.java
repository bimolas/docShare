package com.subject77.docshare.modules.notification.services;

import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Isolates physical notification persistence in an autonomous transaction boundary (REQUIRES_NEW).
 * This ensures that database unique constraint violations on idempotency keys do not mark the caller's
 * outer business transaction as rollback-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationEntity> saveInIsolatedTransaction(NotificationEntity notification) {
        try {
            if (notification.getRecipient() != null && notification.getRecipient().getId() != null) {
                UserEntity r = entityManager.find(UserEntity.class, notification.getRecipient().getId());
                if (r == null) {
                    log.warn("Recipient user {} does not exist in the database (or outer transaction uncommitted). Skipping isolated notification save.",
                            notification.getRecipient().getId());
                    return Optional.empty();
                }
                notification.setRecipient(r);
            }
            if (notification.getActor() != null && notification.getActor().getId() != null) {
                UserEntity a = entityManager.find(UserEntity.class, notification.getActor().getId());
                notification.setActor(a);
            }
            return Optional.of(notificationRepository.saveAndFlush(notification));
        } catch (DataIntegrityViolationException ex) {
            log.warn("Isolated notification insert encountered constraint collision for key {}: {}",
                    notification.getIdempotencyKey(), ex.getMessage());
            return Optional.empty();
        }
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<NotificationEntity> findByIdempotencyKeyIsolated(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return notificationRepository.findByIdempotencyKey(idempotencyKey);
    }
}
