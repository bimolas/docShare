package com.subject77.docshare.modules.audit.dto;

import com.subject77.docshare.modules.audit.entities.Action;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentActivityLogResponse {
    private Long id;
    private Action action;
    private Instant timestamp;
    private Long documentId;
    private String documentFileHash;
    private Long folderId;
    private String accessorEmail;
    private String orgCode;
    private String ipAddress;
    private String userAgent;
    private String details;
    private String txHash;
    private String actorUserId;
    private Long workspaceId;
    private String resourceType;
    private Long resourceId;
    private Long groupId;
    private Long collaborationId;
}
