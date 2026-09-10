package com.subject77.docshare.modules.channel.dto;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponseDto {
    private Long id;
    private Long channelId;
    private String authorId;
    private String authorName;
    private String authorEmail;
    private String content;
    private Long parentMessageId;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;
    private boolean edited;
    private boolean deleted;
    private List<MessageReactionResponseDto> reactions;
    private List<MessageAttachmentResponseDto> attachments;
    private long replyCount;
    private long seenCount;
    private List<String> seenBy;
    private List<MessageReadReceiptDto> readReceipts;
    private boolean readByCurrentUser;
}

