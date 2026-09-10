package com.subject77.docshare.modules.channel.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageReactionResponseDto {
    private Long id;
    private Long messageId;
    private String userId;
    private String userName;
    private String reaction;
    private Instant createdAt;
}
