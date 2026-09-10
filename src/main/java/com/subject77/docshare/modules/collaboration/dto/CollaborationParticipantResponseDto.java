package com.subject77.docshare.modules.collaboration.dto;

import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationParticipantResponseDto {

    private Long id;
    private Long collaborationId;
    private String userId;
    private String userEmail;
    private String userFirstName;
    private String userLastName;
    private String userOrgCode;
    private CollaborationRole collaborationRole;
    private Instant createdAt;
}
