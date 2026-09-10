package com.subject77.docshare.modules.notification.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.dto.UnreadCountResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.springframework.context.ApplicationEventPublisher;
import com.subject77.docshare.modules.notification.events.NotificationCreatedEvent;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPersistenceService notificationPersistenceService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private UserEntity recipient;
    private UserEntity actor;

    @BeforeEach
    void setUp() {
        recipient = UserEntity.builder()
                .id("usr-recipient-1")
                .email("recipient@example.com")
                .firstName("John")
                .lastName("Recipient")
                .role(Role.ROLE_USER)
                .build();

        actor = UserEntity.builder()
                .id("usr-actor-1")
                .email("actor@example.com")
                .firstName("Jane")
                .lastName("Actor")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("createNotification should save notification when recipient and actor are different")
    void createNotification_ShouldSaveNotification() {
        NotificationEntity savedEntity = NotificationEntity.builder()
                .id(100L)
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.PERMISSION_GRANTED)
                .title("Access Granted")
                .body("You were granted access")
                .status(NotificationStatus.UNREAD)
                .sourceType(NotificationSourceType.PERMISSION)
                .sourceId("perm-1")
                .createdAt(Instant.now())
                .build();

        when(notificationPersistenceService.saveInIsolatedTransaction(any(NotificationEntity.class))).thenReturn(Optional.of(savedEntity));

        NotificationEntity result = notificationService.createNotification(
                recipient, actor, NotificationType.PERMISSION_GRANTED,
                "Access Granted", "You were granted access",
                NotificationSourceType.PERMISSION, "perm-1"
        );

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getRecipient().getId()).isEqualTo("usr-recipient-1");

        ArgumentCaptor<NotificationEntity> captor = ArgumentCaptor.forClass(NotificationEntity.class);
        verify(notificationPersistenceService).saveInIsolatedTransaction(captor.capture());
        NotificationEntity captured = captor.getValue();
        assertThat(captured.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(captured.getType()).isEqualTo(NotificationType.PERMISSION_GRANTED);

        verify(eventPublisher).publishEvent(any(NotificationCreatedEvent.class));
    }

    @Test
    @DisplayName("createNotification should return null when recipient is null")
    void createNotification_ShouldReturnNullWhenRecipientIsNull() {
        NotificationEntity result = notificationService.createNotification(
                null, actor, NotificationType.PERMISSION_GRANTED,
                "Title", "Body", NotificationSourceType.PERMISSION, "perm-1"
        );

        assertThat(result).isNull();
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(notificationPersistenceService);
    }

    @Test
    @DisplayName("createNotification should suppress self-notification when actor is same as recipient")
    void createNotification_ShouldSuppressSelfNotification() {
        NotificationEntity result = notificationService.createNotification(
                recipient, recipient, NotificationType.PERMISSION_GRANTED,
                "Title", "Body", NotificationSourceType.PERMISSION, "perm-1"
        );

        assertThat(result).isNull();
        verifyNoInteractions(notificationRepository);
        verifyNoInteractions(notificationPersistenceService);
    }

    @Test
    @DisplayName("getUserNotifications should filter by status, sourceType, and category when provided")
    void getUserNotifications_ShouldFilterByStatusAndSourceType() {
        Pageable pageable = PageRequest.of(0, 10);
        NotificationEntity entity = NotificationEntity.builder()
                .id(1L)
                .recipient(recipient)
                .actor(actor)
                .type(NotificationType.DM_RECEIVED)
                .category(NotificationCategory.COLLABORATION)
                .title("New Message")
                .body("Hello!")
                .status(NotificationStatus.UNREAD)
                .sourceType(NotificationSourceType.DM)
                .build();

        Page<NotificationEntity> page = new PageImpl<>(List.of(entity));
        when(notificationRepository.findFiltered(
                eq("usr-recipient-1"), eq(NotificationStatus.UNREAD), eq(NotificationSourceType.DM), isNull(), eq(pageable)
        )).thenReturn(page);

        Page<NotificationResponseDto> result = notificationService.getUserNotifications(
                recipient, NotificationStatus.UNREAD, NotificationSourceType.DM, pageable
        );

        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("New Message");
        assertThat(result.getContent().get(0).getActorName()).isEqualTo("Jane Actor");
    }

    @Test
    @DisplayName("getUserNotifications with category should call findFiltered with category")
    void getUserNotifications_WithCategory_ShouldFilterByCategory() {
        Pageable pageable = PageRequest.of(0, 10);
        when(notificationRepository.findFiltered(
                eq("usr-recipient-1"), isNull(), isNull(), eq(NotificationCategory.TEAM), eq(pageable)
        )).thenReturn(Page.empty());

        Page<NotificationResponseDto> result = notificationService.getUserNotifications(
                recipient, null, null, NotificationCategory.TEAM, pageable
        );

        assertThat(result).isEmpty();
        verify(notificationRepository).findFiltered(eq("usr-recipient-1"), isNull(), isNull(), eq(NotificationCategory.TEAM), eq(pageable));
    }

    @Test
    @DisplayName("getUnreadCount should return unread notification count")
    void getUnreadCount_ShouldReturnCount() {
        when(notificationRepository.countByRecipient_IdAndStatus("usr-recipient-1", NotificationStatus.UNREAD))
                .thenReturn(5L);

        UnreadCountResponseDto result = notificationService.getUnreadCount(recipient);

        assertThat(result.getUnreadCount()).isEqualTo(5L);
    }

    @Test
    @DisplayName("markAsRead should update status to READ and set readAt timestamp")
    void markAsRead_ShouldUpdateStatus() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(10L)
                .recipient(recipient)
                .actor(actor)
                .status(NotificationStatus.UNREAD)
                .build();

        when(notificationRepository.findByIdAndRecipient_Id(10L, "usr-recipient-1"))
                .thenReturn(Optional.of(entity));
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto result = notificationService.markAsRead(10L, recipient);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(result.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsRead should throw ResourceNotFoundException when notification not found")
    void markAsRead_ShouldThrowExceptionWhenNotFound() {
        when(notificationRepository.findByIdAndRecipient_Id(999L, "usr-recipient-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L, recipient))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("markAllAsRead should bulk update unread notifications")
    void markAllAsRead_ShouldCallRepository() {
        when(notificationRepository.markAllAsRead(eq("usr-recipient-1"), any(Instant.class)))
                .thenReturn(3);

        notificationService.markAllAsRead(recipient);

        verify(notificationRepository).markAllAsRead(eq("usr-recipient-1"), any(Instant.class));
    }

    @Test
    @DisplayName("archiveNotification should set status to ARCHIVED")
    void archiveNotification_ShouldUpdateStatus() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(10L)
                .recipient(recipient)
                .actor(actor)
                .status(NotificationStatus.UNREAD)
                .build();

        when(notificationRepository.findByIdAndRecipient_Id(10L, "usr-recipient-1"))
                .thenReturn(Optional.of(entity));
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto result = notificationService.archiveNotification(10L, recipient);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.ARCHIVED);
    }

    @Test
    @DisplayName("deleteNotification should soft delete notification entity")
    void deleteNotification_ShouldSoftDeleteEntity() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(10L)
                .recipient(recipient)
                .build();

        when(notificationRepository.findByIdAndRecipient_Id(10L, "usr-recipient-1"))
                .thenReturn(Optional.of(entity));

        notificationService.deleteNotification(10L, recipient);

        verify(notificationRepository).save(entity);
        assertThat(entity.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsUnread should set status to UNREAD and clear readAt")
    void markAsUnread_ShouldSetStatusToUnread() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(10L)
                .recipient(recipient)
                .status(NotificationStatus.READ)
                .readAt(Instant.now())
                .build();

        when(notificationRepository.findByIdAndRecipient_Id(10L, "usr-recipient-1"))
                .thenReturn(Optional.of(entity));
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto result = notificationService.markAsUnread(10L, recipient);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(result.getReadAt()).isNull();
    }

    @Test
    @DisplayName("markAsUnread should reject transition from ARCHIVED to UNREAD")
    void markAsUnread_ShouldRejectArchived() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(10L)
                .recipient(recipient)
                .status(NotificationStatus.ARCHIVED)
                .archivedAt(Instant.now())
                .build();

        when(notificationRepository.findByIdAndRecipient_Id(10L, "usr-recipient-1"))
                .thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> notificationService.markAsUnread(10L, recipient))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot mark archived notification as unread");
    }

    @Test
    @DisplayName("unarchiveNotification should restore ARCHIVED notification to READ")
    void unarchiveNotification_ShouldRestoreToRead() {
        NotificationEntity entity = NotificationEntity.builder()
                .id(10L)
                .recipient(recipient)
                .status(NotificationStatus.ARCHIVED)
                .archivedAt(Instant.now())
                .build();

        when(notificationRepository.findByIdAndRecipient_Id(10L, "usr-recipient-1"))
                .thenReturn(Optional.of(entity));
        when(notificationRepository.save(any(NotificationEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        NotificationResponseDto result = notificationService.unarchiveNotification(10L, recipient);

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(result.getArchivedAt()).isNull();
    }

    @Test
    @DisplayName("createNotification with idempotency key should return existing notification if key exists")
    void createNotification_Idempotency_ShouldReturnExisting() {
        String key = "EVENT-123:usr-recipient-1";
        NotificationEntity existing = NotificationEntity.builder()
                .id(50L)
                .recipient(recipient)
                .idempotencyKey(key)
                .status(NotificationStatus.UNREAD)
                .build();

        when(notificationPersistenceService.findByIdempotencyKeyIsolated(key)).thenReturn(Optional.of(existing));

        NotificationEntity result = notificationService.createNotification(
                recipient, actor, NotificationType.PERMISSION_GRANTED,
                "Title", "Body", NotificationSourceType.PERMISSION, "perm-1",
                null, null, key
        );

        assertThat(result).isSameAs(existing);
        verify(notificationPersistenceService, never()).saveInIsolatedTransaction(any());
    }
}
