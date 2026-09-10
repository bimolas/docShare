package com.subject77.docshare.modules.document.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.document.dto.*;
import com.subject77.docshare.modules.document.services.DocumentService;
import com.subject77.docshare.modules.folder.dto.BulkMoveRequestDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            @Valid @RequestBody InitiateUploadRequest request,
            @CurrentUser UserEntity currentUser) {
        InitiateUploadResponse response = documentService.initiateUpload(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/upload/confirm")
    public ResponseEntity<DocumentResponse> confirmUpload(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ConfirmUploadRequest request,
            @CurrentUser UserEntity currentUser) {
        DocumentResponse response = documentService.confirmUpload(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<DownloadUrlResponse> getDownloadUrl(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        DownloadUrlResponse response = documentService.generateDownloadUrl(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<PreviewUrlResponse> getPreviewUrl(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        PreviewUrlResponse response = documentService.generatePreviewUrl(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getDocumentDetails(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        DocumentResponse response = documentService.getDocumentDetails(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        documentService.deleteDocument(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/metadata")
    public ResponseEntity<DocumentResponse> updateMetadata(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateDocumentMetadataRequest request,
            @CurrentUser UserEntity currentUser,
            HttpServletRequest httpServletRequest) {
        String ipAddress = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        DocumentResponse response = documentService.updateMetadata(id, request, currentUser, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/versions/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateNewVersion(
            @PathVariable("id") Long id,
            @Valid @RequestBody InitiateNewVersionRequest request,
            @CurrentUser UserEntity currentUser) {
        InitiateUploadResponse response = documentService.initiateNewVersion(id, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/versions/confirm")
    public ResponseEntity<DocumentResponse> confirmNewVersion(
            @PathVariable("id") Long id,
            @RequestBody(required = false) ConfirmUploadRequest request,
            @CurrentUser UserEntity currentUser,
            HttpServletRequest httpServletRequest) {
        String ipAddress = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        DocumentResponse response = documentService.confirmNewVersion(id, request, currentUser, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<DocumentVersionResponse>> getDocumentVersions(
            @PathVariable("id") Long id,
            @CurrentUser UserEntity currentUser) {
        List<DocumentVersionResponse> response = documentService.getDocumentVersions(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/versions/{versionNumber}/download")
    public ResponseEntity<DownloadUrlResponse> getVersionDownloadUrl(
            @PathVariable("id") Long id,
            @PathVariable("versionNumber") Integer versionNumber,
            @CurrentUser UserEntity currentUser) {
        DownloadUrlResponse response = documentService.generateVersionDownloadUrl(id, versionNumber, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/versions/{versionNumber}/restore")
    public ResponseEntity<DocumentResponse> restoreVersion(
            @PathVariable("id") Long id,
            @PathVariable("versionNumber") Integer versionNumber,
            @RequestBody(required = false) RestoreVersionRequest request,
            @CurrentUser UserEntity currentUser,
            HttpServletRequest httpServletRequest) {
        String ipAddress = httpServletRequest.getRemoteAddr();
        String userAgent = httpServletRequest.getHeader("User-Agent");
        DocumentResponse response = documentService.restoreVersion(id, versionNumber, request, currentUser, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<DocumentResponse> moveDocument(
            @PathVariable("id") Long id,
            @Valid @RequestBody DocumentMoveRequestDto request,
            @CurrentUser UserEntity currentUser) {
        DocumentResponse response = documentService.moveDocument(id, request != null ? request.getTargetFolderId() : null, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bulk-move")
    public ResponseEntity<List<DocumentResponse>> bulkMoveDocuments(
            @Valid @RequestBody BulkMoveRequestDto request,
            @CurrentUser UserEntity currentUser) {
        List<DocumentResponse> response = documentService.bulkMoveDocuments(request, currentUser);
        return ResponseEntity.ok(response);
    }
}

