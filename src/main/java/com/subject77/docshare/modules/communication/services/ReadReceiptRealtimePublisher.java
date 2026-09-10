package com.subject77.docshare.modules.communication.services;

import com.subject77.docshare.modules.communication.dto.ReadReceiptRealtimeEventDto;
import com.subject77.docshare.modules.communication.events.ChannelReadReceiptEvent;
import com.subject77.docshare.modules.communication.events.DirectMessageReadReceiptEvent;
import com.subject77.docshare.modules.dm.entities.UserMessagePrivacySettingsEntity;
import com.subject77.docshare.modules.dm.repository.UserMessagePrivacySettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserMessagePrivacySettingsRepository privacySettingsRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleChannelReadReceipt(ChannelReadReceiptEvent event) {
        if (event == null || event.getChannelId() == null || event.getUserId() == null) {
            return;
        }

        try {
            ReadReceiptRealtimeEventDto payload = ReadReceiptRealtimeEventDto.builder()
                    .conversationType("CHANNEL")
                    .conversationId(event.getChannelId())
                    .userId(event.getUserId())
                    .readAt(event.getReadAt())
                    .build();

            String destination = "/topic/channels/" + event.getChannelId() + "/read";
            log.info("Publishing channel read receipt for channel {} user {}", event.getChannelId(), event.getUserId());
            messagingTemplate.convertAndSend(destination, payload);
        } catch (Exception e) {
            log.warn("Failed to publish real-time channel read receipt for channel {}: {}", event.getChannelId(), e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleDirectMessageReadReceipt(DirectMessageReadReceiptEvent event) {
        if (event == null || event.getThreadId() == null || event.getReaderUserId() == null || event.getRecipientUserId() == null) {
            return;
        }

        boolean readReceiptsEnabled = privacySettingsRepository.findByUser_Id(event.getReaderUserId())
                .map(UserMessagePrivacySettingsEntity::isReadReceiptsEnabled)
                .orElse(true);

        if (!readReceiptsEnabled) {
            log.info("Suppressing DM real-time read receipt delivery: reader {} has read receipts disabled", event.getReaderUserId());
            return;
        }

        try {
            ReadReceiptRealtimeEventDto payload = ReadReceiptRealtimeEventDto.builder()
                    .conversationType("DIRECT_MESSAGE")
                    .conversationId(event.getThreadId())
                    .userId(event.getReaderUserId())
                    .readAt(event.getReadAt())
                    .build();

            log.info("Publishing DM real-time read receipt for thread {} to user {}", event.getThreadId(), event.getRecipientUserId());
            messagingTemplate.convertAndSendToUser(event.getRecipientUserId(), "/queue/dm/read", payload);
        } catch (Exception e) {
            log.warn("Failed to publish real-time DM read receipt for thread {}: {}", event.getThreadId(), e.getMessage());
        }
    }
}
