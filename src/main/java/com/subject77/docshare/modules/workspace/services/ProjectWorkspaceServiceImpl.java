package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.*;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
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
public class ProjectWorkspaceServiceImpl implements ProjectWorkspaceService {

    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final FolderRepository folderRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final CapabilityEvaluatorService capabilityEvaluatorService;

    @Override
    @Transactional
    public ProjectWorkspaceResponseDto createWorkspace(CreateProjectWorkspaceRequestDto request, UserEntity currentUser) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }

        String orgCode = request.getOrgCode();
        OrganizationEntity org = organizationRepository.findById(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));

        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_WORKSPACES_MANAGE);

        if (projectWorkspaceRepository.existsByOrganization_OrgCodeAndName(orgCode, request.getName().trim())) {
            throw new IllegalArgumentException("Workspace with name '" + request.getName() + "' already exists in organization: " + orgCode);
        }

        FolderEntity rootFolder;
        if (request.getRootFolderId() != null) {
            rootFolder = folderRepository.findById(request.getRootFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Root folder not found with ID: " + request.getRootFolderId()));

            if (rootFolder.getOwnerOrg() == null || !rootFolder.getOwnerOrg().getOrgCode().equals(orgCode)) {
                throw new IllegalArgumentException("Root folder does not belong to organization: " + orgCode);
            }
            if (rootFolder.getParentFolder() != null) {
                throw new IllegalArgumentException("Specified root folder must be a top-level root folder");
            }
            if (projectWorkspaceRepository.existsByRootFolder_Id(rootFolder.getId())) {
                throw new IllegalArgumentException("Root folder ID " + rootFolder.getId() + " is already assigned to another workspace");
            }
        } else {
            Optional<FolderEntity> existingFolderOpt = folderRepository
                    .findByNameAndParentFolderIsNullAndOwnerOrg_OrgCode(request.getName().trim(), orgCode);

            if (existingFolderOpt.isPresent() && !projectWorkspaceRepository.existsByRootFolder_Id(existingFolderOpt.get().getId())) {
                rootFolder = existingFolderOpt.get();
            } else {
                boolean isFirstWorkspace = !projectWorkspaceRepository.existsByOrganization_OrgCode(orgCode);
                FolderEntity.FolderEntityBuilder folderBuilder = FolderEntity.builder()
                        .name(request.getName().trim())
                        .parentFolder(null)
                        .ownerOrg(org)
                        .createdBy(currentUser)
                        .isDefaultSubspace(isFirstWorkspace);

                if (isFirstWorkspace) {
                    folderBuilder.subspaceType(SubspaceType.ORGANIZATION_GENERAL);
                }

                FolderEntity newFolder = folderBuilder.build();
                rootFolder = folderRepository.save(newFolder);
                log.info("Created root folder '{}' (ID: {}, isDefaultSubspace: {}) for new workspace in org {}",
                        rootFolder.getName(), rootFolder.getId(), rootFolder.isDefaultSubspace(), orgCode);
            }
        }

        ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                .organization(org)
                .name(request.getName().trim())
                .description(request.getDescription())
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(currentUser)
                .build();

        ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
        log.info("Created ProjectWorkspace '{}' (ID: {}) in org {} by user {}", saved.getName(), saved.getId(), orgCode, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "WORKSPACE_CREATED",
                    "WORKSPACE",
                    saved.getId(),
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    null,
                    null,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public ProjectWorkspaceResponseDto createPersonalWorkspace(CreatePersonalWorkspaceRequestDto request, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to create a personal workspace");
        }
        if (request == null || request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Workspace name is required");
        }

        String name = request.getName().trim();
        if (projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNull(currentUser.getId(), name)) {
            throw new IllegalArgumentException("Personal workspace with name '" + name + "' already exists");
        }

        FolderEntity rootFolder;
        if (request.getRootFolderId() != null) {
            rootFolder = folderRepository.findById(request.getRootFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Root folder not found with ID: " + request.getRootFolderId()));

            if (rootFolder.getOwnerOrg() != null || !rootFolder.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new IllegalArgumentException("Root folder does not belong to user's personal space");
            }
            if (rootFolder.getParentFolder() != null) {
                throw new IllegalArgumentException("Specified root folder must be a top-level root folder");
            }
            if (projectWorkspaceRepository.existsByRootFolder_Id(rootFolder.getId())) {
                throw new IllegalArgumentException("Root folder ID " + rootFolder.getId() + " is already assigned to another workspace");
            }
        } else {
            boolean isFirst = !projectWorkspaceRepository.existsByCreatedBy_IdAndOrganizationIsNull(currentUser.getId());
            FolderEntity newFolder = FolderEntity.builder()
                    .name(name)
                    .parentFolder(null)
                    .ownerOrg(null)
                    .createdBy(currentUser)
                    .subspaceType(SubspaceType.PERSONAL)
                    .isDefaultSubspace(isFirst)
                    .build();
            rootFolder = folderRepository.save(newFolder);
            log.info("Created personal root folder '{}' (ID: {}, isDefault: {}) by user {}",
                    rootFolder.getName(), rootFolder.getId(), rootFolder.isDefaultSubspace(), currentUser.getId());
        }

        ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                .organization(null)
                .name(name)
                .description(request.getDescription())
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(currentUser)
                .build();

        ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
        log.info("Created Personal ProjectWorkspace '{}' (ID: {}) by user {}", saved.getName(), saved.getId(), currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "WORKSPACE_CREATED",
                    "WORKSPACE",
                    saved.getId(),
                    null,
                    null,
                    null,
                    null,
                    currentUser,
                    null,
                    null,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public List<ProjectWorkspaceResponseDto> getPersonalWorkspaces(UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to view personal workspaces");
        }

        List<ProjectWorkspaceEntity> workspaces =
                projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(currentUser.getId());

        if (workspaces.isEmpty()) {
            ensureDefaultPersonalWorkspace(currentUser);
            workspaces = projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(currentUser.getId());
        }

        return workspaces.stream()
                .filter(w -> w.getStatus() == ProjectWorkspaceStatus.ACTIVE)
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProjectWorkspaceResponseDto ensureDefaultPersonalWorkspace(UserEntity user) {
        if (user == null || user.getId() == null) {
            return null;
        }

        List<ProjectWorkspaceEntity> existingWorkspaces =
                projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(user.getId());

        for (ProjectWorkspaceEntity ws : existingWorkspaces) {
            if (ws.getStatus() == ProjectWorkspaceStatus.ACTIVE && ws.getRootFolder() != null) {
                return mapToResponseDto(ws);
            }
        }

        FolderEntity rootFolder = folderRepository
                .findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(user.getId())
                .orElse(null);

        if (rootFolder == null) {
            List<FolderEntity> roots = folderRepository
                    .findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(user.getId());
            if (!roots.isEmpty()) {
                rootFolder = roots.get(0);
                if (!rootFolder.isDefaultSubspace()) {
                    rootFolder.setDefaultSubspace(true);
                    rootFolder = folderRepository.save(rootFolder);
                }
            }
        }

        if (rootFolder != null) {
            if (projectWorkspaceRepository.existsByRootFolder_Id(rootFolder.getId())) {
                return projectWorkspaceRepository.findFirstByRootFolder_Id(rootFolder.getId())
                        .map(this::mapToResponseDto)
                        .orElse(null);
            }

            String name = (rootFolder.getName() != null && !rootFolder.getName().isBlank())
                    ? rootFolder.getName() : "Personal";
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name(name)
                    .description("Default personal workspace")
                    .rootFolderId(rootFolder.getId())
                    .build();
            return createPersonalWorkspace(request, user);
        } else {
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name("Personal")
                    .description("Default personal workspace")
                    .build();
            return createPersonalWorkspace(request, user);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectWorkspaceResponseDto getWorkspaceById(Long id, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + id));

        if (workspace.isPersonal()) {
            if (currentUser == null) {
                throw new AccessDeniedException("Authentication required to access personal workspace");
            }
            boolean isOwner = workspace.getCreatedBy().getId().equals(currentUser.getId());
            if (!isOwner) {
                PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(currentUser, workspace.getRootFolder());
                if (level == null) {
                    throw new AccessDeniedException("User does not have access to this personal workspace");
                }
            }
            return mapToResponseDto(workspace);
        }

        String orgCode = workspace.getOrganization().getOrgCode();
        validateUserInOrganization(currentUser.getId(), orgCode);

        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return mapToResponseDto(workspace);
        }
        
        Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), orgCode);
        if (uo.isPresent() && uo.get().getRole() == Role.ROLE_ORG_ADMIN) {
            return mapToResponseDto(workspace);
        }
        
        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(currentUser, workspace.getRootFolder());
        if (level == null) {
            throw new AccessDeniedException("User does not have access to this workspace");
        }

        return mapToResponseDto(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectWorkspaceResponseDto> getWorkspacesForOrganization(String orgCode, UserEntity currentUser) {
        validateUserInOrganization(currentUser.getId(), orgCode);

        List<ProjectWorkspaceEntity> workspaces = projectWorkspaceRepository.findByOrganization_OrgCode(orgCode);

        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return workspaces.stream().map(this::mapToResponseDto).collect(Collectors.toList());
        }
        
        if (capabilityEvaluatorService.hasCapability(currentUser, orgCode, OrganizationCapability.ORG_SETTINGS_MANAGE)) {
            return workspaces.stream().map(this::mapToResponseDto).collect(Collectors.toList());
        }

        List<FolderEntity> rootFolders = workspaces.stream()
                .map(ProjectWorkspaceEntity::getRootFolder)
                .collect(Collectors.toList());
        Map<Long, PermissionLevel> permissions = permissionEvaluatorService.resolveMaxPermissionLevelsForFolders(currentUser, rootFolders);

        return workspaces.stream()
                .filter(w -> w.getRootFolder() != null && permissions.containsKey(w.getRootFolder().getId()))
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProjectWorkspaceResponseDto updateWorkspace(Long id, UpdateProjectWorkspaceRequestDto request, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + id));

        if (workspace.isPersonal()) {
            if (currentUser == null || !workspace.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the owner can update this personal workspace");
            }

            validateWorkspaceActive(workspace);

            if (request.getName() != null && !request.getName().trim().isEmpty() && !request.getName().trim().equals(workspace.getName())) {
                String newName = request.getName().trim();
                if (projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNullAndIdNot(currentUser.getId(), newName, id)) {
                    throw new IllegalArgumentException("Personal workspace with name '" + newName + "' already exists");
                }
                workspace.setName(newName);
                if (workspace.getRootFolder() != null) {
                    workspace.getRootFolder().setName(newName);
                    folderRepository.save(workspace.getRootFolder());
                }
            }

            if (request.getDescription() != null) {
                workspace.setDescription(request.getDescription());
            }

            ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
            log.info("Updated Personal ProjectWorkspace ID {} by user {}", id, currentUser.getId());

            if (auditLogService != null) {
                auditLogService.logPermissionChange(
                        "WORKSPACE_UPDATED",
                        "WORKSPACE",
                        saved.getId(),
                        null,
                        null,
                        null,
                        null,
                        currentUser,
                        null,
                        null,
                        PermissionLevel.VIEW,
                        null,
                        null
                );
            }

            return mapToResponseDto(saved);
        }

        String orgCode = workspace.getOrganization().getOrgCode();
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_WORKSPACES_MANAGE);
        validateWorkspaceActive(workspace);

        if (request.getName() != null && !request.getName().trim().isEmpty() && !request.getName().trim().equals(workspace.getName())) {
            String newName = request.getName().trim();
            if (projectWorkspaceRepository.existsByOrganization_OrgCodeAndNameAndIdNot(orgCode, newName, id)) {
                throw new IllegalArgumentException("Workspace with name '" + newName + "' already exists in organization: " + orgCode);
            }
            workspace.setName(newName);
            if (workspace.getRootFolder() != null) {
                workspace.getRootFolder().setName(newName);
                folderRepository.save(workspace.getRootFolder());
            }
        }

        if (request.getDescription() != null) {
            workspace.setDescription(request.getDescription());
        }

        ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
        log.info("Updated ProjectWorkspace ID {} in org {} by user {}", id, orgCode, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "WORKSPACE_UPDATED",
                    "WORKSPACE",
                    saved.getId(),
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    null,
                    null,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public ProjectWorkspaceResponseDto archiveWorkspace(Long id, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + id));

        if (workspace.isPersonal()) {
            if (currentUser == null || !workspace.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the owner can archive this personal workspace");
            }

            validateWorkspaceActive(workspace);

            workspace.setStatus(ProjectWorkspaceStatus.ARCHIVED);
            ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
            log.info("Archived Personal ProjectWorkspace ID {} by user {}", id, currentUser.getId());

            if (auditLogService != null) {
                auditLogService.logPermissionChange(
                        "WORKSPACE_ARCHIVED",
                        "WORKSPACE",
                        saved.getId(),
                        null,
                        null,
                        null,
                        null,
                        currentUser,
                        null,
                        null,
                        PermissionLevel.VIEW,
                        null,
                        null
                );
            }

            return mapToResponseDto(saved);
        }

        String orgCode = workspace.getOrganization().getOrgCode();
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_WORKSPACES_MANAGE);
        validateWorkspaceActive(workspace);

        workspace.setStatus(ProjectWorkspaceStatus.ARCHIVED);
        ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
        log.info("Archived ProjectWorkspace ID {} in org {} by user {}", id, orgCode, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "WORKSPACE_ARCHIVED",
                    "WORKSPACE",
                    saved.getId(),
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    null,
                    null,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public ProjectWorkspaceResponseDto restoreWorkspace(Long id, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + id));

        if (workspace.isPersonal()) {
            if (currentUser == null || !workspace.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the owner can restore this personal workspace");
            }

            if (workspace.getStatus() == ProjectWorkspaceStatus.ACTIVE) {
                throw new IllegalStateException("Workspace is already active: " + id);
            }

            workspace.setStatus(ProjectWorkspaceStatus.ACTIVE);
            ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
            log.info("Restored Personal ProjectWorkspace ID {} by user {}", id, currentUser.getId());

            if (auditLogService != null) {
                auditLogService.logPermissionChange(
                        "WORKSPACE_RESTORED",
                        "WORKSPACE",
                        saved.getId(),
                        null,
                        null,
                        null,
                        null,
                        currentUser,
                        null,
                        null,
                        PermissionLevel.VIEW,
                        null,
                        null
                );
            }

            return mapToResponseDto(saved);
        }

        String orgCode = workspace.getOrganization().getOrgCode();
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_WORKSPACES_MANAGE);

        if (workspace.getStatus() == ProjectWorkspaceStatus.ACTIVE) {
            throw new IllegalStateException("Workspace is already active: " + id);
        }

        workspace.setStatus(ProjectWorkspaceStatus.ACTIVE);
        ProjectWorkspaceEntity saved = projectWorkspaceRepository.save(workspace);
        log.info("Restored ProjectWorkspace ID {} in org {} by user {}", id, orgCode, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "WORKSPACE_RESTORED",
                    "WORKSPACE",
                    saved.getId(),
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    null,
                    null,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public WorkspaceAssignmentResponseDto assignTeamToWorkspace(Long workspaceId, WorkspaceTeamAssignmentRequestDto request, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));

        if (workspace.isPersonal()) {
            throw new IllegalArgumentException("Team assignments are not supported for personal workspaces");
        }

        validateWorkspaceActive(workspace);

        String orgCode = workspace.getOrganization().getOrgCode();

        Long targetFolderId = request.getFolderId() != null ? request.getFolderId() : workspace.getRootFolder().getId();
        FolderEntity targetFolder = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + targetFolderId));

        if (!isSameOrAncestorFolder(workspace.getRootFolder(), targetFolder)) {
            throw new IllegalArgumentException("Target folder ID " + targetFolderId + " is not within workspace root folder hierarchy");
        }

        if (targetFolder.getOwnerOrg() == null || !targetFolder.getOwnerOrg().getOrgCode().equalsIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Target folder does not belong to organization: " + orgCode);
        }

        GroupEntity team = groupRepository.findById(request.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + request.getTeamId()));

        if (!team.getOrganization().getOrgCode().equalsIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Team ID " + request.getTeamId() + " does not belong to organization: " + orgCode);
        }

        if (team.getStatus() == GroupStatus.ARCHIVED) {
            throw new IllegalStateException("Archived team cannot be assigned permissions in workspace");
        }

        validateCanManageWorkspacePermissions(currentUser, workspace, targetFolder);

        // Team allocation defines the Team Access Boundary and Permission Ceiling (Zero-Default Content Access)
        PermissionRequestDto permRequest = PermissionRequestDto.builder()
                .permissionType(PermissionType.BOUNDARY)
                .permissionLevel(request.getPermissionLevel())
                .folderId(targetFolder.getId())
                .targetGroupId(team.getId())
                .recipientType(RecipientType.GROUP)
                .workspaceId(workspace.getId())
                .build();

        PermissionResponseDto permResponse = permissionService.grantPermission(permRequest, currentUser.getId());

        // Check existing delegations for this team to enforce narrowing & ceiling clamping
        List<PermissionEntity> existingDelegations = permissionRepository.findByTargetGroup_Id(team.getId()).stream()
                .filter(p -> !p.isRevoked() && !p.isSuspended())
                .filter(p -> p.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER)
                .collect(Collectors.toList());

        for (PermissionEntity d : existingDelegations) {
            FolderEntity dFolder = d.getFolder() != null ? d.getFolder() : (d.getDocument() != null ? d.getDocument().getFolder() : null);
            if (dFolder != null && !isSameOrAncestorFolder(targetFolder, dFolder)) {
                // Out of boundary -> revoke
                d.setRevoked(true);
                d.setRevokedAt(Instant.now());
                d.setRevokedBy(currentUser);
                d.setNote("REVOKED_DUE_TO_BOUNDARY_CHANGE: newRoot=" + targetFolder.getId());
                d.setRecordHash(com.subject77.docshare.modules.permission.utils.PermissionHashUtils.calculateRecordHash(d));
                permissionRepository.save(d);

                if (auditLogService != null) {
                    auditLogService.logPermissionChange(
                            AuditPermissionChangeCommand.builder()
                                    .action("TEAM_DELEGATION_REVOKE")
                                    .targetType(d.getFolder() != null ? "FOLDER" : "DOCUMENT")
                                    .targetId(d.getFolder() != null ? d.getFolder().getId() : (d.getDocument() != null ? d.getDocument().getId() : null))
                                    .targetFileHash(d.getDocumentFileHash())
                                    .folderId(d.getFolder() != null ? d.getFolder().getId() : (d.getDocument() != null && d.getDocument().getFolder() != null ? d.getDocument().getFolder().getId() : null))
                                    .workspaceId(workspace.getId())
                                    .groupId(team.getId())
                                    .spaceType(SpaceType.ORGANIZATION)
                                    .orgCode(orgCode)
                                    .actorUserId(currentUser.getId())
                                    .grantedBy(currentUser)
                                    .grantedToEmail(d.getTargetUserEmail())
                                    .permissionLevel(d.getPermissionLevel())
                                    .previousValue(d.getPermissionLevel() != null ? d.getPermissionLevel().name() : null)
                                    .reason("REVOKED_DUE_TO_BOUNDARY_CHANGE")
                                    .details("Cascade revoked team delegation outside updated boundary for team ID " + team.getId())
                                    .recordHash(d.getRecordHash())
                                    .timestamp(Instant.now())
                                    .build()
                    );
                }
            } else if (d.getPermissionLevel().ordinal() > request.getPermissionLevel().ordinal()) {
                // Ceiling Clamping
                PermissionLevel oldLevel = d.getPermissionLevel();
                d.setPermissionLevel(request.getPermissionLevel());
                d.setRecordHash(com.subject77.docshare.modules.permission.utils.PermissionHashUtils.calculateRecordHash(d));
                permissionRepository.save(d);

                if (auditLogService != null) {
                    auditLogService.logPermissionChange(
                            AuditPermissionChangeCommand.builder()
                                    .action("TEAM_DELEGATION_UPDATE")
                                    .targetType(d.getFolder() != null ? "FOLDER" : "DOCUMENT")
                                    .targetId(d.getFolder() != null ? d.getFolder().getId() : (d.getDocument() != null ? d.getDocument().getId() : null))
                                    .targetFileHash(d.getDocumentFileHash())
                                    .folderId(d.getFolder() != null ? d.getFolder().getId() : (d.getDocument() != null && d.getDocument().getFolder() != null ? d.getDocument().getFolder().getId() : null))
                                    .workspaceId(workspace.getId())
                                    .groupId(team.getId())
                                    .spaceType(SpaceType.ORGANIZATION)
                                    .orgCode(orgCode)
                                    .actorUserId(currentUser.getId())
                                    .grantedBy(currentUser)
                                    .grantedToEmail(d.getTargetUserEmail())
                                    .permissionLevel(request.getPermissionLevel())
                                    .previousValue(oldLevel != null ? oldLevel.name() : null)
                                    .newValue(request.getPermissionLevel().name())
                                    .reason("CEILING_CLAMPED")
                                    .details("Clamped delegation level from " + oldLevel + " to ceiling " + request.getPermissionLevel() + " for team ID " + team.getId())
                                    .recordHash(d.getRecordHash())
                                    .timestamp(Instant.now())
                                    .build()
                    );
                }
            }
        }

        log.info("Assigned Team ID {} to Workspace ID {} (folder ID {}) with BOUNDARY ceiling level {}",
                team.getId(), workspaceId, targetFolder.getId(), request.getPermissionLevel());

        return WorkspaceAssignmentResponseDto.builder()
                .permissionId(permResponse.getId())
                .workspaceId(workspace.getId())
                .folderId(targetFolder.getId())
                .folderName(targetFolder.getName())
                .teamId(team.getId())
                .teamName(team.getName())
                .userEmail(null)
                .permissionLevel(permResponse.getPermissionLevel())
                .permissionType(permResponse.getPermissionType())
                .grantedByUserId(currentUser.getId())
                .grantedByUserEmail(currentUser.getEmail())
                .grantedAt(permResponse.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public WorkspaceAssignmentResponseDto assignUserToWorkspace(Long workspaceId, WorkspaceUserAssignmentRequestDto request, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));

        validateWorkspaceActive(workspace);

        Long targetFolderId = request.getFolderId() != null ? request.getFolderId() : workspace.getRootFolder().getId();
        FolderEntity targetFolder = folderRepository.findById(targetFolderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + targetFolderId));

        if (!isSameOrAncestorFolder(workspace.getRootFolder(), targetFolder)) {
            throw new IllegalArgumentException("Target folder ID " + targetFolderId + " is not within workspace root folder hierarchy");
        }

        UserEntity targetUser = userRepository.findByEmail(request.getUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found with email: " + request.getUserEmail()));

        if (workspace.isPersonal()) {
            if (currentUser == null || !workspace.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the owner can assign user permissions in a personal workspace");
            }
            if (targetFolder.getOwnerOrg() != null || !targetFolder.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new IllegalArgumentException("Target folder does not belong to personal workspace");
            }

            PermissionRequestDto permRequest = PermissionRequestDto.builder()
                    .permissionType(request.getPermissionType() != null ? request.getPermissionType() : PermissionType.ALLOW)
                    .permissionLevel(request.getPermissionLevel())
                    .folderId(targetFolder.getId())
                    .targetUserEmail(targetUser.getEmail())
                    .targetOrgCode(null)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .workspaceId(workspace.getId())
                    .build();

            PermissionResponseDto permResponse = permissionService.grantPermission(permRequest, currentUser.getId());

            log.info("Assigned User {} to Personal Workspace ID {} (folder ID {}) with level {}",
                    targetUser.getEmail(), workspaceId, targetFolder.getId(), request.getPermissionLevel());

            return WorkspaceAssignmentResponseDto.builder()
                    .permissionId(permResponse.getId())
                    .workspaceId(workspace.getId())
                    .folderId(targetFolder.getId())
                    .folderName(targetFolder.getName())
                    .teamId(null)
                    .teamName(null)
                    .userEmail(targetUser.getEmail())
                    .permissionLevel(permResponse.getPermissionLevel())
                    .permissionType(permResponse.getPermissionType())
                    .grantedByUserId(currentUser.getId())
                    .grantedByUserEmail(currentUser.getEmail())
                    .grantedAt(permResponse.getCreatedAt())
                    .build();
        }

        String orgCode = workspace.getOrganization().getOrgCode();

        if (targetFolder.getOwnerOrg() == null || !targetFolder.getOwnerOrg().getOrgCode().equalsIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Target folder does not belong to organization: " + orgCode);
        }

        boolean isMember = (targetUser.getOrganization() != null && targetUser.getOrganization().getOrgCode().equalsIgnoreCase(orgCode))
                || userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(targetUser.getId(), orgCode);
        if (!isMember) {
            throw new IllegalArgumentException("User " + request.getUserEmail() + " is not a member of organization: " + orgCode);
        }

        validateCanManageWorkspacePermissions(currentUser, workspace, targetFolder);

        PermissionRequestDto permRequest = PermissionRequestDto.builder()
                .permissionType(request.getPermissionType() != null ? request.getPermissionType() : PermissionType.ALLOW)
                .permissionLevel(request.getPermissionLevel())
                .folderId(targetFolder.getId())
                .targetUserEmail(targetUser.getEmail())
                .targetOrgCode(orgCode)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .workspaceId(workspace.getId())
                .build();

        PermissionResponseDto permResponse = permissionService.grantPermission(permRequest, currentUser.getId());

        log.info("Assigned User {} to Workspace ID {} (folder ID {}) with level {}",
                targetUser.getEmail(), workspaceId, targetFolder.getId(), request.getPermissionLevel());

        return WorkspaceAssignmentResponseDto.builder()
                .permissionId(permResponse.getId())
                .workspaceId(workspace.getId())
                .folderId(targetFolder.getId())
                .folderName(targetFolder.getName())
                .teamId(null)
                .teamName(null)
                .userEmail(targetUser.getEmail())
                .permissionLevel(permResponse.getPermissionLevel())
                .permissionType(permResponse.getPermissionType())
                .grantedByUserId(currentUser.getId())
                .grantedByUserEmail(currentUser.getEmail())
                .grantedAt(permResponse.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void revokeWorkspaceAssignment(Long workspaceId, Long permissionId, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));

        validateWorkspaceActive(workspace);

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission assignment not found with ID: " + permissionId));

        if (permission.getFolder() == null || !isSameOrAncestorFolder(workspace.getRootFolder(), permission.getFolder())) {
            throw new IllegalArgumentException("Permission ID " + permissionId + " is not associated with workspace ID " + workspaceId);
        }

        if (workspace.isPersonal()) {
            if (currentUser == null || !workspace.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the owner can revoke workspace assignments in a personal workspace");
            }
        } else {
            validateCanManageWorkspacePermissions(currentUser, workspace, permission.getFolder());
        }

        // If revoking a Team Boundary allocation, cascade revoke all active member delegations
        if (permission.getTargetGroup() != null) {
            Long teamId = permission.getTargetGroup().getId();
            FolderEntity boundaryFolder = permission.getFolder();
            List<PermissionEntity> memberDelegations = permissionRepository.findByTargetGroup_Id(teamId);
            for (PermissionEntity d : memberDelegations) {
                if (!d.isRevoked() && d.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER) {
                    FolderEntity dFolder = d.getFolder() != null ? d.getFolder() : (d.getDocument() != null ? d.getDocument().getFolder() : null);
                    if (dFolder != null && boundaryFolder != null && isSameOrAncestorFolder(boundaryFolder, dFolder)) {
                        d.setRevoked(true);
                        d.setRevokedAt(Instant.now());
                        d.setRevokedBy(currentUser);
                        d.setNote("REVOKED_DUE_TO_BOUNDARY_REVOCATION: boundaryPermId=" + permissionId);
                        d.setRecordHash(com.subject77.docshare.modules.permission.utils.PermissionHashUtils.calculateRecordHash(d));
                        permissionRepository.save(d);

                        if (auditLogService != null) {
                            auditLogService.logPermissionChange(
                                    AuditPermissionChangeCommand.builder()
                                            .action("TEAM_DELEGATION_REVOKE")
                                            .targetType(d.getFolder() != null ? "FOLDER" : "DOCUMENT")
                                            .targetId(d.getFolder() != null ? d.getFolder().getId() : (d.getDocument() != null ? d.getDocument().getId() : null))
                                            .targetFileHash(d.getDocumentFileHash())
                                            .folderId(d.getFolder() != null ? d.getFolder().getId() : (d.getDocument() != null && d.getDocument().getFolder() != null ? d.getDocument().getFolder().getId() : null))
                                            .workspaceId(workspaceId)
                                            .groupId(teamId)
                                            .spaceType(SpaceType.ORGANIZATION)
                                            .orgCode(workspace.getOrganization() != null ? workspace.getOrganization().getOrgCode() : null)
                                            .actorUserId(currentUser.getId())
                                            .grantedBy(currentUser)
                                            .grantedToEmail(d.getTargetUserEmail())
                                            .permissionLevel(d.getPermissionLevel())
                                            .previousValue(d.getPermissionLevel() != null ? d.getPermissionLevel().name() : null)
                                            .reason("BOUNDARY_REVOKED")
                                            .details("Cascade revoked team delegation due to workspace boundary deallocation for team ID " + teamId)
                                            .recordHash(d.getRecordHash())
                                            .timestamp(Instant.now())
                                            .build()
                            );
                        }
                    }
                }
            }
        }

        permissionService.revokePermission(permissionId, currentUser.getId());

        log.info("Revoked Workspace Assignment (Permission ID {}) in Workspace ID {} by user {}",
                permissionId, workspaceId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceAssignmentResponseDto> getWorkspaceAssignments(Long workspaceId, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = projectWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));

        if (workspace.isPersonal()) {
            if (currentUser == null || !workspace.getCreatedBy().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the owner can view assignments in a personal workspace");
            }
        } else {
            validateUserInOrganization(currentUser.getId(), workspace.getOrganization().getOrgCode());
        }

        List<FolderEntity> allFolders = getAllFoldersInWorkspace(workspace.getRootFolder());
        List<WorkspaceAssignmentResponseDto> assignments = new ArrayList<>();

        for (FolderEntity folder : allFolders) {
            List<PermissionEntity> perms = permissionRepository.findByFolder_IdAndRevokedFalse(folder.getId());
            for (PermissionEntity perm : perms) {
                if (perm.getCollaboration() == null) {
                    assignments.add(mapToAssignmentResponseDto(perm, workspace.getId()));
                }
            }
        }

        return assignments;
    }

    private void validateCanManageWorkspacePermissions(UserEntity user, ProjectWorkspaceEntity workspace, FolderEntity targetFolder) {
        if (user == null || workspace == null) {
            throw new AccessDeniedException("User does not have permission to manage workspace assignments");
        }
        if (workspace.isPersonal()) {
            if (!workspace.getCreatedBy().getId().equals(user.getId())) {
                throw new AccessDeniedException("Only the owner can manage personal workspace permissions");
            }
            return;
        }

        String orgCode = workspace.getOrganization().getOrgCode();

        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }

        Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode);
        if (uo.isPresent() && (uo.get().getRole() == Role.ROLE_ORG_ADMIN || uo.get().getRole() == Role.ROLE_SUPER_ADMIN)) {
            return;
        }

        if (permissionEvaluatorService != null && permissionEvaluatorService.canAdministerFolderPermissions(user, targetFolder)) {
            return;
        }

        throw new AccessDeniedException("User does not have permission to manage workspace assignments in organization: " + orgCode);
    }

    private boolean isSameOrAncestorFolder(FolderEntity ancestor, FolderEntity current) {
        if (ancestor == null || current == null) return false;
        FolderEntity cursor = current;
        while (cursor != null) {
            if (cursor.getId() != null && cursor.getId().equals(ancestor.getId())) {
                return true;
            }
            cursor = cursor.getParentFolder();
        }
        return false;
    }

    private List<FolderEntity> getAllFoldersInWorkspace(FolderEntity rootFolder) {
        List<FolderEntity> result = new ArrayList<>();
        if (rootFolder == null) return result;
        Queue<FolderEntity> queue = new LinkedList<>();
        queue.add(rootFolder);
        while (!queue.isEmpty()) {
            FolderEntity current = queue.poll();
            result.add(current);
            List<FolderEntity> children = folderRepository.findByParentFolder_Id(current.getId());
            if (children != null) {
                queue.addAll(children);
            }
        }
        return result;
    }

    private WorkspaceAssignmentResponseDto mapToAssignmentResponseDto(PermissionEntity perm, Long workspaceId) {
        return WorkspaceAssignmentResponseDto.builder()
                .permissionId(perm.getId())
                .workspaceId(workspaceId)
                .folderId(perm.getFolder() != null ? perm.getFolder().getId() : null)
                .folderName(perm.getFolder() != null ? perm.getFolder().getName() : null)
                .teamId(perm.getTargetGroup() != null ? perm.getTargetGroup().getId() : null)
                .teamName(perm.getTargetGroup() != null ? perm.getTargetGroup().getName() : null)
                .userEmail(perm.getTargetUserEmail())
                .permissionLevel(perm.getPermissionLevel())
                .permissionType(perm.getPermissionType())
                .grantedByUserId(perm.getGrantedBy() != null ? perm.getGrantedBy().getId() : null)
                .grantedByUserEmail(perm.getGrantedBy() != null ? perm.getGrantedBy().getEmail() : null)
                .grantedAt(perm.getCreatedAt())
                .build();
    }

    private void validateWorkspaceActive(ProjectWorkspaceEntity workspace) {
        if (workspace.getStatus() == ProjectWorkspaceStatus.ARCHIVED) {
            throw new IllegalStateException("Workspace is archived and cannot be modified");
        }
    }

    private void validateUserInOrganization(String userId, String orgCode) {
        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(userId, orgCode)) {
            throw new AccessDeniedException("User is not a member of organization: " + orgCode);
        }
    }

    private void validateOrgAdminPermission(String userId, String orgCode) {
        UserOrganizationEntity membership = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(userId, orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (membership.getRole() != Role.ROLE_ORG_ADMIN && membership.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Organization Admin privileges required in organization: " + orgCode);
        }
    }

    private ProjectWorkspaceResponseDto mapToResponseDto(ProjectWorkspaceEntity workspace) {
        return ProjectWorkspaceResponseDto.builder()
                .id(workspace.getId())
                .orgCode(workspace.getOrganization() != null ? workspace.getOrganization().getOrgCode() : null)
                .name(workspace.getName())
                .description(workspace.getDescription())
                .rootFolderId(workspace.getRootFolder() != null ? workspace.getRootFolder().getId() : null)
                .rootFolderName(workspace.getRootFolder() != null ? workspace.getRootFolder().getName() : null)
                .status(workspace.getStatus())
                .createdByUserId(workspace.getCreatedBy() != null ? workspace.getCreatedBy().getId() : null)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }
}
