package com.subject77.docshare.modules.dm.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserBlockResponseDto {
    private Long id;
    private ParticipantSummaryDto blockedUser;
    private Instant createdAt;
}
