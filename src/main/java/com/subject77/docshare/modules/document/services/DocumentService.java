package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.modules.document.dto.*;
import com.subject77.docshare.modules.folder.dto.BulkMoveRequestDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface DocumentService {
    InitiateUploadResponse initiateUpload(InitiateUploadRequest request, UserEntity currentUser);
    DocumentResponse confirmUpload(Long documentId, ConfirmUploadRequest request, UserEntity currentUser);
    DownloadUrlResponse generateDownloadUrl(Long documentId, UserEntity currentUser);
    PreviewUrlResponse generatePreviewUrl(Long documentId, UserEntity currentUser);
    DocumentResponse getDocumentDetails(Long documentId, UserEntity currentUser);
    void deleteDocument(Long documentId, UserEntity currentUser);
    DocumentResponse updateMetadata(Long documentId, UpdateDocumentMetadataRequest request, UserEntity currentUser, String ipAddress, String userAgent);
    InitiateUploadResponse initiateNewVersion(Long documentId, InitiateNewVersionRequest request, UserEntity currentUser);
    DocumentResponse confirmNewVersion(Long documentId, ConfirmUploadRequest request, UserEntity currentUser, String ipAddress, String userAgent);
    java.util.List<DocumentVersionResponse> getDocumentVersions(Long documentId, UserEntity currentUser);
    DownloadUrlResponse generateVersionDownloadUrl(Long documentId, Integer versionNumber, UserEntity currentUser);
    DocumentResponse restoreVersion(Long documentId, Integer versionNumber, RestoreVersionRequest request, UserEntity currentUser, String ipAddress, String userAgent);
    DocumentResponse moveDocument(Long documentId, Long targetFolderId, UserEntity currentUser);
    List<DocumentResponse> bulkMoveDocuments(BulkMoveRequestDto request, UserEntity currentUser);
}
