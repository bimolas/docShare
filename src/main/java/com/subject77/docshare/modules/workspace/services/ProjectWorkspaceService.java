package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.dto.*;

import java.util.List;

public interface ProjectWorkspaceService {

    ProjectWorkspaceResponseDto createWorkspace(CreateProjectWorkspaceRequestDto request, UserEntity currentUser);

    ProjectWorkspaceResponseDto createPersonalWorkspace(CreatePersonalWorkspaceRequestDto request, UserEntity currentUser);

    List<ProjectWorkspaceResponseDto> getPersonalWorkspaces(UserEntity currentUser);

    ProjectWorkspaceResponseDto getWorkspaceById(Long id, UserEntity currentUser);

    List<ProjectWorkspaceResponseDto> getWorkspacesForOrganization(String orgCode, UserEntity currentUser);

    ProjectWorkspaceResponseDto updateWorkspace(Long id, UpdateProjectWorkspaceRequestDto request, UserEntity currentUser);

    ProjectWorkspaceResponseDto archiveWorkspace(Long id, UserEntity currentUser);

    ProjectWorkspaceResponseDto restoreWorkspace(Long id, UserEntity currentUser);

    WorkspaceAssignmentResponseDto assignTeamToWorkspace(Long workspaceId, WorkspaceTeamAssignmentRequestDto request, UserEntity currentUser);

    WorkspaceAssignmentResponseDto assignUserToWorkspace(Long workspaceId, WorkspaceUserAssignmentRequestDto request, UserEntity currentUser);

    void revokeWorkspaceAssignment(Long workspaceId, Long permissionId, UserEntity currentUser);

    List<WorkspaceAssignmentResponseDto> getWorkspaceAssignments(Long workspaceId, UserEntity currentUser);

    ProjectWorkspaceResponseDto ensureDefaultPersonalWorkspace(UserEntity user);
}
