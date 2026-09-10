package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.PermissionTamperedException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PermissionEvaluatorService {

    private final PermissionRepository permissionRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserGroupRepository userGroupRepository;
    private final PermissionCacheService permissionCacheService;
    private final ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationParticipantRepository collaborationParticipantRepository;

    @Lazy
    @Autowired(required = false)
    private AuditLogService auditLogService;

    @Autowired
    public PermissionEvaluatorService(
            PermissionRepository permissionRepository,
            UserOrganizationRepository userOrganizationRepository,
            UserGroupRepository userGroupRepository,
            PermissionCacheService permissionCacheService,
            ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator,
            CollaborationRepository collaborationRepository,
            CollaborationParticipantRepository collaborationParticipantRepository
    ) {
        this.permissionRepository = permissionRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.userGroupRepository = userGroupRepository;
        this.permissionCacheService = permissionCacheService;
        this.externalSharingPolicyEvaluator = externalSharingPolicyEvaluator;
        this.collaborationRepository = collaborationRepository;
        this.collaborationParticipantRepository = collaborationParticipantRepository;
    }

    public PermissionEvaluatorService(
            PermissionRepository permissionRepository,
            UserOrganizationRepository userOrganizationRepository,
            UserGroupRepository userGroupRepository,
            PermissionCacheService permissionCacheService,
            PermissionAuditLogRepository permissionAuditLogRepository,
            ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator,
            CollaborationRepository collaborationRepository,
            CollaborationParticipantRepository collaborationParticipantRepository
    ) {
        this(permissionRepository, userOrganizationRepository, userGroupRepository, permissionCacheService, externalSharingPolicyEvaluator, collaborationRepository, collaborationParticipantRepository);
    }

    /**
     * Injected lazily to break the potential circular dependency:
     * WorkspaceManagerServiceImpl → ProjectWorkspaceRepository (no issue)
     * but WorkspaceManagerServiceImpl → AuditLogService → (possibly back here via
     * other services).
     * 
     * @Lazy ensures Spring resolves the proxy only when first called.
     */
    @Lazy
    @Autowired
    private WorkspaceManagerService workspaceManagerService;

    @Lazy
    @Autowired(required = false)
    private GroupRepository groupRepository;

    public void setGroupRepository(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    public boolean canUploadToFolder(UserEntity user, FolderEntity folder, String orgCode) {
        if (folder == null) {
            if (orgCode == null || orgCode.isBlank()) {
                return true;
            } else {
                return isUserMemberOfOrg(user, orgCode);
            }
        }
        return canEditFolder(user, folder);
    }

    public boolean canViewDocument(UserEntity user, DocumentEntity document) {
        if (document == null || user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        // Personal space: owner shortcut
        if (document.getOwnerOrg() == null && isDocumentOwner(user, document)) {
            return true;
        }
        if (document.getOwnerOrg() != null && isUserAdminOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (document.getOwnerOrg() != null && !isUserMemberOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            if (!hasApplicableExternalDocumentAuthorization(user, document)) {
                return false;
            }
        }
        PermissionLevel maxLevel = resolveMaxPermissionLevel(user, document);
        return maxLevel != null;
    }

    public boolean canDownloadDocument(UserEntity user, DocumentEntity document) {
        if (document == null || user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        // Personal space: owner shortcut
        if (document.getOwnerOrg() == null && isDocumentOwner(user, document)) {
            return true;
        }
        if (document.getOwnerOrg() != null && isUserAdminOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (document.getOwnerOrg() != null && !isUserMemberOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            if (!hasApplicableExternalDocumentAuthorization(user, document)) {
                return false;
            }
        }
        PermissionLevel maxLevel = resolveMaxPermissionLevel(user, document);
        return maxLevel == PermissionLevel.DOWNLOAD || maxLevel == PermissionLevel.EDIT;
    }

    public boolean canEditDocument(UserEntity user, DocumentEntity document) {
        if (document == null || user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        // Personal space: owner shortcut
        if (document.getOwnerOrg() == null && isDocumentOwner(user, document)) {
            return true;
        }
        if (document.getOwnerOrg() != null && isUserAdminOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (document.getOwnerOrg() != null && !isUserMemberOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            if (!hasApplicableExternalDocumentAuthorization(user, document)) {
                return false;
            }
        }
        PermissionLevel maxLevel = resolveMaxPermissionLevel(user, document);
        return maxLevel == PermissionLevel.EDIT;
    }

    public boolean canViewFolder(UserEntity user, FolderEntity folder) {
        if (folder == null || user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        // Personal space: owner shortcut
        if (folder.getOwnerOrg() == null && isFolderOwner(user, folder)) {
            return true;
        }
        if (folder.getOwnerOrg() != null && isUserAdminOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (folder.getOwnerOrg() != null && !isUserMemberOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            if (!hasApplicableExternalFolderAuthorization(user, folder)) {
                return false;
            }
        }
        PermissionLevel maxLevel = resolveMaxPermissionLevel(user, folder);
        return maxLevel != null;
    }

    public boolean canDownloadFolder(UserEntity user, FolderEntity folder) {
        if (folder == null || user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        // Personal space: owner shortcut
        if (folder.getOwnerOrg() == null && isFolderOwner(user, folder)) {
            return true;
        }
        if (folder.getOwnerOrg() != null && isUserAdminOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (folder.getOwnerOrg() != null && !isUserMemberOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            if (!hasApplicableExternalFolderAuthorization(user, folder)) {
                return false;
            }
        }
        PermissionLevel maxLevel = resolveMaxPermissionLevel(user, folder);
        return maxLevel == PermissionLevel.DOWNLOAD || maxLevel == PermissionLevel.EDIT;
    }

    public boolean canEditFolder(UserEntity user, FolderEntity folder) {
        if (folder == null || user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        // Personal space: owner shortcut
        if (folder.getOwnerOrg() == null && isFolderOwner(user, folder)) {
            return true;
        }
        if (folder.getOwnerOrg() != null && isUserAdminOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (folder.getOwnerOrg() != null && !isUserMemberOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            if (!hasApplicableExternalFolderAuthorization(user, folder)) {
                return false;
            }
        }
        PermissionLevel maxLevel = resolveMaxPermissionLevel(user, folder);
        return maxLevel == PermissionLevel.EDIT;
    }

    public boolean canViewDocumentAuditLogs(UserEntity user, DocumentEntity document) {
        if (user == null || document == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (document.getOwnerOrg() == null) {
            return isDocumentOwner(user, document);
        }
        if (isUserAdminOrOwnerOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (document.getFolder() != null && isWorkspaceManagerForFolder(user, document.getFolder())) {
            return true;
        }
        if (isDocumentOwner(user, document) && isUserMemberOfOrg(user, document.getOwnerOrg().getOrgCode())) {
            return resolveMaxPermissionLevel(user, document) == PermissionLevel.EDIT;
        }
        return false;
    }

    public boolean canViewFolderAuditLogs(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (folder.getOwnerOrg() == null) {
            return isFolderOwner(user, folder);
        }
        if (isUserAdminOrOwnerOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            return true;
        }
        if (isWorkspaceManagerForFolder(user, folder)) {
            return true;
        }
        if (isFolderOwner(user, folder) && isUserMemberOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
            return resolveMaxPermissionLevel(user, folder) == PermissionLevel.EDIT;
        }
        return false;
    }

    public boolean canViewWorkspaceAuditLogs(UserEntity user, com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity workspace) {
        if (user == null || workspace == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (workspace.getOrganization() != null && isUserAdminOrOwnerOfOrg(user, workspace.getOrganization().getOrgCode()))
            return true;
        if (workspaceManagerService != null && workspaceManagerService.isWorkspaceManager(user, workspace))
            return true;
        return false;
    }

    public boolean canViewSensitiveAuditMetadata(UserEntity user, String orgCode) {
        if (user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (orgCode == null || orgCode.isBlank())
            return false;
        return isUserAdminOfOrg(user, orgCode);
    }

    public boolean canViewOrgAuditLogs(UserEntity user, String orgCode) {
        return isUserAdminOrOwnerOfOrg(user, orgCode);
    }

    public boolean canViewTeamAuditLogs(UserEntity user, Long teamId) {
        if (user == null || teamId == null) {
            return false;
        }
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        if (groupRepository != null) {
            GroupEntity group = groupRepository.findById(teamId).orElse(null);
            if (group != null && group.getOrganization() != null) {
                if (isUserAdminOfOrg(user, group.getOrganization().getOrgCode())) {
                    return true;
                }
            }
        }
        return isUserTeamManagerOfGroup(user, teamId);
    }

    public boolean canViewCollaborationAuditLogs(UserEntity user, Long collaborationId) {
        if (user == null || collaborationId == null) {
            return false;
        }
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        if (isUserCollaborationManager(user, collaborationId)) {
            return true;
        }
        if (collaborationRepository != null) {
            CollaborationEntity col = collaborationRepository.findById(collaborationId).orElse(null);
            if (col != null) {
                if (col.getOwnerOrganization() != null && isUserAdminOfOrg(user, col.getOwnerOrganization().getOrgCode())) {
                    return true;
                }
                if (col.getPartnerOrganization() != null && isUserAdminOfOrg(user, col.getPartnerOrganization().getOrgCode())) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isUserAdminOrOwnerOfOrg(UserEntity user, String orgCode) {
        return isUserAdminOfOrg(user, orgCode);
    }

    public boolean isUserAdminOfOrg(UserEntity user, String orgCode) {
        if (user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (orgCode == null)
            return false;

        if (user.getId() != null && userOrganizationRepository != null) {
            try {
                Optional<UserOrganizationEntity> userOrg = userOrganizationRepository
                        .findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode);
                if (userOrg.isPresent()) {
                    UserOrganizationEntity uo = userOrg.get();
                    if (uo.getStatus() != MembershipStatus.ACTIVE) {
                        return false;
                    }
                    return uo.getRole() == Role.ROLE_ORG_ADMIN || uo.getRole() == Role.ROLE_SUPER_ADMIN;
                }
            } catch (Exception e) {
                log.warn("Error checking org admin status for user {} and org {}: {}", user.getId(), orgCode,
                        e.getMessage());
            }
        }

        return false;
    }

    public boolean isUserAdminOrManagerOfOrg(UserEntity user, String orgCode) {
        if (user == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (orgCode == null)
            return false;

        if (user.getId() != null && userOrganizationRepository != null) {
            try {
                Optional<UserOrganizationEntity> userOrg = userOrganizationRepository
                        .findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode);
                if (userOrg.isPresent()) {
                    UserOrganizationEntity uo = userOrg.get();
                    if (uo.getStatus() != MembershipStatus.ACTIVE) {
                        return false;
                    }
                    return uo.getRole() == Role.ROLE_ORG_ADMIN || uo.getRole() == Role.ROLE_ORG_MANAGER
                            || uo.getRole() == Role.ROLE_SUPER_ADMIN;
                }
            } catch (Exception e) {
                log.warn("Error checking org admin/manager status for user {} and org {}: {}", user.getId(), orgCode,
                        e.getMessage());
            }
        }

        return false;
    }

    public boolean canAdministerDocumentPermissions(UserEntity user, DocumentEntity document) {
        if (user == null || document == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (document.getOwnerOrg() != null) {
            if (isUserAdminOfOrg(user, document.getOwnerOrg().getOrgCode())) {
                return true;
            }
        }
        if (document.getFolder() != null) {
            if (canAdministerFolderPermissions(user, document.getFolder())) {
                return true;
            }
        }
        // Personal space document owner
        if (document.getOwnerOrg() == null) {
            return isDocumentOwner(user, document);
        }
        // Organization resource creator / uploader:
        // Exists ONLY when creator is ACTIVE member of ownerOrg, has current effective
        // EDIT, and is not excluded
        if (isDocumentOwner(user, document)) {
            String orgCode = document.getOwnerOrg().getOrgCode();
            if (!isUserMemberOfOrg(user, orgCode)) {
                return false;
            }
            PermissionLevel effectiveLevel = resolveMaxPermissionLevel(user, document);
            return effectiveLevel == PermissionLevel.EDIT;
        }
        return false;
    }

    public boolean canAdministerFolderPermissions(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null)
            return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN)
            return true;
        if (folder.getOwnerOrg() != null) {
            if (isUserAdminOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
                return true;
            }
        }
        // Workspace manager scoped to the workspace's root-folder hierarchy
        if (workspaceManagerService != null && workspaceManagerService.isWorkspaceManagerForFolder(user, folder)) {
            return true;
        }
        if (isCollaborationManagerForFolder(user, folder)) {
            return true;
        }
        // Personal space folder owner
        if (folder.getOwnerOrg() == null) {
            return isFolderOwner(user, folder);
        }
        // Organization folder creator:
        // Exists ONLY when creator is ACTIVE member of ownerOrg, has current effective
        // EDIT, and is not excluded
        if (isFolderOwner(user, folder)) {
            String orgCode = folder.getOwnerOrg().getOrgCode();
            if (!isUserMemberOfOrg(user, orgCode)) {
                return false;
            }
            PermissionLevel effectiveLevel = resolveMaxPermissionLevel(user, folder);
            return effectiveLevel == PermissionLevel.EDIT;
        }
        return false;
    }

    /**
     * Resolves the maximum PermissionLevel that an actor may delegate for a given
     * resource.
     * Returns null if the actor has no permission delegation authority for the
     * resource.
     */
    public PermissionLevel resolveDelegationCeiling(UserEntity actor, FolderEntity folder, DocumentEntity document) {
        if (actor == null) {
            return null;
        }

        // Global administration: SUPER_ADMIN
        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            return PermissionLevel.EDIT;
        }

        String ownerOrgCode = null;
        if (folder != null && folder.getOwnerOrg() != null) {
            ownerOrgCode = folder.getOwnerOrg().getOrgCode();
        } else if (document != null && document.getOwnerOrg() != null) {
            ownerOrgCode = document.getOwnerOrg().getOrgCode();
        }

        // Unlinked/hash-only resources where neither folder nor document entity exists
        if (folder == null && document == null) {
            if (actor.getRole() == Role.ROLE_ORG_ADMIN) {
                return PermissionLevel.EDIT;
            }
            if (actor.getOrganization() != null && isUserAdminOfOrg(actor, actor.getOrganization().getOrgCode())) {
                return PermissionLevel.EDIT;
            }
            return null;
        }

        // Personal Resource Owner: Personal space resources
        if (ownerOrgCode == null) {
            boolean isPersonalOwner = (folder != null && isFolderOwner(actor, folder))
                    || (document != null && isDocumentOwner(actor, document));
            if (isPersonalOwner) {
                return PermissionLevel.EDIT;
            }
            return null;
        }

        // Organization administration: ORG_ADMIN of resource's ownerOrg
        if (ownerOrgCode != null && isUserAdminOfOrg(actor, ownerOrgCode)) {
            return PermissionLevel.EDIT;
        }

        // Workspace Manager: Within assigned workspace scope
        if (folder != null && workspaceManagerService != null
                && workspaceManagerService.isWorkspaceManagerForFolder(actor, folder)) {
            return PermissionLevel.EDIT;
        }
        if (document != null && document.getFolder() != null && workspaceManagerService != null
                && workspaceManagerService.isWorkspaceManagerForFolder(actor, document.getFolder())) {
            return PermissionLevel.EDIT;
        }

        // Collaboration Manager: Within assigned collaboration scope
        if (folder != null && isCollaborationManagerForFolder(actor, folder)) {
            return PermissionLevel.EDIT;
        }
        if (document != null && document.getFolder() != null
                && isCollaborationManagerForFolder(actor, document.getFolder())) {
            return PermissionLevel.EDIT;
        }

        // Organization Resource Creator / Uploader:
        // Must be ACTIVE org member, have current effective EDIT, and not be excluded
        if (!isUserMemberOfOrg(actor, ownerOrgCode)) {
            return null;
        }

        boolean isCreator = (document != null && isDocumentOwner(actor, document))
                || (folder != null && isFolderOwner(actor, folder));

        if (isCreator) {
            PermissionLevel effectiveLevel = null;
            if (document != null) {
                effectiveLevel = resolveMaxPermissionLevel(actor, document);
            } else {
                effectiveLevel = resolveMaxPermissionLevel(actor, folder);
            }
            if (effectiveLevel == PermissionLevel.EDIT) {
                return PermissionLevel.EDIT;
            }
        }

        // Team Manager: Within assigned team boundary scope
        FolderEntity boundaryTargetFolder = folder != null ? folder : (document != null ? document.getFolder() : null);
        if (boundaryTargetFolder != null && isUserTeamManagerForFolder(actor, boundaryTargetFolder)) {
            return resolveTeamBoundaryCeiling(actor, boundaryTargetFolder);
        }

        // All other roles (Normal VIEW/DOWNLOAD/EDIT content users, ORG_MANAGER):
        // NO delegation authority
        return null;
    }

    /**
     * Contextual authority evaluation to determine whether an actor may modify,
     * overwrite, downgrade, suspend, activate, or revoke a permission grant based
     * on
     * authority tier, administrative scope, resource scope, and grant provenance.
     */
    public boolean canActorOverrideGrant(UserEntity actor, PermissionEntity targetPermission, FolderEntity folder,
            DocumentEntity document) {
        if (actor == null || targetPermission == null) {
            return false;
        }

        // 1. SUPER_ADMIN is globally authoritative and can override any grant
        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }

        AuthorityTier targetTier = targetPermission.getGrantorAuthorityTier();
        if (targetTier == null) {
            targetTier = AuthorityTier.PROTECTED_LEGACY;
        }

        // Non-SUPER_ADMIN cannot override a SUPER_ADMIN grant
        if (targetTier == AuthorityTier.SUPER_ADMIN) {
            return false;
        }

        // Resolve resource organization
        FolderEntity effectiveFolder = folder != null ? folder : targetPermission.getFolder();
        DocumentEntity effectiveDoc = document != null ? document : targetPermission.getDocument();

        if (effectiveFolder == null && effectiveDoc != null && effectiveDoc.getFolder() != null) {
            effectiveFolder = effectiveDoc.getFolder();
        }

        String ownerOrgCode = null;
        if (effectiveFolder != null && effectiveFolder.getOwnerOrg() != null) {
            ownerOrgCode = effectiveFolder.getOwnerOrg().getOrgCode();
        } else if (effectiveDoc != null && effectiveDoc.getOwnerOrg() != null) {
            ownerOrgCode = effectiveDoc.getOwnerOrg().getOrgCode();
        } else if (targetPermission.getTargetOrgCode() != null) {
            ownerOrgCode = targetPermission.getTargetOrgCode();
        }

        // 2. Personal Resources (ownerOrgCode == null)
        if (ownerOrgCode == null) {
            if (effectiveFolder != null || effectiveDoc != null) {
                return (effectiveFolder != null && isFolderOwner(actor, effectiveFolder))
                        || (effectiveDoc != null && isDocumentOwner(actor, effectiveDoc));
            }
            // Unlinked personal grant: creator owns it
            return targetPermission.getGrantedBy() != null
                    && targetPermission.getGrantedBy().getId().equals(actor.getId());
        }

        boolean isActorOrgAdmin = ownerOrgCode != null && isUserAdminOfOrg(actor, ownerOrgCode);

        // 3. Legacy Protected Permissions
        if (targetTier == AuthorityTier.PROTECTED_LEGACY) {
            // For an organization resource, only active ORG_ADMIN of owning org (or
            // SUPER_ADMIN) can override
            return isActorOrgAdmin;
        }

        // 4. ORG_ADMIN authority within own organization
        if (isActorOrgAdmin) {
            return true;
        }

        // If targetTier is ORG_ADMIN, a non-ORG_ADMIN cannot override it
        if (targetTier == AuthorityTier.ORG_ADMIN) {
            return false;
        }

        // 5. WORKSPACE_MANAGER target tier
        if (targetTier == AuthorityTier.WORKSPACE_MANAGER) {
            Long targetWorkspaceId = targetPermission.getWorkspaceId();
            if (targetWorkspaceId == null || workspaceManagerService == null) {
                return false;
            }
            Long validatedWsId = workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(actor, effectiveFolder,
                    targetWorkspaceId);
            return targetWorkspaceId.equals(validatedWsId);
        }

        // 6. COLLABORATION_MANAGER target tier
        if (targetTier == AuthorityTier.COLLABORATION_MANAGER) {
            CollaborationEntity targetCollab = targetPermission.getCollaboration();
            if (targetCollab == null || targetCollab.getId() == null) {
                return false;
            }
            boolean isCollabManager = isUserCollaborationManager(actor, targetCollab.getId());
            if (!isCollabManager) {
                return false;
            }
            if (effectiveFolder != null && targetCollab.getRootFolder() != null) {
                return isSameOrAncestorFolder(targetCollab.getRootFolder(), effectiveFolder);
            }
            return true;
        }

        // 7. ORG_RESOURCE_CREATOR target tier
        if (targetTier == AuthorityTier.ORG_RESOURCE_CREATOR) {
            // Workspace Manager in own workspace can override lower-tier grant
            if (effectiveFolder != null && workspaceManagerService != null
                    && workspaceManagerService.isWorkspaceManagerForFolder(actor, effectiveFolder)) {
                return true;
            }
            // Collaboration Manager in own collaboration can override lower-tier grant
            if (effectiveFolder != null && isCollaborationManagerForFolder(actor, effectiveFolder)) {
                return true;
            }
            // Creator can modify ONLY their own grants, within creator's own resource scope
            if (targetPermission.getGrantedBy() != null
                    && targetPermission.getGrantedBy().getId().equals(actor.getId())) {
                boolean isCreator = (effectiveDoc != null && isDocumentOwner(actor, effectiveDoc))
                        || (effectiveFolder != null && isFolderOwner(actor, effectiveFolder));
                if (isCreator && isUserMemberOfOrg(actor, ownerOrgCode)) {
                    PermissionLevel effectiveLevel = effectiveDoc != null
                            ? resolveMaxPermissionLevel(actor, effectiveDoc)
                            : resolveMaxPermissionLevel(actor, effectiveFolder);
                    return effectiveLevel == PermissionLevel.EDIT;
                }
            }
            return false;
        }

        // 8. TEAM_MANAGER target tier
        if (targetTier == AuthorityTier.TEAM_MANAGER) {
            // Workspace Manager in own workspace can override lower-tier grant
            if (effectiveFolder != null && workspaceManagerService != null
                    && workspaceManagerService.isWorkspaceManagerForFolder(actor, effectiveFolder)) {
                return true;
            }
            // Active Team Manager of the target group can override within team boundary
            if (targetPermission.getTargetGroup() != null && effectiveFolder != null) {
                Long groupId = targetPermission.getTargetGroup().getId();
                if (isUserTeamManagerOfGroup(actor, groupId) && isFolderWithinTeamBoundary(groupId, effectiveFolder)) {
                    return true;
                }
            }
            return false;
        }

        return false;
    }

    /**
     * Verifies whether an actor has authority to override/reactivate a permission
     * that was suspended or revoked by an inactivating user (permission.revokedBy).
     */
    public boolean canActorOverrideInactivator(UserEntity actor, UserEntity inactivator,
            PermissionEntity targetPermission, FolderEntity folder, DocumentEntity document) {
        if (actor == null)
            return false;
        if (inactivator == null)
            return true;

        // SUPER_ADMIN can override any inactivator
        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }

        // Non-SUPER_ADMIN cannot override a SUPER_ADMIN inactivator
        if (inactivator.getRole() == Role.ROLE_SUPER_ADMIN) {
            return false;
        }

        FolderEntity effectiveFolder = folder != null ? folder
                : (targetPermission != null ? targetPermission.getFolder() : null);
        DocumentEntity effectiveDoc = document != null ? document
                : (targetPermission != null ? targetPermission.getDocument() : null);

        if (effectiveFolder == null && effectiveDoc != null && effectiveDoc.getFolder() != null) {
            effectiveFolder = effectiveDoc.getFolder();
        }

        String ownerOrgCode = null;
        if (effectiveFolder != null && effectiveFolder.getOwnerOrg() != null) {
            ownerOrgCode = effectiveFolder.getOwnerOrg().getOrgCode();
        } else if (effectiveDoc != null && effectiveDoc.getOwnerOrg() != null) {
            ownerOrgCode = effectiveDoc.getOwnerOrg().getOrgCode();
        } else if (targetPermission != null && targetPermission.getTargetOrgCode() != null) {
            ownerOrgCode = targetPermission.getTargetOrgCode();
        }

        // Personal Resource: personal owner can override
        if (ownerOrgCode == null) {
            if (effectiveFolder != null || effectiveDoc != null) {
                return (effectiveFolder != null && isFolderOwner(actor, effectiveFolder))
                        || (effectiveDoc != null && isDocumentOwner(actor, effectiveDoc));
            }
            return targetPermission.getGrantedBy() != null
                    && targetPermission.getGrantedBy().getId().equals(actor.getId());
        }

        boolean isActorOrgAdmin = ownerOrgCode != null && isUserAdminOfOrg(actor, ownerOrgCode);
        boolean isInactivatorOrgAdmin = ownerOrgCode != null && isUserAdminOfOrg(inactivator, ownerOrgCode);

        // If inactivator was ORG_ADMIN: only SUPER_ADMIN or ORG_ADMIN of same org can
        // override
        if (isInactivatorOrgAdmin) {
            return isActorOrgAdmin;
        }

        // If actor is ORG_ADMIN of ownerOrg, they can override any lower-tier
        // inactivator
        if (isActorOrgAdmin) {
            return true;
        }

        // If target has Workspace Manager provenance
        Long targetWorkspaceId = targetPermission != null ? targetPermission.getWorkspaceId() : null;
        if (targetWorkspaceId != null && workspaceManagerService != null) {
            Long validatedWsId = workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(actor, effectiveFolder,
                    targetWorkspaceId);
            if (targetWorkspaceId.equals(validatedWsId)) {
                return true;
            }
        }

        // If target has Collaboration Manager provenance
        if (targetPermission != null && targetPermission.getCollaboration() != null) {
            Long collabId = targetPermission.getCollaboration().getId();
            if (isUserCollaborationManager(actor, collabId)) {
                return true;
            }
        }

        // If inactivator was Org Resource Creator
        if (actor.getId().equals(inactivator.getId())) {
            return true;
        }

        return false;
    }

    public boolean isUserCollaborationManager(UserEntity user, Long collaborationId) {
        if (user == null || collaborationId == null || collaborationParticipantRepository == null) {
            return false;
        }
        if (collaborationRepository != null) {
            boolean active = collaborationRepository.findById(collaborationId)
                    .map(c -> c.getStatus() == CollaborationStatus.ACTIVE)
                    .orElse(false);
            if (!active) {
                return false;
            }
        }
        return collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaborationId, user.getId())
                .map(p -> p.getCollaborationRole() == CollaborationRole.MANAGER)
                .orElse(false);
    }

    /**
     * Convenience delegation — allows callers to ask whether a user is a workspace
     * manager for a specific folder without importing WorkspaceManagerService.
     */
    public boolean isWorkspaceManagerForFolder(UserEntity user, FolderEntity folder) {
        if (workspaceManagerService == null)
            return false;
        return workspaceManagerService.isWorkspaceManagerForFolder(user, folder);
    }

    /**
     * Resolves the workspace ID if the user is acting as an authorized Workspace
     * Manager
     * for the folder hierarchy. Returns null if not a workspace manager for that
     * folder.
     */
    public Long resolveWorkspaceIdForManager(UserEntity user, FolderEntity folder, Long requestedWorkspaceId) {
        if (workspaceManagerService == null || user == null || folder == null)
            return null;
        return workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(user, folder, requestedWorkspaceId);
    }

    /**
     * Resolves the active collaboration entity if the given folder resides within
     * the root folder hierarchy of an active collaboration.
     */
    public CollaborationEntity resolveCollaborationForFolder(FolderEntity folder) {
        if (folder == null || collaborationRepository == null) {
            return null;
        }
        List<CollaborationEntity> activeCollaborations = collaborationRepository
                .findByStatus(CollaborationStatus.ACTIVE);
        for (CollaborationEntity collaboration : activeCollaborations) {
            if (collaboration.getRootFolder() != null
                    && isSameOrAncestorFolder(collaboration.getRootFolder(), folder)) {
                return collaboration;
            }
        }
        return null;
    }

    /**
     * Determines the institutional authority tier under which a new permission
     * grant
     * is chartered at creation time.
     */
    public AuthorityTier resolveAuthorityTierAtCreation(UserEntity user, FolderEntity folder, DocumentEntity document,
            CollaborationEntity collaboration) {
        if (user == null) {
            return AuthorityTier.PROTECTED_LEGACY;
        }
        // 1. SUPER_ADMIN
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return AuthorityTier.SUPER_ADMIN;
        }

        String ownerOrgCode = null;
        if (folder != null && folder.getOwnerOrg() != null) {
            ownerOrgCode = folder.getOwnerOrg().getOrgCode();
        } else if (document != null && document.getOwnerOrg() != null) {
            ownerOrgCode = document.getOwnerOrg().getOrgCode();
        }

        // 2. ORG_ADMIN (Organization-scoped authority)
        if (ownerOrgCode != null && isUserAdminOfOrg(user, ownerOrgCode)) {
            return AuthorityTier.ORG_ADMIN;
        }

        // 3. COLLABORATION_MANAGER
        FolderEntity effectiveFolder = folder != null ? folder : (document != null ? document.getFolder() : null);
        if (collaboration != null && effectiveFolder != null) {
            boolean active = collaboration.getStatus() == CollaborationStatus.ACTIVE;
            boolean isManager = isUserCollaborationManager(user, collaboration.getId());
            boolean inScope = collaboration.getRootFolder() != null
                    && isSameOrAncestorFolder(collaboration.getRootFolder(), effectiveFolder);
            if (active && isManager && inScope) {
                return AuthorityTier.COLLABORATION_MANAGER;
            }
        } else if (collaboration == null && effectiveFolder != null
                && isCollaborationManagerForFolder(user, effectiveFolder)) {
            return AuthorityTier.COLLABORATION_MANAGER;
        }

        // 4. WORKSPACE_MANAGER (Workspace-scoped authority)
        if (folder != null && workspaceManagerService != null
                && workspaceManagerService.isWorkspaceManagerForFolder(user, folder)) {
            return AuthorityTier.WORKSPACE_MANAGER;
        }
        if (document != null && document.getFolder() != null && workspaceManagerService != null
                && workspaceManagerService.isWorkspaceManagerForFolder(user, document.getFolder())) {
            return AuthorityTier.WORKSPACE_MANAGER;
        }

        // 5. PERSONAL_RESOURCE_OWNER
        if (ownerOrgCode == null && (folder != null || document != null)) {
            return AuthorityTier.PERSONAL_RESOURCE_OWNER;
        }

        // 6. ORG_RESOURCE_CREATOR
        if (document != null && isDocumentOwner(user, document)) {
            return AuthorityTier.ORG_RESOURCE_CREATOR;
        }
        if (folder != null && isFolderOwner(user, folder)) {
            return AuthorityTier.ORG_RESOURCE_CREATOR;
        }

        // 7. TEAM_MANAGER
        if (folder != null && isUserTeamManagerForFolder(user, folder)) {
            return AuthorityTier.TEAM_MANAGER;
        }
        if (document != null && document.getFolder() != null && isUserTeamManagerForFolder(user, document.getFolder())) {
            return AuthorityTier.TEAM_MANAGER;
        }

        return AuthorityTier.PROTECTED_LEGACY;
    }

    public boolean isUserTeamManagerInOrg(UserEntity user, String orgCode) {
        if (user == null || orgCode == null || userGroupRepository == null)
            return false;
        if (!isUserMemberOfOrg(user, orgCode)) {
            return false;
        }
        List<UserGroupEntity> groups = userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode(user.getId(),
                orgCode);
        return groups.stream()
                .filter(ug -> ug.getGroup() != null
                        && (ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE))
                .anyMatch(ug -> ug.getRole() == GroupMemberRole.MANAGER);
    }

    public boolean isUserTeamManagerForFolder(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null || folder.getOwnerOrg() == null || userGroupRepository == null
                || permissionRepository == null) {
            return false;
        }
        String orgCode = folder.getOwnerOrg().getOrgCode();
        if (!isUserMemberOfOrg(user, orgCode)) {
            return false;
        }
        List<UserGroupEntity> groups = userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode(user.getId(),
                orgCode);
        List<Long> managerGroupIds = groups.stream()
                .filter(ug -> ug.getGroup() != null
                        && (ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE))
                .filter(ug -> ug.getRole() == GroupMemberRole.MANAGER)
                .map(ug -> ug.getGroup().getId())
                .collect(Collectors.toList());
        if (managerGroupIds.isEmpty()) {
            return false;
        }
        for (Long groupId : managerGroupIds) {
            List<PermissionEntity> perms = permissionRepository.findByTargetGroup_Id(groupId);
            for (PermissionEntity p : perms) {
                if (!p.isRevoked() && !p.isSuspended() && p.getFolder() != null
                        && (p.getPermissionType() == PermissionType.BOUNDARY || p.getPermissionType() == PermissionType.ALLOW)
                        && isSameOrAncestorFolder(p.getFolder(), folder)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isUserTeamManagerOfGroup(UserEntity user, Long groupId) {
        if (user == null || groupId == null || userGroupRepository == null)
            return false;
        return userGroupRepository.findByUser_IdAndGroup_Id(user.getId(), groupId)
                .map(ug -> ug.getRole() == GroupMemberRole.MANAGER
                        && (ug.getGroup() != null && (ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE)))
                .orElse(false);
    }

    public boolean isFolderWithinTeamBoundary(Long groupId, FolderEntity folder) {
        if (groupId == null || folder == null || permissionRepository == null)
            return false;
        List<PermissionEntity> perms = permissionRepository.findByTargetGroup_Id(groupId);
        for (PermissionEntity p : perms) {
            if (!p.isRevoked() && !p.isSuspended() && p.getFolder() != null
                    && (p.getPermissionType() == PermissionType.BOUNDARY || p.getPermissionType() == PermissionType.ALLOW)
                    && isSameOrAncestorFolder(p.getFolder(), folder)) {
                return true;
            }
        }
        return false;
    }

    public PermissionLevel resolveTeamBoundaryCeiling(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null || folder.getOwnerOrg() == null || userGroupRepository == null
                || permissionRepository == null) {
            return null;
        }
        String orgCode = folder.getOwnerOrg().getOrgCode();
        if (!isUserMemberOfOrg(user, orgCode)) {
            return null;
        }
        List<UserGroupEntity> groups = userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode(user.getId(),
                orgCode);
        List<Long> managerGroupIds = groups.stream()
                .filter(ug -> ug.getGroup() != null
                        && (ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE))
                .filter(ug -> ug.getRole() == GroupMemberRole.MANAGER)
                .map(ug -> ug.getGroup().getId())
                .collect(Collectors.toList());
        if (managerGroupIds.isEmpty()) {
            return null;
        }
        PermissionLevel maxCeiling = null;
        for (Long groupId : managerGroupIds) {
            List<PermissionEntity> perms = permissionRepository.findByTargetGroup_Id(groupId);
            for (PermissionEntity p : perms) {
                if (!p.isRevoked() && !p.isSuspended() && p.getFolder() != null
                        && (p.getPermissionType() == PermissionType.BOUNDARY || p.getPermissionType() == PermissionType.ALLOW)
                        && isSameOrAncestorFolder(p.getFolder(), folder)) {
                    maxCeiling = compareLevels(maxCeiling, p.getPermissionLevel());
                }
            }
        }
        return maxCeiling;
    }

    public boolean isCollaborationManagerForFolder(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null || collaborationRepository == null
                || collaborationParticipantRepository == null) {
            return false;
        }
        List<CollaborationEntity> activeCollaborations = collaborationRepository
                .findByStatus(CollaborationStatus.ACTIVE);
        for (CollaborationEntity collaboration : activeCollaborations) {
            if (collaboration.getRootFolder() != null
                    && isSameOrAncestorFolder(collaboration.getRootFolder(), folder)) {
                boolean isManager = collaborationParticipantRepository
                        .findByCollaboration_IdAndUser_Id(collaboration.getId(), user.getId())
                        .map(p -> p.getCollaborationRole() == CollaborationRole.MANAGER)
                        .orElse(false);
                boolean isOwnerOrPartnerAdmin = (collaboration.getOwnerOrganization() != null
                        && isUserAdminOfOrg(user, collaboration.getOwnerOrganization().getOrgCode()))
                        || (collaboration.getPartnerOrganization() != null
                                && isUserAdminOfOrg(user, collaboration.getPartnerOrganization().getOrgCode()));
                if (isManager || isOwnerOrPartnerAdmin) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isDocumentOwner(UserEntity user, DocumentEntity document) {
        return document != null && document.getUploadedBy() != null
                && document.getUploadedBy().getId().equals(user.getId());
    }

    public boolean isFolderOwner(UserEntity user, FolderEntity folder) {
        return folder != null && folder.getCreatedBy() != null && folder.getCreatedBy().getId().equals(user.getId());
    }

    private boolean isUserMemberOfOrg(UserEntity user, String orgCode) {
        if (user == null || orgCode == null)
            return false;
        if (user.getId() != null && userOrganizationRepository != null) {
            try {
                return userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(user.getId(), orgCode,
                        MembershipStatus.ACTIVE);
            } catch (Exception e) {
                log.warn("Error checking org membership for user {} and org {}: {}", user.getId(), orgCode,
                        e.getMessage());
            }
        }
        return false;
    }

    /**
     * Determines whether an external (non-member) user possesses an applicable,
     * explicit, direct
     * USER_PERSONAL authorization for an organizational document (directly or
     * inherited from an ancestor folder).
     * Strictly ignores share links (shareLinkToken != null).
     */
    private boolean hasApplicableExternalDocumentAuthorization(UserEntity user, DocumentEntity document) {
        if (user == null || user.getEmail() == null || document == null) {
            return false;
        }
        if (document.getId() != null && permissionCacheService != null) {
            try {
                Optional<PermissionLevel> cachedLevel = permissionCacheService
                        .getCachedDocumentPermission(user.getEmail(), document.getId());
                if (cachedLevel.isPresent()) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Redis cache unavailable for external auth check: {}", e.getMessage());
            }
        }
        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);
        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        for (PermissionEntity p : permissions) {
            if (p == null)
                continue;
            boolean matchesDoc = (document.getId() != null && p.getDocument() != null
                    && document.getId().equals(p.getDocument().getId()))
                    || (document.getFileHash() != null && document.getFileHash().equals(p.getDocumentFileHash()));
            boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), document.getFolder());
            if (!matchesDoc && !matchesFolder) {
                continue;
            }
            // Strict requirement: Must be USER_PERSONAL (or legacy personal) AND
            // shareLinkToken == null
            RecipientType type = p.getRecipientType();
            boolean isPersonal = (type == RecipientType.USER_PERSONAL)
                    || (type == null && p.getTargetOrgCode() == null && p.getTargetGroup() == null);
            if (!isPersonal) {
                continue;
            }
            if (p.getShareLinkToken() != null && !p.getShareLinkToken().isBlank()) {
                continue;
            }
            boolean isUserTarget = p.getTargetUserEmail() != null
                    && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail());
            if (!isUserTarget) {
                continue;
            }
            if (!isPermissionValid(p, user)) {
                continue;
            }
            if (p.getPermissionType() == PermissionType.ALLOW) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines whether an external (non-member) user possesses an applicable,
     * explicit, direct
     * USER_PERSONAL authorization for an organizational folder (directly or
     * inherited from an ancestor folder).
     * Strictly ignores share links (shareLinkToken != null).
     */
    private boolean hasApplicableExternalFolderAuthorization(UserEntity user, FolderEntity folder) {
        if (user == null || user.getEmail() == null || folder == null) {
            return false;
        }
        if (folder.getId() != null && permissionCacheService != null) {
            try {
                Optional<PermissionLevel> cachedLevel = permissionCacheService
                        .getCachedFolderPermission(user.getEmail(), folder.getId());
                if (cachedLevel.isPresent()) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Redis cache unavailable for external auth check: {}", e.getMessage());
            }
        }
        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);
        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        for (PermissionEntity p : permissions) {
            if (p == null)
                continue;
            boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), folder);
            if (!matchesFolder) {
                continue;
            }
            // Strict requirement: Must be USER_PERSONAL (or legacy personal) AND
            // shareLinkToken == null
            RecipientType type = p.getRecipientType();
            boolean isPersonal = (type == RecipientType.USER_PERSONAL)
                    || (type == null && p.getTargetOrgCode() == null && p.getTargetGroup() == null);
            if (!isPersonal) {
                continue;
            }
            if (p.getShareLinkToken() != null && !p.getShareLinkToken().isBlank()) {
                continue;
            }
            boolean isUserTarget = p.getTargetUserEmail() != null
                    && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail());
            if (!isUserTarget) {
                continue;
            }
            if (!isPermissionValid(p, user)) {
                continue;
            }
            if (p.getPermissionType() == PermissionType.ALLOW) {
                return true;
            }
        }
        return false;
    }

    private List<String> getActiveOrgCodesForUser(UserEntity user) {
        if (user == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        if (user.getId() != null && userOrganizationRepository != null) {
            try {
                List<UserOrganizationEntity> userOrgs = userOrganizationRepository.findByUser_IdAndStatus(user.getId(),
                        MembershipStatus.ACTIVE);
                if (userOrgs != null) {
                    for (UserOrganizationEntity uo : userOrgs) {
                        if (uo.getOrganization() != null && uo.getOrganization().getOrgCode() != null) {
                            codes.add(uo.getOrganization().getOrgCode());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching active org memberships for user {}: {}", user.getId(), e.getMessage());
            }
        }
        return codes;
    }

    private List<Long> getActiveGroupIdsForUser(UserEntity user, List<String> activeOrgCodes) {
        if (user == null || user.getId() == null || userGroupRepository == null || activeOrgCodes == null
                || activeOrgCodes.isEmpty()) {
            return List.of();
        }
        try {
            List<UserGroupEntity> userGroups = userGroupRepository.findByUser_Id(user.getId());
            if (userGroups == null) {
                return List.of();
            }
            return userGroups.stream()
                    .filter(ug -> ug.getGroup() != null && ug.getGroup().getOrganization() != null
                            && ug.getGroup().getOrganization().getOrgCode() != null)
                    .filter(ug -> ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE)
                    .filter(ug -> activeOrgCodes.stream()
                            .anyMatch(code -> code.equalsIgnoreCase(ug.getGroup().getOrganization().getOrgCode())))
                    .map(ug -> ug.getGroup().getId())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error fetching active group IDs for user {}: {}", user.getId(), e.getMessage());
            return List.of();
        }
    }

    private List<PermissionEntity> fetchCandidatePermissions(UserEntity user, List<String> activeOrgCodes,
            List<Long> groupIds) {
        if (user == null)
            return List.of();

        List<PermissionEntity> result = new ArrayList<>();
        java.util.Set<Long> seenIds = new java.util.HashSet<>();

        String firstOrgCode = (activeOrgCodes != null && !activeOrgCodes.isEmpty()) ? activeOrgCodes.get(0) : null;
        List<PermissionEntity> basePerms;
        if (groupIds == null || groupIds.isEmpty()) {
            basePerms = permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), firstOrgCode);
        } else {
            basePerms = permissionRepository.findPermissionsForUserOrOrgOrGroups(user.getEmail(), firstOrgCode,
                    groupIds);
        }

        if (basePerms != null) {
            for (PermissionEntity p : basePerms) {
                if (p != null) {
                    if (p.getId() != null) {
                        if (seenIds.add(p.getId())) {
                            result.add(p);
                        }
                    } else {
                        result.add(p);
                    }
                }
            }
        }

        if (activeOrgCodes != null && activeOrgCodes.size() > 1) {
            for (int i = 1; i < activeOrgCodes.size(); i++) {
                String orgCode = activeOrgCodes.get(i);
                List<PermissionEntity> orgPerms = permissionRepository.findByTargetUserEmailOrTargetOrgCode(null,
                        orgCode);
                if (orgPerms != null) {
                    for (PermissionEntity p : orgPerms) {
                        if (p != null) {
                            if (p.getId() != null) {
                                if (seenIds.add(p.getId())) {
                                    result.add(p);
                                }
                            } else {
                                result.add(p);
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private boolean isPermissionValid(PermissionEntity permission, UserEntity accessor) {
        if (permission.isRevoked() || permission.isSuspended()) {
            return false;
        }
        if (permission.getValidFrom() != null && permission.getValidFrom().isAfter(Instant.now())) {
            return false;
        }
        if (permission.getExpiresAt() != null && permission.getExpiresAt().isBefore(Instant.now())) {
            return false;
        }
        if (externalSharingPolicyEvaluator != null
                && !externalSharingPolicyEvaluator.isExternalPermissionRuntimeValid(permission, accessor)) {
            log.debug("Permission ID={} is invalid under current external sharing policy conditions",
                    permission.getId());
            return false;
        }
        return true;
    }

    private boolean matchesTarget(PermissionEntity p, UserEntity user, List<String> activeOrgCodes,
            List<Long> groupIds) {
        if (p == null || user == null) {
            return false;
        }

        RecipientType type = p.getRecipientType();

        if (type != null) {
            switch (type) {
                case USER_PERSONAL:
                    return p.getTargetUserEmail() != null
                            && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail());

                case USER_ORGANIZATION_MEMBER:
                    if (p.getTargetUserEmail() == null || !p.getTargetUserEmail().equalsIgnoreCase(user.getEmail())) {
                        return false;
                    }
                    if (p.getTargetOrgCode() == null || activeOrgCodes == null || activeOrgCodes.isEmpty()) {
                        return false;
                    }
                    if (!activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()))) {
                        return false;
                    }
                    if (p.getTargetGroup() != null) {
                        if (groupIds == null || !groupIds.contains(p.getTargetGroup().getId())) {
                            return false;
                        }
                    }
                    return true;

                case ORGANIZATION:
                    if (p.getTargetOrgCode() == null || activeOrgCodes == null || activeOrgCodes.isEmpty()) {
                        return false;
                    }
                    return activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));

                case GROUP:
                    if (p.getTargetGroup() == null || groupIds == null) {
                        return false;
                    }
                    return groupIds.contains(p.getTargetGroup().getId());

                default:
                    return false;
            }
        }

        // Fallback for legacy permission records where recipientType might be null
        if (p.getTargetUserEmail() != null && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail())) {
            if (p.getTargetOrgCode() != null) {
                return activeOrgCodes != null
                        && activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));
            }
            return true;
        }

        if (p.getTargetOrgCode() != null && activeOrgCodes != null) {
            return activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));
        }

        if (p.getTargetGroup() != null && groupIds != null) {
            return groupIds.contains(p.getTargetGroup().getId());
        }

        return false;
    }

    private PermissionLevel resolveMaxPermissionLevel(UserEntity user, DocumentEntity document) {
        // Fast Path: Check Redis Cache with Fault Tolerance
        try {
            Optional<PermissionLevel> cachedLevel = permissionCacheService.getCachedDocumentPermission(user.getEmail(),
                    document.getId());
            if (cachedLevel.isPresent()) {
                return cachedLevel.get();
            }
        } catch (Exception e) {
            log.warn(
                    "Redis cache unavailable for permission check (email={}, docId={}). Falling back to database check: {}",
                    user.getEmail(), document.getId(), e.getMessage());
        }

        // Personal space: owner shortcut
        if (document.getOwnerOrg() == null && isDocumentOwner(user, document)) {
            return PermissionLevel.EDIT;
        }

        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);

        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);

        PermissionLevel highest = null;
        boolean isExcluded = false;
        boolean isDirectlyRevokedOrSuspended = false;
        for (PermissionEntity p : permissions) {
            boolean matchesDoc = (document.getId() != null && p.getDocument() != null
                    && document.getId().equals(p.getDocument().getId()))
                    || (document.getFileHash() != null && document.getFileHash().equals(p.getDocumentFileHash()));
            boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), document.getFolder());

            if ((matchesDoc || (matchesFolder && p.getFolder() != null && document.getFolder() != null
                    && p.getFolder().getId().equals(document.getFolder().getId())))
                    && (p.isRevoked() || p.isSuspended())
                    && p.getTargetUserEmail() != null && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail())) {
                isDirectlyRevokedOrSuspended = true;
            }

            if (!isPermissionValid(p, user)) {
                continue;
            }

            if (!matchesTarget(p, user, activeOrgCodes, groupIds)) {
                continue;
            }

            // Security Guard: Check SHA-256 Record Hash Integrity
            if (p.getRecordHash() != null && !verifyRecordHashIntegrity(p)) {
                log.error("TAMPER ALERT! Permission record ID={} has invalid SHA-256 fingerprint! Record: {}",
                        p.getId(), p.getRecordHash());
                logTamperAlert(p, user, document);
                throw new PermissionTamperedException(
                        "Security violation: Permission record ID " + p.getId() + " cryptographic checksum mismatch");
            }

            if (p.getPermissionType() == PermissionType.EXCLUDE) {
                if (matchesDoc || matchesFolder) {
                    isExcluded = true;
                }
            } else if (p.getPermissionType() == PermissionType.ALLOW) {
                if (matchesDoc || matchesFolder) {
                    highest = compareLevels(highest, p.getPermissionLevel());
                }
            }
        }

        if (isExcluded) {
            return null;
        }

        // Creator baseline access for organizational document
        if (highest == null && !isDirectlyRevokedOrSuspended && isDocumentOwner(user, document)) {
            if (document.getOwnerOrg() != null && isUserMemberOfOrg(user, document.getOwnerOrg().getOrgCode())) {
                highest = PermissionLevel.EDIT;
            }
        }

        // Cache evaluated result in Redis with Fault Tolerance
        if (highest != null) {
            try {
                permissionCacheService.cacheDocumentPermission(user.getEmail(), document.getId(), highest, 300);
            } catch (Exception e) {
                log.warn("Failed to write permission result to Redis cache: {}", e.getMessage());
            }
        }

        return highest;
    }

    public PermissionLevel resolveMaxPermissionLevel(UserEntity user, FolderEntity folder) {
        if (folder == null || user == null)
            return null;
        if (folder.getOwnerOrg() == null && isFolderOwner(user, folder)) {
            return PermissionLevel.EDIT;
        }

        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);

        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);

        PermissionLevel highest = null;
        boolean isExcluded = false;
        boolean isDirectlyRevokedOrSuspended = false;
        for (PermissionEntity p : permissions) {
            boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), folder);

            if (matchesFolder && (p.isRevoked() || p.isSuspended())
                    && p.getTargetUserEmail() != null && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail())
                    && p.getFolder() != null && p.getFolder().getId() != null
                    && p.getFolder().getId().equals(folder.getId())) {
                isDirectlyRevokedOrSuspended = true;
            }

            if (!isPermissionValid(p, user)) {
                continue;
            }

            if (!matchesTarget(p, user, activeOrgCodes, groupIds)) {
                continue;
            }

            if (p.getRecordHash() != null && !verifyRecordHashIntegrity(p)) {
                log.error("TAMPER ALERT! Permission record ID={} has invalid SHA-256 fingerprint!", p.getId());
                throw new PermissionTamperedException(
                        "Security violation: Permission record ID " + p.getId() + " cryptographic checksum mismatch");
            }

            if (p.getPermissionType() == PermissionType.EXCLUDE) {
                if (matchesFolder) {
                    isExcluded = true;
                }
            } else if (p.getPermissionType() == PermissionType.ALLOW) {
                if (matchesFolder) {
                    highest = compareLevels(highest, p.getPermissionLevel());
                }
            }
        }

        if (isExcluded) {
            return null;
        }

        // Creator baseline access for organizational folder
        if (highest == null && !isDirectlyRevokedOrSuspended && isFolderOwner(user, folder)) {
            if (folder.getOwnerOrg() != null && isUserMemberOfOrg(user, folder.getOwnerOrg().getOrgCode())) {
                highest = PermissionLevel.EDIT;
            }
        }

        return highest;
    }

    public Map<Long, PermissionLevel> resolveMaxPermissionLevelsForFolders(UserEntity user,
            List<FolderEntity> folders) {
        if (folders == null || folders.isEmpty())
            return Map.of();

        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);
        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);

        Map<Long, PermissionLevel> results = new java.util.HashMap<>();

        for (FolderEntity folder : folders) {
            if (folder == null)
                continue;
            if (isFolderOwner(user, folder)) {
                results.put(folder.getId(), PermissionLevel.EDIT);
                continue;
            }

            PermissionLevel highest = null;
            boolean isExcluded = false;
            for (PermissionEntity p : permissions) {
                if (!isPermissionValid(p, user)) {
                    continue;
                }

                if (!matchesTarget(p, user, activeOrgCodes, groupIds)) {
                    continue;
                }

                if (p.getRecordHash() != null && !verifyRecordHashIntegrity(p)) {
                    log.error("TAMPER ALERT! Permission record ID={} has invalid SHA-256 fingerprint!", p.getId());
                    throw new PermissionTamperedException("Security violation: Permission record ID " + p.getId()
                            + " cryptographic checksum mismatch");
                }

                boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), folder);

                if (p.getPermissionType() == PermissionType.EXCLUDE) {
                    if (matchesFolder) {
                        isExcluded = true;
                    }
                } else if (p.getPermissionType() == PermissionType.ALLOW) {
                    if (matchesFolder) {
                        highest = compareLevels(highest, p.getPermissionLevel());
                    }
                }
            }

            if (!isExcluded && highest != null) {
                results.put(folder.getId(), highest);
            }
        }

        return results;
    }

    public boolean isSameOrAncestorFolder(FolderEntity permissionFolder, FolderEntity targetFolder) {
        if (permissionFolder == null || targetFolder == null) {
            return false;
        }
        FolderEntity current = targetFolder;
        while (current != null) {
            if (permissionFolder.getId() != null && current.getId() != null
                    && permissionFolder.getId().equals(current.getId())) {
                return true;
            }
            current = current.getParentFolder();
        }
        return false;
    }

    private boolean verifyRecordHashIntegrity(PermissionEntity p) {
        return PermissionHashUtils.verifyRecordHash(p);
    }

    private void logTamperAlert(PermissionEntity p, UserEntity accessor, DocumentEntity doc) {
        try {
            AuditPermissionChangeCommand cmd = AuditPermissionChangeCommand.builder()
                    .action("TAMPER_ALERT")
                    .targetType("DOCUMENT")
                    .targetId(doc != null ? doc.getId() : null)
                    .targetFileHash(doc != null ? doc.getFileHash() : p.getDocumentFileHash())
                    .folderId(doc != null && doc.getFolder() != null ? doc.getFolder().getId() : (p.getFolder() != null ? p.getFolder().getId() : null))
                    .workspaceId(p.getWorkspaceId())
                    .groupId(p.getTargetGroup() != null ? p.getTargetGroup().getId() : null)
                    .collaborationId(p.getCollaboration() != null ? p.getCollaboration().getId() : null)
                    .spaceType(doc != null ? (doc.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (p.getFolder() != null ? (p.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                    .orgCode(doc != null && doc.getOwnerOrg() != null ? doc.getOwnerOrg().getOrgCode() : (p.getFolder() != null && p.getFolder().getOwnerOrg() != null ? p.getFolder().getOwnerOrg().getOrgCode() : p.getTargetOrgCode()))
                    .actorUserId(accessor != null ? accessor.getId() : (p.getGrantedBy() != null ? p.getGrantedBy().getId() : "SYSTEM"))
                    .grantedBy(p.getGrantedBy())
                    .grantedToEmail(p.getTargetUserEmail())
                    .grantedToOrgCode(p.getTargetOrgCode())
                    .permissionLevel(p.getPermissionLevel())
                    .recordHash(p.getRecordHash())
                    .blockchainStatus(p.getBlockchainStatus())
                    .blockchainTxHash(p.getBlockchainTxHash())
                    .timestamp(Instant.now())
                    .build();
            if (auditLogService != null) {
                auditLogService.logPermissionChange(cmd);
            }
        } catch (Exception e) {
            log.error("Error writing TAMPER_ALERT audit log", e);
        }
    }

    private PermissionLevel compareLevels(PermissionLevel current, PermissionLevel candidate) {
        if (current == null)
            return candidate;
        if (candidate == PermissionLevel.EDIT)
            return PermissionLevel.EDIT;
        if (candidate == PermissionLevel.DOWNLOAD && current == PermissionLevel.VIEW)
            return PermissionLevel.DOWNLOAD;
        return current;
    }

    public Optional<PermissionEntity> findExplicitExclusion(UserEntity user, DocumentEntity document) {
        if (user == null || document == null) {
            return Optional.empty();
        }
        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);
        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);
        for (PermissionEntity p : permissions) {
            boolean matchesDoc = (document.getId() != null && p.getDocument() != null
                    && document.getId().equals(p.getDocument().getId()))
                    || (document.getFileHash() != null && document.getFileHash().equals(p.getDocumentFileHash()));
            boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), document.getFolder());
            if (!isPermissionValid(p, user) || !matchesTarget(p, user, activeOrgCodes, groupIds)) {
                continue;
            }
            if (p.getPermissionType() == PermissionType.EXCLUDE && (matchesDoc || matchesFolder)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public boolean hasExplicitExclusion(UserEntity user, DocumentEntity document) {
        return findExplicitExclusion(user, document).isPresent();
    }

    public Optional<PermissionEntity> findExplicitExclusion(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null) {
            return Optional.empty();
        }
        List<String> activeOrgCodes = getActiveOrgCodesForUser(user);
        List<Long> groupIds = getActiveGroupIdsForUser(user, activeOrgCodes);
        List<PermissionEntity> permissions = fetchCandidatePermissions(user, activeOrgCodes, groupIds);
        for (PermissionEntity p : permissions) {
            boolean matchesFolder = isSameOrAncestorFolder(p.getFolder(), folder);
            if (!isPermissionValid(p, user) || !matchesTarget(p, user, activeOrgCodes, groupIds)) {
                continue;
            }
            if (p.getPermissionType() == PermissionType.EXCLUDE && matchesFolder) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    public boolean hasExplicitExclusion(UserEntity user, FolderEntity folder) {
        return findExplicitExclusion(user, folder).isPresent();
    }

}
