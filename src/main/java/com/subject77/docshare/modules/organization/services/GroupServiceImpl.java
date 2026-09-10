package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.dto.GroupRequestDto;
import com.subject77.docshare.modules.organization.dto.GroupResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberResponseDto;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final UserRepository userRepository;
    private final FolderRepository folderRepository;
    private final PermissionRepository permissionRepository;
    private final PermissionCacheService permissionCacheService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public GroupResponseDto createGroup(String orgCode, GroupRequestDto request, UserEntity currentUser) {
        OrganizationEntity org = organizationRepository.findById(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with code: " + orgCode));

        validateOrgAdminPermission(currentUser.getId(), orgCode, currentUser);

        if (groupRepository.existsByNameAndOrganization_OrgCode(request.getName(), orgCode)) {
            throw new IllegalArgumentException("Group with name '" + request.getName() + "' already exists in organization: " + orgCode);
        }

        String initialManagerUserId = request.getInitialManagerUserId();
        if (initialManagerUserId == null || initialManagerUserId.isBlank()) {
            throw new IllegalArgumentException("Initial Team Manager user ID is required when creating a group.");
        }

        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(initialManagerUserId, orgCode, MembershipStatus.ACTIVE)) {
            throw new IllegalArgumentException("Initial Team Manager must be an active member of organization " + orgCode);
        }

        UserEntity managerUser = userRepository.findById(initialManagerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + initialManagerUserId));

        GroupEntity group = GroupEntity.builder()
                .name(request.getName())
                .description(request.getDescription())
                .organization(org)
                .createdBy(currentUser)
                .status(GroupStatus.ACTIVE)
                .build();

        GroupEntity savedGroup = groupRepository.save(group);

        UserGroupEntity managerGroup = UserGroupEntity.builder()
                .user(managerUser)
                .group(savedGroup)
                .role(GroupMemberRole.MANAGER)
                .build();
        userGroupRepository.save(managerGroup);

        if (permissionCacheService != null && managerUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(managerUser.getEmail());
        }

        List<String> memberUserIds = request.getInitialMemberUserIds();
        if (memberUserIds != null && !memberUserIds.isEmpty()) {
            Set<String> uniqueMemberIds = new HashSet<>(memberUserIds);
            uniqueMemberIds.remove(initialManagerUserId); // manager is already added

            for (String memberId : uniqueMemberIds) {
                if (memberId == null || memberId.isBlank()) continue;
                if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(memberId, orgCode, MembershipStatus.ACTIVE)) {
                    throw new IllegalArgumentException("User " + memberId + " must be an active member of organization " + orgCode + " to be added to team.");
                }
                UserEntity memberUser = userRepository.findById(memberId)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + memberId));

                UserGroupEntity memberGroup = UserGroupEntity.builder()
                        .user(memberUser)
                        .group(savedGroup)
                        .role(GroupMemberRole.MEMBER)
                        .build();
                userGroupRepository.save(memberGroup);

                if (permissionCacheService != null && memberUser.getEmail() != null) {
                    permissionCacheService.evictUserPermissions(memberUser.getEmail());
                }

                if (auditLogService != null) {
                    auditLogService.logPermissionChange(
                            AuditPermissionChangeCommand.builder()
                                    .action("TEAM_MEMBER_ADD")
                                    .targetType("GROUP")
                                    .targetId(savedGroup.getId())
                                    .groupId(savedGroup.getId())
                                    .spaceType(SpaceType.ORGANIZATION)
                                    .orgCode(orgCode)
                                    .actorUserId(currentUser.getId())
                                    .grantedBy(currentUser)
                                    .grantedToEmail(memberUser.getEmail())
                                    .permissionLevel(PermissionLevel.VIEW)
                                    .newValue(GroupMemberRole.MEMBER.name())
                                    .details("Added member " + memberUser.getEmail() + " to team '" + savedGroup.getName() + "'")
                                    .timestamp(Instant.now())
                                    .build()
                    );
                }
            }
        }

        log.info("Created group '{}' (ID: {}) in org {} by user {} with manager {}", savedGroup.getName(), savedGroup.getId(), orgCode, currentUser.getId(), initialManagerUserId);

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_CREATE")
                            .targetType("GROUP")
                            .targetId(savedGroup.getId())
                            .groupId(savedGroup.getId())
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .grantedToEmail(managerUser.getEmail())
                            .permissionLevel(PermissionLevel.VIEW)
                            .newValue(savedGroup.getName())
                            .details("Created team '" + savedGroup.getName() + "' with initial manager " + managerUser.getEmail())
                            .timestamp(Instant.now())
                            .build()
            );
        }

        if (notificationService != null) {
            try {
                String managerAssignedKey = "TEAM_MANAGER_ASSIGNED:" + savedGroup.getId() + ":" + managerUser.getId();
                notificationService.createNotification(
                        managerUser,
                        currentUser,
                        NotificationType.TEAM_MANAGER_ASSIGNED,
                        "Assigned Team Manager",
                        "You were assigned as manager of new team " + savedGroup.getName(),
                        NotificationSourceType.TEAM,
                        savedGroup.getId().toString(),
                        NotificationCategory.TEAM,
                        NotificationPriority.MEDIUM,
                        managerAssignedKey
                );
            } catch (Exception e) {
                log.error("Failed to send TEAM_MANAGER_ASSIGNED notification to user {}: {}", managerUser.getId(), e.getMessage());
            }
        }

        return mapToGroupResponse(savedGroup);
    }

    @Override
    @Transactional
    public GroupResponseDto updateGroup(String orgCode, Long groupId, GroupRequestDto request, UserEntity currentUser) {
        GroupEntity group = groupRepository.findByIdAndOrganization_OrgCode(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));

        validateGroupActive(group);
        validateOrgAdminPermission(currentUser.getId(), orgCode, currentUser);

        String oldName = group.getName();
        if (request.getName() != null && !request.getName().equals(group.getName())) {
            if (groupRepository.existsByNameAndOrganization_OrgCode(request.getName(), orgCode)) {
                throw new IllegalArgumentException("Group with name '" + request.getName() + "' already exists in organization: " + orgCode);
            }
            group.setName(request.getName());
        }

        if (request.getDescription() != null) {
            group.setDescription(request.getDescription());
        }

        GroupEntity savedGroup = groupRepository.save(group);
        log.info("Updated group ID {} in org {} by user {}", groupId, orgCode, currentUser.getId());

        if (auditLogService != null) {
            boolean nameChanged = !oldName.equals(savedGroup.getName());
            String details = nameChanged
                    ? "Renamed team from '" + oldName + "' to '" + savedGroup.getName() + "'"
                    : "Updated team '" + savedGroup.getName() + "' description";

            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_RENAME")
                            .targetType("GROUP")
                            .targetId(savedGroup.getId())
                            .groupId(savedGroup.getId())
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .permissionLevel(PermissionLevel.VIEW)
                            .previousValue(oldName)
                            .newValue(savedGroup.getName())
                            .details(details)
                            .timestamp(Instant.now())
                            .build()
            );
        }

        return mapToGroupResponse(savedGroup);
    }

    @Override
    @Transactional
    public GroupResponseDto archiveGroup(String orgCode, Long groupId, UserEntity currentUser) {
        GroupEntity group = groupRepository.findByIdAndOrganization_OrgCode(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));

        validateGroupActive(group);
        validateOrgAdminPermission(currentUser.getId(), orgCode, currentUser);

        group.setStatus(GroupStatus.ARCHIVED);
        GroupEntity savedGroup = groupRepository.save(group);
        log.info("Archived group ID {} in org {} by user {}", groupId, orgCode, currentUser.getId());

        // Suspend all boundary allocations and member delegations for this team
        List<PermissionEntity> groupPerms = permissionRepository.findByTargetGroup_Id(groupId);
        for (PermissionEntity perm : groupPerms) {
            if (!perm.isRevoked() && !perm.isSuspended()) {
                perm.setSuspended(true);
                perm.setRecordHash(PermissionHashUtils.calculateRecordHash(perm));
                permissionRepository.save(perm);
                if (perm.getTargetUserEmail() != null && permissionCacheService != null) {
                    permissionCacheService.evictUserPermissions(perm.getTargetUserEmail());
                }
            }
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_ARCHIVE")
                            .targetType("GROUP")
                            .targetId(savedGroup.getId())
                            .groupId(savedGroup.getId())
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .permissionLevel(PermissionLevel.VIEW)
                            .details("Archived team '" + savedGroup.getName() + "'")
                            .timestamp(Instant.now())
                            .build()
            );
        }

        return mapToGroupResponse(savedGroup);
    }

    @Override
    @Transactional
    public GroupResponseDto unarchiveGroup(String orgCode, Long groupId, UserEntity currentUser) {
        GroupEntity group = groupRepository.findByIdAndOrganization_OrgCode(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));

        validateOrgAdminPermission(currentUser.getId(), orgCode, currentUser);

        if (group.getStatus() == GroupStatus.ACTIVE) {
            throw new IllegalStateException("Group " + groupId + " is already active.");
        }

        group.setStatus(GroupStatus.ACTIVE);
        GroupEntity savedGroup = groupRepository.save(group);
        log.info("Unarchived group ID {} in org {} by user {}", groupId, orgCode, currentUser.getId());

        // Selective integrity restoration for suspended permissions
        List<PermissionEntity> groupPerms = permissionRepository.findByTargetGroup_Id(groupId);
        for (PermissionEntity perm : groupPerms) {
            if (!perm.isRevoked() && perm.isSuspended()) {
                boolean canRestore = true;
                if (perm.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER && perm.getTargetUserEmail() != null) {
                    Optional<UserEntity> userOpt = userRepository.findByEmail(perm.getTargetUserEmail());
                    if (userOpt.isEmpty()
                            || !userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(userOpt.get().getId(), orgCode, MembershipStatus.ACTIVE)
                            || !userGroupRepository.findByUser_IdAndGroup_Id(userOpt.get().getId(), groupId).isPresent()) {
                        canRestore = false;
                        perm.setRevoked(true);
                        perm.setRevokedAt(Instant.now());
                        perm.setRevokedBy(currentUser);
                        perm.setNote("REVOKED_DURING_UNARCHIVE_VALIDATION: user no longer active team member");
                    }
                }
                if (perm.getFolder() != null && !folderRepository.existsById(perm.getFolder().getId())) {
                    canRestore = false;
                    perm.setRevoked(true);
                    perm.setRevokedAt(Instant.now());
                    perm.setRevokedBy(currentUser);
                }
                if (canRestore) {
                    perm.setSuspended(false);
                }
                perm.setRecordHash(PermissionHashUtils.calculateRecordHash(perm));
                permissionRepository.save(perm);
                if (perm.getTargetUserEmail() != null && permissionCacheService != null) {
                    permissionCacheService.evictUserPermissions(perm.getTargetUserEmail());
                }
            }
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_RESTORE")
                            .targetType("GROUP")
                            .targetId(savedGroup.getId())
                            .groupId(savedGroup.getId())
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .permissionLevel(PermissionLevel.VIEW)
                            .details("Restored team '" + savedGroup.getName() + "'")
                            .timestamp(Instant.now())
                            .build()
            );
        }

        return mapToGroupResponse(savedGroup);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponseDto> getOrganizationGroups(String orgCode, UserEntity currentUser) {
        validateUserInOrganization(currentUser.getId(), orgCode);

        List<GroupEntity> groups = groupRepository.findByOrganization_OrgCode(orgCode);
        if (groups.isEmpty()) {
            return List.of();
        }

        List<Object[]> countResults = groupRepository.findGroupIdsWithMemberCountByOrgCode(orgCode);
        java.util.Map<Long, Integer> memberCountMap = countResults.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue()
                ));

        return groups.stream()
                .map(group -> mapToGroupResponse(group, memberCountMap.getOrDefault(group.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponseDto getGroupById(String orgCode, Long groupId, UserEntity currentUser) {
        validateUserInOrganization(currentUser.getId(), orgCode);

        GroupEntity group = groupRepository.findByIdAndOrganization_OrgCode(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));

        return mapToGroupResponse(group);
    }

    @Override
    @Transactional
    public void addMemberToGroup(String orgCode, Long groupId, String targetUserId, UserEntity currentUser) {
        addMemberToGroup(orgCode, groupId, targetUserId, GroupMemberRole.MEMBER, currentUser);
    }

    @Override
    @Transactional
    public void addMemberToGroup(String orgCode, Long groupId, String targetUserId, GroupMemberRole role, UserEntity currentUser) {
        if (role == GroupMemberRole.MANAGER) {
            throw new IllegalArgumentException("Cannot assign MANAGER role via addMemberToGroup. Use transferTeamManager instead.");
        }

        GroupEntity group = groupRepository.findByIdAndOrganization_OrgCode(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));

        validateGroupActive(group);
        validateTeamManagerOrOrgAdmin(currentUser, groupId, orgCode);

        UserEntity targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(targetUserId, orgCode, MembershipStatus.ACTIVE)) {
            throw new IllegalArgumentException("User must be an active member of organization " + orgCode + " before joining a group.");
        }

        if (userGroupRepository.existsByUser_IdAndGroup_Id(targetUserId, groupId)) {
            throw new IllegalArgumentException("User is already a member of this group.");
        }

        GroupMemberRole assignedRole = GroupMemberRole.MEMBER;

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(targetUser)
                .group(group)
                .role(assignedRole)
                .build();

        userGroupRepository.save(userGroup);
        log.info("Added user {} as {} to group {} in org {}", targetUserId, assignedRole, groupId, orgCode);

        if (permissionCacheService != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_MEMBER_ADD")
                            .targetType("GROUP")
                            .targetId(groupId)
                            .groupId(groupId)
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .grantedToEmail(targetUser.getEmail())
                            .permissionLevel(PermissionLevel.VIEW)
                            .newValue(assignedRole.name())
                            .details("Added member " + targetUser.getEmail() + " to team '" + group.getName() + "'")
                            .timestamp(Instant.now())
                            .build()
            );
        }

        if (notificationService != null) {
            try {
                String teamAddedKey = "TEAM_ADDED:" + group.getId() + ":" + targetUser.getId();
                notificationService.createNotification(
                        targetUser,
                        currentUser,
                        NotificationType.TEAM_ADDED,
                        "Added to Team",
                        "You were added to team " + group.getName(),
                        NotificationSourceType.TEAM,
                        group.getId().toString(),
                        NotificationCategory.TEAM,
                        NotificationPriority.MEDIUM,
                        teamAddedKey
                );
            } catch (Exception e) {
                log.error("Failed to send notification for group member add to user {}: {}", targetUserId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void updateMemberRole(String orgCode, Long groupId, String targetUserId, GroupMemberRole newRole, UserEntity currentUser) {
        if (newRole == null) {
            throw new IllegalArgumentException("Role is required.");
        }

        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("Target user ID is required.");
        }

        GroupEntity group = lockGroup(groupId, orgCode);

        validateGroupActive(group);
        validateCanTransferTeamManager(currentUser, groupId, orgCode);

        UserGroupEntity targetUserGroup = userGroupRepository.findByUser_IdAndGroup_Id(targetUserId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of group " + groupId));

        if (targetUserGroup.getRole() == newRole) {
            log.info("User {} already has role {} in group {}", targetUserId, newRole, groupId);
            return;
        }

        if (newRole == GroupMemberRole.MANAGER) {
            if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(targetUserId, orgCode, MembershipStatus.ACTIVE)) {
                throw new IllegalArgumentException("User " + targetUserId + " must be an active member of organization " + orgCode + " to become Team Manager.");
            }
            executeTransferManager(group, orgCode, groupId, targetUserGroup, targetUserId, currentUser);
            return;
        }

        if (newRole == GroupMemberRole.MEMBER) {
            throw new IllegalArgumentException("Cannot demote Team Manager to Member directly. A Team must maintain an active Team Manager. Use transferTeamManager to assign a successor.");
        }
    }

    private GroupEntity lockGroup(Long groupId, String orgCode) {
        return groupRepository.findByIdAndOrganization_OrgCodeForUpdate(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));
    }

    @Override
    @Transactional
    public void removeMemberFromGroup(String orgCode, Long groupId, String targetUserId, UserEntity currentUser) {
        GroupEntity group = lockGroup(groupId, orgCode);

        validateGroupActive(group);
        validateTeamManagerOrOrgAdmin(currentUser, groupId, orgCode);

        UserGroupEntity userGroup = userGroupRepository.findByUser_IdAndGroup_Id(targetUserId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("User is not a member of group " + groupId));

        if (userGroup.getRole() == GroupMemberRole.MANAGER) {
            throw new IllegalArgumentException("Cannot remove the Team Manager from the team. Transfer the manager role first before removing user from team.");
        }

        UserEntity targetUser = userGroup.getUser();
        userGroupRepository.delete(userGroup);
        log.info("Removed user {} from group {} in org {}", targetUserId, groupId, orgCode);

        if (permissionCacheService != null && targetUser != null && targetUser.getEmail() != null) {
            permissionCacheService.evictUserPermissions(targetUser.getEmail());
        }

        // Cascade revoke all active member delegations chartered under this group for this user
        if (targetUser != null && targetUser.getEmail() != null) {
            List<PermissionEntity> teamDelegations = permissionRepository.findByTargetGroup_Id(groupId);
            for (PermissionEntity perm : teamDelegations) {
                if (!perm.isRevoked() && perm.getRecipientType() == RecipientType.USER_ORGANIZATION_MEMBER
                        && perm.getTargetUserEmail() != null
                        && perm.getTargetUserEmail().equalsIgnoreCase(targetUser.getEmail())) {
                    perm.setRevoked(true);
                    perm.setSuspended(false);
                    perm.setRevokedAt(Instant.now());
                    perm.setRevokedBy(currentUser);
                    perm.setNote("REVOKED_DUE_TO_MEMBER_REMOVAL: teamId=" + groupId);
                    perm.setRecordHash(PermissionHashUtils.calculateRecordHash(perm));
                    perm.setBlockchainStatus(BlockchainStatus.PENDING);
                    permissionRepository.save(perm);
                    if (auditLogService != null) {
                        auditLogService.logPermissionChange(
                                AuditPermissionChangeCommand.builder()
                                        .action("TEAM_DELEGATION_REVOKE")
                                        .targetType(perm.getFolder() != null ? "FOLDER" : "DOCUMENT")
                                        .targetId(perm.getFolder() != null ? perm.getFolder().getId() : (perm.getDocument() != null ? perm.getDocument().getId() : null))
                                        .targetFileHash(perm.getDocumentFileHash())
                                        .folderId(perm.getFolder() != null ? perm.getFolder().getId() : (perm.getDocument() != null && perm.getDocument().getFolder() != null ? perm.getDocument().getFolder().getId() : null))
                                        .workspaceId(perm.getWorkspaceId())
                                        .groupId(groupId)
                                        .spaceType(SpaceType.ORGANIZATION)
                                        .orgCode(orgCode)
                                        .actorUserId(currentUser.getId())
                                        .grantedBy(currentUser)
                                        .grantedToEmail(targetUser.getEmail())
                                        .permissionLevel(perm.getPermissionLevel())
                                        .previousValue(perm.getPermissionLevel() != null ? perm.getPermissionLevel().name() : null)
                                        .reason("MEMBER_REMOVED_FROM_TEAM")
                                        .details("Cascade revoked team delegation due to member removal from team ID " + groupId)
                                        .recordHash(perm.getRecordHash())
                                        .validFrom(perm.getValidFrom())
                                        .expiresAt(perm.getExpiresAt())
                                        .timestamp(Instant.now())
                                        .build()
                        );
                    }
                }
            }
        }

        if (auditLogService != null && targetUser != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_MEMBER_REMOVE")
                            .targetType("GROUP")
                            .targetId(groupId)
                            .groupId(groupId)
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .grantedToEmail(targetUser.getEmail())
                            .permissionLevel(PermissionLevel.VIEW)
                            .details("Removed member " + targetUser.getEmail() + " from team '" + group.getName() + "'")
                            .timestamp(Instant.now())
                            .build()
            );
        }

        if (notificationService != null && targetUser != null) {
            try {
                String teamRemovedKey = "TEAM_REMOVED:" + groupId + ":" + targetUser.getId();
                notificationService.createNotification(
                        targetUser,
                        currentUser,
                        NotificationType.TEAM_REMOVED,
                        "Removed from Team",
                        "You were removed from team " + group.getName(),
                        NotificationSourceType.TEAM,
                        groupId.toString(),
                        NotificationCategory.TEAM,
                        NotificationPriority.HIGH,
                        teamRemovedKey
                );
            } catch (Exception e) {
                log.error("Failed to send TEAM_REMOVED notification to user {}: {}", targetUser.getId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional
    public void transferTeamManager(String orgCode, Long groupId, String newManagerUserId, UserEntity currentUser) {
        GroupEntity group = lockGroup(groupId, orgCode);

        validateGroupActive(group);
        validateCanTransferTeamManager(currentUser, groupId, orgCode);

        if (newManagerUserId == null || newManagerUserId.isBlank()) {
            throw new IllegalArgumentException("New Team Manager user ID is required.");
        }

        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(newManagerUserId, orgCode, MembershipStatus.ACTIVE)) {
            throw new IllegalArgumentException("User " + newManagerUserId + " must be an active member of organization " + orgCode + " to become Team Manager.");
        }

        UserGroupEntity targetUserGroup = userGroupRepository.findByUser_IdAndGroup_Id(newManagerUserId, groupId)
                .orElseThrow(() -> new IllegalArgumentException("Target user " + newManagerUserId + " is not a member of group " + groupId + ". Only existing members can be promoted to Team Manager."));

        if (targetUserGroup.getRole() == GroupMemberRole.MANAGER) {
            log.info("User {} is already the Team Manager of group {}", newManagerUserId, groupId);
            return;
        }

        executeTransferManager(group, orgCode, groupId, targetUserGroup, newManagerUserId, currentUser);
    }

    private void executeTransferManager(GroupEntity group, String orgCode, Long groupId, UserGroupEntity targetUserGroup, String newManagerUserId, UserEntity currentUser) {
        List<UserGroupEntity> currentManagers = userGroupRepository.findByGroup_IdAndRole(groupId, GroupMemberRole.MANAGER);
        String oldManagerEmail = currentManagers.isEmpty() || currentManagers.get(0).getUser() == null
                ? null
                : currentManagers.get(0).getUser().getEmail();

        for (UserGroupEntity manager : currentManagers) {
            manager.setRole(GroupMemberRole.MEMBER);
            userGroupRepository.save(manager);
            if (permissionCacheService != null && manager.getUser() != null && manager.getUser().getEmail() != null) {
                permissionCacheService.evictUserPermissions(manager.getUser().getEmail());
            }
        }

        targetUserGroup.setRole(GroupMemberRole.MANAGER);
        userGroupRepository.save(targetUserGroup);

        UserEntity newManager = targetUserGroup.getUser();
        if (permissionCacheService != null && newManager != null && newManager.getEmail() != null) {
            permissionCacheService.evictUserPermissions(newManager.getEmail());
        }

        log.info("Transferred Team Manager role of group {} to user {} in org {}", groupId, newManagerUserId, orgCode);

        if (auditLogService != null && newManager != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("TEAM_MANAGER_TRANSFER")
                            .targetType("GROUP")
                            .targetId(groupId)
                            .groupId(groupId)
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .grantedToEmail(newManager.getEmail())
                            .permissionLevel(PermissionLevel.VIEW)
                            .previousValue(oldManagerEmail)
                            .newValue(newManager.getEmail())
                            .details("Transferred Team Manager of team '" + group.getName() + "' from "
                                    + (oldManagerEmail != null ? oldManagerEmail : "none") + " to " + newManager.getEmail())
                            .timestamp(Instant.now())
                            .build()
            );
        }

        if (notificationService != null) {
            // Notify previous managers (excluding actor)
            for (UserGroupEntity prev : currentManagers) {
                if (prev.getUser() != null && !prev.getUser().getId().equals(currentUser.getId()) && (newManager == null || !prev.getUser().getId().equals(newManager.getId()))) {
                    try {
                        // Key: transfer event for old manager is bound to group + who took over + who is being notified
                        String transferKey = "TEAM_MANAGER_TRANSFERRED:" + group.getId() + ":" + (newManager != null ? newManager.getId() : newManagerUserId) + ":" + prev.getUser().getId();
                        notificationService.createNotification(
                                prev.getUser(),
                                currentUser,
                                NotificationType.TEAM_MANAGER_ASSIGNED,
                                "Team Manager Role Transferred",
                                "Team Manager role for team " + group.getName() + " was transferred to " + (newManager != null ? newManager.getEmail() : newManagerUserId),
                                NotificationSourceType.TEAM,
                                group.getId().toString(),
                                NotificationCategory.TEAM,
                                NotificationPriority.MEDIUM,
                                transferKey
                        );
                    } catch (Exception e) {
                        log.error("Failed to send notification to previous manager {}: {}", prev.getUser().getId(), e.getMessage());
                    }
                }
            }

            // Notify new manager (excluding actor)
            if (newManager != null && !newManager.getId().equals(currentUser.getId())) {
                try {
                    String newManagerKey = "TEAM_MANAGER_ASSIGNED:" + group.getId() + ":" + newManager.getId();
                    notificationService.createNotification(
                            newManager,
                            currentUser,
                            NotificationType.TEAM_MANAGER_ASSIGNED,
                            "Assigned Team Manager",
                            "You were assigned as manager of team " + group.getName(),
                            NotificationSourceType.TEAM,
                            group.getId().toString(),
                            NotificationCategory.TEAM,
                            NotificationPriority.MEDIUM,
                            newManagerKey
                    );
                } catch (Exception e) {
                    log.error("Failed to send TEAM_MANAGER_ASSIGNED notification to user {}: {}", newManagerUserId, e.getMessage());
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationMemberResponseDto> getGroupMembers(String orgCode, Long groupId, UserEntity currentUser) {
        validateUserInOrganization(currentUser.getId(), orgCode);

        List<UserGroupEntity> userGroups = userGroupRepository.findByGroup_Id(groupId);
        return userGroups.stream()
                .map(ug -> {
                    UserEntity u = ug.getUser();
                    UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(u.getId(), orgCode).orElse(null);
                    Role role = membership != null ? membership.getRole() : Role.ROLE_USER;
                    GroupMemberRole groupRole = ug.getRole() != null ? ug.getRole() : GroupMemberRole.MEMBER;
                    return OrganizationMemberResponseDto.builder()
                            .userId(u.getId())
                            .email(u.getEmail())
                            .firstName(u.getFirstName())
                            .lastName(u.getLastName())
                            .role(role)
                            .groupRole(groupRole)
                            .joinedAt(ug.getJoinedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteGroup(String orgCode, Long groupId, UserEntity currentUser) {
        GroupEntity group = groupRepository.findByIdAndOrganization_OrgCode(groupId, orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with ID " + groupId + " in organization " + orgCode));

        validateOrgAdminPermission(currentUser.getId(), orgCode, currentUser);

        List<UserGroupEntity> members = userGroupRepository.findByGroup_Id(groupId);

        if (permissionRepository != null) {
            List<PermissionEntity> groupPermissions = permissionRepository.findByTargetGroup_Id(groupId);
            if (!groupPermissions.isEmpty()) {
                permissionRepository.deleteAll(groupPermissions);
            }
        }

        userGroupRepository.deleteAll(members);

        if (permissionCacheService != null) {
            for (UserGroupEntity ug : members) {
                if (ug.getUser() != null && ug.getUser().getEmail() != null) {
                    permissionCacheService.evictUserPermissions(ug.getUser().getEmail());
                }
            }
        }

        groupRepository.delete(group);
        log.info("Deleted group {} from org {} by user {}", groupId, orgCode, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    AuditPermissionChangeCommand.builder()
                            .action("DELETE_GROUP")
                            .targetType("GROUP")
                            .targetId(groupId)
                            .groupId(groupId)
                            .spaceType(SpaceType.ORGANIZATION)
                            .orgCode(orgCode)
                            .actorUserId(currentUser.getId())
                            .grantedBy(currentUser)
                            .permissionLevel(PermissionLevel.VIEW)
                            .previousValue(group.getName())
                            .details("Deleted team '" + group.getName() + "'")
                            .timestamp(Instant.now())
                            .build()
            );
        }
    }

    private void validateGroupActive(GroupEntity group) {
        if (group.getStatus() == GroupStatus.ARCHIVED) {
            throw new IllegalStateException("Group " + group.getId() + " is archived and cannot be modified.");
        }
    }

    private void validateUserInOrganization(String userId, String orgCode) {
        if (!userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(userId, orgCode, MembershipStatus.ACTIVE)) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }
    }

    private void validateOrgAdminPermission(String userId, String orgCode, UserEntity currentUser) {
        if (currentUser != null && currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(userId, orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User membership is not active in organization: " + orgCode);
        }

        if (membership.getRole() != Role.ROLE_ORG_ADMIN && membership.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Organization Admin privileges required in organization: " + orgCode);
        }
    }

    private void validateTeamManagerOrOrgAdmin(UserEntity currentUser, Long groupId, String orgCode) {
        if (currentUser != null && currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(currentUser.getId(), orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User membership is not active in organization: " + orgCode);
        }

        Role role = membership.getRole();
        if (role == Role.ROLE_ORG_ADMIN || role == Role.ROLE_SUPER_ADMIN) {
            return;
        }

        boolean isTeamManager = userGroupRepository.existsByUser_IdAndGroup_IdAndRole(currentUser.getId(), groupId, GroupMemberRole.MANAGER);
        if (!isTeamManager) {
            throw new AccessDeniedException("Team Manager or Organization Admin privileges required for group: " + groupId);
        }
    }

    private void validateCanTransferTeamManager(UserEntity currentUser, Long groupId, String orgCode) {
        validateTeamManagerOrOrgAdmin(currentUser, groupId, orgCode);
    }

    private GroupResponseDto mapToGroupResponse(GroupEntity group) {
        int memberCount = 0;
        if (group != null && group.getId() != null && group.getOrganization() != null && group.getOrganization().getOrgCode() != null) {
            memberCount = (int) userGroupRepository.countByGroup_IdAndGroup_Organization_OrgCode(group.getId(), group.getOrganization().getOrgCode());
        }
        return mapToGroupResponse(group, memberCount);
    }

    private GroupResponseDto mapToGroupResponse(GroupEntity group, int memberCount) {
        return GroupResponseDto.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .orgCode(group.getOrganization() != null ? group.getOrganization().getOrgCode() : null)
                .status(group.getStatus() != null ? group.getStatus() : GroupStatus.ACTIVE)
                .createdByUserId(group.getCreatedBy() != null ? group.getCreatedBy().getId() : null)
                .createdAt(group.getCreatedAt())
                .memberCount(memberCount)
                .build();
    }
}
