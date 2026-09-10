package com.subject77.docshare.modules.notification.services;

import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.events.NotificationCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationRealtimePublisher publisher;

    private NotificationResponseDto dto;

    @BeforeEach
    void setUp() {
        dto = NotificationResponseDto.builder()
                .id(1L)
                .recipientId("usr-recipient-1")
                .actorId("usr-actor-1")
                .type(NotificationType.PERMISSION_GRANTED)
                .title("Access Granted")
                .body("You have been granted access")
                .status(NotificationStatus.UNREAD)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("handleNotificationCreated should send message to recipient queue")
    void handleNotificationCreated_ShouldSendMessageToUser() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(dto);

        publisher.handleNotificationCreated(event);

        verify(messagingTemplate).convertAndSendToUser(
                eq("usr-recipient-1"),
                eq("/queue/notifications"),
                eq(dto)
        );
    }

    @Test
    @DisplayName("handleNotificationCreated should do nothing when event or payload is null")
    void handleNotificationCreated_NullEvent_ShouldDoNothing() {
        publisher.handleNotificationCreated(null);
        publisher.handleNotificationCreated(new NotificationCreatedEvent(null));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("handleNotificationCreated should do nothing when recipientId is empty")
    void handleNotificationCreated_EmptyRecipientId_ShouldDoNothing() {
        NotificationResponseDto invalidDto = NotificationResponseDto.builder()
                .id(2L)
                .recipientId("")
                .type(NotificationType.DM_RECEIVED)
                .build();

        publisher.handleNotificationCreated(new NotificationCreatedEvent(invalidDto));

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("handleNotificationCreated should handle messaging exception gracefully without rethrowing")
    void handleNotificationCreated_MessagingException_ShouldCatchAndLog() {
        NotificationCreatedEvent event = new NotificationCreatedEvent(dto);

        doThrow(new MessagingException("Broker unavailable"))
                .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());

        // Should not throw exception
        publisher.handleNotificationCreated(event);

        verify(messagingTemplate).convertAndSendToUser(eq("usr-recipient-1"), eq("/queue/notifications"), eq(dto));
    }
}
