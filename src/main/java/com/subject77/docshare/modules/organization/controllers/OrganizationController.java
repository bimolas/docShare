package com.subject77.docshare.modules.organization.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.organization.dto.AddMemberRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberDetailResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationResponseDto;
import com.subject77.docshare.modules.organization.dto.TransferOrgAdminRequestDto;
import com.subject77.docshare.modules.organization.services.OrganizationService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrganizationResponseDto> createOrganization(
            @Valid @RequestBody OrganizationRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationResponseDto response = organizationService.createOrganization(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<OrganizationResponseDto>> getUserOrganizations(
            @CurrentUser UserEntity currentUser) {
        List<OrganizationResponseDto> response = organizationService.getUserOrganizations(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<List<OrganizationResponseDto>> searchOrganizations(
            @RequestParam(name = "q", required = false) String query,
            @CurrentUser UserEntity currentUser) {
        List<OrganizationResponseDto> response = organizationService.searchOrganizations(query, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orgCode}")
    public ResponseEntity<OrganizationResponseDto> getOrganizationByCode(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        OrganizationResponseDto response = organizationService.getOrganizationByCode(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orgCode}/members")
    public ResponseEntity<OrganizationMemberResponseDto> addMember(
            @PathVariable("orgCode") String orgCode,
            @Valid @RequestBody AddMemberRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberResponseDto response = organizationService.addMemberToOrganization(orgCode, request,
                currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orgCode}/members")
    public ResponseEntity<Page<OrganizationMemberResponseDto>> getMembers(
            @PathVariable("orgCode") String orgCode,
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        Page<OrganizationMemberResponseDto> response = organizationService.getOrganizationMembers(orgCode, pageable,
                currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{orgCode}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable("orgCode") String orgCode,
            @PathVariable("userId") String userId,
            @CurrentUser UserEntity currentUser) {
        organizationService.removeMemberFromOrganization(orgCode, userId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orgCode}/transfer-admin")
    public ResponseEntity<OrganizationMemberDetailResponseDto> transferOrgAdmin(
            @PathVariable("orgCode") String orgCode,
            @Valid @RequestBody TransferOrgAdminRequestDto request,
            @CurrentUser UserEntity currentUser) {
        OrganizationMemberDetailResponseDto response = organizationService.transferOrgAdmin(
                orgCode, request.getNewAdminUserId(), currentUser);
        return ResponseEntity.ok(response);
    }
}
