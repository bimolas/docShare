package com.subject77.docshare.modules.audit.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditProvenanceDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.repository.UnifiedAuditRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnifiedAuditServiceImpl implements UnifiedAuditService {

    private final UnifiedAuditRepository unifiedAuditRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final GroupRepository groupRepository;
    private final WorkspaceManagerService workspaceManagerService;

    @Override
    @Transactional(readOnly = true)
    public Page<UnifiedAuditEventResponseDto> queryEvents(
            UnifiedAuditQueryCriteria criteria,
            Pageable pageable,
            UserEntity currentUser) {

        // 1. Authenticate caller
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to access audit events");
        }

        // 2. Validate scope contract
        if (criteria == null || criteria.getScopeType() == null) {
            throw new IllegalArgumentException("scopeType is required");
        }

        if (Boolean.TRUE.equals(criteria.getIncludeDescendants()) && criteria.getScopeType() != AuditScopeType.FOLDER) {
            throw new IllegalArgumentException("includeDescendants is only applicable for FOLDER scope");
        }

        // 3. Security Gateway: Authorize Scope & Establish Redaction Boundary
        boolean canViewSensitiveMetadata = authorizeScopeAndResolveMetadataPrivilege(criteria, currentUser);

        // 4. Resolve Folder Descendants (if requested for FOLDER scope)
        List<Long> folderDescendantIds = null;
        if (criteria.getScopeType() == AuditScopeType.FOLDER && Boolean.TRUE.equals(criteria.getIncludeDescendants())) {
            folderDescendantIds = folderRepository.findSubtreeFolderIds(criteria.getFolderId());
        }

        // 5. Query execution via UnifiedAuditRepository
        return unifiedAuditRepository.queryUnifiedAuditEvents(
                criteria,
                folderDescendantIds,
                currentUser.getId(),
                canViewSensitiveMetadata,
                pageable
        );
    }

    private boolean authorizeScopeAndResolveMetadataPrivilege(UnifiedAuditQueryCriteria criteria, UserEntity currentUser) {
        switch (criteria.getScopeType()) {
            case PERSONAL -> {
                // Personal Space: currentUser is inherently authorized for their own personal space
                return currentUser.getRole() == Role.ROLE_SUPER_ADMIN;
            }

            case ORGANIZATION -> {
                String orgCode = criteria.getOrgCode();
                if (orgCode == null || orgCode.isBlank()) {
                    throw new IllegalArgumentException("orgCode is required for ORGANIZATION scope");
                }
                if (!permissionEvaluatorService.canViewOrgAuditLogs(currentUser, orgCode)) {
                    throw new AccessDeniedException("Access denied: You do not have permission to view audit logs for organization " + orgCode);
                }
                return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, orgCode);
            }

            case WORKSPACE -> {
                Long workspaceId = criteria.getWorkspaceId();
                if (workspaceId == null) {
                    throw new IllegalArgumentException("workspaceId is required for WORKSPACE scope");
                }
                ProjectWorkspaceEntity ws = projectWorkspaceRepository.findById(workspaceId).orElse(null);
                if (ws != null) {
                    if (!permissionEvaluatorService.canViewWorkspaceAuditLogs(currentUser, ws)) {
                        throw new AccessDeniedException("Access denied: You do not have permission to view audit logs for workspace " + workspaceId);
                    }
                    String orgCode = ws.getOrganization() != null ? ws.getOrganization().getOrgCode() : null;
                    return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, orgCode);
                } else {
                    // Deleted workspace provenance check
                    AuditProvenanceDto prov = unifiedAuditRepository.findDeletedResourceProvenance("WORKSPACE", workspaceId)
                            .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));
                    if (!permissionEvaluatorService.isUserAdminOfOrg(currentUser, prov.getOrgCode())) {
                        throw new AccessDeniedException("Access denied: Unauthorized to inspect historical audit logs for deleted workspace " + workspaceId);
                    }
                    return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, prov.getOrgCode());
                }
            }

            case FOLDER -> {
                Long folderId = criteria.getFolderId();
                if (folderId == null) {
                    throw new IllegalArgumentException("folderId is required for FOLDER scope");
                }
                FolderEntity folder = folderRepository.findById(folderId).orElse(null);
                if (folder != null) {
                    if (!permissionEvaluatorService.canViewFolderAuditLogs(currentUser, folder)) {
                        throw new AccessDeniedException("Access denied: You do not have permission to view audit logs for folder " + folderId);
                    }
                    String orgCode = folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : null;
                    return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, orgCode);
                } else {
                    // Deleted folder provenance check
                    AuditProvenanceDto prov = unifiedAuditRepository.findDeletedResourceProvenance("FOLDER", folderId)
                            .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));
                    if (prov.getOrgCode() == null) {
                        // Personal folder: only creator/owner can inspect
                        if (!currentUser.getId().equals(prov.getActorUserId()) && currentUser.getRole() != Role.ROLE_SUPER_ADMIN) {
                            throw new AccessDeniedException("Access denied: Unauthorized to inspect historical audit logs for deleted personal folder");
                        }
                        return currentUser.getRole() == Role.ROLE_SUPER_ADMIN;
                    } else {
                        // Org folder: Org admin or current Workspace Manager
                        boolean authorized = permissionEvaluatorService.isUserAdminOfOrg(currentUser, prov.getOrgCode());
                        if (!authorized && prov.getWorkspaceId() != null) {
                            authorized = isUserWorkspaceManagerForId(currentUser, prov.getWorkspaceId());
                        }
                        if (!authorized) {
                            throw new AccessDeniedException("Access denied: Unauthorized to inspect historical audit logs for deleted folder");
                        }
                        return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, prov.getOrgCode());
                    }
                }
            }

            case DOCUMENT -> {
                Long documentId = criteria.getDocumentId();
                if (documentId == null) {
                    throw new IllegalArgumentException("documentId is required for DOCUMENT scope");
                }
                DocumentEntity doc = documentRepository.findById(documentId).orElse(null);
                if (doc != null) {
                    if (!permissionEvaluatorService.canViewDocumentAuditLogs(currentUser, doc)) {
                        throw new AccessDeniedException("Access denied: You do not have permission to view audit logs for document " + documentId);
                    }
                    String orgCode = doc.getOwnerOrg() != null ? doc.getOwnerOrg().getOrgCode() : null;
                    return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, orgCode);
                } else {
                    // Deleted document provenance check
                    AuditProvenanceDto prov = unifiedAuditRepository.findDeletedResourceProvenance("DOCUMENT", documentId)
                            .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));
                    if (prov.getOrgCode() == null) {
                        // Personal document: only uploader/owner can inspect
                        if (!currentUser.getId().equals(prov.getActorUserId()) && currentUser.getRole() != Role.ROLE_SUPER_ADMIN) {
                            throw new AccessDeniedException("Access denied: Unauthorized to inspect historical audit logs for deleted personal document");
                        }
                        return currentUser.getRole() == Role.ROLE_SUPER_ADMIN;
                    } else {
                        // Org document: Org admin or current Workspace Manager
                        boolean authorized = permissionEvaluatorService.isUserAdminOfOrg(currentUser, prov.getOrgCode());
                        if (!authorized && prov.getWorkspaceId() != null) {
                            authorized = isUserWorkspaceManagerForId(currentUser, prov.getWorkspaceId());
                        }
                        if (!authorized) {
                            throw new AccessDeniedException("Access denied: Unauthorized to inspect historical audit logs for deleted document");
                        }
                        return permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, prov.getOrgCode());
                    }
                }
            }

            case TEAM -> {
                Long teamId = criteria.getTeamId();
                if (teamId == null) {
                    throw new IllegalArgumentException("teamId is required for TEAM scope");
                }
                if (!permissionEvaluatorService.canViewTeamAuditLogs(currentUser, teamId)) {
                    throw new AccessDeniedException("Access denied: You do not have permission to view audit logs for team " + teamId);
                }
                // Sensitive metadata: Team managers receive redacted network telemetry; only Org Admins or Super Admins receive IP/User-Agent
                GroupEntity group = groupRepository.findById(teamId).orElse(null);
                return group != null && group.getOrganization() != null &&
                        permissionEvaluatorService.canViewSensitiveAuditMetadata(currentUser, group.getOrganization().getOrgCode());
            }

            case COLLABORATION -> {
                Long collaborationId = criteria.getCollaborationId();
                if (collaborationId == null) {
                    throw new IllegalArgumentException("collaborationId is required for COLLABORATION scope");
                }
                if (!permissionEvaluatorService.canViewCollaborationAuditLogs(currentUser, collaborationId)) {
                    throw new AccessDeniedException("Access denied: You do not have permission to view audit logs for collaboration " + collaborationId);
                }
                return currentUser.getRole() == Role.ROLE_SUPER_ADMIN;
            }

            default -> throw new IllegalArgumentException("Unsupported audit scope: " + criteria.getScopeType());
        }
    }

    private boolean isUserWorkspaceManagerForId(UserEntity user, Long workspaceId) {
        if (user == null || workspaceId == null) return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return true;
        ProjectWorkspaceEntity ws = projectWorkspaceRepository.findById(workspaceId).orElse(null);
        if (ws == null) return false;
        return workspaceManagerService != null && workspaceManagerService.isWorkspaceManager(user, ws);
    }
}
