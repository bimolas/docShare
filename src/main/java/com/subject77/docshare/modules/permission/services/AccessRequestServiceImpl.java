package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.permission.dto.AccessRequestActionDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestEligibilityDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestEntity;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.mapper.AccessRequestMapper;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccessRequestServiceImpl implements AccessRequestService {

    private final AccessRequestRepository accessRequestRepository;
    private final PermissionService permissionService;
    private final PermissionRepository permissionRepository;
    private final PermissionEvaluatorService evaluatorService;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final UserOrganizationRepository userOrganizationRepository;
    private final AccessRequestMapper accessRequestMapper;
    private final WorkspacePolicyService workspacePolicyService;
    private final ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    private final CollaborationParticipantRepository collaborationParticipantRepository;
    private final CollaborationRepository collaborationRepository;
    private final UserRepository userRepository;
    private final com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository projectWorkspaceRepository;
    private final com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository workspaceManagerRepository;




    @Override
    @Transactional(readOnly = true)
    public AccessRequestEligibilityDto checkEligibility(Long documentId, String documentFileHash, Long folderId, UserEntity requester) {
        if (requester == null) {
            return AccessRequestEligibilityDto.builder()
                    .eligible(false)
                    .reasonCode("RESOURCE_NOT_FOUND_OR_RESTRICTED")
                    .build();
        }

        DocumentEntity document = null;
        FolderEntity folder = null;

        if (documentId != null || (documentFileHash != null && !documentFileHash.isBlank())) {
            if (documentId != null) {
                document = documentRepository.findById(documentId).orElse(null);
            } else {
                List<DocumentEntity> docs = documentRepository.findByFileHash(documentFileHash);
                if (docs.isEmpty()) {
                    document = null;
                } else if (docs.size() == 1) {
                    document = docs.get(0);
                } else {
                    return AccessRequestEligibilityDto.builder()
                            .eligible(false)
                            .reasonCode("AMBIGUOUS_DOCUMENT_SELECTION")
                            .build();
                }
            }
            if (document == null) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("RESOURCE_NOT_FOUND_OR_RESTRICTED")
                        .build();
            }
        } else if (folderId != null) {
            folder = folderRepository.findById(folderId).orElse(null);
            if (folder == null) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("RESOURCE_NOT_FOUND_OR_RESTRICTED")
                        .build();
            }
        } else {
            return AccessRequestEligibilityDto.builder()
                    .eligible(false)
                    .reasonCode("RESOURCE_NOT_FOUND_OR_RESTRICTED")
                    .build();
        }

        // 1. Check if requester already has access or is owner/admin
        boolean isOwnerOrAdmin = document != null 
                ? evaluatorService.canAdministerDocumentPermissions(requester, document)
                : evaluatorService.canAdministerFolderPermissions(requester, folder);

        if (isOwnerOrAdmin) {
            return AccessRequestEligibilityDto.builder()
                    .eligible(false)
                    .reasonCode("ALREADY_HAS_ACCESS")
                    .currentPermissionLevel(PermissionLevel.EDIT)
                    .build();
        }

        if (document != null) {
            if (evaluatorService.canEditDocument(requester, document)) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("ALREADY_HAS_ACCESS")
                        .currentPermissionLevel(PermissionLevel.EDIT)
                        .build();
            } else if (evaluatorService.canDownloadDocument(requester, document)) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("ALREADY_HAS_ACCESS")
                        .currentPermissionLevel(PermissionLevel.DOWNLOAD)
                        .build();
            } else if (evaluatorService.canViewDocument(requester, document)) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("ALREADY_HAS_ACCESS")
                        .currentPermissionLevel(PermissionLevel.VIEW)
                        .build();
            }
        } else {
            PermissionLevel currentFolderLevel = evaluatorService.resolveMaxPermissionLevel(requester, folder);
            if (currentFolderLevel != null) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("ALREADY_HAS_ACCESS")
                        .currentPermissionLevel(currentFolderLevel)
                        .build();
            }
        }

        // 2. Check for pending request
        if (document != null) {
            Optional<AccessRequestEntity> pendingOpt = accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus(requester.getId(), document.getId(), AccessRequestStatus.PENDING);
            if (pendingOpt.isPresent()) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("PENDING_REQUEST_EXISTS")
                        .existingRequestId(pendingOpt.get().getId())
                        .build();
            }
        } else {
            Optional<AccessRequestEntity> pendingOpt = accessRequestRepository.findFirstByRequester_IdAndFolder_IdAndStatus(requester.getId(), folder.getId(), AccessRequestStatus.PENDING);
            if (pendingOpt.isPresent()) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("PENDING_REQUEST_EXISTS")
                        .existingRequestId(pendingOpt.get().getId())
                        .build();
            }
        }

        // 3. Policy evaluation
        OrganizationEntity sourceOrg = resolveSourceOrg(folder, document);
        UserEntity sourceUser = resolveSourceUser(folder, document);

        WorkspacePolicyEntity effectivePolicy = getEffectiveWorkspacePolicy(sourceOrg, sourceUser);

        boolean requireReason = false;
        if (effectivePolicy != null) {
            if (Boolean.FALSE.equals(effectivePolicy.getAllowAccessRequests())) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("REQUESTS_DISABLED_BY_POLICY")
                        .build();
            }

            boolean isExternal = isExternalRequester(requester, sourceOrg, sourceUser);
            if (isExternal && Boolean.FALSE.equals(effectivePolicy.getAllowExternalAccessRequests())) {
                return AccessRequestEligibilityDto.builder()
                        .eligible(false)
                        .reasonCode("EXTERNAL_REQUESTS_DISABLED")
                        .build();
            }

            if (isExternal && externalSharingPolicyEvaluator != null) {
                try {
                    boolean isMember = isMemberOfSourceOrg(requester, sourceOrg);
                    String targetOrgCode = isMember ? sourceOrg.getOrgCode() : null;
                    RecipientType recipientType = isMember ? RecipientType.USER_ORGANIZATION_MEMBER : RecipientType.USER_PERSONAL;

                    PermissionRequestDto checkDto = PermissionRequestDto.builder()
                            .recipientType(recipientType)
                            .targetUserEmail(requester.getEmail())
                            .targetOrgCode(targetOrgCode)
                            .build();
                    externalSharingPolicyEvaluator.evaluateAndValidateSharingPolicy(folder, document, checkDto, null);
                } catch (Exception e) {
                    return AccessRequestEligibilityDto.builder()
                            .eligible(false)
                            .reasonCode("EXTERNAL_REQUESTS_DISABLED")
                            .build();
                }
            }

            requireReason = Boolean.TRUE.equals(effectivePolicy.getRequireRequestReason());
        }

        return AccessRequestEligibilityDto.builder()
                .eligible(true)
                .reasonCode("ELIGIBLE")
                .requireReason(requireReason)
                .build();
    }

    @Override
    @Transactional
    public AccessRequestResponseDto createAccessRequest(AccessRequestCreateDto dto, UserEntity requester) {
        if (dto == null) {
            throw new IllegalArgumentException("Access request payload must not be null");
        }
        if (requester == null || requester.getId() == null) {
            throw new IllegalArgumentException("Requester must not be null");
        }
        if (dto.getRequestedPermissionLevel() == null) {
            throw new IllegalArgumentException("Requested permission level is required");
        }

        // Objective C: Obtain PESSIMISTIC_WRITE lock on requester UserEntity to serialize creation and prevent duplicate pending requests
        UserEntity lockedRequester = userRepository.findByIdForUpdate(requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found with ID: " + requester.getId()));

        DocumentEntity document = null;
        FolderEntity folder = null;
        String fileHash = null;

        if (dto.getDocumentId() != null || (dto.getDocumentFileHash() != null && !dto.getDocumentFileHash().isBlank())) {
            if (dto.getDocumentId() != null) {
                document = documentRepository.findById(dto.getDocumentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + dto.getDocumentId()));
            } else {
                List<DocumentEntity> docs = documentRepository.findByFileHash(dto.getDocumentFileHash());
                if (docs.isEmpty()) {
                    throw new ResourceNotFoundException("Document not found with hash: " + dto.getDocumentFileHash());
                } else if (docs.size() == 1) {
                    document = docs.get(0);
                } else {
                    throw new IllegalArgumentException("Ambiguous document selection: multiple documents match file hash. Please provide documentId.");
                }
            }
            fileHash = document.getFileHash();

            if (evaluatorService.canAdministerDocumentPermissions(lockedRequester, document)) {
                throw new IllegalArgumentException("Requester is already the owner or administrator of this document");
            }
            if (evaluatorService.canViewDocument(lockedRequester, document)) {
                throw new IllegalArgumentException("Requester already has access to this document");
            }

            if (accessRequestRepository.existsByRequester_IdAndDocument_IdAndStatus(lockedRequester.getId(), document.getId(), AccessRequestStatus.PENDING)) {
                throw new IllegalArgumentException("An access request for this document is already pending");
            }
        } else if (dto.getFolderId() != null) {
            folder = folderRepository.findById(dto.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + dto.getFolderId()));

            if (evaluatorService.canAdministerFolderPermissions(lockedRequester, folder)) {
                throw new IllegalArgumentException("Requester is already the owner or administrator of this folder");
            }
            if (evaluatorService.resolveMaxPermissionLevel(lockedRequester, folder) != null) {
                throw new IllegalArgumentException("Requester already has access to this folder");
            }

            if (accessRequestRepository.existsByRequester_IdAndFolder_IdAndStatus(lockedRequester.getId(), folder.getId(), AccessRequestStatus.PENDING)) {
                throw new IllegalArgumentException("An access request for this folder is already pending");
            }
        } else {
            throw new IllegalArgumentException("Either a document or folder target must be specified");
        }

        // Policy, Suspended Membership & Reason validation
        OrganizationEntity sourceOrg = resolveSourceOrg(folder, document);
        UserEntity sourceUser = resolveSourceUser(folder, document);

        // Objective E: Block suspended organization members from requesting access to organization resources
        if (sourceOrg != null) {
            Optional<UserOrganizationEntity> membershipOpt =
                    userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(lockedRequester.getId(), sourceOrg.getOrgCode());
            if (membershipOpt.isPresent() && membershipOpt.get().getStatus() == MembershipStatus.SUSPENDED) {
                throw new AccessDeniedException("Suspended organization members cannot request access to organization resources");
            }
        }

        WorkspacePolicyEntity effectivePolicy = getEffectiveWorkspacePolicy(sourceOrg, sourceUser);
        if (effectivePolicy != null) {
            if (Boolean.FALSE.equals(effectivePolicy.getAllowAccessRequests())) {
                throw new AccessDeniedException("Access requests are disabled by workspace policy");
            }

            boolean isExternal = isExternalRequester(lockedRequester, sourceOrg, sourceUser);
            if (isExternal && Boolean.FALSE.equals(effectivePolicy.getAllowExternalAccessRequests())) {
                throw new AccessDeniedException("External access requests are disabled by workspace policy");
            }

            if (isExternal && externalSharingPolicyEvaluator != null) {
                boolean isMember = isMemberOfSourceOrg(lockedRequester, sourceOrg);
                String targetOrgCode = isMember ? sourceOrg.getOrgCode() : null;
                RecipientType recipientType = isMember ? RecipientType.USER_ORGANIZATION_MEMBER : RecipientType.USER_PERSONAL;

                PermissionRequestDto checkDto = PermissionRequestDto.builder()
                        .recipientType(recipientType)
                        .targetUserEmail(lockedRequester.getEmail())
                        .targetOrgCode(targetOrgCode)
                        .build();
                externalSharingPolicyEvaluator.evaluateAndValidateSharingPolicy(folder, document, checkDto, null);
            }

            if (Boolean.TRUE.equals(effectivePolicy.getRequireRequestReason()) && (dto.getReason() == null || dto.getReason().isBlank())) {
                throw new IllegalArgumentException("Request reason is required by workspace policy");
            }
        }

        AccessRequestEntity entity = AccessRequestEntity.builder()
                .requester(lockedRequester)
                .document(document)
                .documentFileHash(fileHash)
                .folder(folder)
                .requestedPermissionLevel(dto.getRequestedPermissionLevel())
                .status(AccessRequestStatus.PENDING)
                .reason(dto.getReason())
                .build();

        AccessRequestEntity saved = accessRequestRepository.save(entity);

        // Audit Logging
        String targetType = document != null ? "DOCUMENT" : "FOLDER";
        Long targetId = document != null ? document.getId() : folder.getId();
        Long folderIdValue = folder != null ? folder.getId() : (document.getFolder() != null ? document.getFolder().getId() : null);
        SpaceType spaceType = document != null ? (document.getOwnerOrg() == null ? SpaceType.PERSONAL : SpaceType.ORGANIZATION)
                : (folder.getOwnerOrg() == null ? SpaceType.PERSONAL : SpaceType.ORGANIZATION);
        String orgCode = document != null ? (document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null)
                : (folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("ACCESS_REQUEST_CREATED")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(fileHash)
                .folderId(folderIdValue)
                .spaceType(spaceType)
                .orgCode(orgCode)
                .grantedBy(lockedRequester)
                .actorUserId(lockedRequester.getId())
                .grantedToEmail(lockedRequester.getEmail())
                .permissionLevel(dto.getRequestedPermissionLevel())
                .previousValue(null)
                .newValue(dto.getRequestedPermissionLevel() != null ? dto.getRequestedPermissionLevel().name() : null)
                .reason(dto.getReason())
                .details("Requested " + dto.getRequestedPermissionLevel() + " access for " + targetType + " ID " + targetId)
                .timestamp(Instant.now())
                .build();
        auditLogService.logPermissionChange(auditCmd);

        // Notification to authorized deciders
        notifyAccessRequestSubmitted(saved, lockedRequester, dto);

        return accessRequestMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public AccessRequestResponseDto approveAccessRequest(Long requestId, AccessRequestActionDto actionDto, UserEntity reviewer) {
        AccessRequestEntity request = getPendingRequestForUpdateOrThrow(requestId);

        if (request.getRequester().getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("Requesters cannot approve their own access requests");
        }

        validateReviewerAuthority(reviewer, request);

        // Revalidation Pipeline
        revalidateApprovalRequest(request, reviewer);

        // Delegate to PermissionService to grant actual permission
        OrganizationEntity sourceOrg = resolveSourceOrg(request.getFolder(), request.getDocument());

        // Objective D: RecipientType must be based on actual active membership in sourceOrg
        boolean isMemberOfSourceOrg = isMemberOfSourceOrg(request.getRequester(), sourceOrg);
        RecipientType recipientType = isMemberOfSourceOrg ? RecipientType.USER_ORGANIZATION_MEMBER : RecipientType.USER_PERSONAL;
        String targetOrgCode = isMemberOfSourceOrg ? sourceOrg.getOrgCode() : null;

        // Revoke any active explicit EXCLUDE permissions for the requester on this target resource
        if (request.getDocument() != null || request.getDocumentFileHash() != null) {
            String hash = request.getDocumentFileHash() != null ? request.getDocumentFileHash()
                    : (request.getDocument() != null ? request.getDocument().getFileHash() : null);
            List<PermissionEntity> exclusions = new ArrayList<>();
            if (request.getDocument() != null && request.getDocument().getId() != null) {
                exclusions.addAll(permissionRepository.findByTargetUserEmailIgnoreCaseAndDocument_IdAndRevokedFalse(
                        request.getRequester().getEmail(), request.getDocument().getId()));
            }
            if (hash != null && !hash.isBlank()) {
                exclusions.addAll(permissionRepository.findByTargetUserEmailIgnoreCaseAndDocumentFileHashAndRevokedFalse(
                        request.getRequester().getEmail(), hash));
            }
            for (PermissionEntity ex : exclusions) {
                if (ex.getPermissionType() == PermissionType.EXCLUDE && !ex.isRevoked()) {
                    Instant now = Instant.now();
                    ex.setRevoked(true);
                    ex.setRevokedAt(now);
                    ex.setRevokedBy(reviewer);
                    String exRecordHash = PermissionHashUtils.calculateRecordHash(ex);
                    ex.setRecordHash(exRecordHash);
                    ex.setBlockchainStatus(BlockchainStatus.PENDING);
                    permissionRepository.save(ex);

                    AuditPermissionChangeCommand exAuditCmd = AuditPermissionChangeCommand.builder()
                            .action("PERMISSION_REVOKED")
                            .targetType("DOCUMENT")
                            .targetId(ex.getDocument() != null ? ex.getDocument().getId() : null)
                            .targetFileHash(ex.getDocumentFileHash() != null ? ex.getDocumentFileHash() : (ex.getDocument() != null ? ex.getDocument().getFileHash() : hash))
                            .folderId(ex.getFolder() != null ? ex.getFolder().getId() : (ex.getDocument() != null && ex.getDocument().getFolder() != null ? ex.getDocument().getFolder().getId() : null))
                            .workspaceId(ex.getWorkspaceId())
                            .spaceType(ex.getFolder() != null ? (ex.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                                    : (ex.getDocument() != null ? (ex.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (targetOrgCode != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)))
                            .orgCode(ex.getTargetOrgCode() != null ? ex.getTargetOrgCode()
                                    : (ex.getFolder() != null && ex.getFolder().getOwnerOrg() != null ? ex.getFolder().getOwnerOrg().getOrgCode()
                                    : (ex.getDocument() != null && ex.getDocument().getOwnerOrg() != null ? ex.getDocument().getOwnerOrg().getOrgCode() : targetOrgCode)))
                            .actorUserId(reviewer.getId())
                            .grantedBy(reviewer)
                            .grantedToEmail(request.getRequester().getEmail())
                            .permissionLevel(ex.getPermissionLevel())
                            .previousValue("EXCLUDE")
                            .newValue("REVOKED")
                            .reason("REVOKED_CONFLICTING_EXCLUSION_ON_ACCESS_REQUEST_APPROVAL")
                            .details("Revoked conflicting EXCLUDE rule upon approval of access request ID " + requestId)
                            .recordHash(exRecordHash)
                            .blockchainStatus(BlockchainStatus.PENDING)
                            .timestamp(now)
                            .build();
                    auditLogService.logPermissionChange(exAuditCmd);
                }
            }
        } else if (request.getFolder() != null) {
            List<PermissionEntity> exclusions = permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse(
                    request.getRequester().getEmail(), request.getFolder().getId());
            for (PermissionEntity ex : exclusions) {
                if (ex.getPermissionType() == PermissionType.EXCLUDE && !ex.isRevoked()) {
                    Instant now = Instant.now();
                    ex.setRevoked(true);
                    ex.setRevokedAt(now);
                    ex.setRevokedBy(reviewer);
                    String exRecordHash = PermissionHashUtils.calculateRecordHash(ex);
                    ex.setRecordHash(exRecordHash);
                    ex.setBlockchainStatus(BlockchainStatus.PENDING);
                    permissionRepository.save(ex);

                    AuditPermissionChangeCommand exAuditCmd = AuditPermissionChangeCommand.builder()
                            .action("PERMISSION_REVOKED")
                            .targetType("FOLDER")
                            .targetId(ex.getFolder().getId())
                            .folderId(ex.getFolder().getId())
                            .workspaceId(ex.getWorkspaceId())
                            .spaceType(ex.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                            .orgCode(ex.getTargetOrgCode() != null ? ex.getTargetOrgCode()
                                    : (ex.getFolder().getOwnerOrg() != null ? ex.getFolder().getOwnerOrg().getOrgCode() : targetOrgCode))
                            .actorUserId(reviewer.getId())
                            .grantedBy(reviewer)
                            .grantedToEmail(request.getRequester().getEmail())
                            .permissionLevel(ex.getPermissionLevel())
                            .previousValue("EXCLUDE")
                            .newValue("REVOKED")
                            .reason("REVOKED_CONFLICTING_EXCLUSION_ON_ACCESS_REQUEST_APPROVAL")
                            .details("Revoked conflicting EXCLUDE rule upon approval of access request ID " + requestId)
                            .recordHash(exRecordHash)
                            .blockchainStatus(BlockchainStatus.PENDING)
                            .timestamp(now)
                            .build();
                    auditLogService.logPermissionChange(exAuditCmd);
                }
            }
        }

        PermissionLevel grantedLevel = (actionDto != null && actionDto.getGrantedPermissionLevel() != null)
                ? actionDto.getGrantedPermissionLevel()
                : request.getRequestedPermissionLevel();

        PermissionRequestDto permRequest = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(grantedLevel)
                .recipientType(recipientType)
                .targetOrgCode(targetOrgCode)
                .documentFileHash(request.getDocumentFileHash() != null ? request.getDocumentFileHash() : (request.getDocument() != null ? request.getDocument().getFileHash() : null))
                .folderId(request.getFolder() != null ? request.getFolder().getId() : null)
                .targetUserEmail(request.getRequester().getEmail())
                .note(actionDto != null && actionDto.getReviewerNote() != null && !actionDto.getReviewerNote().isBlank()
                        ? actionDto.getReviewerNote() : "Approved access request")
                .build();

        PermissionResponseDto grantedPermDto = permissionService.grantPermission(permRequest, reviewer.getId());
        PermissionEntity grantedPermission = permissionRepository.findById(grantedPermDto.getId()).orElse(null);

        request.setStatus(AccessRequestStatus.APPROVED);
        request.setProcessedBy(reviewer);
        request.setProcessedAt(Instant.now());
        if (actionDto != null) {
            request.setReviewerNote(actionDto.getReviewerNote());
        }
        request.setGrantedPermission(grantedPermission);

        AccessRequestEntity saved = accessRequestRepository.save(request);

        // Audit Logging
        logAuditForStateChange("ACCESS_REQUEST_APPROVED", saved, reviewer);

        // Notification to Requester
        String approveKey = NotificationType.ACCESS_REQUEST_APPROVED.name() + ":" + saved.getId() + ":" + saved.getRequester().getId();
        notificationService.createNotification(
                saved.getRequester(),
                reviewer,
                NotificationType.ACCESS_REQUEST_APPROVED,
                "Access Request Approved",
                "Your request for access has been approved.",
                NotificationSourceType.ACCESS_REQUEST,
                String.valueOf(saved.getId()),
                NotificationCategory.ACCESS_REQUEST,
                NotificationPriority.MEDIUM,
                approveKey
        );

        return accessRequestMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public AccessRequestResponseDto denyAccessRequest(Long requestId, AccessRequestActionDto actionDto, UserEntity reviewer) {
        AccessRequestEntity request = getPendingRequestForUpdateOrThrow(requestId);

        if (request.getRequester().getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("Requesters cannot deny their own access requests");
        }

        validateReviewerAuthority(reviewer, request);

        request.setStatus(AccessRequestStatus.DENIED);
        request.setProcessedBy(reviewer);
        request.setProcessedAt(Instant.now());
        if (actionDto != null) {
            request.setReviewerNote(actionDto.getReviewerNote());
        }

        AccessRequestEntity saved = accessRequestRepository.save(request);

        // Audit Logging
        logAuditForStateChange("ACCESS_REQUEST_DENIED", saved, reviewer);

        // Notification to Requester
        String denyKey = NotificationType.ACCESS_REQUEST_DENIED.name() + ":" + saved.getId() + ":" + saved.getRequester().getId();
        notificationService.createNotification(
                saved.getRequester(),
                reviewer,
                NotificationType.ACCESS_REQUEST_DENIED,
                "Access Request Denied",
                "Your request for access has been denied.",
                NotificationSourceType.ACCESS_REQUEST,
                String.valueOf(saved.getId()),
                NotificationCategory.ACCESS_REQUEST,
                NotificationPriority.MEDIUM,
                denyKey
        );

        return accessRequestMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public AccessRequestResponseDto cancelAccessRequest(Long requestId, UserEntity requester) {
        AccessRequestEntity request = getPendingRequestForUpdateOrThrow(requestId);

        if (!request.getRequester().getId().equals(requester.getId())) {
            throw new AccessDeniedException("Only the requester can cancel this access request");
        }

        request.setStatus(AccessRequestStatus.CANCELLED);
        request.setProcessedAt(Instant.now());

        AccessRequestEntity saved = accessRequestRepository.save(request);

        // Audit Logging
        logAuditForStateChange("ACCESS_REQUEST_CANCELLED", saved, requester);

        // Objective G: Dispatch ACCESS_REQUEST_CANCELLED notification to deciders
        notifyAccessRequestCancelled(saved, requester);

        return accessRequestMapper.toResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccessRequestResponseDto> getMyAccessRequests(UserEntity currentUser, AccessRequestStatus status, Pageable pageable) {
        return getMyAccessRequests(currentUser, status, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccessRequestResponseDto> getMyAccessRequests(UserEntity currentUser, AccessRequestStatus status, String search, Pageable pageable) {
        Page<AccessRequestEntity> page;
        if (search != null && !search.trim().isEmpty()) {
            page = accessRequestRepository.findMyAccessRequests(currentUser.getId(), status, search.trim(), pageable);
        } else if (status != null) {
            page = accessRequestRepository.findByRequester_IdAndStatus(currentUser.getId(), status, pageable);
        } else {
            page = accessRequestRepository.findByRequester_Id(currentUser.getId(), pageable);
        }
        return page.map(req -> populateDtoDetails(accessRequestMapper.toResponseDto(req), req, currentUser));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccessRequestResponseDto> getPendingReviews(UserEntity currentUser, Pageable pageable) {
        return getReviewerAccessRequests(currentUser, AccessRequestStatus.PENDING, null, null, null, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AccessRequestResponseDto> getReviewerAccessRequests(
            UserEntity currentUser,
            AccessRequestStatus status,
            String orgCode,
            Long workspaceId,
            String search,
            Pageable pageable) {

        if (currentUser == null) {
            return Page.empty(pageable);
        }

        List<String> adminOrgCodes = getUserAdminOrgCodes(currentUser);
        if (orgCode != null && !orgCode.isBlank()) {
            adminOrgCodes = adminOrgCodes.stream().filter(c -> c.equalsIgnoreCase(orgCode)).collect(Collectors.toList());
        }

        List<AccessRequestEntity> allCandidates = new ArrayList<>();

        if (status == AccessRequestStatus.PENDING) {
            if (!adminOrgCodes.isEmpty()) {
                Page<AccessRequestEntity> orgCandidatePage = accessRequestRepository.findPendingReviewsForUser(
                        currentUser.getId(), status, adminOrgCodes, Pageable.unpaged());
                if (orgCandidatePage != null && orgCandidatePage.getContent() != null) {
                    allCandidates.addAll(orgCandidatePage.getContent());
                }
            } else {
                Page<AccessRequestEntity> directCandidatePage = accessRequestRepository.findPendingReviewsForUserDirect(
                        currentUser.getId(), status, Pageable.unpaged());
                if (directCandidatePage != null && directCandidatePage.getContent() != null) {
                    allCandidates.addAll(directCandidatePage.getContent());
                }
            }

            // Workspace manager candidates
            if (workspaceManagerRepository != null) {
                List<com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity> wmAssignments =
                        workspaceManagerRepository.findByUser_Id(currentUser.getId());
                if (wmAssignments != null && !wmAssignments.isEmpty()) {
                    List<Long> wsRootFolderIds = wmAssignments.stream()
                            .filter(wm -> wm.getWorkspace() != null
                                    && wm.getWorkspace().getStatus() == com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus.ACTIVE
                                    && wm.getWorkspace().getRootFolder() != null)
                            .map(wm -> wm.getWorkspace().getRootFolder().getId())
                            .distinct()
                            .collect(Collectors.toList());

                    if (!wsRootFolderIds.isEmpty()) {
                        Page<AccessRequestEntity> wsPage = accessRequestRepository.findPendingReviewsForWorkspaceManager(
                                status, wsRootFolderIds, Pageable.unpaged());
                        if (wsPage != null && wsPage.getContent() != null) {
                            List<Long> existingIds = allCandidates.stream().map(AccessRequestEntity::getId).collect(Collectors.toList());
                            wsPage.getContent().stream()
                                    .filter(ar -> !existingIds.contains(ar.getId()))
                                    .forEach(allCandidates::add);
                        }
                    }
                }
            }

            // Collaboration manager candidates
            List<Long> collabRootFolderIds = collaborationParticipantRepository
                    .findByUser_IdAndCollaborationRole(currentUser.getId(), CollaborationRole.MANAGER)
                    .stream()
                    .filter(cp -> cp.getCollaboration() != null
                            && cp.getCollaboration().getStatus() == CollaborationStatus.ACTIVE
                            && cp.getCollaboration().getRootFolder() != null)
                    .map(cp -> cp.getCollaboration().getRootFolder().getId())
                    .distinct()
                    .collect(Collectors.toList());

            if (!collabRootFolderIds.isEmpty()) {
                Page<AccessRequestEntity> collabPage = accessRequestRepository.findPendingReviewsForCollaborationManager(
                        status, collabRootFolderIds, Pageable.unpaged());
                if (collabPage != null && collabPage.getContent() != null) {
                    List<Long> existingIds = allCandidates.stream().map(AccessRequestEntity::getId).collect(Collectors.toList());
                    collabPage.getContent().stream()
                            .filter(ar -> !existingIds.contains(ar.getId()))
                            .forEach(allCandidates::add);
                }
            }
        } else {
            if (!adminOrgCodes.isEmpty()) {
                Page<AccessRequestEntity> orgCandidatePage = accessRequestRepository.findReviewsForUser(
                        currentUser.getId(), status, adminOrgCodes, Pageable.unpaged());
                if (orgCandidatePage != null && orgCandidatePage.getContent() != null) {
                    allCandidates.addAll(orgCandidatePage.getContent());
                }
            } else {
                Page<AccessRequestEntity> directCandidatePage = accessRequestRepository.findReviewsForUserDirect(
                        currentUser.getId(), status, Pageable.unpaged());
                if (directCandidatePage != null && directCandidatePage.getContent() != null) {
                    allCandidates.addAll(directCandidatePage.getContent());
                }
            }

            // Workspace manager candidates
            if (workspaceManagerRepository != null) {
                List<com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity> wmAssignments =
                        workspaceManagerRepository.findByUser_Id(currentUser.getId());
                if (wmAssignments != null && !wmAssignments.isEmpty()) {
                    List<Long> wsRootFolderIds = wmAssignments.stream()
                            .filter(wm -> wm.getWorkspace() != null
                                    && wm.getWorkspace().getStatus() == com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus.ACTIVE
                                    && wm.getWorkspace().getRootFolder() != null)
                            .map(wm -> wm.getWorkspace().getRootFolder().getId())
                            .distinct()
                            .collect(Collectors.toList());

                    if (!wsRootFolderIds.isEmpty()) {
                        Page<AccessRequestEntity> wsPage = accessRequestRepository.findReviewsForRootFolderIds(
                                status, wsRootFolderIds, Pageable.unpaged());
                        if (wsPage != null && wsPage.getContent() != null) {
                            List<Long> existingIds = allCandidates.stream().map(AccessRequestEntity::getId).collect(Collectors.toList());
                            wsPage.getContent().stream()
                                    .filter(ar -> !existingIds.contains(ar.getId()))
                                    .forEach(allCandidates::add);
                        }
                    }
                }
            }

            // Collaboration manager candidates
            List<Long> collabRootFolderIds = collaborationParticipantRepository
                    .findByUser_IdAndCollaborationRole(currentUser.getId(), CollaborationRole.MANAGER)
                    .stream()
                    .filter(cp -> cp.getCollaboration() != null
                            && cp.getCollaboration().getStatus() == CollaborationStatus.ACTIVE
                            && cp.getCollaboration().getRootFolder() != null)
                    .map(cp -> cp.getCollaboration().getRootFolder().getId())
                    .distinct()
                    .collect(Collectors.toList());

            if (!collabRootFolderIds.isEmpty()) {
                Page<AccessRequestEntity> collabPage = accessRequestRepository.findReviewsForRootFolderIds(
                        status, collabRootFolderIds, Pageable.unpaged());
                if (collabPage != null && collabPage.getContent() != null) {
                    List<Long> existingIds = allCandidates.stream().map(AccessRequestEntity::getId).collect(Collectors.toList());
                    collabPage.getContent().stream()
                            .filter(ar -> !existingIds.contains(ar.getId()))
                            .forEach(allCandidates::add);
                }
            }
        }


        final String searchLower = search != null ? search.trim().toLowerCase() : null;

        List<AccessRequestResponseDto> filtered = allCandidates.stream()
                .filter(ar -> isUserAuthorizedReviewer(currentUser, ar))
                .filter(ar -> {
                    if (status != null && ar.getStatus() != status) return false;

                    FolderEntity f = ar.getFolder() != null ? ar.getFolder() : (ar.getDocument() != null ? ar.getDocument().getFolder() : null);
                    String reqOrgCode = f != null && f.getOwnerOrg() != null ? f.getOwnerOrg().getOrgCode()
                            : (ar.getDocument() != null && ar.getDocument().getOwnerOrg() != null ? ar.getDocument().getOwnerOrg().getOrgCode() : null);

                    if (orgCode != null && !orgCode.isBlank() && (reqOrgCode == null || !reqOrgCode.equalsIgnoreCase(orgCode))) {
                        return false;
                    }

                    if (searchLower != null && !searchLower.isEmpty()) {
                        boolean matchDoc = ar.getDocument() != null && ar.getDocument().getFileName() != null && ar.getDocument().getFileName().toLowerCase().contains(searchLower);
                        boolean matchFolder = ar.getFolder() != null && ar.getFolder().getName() != null && ar.getFolder().getName().toLowerCase().contains(searchLower);
                        boolean matchReason = ar.getReason() != null && ar.getReason().toLowerCase().contains(searchLower);
                        boolean matchEmail = ar.getRequester() != null && ar.getRequester().getEmail() != null && ar.getRequester().getEmail().toLowerCase().contains(searchLower);
                        boolean matchName = ar.getRequester() != null && (
                                (ar.getRequester().getFirstName() != null && ar.getRequester().getFirstName().toLowerCase().contains(searchLower)) ||
                                (ar.getRequester().getLastName() != null && ar.getRequester().getLastName().toLowerCase().contains(searchLower))
                        );
                        if (!matchDoc && !matchFolder && !matchReason && !matchEmail && !matchName) {
                            return false;
                        }
                    }

                    return true;
                })
                .sorted((a, b) -> {
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(ar -> populateDtoDetails(accessRequestMapper.toResponseDto(ar), ar, currentUser))
                .collect(Collectors.toList());

        if (pageable.isUnpaged()) {
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filtered.size());
        List<AccessRequestResponseDto> paged = start <= end ? filtered.subList(start, end) : List.of();

        return new PageImpl<>(paged, pageable, filtered.size());
    }

    @Override
    @Transactional(readOnly = true)
    public AccessRequestResponseDto getAccessRequestById(Long requestId, UserEntity currentUser) {
        AccessRequestEntity request = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found with ID: " + requestId));

        boolean isRequester = request.getRequester().getId().equals(currentUser.getId());
        boolean isReviewer = isUserAuthorizedReviewer(currentUser, request);

        if (!isRequester && !isReviewer) {
            throw new AccessDeniedException("You are not authorized to view this access request");
        }

        return accessRequestMapper.toResponseDto(request);
    }

    private void revalidateApprovalRequest(AccessRequestEntity request, UserEntity reviewer) {
        UserEntity requester = request.getRequester();
        DocumentEntity document = request.getDocument();
        FolderEntity folder = request.getFolder();

        OrganizationEntity sourceOrg = resolveSourceOrg(folder, document);
        UserEntity sourceUser = resolveSourceUser(folder, document);

        // 1. Re-verify requester active membership if in target org (Objective F)
        if (sourceOrg != null) {
            Optional<UserOrganizationEntity> uoOpt = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(requester.getId(), sourceOrg.getOrgCode());
            if (uoOpt.isPresent()) {
                if (uoOpt.get().getStatus() != MembershipStatus.ACTIVE) {
                    throw new AccessDeniedException("Requester organization membership is not active");
                }
            }
        }

        // 2. Re-verify effective policy
        WorkspacePolicyEntity effectivePolicy = getEffectiveWorkspacePolicy(sourceOrg, sourceUser);
        if (effectivePolicy != null) {
            if (Boolean.FALSE.equals(effectivePolicy.getAllowAccessRequests())) {
                throw new AccessDeniedException("Access requests are currently disabled by workspace policy");
            }

            boolean isExternal = isExternalRequester(requester, sourceOrg, sourceUser);
            if (isExternal && Boolean.FALSE.equals(effectivePolicy.getAllowExternalAccessRequests())) {
                throw new AccessDeniedException("External access requests are currently disabled by workspace policy");
            }
        }

        // 3. Re-verify external trust & policy if external
        boolean isExternal = isExternalRequester(requester, sourceOrg, sourceUser);
        if (isExternal && externalSharingPolicyEvaluator != null) {
            boolean isMember = isMemberOfSourceOrg(requester, sourceOrg);
            String targetOrgCode = isMember ? sourceOrg.getOrgCode() : null;
            RecipientType recipientType = isMember ? RecipientType.USER_ORGANIZATION_MEMBER : RecipientType.USER_PERSONAL;

            PermissionRequestDto permRequest = PermissionRequestDto.builder()
                    .recipientType(recipientType)
                    .targetUserEmail(requester.getEmail())
                    .targetOrgCode(targetOrgCode)
                    .build();
            externalSharingPolicyEvaluator.evaluateAndValidateSharingPolicy(folder, document, permRequest, null);
        }
    }

    private WorkspacePolicyEntity getEffectiveWorkspacePolicy(OrganizationEntity sourceOrg, UserEntity sourceUser) {
        if (workspacePolicyService == null) return null;
        if (sourceOrg != null) {
            return workspacePolicyService.getEffectivePolicyForOrganization(sourceOrg);
        } else if (sourceUser != null) {
            return workspacePolicyService.getEffectivePolicyForUser(sourceUser);
        }
        return null;
    }

    private OrganizationEntity resolveSourceOrg(FolderEntity folder, DocumentEntity document) {
        if (document != null) {
            if (document.getOwnerOrg() != null) return document.getOwnerOrg();
            if (document.getFolder() != null && document.getFolder().getOwnerOrg() != null) {
                return document.getFolder().getOwnerOrg();
            }
        }
        if (folder != null) {
            return folder.getOwnerOrg();
        }
        return null;
    }

    private UserEntity resolveSourceUser(FolderEntity folder, DocumentEntity document) {
        if (document != null) {
            if (document.getUploadedBy() != null) return document.getUploadedBy();
            if (document.getFolder() != null && document.getFolder().getCreatedBy() != null) {
                return document.getFolder().getCreatedBy();
            }
        }
        if (folder != null) {
            return folder.getCreatedBy();
        }
        return null;
    }

    private boolean isMemberOfSourceOrg(UserEntity user, OrganizationEntity sourceOrg) {
        if (user == null || sourceOrg == null) {
            return false;
        }
        if (user.getOrganization() != null && sourceOrg.getOrgCode().equalsIgnoreCase(user.getOrganization().getOrgCode())) {
            return true;
        }
        return userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                user.getId(), sourceOrg.getOrgCode(), MembershipStatus.ACTIVE);
    }

    private boolean isExternalRequester(UserEntity requester, OrganizationEntity sourceOrg, UserEntity sourceUser) {
        if (requester == null) return false;
        if (sourceOrg != null) {
            return !isMemberOfSourceOrg(requester, sourceOrg);
        } else if (sourceUser != null) {
            return !requester.getId().equals(sourceUser.getId());
        }
        return false;
    }

    private AccessRequestEntity getPendingRequestForUpdateOrThrow(Long requestId) {
        AccessRequestEntity request = accessRequestRepository.findByIdForUpdate(requestId)
                .or(() -> accessRequestRepository.findById(requestId))
                .orElseThrow(() -> new ResourceNotFoundException("Access request not found with ID: " + requestId));
        if (request.getStatus() != AccessRequestStatus.PENDING) {
            throw new IllegalStateException("Access request is not in PENDING status (is not pending)");
        }
        return request;
    }

    private void validateReviewerAuthority(UserEntity reviewer, AccessRequestEntity request) {
        if (!isUserAuthorizedReviewer(reviewer, request)) {
            throw new AccessDeniedException("User does not have authority to review access requests for this resource");
        }
    }

    private boolean isUserAuthorizedReviewer(UserEntity user, AccessRequestEntity request) {
        if (user == null || request == null) return false;
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return true;

        FolderEntity effectiveFolder = request.getFolder() != null
                ? request.getFolder()
                : (request.getDocument() != null ? request.getDocument().getFolder() : null);
        DocumentEntity document = request.getDocument();

        // Personal space: owner approves
        if (document != null && document.getOwnerOrg() == null) {
            return evaluatorService.canAdministerDocumentPermissions(user, document);
        }
        if (effectiveFolder != null && effectiveFolder.getOwnerOrg() == null) {
            return evaluatorService.canAdministerFolderPermissions(user, effectiveFolder);
        }

        // Collaboration space check:
        if (effectiveFolder != null && evaluatorService.isCollaborationManagerForFolder(user, effectiveFolder)) {
            return true;
        }

        // Organization space:
        String orgCode = null;
        if (effectiveFolder != null && effectiveFolder.getOwnerOrg() != null) {
            orgCode = effectiveFolder.getOwnerOrg().getOrgCode();
        } else if (document != null && document.getOwnerOrg() != null) {
            orgCode = document.getOwnerOrg().getOrgCode();
        }

        if (orgCode == null) {
            return false;
        }

        // 1. Organization Administrator
        if (evaluatorService.isUserAdminOfOrg(user, orgCode)) {
            return true;
        }

        // 2. Workspace Manager for this folder hierarchy
        if (effectiveFolder != null) {
            if (evaluatorService.isWorkspaceManagerForFolder(user, effectiveFolder)) {
                return true;
            }
            if (projectWorkspaceRepository != null && workspaceManagerRepository != null) {
                List<com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity> workspaces =
                        projectWorkspaceRepository.findByOrganization_OrgCode(orgCode);
                if (workspaces != null) {
                    for (com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity ws : workspaces) {
                        if (ws.getStatus() == com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus.ACTIVE
                                && ws.getRootFolder() != null
                                && isSameOrAncestorFolder(ws.getRootFolder(), effectiveFolder)) {
                            List<com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity> managers =
                                    workspaceManagerRepository.findByWorkspace_Id(ws.getId());
                            if (managers != null) {
                                for (com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity wm : managers) {
                                    if (wm.getUser() != null && wm.getUser().getId().equals(user.getId())) {
                                        return true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Direct Org Share: Original Sharer
        // Only if user was the grantedBy actor of the exclusion AND user currently has actual authority
        UserEntity originalSharer = findOriginalSharerForExclusion(request.getRequester(), document, effectiveFolder);
        if (originalSharer != null && originalSharer.getId().equals(user.getId())) {
            boolean hasAuthority = document != null
                    ? evaluatorService.canAdministerDocumentPermissions(user, document)
                    : (effectiveFolder != null && evaluatorService.canAdministerFolderPermissions(user, effectiveFolder));
            if (hasAuthority) {
                return true;
            }
        }

        return false;
    }

    private UserEntity findOriginalSharerForExclusion(UserEntity requester, DocumentEntity document, FolderEntity folder) {
        if (requester == null) return null;
        if (document != null) {
            return evaluatorService.findExplicitExclusion(requester, document)
                    .map(PermissionEntity::getGrantedBy)
                    .orElse(null);
        } else if (folder != null) {
            return evaluatorService.findExplicitExclusion(requester, folder)
                    .map(PermissionEntity::getGrantedBy)
                    .orElse(null);
        }
        return null;
    }


    private boolean isSameOrAncestorFolder(FolderEntity ancestor, FolderEntity target) {
        if (ancestor == null || target == null) return false;
        FolderEntity cursor = target;
        while (cursor != null) {
            if (ancestor.getId() != null && ancestor.getId().equals(cursor.getId())) {
                return true;
            }
            cursor = cursor.getParentFolder();
        }
        return false;
    }

    private List<String> getUserAdminOrgCodes(UserEntity user) {
        List<String> orgCodes = new ArrayList<>();
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return orgCodes;
        }
        if (user.getOrganization() != null) {
            if (user.getRole() == Role.ROLE_ORG_ADMIN) {
                orgCodes.add(user.getOrganization().getOrgCode());
            }
        }
        List<UserOrganizationEntity> memberships = userOrganizationRepository.findByUser_Id(user.getId());
        for (UserOrganizationEntity uo : memberships) {
            if (uo.getStatus() == MembershipStatus.ACTIVE && uo.getOrganization() != null) {
                if (uo.getRole() == Role.ROLE_ORG_ADMIN) {
                    orgCodes.add(uo.getOrganization().getOrgCode());
                }
            }
        }
        return orgCodes;
    }

    private void logAuditForStateChange(String action, AccessRequestEntity request, UserEntity actor) {
        String targetType = request.getDocument() != null ? "DOCUMENT" : "FOLDER";
        Long targetId = request.getDocument() != null ? request.getDocument().getId() : request.getFolder().getId();
        String fileHash = request.getDocumentFileHash();
        Long folderId = request.getFolder() != null ? request.getFolder().getId() : (request.getDocument() != null && request.getDocument().getFolder() != null ? request.getDocument().getFolder().getId() : null);
        SpaceType spaceType = request.getDocument() != null 
                ? (request.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) 
                : (request.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL);
        String orgCode = request.getDocument() != null ? (request.getDocument().getOwnerOrg() != null ? request.getDocument().getOwnerOrg().getOrgCode() : null)
                : (request.getFolder().getOwnerOrg() != null ? request.getFolder().getOwnerOrg().getOrgCode() : null);

        String previousValue = "PENDING";
        String newValue = request.getStatus() != null ? request.getStatus().name() : null;
        String reason = request.getReason();
        String details;
        if (request.getStatus() == AccessRequestStatus.APPROVED) {
            details = (request.getReviewerNote() != null && !request.getReviewerNote().isBlank())
                    ? request.getReviewerNote()
                    : "Access request approved";
        } else if (request.getStatus() == AccessRequestStatus.DENIED) {
            details = (request.getReviewerNote() != null && !request.getReviewerNote().isBlank())
                    ? request.getReviewerNote()
                    : "Access request denied";
        } else if (request.getStatus() == AccessRequestStatus.CANCELLED) {
            details = "Access request cancelled by requester";
        } else {
            details = (request.getReviewerNote() != null && !request.getReviewerNote().isBlank())
                    ? request.getReviewerNote()
                    : ("Access request " + (request.getStatus() != null ? request.getStatus().name().toLowerCase() : "updated"));
        }

        AuditPermissionChangeCommand cmd = AuditPermissionChangeCommand.builder()
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(fileHash)
                .folderId(folderId)
                .spaceType(spaceType)
                .orgCode(orgCode)
                .grantedBy(actor)
                .actorUserId(actor != null ? actor.getId() : null)
                .grantedToEmail(request.getRequester() != null ? request.getRequester().getEmail() : null)
                .permissionLevel(request.getRequestedPermissionLevel())
                .previousValue(previousValue)
                .newValue(newValue)
                .reason(reason)
                .details(details)
                .timestamp(Instant.now())
                .build();

        auditLogService.logPermissionChange(cmd);
    }

    private Set<UserEntity> resolveDecidersForRequest(AccessRequestEntity saved) {
        Set<UserEntity> deciders = new LinkedHashSet<>();
        if (saved == null) {
            return deciders;
        }
        DocumentEntity document = saved.getDocument();
        FolderEntity folder = saved.getFolder();
        FolderEntity effectiveFolder = folder != null ? folder : (document != null ? document.getFolder() : null);

        boolean isPersonal = (document != null && document.getOwnerOrg() == null) ||
                             (effectiveFolder != null && effectiveFolder.getOwnerOrg() == null);

        if (isPersonal) {
            UserEntity owner = document != null ? document.getUploadedBy() : (effectiveFolder != null ? effectiveFolder.getCreatedBy() : null);
            if (owner != null) {
                deciders.add(owner);
            }
        } else {
            // Check Collaboration space
            if (effectiveFolder != null && collaborationRepository != null) {
                List<CollaborationEntity> activeCollaborations = collaborationRepository.findByStatus(CollaborationStatus.ACTIVE);
                for (CollaborationEntity collaboration : activeCollaborations) {
                    if (collaboration.getRootFolder() != null && isSameOrAncestorFolder(collaboration.getRootFolder(), effectiveFolder)) {
                        List<CollaborationParticipantEntity> participants = collaborationParticipantRepository
                                 .findByCollaboration_Id(collaboration.getId());
                        if (participants != null) {
                            for (CollaborationParticipantEntity cp : participants) {
                                if (cp.getCollaborationRole() == CollaborationRole.MANAGER && cp.getUser() != null) {
                                    deciders.add(cp.getUser());
                                }
                            }
                        }
                    }
                }
            }

            // 1. Direct Org Share: Original Sharer (if currently holds authority)
            UserEntity originalSharer = findOriginalSharerForExclusion(saved.getRequester(), document, effectiveFolder);
            if (originalSharer != null) {
                boolean hasAuthority = document != null
                        ? evaluatorService.canAdministerDocumentPermissions(originalSharer, document)
                        : (effectiveFolder != null && evaluatorService.canAdministerFolderPermissions(originalSharer, effectiveFolder));
                if (hasAuthority) {
                    deciders.add(originalSharer);
                }
            }

            // Check Org space
            String orgCode = effectiveFolder != null && effectiveFolder.getOwnerOrg() != null
                    ? effectiveFolder.getOwnerOrg().getOrgCode()
                    : (document != null && document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : null);

            // 2. Team / Workspace Share: Active Workspace Managers
            if (effectiveFolder != null && orgCode != null && projectWorkspaceRepository != null && workspaceManagerRepository != null) {
                List<com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity> workspaces =
                        projectWorkspaceRepository.findByOrganization_OrgCode(orgCode);
                if (workspaces != null) {
                    for (com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity ws : workspaces) {
                        if (ws.getStatus() == com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus.ACTIVE
                                && ws.getRootFolder() != null
                                && isSameOrAncestorFolder(ws.getRootFolder(), effectiveFolder)) {
                            List<com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity> managers =
                                    workspaceManagerRepository.findByWorkspace_Id(ws.getId());
                            if (managers != null) {
                                for (com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity wm : managers) {
                                    if (wm.getUser() != null && evaluatorService.isWorkspaceManagerForFolder(wm.getUser(), effectiveFolder)) {
                                        deciders.add(wm.getUser());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Organization Administrators
            if (orgCode != null) {
                List<UserOrganizationEntity> admins = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(orgCode, MembershipStatus.ACTIVE);
                for (UserOrganizationEntity uo : admins) {
                    if (uo.getRole() == Role.ROLE_ORG_ADMIN && uo.getUser() != null) {
                        deciders.add(uo.getUser());
                    }
                }
            }
        }
        return deciders;
    }

    private void notifyAccessRequestCancelled(AccessRequestEntity saved, UserEntity requester) {
        if (saved == null || notificationService == null) {
            return;
        }

        Set<UserEntity> deciders = resolveDecidersForRequest(saved);

        for (UserEntity decider : deciders) {
            if (decider == null || decider.getId() == null || decider.getId().equals(requester.getId())) {
                continue;
            }
            if (decider.getOrganization() != null) {
                Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(decider.getId(), decider.getOrganization().getOrgCode());
                if (uo.isPresent() && uo.get().getStatus() != MembershipStatus.ACTIVE) {
                    continue;
                }
            }

            String idempotencyKey = NotificationType.ACCESS_REQUEST_CANCELLED.name() + ":" + saved.getId() + ":" + decider.getId();
            notificationService.createNotification(
                    decider,
                    requester,
                    NotificationType.ACCESS_REQUEST_CANCELLED,
                    "Access Request Cancelled",
                    requester.getEmail() + " cancelled their access request.",
                    NotificationSourceType.ACCESS_REQUEST,
                    String.valueOf(saved.getId()),
                    NotificationCategory.ACCESS_REQUEST,
                    NotificationPriority.MEDIUM,
                    idempotencyKey
            );
        }
    }

    private void notifyAccessRequestSubmitted(AccessRequestEntity saved, UserEntity requester, AccessRequestCreateDto dto) {
        if (saved == null || notificationService == null) {
            return;
        }

        Set<UserEntity> deciders = resolveDecidersForRequest(saved);

        // Filter out: nulls, requester (actor), inactive or suspended users
        for (UserEntity decider : deciders) {
            if (decider == null || decider.getId() == null || decider.getId().equals(requester.getId())) {
                continue;
            }
            if (decider.getOrganization() != null) {
                Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(decider.getId(), decider.getOrganization().getOrgCode());
                if (uo.isPresent() && uo.get().getStatus() != MembershipStatus.ACTIVE) {
                    continue;
                }
            }

            String idempotencyKey = NotificationType.ACCESS_REQUEST_SUBMITTED.name() + ":" + saved.getId() + ":" + decider.getId();
            notificationService.createNotification(
                    decider,
                    requester,
                    NotificationType.ACCESS_REQUEST_SUBMITTED,
                    "Access Request Submitted",
                    requester.getEmail() + " requested " + dto.getRequestedPermissionLevel() + " access.",
                    NotificationSourceType.ACCESS_REQUEST,
                    String.valueOf(saved.getId()),
                    NotificationCategory.ACCESS_REQUEST,
                    NotificationPriority.HIGH,
                    idempotencyKey
            );
        }
    }

    private AccessRequestResponseDto populateDtoDetails(AccessRequestResponseDto dto, AccessRequestEntity entity, UserEntity currentUser) {
        if (dto == null || entity == null) return dto;

        FolderEntity folder = entity.getFolder();
        DocumentEntity doc = entity.getDocument();
        FolderEntity effectiveFolder = folder != null ? folder : (doc != null ? doc.getFolder() : null);

        String orgCode = null;
        if (effectiveFolder != null && effectiveFolder.getOwnerOrg() != null) {
            orgCode = effectiveFolder.getOwnerOrg().getOrgCode();
        } else if (doc != null && doc.getOwnerOrg() != null) {
            orgCode = doc.getOwnerOrg().getOrgCode();
        }
        dto.setOrgCode(orgCode);

        if (currentUser != null) {
            if (orgCode != null && evaluatorService.isUserAdminOfOrg(currentUser, orgCode)) {
                dto.setReviewerAuthorityExplanation("Organization Administrator for " + orgCode);
            } else if ((doc != null && doc.getUploadedBy() != null && doc.getUploadedBy().getId().equals(currentUser.getId())) ||
                    (folder != null && folder.getCreatedBy() != null && folder.getCreatedBy().getId().equals(currentUser.getId()))) {
                dto.setReviewerAuthorityExplanation("Resource Owner");
            } else if (effectiveFolder != null && evaluatorService.isWorkspaceManagerForFolder(currentUser, effectiveFolder)) {
                dto.setReviewerAuthorityExplanation("Workspace Manager");
            } else if (effectiveFolder != null && evaluatorService.isCollaborationManagerForFolder(currentUser, effectiveFolder)) {
                dto.setReviewerAuthorityExplanation("Collaboration Manager");
            } else {
                UserEntity originalSharer = findOriginalSharerForExclusion(entity.getRequester(), doc, effectiveFolder);
                if (originalSharer != null && originalSharer.getId().equals(currentUser.getId())) {
                    dto.setReviewerAuthorityExplanation("Original Sharer");
                }
            }
        }


        return dto;
    }
}
