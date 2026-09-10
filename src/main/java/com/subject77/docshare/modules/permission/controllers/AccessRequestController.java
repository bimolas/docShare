package com.subject77.docshare.modules.permission.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.permission.dto.AccessRequestActionDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.services.AccessRequestService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.subject77.docshare.modules.permission.dto.AccessRequestEligibilityDto;

@Slf4j
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/access-requests")
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    @GetMapping("/eligibility")
    public ResponseEntity<AccessRequestEligibilityDto> checkEligibility(
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "documentFileHash", required = false) String documentFileHash,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to check access request eligibility for user: {}", currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        AccessRequestEligibilityDto response = accessRequestService.checkEligibility(documentId, documentFileHash, folderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<AccessRequestResponseDto> createAccessRequest(
            @Valid @RequestBody AccessRequestCreateDto createDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to create access request by user: {}", currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        AccessRequestResponseDto response = accessRequestService.createAccessRequest(createDto, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/my")
    public ResponseEntity<Page<AccessRequestResponseDto>> getMyAccessRequests(
            @RequestParam(value = "status", required = false) AccessRequestStatus status,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get my access requests (status={}, search={}) for user: {}", status, search, currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        Page<AccessRequestResponseDto> response = (search != null && !search.isBlank())
                ? accessRequestService.getMyAccessRequests(currentUser, status, search, pageable)
                : accessRequestService.getMyAccessRequests(currentUser, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/reviews")
    public ResponseEntity<Page<AccessRequestResponseDto>> getReviewerAccessRequests(
            @RequestParam(value = "status", required = false) AccessRequestStatus status,
            @RequestParam(value = "orgCode", required = false) String orgCode,
            @RequestParam(value = "workspaceId", required = false) Long workspaceId,
            @RequestParam(value = "search", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get reviews (status={}, orgCode={}, workspaceId={}, search={}) for user: {}",
                status, orgCode, workspaceId, search, currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        Page<AccessRequestResponseDto> response = accessRequestService.getReviewerAccessRequests(
                currentUser, status, orgCode, workspaceId, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending-reviews")
    public ResponseEntity<Page<AccessRequestResponseDto>> getPendingReviews(
            @PageableDefault(size = 20) Pageable pageable,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get pending access request reviews for user: {}", currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        Page<AccessRequestResponseDto> response = accessRequestService.getPendingReviews(currentUser, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccessRequestResponseDto> getAccessRequestById(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get access request ID: {} for user: {}", id, currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        AccessRequestResponseDto response = accessRequestService.getAccessRequestById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AccessRequestResponseDto> approveAccessRequest(
            @PathVariable("id") Long id,
            @RequestBody(required = false) AccessRequestActionDto actionDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to approve access request ID: {} by user: {}", id, currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        AccessRequestResponseDto response = accessRequestService.approveAccessRequest(id, actionDto, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/deny")
    public ResponseEntity<AccessRequestResponseDto> denyAccessRequest(
            @PathVariable("id") Long id,
            @RequestBody(required = false) AccessRequestActionDto actionDto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to deny access request ID: {} by user: {}", id, currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        AccessRequestResponseDto response = accessRequestService.denyAccessRequest(id, actionDto, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<AccessRequestResponseDto> cancelAccessRequest(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to cancel access request ID: {} by user: {}", id, currentUser != null ? currentUser.getEmail() : "ANONYMOUS");
        AccessRequestResponseDto response = accessRequestService.cancelAccessRequest(id, currentUser);
        return ResponseEntity.ok(response);
    }
}
