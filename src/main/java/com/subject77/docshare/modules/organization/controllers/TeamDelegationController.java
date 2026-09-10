package com.subject77.docshare.modules.organization.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.services.TeamDelegationService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/organizations/{orgCode}/teams/{teamId}/member-access")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class TeamDelegationController {

    private final TeamDelegationService teamDelegationService;

    @GetMapping("/boundary")
    public ResponseEntity<TeamAccessBoundaryResponseDto> getTeamBoundary(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("teamId") Long teamId,
            @CurrentUser UserEntity currentUser) {
        TeamAccessBoundaryResponseDto boundary = teamDelegationService.getTeamBoundary(orgCode, teamId, currentUser);
        if (boundary == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(boundary);
    }

    @GetMapping
    public ResponseEntity<List<TeamMemberAccessResponseDto>> getMemberAccessList(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("teamId") Long teamId,
            @CurrentUser UserEntity currentUser) {
        List<TeamMemberAccessResponseDto> list = teamDelegationService.getMemberAccessList(orgCode, teamId, currentUser);
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<TeamMemberAccessResponseDto> grantMemberAccess(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("teamId") Long teamId,
            @Valid @RequestBody GrantMemberAccessRequestDto req,
            @CurrentUser UserEntity currentUser) {
        TeamMemberAccessResponseDto response = teamDelegationService.grantMemberAccess(orgCode, teamId, req, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{permissionId}")
    public ResponseEntity<TeamMemberAccessResponseDto> updateMemberAccess(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("teamId") Long teamId,
            @PathVariable("permissionId") Long permissionId,
            @Valid @RequestBody UpdateMemberAccessRequestDto req,
            @CurrentUser UserEntity currentUser) {
        TeamMemberAccessResponseDto response = teamDelegationService.updateMemberAccess(orgCode, teamId, permissionId, req, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{permissionId}")
    public ResponseEntity<Void> revokeMemberAccess(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("teamId") Long teamId,
            @PathVariable("permissionId") Long permissionId,
            @CurrentUser UserEntity currentUser) {
        teamDelegationService.revokeMemberAccess(orgCode, teamId, permissionId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scoped-resources")
    public ResponseEntity<ScopedResourceNodeDto> getScopedResourceTree(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("teamId") Long teamId,
            @CurrentUser UserEntity currentUser) {
        ScopedResourceNodeDto tree = teamDelegationService.getScopedResourceTree(orgCode, teamId, currentUser);
        return ResponseEntity.ok(tree);
    }
}
