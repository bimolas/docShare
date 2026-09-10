package com.subject77.docshare.modules.communication.services;

import com.subject77.docshare.modules.communication.dto.ReadReceiptRealtimeEventDto;
import com.subject77.docshare.modules.communication.events.ChannelReadReceiptEvent;
import com.subject77.docshare.modules.communication.events.DirectMessageReadReceiptEvent;
import com.subject77.docshare.modules.dm.entities.UserMessagePrivacySettingsEntity;
import com.subject77.docshare.modules.dm.repository.UserMessagePrivacySettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReadReceiptRealtimePublisherTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserMessagePrivacySettingsRepository privacySettingsRepository;

    @InjectMocks
    private ReadReceiptRealtimePublisher publisher;

    @Test
    @DisplayName("handleChannelReadReceipt should publish event payload to channel read topic")
    void handleChannelReadReceipt_ShouldPublishToTopic() {
        Instant now = Instant.now();
        ChannelReadReceiptEvent event = ChannelReadReceiptEvent.builder()
                .channelId(100L)
                .userId("usr-1")
                .readAt(now)
                .build();

        publisher.handleChannelReadReceipt(event);

        ArgumentCaptor<ReadReceiptRealtimeEventDto> captor = ArgumentCaptor.forClass(ReadReceiptRealtimeEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/channels/100/read"), captor.capture());

        ReadReceiptRealtimeEventDto payload = captor.getValue();
        assertThat(payload.getConversationType()).isEqualTo("CHANNEL");
        assertThat(payload.getConversationId()).isEqualTo(100L);
        assertThat(payload.getUserId()).isEqualTo("usr-1");
        assertThat(payload.getReadAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("handleDirectMessageReadReceipt when read receipts enabled should send payload to user queue")
    void handleDirectMessageReadReceipt_Enabled_ShouldSendToUser() {
        Instant now = Instant.now();
        DirectMessageReadReceiptEvent event = DirectMessageReadReceiptEvent.builder()
                .threadId(200L)
                .readerUserId("reader-1")
                .recipientUserId("recipient-2")
                .readAt(now)
                .build();

        UserMessagePrivacySettingsEntity settings = UserMessagePrivacySettingsEntity.builder()
                .readReceiptsEnabled(true)
                .build();
        when(privacySettingsRepository.findByUser_Id("reader-1")).thenReturn(Optional.of(settings));

        publisher.handleDirectMessageReadReceipt(event);

        ArgumentCaptor<ReadReceiptRealtimeEventDto> captor = ArgumentCaptor.forClass(ReadReceiptRealtimeEventDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("recipient-2"), eq("/queue/dm/read"), captor.capture());

        ReadReceiptRealtimeEventDto payload = captor.getValue();
        assertThat(payload.getConversationType()).isEqualTo("DIRECT_MESSAGE");
        assertThat(payload.getConversationId()).isEqualTo(200L);
        assertThat(payload.getUserId()).isEqualTo("reader-1");
        assertThat(payload.getReadAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("handleDirectMessageReadReceipt when reader disabled read receipts should suppress event delivery")
    void handleDirectMessageReadReceipt_Disabled_ShouldSuppress() {
        Instant now = Instant.now();
        DirectMessageReadReceiptEvent event = DirectMessageReadReceiptEvent.builder()
                .threadId(200L)
                .readerUserId("reader-1")
                .recipientUserId("recipient-2")
                .readAt(now)
                .build();

        UserMessagePrivacySettingsEntity settings = UserMessagePrivacySettingsEntity.builder()
                .readReceiptsEnabled(false)
                .build();
        when(privacySettingsRepository.findByUser_Id("reader-1")).thenReturn(Optional.of(settings));

        publisher.handleDirectMessageReadReceipt(event);

        verifyNoInteractions(messagingTemplate);
    }
}
