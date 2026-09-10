package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final UserGroupRepository userGroupRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final WorkspacePolicyService workspacePolicyService;
    private final OrgTrustRelationshipService orgTrustRelationshipService;
    private final ProjectWorkspaceService projectWorkspaceService;
    private final CapabilityEvaluatorService capabilityEvaluatorService;
    private final WorkspaceManagerRepository workspaceManagerRepository;
    private final CollaborationRepository collaborationRepository;
    private final CollaborationParticipantRepository collaborationParticipantRepository;

    @Override
    @Transactional
    public OrganizationResponseDto createOrganization(OrganizationRequestDto request, UserEntity currentUser) {
        if (organizationRepository.existsByOrgCode(request.getOrgCode())) {
            throw new IllegalArgumentException("Organization code already exists: " + request.getOrgCode());
        }

        OrganizationEntity organization = OrganizationEntity.builder()
                .orgCode(request.getOrgCode())
                .name(request.getName())
                .createdBy(currentUser)
                .build();

        OrganizationEntity savedOrg = organizationRepository.save(organization);

        // Creator automatically becomes ORG_ADMIN
        UserOrganizationEntity creatorMembership = UserOrganizationEntity.builder()
                .user(currentUser)
                .organization(savedOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        userOrganizationRepository.save(creatorMembership);

        // Create initial canonical General workspace (which creates and owns the canonical General root folder)
        CreateProjectWorkspaceRequestDto initialWorkspaceRequest = CreateProjectWorkspaceRequestDto.builder()
                .orgCode(savedOrg.getOrgCode())
                .name("General")
                .description(null)
                .build();

        projectWorkspaceService.createWorkspace(initialWorkspaceRequest, currentUser);

        log.info("Created organization '{}' ({}) with initial canonical General workspace by user {}",
                savedOrg.getName(), savedOrg.getOrgCode(), currentUser.getId());

        return mapToOrganizationResponse(savedOrg);
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationResponseDto getOrganizationByCode(String orgCode, UserEntity currentUser) {
        OrganizationEntity org = organizationRepository.findById(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));

        validateUserInOrganization(currentUser.getId(), orgCode);

        return mapToOrganizationResponse(org);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponseDto> getUserOrganizations(UserEntity currentUser) {
        List<UserOrganizationEntity> memberships = userOrganizationRepository.findByUser_IdAndStatus(currentUser.getId(), MembershipStatus.ACTIVE);
        return memberships.stream()
                .map(m -> mapToOrganizationResponse(m.getOrganization()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrganizationMemberResponseDto addMemberToOrganization(String orgCode, AddMemberRequestDto request, UserEntity currentUser) {
        OrganizationEntity org = organizationRepository.findById(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));

        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_MEMBERS_MANAGE);

        if (request == null || request.getRole() == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        if (request.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalArgumentException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
        }

        if (request.getRole() == Role.ROLE_ORG_ADMIN
                && !capabilityEvaluatorService.hasCapability(currentUser, orgCode, OrganizationCapability.ORG_SETTINGS_MANAGE)) {
            throw new AccessDeniedException("Organization Managers cannot add members with administrative roles.");
        }

        UserEntity targetUser = userRepository.findByEmail(request.getUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getUserEmail()));

        if (userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(targetUser.getId(), orgCode)) {
            throw new IllegalArgumentException("User is already a member of organization: " + orgCode);
        }

        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(targetUser)
                .organization(org)
                .role(request.getRole() != null ? request.getRole() : Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        UserOrganizationEntity savedMembership = userOrganizationRepository.save(membership);
        log.info("Added user {} to org {} with role {}", targetUser.getEmail(), orgCode, savedMembership.getRole());

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "ADD_MEMBER",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    targetUser.getEmail(),
                    orgCode,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToMemberResponse(savedMembership);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrganizationMemberResponseDto> getOrganizationMembers(String orgCode, Pageable pageable, UserEntity currentUser) {
        validateUserInOrganization(currentUser.getId(), orgCode);

        Pageable safePageable = pageable != null ? pageable : PageRequest.of(0, 20);
        Page<UserOrganizationEntity> memberships = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(orgCode, MembershipStatus.ACTIVE, safePageable);

        return memberships.map(this::mapToMemberResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMemberResponseDto> getOrganizationMembers(String orgCode, UserEntity currentUser) {
        return getOrganizationMembers(orgCode, Pageable.unpaged(), currentUser).getContent();
    }

    @Override
    @Transactional
    public void removeMemberFromOrganization(String orgCode, String targetUserId, UserEntity currentUser) {
        removeMember(orgCode, targetUserId, null, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMemberDetailResponseDto> getOrganizationMemberDetails(String orgCode, MembershipStatus statusFilter, String searchQuery, UserEntity currentUser) {
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_MEMBERS_MANAGE);

        List<UserOrganizationEntity> memberships;
        if (statusFilter != null) {
            memberships = userOrganizationRepository.findByOrganization_OrgCodeAndStatus(orgCode, statusFilter);
        } else {
            memberships = userOrganizationRepository.findByOrganization_OrgCode(orgCode);
        }

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            String q = searchQuery.trim().toLowerCase();
            memberships = memberships.stream()
                    .filter(m -> {
                        UserEntity u = m.getUser();
                        return (u.getEmail() != null && u.getEmail().toLowerCase().contains(q)) ||
                               (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(q)) ||
                               (u.getLastName() != null && u.getLastName().toLowerCase().contains(q));
                    })
                    .collect(Collectors.toList());
        }

        return memberships.stream()
                .map(this::mapToMemberDetailResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationMemberDetailResponseDto getOrganizationMemberDetail(String orgCode, String targetUserId, UserEntity currentUser) {
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_MEMBERS_MANAGE);

        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in organization " + orgCode + " with user ID: " + targetUserId));

        return mapToMemberDetailResponse(membership);
    }

    @Override
    @Transactional(readOnly = true)
    public MemberAccessSummaryResponseDto getOrganizationMemberAccessSummary(String orgCode, String targetUserId, UserEntity currentUser) {
        capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_MEMBERS_MANAGE);

        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in organization " + orgCode + " with user ID: " + targetUserId));

        UserEntity user = membership.getUser();

        // 1. Teams
        List<UserGroupEntity> userGroups = userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode(targetUserId, orgCode);
        List<MemberAccessSummaryResponseDto.TeamSummaryDto> teams = userGroups.stream()
                .map(ug -> MemberAccessSummaryResponseDto.TeamSummaryDto.builder()
                        .groupId(ug.getGroup().getId())
                        .groupName(ug.getGroup().getName())
                        .role(ug.getRole())
                        .build())
                .collect(Collectors.toList());

        // 2. Workspaces
        List<ProjectWorkspaceEntity> workspacesList = projectWorkspaceRepository.findByOrganization_OrgCode(orgCode);
        List<MemberAccessSummaryResponseDto.WorkspaceSummaryDto> workspaces = workspacesList.stream()
                .map(w -> MemberAccessSummaryResponseDto.WorkspaceSummaryDto.builder()
                        .workspaceId(w.getId() != null ? w.getId().toString() : null)
                        .name(w.getName())
                        .accessType("ORGANIZATION_WORKSPACE")
                        .build())
                .collect(Collectors.toList());

        // 3. Direct Permissions
        List<PermissionEntity> rawPermissions = permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), orgCode);
        List<MemberAccessSummaryResponseDto.DirectPermissionSummaryDto> directPermissions = new ArrayList<>();
        if (rawPermissions != null) {
            for (PermissionEntity p : rawPermissions) {
                if (p.isRevoked()) continue;
                String targetType = p.getFolder() != null ? "FOLDER" : (p.getDocument() != null ? "DOCUMENT" : "WORKSPACE");
                Long targetId = p.getFolder() != null ? p.getFolder().getId() : (p.getDocument() != null ? p.getDocument().getId() : null);
                String targetName = p.getFolder() != null ? p.getFolder().getName() : (p.getDocument() != null ? p.getDocument().getFileName() : null);

                directPermissions.add(MemberAccessSummaryResponseDto.DirectPermissionSummaryDto.builder()
                        .permissionId(p.getId())
                        .targetType(targetType)
                        .targetId(targetId)
                        .targetName(targetName)
                        .permissionLevel(p.getPermissionLevel())
                        .build());
            }
        }

        return MemberAccessSummaryResponseDto.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .orgCode(orgCode)
                .orgRole(membership.getRole())
                .status(membership.getStatus())
                .teams(teams)
                .workspaces(workspaces)
                .directPermissions(directPermissions)
                .build();
    }

    private OrganizationEntity lockOrganization(String orgCode) {
        return organizationRepository.findByOrgCodeForUpdate(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));
    }

    @Override
    @Transactional
    public OrganizationMemberDetailResponseDto updateMemberRole(String orgCode, String targetUserId, UpdateMemberRoleRequestDto request, UserEntity currentUser) {
        lockOrganization(orgCode);

        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in organization " + orgCode + " with user ID: " + targetUserId));

        capabilityEvaluatorService.validateMemberManagementTarget(currentUser, orgCode, membership);

        if (request == null || request.getRole() == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        Role newRole = request.getRole();
        if (newRole == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalArgumentException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
        }

        Role oldRole = membership.getRole();

        // Requirement 13: Non-active members cannot be assigned administrative or managerial roles
        if (membership.getStatus() != MembershipStatus.ACTIVE
                && (newRole == Role.ROLE_ORG_ADMIN || newRole == Role.ROLE_ORG_MANAGER)) {
            throw new IllegalArgumentException("Cannot assign administrative or managerial role to a non-active member (current status: " + membership.getStatus() + ").");
        }

        // Prevent non-admin (e.g. ROLE_ORG_MANAGER) from promoting to ORG_ADMIN
        if (newRole == Role.ROLE_ORG_ADMIN
                && !capabilityEvaluatorService.hasCapability(currentUser, orgCode, OrganizationCapability.ORG_SETTINGS_MANAGE)) {
            throw new AccessDeniedException("Organization Managers cannot promote members to Organization Admin.");
        }

        // Last-Admin Safety Check
        if ((oldRole == Role.ROLE_ORG_ADMIN || oldRole == Role.ROLE_SUPER_ADMIN) &&
                membership.getStatus() == MembershipStatus.ACTIVE &&
                (newRole != Role.ROLE_ORG_ADMIN && newRole != Role.ROLE_SUPER_ADMIN)) {
            long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                    orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
            if (activeAdminCount <= 1) {
                throw new IllegalStateException("Cannot demote the last active administrator of organization: " + orgCode);
            }
        }

        membership.setRole(newRole);
        membership.setUpdatedByUserId(currentUser.getId());
        // Stamp statusUpdatedAt only if role actually changed to keep retry stable
        if (!newRole.equals(oldRole)) {
            membership.setStatusUpdatedAt(Instant.now());
        }

        UserOrganizationEntity saved = userOrganizationRepository.save(membership);
        UserEntity targetUser = saved.getUser();

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "UPDATE_MEMBER_ROLE",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    targetUser.getEmail(),
                    orgCode,
                    null,
                    oldRole != null ? oldRole.name() : null,
                    newRole.name(),
                    null,
                    null,
                    null,
                    null
            );
        }

        if (notificationService != null) {
            // Key: same role-update retry is idempotent; different role = different key
            String roleUpdatedKey = "ORG_MEMBER_ROLE_UPDATED:" + saved.getId() + ":" + newRole.name() + ":" + targetUser.getId();
            notificationService.createNotification(
                    targetUser,
                    currentUser,
                    NotificationType.ORG_MEMBER_ROLE_UPDATED,
                    "Organization Role Updated",
                    "Your role in organization " + orgCode + " was updated to " + newRole.name(),
                    NotificationSourceType.ORGANIZATION,
                    orgCode,
                    NotificationCategory.GOVERNANCE,
                    NotificationPriority.MEDIUM,
                    roleUpdatedKey
            );
        }

        return mapToMemberDetailResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationMemberDetailResponseDto suspendMember(String orgCode, String targetUserId, MemberStatusChangeRequestDto request, UserEntity currentUser) {
        if (currentUser != null && currentUser.getId() != null && currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Self-suspension is not allowed. An administrator cannot suspend themselves from the organization.");
        }

        lockOrganization(orgCode);

        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in organization " + orgCode + " with user ID: " + targetUserId));

        capabilityEvaluatorService.validateMemberManagementTarget(currentUser, orgCode, membership);

        // Last-Admin Safety Check
        if ((membership.getRole() == Role.ROLE_ORG_ADMIN || membership.getRole() == Role.ROLE_SUPER_ADMIN) &&
                membership.getStatus() == MembershipStatus.ACTIVE) {
            long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                    orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
            if (activeAdminCount <= 1) {
                throw new IllegalStateException("Cannot suspend the last active administrator of organization: " + orgCode);
            }
        }

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.SUSPENDED);
        membership.setStatusReason(request != null ? request.getReason() : null);
        membership.setUpdatedByUserId(currentUser.getId());
        // Preserve statusUpdatedAt if already SUSPENDED so retry produces the same idempotency key
        if (oldStatus != MembershipStatus.SUSPENDED) {
            membership.setStatusUpdatedAt(Instant.now());
        }

        UserOrganizationEntity saved = userOrganizationRepository.save(membership);
        UserEntity targetUser = saved.getUser();

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "SUSPEND_MEMBER",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    targetUser.getEmail(),
                    orgCode,
                    null,
                    oldStatus != null ? oldStatus.name() : MembershipStatus.ACTIVE.name(),
                    MembershipStatus.SUSPENDED.name(),
                    request != null ? request.getReason() : null,
                    null,
                    null,
                    null
            );
        }

        if (notificationService != null) {
            // Key: same suspension event = same statusUpdatedAt → same key on retry
            String suspendedKey = "ORG_MEMBER_SUSPENDED:" + saved.getId() + ":" + saved.getStatusUpdatedAt().toEpochMilli() + ":" + targetUser.getId();
            notificationService.createNotification(
                    targetUser,
                    currentUser,
                    NotificationType.ORG_MEMBER_SUSPENDED,
                    "Organization Access Suspended",
                    "Your membership in organization " + orgCode + " has been suspended.",
                    NotificationSourceType.ORGANIZATION,
                    orgCode,
                    NotificationCategory.GOVERNANCE,
                    NotificationPriority.HIGH,
                    suspendedKey
            );
        }

        return mapToMemberDetailResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationMemberDetailResponseDto reactivateMember(String orgCode, String targetUserId, UserEntity currentUser) {
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in organization " + orgCode + " with user ID: " + targetUserId));

        capabilityEvaluatorService.validateMemberManagementTarget(currentUser, orgCode, membership);

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setStatusReason(null);
        membership.setUpdatedByUserId(currentUser.getId());
        // Preserve statusUpdatedAt if already ACTIVE so retry produces the same idempotency key
        if (oldStatus != MembershipStatus.ACTIVE) {
            membership.setStatusUpdatedAt(Instant.now());
        }

        UserOrganizationEntity saved = userOrganizationRepository.save(membership);
        UserEntity targetUser = saved.getUser();

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "REACTIVATE_MEMBER",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    targetUser.getEmail(),
                    orgCode,
                    null,
                    oldStatus != null ? oldStatus.name() : MembershipStatus.SUSPENDED.name(),
                    MembershipStatus.ACTIVE.name(),
                    null,
                    null,
                    null,
                    null
            );
        }

        if (notificationService != null) {
            // Key: same reactivation event = same statusUpdatedAt → same key on retry
            String reactivatedKey = "ORG_MEMBER_REACTIVATED:" + saved.getId() + ":" + saved.getStatusUpdatedAt().toEpochMilli() + ":" + targetUser.getId();
            notificationService.createNotification(
                    targetUser,
                    currentUser,
                    NotificationType.ORG_MEMBER_REACTIVATED,
                    "Organization Access Reactivated",
                    "Your membership in organization " + orgCode + " has been reactivated.",
                    NotificationSourceType.ORGANIZATION,
                    orgCode,
                    NotificationCategory.GOVERNANCE,
                    NotificationPriority.MEDIUM,
                    reactivatedKey
            );
        }

        return mapToMemberDetailResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationMemberDetailResponseDto removeMember(String orgCode, String targetUserId, MemberStatusChangeRequestDto request, UserEntity currentUser) {
        if (currentUser != null && currentUser.getId() != null && currentUser.getId().equals(targetUserId)) {
            throw new IllegalArgumentException("Self-removal is not allowed. An administrator cannot remove themselves from the organization.");
        }

        lockOrganization(orgCode);

        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found in organization " + orgCode + " with user ID: " + targetUserId));

        capabilityEvaluatorService.validateMemberManagementTarget(currentUser, orgCode, membership);

        // Last-Admin Safety Check: organization must always retain at least one ACTIVE administrator
        if (membership.getRole() == Role.ROLE_ORG_ADMIN || membership.getRole() == Role.ROLE_SUPER_ADMIN) {
            long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                    orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
            long remainingActiveAdmins = (membership.getStatus() == MembershipStatus.ACTIVE) ? activeAdminCount - 1 : activeAdminCount;
            if (remainingActiveAdmins < 1) {
                throw new IllegalStateException("Cannot remove the last active administrator of organization: " + orgCode);
            }
        }

        MembershipStatus oldStatus = membership.getStatus();
        membership.setStatus(MembershipStatus.REMOVED);
        membership.setStatusReason(request != null ? request.getReason() : null);
        membership.setUpdatedByUserId(currentUser.getId());
        // Preserve statusUpdatedAt if already REMOVED so retry produces the same idempotency key
        if (oldStatus != MembershipStatus.REMOVED) {
            membership.setStatusUpdatedAt(Instant.now());
        }

        UserOrganizationEntity saved = userOrganizationRepository.save(membership);
        UserEntity targetUser = saved.getUser();

        // Remove from organization teams/groups
        List<UserGroupEntity> userGroups = userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode(targetUserId, orgCode);
        if (userGroups != null && !userGroups.isEmpty()) {
            userGroupRepository.deleteAll(userGroups);
        }

        // Remove from workspace manager assignments in this organization
        if (workspaceManagerRepository != null) {
            List<WorkspaceManagerEntity> workspaceManagers = workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode(targetUserId, orgCode);
            if (workspaceManagers != null && !workspaceManagers.isEmpty()) {
                workspaceManagerRepository.deleteAll(workspaceManagers);
            }
        }

        // Remove from collaboration participant assignments where the user has no remaining active membership in participating orgs
        if (collaborationRepository != null && collaborationParticipantRepository != null) {
            List<CollaborationEntity> collabs = collaborationRepository.findByOwnerOrganization_OrgCodeOrPartnerOrganization_OrgCode(orgCode, orgCode);
            if (collabs != null) {
                for (CollaborationEntity collab : collabs) {
                    Optional<CollaborationParticipantEntity> participantOpt = collaborationParticipantRepository
                            .findByCollaboration_IdAndUser_Id(collab.getId(), targetUserId);
                    if (participantOpt.isPresent()) {
                        String otherOrgCode = (collab.getOwnerOrganization() != null && collab.getOwnerOrganization().getOrgCode().equalsIgnoreCase(orgCode))
                                ? (collab.getPartnerOrganization() != null ? collab.getPartnerOrganization().getOrgCode() : null)
                                : (collab.getOwnerOrganization() != null ? collab.getOwnerOrganization().getOrgCode() : null);
                        boolean activeInOther = otherOrgCode != null && userOrganizationRepository
                                .existsByUser_IdAndOrganization_OrgCodeAndStatus(targetUserId, otherOrgCode, MembershipStatus.ACTIVE);
                        if (!activeInOther) {
                            if (permissionRepository != null && targetUser != null && targetUser.getEmail() != null) {
                                List<PermissionEntity> collabPerms = permissionRepository
                                        .findByCollaboration_IdAndTargetUserEmailIgnoreCaseAndRevokedFalse(collab.getId(), targetUser.getEmail());
                                if (collabPerms != null && !collabPerms.isEmpty()) {
                                    for (PermissionEntity p : collabPerms) {
                                        p.setRevoked(true);
                                        p.setRevokedAt(Instant.now());
                                        p.setRevokedBy(currentUser);
                                        permissionRepository.save(p);
                                    }
                                }
                            }
                            collaborationParticipantRepository.delete(participantOpt.get());
                        }
                    }
                }
            }
        }

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "REMOVE_MEMBER",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    targetUser.getEmail(),
                    orgCode,
                    null,
                    oldStatus != null ? oldStatus.name() : null,
                    MembershipStatus.REMOVED.name(),
                    request != null ? request.getReason() : null,
                    null,
                    null,
                    null
            );
        }

        if (notificationService != null) {
            // Key: same removal event = same statusUpdatedAt → same key on retry
            String removedKey = "ORG_MEMBER_REMOVED:" + saved.getId() + ":" + saved.getStatusUpdatedAt().toEpochMilli() + ":" + targetUser.getId();
            notificationService.createNotification(
                    targetUser,
                    currentUser,
                    NotificationType.ORG_MEMBER_REMOVED,
                    "Organization Membership Removed",
                    "Your membership in organization " + orgCode + " has been removed.",
                    NotificationSourceType.ORGANIZATION,
                    orgCode,
                    NotificationCategory.GOVERNANCE,
                    NotificationPriority.HIGH,
                    removedKey
            );
        }

        return mapToMemberDetailResponse(saved);
    }

    @Override
    @Transactional
    public OrganizationMemberDetailResponseDto transferOrgAdmin(String orgCode, String newAdminUserId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        if (orgCode == null || orgCode.isBlank()) {
            throw new IllegalArgumentException("Organization code is required.");
        }
        if (newAdminUserId == null || newAdminUserId.isBlank()) {
            throw new IllegalArgumentException("New administrator user ID is required.");
        }

        // 1. Lock the organization aggregate boundary
        lockOrganization(orgCode);

        // 2. Validate Actor
        boolean isSuperAdmin = currentUser.getRole() == Role.ROLE_SUPER_ADMIN;
        UserOrganizationEntity callerMembership = null;
        if (!isSuperAdmin) {
            callerMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), orgCode)
                    .orElseThrow(() -> new AccessDeniedException("User is not an active member of organization: " + orgCode));

            if (callerMembership.getStatus() != MembershipStatus.ACTIVE) {
                throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
            }

            if (callerMembership.getRole() != Role.ROLE_ORG_ADMIN) {
                throw new AccessDeniedException("Only Organization Administrators can transfer administration of organization: " + orgCode);
            }
        } else {
            callerMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), orgCode).orElse(null);
        }

        // 3. Validate Self-Transfer
        if (currentUser.getId().equals(newAdminUserId)) {
            throw new IllegalArgumentException("Self-transfer is not allowed. Cannot transfer organization administration to yourself.");
        }

        // 4. Validate Target
        UserOrganizationEntity targetMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(newAdminUserId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Target member not found in organization " + orgCode + " with user ID: " + newAdminUserId));

        if (targetMembership.getStatus() != MembershipStatus.ACTIVE) {
            throw new IllegalArgumentException("Target user must be an active member of the organization (current status: " + targetMembership.getStatus() + ").");
        }

        if (targetMembership.getRole() == Role.ROLE_SUPER_ADMIN) {
            throw new IllegalArgumentException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
        }

        if (targetMembership.getRole() == Role.ROLE_ORG_ADMIN) {
            throw new IllegalArgumentException("Target user is already an Organization Administrator.");
        }

        Role oldTargetRole = targetMembership.getRole();
        Role oldActorRole = callerMembership != null ? callerMembership.getRole() : null;
        Instant now = Instant.now();

        // 5. Promote Target to ROLE_ORG_ADMIN
        targetMembership.setRole(Role.ROLE_ORG_ADMIN);
        targetMembership.setStatusUpdatedAt(now);
        targetMembership.setUpdatedByUserId(currentUser.getId());
        UserOrganizationEntity savedTarget = userOrganizationRepository.save(targetMembership);
        UserEntity targetUser = savedTarget.getUser();

        // 6. Demote Initiating Admin to ROLE_ORG_MANAGER (if caller is an ORG_ADMIN in this org)
        if (callerMembership != null && callerMembership.getRole() == Role.ROLE_ORG_ADMIN) {
            callerMembership.setRole(Role.ROLE_ORG_MANAGER);
            callerMembership.setStatusUpdatedAt(now);
            callerMembership.setUpdatedByUserId(currentUser.getId());
            userOrganizationRepository.save(callerMembership);
        }

        // 7. Verify Last-Active-Admin Invariant
        long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        if (activeAdminCount < 1) {
            throw new IllegalStateException("Organization must retain at least one active administrator.");
        }

        // 8. Cache Eviction for both users
        if (permissionCacheService != null) {
            if (targetUser != null && targetUser.getEmail() != null) {
                permissionCacheService.evictUserPermissions(targetUser.getEmail());
            }
            if (currentUser.getEmail() != null) {
                permissionCacheService.evictUserPermissions(currentUser.getEmail());
            }
        }

        // 9. Dedicated Governance Audit Event
        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "TRANSFER_ORG_ADMIN",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    orgCode,
                    currentUser,
                    targetUser.getEmail(),
                    orgCode,
                    null,
                    oldTargetRole != null ? oldTargetRole.name() : null,
                    Role.ROLE_ORG_ADMIN.name(),
                    "Handover: " + currentUser.getId() + " (" + (oldActorRole != null ? oldActorRole.name() : "SUPER_ADMIN") + " -> ROLE_ORG_MANAGER) to " + targetUser.getId() + " (" + oldTargetRole.name() + " -> ROLE_ORG_ADMIN)",
                    null,
                    null,
                    null
            );
        }

        // 10. Notifications
        if (notificationService != null) {
            String targetKey = "ORG_ADMIN_TRANSFERRED_TARGET:" + savedTarget.getId() + ":" + now.toEpochMilli();
            notificationService.createNotification(
                    targetUser,
                    currentUser,
                    NotificationType.ORG_MEMBER_ROLE_UPDATED,
                    "Organization Administrator Role Assigned",
                    "You have been appointed as an Organization Administrator for organization " + orgCode + ".",
                    NotificationSourceType.ORGANIZATION,
                    orgCode,
                    NotificationCategory.GOVERNANCE,
                    NotificationPriority.HIGH,
                    targetKey
            );

            if (callerMembership != null) {
                String actorKey = "ORG_ADMIN_TRANSFERRED_ACTOR:" + callerMembership.getId() + ":" + now.toEpochMilli();
                notificationService.createNotification(
                        currentUser,
                        currentUser,
                        NotificationType.ORG_MEMBER_ROLE_UPDATED,
                        "Organization Administration Transferred",
                        "You transferred Organization Administration for " + orgCode + " to " + targetUser.getEmail() + ". Your role is now ROLE_ORG_MANAGER.",
                        NotificationSourceType.ORGANIZATION,
                        orgCode,
                        NotificationCategory.GOVERNANCE,
                        NotificationPriority.MEDIUM,
                        actorKey
                );
            }
        }

        return mapToMemberDetailResponse(savedTarget);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationResponseDto> searchOrganizations(String query, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("Search query cannot be blank");
        }

        String cleanQuery = query.trim();
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 50);

        List<OrganizationEntity> candidates = organizationRepository
                .findByNameContainingIgnoreCaseOrOrgCodeContainingIgnoreCase(cleanQuery, cleanQuery, pageable);

        Set<String> requesterOrgCodes = userOrganizationRepository.findByUser_IdAndStatus(currentUser.getId(), MembershipStatus.ACTIVE).stream()
                .map(m -> m.getOrganization().getOrgCode())
                .collect(Collectors.toSet());

        return candidates.stream()
                .filter(targetOrg -> isOrgDiscoverableToUser(targetOrg, requesterOrgCodes))
                .limit(10)
                .map(this::mapToOrganizationResponse)
                .collect(Collectors.toList());
    }

    private boolean isOrgDiscoverableToUser(OrganizationEntity targetOrg, Set<String> requesterOrgCodes) {
        String targetOrgCode = targetOrg.getOrgCode();

        // Internal member can always discover their own organization
        if (requesterOrgCodes.contains(targetOrgCode)) {
            return true;
        }

        DiscoverabilityMode mode = workspacePolicyService.getPolicyForOrganization(targetOrgCode)
                .map(WorkspacePolicyEntity::getDiscoverabilityMode)
                .orElse(DiscoverabilityMode.DISCOVERABLE);

        switch (mode) {
            case PRIVATE:
                return false;
            case TRUSTED_ONLY:
                if (requesterOrgCodes.isEmpty()) {
                    return false;
                }
                return requesterOrgCodes.stream()
                        .anyMatch(reqOrgCode -> orgTrustRelationshipService.hasActiveTrust(reqOrgCode, targetOrgCode));
            case DISCOVERABLE:
            default:
                return true;
        }
    }

    private void validateUserInOrganization(String userId, String orgCode) {
        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(userId, orgCode, MembershipStatus.ACTIVE)) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }
    }

    private void validateOrgAdminPermission(String userId, String orgCode) {
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(userId, orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }

        if (membership.getRole() != Role.ROLE_ORG_ADMIN && membership.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Admin privileges required in organization: " + orgCode);
        }
    }

    private OrganizationResponseDto mapToOrganizationResponse(OrganizationEntity org) {
        return OrganizationResponseDto.builder()
                .orgCode(org.getOrgCode())
                .name(org.getName())
                .createdByUserId(org.getCreatedBy() != null ? org.getCreatedBy().getId() : null)
                .createdAt(org.getCreatedAt())
                .build();
    }

    private OrganizationMemberResponseDto mapToMemberResponse(UserOrganizationEntity membership) {
        UserEntity u = membership.getUser();
        return OrganizationMemberResponseDto.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .role(membership.getRole())
                .joinedAt(membership.getJoinedAt())
                .build();
    }

    private OrganizationMemberDetailResponseDto mapToMemberDetailResponse(UserOrganizationEntity membership) {
        UserEntity u = membership.getUser();
        return OrganizationMemberDetailResponseDto.builder()
                .userId(u.getId())
                .email(u.getEmail())
                .firstName(u.getFirstName())
                .lastName(u.getLastName())
                .orgCode(membership.getOrganization().getOrgCode())
                .role(membership.getRole())
                .status(membership.getStatus())
                .joinedAt(membership.getJoinedAt())
                .statusUpdatedAt(membership.getStatusUpdatedAt())
                .updatedByUserId(membership.getUpdatedByUserId())
                .statusReason(membership.getStatusReason())
                .build();
    }
}

