package com.subject77.docshare.modules.organization.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.organization.dto.GroupRequestDto;
import com.subject77.docshare.modules.organization.dto.GroupResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberResponseDto;
import com.subject77.docshare.modules.organization.dto.TransferManagerRequestDto;
import com.subject77.docshare.modules.organization.dto.UpdateGroupMemberRoleRequestDto;
import com.subject77.docshare.modules.organization.dto.UpdateMemberRoleRequestDto;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.services.GroupService;
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
@RequestMapping("/api/v1/organizations/{orgCode}/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ResponseEntity<GroupResponseDto> createGroup(
            @PathVariable("orgCode") String orgCode,
            @Valid @RequestBody GroupRequestDto request,
            @CurrentUser UserEntity currentUser) {
        GroupResponseDto response = groupService.createGroup(orgCode, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<GroupResponseDto> updateGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody GroupRequestDto request,
            @CurrentUser UserEntity currentUser) {
        GroupResponseDto response = groupService.updateGroup(orgCode, groupId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/archive")
    public ResponseEntity<GroupResponseDto> archiveGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        GroupResponseDto response = groupService.archiveGroup(orgCode, groupId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/unarchive")
    public ResponseEntity<GroupResponseDto> unarchiveGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        GroupResponseDto response = groupService.unarchiveGroup(orgCode, groupId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<GroupResponseDto>> getOrganizationGroups(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<GroupResponseDto> response = groupService.getOrganizationGroups(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<GroupResponseDto> getGroupById(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        GroupResponseDto response = groupService.getGroupById(orgCode, groupId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> addMemberToGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") String userId,
            @RequestParam(name = "role", required = false) GroupMemberRole role,
            @CurrentUser UserEntity currentUser) {
        if (role != null) {
            groupService.addMemberToGroup(orgCode, groupId, userId, role, currentUser);
        } else {
            groupService.addMemberToGroup(orgCode, groupId, userId, currentUser);
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateGroupMemberRoleRequestDto request,
            @CurrentUser UserEntity currentUser) {
        groupService.updateMemberRole(orgCode, groupId, userId, request.getRole(), currentUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    public ResponseEntity<Void> removeMemberFromGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        groupService.removeMemberFromGroup(orgCode, groupId, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<List<OrganizationMemberResponseDto>> getGroupMembers(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        List<OrganizationMemberResponseDto> response = groupService.getGroupMembers(orgCode, groupId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{groupId}/transfer-manager")
    public ResponseEntity<Void> transferTeamManager(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody TransferManagerRequestDto request,
            @CurrentUser UserEntity currentUser) {
        groupService.transferTeamManager(orgCode, groupId, request.getNewManagerUserId(), currentUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("groupId") Long groupId,
            @CurrentUser UserEntity currentUser) {
        groupService.deleteGroup(orgCode, groupId, currentUser);
        return ResponseEntity.noContent().build();
    }
}

