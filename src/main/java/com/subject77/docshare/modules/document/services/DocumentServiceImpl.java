package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.dto.*;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.DocumentVersionEntity;
import com.subject77.docshare.modules.document.mapper.DocumentMapper;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.folder.dto.BulkMoveRequestDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final FolderRepository folderRepository;
    private final OrganizationRepository organizationRepository;
    private final StorageService storageService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final WorkspaceManagerRepository workspaceManagerRepository;
    private final UserOrganizationRepository userOrganizationRepository;

    @Override
    @Transactional
    public InitiateUploadResponse initiateUpload(InitiateUploadRequest request, UserEntity currentUser) {
        FolderEntity folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + request.getFolderId()));
        }

        if (!permissionEvaluatorService.canUploadToFolder(currentUser, folder, request.getOrgCode())) {
            throw new AccessDeniedException("User does not have permission to upload documents to this space/folder");
        }

        OrganizationEntity ownerOrg = null;
        if (request.getOrgCode() != null && !request.getOrgCode().isBlank()) {
            ownerOrg = organizationRepository.findById(request.getOrgCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + request.getOrgCode()));
        }

        // ── Idempotency guard ──────────────────────────────────────────────────
        // If a PENDING_UPLOAD record already exists for the same user + folder +
        // file hash, it means a previous initiate call succeeded but S3 upload
        // never completed (e.g. browser closed, presigned URL expired).  Reuse
        // the existing DB record and just regenerate a fresh presigned URL so the
        // client can retry the PUT without creating a duplicate document row.
        final FolderEntity finalFolder = folder;
        final OrganizationEntity finalOwnerOrg = ownerOrg;
        java.util.Optional<DocumentEntity> existingPending = documentRepository
                .findByFileHashAndUploadedBy_IdAndStatusAndFolder_Id(
                        request.getFileHash(),
                        currentUser.getId(),
                        DocumentStatus.PENDING_UPLOAD,
                        folder != null ? folder.getId() : null);

        if (existingPending.isPresent()) {
            DocumentEntity pending = existingPending.get();
            String freshUrl = storageService.generatePresignedUploadUrl(
                    pending.getObjectKey(), request.getContentType(), PRESIGNED_URL_EXPIRY_MINUTES);
            Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));
            return InitiateUploadResponse.builder()
                    .documentId(pending.getId())
                    .objectKey(pending.getObjectKey())
                    .s3Bucket(pending.getS3Bucket())
                    .presignedUploadUrl(freshUrl)
                    .expiresAt(expiresAt)
                    .status(pending.getStatus())
                    .build();
        }
        // ─────────────────────────────────────────────────────────────────────

        String prefix = (ownerOrg != null) ? "orgs/" + ownerOrg.getOrgCode() : "users/" + currentUser.getId();
        String sanitizedFileName = request.getFileName().replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String objectKey = prefix + "/" + UUID.randomUUID() + "-v1-" + sanitizedFileName;

        String presignedUploadUrl = storageService.generatePresignedUploadUrl(objectKey, request.getContentType(), PRESIGNED_URL_EXPIRY_MINUTES);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));

        DocumentEntity document = DocumentEntity.builder()
                .fileName(request.getFileName())
                .contentType(request.getContentType())
                .fileSize(request.getFileSize())
                .fileHash(request.getFileHash())
                .s3Bucket(storageService.getDefaultBucketName())
                .objectKey(objectKey)
                .status(DocumentStatus.PENDING_UPLOAD)
                .ownerOrg(ownerOrg)
                .uploadedBy(currentUser)
                .folder(folder)
                .uploadedAt(Instant.now())
                .version(1)
                .build();

        DocumentEntity savedDocument = documentRepository.save(document);

        auditLogService.logDocumentActivity(
                Action.INITIATE_UPLOAD,
                savedDocument.getId(),
                savedDocument.getFileHash(),
                folder != null ? folder.getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                ownerOrg != null ? ownerOrg.getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Initiated upload for document: " + request.getFileName(),
                null
        );

        return InitiateUploadResponse.builder()
                .documentId(savedDocument.getId())
                .objectKey(objectKey)
                .s3Bucket(storageService.getDefaultBucketName())
                .presignedUploadUrl(presignedUploadUrl)
                .expiresAt(expiresAt)
                .status(savedDocument.getStatus())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse confirmUpload(Long documentId, ConfirmUploadRequest request, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!document.getUploadedBy().getId().equals(currentUser.getId())
                && !permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to confirm this upload");
        }

        boolean existsInStorage = storageService.doesObjectExist(document.getObjectKey());
        if (!existsInStorage) {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            throw new StorageFileNotFoundException("File object not found in storage. Upload confirmation failed for document ID: " + documentId);
        }

        document.setStatus(DocumentStatus.ACTIVE);
        if (request != null && request.getClientHash() != null && !request.getClientHash().isBlank()) {
            document.setFileHash(request.getClientHash());
        }

        // Create initial DocumentVersionEntity (v1) snapshot if absent
        if (document.getVersions() == null || document.getVersions().isEmpty()) {
            DocumentVersionEntity v1 = DocumentVersionEntity.builder()
                    .document(document)
                    .versionNumber(1)
                    .s3Bucket(document.getS3Bucket())
                    .objectKey(document.getObjectKey())
                    .fileName(document.getFileName())
                    .contentType(document.getContentType())
                    .fileSize(document.getFileSize())
                    .checksumSha256(document.getFileHash() != null ? document.getFileHash() : "N/A")
                    .uploadedBy(document.getUploadedBy())
                    .uploadedAt(document.getUploadedAt() != null ? document.getUploadedAt() : Instant.now())
                    .build();

            DocumentVersionEntity savedV1 = documentVersionRepository.save(v1);
            document.setCurrentVersion(savedV1);
            if (document.getVersions() == null) {
                document.setVersions(new ArrayList<>());
            }
            document.getVersions().add(savedV1);
        }

        DocumentEntity updatedDocument = documentRepository.save(document);

        auditLogService.logDocumentActivity(
                Action.CONFIRM_UPLOAD,
                updatedDocument.getId(),
                updatedDocument.getFileHash(),
                updatedDocument.getFolder() != null ? updatedDocument.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                updatedDocument.getOwnerOrg() != null ? updatedDocument.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Confirmed upload for document: " + updatedDocument.getFileName(),
                null
        );

        return DocumentMapper.toDocumentResponse(updatedDocument);
    }

    @Override
    @Transactional
    public DownloadUrlResponse generateDownloadUrl(Long documentId, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canDownloadDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to download this document");
        }

        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new InvalidDocumentStateException("Document is not ready for download. Current status: " + document.getStatus());
        }

        String downloadUrl = storageService.generatePresignedDownloadUrl(document.getObjectKey(), document.getFileName(), PRESIGNED_URL_EXPIRY_MINUTES);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));

        auditLogService.logDocumentActivity(
                Action.DOWNLOAD_PRESIGNED,
                document.getId(),
                document.getFileHash(),
                document.getFolder() != null ? document.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Generated download URL for current version of document: " + document.getFileName(),
                null
        );

        return DownloadUrlResponse.builder()
                .documentId(document.getId())
                .downloadUrl(downloadUrl)
                .expiresAt(expiresAt)
                .fileName(document.getFileName())
                .build();
    }

    @Override
    @Transactional
    public PreviewUrlResponse generatePreviewUrl(Long documentId, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canViewDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to view this document");
        }

        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new InvalidDocumentStateException("Document is not ready for preview. Current status: " + document.getStatus());
        }

        String previewUrl = storageService.generatePresignedPreviewUrl(
                document.getObjectKey(),
                document.getFileName(),
                document.getContentType(),
                PRESIGNED_URL_EXPIRY_MINUTES
        );
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));

        auditLogService.logDocumentActivity(
                Action.PREVIEW,
                document.getId(),
                document.getFileHash(),
                document.getFolder() != null ? document.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Generated preview URL for document: " + document.getFileName(),
                null
        );

        return PreviewUrlResponse.builder()
                .documentId(document.getId())
                .previewUrl(previewUrl)
                .expiresAt(expiresAt)
                .fileName(document.getFileName())
                .contentType(document.getContentType())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse getDocumentDetails(Long documentId, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canViewDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to view this document");
        }

        auditLogService.logDocumentActivity(
                Action.VIEW_METADATA,
                document.getId(),
                document.getFileHash(),
                document.getFolder() != null ? document.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Viewed details of document: " + document.getFileName(),
                null
        );

        return DocumentMapper.toDocumentResponse(document);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to delete this document");
        }

        storageService.deleteObject(document.getObjectKey());

        auditLogService.logDocumentActivity(
                Action.DELETE_DOCUMENT,
                document.getId(),
                document.getFileHash(),
                document.getFolder() != null ? document.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Deleted document: " + document.getFileName(),
                null
        );

        documentRepository.delete(document);
    }

    @Override
    @Transactional
    public DocumentResponse updateMetadata(Long documentId, UpdateDocumentMetadataRequest request, UserEntity currentUser, String ipAddress, String userAgent) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to edit metadata for this document");
        }

        boolean renamed = false;
        boolean moved = false;

        if (request.getFileName() != null && !request.getFileName().isBlank() && !request.getFileName().equals(document.getFileName())) {
            String oldName = document.getFileName();
            document.setFileName(request.getFileName());
            renamed = true;
            auditLogService.logDocumentActivity(
                    Action.RENAME_METADATA,
                    document.getId(),
                    document.getFileHash(),
                    document.getFolder() != null ? document.getFolder().getId() : null,
                    currentUser != null ? currentUser.getId() : null,
                    currentUser.getEmail(),
                    document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                    ipAddress != null ? ipAddress : "0.0.0.0",
                    userAgent != null ? userAgent : "API",
                    "Renamed document from '" + oldName + "' to '" + request.getFileName() + "'",
                    null
            );
        }

        if (request.getFolderId() != null) {
            FolderEntity newFolder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found with ID: " + request.getFolderId()));
            Long oldFolderId = document.getFolder() != null ? document.getFolder().getId() : null;
            if (oldFolderId == null || !oldFolderId.equals(newFolder.getId())) {
                document.setFolder(newFolder);
                moved = true;
                auditLogService.logDocumentActivity(
                        Action.MOVE_DOCUMENT,
                        document.getId(),
                        document.getFileHash(),
                        newFolder.getId(),
                        currentUser != null ? currentUser.getId() : null,
                        currentUser.getEmail(),
                        document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                        ipAddress != null ? ipAddress : "0.0.0.0",
                        userAgent != null ? userAgent : "API",
                        "Moved document to folder ID: " + newFolder.getId(),
                        null
                );
            }
        }

        if (renamed || moved) {
            document.setLastModifiedBy(currentUser);
            document.setLastModifiedAt(Instant.now());
            if (document.getOwnerOrg() != null) {
                document.setLastModifiedByOrg(document.getOwnerOrg());
            }
        }

        DocumentEntity updatedDocument = documentRepository.save(document);
        return DocumentMapper.toDocumentResponse(updatedDocument);
    }

    @Override
    @Transactional
    public InitiateUploadResponse initiateNewVersion(Long documentId, InitiateNewVersionRequest request, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to upload a new version of this document");
        }

        List<DocumentVersionEntity> existingVersions = documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(documentId);
        int nextVersionNumber = existingVersions.isEmpty() ? 2 : existingVersions.get(0).getVersionNumber() + 1;

        String prefix = (document.getOwnerOrg() != null) ? "orgs/" + document.getOwnerOrg().getOrgCode() : "users/" + currentUser.getId();
        String targetFileName = (request.getFileName() != null && !request.getFileName().isBlank()) ? request.getFileName() : document.getFileName();
        String sanitizedFileName = targetFileName.replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String objectKey = prefix + "/" + UUID.randomUUID() + "-v" + nextVersionNumber + "-" + sanitizedFileName;

        String presignedUploadUrl = storageService.generatePresignedUploadUrl(objectKey, request.getContentType(), PRESIGNED_URL_EXPIRY_MINUTES);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));

        auditLogService.logDocumentActivity(
                Action.INITIATE_NEW_VERSION,
                document.getId(),
                request.getChecksumSha256(),
                document.getFolder() != null ? document.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Initiated upload for version v" + nextVersionNumber + " of document ID: " + documentId,
                null
        );

        return InitiateUploadResponse.builder()
                .documentId(document.getId())
                .objectKey(objectKey)
                .s3Bucket(storageService.getDefaultBucketName())
                .presignedUploadUrl(presignedUploadUrl)
                .expiresAt(expiresAt)
                .status(document.getStatus())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse confirmNewVersion(Long documentId, ConfirmUploadRequest request, UserEntity currentUser, String ipAddress, String userAgent) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to confirm new version upload");
        }

        String objectKey = request.getObjectKey() != null ? request.getObjectKey() : document.getObjectKey();
        boolean existsInStorage = storageService.doesObjectExist(objectKey);
        if (!existsInStorage) {
            throw new StorageFileNotFoundException("File object not found in storage. Version upload confirmation failed for document ID: " + documentId);
        }

        // Snapshot v1 if missing
        List<DocumentVersionEntity> existingVersions = documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(documentId);
        if (existingVersions.isEmpty()) {
            DocumentVersionEntity v1 = DocumentVersionEntity.builder()
                    .document(document)
                    .versionNumber(1)
                    .s3Bucket(document.getS3Bucket())
                    .objectKey(document.getObjectKey())
                    .fileName(document.getFileName())
                    .contentType(document.getContentType())
                    .fileSize(document.getFileSize())
                    .checksumSha256(document.getFileHash() != null ? document.getFileHash() : "N/A")
                    .uploadedBy(document.getUploadedBy())
                    .uploadedAt(document.getUploadedAt() != null ? document.getUploadedAt() : Instant.now())
                    .build();

            DocumentVersionEntity savedV1 = documentVersionRepository.save(v1);
            existingVersions.add(savedV1);
        }

        int nextVersionNumber = existingVersions.get(0).getVersionNumber() + 1;
        String fileName = request.getFileName() != null ? request.getFileName() : document.getFileName();

        DocumentVersionEntity newVersion = DocumentVersionEntity.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .s3Bucket(storageService.getDefaultBucketName())
                .objectKey(objectKey)
                .fileName(fileName)
                .contentType(request.getContentType() != null ? request.getContentType() : document.getContentType())
                .fileSize(request.getFileSize() != null ? request.getFileSize() : document.getFileSize())
                .checksumSha256(request.getClientHash() != null ? request.getClientHash() : "N/A")
                .uploadedBy(currentUser)
                .uploadedAt(Instant.now())
                .build();

        DocumentVersionEntity savedVersion = documentVersionRepository.save(newVersion);

        // Update DocumentEntity current state
        document.setFileName(fileName);
        document.setContentType(newVersion.getContentType());
        document.setFileSize(newVersion.getFileSize());
        document.setObjectKey(objectKey);
        document.setFileHash(newVersion.getChecksumSha256());
        document.setCurrentVersion(savedVersion);
        document.setLastModifiedBy(currentUser);
        document.setLastModifiedAt(Instant.now());
        if (currentUser.getOrganization() != null) {
            document.setLastModifiedByOrg(currentUser.getOrganization());
        }

        DocumentEntity updatedDocument = documentRepository.save(document);

        auditLogService.logDocumentActivity(
                Action.CONFIRM_NEW_VERSION,
                updatedDocument.getId(),
                savedVersion.getChecksumSha256(),
                updatedDocument.getFolder() != null ? updatedDocument.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                updatedDocument.getOwnerOrg() != null ? updatedDocument.getOwnerOrg().getOrgCode() : null,
                ipAddress != null ? ipAddress : "0.0.0.0",
                userAgent != null ? userAgent : "API",
                "Uploaded new version v" + nextVersionNumber + " for document: " + fileName,
                null
        );

        return DocumentMapper.toDocumentResponse(updatedDocument);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentVersionResponse> getDocumentVersions(Long documentId, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canViewDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to view versions for this document");
        }

        List<DocumentVersionEntity> versions = documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(documentId);

        if (versions.isEmpty()) {
            // Synthetic v1 response from current DocumentEntity
            return List.of(DocumentVersionResponse.builder()
                    .id(null)
                    .documentId(document.getId())
                    .versionNumber(1)
                    .fileName(document.getFileName())
                    .fileSize(document.getFileSize())
                    .contentType(document.getContentType())
                    .checksumSha256(document.getFileHash())
                    .uploadedByEmail(document.getUploadedBy() != null ? document.getUploadedBy().getEmail() : "SYSTEM")
                    .uploadedAt(document.getUploadedAt())
                    .isCurrent(true)
                    .build());
        }

        Long currentVersionId = document.getCurrentVersion() != null ? document.getCurrentVersion().getId() : null;

        return versions.stream().map(v -> DocumentVersionResponse.builder()
                .id(v.getId())
                .documentId(documentId)
                .versionNumber(v.getVersionNumber())
                .fileName(v.getFileName())
                .fileSize(v.getFileSize())
                .contentType(v.getContentType())
                .checksumSha256(v.getChecksumSha256())
                .uploadedByEmail(v.getUploadedBy() != null ? v.getUploadedBy().getEmail() : "SYSTEM")
                .uploadedAt(v.getUploadedAt())
                .isCurrent(currentVersionId != null ? currentVersionId.equals(v.getId()) : (v.getVersionNumber() == versions.get(0).getVersionNumber()))
                .build()
        ).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DownloadUrlResponse generateVersionDownloadUrl(Long documentId, Integer versionNumber, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canDownloadDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to download versions of this document");
        }

        DocumentVersionEntity version = documentVersionRepository.findByDocument_IdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + versionNumber + " not found for document ID: " + documentId));

        String downloadUrl = storageService.generatePresignedDownloadUrl(version.getObjectKey(), version.getFileName(), PRESIGNED_URL_EXPIRY_MINUTES);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));

        auditLogService.logDocumentActivity(
                Action.DOWNLOAD_PRESIGNED,
                document.getId(),
                version.getChecksumSha256(),
                document.getFolder() != null ? document.getFolder().getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Generated download URL for version v" + versionNumber + " of document: " + version.getFileName(),
                null
        );

        return DownloadUrlResponse.builder()
                .documentId(document.getId())
                .downloadUrl(downloadUrl)
                .expiresAt(expiresAt)
                .fileName(version.getFileName())
                .build();
    }

    @Override
    @Transactional
    public DocumentResponse restoreVersion(Long documentId, Integer versionNumber, RestoreVersionRequest request, UserEntity currentUser, String ipAddress, String userAgent) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to restore versions of this document");
        }

        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new InvalidDocumentStateException("Document is not in ACTIVE state. Current status: " + document.getStatus());
        }

        DocumentVersionEntity sourceVersion = documentVersionRepository.findByDocument_IdAndVersionNumber(documentId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Version " + versionNumber + " not found for document ID: " + documentId));

        DocumentVersionEntity previousActiveVersion = document.getCurrentVersion();
        if (previousActiveVersion != null && previousActiveVersion.getId().equals(sourceVersion.getId())) {
            throw new InvalidDocumentStateException("Version " + versionNumber + " is already the active version of document ID: " + documentId);
        }

        boolean existsInStorage = storageService.doesObjectExist(sourceVersion.getS3Bucket(), sourceVersion.getObjectKey());
        if (!existsInStorage) {
            throw new StorageFileNotFoundException("File object not found in storage. Version restore failed for document ID: "
                    + documentId + ", version: " + versionNumber);
        }

        List<DocumentVersionEntity> existingVersions = documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(documentId);
        int nextVersionNumber = existingVersions.isEmpty() ? 1 : existingVersions.get(0).getVersionNumber() + 1;

        DocumentVersionEntity newVersion = DocumentVersionEntity.builder()
                .document(document)
                .versionNumber(nextVersionNumber)
                .s3Bucket(sourceVersion.getS3Bucket())
                .objectKey(sourceVersion.getObjectKey())
                .fileName(sourceVersion.getFileName())
                .contentType(sourceVersion.getContentType())
                .fileSize(sourceVersion.getFileSize())
                .checksumSha256(sourceVersion.getChecksumSha256())
                .uploadedBy(currentUser)
                .uploadedAt(Instant.now())
                .build();

        DocumentVersionEntity savedVersion = documentVersionRepository.save(newVersion);

        // Update DocumentEntity current state
        document.setFileName(savedVersion.getFileName());
        document.setContentType(savedVersion.getContentType());
        document.setFileSize(savedVersion.getFileSize());
        document.setObjectKey(savedVersion.getObjectKey());
        document.setFileHash(savedVersion.getChecksumSha256());
        document.setCurrentVersion(savedVersion);
        document.setLastModifiedBy(currentUser);
        document.setLastModifiedAt(Instant.now());
        if (currentUser.getOrganization() != null) {
            document.setLastModifiedByOrg(currentUser.getOrganization());
        }

        DocumentEntity updatedDocument = documentRepository.save(document);

        // Resolve workspace scope for audit & notifications
        Long workspaceId = null;
        ProjectWorkspaceEntity workspace = null;
        if (document.getFolder() != null) {
            FolderEntity cursor = document.getFolder();
            while (cursor.getParentFolder() != null) {
                cursor = cursor.getParentFolder();
            }
            if (cursor.getId() != null && projectWorkspaceRepository != null) {
                workspace = projectWorkspaceRepository.findFirstByRootFolder_Id(cursor.getId()).orElse(null);
                if (workspace != null) {
                    workspaceId = workspace.getId();
                }
            }
        }

        String orgCode = document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null;
        SpaceType spaceType = orgCode != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL;

        // Phase 6B Governance Audit Logging (Atomic with Transaction via Propagation.REQUIRED)
        String prevVal = (previousActiveVersion != null)
                ? "v" + previousActiveVersion.getVersionNumber() + " (id=" + previousActiveVersion.getId() + ")"
                : "N/A";
        String newVal = "v" + nextVersionNumber + " (id=" + savedVersion.getId() + ", source_version=v" + sourceVersion.getVersionNumber() + ", source_id=" + sourceVersion.getId() + ")";
        String reason = request != null ? request.getReason() : null;
        String details = String.format("Restored version v%d (id=%d) as new version v%d (id=%d); previous active version: %s%s",
                sourceVersion.getVersionNumber(),
                sourceVersion.getId(),
                nextVersionNumber,
                savedVersion.getId(),
                prevVal,
                (reason != null && !reason.isBlank() ? "; reason: " + reason.trim() : ""));

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("DOCUMENT_VERSION_RESTORED")
                .targetType("DOCUMENT")
                .targetId(document.getId())
                .targetFileHash(savedVersion.getChecksumSha256())
                .folderId(document.getFolder() != null ? document.getFolder().getId() : null)
                .workspaceId(workspaceId)
                .spaceType(spaceType)
                .orgCode(orgCode)
                .actorUserId(currentUser.getId())
                .grantedBy(currentUser)
                .previousValue(prevVal)
                .newValue(newVal)
                .reason(reason)
                .details(details)
                .timestamp(Instant.now())
                .build();

        auditLogService.logPermissionChange(auditCmd);

        // Notification Dispatch with Deduplication and Actor Exclusion
        if (notificationService != null) {
            Map<String, UserEntity> recipients = new LinkedHashMap<>();

            // 1. Document Owner (if distinct from actor and active)
            UserEntity owner = document.getUploadedBy();
            if (owner != null && !owner.getId().equals(currentUser.getId()) && isUserEligibleForNotification(owner, orgCode)) {
                recipients.put(owner.getId(), owner);
            }

            // 2. Previous Active Version Author (if distinct from actor and active)
            UserEntity prevAuthor = previousActiveVersion != null ? previousActiveVersion.getUploadedBy() : null;
            if (prevAuthor != null && !prevAuthor.getId().equals(currentUser.getId()) && isUserEligibleForNotification(prevAuthor, orgCode)) {
                recipients.put(prevAuthor.getId(), prevAuthor);
            }

            // 3. Active Workspace Managers (Only if organizational workspace)
            if (workspace != null && !workspace.isPersonal() && workspaceId != null && workspaceManagerRepository != null) {
                List<WorkspaceManagerEntity> managers = workspaceManagerRepository.findByWorkspace_Id(workspaceId);
                if (managers != null) {
                    for (WorkspaceManagerEntity mgr : managers) {
                        UserEntity wmUser = mgr.getUser();
                        if (wmUser != null && !wmUser.getId().equals(currentUser.getId()) && isUserEligibleForNotification(wmUser, orgCode)) {
                            recipients.put(wmUser.getId(), wmUser);
                        }
                    }
                }
            }

            for (UserEntity recipient : recipients.values()) {
                String idempotencyKey = "DOCUMENT_VERSION_RESTORED:" + savedVersion.getId() + ":" + recipient.getId();
                String title = "Document Version Restored";
                String body = String.format("Version v%d of '%s' was restored as version v%d by %s.",
                        sourceVersion.getVersionNumber(),
                        document.getFileName(),
                        nextVersionNumber,
                        currentUser.getEmail());

                notificationService.createNotification(
                        recipient,
                        currentUser,
                        NotificationType.DOCUMENT_VERSION_RESTORED,
                        title,
                        body,
                        NotificationSourceType.DOCUMENT,
                        String.valueOf(document.getId()),
                        NotificationCategory.DOCUMENT,
                        NotificationPriority.MEDIUM,
                        idempotencyKey
                );
            }
        }

        return DocumentMapper.toDocumentResponse(updatedDocument);
    }

    private boolean isUserEligibleForNotification(UserEntity user, String orgCode) {
        if (user == null || user.getId() == null) {
            return false;
        }
        if (orgCode != null && !orgCode.isBlank()) {
            if (userOrganizationRepository == null) {
                return false;
            }
            return userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                    user.getId(), orgCode, MembershipStatus.ACTIVE);
        }
        return true;
    }

    @Override
    @Transactional
    public DocumentResponse moveDocument(Long documentId, Long targetFolderId, UserEntity currentUser) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!permissionEvaluatorService.canEditDocument(currentUser, document)) {
            throw new AccessDeniedException("User does not have permission to edit/move document ID: " + documentId);
        }

        FolderEntity targetFolder = null;
        if (targetFolderId != null) {
            targetFolder = folderRepository.findById(targetFolderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found with ID: " + targetFolderId));

            if (!permissionEvaluatorService.canEditFolder(currentUser, targetFolder)) {
                throw new AccessDeniedException("User does not have permission to write/move into target folder ID: " + targetFolderId);
            }
        }

        document.setFolder(targetFolder);
        document.setLastModifiedBy(currentUser);
        document.setLastModifiedAt(Instant.now());
        if (currentUser.getOrganization() != null) {
            document.setLastModifiedByOrg(currentUser.getOrganization());
        }

        DocumentEntity updated = documentRepository.save(document);

        auditLogService.logDocumentActivity(
                Action.MOVE_DOCUMENT,
                updated.getId(),
                updated.getFileHash(),
                targetFolder != null ? targetFolder.getId() : null,
                currentUser != null ? currentUser.getId() : null,
                currentUser.getEmail(),
                updated.getOwnerOrg() != null ? updated.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0",
                "API",
                "Moved document ID " + documentId + " to folder ID " + targetFolderId,
                null
        );

        return DocumentMapper.toDocumentResponse(updated);
    }

    @Override
    @Transactional
    public List<DocumentResponse> bulkMoveDocuments(BulkMoveRequestDto request, UserEntity currentUser) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            return List.of();
        }

        List<DocumentResponse> movedDocs = new ArrayList<>();
        for (Long docId : request.getIds()) {
            movedDocs.add(moveDocument(docId, request.getTargetFolderId(), currentUser));
        }

        return movedDocs;
    }
}
