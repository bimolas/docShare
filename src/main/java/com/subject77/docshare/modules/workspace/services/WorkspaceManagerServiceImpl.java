package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.WorkspaceManagerResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceManagerServiceImpl implements WorkspaceManagerService {

    private final WorkspaceManagerRepository workspaceManagerRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final PermissionCacheService permissionCacheService;

    // -------------------------------------------------------------------------
    // Assign
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public WorkspaceManagerResponseDto assignWorkspaceManager(Long workspaceId, String targetUserId, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = requireWorkspace(workspaceId);
        requireActiveWorkspace(workspace);

        String orgCode = workspace.getOrganization().getOrgCode();

        // Caller must be org admin
        requireOrgAdmin(currentUser, orgCode);

        // Target user must exist and be an active org member
        UserEntity targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        requireActiveMember(targetUser, orgCode);

        // Workspace manager cannot self-assign (admin does the assigning; target != caller)
        // The rule also means a workspace manager cannot assign themselves via this path,
        // but since only org admins can call this method, we prevent the admin from
        // accidentally assigning themselves as workspace manager (which would be redundant
        // but not a security hole). We enforce the spirit: targetUser != "a workspace manager
        // trying to self-promote". If caller is also the target it is fine (org admin assigning
        // themselves to a workspace is allowed by design; the spec says workspace managers
        // cannot self-assign via the workspace manager role, implying only org admins can assign).
        // Re-reading: "Workspace Manager → assign themselves" is DENIED. The action is
        // "assign workspace manager". An org admin is NOT a workspace manager in the workspace
        // manager sense (unless previously assigned). So no extra check is needed here as long
        // as only org admins can reach this method.

        // Prevent duplicates
        if (workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(workspaceId, targetUserId)) {
            throw new IllegalArgumentException("User " + targetUserId + " is already a manager of workspace " + workspaceId);
        }

        WorkspaceManagerEntity entity = WorkspaceManagerEntity.builder()
                .workspace(workspace)
                .user(targetUser)
                .build();

        WorkspaceManagerEntity saved = workspaceManagerRepository.save(entity);
        log.info("Assigned user {} as workspace manager of workspace {} by {}", targetUserId, workspaceId, currentUser.getId());

        // Audit
        auditLogService.logPermissionChange(
                "WORKSPACE_MANAGER_ASSIGNED",
                "WORKSPACE",
                workspaceId,
                null,
                null,
                null,
                orgCode,
                currentUser,
                targetUser.getEmail(),
                null,
                PermissionLevel.EDIT,
                null,
                null
        );

        // Notification to the newly assigned manager
        String assignedKey = "WORKSPACE_MANAGER_ASSIGNED:" + workspaceId + ":" + targetUser.getId();
        notificationService.createNotification(
                targetUser,
                currentUser,
                NotificationType.WORKSPACE_MANAGER_ASSIGNED,
                "Workspace Manager Role Assigned",
                "You have been assigned as a manager of workspace: " + workspace.getName(),
                NotificationSourceType.SYSTEM,
                "workspace-" + workspaceId,
                null,
                null,
                assignedKey
        );

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        return toResponseDto(saved);
    }

    // -------------------------------------------------------------------------
    // Remove
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void removeWorkspaceManager(Long workspaceId, String targetUserId, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = requireWorkspace(workspaceId);
        String orgCode = workspace.getOrganization().getOrgCode();

        // Caller must be org admin
        requireOrgAdmin(currentUser, orgCode);

        WorkspaceManagerEntity assignment = workspaceManagerRepository
                .findByWorkspace_IdAndUser_Id(workspaceId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Workspace manager assignment not found for user " + targetUserId + " in workspace " + workspaceId));

        UserEntity removedManager = assignment.getUser();

        workspaceManagerRepository.delete(assignment);
        log.info("Removed workspace manager {} from workspace {} by {}", targetUserId, workspaceId, currentUser.getId());

        // Notification to the removed manager
        if (removedManager != null) {
            String removedKey = "WORKSPACE_MANAGER_REMOVED:" + workspaceId + ":" + removedManager.getId();
            notificationService.createNotification(
                    removedManager,
                    currentUser,
                    NotificationType.WORKSPACE_MANAGER_REMOVED,
                    "Workspace Manager Role Removed",
                    "Your workspace manager role has been removed for workspace: " + workspace.getName(),
                    NotificationSourceType.SYSTEM,
                    "workspace-" + workspaceId,
                    null,
                    null,
                    removedKey
            );
        }

        // Audit
        auditLogService.logPermissionChange(
                "WORKSPACE_MANAGER_REMOVED",
                "WORKSPACE",
                workspaceId,
                null,
                null,
                null,
                orgCode,
                currentUser,
                removedManager != null ? removedManager.getEmail() : null,
                null,
                PermissionLevel.EDIT,
                null,
                null
        );

        if (permissionCacheService != null && removedManager != null && removedManager.getEmail() != null) {
            permissionCacheService.evictUserPermissions(removedManager.getEmail());
        }
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceManagerResponseDto> listWorkspaceManagers(Long workspaceId, UserEntity currentUser) {
        ProjectWorkspaceEntity workspace = requireWorkspace(workspaceId);
        String orgCode = workspace.getOrganization().getOrgCode();

        // Any active org member can list managers (read-only)
        requireActiveMemberByUserId(currentUser.getId(), orgCode);

        return workspaceManagerRepository.findByWorkspace_Id(workspaceId)
                .stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Authority checks — used by PermissionEvaluatorService
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public boolean isWorkspaceManagerForFolder(UserEntity user, FolderEntity folder) {
        if (user == null || folder == null) return false;

        List<WorkspaceManagerEntity> assignments = workspaceManagerRepository.findByUser_Id(user.getId());
        for (WorkspaceManagerEntity wm : assignments) {
            ProjectWorkspaceEntity ws = wm.getWorkspace();
            if (ws == null || ws.getStatus() != ProjectWorkspaceStatus.ACTIVE) continue;
            if (!isActiveMemberOf(user, ws.getOrganization().getOrgCode())) continue;
            if (ws.getRootFolder() != null && isSameOrAncestorFolder(ws.getRootFolder(), folder)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isWorkspaceManager(UserEntity user, ProjectWorkspaceEntity workspace) {
        if (user == null || workspace == null) return false;
        if (workspace.getStatus() != ProjectWorkspaceStatus.ACTIVE) return false;
        String orgCode = workspace.getOrganization().getOrgCode();
        if (!isActiveMemberOf(user, orgCode)) return false;
        return workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(workspace.getId(), user.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public Long resolveWorkspaceIdForManagerAndFolder(UserEntity user, FolderEntity folder, Long requestedWorkspaceId) {
        if (user == null || folder == null) return null;

        List<WorkspaceManagerEntity> assignments = workspaceManagerRepository.findByUser_Id(user.getId());
        if (requestedWorkspaceId != null) {
            for (WorkspaceManagerEntity wm : assignments) {
                ProjectWorkspaceEntity ws = wm.getWorkspace();
                if (ws == null || !ws.getId().equals(requestedWorkspaceId)) continue;
                if (ws.getStatus() != ProjectWorkspaceStatus.ACTIVE) continue;
                if (!isActiveMemberOf(user, ws.getOrganization().getOrgCode())) continue;
                if (ws.getRootFolder() != null && isSameOrAncestorFolder(ws.getRootFolder(), folder)) {
                    return ws.getId();
                }
            }
            return null;
        }

        for (WorkspaceManagerEntity wm : assignments) {
            ProjectWorkspaceEntity ws = wm.getWorkspace();
            if (ws == null || ws.getStatus() != ProjectWorkspaceStatus.ACTIVE) continue;
            if (!isActiveMemberOf(user, ws.getOrganization().getOrgCode())) continue;
            if (ws.getRootFolder() != null && isSameOrAncestorFolder(ws.getRootFolder(), folder)) {
                return ws.getId();
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ProjectWorkspaceEntity requireWorkspace(Long workspaceId) {
        return projectWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found with ID: " + workspaceId));
    }

    private void requireActiveWorkspace(ProjectWorkspaceEntity workspace) {
        if (workspace.getStatus() == ProjectWorkspaceStatus.ARCHIVED) {
            throw new IllegalStateException("Workspace is archived");
        }
    }

    private void requireOrgAdmin(UserEntity user, String orgCode) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        UserOrganizationEntity uo = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (uo.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User membership is not active in organization: " + orgCode);
        }
        if (uo.getRole() != Role.ROLE_ORG_ADMIN && uo.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Organization Admin privileges required to manage workspace managers");
        }
    }

    private void requireActiveMember(UserEntity user, String orgCode) {
        if (!isActiveMemberOf(user, orgCode)) {
            throw new IllegalArgumentException("Target user is not an active member of organization: " + orgCode);
        }
    }

    private void requireActiveMemberByUserId(String userId, String orgCode) {
        boolean isMember = userOrganizationRepository
                .existsByUser_IdAndOrganization_OrgCodeAndStatus(userId, orgCode, MembershipStatus.ACTIVE);
        if (!isMember) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }
    }

    private boolean isActiveMemberOf(UserEntity user, String orgCode) {
        Optional<UserOrganizationEntity> uo =
                userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode);
        if (uo.isPresent()) {
            return uo.get().getStatus() == MembershipStatus.ACTIVE;
        }
        // Fall back to the user's primary organization
        return user.getOrganization() != null && user.getOrganization().getOrgCode().equalsIgnoreCase(orgCode);
    }

    private boolean isSameOrAncestorFolder(FolderEntity ancestor, FolderEntity target) {
        if (ancestor == null || target == null) return false;
        FolderEntity cursor = target;
        while (cursor != null) {
            if (ancestor.getId() != null && ancestor.getId().equals(cursor.getId())) return true;
            cursor = cursor.getParentFolder();
        }
        return false;
    }

    private WorkspaceManagerResponseDto toResponseDto(WorkspaceManagerEntity entity) {
        return WorkspaceManagerResponseDto.builder()
                .id(entity.getId())
                .workspaceId(entity.getWorkspace() != null ? entity.getWorkspace().getId() : null)
                .workspaceName(entity.getWorkspace() != null ? entity.getWorkspace().getName() : null)
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .userEmail(entity.getUser() != null ? entity.getUser().getEmail() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
