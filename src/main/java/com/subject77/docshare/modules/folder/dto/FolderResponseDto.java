package com.subject77.docshare.modules.folder.dto;

import lombok.*;
import java.time.Instant;

import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderResponseDto {
    private Long id;
    private String name;
    private Long parentFolderId;
    private String ownerOrgCode;
    private String createdByUserId;
    private Instant createdAt;
    private SubspaceType subspaceType;
    @JsonProperty("isDefaultSubspace")
    private boolean isDefaultSubspace;
    private String accessStatus;
    private Boolean isLocked;
    private String requestStatus;
    private Boolean canRequestAccess;
    private Long pendingRequestId;
}
