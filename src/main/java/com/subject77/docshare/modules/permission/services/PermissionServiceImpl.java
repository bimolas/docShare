package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.dto.BulkSelectedMemberShareDto;
import com.subject77.docshare.modules.permission.dto.BulkShareWithExclusionsDto;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessEntryDto;
import com.subject77.docshare.modules.permission.dto.ManageAccessSummaryDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.dto.UserAccessSummaryDto;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.events.PermissionChangeEvent;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.mapper.PermissionMapper;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;

import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.Role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final AuditLogService auditLogService;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final com.subject77.docshare.modules.organization.repository.GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final OrganizationRepository organizationRepository;
    private final CollaborationRepository collaborationRepository;
    private final PermissionEventPublisher eventPublisher;
    private final PermissionCacheService cacheService;
    private final PermissionEvaluatorService evaluatorService;
    private final ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    private final NotificationService notificationService;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;

    @Override
    @Transactional
    public PermissionResponseDto grantPermission(PermissionRequestDto requestDto, String grantedByUserId) {
        log.info("Granting permission: type={}, level={}, grantedBy={}", 
                requestDto.getPermissionType(), requestDto.getPermissionLevel(), grantedByUserId);

        UserEntity grantedBy = userRepository.findById(grantedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + grantedByUserId));

        FolderEntity folder = null;
        if (requestDto.getFolderId() != null) {
            folder = folderRepository.findById(requestDto.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + requestDto.getFolderId()));
        }

        DocumentEntity document = null;
        if (requestDto.getDocumentId() != null || (requestDto.getDocumentFileHash() != null && !requestDto.getDocumentFileHash().isBlank())) {
            document = resolveDocumentEntity(requestDto.getDocumentId(), requestDto.getDocumentFileHash(), grantedBy);
        }

        com.subject77.docshare.modules.organization.entities.GroupEntity targetGroup = null;
        if (requestDto.getTargetGroupId() != null) {
            targetGroup = groupRepository.findById(requestDto.getTargetGroupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + requestDto.getTargetGroupId()));
        }

        CollaborationEntity collaboration = null;
        if (requestDto.getCollaborationId() != null) {
            collaboration = collaborationRepository.findById(requestDto.getCollaborationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + requestDto.getCollaborationId()));

            // 1. Collaboration must be ACTIVE
            if (collaboration.getStatus() != null && collaboration.getStatus() != CollaborationStatus.ACTIVE) {
                throw new IllegalStateException("Collaboration is not active");
            }

            // 2. Target folder/document must be within collaboration root folder hierarchy
            FolderEntity targetFolder = (folder != null) ? folder : (document != null ? document.getFolder() : null);
            if (targetFolder != null && collaboration.getRootFolder() != null
                    && !evaluatorService.isSameOrAncestorFolder(collaboration.getRootFolder(), targetFolder)) {
                throw new IllegalArgumentException("Target resource is not within collaboration scope");
            }

            // 3. Target resource organization compatibility
            if (targetFolder != null && targetFolder.getOwnerOrg() != null) {
                String resOrgCode = targetFolder.getOwnerOrg().getOrgCode();
                if (collaboration.getOwnerOrganization() != null || collaboration.getPartnerOrganization() != null) {
                    boolean matchesOwner = collaboration.getOwnerOrganization() != null && resOrgCode.equalsIgnoreCase(collaboration.getOwnerOrganization().getOrgCode());
                    boolean matchesPartner = collaboration.getPartnerOrganization() != null && resOrgCode.equalsIgnoreCase(collaboration.getPartnerOrganization().getOrgCode());
                    if (!matchesOwner && !matchesPartner) {
                        throw new IllegalArgumentException("Target resource does not belong to collaboration partner organizations");
                    }
                }
            }

            // 4. Actor must be an authorized manager of this specific collaboration (or partner org admin / super admin)
            boolean isSuperAdmin = grantedBy.getRole() == Role.ROLE_SUPER_ADMIN;
            boolean isCollabMgr = evaluatorService.isUserCollaborationManager(grantedBy, collaboration.getId());
            boolean isPartnerAdmin = (collaboration.getOwnerOrganization() != null && evaluatorService.isUserAdminOfOrg(grantedBy, collaboration.getOwnerOrganization().getOrgCode()))
                    || (collaboration.getPartnerOrganization() != null && evaluatorService.isUserAdminOfOrg(grantedBy, collaboration.getPartnerOrganization().getOrgCode()));
            boolean hasAuthority = isSuperAdmin || isCollabMgr || isPartnerAdmin;
            if (!hasAuthority) {
                AuthorityTier tier = evaluatorService.resolveAuthorityTierAtCreation(grantedBy, folder, document, collaboration);
                if (tier != null && tier != AuthorityTier.COLLABORATION_MANAGER && tier != AuthorityTier.SUPER_ADMIN && tier != AuthorityTier.ORG_ADMIN) {
                    throw new org.springframework.security.access.AccessDeniedException("User is not an authorized manager of collaboration ID: " + collaboration.getId());
                }
            }
        } else {
            FolderEntity targetFolder = (folder != null) ? folder : (document != null ? document.getFolder() : null);
            if (targetFolder != null) {
                collaboration = evaluatorService.resolveCollaborationForFolder(targetFolder);
            }
        }

        // Recipient User Existence Check
        if (requestDto.getTargetUserEmail() != null && !requestDto.getTargetUserEmail().isBlank()) {
            if (!userRepository.existsByEmail(requestDto.getTargetUserEmail())) {
                throw new ResourceNotFoundException("Target user not found with email: " + requestDto.getTargetUserEmail());
            }
        }

        // RecipientType Validation & Resolution
        validateAndResolveRecipientType(requestDto);

        // Mismatched folder/document resource check
        if (folder != null && document != null) {
            String folderOrgCode = (folder.getOwnerOrg() != null) ? folder.getOwnerOrg().getOrgCode() : null;
            String documentOrgCode = (document.getOwnerOrg() != null) ? document.getOwnerOrg().getOrgCode() : null;
            if ((folderOrgCode == null && documentOrgCode != null) ||
                (folderOrgCode != null && documentOrgCode == null) ||
                (folderOrgCode != null && !folderOrgCode.equalsIgnoreCase(documentOrgCode))) {
                throw new IllegalArgumentException("Folder and document belong to different organizations");
            }
        }

        // Organization Isolation Validation
        validateOrganizationIsolation(folder, document, requestDto, targetGroup);

        // Permission Level Validation
        if (requestDto.getPermissionLevel() == null) {
            throw new IllegalArgumentException("Permission level is required");
        }

        // Expiration Date Validation
        if (requestDto.getExpiresAt() != null && requestDto.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expiration date cannot be in the past");
        }

        // Access-Administration Enforcement
        if (folder != null) {
            if (!evaluatorService.canAdministerFolderPermissions(grantedBy, folder)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer folder permissions");
            }
        }
        if (document != null) {
            if (!evaluatorService.canAdministerDocumentPermissions(grantedBy, document)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer document permissions");
            }
        }

        // Delegation Ceiling Enforcement (Phase 3B.2)
        PermissionLevel delegationCeiling = evaluatorService.resolveDelegationCeiling(grantedBy, folder, document);
        if (delegationCeiling == null || requestDto.getPermissionLevel().ordinal() > delegationCeiling.ordinal()) {
            throw new org.springframework.security.access.AccessDeniedException("Requested permission level " 
                    + requestDto.getPermissionLevel() + " exceeds grantor delegation ceiling of " + delegationCeiling);
        }

        // External-sharing policy check
        externalSharingPolicyEvaluator.evaluateAndValidateSharingPolicy(folder, document, requestDto, targetGroup);

        Instant validFrom = Instant.now();

        PermissionEntity permission = null;
        if (targetGroup != null && folder != null) {
            List<PermissionEntity> existingList = permissionRepository
                    .findByTargetGroup_IdAndFolder_IdAndRevokedFalse(targetGroup.getId(), folder.getId()).stream()
                    .filter(p -> p.getRecipientType() == requestDto.getRecipientType())
                    .collect(Collectors.toList());
            if (!existingList.isEmpty()) {
                PermissionEntity existing = existingList.get(0);
                // Provenance Override Enforcement (Phase 3B.3)
                if (!evaluatorService.canActorOverrideGrant(grantedBy, existing, folder, document)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "User does not have authority to override existing permission created with tier " 
                            + existing.getGrantorAuthorityTier());
                }
                permission = existing;
                permission.setPermissionType(requestDto.getPermissionType());
                permission.setPermissionLevel(requestDto.getPermissionLevel());
                permission.setRecipientType(requestDto.getRecipientType());
                // Preserved: grantedBy and grantorAuthorityTier remain the original grant creator
                permission.setDocumentFileHash(requestDto.getDocumentFileHash());
                permission.setTargetUserEmail(requestDto.getTargetUserEmail());
                permission.setTargetOrgCode(requestDto.getTargetOrgCode());
                permission.setCollaboration(collaboration);
                permission.setPasswordHash(requestDto.getPasswordHash());
                permission.setValidFrom(validFrom);
                permission.setExpiresAt(requestDto.getExpiresAt());
                permission.setNote(requestDto.getNote());
                permission.setSuspended(false);
                permission.setRevoked(false);
                permission.setRevokedAt(null);
                permission.setRevokedBy(null);
                permission.setBlockchainStatus(BlockchainStatus.PENDING);

                for (int i = 1; i < existingList.size(); i++) {
                    PermissionEntity sibling = existingList.get(i);
                    sibling.setRevoked(true);
                    sibling.setRevokedAt(Instant.now());
                    sibling.setRevokedBy(grantedBy);
                    permissionRepository.save(sibling);
                }
            }
        } else if (targetGroup != null && document != null) {
            List<PermissionEntity> existingList = permissionRepository
                    .findByTargetGroup_IdAndDocument_IdAndRevokedFalse(targetGroup.getId(), document.getId()).stream()
                    .filter(p -> p.getRecipientType() == requestDto.getRecipientType())
                    .collect(Collectors.toList());
            if (!existingList.isEmpty()) {
                PermissionEntity existing = existingList.get(0);
                // Provenance Override Enforcement (Phase 3B.3)
                if (!evaluatorService.canActorOverrideGrant(grantedBy, existing, folder, document)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "User does not have authority to override existing permission created with tier " 
                            + existing.getGrantorAuthorityTier());
                }
                permission = existing;
                permission.setPermissionType(requestDto.getPermissionType());
                permission.setPermissionLevel(requestDto.getPermissionLevel());
                permission.setRecipientType(requestDto.getRecipientType());
                // Preserved: grantedBy and grantorAuthorityTier remain the original grant creator
                permission.setDocumentFileHash(requestDto.getDocumentFileHash());
                permission.setTargetUserEmail(requestDto.getTargetUserEmail());
                permission.setTargetOrgCode(requestDto.getTargetOrgCode());
                permission.setCollaboration(collaboration);
                permission.setPasswordHash(requestDto.getPasswordHash());
                permission.setValidFrom(validFrom);
                permission.setExpiresAt(requestDto.getExpiresAt());
                permission.setNote(requestDto.getNote());
                permission.setSuspended(false);
                permission.setRevoked(false);
                permission.setRevokedAt(null);
                permission.setRevokedBy(null);
                permission.setBlockchainStatus(BlockchainStatus.PENDING);

                for (int i = 1; i < existingList.size(); i++) {
                    PermissionEntity sibling = existingList.get(i);
                    sibling.setRevoked(true);
                    sibling.setRevokedAt(Instant.now());
                    sibling.setRevokedBy(grantedBy);
                    permissionRepository.save(sibling);
                }
            }
        } else if (requestDto.getTargetUserEmail() != null && !requestDto.getTargetUserEmail().isBlank()) {
            String email = requestDto.getTargetUserEmail().trim();
            List<PermissionEntity> existingList = List.of();
            if (folder != null) {
                existingList = permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse(email, folder.getId());
            } else if (document != null) {
                existingList = permissionRepository.findByTargetUserEmailIgnoreCaseAndDocument_IdAndRevokedFalse(email, document.getId());
                if (existingList.isEmpty() && document.getFileHash() != null) {
                    existingList = permissionRepository.findByTargetUserEmailIgnoreCaseAndDocumentFileHashAndRevokedFalse(email, document.getFileHash());
                }
            } else if (requestDto.getDocumentFileHash() != null && !requestDto.getDocumentFileHash().isBlank()) {
                existingList = permissionRepository.findByTargetUserEmailIgnoreCaseAndDocumentFileHashAndRevokedFalse(email, requestDto.getDocumentFileHash());
            }

            List<PermissionEntity> activeList = existingList.stream()
                    .filter(p -> !p.isRevoked())
                    .filter(p -> p.getExpiresAt() == null || p.getExpiresAt().isAfter(Instant.now()))
                    .collect(Collectors.toList());

            if (!activeList.isEmpty()) {
                PermissionEntity existing = activeList.get(0);
                // Provenance Override Enforcement (Phase 3B.3)
                if (!evaluatorService.canActorOverrideGrant(grantedBy, existing, folder, document)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "User does not have authority to override existing permission created with tier " 
                            + existing.getGrantorAuthorityTier());
                }
                permission = existing;
                permission.setPermissionType(requestDto.getPermissionType());
                permission.setPermissionLevel(requestDto.getPermissionLevel());
                permission.setRecipientType(requestDto.getRecipientType());
                // Preserved: grantedBy, grantorAuthorityTier, and workspaceId remain the original grant creator
                permission.setDocumentFileHash(requestDto.getDocumentFileHash());
                permission.setTargetUserEmail(requestDto.getTargetUserEmail());
                permission.setTargetOrgCode(requestDto.getTargetOrgCode());
                permission.setCollaboration(collaboration);
                permission.setPasswordHash(requestDto.getPasswordHash());
                permission.setValidFrom(validFrom);
                permission.setExpiresAt(requestDto.getExpiresAt());
                permission.setNote(requestDto.getNote());
                permission.setSuspended(false);
                permission.setRevoked(false);
                permission.setRevokedAt(null);
                permission.setRevokedBy(null);
                permission.setBlockchainStatus(BlockchainStatus.PENDING);

                for (int i = 1; i < activeList.size(); i++) {
                    PermissionEntity sibling = activeList.get(i);
                    sibling.setRevoked(true);
                    sibling.setRevokedAt(Instant.now());
                    sibling.setRevokedBy(grantedBy);
                    permissionRepository.save(sibling);
                }
            }
        } else if (requestDto.getRecipientType() == RecipientType.ORGANIZATION
                && requestDto.getTargetOrgCode() != null && !requestDto.getTargetOrgCode().isBlank()) {
            String orgCode = requestDto.getTargetOrgCode().trim();
            PermissionType requestedPermissionType = requestDto.getPermissionType() != null 
                    ? requestDto.getPermissionType() 
                    : PermissionType.ALLOW;
            List<PermissionEntity> existingList = List.of();
            if (folder != null) {
                existingList = permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                        RecipientType.ORGANIZATION, orgCode, folder.getId());
            } else if (document != null) {
                existingList = permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocument_IdAndRevokedFalse(
                        RecipientType.ORGANIZATION, orgCode, document.getId());
                String docHash = (document.getFileHash() != null && !document.getFileHash().isBlank())
                        ? document.getFileHash()
                        : requestDto.getDocumentFileHash();
                if (docHash != null && !docHash.isBlank()) {
                    List<PermissionEntity> byHash = permissionRepository
                            .findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocumentFileHashAndRevokedFalse(
                                    RecipientType.ORGANIZATION, orgCode, docHash.trim());
                    if (!byHash.isEmpty()) {
                        List<PermissionEntity> combined = new java.util.ArrayList<>(existingList);
                        for (PermissionEntity p : byHash) {
                            if (combined.stream().noneMatch(c -> c.getId() != null && c.getId().equals(p.getId()))) {
                                combined.add(p);
                            }
                        }
                        existingList = combined;
                    }
                }
            } else if (requestDto.getDocumentFileHash() != null && !requestDto.getDocumentFileHash().isBlank()) {
                existingList = permissionRepository
                        .findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocumentFileHashAndRevokedFalse(
                                RecipientType.ORGANIZATION, orgCode, requestDto.getDocumentFileHash().trim());
            }

            List<PermissionEntity> activeList = existingList.stream()
                    .filter(p -> !p.isRevoked())
                    .filter(p -> p.getPermissionType() == requestedPermissionType)
                    .filter(p -> p.getExpiresAt() == null || p.getExpiresAt().isAfter(Instant.now()))
                    .collect(Collectors.toList());

            if (!activeList.isEmpty()) {
                PermissionEntity existing = activeList.get(0);
                // Provenance Override Enforcement (Phase 3B.3)
                if (!evaluatorService.canActorOverrideGrant(grantedBy, existing, folder, document)) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "User does not have authority to override existing permission created with tier " 
                            + existing.getGrantorAuthorityTier());
                }
                if (existing.isSuspended() && existing.getRevokedBy() != null) {
                    if (!evaluatorService.canActorOverrideInactivator(grantedBy, existing.getRevokedBy(), existing, folder, document)) {
                        throw new org.springframework.security.access.AccessDeniedException(
                                "User does not have authority to reactivate permission suspended by user " 
                                + existing.getRevokedBy().getEmail());
                    }
                }
                permission = existing;
                permission.setPermissionType(requestedPermissionType);
                permission.setPermissionLevel(requestDto.getPermissionLevel());
                permission.setRecipientType(RecipientType.ORGANIZATION);
                // Preserved: grantedBy, grantorAuthorityTier, and workspaceId remain the original grant creator
                if (permission.getFolder() == null && folder != null) {
                    permission.setFolder(folder);
                }
                if (permission.getDocument() == null && document != null) {
                    permission.setDocument(document);
                }
                String targetHash = requestDto.getDocumentFileHash();
                if ((targetHash == null || targetHash.isBlank()) && document != null) {
                    targetHash = document.getFileHash();
                }
                if (targetHash == null || targetHash.isBlank()) {
                    targetHash = existing.getDocumentFileHash();
                }
                permission.setDocumentFileHash(targetHash);
                permission.setTargetUserEmail(null);
                permission.setTargetOrgCode(requestDto.getTargetOrgCode());
                permission.setTargetGroup(null);
                permission.setCollaboration(collaboration);
                permission.setPasswordHash(requestDto.getPasswordHash());
                permission.setValidFrom(validFrom);
                permission.setExpiresAt(requestDto.getExpiresAt());
                permission.setNote(requestDto.getNote());
                permission.setSuspended(false);
                permission.setRevoked(false);
                permission.setRevokedAt(null);
                permission.setRevokedBy(null);
                permission.setBlockchainStatus(BlockchainStatus.PENDING);

                for (int i = 1; i < activeList.size(); i++) {
                    PermissionEntity sibling = activeList.get(i);
                    sibling.setRevoked(true);
                    sibling.setSuspended(false);
                    sibling.setRevokedAt(Instant.now());
                    sibling.setRevokedBy(grantedBy);
                    sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                    sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                    permissionRepository.save(sibling);

                    AuditPermissionChangeCommand siblingCmd = AuditPermissionChangeCommand.builder()
                            .action("REVOKE_DUPLICATE_ORGANIZATION_GRANT")
                            .targetType(folder != null ? "FOLDER" : "DOCUMENT")
                            .targetId(folder != null ? folder.getId() : (document != null ? document.getId() : null))
                            .targetFileHash(requestDto.getDocumentFileHash())
                            .folderId(folder != null ? folder.getId() : (document != null && document.getFolder() != null ? document.getFolder().getId() : requestDto.getFolderId()))
                            .workspaceId(sibling.getWorkspaceId())
                            .groupId(sibling.getTargetGroup() != null ? sibling.getTargetGroup().getId() : null)
                            .collaborationId(sibling.getCollaboration() != null ? sibling.getCollaboration().getId() : null)
                            .spaceType(folder != null ? (folder.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (document != null ? (document.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                            .orgCode(folder != null && folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : (document != null && document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : requestDto.getTargetOrgCode()))
                            .actorUserId(grantedBy != null ? grantedBy.getId() : null)
                            .grantedBy(grantedBy)
                            .grantedToOrgCode(requestDto.getTargetOrgCode())
                            .permissionLevel(sibling.getPermissionLevel())
                            .validFrom(sibling.getValidFrom())
                            .expiresAt(sibling.getExpiresAt())
                            .recordHash(sibling.getRecordHash())
                            .blockchainStatus(BlockchainStatus.PENDING)
                            .timestamp(Instant.now())
                            .build();
                    if (auditLogService != null) auditLogService.logPermissionChange(siblingCmd);
                }
            }
        }

        if (permission == null) {
            AuthorityTier resolvedAuthorityTier = evaluatorService.resolveAuthorityTierAtCreation(grantedBy, folder, document, collaboration);
            Long resolvedWorkspaceId = null;
            if (resolvedAuthorityTier == AuthorityTier.WORKSPACE_MANAGER) {
                FolderEntity targetFolder = (folder != null) ? folder : (document != null ? document.getFolder() : null);
                resolvedWorkspaceId = evaluatorService.resolveWorkspaceIdForManager(grantedBy, targetFolder, requestDto.getWorkspaceId());
            } else if (requestDto.getWorkspaceId() != null) {
                resolvedWorkspaceId = requestDto.getWorkspaceId();
            }

            permission = PermissionEntity.builder()
                    .permissionType(requestDto.getPermissionType())
                    .permissionLevel(requestDto.getPermissionLevel())
                    .recipientType(requestDto.getRecipientType())
                    .grantedBy(grantedBy)
                    .grantorAuthorityTier(resolvedAuthorityTier)
                    .workspaceId(resolvedWorkspaceId)
                    .document(document)
                    .documentFileHash(requestDto.getDocumentFileHash())
                    .folder(folder)
                    .targetUserEmail(requestDto.getTargetUserEmail())
                    .targetOrgCode(requestDto.getTargetOrgCode())
                    .targetGroup(targetGroup)
                    .collaboration(collaboration)
                    .shareLinkToken(null)
                    .passwordHash(requestDto.getPasswordHash())
                    .validFrom(validFrom)
                    .expiresAt(requestDto.getExpiresAt())
                    .note(requestDto.getNote())
                    .blockchainStatus(BlockchainStatus.PENDING)
                    .revoked(false)
                    .build();
        }

        // Calculate cryptographic SHA-256 fingerprint
        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);

        PermissionEntity savedPermission = permissionRepository.save(permission);


        // Audit Logging
        String targetType = folder != null ? "FOLDER" : "DOCUMENT";
        Long targetId = folder != null ? folder.getId() : (document != null ? document.getId() : null);

        boolean isBoundary = requestDto.getPermissionType() == PermissionType.BOUNDARY;
        String actionName = isBoundary
                ? "TEAM_ALLOCATE_WORKSPACE"
                : "GRANT_" + requestDto.getPermissionType().name();
        String details = isBoundary
                ? "Allocated workspace boundary folder '" + (folder != null ? folder.getName() : "") + "' to team '" + (targetGroup != null ? targetGroup.getName() : "") + "' with ceiling " + requestDto.getPermissionLevel()
                : null;

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action(actionName)
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(requestDto.getDocumentFileHash())
                .folderId(folder != null ? folder.getId() : (document != null && document.getFolder() != null ? document.getFolder().getId() : requestDto.getFolderId()))
                .workspaceId(savedPermission.getWorkspaceId())
                .groupId(targetGroup != null ? targetGroup.getId() : null)
                .collaborationId(collaboration != null ? collaboration.getId() : null)
                .spaceType(folder != null ? (folder.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (document != null ? (document.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(folder != null && folder.getOwnerOrg() != null ? folder.getOwnerOrg().getOrgCode() : (document != null && document.getOwnerOrg() != null ? document.getOwnerOrg().getOrgCode() : requestDto.getTargetOrgCode()))
                .actorUserId(grantedBy != null ? grantedBy.getId() : null)
                .grantedBy(grantedBy)
                .grantedToEmail(requestDto.getTargetUserEmail())
                .grantedToOrgCode(requestDto.getTargetOrgCode())
                .permissionLevel(requestDto.getPermissionLevel())
                .newValue(requestDto.getPermissionLevel() != null ? requestDto.getPermissionLevel().name() : null)
                .details(details)
                .validFrom(validFrom)
                .expiresAt(requestDto.getExpiresAt())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event for Web3 Blockchain Anchoring
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("GRANT")
                .recordHash(recordHash)
                .timestamp(Instant.now())
                .build();

        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Invalidate Redis Cache for target user or org members
        evictCacheForPermissionTarget(savedPermission);

        // Trigger Notification
        String resourceName = savedPermission.getFolder() != null ? savedPermission.getFolder().getName() 
                : (savedPermission.getDocument() != null ? savedPermission.getDocument().getFileName() : "Resource");
        sendPermissionNotification(
                savedPermission, 
                grantedBy, 
                NotificationType.PERMISSION_GRANTED, 
                "Permission Granted", 
                String.format("You were granted %s access to %s.", savedPermission.getPermissionLevel(), resourceName)
        );
        if (savedPermission.getNote() != null && !savedPermission.getNote().isBlank() && savedPermission.getTargetUserEmail() != null) {
            sendPermissionNotification(
                    savedPermission, 
                    grantedBy, 
                    NotificationType.PERMISSION_NOTE_ADDED, 
                    "Permission Note Added", 
                    "Note: " + savedPermission.getNote()
            );
        }

        return PermissionMapper.toResponseDto(savedPermission);
    }

    private RecipientType validateAndResolveRecipientType(PermissionRequestDto requestDto) {
        RecipientType type = requestDto.getRecipientType();

        boolean hasEmail = requestDto.getTargetUserEmail() != null && !requestDto.getTargetUserEmail().isBlank();
        boolean hasOrgCode = requestDto.getTargetOrgCode() != null && !requestDto.getTargetOrgCode().isBlank();
        boolean hasGroup = requestDto.getTargetGroupId() != null;

        if (type == null) {
            throw new IllegalArgumentException("Recipient type is mandatory for permission requests");
        }

        // Validate according to explicit RecipientType — strict field contracts
        switch (type) {
            case USER_PERSONAL:
                if (!hasEmail) {
                    throw new IllegalArgumentException("targetUserEmail is required for USER_PERSONAL recipient type");
                }
                if (hasOrgCode) {
                    throw new IllegalArgumentException("targetOrgCode must be null/blank for USER_PERSONAL recipient type");
                }
                if (hasGroup) {
                    throw new IllegalArgumentException("targetGroupId must be null for USER_PERSONAL recipient type");
                }
                break;
            case USER_ORGANIZATION_MEMBER:
                if (!hasEmail) {
                    throw new IllegalArgumentException("targetUserEmail is required for USER_ORGANIZATION_MEMBER recipient type");
                }
                if (!hasOrgCode) {
                    throw new IllegalArgumentException("targetOrgCode is required for USER_ORGANIZATION_MEMBER recipient type");
                }
                if (hasGroup) {
                    throw new IllegalArgumentException("targetGroupId must be null for USER_ORGANIZATION_MEMBER recipient type");
                }
                boolean isMember = userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(
                        requestDto.getTargetUserEmail(), requestDto.getTargetOrgCode(), MembershipStatus.ACTIVE);
                if (!isMember) {
                    throw new ResourceNotFoundException("User with email " + requestDto.getTargetUserEmail() + " is not an ACTIVE member of organization " + requestDto.getTargetOrgCode());
                }
                break;
            case ORGANIZATION:
                if (!hasOrgCode) {
                    throw new IllegalArgumentException("targetOrgCode is required for ORGANIZATION recipient type");
                }
                if (hasEmail) {
                    throw new IllegalArgumentException("targetUserEmail must be null/blank for ORGANIZATION recipient type");
                }
                if (hasGroup) {
                    throw new IllegalArgumentException("targetGroupId must be null for ORGANIZATION recipient type");
                }
                if (!organizationRepository.existsByOrgCode(requestDto.getTargetOrgCode())) {
                    throw new ResourceNotFoundException("Organization not found with orgCode: " + requestDto.getTargetOrgCode());
                }
                break;
            case GROUP:
                if (!hasGroup) {
                    throw new IllegalArgumentException("targetGroupId is required for GROUP recipient type");
                }
                if (hasEmail) {
                    throw new IllegalArgumentException("targetUserEmail must be null/blank for GROUP recipient type");
                }
                if (hasOrgCode) {
                    throw new IllegalArgumentException("targetOrgCode must be null/blank for GROUP recipient type");
                }
                break;
            default:
                throw new IllegalArgumentException("Unsupported recipient type: " + type);
        }

        return type;
    }

    private void validateOrganizationIsolation(FolderEntity folder, DocumentEntity document, PermissionRequestDto requestDto, GroupEntity targetGroup) {
        OrganizationEntity resourceOrg = (folder != null) ? folder.getOwnerOrg() : (document != null ? document.getOwnerOrg() : null);

        if (folder != null || document != null) {
            if (resourceOrg == null) {
                // Personal resource (ownerOrg == null) may only receive USER_PERSONAL permissions
                if (requestDto.getRecipientType() != RecipientType.USER_PERSONAL) {
                    throw new IllegalArgumentException("Personal resources can only receive direct personal shares (USER_PERSONAL)");
                }
                return;
            }

            String resourceOrgCode = resourceOrg.getOrgCode();

            // A GROUP permission target must belong to the same organization as the folder/document resource
            if (requestDto.getRecipientType() == RecipientType.GROUP) {
                if (targetGroup == null || targetGroup.getOrganization() == null ||
                        !targetGroup.getOrganization().getOrgCode().equalsIgnoreCase(resourceOrgCode)) {
                    throw new IllegalArgumentException("Team/Group target must belong to the same organization as the resource: " + resourceOrgCode);
                }
            }

            // A USER_ORGANIZATION_MEMBER target organization must exactly match the resource's owning organization
            if (requestDto.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER) {
                if (requestDto.getTargetOrgCode() == null ||
                        !requestDto.getTargetOrgCode().equalsIgnoreCase(resourceOrgCode)) {
                    throw new IllegalArgumentException("USER_ORGANIZATION_MEMBER target organization must match resource organization: " + resourceOrgCode);
                }
            }
        } else {
            if (requestDto.getRecipientType() == RecipientType.GROUP) {
                throw new IllegalArgumentException("Cannot grant team/group permission without a valid organization resource");
            }
        }
    }

    @Override
    @Transactional
    public List<PermissionResponseDto> grantFolderShareWithExclusions(BulkShareWithExclusionsDto requestDto, String grantedByUserId) {
        log.info("Granting folder share with exclusions for folderId: {}, targetEmail: {}", 
                requestDto.getFolderId(), requestDto.getTargetUserEmail());

        List<PermissionResponseDto> createdPermissions = new ArrayList<>();

        // 1. Grant ALLOW permission on root folder
        PermissionRequestDto mainFolderReq = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(requestDto.getPermissionLevel())
                .recipientType(requestDto.getRecipientType())
                .folderId(requestDto.getFolderId())
                .targetUserEmail(requestDto.getTargetUserEmail())
                .targetOrgCode(requestDto.getTargetOrgCode())
                .targetGroupId(requestDto.getTargetGroupId())
                .expiresAt(requestDto.getExpiresAt())
                .note(requestDto.getNote())
                .build();

        createdPermissions.add(grantPermission(mainFolderReq, grantedByUserId));

        // 2. Process document exclusions
        if (requestDto.getExcludedDocumentIds() != null) {
            for (Long docId : requestDto.getExcludedDocumentIds()) {
                DocumentEntity doc = documentRepository.findById(docId)
                        .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + docId));

                PermissionRequestDto docExcludeReq = PermissionRequestDto.builder()
                        .permissionType(PermissionType.EXCLUDE)
                        .permissionLevel(requestDto.getPermissionLevel())
                        .recipientType(requestDto.getRecipientType())
                        .documentFileHash(doc.getFileHash())
                        .targetUserEmail(requestDto.getTargetUserEmail())
                        .targetOrgCode(requestDto.getTargetOrgCode())
                        .targetGroupId(requestDto.getTargetGroupId())
                        .expiresAt(requestDto.getExpiresAt())
                        .build();

                createdPermissions.add(grantPermission(docExcludeReq, grantedByUserId));
            }
        }

        // 3. Process subfolder exclusions
        if (requestDto.getExcludedSubfolderIds() != null) {
            for (Long subfolderId : requestDto.getExcludedSubfolderIds()) {
                FolderEntity subfolder = folderRepository.findById(subfolderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Subfolder not found with ID: " + subfolderId));

                PermissionRequestDto subfolderExcludeReq = PermissionRequestDto.builder()
                        .permissionType(PermissionType.EXCLUDE)
                        .permissionLevel(requestDto.getPermissionLevel())
                        .recipientType(requestDto.getRecipientType())
                        .folderId(subfolder.getId())
                        .targetUserEmail(requestDto.getTargetUserEmail())
                        .targetOrgCode(requestDto.getTargetOrgCode())
                        .targetGroupId(requestDto.getTargetGroupId())
                        .expiresAt(requestDto.getExpiresAt())
                        .build();

                createdPermissions.add(grantPermission(subfolderExcludeReq, grantedByUserId));
            }
        }

        return createdPermissions;
    }

    @Override
    @Transactional
    public List<PermissionResponseDto> grantSelectedMembers(BulkSelectedMemberShareDto requestDto, String grantedByUserId) {
        log.info("Granting bulk permissions to selected members of org: {}, grantedBy: {}", 
                requestDto != null ? requestDto.getTargetOrgCode() : null, grantedByUserId);

        if (requestDto == null || requestDto.getTargetOrgCode() == null || requestDto.getTargetOrgCode().isBlank()) {
            throw new IllegalArgumentException("Target organization code is required");
        }

        if (requestDto.getSelectedMembers() == null || requestDto.getSelectedMembers().isEmpty()) {
            throw new IllegalArgumentException("At least one selected member is required");
        }

        if (!organizationRepository.existsByOrgCode(requestDto.getTargetOrgCode())) {
            throw new ResourceNotFoundException("Organization not found with orgCode: " + requestDto.getTargetOrgCode());
        }

        if (requestDto.getFolderId() != null) {
            FolderEntity folder = folderRepository.findById(requestDto.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + requestDto.getFolderId()));
            if (folder.getOwnerOrg() == null || !folder.getOwnerOrg().getOrgCode().equalsIgnoreCase(requestDto.getTargetOrgCode())) {
                throw new IllegalArgumentException("Target folder does not belong to organization: " + requestDto.getTargetOrgCode());
            }
        }

        List<PermissionResponseDto> createdPermissions = new ArrayList<>();

        for (BulkSelectedMemberShareDto.MemberShareItemDto memberItem : requestDto.getSelectedMembers()) {
            if (memberItem == null || memberItem.getTargetUserEmail() == null || memberItem.getTargetUserEmail().isBlank()) {
                throw new IllegalArgumentException("Target user email is required for all selected members");
            }

            // This endpoint is semantically USER_ORGANIZATION_MEMBER — no fallback, no inference
            String itemNote = memberItem.getNote() != null ? memberItem.getNote() : requestDto.getNote();
            PermissionRequestDto singleRequest = PermissionRequestDto.builder()
                    .permissionType(memberItem.getPermissionType() != null ? memberItem.getPermissionType() : PermissionType.ALLOW)
                    .permissionLevel(memberItem.getPermissionLevel())
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetUserEmail(memberItem.getTargetUserEmail())
                    .targetOrgCode(requestDto.getTargetOrgCode())
                    .documentFileHash(requestDto.getDocumentFileHash())
                    .folderId(requestDto.getFolderId())
                    .expiresAt(memberItem.getExpiresAt())
                    .note(itemNote)
                    .build();

            createdPermissions.add(grantPermission(singleRequest, grantedByUserId));
        }

        return createdPermissions;
    }

    @Override
    @Transactional
    public PermissionResponseDto updatePermission(Long permissionId, com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto requestDto, String updatedByUserId) {
        log.info("Updating permission ID: {} by user: {}", permissionId, updatedByUserId);

        if (requestDto == null || requestDto.getPermissionLevel() == null) {
            throw new IllegalArgumentException("Permission level is required");
        }

        if (requestDto.getExpiresAt() != null && requestDto.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Expiration date cannot be in the past");
        }

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        if (permission.isRevoked()) {
            throw new IllegalArgumentException("Cannot update a revoked permission");
        }

        UserEntity updatedBy = userRepository.findById(updatedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + updatedByUserId));

        // Access-Administration Enforcement
        if (permission.getFolder() != null) {
            if (!evaluatorService.canAdministerFolderPermissions(updatedBy, permission.getFolder())) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer folder permissions");
            }
        }
        DocumentEntity doc = permission.getDocument();
        if (doc == null && permission.getDocumentFileHash() != null) {
            doc = resolveDocumentEntity(null, permission.getDocumentFileHash(), permission.getGrantedBy());
            if (doc != null) {
                permission.setDocument(doc);
            }
        }
        if (doc != null) {
            if (!evaluatorService.canAdministerDocumentPermissions(updatedBy, doc)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer document permissions");
            }
        }

        FolderEntity targetFolder = permission.getFolder();
        DocumentEntity targetDoc = permission.getDocument();

        // Provenance Override Enforcement (Phase 3B.3)
        if (!evaluatorService.canActorOverrideGrant(updatedBy, permission, targetFolder, targetDoc)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User does not have authority to modify permission created with tier " 
                    + permission.getGrantorAuthorityTier());
        }

        PermissionLevel oldLevel = permission.getPermissionLevel();
        if (requestDto.getPermissionLevel() != null && requestDto.getPermissionLevel() != oldLevel) {
            PermissionLevel delegationCeiling = evaluatorService.resolveDelegationCeiling(updatedBy, targetFolder, targetDoc);
            if (delegationCeiling == null || requestDto.getPermissionLevel().ordinal() > delegationCeiling.ordinal()) {
                throw new org.springframework.security.access.AccessDeniedException("Requested permission level " 
                        + requestDto.getPermissionLevel() + " exceeds updater delegation ceiling of " + delegationCeiling);
            }
            permission.setPermissionLevel(requestDto.getPermissionLevel());
        }
        permission.setExpiresAt(requestDto.getExpiresAt());

        // Recalculate hash for tamper proof update record
        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);
        permission.setBlockchainStatus(BlockchainStatus.PENDING);

        PermissionEntity savedPermission = permissionRepository.save(permission);

        // Audit Log
        String targetType = permission.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null ? permission.getDocument().getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("UPDATE_PERMISSION")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(permission.getDocumentFileHash())
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null && permission.getDocument().getFolder() != null ? permission.getDocument().getFolder().getId() : null))
                .workspaceId(permission.getWorkspaceId())
                .groupId(permission.getTargetGroup() != null ? permission.getTargetGroup().getId() : null)
                .collaborationId(permission.getCollaboration() != null ? permission.getCollaboration().getId() : null)
                .spaceType(permission.getFolder() != null ? (permission.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (permission.getDocument() != null ? (permission.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(permission.getFolder() != null && permission.getFolder().getOwnerOrg() != null ? permission.getFolder().getOwnerOrg().getOrgCode() : (permission.getDocument() != null && permission.getDocument().getOwnerOrg() != null ? permission.getDocument().getOwnerOrg().getOrgCode() : permission.getTargetOrgCode()))
                .actorUserId(updatedBy != null ? updatedBy.getId() : null)
                .grantedBy(updatedBy)
                .grantedToEmail(permission.getTargetUserEmail())
                .grantedToOrgCode(permission.getTargetOrgCode())
                .permissionLevel(requestDto.getPermissionLevel())
                .previousValue(oldLevel != null ? oldLevel.name() : null)
                .newValue(requestDto.getPermissionLevel() != null ? requestDto.getPermissionLevel().name() : null)
                .validFrom(permission.getValidFrom())
                .expiresAt(requestDto.getExpiresAt())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("UPDATE")
                .recordHash(recordHash)
                .timestamp(Instant.now())
                .build();

        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Invalidate Redis Cache for target user or org members
        evictCacheForPermissionTarget(savedPermission);

        // Trigger Notification
        String resourceName = savedPermission.getFolder() != null ? savedPermission.getFolder().getName() 
                : (savedPermission.getDocument() != null ? savedPermission.getDocument().getFileName() : "Resource");
        sendPermissionNotification(
                savedPermission, 
                updatedBy, 
                NotificationType.PERMISSION_UPDATED, 
                "Permission Updated", 
                String.format("Your access to %s was updated to %s.", resourceName, savedPermission.getPermissionLevel())
        );

        return PermissionMapper.toResponseDto(savedPermission);
    }

    @Override
    @Transactional
    public PermissionResponseDto revokePermission(Long permissionId, String revokedByUserId) {
        log.info("Revoking permission ID: {} by user: {}", permissionId, revokedByUserId);

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        if (permission.isRevoked()) {
            log.warn("Permission ID {} is already revoked", permissionId);
            return PermissionMapper.toResponseDto(permission);
        }

        boolean isSystemActor = "SYSTEM_SCHEDULER".equalsIgnoreCase(revokedByUserId);

        UserEntity revokedBy = null;
        if (!isSystemActor) {
            revokedBy = userRepository.findById(revokedByUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + revokedByUserId));

            boolean isCollabAuthorized = false;
            if (permission.getCollaboration() != null) {
                CollaborationEntity collab = permission.getCollaboration();
                String ownerCode = collab.getOwnerOrganization() != null ? collab.getOwnerOrganization().getOrgCode() : null;
                String partnerCode = collab.getPartnerOrganization() != null ? collab.getPartnerOrganization().getOrgCode() : null;
                isCollabAuthorized = revokedBy.getRole() == Role.ROLE_SUPER_ADMIN
                        || (ownerCode != null && evaluatorService.isUserAdminOfOrg(revokedBy, ownerCode))
                        || (partnerCode != null && evaluatorService.isUserAdminOfOrg(revokedBy, partnerCode));
            }

            // Access-Administration Enforcement
            if (!isCollabAuthorized && permission.getFolder() != null) {
                if (!evaluatorService.canAdministerFolderPermissions(revokedBy, permission.getFolder())) {
                    throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer folder permissions");
                }
            }
            DocumentEntity doc = permission.getDocument();
            if (doc == null && permission.getDocumentFileHash() != null) {
                doc = resolveDocumentEntity(null, permission.getDocumentFileHash(), permission.getGrantedBy());
                if (doc != null) {
                    permission.setDocument(doc);
                }
            }
            if (!isCollabAuthorized && doc != null) {
                if (!evaluatorService.canAdministerDocumentPermissions(revokedBy, doc)) {
                    throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer document permissions");
                }
            }

            // Provenance Override Enforcement (Phase 3B.3)
            if (!isCollabAuthorized && !evaluatorService.canActorOverrideGrant(revokedBy, permission, permission.getFolder(), doc)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "User does not have authority to revoke permission created with tier " 
                        + permission.getGrantorAuthorityTier());
            }
        }

        Instant now = Instant.now();
        permission.setRevoked(true);
        permission.setSuspended(false);
        permission.setRevokedAt(now);
        permission.setRevokedBy(revokedBy);

        // Recalculate hash for tamper proof revocation record
        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);
        permission.setBlockchainStatus(BlockchainStatus.PENDING);

        PermissionEntity savedPermission = permissionRepository.save(permission);

        if (permission.getTargetGroup() != null) {
            if (permission.getFolder() != null) {
                List<PermissionEntity> siblings = permissionRepository
                        .findByTargetGroup_IdAndFolder_IdAndRevokedFalse(
                                permission.getTargetGroup().getId(), permission.getFolder().getId()).stream()
                        .filter(p -> p.getRecipientType() == permission.getRecipientType())
                        .collect(Collectors.toList());
                for (PermissionEntity sibling : siblings) {
                    if (!sibling.getId().equals(permission.getId())) {
                        sibling.setRevoked(true);
                        sibling.setSuspended(false);
                        sibling.setRevokedAt(now);
                        sibling.setRevokedBy(revokedBy);
                        sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                        sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                        permissionRepository.save(sibling);
                    }
                }
            } else if (permission.getDocument() != null) {
                List<PermissionEntity> siblings = permissionRepository
                        .findByTargetGroup_IdAndDocument_IdAndRevokedFalse(
                                permission.getTargetGroup().getId(), permission.getDocument().getId()).stream()
                        .filter(p -> p.getRecipientType() == permission.getRecipientType())
                        .collect(Collectors.toList());
                for (PermissionEntity sibling : siblings) {
                    if (!sibling.getId().equals(permission.getId())) {
                        sibling.setRevoked(true);
                        sibling.setSuspended(false);
                        sibling.setRevokedAt(now);
                        sibling.setRevokedBy(revokedBy);
                        sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                        sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                        permissionRepository.save(sibling);
                    }
                }
            }
        } else if (permission.getTargetUserEmail() != null && !permission.getTargetUserEmail().isBlank()) {
            String email = permission.getTargetUserEmail().trim();
            if (permission.getFolder() != null) {
                List<PermissionEntity> siblings = permissionRepository
                        .findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse(
                                email, permission.getFolder().getId());
                for (PermissionEntity sibling : siblings) {
                    if (!sibling.getId().equals(permission.getId())) {
                        sibling.setRevoked(true);
                        sibling.setSuspended(false);
                        sibling.setRevokedAt(now);
                        sibling.setRevokedBy(revokedBy);
                        sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                        sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                        permissionRepository.save(sibling);
                    }
                }
            } else if (permission.getDocument() != null) {
                List<PermissionEntity> siblings = permissionRepository
                        .findByTargetUserEmailIgnoreCaseAndDocument_IdAndRevokedFalse(
                                email, permission.getDocument().getId());
                for (PermissionEntity sibling : siblings) {
                    if (!sibling.getId().equals(permission.getId())) {
                        sibling.setRevoked(true);
                        sibling.setSuspended(false);
                        sibling.setRevokedAt(now);
                        sibling.setRevokedBy(revokedBy);
                        sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                        sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                        permissionRepository.save(sibling);
                    }
                }
            }
        } else if (permission.getRecipientType() == RecipientType.ORGANIZATION
                && permission.getTargetOrgCode() != null && !permission.getTargetOrgCode().isBlank()) {
            String orgCode = permission.getTargetOrgCode().trim();
            if (permission.getFolder() != null) {
                List<PermissionEntity> siblings = permissionRepository
                        .findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                                RecipientType.ORGANIZATION, orgCode, permission.getFolder().getId());
                for (PermissionEntity sibling : siblings) {
                    if (!sibling.getId().equals(permission.getId())) {
                        sibling.setRevoked(true);
                        sibling.setSuspended(false);
                        sibling.setRevokedAt(now);
                        sibling.setRevokedBy(revokedBy);
                        sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                        sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                        permissionRepository.save(sibling);
                    }
                }
            } else if (permission.getDocument() != null || (permission.getDocumentFileHash() != null && !permission.getDocumentFileHash().isBlank())) {
                List<PermissionEntity> siblings = new java.util.ArrayList<>();
                if (permission.getDocument() != null) {
                    siblings.addAll(permissionRepository
                            .findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocument_IdAndRevokedFalse(
                                    RecipientType.ORGANIZATION, orgCode, permission.getDocument().getId()));
                }
                String hash = permission.getDocument() != null && permission.getDocument().getFileHash() != null
                        ? permission.getDocument().getFileHash()
                        : permission.getDocumentFileHash();
                if (hash != null && !hash.isBlank()) {
                    List<PermissionEntity> byHash = permissionRepository
                            .findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocumentFileHashAndRevokedFalse(
                                    RecipientType.ORGANIZATION, orgCode, hash.trim());
                    for (PermissionEntity p : byHash) {
                        if (siblings.stream().noneMatch(s -> s.getId() != null && s.getId().equals(p.getId()))) {
                            siblings.add(p);
                        }
                    }
                }
                for (PermissionEntity sibling : siblings) {
                    if (!sibling.getId().equals(permission.getId())) {
                        sibling.setRevoked(true);
                        sibling.setSuspended(false);
                        sibling.setRevokedAt(now);
                        sibling.setRevokedBy(revokedBy);
                        sibling.setRecordHash(PermissionHashUtils.calculateRecordHash(sibling));
                        sibling.setBlockchainStatus(BlockchainStatus.PENDING);
                        permissionRepository.save(sibling);
                    }
                }
            }
        }


        // Audit Log
        String targetType = permission.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null ? permission.getDocument().getId() : null);

        boolean isBoundary = permission.getPermissionType() == PermissionType.BOUNDARY;
        String actionName = isBoundary ? "TEAM_DEALLOCATE" : "REVOKE_PERMISSION";
        String details = isBoundary
                ? "Deallocated team '" + (permission.getTargetGroup() != null ? permission.getTargetGroup().getName() : "") + "' from workspace folder"
                : null;
        String previousValue = permission.getPermissionLevel() != null ? permission.getPermissionLevel().name() : null;

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action(actionName)
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(permission.getDocumentFileHash())
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null && permission.getDocument().getFolder() != null ? permission.getDocument().getFolder().getId() : null))
                .workspaceId(permission.getWorkspaceId())
                .groupId(permission.getTargetGroup() != null ? permission.getTargetGroup().getId() : null)
                .collaborationId(permission.getCollaboration() != null ? permission.getCollaboration().getId() : null)
                .spaceType(permission.getFolder() != null ? (permission.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (permission.getDocument() != null ? (permission.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(permission.getFolder() != null && permission.getFolder().getOwnerOrg() != null ? permission.getFolder().getOwnerOrg().getOrgCode() : (permission.getDocument() != null && permission.getDocument().getOwnerOrg() != null ? permission.getDocument().getOwnerOrg().getOrgCode() : permission.getTargetOrgCode()))
                .actorUserId(isSystemActor ? revokedByUserId : (revokedBy != null ? revokedBy.getId() : null))
                .grantedBy(revokedBy)
                .grantedToEmail(permission.getTargetUserEmail())
                .grantedToOrgCode(permission.getTargetOrgCode())
                .permissionLevel(permission.getPermissionLevel())
                .previousValue(previousValue)
                .details(details)
                .validFrom(permission.getValidFrom())
                .expiresAt(permission.getExpiresAt())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(now)
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("REVOKE")
                .recordHash(recordHash)
                .timestamp(now)
                .build();

        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Invalidate Redis Cache for target user or org members
        evictCacheForPermissionTarget(savedPermission);

        // Trigger Notification
        String resourceName = savedPermission.getFolder() != null ? savedPermission.getFolder().getName() 
                : (savedPermission.getDocument() != null ? savedPermission.getDocument().getFileName() : "Resource");
        sendPermissionNotification(
                savedPermission, 
                revokedBy, 
                NotificationType.PERMISSION_REVOKED, 
                "Permission Revoked", 
                String.format("Your access to %s has been revoked.", resourceName)
        );

        return PermissionMapper.toResponseDto(savedPermission);
    }

    @Override
    @Transactional
    public PermissionResponseDto activatePermission(Long permissionId, String activatedByUserId) {
        log.info("Activating permission ID: {} by user: {}", permissionId, activatedByUserId);

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        if (!permission.isRevoked() && !permission.isSuspended()) {
            log.warn("Permission ID {} is already active — no action needed", permissionId);
            return PermissionMapper.toResponseDto(permission);
        }

        UserEntity activatedBy = userRepository.findById(activatedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + activatedByUserId));

        // Access-Administration Enforcement
        if (permission.getFolder() != null) {
            if (!evaluatorService.canAdministerFolderPermissions(activatedBy, permission.getFolder())) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer folder permissions");
            }
        }
        DocumentEntity doc = permission.getDocument();
        if (doc == null && permission.getDocumentFileHash() != null) {
            doc = resolveDocumentEntity(null, permission.getDocumentFileHash(), permission.getGrantedBy());
            if (doc != null) {
                permission.setDocument(doc);
            }
        }
        if (doc != null) {
            if (!evaluatorService.canAdministerDocumentPermissions(activatedBy, doc)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer document permissions");
            }
        }

        // Provenance Override Enforcement (Phase 3B.3)
        // Check A: Caller must be allowed to override original grantor provenance
        if (!evaluatorService.canActorOverrideGrant(activatedBy, permission, permission.getFolder(), doc)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User does not have authority to activate permission created with tier " 
                    + permission.getGrantorAuthorityTier());
        }

        // Check B: Caller must be allowed to override the inactivating authority
        if (permission.getRevokedBy() != null) {
            if (!evaluatorService.canActorOverrideInactivator(activatedBy, permission.getRevokedBy(), permission, permission.getFolder(), doc)) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "User does not have authority to activate permission suspended/revoked by user " 
                        + permission.getRevokedBy().getEmail());
            }
        }

        Instant now = Instant.now();
        permission.setRevoked(false);
        permission.setSuspended(false);
        permission.setRevokedAt(null);
        permission.setRevokedBy(null);

        // Recalculate tamper-proof record hash
        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);
        permission.setBlockchainStatus(BlockchainStatus.PENDING);

        PermissionEntity savedPermission = permissionRepository.save(permission);

        // Audit Log
        String targetType = permission.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = permission.getFolder() != null ? permission.getFolder().getId() :
                (permission.getDocument() != null ? permission.getDocument().getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("ACTIVATE_PERMISSION")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(permission.getDocumentFileHash())
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null && permission.getDocument().getFolder() != null ? permission.getDocument().getFolder().getId() : null))
                .workspaceId(permission.getWorkspaceId())
                .groupId(permission.getTargetGroup() != null ? permission.getTargetGroup().getId() : null)
                .collaborationId(permission.getCollaboration() != null ? permission.getCollaboration().getId() : null)
                .spaceType(permission.getFolder() != null ? (permission.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (permission.getDocument() != null ? (permission.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(permission.getFolder() != null && permission.getFolder().getOwnerOrg() != null ? permission.getFolder().getOwnerOrg().getOrgCode() : (permission.getDocument() != null && permission.getDocument().getOwnerOrg() != null ? permission.getDocument().getOwnerOrg().getOrgCode() : permission.getTargetOrgCode()))
                .actorUserId(activatedBy != null ? activatedBy.getId() : null)
                .grantedBy(activatedBy)
                .grantedToEmail(permission.getTargetUserEmail())
                .grantedToOrgCode(permission.getTargetOrgCode())
                .permissionLevel(permission.getPermissionLevel())
                .validFrom(permission.getValidFrom())
                .expiresAt(permission.getExpiresAt())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(now)
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("ACTIVATE")
                .recordHash(recordHash)
                .timestamp(now)
                .build();
        eventPublisher.publishPermissionChangeEvent(changeEvent);

        // Invalidate Redis Cache
        evictCacheForPermissionTarget(savedPermission);

        return PermissionMapper.toResponseDto(savedPermission);
    }

    @Override
    @Transactional
    public PermissionResponseDto suspendPermission(Long permissionId, String suspendedByUserId) {
        log.info("Suspending permission ID: {} by user: {}", permissionId, suspendedByUserId);

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        if (permission.isSuspended()) {
            log.warn("Permission ID {} is already suspended", permissionId);
            return PermissionMapper.toResponseDto(permission);
        }

        UserEntity suspendedBy = userRepository.findById(suspendedByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + suspendedByUserId));

        // Access-Administration Enforcement
        if (permission.getFolder() != null) {
            if (!evaluatorService.canAdministerFolderPermissions(suspendedBy, permission.getFolder())) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer folder permissions");
            }
        }
        DocumentEntity doc = permission.getDocument();
        if (doc == null && permission.getDocumentFileHash() != null) {
            doc = resolveDocumentEntity(null, permission.getDocumentFileHash(), permission.getGrantedBy());
            if (doc != null) {
                permission.setDocument(doc);
            }
        }
        if (doc != null) {
            if (!evaluatorService.canAdministerDocumentPermissions(suspendedBy, doc)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to administer document permissions");
            }
        }

        // Provenance Override Enforcement (Phase 3B.3)
        if (!evaluatorService.canActorOverrideGrant(suspendedBy, permission, permission.getFolder(), doc)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "User does not have authority to suspend permission created with tier " 
                    + permission.getGrantorAuthorityTier());
        }

        Instant now = Instant.now();
        permission.setSuspended(true);
        permission.setRevoked(false);
        permission.setRevokedBy(suspendedBy);

        String recordHash = PermissionHashUtils.calculateRecordHash(permission);
        permission.setRecordHash(recordHash);
        permission.setBlockchainStatus(BlockchainStatus.PENDING);

        PermissionEntity savedPermission = permissionRepository.save(permission);

        // Audit Log
        String targetType = permission.getFolder() != null ? "FOLDER" : "DOCUMENT";
        Long targetId = permission.getFolder() != null ? permission.getFolder().getId() :
                (permission.getDocument() != null ? permission.getDocument().getId() : null);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("SUSPEND_PERMISSION")
                .targetType(targetType)
                .targetId(targetId)
                .targetFileHash(permission.getDocumentFileHash())
                .folderId(permission.getFolder() != null ? permission.getFolder().getId() : (permission.getDocument() != null && permission.getDocument().getFolder() != null ? permission.getDocument().getFolder().getId() : null))
                .workspaceId(permission.getWorkspaceId())
                .groupId(permission.getTargetGroup() != null ? permission.getTargetGroup().getId() : null)
                .collaborationId(permission.getCollaboration() != null ? permission.getCollaboration().getId() : null)
                .spaceType(permission.getFolder() != null ? (permission.getFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : (permission.getDocument() != null ? (permission.getDocument().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL) : null))
                .orgCode(permission.getFolder() != null && permission.getFolder().getOwnerOrg() != null ? permission.getFolder().getOwnerOrg().getOrgCode() : (permission.getDocument() != null && permission.getDocument().getOwnerOrg() != null ? permission.getDocument().getOwnerOrg().getOrgCode() : permission.getTargetOrgCode()))
                .actorUserId(suspendedBy != null ? suspendedBy.getId() : null)
                .grantedBy(suspendedBy)
                .grantedToEmail(permission.getTargetUserEmail())
                .grantedToOrgCode(permission.getTargetOrgCode())
                .permissionLevel(permission.getPermissionLevel())
                .validFrom(permission.getValidFrom())
                .expiresAt(permission.getExpiresAt())
                .recordHash(recordHash)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(now)
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        // Kafka Event
        PermissionChangeEvent changeEvent = PermissionChangeEvent.builder()
                .permissionId(savedPermission.getId())
                .eventType("SUSPEND")
                .recordHash(recordHash)
                .timestamp(now)
                .build();
        eventPublisher.publishPermissionChangeEvent(changeEvent);

        evictCacheForPermissionTarget(savedPermission);

        return PermissionMapper.toResponseDto(savedPermission);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponseDto getPermissionById(Long permissionId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view permission");
        }
        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        FolderEntity targetFolder = permission.getFolder();
        DocumentEntity targetDoc = permission.getDocument();
        if (targetDoc == null && permission.getDocumentFileHash() != null && !permission.getDocumentFileHash().isBlank()) {
            targetDoc = resolveDocumentEntity(null, permission.getDocumentFileHash(), permission.getGrantedBy());
        }
        if (targetFolder == null && targetDoc != null && targetDoc.getFolder() != null) {
            targetFolder = targetDoc.getFolder();
        }

        boolean isAuthorized = currentUser.getRole() == Role.ROLE_SUPER_ADMIN;

        if (!isAuthorized && targetFolder != null) {
            isAuthorized = evaluatorService.canAdministerFolderPermissions(currentUser, targetFolder);
        }
        if (!isAuthorized && targetDoc != null) {
            isAuthorized = evaluatorService.canAdministerDocumentPermissions(currentUser, targetDoc);
        }

        if (!isAuthorized) {
            throw new org.springframework.security.access.AccessDeniedException("User does not have authority to view this permission");
        }

        return PermissionMapper.toResponseDto(permission);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getActivePermissionsForDocument(Long documentId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view permissions");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("Document ID is required");
        }
        DocumentEntity doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));

        if (!evaluatorService.canAdministerDocumentPermissions(currentUser, doc)) {
            throw new org.springframework.security.access.AccessDeniedException("User does not have authority to view permissions for this document");
        }

        return permissionRepository.findByDocument_IdAndRevokedFalse(documentId).stream()
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getActivePermissionsForFolder(Long folderId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view permissions");
        }
        if (folderId == null) {
            throw new IllegalArgumentException("Folder ID is required");
        }
        FolderEntity folder = folderRepository.findById(folderId)
                .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + folderId));

        if (!evaluatorService.canAdministerFolderPermissions(currentUser, folder)) {
            throw new org.springframework.security.access.AccessDeniedException("User does not have authority to view permissions for this folder");
        }

        return permissionRepository.findByFolder_IdAndRevokedFalse(folderId).stream()
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getActivePermissionsForGroup(Long groupId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view permissions");
        }
        if (groupId == null) {
            return List.of();
        }
        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));

        boolean isAuthorized = currentUser != null && (currentUser.getRole() == Role.ROLE_SUPER_ADMIN
                || (group.getOrganization() != null && evaluatorService.isUserAdminOfOrg(currentUser, group.getOrganization().getOrgCode())));

        if (!isAuthorized && currentUser != null && group.getOrganization() != null) {
            boolean isGroupActive = group.getStatus() == null || group.getStatus() == GroupStatus.ACTIVE;
            if (isGroupActive) {
                boolean isManager = userGroupRepository.findByUser_IdAndGroup_Id(currentUser.getId(), groupId)
                        .map(ug -> ug.getRole() == GroupMemberRole.MANAGER)
                        .orElse(false);
                if (isManager) {
                    isAuthorized = userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                            currentUser.getId(), group.getOrganization().getOrgCode(), MembershipStatus.ACTIVE);
                }
            }
        }

        if (!isAuthorized) {
            throw new org.springframework.security.access.AccessDeniedException("User does not have authority to view permissions for this group");
        }

        return permissionRepository.findByTargetGroup_IdAndRevokedFalse(groupId).stream()
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getPermissionsForTarget(String targetUserEmail, String targetOrgCode, Long targetGroupId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view permissions");
        }

        if (targetGroupId != null) {
            return getActivePermissionsForGroup(targetGroupId, currentUser);
        }

        boolean isSuperAdmin = currentUser.getRole() == Role.ROLE_SUPER_ADMIN;

        // Authorize targetOrgCode query
        if (targetOrgCode != null && !targetOrgCode.isBlank()) {
            boolean isOrgAdmin = evaluatorService.isUserAdminOfOrg(currentUser, targetOrgCode);
            if (!isSuperAdmin && !isOrgAdmin) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to query permissions for organization: " + targetOrgCode);
            }
        }

        // Authorize targetUserEmail query
        if (targetUserEmail != null && !targetUserEmail.isBlank()) {
            boolean isSelf = targetUserEmail.equalsIgnoreCase(currentUser.getEmail());
            boolean isOrgAdminOfTarget = targetOrgCode != null && evaluatorService.isUserAdminOfOrg(currentUser, targetOrgCode)
                    && userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(targetUserEmail, targetOrgCode, MembershipStatus.ACTIVE);
            if (!isSuperAdmin && !isSelf && !isOrgAdminOfTarget) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to query permissions for user: " + targetUserEmail);
            }
        }

        boolean hasEmail = targetUserEmail != null && !targetUserEmail.isBlank();
        boolean hasOrg = targetOrgCode != null && !targetOrgCode.isBlank();

        List<PermissionEntity> permissions;
        if (hasEmail && hasOrg) {
            permissions = permissionRepository.findPermissionsForUserInOrg(targetUserEmail, targetOrgCode);
        } else if (hasEmail) {
            permissions = permissionRepository.findByTargetUserEmail(targetUserEmail);
        } else if (hasOrg) {
            permissions = permissionRepository.findByTargetOrgCode(targetOrgCode);
        } else {
            throw new IllegalArgumentException("At least one target parameter (email, orgCode, or groupId) must be provided");
        }

        return permissions.stream()
                .filter(p -> !p.isRevoked())
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    private static final Comparator<PermissionEntity> DETERMINISTIC_PERMISSION_COMPARATOR = Comparator
            .comparing(PermissionEntity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(PermissionEntity::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private <T> List<List<T>> partitionCollection(Collection<T> collection, int batchSize) {
        if (collection == null || collection.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> list = new ArrayList<>(collection);
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    private String formatUserDisplayName(UserEntity u) {
        if (u == null) return null;
        String first = u.getFirstName() != null ? u.getFirstName().trim() : "";
        String last = u.getLastName() != null ? u.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return !full.isEmpty() ? full : u.getEmail();
    }

    private Map<String, UserEntity> preloadGrantorUsers(Set<String> grantorUserIds, List<PermissionEntity> perms) {
        Map<String, UserEntity> map = new HashMap<>();
        if (perms != null) {
            for (PermissionEntity p : perms) {
                if (p.getGrantedBy() != null && p.getGrantedBy().getId() != null) {
                    map.put(p.getGrantedBy().getId(), p.getGrantedBy());
                }
            }
        }
        if (grantorUserIds != null && !grantorUserIds.isEmpty() && userRepository != null) {
            try {
                for (List<String> chunk : partitionCollection(grantorUserIds, 500)) {
                    userRepository.findAllById(chunk).forEach(u -> {
                        if (u.getId() != null) {
                            map.put(u.getId(), u);
                        }
                    });
                }
            } catch (Exception e) {
                log.debug("Bulk grantor lookup fallback: {}", e.getMessage());
            }
        }
        return map;
    }

    private Map<String, String> preloadUserDisplayNamesByEmail(Set<String> emails) {
        if (emails == null || emails.isEmpty() || userRepository == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        try {
            for (List<String> chunk : partitionCollection(emails, 500)) {
                List<UserEntity> users = userRepository.findByEmailInIgnoreCase(chunk);
                if (users != null) {
                    for (UserEntity u : users) {
                        if (u.getEmail() != null) {
                            map.put(u.getEmail().toLowerCase(), formatUserDisplayName(u));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Bulk user by email lookup error: {}", e.getMessage());
        }
        return map;
    }

    private Map<String, String> preloadOrgNamesByOrgCode(Set<String> orgCodes) {
        if (orgCodes == null || orgCodes.isEmpty() || organizationRepository == null) {
            return Collections.emptyMap();
        }
        Map<String, String> map = new HashMap<>();
        try {
            for (List<String> chunk : partitionCollection(orgCodes, 500)) {
                List<OrganizationEntity> orgs = organizationRepository.findAllById(chunk);
                if (orgs != null) {
                    for (OrganizationEntity org : orgs) {
                        if (org.getOrgCode() != null) {
                            map.put(org.getOrgCode().toUpperCase(), org.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Bulk org lookup error: {}", e.getMessage());
        }
        return map;
    }

    private Set<String> preloadActiveUserOrgCodes(UserEntity currentUser, Set<String> orgCodes) {
        if (currentUser == null || userOrganizationRepository == null || orgCodes == null || orgCodes.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> activeOrgs = new HashSet<>();
        try {
            for (List<String> chunk : partitionCollection(orgCodes, 500)) {
                if (currentUser.getId() != null) {
                    List<UserOrganizationEntity> list = userOrganizationRepository
                            .findByUser_IdAndOrganization_OrgCodeInAndStatus(currentUser.getId(), chunk, MembershipStatus.ACTIVE);
                    if (list != null) {
                        for (UserOrganizationEntity uo : list) {
                            if (uo.getOrganization() != null && uo.getOrganization().getOrgCode() != null) {
                                activeOrgs.add(uo.getOrganization().getOrgCode().toUpperCase());
                            }
                        }
                    }
                }
                if (currentUser.getEmail() != null) {
                    List<UserOrganizationEntity> list = userOrganizationRepository
                            .findByUser_EmailAndOrganization_OrgCodeInAndStatus(currentUser.getEmail(), chunk, MembershipStatus.ACTIVE);
                    if (list != null) {
                        for (UserOrganizationEntity uo : list) {
                            if (uo.getOrganization() != null && uo.getOrganization().getOrgCode() != null) {
                                activeOrgs.add(uo.getOrganization().getOrgCode().toUpperCase());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Bulk user org lookup error: {}", e.getMessage());
        }
        return activeOrgs;
    }

    private Map<Long, ProjectWorkspaceEntity> preloadWorkspacesByRootFolder(Set<Long> rootFolderCandidateIds) {
        if (projectWorkspaceRepository == null || rootFolderCandidateIds == null || rootFolderCandidateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ProjectWorkspaceEntity> map = new HashMap<>();
        try {
            for (List<Long> chunk : partitionCollection(rootFolderCandidateIds, 500)) {
                List<ProjectWorkspaceEntity> list = projectWorkspaceRepository.findByRootFolder_IdIn(chunk);
                if (list != null) {
                    for (ProjectWorkspaceEntity ws : list) {
                        if (ws.getRootFolder() != null && ws.getRootFolder().getId() != null) {
                            map.put(ws.getRootFolder().getId(), ws);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Bulk root folder workspace lookup error: {}", e.getMessage());
        }
        return map;
    }

    private Map<Long, String> preloadWorkspaceNamesById(Set<Long> wsIds) {
        if (projectWorkspaceRepository == null || wsIds == null || wsIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> map = new HashMap<>();
        try {
            for (List<Long> chunk : partitionCollection(wsIds, 500)) {
                List<ProjectWorkspaceEntity> list = projectWorkspaceRepository.findAllById(chunk);
                if (list != null) {
                    for (ProjectWorkspaceEntity ws : list) {
                        if (ws.getId() != null) {
                            map.put(ws.getId(), ws.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Bulk workspace by id error: {}", e.getMessage());
        }
        return map;
    }

    private Set<String> resolveActiveUserOrgCodes(UserEntity currentUser) {
        if (currentUser == null || currentUser.getId() == null || userOrganizationRepository == null) {
            return Collections.emptySet();
        }
        try {
            List<UserOrganizationEntity> list = userOrganizationRepository.findByUser_IdAndStatus(currentUser.getId(), MembershipStatus.ACTIVE);
            if (list == null || list.isEmpty()) {
                return Collections.emptySet();
            }
            return list.stream()
                    .filter(uo -> uo.getOrganization() != null && uo.getOrganization().getOrgCode() != null)
                    .map(uo -> uo.getOrganization().getOrgCode().toUpperCase())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.debug("Error resolving active user org codes: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<Long> resolveActiveUserTeamIds(UserEntity currentUser, Set<String> activeOrgCodes) {
        if (currentUser == null || currentUser.getId() == null || userGroupRepository == null || activeOrgCodes == null || activeOrgCodes.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<UserGroupEntity> list = userGroupRepository.findByUser_Id(currentUser.getId());
            if (list == null || list.isEmpty()) {
                return Collections.emptySet();
            }
            return list.stream()
                    .filter(ug -> ug.getGroup() != null && ug.getGroup().getId() != null)
                    .filter(ug -> ug.getGroup().getOrganization() != null && ug.getGroup().getOrganization().getOrgCode() != null
                            && activeOrgCodes.contains(ug.getGroup().getOrganization().getOrgCode().toUpperCase()))
                    .map(ug -> ug.getGroup().getId())
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.debug("Error resolving active user team IDs: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    private String resolveSourceLabel(PermissionEntity perm) {
        if (perm == null) {
            return "DIRECT SHARE";
        }
        if (perm.getCollaboration() != null) {
            return "COLLABORATION SHARE";
        }
        if (perm.getTargetGroup() != null || perm.getRecipientType() == RecipientType.GROUP) {
            return "TEAM SHARE";
        }
        if (perm.getRecipientType() == RecipientType.ORGANIZATION || (perm.getTargetOrgCode() != null && (perm.getTargetUserEmail() == null || perm.getTargetUserEmail().isBlank()))) {
            return "ORGANIZATION SHARE";
        }
        return "DIRECT SHARE";
    }

    private int getSourcePriority(String sourceLabel) {
        if ("DIRECT SHARE".equals(sourceLabel)) return 4;
        if ("TEAM SHARE".equals(sourceLabel)) return 3;
        if ("COLLABORATION SHARE".equals(sourceLabel)) return 2;
        if ("ORGANIZATION SHARE".equals(sourceLabel)) return 1;
        return 0;
    }

    private PermissionLevel resolveEffectivePermissionLevel(List<PermissionEntity> grants) {
        if (grants == null || grants.isEmpty()) {
            return PermissionLevel.VIEW;
        }
        for (PermissionEntity g : grants) {
            if (g.getPermissionLevel() == PermissionLevel.EDIT) {
                return PermissionLevel.EDIT;
            }
        }
        for (PermissionEntity g : grants) {
            if (g.getPermissionLevel() == PermissionLevel.DOWNLOAD) {
                return PermissionLevel.DOWNLOAD;
            }
        }
        return PermissionLevel.VIEW;
    }

    private PermissionEntity resolvePrimaryGrant(List<PermissionEntity> grants) {
        if (grants == null || grants.isEmpty()) {
            return null;
        }
        if (grants.size() == 1) {
            return grants.get(0);
        }
        PermissionEntity best = grants.get(0);
        for (int i = 1; i < grants.size(); i++) {
            PermissionEntity cand = grants.get(i);
            int bestLevelOrd = best.getPermissionLevel() != null ? best.getPermissionLevel().ordinal() : -1;
            int candLevelOrd = cand.getPermissionLevel() != null ? cand.getPermissionLevel().ordinal() : -1;
            if (candLevelOrd > bestLevelOrd) {
                best = cand;
            } else if (candLevelOrd == bestLevelOrd) {
                int bestSrcPrio = getSourcePriority(resolveSourceLabel(best));
                int candSrcPrio = getSourcePriority(resolveSourceLabel(cand));
                if (candSrcPrio > bestSrcPrio) {
                    best = cand;
                } else if (candSrcPrio == bestSrcPrio) {
                    if (cand.getCreatedAt() != null && (best.getCreatedAt() == null || cand.getCreatedAt().isAfter(best.getCreatedAt()))) {
                        best = cand;
                    }
                }
            }
        }
        return best;
    }

    private List<String> resolveDistinctSourceLabels(List<PermissionEntity> grants) {
        if (grants == null || grants.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (PermissionEntity g : grants) {
            String label = resolveSourceLabel(g);
            if (!list.contains(label)) {
                list.add(label);
            }
        }
        return list;
    }

    private Instant resolveConsolidatedExpiration(List<PermissionEntity> grants) {
        if (grants == null || grants.isEmpty()) {
            return null;
        }
        for (PermissionEntity g : grants) {
            if (g.getExpiresAt() == null) {
                return null; // Indefinite grant exists
            }
        }
        return grants.stream()
                .map(PermissionEntity::getExpiresAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String resolveRecipientTypeString(PermissionEntity perm) {
        if (perm == null) return "USER";
        if (perm.getCollaboration() != null) return "COLLABORATION";
        if (perm.getTargetGroup() != null || perm.getRecipientType() == RecipientType.GROUP) return "GROUP";
        if (perm.getRecipientType() == RecipientType.ORGANIZATION || (perm.getTargetOrgCode() != null && (perm.getTargetUserEmail() == null || perm.getTargetUserEmail().isBlank()))) return "ORGANIZATION";
        return "USER";
    }

    private String buildSourceDescription(List<PermissionEntity> grants, PermissionEntity primaryGrant,
                                          List<String> sourceLabels, PermissionLevel effectiveLevel,
                                          String grantorDisplayName, String targetGroupName, String targetOrgName, String collaborationName) {
        if (grants == null || grants.isEmpty()) {
            return null;
        }
        if (grants.size() == 1) {
            String label = sourceLabels.isEmpty() ? resolveSourceLabel(primaryGrant) : sourceLabels.get(0);
            if ("COLLABORATION SHARE".equals(label)) {
                return "Shared via collaboration '" + (collaborationName != null ? collaborationName : "Collaboration") + "'";
            } else if ("TEAM SHARE".equals(label)) {
                return "Shared with team '" + (targetGroupName != null ? targetGroupName : "Team") + "'" + (grantorDisplayName != null ? " by " + grantorDisplayName : "");
            } else if ("ORGANIZATION SHARE".equals(label)) {
                return "Shared with organization '" + (targetOrgName != null ? targetOrgName : "Organization") + "'" + (grantorDisplayName != null ? " by " + grantorDisplayName : "");
            } else {
                return "Shared directly with you" + (grantorDisplayName != null ? " by " + grantorDisplayName : "");
            }
        }
        return "Shared via " + String.join(", ", sourceLabels) + " (" + grants.size() + " grants, highest: " + effectiveLevel + ")";
    }

    private String buildSharedByMeSourceDescription(String sourceLabel, String targetUserName, String targetUserEmail,
                                                   String targetGroupName, String targetOrgName, String targetOrgCode, String collaborationName) {
        if ("COLLABORATION SHARE".equals(sourceLabel)) {
            return "Shared via collaboration '" + (collaborationName != null ? collaborationName : "Collaboration") + "'";
        } else if ("TEAM SHARE".equals(sourceLabel)) {
            return "Shared with team '" + (targetGroupName != null ? targetGroupName : "Team") + "'";
        } else if ("ORGANIZATION SHARE".equals(sourceLabel)) {
            return "Shared with organization '" + (targetOrgName != null ? targetOrgName : (targetOrgCode != null ? targetOrgCode : "Organization")) + "'";
        } else {
            return "Shared with " + (targetUserName != null ? targetUserName : (targetUserEmail != null ? targetUserEmail : "user"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SharedWithMeResponseDto getSharedWithMe(UserEntity currentUser) {
        if (currentUser == null) {
            return SharedWithMeResponseDto.builder()
                    .sharedFolders(List.of())
                    .sharedDocuments(List.of())
                    .build();
        }

        String userEmail = currentUser.getEmail();
        Set<String> activeOrgCodes = resolveActiveUserOrgCodes(currentUser);
        Set<Long> activeTeamIds = resolveActiveUserTeamIds(currentUser, activeOrgCodes);

        boolean hasTeams = !activeTeamIds.isEmpty();
        Collection<Long> queryTeamIds = hasTeams ? activeTeamIds : List.of(-1L);

        boolean hasOrgs = !activeOrgCodes.isEmpty();
        Collection<String> queryOrgCodes = hasOrgs ? activeOrgCodes : List.of("__NONE__");

        Instant now = Instant.now();

        // Stage 1: Candidate Fetch (Multi-Channel Explicit Shares)
        List<PermissionEntity> candidatePermissions = null;
        try {
            candidatePermissions = permissionRepository.findSharedWithMeCandidates(
                    userEmail, currentUser.getId(), queryTeamIds, hasTeams, queryOrgCodes, hasOrgs, now);
        } catch (Exception e) {
            log.error("Failed to execute findSharedWithMeCandidates query: {}", e.getMessage(), e);
            throw e;
        }
        if (candidatePermissions == null || candidatePermissions.isEmpty()) {
            return SharedWithMeResponseDto.builder()
                    .sharedFolders(List.of())
                    .sharedDocuments(List.of())
                    .build();
        }

        // Stage 1.1: Security Filtering & Defense-in-Depth
        Set<Long> candidateFolderIds = candidatePermissions.stream()
                .map(PermissionEntity::getFolder)
                .filter(Objects::nonNull)
                .map(FolderEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProjectWorkspaceEntity> wsByRootFolder = preloadWorkspacesByRootFolder(candidateFolderIds);

        List<PermissionEntity> authorizedPerms = new ArrayList<>();
        Set<Long> seenPermissionIds = new HashSet<>();

        for (PermissionEntity perm : candidatePermissions) {
            if (perm == null || perm.getId() == null) {
                continue;
            }
            if (!seenPermissionIds.add(perm.getId())) {
                continue;
            }
            if (perm.isRevoked() || perm.isSuspended() || perm.getPermissionType() != PermissionType.ALLOW) {
                continue;
            }
            if (perm.getExpiresAt() != null && perm.getExpiresAt().isBefore(now)) {
                continue;
            }
            // Exclude self-grants
            if (perm.getGrantedBy() != null && currentUser.getId() != null
                    && currentUser.getId().equals(perm.getGrantedBy().getId())) {
                continue;
            }
            // Exclude share links
            if (perm.getShareLinkToken() != null && !perm.getShareLinkToken().isBlank()) {
                continue;
            }
            // Exclude non-active documents
            if (perm.getDocument() != null && perm.getDocument().getStatus() != DocumentStatus.ACTIVE) {
                continue;
            }
            if (perm.getFolder() == null && perm.getDocument() == null) {
                continue;
            }
            // Exclude workspace root folder allocations (internal allocations with no grantor)
            if (perm.getFolder() != null && perm.getGrantedBy() == null
                    && (wsByRootFolder.containsKey(perm.getFolder().getId())
                        || (projectWorkspaceRepository != null && projectWorkspaceRepository.existsByRootFolder_Id(perm.getFolder().getId())))) {
                continue;
            }
            // Exclude collaboration root containers
            if (perm.getFolder() != null && perm.getCollaboration() != null && perm.getCollaboration().getRootFolder() != null
                    && perm.getFolder().getId().equals(perm.getCollaboration().getRootFolder().getId())) {
                continue;
            }
            // Validate collaboration status
            if (perm.getCollaboration() != null) {
                if (perm.getCollaboration().getStatus() != com.subject77.docshare.modules.collaboration.entities.CollaborationStatus.ACTIVE) {
                    continue;
                }
                if (perm.getCollaboration().getEndsAt() != null && perm.getCollaboration().getEndsAt().isBefore(now)) {
                    continue;
                }
            }

            // Validate explicit recipient channels
            boolean isDirectShare = perm.getTargetUserEmail() != null && perm.getTargetUserEmail().equalsIgnoreCase(userEmail);
            boolean isTeamShare = perm.getTargetGroup() != null && activeTeamIds.contains(perm.getTargetGroup().getId());
            boolean isOrgShare = perm.getRecipientType() == RecipientType.ORGANIZATION
                    && perm.getTargetOrgCode() != null && activeOrgCodes.contains(perm.getTargetOrgCode().toUpperCase());

            // For collaboration shares, enforce participating organization boundary
            if (perm.getCollaboration() != null) {
                com.subject77.docshare.modules.collaboration.entities.CollaborationEntity collab = perm.getCollaboration();
                String ownerOrg = collab.getOwnerOrganization() != null ? collab.getOwnerOrganization().getOrgCode() : null;
                String partnerOrg = collab.getPartnerOrganization() != null ? collab.getPartnerOrganization().getOrgCode() : null;
                if (isOrgShare && perm.getTargetOrgCode() != null) {
                    boolean orgInCollab = (ownerOrg != null && ownerOrg.equalsIgnoreCase(perm.getTargetOrgCode()))
                            || (partnerOrg != null && partnerOrg.equalsIgnoreCase(perm.getTargetOrgCode()));
                    if (!orgInCollab && (ownerOrg != null || partnerOrg != null)) {
                        continue;
                    }
                }
                if (isTeamShare && perm.getTargetGroup() != null && perm.getTargetGroup().getOrganization() != null) {
                    String teamOrg = perm.getTargetGroup().getOrganization().getOrgCode();
                    boolean teamOrgInCollab = (ownerOrg != null && ownerOrg.equalsIgnoreCase(teamOrg))
                            || (partnerOrg != null && partnerOrg.equalsIgnoreCase(teamOrg));
                    if (!teamOrgInCollab && (ownerOrg != null || partnerOrg != null)) {
                        continue;
                    }
                }
            }

            boolean isCollabExplicit = perm.getCollaboration() != null && (isDirectShare || isTeamShare || isOrgShare);

            // USER_ORGANIZATION_MEMBER requires active org membership
            if (isDirectShare && perm.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER && perm.getTargetOrgCode() != null) {
                if (!activeOrgCodes.contains(perm.getTargetOrgCode().toUpperCase())) {
                    continue;
                }
            }

            if (!isDirectShare && !isTeamShare && !isOrgShare && !isCollabExplicit) {
                continue;
            }

            authorizedPerms.add(perm);
        }

        if (authorizedPerms.isEmpty()) {
            return SharedWithMeResponseDto.builder()
                    .sharedFolders(List.of())
                    .sharedDocuments(List.of())
                    .build();
        }

        // Stage 2: Group by Resource for Single-Card Deduplication
        Map<Long, List<PermissionEntity>> folderGrants = new LinkedHashMap<>();
        Map<Long, List<PermissionEntity>> docGrants = new LinkedHashMap<>();

        Set<String> grantorUserIds = new HashSet<>();
        Set<String> targetUserEmails = new HashSet<>();
        Set<String> ownerOrgCodes = new HashSet<>();
        Set<Long> explicitWsIds = new HashSet<>();

        for (PermissionEntity perm : authorizedPerms) {
            if (perm.getGrantedBy() != null && perm.getGrantedBy().getId() != null) {
                grantorUserIds.add(perm.getGrantedBy().getId());
            }
            if (perm.getTargetUserEmail() != null && !perm.getTargetUserEmail().isBlank()) {
                targetUserEmails.add(perm.getTargetUserEmail());
            }
            if (perm.getWorkspaceId() != null) {
                explicitWsIds.add(perm.getWorkspaceId());
            }
            if (perm.getTargetOrgCode() != null && !perm.getTargetOrgCode().isBlank()) {
                ownerOrgCodes.add(perm.getTargetOrgCode());
            }

            if (perm.getFolder() != null) {
                FolderEntity folder = perm.getFolder();
                folderGrants.computeIfAbsent(folder.getId(), k -> new ArrayList<>()).add(perm);
                if (folder.getOwnerOrg() != null && folder.getOwnerOrg().getOrgCode() != null) {
                    ownerOrgCodes.add(folder.getOwnerOrg().getOrgCode());
                }
            } else if (perm.getDocument() != null) {
                DocumentEntity doc = perm.getDocument();
                docGrants.computeIfAbsent(doc.getId(), k -> new ArrayList<>()).add(perm);
                OrganizationEntity ownerOrg = doc.getOwnerOrg();
                if (ownerOrg == null && doc.getFolder() != null) {
                    ownerOrg = doc.getFolder().getOwnerOrg();
                }
                if (ownerOrg != null && ownerOrg.getOrgCode() != null) {
                    ownerOrgCodes.add(ownerOrg.getOrgCode());
                }
            }
        }

        Map<String, UserEntity> grantorUserMap = preloadGrantorUsers(grantorUserIds, authorizedPerms);
        Map<String, String> userNamesByEmail = preloadUserDisplayNamesByEmail(targetUserEmails);
        Map<String, String> orgNamesByCode = preloadOrgNamesByOrgCode(ownerOrgCodes);
        Set<String> userOrgs = preloadActiveUserOrgCodes(currentUser, ownerOrgCodes);

        // Add any workspaces found from root folder lookup to explicitWsIds
        wsByRootFolder.values().forEach(ws -> {
            if (ws.getId() != null) {
                explicitWsIds.add(ws.getId());
            }
        });
        Map<Long, String> wsNamesById = preloadWorkspaceNamesById(explicitWsIds);

        // Stage 3: Assemble Deduplicated DTOs
        List<SharedItemDto> sharedFolders = new ArrayList<>();
        for (Map.Entry<Long, List<PermissionEntity>> entry : folderGrants.entrySet()) {
            List<PermissionEntity> grants = entry.getValue();
            PermissionEntity primaryGrant = resolvePrimaryGrant(grants);
            FolderEntity folder = primaryGrant.getFolder();
            PermissionLevel effectiveLevel = resolveEffectivePermissionLevel(grants);
            List<String> sourceLabels = resolveDistinctSourceLabels(grants);
            String primarySourceLabel = resolveSourceLabel(primaryGrant);

            UserEntity grantor = primaryGrant.getGrantedBy() != null ? grantorUserMap.get(primaryGrant.getGrantedBy().getId()) : null;
            if (grantor == null && primaryGrant.getGrantedBy() != null) {
                grantor = primaryGrant.getGrantedBy();
            }
            String grantedByName = resolveGrantorDisplayName(grantor);

            OrganizationEntity ownerOrg = folder.getOwnerOrg();
            String ownerOrgCode = (ownerOrg != null) ? ownerOrg.getOrgCode() : null;
            String ownerOrgName = (ownerOrg != null && ownerOrg.getName() != null)
                    ? ownerOrg.getName()
                    : (ownerOrgCode != null ? orgNamesByCode.get(ownerOrgCode.toUpperCase()) : null);
            String sourceType = (ownerOrgCode != null) ? "ORGANIZATION" : "PERSONAL";

            boolean external = false;
            if (ownerOrgCode != null) {
                external = !userOrgs.contains(ownerOrgCode.toUpperCase());
            }

            Long wsId = primaryGrant.getWorkspaceId();
            String wsName = null;
            if (wsId == null && folder.getId() != null) {
                ProjectWorkspaceEntity ws = wsByRootFolder.get(folder.getId());
                if (ws != null) {
                    wsId = ws.getId();
                    wsName = ws.getName();
                }
            }
            if (wsId != null && wsName == null) {
                wsName = wsNamesById.get(wsId);
            }

            Instant maxGrantedAt = grants.stream()
                    .map(PermissionEntity::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(primaryGrant.getCreatedAt());

            Instant resolvedExpiresAt = resolveConsolidatedExpiration(grants);

            String targetGroupName = primaryGrant.getTargetGroup() != null ? primaryGrant.getTargetGroup().getName() : null;
            Long targetGroupId = primaryGrant.getTargetGroup() != null ? primaryGrant.getTargetGroup().getId() : null;
            String targetOrgCode = primaryGrant.getTargetOrgCode();
            String targetOrgName = targetOrgCode != null ? orgNamesByCode.getOrDefault(targetOrgCode.toUpperCase(), targetOrgCode) : null;
            Long collaborationId = primaryGrant.getCollaboration() != null ? primaryGrant.getCollaboration().getId() : null;
            String collaborationName = primaryGrant.getCollaboration() != null ? primaryGrant.getCollaboration().getName() : null;

            String recipientType = resolveRecipientTypeString(primaryGrant);
            String sourceDesc = buildSourceDescription(grants, primaryGrant, sourceLabels, effectiveLevel, grantedByName, targetGroupName, targetOrgName, collaborationName);

            SharedItemDto dto = SharedItemDto.builder()
                    .permissionId(primaryGrant.getId())
                    .resourceType("FOLDER")
                    .resourceId(folder.getId())
                    .name(folder.getName())
                    .fileHash(null)
                    .grantedByUserId(grantor != null ? grantor.getId() : (primaryGrant.getGrantedBy() != null ? primaryGrant.getGrantedBy().getId() : null))
                    .grantedByUserEmail(grantor != null ? grantor.getEmail() : (primaryGrant.getGrantedBy() != null ? primaryGrant.getGrantedBy().getEmail() : null))
                    .grantedByName(grantedByName)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(effectiveLevel)
                    .grantedAt(maxGrantedAt)
                    .expiresAt(resolvedExpiresAt)
                    .note(primaryGrant.getNote())
                    .sourceType(sourceType)
                    .ownerOrgCode(ownerOrgCode)
                    .ownerOrgName(ownerOrgName)
                    .workspaceId(wsId)
                    .workspaceName(wsName)
                    .fileSize(null)
                    .contentType(null)
                    .external(external)
                    .sourceLabel(primarySourceLabel)
                    .sourceLabels(sourceLabels)
                    .sourceDescription(sourceDesc)
                    .matchingGrantCount(grants.size())
                    .matchedPermissionIds(grants.stream().map(PermissionEntity::getId).collect(Collectors.toList()))
                    .recipientType(recipientType)
                    .targetUserEmail(primaryGrant.getTargetUserEmail())
                    .targetUserName(primaryGrant.getTargetUserEmail() != null ? userNamesByEmail.get(primaryGrant.getTargetUserEmail().toLowerCase()) : null)
                    .targetGroupId(targetGroupId)
                    .targetGroupName(targetGroupName)
                    .targetOrgCode(targetOrgCode)
                    .targetOrgName(targetOrgName)
                    .collaborationId(collaborationId)
                    .collaborationName(collaborationName)
                    .build();

            sharedFolders.add(dto);
        }

        List<SharedItemDto> sharedDocuments = new ArrayList<>();
        for (Map.Entry<Long, List<PermissionEntity>> entry : docGrants.entrySet()) {
            List<PermissionEntity> grants = entry.getValue();
            PermissionEntity primaryGrant = resolvePrimaryGrant(grants);
            DocumentEntity doc = primaryGrant.getDocument();
            PermissionLevel effectiveLevel = resolveEffectivePermissionLevel(grants);
            List<String> sourceLabels = resolveDistinctSourceLabels(grants);
            String primarySourceLabel = resolveSourceLabel(primaryGrant);

            UserEntity grantor = primaryGrant.getGrantedBy() != null ? grantorUserMap.get(primaryGrant.getGrantedBy().getId()) : null;
            if (grantor == null && primaryGrant.getGrantedBy() != null) {
                grantor = primaryGrant.getGrantedBy();
            }
            String grantedByName = resolveGrantorDisplayName(grantor);

            OrganizationEntity ownerOrg = doc.getOwnerOrg();
            if (ownerOrg == null && doc.getFolder() != null) {
                ownerOrg = doc.getFolder().getOwnerOrg();
            }
            String ownerOrgCode = (ownerOrg != null) ? ownerOrg.getOrgCode() : null;
            String ownerOrgName = (ownerOrg != null && ownerOrg.getName() != null)
                    ? ownerOrg.getName()
                    : (ownerOrgCode != null ? orgNamesByCode.get(ownerOrgCode.toUpperCase()) : null);
            String sourceType = (ownerOrgCode != null) ? "ORGANIZATION" : "PERSONAL";

            boolean external = false;
            if (ownerOrgCode != null) {
                external = !userOrgs.contains(ownerOrgCode.toUpperCase());
            }

            Long wsId = primaryGrant.getWorkspaceId();
            String wsName = null;
            if (wsId != null) {
                wsName = wsNamesById.get(wsId);
            }

            Instant maxGrantedAt = grants.stream()
                    .map(PermissionEntity::getCreatedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(primaryGrant.getCreatedAt());

            Instant resolvedExpiresAt = resolveConsolidatedExpiration(grants);

            String targetGroupName = primaryGrant.getTargetGroup() != null ? primaryGrant.getTargetGroup().getName() : null;
            Long targetGroupId = primaryGrant.getTargetGroup() != null ? primaryGrant.getTargetGroup().getId() : null;
            String targetOrgCode = primaryGrant.getTargetOrgCode();
            String targetOrgName = targetOrgCode != null ? orgNamesByCode.getOrDefault(targetOrgCode.toUpperCase(), targetOrgCode) : null;
            Long collaborationId = primaryGrant.getCollaboration() != null ? primaryGrant.getCollaboration().getId() : null;
            String collaborationName = primaryGrant.getCollaboration() != null ? primaryGrant.getCollaboration().getName() : null;

            String recipientType = resolveRecipientTypeString(primaryGrant);
            String sourceDesc = buildSourceDescription(grants, primaryGrant, sourceLabels, effectiveLevel, grantedByName, targetGroupName, targetOrgName, collaborationName);

            SharedItemDto dto = SharedItemDto.builder()
                    .permissionId(primaryGrant.getId())
                    .resourceType("DOCUMENT")
                    .resourceId(doc.getId())
                    .name(doc.getFileName())
                    .fileHash(doc.getFileHash())
                    .grantedByUserId(grantor != null ? grantor.getId() : (primaryGrant.getGrantedBy() != null ? primaryGrant.getGrantedBy().getId() : null))
                    .grantedByUserEmail(grantor != null ? grantor.getEmail() : (primaryGrant.getGrantedBy() != null ? primaryGrant.getGrantedBy().getEmail() : null))
                    .grantedByName(grantedByName)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(effectiveLevel)
                    .grantedAt(maxGrantedAt)
                    .expiresAt(resolvedExpiresAt)
                    .note(primaryGrant.getNote())
                    .sourceType(sourceType)
                    .ownerOrgCode(ownerOrgCode)
                    .ownerOrgName(ownerOrgName)
                    .workspaceId(wsId)
                    .workspaceName(wsName)
                    .fileSize(doc.getFileSize())
                    .contentType(doc.getContentType())
                    .external(external)
                    .sourceLabel(primarySourceLabel)
                    .sourceLabels(sourceLabels)
                    .sourceDescription(sourceDesc)
                    .matchingGrantCount(grants.size())
                    .matchedPermissionIds(grants.stream().map(PermissionEntity::getId).collect(Collectors.toList()))
                    .recipientType(recipientType)
                    .targetUserEmail(primaryGrant.getTargetUserEmail())
                    .targetUserName(primaryGrant.getTargetUserEmail() != null ? userNamesByEmail.get(primaryGrant.getTargetUserEmail().toLowerCase()) : null)
                    .targetGroupId(targetGroupId)
                    .targetGroupName(targetGroupName)
                    .targetOrgCode(targetOrgCode)
                    .targetOrgName(targetOrgName)
                    .collaborationId(collaborationId)
                    .collaborationName(collaborationName)
                    .build();

            sharedDocuments.add(dto);
        }

        return SharedWithMeResponseDto.builder()
                .sharedFolders(sharedFolders)
                .sharedDocuments(sharedDocuments)
                .build();
    }

    private String resolveGrantorDisplayName(UserEntity grantor) {
        if (grantor == null) {
            return null;
        }
        return formatUserDisplayName(grantor);
    }

    @Override
    @Transactional(readOnly = true)
    public SharedByMeResponseDto getSharedByMe(UserEntity currentUser, boolean includeInactive) {
        if (currentUser == null || currentUser.getId() == null) {
            return SharedByMeResponseDto.builder()
                    .sharedFolders(List.of())
                    .sharedDocuments(List.of())
                    .build();
        }

        List<PermissionEntity> permissions = null;
        try {
            permissions = permissionRepository.findByGrantedBy_IdOrderByCreatedAtDescIdDesc(currentUser.getId());
        } catch (Exception e) {
            log.debug("Fallback to findByGrantedBy_IdOrderByCreatedAtDesc: {}", e.getMessage());
        }
        if (permissions == null || permissions.isEmpty()) {
            permissions = permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc(currentUser.getId());
        }
        if (permissions == null || permissions.isEmpty()) {
            return SharedByMeResponseDto.builder()
                    .sharedFolders(List.of())
                    .sharedDocuments(List.of())
                    .build();
        }

        Instant now = Instant.now();

        Set<Long> candidateFolderIds = permissions.stream()
                .map(PermissionEntity::getFolder)
                .filter(Objects::nonNull)
                .map(FolderEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, ProjectWorkspaceEntity> wsByRootFolder = preloadWorkspacesByRootFolder(candidateFolderIds);

        List<PermissionEntity> authorizedPerms = new ArrayList<>();
        Set<Long> seenPermissionIds = new java.util.HashSet<>();

        for (PermissionEntity perm : permissions) {
            if (perm == null || perm.getId() == null) {
                continue;
            }
            if (!seenPermissionIds.add(perm.getId())) {
                continue;
            }
            if (perm.getShareLinkToken() != null && !perm.getShareLinkToken().isBlank()) {
                continue;
            }
            String status = resolvePermissionStatus(perm, now);
            if (!includeInactive && !"ACTIVE".equals(status)) {
                continue;
            }
            if (perm.getFolder() == null && perm.getDocument() == null) {
                continue;
            }
            if (perm.getDocument() != null && perm.getDocument().getStatus() != DocumentStatus.ACTIVE) {
                continue;
            }
            if (perm.getFolder() != null) {
                if (perm.getFolder().getId() != null && wsByRootFolder.containsKey(perm.getFolder().getId())) {
                    continue;
                }
                if (perm.getCollaboration() != null && perm.getCollaboration().getRootFolder() != null
                        && perm.getFolder().getId() != null
                        && perm.getFolder().getId().equals(perm.getCollaboration().getRootFolder().getId())) {
                    continue;
                }
            }
            authorizedPerms.add(perm);
        }

        if (authorizedPerms.isEmpty()) {
            return SharedByMeResponseDto.builder()
                    .sharedFolders(List.of())
                    .sharedDocuments(List.of())
                    .build();
        }

        // Repository ordering is already deterministic (createdAt DESC, id DESC).
        String grantorDisplayName = resolveGrantorDisplayName(currentUser);

        // Stage 2: Extract IDs and Bulk Load Maps
        Set<String> targetUserEmails = new HashSet<>();
        Set<String> targetOrgCodes = new HashSet<>();
        Set<Long> explicitWsIds = new HashSet<>();

        for (PermissionEntity perm : authorizedPerms) {
            if (perm.getCollaboration() == null && perm.getTargetGroup() == null && perm.getRecipientType() != RecipientType.GROUP) {
                if (perm.getTargetOrgCode() != null && (perm.getTargetUserEmail() == null || perm.getTargetUserEmail().isBlank())) {
                    targetOrgCodes.add(perm.getTargetOrgCode());
                } else if (perm.getTargetUserEmail() != null && !perm.getTargetUserEmail().isBlank()) {
                    targetUserEmails.add(perm.getTargetUserEmail());
                    if (perm.getTargetOrgCode() != null) {
                        targetOrgCodes.add(perm.getTargetOrgCode());
                    }
                }
            } else if (perm.getTargetGroup() != null) {
                if (perm.getTargetGroup().getOrganization() != null && perm.getTargetGroup().getOrganization().getOrgCode() != null) {
                    targetOrgCodes.add(perm.getTargetGroup().getOrganization().getOrgCode());
                } else if (perm.getTargetOrgCode() != null) {
                    targetOrgCodes.add(perm.getTargetOrgCode());
                }
            }

            if (perm.getWorkspaceId() != null) {
                explicitWsIds.add(perm.getWorkspaceId());
            }

            if (perm.getFolder() != null) {
                FolderEntity folder = perm.getFolder();
                if (folder.getOwnerOrg() != null && folder.getOwnerOrg().getOrgCode() != null) {
                    targetOrgCodes.add(folder.getOwnerOrg().getOrgCode());
                }
            } else if (perm.getDocument() != null) {
                DocumentEntity doc = perm.getDocument();
                OrganizationEntity ownerOrg = doc.getOwnerOrg();
                if (ownerOrg == null && doc.getFolder() != null) {
                    ownerOrg = doc.getFolder().getOwnerOrg();
                }
                if (ownerOrg != null && ownerOrg.getOrgCode() != null) {
                    targetOrgCodes.add(ownerOrg.getOrgCode());
                }
            }
        }

        Map<String, String> userNamesByEmail = preloadUserDisplayNamesByEmail(targetUserEmails);
        Map<String, String> orgNamesByCode = preloadOrgNamesByOrgCode(targetOrgCodes);

        wsByRootFolder.values().forEach(ws -> {
            if (ws.getId() != null) {
                explicitWsIds.add(ws.getId());
            }
        });
        Map<Long, String> wsNamesById = preloadWorkspaceNamesById(explicitWsIds);

        List<SharedItemDto> sharedFolders = new ArrayList<>();
        List<SharedItemDto> sharedDocuments = new ArrayList<>();

        for (PermissionEntity perm : authorizedPerms) {
            String status = resolvePermissionStatus(perm, now);
            SharedItemDto dto = buildSharedByMeItemDto(
                    perm, grantorDisplayName, status,
                    userNamesByEmail, orgNamesByCode, wsByRootFolder, wsNamesById);

            if (perm.getFolder() != null) {
                sharedFolders.add(dto);
            } else {
                sharedDocuments.add(dto);
            }
        }

        return SharedByMeResponseDto.builder()
                .sharedFolders(sharedFolders)
                .sharedDocuments(sharedDocuments)
                .build();
    }

    private String resolvePermissionStatus(PermissionEntity p, Instant now) {
        if (p.isRevoked()) {
            return "REVOKED";
        }
        if (p.isSuspended()) {
            return "SUSPENDED";
        }
        if (p.getExpiresAt() != null && p.getExpiresAt().isBefore(now)) {
            return "EXPIRED";
        }
        return "ACTIVE";
    }

    private String resolveUserDisplayNameByEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        if (userRepository == null) {
            return email;
        }
        Optional<UserEntity> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            return formatUserDisplayName(userOpt.get());
        }
        return email;
    }

    private SharedItemDto buildSharedByMeItemDto(PermissionEntity perm, String grantorDisplayName, String status) {
        return buildSharedByMeItemDto(perm, grantorDisplayName, status,
                Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
    }

    private SharedItemDto buildSharedByMeItemDto(
            PermissionEntity perm,
            String grantorDisplayName,
            String status,
            Map<String, String> userNamesByEmail,
            Map<String, String> orgNamesByOrgCode,
            Map<Long, ProjectWorkspaceEntity> wsByRootFolder,
            Map<Long, String> wsNamesById) {
        // Resolve Recipient Metadata
        String recipientType;
        String targetUserEmail = null;
        String targetUserName = null;
        Long targetGroupId = null;
        String targetGroupName = null;
        String targetOrgCode = null;
        String targetOrgName = null;
        Long collaborationId = null;
        String collaborationName = null;

        if (perm.getCollaboration() != null) {
            recipientType = "COLLABORATION";
            collaborationId = perm.getCollaboration().getId();
            collaborationName = perm.getCollaboration().getName();
        } else if (perm.getTargetGroup() != null || perm.getRecipientType() == RecipientType.GROUP) {
            recipientType = "GROUP";
            if (perm.getTargetGroup() != null) {
                targetGroupId = perm.getTargetGroup().getId();
                targetGroupName = perm.getTargetGroup().getName();
                if (perm.getTargetGroup().getOrganization() != null) {
                    targetOrgCode = perm.getTargetGroup().getOrganization().getOrgCode();
                    targetOrgName = perm.getTargetGroup().getOrganization().getName();
                }
            }
            if (targetOrgCode == null) {
                targetOrgCode = perm.getTargetOrgCode();
            }
            if (targetOrgName == null && targetOrgCode != null) {
                targetOrgName = orgNamesByOrgCode.get(targetOrgCode.toUpperCase());
            }
        } else if (perm.getTargetOrgCode() != null && (perm.getTargetUserEmail() == null || perm.getTargetUserEmail().isBlank())
                && perm.getTargetGroup() == null && perm.getCollaboration() == null) {
            recipientType = "ORGANIZATION";
            targetOrgCode = perm.getTargetOrgCode();
            targetOrgName = orgNamesByOrgCode.getOrDefault(targetOrgCode.toUpperCase(), targetOrgCode);
        } else {
            recipientType = "USER";
            targetUserEmail = perm.getTargetUserEmail();
            if (targetUserEmail != null) {
                targetUserName = userNamesByEmail.get(targetUserEmail.toLowerCase());
                if (targetUserName == null) {
                    targetUserName = resolveUserDisplayNameByEmail(targetUserEmail);
                }
            }
            targetOrgCode = perm.getTargetOrgCode();
            if (targetOrgCode != null) {
                targetOrgName = orgNamesByOrgCode.get(targetOrgCode.toUpperCase());
            }
        }

        // Resource & Workspace info
        String resourceType;
        Long resourceId;
        String resourceName;
        String fileHash = null;
        Long fileSize = null;
        String contentType = null;
        String sourceType;
        String ownerOrgCode = null;
        String ownerOrgName = null;
        Long wsId = perm.getWorkspaceId();
        String wsName = null;

        if (perm.getFolder() != null) {
            FolderEntity folder = perm.getFolder();
            resourceType = "FOLDER";
            resourceId = folder.getId();
            resourceName = folder.getName();
            OrganizationEntity ownerOrg = folder.getOwnerOrg();
            if (ownerOrg != null) {
                ownerOrgCode = ownerOrg.getOrgCode();
                ownerOrgName = ownerOrg.getName() != null ? ownerOrg.getName() : orgNamesByOrgCode.get(ownerOrgCode.toUpperCase());
            }
            sourceType = (ownerOrgCode != null) ? "ORGANIZATION" : "PERSONAL";

            if (wsId == null && folder.getId() != null) {
                ProjectWorkspaceEntity ws = wsByRootFolder.get(folder.getId());
                if (ws != null) {
                    wsId = ws.getId();
                    wsName = ws.getName();
                }
            }
            if (wsId != null && wsName == null) {
                wsName = wsNamesById.get(wsId);
            }
        } else {
            DocumentEntity doc = perm.getDocument();
            resourceType = "DOCUMENT";
            resourceId = doc.getId();
            resourceName = doc.getFileName();
            fileHash = doc.getFileHash();
            fileSize = doc.getFileSize();
            contentType = doc.getContentType();
            OrganizationEntity ownerOrg = doc.getOwnerOrg();
            if (ownerOrg == null && doc.getFolder() != null) {
                ownerOrg = doc.getFolder().getOwnerOrg();
            }
            if (ownerOrg != null) {
                ownerOrgCode = ownerOrg.getOrgCode();
                ownerOrgName = ownerOrg.getName() != null ? ownerOrg.getName() : orgNamesByOrgCode.get(ownerOrgCode.toUpperCase());
            }
            sourceType = (ownerOrgCode != null) ? "ORGANIZATION" : "PERSONAL";

            if (wsId != null) {
                wsName = wsNamesById.get(wsId);
            }
        }

        String sourceLabel = resolveSourceLabel(perm);
        List<String> sourceLabels = List.of(sourceLabel);
        String sourceDesc = buildSharedByMeSourceDescription(
                sourceLabel, targetUserName, targetUserEmail,
                targetGroupName, targetOrgName, targetOrgCode, collaborationName);

        return SharedItemDto.builder()
                .permissionId(perm.getId())
                .resourceType(resourceType)
                .resourceId(resourceId)
                .name(resourceName)
                .fileHash(fileHash)
                .fileSize(fileSize)
                .contentType(contentType)
                .grantedByUserId(perm.getGrantedBy() != null ? perm.getGrantedBy().getId() : null)
                .grantedByUserEmail(perm.getGrantedBy() != null ? perm.getGrantedBy().getEmail() : null)
                .grantedByName(grantorDisplayName)
                .permissionType(perm.getPermissionType())
                .permissionLevel(perm.getPermissionLevel())
                .grantedAt(perm.getCreatedAt())
                .expiresAt(perm.getExpiresAt())
                .note(perm.getNote())
                .sourceType(sourceType)
                .ownerOrgCode(ownerOrgCode)
                .ownerOrgName(ownerOrgName)
                .workspaceId(wsId)
                .workspaceName(wsName)
                .external(false)
                .sourceLabel(sourceLabel)
                .sourceLabels(sourceLabels)
                .sourceDescription(sourceDesc)
                .matchingGrantCount(1)
                .matchedPermissionIds(List.of(perm.getId()))
                // Recipient metadata
                .recipientType(recipientType)
                .targetUserEmail(targetUserEmail)
                .targetUserName(targetUserName)
                .targetGroupId(targetGroupId)
                .targetGroupName(targetGroupName)
                .targetOrgCode(targetOrgCode)
                .targetOrgName(targetOrgName)
                .collaborationId(collaborationId)
                .collaborationName(collaborationName)
                // Lifecycle metadata
                .status(status)
                .revoked(perm.isRevoked())
                .suspended(perm.isSuspended())
                .revokedAt(perm.getRevokedAt())
                .build();
    }

    private boolean matchesRecipientForUser(PermissionEntity p, UserEntity user, List<String> activeOrgCodes, List<Long> groupIds) {
        if (p == null || user == null) return false;
        RecipientType recipientType = p.getRecipientType();

        if (recipientType != null) {
            switch (recipientType) {
                case USER_PERSONAL:
                    return p.getTargetUserEmail() != null 
                            && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail());

                case USER_ORGANIZATION_MEMBER:
                    if (p.getTargetUserEmail() == null || !p.getTargetUserEmail().equalsIgnoreCase(user.getEmail())) {
                        return false;
                    }
                    if (p.getTargetOrgCode() == null || activeOrgCodes == null || activeOrgCodes.isEmpty()) {
                        return false;
                    }
                    return activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));

                case ORGANIZATION:
                    if (p.getTargetOrgCode() == null || activeOrgCodes == null || activeOrgCodes.isEmpty()) {
                        return false;
                    }
                    return activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));

                case GROUP:
                    if (p.getTargetGroup() == null || groupIds == null) {
                        return false;
                    }
                    return groupIds.contains(p.getTargetGroup().getId());

                default:
                    return false;
            }
        }

        // Fallback for legacy permission records where recipientType might be null
        if (p.getTargetUserEmail() != null && p.getTargetUserEmail().equalsIgnoreCase(user.getEmail())) {
            if (p.getTargetOrgCode() != null) {
                return activeOrgCodes != null && activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));
            }
            return true;
        }

        if (p.getTargetOrgCode() != null && activeOrgCodes != null) {
            return activeOrgCodes.stream().anyMatch(code -> code.equalsIgnoreCase(p.getTargetOrgCode()));
        }

        if (p.getTargetGroup() != null && groupIds != null) {
            return groupIds.contains(p.getTargetGroup().getId());
        }

        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public EffectiveAccessAuditDto getEffectivePermissions(Long targetId, String targetType, UserEntity currentUser) {
        log.info("Fetching effective permissions for targetId={}, targetType={} by user={}", targetId, targetType, currentUser != null ? currentUser.getEmail() : "SYSTEM");

        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view effective permissions");
        }

        String resourceName = "";
        String ownerEmail = "";
        List<PermissionEntity> directEntities = new ArrayList<>();
        List<PermissionEntity> inheritedEntities = new ArrayList<>();
        DocumentEntity targetDoc = null;
        FolderEntity targetFolder = null;

        if ("DOCUMENT".equalsIgnoreCase(targetType)) {
            DocumentEntity doc = documentRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + targetId));

            if (!evaluatorService.canAdministerDocumentPermissions(currentUser, doc)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to inspect effective permissions for this document");
            }
            targetDoc = doc;

            resourceName = doc.getFileName();
            ownerEmail = doc.getUploadedBy() != null ? doc.getUploadedBy().getEmail() : "SYSTEM";

            directEntities = permissionRepository.findByDocument_IdAndRevokedFalse(targetId);

            FolderEntity parent = doc.getFolder();
            while (parent != null) {
                inheritedEntities.addAll(permissionRepository.findByFolder_IdAndRevokedFalse(parent.getId()));
                parent = parent.getParentFolder();
            }
        } else if ("FOLDER".equalsIgnoreCase(targetType)) {
            FolderEntity folder = folderRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + targetId));

            if (!evaluatorService.canAdministerFolderPermissions(currentUser, folder)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to inspect effective permissions for this folder");
            }
            targetFolder = folder;

            resourceName = folder.getName();
            ownerEmail = folder.getCreatedBy() != null ? folder.getCreatedBy().getEmail() : "SYSTEM";

            directEntities = permissionRepository.findByFolder_IdAndRevokedFalse(targetId);

            FolderEntity parent = folder.getParentFolder();
            while (parent != null) {
                inheritedEntities.addAll(permissionRepository.findByFolder_IdAndRevokedFalse(parent.getId()));
                parent = parent.getParentFolder();
            }
        } else {
            throw new IllegalArgumentException("Invalid targetType: " + targetType + ". Must be FOLDER or DOCUMENT.");
        }

        List<PermissionResponseDto> directDtos = directEntities.stream()
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());

        List<PermissionResponseDto> inheritedDtos = inheritedEntities.stream()
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());

        final DocumentEntity effectiveDoc = targetDoc;
        final FolderEntity effectiveFolder = targetFolder;
        final String resourceOrgCode = effectiveFolder != null
                ? (effectiveFolder.getOwnerOrg() != null ? effectiveFolder.getOwnerOrg().getOrgCode() : null)
                : (effectiveDoc != null && effectiveDoc.getOwnerOrg() != null ? effectiveDoc.getOwnerOrg().getOrgCode() : null);

        List<ShareLinkResponseDto> shareLinks = directEntities.stream()
                .filter(p -> p.getShareLinkToken() != null && !p.getShareLinkToken().isBlank())
                .map(p -> {
                    boolean active = !p.isRevoked()
                            && (p.getExpiresAt() == null || p.getExpiresAt().isAfter(Instant.now()));

                    boolean isCreator = p.getGrantedBy() != null
                            && currentUser != null
                            && p.getGrantedBy().getId().equals(currentUser.getId());

                    boolean isPersonalOwner = (resourceOrgCode == null)
                            && currentUser != null
                            && ((effectiveFolder != null && evaluatorService.isFolderOwner(currentUser, effectiveFolder))
                                || (effectiveDoc != null && evaluatorService.isDocumentOwner(currentUser, effectiveDoc)));

                    boolean canReveal = active && (isCreator || isPersonalOwner);

                    return ShareLinkResponseDto.builder()
                            .permissionId(p.getId())
                            .shareLinkToken(null) // Sanitized: secret share tokens must never be exposed via general inspection endpoints
                            .createdByUserId(p.getGrantedBy() != null ? p.getGrantedBy().getId() : null)
                            .createdByUserEmail(p.getGrantedBy() != null ? p.getGrantedBy().getEmail() : null)
                            .canReveal(canReveal)
                            .documentId(p.getDocument() != null ? p.getDocument().getId() : null)
                            .documentName(p.getDocument() != null ? p.getDocument().getFileName() : null)
                            .folderId(p.getFolder() != null ? p.getFolder().getId() : null)
                            .folderName(p.getFolder() != null ? p.getFolder().getName() : null)
                            .requiresPassword(p.getPasswordHash() != null && !p.getPasswordHash().isBlank())
                            .expiresAt(p.getExpiresAt())
                            .permissionLevel(p.getPermissionLevel())
                            .permissionType(p.getPermissionType())
                            .targetUserEmail(p.getTargetUserEmail())
                            .createdAt(p.getCreatedAt())
                            .note(p.getNote())
                            .active(active)
                            .build();
                })
                .collect(Collectors.toList());

        List<UserAccessSummaryDto> userSummaries = new ArrayList<>();

        if (ownerEmail != null && !ownerEmail.isBlank()) {
            userSummaries.add(UserAccessSummaryDto.builder()
                    .email(ownerEmail)
                    .effectiveAccessLevel(com.subject77.docshare.modules.permission.entities.PermissionLevel.EDIT)
                    .accessSource("OWNER")
                    .build());
        }

        for (PermissionEntity p : directEntities) {
            if (p.getTargetUserEmail() != null && !p.getTargetUserEmail().equalsIgnoreCase(ownerEmail)) {
                userSummaries.add(UserAccessSummaryDto.builder()
                        .email(p.getTargetUserEmail())
                        .effectiveAccessLevel(p.getPermissionLevel())
                        .accessSource(p.getPermissionType() == PermissionType.EXCLUDE ? "EXCLUDED" : "DIRECT")
                        .build());
            }
        }

        for (PermissionEntity p : inheritedEntities) {
            if (p.getTargetUserEmail() != null && !p.getTargetUserEmail().equalsIgnoreCase(ownerEmail)) {
                boolean alreadyAdded = userSummaries.stream().anyMatch(u -> u.getEmail() != null && u.getEmail().equalsIgnoreCase(p.getTargetUserEmail()));
                if (!alreadyAdded) {
                    userSummaries.add(UserAccessSummaryDto.builder()
                            .email(p.getTargetUserEmail())
                            .effectiveAccessLevel(p.getPermissionLevel())
                            .accessSource("INHERITED")
                            .inheritedFromFolderId(p.getFolder() != null ? p.getFolder().getId() : null)
                            .inheritedFromFolderName(p.getFolder() != null ? p.getFolder().getName() : null)
                            .build());
                }
            }
        }

        return EffectiveAccessAuditDto.builder()
                .resourceId(targetId)
                .resourceType(targetType.toUpperCase())
                .resourceName(resourceName)
                .ownerEmail(ownerEmail)
                .userAccessSummaries(userSummaries)
                .directPermissions(directDtos)
                .inheritedPermissions(inheritedDtos)
                .activeShareLinks(shareLinks)
                .build();
    }

    private void evictCacheForPermissionTarget(PermissionEntity permission) {
        if (permission == null) {
            return;
        }

        RecipientType recipientType = permission.getRecipientType();

        if (recipientType == RecipientType.USER_PERSONAL || recipientType == RecipientType.USER_ORGANIZATION_MEMBER) {
            if (permission.getTargetUserEmail() != null && !permission.getTargetUserEmail().isBlank()) {
                cacheService.evictUserPermissions(permission.getTargetUserEmail());
            }
        } else if (recipientType == RecipientType.ORGANIZATION) {
            if (permission.getTargetOrgCode() != null && !permission.getTargetOrgCode().isBlank()) {
                List<UserOrganizationEntity> members = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(
                        permission.getTargetOrgCode(), MembershipStatus.ACTIVE);
                for (UserOrganizationEntity member : members) {
                    if (member.getUser() != null && member.getUser().getEmail() != null) {
                        cacheService.evictUserPermissions(member.getUser().getEmail());
                    }
                }
            }
        } else if (recipientType == RecipientType.GROUP) {
            if (permission.getTargetGroup() != null && permission.getTargetGroup().getId() != null) {
                List<UserGroupEntity> groupMembers = userGroupRepository.findActiveOrgMembersByGroupId(
                        permission.getTargetGroup().getId(), MembershipStatus.ACTIVE);
                for (UserGroupEntity member : groupMembers) {
                    if (member.getUser() != null && member.getUser().getEmail() != null) {
                        cacheService.evictUserPermissions(member.getUser().getEmail());
                    }
                }
            }
        } else {
            // Legacy / ambiguous (recipientType == null): preserve broad invalidation for compatibility
            if (permission.getTargetUserEmail() != null && !permission.getTargetUserEmail().isBlank()) {
                cacheService.evictUserPermissions(permission.getTargetUserEmail());
            }
            if (permission.getTargetOrgCode() != null && !permission.getTargetOrgCode().isBlank()) {
                List<UserOrganizationEntity> members = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(
                        permission.getTargetOrgCode(), MembershipStatus.ACTIVE);
                for (UserOrganizationEntity member : members) {
                    if (member.getUser() != null && member.getUser().getEmail() != null) {
                        cacheService.evictUserPermissions(member.getUser().getEmail());
                    }
                }
            }
            if (permission.getTargetGroup() != null && permission.getTargetGroup().getId() != null) {
                List<UserGroupEntity> groupMembers = userGroupRepository.findActiveOrgMembersByGroupId(
                        permission.getTargetGroup().getId(), MembershipStatus.ACTIVE);
                for (UserGroupEntity member : groupMembers) {
                    if (member.getUser() != null && member.getUser().getEmail() != null) {
                        cacheService.evictUserPermissions(member.getUser().getEmail());
                    }
                }
            }
        }
    }

    private void sendPermissionNotification(PermissionEntity permission, UserEntity actor, NotificationType type, String title, String body) {
        if (permission == null || notificationService == null) {
            return;
        }

        String resourceName = permission.getFolder() != null ? permission.getFolder().getName()
                : (permission.getDocument() != null ? permission.getDocument().getFileName() : "Resource");

        // 1. Direct User Grant
        if (permission.getTargetUserEmail() != null && !permission.getTargetUserEmail().isBlank()) {
            userRepository.findByEmail(permission.getTargetUserEmail()).ifPresent(recipient -> {
                String key = type.name() + ":" + permission.getId() + ":" + recipient.getId();
                notificationService.createNotification(
                        recipient,
                        actor,
                        type,
                        title,
                        body,
                        NotificationSourceType.PERMISSION,
                        String.valueOf(permission.getId()),
                        NotificationCategory.SHARING,
                        NotificationPriority.MEDIUM,
                        key
                );
            });
            return;
        }

        // 2. Team (Group) Grant -> notify Team Manager
        if (permission.getTargetGroup() != null) {
            GroupEntity group = permission.getTargetGroup();
            List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(group.getId(), GroupMemberRole.MANAGER);
            for (UserGroupEntity ug : managers) {
                if (ug.getUser() != null) {
                    UserEntity manager = ug.getUser();
                    if (group.getOrganization() != null) {
                        String orgCode = group.getOrganization().getOrgCode();
                        boolean activeOrgMember = userOrganizationRepository
                                .findByUser_IdAndOrganization_OrgCode(manager.getId(), orgCode)
                                .map(uo -> uo.getStatus() == MembershipStatus.ACTIVE)
                                .orElse(false);
                        if (!activeOrgMember) {
                            log.debug("Skipping notification for suspended/inactive team manager {} in org {}", manager.getId(), orgCode);
                            continue;
                        }
                    }
                    String groupActionTitle = type == NotificationType.PERMISSION_REVOKED ? "Team Permission Revoked" : "Team Permission Granted";
                    String groupActionBody = type == NotificationType.PERMISSION_REVOKED
                            ? String.format("Team '%s' access to %s has been revoked.", group.getName(), resourceName)
                            : String.format("Your team '%s' was granted %s access to %s.", group.getName(), permission.getPermissionLevel(), resourceName);
                    String key = type.name() + ":GROUP:" + permission.getId() + ":" + manager.getId();
                    notificationService.createNotification(
                            manager,
                            actor,
                            type,
                            groupActionTitle,
                            groupActionBody,
                            NotificationSourceType.PERMISSION,
                            String.valueOf(permission.getId()),
                            NotificationCategory.SHARING,
                            NotificationPriority.MEDIUM,
                            key
                    );
                }
            }
            return;
        }

        // 3. Organization Grant -> notify active ORG_ADMINs (excluding actor)
        if (permission.getTargetOrgCode() != null && !permission.getTargetOrgCode().isBlank()) {
            String orgCode = permission.getTargetOrgCode();
            List<UserOrganizationEntity> orgAdmins = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(orgCode, MembershipStatus.ACTIVE);
            for (UserOrganizationEntity uo : orgAdmins) {
                if (uo.getRole() == Role.ROLE_ORG_ADMIN && uo.getUser() != null) {
                    UserEntity admin = uo.getUser();
                    if (actor != null && admin.getId().equals(actor.getId())) {
                        continue;
                    }
                    String orgActionTitle = type == NotificationType.PERMISSION_REVOKED ? "Organization Permission Revoked" : "Organization Permission Granted";
                    String orgActionBody = type == NotificationType.PERMISSION_REVOKED
                            ? String.format("Organization '%s' access to %s has been revoked.", orgCode, resourceName)
                            : String.format("Your organization '%s' was granted %s access to %s.", orgCode, permission.getPermissionLevel(), resourceName);
                    String key = type.name() + ":ORG:" + permission.getId() + ":" + admin.getId();
                    notificationService.createNotification(
                            admin,
                            actor,
                            type,
                            orgActionTitle,
                            orgActionBody,
                            NotificationSourceType.PERMISSION,
                            String.valueOf(permission.getId()),
                            NotificationCategory.SHARING,
                            NotificationPriority.MEDIUM,
                            key
                    );
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public ManageAccessSummaryDto getManageAccessSummary(Long resourceId, String resourceType, UserEntity currentUser) {
        if (currentUser == null) {
            throw new org.springframework.security.access.AccessDeniedException("User must be authenticated to view access management summary");
        }
        if (resourceId == null || resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Resource ID and resource type must be provided");
        }

        String normalizedType = resourceType.toUpperCase();
        String resourceName;
        UserEntity ownerUser;
        String ownerOrgCode = null;

        List<PermissionEntity> directEntities;
        List<PermissionEntity> inheritedEntities = new ArrayList<>();

        if ("DOCUMENT".equals(normalizedType)) {
            DocumentEntity doc = documentRepository.findById(resourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + resourceId));

            if (!evaluatorService.canAdministerDocumentPermissions(currentUser, doc)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to view access management summary for this document");
            }

            resourceName = doc.getFileName();
            ownerUser = doc.getUploadedBy();
            if (doc.getOwnerOrg() != null) {
                ownerOrgCode = doc.getOwnerOrg().getOrgCode();
            } else if (ownerUser != null && ownerUser.getOrganization() != null) {
                ownerOrgCode = ownerUser.getOrganization().getOrgCode();
            }

            directEntities = permissionRepository.findByDocument_Id(resourceId);

            FolderEntity parent = doc.getFolder();
            while (parent != null) {
                inheritedEntities.addAll(permissionRepository.findByFolder_Id(parent.getId()));
                parent = parent.getParentFolder();
            }
        } else if ("FOLDER".equals(normalizedType)) {
            FolderEntity folder = folderRepository.findById(resourceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Folder not found with ID: " + resourceId));

            if (!evaluatorService.canAdministerFolderPermissions(currentUser, folder)) {
                throw new org.springframework.security.access.AccessDeniedException("User does not have authority to view access management summary for this folder");
            }

            resourceName = folder.getName();
            ownerUser = folder.getCreatedBy();
            if (folder.getOwnerOrg() != null) {
                ownerOrgCode = folder.getOwnerOrg().getOrgCode();
            } else if (ownerUser != null && ownerUser.getOrganization() != null) {
                ownerOrgCode = ownerUser.getOrganization().getOrgCode();
            }

            directEntities = permissionRepository.findByFolder_Id(resourceId);

            FolderEntity parent = folder.getParentFolder();
            while (parent != null) {
                inheritedEntities.addAll(permissionRepository.findByFolder_Id(parent.getId()));
                parent = parent.getParentFolder();
            }
        } else {
            throw new IllegalArgumentException("Invalid resourceType: " + resourceType + ". Must be FOLDER or DOCUMENT.");
        }

        List<EffectiveAccessEntryDto> entries = new ArrayList<>();

        String ownerUserId = ownerUser != null ? ownerUser.getId() : null;
        String ownerEmail = ownerUser != null ? ownerUser.getEmail() : null;
        String ownerDisplayName = ownerUser != null ? 
                ((ownerUser.getFirstName() != null || ownerUser.getLastName() != null) ? 
                        (ownerUser.getFirstName() + " " + (ownerUser.getLastName() != null ? ownerUser.getLastName() : "")).trim() : 
                        ownerUser.getEmail()) 
                : "SYSTEM";

        if (ownerUser != null) {
            entries.add(EffectiveAccessEntryDto.builder()
                    .permissionId(null)
                    .principalType("USER")
                    .principalId(ownerUserId)
                    .displayName(ownerDisplayName)
                    .email(ownerEmail)
                    .permissionLevel(PermissionLevel.EDIT)
                    .permissionType(PermissionType.ALLOW)
                    .source("OWNER")
                    .inheritedFromResourceId(null)
                    .inheritedFromResourceName(null)
                    .expiresAt(null)
                    .external(false)
                    .status("ACTIVE")
                    .build());
        }

        for (PermissionEntity p : directEntities) {
            EffectiveAccessEntryDto entry = buildEffectiveAccessEntry(p, "DIRECT", null, null, ownerOrgCode);
            entries.add(entry);
        }

        for (PermissionEntity p : inheritedEntities) {
            Long parentFolderId = p.getFolder() != null ? p.getFolder().getId() : null;
            String parentFolderName = p.getFolder() != null ? p.getFolder().getName() : null;
            EffectiveAccessEntryDto entry = buildEffectiveAccessEntry(p, "INHERITED", parentFolderId, parentFolderName, ownerOrgCode);
            entries.add(entry);
        }

        return ManageAccessSummaryDto.builder()
                .resourceId(resourceId)
                .resourceType(normalizedType)
                .resourceName(resourceName)
                .ownerUserId(ownerUserId)
                .ownerDisplayName(ownerDisplayName)
                .ownerEmail(ownerEmail)
                .ownerOrgCode(ownerOrgCode)
                .entries(entries)
                .build();
    }

    private EffectiveAccessEntryDto buildEffectiveAccessEntry(PermissionEntity p, String defaultSource, Long parentFolderId, String parentFolderName, String ownerOrgCode) {
        String status;
        if (p.isRevoked()) {
            status = "REVOKED";
        } else if (p.getExpiresAt() != null && p.getExpiresAt().isBefore(Instant.now())) {
            status = "EXPIRED";
        } else {
            status = "ACTIVE";
        }

        String source;
        if (p.getPermissionType() == PermissionType.EXCLUDE) {
            source = "EXCLUDED";
        } else if ("INHERITED".equals(defaultSource)) {
            source = "INHERITED";
        } else if (p.getCollaboration() != null) {
            source = "COLLABORATION";
        } else if (p.getRecipientType() == RecipientType.GROUP || (p.getRecipientType() == null && p.getTargetGroup() != null)) {
            source = "TEAM";
        } else if (p.getRecipientType() == RecipientType.ORGANIZATION || (p.getRecipientType() == null && p.getTargetOrgCode() != null && (p.getTargetUserEmail() == null || p.getTargetUserEmail().isBlank()))) {
            source = "ORGANIZATION";
        } else {
            source = "DIRECT";
        }

        String principalType;
        String principalId = null;
        String displayName = null;
        String email = null;
        boolean external = false;

        RecipientType recipientType = p.getRecipientType();

        if (recipientType == RecipientType.USER_PERSONAL) {
            principalType = "USER";
            email = p.getTargetUserEmail();
            if (email != null && !email.isBlank()) {
                Optional<UserEntity> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    UserEntity u = userOpt.get();
                    principalId = u.getId();
                    displayName = (u.getFirstName() != null || u.getLastName() != null) ?
                            (u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : "")).trim() :
                            u.getEmail();
                } else {
                    displayName = email;
                }
            }
            if (ownerOrgCode != null && !ownerOrgCode.isBlank()) {
                external = true;
            }
        } else if (recipientType == RecipientType.USER_ORGANIZATION_MEMBER) {
            principalType = "USER";
            email = p.getTargetUserEmail();
            if (email != null && !email.isBlank()) {
                Optional<UserEntity> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    UserEntity u = userOpt.get();
                    principalId = u.getId();
                    displayName = (u.getFirstName() != null || u.getLastName() != null) ?
                            (u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : "")).trim() :
                            u.getEmail();
                } else {
                    displayName = email;
                }
            }
            if (ownerOrgCode != null && !ownerOrgCode.isBlank()) {
                if (p.getTargetOrgCode() != null) {
                    external = !p.getTargetOrgCode().equalsIgnoreCase(ownerOrgCode);
                } else {
                    external = true;
                }
            }
        } else if (recipientType == RecipientType.ORGANIZATION || (recipientType == null && p.getTargetOrgCode() != null && (p.getTargetUserEmail() == null || p.getTargetUserEmail().isBlank()) && p.getTargetGroup() == null && p.getCollaboration() == null)) {
            principalType = "ORGANIZATION";
            principalId = p.getTargetOrgCode();
            displayName = organizationRepository.findByOrgCode(p.getTargetOrgCode())
                    .map(com.subject77.docshare.modules.organization.entities.OrganizationEntity::getName)
                    .orElse(p.getTargetOrgCode());
            if (ownerOrgCode != null && !ownerOrgCode.isBlank() && p.getTargetOrgCode() != null) {
                external = !p.getTargetOrgCode().equalsIgnoreCase(ownerOrgCode);
            }
        } else if (recipientType == RecipientType.GROUP || (recipientType == null && p.getTargetGroup() != null)) {
            principalType = "TEAM";
            if (p.getTargetGroup() != null) {
                principalId = String.valueOf(p.getTargetGroup().getId());
                displayName = p.getTargetGroup().getName();
                if (ownerOrgCode != null && !ownerOrgCode.isBlank() && p.getTargetGroup().getOrganization() != null) {
                    external = !p.getTargetGroup().getOrganization().getOrgCode().equalsIgnoreCase(ownerOrgCode);
                }
            }
        } else if (p.getTargetUserEmail() != null && !p.getTargetUserEmail().isBlank()) {
            principalType = "USER";
            email = p.getTargetUserEmail();
            Optional<UserEntity> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                UserEntity u = userOpt.get();
                principalId = u.getId();
                displayName = (u.getFirstName() != null || u.getLastName() != null) ?
                        (u.getFirstName() + " " + (u.getLastName() != null ? u.getLastName() : "")).trim() :
                        u.getEmail();
                if (ownerOrgCode != null && !ownerOrgCode.isBlank()) {
                    if (p.getTargetOrgCode() != null) {
                        external = !p.getTargetOrgCode().equalsIgnoreCase(ownerOrgCode);
                    } else if (u.getOrganization() != null) {
                        external = !u.getOrganization().getOrgCode().equalsIgnoreCase(ownerOrgCode);
                    } else {
                        external = true;
                    }
                }
            } else {
                displayName = email;
                external = true;
            }
        } else if (p.getCollaboration() != null) {
            principalType = "COLLABORATION";
            principalId = String.valueOf(p.getCollaboration().getId());
            displayName = p.getCollaboration().getName();
            external = true;
        } else if (p.getShareLinkToken() != null && !p.getShareLinkToken().isBlank()) {
            principalType = "SHARE_LINK";
            principalId = null;
            displayName = "Share Link";
            external = true;
        } else {
            principalType = "UNKNOWN";
        }

        return EffectiveAccessEntryDto.builder()
                .permissionId(p.getId())
                .principalType(principalType)
                .principalId(principalId)
                .displayName(displayName)
                .email(email)
                .permissionLevel(p.getPermissionLevel())
                .permissionType(p.getPermissionType())
                .source(source)
                .inheritedFromResourceId(parentFolderId)
                .inheritedFromResourceName(parentFolderName)
                .expiresAt(p.getExpiresAt())
                .external(external)
                .status(status)
                .build();
    }

    private DocumentEntity resolveDocumentEntity(Long documentId, String fileHash, UserEntity grantorOrOwner) {
        if (documentId != null) {
            return documentRepository.findById(documentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found with ID: " + documentId));
        }
        if (fileHash != null && !fileHash.isBlank()) {
            List<DocumentEntity> docs = documentRepository.findByFileHash(fileHash);
            if (docs.isEmpty()) {
                return null;
            }
            if (docs.size() == 1) {
                return docs.get(0);
            }
            if (grantorOrOwner != null) {
                List<DocumentEntity> ownerDocs = docs.stream()
                        .filter(d -> d.getUploadedBy() != null && d.getUploadedBy().getId().equals(grantorOrOwner.getId()))
                        .toList();
                if (ownerDocs.size() == 1) {
                    return ownerDocs.get(0);
                }
            }
            throw new IllegalArgumentException("Ambiguous document selection: multiple documents match file hash. Please provide documentId.");
        }
        return null;
    }
}

