package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.organization.dto.GroupRequestDto;
import com.subject77.docshare.modules.organization.dto.GroupResponseDto;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupServiceImplTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private GroupServiceImpl groupService;

    private UserEntity adminUser;
    private UserEntity memberUser;
    private UserEntity managerUser;
    private OrganizationEntity testOrg;
    private GroupEntity testGroup;

    @BeforeEach
    void setUp() {
        adminUser = UserEntity.builder()
                .id("admin-123")
                .email("admin@acme.com")
                .role(Role.ROLE_USER)
                .build();

        memberUser = UserEntity.builder()
                .id("user-456")
                .email("user@acme.com")
                .role(Role.ROLE_USER)
                .build();

        managerUser = UserEntity.builder()
                .id("manager-789")
                .email("manager@acme.com")
                .role(Role.ROLE_USER)
                .build();

        testOrg = OrganizationEntity.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .build();

        testGroup = GroupEntity.builder()
                .id(10L)
                .name("DevOps Team")
                .organization(testOrg)
                .status(GroupStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("Should create group in organization when requested by org admin/manager")
    void createGroup_Success() {
        GroupRequestDto request = GroupRequestDto.builder()
                .name("DevOps Team")
                .description("Engineering team")
                .initialManagerUserId("manager-789")
                .build();

        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(groupRepository.existsByNameAndOrganization_OrgCode("DevOps Team", "ACME")).thenReturn(false);
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("manager-789", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userRepository.findById("manager-789")).thenReturn(Optional.of(managerUser));
        when(groupRepository.save(any(GroupEntity.class))).thenReturn(testGroup);

        GroupResponseDto response = groupService.createGroup("ACME", request, adminUser);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("DevOps Team");
        assertThat(response.getOrgCode()).isEqualTo("ACME");
        verify(userGroupRepository).save(any(UserGroupEntity.class));
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_CREATE".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId())));
    }

    @Test
    @DisplayName("Should archive group when requested by org admin")
    void archiveGroup_Success() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponseDto response = groupService.archiveGroup("ACME", 10L, adminUser);

        assertThat(response.getStatus()).isEqualTo(GroupStatus.ARCHIVED);
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_ARCHIVE".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId())));
    }

    @Test
    @DisplayName("Should unarchive group when requested by org admin")
    void unarchiveGroup_Success() {
        testGroup.setStatus(GroupStatus.ARCHIVED);
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(groupRepository.save(any(GroupEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GroupResponseDto response = groupService.unarchiveGroup("ACME", 10L, adminUser);

        assertThat(response.getStatus()).isEqualTo(GroupStatus.ACTIVE);
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_RESTORE".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId())));
    }

    @Test
    @DisplayName("Should throw exception when archiving an already archived group")
    void archiveGroup_AlreadyArchived_ThrowsException() {
        testGroup.setStatus(GroupStatus.ARCHIVED);

        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));

        assertThatThrownBy(() -> groupService.archiveGroup("ACME", 10L, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");
    }

    @Test
    @DisplayName("Should add member to group and evict cache")
    void addMemberToGroup_Success() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userRepository.findById("user-456")).thenReturn(Optional.of(memberUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userGroupRepository.existsByUser_IdAndGroup_Id("user-456", 10L)).thenReturn(false);

        groupService.addMemberToGroup("ACME", 10L, "user-456", adminUser);

        verify(userGroupRepository, times(1)).save(any(UserGroupEntity.class));
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_MEMBER_ADD".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId()) && "user@acme.com".equals(cmd.getGrantedToEmail())));
    }

    @Test
    @DisplayName("Should allow team manager to add member to group")
    void addMemberToGroup_AsTeamManager_Success() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(managerUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("manager-789", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.existsByUser_IdAndGroup_IdAndRole("manager-789", 10L, GroupMemberRole.MANAGER))
                .thenReturn(true);
        when(userRepository.findById("user-456")).thenReturn(Optional.of(memberUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userGroupRepository.existsByUser_IdAndGroup_Id("user-456", 10L)).thenReturn(false);

        groupService.addMemberToGroup("ACME", 10L, "user-456", managerUser);

        verify(userGroupRepository, times(1)).save(any(UserGroupEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when modifying members of an archived group")
    void addMemberToGroup_ArchivedGroup_ThrowsException() {
        testGroup.setStatus(GroupStatus.ARCHIVED);
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME")).thenReturn(Optional.of(testGroup));

        assertThatThrownBy(() -> groupService.addMemberToGroup("ACME", 10L, "user-456", adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");
    }

    @Test
    @DisplayName("updateMemberRole: MEMBER -> MANAGER delegates to canonical transfer and updates manager")
    void updateMemberRole_MemberToManager_UsesCanonicalTransfer() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity managerUserGroup = UserGroupEntity.builder()
                .user(managerUser)
                .group(testGroup)
                .role(GroupMemberRole.MANAGER)
                .build();

        UserGroupEntity targetUserGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(targetUserGroup));
        when(userGroupRepository.findByGroup_IdAndRole(10L, GroupMemberRole.MANAGER))
                .thenReturn(List.of(managerUserGroup));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);

        groupService.updateMemberRole("ACME", 10L, "user-456", GroupMemberRole.MANAGER, adminUser);

        assertThat(managerUserGroup.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(targetUserGroup.getRole()).isEqualTo(GroupMemberRole.MANAGER);

        verify(userGroupRepository).save(managerUserGroup);
        verify(userGroupRepository).save(targetUserGroup);
        verify(permissionCacheService).evictUserPermissions("manager@acme.com");
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_MANAGER_TRANSFER".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId()) && "user@acme.com".equals(cmd.getGrantedToEmail())));
    }

    @Test
    @DisplayName("updateMemberRole: MEMBER -> MEMBER is an idempotent no-op")
    void updateMemberRole_MemberToMember_NoOp() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity targetUserGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(targetUserGroup));

        groupService.updateMemberRole("ACME", 10L, "user-456", GroupMemberRole.MEMBER, adminUser);

        assertThat(targetUserGroup.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        verify(userGroupRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("updateMemberRole: MANAGER -> MANAGER is an idempotent no-op")
    void updateMemberRole_ManagerToManager_NoOp() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity managerUserGroup = UserGroupEntity.builder()
                .user(managerUser)
                .group(testGroup)
                .role(GroupMemberRole.MANAGER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("manager-789", 10L))
                .thenReturn(Optional.of(managerUserGroup));

        groupService.updateMemberRole("ACME", 10L, "manager-789", GroupMemberRole.MANAGER, adminUser);

        assertThat(managerUserGroup.getRole()).isEqualTo(GroupMemberRole.MANAGER);
        verify(userGroupRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("updateMemberRole: MANAGER -> MEMBER is rejected to prevent zero-manager state")
    void updateMemberRole_ManagerToMember_Rejected() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity managerUserGroup = UserGroupEntity.builder()
                .user(managerUser)
                .group(testGroup)
                .role(GroupMemberRole.MANAGER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("manager-789", 10L))
                .thenReturn(Optional.of(managerUserGroup));

        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "manager-789", GroupMemberRole.MEMBER, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot demote Team Manager to Member directly");
    }

    @Test
    @DisplayName("updateMemberRole: Null role throws IllegalArgumentException")
    void updateMemberRole_NullRole_Rejected() {
        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "user-456", null, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role is required");
    }

    @Test
    @DisplayName("updateMemberRole: Target not a member of group throws ResourceNotFoundException")
    void updateMemberRole_TargetNotMember() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("non-member", 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "non-member", GroupMemberRole.MANAGER, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not a member of group");
    }

    @Test
    @DisplayName("updateMemberRole: Target suspended in organization throws IllegalArgumentException")
    void updateMemberRole_TargetSuspended() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity targetUserGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(targetUserGroup));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "user-456", GroupMemberRole.MANAGER, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an active member of organization");
    }

    @Test
    @DisplayName("updateMemberRole: Archived group throws IllegalStateException")
    void updateMemberRole_ArchivedGroup() {
        testGroup.setStatus(GroupStatus.ARCHIVED);
        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));

        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "user-456", GroupMemberRole.MANAGER, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("archived");
    }

    @Test
    @DisplayName("updateMemberRole: Caller without Org Admin or Team Manager authority throws AccessDeniedException")
    void updateMemberRole_UnauthorizedCaller() {
        UserOrganizationEntity memberMembership = UserOrganizationEntity.builder()
                .user(memberUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-456", "ACME"))
                .thenReturn(Optional.of(memberMembership));
        when(userGroupRepository.existsByUser_IdAndGroup_IdAndRole("user-456", 10L, GroupMemberRole.MANAGER))
                .thenReturn(false);

        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "user-456", GroupMemberRole.MANAGER, memberUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Team Manager or Organization Admin privileges required");
    }

    @Test
    @DisplayName("updateMemberRole: Suspended caller throws AccessDeniedException")
    void updateMemberRole_SuspendedCaller() {
        UserOrganizationEntity suspendedAdminMembership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.SUSPENDED)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(suspendedAdminMembership));

        assertThatThrownBy(() -> groupService.updateMemberRole("ACME", 10L, "user-456", GroupMemberRole.MANAGER, adminUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membership is not active");
    }

    @Test
    @DisplayName("Should transfer Team Manager role from current manager to target active member")
    void transferTeamManager_Success() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity managerUserGroup = UserGroupEntity.builder()
                .user(managerUser)
                .group(testGroup)
                .role(GroupMemberRole.MANAGER)
                .build();

        UserGroupEntity targetUserGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(targetUserGroup));
        when(userGroupRepository.findByGroup_IdAndRole(10L, GroupMemberRole.MANAGER))
                .thenReturn(List.of(managerUserGroup));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);

        groupService.transferTeamManager("ACME", 10L, "user-456", adminUser);

        assertThat(managerUserGroup.getRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(targetUserGroup.getRole()).isEqualTo(GroupMemberRole.MANAGER);

        verify(userGroupRepository).save(managerUserGroup);
        verify(userGroupRepository).save(targetUserGroup);
        verify(permissionCacheService).evictUserPermissions("manager@acme.com");
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_MANAGER_TRANSFER".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId()) && "user@acme.com".equals(cmd.getGrantedToEmail())));
    }

    @Test
    @DisplayName("Should remove member from group and evict cache")
    void removeMemberFromGroup_Success() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(userGroup));

        groupService.removeMemberFromGroup("ACME", 10L, "user-456", adminUser);

        verify(userGroupRepository).delete(userGroup);
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "TEAM_MEMBER_REMOVE".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId()) && "user@acme.com".equals(cmd.getGrantedToEmail())));
    }

    @Test
    @DisplayName("Should revoke team delegations upon member removal while preserving independent shares")
    void removeMemberFromGroup_RevokesTeamDelegations_PreservesIndependentShares() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(userGroup));

        PermissionEntity teamDelegation = PermissionEntity.builder()
                .id(101L)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.USER_ORGANIZATION_MEMBER)
                .targetGroup(testGroup)
                .targetUserEmail("user@acme.com")
                .permissionLevel(com.subject77.docshare.modules.permission.entities.PermissionLevel.VIEW)
                .revoked(false)
                .build();

        when(permissionRepository.findByTargetGroup_Id(10L))
                .thenReturn(List.of(teamDelegation));

        groupService.removeMemberFromGroup("ACME", 10L, "user-456", adminUser);

        assertThat(teamDelegation.isRevoked()).isTrue();
        verify(permissionRepository).save(teamDelegation);
        verify(userGroupRepository).delete(userGroup);
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
    }

    @Test
    @DisplayName("Should delete group, remove permissions, evict cache for members and log audit")
    void deleteGroup_Success() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(memberUser)
                .group(testGroup)
                .build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(100L)
                .targetGroup(testGroup)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userGroupRepository.findByGroup_Id(10L))
                .thenReturn(List.of(userGroup));
        when(permissionRepository.findByTargetGroup_Id(10L))
                .thenReturn(List.of(perm));

        groupService.deleteGroup("ACME", 10L, adminUser);

        verify(permissionRepository).deleteAll(List.of(perm));
        verify(userGroupRepository).deleteAll(List.of(userGroup));
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        verify(groupRepository).delete(testGroup);
        verify(auditLogService).logPermissionChange(argThat((AuditPermissionChangeCommand cmd) -> "DELETE_GROUP".equals(cmd.getAction()) && Long.valueOf(10L).equals(cmd.getTargetId())));
    }

    @Test
    @DisplayName("Should throw exception when adding user who is not a member of the organization to a group")
    void addMemberToGroup_UserNotInOrg() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userRepository.findById("user-456")).thenReturn(Optional.of(memberUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> groupService.addMemberToGroup("ACME", 10L, "user-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member of organization");
    }

    @Test
    @DisplayName("Should get organization groups with exact bulk member counts and empty group 0 count")
    void getOrganizationGroups_PopulatesBulkMemberCounts() {
        GroupEntity group1 = GroupEntity.builder()
                .id(10L)
                .name("DevOps")
                .organization(testOrg)
                .status(GroupStatus.ACTIVE)
                .build();

        GroupEntity group2 = GroupEntity.builder()
                .id(20L)
                .name("Security")
                .organization(testOrg)
                .status(GroupStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("admin-123", "ACME", MembershipStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findByOrganization_OrgCode("ACME")).thenReturn(List.of(group1, group2));
        when(groupRepository.findGroupIdsWithMemberCountByOrgCode("ACME"))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 4L}, new Object[]{20L, 0L}));

        List<GroupResponseDto> result = groupService.getOrganizationGroups("ACME", adminUser);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(10L);
        assertThat(result.get(0).getMemberCount()).isEqualTo(4);
        assertThat(result.get(1).getId()).isEqualTo(20L);
        assertThat(result.get(1).getMemberCount()).isEqualTo(0);

        verify(groupRepository).findByOrganization_OrgCode("ACME");
        verify(groupRepository).findGroupIdsWithMemberCountByOrgCode("ACME");
    }

    @Test
    @DisplayName("Should get single group by ID with accurate member count")
    void getGroupById_ReturnsSingleGroupWithMemberCount() {
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("admin-123", "ACME", MembershipStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userGroupRepository.countByGroup_IdAndGroup_Organization_OrgCode(10L, "ACME")).thenReturn(3L);

        GroupResponseDto result = groupService.getGroupById("ACME", 10L, adminUser);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getMemberCount()).isEqualTo(3);
        verify(userGroupRepository).countByGroup_IdAndGroup_Organization_OrgCode(10L, "ACME");
    }

    @Test
    @DisplayName("Should ensure organizations cannot affect each other's group member counts")
    void getOrganizationGroups_OrgIsolationEnforced() {
        OrganizationEntity otherOrg = OrganizationEntity.builder()
                .orgCode("BETA")
                .name("Beta Inc")
                .build();

        GroupEntity betaGroup = GroupEntity.builder()
                .id(99L)
                .name("Beta Team")
                .organization(otherOrg)
                .status(GroupStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("admin-123", "BETA", MembershipStatus.ACTIVE)).thenReturn(true);
        when(groupRepository.findByOrganization_OrgCode("BETA")).thenReturn(List.of(betaGroup));
        when(groupRepository.findGroupIdsWithMemberCountByOrgCode("BETA"))
                .thenReturn(List.<Object[]>of(new Object[]{99L, 12L}));

        List<GroupResponseDto> result = groupService.getOrganizationGroups("BETA", adminUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrgCode()).isEqualTo("BETA");
        assertThat(result.get(0).getMemberCount()).isEqualTo(12);

        verify(groupRepository).findByOrganization_OrgCode("BETA");
        verify(groupRepository).findGroupIdsWithMemberCountByOrgCode("BETA");
        verify(groupRepository, never()).findGroupIdsWithMemberCountByOrgCode("ACME");
    }

    // -----------------------------------------------------------------------
    // Cache-isolation tests (required by security audit)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Cache eviction targets only the affected user — unrelated users are never evicted")
    void addMemberToGroup_OnlyAffectedUserCacheEvicted() {
        // adminUser performs the add; memberUser is the one being added.
        // The cache for adminUser (the actor) must never be touched.
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userRepository.findById("user-456")).thenReturn(Optional.of(memberUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userGroupRepository.existsByUser_IdAndGroup_Id("user-456", 10L)).thenReturn(false);

        groupService.addMemberToGroup("ACME", 10L, "user-456", adminUser);

        // Only the added user's cache must be evicted.
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        // The acting admin's cache must NOT be touched.
        verify(permissionCacheService, never()).evictUserPermissions("admin@acme.com");
        // No other cache operations must occur.
        verify(permissionCacheService, times(1)).evictUserPermissions(any());
    }

    @Test
    @DisplayName("Removing a member from org A does not evict cache for an unrelated user in org B")
    void removeMemberFromGroup_DoesNotEvictUnrelatedOrgUserCache() {
        // Org A (ACME) removes memberUser from group 10.
        // managerUser belongs to a completely different org (BETA) and must never be touched.
        UserOrganizationEntity acmeMembership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity userGroupAcme = UserGroupEntity.builder()
                .user(memberUser)       // user@acme.com
                .group(testGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(acmeMembership));
        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.of(userGroupAcme));

        groupService.removeMemberFromGroup("ACME", 10L, "user-456", adminUser);

        // Only the removed user's (ACME) cache is evicted.
        verify(permissionCacheService).evictUserPermissions("user@acme.com");
        // The BETA org user (managerUser) is never touched.
        verify(permissionCacheService, never()).evictUserPermissions("manager@acme.com");
        // Exactly one eviction total.
        verify(permissionCacheService, times(1)).evictUserPermissions(any());
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when ROLE_ORG_MANAGER attempts to create a group")
    void createGroup_AsOrgManager_ThrowsAccessDenied() {
        GroupRequestDto request = GroupRequestDto.builder()
                .name("DevOps Team")
                .initialManagerUserId("manager-789")
                .build();

        UserEntity orgManagerUser = UserEntity.builder()
                .id("orgmgr-101")
                .email("orgmgr@acme.com")
                .role(Role.ROLE_USER)
                .build();

        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(orgManagerUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_MANAGER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("orgmgr-101", "ACME"))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> groupService.createGroup("ACME", request, orgManagerUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Organization Admin privileges required");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when Team Manager without Org Admin role attempts to archive a group")
    void archiveGroup_AsTeamManager_ThrowsAccessDenied() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(managerUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("manager-789", "ACME"))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(() -> groupService.archiveGroup("ACME", 10L, managerUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("Organization Admin privileges required");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when target user is not a member of the group during manager transfer")
    void transferTeamManager_TargetNotMember_ThrowsException() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userGroupRepository.findByUser_IdAndGroup_Id("user-456", 10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.transferTeamManager("ACME", 10L, "user-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a member of group");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when target user is suspended in organization during manager transfer")
    void transferTeamManager_TargetSuspendedInOrg_ThrowsException() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME")).thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("user-456", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(false);

        assertThatThrownBy(() -> groupService.transferTeamManager("ACME", 10L, "user-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be an active member of organization");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when attempting to remove the active Team Manager from the group")
    void removeMemberFromGroup_AttemptRemoveManager_ThrowsException() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        UserGroupEntity managerUserGroup = UserGroupEntity.builder()
                .user(managerUser)
                .group(testGroup)
                .role(GroupMemberRole.MANAGER)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(membership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("manager-789", 10L))
                .thenReturn(Optional.of(managerUserGroup));

        assertThatThrownBy(() -> groupService.removeMemberFromGroup("ACME", 10L, "manager-789", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot remove the Team Manager from the team");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when group is not found during manager transfer")
    void transferTeamManager_GroupNotFound_ThrowsResourceNotFoundException() {
        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.transferTeamManager("ACME", 10L, "user-456", adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found with ID 10");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when Team Manager is suspended in organization during manager transfer")
    void transferTeamManager_SuspendedTeamManager_ThrowsAccessDenied() {
        UserOrganizationEntity suspendedMembership = UserOrganizationEntity.builder()
                .user(managerUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.SUSPENDED)
                .build();

        when(groupRepository.findByIdAndOrganization_OrgCodeForUpdate(10L, "ACME"))
                .thenReturn(Optional.of(testGroup));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("manager-789", "ACME"))
                .thenReturn(Optional.of(suspendedMembership));

        assertThatThrownBy(() -> groupService.transferTeamManager("ACME", 10L, "user-456", managerUser))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("membership is not active in organization");
    }
}


