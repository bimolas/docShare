package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.dto.ConfirmUploadRequest;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.InitiateUploadRequest;
import com.subject77.docshare.modules.document.dto.InitiateUploadResponse;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.DocumentVersionEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.FileRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.FileRequestEntity;
import com.subject77.docshare.modules.permission.entities.FileRequestRecipientType;
import com.subject77.docshare.modules.permission.entities.FileRequestStatus;
import com.subject77.docshare.modules.permission.mapper.FileRequestMapper;
import com.subject77.docshare.modules.permission.repository.FileRequestRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileRequestServiceImpl implements FileRequestService {

    private static final long PRESIGNED_URL_EXPIRY_MINUTES = 15;

    private final FileRequestRepository fileRequestRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final StorageService storageService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final FileRequestMapper fileRequestMapper;

    // ── Create ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FileRequestResponseDto createFileRequest(FileRequestCreateDto dto, UserEntity currentUser) {
        if (dto.getDestinationFolderId() == null) {
            throw new IllegalArgumentException("Destination folder ID is required");
        }

        FolderEntity destinationFolder = folderRepository.findById(dto.getDestinationFolderId())
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found: " + dto.getDestinationFolderId()));

        boolean canManage = permissionEvaluatorService.canEditFolder(currentUser, destinationFolder)
                || permissionEvaluatorService.canAdministerFolderPermissions(currentUser, destinationFolder);
        if (!canManage) {
            throw new AccessDeniedException("User does not have permission to request files into this destination folder");
        }

        FileRequestRecipientType recipientType = dto.getRecipientType() != null
                ? dto.getRecipientType() : FileRequestRecipientType.USER;

        validateRecipientFieldCombination(dto, recipientType);

        FileRequestEntity fileRequest;
        if (recipientType == FileRequestRecipientType.TEAM) {
            fileRequest = createTeamFileRequest(dto, currentUser, destinationFolder);
        } else {
            fileRequest = createUserFileRequest(dto, currentUser, destinationFolder);
        }

        FileRequestEntity saved = fileRequestRepository.save(fileRequest);

        String recipientContext = buildRecipientAuditContext(saved);
        auditLogService.logDocumentActivity(
                Action.FILE_REQUEST_CREATED, null, null,
                destinationFolder.getId(), currentUser.getEmail(),
                destinationFolder.getOwnerOrg() != null ? destinationFolder.getOwnerOrg().getOrgCode() : null,
                "0.0.0.0", "API",
                "Created file request: " + dto.getTitle() + " for " + recipientContext, null);

        sendCreationNotifications(saved, currentUser);

        return fileRequestMapper.toResponseDto(saved);
    }

    private void validateRecipientFieldCombination(FileRequestCreateDto dto, FileRequestRecipientType type) {
        if (type == FileRequestRecipientType.TEAM) {
            if (dto.getRecipientGroupId() == null) {
                throw new IllegalArgumentException("recipientGroupId is required for TEAM requests");
            }
            if (dto.getRecipientUserId() != null || dto.getRecipientEmail() != null) {
                throw new IllegalArgumentException("recipientUserId and recipientEmail must not be set for TEAM requests");
            }
        } else {
            if (dto.getRecipientGroupId() != null) {
                throw new IllegalArgumentException("recipientGroupId must not be set for USER requests");
            }
            if (dto.getRecipientUserId() == null && (dto.getRecipientEmail() == null || dto.getRecipientEmail().isBlank())) {
                throw new IllegalArgumentException("Either recipientUserId or recipientEmail must be provided for USER requests");
            }
        }
    }

    private FileRequestEntity createUserFileRequest(FileRequestCreateDto dto, UserEntity currentUser, FolderEntity folder) {
        UserEntity recipient;
        if (dto.getRecipientUserId() != null) {
            recipient = userRepository.findById(dto.getRecipientUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found: " + dto.getRecipientUserId()));
        } else {
            recipient = userRepository.findByEmail(dto.getRecipientEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipient user not found with email: " + dto.getRecipientEmail()));
        }
        return FileRequestEntity.builder()
                .requester(currentUser)
                .recipientType(FileRequestRecipientType.USER)
                .recipientUser(recipient)
                .destinationFolder(folder)
                .title(dto.getTitle())
                .note(dto.getNote())
                .status(FileRequestStatus.PENDING)
                .expiresAt(dto.getExpiresAt())
                .build();
    }

    private FileRequestEntity createTeamFileRequest(FileRequestCreateDto dto, UserEntity currentUser, FolderEntity folder) {
        GroupEntity group = groupRepository.findById(dto.getRecipientGroupId())
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + dto.getRecipientGroupId()));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot create a file request for an inactive/archived group");
        }

        // Group must belong to the same organisation as the destination folder
        OrganizationEntity folderOrg = folder.getOwnerOrg();
        if (folderOrg == null || !folderOrg.getOrgCode().equals(group.getOrganization().getOrgCode())) {
            throw new AccessDeniedException("Group does not belong to the organisation that owns the destination folder");
        }

        return FileRequestEntity.builder()
                .requester(currentUser)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroup(group)
                .destinationFolder(folder)
                .title(dto.getTitle())
                .note(dto.getNote())
                .status(FileRequestStatus.PENDING)
                .expiresAt(dto.getExpiresAt())
                .build();
    }

    private void sendCreationNotifications(FileRequestEntity saved, UserEntity actor) {
        if (saved.getRecipientType() == FileRequestRecipientType.TEAM) {
            // Fan-out: notify all current active org members of the group
            List<UserGroupEntity> eligibleMembers = userGroupRepository
                    .findActiveOrgMembersByGroupId(saved.getRecipientGroup().getId(), MembershipStatus.ACTIVE);
            for (UserGroupEntity membership : eligibleMembers) {
                String frCreatedKey = "FILE_REQUEST_CREATED:" + saved.getId() + ":" + membership.getUser().getId();
                notificationService.createNotification(
                        membership.getUser(), actor,
                        NotificationType.FILE_REQUEST_CREATED,
                        "New File Request",
                        actor.getFirstName() + " requested files from your team (" +
                                saved.getRecipientGroup().getName() + "): " + saved.getTitle(),
                        NotificationSourceType.FILE_REQUEST,
                        saved.getId().toString(),
                        NotificationCategory.FILE_REQUEST,
                        NotificationPriority.MEDIUM,
                        frCreatedKey);
            }
        } else {
            UserEntity recipient = saved.getRecipientUser();
            if (recipient != null) {
                String frCreatedKey = "FILE_REQUEST_CREATED:" + saved.getId() + ":" + recipient.getId();
                notificationService.createNotification(
                        recipient, actor,
                        NotificationType.FILE_REQUEST_CREATED,
                        "New File Request",
                        actor.getFirstName() + " requested you to upload files: " + saved.getTitle(),
                        NotificationSourceType.FILE_REQUEST,
                        saved.getId().toString(),
                        NotificationCategory.FILE_REQUEST,
                        NotificationPriority.MEDIUM,
                        frCreatedKey);
            }
        }
    }

    private String buildRecipientAuditContext(FileRequestEntity entity) {
        if (entity.getRecipientType() == FileRequestRecipientType.TEAM) {
            return "team: " + (entity.getRecipientGroup() != null ? entity.getRecipientGroup().getName() : "unknown");
        }
        return "user: " + (entity.getRecipientUser() != null ? entity.getRecipientUser().getEmail() : "unknown");
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<FileRequestResponseDto> getRequestsMine(UserEntity currentUser) {
        List<FileRequestEntity> requests = fileRequestRepository.findByRequesterIdOrderByCreatedAtDesc(currentUser.getId());
        for (FileRequestEntity fr : requests) {
            checkAndApplyLazyExpiration(fr);
        }
        return requests.stream().map(fileRequestMapper::toResponseDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<FileRequestResponseDto> getRequestsReceived(UserEntity currentUser) {
        // USER-type requests where this user is the direct recipient
        List<FileRequestEntity> userRequests =
                fileRequestRepository.findByRecipientUserIdOrderByCreatedAtDesc(currentUser.getId());
        // TEAM-type requests where this user is a current member of the recipient group
        List<FileRequestEntity> teamRequests =
                fileRequestRepository.findTeamRequestsForUser(currentUser.getId());

        List<FileRequestEntity> all = new ArrayList<>(userRequests);
        all.addAll(teamRequests);
        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        for (FileRequestEntity fr : all) {
            checkAndApplyLazyExpiration(fr);
        }

        return all.stream().map(fileRequestMapper::toResponseDto).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public FileRequestResponseDto getFileRequestById(Long id, UserEntity currentUser) {
        FileRequestEntity fr = fileRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("File request not found: " + id));

        if (!isAuthorizedToView(fr, currentUser)) {
            throw new AccessDeniedException("User is not authorized to view this file request");
        }
        checkAndApplyLazyExpiration(fr);
        return fileRequestMapper.toResponseDto(fr);
    }

    private void checkAndApplyLazyExpiration(FileRequestEntity fr) {
        if (fr.getStatus() == FileRequestStatus.PENDING && fr.isExpired()) {
            fr.setStatus(FileRequestStatus.EXPIRED);
            fileRequestRepository.save(fr);
        }
    }

    private boolean isAuthorizedToView(FileRequestEntity fr, UserEntity user) {
        if (fr.getRequester().getId().equals(user.getId())) return true;
        if (fr.getRecipientType() == FileRequestRecipientType.TEAM) {
            return fr.getRecipientGroup() != null &&
                    userGroupRepository.existsByUser_IdAndGroup_Id(user.getId(), fr.getRecipientGroup().getId());
        }
        return fr.getRecipientUser() != null && fr.getRecipientUser().getId().equals(user.getId());
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public InitiateUploadResponse initiateUpload(Long requestId, InitiateUploadRequest request, UserEntity currentUser) {
        FileRequestEntity fr = fileRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("File request not found: " + requestId));

        assertEligibleUploader(fr, currentUser);
        assertPendingAndNotExpired(fr);

        FolderEntity folder = fr.getDestinationFolder();
        OrganizationEntity ownerOrg = folder != null ? folder.getOwnerOrg() : null;

        Optional<DocumentEntity> existingPending = documentRepository
                .findByFileHashAndUploadedBy_IdAndStatusAndFolder_Id(
                        request.getFileHash(), currentUser.getId(),
                        DocumentStatus.PENDING_UPLOAD, folder != null ? folder.getId() : null);

        if (existingPending.isPresent()) {
            DocumentEntity pending = existingPending.get();
            String freshUrl = storageService.generatePresignedUploadUrl(
                    pending.getObjectKey(), request.getContentType(), PRESIGNED_URL_EXPIRY_MINUTES);
            return InitiateUploadResponse.builder()
                    .documentId(pending.getId()).objectKey(pending.getObjectKey())
                    .s3Bucket(pending.getS3Bucket()).presignedUploadUrl(freshUrl)
                    .expiresAt(Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES)))
                    .status(pending.getStatus()).build();
        }

        String prefix = ownerOrg != null ? "orgs/" + ownerOrg.getOrgCode() : "users/" + currentUser.getId();
        String sanitized = request.getFileName().replaceAll("[^a-zA-Z0-9.\\-_]", "_");
        String objectKey = prefix + "/" + UUID.randomUUID() + "-v1-" + sanitized;
        String presignedUrl = storageService.generatePresignedUploadUrl(objectKey, request.getContentType(), PRESIGNED_URL_EXPIRY_MINUTES);

        DocumentEntity doc = DocumentEntity.builder()
                .fileName(request.getFileName()).contentType(request.getContentType())
                .fileSize(request.getFileSize()).fileHash(request.getFileHash())
                .s3Bucket(storageService.getDefaultBucketName()).objectKey(objectKey)
                .status(DocumentStatus.PENDING_UPLOAD).ownerOrg(ownerOrg)
                .uploadedBy(currentUser).folder(folder).fileRequest(fr)
                .uploadedAt(Instant.now()).version(1).build();

        DocumentEntity saved = documentRepository.save(doc);

        auditLogService.logDocumentActivity(Action.INITIATE_UPLOAD, saved.getId(), saved.getFileHash(),
                folder != null ? folder.getId() : null, currentUser.getEmail(),
                ownerOrg != null ? ownerOrg.getOrgCode() : null, "0.0.0.0", "API",
                "Initiated upload for requested file: " + request.getFileName() +
                        " (FileRequest #" + fr.getId() + ", recipientType=" + fr.getRecipientType() + ")", null);

        return InitiateUploadResponse.builder()
                .documentId(saved.getId()).objectKey(objectKey)
                .s3Bucket(storageService.getDefaultBucketName()).presignedUploadUrl(presignedUrl)
                .expiresAt(Instant.now().plus(Duration.ofMinutes(PRESIGNED_URL_EXPIRY_MINUTES)))
                .status(saved.getStatus()).build();
    }

    @Override
    @Transactional
    public DocumentResponse confirmUpload(Long requestId, ConfirmUploadRequest request, UserEntity currentUser) {
        FileRequestEntity fr = fileRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("File request not found: " + requestId));

        assertEligibleUploader(fr, currentUser);
        assertPendingAndNotExpired(fr);

        DocumentEntity document = documentRepository.findById(request.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + request.getDocumentId()));

        if (document.getFileRequest() == null || !document.getFileRequest().getId().equals(requestId)) {
            throw new IllegalArgumentException("Document does not belong to the specified file request");
        }

        if (!storageService.doesObjectExist(document.getObjectKey())) {
            document.setStatus(DocumentStatus.FAILED);
            documentRepository.save(document);
            throw new StorageFileNotFoundException("File not found in storage. Upload confirmation failed.");
        }

        document.setStatus(DocumentStatus.ACTIVE);
        if (request.getClientHash() != null && !request.getClientHash().isBlank()) {
            document.setFileHash(request.getClientHash());
        }

        if (document.getVersions() == null || document.getVersions().isEmpty()) {
            DocumentVersionEntity v1 = DocumentVersionEntity.builder()
                    .document(document).versionNumber(1)
                    .s3Bucket(document.getS3Bucket()).objectKey(document.getObjectKey())
                    .fileName(document.getFileName()).contentType(document.getContentType())
                    .fileSize(document.getFileSize())
                    .checksumSha256(document.getFileHash() != null ? document.getFileHash() : "N/A")
                    .uploadedBy(document.getUploadedBy())
                    .uploadedAt(document.getUploadedAt() != null ? document.getUploadedAt() : Instant.now())
                    .build();
            DocumentVersionEntity savedV1 = documentVersionRepository.save(v1);
            document.setCurrentVersion(savedV1);
            if (document.getVersions() == null) document.setVersions(new ArrayList<>());
            document.getVersions().add(savedV1);
        }

        // No PermissionEntity is created here — upload remains strictly upload-only.
        DocumentEntity savedDoc = documentRepository.save(document);

        auditLogService.logDocumentActivity(Action.FILE_REQUEST_UPLOADED, savedDoc.getId(), savedDoc.getFileHash(),
                fr.getDestinationFolder().getId(), currentUser.getEmail(),
                fr.getDestinationFolder().getOwnerOrg() != null ? fr.getDestinationFolder().getOwnerOrg().getOrgCode() : null,
                "0.0.0.0", "API",
                "Uploaded file: " + savedDoc.getFileName() + " for FileRequest #" + fr.getId() +
                        " (recipientType=" + fr.getRecipientType() + ")", null);

        String frUploadedKey = "FILE_REQUEST_UPLOADED:" + fr.getId() + ":" + savedDoc.getId() + ":" + fr.getRequester().getId();
        notificationService.createNotification(fr.getRequester(), currentUser,
                NotificationType.FILE_REQUEST_UPLOADED, "File Uploaded for Request",
                currentUser.getFirstName() + " uploaded " + savedDoc.getFileName() + " for request: " + fr.getTitle(),
                NotificationSourceType.FILE_REQUEST, fr.getId().toString(),
                NotificationCategory.FILE_REQUEST, NotificationPriority.MEDIUM, frUploadedKey);

        return DocumentResponse.builder()
                .id(savedDoc.getId()).fileName(savedDoc.getFileName())
                .contentType(savedDoc.getContentType()).fileSize(savedDoc.getFileSize())
                .fileHash(savedDoc.getFileHash()).s3Bucket(savedDoc.getS3Bucket())
                .objectKey(savedDoc.getObjectKey()).status(savedDoc.getStatus())
                .uploadedByUserId(savedDoc.getUploadedBy() != null ? savedDoc.getUploadedBy().getId() : null)
                .uploadedByUserEmail(savedDoc.getUploadedBy() != null ? savedDoc.getUploadedBy().getEmail() : null)
                .folderId(fr.getDestinationFolder() != null ? fr.getDestinationFolder().getId() : null)
                .folderName(fr.getDestinationFolder() != null ? fr.getDestinationFolder().getName() : null)
                .uploadedAt(savedDoc.getUploadedAt()).version(savedDoc.getVersion()).build();
    }

    /**
     * Asserts that {@code currentUser} is eligible to upload for {@code fr}.
     * <ul>
     *   <li>USER request: caller must be the designated recipient user.</li>
     *   <li>TEAM request: caller must be a current, active member of the
     *       recipient group AND hold an ACTIVE org membership in the group's
     *       organisation.  Membership is resolved dynamically — not snapshotted.</li>
     * </ul>
     * <p><strong>Security:</strong> all data is read from the persisted
     * {@code FileRequestEntity}; no caller-supplied recipient/group IDs
     * are trusted.</p>
     */
    private void assertEligibleUploader(FileRequestEntity fr, UserEntity currentUser) {
        if (fr.getRecipientType() == FileRequestRecipientType.TEAM) {
            GroupEntity group = fr.getRecipientGroup();
            if (group == null) {
                throw new AccessDeniedException("File request has no associated group");
            }
            boolean eligible = userGroupRepository.isActiveOrgMemberOfGroup(
                    currentUser.getId(), group.getId(), MembershipStatus.ACTIVE);
            if (!eligible) {
                throw new AccessDeniedException(
                        "Only active members of the recipient team can upload files for this file request");
            }
        } else {
            if (fr.getRecipientUser() == null || !fr.getRecipientUser().getId().equals(currentUser.getId())) {
                throw new AccessDeniedException("Only the assigned recipient can upload files for this file request");
            }
            if (isRecipientUserSuspended(currentUser, fr.getDestinationFolder())) {
                throw new AccessDeniedException("Suspended users cannot upload files for this file request");
            }
        }
    }

    private boolean isRecipientUserSuspended(UserEntity user, FolderEntity folder) {
        if (user == null || userOrganizationRepository == null) {
            return false;
        }
        if (folder != null && folder.getOwnerOrg() != null) {
            Optional<UserOrganizationEntity> folderOrgMembership = userOrganizationRepository
                    .findByUser_IdAndOrganization_OrgCode(user.getId(), folder.getOwnerOrg().getOrgCode());
            if (folderOrgMembership.isPresent() && folderOrgMembership.get().getStatus() == MembershipStatus.SUSPENDED) {
                return true;
            }
        }
        if (user.getOrganization() != null) {
            Optional<UserOrganizationEntity> primaryMembership = userOrganizationRepository
                    .findByUser_IdAndOrganization_OrgCode(user.getId(), user.getOrganization().getOrgCode());
            if (primaryMembership.isPresent() && primaryMembership.get().getStatus() == MembershipStatus.SUSPENDED) {
                return true;
            }
        }
        return false;
    }

    private void assertPendingAndNotExpired(FileRequestEntity fr) {
        if (fr.isExpired()) {
            fr.setStatus(FileRequestStatus.EXPIRED);
            fileRequestRepository.save(fr);
            throw new InvalidDocumentStateException("File request has expired");
        }
        if (fr.getStatus() != FileRequestStatus.PENDING) {
            throw new InvalidDocumentStateException("File request is not in PENDING status (status: " + fr.getStatus() + ")");
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FileRequestResponseDto completeFileRequest(Long requestId, UserEntity currentUser) {
        FileRequestEntity fr = fileRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("File request not found: " + requestId));

        boolean isRequester = fr.getRequester().getId().equals(currentUser.getId());
        boolean isEligibleRecipient = isEligibleUploader(fr, currentUser);

        if (!isRequester && !isEligibleRecipient) {
            throw new AccessDeniedException("User is not authorized to complete this file request");
        }

        assertPendingAndNotExpired(fr);

        Instant now = Instant.now();
        int rows = fileRequestRepository.markFulfilledIfPending(requestId, now);
        if (rows == 0) {
            throw new InvalidDocumentStateException("Only PENDING file requests can be completed");
        }

        fr.setStatus(FileRequestStatus.FULFILLED);
        fr.setFulfilledAt(now);
        fr.setUpdatedAt(now);

        auditLogService.logDocumentActivity(Action.FILE_REQUEST_FULFILLED, null, null,
                fr.getDestinationFolder().getId(), currentUser.getEmail(),
                fr.getDestinationFolder().getOwnerOrg() != null ? fr.getDestinationFolder().getOwnerOrg().getOrgCode() : null,
                "0.0.0.0", "API", "Completed file request #" + fr.getId(), null);

        // Notify requester (if a team member completed) or a representative recipient
        UserEntity notifyTarget = isRequester ? getRepresentativeRecipient(fr) : fr.getRequester();
        if (notifyTarget != null) {
            String frFulfilledKey = "FILE_REQUEST_FULFILLED:" + fr.getId() + ":" + notifyTarget.getId();
            notificationService.createNotification(notifyTarget, currentUser,
                    NotificationType.FILE_REQUEST_FULFILLED, "File Request Completed",
                    "File request '" + fr.getTitle() + "' has been completed.",
                    NotificationSourceType.FILE_REQUEST, fr.getId().toString(),
                    NotificationCategory.FILE_REQUEST, NotificationPriority.MEDIUM, frFulfilledKey);
        }

        return fileRequestMapper.toResponseDto(fr);
    }

    @Override
    @Transactional
    public FileRequestResponseDto cancelFileRequest(Long requestId, UserEntity currentUser) {
        FileRequestEntity fr = fileRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("File request not found: " + requestId));

        boolean isRequester = fr.getRequester().getId().equals(currentUser.getId());
        boolean canManage = permissionEvaluatorService.canEditFolder(currentUser, fr.getDestinationFolder())
                || permissionEvaluatorService.canAdministerFolderPermissions(currentUser, fr.getDestinationFolder());

        if (!isRequester && !canManage) {
            throw new AccessDeniedException("Only the requester or folder administrator can cancel this file request");
        }

        assertPendingAndNotExpired(fr);

        Instant now = Instant.now();
        int rows = fileRequestRepository.markCancelledIfPending(requestId, now);
        if (rows == 0) {
            throw new InvalidDocumentStateException("Only PENDING file requests can be cancelled");
        }

        fr.setStatus(FileRequestStatus.CANCELLED);
        fr.setCancelledAt(now);
        fr.setUpdatedAt(now);

        auditLogService.logDocumentActivity(Action.FILE_REQUEST_CANCELLED, null, null,
                fr.getDestinationFolder().getId(), currentUser.getEmail(),
                fr.getDestinationFolder().getOwnerOrg() != null ? fr.getDestinationFolder().getOwnerOrg().getOrgCode() : null,
                "0.0.0.0", "API", "Cancelled file request #" + fr.getId(), null);

        sendCancellationNotifications(fr, currentUser);

        return fileRequestMapper.toResponseDto(fr);
    }

    private void sendCancellationNotifications(FileRequestEntity fr, UserEntity actor) {
        if (fr.getRecipientType() == FileRequestRecipientType.TEAM) {
            List<UserGroupEntity> members = userGroupRepository
                    .findActiveOrgMembersByGroupId(fr.getRecipientGroup().getId(), MembershipStatus.ACTIVE);
            for (UserGroupEntity m : members) {
                String frCancelledKey = "FILE_REQUEST_CANCELLED:" + fr.getId() + ":" + m.getUser().getId();
                notificationService.createNotification(m.getUser(), actor,
                        NotificationType.FILE_REQUEST_CANCELLED, "File Request Cancelled",
                        "File request '" + fr.getTitle() + "' has been cancelled.",
                        NotificationSourceType.FILE_REQUEST, fr.getId().toString(),
                        NotificationCategory.FILE_REQUEST, NotificationPriority.MEDIUM, frCancelledKey);
            }
        } else if (fr.getRecipientUser() != null) {
            String frCancelledKey = "FILE_REQUEST_CANCELLED:" + fr.getId() + ":" + fr.getRecipientUser().getId();
            notificationService.createNotification(fr.getRecipientUser(), actor,
                    NotificationType.FILE_REQUEST_CANCELLED, "File Request Cancelled",
                    "File request '" + fr.getTitle() + "' has been cancelled.",
                    NotificationSourceType.FILE_REQUEST, fr.getId().toString(),
                    NotificationCategory.FILE_REQUEST, NotificationPriority.MEDIUM, frCancelledKey);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Non-throwing membership check used for authorization decisions. */
    private boolean isEligibleUploader(FileRequestEntity fr, UserEntity user) {
        if (fr.getRecipientType() == FileRequestRecipientType.TEAM) {
            return fr.getRecipientGroup() != null &&
                    userGroupRepository.isActiveOrgMemberOfGroup(
                            user.getId(), fr.getRecipientGroup().getId(), MembershipStatus.ACTIVE);
        }
        return fr.getRecipientUser() != null && fr.getRecipientUser().getId().equals(user.getId())
                && !isRecipientUserSuspended(user, fr.getDestinationFolder());
    }

    /**
     * Returns the best single user to notify on behalf of a TEAM recipient
     * (the requester themselves, since TEAM recipients get their own per-member
     * notifications elsewhere).  For USER requests, returns the recipient user.
     */
    private UserEntity getRepresentativeRecipient(FileRequestEntity fr) {
        if (fr.getRecipientType() == FileRequestRecipientType.TEAM) {
            return null; // notifications already sent per-member elsewhere
        }
        return fr.getRecipientUser();
    }
}
