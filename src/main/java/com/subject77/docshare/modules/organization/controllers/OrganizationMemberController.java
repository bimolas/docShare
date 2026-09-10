package com.subject77.docshare.modules.organization.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.services.OrganizationService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/organizations/{orgCode}/members")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final OrganizationService organizationService;

    @GetMapping("/details")
    public ResponseEntity<List<OrganizationMemberDetailResponseDto>> getOrganizationMemberDetails(
            @PathVariable("orgCode") String orgCode,
            @RequestParam(name = "status", required = false) MembershipStatus statusFilter,
            @RequestParam(name = "q", required = false) String searchQuery,
            @CurrentUser UserEntity currentUser) {
        List<OrganizationMemberDetailResponseDto> response = organizationService.getOrganizationMemberDetails(orgCode, statusFilter, searchQuery, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = {"/{userId}", "/{userId}/details"})
    public ResponseEntity<OrganizationMemberDetailResponseDto> getOrganizationMemberDetail(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberDetailResponseDto response = organizationService.getOrganizationMemberDetail(orgCode, userId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{userId}/access-summary")
    public ResponseEntity<MemberAccessSummaryResponseDto> getOrganizationMemberAccessSummary(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        MemberAccessSummaryResponseDto response = organizationService.getOrganizationMemberAccessSummary(orgCode, userId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<OrganizationMemberDetailResponseDto> updateMemberRole(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @Valid @RequestBody UpdateMemberRoleRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberDetailResponseDto response = organizationService.updateMemberRole(orgCode, userId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/suspend")
    public ResponseEntity<OrganizationMemberDetailResponseDto> suspendMember(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @RequestBody(required = false) MemberStatusChangeRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberDetailResponseDto response = organizationService.suspendMember(orgCode, userId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/reactivate")
    public ResponseEntity<OrganizationMemberDetailResponseDto> reactivateMember(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberDetailResponseDto response = organizationService.reactivateMember(orgCode, userId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{userId}/remove")
    public ResponseEntity<OrganizationMemberDetailResponseDto> removeMember(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @RequestBody(required = false) MemberStatusChangeRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberDetailResponseDto response = organizationService.removeMember(orgCode, userId, request, currentUser);
        return ResponseEntity.ok(response);
    }
}
