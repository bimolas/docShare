package com.subject77.docshare.modules.permission.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.document.dto.ConfirmUploadRequest;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.InitiateUploadRequest;
import com.subject77.docshare.modules.document.dto.InitiateUploadResponse;
import com.subject77.docshare.modules.permission.dto.FileRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.permission.services.FileRequestService;
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
@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/file-requests")
@RequiredArgsConstructor
public class FileRequestController {

    private final FileRequestService fileRequestService;

    @PostMapping
    public ResponseEntity<FileRequestResponseDto> createFileRequest(
            @Valid @RequestBody FileRequestCreateDto dto,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to create file request by user ID: {}", currentUser != null ? currentUser.getId() : null);
        FileRequestResponseDto response = fileRequestService.createFileRequest(dto, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mine")
    public ResponseEntity<List<FileRequestResponseDto>> getRequestsMine(
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get file requests created by user ID: {}", currentUser != null ? currentUser.getId() : null);
        List<FileRequestResponseDto> response = fileRequestService.getRequestsMine(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/received")
    public ResponseEntity<List<FileRequestResponseDto>> getRequestsReceived(
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get file requests received by user ID: {}", currentUser != null ? currentUser.getId() : null);
        List<FileRequestResponseDto> response = fileRequestService.getRequestsReceived(currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileRequestResponseDto> getFileRequestById(
            @PathVariable Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to get file request #{} by user ID: {}", id, currentUser != null ? currentUser.getId() : null);
        FileRequestResponseDto response = fileRequestService.getFileRequestById(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/uploads/initiate")
    public ResponseEntity<InitiateUploadResponse> initiateUpload(
            @PathVariable Long id,
            @Valid @RequestBody InitiateUploadRequest request,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to initiate upload for file request #{} by user ID: {}", id, currentUser != null ? currentUser.getId() : null);
        InitiateUploadResponse response = fileRequestService.initiateUpload(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/uploads/confirm")
    public ResponseEntity<DocumentResponse> confirmUpload(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmUploadRequest request,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to confirm upload for file request #{} by user ID: {}", id, currentUser != null ? currentUser.getId() : null);
        DocumentResponse response = fileRequestService.confirmUpload(id, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<FileRequestResponseDto> completeFileRequest(
            @PathVariable Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to complete file request #{} by user ID: {}", id, currentUser != null ? currentUser.getId() : null);
        FileRequestResponseDto response = fileRequestService.completeFileRequest(id, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<FileRequestResponseDto> cancelFileRequest(
            @PathVariable Long id,
            @CurrentUser UserEntity currentUser) {
        log.info("REST request to cancel file request #{} by user ID: {}", id, currentUser != null ? currentUser.getId() : null);
        FileRequestResponseDto response = fileRequestService.cancelFileRequest(id, currentUser);
        return ResponseEntity.ok(response);
    }
}
