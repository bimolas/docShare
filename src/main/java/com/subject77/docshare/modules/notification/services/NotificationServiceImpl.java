package com.subject77.docshare.modules.notification.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.dto.UnreadCountResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.events.NotificationCreatedEvent;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public NotificationEntity createNotification(UserEntity recipient, UserEntity actor, NotificationType type, String title, String body, NotificationSourceType sourceType, String sourceId) {
        return createNotification(recipient, actor, type, title, body, sourceType, sourceId, null, null, null);
    }

    @Override
    @Transactional
    public NotificationEntity createNotification(UserEntity recipient, UserEntity actor, NotificationType type, String title, String body, NotificationSourceType sourceType, String sourceId, NotificationCategory category, NotificationPriority priority, String idempotencyKey) {
        if (recipient == null) {
            log.warn("Cannot create notification with null recipient");
            return null;
        }

        // Skip self-notifications (exempting ONBOARDING_COMPLETED lifecycle completion)
        if (type != NotificationType.ONBOARDING_COMPLETED && actor != null && actor.getId() != null && actor.getId().equals(recipient.getId())) {
            log.debug("Skipping self-notification for user {}", recipient.getId());
            return null;
        }

        if (category == null) {
            category = inferCategory(type);
        }
        if (priority == null) {
            priority = inferPriority(type);
        }

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<NotificationEntity> existing = notificationPersistenceService.findByIdempotencyKeyIsolated(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Notification with idempotency key {} already exists. Skipping duplicate.", idempotencyKey);
                return existing.get();
            }
        }

        NotificationEntity notification = NotificationEntity.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .category(category)
                .priority(priority)
                .title(title)
                .body(body)
                .status(NotificationStatus.UNREAD)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .idempotencyKey(idempotencyKey)
                .build();

        Optional<NotificationEntity> savedOpt = notificationPersistenceService.saveInIsolatedTransaction(notification);
        if (savedOpt.isEmpty()) {
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                log.warn("Concurrent insert encountered for idempotency key {}. Returning existing record.", idempotencyKey);
                return notificationPersistenceService.findByIdempotencyKeyIsolated(idempotencyKey).orElse(null);
            }
            return null;
        }

        NotificationEntity saved = savedOpt.get();
        log.info("Created notification ID {} (type: {}, category: {}, priority: {}) for recipient {}",
                saved.getId(), type, category, priority, recipient.getId());

        NotificationResponseDto responseDto = mapToResponseDto(saved);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new NotificationCreatedEvent(this, responseDto));
        }

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getUserNotifications(UserEntity currentUser, NotificationStatus status, NotificationSourceType sourceType, Pageable pageable) {
        return getUserNotifications(currentUser, status, sourceType, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponseDto> getUserNotifications(UserEntity currentUser, NotificationStatus status, NotificationSourceType sourceType, NotificationCategory category, Pageable pageable) {
        String recipientId = currentUser.getId();
        Page<NotificationEntity> page = notificationRepository.findFiltered(recipientId, status, sourceType, category, pageable);
        return page.map(this::mapToResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponseDto getUnreadCount(UserEntity currentUser) {
        long count = notificationRepository.countByRecipient_IdAndStatus(currentUser.getId(), NotificationStatus.UNREAD);
        return UnreadCountResponseDto.builder().unreadCount(count).build();
    }

    @Override
    @Transactional
    public NotificationResponseDto markAsRead(Long notificationId, UserEntity currentUser) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipient_Id(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID " + notificationId));

        if (notification.getStatus() != NotificationStatus.READ) {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(Instant.now());
            notification.setArchivedAt(null);
            notification = notificationRepository.save(notification);
            log.info("Marked notification {} as READ for user {}", notificationId, currentUser.getId());
        }

        return mapToResponseDto(notification);
    }

    @Override
    @Transactional
    public NotificationResponseDto markAsUnread(Long notificationId, UserEntity currentUser) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipient_Id(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID " + notificationId));

        if (notification.getStatus() == NotificationStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot mark archived notification as unread. Unarchive notification first.");
        }

        if (notification.getStatus() != NotificationStatus.UNREAD) {
            notification.setStatus(NotificationStatus.UNREAD);
            notification.setReadAt(null);
            notification = notificationRepository.save(notification);
            log.info("Marked notification {} as UNREAD for user {}", notificationId, currentUser.getId());
        }

        return mapToResponseDto(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(UserEntity currentUser) {
        int updated = notificationRepository.markAllAsRead(currentUser.getId(), Instant.now());
        log.info("Marked {} notifications as READ for user {}", updated, currentUser.getId());
    }

    @Override
    @Transactional
    public NotificationResponseDto archiveNotification(Long notificationId, UserEntity currentUser) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipient_Id(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID " + notificationId));

        if (notification.getStatus() != NotificationStatus.ARCHIVED) {
            notification.setStatus(NotificationStatus.ARCHIVED);
            notification.setArchivedAt(Instant.now());
            notification = notificationRepository.save(notification);
            log.info("Archived notification {} for user {}", notificationId, currentUser.getId());
        }

        return mapToResponseDto(notification);
    }

    @Override
    @Transactional
    public NotificationResponseDto unarchiveNotification(Long notificationId, UserEntity currentUser) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipient_Id(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID " + notificationId));

        if (notification.getStatus() == NotificationStatus.ARCHIVED) {
            notification.setStatus(NotificationStatus.READ);
            notification.setArchivedAt(null);
            if (notification.getReadAt() == null) {
                notification.setReadAt(Instant.now());
            }
            notification = notificationRepository.save(notification);
            log.info("Unarchived notification {} for user {}", notificationId, currentUser.getId());
        }

        return mapToResponseDto(notification);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId, UserEntity currentUser) {
        NotificationEntity notification = notificationRepository.findByIdAndRecipient_Id(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID " + notificationId));

        notification.setDeletedAt(Instant.now());
        notificationRepository.save(notification);
        log.info("Soft-deleted notification {} for user {}", notificationId, currentUser.getId());
    }

    private NotificationCategory inferCategory(NotificationType type) {
        if (type == null) return NotificationCategory.SYSTEM;
        return switch (type) {
            case PERMISSION_GRANTED, PERMISSION_UPDATED, PERMISSION_REVOKED, PERMISSION_NOTE_ADDED -> NotificationCategory.SHARING;
            case ACCESS_REQUEST_SUBMITTED, ACCESS_REQUEST_APPROVED, ACCESS_REQUEST_DENIED, ACCESS_REQUEST_CANCELLED -> NotificationCategory.ACCESS_REQUEST;
            case FILE_REQUEST_CREATED, FILE_REQUEST_UPLOADED, FILE_REQUEST_FULFILLED, FILE_REQUEST_CANCELLED -> NotificationCategory.FILE_REQUEST;
            case COLLABORATION_INVITE, COLLABORATION_ACCEPTED, COLLABORATION_REJECTED, COLLABORATION_EXPIRED, COLLABORATION_REVOKED,
                 CHANNEL_INVITE, CHANNEL_MENTION, CHANNEL_THREAD_REPLY, DM_RECEIVED -> NotificationCategory.COLLABORATION;
            case ORG_INVITATION_SENT, ORG_INVITATION_ACCEPTED, ORG_INVITATION_DECLINED, ORG_INVITATION_REVOKED, ONBOARDING_COMPLETED -> NotificationCategory.INVITATION;
            case ORG_MEMBER_ROLE_UPDATED, ORG_MEMBER_SUSPENDED, ORG_MEMBER_REACTIVATED, ORG_MEMBER_REMOVED -> NotificationCategory.GOVERNANCE;
            case TEAM_ADDED, TEAM_REMOVED, TEAM_MANAGER_ASSIGNED -> NotificationCategory.TEAM;
            case WORKSPACE_MANAGER_ASSIGNED, WORKSPACE_MANAGER_REMOVED -> NotificationCategory.WORKSPACE;
            case DOCUMENT_VERSION_RESTORED -> NotificationCategory.DOCUMENT;
            default -> NotificationCategory.SYSTEM;
        };
    }

    private NotificationPriority inferPriority(NotificationType type) {
        if (type == null) return NotificationPriority.MEDIUM;
        return switch (type) {
            case ORG_MEMBER_SUSPENDED, PERMISSION_REVOKED, WORKSPACE_MANAGER_REMOVED, COLLABORATION_REVOKED, TEAM_REMOVED -> NotificationPriority.HIGH;
            case ACCESS_REQUEST_SUBMITTED -> NotificationPriority.HIGH;
            case DM_RECEIVED, CHANNEL_MENTION -> NotificationPriority.HIGH;
            case DOCUMENT_VERSION_RESTORED -> NotificationPriority.MEDIUM;
            default -> NotificationPriority.MEDIUM;
        };
    }

    private NotificationResponseDto mapToResponseDto(NotificationEntity entity) {
        String actorId = entity.getActor() != null ? entity.getActor().getId() : null;
        String actorName = null;
        if (entity.getActor() != null) {
            if (entity.getActor().getFirstName() != null || entity.getActor().getLastName() != null) {
                actorName = ((entity.getActor().getFirstName() != null ? entity.getActor().getFirstName() : "") + " " +
                        (entity.getActor().getLastName() != null ? entity.getActor().getLastName() : "")).trim();
            }
            if (actorName == null || actorName.isBlank()) {
                actorName = entity.getActor().getEmail();
            }
        }

        return NotificationResponseDto.builder()
                .id(entity.getId())
                .recipientId(entity.getRecipient() != null ? entity.getRecipient().getId() : null)
                .actorId(actorId)
                .actorName(actorName)
                .type(entity.getType())
                .category(entity.getCategory())
                .priority(entity.getPriority())
                .title(entity.getTitle())
                .body(entity.getBody())
                .status(entity.getStatus())
                .sourceType(entity.getSourceType())
                .sourceId(entity.getSourceId())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .archivedAt(entity.getArchivedAt())
                .build();
    }
}

