package com.subject77.docshare.modules.audit.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.dto.DocumentActivityLogResponse;
import com.subject77.docshare.modules.audit.dto.PermissionAuditLogResponse;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.AuditEventType;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AccessLogRepository accessLogRepository;
    private final PermissionAuditLogRepository permissionAuditLogRepository;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDocumentActivity(Action action, Long documentId, String fileHash, Long folderId, String accessorEmail, String orgCode, String ipAddress, String userAgent, String details, String txHash) {
        logDocumentActivity(action, documentId, fileHash, folderId, null, null, accessorEmail, orgCode, ipAddress, userAgent, details, txHash);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDocumentActivity(Action action, Long documentId, String fileHash, Long folderId, String actorUserId, String accessorEmail, String orgCode, String ipAddress, String userAgent, String details, String txHash) {
        logDocumentActivity(action, documentId, fileHash, folderId, null, actorUserId, accessorEmail, orgCode, ipAddress, userAgent, details, txHash);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logDocumentActivity(Action action, Long documentId, String fileHash, Long folderId, Long workspaceId, String actorUserId, String accessorEmail, String orgCode, String ipAddress, String userAgent, String details, String txHash) {
        try {
            FolderEntity folder = null;

            if (folderId != null) {
                if (action == Action.DELETE_FOLDER || action == Action.CREATE_FOLDER) {
                    // For CREATE_FOLDER, the folder was created in caller's uncommitted transaction;
                    // referencing it across the REQUIRES_NEW boundary can cause uncommitted FK lock wait / violation.
                    // For DELETE_FOLDER, the folder is about to be deleted in caller's transaction.
                    // In both cases, we record scalar resource_type='FOLDER' and resource_id=folderId.
                    folder = null;
                } else {
                    folder = folderRepository.findById(folderId).orElse(null);
                }
            }

            String resourceType = null;
            Long resourceId = null;

            if (documentId != null) {
                resourceType = "DOCUMENT";
                resourceId = documentId;
                if (folder == null) {
                    DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
                    if (doc != null) {
                        if (doc.getFolder() != null) {
                            folder = doc.getFolder();
                        }
                        if (orgCode == null && doc.getOwnerOrg() != null) {
                            orgCode = doc.getOwnerOrg().getOrgCode();
                        }
                    }
                }
            } else if (folderId != null) {
                resourceType = "FOLDER";
                resourceId = folderId;
            }

            if (folder != null && workspaceId == null) {
                workspaceId = resolveWorkspaceIdForFolder(folder);
            }

            AccessLogEntity logEntry = AccessLogEntity.builder()
                    .action(action)
                    .documentId(documentId)
                    .documentFileHash(fileHash)
                    .folder(folder)
                    .actorUserId(actorUserId)
                    .workspaceId(workspaceId)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .accessorEmail(accessorEmail != null ? accessorEmail : "SYSTEM")
                    .orgCode(orgCode)
                    .ipAddress(ipAddress != null ? ipAddress : "0.0.0.0")
                    .userAgent(userAgent != null ? userAgent : "Internal")
                    .details(details)
                    .txHash(txHash)
                    .timestamp(Instant.now())
                    .build();

            accessLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("Non-blocking failure recording document access telemetry: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logPermissionChange(AuditPermissionChangeCommand cmd) {
        if (cmd == null) {
            throw new IllegalArgumentException("AuditPermissionChangeCommand must not be null");
        }
        if (cmd.getAction() == null || cmd.getAction().isBlank()) {
            throw new IllegalArgumentException("Action must not be null or blank for governance audit");
        }
        if (cmd.getTargetType() == null || cmd.getTargetType().isBlank()) {
            throw new IllegalArgumentException("TargetType must not be null or blank for governance audit");
        }

        // 1. Resolve Actor Scope
        String actorUserId = cmd.getActorUserId();
        if (actorUserId == null && cmd.getGrantedBy() != null) {
            actorUserId = cmd.getGrantedBy().getId();
        }
        if (actorUserId == null && cmd.getAction() != null && cmd.getAction().toUpperCase().contains("EXPIRED")) {
            actorUserId = "SYSTEM";
        }
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new IllegalArgumentException("ActorUserId must not be null or blank for governance audit");
        }

        // 2. Resolve Workspace Scope
        Long workspaceId = cmd.getWorkspaceId();
        Long folderId = cmd.getFolderId();
        Long targetId = cmd.getTargetId();
        String targetType = cmd.getTargetType();

        if (workspaceId == null) {
            if ("WORKSPACE".equalsIgnoreCase(targetType) && targetId != null) {
                workspaceId = targetId;
            } else if ("FOLDER".equalsIgnoreCase(targetType) && targetId != null) {
                FolderEntity f = folderRepository.findById(targetId).orElse(null);
                workspaceId = resolveWorkspaceIdForFolder(f);
            } else if ("DOCUMENT".equalsIgnoreCase(targetType) && targetId != null) {
                DocumentEntity d = documentRepository.findById(targetId).orElse(null);
                if (d != null && d.getFolder() != null) {
                    workspaceId = resolveWorkspaceIdForFolder(d.getFolder());
                }
            } else if (folderId != null) {
                FolderEntity f = folderRepository.findById(folderId).orElse(null);
                workspaceId = resolveWorkspaceIdForFolder(f);
            }
        }

        // 3. Resolve Group & Collaboration Scopes
        Long groupId = cmd.getGroupId();
        if (groupId == null && "GROUP".equalsIgnoreCase(targetType) && targetId != null) {
            groupId = targetId;
        }

        Long collaborationId = cmd.getCollaborationId();
        if (collaborationId == null && "COLLABORATION".equalsIgnoreCase(targetType) && targetId != null) {
            collaborationId = targetId;
        }

        // 4. Resolve Space Type & Organization Scope
        SpaceType spaceType = cmd.getSpaceType();
        String orgCode = cmd.getOrgCode();

        if (orgCode == null) {
            if ("FOLDER".equalsIgnoreCase(targetType) && targetId != null) {
                FolderEntity f = folderRepository.findById(targetId).orElse(null);
                if (f != null && f.getOwnerOrg() != null) {
                    orgCode = f.getOwnerOrg().getOrgCode();
                    if (spaceType == null) spaceType = SpaceType.ORGANIZATION;
                } else if (f != null && spaceType == null) {
                    spaceType = SpaceType.PERSONAL;
                }
            } else if ("DOCUMENT".equalsIgnoreCase(targetType) && targetId != null) {
                DocumentEntity d = documentRepository.findById(targetId).orElse(null);
                if (d != null && d.getOwnerOrg() != null) {
                    orgCode = d.getOwnerOrg().getOrgCode();
                    if (spaceType == null) spaceType = SpaceType.ORGANIZATION;
                } else if (d != null && spaceType == null) {
                    spaceType = SpaceType.PERSONAL;
                }
            }
        }

        if (spaceType == SpaceType.ORGANIZATION && (orgCode == null || orgCode.isBlank())) {
            throw new IllegalStateException("Cannot establish required organization scope for governance audit on organization resource");
        }

        Instant eventTimestamp = cmd.getTimestamp() != null ? cmd.getTimestamp() : Instant.now();

        PermissionAuditLogEntity logEntry = PermissionAuditLogEntity.builder()
                .action(cmd.getAction())
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(cmd.getTargetFileHash())
                .folderId(folderId)
                .actorUserId(actorUserId)
                .workspaceId(workspaceId)
                .groupId(groupId)
                .collaborationId(collaborationId)
                .spaceType(spaceType)
                .orgCode(orgCode)
                .grantedBy(cmd.getGrantedBy())
                .grantedToEmail(cmd.getGrantedToEmail())
                .grantedToOrgCode(cmd.getGrantedToOrgCode())
                .permissionLevel(cmd.getPermissionLevel())
                .previousValue(cmd.getPreviousValue())
                .newValue(cmd.getNewValue())
                .reason(cmd.getReason())
                .details(cmd.getDetails())
                .recordHash(cmd.getRecordHash())
                .blockchainStatus(cmd.getBlockchainStatus())
                .blockchainTxHash(cmd.getBlockchainTxHash())
                .validFrom(cmd.getValidFrom())
                .expiresAt(cmd.getExpiresAt())
                .timestamp(eventTimestamp)
                .build();

        permissionAuditLogRepository.save(logEntry);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logPermissionChange(String action, String targetType, Long targetId, String fileHash, Long folderId, SpaceType spaceType, String orgCode, UserEntity grantedBy, String grantedToEmail, String grantedToOrgCode, PermissionLevel level, Instant validFrom, Instant expiresAt) {
        logPermissionChange(action, targetType, targetId, fileHash, folderId, null, null, null, spaceType, orgCode, grantedBy, grantedToEmail, grantedToOrgCode, level, null, null, null, null, validFrom, expiresAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logPermissionChange(String action, String targetType, Long targetId, String fileHash, Long folderId, SpaceType spaceType, String orgCode, UserEntity grantedBy, String grantedToEmail, String grantedToOrgCode, PermissionLevel level, String previousValue, String newValue, String reason, String details, Instant validFrom, Instant expiresAt) {
        logPermissionChange(action, targetType, targetId, fileHash, folderId, null, null, null, spaceType, orgCode, grantedBy, grantedToEmail, grantedToOrgCode, level, previousValue, newValue, reason, details, validFrom, expiresAt);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void logPermissionChange(String action, String targetType, Long targetId, String fileHash, Long folderId, Long workspaceId, Long groupId, Long collaborationId, SpaceType spaceType, String orgCode, UserEntity grantedBy, String grantedToEmail, String grantedToOrgCode, PermissionLevel level, String previousValue, String newValue, String reason, String details, Instant validFrom, Instant expiresAt) {
        AuditPermissionChangeCommand cmd = AuditPermissionChangeCommand.builder()
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(fileHash)
                .folderId(folderId)
                .workspaceId(workspaceId)
                .groupId(groupId)
                .collaborationId(collaborationId)
                .spaceType(spaceType)
                .orgCode(orgCode)
                .grantedBy(grantedBy)
                .actorUserId(grantedBy != null ? grantedBy.getId() : null)
                .grantedToEmail(grantedToEmail)
                .grantedToOrgCode(grantedToOrgCode)
                .permissionLevel(level)
                .previousValue(previousValue)
                .newValue(newValue)
                .reason(reason)
                .details(details)
                .validFrom(validFrom)
                .expiresAt(expiresAt)
                .build();
        logPermissionChange(cmd);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentActivityLogResponse> getDocumentActivityLogs(Long documentId, Long folderId, String orgCode, UserEntity currentUser) {
        return getDocumentActivityLogs(documentId, folderId, null, orgCode, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentActivityLogResponse> getDocumentActivityLogs(Long documentId, Long folderId, Long workspaceId, String orgCode, UserEntity currentUser) {
        Page<DocumentActivityLogResponse> page = queryActivityLogs(
                documentId, folderId, workspaceId, orgCode, null, null, null, null, null, null, null, null,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp", "id")), currentUser);
        return page.getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DocumentActivityLogResponse> queryActivityLogs(
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
            Pageable pageable,
            UserEntity currentUser) {

        Pageable sanitizedPageable = sanitizePageable(pageable);

        // 1. Authorize & establish scope boundary
        Specification<AccessLogEntity> scopeSpec;

        if (documentId != null) {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));
            if (!permissionEvaluatorService.canViewDocumentAuditLogs(currentUser, document)) {
                throw new AccessDeniedException("You do not have permission to view audit logs for this document.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("documentId"), documentId);
        } else if (folderId != null) {
            FolderEntity folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));
            if (!permissionEvaluatorService.canViewFolderAuditLogs(currentUser, folder)) {
                throw new AccessDeniedException("You do not have permission to view audit logs for this folder.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("folder").get("id"), folderId);
        } else if (workspaceId != null) {
            ProjectWorkspaceEntity ws = projectWorkspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));
            if (!permissionEvaluatorService.canViewWorkspaceAuditLogs(currentUser, ws)) {
                throw new AccessDeniedException("You do not have permission to view audit logs for this workspace.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
        } else if (orgCode != null && !orgCode.isBlank()) {
            if (!permissionEvaluatorService.canViewOrgAuditLogs(currentUser, orgCode)) {
                throw new AccessDeniedException("You do not have permission to view organization audit logs.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("orgCode"), orgCode);
        } else {
            // Default: own activity
            scopeSpec = (root, query, cb) -> cb.or(
                    cb.equal(root.get("accessorEmail"), currentUser.getEmail()),
                    cb.equal(root.get("actorUserId"), currentUser.getId())
            );
        }

        // 2. Add narrowing filter predicates
        Specification<AccessLogEntity> filterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (action != null) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.or(
                        cb.equal(root.get("accessorEmail"), actor),
                        cb.equal(root.get("actorUserId"), actor)
                ));
            }
            if (resourceType != null && !resourceType.isBlank()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            if (resourceId != null) {
                predicates.add(cb.equal(root.get("resourceId"), resourceId));
            }
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (collaborationId != null) {
                predicates.add(cb.equal(root.get("collaborationId"), collaborationId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<AccessLogEntity> combinedSpec = scopeSpec.and(filterSpec);
        Page<AccessLogEntity> entityPage = accessLogRepository.findAll(combinedSpec, sanitizedPageable);

        return entityPage.map(log -> toDocumentActivityLogResponse(log, currentUser));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionAuditLogResponse> getPermissionAuditLogs(Long targetId, String targetType, Long folderId, String orgCode, UserEntity currentUser) {
        return getPermissionAuditLogs(targetId, targetType, folderId, null, orgCode, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionAuditLogResponse> getPermissionAuditLogs(Long targetId, String targetType, Long folderId, Long workspaceId, String orgCode, UserEntity currentUser) {
        Page<PermissionAuditLogResponse> page = queryGovernanceLogs(
                targetId, targetType, folderId, workspaceId, orgCode, null, null, null, null, null, null,
                PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "timestamp", "id")), currentUser);
        return page.getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PermissionAuditLogResponse> queryGovernanceLogs(
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
            Pageable pageable,
            UserEntity currentUser) {

        Pageable sanitizedPageable = sanitizePageable(pageable);

        // 1. Authorize & establish scope boundary
        Specification<PermissionAuditLogEntity> scopeSpec;

        if (targetId != null && targetType != null) {
            if ("DOCUMENT".equalsIgnoreCase(targetType)) {
                DocumentEntity document = documentRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + targetId));
                if (!permissionEvaluatorService.canViewDocumentAuditLogs(currentUser, document)) {
                    throw new AccessDeniedException("You do not have permission to view permission audit logs for this document.");
                }
            } else if ("FOLDER".equalsIgnoreCase(targetType)) {
                FolderEntity folder = folderRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + targetId));
                if (!permissionEvaluatorService.canViewFolderAuditLogs(currentUser, folder)) {
                    throw new AccessDeniedException("You do not have permission to view permission audit logs for this folder.");
                }
            } else if ("WORKSPACE".equalsIgnoreCase(targetType)) {
                ProjectWorkspaceEntity ws = projectWorkspaceRepository.findById(targetId)
                        .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + targetId));
                if (!permissionEvaluatorService.canViewWorkspaceAuditLogs(currentUser, ws)) {
                    throw new AccessDeniedException("You do not have permission to view permission audit logs for this workspace.");
                }
            }
            scopeSpec = (root, query, cb) -> cb.and(
                    cb.equal(root.get("targetType"), targetType),
                    cb.equal(root.get("targetId"), targetId)
            );
        } else if (folderId != null) {
            FolderEntity folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));
            if (!permissionEvaluatorService.canViewFolderAuditLogs(currentUser, folder)) {
                throw new AccessDeniedException("You do not have permission to view permission audit logs for this folder.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("folderId"), folderId);
        } else if (workspaceId != null) {
            ProjectWorkspaceEntity ws = projectWorkspaceRepository.findById(workspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));
            if (!permissionEvaluatorService.canViewWorkspaceAuditLogs(currentUser, ws)) {
                throw new AccessDeniedException("You do not have permission to view permission audit logs for this workspace.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
        } else if (orgCode != null && !orgCode.isBlank()) {
            if (!permissionEvaluatorService.canViewOrgAuditLogs(currentUser, orgCode)) {
                throw new AccessDeniedException("You do not have permission to view organization permission audit logs.");
            }
            scopeSpec = (root, query, cb) -> cb.equal(root.get("orgCode"), orgCode);
        } else {
            // Default: permissions granted by user
            scopeSpec = (root, query, cb) -> cb.or(
                    cb.equal(root.get("grantedBy").get("id"), currentUser.getId()),
                    cb.equal(root.get("actorUserId"), currentUser.getId())
            );
        }

        // 2. Add narrowing filter predicates
        Specification<PermissionAuditLogEntity> filterSpec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (actor != null && !actor.isBlank()) {
                predicates.add(cb.or(
                        cb.equal(root.get("grantedBy").get("email"), actor),
                        cb.equal(root.get("actorUserId"), actor)
                ));
            }
            if (groupId != null) {
                predicates.add(cb.equal(root.get("groupId"), groupId));
            }
            if (collaborationId != null) {
                predicates.add(cb.equal(root.get("collaborationId"), collaborationId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Specification<PermissionAuditLogEntity> combinedSpec = scopeSpec.and(filterSpec);
        Page<PermissionAuditLogEntity> entityPage = permissionAuditLogRepository.findAll(combinedSpec, sanitizedPageable);

        return entityPage.map(log -> toPermissionAuditLogResponse(log, currentUser));
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "timestamp", "id"));
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.min(Math.max(1, pageable.getPageSize()), 100);
        Sort sort = pageable.getSort().isSorted()
                ? pageable.getSort().and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by(Sort.Direction.DESC, "timestamp", "id");
        return PageRequest.of(page, size, sort);
    }

    private Long resolveWorkspaceIdForFolder(FolderEntity folder) {
        if (folder == null) return null;
        FolderEntity cursor = folder;
        while (cursor.getParentFolder() != null) {
            cursor = cursor.getParentFolder();
        }
        if (cursor.getId() != null) {
            return projectWorkspaceRepository.findFirstByRootFolder_Id(cursor.getId())
                    .map(ProjectWorkspaceEntity::getId)
                    .orElse(null);
        }
        return null;
    }

    private DocumentActivityLogResponse toDocumentActivityLogResponse(AccessLogEntity entity, UserEntity currentUser) {
        boolean canViewSensitive = permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, entity.getOrgCode());
        boolean isOwnActivity = currentUser != null && currentUser.getEmail() != null &&
                currentUser.getEmail().equalsIgnoreCase(entity.getAccessorEmail());

        boolean exposeNetworkTelemetry = canViewSensitive;

        return DocumentActivityLogResponse.builder()
                .id(entity.getId())
                .action(entity.getAction())
                .timestamp(entity.getTimestamp())
                .documentId(entity.getDocumentId())
                .documentFileHash(entity.getDocumentFileHash())
                .folderId(entity.getFolder() != null ? entity.getFolder().getId() : null)
                .accessorEmail(entity.getAccessorEmail())
                .orgCode(entity.getOrgCode())
                .ipAddress(exposeNetworkTelemetry ? entity.getIpAddress() : null)
                .userAgent(exposeNetworkTelemetry ? entity.getUserAgent() : null)
                .details(sanitizeDetails(entity.getDetails()))
                .txHash(entity.getTxHash())
                .actorUserId(entity.getActorUserId())
                .workspaceId(entity.getWorkspaceId())
                .resourceType(entity.getResourceType())
                .resourceId(entity.getResourceId())
                .groupId(entity.getGroupId())
                .collaborationId(entity.getCollaborationId())
                .build();
    }

    private PermissionAuditLogResponse toPermissionAuditLogResponse(PermissionAuditLogEntity entity, UserEntity currentUser) {
        return PermissionAuditLogResponse.builder()
                .id(entity.getId())
                .action(entity.getAction())
                .canonicalAction(AuditEventType.fromRaw(entity.getAction()))
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .targetFileHash(entity.getTargetFileHash())
                .folderId(entity.getFolderId())
                .spaceType(entity.getSpaceType())
                .orgCode(entity.getOrgCode())
                .grantedByEmail(entity.getGrantedBy() != null ? entity.getGrantedBy().getEmail() : "SYSTEM")
                .grantedToEmail(entity.getGrantedToEmail())
                .grantedToOrgCode(entity.getGrantedToOrgCode())
                .permissionLevel(entity.getPermissionLevel())
                .previousValue(entity.getPreviousValue())
                .newValue(entity.getNewValue())
                .reason(entity.getReason())
                .details(sanitizeDetails(entity.getDetails()))
                .validFrom(entity.getValidFrom())
                .expiresAt(entity.getExpiresAt())
                .timestamp(entity.getTimestamp())
                .actorUserId(entity.getActorUserId() != null ? entity.getActorUserId() : (entity.getGrantedBy() != null ? entity.getGrantedBy().getId() : null))
                .workspaceId(entity.getWorkspaceId())
                .groupId(entity.getGroupId())
                .collaborationId(entity.getCollaborationId())
                .build();
    }

    private String sanitizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return details;
        }
        return details.replaceAll("(?i)(shareLinkToken|token|password|secret|bearer)\\s*[:=]\\s*[^,;\\s}\\]]+", "$1=[REDACTED]");
    }
}
