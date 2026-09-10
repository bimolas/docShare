package com.subject77.docshare.modules.notification.services;

import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.events.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleNotificationCreated(NotificationCreatedEvent event) {
        if (event == null || event.getNotification() == null) {
            return;
        }

        NotificationResponseDto notification = event.getNotification();
        String recipientId = notification.getRecipientId();

        if (recipientId == null || recipientId.isBlank()) {
            log.warn("Skipping WebSocket notification push: recipientId is empty");
            return;
        }

        try {
            log.info("Pushing realtime WebSocket notification {} (type: {}) to user {}",
                    notification.getId(), notification.getType(), recipientId);
            messagingTemplate.convertAndSendToUser(recipientId, "/queue/notifications", notification);
            log.debug("Successfully published WebSocket notification {} to recipient {}", notification.getId(), recipientId);
        } catch (Exception e) {
            log.warn("Failed to push WebSocket notification {} to recipient {}: {}. Notification remains available via REST.",
                    notification.getId(), recipientId, e.getMessage());
        }
    }
}
