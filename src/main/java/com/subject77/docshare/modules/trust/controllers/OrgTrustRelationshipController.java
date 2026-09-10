package com.subject77.docshare.modules.trust.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.trust.dto.CreateTrustRequestDto;
import com.subject77.docshare.modules.trust.dto.TrustRelationshipResponseDto;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
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
@RequestMapping("/api/v1/trust-relationships")
@RequiredArgsConstructor
public class OrgTrustRelationshipController {

    private final OrgTrustRelationshipService trustRelationshipService;

    @PostMapping
    public ResponseEntity<TrustRelationshipResponseDto> createTrustRequest(
            @Valid @RequestBody CreateTrustRequestDto request,
            @CurrentUser UserEntity currentUser) {
        TrustRelationshipResponseDto response = trustRelationshipService.createTrustRequest(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<TrustRelationshipResponseDto> approveTrustRequest(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        TrustRelationshipResponseDto response = trustRelationshipService.approveTrustRequest(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/revoke")
    public ResponseEntity<TrustRelationshipResponseDto> revokeTrustRelationship(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        TrustRelationshipResponseDto response = trustRelationshipService.revokeTrustRelationship(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/organization/{orgCode}")
    public ResponseEntity<List<TrustRelationshipResponseDto>> getTrustRelationshipsForOrganization(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<TrustRelationshipResponseDto> response = trustRelationshipService.getTrustRelationshipsForOrganization(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrustRelationshipResponseDto> getTrustRelationship(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        TrustRelationshipResponseDto response = trustRelationshipService.getTrustRelationship(id, currentUser);
        return ResponseEntity.ok(response);
    }
}
