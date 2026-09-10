package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.dto.CreateOrganizationInvitationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationInvitationResponseDto;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationInvitationRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationInvitationServiceImpl implements OrganizationInvitationService {

    private final OrganizationInvitationRepository invitationRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final PermissionCacheService permissionCacheService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final CapabilityEvaluatorService capabilityEvaluatorService;

    private static final int DEFAULT_EXPIRATION_DAYS = 7;

    @Override
    @Transactional
    public OrganizationInvitationResponseDto createInvitation(String orgCode, CreateOrganizationInvitationRequestDto request, UserEntity currentUser) {
        OrganizationEntity org = organizationRepository.findByOrgCodeForUpdate(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));

        capabilityEvaluatorService.validateInvitationTargetRole(currentUser, orgCode, request.getRole() != null ? request.getRole().name() : null);

        if (request.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalArgumentException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
        }

        if (request.getTargetGroupRole() != null && request.getTargetGroupRole() != GroupMemberRole.MEMBER) {
            throw new IllegalArgumentException("Cannot assign Team Manager role via organization invitation. Team Manager role must be transferred via transferTeamManager.");
        }

        String inviteeEmail = request.getInviteeEmail().trim().toLowerCase();

        // 1. Check existing organization membership according to lifecycle rules:
        // ACTIVE -> rejected
        // SUSPENDED -> rejected
        // REMOVED -> allowed
        if (userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(inviteeEmail, orgCode, MembershipStatus.ACTIVE)) {
            throw new IllegalArgumentException("User with email '" + inviteeEmail + "' is already an active member of organization: " + orgCode);
        }

        if (userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(inviteeEmail, orgCode, MembershipStatus.SUSPENDED)) {
            throw new IllegalArgumentException("User with email '" + inviteeEmail + "' is suspended in organization " + orgCode + ". Suspended members cannot receive invitations.");
        }

        // 2. Check if an active PENDING invitation already exists for this email & org
        if (invitationRepository.existsByOrganization_OrgCodeAndInviteeEmailAndStatus(orgCode, inviteeEmail, OrganizationInvitationStatus.PENDING)) {
            throw new IllegalArgumentException("A pending invitation already exists for email: " + inviteeEmail + " in organization: " + orgCode);
        }

        // 3. Optional Onboarding Assignments validation
        GroupEntity targetGroup = null;
        if (request.getTargetGroupId() != null) {
            targetGroup = groupRepository.findByIdAndOrganization_OrgCode(request.getTargetGroupId(), orgCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Target group not found with ID: " + request.getTargetGroupId() + " in org: " + orgCode));

            if (targetGroup.getStatus() == GroupStatus.ARCHIVED) {
                throw new IllegalStateException("Cannot assign archived group for onboarding: " + request.getTargetGroupId());
            }
        }

        ProjectWorkspaceEntity targetWorkspace = null;
        if (request.getTargetWorkspaceId() != null) {
            targetWorkspace = projectWorkspaceRepository.findById(request.getTargetWorkspaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Target workspace not found with ID: " + request.getTargetWorkspaceId()));

            if (!targetWorkspace.getOrganization().getOrgCode().equalsIgnoreCase(orgCode)) {
                throw new IllegalArgumentException("Target workspace ID " + request.getTargetWorkspaceId() + " does not belong to organization: " + orgCode);
            }

            if (targetWorkspace.getStatus() == ProjectWorkspaceStatus.ARCHIVED) {
                throw new IllegalStateException("Cannot assign archived workspace for onboarding: " + request.getTargetWorkspaceId());
            }
        }

        // Check if target invitee registered user exists
        Optional<UserEntity> existingUserOpt = userRepository.findByEmail(inviteeEmail);

        int expDays = (request.getExpirationDays() != null && request.getExpirationDays() > 0)
                ? request.getExpirationDays()
                : DEFAULT_EXPIRATION_DAYS;

        Instant expiresAt = Instant.now().plus(Duration.ofDays(expDays));
        String token = UUID.randomUUID().toString();

        Role assignedRole = request.getRole() != null ? request.getRole() : Role.ROLE_USER;
        GroupMemberRole targetGroupRole = (targetGroup != null) ? GroupMemberRole.MEMBER : null;

        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .token(token)
                .organization(org)
                .inviter(currentUser)
                .inviteeEmail(inviteeEmail)
                .invitee(existingUserOpt.orElse(null))
                .role(assignedRole)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(expiresAt)
                .note(request.getNote())
                .targetGroup(targetGroup)
                .targetGroupRole(targetGroup != null ? targetGroupRole : null)
                .targetWorkspace(targetWorkspace)
                .targetFolderId(request.getTargetFolderId())
                .targetDocumentHash(request.getTargetDocumentHash())
                .onboardingPermissionLevel(request.getOnboardingPermissionLevel())
                .build();

        OrganizationInvitationEntity savedInvitation = invitationRepository.save(invitation);

        log.info("Created invitation ID {} (token {}) for email {} to join org {} by inviter {}",
                savedInvitation.getId(), savedInvitation.getToken(), inviteeEmail, orgCode, currentUser.getId());

        // Emit notification if invitee registered user exists
        if (existingUserOpt.isPresent() && notificationService != null) {
            String inviteSentKey = "ORG_INVITATION_SENT:" + savedInvitation.getId() + ":" + existingUserOpt.get().getId();
            notificationService.createNotification(
                    existingUserOpt.get(),
                    currentUser,
                    NotificationType.ORG_INVITATION_SENT,
                    "Organization Invitation",
                    "You have been invited to join organization: " + org.getName(),
                    NotificationSourceType.ORGANIZATION,
                    String.valueOf(savedInvitation.getId()),
                    NotificationCategory.INVITATION,
                    NotificationPriority.MEDIUM,
                    inviteSentKey
            );
        }

        // Audit Log
        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "CREATE_INVITATION",
                    "ORGANIZATION",
                    savedInvitation.getId(),
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    inviteeEmail,
                    null,
                    null,
                    null,
                    OrganizationInvitationStatus.PENDING.name(),
                    request.getNote(),
                    null,
                    null,
                    null
            );
        }

        return mapToInvitationResponse(savedInvitation);
    }

    @Override
    @Transactional
    public OrganizationInvitationResponseDto acceptInvitation(String token, UserEntity currentUser) {
        OrganizationInvitationEntity invitation = invitationRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found with token: " + token));

        // Evaluate lazy expiration
        if (expireInvitationIfPending(invitation)) {
            throw new IllegalStateException("Invitation has expired");
        }

        if (invitation.getStatus() != OrganizationInvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is no longer pending (status: " + invitation.getStatus() + ")");
        }

        // STRICT SECURITY INVARIANT: Authenticated user email must match invitation inviteeEmail
        if (!currentUser.getEmail().equalsIgnoreCase(invitation.getInviteeEmail())) {
            throw new AccessDeniedException("Authenticated user email (" + currentUser.getEmail()
                    + ") does not match invitation invitee email (" + invitation.getInviteeEmail() + ")");
        }

        if (invitation.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalStateException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
        }

        OrganizationEntity org = invitation.getOrganization();

        // 1. Create or Reactivate Organization Membership
        Role assignedRole = invitation.getRole() != null ? invitation.getRole() : Role.ROLE_USER;
        if (assignedRole == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalStateException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
        }

        Optional<UserOrganizationEntity> existingMembershipOpt = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), org.getOrgCode());
        if (existingMembershipOpt.isPresent()) {
            UserOrganizationEntity membership = existingMembershipOpt.get();
            if (membership.getStatus() == MembershipStatus.SUSPENDED) {
                throw new IllegalStateException("Cannot accept invitation while membership is suspended in organization: " + org.getOrgCode());
            } else if (membership.getStatus() == MembershipStatus.REMOVED) {
                membership.setStatus(MembershipStatus.ACTIVE);
                membership.setRole(assignedRole);
                membership.setStatusUpdatedAt(Instant.now());
                membership.setStatusReason("Reactivated via accepted invitation");
                membership.setUpdatedByUserId(invitation.getInviter() != null ? invitation.getInviter().getId() : null);
                userOrganizationRepository.save(membership);
                log.info("Reactivated REMOVED user-organization membership for user {} in org {} with role {}",
                        currentUser.getId(), org.getOrgCode(), assignedRole);
            }
            // If already ACTIVE, preserve existing membership without duplicating
        } else {
            UserOrganizationEntity membership = UserOrganizationEntity.builder()
                    .user(currentUser)
                    .organization(org)
                    .role(assignedRole)
                    .status(MembershipStatus.ACTIVE)
                    .build();
            userOrganizationRepository.save(membership);
            log.info("Created user-organization membership for user {} in org {}", currentUser.getId(), org.getOrgCode());
        }

        if (currentUser.getOrganization() == null) {
            currentUser.setOrganization(org);
            userRepository.save(currentUser);
        }

        if (permissionCacheService != null) {
            permissionCacheService.evictUserPermissions(currentUser.getEmail());
        }

        // 2. Sequential Onboarding Assignments
        // A. Team / Group assignment
        if (invitation.getTargetGroup() != null) {
            GroupEntity group = invitation.getTargetGroup();
            if (group.getStatus() == GroupStatus.ACTIVE) {
                if (!userGroupRepository.existsByUser_IdAndGroup_Id(currentUser.getId(), group.getId())) {
                    if (invitation.getTargetGroupRole() == GroupMemberRole.MANAGER) {
                        log.warn("Onboarding invitation {} contained MANAGER role; normalizing to MEMBER for user {}", invitation.getId(), currentUser.getId());
                    }

                    UserGroupEntity userGroup = UserGroupEntity.builder()
                            .user(currentUser)
                            .group(group)
                            .role(GroupMemberRole.MEMBER)
                            .build();
                    userGroupRepository.save(userGroup);
                    log.info("Onboarding: Added user {} to group {} with role {}", currentUser.getId(), group.getId(), GroupMemberRole.MEMBER);
                }
            }
        }

        // B. Resource / Workspace permission assignment
        PermissionLevel permLevel = invitation.getOnboardingPermissionLevel() != null
                ? invitation.getOnboardingPermissionLevel()
                : PermissionLevel.VIEW;

        String orgCode = org.getOrgCode();

        if (invitation.getTargetFolderId() != null) {
            grantFolderPermission(invitation.getTargetFolderId(), currentUser.getEmail(), permLevel, invitation.getInviter().getId(), orgCode);
        } else if (invitation.getTargetWorkspace() != null && invitation.getTargetWorkspace().getRootFolder() != null) {
            grantFolderPermission(invitation.getTargetWorkspace().getRootFolder().getId(), currentUser.getEmail(), permLevel, invitation.getInviter().getId(), orgCode);
        } else if (invitation.getTargetDocumentHash() != null) {
            grantDocumentPermission(invitation.getTargetDocumentHash(), currentUser.getEmail(), permLevel, invitation.getInviter().getId(), orgCode);
        }

        // 3. Mark Invitation Accepted
        invitation.setStatus(OrganizationInvitationStatus.ACCEPTED);
        invitation.setInvitee(currentUser);
        invitation.setRespondedAt(Instant.now());
        OrganizationInvitationEntity updatedInvitation = invitationRepository.save(invitation);

        log.info("User {} successfully accepted invitation ID {}", currentUser.getId(), updatedInvitation.getId());

        // 4. Notifications
        if (notificationService != null) {
            // Notify Inviter
            String acceptedKey = "ORG_INVITATION_ACCEPTED:" + updatedInvitation.getId() + ":" + invitation.getInviter().getId();
            notificationService.createNotification(
                    invitation.getInviter(),
                    currentUser,
                    NotificationType.ORG_INVITATION_ACCEPTED,
                    "Invitation Accepted",
                    currentUser.getEmail() + " accepted your invitation to join " + org.getName(),
                    NotificationSourceType.ORGANIZATION,
                    String.valueOf(updatedInvitation.getId()),
                    NotificationCategory.INVITATION,
                    NotificationPriority.MEDIUM,
                    acceptedKey
            );

            // Notify Invitee (CurrentUser) on onboarding completion
            // ONBOARDING_COMPLETED is a lifecycle singleton: one record per invitation per user
            String onboardingKey = "ONBOARDING_COMPLETED:" + updatedInvitation.getId() + ":" + currentUser.getId();
            notificationService.createNotification(
                    currentUser,
                    currentUser,
                    NotificationType.ONBOARDING_COMPLETED,
                    "Onboarding Completed",
                    "Onboarding completed for organization: " + org.getName(),
                    NotificationSourceType.ORGANIZATION,
                    String.valueOf(updatedInvitation.getId()),
                    NotificationCategory.INVITATION,
                    NotificationPriority.LOW,
                    onboardingKey
            );
        }

        // 5. Audit Log with onboarding context details
        if (auditLogService != null) {
            StringBuilder detailsBuilder = new StringBuilder();
            detailsBuilder.append("AssignedRole=").append(updatedInvitation.getRole() != null ? updatedInvitation.getRole().name() : "ROLE_USER");
            if (updatedInvitation.getTargetGroup() != null) {
                detailsBuilder.append(";TargetGroupId=").append(updatedInvitation.getTargetGroup().getId());
                detailsBuilder.append(";TargetGroupName=").append(updatedInvitation.getTargetGroup().getName());
                if (updatedInvitation.getTargetGroupRole() != null) {
                    detailsBuilder.append(";TargetGroupRole=").append(updatedInvitation.getTargetGroupRole().name());
                }
            }
            if (updatedInvitation.getTargetWorkspace() != null) {
                detailsBuilder.append(";TargetWorkspaceId=").append(updatedInvitation.getTargetWorkspace().getId());
                detailsBuilder.append(";TargetWorkspaceName=").append(updatedInvitation.getTargetWorkspace().getName());
            }
            if (updatedInvitation.getTargetFolderId() != null) {
                detailsBuilder.append(";TargetFolderId=").append(updatedInvitation.getTargetFolderId());
            }
            if (updatedInvitation.getTargetDocumentHash() != null) {
                detailsBuilder.append(";TargetDocumentHash=").append(updatedInvitation.getTargetDocumentHash());
            }
            if (updatedInvitation.getOnboardingPermissionLevel() != null) {
                detailsBuilder.append(";OnboardingPermissionLevel=").append(updatedInvitation.getOnboardingPermissionLevel().name());
            }

            auditLogService.logPermissionChange(
                    "ACCEPT_INVITATION",
                    "ORGANIZATION",
                    updatedInvitation.getId(),
                    null,
                    null,
                    null,
                    org.getOrgCode(),
                    currentUser,
                    currentUser.getEmail(),
                    null,
                    null,
                    OrganizationInvitationStatus.PENDING.name(),
                    OrganizationInvitationStatus.ACCEPTED.name(),
                    null,
                    detailsBuilder.toString(),
                    null,
                    null
            );
        }

        return mapToInvitationResponse(updatedInvitation);
    }

    @Override
    @Transactional
    public OrganizationInvitationResponseDto declineInvitation(String token, UserEntity currentUser) {
        OrganizationInvitationEntity invitation = invitationRepository.findByTokenForUpdate(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found with token: " + token));

        // Evaluate lazy expiration
        if (expireInvitationIfPending(invitation)) {
            throw new IllegalStateException("Invitation has expired");
        }

        if (invitation.getStatus() != OrganizationInvitationStatus.PENDING) {
            throw new IllegalStateException("Invitation is no longer pending (status: " + invitation.getStatus() + ")");
        }

        // STRICT SECURITY INVARIANT: Authenticated user email must match invitation inviteeEmail
        if (!currentUser.getEmail().equalsIgnoreCase(invitation.getInviteeEmail())) {
            throw new AccessDeniedException("Authenticated user email (" + currentUser.getEmail()
                    + ") does not match invitation invitee email (" + invitation.getInviteeEmail() + ")");
        }

        invitation.setStatus(OrganizationInvitationStatus.DECLINED);
        invitation.setInvitee(currentUser);
        invitation.setRespondedAt(Instant.now());
        OrganizationInvitationEntity updatedInvitation = invitationRepository.save(invitation);

        log.info("User {} declined invitation ID {}", currentUser.getId(), updatedInvitation.getId());

        // Notify Inviter
        if (notificationService != null) {
            String declinedKey = "ORG_INVITATION_DECLINED:" + updatedInvitation.getId() + ":" + invitation.getInviter().getId();
            notificationService.createNotification(
                    invitation.getInviter(),
                    currentUser,
                    NotificationType.ORG_INVITATION_DECLINED,
                    "Invitation Declined",
                    currentUser.getEmail() + " declined your invitation to join " + invitation.getOrganization().getName(),
                    NotificationSourceType.ORGANIZATION,
                    String.valueOf(updatedInvitation.getId()),
                    NotificationCategory.INVITATION,
                    NotificationPriority.MEDIUM,
                    declinedKey
            );
        }

        // Audit Log
        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "DECLINE_INVITATION",
                    "ORGANIZATION",
                    updatedInvitation.getId(),
                    null,
                    null,
                    null,
                    invitation.getOrganization().getOrgCode(),
                    currentUser,
                    currentUser.getEmail(),
                    null,
                    null,
                    OrganizationInvitationStatus.PENDING.name(),
                    OrganizationInvitationStatus.DECLINED.name(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return mapToInvitationResponse(updatedInvitation);
    }

    @Override
    @Transactional
    public void revokeInvitation(String orgCode, Long invitationId, UserEntity currentUser) {
        OrganizationInvitationEntity invitation = invitationRepository.findByIdForUpdate(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found with ID: " + invitationId));

        if (!invitation.getOrganization().getOrgCode().equalsIgnoreCase(orgCode)) {
            throw new IllegalArgumentException("Invitation ID " + invitationId + " does not belong to org: " + orgCode);
        }

        // Allow revocation if caller has ORG_MEMBERS_INVITE capability OR is original Inviter
        boolean isInviter = invitation.getInviter().getId().equals(currentUser.getId());
        if (!isInviter) {
            capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_MEMBERS_INVITE);
        }

        // Check if invitation has expired by elapsed time
        if (expireInvitationIfPending(invitation)) {
            throw new IllegalStateException("Invitation has expired and cannot be revoked");
        }

        if (invitation.getStatus() != OrganizationInvitationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING invitations can be revoked (current status: " + invitation.getStatus() + ")");
        }

        invitation.setStatus(OrganizationInvitationStatus.REVOKED);
        invitation.setRespondedAt(Instant.now());
        invitationRepository.save(invitation);

        log.info("Revoked invitation ID {} in org {} by user {}", invitationId, orgCode, currentUser.getId());

        // Notify invitee if registered
        if (invitation.getInvitee() != null && notificationService != null) {
            String revokedKey = "ORG_INVITATION_REVOKED:" + invitationId + ":" + invitation.getInvitee().getId();
            notificationService.createNotification(
                    invitation.getInvitee(),
                    currentUser,
                    NotificationType.ORG_INVITATION_REVOKED,
                    "Invitation Revoked",
                    "Invitation to join organization " + invitation.getOrganization().getName() + " was revoked",
                    NotificationSourceType.ORGANIZATION,
                    String.valueOf(invitationId),
                    NotificationCategory.INVITATION,
                    NotificationPriority.MEDIUM,
                    revokedKey
            );
        }

        // Audit Log
        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "REVOKE_INVITATION",
                    "ORGANIZATION",
                    invitationId,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    invitation.getInviteeEmail(),
                    null,
                    null,
                    OrganizationInvitationStatus.PENDING.name(),
                    OrganizationInvitationStatus.REVOKED.name(),
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    @Override
    @Transactional
    public List<OrganizationInvitationResponseDto> getPendingInvitationsForOrganization(String orgCode, UserEntity currentUser) {
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_MEMBERS_INVITE);

        // Perform lazy expiration check for PENDING invitations
        List<OrganizationInvitationEntity> pendingInvitations = invitationRepository.findByOrganization_OrgCodeAndStatus(orgCode, OrganizationInvitationStatus.PENDING);
        pendingInvitations.forEach(this::expireInvitationIfPending);

        return pendingInvitations.stream()
                .filter(inv -> inv.getStatus() == OrganizationInvitationStatus.PENDING)
                .map(this::mapToInvitationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<OrganizationInvitationResponseDto> getMyPendingInvitations(UserEntity currentUser) {
        String email = currentUser.getEmail().trim().toLowerCase();
        List<OrganizationInvitationEntity> pendingInvitations = invitationRepository.findByInviteeEmailAndStatus(email, OrganizationInvitationStatus.PENDING);

        pendingInvitations.forEach(this::expireInvitationIfPending);

        return pendingInvitations.stream()
                .filter(inv -> inv.getStatus() == OrganizationInvitationStatus.PENDING)
                .map(this::mapToInvitationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrganizationInvitationResponseDto getInvitationByToken(String token) {
        OrganizationInvitationEntity invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found with token: " + token));

        expireInvitationIfPending(invitation);

        return mapToInvitationResponse(invitation);
    }

    @Override
    @Transactional
    public int processExpiredInvitations() {
        List<OrganizationInvitationEntity> expired = invitationRepository.findByStatusAndExpiresAtBefore(OrganizationInvitationStatus.PENDING, Instant.now());
        if (expired.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (OrganizationInvitationEntity inv : expired) {
            Optional<OrganizationInvitationEntity> lockedOpt = invitationRepository.findByIdForUpdate(inv.getId());
            if (lockedOpt.isPresent()) {
                OrganizationInvitationEntity lockedInv = lockedOpt.get();
                if (expireInvitationIfPending(lockedInv)) {
                    count++;
                }
            }
        }
        log.info("Processed and marked {} expired organization invitations", count);
        return count;
    }

    private boolean expireInvitationIfPending(OrganizationInvitationEntity invitation) {
        if (invitation.getStatus() == OrganizationInvitationStatus.PENDING 
                && invitation.getExpiresAt() != null 
                && invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(OrganizationInvitationStatus.EXPIRED);
            invitationRepository.save(invitation);

            if (auditLogService != null) {
                auditLogService.logPermissionChange(
                        "EXPIRED_INVITATION",
                        "ORGANIZATION",
                        invitation.getId(),
                        null,
                        null,
                        null,
                        invitation.getOrganization() != null ? invitation.getOrganization().getOrgCode() : null,
                        null,
                        invitation.getInviteeEmail(),
                        null,
                        null,
                        OrganizationInvitationStatus.PENDING.name(),
                        OrganizationInvitationStatus.EXPIRED.name(),
                        "Invitation expired",
                        null,
                        null,
                        null
                );
            }
            return true;
        }
        return false;
    }

    private void validateOrgAdminPermission(UserEntity user, String orgCode) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (membership.getRole() != Role.ROLE_ORG_ADMIN && membership.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Admin privileges required in organization: " + orgCode);
        }
    }

    private void grantFolderPermission(Long folderId, String userEmail, PermissionLevel level, String grantedByUserId, String orgCode) {
        if (permissionService == null) return;
        try {
            PermissionRequestDto dto = PermissionRequestDto.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(level)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetOrgCode(orgCode)
                    .folderId(folderId)
                    .targetUserEmail(userEmail)
                    .build();
            permissionService.grantPermission(dto, grantedByUserId);
        } catch (Exception e) {
            log.error("Failed to grant folder permission during invitation onboarding for folderId {}: {}", folderId, e.getMessage());
        }
    }

    private void grantDocumentPermission(String documentHash, String userEmail, PermissionLevel level, String grantedByUserId, String orgCode) {
        if (permissionService == null) return;
        try {
            PermissionRequestDto dto = PermissionRequestDto.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(level)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetOrgCode(orgCode)
                    .documentFileHash(documentHash)
                    .targetUserEmail(userEmail)
                    .build();
            permissionService.grantPermission(dto, grantedByUserId);
        } catch (Exception e) {
            log.error("Failed to grant document permission during invitation onboarding for docHash {}: {}", documentHash, e.getMessage());
        }
    }

    private OrganizationInvitationResponseDto mapToInvitationResponse(OrganizationInvitationEntity inv) {
        return OrganizationInvitationResponseDto.builder()
                .id(inv.getId())
                .token(inv.getToken())
                .orgCode(inv.getOrganization().getOrgCode())
                .organizationName(inv.getOrganization().getName())
                .inviterUserId(inv.getInviter().getId())
                .inviterEmail(inv.getInviter().getEmail())
                .inviteeEmail(inv.getInviteeEmail())
                .inviteeUserId(inv.getInvitee() != null ? inv.getInvitee().getId() : null)
                .role(inv.getRole())
                .status(inv.getStatus())
                .createdAt(inv.getCreatedAt())
                .expiresAt(inv.getExpiresAt())
                .respondedAt(inv.getRespondedAt())
                .note(inv.getNote())
                .targetGroupId(inv.getTargetGroup() != null ? inv.getTargetGroup().getId() : null)
                .targetGroupName(inv.getTargetGroup() != null ? inv.getTargetGroup().getName() : null)
                .targetGroupRole(inv.getTargetGroupRole())
                .targetWorkspaceId(inv.getTargetWorkspace() != null ? inv.getTargetWorkspace().getId() : null)
                .targetWorkspaceName(inv.getTargetWorkspace() != null ? inv.getTargetWorkspace().getName() : null)
                .targetFolderId(inv.getTargetFolderId())
                .targetDocumentHash(inv.getTargetDocumentHash())
                .onboardingPermissionLevel(inv.getOnboardingPermissionLevel())
                .build();
    }
}
