package com.subject77.docshare.modules.organization.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.organization.dto.CreateOrganizationInvitationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationInvitationResponseDto;
import com.subject77.docshare.modules.organization.services.OrganizationInvitationService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class OrganizationInvitationController {

    private final OrganizationInvitationService invitationService;

    // Organization Admin Management Endpoints
    @PostMapping("/api/v1/organizations/{orgCode}/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInvitationResponseDto> createInvitation(
            @PathVariable("orgCode") String orgCode,
            @Valid @RequestBody CreateOrganizationInvitationRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationInvitationResponseDto response = invitationService.createInvitation(orgCode, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/organizations/{orgCode}/invitations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationInvitationResponseDto>> getOrganizationPendingInvitations(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<OrganizationInvitationResponseDto> response = invitationService.getPendingInvitationsForOrganization(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/api/v1/organizations/{orgCode}/invitations/{invitationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeInvitation(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("invitationId") Long invitationId,
            @CurrentUser UserEntity currentUser) {
        invitationService.revokeInvitation(orgCode, invitationId, currentUser);
        return ResponseEntity.noContent().build();
    }

    // Invitee Endpoints
    @GetMapping("/api/v1/invitations/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationInvitationResponseDto>> getMyPendingInvitations(
            @CurrentUser UserEntity currentUser) {
        List<OrganizationInvitationResponseDto> response = invitationService.getMyPendingInvitations(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/api/v1/invitations/token/{token}", "/api/v1/invitations/{token}"})
    @PreAuthorize("permitAll()")
    public ResponseEntity<OrganizationInvitationResponseDto> getInvitationByToken(
            @PathVariable("token") String token) {
        OrganizationInvitationResponseDto response = invitationService.getInvitationByToken(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/api/v1/invitations/token/{token}/accept", "/api/v1/invitations/{token}/accept"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInvitationResponseDto> acceptInvitation(
            @PathVariable("token") String token,
            @CurrentUser UserEntity currentUser) {
        OrganizationInvitationResponseDto response = invitationService.acceptInvitation(token, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping({"/api/v1/invitations/token/{token}/decline", "/api/v1/invitations/{token}/decline"})
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationInvitationResponseDto> declineInvitation(
            @PathVariable("token") String token,
            @CurrentUser UserEntity currentUser) {
        OrganizationInvitationResponseDto response = invitationService.declineInvitation(token, currentUser);
        return ResponseEntity.ok(response);
    }
}
