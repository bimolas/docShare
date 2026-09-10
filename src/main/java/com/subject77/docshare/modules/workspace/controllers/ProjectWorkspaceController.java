package com.subject77.docshare.modules.workspace.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.dto.*;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authoritative Canonical REST API for Project Workspace management.
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectWorkspaceController {

    private final ProjectWorkspaceService projectWorkspaceService;

    /**
     * List all active personal workspaces for current user.
     * GET /api/v1/personal/workspaces
     */
    @GetMapping("/personal/workspaces")
    public ResponseEntity<List<ProjectWorkspaceResponseDto>> getPersonalWorkspaces(
            @CurrentUser UserEntity currentUser) {
        List<ProjectWorkspaceResponseDto> response =
                projectWorkspaceService.getPersonalWorkspaces(currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new personal workspace for current user.
     * POST /api/v1/personal/workspaces
     */
    @PostMapping("/personal/workspaces")
    public ResponseEntity<ProjectWorkspaceResponseDto> createPersonalWorkspace(
            @Valid @RequestBody CreatePersonalWorkspaceRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ProjectWorkspaceResponseDto response =
                projectWorkspaceService.createPersonalWorkspace(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List all active/accessible workspaces for an organization.
     * GET /api/v1/organizations/{orgCode}/workspaces
     */
    @GetMapping("/organizations/{orgCode}/workspaces")
    public ResponseEntity<List<ProjectWorkspaceResponseDto>> getWorkspacesForOrganization(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<ProjectWorkspaceResponseDto> response =
                projectWorkspaceService.getWorkspacesForOrganization(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Create a new project workspace in an organization.
     * POST /api/v1/organizations/{orgCode}/workspaces
     */
    @PostMapping("/organizations/{orgCode}/workspaces")
    public ResponseEntity<ProjectWorkspaceResponseDto> createWorkspace(
            @PathVariable("orgCode") String orgCode,
            @RequestBody CreateProjectWorkspaceRequestDto request,
            @CurrentUser UserEntity currentUser) {
        if (request == null) {
            throw new IllegalArgumentException("Request body cannot be null");
        }
        if (request.getOrgCode() == null || request.getOrgCode().isBlank()) {
            request.setOrgCode(orgCode);
        } else if (!request.getOrgCode().equalsIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Organization code in URL path (" + orgCode
                    + ") does not match request body (" + request.getOrgCode() + ")");
        }
        ProjectWorkspaceResponseDto response =
                projectWorkspaceService.createWorkspace(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get workspace details by workspace ID.
     * GET /api/v1/workspaces/{workspaceId}
     */
    @GetMapping("/workspaces/{workspaceId}")
    public ResponseEntity<ProjectWorkspaceResponseDto> getWorkspace(
            @PathVariable("workspaceId") Long workspaceId,
            @CurrentUser UserEntity currentUser) {
        ProjectWorkspaceResponseDto response =
                projectWorkspaceService.getWorkspaceById(workspaceId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Update workspace name and/or description.
     * PATCH /api/v1/workspaces/{workspaceId}
     */
    @PatchMapping("/workspaces/{workspaceId}")
    public ResponseEntity<ProjectWorkspaceResponseDto> updateWorkspace(
            @PathVariable("workspaceId") Long workspaceId,
            @RequestBody UpdateProjectWorkspaceRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ProjectWorkspaceResponseDto response =
                projectWorkspaceService.updateWorkspace(workspaceId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Archive an active workspace.
     * POST /api/v1/workspaces/{workspaceId}/archive
     */
    @PostMapping("/workspaces/{workspaceId}/archive")
    public ResponseEntity<ProjectWorkspaceResponseDto> archiveWorkspace(
            @PathVariable("workspaceId") Long workspaceId,
            @CurrentUser UserEntity currentUser) {
        ProjectWorkspaceResponseDto response =
                projectWorkspaceService.archiveWorkspace(workspaceId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Restore an archived workspace to active status.
     * POST /api/v1/workspaces/{workspaceId}/restore
     */
    @PostMapping("/workspaces/{workspaceId}/restore")
    public ResponseEntity<ProjectWorkspaceResponseDto> restoreWorkspace(
            @PathVariable("workspaceId") Long workspaceId,
            @CurrentUser UserEntity currentUser) {
        ProjectWorkspaceResponseDto response =
                projectWorkspaceService.restoreWorkspace(workspaceId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * List all direct team/user assignments within a workspace.
     * GET /api/v1/workspaces/{workspaceId}/assignments
     */
    @GetMapping("/workspaces/{workspaceId}/assignments")
    public ResponseEntity<List<WorkspaceAssignmentResponseDto>> getWorkspaceAssignments(
            @PathVariable("workspaceId") Long workspaceId,
            @CurrentUser UserEntity currentUser) {
        List<WorkspaceAssignmentResponseDto> response =
                projectWorkspaceService.getWorkspaceAssignments(workspaceId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Assign a team to a folder within a workspace.
     * POST /api/v1/workspaces/{workspaceId}/assignments/teams
     */
    @PostMapping("/workspaces/{workspaceId}/assignments/teams")
    public ResponseEntity<WorkspaceAssignmentResponseDto> assignTeamToWorkspace(
            @PathVariable("workspaceId") Long workspaceId,
            @Valid @RequestBody WorkspaceTeamAssignmentRequestDto request,
            @CurrentUser UserEntity currentUser) {
        WorkspaceAssignmentResponseDto response =
                projectWorkspaceService.assignTeamToWorkspace(workspaceId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Assign a user directly to a folder within a workspace.
     * POST /api/v1/workspaces/{workspaceId}/assignments/users
     */
    @PostMapping("/workspaces/{workspaceId}/assignments/users")
    public ResponseEntity<WorkspaceAssignmentResponseDto> assignUserToWorkspace(
            @PathVariable("workspaceId") Long workspaceId,
            @Valid @RequestBody WorkspaceUserAssignmentRequestDto request,
            @CurrentUser UserEntity currentUser) {
        WorkspaceAssignmentResponseDto response =
                projectWorkspaceService.assignUserToWorkspace(workspaceId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Revoke a workspace assignment by permission ID.
     * DELETE /api/v1/workspaces/{workspaceId}/assignments/{permissionId}
     */
    @DeleteMapping("/workspaces/{workspaceId}/assignments/{permissionId}")
    public ResponseEntity<Void> revokeWorkspaceAssignment(
            @PathVariable("workspaceId") Long workspaceId,
            @PathVariable("permissionId") Long permissionId,
            @CurrentUser UserEntity currentUser) {
        projectWorkspaceService.revokeWorkspaceAssignment(workspaceId, permissionId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
