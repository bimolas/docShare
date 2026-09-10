package com.subject77.docshare.modules.communication.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelReadReceiptEvent {
    private Long channelId;
    private String userId;
    private Instant readAt;
    private List<Long> messageIds;
}
