package com.subject77.docshare.modules.channel.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.entities.*;
import com.subject77.docshare.modules.channel.repository.ChannelMemberRepository;
import com.subject77.docshare.modules.channel.repository.ChannelReadStateRepository;
import com.subject77.docshare.modules.channel.repository.ChannelRepository;
import com.subject77.docshare.modules.channel.repository.MessageRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelServiceImpl implements ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final OrganizationRepository organizationRepository;
    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final CollaborationRepository collaborationRepository;
    private final UserRepository userRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final WorkspacePolicyService workspacePolicyService;
    private final OrgTrustRelationshipService orgTrustRelationshipService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ChannelReadStateRepository channelReadStateRepository;
    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public ChannelResponseDto createChannel(CreateChannelRequestDto request, UserEntity currentUser) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Channel name is required");
        }
        if (request.getType() == null) {
            throw new IllegalArgumentException("Channel type is required");
        }

        // Validate scope invariant: exactly one parent scope must be provided
        int scopeCount = 0;
        if (request.getOrgCode() != null && !request.getOrgCode().trim().isEmpty()) scopeCount++;
        if (request.getTeamId() != null) scopeCount++;
        if (request.getProjectWorkspaceId() != null) scopeCount++;
        if (request.getCollaborationId() != null) scopeCount++;

        if (scopeCount != 1) {
            throw new IllegalArgumentException("Channel must have exactly one parent scope (Organization, Team, Project Workspace, or Collaboration)");
        }

        // Type invariant checks
        if (request.getType() == ChannelType.COLLABORATION && request.getCollaborationId() == null) {
            throw new IllegalArgumentException("COLLABORATION channel type requires a Collaboration parent scope");
        }

        OrganizationEntity org = null;
        GroupEntity team = null;
        ProjectWorkspaceEntity projectWorkspace = null;
        CollaborationEntity collaboration = null;
        String name = request.getName().trim();

        if (request.getOrgCode() != null && !request.getOrgCode().trim().isEmpty()) {
            String orgCode = request.getOrgCode().trim();
            org = organizationRepository.findById(orgCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));
            verifyUserInOrg(currentUser, orgCode);

            if (channelRepository.existsByOrganization_OrgCodeAndName(orgCode, name)) {
                throw new IllegalArgumentException("Channel with name '" + name + "' already exists in organization: " + orgCode);
            }
        } else if (request.getTeamId() != null) {
            team = groupRepository.findById(request.getTeamId())
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + request.getTeamId()));
            org = team.getOrganization();
            verifyUserInOrg(currentUser, org.getOrgCode());

            if (channelRepository.existsByTeam_IdAndName(team.getId(), name)) {
                throw new IllegalArgumentException("Channel with name '" + name + "' already exists in team: " + team.getId());
            }
        } else if (request.getProjectWorkspaceId() != null) {
            projectWorkspace = projectWorkspaceRepository.findById(request.getProjectWorkspaceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project workspace not found with ID: " + request.getProjectWorkspaceId()));
            org = projectWorkspace.getOrganization();
            verifyUserInOrg(currentUser, org.getOrgCode());

            if (channelRepository.existsByProjectWorkspace_IdAndName(projectWorkspace.getId(), name)) {
                throw new IllegalArgumentException("Channel with name '" + name + "' already exists in project workspace: " + projectWorkspace.getId());
            }
        } else if (request.getCollaborationId() != null) {
            collaboration = collaborationRepository.findById(request.getCollaborationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + request.getCollaborationId()));
            verifyUserInCollaboration(currentUser, collaboration);

            if (channelRepository.existsByCollaboration_IdAndName(collaboration.getId(), name)) {
                throw new IllegalArgumentException("Channel with name '" + name + "' already exists in collaboration: " + collaboration.getId());
            }
        }

        // Validate SHARED channel policy if applicable
        if (request.getType() == ChannelType.SHARED && org != null) {
            validateExternalSharingAllowed(org.getOrgCode());
        }

        ChannelEntity channel = ChannelEntity.builder()
                .name(name)
                .description(request.getDescription())
                .type(request.getType())
                .status(ChannelStatus.ACTIVE)
                .allowExternal(request.getAllowExternal() != null && request.getAllowExternal())
                .organization(org)
                .team(team)
                .projectWorkspace(projectWorkspace)
                .collaboration(collaboration)
                .createdBy(currentUser)
                .build();

        ChannelEntity savedChannel = channelRepository.save(channel);

        // Creator automatically joins as MANAGER
        ChannelMemberEntity creatorMember = ChannelMemberEntity.builder()
                .channel(savedChannel)
                .user(currentUser)
                .role(ChannelMemberRole.MANAGER)
                .build();
        channelMemberRepository.save(creatorMember);

        log.info("Created Channel '{}' (ID: {}, Type: {}) by user {}", savedChannel.getName(), savedChannel.getId(), savedChannel.getType(), currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "CHANNEL_CREATED",
                    "CHANNEL",
                    savedChannel.getId(),
                    null,
                    null,
                    null,
                    org != null ? org.getOrgCode() : null,
                    currentUser,
                    currentUser.getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return mapToChannelResponseDto(savedChannel, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public ChannelResponseDto getChannel(Long channelId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);
        return mapToChannelResponseDto(channel, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelResponseDto> listChannels(String orgCode, Long teamId, Long projectWorkspaceId, Long collaborationId, UserEntity currentUser) {
        List<ChannelEntity> channels;

        if (orgCode != null && !orgCode.trim().isEmpty()) {
            verifyUserInOrg(currentUser, orgCode.trim());
            channels = channelRepository.findByOrganization_OrgCode(orgCode.trim());
        } else if (teamId != null) {
            GroupEntity team = groupRepository.findById(teamId)
                    .orElseThrow(() -> new ResourceNotFoundException("Team not found with ID: " + teamId));
            verifyUserInOrg(currentUser, team.getOrganization().getOrgCode());
            channels = channelRepository.findByTeam_Id(teamId);
        } else if (projectWorkspaceId != null) {
            ProjectWorkspaceEntity ws = projectWorkspaceRepository.findById(projectWorkspaceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project workspace not found with ID: " + projectWorkspaceId));
            verifyUserInOrg(currentUser, ws.getOrganization().getOrgCode());
            channels = channelRepository.findByProjectWorkspace_Id(projectWorkspaceId);
        } else if (collaborationId != null) {
            CollaborationEntity collab = collaborationRepository.findById(collaborationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Collaboration not found with ID: " + collaborationId));
            verifyUserInCollaboration(currentUser, collab);
            channels = channelRepository.findByCollaboration_Id(collaborationId);
        } else {
            // Default to user's channels
            List<ChannelMemberEntity> memberships = channelMemberRepository.findByUser_Id(currentUser.getId());
            channels = memberships.stream()
                    .map(ChannelMemberEntity::getChannel)
                    .collect(Collectors.toList());
        }

        return channels.stream()
                .map(c -> mapToChannelResponseDto(c, currentUser))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChannelResponseDto updateChannel(Long channelId, UpdateChannelRequestDto request, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelNotArchived(channel);

        verifyChannelManagerOrAdmin(channel, currentUser);

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            channel.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            channel.setDescription(request.getDescription());
        }

        ChannelEntity updated = channelRepository.save(channel);
        return mapToChannelResponseDto(updated, currentUser);
    }

    @Override
    @Transactional
    public ChannelResponseDto archiveChannel(Long channelId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelNotArchived(channel);

        verifyChannelManagerOrAdmin(channel, currentUser);

        channel.setStatus(ChannelStatus.ARCHIVED);
        ChannelEntity archived = channelRepository.save(channel);

        log.info("Archived Channel '{}' (ID: {}) by user {}", archived.getName(), archived.getId(), currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "CHANNEL_ARCHIVED",
                    "CHANNEL",
                    archived.getId(),
                    null,
                    null,
                    null,
                    archived.getOrganization() != null ? archived.getOrganization().getOrgCode() : null,
                    currentUser,
                    currentUser.getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return mapToChannelResponseDto(archived, currentUser);
    }

    @Override
    @Transactional
    public ChannelMemberResponseDto addMember(Long channelId, AddChannelMemberRequestDto request, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelNotArchived(channel);

        verifyChannelManagerOrAdmin(channel, currentUser);

        if (request.getUserEmail() == null || request.getUserEmail().trim().isEmpty()) {
            throw new IllegalArgumentException("User email is required");
        }

        UserEntity targetUser = userRepository.findByEmail(request.getUserEmail().trim())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getUserEmail()));

        if (channelMemberRepository.existsByChannel_IdAndUser_Id(channel.getId(), targetUser.getId())) {
            throw new IllegalArgumentException("User is already a member of this channel");
        }

        // Verify external user sharing rules if target user is external to channel org
        if (channel.getOrganization() != null) {
            String channelOrgCode = channel.getOrganization().getOrgCode();
            boolean isTargetInOrg = userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(targetUser.getId(), channelOrgCode);

            if (!isTargetInOrg) {
                // Enforce full policy precedence: Org → Workspace → Trust → Channel
                validateExternalUserAddition(channel, channelOrgCode, targetUser);
            }
        }

        ChannelMemberRole role = request.getRole() != null ? request.getRole() : ChannelMemberRole.MEMBER;

        ChannelMemberEntity member = ChannelMemberEntity.builder()
                .channel(channel)
                .user(targetUser)
                .role(role)
                .build();

        ChannelMemberEntity savedMember = channelMemberRepository.save(member);

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "CHANNEL_MEMBER_ADDED",
                    "CHANNEL",
                    channel.getId(),
                    null,
                    null,
                    null,
                    channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null,
                    currentUser,
                    targetUser.getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }

        if (notificationService != null) {
            try {
                String inviteKey = "CHANNEL_INVITE:" + channel.getId() + ":" + targetUser.getId();
                notificationService.createNotification(
                        targetUser,
                        currentUser,
                        NotificationType.CHANNEL_INVITE,
                        "Added to Channel",
                        String.format("You were added to channel #%s", channel.getName()),
                        NotificationSourceType.CHANNEL,
                        String.valueOf(channel.getId()),
                        null,
                        null,
                        inviteKey
                );
            } catch (Exception e) {
                log.error("Failed to send CHANNEL_INVITE notification to user {}: {}", targetUser.getId(), e.getMessage());
            }
        }

        return mapToChannelMemberResponseDto(savedMember);
    }

    @Override
    @Transactional
    public void removeMember(Long channelId, String userId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelNotArchived(channel);

        // Member can remove self, or caller must be Channel Manager / Scope Admin
        boolean isSelf = currentUser.getId().equals(userId);
        if (!isSelf) {
            verifyChannelManagerOrAdmin(channel, currentUser);
        }

        ChannelMemberEntity member = channelMemberRepository.findByChannel_IdAndUser_Id(channel.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of channel ID: " + channelId));

        channelMemberRepository.delete(member);

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "CHANNEL_MEMBER_REMOVED",
                    "CHANNEL",
                    channel.getId(),
                    null,
                    null,
                    null,
                    channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null,
                    currentUser,
                    member.getUser().getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChannelMemberResponseDto> listMembers(Long channelId, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        verifyAccessToChannel(channel, currentUser);

        List<ChannelMemberEntity> members = channelMemberRepository.findByChannel_Id(channel.getId());
        return members.stream()
                .map(this::mapToChannelMemberResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ChannelMemberResponseDto updateMemberRole(Long channelId, String userId, UpdateChannelMemberRoleRequestDto request, UserEntity currentUser) {
        ChannelEntity channel = findChannelById(channelId);
        ensureChannelNotArchived(channel);

        verifyChannelManagerOrAdmin(channel, currentUser);

        if (request.getRole() == null) {
            throw new IllegalArgumentException("Member role is required");
        }

        ChannelMemberEntity member = channelMemberRepository.findByChannel_IdAndUser_Id(channel.getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of channel ID: " + channelId));

        member.setRole(request.getRole());
        ChannelMemberEntity saved = channelMemberRepository.save(member);

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "CHANNEL_MEMBER_ROLE_UPDATED",
                    "CHANNEL",
                    channel.getId(),
                    null,
                    null,
                    null,
                    channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null,
                    currentUser,
                    member.getUser().getEmail(),
                    null,
                    null,
                    null,
                    null
            );
        }

        return mapToChannelMemberResponseDto(saved);
    }

    // Helper Methods

    private ChannelEntity findChannelById(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new ResourceNotFoundException("Channel not found with ID: " + channelId));
    }

    private void ensureChannelNotArchived(ChannelEntity channel) {
        if (channel.getStatus() == ChannelStatus.ARCHIVED) {
            throw new IllegalStateException("Channel is archived and cannot be modified");
        }
    }

    private void verifyUserInOrg(UserEntity user, String orgCode) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;
        boolean inOrg = userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(user.getId(), orgCode);
        if (!inOrg) {
            throw new AccessDeniedException("User does not belong to organization: " + orgCode);
        }
    }

    private void verifyUserInCollaboration(UserEntity user, CollaborationEntity collab) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        String ownerOrgCode = collab.getOwnerOrganization().getOrgCode();
        String partnerOrgCode = collab.getPartnerOrganization().getOrgCode();

        boolean inOwner = userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(user.getId(), ownerOrgCode);
        boolean inPartner = userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(user.getId(), partnerOrgCode);

        if (!inOwner && !inPartner) {
            throw new AccessDeniedException("User is not a member of any organization participating in collaboration: " + collab.getId());
        }
    }

    private void verifyAccessToChannel(ChannelEntity channel, UserEntity user) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        // Is direct channel member?
        boolean isMember = channelMemberRepository.existsByChannel_IdAndUser_Id(channel.getId(), user.getId());
        if (isMember) return;

        // If STANDARD or ANNOUNCEMENT type and user belongs to parent scope, access allowed
        if (channel.getType() == ChannelType.STANDARD || channel.getType() == ChannelType.ANNOUNCEMENT) {
            if (channel.getOrganization() != null && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(user.getId(), channel.getOrganization().getOrgCode())) {
                return;
            }
        }

        // If PRIVATE type and not a member -> denied
        throw new AccessDeniedException("Access denied to channel ID: " + channel.getId());
    }

    private void verifyChannelManagerOrAdmin(ChannelEntity channel, UserEntity user) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        // Check if Channel Admin or Manager
        Optional<ChannelMemberEntity> memberOpt = channelMemberRepository.findByChannel_IdAndUser_Id(channel.getId(), user.getId());
        if (memberOpt.isPresent()) {
            ChannelMemberRole role = memberOpt.get().getRole();
            if (role == ChannelMemberRole.ADMIN || role == ChannelMemberRole.MANAGER) {
                return;
            }
        }

        // Check if Org Admin
        if (channel.getOrganization() != null) {
            Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), channel.getOrganization().getOrgCode());
            if (uo.isPresent() && (uo.get().getRole() == Role.ROLE_ORG_ADMIN || uo.get().getRole() == Role.ROLE_ORG_MANAGER)) {
                return;
            }
        }

        throw new AccessDeniedException("User is not a Manager or Administrator for channel ID: " + channel.getId());
    }

    @Override
    public void verifyChannelModeratorOrAdmin(ChannelEntity channel, UserEntity user) {
        if (user.getRole() == Role.ROLE_SUPER_ADMIN) return;

        Optional<ChannelMemberEntity> memberOpt = channelMemberRepository.findByChannel_IdAndUser_Id(channel.getId(), user.getId());
        if (memberOpt.isPresent()) {
            ChannelMemberRole role = memberOpt.get().getRole();
            if (role == ChannelMemberRole.ADMIN || role == ChannelMemberRole.MANAGER || role == ChannelMemberRole.MODERATOR) {
                return;
            }
        }

        if (channel.getOrganization() != null) {
            Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), channel.getOrganization().getOrgCode());
            if (uo.isPresent() && (uo.get().getRole() == Role.ROLE_ORG_ADMIN || uo.get().getRole() == Role.ROLE_ORG_MANAGER)) {
                return;
            }
        }

        throw new AccessDeniedException("User does not have moderator or admin access for channel ID: " + channel.getId());
    }

    /**
     * Validates that a SHARED/EXTERNAL channel can be created for the given org.
     * Precedence: Org policy (OFF blocks) → Workspace policy (OFF blocks).
     * A more restrictive applicable policy always wins.
     */
    private void validateExternalSharingAllowed(String orgCode) {
        // Layer 1: Organization policy
        Optional<WorkspacePolicyEntity> orgPolicyOpt = workspacePolicyService.getPolicyForOrganization(orgCode);
        if (orgPolicyOpt.isPresent() && orgPolicyOpt.get().getExternalSharingMode() == ExternalSharingMode.OFF) {
            throw new IllegalArgumentException("External sharing is disabled for organization: " + orgCode);
        }

        // Layer 2: Workspace (effective) policy — may be more restrictive than the org-level entry
        OrganizationEntity orgEntity = organizationRepository.findById(orgCode).orElse(null);
        if (orgEntity != null) {
            WorkspacePolicyEntity effectivePolicy = workspacePolicyService.getEffectivePolicyForOrganization(orgEntity);
            if (effectivePolicy != null && effectivePolicy.getExternalSharingMode() == ExternalSharingMode.OFF) {
                throw new IllegalArgumentException("External sharing is disabled by workspace policy for organization: " + orgCode);
            }
        }
    }

    /**
     * Validates that an external user can be added to the channel.
     * Enforces strict policy precedence:
     *   1. channel.allowExternal must be true (channel gate)
     *   2. Organization policy must allow external sharing (OFF blocks)
     *   3. Workspace (effective) policy must allow external sharing (OFF blocks)
     *   4. Active trust relationship must exist between the channel org and the user's org
     *
     * channel.allowExternal=true can NEVER override a higher-level policy OFF.
     */
    private void validateExternalUserAddition(ChannelEntity channel, String orgCode, UserEntity targetUser) {
        // Layer 1: Channel gate — channel must explicitly allow external members
        if (!channel.isAllowExternal()) {
            throw new IllegalArgumentException("Channel does not allow external members (allowExternal is false)");
        }

        // Layer 2: Organization policy — cannot be bypassed by channel configuration
        Optional<WorkspacePolicyEntity> orgPolicyOpt = workspacePolicyService.getPolicyForOrganization(orgCode);
        if (orgPolicyOpt.isPresent() && orgPolicyOpt.get().getExternalSharingMode() == ExternalSharingMode.OFF) {
            throw new IllegalArgumentException("External user sharing is disabled by organization policy: " + orgCode);
        }

        // Layer 3: Workspace (effective) policy — cannot be bypassed by channel configuration
        OrganizationEntity orgEntity = organizationRepository.findById(orgCode).orElse(null);
        if (orgEntity != null) {
            WorkspacePolicyEntity effectivePolicy = workspacePolicyService.getEffectivePolicyForOrganization(orgEntity);
            if (effectivePolicy != null && effectivePolicy.getExternalSharingMode() == ExternalSharingMode.OFF) {
                throw new IllegalArgumentException("External user sharing is disabled by workspace policy for organization: " + orgCode);
            }
        }

        // Layer 4: Trust/collaboration — active org-to-org trust must exist
        List<UserOrganizationEntity> targetMemberships = userOrganizationRepository.findByUser_Id(targetUser.getId());
        boolean hasTrustedOrg = targetMemberships.stream().anyMatch(uo ->
                orgTrustRelationshipService.hasActiveTrust(orgCode, uo.getOrganization().getOrgCode()));
        if (!hasTrustedOrg) {
            throw new IllegalArgumentException("Target user does not belong to an organization with active trust with organization: " + orgCode);
        }
    }

    private ChannelResponseDto mapToChannelResponseDto(ChannelEntity channel, UserEntity currentUser) {
        long memberCount = channelMemberRepository.countByChannel_Id(channel.getId());

        long unreadCount = 0L;
        ChannelMemberRole currentUserRole = null;
        boolean canManageChannel = false;
        boolean canManageMembers = false;
        boolean canModerateMessages = false;
        boolean canArchive = false;
        boolean canPost = false;

        if (currentUser != null) {
            Optional<ChannelMemberEntity> memberOpt = channelMemberRepository.findByChannel_IdAndUser_Id(channel.getId(), currentUser.getId());
            if (memberOpt.isPresent()) {
                currentUserRole = memberOpt.get().getRole();
                unreadCount = channelReadStateRepository
                        .findByChannel_IdAndUser_Id(channel.getId(), currentUser.getId())
                        .map(rs -> messageRepository.countByChannel_IdAndCreatedAtAfterAndDeletedAtIsNullAndAuthor_IdNot(
                                channel.getId(), rs.getLastReadAt(), currentUser.getId()))
                        .orElseGet(() -> messageRepository.countByChannel_IdAndDeletedAtIsNullAndAuthor_IdNot(
                                channel.getId(), currentUser.getId()));
            }

            boolean isSuperAdmin = currentUser.getRole() == Role.ROLE_SUPER_ADMIN;
            boolean isOrgAdminOrManager = false;
            if (channel.getOrganization() != null) {
                Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), channel.getOrganization().getOrgCode());
                if (uo.isPresent() && (uo.get().getRole() == Role.ROLE_ORG_ADMIN || uo.get().getRole() == Role.ROLE_ORG_MANAGER)) {
                    isOrgAdminOrManager = true;
                }
            }

            boolean isChannelAdminOrManager = currentUserRole == ChannelMemberRole.ADMIN || currentUserRole == ChannelMemberRole.MANAGER;
            boolean hasAdminAuthority = isSuperAdmin || isOrgAdminOrManager || isChannelAdminOrManager;

            canManageChannel = hasAdminAuthority;
            canManageMembers = hasAdminAuthority;
            canArchive = hasAdminAuthority;
            canModerateMessages = hasAdminAuthority || currentUserRole == ChannelMemberRole.MODERATOR;

            // canPost: mirrors verifyCanPostToChannel rules exactly
            // Archived channels never accept new messages
            if (channel.getStatus() != ChannelStatus.ARCHIVED) {
                if (isSuperAdmin) {
                    canPost = true;
                } else if (channel.getType() == ChannelType.ANNOUNCEMENT) {
                    // ANNOUNCEMENT: only privileged roles may post
                    boolean isPrivilegedChannelRole = currentUserRole == ChannelMemberRole.ADMIN
                            || currentUserRole == ChannelMemberRole.MANAGER
                            || currentUserRole == ChannelMemberRole.MODERATOR;
                    canPost = isPrivilegedChannelRole || isOrgAdminOrManager;
                } else {
                    // STANDARD / PRIVATE / SHARED: any accessible member may post
                    boolean hasMembership = currentUserRole != null;
                    boolean hasOrgAccess = channel.getOrganization() != null
                            && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode(
                                    currentUser.getId(), channel.getOrganization().getOrgCode());
                    canPost = hasMembership || hasOrgAccess;
                }
            }
        }

        return ChannelResponseDto.builder()
                .id(channel.getId())
                .name(channel.getName())
                .description(channel.getDescription())
                .type(channel.getType())
                .status(channel.getStatus())
                .orgCode(channel.getOrganization() != null ? channel.getOrganization().getOrgCode() : null)
                .teamId(channel.getTeam() != null ? channel.getTeam().getId() : null)
                .projectWorkspaceId(channel.getProjectWorkspace() != null ? channel.getProjectWorkspace().getId() : null)
                .collaborationId(channel.getCollaboration() != null ? channel.getCollaboration().getId() : null)
                .createdById(channel.getCreatedBy() != null ? channel.getCreatedBy().getId() : null)
                .createdByEmail(channel.getCreatedBy() != null ? channel.getCreatedBy().getEmail() : null)
                .createdAt(channel.getCreatedAt())
                .updatedAt(channel.getUpdatedAt())
                .memberCount(memberCount)
                .unreadCount(unreadCount)
                .currentUserRole(currentUserRole)
                .canManageChannel(canManageChannel)
                .canManageMembers(canManageMembers)
                .canModerateMessages(canModerateMessages)
                .canArchive(canArchive)
                .canPost(canPost)
                .build();
    }

    private ChannelMemberResponseDto mapToChannelMemberResponseDto(ChannelMemberEntity member) {
        UserEntity user = member.getUser();
        String userOrgCode = null;
        List<UserOrganizationEntity> uos = userOrganizationRepository.findByUser_Id(user.getId());
        if (!uos.isEmpty()) {
            userOrgCode = uos.get(0).getOrganization().getOrgCode();
        }

        return ChannelMemberResponseDto.builder()
                .id(member.getId())
                .channelId(member.getChannel().getId())
                .userId(user.getId())
                .userEmail(user.getEmail())
                .userName(user.getFirstName() + " " + user.getLastName())
                .userOrgCode(userOrgCode)
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
