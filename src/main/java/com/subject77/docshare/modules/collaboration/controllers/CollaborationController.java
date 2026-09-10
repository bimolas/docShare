package com.subject77.docshare.modules.collaboration.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.collaboration.dto.AddGroupParticipantRequestDto;
import com.subject77.docshare.modules.collaboration.dto.AddParticipantRequestDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationGroupParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CreateCollaborationRequestDto;
import com.subject77.docshare.modules.collaboration.services.CollaborationService;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/collaborations")
@RequiredArgsConstructor
public class CollaborationController {

    private final CollaborationService collaborationService;

    @PostMapping
    public ResponseEntity<CollaborationResponseDto> createCollaboration(
            @Valid @RequestBody CreateCollaborationRequestDto request,
            @CurrentUser UserEntity currentUser) {
        CollaborationResponseDto response = collaborationService.createCollaboration(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CollaborationResponseDto> getCollaborationById(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        CollaborationResponseDto response = collaborationService.getCollaborationById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/organization/{orgCode}")
    public ResponseEntity<List<CollaborationResponseDto>> getCollaborationsForOrganization(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<CollaborationResponseDto> response = collaborationService.getCollaborationsForOrganization(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<CollaborationResponseDto> acceptCollaboration(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        CollaborationResponseDto response = collaborationService.acceptCollaboration(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/decline")
    public ResponseEntity<CollaborationResponseDto> declineCollaboration(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        CollaborationResponseDto response = collaborationService.declineCollaboration(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/participants")
    public ResponseEntity<CollaborationParticipantResponseDto> addParticipant(
            @PathVariable("id") Long id,
            @Valid @RequestBody AddParticipantRequestDto request,
            @CurrentUser UserEntity currentUser) {
        CollaborationParticipantResponseDto response = collaborationService.addParticipant(id, request.getUserId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/participants/{userId}")
    public ResponseEntity<Void> removeParticipant(
            @PathVariable("id") Long id,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        collaborationService.removeParticipant(id, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/participants")
    public ResponseEntity<List<CollaborationParticipantResponseDto>> getParticipants(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        List<CollaborationParticipantResponseDto> response = collaborationService.getParticipants(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/groups")
    public ResponseEntity<CollaborationGroupParticipantResponseDto> addGroupParticipant(
            @PathVariable("id") Long id,
            @Valid @RequestBody AddGroupParticipantRequestDto request,
            @CurrentUser UserEntity currentUser) {
        CollaborationGroupParticipantResponseDto response = collaborationService.addGroupParticipant(id, request.getGroupId(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/groups/{groupId}")
    public ResponseEntity<Void> removeGroupParticipant(
            @PathVariable("id") Long id,
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        collaborationService.removeGroupParticipant(id, groupId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/groups")
    public ResponseEntity<List<CollaborationGroupParticipantResponseDto>> getGroupParticipants(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        List<CollaborationGroupParticipantResponseDto> response = collaborationService.getGroupParticipants(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/participants/{userId}/manager")
    public ResponseEntity<CollaborationParticipantResponseDto> assignManager(
            @PathVariable("id") Long id,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        CollaborationParticipantResponseDto response = collaborationService.assignManager(id, userId, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}/participants/{userId}/manager")
    public ResponseEntity<CollaborationParticipantResponseDto> removeManager(
            @PathVariable("id") Long id,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        CollaborationParticipantResponseDto response = collaborationService.removeManager(id, userId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<PermissionResponseDto> grantCollaborationPermission(
            @PathVariable("id") Long id,
            @Valid @RequestBody PermissionRequestDto request,
            @CurrentUser UserEntity currentUser) {
        PermissionResponseDto response = collaborationService.grantCollaborationPermission(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/permissions/{permissionId}")
    public ResponseEntity<PermissionResponseDto> revokeCollaborationPermission(
            @PathVariable("id") Long id,
            @PathVariable("permissionId") Long permissionId,
            @CurrentUser UserEntity currentUser) {
        PermissionResponseDto response = collaborationService.revokeCollaborationPermission(id, permissionId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/permissions")
    public ResponseEntity<List<PermissionResponseDto>> getCollaborationPermissions(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        List<PermissionResponseDto> response = collaborationService.getCollaborationPermissions(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<CollaborationResponseDto> revokeCollaboration(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        CollaborationResponseDto response = collaborationService.revokeCollaboration(id, currentUser);
        return ResponseEntity.ok(response);
    }
}



