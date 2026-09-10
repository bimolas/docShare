package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.dto.WorkspaceManagerResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.folder.entities.FolderEntity;

import java.util.List;

public interface WorkspaceManagerService {

    /**
     * Assign a user as a Workspace Manager.
     * Only an org admin of the workspace's organization may call this.
     */
    WorkspaceManagerResponseDto assignWorkspaceManager(Long workspaceId, String targetUserId, UserEntity currentUser);

    /**
     * Remove a Workspace Manager assignment.
     * Only an org admin of the workspace's organization may call this.
     */
    void removeWorkspaceManager(Long workspaceId, String targetUserId, UserEntity currentUser);

    /** List all managers for a workspace. */
    List<WorkspaceManagerResponseDto> listWorkspaceManagers(Long workspaceId, UserEntity currentUser);

    /**
     * Core authority check:
     * Returns true iff the user has an active WorkspaceManagerEntity for a workspace
     * whose rootFolder hierarchy contains the given folder.
     */
    boolean isWorkspaceManagerForFolder(UserEntity user, FolderEntity folder);

    /**
     * Returns true iff the user has an active WorkspaceManagerEntity for the given workspace,
     * AND the workspace is ACTIVE, AND the user is an active org member.
     */
    boolean isWorkspaceManager(UserEntity user, ProjectWorkspaceEntity workspace);

    /**
     * Resolves and validates the workspace ID for a given user and folder if the user is an active Workspace Manager
     * for that workspace hierarchy. Returns null if not a workspace manager for that folder.
     */
    Long resolveWorkspaceIdForManagerAndFolder(UserEntity user, FolderEntity folder, Long requestedWorkspaceId);
}
