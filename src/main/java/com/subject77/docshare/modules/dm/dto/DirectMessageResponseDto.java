package com.subject77.docshare.modules.dm.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectMessageResponseDto {
    private Long id;
    private Long threadId;
    private ParticipantSummaryDto sender;
    private String content;
    private boolean edited;
    private boolean deleted;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private boolean readByRecipient;
    private boolean readByCurrentUser;
    private Instant readAt;
    private List<DirectMessageReadReceiptDto> readReceipts;
}
