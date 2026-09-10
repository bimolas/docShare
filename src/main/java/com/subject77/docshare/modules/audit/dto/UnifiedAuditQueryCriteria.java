package com.subject77.docshare.modules.audit.dto;

import com.subject77.docshare.modules.audit.enums.AuditCategory;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.enums.AuditViewType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedAuditQueryCriteria {

    // 1. Scope definition
    private AuditScopeType scopeType;
    private String orgCode;
    private Long workspaceId;
    private Long folderId;
    private Long documentId;
    private Long teamId;
    private Long collaborationId;
    private Boolean includeDescendants;

    // 2. Narrowing filters
    @Builder.Default
    private AuditViewType view = AuditViewType.ALL;
    private AuditCategory category;
    private String action;
    private String actor;
    private String search;
    private Instant from;
    private Instant to;
}
