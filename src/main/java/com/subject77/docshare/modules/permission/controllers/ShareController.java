package com.subject77.docshare.modules.permission.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.document.dto.DownloadUrlResponse;
import com.subject77.docshare.modules.permission.dto.ShareLinkRequestDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkRevealResponseDto;
import com.subject77.docshare.modules.permission.services.ShareService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShareLinkResponseDto> createShareLink(
            @Valid @RequestBody ShareLinkRequestDto request,
            @CurrentUser UserEntity currentUser) {
        ShareLinkResponseDto response = shareService.createShareLink(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/links/{permissionId}/reveal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShareLinkRevealResponseDto> revealShareLink(
            @PathVariable("permissionId") Long permissionId,
            @CurrentUser UserEntity currentUser) {
        ShareLinkRevealResponseDto response = shareService.revealShareLink(permissionId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{token}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<ShareLinkResponseDto> getShareLinkInfo(@PathVariable("token") String token) {
        ShareLinkResponseDto response = shareService.getShareLinkInfo(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{token}/access")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Object> accessSharedResource(
            @PathVariable("token") String token,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @CurrentUser UserEntity currentUser) {
        Object response = shareService.accessSharedResource(token, password, folderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{token}/download")
    @PreAuthorize("permitAll()")
    public ResponseEntity<DownloadUrlResponse> downloadSharedDocument(
            @PathVariable("token") String token,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @CurrentUser UserEntity currentUser) {
        DownloadUrlResponse response = shareService.downloadSharedDocument(token, password, documentId, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{token}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> revokeShareLink(
            @PathVariable("token") String token,
            @CurrentUser UserEntity currentUser) {
        shareService.revokeShareLink(token, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{token}/exclude")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ShareLinkResponseDto> excludeResourceFromShare(
            @PathVariable("token") String token,
            @RequestParam(value = "documentId", required = false) Long documentId,
            @RequestParam(value = "folderId", required = false) Long folderId,
            @CurrentUser UserEntity currentUser) {
        ShareLinkResponseDto response = shareService.excludeResourceFromShare(token, documentId, folderId, currentUser);
        return ResponseEntity.ok(response);
    }
}
