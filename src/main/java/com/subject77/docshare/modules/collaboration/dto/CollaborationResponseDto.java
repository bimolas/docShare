package com.subject77.docshare.modules.collaboration.dto;

import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationResponseDto {

    private Long id;
    private String name;
    private String ownerOrgCode;
    private String ownerOrgName;
    private String partnerOrgCode;
    private String partnerOrgName;
    private Long rootFolderId;
    private String rootFolderName;
    private String createdByUserId;
    private String createdByUserEmail;
    private CollaborationStatus status;
    private Instant startsAt;
    private Instant endsAt;
    private String acceptedByUserId;
    private String acceptedByUserEmail;
    private Instant acceptedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
