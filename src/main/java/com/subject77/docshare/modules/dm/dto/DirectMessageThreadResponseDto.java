package com.subject77.docshare.modules.dm.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageThreadResponseDto {
    private Long id;
    private ParticipantSummaryDto participant;
    private DirectMessageResponseDto lastMessage;
    private long unreadCount;
    private Instant lastMessageAt;
    private Instant createdAt;
}
