package com.subject77.docshare.modules.communication.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReadReceiptRealtimeEventDto {
    private Long messageId;
    private String conversationType; // "CHANNEL" or "DIRECT_MESSAGE"
    private Long conversationId;     // channelId or threadId
    private String userId;
    private Instant readAt;
    private Long seenCount;
    private Boolean readByCurrentUser;
}
