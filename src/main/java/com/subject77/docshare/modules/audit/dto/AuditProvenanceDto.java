package com.subject77.docshare.modules.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditProvenanceDto {
    private String resourceType; // "DOCUMENT", "FOLDER", "WORKSPACE"
    private Long resourceId;
    private String orgCode;
    private Long workspaceId;
    private String spaceType; // "PERSONAL", "ORGANIZATION"
    private String actorUserId;
}
