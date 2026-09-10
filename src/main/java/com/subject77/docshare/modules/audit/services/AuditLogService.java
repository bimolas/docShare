package com.subject77.docshare.modules.audit.services;

import com.subject77.docshare.modules.audit.dto.DocumentActivityLogResponse;
import com.subject77.docshare.modules.audit.dto.PermissionAuditLogResponse;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.time.Instant;
import java.util.List;

public interface AuditLogService {

    void logDocumentActivity(Action action, Long documentId, String fileHash, Long folderId, String accessorEmail, String orgCode, String ipAddress, String userAgent, String details, String txHash);

    void logDocumentActivity(Action action, Long documentId, String fileHash, Long folderId, String actorUserId, String accessorEmail, String orgCode, String ipAddress, String userAgent, String details, String txHash);

    void logDocumentActivity(Action action, Long documentId, String fileHash, Long folderId, Long workspaceId, String actorUserId, String accessorEmail, String orgCode, String ipAddress, String userAgent, String details, String txHash);

    void logPermissionChange(com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand command);

    void logPermissionChange(String action, String targetType, Long targetId, String fileHash, Long folderId, SpaceType spaceType, String orgCode, UserEntity grantedBy, String grantedToEmail, String grantedToOrgCode, PermissionLevel level, Instant validFrom, Instant expiresAt);

    void logPermissionChange(String action, String targetType, Long targetId, String fileHash, Long folderId, SpaceType spaceType, String orgCode, UserEntity grantedBy, String grantedToEmail, String grantedToOrgCode, PermissionLevel level, String previousValue, String newValue, String reason, String details, Instant validFrom, Instant expiresAt);

    void logPermissionChange(String action, String targetType, Long targetId, String fileHash, Long folderId, Long workspaceId, Long groupId, Long collaborationId, SpaceType spaceType, String orgCode, UserEntity grantedBy, String grantedToEmail, String grantedToOrgCode, PermissionLevel level, String previousValue, String newValue, String reason, String details, Instant validFrom, Instant expiresAt);

    List<DocumentActivityLogResponse> getDocumentActivityLogs(Long documentId, Long folderId, String orgCode, UserEntity currentUser);

    List<DocumentActivityLogResponse> getDocumentActivityLogs(Long documentId, Long folderId, Long workspaceId, String orgCode, UserEntity currentUser);

    org.springframework.data.domain.Page<DocumentActivityLogResponse> queryActivityLogs(
            Long documentId,
            Long folderId,
            Long workspaceId,
            String orgCode,
            String actor,
            Action action,
            String resourceType,
            Long resourceId,
            Long groupId,
            Long collaborationId,
            Instant from,
            Instant to,
            org.springframework.data.domain.Pageable pageable,
            UserEntity currentUser
    );

    List<PermissionAuditLogResponse> getPermissionAuditLogs(Long targetId, String targetType, Long folderId, String orgCode, UserEntity currentUser);

    List<PermissionAuditLogResponse> getPermissionAuditLogs(Long targetId, String targetType, Long folderId, Long workspaceId, String orgCode, UserEntity currentUser);

    org.springframework.data.domain.Page<PermissionAuditLogResponse> queryGovernanceLogs(
            Long targetId,
            String targetType,
            Long folderId,
            Long workspaceId,
            String orgCode,
            String actor,
            String action,
            Long groupId,
            Long collaborationId,
            Instant from,
            Instant to,
            org.springframework.data.domain.Pageable pageable,
            UserEntity currentUser
    );
}
