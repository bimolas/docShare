package com.subject77.docshare.modules.collaboration.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.collaboration.dto.CollaborationGroupParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CreateCollaborationRequestDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationGroupParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationGroupParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.mapper.PermissionMapper;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CollaborationServiceImpl implements CollaborationService {

    private final CollaborationRepository collaborationRepository;
    private final CollaborationParticipantRepository collaborationParticipantRepository;
    private final CollaborationGroupParticipantRepository collaborationGroupParticipantRepository;
    private final OrganizationRepository organizationRepository;
    private final FolderRepository folderRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final OrgTrustRelationshipService orgTrustRelationshipService;
    private final AuditLogService auditLogService;
    private final PermissionService permissionService;
    private final PermissionEvaluatorService permissionEvaluatorService;
    private final PermissionRepository permissionRepository;
    private final DocumentRepository documentRepository;
    private final UserGroupRepository userGroupRepository;
    private final NotificationService notificationService;
    private final PermissionCacheService permissionCacheService;


    @Override
    public CollaborationResponseDto createCollaboration(CreateCollaborationRequestDto request, UserEntity currentUser) {
        log.info("Creating collaboration '{}' between owner org: {} and partner org: {}",
                request != null ? request.getName() : null,
                request != null ? request.getOwnerOrgCode() : null,
                request != null ? request.getPartnerOrgCode() : null);

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to create a collaboration");
        }

        if (request == null) {
            throw new IllegalArgumentException("CreateCollaborationRequestDto cannot be null");
        }

        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Collaboration name is required");
        }

        if (request.getOwnerOrgCode() == null || request.getOwnerOrgCode().isBlank()) {
            throw new IllegalArgumentException("Owner organization code is required");
        }

        if (request.getPartnerOrgCode() == null || request.getPartnerOrgCode().isBlank()) {
            throw new IllegalArgumentException("Partner organization code is required");
        }

        if (request.getRootFolderId() == null) {
            throw new IllegalArgumentException("Root folder ID is required");
        }

        if (request.getStartsAt() == null) {
            throw new IllegalArgumentException("Start timestamp (startsAt) is required");
        }

        if (request.getEndsAt() != null && request.getEndsAt().isBefore(request.getStartsAt())) {
            throw new IllegalArgumentException("endsAt cannot be before startsAt");
        }

        if (request.getOwnerOrgCode().equalsIgnoreCase(request.getPartnerOrgCode())) {
            throw new IllegalArgumentException("Owner organization and partner organization cannot be the same");
        }

        OrganizationEntity ownerOrg = organizationRepository.findById(request.getOwnerOrgCode())
                .orElseThrow(() -> new ResourceNotFoundException("Owner organization not found with code: " + request.getOwnerOrgCode()));

        OrganizationEntity partnerOrg = organizationRepository.findById(request.getPartnerOrgCode())
                .orElseThrow(() -> new ResourceNotFoundException("Partner organization not found with code: " + request.getPartnerOrgCode()));

        // Enforce active trust relationship check
        boolean hasActiveTrust = orgTrustRelationshipService.hasActiveTrust(request.getOwnerOrgCode(), request.getPartnerOrgCode());
        if (!hasActiveTrust) {
            throw new IllegalStateException("An active trust relationship is required between the owner and partner organizations");
        }

        // Authorization check: creator must be ORG_ADMIN or SUPER_ADMIN in owner org
        if (!isOrgAdmin(currentUser, request.getOwnerOrgCode())) {
            throw new AccessDeniedException("User must be an organization administrator in owner organization to create a collaboration");
        }

        FolderEntity rootFolder = folderRepository.findById(request.getRootFolderId())
                .orElseThrow(() -> new ResourceNotFoundException("Root folder not found with ID: " + request.getRootFolderId()));

        CollaborationEntity collaboration = CollaborationEntity.builder()
                .name(request.getName().trim())
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .createdBy(currentUser)
                .status(CollaborationStatus.PENDING)
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .build();

        CollaborationEntity saved = collaborationRepository.save(collaboration);

        // Record audit log for COLLABORATION_CREATED
        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_CREATED")
                .targetType("FOLDER")
                .targetId(rootFolder.getId())
                .folderId(rootFolder.getId())
                .workspaceId(null)
                .collaborationId(saved.getId())
                .spaceType(rootFolder != null && rootFolder.getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(request.getOwnerOrgCode())
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToOrgCode(request.getPartnerOrgCode())
                .permissionLevel(PermissionLevel.VIEW)
                .validFrom(request.getStartsAt())
                .expiresAt(request.getEndsAt())
                .recordHash("COLLAB_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        List<UserEntity> partnerAdmins = getOrgAdmins(request.getPartnerOrgCode());
        sendCollaborationNotification(
                partnerAdmins,
                currentUser,
                NotificationType.COLLABORATION_INVITE,
                "Collaboration Invitation",
                String.format("Organization %s invited your organization (%s) to collaborate on '%s'.", request.getOwnerOrgCode(), request.getPartnerOrgCode(), saved.getName()),
                String.valueOf(saved.getId())
        );

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CollaborationResponseDto getCollaborationById(Long id, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        CollaborationEntity collaboration = collaborationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + id));

        validateUserOrgAccess(currentUser, collaboration.getOwnerOrganization().getOrgCode(), collaboration.getPartnerOrganization().getOrgCode());

        return mapToResponseDto(collaboration);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollaborationResponseDto> getCollaborationsForOrganization(String orgCode, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (orgCode == null || orgCode.isBlank()) {
            throw new IllegalArgumentException("Organization code is required");
        }

        validateUserInOrg(currentUser, orgCode);

        List<CollaborationEntity> list = collaborationRepository.findByOwnerOrganization_OrgCodeOrPartnerOrganization_OrgCode(orgCode, orgCode);
        return list.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public CollaborationResponseDto acceptCollaboration(Long id, UserEntity currentUser) {
        log.info("Attempting to accept collaboration ID: {} by user: {}", id, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to accept collaboration");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + id));

        if (collaboration.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING collaborations can be accepted");
        }

        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();
        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();

        // Target organization approval: only partner org admin or super admin can accept
        if (!isOrgAdmin(currentUser, partnerOrgCode)) {
            throw new AccessDeniedException("User must be an organization administrator in the partner organization to accept collaboration");
        }

        // Expiration check
        if (collaboration.getEndsAt() != null && collaboration.getEndsAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Cannot accept an expired collaboration request");
        }

        // Active trust recheck
        boolean hasActiveTrust = orgTrustRelationshipService.hasActiveTrust(ownerOrgCode, partnerOrgCode);
        if (!hasActiveTrust) {
            throw new IllegalStateException("Cannot accept collaboration because active trust relationship between organizations no longer exists");
        }

        collaboration.setStatus(CollaborationStatus.ACTIVE);
        collaboration.setAcceptedBy(currentUser);
        collaboration.setAcceptedAt(Instant.now());

        CollaborationEntity saved = collaborationRepository.save(collaboration);

        // Record audit log for COLLABORATION_ACCEPTED
        AuditPermissionChangeCommand acceptAuditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_ACCEPTED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(saved.getId())
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToOrgCode(partnerOrgCode)
                .permissionLevel(PermissionLevel.VIEW)
                .validFrom(collaboration.getStartsAt())
                .expiresAt(collaboration.getEndsAt())
                .recordHash("COLLAB_ACCEPT_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(acceptAuditCmd);

        List<UserEntity> acceptRecipients = new ArrayList<>();
        if (saved.getCreatedBy() != null) {
            acceptRecipients.add(saved.getCreatedBy());
        }
        acceptRecipients.addAll(getOrgAdmins(ownerOrgCode));
        sendCollaborationNotification(
                acceptRecipients, 
                currentUser, 
                NotificationType.COLLABORATION_ACCEPTED, 
                "Collaboration Accepted", 
                String.format("Organization %s accepted the collaboration '%s'.", partnerOrgCode, saved.getName()), 
                String.valueOf(saved.getId())
        );

        return mapToResponseDto(saved);
    }

    @Override
    public CollaborationResponseDto declineCollaboration(Long id, UserEntity currentUser) {
        log.info("Attempting to decline collaboration ID: {} by user: {}", id, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to decline collaboration");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + id));

        if (collaboration.getStatus() != CollaborationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING collaborations can be declined");
        }

        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();
        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();

        // Target organization approval: only partner org admin or super admin can decline
        if (!isOrgAdmin(currentUser, partnerOrgCode)) {
            throw new AccessDeniedException("User must be an organization administrator in the partner organization to decline collaboration");
        }

        collaboration.setStatus(CollaborationStatus.REVOKED);

        CollaborationEntity saved = collaborationRepository.save(collaboration);

        // Record audit log for COLLABORATION_DECLINED
        AuditPermissionChangeCommand declineAuditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_DECLINED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(saved.getId())
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToOrgCode(partnerOrgCode)
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_DECLINE_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(declineAuditCmd);

        List<UserEntity> declineRecipients = new ArrayList<>();
        if (saved.getCreatedBy() != null) {
            declineRecipients.add(saved.getCreatedBy());
        }
        declineRecipients.addAll(getOrgAdmins(ownerOrgCode));
        sendCollaborationNotification(
                declineRecipients, 
                currentUser, 
                NotificationType.COLLABORATION_REJECTED, 
                "Collaboration Declined", 
                String.format("Organization %s declined the collaboration '%s'.", partnerOrgCode, saved.getName()), 
                String.valueOf(saved.getId())
        );

        return mapToResponseDto(saved);
    }

    @Override
    public CollaborationParticipantResponseDto addParticipant(Long collaborationId, String userId, UserEntity currentUser) {
        log.info("Adding participant user ID: {} to collaboration ID: {} by user: {}", userId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to add participant");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Participants can only be managed on ACTIVE collaborations");
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization check: must be org admin or collaboration manager
        if (!isOrgAdmin(currentUser, ownerOrgCode) && !isOrgAdmin(currentUser, partnerOrgCode) && !isCollaborationManager(currentUser, collaborationId)) {
            throw new AccessDeniedException("User must be an organization administrator or collaboration manager to manage participants");
        }

        UserEntity targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        // Enforce organization boundary: target user MUST belong to owner or partner org
        if (!isUserInOrg(targetUser, ownerOrgCode, partnerOrgCode)) {
            throw new IllegalArgumentException("User does not belong to either owner or partner organization of this collaboration");
        }

        // Duplicate participant check
        if (collaborationParticipantRepository.existsByCollaboration_IdAndUser_Id(collaborationId, userId)) {
            throw new IllegalStateException("User is already a participant in this collaboration");
        }

        CollaborationParticipantEntity participant = CollaborationParticipantEntity.builder()
                .collaboration(collaboration)
                .user(targetUser)
                .createdAt(Instant.now())
                .build();

        CollaborationParticipantEntity saved = collaborationParticipantRepository.save(participant);

        // Record audit log for COLLABORATION_PARTICIPANT_ADDED
        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_PARTICIPANT_ADDED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(collaboration.getId())
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToEmail(targetUser.getEmail())
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_PARTICIPANT_ADD_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        return mapToParticipantResponseDto(saved);
    }

    @Override
    public void removeParticipant(Long collaborationId, String userId, UserEntity currentUser) {
        log.info("Removing participant user ID: {} from collaboration ID: {} by user: {}", userId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to remove participant");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Participants can only be managed on ACTIVE collaborations");
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization check: must be org admin or collaboration manager
        if (!isOrgAdmin(currentUser, ownerOrgCode) && !isOrgAdmin(currentUser, partnerOrgCode) && !isCollaborationManager(currentUser, collaborationId)) {
            throw new AccessDeniedException("User must be an organization administrator or collaboration manager to manage participants");
        }

        CollaborationParticipantEntity participant = collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaborationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found in collaboration with ID: " + collaborationId));

        // Revoke all active collaboration-derived permissions for this target user in this collaboration BEFORE deletion
        String targetUserEmail = participant.getUser() != null ? participant.getUser().getEmail() : null;
        if (targetUserEmail != null) {
            List<PermissionEntity> activeCollabPermissions = permissionRepository
                    .findByCollaboration_IdAndTargetUserEmailIgnoreCaseAndRevokedFalse(collaborationId, targetUserEmail);
            for (PermissionEntity permission : activeCollabPermissions) {
                permissionService.revokePermission(permission.getId(), currentUser.getId());
            }
        }

        collaborationParticipantRepository.delete(participant);

        if (permissionCacheService != null && targetUserEmail != null) {
            permissionCacheService.evictUserPermissions(targetUserEmail);
        }

        // Record audit log for COLLABORATION_PARTICIPANT_REMOVED
        AuditPermissionChangeCommand removeAuditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_PARTICIPANT_REMOVED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(collaborationId)
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToEmail(participant.getUser().getEmail())
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_PARTICIPANT_REMOVE_" + collaborationId + "_" + userId)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(removeAuditCmd);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollaborationParticipantResponseDto> getParticipants(Long collaborationId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to get participants");
        }

        CollaborationEntity collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        validateUserOrgAccess(currentUser, collaboration.getOwnerOrganization().getOrgCode(), collaboration.getPartnerOrganization().getOrgCode());

        List<CollaborationParticipantEntity> list = collaborationParticipantRepository.findByCollaboration_Id(collaborationId);
        return list.stream()
                .map(this::mapToParticipantResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public CollaborationGroupParticipantResponseDto addGroupParticipant(Long collaborationId, Long groupId, UserEntity currentUser) {
        log.info("Adding group participant ID: {} to collaboration ID: {} by user: {}", groupId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to add group participant");
        }

        if (groupId == null) {
            throw new IllegalArgumentException("Group ID is required");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Group participants can only be managed on ACTIVE collaborations");
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization check: must be org admin or collaboration manager
        if (!isOrgAdmin(currentUser, ownerOrgCode) && !isOrgAdmin(currentUser, partnerOrgCode) && !isCollaborationManager(currentUser, collaborationId)) {
            throw new AccessDeniedException("User must be an organization administrator or collaboration manager to manage group participants");
        }

        GroupEntity group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID: " + groupId));

        // Enforce organization boundary: group MUST belong to owner or partner org
        String groupOrgCode = group.getOrganization() != null ? group.getOrganization().getOrgCode() : null;
        if (groupOrgCode == null || (!groupOrgCode.equalsIgnoreCase(ownerOrgCode) && !groupOrgCode.equalsIgnoreCase(partnerOrgCode))) {
            throw new IllegalArgumentException("Group does not belong to either owner or partner organization of this collaboration");
        }

        // Duplicate group participant check
        if (collaborationGroupParticipantRepository.existsByCollaboration_IdAndGroup_Id(collaborationId, groupId)) {
            throw new IllegalStateException("Group is already a participant in this collaboration");
        }

        CollaborationGroupParticipantEntity entity = CollaborationGroupParticipantEntity.builder()
                .collaboration(collaboration)
                .group(group)
                .createdAt(Instant.now())
                .build();

        CollaborationGroupParticipantEntity saved = collaborationGroupParticipantRepository.save(entity);

        // Record audit log for COLLABORATION_GROUP_ADDED
        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_GROUP_ADDED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(collaboration.getId())
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToOrgCode(groupOrgCode)
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_GROUP_ADD_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        return mapToGroupParticipantResponseDto(saved);
    }

    @Override
    public void removeGroupParticipant(Long collaborationId, Long groupId, UserEntity currentUser) {
        log.info("Removing group participant ID: {} from collaboration ID: {} by user: {}", groupId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to remove group participant");
        }

        if (groupId == null) {
            throw new IllegalArgumentException("Group ID is required");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Group participants can only be managed on ACTIVE collaborations");
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization check: must be org admin or collaboration manager
        if (!isOrgAdmin(currentUser, ownerOrgCode) && !isOrgAdmin(currentUser, partnerOrgCode) && !isCollaborationManager(currentUser, collaborationId)) {
            throw new AccessDeniedException("User must be an organization administrator or collaboration manager to manage group participants");
        }

        CollaborationGroupParticipantEntity participant = collaborationGroupParticipantRepository.findByCollaboration_IdAndGroup_Id(collaborationId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group participant not found in collaboration with ID: " + collaborationId));

        // Revoke all active collaboration-derived permissions for this target group in this collaboration BEFORE deletion
        List<PermissionEntity> activeCollabGroupPermissions = permissionRepository
                    .findByCollaboration_IdAndTargetGroup_IdAndRevokedFalse(collaborationId, groupId);
        for (PermissionEntity permission : activeCollabGroupPermissions) {
            permissionService.revokePermission(permission.getId(), currentUser.getId());
        }

        collaborationGroupParticipantRepository.delete(participant);

        // Record audit log for COLLABORATION_GROUP_REMOVED
        String groupOrgCode = participant.getGroup().getOrganization() != null ? participant.getGroup().getOrganization().getOrgCode() : null;
        AuditPermissionChangeCommand removeAuditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_GROUP_REMOVED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(collaborationId)
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToOrgCode(groupOrgCode)
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_GROUP_REMOVE_" + collaborationId + "_" + groupId)
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(removeAuditCmd);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CollaborationGroupParticipantResponseDto> getGroupParticipants(Long collaborationId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to get group participants");
        }

        CollaborationEntity collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        validateUserOrgAccess(currentUser, collaboration.getOwnerOrganization().getOrgCode(), collaboration.getPartnerOrganization().getOrgCode());

        List<CollaborationGroupParticipantEntity> list = collaborationGroupParticipantRepository.findByCollaboration_Id(collaborationId);
        return list.stream()
                .map(this::mapToGroupParticipantResponseDto)
                .collect(Collectors.toList());
    }

    private CollaborationGroupParticipantResponseDto mapToGroupParticipantResponseDto(CollaborationGroupParticipantEntity entity) {
        return CollaborationGroupParticipantResponseDto.builder()
                .id(entity.getId())
                .collaborationId(entity.getCollaboration().getId())
                .groupId(entity.getGroup().getId())
                .groupName(entity.getGroup().getName())
                .groupDescription(entity.getGroup().getDescription())
                .groupOrgCode(entity.getGroup().getOrganization() != null ? entity.getGroup().getOrganization().getOrgCode() : null)
                .createdAt(entity.getCreatedAt())
                .build();
    }


    @Override
    public CollaborationParticipantResponseDto assignManager(Long collaborationId, String userId, UserEntity currentUser) {
        log.info("Assigning manager role to user ID: {} for collaboration ID: {} by user: {}", userId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to assign manager");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Manager roles can only be assigned on ACTIVE collaborations");
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization check: ONLY Organization Administrator can assign Collaboration Manager
        if (!isOrgAdmin(currentUser, ownerOrgCode) && !isOrgAdmin(currentUser, partnerOrgCode)) {
            throw new AccessDeniedException("User must be an organization administrator to assign collaboration managers");
        }

        CollaborationParticipantEntity participant = collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaborationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found in collaboration with ID: " + collaborationId));

        if (!isUserInOrg(participant.getUser(), ownerOrgCode, partnerOrgCode)) {
            throw new IllegalArgumentException("User does not belong to either owner or partner organization of this collaboration as an active member");
        }

        participant.setCollaborationRole(CollaborationRole.MANAGER);
        CollaborationParticipantEntity saved = collaborationParticipantRepository.save(participant);

        if (permissionCacheService != null && participant.getUser() != null && participant.getUser().getEmail() != null) {
            permissionCacheService.evictUserPermissions(participant.getUser().getEmail());
        }

        // Record audit log for COLLABORATION_MANAGER_ASSIGNED
        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_MANAGER_ASSIGNED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(collaboration.getId())
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToEmail(participant.getUser().getEmail())
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_MANAGER_ASSIGN_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        return mapToParticipantResponseDto(saved);
    }

    @Override
    public CollaborationParticipantResponseDto removeManager(Long collaborationId, String userId, UserEntity currentUser) {
        log.info("Removing manager role from user ID: {} for collaboration ID: {} by user: {}", userId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to remove manager");
        }

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID is required");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Manager roles can only be removed on ACTIVE collaborations");
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization check: ONLY Organization Administrator can remove Collaboration Manager
        if (!isOrgAdmin(currentUser, ownerOrgCode) && !isOrgAdmin(currentUser, partnerOrgCode)) {
            throw new AccessDeniedException("User must be an organization administrator to remove collaboration managers");
        }

        CollaborationParticipantEntity participant = collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaborationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found in collaboration with ID: " + collaborationId));

        participant.setCollaborationRole(CollaborationRole.MEMBER);
        CollaborationParticipantEntity saved = collaborationParticipantRepository.save(participant);

        if (permissionCacheService != null && participant.getUser() != null && participant.getUser().getEmail() != null) {
            permissionCacheService.evictUserPermissions(participant.getUser().getEmail());
        }

        // Record audit log for COLLABORATION_MANAGER_REMOVED
        AuditPermissionChangeCommand removeAuditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_MANAGER_REMOVED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(collaborationId)
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToEmail(participant.getUser().getEmail())
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_MANAGER_REMOVE_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(removeAuditCmd);

        return mapToParticipantResponseDto(saved);
    }

    @Override
    public PermissionResponseDto grantCollaborationPermission(Long collaborationId, PermissionRequestDto request, UserEntity currentUser) {
        log.info("Granting permission for collaboration ID: {} by user: {}", collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to grant collaboration permission");
        }

        if (collaborationId == null || request == null) {
            throw new IllegalArgumentException("Collaboration ID and permission request cannot be null");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Collaboration is not ACTIVE. Current status: " + collaboration.getStatus());
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        boolean isAuthorized = currentUser.getRole() == Role.ROLE_SUPER_ADMIN
                || isOrgAdmin(currentUser, ownerOrgCode)
                || isOrgAdmin(currentUser, partnerOrgCode)
                || isCollaborationManager(currentUser, collaborationId);

        if (!isAuthorized) {
            throw new AccessDeniedException("User does not have authority to manage permissions for this collaboration");
        }

        FolderEntity rootFolder = collaboration.getRootFolder();
        if (rootFolder == null) {
            throw new IllegalStateException("Collaboration has no root folder configured");
        }

        if (request.getFolderId() != null) {
            FolderEntity targetFolder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target folder not found with ID: " + request.getFolderId()));

            if (!permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, targetFolder)) {
                throw new IllegalArgumentException("Target folder is outside the collaboration root folder scope");
            }
        } else if (request.getDocumentId() != null || (request.getDocumentFileHash() != null && !request.getDocumentFileHash().isBlank())) {
            DocumentEntity document = null;
            if (request.getDocumentId() != null) {
                document = documentRepository.findById(request.getDocumentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Target document not found with ID: " + request.getDocumentId()));
            } else {
                List<DocumentEntity> docs = documentRepository.findByFileHash(request.getDocumentFileHash());
                if (docs.isEmpty()) {
                    throw new ResourceNotFoundException("Target document not found with file hash: " + request.getDocumentFileHash());
                } else if (docs.size() == 1) {
                    document = docs.get(0);
                } else {
                    List<DocumentEntity> inScopeDocs = docs.stream()
                            .filter(d -> d.getFolder() != null && permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, d.getFolder()))
                            .toList();
                    if (inScopeDocs.size() == 1) {
                        document = inScopeDocs.get(0);
                    } else {
                        throw new IllegalArgumentException("Ambiguous document selection: multiple documents match file hash. Please provide documentId.");
                    }
                }
            }

            if (document.getFolder() == null || !permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, document.getFolder())) {
                throw new IllegalArgumentException("Target document is outside the collaboration root folder scope");
            }
        } else {
            throw new IllegalArgumentException("Permission target (folderId, documentId, or documentFileHash) must be provided");
        }

        if (request.getTargetUserEmail() != null && !request.getTargetUserEmail().isBlank()) {
            UserEntity targetUser = userRepository.findByEmail(request.getTargetUserEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Target user not found with email: " + request.getTargetUserEmail()));

            boolean isDirectParticipant = collaborationParticipantRepository.existsByCollaboration_IdAndUser_Id(collaborationId, targetUser.getId());
            boolean isGroupMemberParticipant = false;
            if (!isDirectParticipant && userGroupRepository != null) {
                List<CollaborationGroupParticipantEntity> groupParticipants = collaborationGroupParticipantRepository.findByCollaboration_Id(collaborationId);
                for (CollaborationGroupParticipantEntity gp : groupParticipants) {
                    if (gp.getGroup() != null && userGroupRepository.existsByUser_IdAndGroup_Id(targetUser.getId(), gp.getGroup().getId())) {
                        isGroupMemberParticipant = true;
                        break;
                    }
                }
            }

            if (!isDirectParticipant && !isGroupMemberParticipant) {
                throw new IllegalArgumentException("Target user is not a participant in this collaboration");
            }
        } else if (request.getTargetOrgCode() != null && !request.getTargetOrgCode().isBlank()) {
            if (!request.getTargetOrgCode().equalsIgnoreCase(ownerOrgCode) && !request.getTargetOrgCode().equalsIgnoreCase(partnerOrgCode)) {
                throw new IllegalArgumentException("Target organization is not a participant organization in this collaboration");
            }
        } else if (request.getTargetGroupId() != null) {
            boolean isGroupParticipant = collaborationGroupParticipantRepository.existsByCollaboration_IdAndGroup_Id(collaborationId, request.getTargetGroupId());
            if (!isGroupParticipant) {
                throw new IllegalArgumentException("Target group is not a group participant in this collaboration");
            }
        } else {
            throw new IllegalArgumentException("Permission target recipient (targetUserEmail, targetOrgCode, or targetGroupId) must be specified");
        }

        request.setCollaborationId(collaborationId);

        return permissionService.grantPermission(request, currentUser.getId());
    }

    @Override
    public PermissionResponseDto revokeCollaborationPermission(Long collaborationId, Long permissionId, UserEntity currentUser) {
        log.info("Revoking collaboration permission ID: {} for collaboration ID: {} by user: {}", permissionId, collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to revoke collaboration permission");
        }

        if (collaborationId == null || permissionId == null) {
            throw new IllegalArgumentException("Collaboration ID and permission ID cannot be null");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        if (collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            throw new IllegalStateException("Collaboration is not ACTIVE. Current status: " + collaboration.getStatus());
        }

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        boolean isAuthorized = currentUser.getRole() == Role.ROLE_SUPER_ADMIN
                || isOrgAdmin(currentUser, ownerOrgCode)
                || isOrgAdmin(currentUser, partnerOrgCode)
                || isCollaborationManager(currentUser, collaborationId);

        if (!isAuthorized) {
            throw new AccessDeniedException("User does not have authority to manage permissions for this collaboration");
        }

        PermissionEntity permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found with ID: " + permissionId));

        FolderEntity rootFolder = collaboration.getRootFolder();
        if (rootFolder == null) {
            throw new IllegalStateException("Collaboration has no root folder configured");
        }

        boolean inScope = false;
        if (permission.getFolder() != null) {
            inScope = permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, permission.getFolder());
        } else if (permission.getDocument() != null && permission.getDocument().getFolder() != null) {
            inScope = permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, permission.getDocument().getFolder());
        }

        if (!inScope) {
            throw new IllegalArgumentException("Permission target is outside the collaboration root folder scope");
        }

        return permissionService.revokePermission(permissionId, currentUser.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PermissionResponseDto> getCollaborationPermissions(Long collaborationId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to view collaboration permissions");
        }

        if (collaborationId == null) {
            throw new IllegalArgumentException("Collaboration ID cannot be null");
        }

        CollaborationEntity collaboration = collaborationRepository.findById(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        boolean isAuthorized = currentUser.getRole() == Role.ROLE_SUPER_ADMIN
                || isOrgAdmin(currentUser, ownerOrgCode)
                || isOrgAdmin(currentUser, partnerOrgCode)
                || isCollaborationManager(currentUser, collaborationId)
                || (collaborationParticipantRepository.existsByCollaboration_IdAndUser_Id(collaborationId, currentUser.getId())
                        && isUserInOrg(currentUser, ownerOrgCode, partnerOrgCode));

        if (!isAuthorized && userGroupRepository != null) {
            List<CollaborationGroupParticipantEntity> groupParticipants = collaborationGroupParticipantRepository.findByCollaboration_Id(collaborationId);
            for (CollaborationGroupParticipantEntity gp : groupParticipants) {
                if (gp.getGroup() != null && userGroupRepository.existsByUser_IdAndGroup_Id(currentUser.getId(), gp.getGroup().getId())
                        && isUserInOrg(currentUser, ownerOrgCode, partnerOrgCode)) {
                    isAuthorized = true;
                    break;
                }
            }
        }

        if (!isAuthorized) {
            throw new AccessDeniedException("User is not authorized to view permissions for this collaboration");
        }

        FolderEntity rootFolder = collaboration.getRootFolder();
        if (rootFolder == null) {
            return List.of();
        }

        List<PermissionEntity> allActivePermissions = permissionRepository.findByRevokedFalse();
        return allActivePermissions.stream()
                .filter(p -> {
                    if (p.getFolder() != null) {
                        return permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, p.getFolder());
                    } else if (p.getDocument() != null && p.getDocument().getFolder() != null) {
                        return permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, p.getDocument().getFolder());
                    }
                    return false;
                })
                .map(PermissionMapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    public CollaborationResponseDto revokeCollaboration(Long collaborationId, UserEntity currentUser) {
        log.info("Revoking collaboration ID: {} by user: {}", collaborationId, currentUser != null ? currentUser.getId() : "null");

        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required to revoke collaboration");
        }

        if (collaborationId == null) {
            throw new IllegalArgumentException("Collaboration ID cannot be null");
        }

        CollaborationEntity collaboration = collaborationRepository.findByIdForUpdate(collaborationId)
                .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));

        String ownerOrgCode = collaboration.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collaboration.getPartnerOrganization().getOrgCode();

        // Authorization: only ROLE_ORG_ADMIN of owner/partner org or ROLE_SUPER_ADMIN
        // Collaboration Manager must NOT be allowed to revoke the entire collaboration
        boolean isOrgLevelAdmin = currentUser.getRole() == Role.ROLE_SUPER_ADMIN
                || isOrgAdmin(currentUser, ownerOrgCode)
                || isOrgAdmin(currentUser, partnerOrgCode);

        if (!isOrgLevelAdmin) {
            throw new AccessDeniedException(
                    "Only organization administrators (owner or partner) or SUPER_ADMIN can revoke a collaboration");
        }

        if (collaboration.getStatus() == CollaborationStatus.REVOKED) {
            log.info("Collaboration ID: {} is already REVOKED — no action taken", collaborationId);
            return mapToResponseDto(collaboration);
        }

        if (collaboration.getStatus() == CollaborationStatus.EXPIRED) {
            log.info("Collaboration ID: {} is already EXPIRED — no action taken", collaborationId);
            return mapToResponseDto(collaboration);
        }

        // Teardown collaboration-derived permissions only if it was ACTIVE
        if (collaboration.getStatus() == CollaborationStatus.ACTIVE) {
            performCollaborationTeardown(collaboration, currentUser.getId());
        }

        collaboration.setStatus(CollaborationStatus.REVOKED);
        CollaborationEntity saved = collaborationRepository.save(collaboration);

        AuditPermissionChangeCommand auditCmd = AuditPermissionChangeCommand.builder()
                .action("COLLABORATION_REVOKED")
                .targetType("FOLDER")
                .targetId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .folderId(collaboration.getRootFolder() != null ? collaboration.getRootFolder().getId() : null)
                .workspaceId(null)
                .collaborationId(saved.getId())
                .spaceType(collaboration.getRootFolder() != null && collaboration.getRootFolder().getOwnerOrg() != null ? SpaceType.ORGANIZATION : SpaceType.PERSONAL)
                .orgCode(ownerOrgCode)
                .actorUserId(currentUser != null ? currentUser.getId() : "SYSTEM")
                .grantedBy(currentUser)
                .grantedToOrgCode(partnerOrgCode)
                .permissionLevel(PermissionLevel.VIEW)
                .recordHash("COLLAB_REVOKED_" + saved.getId())
                .blockchainStatus(BlockchainStatus.PENDING)
                .timestamp(Instant.now())
                .build();
        if (auditLogService != null) auditLogService.logPermissionChange(auditCmd);

        List<UserEntity> revokeRecipients = new ArrayList<>();
        if (saved.getCreatedBy() != null) {
            revokeRecipients.add(saved.getCreatedBy());
        }
        revokeRecipients.addAll(getOrgAdmins(ownerOrgCode));
        revokeRecipients.addAll(getOrgAdmins(partnerOrgCode));
        sendCollaborationNotification(
                revokeRecipients, 
                currentUser, 
                NotificationType.COLLABORATION_REVOKED, 
                "Collaboration Revoked", 
                String.format("Collaboration '%s' has been revoked.", saved.getName()), 
                String.valueOf(saved.getId())
        );

        log.info("Collaboration ID: {} successfully revoked", collaborationId);
        return mapToResponseDto(saved);
    }

    /**
     * Revokes all active collaboration-derived PermissionEntity records for the given collaboration.
     * Only permissions where permission.collaboration.id = collaboration.id are affected.
     * Uses the centralized PermissionService pipeline (DB + hash + audit + Kafka + Redis).
     * Safe to call multiple times — already-revoked permissions are ignored (idempotent).
     * Called by: revokeCollaboration (explicit) and CollaborationExpirationScheduler (automated).
     */
    public void performCollaborationTeardown(CollaborationEntity collaboration, String revokedByUserId) {
        Long collaborationId = collaboration.getId();
        log.info("Starting collaboration-wide permission teardown for collaboration ID: {}", collaborationId);

        List<PermissionEntity> activePermissions =
                permissionRepository.findByCollaboration_IdAndRevokedFalse(collaborationId);

        if (activePermissions.isEmpty()) {
            log.info("Collaboration ID: {} has no active collaboration-derived permissions to revoke", collaborationId);
            return;
        }

        log.info("Found {} active permission(s) to revoke for collaboration ID: {}",
                activePermissions.size(), collaborationId);

        int revokedCount = 0;
        for (PermissionEntity permission : activePermissions) {
            try {
                permissionService.revokePermission(permission.getId(), revokedByUserId);
                revokedCount++;
            } catch (Exception e) {
                log.error("Failed to revoke permission ID: {} during collaboration {} teardown: {}",
                        permission.getId(), collaborationId, e.getMessage());
            }
        }

        log.info("Collaboration ID: {} teardown complete — {} permission(s) revoked", collaborationId, revokedCount);
    }

    private boolean isCollaborationManager(UserEntity currentUser, Long collaborationId) {
        if (currentUser == null || collaborationId == null) {
            return false;
        }
        Optional<CollaborationParticipantEntity> participantOpt =
                collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaborationId, currentUser.getId());
        if (participantOpt.isEmpty() || participantOpt.get().getCollaborationRole() != CollaborationRole.MANAGER) {
            return false;
        }
        CollaborationEntity collaboration = participantOpt.get().getCollaboration();
        if (collaboration != null && collaboration.getStatus() != CollaborationStatus.ACTIVE) {
            return false;
        }
        String ownerOrgCode = collaboration != null && collaboration.getOwnerOrganization() != null
                ? collaboration.getOwnerOrganization().getOrgCode() : null;
        String partnerOrgCode = collaboration != null && collaboration.getPartnerOrganization() != null
                ? collaboration.getPartnerOrganization().getOrgCode() : null;
        return isUserInOrg(currentUser, ownerOrgCode, partnerOrgCode);
    }

    private boolean isOrgAdmin(UserEntity currentUser, String orgCode) {
        if (currentUser == null || orgCode == null || orgCode.isBlank()) {
            return false;
        }
        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        return userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), orgCode)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE
                        && (m.getRole() == Role.ROLE_ORG_ADMIN || m.getRole() == Role.ROLE_SUPER_ADMIN))
                .orElse(false);
    }

    private void validateUserOrgAccess(UserEntity currentUser, String ownerOrgCode, String partnerOrgCode) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        if (!isUserInOrg(currentUser, ownerOrgCode, partnerOrgCode)) {
            throw new AccessDeniedException("User is not an active member of either owner or partner organization of this collaboration");
        }
    }

    private void validateUserInOrg(UserEntity currentUser, String orgCode) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if (currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        boolean isMember = orgCode != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                currentUser.getId(), orgCode, MembershipStatus.ACTIVE);
        if (!isMember) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }
    }

    private boolean isUserInOrg(UserEntity targetUser, String ownerOrgCode, String partnerOrgCode) {
        if (targetUser == null) {
            return false;
        }
        String userId = targetUser.getId();
        boolean inOwner = ownerOrgCode != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                userId, ownerOrgCode, MembershipStatus.ACTIVE);
        boolean inPartner = partnerOrgCode != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                userId, partnerOrgCode, MembershipStatus.ACTIVE);
        return inOwner || inPartner;
    }

    private String determineUserOrgCode(UserEntity targetUser, String ownerOrgCode, String partnerOrgCode) {
        if (targetUser == null) {
            return null;
        }
        String userId = targetUser.getId();
        if (ownerOrgCode != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                userId, ownerOrgCode, MembershipStatus.ACTIVE)) {
            return ownerOrgCode;
        }
        if (partnerOrgCode != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(
                userId, partnerOrgCode, MembershipStatus.ACTIVE)) {
            return partnerOrgCode;
        }
        return null;
    }

    private CollaborationResponseDto mapToResponseDto(CollaborationEntity entity) {
        return CollaborationResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .ownerOrgCode(entity.getOwnerOrganization() != null ? entity.getOwnerOrganization().getOrgCode() : null)
                .ownerOrgName(entity.getOwnerOrganization() != null ? entity.getOwnerOrganization().getName() : null)
                .partnerOrgCode(entity.getPartnerOrganization() != null ? entity.getPartnerOrganization().getOrgCode() : null)
                .partnerOrgName(entity.getPartnerOrganization() != null ? entity.getPartnerOrganization().getName() : null)
                .rootFolderId(entity.getRootFolder() != null ? entity.getRootFolder().getId() : null)
                .rootFolderName(entity.getRootFolder() != null ? entity.getRootFolder().getName() : null)
                .createdByUserId(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdByUserEmail(entity.getCreatedBy() != null ? entity.getCreatedBy().getEmail() : null)
                .status(entity.getStatus())
                .startsAt(entity.getStartsAt())
                .endsAt(entity.getEndsAt())
                .acceptedByUserId(entity.getAcceptedBy() != null ? entity.getAcceptedBy().getId() : null)
                .acceptedByUserEmail(entity.getAcceptedBy() != null ? entity.getAcceptedBy().getEmail() : null)
                .acceptedAt(entity.getAcceptedAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CollaborationParticipantResponseDto mapToParticipantResponseDto(CollaborationParticipantEntity entity) {
        String ownerOrg = entity.getCollaboration().getOwnerOrganization().getOrgCode();
        String partnerOrg = entity.getCollaboration().getPartnerOrganization().getOrgCode();
        String userOrgCode = determineUserOrgCode(entity.getUser(), ownerOrg, partnerOrg);

        return CollaborationParticipantResponseDto.builder()
                .id(entity.getId())
                .collaborationId(entity.getCollaboration().getId())
                .userId(entity.getUser().getId())
                .userEmail(entity.getUser().getEmail())
                .userFirstName(entity.getUser().getFirstName())
                .userLastName(entity.getUser().getLastName())
                .userOrgCode(userOrgCode)
                .collaborationRole(entity.getCollaborationRole())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public void sendCollaborationExpiredNotification(CollaborationEntity collaboration) {
        if (collaboration == null) return;
        String ownerOrgCode = collaboration.getOwnerOrganization() != null ? collaboration.getOwnerOrganization().getOrgCode() : null;
        String partnerOrgCode = collaboration.getPartnerOrganization() != null ? collaboration.getPartnerOrganization().getOrgCode() : null;

        List<UserEntity> recipients = new ArrayList<>();
        if (collaboration.getCreatedBy() != null) {
            recipients.add(collaboration.getCreatedBy());
        }
        recipients.addAll(getOrgAdmins(ownerOrgCode));
        recipients.addAll(getOrgAdmins(partnerOrgCode));

        sendCollaborationNotification(
                recipients, 
                null, 
                NotificationType.COLLABORATION_EXPIRED, 
                "Collaboration Expired", 
                String.format("Collaboration '%s' has expired.", collaboration.getName()), 
                String.valueOf(collaboration.getId())
        );
    }

    private void sendCollaborationNotification(List<UserEntity> recipients, UserEntity actor, NotificationType type, String title, String body, String sourceId) {
        if (recipients == null || notificationService == null) {
            return;
        }
        Set<String> notifiedUserIds = new HashSet<>();
        for (UserEntity recipient : recipients) {
            if (recipient != null && recipient.getId() != null && notifiedUserIds.add(recipient.getId())) {
                try {
                    String key = type.name() + ":" + sourceId + ":" + recipient.getId();
                    notificationService.createNotification(
                            recipient,
                            actor,
                            type,
                            title,
                            body,
                            NotificationSourceType.COLLABORATION,
                            sourceId,
                            null,
                            null,
                            key
                    );
                } catch (Exception e) {
                    log.error("Failed to send notification {} to user {}: {}", type, recipient.getId(), e.getMessage());
                }
            }
        }
    }

    private List<UserEntity> getOrgAdmins(String orgCode) {
        List<UserEntity> admins = new ArrayList<>();
        if (orgCode == null || orgCode.isBlank()) {
            return admins;
        }
        List<UserOrganizationEntity> members = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(orgCode, MembershipStatus.ACTIVE);
        for (UserOrganizationEntity member : members) {
            if (member.getUser() != null && (member.getRole() == Role.ROLE_ORG_ADMIN || member.getRole() == Role.ROLE_SUPER_ADMIN)) {
                admins.add(member.getUser());
            }
        }
        return admins;
    }
}
