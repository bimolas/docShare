package com.subject77.docshare.modules.collaboration.dto;

import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollaborationGroupParticipantResponseDto {

    private Long id;
    private Long collaborationId;
    private Long groupId;
    private String groupName;
    private String groupDescription;
    private String groupOrgCode;
    private Instant createdAt;
}
