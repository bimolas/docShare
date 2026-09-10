package com.subject77.docshare.modules.folder.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.folder.dto.BulkMoveRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderContentResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderMoveRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderPeekResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.services.FolderService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FolderController {

    private final FolderService folderService;

    @PostMapping("/organizations/{orgCode}/subspaces")
    public ResponseEntity<FolderResponseDto> createSubspace(
            @PathVariable("orgCode") String orgCode,
            @Valid @RequestBody FolderRequestDto request,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.createSubspace(orgCode, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/organizations/{orgCode}/subspaces")
    public ResponseEntity<List<FolderResponseDto>> getOrganizationSubspaces(
            @PathVariable("orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<FolderResponseDto> response = folderService.getOrganizationSubspaces(orgCode, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/organizations/{orgCode}/folders")
    public ResponseEntity<FolderResponseDto> createFolder(
            @PathVariable("orgCode") String orgCode,
            @Valid @RequestBody FolderRequestDto request,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.createFolder(orgCode, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/personal/subspaces")
    public ResponseEntity<FolderResponseDto> createPersonalSubspace(
            @Valid @RequestBody FolderRequestDto request,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.createPersonalSubspace(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/personal/subspaces")
    public ResponseEntity<List<FolderResponseDto>> getPersonalSubspaces(
            @CurrentUser UserEntity currentUser) {
        List<FolderResponseDto> response = folderService.getPersonalSubspaces(currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/personal/folders")
    public ResponseEntity<FolderResponseDto> createPersonalFolder(
            @Valid @RequestBody FolderRequestDto request,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.createPersonalFolder(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @GetMapping("/folders/{folderId}")
    public ResponseEntity<FolderResponseDto> getFolderById(
            @PathVariable("folderId") Long folderId,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.getFolderById(folderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/folders/{folderId}/contents")
    public ResponseEntity<FolderContentResponseDto> getFolderContents(
            @PathVariable("folderId") Long folderId,
            @CurrentUser UserEntity currentUser) {
        FolderContentResponseDto response = folderService.getFolderContents(folderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable("folderId") Long folderId,
            @CurrentUser UserEntity currentUser) {
        folderService.deleteFolder(folderId, currentUser);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/folders/{folderId}/move")
    public ResponseEntity<FolderResponseDto> moveFolder(
            @PathVariable("folderId") Long folderId,
            @Valid @RequestBody FolderMoveRequestDto request,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.moveFolder(folderId, request != null ? request.getTargetFolderId() : null, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/folders/{folderId}/peek")
    public ResponseEntity<FolderPeekResponseDto> getFolderPeek(
            @PathVariable("folderId") Long folderId,
            @CurrentUser UserEntity currentUser) {
        FolderPeekResponseDto response = folderService.getFolderPeek(folderId, currentUser);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/folders/bulk-move")
    public ResponseEntity<List<FolderResponseDto>> bulkMoveFolders(
            @Valid @RequestBody BulkMoveRequestDto request,
            @CurrentUser UserEntity currentUser) {
        List<FolderResponseDto> response = folderService.bulkMoveFolders(request, currentUser);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/folders/{folderId}")
    public ResponseEntity<FolderResponseDto> renameFolder(
            @PathVariable("folderId") Long folderId,
            @Valid @RequestBody FolderRequestDto request,
            @CurrentUser UserEntity currentUser) {
        FolderResponseDto response = folderService.renameFolder(folderId, request.getName(), currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/folders/{folderId}/download")
    public void downloadFolder(
            @PathVariable("folderId") Long folderId,
            @CurrentUser UserEntity currentUser,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        
        FolderResponseDto folder = folderService.getFolderById(folderId, currentUser);
        
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + folder.getName() + ".zip\"");
        
        folderService.downloadFolderAsZip(folderId, currentUser, response.getOutputStream());
    }
}
