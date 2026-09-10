package com.subject77.docshare.modules.policy.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.policy.dto.UpdateWorkspacePolicyRequestDto;
import com.subject77.docshare.modules.policy.dto.WorkspacePolicyDto;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.mapper.WorkspacePolicyMapper;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspacePolicyController {

    private final WorkspacePolicyService workspacePolicyService;
    private final PermissionEvaluatorService evaluatorService;

    @GetMapping("/{workspaceId}/policies")
    public ResponseEntity<WorkspacePolicyDto> getWorkspacePolicy(
            @CurrentUser UserEntity currentUser,
            @PathVariable("workspaceId") String workspaceId) {

        WorkspacePolicyEntity entity;

        if ("personal".equalsIgnoreCase(workspaceId)) {
            entity = workspacePolicyService.getOrCreatePersonalPolicy(currentUser);
        } else {
            entity = workspacePolicyService.getOrCreateOrganizationPolicy(workspaceId);
        }

        return ResponseEntity.ok(
                WorkspacePolicyMapper.toWorkspacePolicyDto(entity));
    }

    @PutMapping("/{workspaceId}/policies")
    public ResponseEntity<WorkspacePolicyDto> updateWorkspacePolicy(
            @CurrentUser UserEntity currentUser,
            @PathVariable("workspaceId") String workspaceId,
            @RequestBody UpdateWorkspacePolicyRequestDto request) {

        WorkspacePolicyEntity entity;
        if ("personal".equalsIgnoreCase(workspaceId)) {
            if (currentUser == null) {
                throw new AccessDeniedException("Authentication required to update personal workspace policy");
            }
            entity = workspacePolicyService.updatePersonalPolicy(
                    currentUser.getId(),
                    request.getExternalSharingMode(),
                    request.getDiscoverabilityMode(),
                    request.getAllowAccessRequests(),
                    request.getRequireRequestReason(),
                    request.getAllowExternalAccessRequests(),
                    currentUser);
        } else {
            if (currentUser == null || !evaluatorService.isUserAdminOfOrg(currentUser, workspaceId)) {
                throw new AccessDeniedException("Organization administrator privileges required to update organization workspace policy");
            }
            entity = workspacePolicyService.updateOrganizationPolicy(
                    workspaceId,
                    request.getExternalSharingMode(),
                    request.getDiscoverabilityMode(),
                    request.getAllowAccessRequests(),
                    request.getRequireRequestReason(),
                    request.getAllowExternalAccessRequests(),
                    currentUser);
        }

        return ResponseEntity.ok(WorkspacePolicyMapper.toWorkspacePolicyDto(entity));
    }
}
