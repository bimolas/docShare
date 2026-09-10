package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.DownloadUrlResponse;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.document.mapper.DocumentMapper;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.dto.FolderContentResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.permission.dto.ShareLinkRequestDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkRevealResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.events.PermissionChangeEvent;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private static final long PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionEvaluatorService evaluatorService;
    private final PermissionEventPublisher eventPublisher;
    private final PermissionCacheService cacheService;
    private final StorageService storageService;
    private final ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @Override
    @Transactional
    public ShareLinkResponseDto createShareLink(ShareLinkRequestDto request, UserEntity currentUser) {
        if (request.getDocumentId() == null && request.getFolderId() == null) {
            throw new IllegalArgumentException("Either documentId or folderId must be provided to create a share link.");
        }

        DocumentEntity document = null;
        if (request.getDocumentId() != null) {
            document = documentRepository.findById(request.getDocumentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + request.getDocumentId()));
        }

        FolderEntity folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + request.getFolderId()));
        }

        // Reject mismatched folder/document resource combinations
        if (folder != null && document != null) {
            String folderOrgCode = (folder.getOwnerOrg() != null) ? folder.getOwnerOrg().getOrgCode() : null;
            String documentOrgCode = (document.getOwnerOrg() != null) ? document.getOwnerOrg().getOrgCode() : null;
            if ((folderOrgCode == null && documentOrgCode != null) ||
                (folderOrgCode != null && documentOrgCode == null) ||
                (folderOrgCode != null && !folderOrgCode.equalsIgnoreCase(documentOrgCode))) {
                throw new IllegalArgumentException("Folder and document belong to different organizations");
            }
        }

        // Access-Administration Enforcement
        if (folder != null) {
            if (!evaluatorService.canAdministerFolderPermissions(currentUser, folder)) {
                throw new AccessDeniedException("User does not have authority to administer folder permissions");
            }
        }
        if (document != null) {
            if (!evaluatorService.canAdministerDocumentPermissions(currentUser, document)) {
                throw new AccessDeniedException("User does not have authority to administer document permissions");
            }
        }

        // Delegation Ceiling Enforcement (Phase 3B.2 & 3B.4)
        PermissionLevel delegationCeiling = evaluatorService.resolveDelegationCeiling(currentUser, folder, document);
        if (delegationCeiling == null || request.getPermissionLevel().ordinal() > delegationCeiling.ordinal()) {
            throw new AccessDeniedException("Requested permission level " 
                    + request.getPermissionLevel() + " exceeds delegator ceiling of " + delegationCeiling);
        }

        RecipientType recipientType = RecipientType.USER_PERSONAL;

        // Policy & Organization Isolation Evaluation
        PermissionRequestDto permRequest = PermissionRequestDto.builder()
                .permissionType(request.getPermissionType() != null ? request.getPermissionType() : PermissionType.ALLOW)
                .permissionLevel(request.getPermissionLevel())
                .recipientType(recipientType)
                .targetUserEmail(request.getTargetUserEmail())
                .folderId(folder != null ? folder.getId() : null)
                .documentId(document != null ? document.getId() : null)
                .documentFileHash(document != null ? document.getFileHash() : null)
                .build();

        externalSharingPolicyEvaluator.evaluateAndValidateSharingPolicy(folder, document, permRequest, null);

        String token = UUID.randomUUID().toString().replace("-", "");

        String encodedPassword = null;
        if (request.getPassword() != null && !request.getPassword().trim().isEmpty()) {
            encodedPassword = passwordEncoder.encode(request.getPassword().trim());
        }

        Instant expiresAt = null;
        if (request.getExpiresInHours() != null && request.getExpiresInHours() > 0) {
            expiresAt = Instant.now().plus(request.getExpiresInHours(), ChronoUnit.HOURS);
        }

        FolderEntity targetFolder = (folder != null) ? folder : (document != null ? document.getFolder() : null);
        CollaborationEntity collaboration = evaluatorService.resolveCollaborationForFolder(targetFolder);
        AuthorityTier resolvedAuthorityTier = evaluatorService.resolveAuthorityTierAtCreation(currentUser, folder, document, collaboration);
        Long resolvedWorkspaceId = null;
        if (resolvedAuthorityTier == AuthorityTier.WORKSPACE_MANAGER) {
            resolvedWorkspaceId = evaluatorService.resolveWorkspaceIdForManager(currentUser, targetFolder, null);
        }

        PermissionEntity permission = PermissionEntity.builder()
                .permissionType(request.getPermissionType() != null ? request.getPermissionType() : PermissionType.ALLOW)
                .permissionLevel(request.getPermissionLevel())
                .recipientType(recipientType)
                .grantedBy(currentUser)
                .grantorAuthorityTier(resolvedAuthorityTier)
                .workspaceId(resolvedWorkspaceId)
                .collaboration(resolvedAuthorityTier == AuthorityTier.COLLABORATION_MANAGER ? collaboration : null)
                .document(document)
                .documentFileHash(document != null ? document.getFileHash() : null)
                .folder(folder)
                .targetUserEmail(request.getTargetUserEmail())
                .shareLinkToken(token)
                .passwordHash(encodedPassword)
                .validFrom(Instant.now())
                .expiresAt(expiresAt)
                .note(request.getNote())
                .blockchainStatus(BlockchainStatus.PENDING)
                .revoked(false)
                .build();

        permission.setRecordHash(PermissionHashUtils.calculateRecordHash(permission));

        PermissionEntity savedPermission = permissionRepository.save(permission);
        log.info("Created share link (permission ID: {}) for {} (ID: {}) by user {}",
                savedPermission.getId(),
                document != null ? "document" : "folder",
                document != null ? document.getId() : folder.getId(),
                currentUser.getId());

        // Audit Logging
        String targetType = folder != null ? "FOLDER" : "DOCUMENT";
        Long targetId = folder != null ? folder.getId() : (document != null ? document.getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("CREATED_SHARE_LINK")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(document != null ? document.getFileHash() : null)
                .folderId(folder != null ? folder.getId() : (document != null && document.getFolder() != null ? document.getFolder().getId() : null))
                .workspaceId(savedPermission.getWorkspaceId())
                .groupId(savedPermission.getTargetGroup() != null ? savedPermission.getTargetGroup().getId() : null)
                .collaborationId(savedPermission.getCollaboration() != null ? savedPermission.getCollaboration().getId() : null)
                .spaceType(folder != null ? (folder.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (document != null ? (document.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(folder != null && folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : (document != null && document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null))
                .actorUserId(currentUser != null ? currentUser.getId() : null)
                .grantedBy(currentUser)
                .grantedToEmail(request.getTargetUserEmail())
                .permissionLevel(request.getPermissionLevel())
                .validFrom(savedPermission.getValidFrom())
                .expiresAt(savedPermission.getExpiresAt())
                .recordHash(savedPermission.getRecordHash())
                .blockchainStatus(savedPermission.getBlockchainStatus())
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event for Web3 Blockchain Anchoring
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("GRANT")
                .recordHash(savedPermission.getRecordHash())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Redis Cache Invalidation
        if (document != null) {
            cacheService.evictDocumentPermissions(document.getId());
        }
        if (request.getTargetUserEmail() != null) {
            cacheService.evictUserPermissions(request.getTargetUserEmail());
        }

        return mapToShareLinkResponse(savedPermission);
    }

    @Override
    @Transactional
    public ShareLinkRevealResponseDto revealShareLink(Long permissionId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to reveal share link.");
        }
        if (permissionId == null) {
            throw new IllegalArgumentException("Permission ID must not be null.");
        }

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found with ID: " + permissionId));

        if (permission.getShareLinkToken() == null || permission.getShareLinkToken().isBlank()) {
            throw new IllegalArgumentException("Permission is not a share link.");
        }

        if (permission.isRevoked()) {
            throw new IllegalArgumentException("Cannot reveal a revoked share link.");
        }

        if (permission.getExpiresAt() != null && Instant.now().isAfter(permission.getExpiresAt())) {
            throw new IllegalArgumentException("Cannot reveal an expired share link.");
        }

        FolderEntity folder = permission.getFolder();
        DocumentEntity doc = permission.getDocument();
        if (doc == null && permission.getDocumentFileHash() != null) {
            List<DocumentEntity> docs = documentRepository.findByFileHash(permission.getDocumentFileHash());
            if (docs.size() == 1) {
                doc = docs.get(0);
            }
        }

        String ownerOrgCode = null;
        if (folder != null && folder.getOwnerOrg() != null) {
            ownerOrgCode = folder.getOwnerOrg().getOrgCode();
        } else if (doc != null && doc.getOwnerOrg() != null) {
            ownerOrgCode = doc.getOwnerOrg().getOrgCode();
        }

        boolean isCreator = permission.getGrantedBy() != null
                && permission.getGrantedBy().getId().equals(currentUser.getId());

        boolean isPersonalOwner = (ownerOrgCode == null)
                && ((folder != null && evaluatorService.isFolderOwner(currentUser, folder))
                    || (doc != null && evaluatorService.isDocumentOwner(currentUser, doc)));

        if (!isCreator && !isPersonalOwner) {
            throw new AccessDeniedException("Only the original creator of this share link (or the personal resource owner) has authority to reveal the bearer secret.");
        }

        // Verify caller still has active access to the resource
        boolean hasAccess = false;
        if (folder != null) {
            hasAccess = evaluatorService.canViewFolder(currentUser, folder);
        } else if (doc != null) {
            hasAccess = evaluatorService.canViewDocument(currentUser, doc);
        }
        if (!hasAccess) {
            throw new AccessDeniedException("User no longer has access to this resource.");
        }

        // Audit Logging - explicitly capturing REVEALED_SHARE_LINK
        // Invariant: Never log plaintext bearer token in application logs, audit logs, or analytics
        log.info("Share link revealed for permission ID: {} by user: {}", permission.getId(), currentUser.getId());

        String targetType = folder != null ? "FOLDER" : "DOCUMENT";
        Long targetId = folder != null ? folder.getId() : (doc != null ? doc.getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("REVEALED_SHARE_LINK")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(doc != null ? doc.getFileHash() : null)
                .folderId(folder != null ? folder.getId() : (doc != null && doc.getFolder() != null ? doc.getFolder().getId() : null))
                .workspaceId(permission.getWorkspaceId())
                .groupId(permission.getTargetGroup() != null ? permission.getTargetGroup().getId() : null)
                .collaborationId(permission.getCollaboration() != null ? permission.getCollaboration().getId() : null)
                .spaceType(folder != null ? (folder.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (doc != null ? (doc.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(ownerOrgCode != null ? ownerOrgCode : permission.getTargetOrgCode())
                .actorUserId(currentUser.getId())
                .grantedBy(permission.getGrantedBy())
                .grantedToEmail(permission.getTargetUserEmail())
                .permissionLevel(permission.getPermissionLevel())
                .recordHash(permission.getRecordHash())
                .blockchainStatus(permission.getBlockchainStatus())
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) {
            auditLogService.logPermissionChange(auditCmd);
        }

        String shareUrl = "/share/" + permission.getShareLinkToken();

        return ShareLinkRevealResponseDto.builder()
                .permissionId(permission.getId())
                .shareLinkToken(permission.getShareLinkToken())
                .shareUrl(shareUrl)
                .expiresAt(permission.getExpiresAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShareLinkResponseDto getShareLinkInfo(String shareLinkToken) {
        PermissionEntity permission = permissionRepository.findByShareLinkTokenAndRevokedFalse(shareLinkToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or has been revoked."));

        validateNotExpired(permission);

        return mapToShareLinkResponse(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public Object accessSharedResource(String shareLinkToken, String password, Long requestedFolderId, UserEntity currentUser) {
        PermissionEntity permission = permissionRepository.findByShareLinkTokenAndRevokedFalse(shareLinkToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or has been revoked."));

        validateNotExpired(permission);

        if (permission.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, permission.getPasswordHash())) {
                throw new AccessDeniedException("Invalid or missing password for share link.");
            }
        }

        if (permission.getDocument() != null) {
            return DocumentMapper.toDocumentResponse(permission.getDocument());
        }

        if (permission.getFolder() != null) {
            FolderEntity sharedRootFolder = permission.getFolder();
            FolderEntity targetFolder = sharedRootFolder;
            if (requestedFolderId != null) {
                targetFolder = folderRepository.findById(requestedFolderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + requestedFolderId));

                if (!isFolderInHierarchy(targetFolder, sharedRootFolder)) {
                    throw new AccessDeniedException("Requested folder is not within the shared folder hierarchy.");
                }
            }

            List<PermissionEntity> exclusions = permissionRepository.findByShareLinkTokenAndPermissionTypeAndRevokedFalse(shareLinkToken, PermissionType.EXCLUDE);
            Set<Long> excludedDocIds = exclusions.stream()
                    .filter(p -> p.getDocument() != null)
                    .map(p -> p.getDocument().getId())
                    .collect(Collectors.toSet());

            Set<Long> excludedFolderIds = exclusions.stream()
                    .filter(p -> p.getFolder() != null)
                    .map(p -> p.getFolder().getId())
                    .collect(Collectors.toSet());

            if (isFolderOrAncestorExcluded(targetFolder, sharedRootFolder, excludedFolderIds)) {
                throw new AccessDeniedException("Access to this folder is excluded under the current share link.");
            }

            List<FolderEntity> subFolders = folderRepository.findByParentFolder_Id(targetFolder.getId());

            List<DocumentResponse> documents = documentRepository.findByFolder_Id(targetFolder.getId()).stream()
                    .filter(doc -> doc.getStatus() == DocumentStatus.ACTIVE && !excludedDocIds.contains(doc.getId()))
                    .map(DocumentMapper::toDocumentResponse)
                    .collect(Collectors.toList());

            return FolderContentResponseDto.builder()
                    .currentFolder(mapToFolderResponse(targetFolder))
                    .subFolders(subFolders.stream()
                            .filter(f -> !excludedFolderIds.contains(f.getId()))
                            .map(this::mapToFolderResponse)
                            .collect(Collectors.toList()))
                    .documents(documents)
                    .build();
        }

        throw new IllegalStateException("Share link is not bound to a document or folder.");
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadSharedDocument(String shareLinkToken, String password, Long documentId, UserEntity currentUser) {
        PermissionEntity permission = permissionRepository.findByShareLinkTokenAndRevokedFalse(shareLinkToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or has been revoked."));

        validateNotExpired(permission);

        if (permission.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, permission.getPasswordHash())) {
                throw new AccessDeniedException("Invalid or missing password for share link.");
            }
        }

        DocumentEntity document;
        if (permission.getDocument() != null) {
            document = permission.getDocument();
            if (documentId != null && !document.getId().equals(documentId)) {
                throw new IllegalArgumentException("Requested document ID does not match the document bound to this share link.");
            }
        } else if (permission.getFolder() != null) {
            if (documentId == null) {
                throw new IllegalArgumentException("Document ID must be provided when downloading from a folder share link.");
            }
            document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

            FolderEntity sharedRootFolder = permission.getFolder();
            if (document.getFolder() == null || !isFolderInHierarchy(document.getFolder(), sharedRootFolder)) {
                throw new AccessDeniedException("Requested document does not belong to the shared folder.");
            }

            List<PermissionEntity> exclusions = permissionRepository.findByShareLinkTokenAndPermissionTypeAndRevokedFalse(shareLinkToken, PermissionType.EXCLUDE);
            boolean isExcluded = exclusions.stream()
                    .anyMatch(p -> p.getDocument() != null && p.getDocument().getId().equals(documentId));
            if (isExcluded) {
                throw new AccessDeniedException("Access to this document is excluded under the current share link.");
            }

            Set<Long> excludedFolderIds = exclusions.stream()
                    .filter(p -> p.getFolder() != null)
                    .map(p -> p.getFolder().getId())
                    .collect(Collectors.toSet());
            if (isFolderOrAncestorExcluded(document.getFolder(), sharedRootFolder, excludedFolderIds)) {
                throw new AccessDeniedException("Access to this document is excluded because its containing folder is excluded under the current share link.");
            }
        } else {
            throw new IllegalStateException("Share link is not bound to a document or folder.");
        }

        if (document.getStatus() != DocumentStatus.ACTIVE) {
            throw new InvalidDocumentStateException("Document is not ready for download. Current status: " + document.getStatus());
        }

        // Before generating/returning a presigned S3 download URL:
        // verify permission.getPermissionLevel(), allow only DOWNLOAD or EDIT, reject VIEW
        if (permission.getPermissionLevel() == PermissionLevel.VIEW
                || (permission.getPermissionLevel() != null && permission.getPermissionLevel() != PermissionLevel.DOWNLOAD && permission.getPermissionLevel() != PermissionLevel.EDIT)) {
            throw new AccessDeniedException("Share link permission level " + permission.getPermissionLevel() 
                    + " does not permit downloading files.");
        }

        String downloadUrl = storageService.generatePresignedDownloadUrl(document.getObjectKey(), document.getFileName(), PRESIGNED_URL_EXPIRY_MINUTES);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES));

        log.info("Generated presigned download URL for document ID {}", document.getId());

        return DownloadUrlResponse.builder()
                .documentId(document.getId())
                .downloadUrl(downloadUrl)
                .expiresAt(expiresAt)
                .fileName(document.getFileName())
                .build();
    }

    private boolean isFolderInHierarchy(FolderEntity childFolder, FolderEntity targetFolder) {
        FolderEntity current = childFolder;
        while (current != null) {
            if (current.getId().equals(targetFolder.getId())) {
                return true;
            }
            current = current.getParentFolder();
        }
        return false;
    }

    private boolean isFolderOrAncestorExcluded(FolderEntity folder, FolderEntity rootFolder, Set<Long> excludedFolderIds) {
        if (excludedFolderIds.isEmpty() || folder == null) {
            return false;
        }
        FolderEntity current = folder;
        while (current != null) {
            if (excludedFolderIds.contains(current.getId())) {
                return true;
            }
            if (rootFolder != null && current.getId().equals(rootFolder.getId())) {
                break;
            }
            current = current.getParentFolder();
        }
        return false;
    }

    @Override
    @Transactional
    public void revokeShareLink(String shareLinkToken, UserEntity currentUser) {
        PermissionEntity permission = permissionRepository.findByShareLinkTokenAndRevokedFalse(shareLinkToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or already revoked."));

        if (permission.getFolder() != null) {
            if (!evaluatorService.canAdministerFolderPermissions(currentUser, permission.getFolder())) {
                throw new AccessDeniedException("User does not have authority to administer folder permissions");
            }
        }
        DocumentEntity doc = permission.getDocument();
        if (doc == null && permission.getDocumentFileHash() != null) {
            List<DocumentEntity> docs = documentRepository.findByFileHash(permission.getDocumentFileHash());
            if (docs.size() == 1) {
                doc = docs.get(0);
            } else if (docs.size() > 1) {
                UserEntity grantor = permission.getGrantedBy();
                if (grantor != null) {
                    List<DocumentEntity> ownerDocs = docs.stream()
                            .filter(d -> d.getUploadedBy() != null && d.getUploadedBy().getId().equals(grantor.getId()))
                            .toList();
                    if (ownerDocs.size() == 1) {
                        doc = ownerDocs.get(0);
                    }
                }
                if (doc == null) {
                    throw new IllegalArgumentException("Ambiguous document selection: multiple documents match file hash.");
                }
            }
        }
        if (doc != null) {
            if (!evaluatorService.canAdministerDocumentPermissions(currentUser, doc)) {
                throw new AccessDeniedException("User does not have authority to administer document permissions");
            }
        }

        // Provenance Override Enforcement (Phase 3B.3 & 3B.4)
        if (!evaluatorService.canActorOverrideGrant(currentUser, permission, permission.getFolder(), doc)) {
            throw new AccessDeniedException("User does not have authority to revoke share link created with tier " 
                    + permission.getGrantorAuthorityTier());
        }

        permission.setRevoked(true);
        permission.setRevokedAt(Instant.now());
        permission.setRevokedBy(currentUser);
        permission.setRecordHash(PermissionHashUtils.calculateRecordHash(permission));

        PermissionEntity savedPermission = permissionRepository.save(permission);
        log.info("Revoked share link (permission ID: {}) by user {}", savedPermission.getId(), currentUser.getId());

        // Audit Logging
        String targetType = permission.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = permission.getFolder() != null ? permission.getFolder().getId() : (doc != null ? doc.getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("REVOKED_SHARE_LINK")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(doc != null ? doc.getFileHash() : null)
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : (doc != null && doc.getFolder() != null ? doc.getFolder().getId() : null))
                .workspaceId(savedPermission.getWorkspaceId())
                .groupId(permission.getTargetGroup() != null ? permission.getTargetGroup().getId() : null)
                .collaborationId(permission.getCollaboration() != null ? permission.getCollaboration().getId() : null)
                .spaceType(permission.getFolder() != null ? (permission.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (doc != null ? (doc.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(permission.getFolder() != null && permission.getFolder().getOwnerOrg() != null ? permission.getFolder().getOwnerOrg().getOrgCode() : (doc != null && doc.getOwnerOrg() != null ? doc.getOwnerOrg().getOrgCode() : permission.getTargetOrgCode()))
                .actorUserId(currentUser != null ? currentUser.getId() : null)
                .grantedBy(currentUser)
                .grantedToEmail(permission.getTargetUserEmail())
                .permissionLevel(permission.getPermissionLevel())
                .recordHash(savedPermission.getRecordHash())
                .blockchainStatus(savedPermission.getBlockchainStatus())
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event for Web3 Blockchain Anchoring
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("REVOKE")
                .recordHash(savedPermission.getRecordHash())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Redis Cache Invalidation
        if (doc != null) {
            cacheService.evictDocumentPermissions(doc.getId());
        }
        if (permission.getTargetUserEmail() != null) {
            cacheService.evictUserPermissions(permission.getTargetUserEmail());
        }
    }

    @Override
    @Transactional
    public ShareLinkResponseDto excludeResourceFromShare(String shareLinkToken, Long documentId, Long folderId, UserEntity currentUser) {
        if (documentId == null && folderId == null) {
            throw new IllegalArgumentException("Either documentId or folderId must be provided for exclusion.");
        }
        if (documentId != null && folderId != null) {
            throw new IllegalArgumentException("Cannot exclude both document and folder in a single request.");
        }

        PermissionEntity parentShare = permissionRepository.findByShareLinkTokenAndRevokedFalse(shareLinkToken)
                .orElseThrow(() -> new ResourceNotFoundException("Share link not found or revoked."));

        PermissionEntity.PermissionEntityBuilder denyBuilder = PermissionEntity.builder()
                .permissionType(PermissionType.EXCLUDE)
                .permissionLevel(parentShare.getPermissionLevel())
                .recipientType(parentShare.getRecipientType())
                .targetUserEmail(parentShare.getTargetUserEmail())
                .targetOrgCode(parentShare.getTargetOrgCode())
                .targetGroup(parentShare.getTargetGroup())
                .grantedBy(currentUser)
                .shareLinkToken(shareLinkToken)
                .validFrom(Instant.now())
                .expiresAt(parentShare.getExpiresAt())
                .blockchainStatus(BlockchainStatus.PENDING)
                .revoked(false);

        String targetType;
        Long targetId;
        String targetFileHash = null;

        if (documentId != null) {
            DocumentEntity document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

            if (!evaluatorService.canAdministerDocumentPermissions(currentUser, document)) {
                throw new AccessDeniedException("User does not have authority to administer document permissions");
            }
            if (parentShare.getFolder() != null) {
                if (!evaluatorService.canAdministerFolderPermissions(currentUser, parentShare.getFolder())) {
                    throw new AccessDeniedException("User does not have authority to administer folder permissions");
                }
            }

            denyBuilder.document(document).documentFileHash(document.getFileHash());
            targetType = "DOCUMENT";
            targetId = document.getId();
            targetFileHash = document.getFileHash();
        } else {
            FolderEntity folder = folderRepository.findById(folderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

            if (!evaluatorService.canAdministerFolderPermissions(currentUser, folder)) {
                throw new AccessDeniedException("User does not have authority to administer folder permissions");
            }
            if (parentShare.getFolder() != null) {
                if (!isFolderInHierarchy(folder, parentShare.getFolder())) {
                    throw new AccessDeniedException("Folder to exclude is not within the shared folder hierarchy.");
                }
            }

            denyBuilder.folder(folder);
            targetType = "FOLDER";
            targetId = folder.getId();
        }

        PermissionEntity denyPermission = denyBuilder.build();
        denyPermission.setRecordHash(PermissionHashUtils.calculateRecordHash(denyPermission));
        PermissionEntity saved = permissionRepository.save(denyPermission);
        log.info("Created DENY exclusion rule for {} ID {}", targetType, targetId);

        // Audit Logging
        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("EXCLUDE_RESOURCE_FROM_SHARE")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(targetFileHash)
                .folderId("FOLDER".equals(targetType) ? targetId : (parentShare.getFolder() != null ? parentShare.getFolder().getId() : null))
                .workspaceId(parentShare.getWorkspaceId())
                .groupId(parentShare.getTargetGroup() != null ? parentShare.getTargetGroup().getId() : null)
                .collaborationId(parentShare.getCollaboration() != null ? parentShare.getCollaboration().getId() : null)
                .spaceType(parentShare.getFolder() != null ? (parentShare.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (parentShare.getDocument() != null ? (parentShare.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(parentShare.getFolder() != null && parentShare.getFolder().getOwnerOrg() != null ? parentShare.getFolder().getOwnerOrg().getOrgCode() : (parentShare.getDocument() != null && parentShare.getDocument().getOwnerOrg() != null ? parentShare.getDocument().getOwnerOrg().getOrgCode() : parentShare.getTargetOrgCode()))
                .actorUserId(currentUser != null ? currentUser.getId() : null)
                .grantedBy(currentUser)
                .permissionLevel(parentShare.getPermissionLevel())
                .validFrom(saved.getValidFrom())
                .expiresAt(saved.getExpiresAt())
                .recordHash(saved.getRecordHash())
                .blockchainStatus(saved.getBlockchainStatus())
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event for Web3 Blockchain Anchoring
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(saved.getId())
                .eventType("GRANT")
                .recordHash(saved.getRecordHash())
                .timestamp(Instant.now())
                .build();
        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Redis Cache Invalidation
        if ("DOCUMENT".equals(targetType)) {
            cacheService.evictDocumentPermissions(targetId);
        }

        return mapToShareLinkResponse(saved);
    }

    @Override
    @Transactional
    public ShareLinkResponseDto excludeFileFromShare(String shareLinkToken, Long documentId, UserEntity currentUser) {
        return excludeResourceFromShare(shareLinkToken, documentId, null, currentUser);
    }

    private void validateNotExpired(PermissionEntity permission) {
        if (permission.getExpiresAt() != null && Instant.now().isAfter(permission.getExpiresAt())) {
            throw new IllegalArgumentException("Share link has expired.");
        }
    }

    private ShareLinkResponseDto mapToShareLinkResponse(PermissionEntity permission) {
        boolean active = !permission.isRevoked()
                && (permission.getExpiresAt() == null || permission.getExpiresAt().isAfter(Instant.now()));

        return ShareLinkResponseDto.builder()
                .permissionId(permission.getId())
                .shareLinkToken(permission.getShareLinkToken())
                .documentId(permission.getDocument() != null ? permission.getDocument().getId() : null)
                .documentName(permission.getDocument() != null ? permission.getDocument().getFileName() : null)
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : null)
                .folderName(permission.getFolder() != null ? permission.getFolder().getName() : null)
                .permissionType(permission.getPermissionType())
                .permissionLevel(permission.getPermissionLevel())
                .requiresPassword(permission.getPasswordHash() != null)
                .expiresAt(permission.getExpiresAt())
                .targetUserEmail(permission.getTargetUserEmail())
                .createdAt(permission.getCreatedAt())
                .note(permission.getNote())
                .active(active)
                .build();
    }

    private FolderResponseDto mapToFolderResponse(FolderEntity folder) {
        return FolderResponseDto.builder()
                .id(folder.getId())
                .name(folder.getName())
                .parentFolderId(folder.getParentFolder() != null ? folder.getParentFolder().getId() : null)
                .ownerOrgCode(folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : null)
                .createdByUserId(folder.getCreatedBy() != null ? folder.getCreatedBy().getId() : null)
                .createdAt(folder.getCreatedAt())
                .build();
    }
}

