package com.subject77.docshare.modules.notification;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.dto.UnreadCountResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class NotificationModelAndIdempotencyIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity recipient;
    private UserEntity actor;

    @BeforeEach
    void setUp() {
        String recipUuid = UUID.randomUUID().toString();
        recipient = userRepository.save(UserEntity.builder()
                .id(recipUuid)
                .email("noti_recip_" + recipUuid.substring(0, 8) + "@example.com")
                .firstName("Recipient")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        String actorUuid = UUID.randomUUID().toString();
        actor = userRepository.save(UserEntity.builder()
                .id(actorUuid)
                .email("noti_actor_" + actorUuid.substring(0, 8) + "@example.com")
                .firstName("Actor")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        if (recipient != null) userRepository.deleteById(recipient.getId());
        if (actor != null) userRepository.deleteById(actor.getId());
    }

    @Test
    @DisplayName("6B.4: Lifecycle state transitions UNREAD -> READ -> UNREAD -> ARCHIVED -> READ (unarchive)")
    void testLifecycleTransitions() {
        NotificationEntity noti = notificationService.createNotification(
                recipient, actor, NotificationType.PERMISSION_GRANTED,
                "Permission Granted", "You received view access",
                NotificationSourceType.PERMISSION, "perm-123"
        );

        assertNotNull(noti);
        assertEquals(NotificationStatus.UNREAD, noti.getStatus());
        assertNull(noti.getReadAt());
        assertNull(noti.getArchivedAt());
        assertNull(noti.getDeletedAt());

        // 1. UNREAD -> READ
        NotificationResponseDto readDto = notificationService.markAsRead(noti.getId(), recipient);
        assertEquals(NotificationStatus.READ, readDto.getStatus());
        assertNotNull(readDto.getReadAt());
        assertNull(readDto.getArchivedAt());

        // 2. READ -> UNREAD
        NotificationResponseDto unreadDto = notificationService.markAsUnread(noti.getId(), recipient);
        assertEquals(NotificationStatus.UNREAD, unreadDto.getStatus());
        assertNull(unreadDto.getReadAt());

        // 3. UNREAD -> ARCHIVED
        NotificationResponseDto archivedDto = notificationService.archiveNotification(noti.getId(), recipient);
        assertEquals(NotificationStatus.ARCHIVED, archivedDto.getStatus());
        assertNotNull(archivedDto.getArchivedAt());

        // 4. ARCHIVED -> UNREAD (invalid: must reject)
        assertThrows(IllegalStateException.class, () ->
                notificationService.markAsUnread(noti.getId(), recipient)
        );

        // 5. ARCHIVED -> READ (unarchive)
        NotificationResponseDto unarchivedDto = notificationService.unarchiveNotification(noti.getId(), recipient);
        assertEquals(NotificationStatus.READ, unarchivedDto.getStatus());
        assertNull(unarchivedDto.getArchivedAt());
        assertNotNull(unarchivedDto.getReadAt());
    }

    @Test
    @DisplayName("6B.4: Soft deletion excludes notification from unread count, listings, and transitions")
    void testSoftDeletionAndExclusion() {
        NotificationEntity noti1 = notificationService.createNotification(
                recipient, actor, NotificationType.PERMISSION_GRANTED,
                "Notification 1", "Body 1",
                NotificationSourceType.PERMISSION, "perm-1"
        );
        NotificationEntity noti2 = notificationService.createNotification(
                recipient, actor, NotificationType.FILE_REQUEST_CREATED,
                "Notification 2", "Body 2",
                NotificationSourceType.FILE_REQUEST, "freq-2"
        );

        // Mark noti2 as READ, so noti1 is UNREAD and noti2 is READ
        notificationService.markAsRead(noti2.getId(), recipient);

        UnreadCountResponseDto countDto = notificationService.getUnreadCount(recipient);
        assertEquals(1, countDto.getUnreadCount());

        Page<NotificationResponseDto> listBefore = notificationService.getUserNotifications(
                recipient, null, null, PageRequest.of(0, 20)
        );
        assertEquals(2, listBefore.getTotalElements());

        // Soft delete noti1
        notificationService.deleteNotification(noti1.getId(), recipient);

        // Unread count must exclude deleted notifications
        UnreadCountResponseDto countAfter = notificationService.getUnreadCount(recipient);
        assertEquals(0, countAfter.getUnreadCount());

        // Listing must exclude deleted notifications
        Page<NotificationResponseDto> listAfter = notificationService.getUserNotifications(
                recipient, null, null, PageRequest.of(0, 20)
        );
        assertEquals(1, listAfter.getTotalElements());
        assertEquals(noti2.getId(), listAfter.getContent().get(0).getId());

        // Attempting to act on soft-deleted notification must throw ResourceNotFoundException
        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.markAsRead(noti1.getId(), recipient)
        );
        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.archiveNotification(noti1.getId(), recipient)
        );
        assertThrows(ResourceNotFoundException.class, () ->
                notificationService.deleteNotification(noti1.getId(), recipient)
        );
    }

    @Test
    @DisplayName("6B.4: Idempotent retry with stable eventId + recipientId prevents duplicates")
    void testIdempotentRetry() {
        String eventId = "ACCESS_REQUEST_SUBMITTED:777";
        String idempotencyKey = eventId + ":" + recipient.getId();

        NotificationEntity noti1 = notificationService.createNotification(
                recipient, actor, NotificationType.ACCESS_REQUEST_SUBMITTED,
                "Access Requested", "User requested access to document",
                NotificationSourceType.ACCESS_REQUEST, "777",
                NotificationCategory.ACCESS_REQUEST, NotificationPriority.HIGH,
                idempotencyKey
        );
        assertNotNull(noti1);

        // Retry with same idempotency key
        NotificationEntity noti2 = notificationService.createNotification(
                recipient, actor, NotificationType.ACCESS_REQUEST_SUBMITTED,
                "Access Requested", "User requested access to document",
                NotificationSourceType.ACCESS_REQUEST, "777",
                NotificationCategory.ACCESS_REQUEST, NotificationPriority.HIGH,
                idempotencyKey
        );
        assertNotNull(noti2);

        // Must return the exact same entity and not create duplicates
        assertEquals(noti1.getId(), noti2.getId());

        long count = notificationRepository.findAll().stream()
                .filter(n -> idempotencyKey.equals(n.getIdempotencyKey()))
                .count();
        assertEquals(1, count);
    }

    @Test
    @DisplayName("6B.4: Distinct event types on the same source resource remain distinct")
    void testDistinctEventTypesOnSameSource() {
        String sourceId = "req-999";
        String submittedKey = "ACCESS_REQUEST_SUBMITTED:" + sourceId + ":" + recipient.getId();
        String approvedKey = "ACCESS_REQUEST_APPROVED:" + sourceId + ":" + recipient.getId();

        NotificationEntity notiSubmitted = notificationService.createNotification(
                recipient, actor, NotificationType.ACCESS_REQUEST_SUBMITTED,
                "Request Submitted", "Your access request was submitted",
                NotificationSourceType.ACCESS_REQUEST, sourceId,
                NotificationCategory.ACCESS_REQUEST, NotificationPriority.HIGH,
                submittedKey
        );

        NotificationEntity notiApproved = notificationService.createNotification(
                recipient, actor, NotificationType.ACCESS_REQUEST_APPROVED,
                "Request Approved", "Your access request was approved",
                NotificationSourceType.ACCESS_REQUEST, sourceId,
                NotificationCategory.ACCESS_REQUEST, NotificationPriority.MEDIUM,
                approvedKey
        );

        assertNotNull(notiSubmitted);
        assertNotNull(notiApproved);
        assertNotEquals(notiSubmitted.getId(), notiApproved.getId());
        assertEquals(NotificationType.ACCESS_REQUEST_SUBMITTED, notiSubmitted.getType());
        assertEquals(NotificationType.ACCESS_REQUEST_APPROVED, notiApproved.getType());
    }

    @Test
    @DisplayName("6B.4: Category and priority defaults and explicit overrides are correctly persisted")
    void testCategoryAndPriorityInferenceAndPersistence() {
        // Inferred defaults
        NotificationEntity inferred = notificationService.createNotification(
                recipient, actor, NotificationType.ORG_MEMBER_SUSPENDED,
                "Suspended", "Account suspended",
                NotificationSourceType.ORGANIZATION, "org-1"
        );
        assertEquals(NotificationCategory.GOVERNANCE, inferred.getCategory());
        assertEquals(NotificationPriority.HIGH, inferred.getPriority());

        // Explicit override
        NotificationEntity explicit = notificationService.createNotification(
                recipient, actor, NotificationType.PERMISSION_GRANTED,
                "Security Notice", "Critical security alert",
                NotificationSourceType.PERMISSION, "sec-1",
                NotificationCategory.SECURITY, NotificationPriority.URGENT,
                "SEC-101:" + recipient.getId()
        );
        assertEquals(NotificationCategory.SECURITY, explicit.getCategory());
        assertEquals(NotificationPriority.URGENT, explicit.getPriority());
    }
}
