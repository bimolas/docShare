package com.subject77.docshare.modules.permission.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.permission.dto.BulkSelectedMemberShareDto;
import com.subject77.docshare.modules.permission.dto.BulkShareWithExclusionsDto;
import com.subject77.docshare.modules.permission.dto.ManageAccessSummaryDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    public ResponseEntity<PermissionResponseDto> grantPermission(
            @Valid @RequestBody PermissionRequestDto requestDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to grant permission by user: {}", currentUser != null ? currentUser.getId() : "SYSTEM");
        String grantedByUserId = currentUser != null ? currentUser.getId() : "SYSTEM";
        PermissionResponseDto response = permissionService.grantPermission(requestDto, grantedByUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/grant-with-exclusions")
    public ResponseEntity<List<PermissionResponseDto>> grantFolderShareWithExclusions(
            @Valid @RequestBody BulkShareWithExclusionsDto requestDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to grant folder share with exclusions by user: {}", currentUser != null ? currentUser.getId() : "SYSTEM");
        String grantedByUserId = currentUser != null ? currentUser.getId() : "SYSTEM";
        List<PermissionResponseDto> response = permissionService.grantFolderShareWithExclusions(requestDto, grantedByUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/grant-selected-members")
    public ResponseEntity<List<PermissionResponseDto>> grantSelectedMembers(
            @Valid @RequestBody BulkSelectedMemberShareDto requestDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to grant permissions to selected org members by user: {}", currentUser != null ? currentUser.getId() : "SYSTEM");
        String grantedByUserId = currentUser != null ? currentUser.getId() : "SYSTEM";
        List<PermissionResponseDto> response = permissionService.grantSelectedMembers(requestDto, grantedByUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<PermissionResponseDto> updatePermission(
            @PathVariable("id") Long id,
            @Valid @RequestBody PermissionUpdateRequestDto requestDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to update permission ID: {} by user: {}", id, currentUser != null ? currentUser.getId() : "SYSTEM");
        String updatedByUserId = currentUser != null ? currentUser.getId() : "SYSTEM";
        PermissionResponseDto response = permissionService.updatePermission(id, requestDto, updatedByUserId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PermissionResponseDto> patchPermission(
            @PathVariable("id") Long id,
            @Valid @RequestBody PermissionUpdateRequestDto requestDto,
            @CurrentUser UserEntity currentUser) {
        return updatePermission(id, requestDto, currentUser);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<PermissionResponseDto> activatePermission(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to activate permission ID: {} by user: {}", id, currentUser != null ? currentUser.getId() : "SYSTEM");
        String activatedByUserId = currentUser != null ? currentUser.getId() : "SYSTEM";
        PermissionResponseDto response = permissionService.activatePermission(id, activatedByUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<PermissionResponseDto> suspendPermission(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to suspend permission ID: {} by user: {}", id, currentUser != null ? currentUser.getId() : "SYSTEM");
        String suspendedByUserId = currentUser != null ? currentUser.getId() : "SYSTEM";
        PermissionResponseDto response = permissionService.suspendPermission(id, suspendedByUserId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<PermissionResponseDto> revokePermission(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to revoke permissions");
        }
        log.info("REST request to revoke permission ID: {} by user: {}", id, currentUser.getId());
        PermissionResponseDto response = permissionService.revokePermission(id, currentUser.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PermissionResponseDto> getPermissionById(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        PermissionResponseDto response = permissionService.getPermissionById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/document/{documentId}")
    public ResponseEntity<List<PermissionResponseDto>> getActivePermissionsForDocument(
            @PathVariable("documentId") Long documentId,
            @CurrentUser UserEntity currentUser) {
        List<PermissionResponseDto> permissions = permissionService.getActivePermissionsForDocument(documentId, currentUser);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/folder/{folderId}")
    public ResponseEntity<List<PermissionResponseDto>> getActivePermissionsForFolder(
            @PathVariable("folderId") Long folderId,
            @CurrentUser UserEntity currentUser) {
        List<PermissionResponseDto> permissions = permissionService.getActivePermissionsForFolder(folderId, currentUser);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<List<PermissionResponseDto>> getActivePermissionsForGroup(
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        List<PermissionResponseDto> permissions = permissionService.getActivePermissionsForGroup(groupId, currentUser);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/target")
    public ResponseEntity<List<PermissionResponseDto>> getPermissionsForTarget(
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "groupId", required = false) Long groupId,
            @CurrentUser UserEntity currentUser) {
        List<PermissionResponseDto> permissions = permissionService.getPermissionsForTarget(email, orgCode, groupId, currentUser);
        return ResponseEntity.ok(permissions);
    }

    @GetMapping("/shared-with-me")
    public ResponseEntity<SharedWithMeResponseDto> getSharedWithMe(@CurrentUser UserEntity currentUser) {
        log.info("REST request to get items shared with user: {}", currentUser != null ? currentUser.getId() : "ANONYMOUS");
        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/shared-by-me")
    public ResponseEntity<SharedByMeResponseDto> getSharedByMe(
            @RequestParam(value = "includeInactive", required = false, defaultValue = "true") boolean includeInactive,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get items shared by user: {} (includeInactive={})", currentUser != null ? currentUser.getId() : "ANONYMOUS", includeInactive);
        SharedByMeResponseDto response = permissionService.getSharedByMe(currentUser, includeInactive);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/effective")
    public ResponseEntity<com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto> getEffectivePermissions(
            @RequestParam("targetId") Long targetId,
            @RequestParam("targetType") String targetType,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get effective permissions for targetId: {}, targetType: {}", targetId, targetType);
        com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto response = permissionService.getEffectivePermissions(targetId, targetType, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/manage")
    public ResponseEntity<ManageAccessSummaryDto> getManageAccessSummary(
            @RequestParam("resourceId") Long resourceId,
            @RequestParam("resourceType") String resourceType,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get manage access summary for resourceId: {}, resourceType: {} by user: {}",
                resourceId, resourceType, currentUser != null ? currentUser.getId() : "ANONYMOUS");
        ManageAccessSummaryDto response = permissionService.getManageAccessSummary(resourceId, resourceType, currentUser);
        return ResponseEntity.ok(response);
    }
}
