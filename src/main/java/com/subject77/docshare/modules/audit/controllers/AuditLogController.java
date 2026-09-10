package com.subject77.docshare.modules.audit.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.audit.dto.DocumentActivityLogResponse;
import com.subject77.docshare.modules.audit.dto.PermissionAuditLogResponse;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.enums.AuditCategory;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.enums.AuditViewType;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.audit.services.UnifiedAuditService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final UnifiedAuditService unifiedAuditService;

    @GetMapping("/events")
    public ResponseEntity<Page<UnifiedAuditEventResponseDto>> getUnifiedAuditEvents(
            @RequestParam("scopeType") AuditScopeType scopeType,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "teamId", required = false) Long teamId,
            @RequestParam(value = "collaborationId", required = false) Long collaborationId,
            @RequestParam(value = "includeDescendants", required = false) Boolean includeDescendants,
            @RequestParam(value = "view", required = false, defaultValue = "ALL") AuditViewType view,
            @RequestParam(value = "category", required = false) AuditCategory category,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        UnifiedAuditQueryCriteria criteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(scopeType)
                .orgCode(orgCode)
                .workspaceId(workspaceId)
                .folderId(folderId)
                .documentId(documentId)
                .teamId(teamId)
                .collaborationId(collaborationId)
                .includeDescendants(includeDescendants)
                .view(view != null ? view : AuditViewType.ALL)
                .category(category)
                .action(action)
                .actor(actor)
                .search(search)
                .from(from)
                .to(to)
                .build();
        Page<UnifiedAuditEventResponseDto> page = unifiedAuditService.queryEvents(criteria, pageable, currentUser);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/activity")
    public ResponseEntity<Page<DocumentActivityLogResponse>> getDocumentActivityLogs(
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) Action action,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "resourceId", required = false) Long resourceId,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "collaborationId", required = false) Long collaborationId,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        Page<DocumentActivityLogResponse> logs = auditLogService.queryActivityLogs(
                documentId, folderId, workspaceId, orgCode, actor, action, resourceType, resourceId, groupId, collaborationId, from, to, pageable, currentUser);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/governance")
    public ResponseEntity<Page<PermissionAuditLogResponse>> getGovernanceLogs(
            @RequestParam(value = "targetId", required = false) Long targetId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "collaborationId", required = false) Long collaborationId,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        Page<PermissionAuditLogResponse> logs = auditLogService.queryGovernanceLogs(
                targetId, targetType, folderId, workspaceId, orgCode, actor, action, groupId, collaborationId, from, to, pageable, currentUser);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/permissions")
    public ResponseEntity<Page<PermissionAuditLogResponse>> getPermissionAuditLogs(
            @RequestParam(value = "targetId", required = false) Long targetId,
            @RequestParam(value = "targetType", required = false) String targetType,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "actor", required = false) String actor,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "collaborationId", required = false) Long collaborationId,
            @RequestParam(value = "from", required = false) Instant from,
            @RequestParam(value = "to", required = false) Instant to,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        return getGovernanceLogs(targetId, targetType, folderId, workspaceId, orgCode, actor, action, groupId, collaborationId, from, to, pageable, currentUser);
    }
}
