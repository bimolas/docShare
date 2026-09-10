package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamDelegationServiceImpl implements TeamDelegationService {

    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final PermissionRepository permissionRepository;
    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final PermissionCacheService permissionCacheService;

    @Override
    @Transactional(readOnly = true)
    public TeamAccessBoundaryResponseDto getTeamBoundary(String orgCode, Long teamId, UserEntity currentUser) {
        validateTeamInOrg(orgCode, teamId);
        validateCanViewTeam(currentUser, orgCode, teamId);

        GroupEntity team = getActiveTeamOrThrow(orgCode, teamId);
        PermissionEntity boundary = findActiveBoundaryForTeam(team.getId());
        if (boundary == null || boundary.getFolder() == null) {
            return null;
        }

        FolderEntity folder = boundary.getFolder();
        String workspaceName = null;
        if (boundary.getWorkspaceId() != null) {
            workspaceName = projectWorkspaceRepository.findById(boundary.getWorkspaceId())
                    .map(ProjectWorkspaceEntity::getName)
                    .orElse(null);
        }

        return TeamAccessBoundaryResponseDto.builder()
                .permissionId(boundary.getId())
                .teamId(team.getId())
                .teamName(team.getName())
                .workspaceId(boundary.getWorkspaceId())
                .workspaceName(workspaceName)
                .folderId(folder.getId())
                .folderName(folder.getName())
                .folderPath(buildFolderPath(folder))
                .ceilingLevel(boundary.getPermissionLevel())
                .allocatedByUserId(boundary.getGrantedBy() != null ? boundary.getGrantedBy().getId() : null)
                .allocatedByUserEmail(boundary.getGrantedBy() != null ? boundary.getGrantedBy().getEmail() : null)
                .allocatedAt(boundary.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamMemberAccessResponseDto> getMemberAccessList(String orgCode, Long teamId, UserEntity currentUser) {
        validateTeamInOrg(orgCode, teamId);
        validateCanViewTeam(currentUser, orgCode, teamId);

        GroupEntity team = getActiveTeamOrThrow(orgCode, teamId);

        List<PermissionEntity> delegations = permissionRepository.findByTargetGroup_Id(team.getId()).stream()
                .filter(p -> !p.isRevoked() && !p.isSuspended())
                .filter(p -> p.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER)
                .filter(p -> p.getPermissionType() == PermissionType.ALLOW)
                .collect(Collectors.toList());

        List<TeamMemberAccessResponseDto> result = new ArrayList<>();
        for (PermissionEntity p : delegations) {
            String resourceType = p.getFolder() != null ? "FOLDER" : (p.getDocument() != null ? "DOCUMENT" : "UNKNOWN");
            Long resourceId = p.getFolder() != null ? p.getFolder().getId() : (p.getDocument() != null ? p.getDocument().getId() : null);
            String resourceName = p.getFolder() != null ? p.getFolder().getName() : (p.getDocument() != null ? p.getDocument().getFileName() : null);
            String resourcePath = p.getFolder() != null ? buildFolderPath(p.getFolder()) : (p.getDocument() != null ? (p.getDocument().getFolder() != null ? buildFolderPath(p.getDocument().getFolder()) + "/" + p.getDocument().getFileName() : p.getDocument().getFileName()) : null);

            String userName = null;
            if (p.getTargetUserEmail() != null) {
                userName = userRepository.findByEmail(p.getTargetUserEmail())
                        .map(this::getUserDisplayName)
                        .orElse(null);
            }

            boolean isMigrated = p.getNote() != null && p.getNote().contains("MIGRATED_DELEGATION");

            result.add(TeamMemberAccessResponseDto.builder()
                    .permissionId(p.getId())
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .userEmail(p.getTargetUserEmail())
                    .userName(userName)
                    .resourceId(resourceId)
                    .resourceType(resourceType)
                    .resourceName(resourceName)
                    .resourcePath(resourcePath)
                    .permissionLevel(p.getPermissionLevel())
                    .grantorAuthorityTier(p.getGrantorAuthorityTier())
                    .grantedByUserId(p.getGrantedBy() != null ? p.getGrantedBy().getId() : null)
                    .grantedByUserEmail(p.getGrantedBy() != null ? p.getGrantedBy().getEmail() : null)
                    .grantedAt(p.getCreatedAt())
                    .isMigrated(isMigrated)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional
    public TeamMemberAccessResponseDto grantMemberAccess(String orgCode, Long teamId, GrantMemberAccessRequestDto req, UserEntity currentUser) {
        validateTeamInOrg(orgCode, teamId);
        validateManagerAuthority(currentUser, orgCode, teamId);

        GroupEntity team = getActiveTeamOrThrow(orgCode, teamId);

        PermissionEntity boundary = findActiveBoundaryForTeam(team.getId());
        if (boundary == null || boundary.getFolder() == null) {
            throw new IllegalStateException("Team does not have an active workspace boundary allocation");
        }

        if (req.getUserEmail() == null || req.getUserEmail().isBlank()) {
            throw new IllegalArgumentException("Member email is required");
        }
        UserEntity targetUser = userRepository.findByEmail(req.getUserEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found with email: " + req.getUserEmail()));

        // Invariant 3: Target user must be an active member of the Team
        boolean isMemberOfTeam = userGroupRepository.findByUser_IdAndGroup_Id(targetUser.getId(), team.getId())
                .filter(ug -> ug.getGroup() != null && (ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE))
                .isPresent();
        if (!isMemberOfTeam) {
            throw new AccessDeniedException("Target user " + req.getUserEmail() + " is not an active member of team: " + teamId);
        }

        FolderEntity targetFolder = null;
        DocumentEntity targetDoc = null;
        FolderEntity effectiveFolder = null;

        if (req.getFolderId() != null) {
            targetFolder = folderRepository.findById(req.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + req.getFolderId()));
            effectiveFolder = targetFolder;
        } else if (req.getDocumentId() != null) {
            targetDoc = documentRepository.findById(req.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + req.getDocumentId()));
            effectiveFolder = targetDoc.getFolder();
            if (effectiveFolder == null) {
                throw new IllegalArgumentException("Document must belong to a folder within the team boundary");
            }
        } else {
            throw new IllegalArgumentException("Either folderId or documentId must be specified");
        }

        // Invariant 2: Spatial Containment (Target resource must be inside boundary)
        if (!permissionEvaluatorService.isSameOrAncestorFolder(boundary.getFolder(), effectiveFolder)) {
            throw new AccessDeniedException("Target resource is outside the team access boundary: " + boundary.getFolder().getName());
        }

        // Invariant 1: Ceiling Enforcement
        if (req.getPermissionLevel() == null) {
            throw new IllegalArgumentException("Permission level is required");
        }
        if (req.getPermissionLevel().ordinal() > boundary.getPermissionLevel().ordinal()) {
            throw new AccessDeniedException("Requested permission level " + req.getPermissionLevel()
                    + " exceeds boundary ceiling of " + boundary.getPermissionLevel());
        }

        // Check for existing active delegation for this user on this resource chartered under this team
        PermissionEntity existing = findExistingDelegation(team.getId(), targetUser.getEmail(), targetFolder, targetDoc);
        PermissionEntity permissionToSave;

        if (existing != null) {
            // Validate Invariant 4: No Authority Escalation (cannot mutate higher-tier grants)
            if (existing.getGrantorAuthorityTier() != null && existing.getGrantorAuthorityTier() != AuthorityTier.TEAM_MANAGER) {
                throw new AccessDeniedException("Cannot modify permission created with higher authority tier: " + existing.getGrantorAuthorityTier());
            }
            existing.setPermissionLevel(req.getPermissionLevel());
            if (req.getNote() != null) {
                existing.setNote(req.getNote());
            }
            permissionToSave = existing;
        } else {
            permissionToSave = PermissionEntity.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(req.getPermissionLevel())
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetGroup(team)
                    .targetUserEmail(targetUser.getEmail())
                    .targetOrgCode(orgCode)
                    .folder(targetFolder)
                    .document(targetDoc)
                    .workspaceId(boundary.getWorkspaceId())
                    .grantorAuthorityTier(AuthorityTier.TEAM_MANAGER)
                    .grantedBy(currentUser)
                    .note(req.getNote())
                    .validFrom(Instant.now())
                    .build();
        }

        String recordHash = PermissionHashUtils.calculateRecordHash(permissionToSave);
        permissionToSave.setRecordHash(recordHash);
        permissionToSave.setBlockchainStatus(BlockchainStatus.PENDING);

        PermissionEntity saved = permissionRepository.save(permissionToSave);

        // Audit Logging
        String targetType = saved.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = saved.getFolder() != null ? saved.getFolder().getId() : saved.getDocument().getId();
        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("TEAM_DELEGATION_GRANT")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(saved.getDocumentFileHash())
                .folderId(saved.getFolder() != null ? saved.getFolder().getId() : (saved.getDocument() != null && saved.getDocument().getFolder() != null ? saved.getDocument().getFolder().getId() : null))
                .workspaceId(saved.getWorkspaceId())
                .groupId(team.getId())
                .spaceType(SpaceType.ORGANIZATION)
                .orgCode(orgCode)
                .actorUserId(currentUser.getId())
                .grantedBy(currentUser)
                .grantedToEmail(targetUser.getEmail())
                .permissionLevel(saved.getPermissionLevel())
                .newValue(saved.getPermissionLevel().name())
                .details("Granted " + saved.getPermissionLevel() + " delegation on " + targetType + " to " + targetUser.getEmail() + " under team " + team.getName())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Evict cache
        if (permissionCacheService != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        log.info("Team Manager {} granted {} access on {} to {} under team {}",
                currentUser.getId(), saved.getPermissionLevel(), targetType + " " + targetId, targetUser.getEmail(), team.getId());

        String resourceName = saved.getFolder() != null ? saved.getFolder().getName() : (saved.getDocument() != null ? saved.getDocument().getFileName() : null);
        String resourcePath = saved.getFolder() != null ? buildFolderPath(saved.getFolder()) : (saved.getDocument() != null && saved.getDocument().getFolder() != null ? buildFolderPath(saved.getDocument().getFolder()) + "/" + saved.getDocument().getFileName() : resourceName);

        return TeamMemberAccessResponseDto.builder()
                .permissionId(saved.getId())
                .teamId(team.getId())
                .teamName(team.getName())
                .userEmail(targetUser.getEmail())
                .userName(getUserDisplayName(targetUser))
                .resourceId(targetId)
                .resourceType(targetType)
                .resourceName(resourceName)
                .resourcePath(resourcePath)
                .permissionLevel(saved.getPermissionLevel())
                .grantorAuthorityTier(saved.getGrantorAuthorityTier())
                .grantedByUserId(currentUser.getId())
                .grantedByUserEmail(currentUser.getEmail())
                .grantedAt(saved.getCreatedAt())
                .isMigrated(false)
                .build();
    }

    @Override
    @Transactional
    public TeamMemberAccessResponseDto updateMemberAccess(String orgCode, Long teamId, Long permissionId, UpdateMemberAccessRequestDto req, UserEntity currentUser) {
        validateTeamInOrg(orgCode, teamId);
        validateManagerAuthority(currentUser, orgCode, teamId);

        GroupEntity team = getActiveTeamOrThrow(orgCode, teamId);
        PermissionEntity boundary = findActiveBoundaryForTeam(team.getId());
        if (boundary == null) {
            throw new IllegalStateException("Team does not have an active workspace boundary allocation");
        }

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        if (permission.isRevoked()) {
            throw new IllegalArgumentException("Cannot update a revoked permission");
        }

        // Invariant 4: Delegation must belong to this team!
        if (permission.getTargetGroup() == null || !permission.getTargetGroup().getId().equals(team.getId())) {
            throw new AccessDeniedException("Permission does not belong to team: " + teamId);
        }

        // Invariant 4: Team Manager cannot mutate higher authority grants!
        if (permission.getGrantorAuthorityTier() != null && permission.getGrantorAuthorityTier() != AuthorityTier.TEAM_MANAGER) {
            throw new AccessDeniedException("Cannot modify permission created with higher authority tier: " + permission.getGrantorAuthorityTier());
        }

        // Invariant 1: Ceiling enforcement
        if (req.getPermissionLevel() == null) {
            throw new IllegalArgumentException("Permission level is required");
        }
        if (req.getPermissionLevel().ordinal() > boundary.getPermissionLevel().ordinal()) {
            throw new AccessDeniedException("Requested permission level " + req.getPermissionLevel()
                    + " exceeds boundary ceiling of " + boundary.getPermissionLevel());
        }

        FolderEntity effectiveFolder = permission.getFolder() != null ? permission.getFolder() : (permission.getDocument() != null ? permission.getDocument().getFolder() : null);
        if (effectiveFolder != null && !permissionEvaluatorService.isSameOrAncestorFolder(boundary.getFolder(), effectiveFolder)) {
            throw new AccessDeniedException("Resource is outside team access boundary");
        }

        PermissionLevel oldLevel = permission.getPermissionLevel();
        permission.setPermissionLevel(req.getPermissionLevel());
        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);
        permission.setBlockchainStatus(BlockchainStatus.PENDING);

        PermissionEntity saved = permissionRepository.save(permission);

        String targetType = saved.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = saved.getFolder() != null ? saved.getFolder().getId() : (saved.getDocument() != null ? saved.getDocument().getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("TEAM_DELEGATION_UPDATE")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(saved.getDocumentFileHash())
                .folderId(saved.getFolder() != null ? saved.getFolder().getId() : (saved.getDocument() != null && saved.getDocument().getFolder() != null ? saved.getDocument().getFolder().getId() : null))
                .workspaceId(saved.getWorkspaceId())
                .groupId(team.getId())
                .spaceType(SpaceType.ORGANIZATION)
                .orgCode(orgCode)
                .actorUserId(currentUser.getId())
                .grantedBy(currentUser)
                .grantedToEmail(saved.getTargetUserEmail())
                .permissionLevel(saved.getPermissionLevel())
                .previousValue(oldLevel != null ? oldLevel.name() : null)
                .newValue(saved.getPermissionLevel().name())
                .details("Updated delegation to " + saved.getPermissionLevel() + " on " + targetType + " for " + saved.getTargetUserEmail() + " under team " + team.getName())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        if (permissionCacheService != null && saved.getTargetUserEmail() != null) {
            permissionCacheService.evictUserPermissions(saved.getTargetUserEmail());
        }

        String userName = null;
        if (saved.getTargetUserEmail() != null) {
            userName = userRepository.findByEmail(saved.getTargetUserEmail())
                    .map(this::getUserDisplayName)
                    .orElse(null);
        }

        String resourceName = saved.getFolder() != null ? saved.getFolder().getName() : (saved.getDocument() != null ? saved.getDocument().getFileName() : null);
        String resourcePath = saved.getFolder() != null ? buildFolderPath(saved.getFolder()) : (saved.getDocument() != null && saved.getDocument().getFolder() != null ? buildFolderPath(saved.getDocument().getFolder()) + "/" + saved.getDocument().getFileName() : resourceName);

        boolean isMigrated = saved.getNote() != null && saved.getNote().contains("MIGRATED_DELEGATION");

        return TeamMemberAccessResponseDto.builder()
                .permissionId(saved.getId())
                .teamId(team.getId())
                .teamName(team.getName())
                .userEmail(saved.getTargetUserEmail())
                .userName(userName)
                .resourceId(targetId)
                .resourceType(targetType)
                .resourceName(resourceName)
                .resourcePath(resourcePath)
                .permissionLevel(saved.getPermissionLevel())
                .grantorAuthorityTier(saved.getGrantorAuthorityTier())
                .grantedByUserId(saved.getGrantedBy() != null ? saved.getGrantedBy().getId() : null)
                .grantedByUserEmail(saved.getGrantedBy() != null ? saved.getGrantedBy().getEmail() : null)
                .grantedAt(saved.getCreatedAt())
                .isMigrated(isMigrated)
                .build();
    }

    @Override
    @Transactional
    public void revokeMemberAccess(String orgCode, Long teamId, Long permissionId, UserEntity currentUser) {
        validateTeamInOrg(orgCode, teamId);
        validateManagerAuthority(currentUser, orgCode, teamId);

        GroupEntity team = getActiveTeamOrThrow(orgCode, teamId);

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        if (permission.isRevoked()) {
            return;
        }

        // Invariant 4: Delegation must belong to this team!
        if (permission.getTargetGroup() == null || !permission.getTargetGroup().getId().equals(team.getId())) {
            throw new AccessDeniedException("Permission does not belong to team: " + teamId);
        }

        // Invariant 4: Team Manager cannot revoke higher authority grants!
        if (permission.getGrantorAuthorityTier() != null && permission.getGrantorAuthorityTier() != AuthorityTier.TEAM_MANAGER) {
            throw new AccessDeniedException("Cannot revoke permission created with higher authority tier: " + permission.getGrantorAuthorityTier());
        }

        permission.setRevoked(true);
        permission.setSuspended(false);
        permission.setRevokedAt(Instant.now());
        permission.setRevokedBy(currentUser);
        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);
        permission.setBlockchainStatus(BlockchainStatus.PENDING);

        permissionRepository.save(permission);

        String targetType = permission.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null ? permission.getDocument().getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("TEAM_DELEGATION_REVOKE")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(permission.getDocumentFileHash())
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null && permission.getDocument().getFolder() != null ? permission.getDocument().getFolder().getId() : null))
                .workspaceId(permission.getWorkspaceId())
                .groupId(team.getId())
                .spaceType(SpaceType.ORGANIZATION)
                .orgCode(orgCode)
                .actorUserId(currentUser.getId())
                .grantedBy(currentUser)
                .grantedToEmail(permission.getTargetUserEmail())
                .permissionLevel(permission.getPermissionLevel())
                .previousValue(permission.getPermissionLevel() != null ? permission.getPermissionLevel().name() : null)
                .details("Revoked team delegation on " + targetType + " for " + permission.getTargetUserEmail() + " under team " + team.getName())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        if (permissionCacheService != null && permission.getTargetUserEmail() != null) {
            permissionCacheService.evictUserPermissions(permission.getTargetUserEmail());
        }

        log.info("Revoked member access permission ID {} under team {} by user {}", permissionId, teamId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public ScopedResourceNodeDto getScopedResourceTree(String orgCode, Long teamId, UserEntity currentUser) {
        validateTeamInOrg(orgCode, teamId);
        validateCanViewTeam(currentUser, orgCode, teamId);

        GroupEntity team = getActiveTeamOrThrow(orgCode, teamId);
        PermissionEntity boundary = findActiveBoundaryForTeam(team.getId());
        if (boundary == null || boundary.getFolder() == null) {
            throw new IllegalStateException("Team does not have an active workspace boundary allocation");
        }

        FolderEntity root = boundary.getFolder();
        return buildScopedTreeRecursively(root, "");
    }

    private ScopedResourceNodeDto buildScopedTreeRecursively(FolderEntity currentFolder, String parentPath) {
        String currentPath = parentPath.isEmpty() ? currentFolder.getName() : parentPath + "/" + currentFolder.getName();

        ScopedResourceNodeDto node = ScopedResourceNodeDto.builder()
                .id(currentFolder.getId())
                .name(currentFolder.getName())
                .type("FOLDER")
                .relativePath(currentPath)
                .children(new ArrayList<>())
                .build();

        // Add subfolders
        List<FolderEntity> subfolders = folderRepository.findByParentFolder_Id(currentFolder.getId());
        if (subfolders != null) {
            for (FolderEntity sub : subfolders) {
                node.getChildren().add(buildScopedTreeRecursively(sub, currentPath));
            }
        }

        // Add documents
        List<DocumentEntity> docs = documentRepository.findByFolder_Id(currentFolder.getId());
        if (docs != null) {
            for (DocumentEntity doc : docs) {
                node.getChildren().add(ScopedResourceNodeDto.builder()
                        .id(doc.getId())
                        .name(doc.getFileName())
                        .type("DOCUMENT")
                        .relativePath(currentPath + "/" + doc.getFileName())
                        .children(new ArrayList<>())
                        .build());
            }
        }

        return node;
    }

    private PermissionEntity findActiveBoundaryForTeam(Long teamId) {
        List<PermissionEntity> perms = permissionRepository.findByTargetGroup_Id(teamId);
        for (PermissionEntity p : perms) {
            if (!p.isRevoked() && !p.isSuspended() && p.getFolder() != null
                    && (p.getPermissionType() == PermissionType.BOUNDARY || p.getPermissionType() == PermissionType.ALLOW)) {
                return p;
            }
        }
        return null;
    }

    private PermissionEntity findExistingDelegation(Long teamId, String targetEmail, FolderEntity folder, DocumentEntity doc) {
        List<PermissionEntity> perms = permissionRepository.findByTargetGroup_Id(teamId);
        for (PermissionEntity p : perms) {
            if (!p.isRevoked() && p.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER
                    && p.getTargetUserEmail() != null && p.getTargetUserEmail().equalsIgnoreCase(targetEmail)) {
                if (folder != null && p.getFolder() != null && p.getFolder().getId().equals(folder.getId())) {
                    return p;
                }
                if (doc != null && p.getDocument() != null && p.getDocument().getId().equals(doc.getId())) {
                    return p;
                }
            }
        }
        return null;
    }

    private GroupEntity getActiveTeamOrThrow(String orgCode, Long teamId) {
        GroupEntity team = groupRepository.findByIdAndOrganization_OrgCode(teamId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));
        if (team.getStatus() == GroupStatus.ARCHIVED) {
            throw new IllegalStateException("Team is archived: " + teamId);
        }
        return team;
    }

    private void validateTeamInOrg(String orgCode, Long teamId) {
        if (!groupRepository.findByIdAndOrganization_OrgCode(teamId, orgCode).isPresent()) {
            throw new ResourceNotFoundException("Team ID " + teamId + " does not belong to organization: " + orgCode);
        }
    }

    private void validateCanViewTeam(UserEntity user, String orgCode, Long teamId) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        if (permissionEvaluatorService.isUserAdminOfOrg(user, orgCode)) {
            return;
        }
        boolean isMemberOfTeam = userGroupRepository.findByUser_IdAndGroup_Id(user.getId(), teamId).isPresent();
        if (!isMemberOfTeam) {
            throw new AccessDeniedException("User does not have access to view team: " + teamId);
        }
    }

    private void validateManagerAuthority(UserEntity user, String orgCode, Long teamId) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        if (permissionEvaluatorService.isUserAdminOfOrg(user, orgCode)) {
            return;
        }
        boolean isManager = userGroupRepository.findByUser_IdAndGroup_Id(user.getId(), teamId)
                .filter(ug -> ug.getGroup() != null && (ug.getGroup().getStatus() == null || ug.getGroup().getStatus() == GroupStatus.ACTIVE))
                .filter(ug -> ug.getRole() == GroupMemberRole.MANAGER)
                .isPresent();
        if (!isManager) {
            throw new AccessDeniedException("User is not the active Team Manager of team: " + teamId);
        }
    }

    private String buildFolderPath(FolderEntity folder) {
        if (folder == null) return "";
        List<String> segments = new ArrayList<>();
        FolderEntity current = folder;
        while (current != null) {
            segments.add(0, current.getName());
            current = current.getParentFolder();
        }
        return "/" + String.join("/", segments);
    }

    private String getUserDisplayName(UserEntity u) {
        if (u == null) return null;
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last = u.getLastName() != null ? u.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? u.getEmail() : full;
    }
}
