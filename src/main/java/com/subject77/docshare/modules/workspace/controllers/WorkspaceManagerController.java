package com.subject77.docshare.modules.workspace.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.workspace.dto.WorkspaceManagerResponseDto;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for scoped Workspace Manager administration.
 * All mutation endpoints require Organization Admin authority (enforced by WorkspaceManagerServiceImpl).
 */
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceManagerController {

    private final WorkspaceManagerService workspaceManagerService;

    /**
     * Assign a user as workspace manager.
     * PUT /api/v1/workspaces/{workspaceId}/managers/{userId}
     * Required authority: Org Admin of the workspace's organization.
     */
    @PutMapping("/{workspaceId}/managers/{userId}")
    public ResponseEntity<WorkspaceManagerResponseDto> assignWorkspaceManager(
            @PathVariable Long workspaceId,
            @PathVariable String userId,
            @CurrentUser UserEntity currentUser) {

        WorkspaceManagerResponseDto dto =
                workspaceManagerService.assignWorkspaceManager(workspaceId, userId, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * Remove a workspace manager assignment.
     * DELETE /api/v1/workspaces/{workspaceId}/managers/{userId}
     * Required authority: Org Admin of the workspace's organization.
     */
    @DeleteMapping("/{workspaceId}/managers/{userId}")
    public ResponseEntity<Void> removeWorkspaceManager(
            @PathVariable Long workspaceId,
            @PathVariable String userId,
            @CurrentUser UserEntity currentUser) {

        workspaceManagerService.removeWorkspaceManager(workspaceId, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * List all managers of a workspace.
     * GET /api/v1/workspaces/{workspaceId}/managers
     * Required authority: Active member of the workspace's organization.
     */
    @GetMapping("/{workspaceId}/managers")
    public ResponseEntity<List<WorkspaceManagerResponseDto>> listWorkspaceManagers(
            @PathVariable Long workspaceId,
            @CurrentUser UserEntity currentUser) {

        List<WorkspaceManagerResponseDto> managers =
                workspaceManagerService.listWorkspaceManagers(workspaceId, currentUser);
        return ResponseEntity.ok(managers);
    }
}
