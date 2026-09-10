package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionEvaluatorServiceTeamAuditTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @Mock
    private GroupRepository groupRepository;

    private PermissionEvaluatorService evaluatorService;

    private OrganizationEntity testOrg;
    private GroupEntity testTeam;
    private UserEntity creatorUser;
    private UserEntity currentManagerUser;
    private UserEntity previousManagerUser;
    private UserEntity orgAdminUser;
    private UserEntity orgManagerUser;
    private UserEntity workspaceManagerUser;
    private UserEntity unrelatedManagerUser;
    private UserEntity superAdminUser;

    @BeforeEach
    void setUp() {
        evaluatorService = new PermissionEvaluatorService(
                permissionRepository,
                userOrganizationRepository,
                userGroupRepository,
                permissionCacheService,
                externalSharingPolicyEvaluator,
                collaborationRepository,
                collaborationParticipantRepository
        );
        evaluatorService.setGroupRepository(groupRepository);

        testOrg = OrganizationEntity.builder()
                .orgCode("FINANCE_CORP")
                .name("Finance Corp")
                .build();

        creatorUser = UserEntity.builder()
                .id("creator-uuid")
                .email("creator@example.com")
                .role(Role.ROLE_USER)
                .build();

        currentManagerUser = UserEntity.builder()
                .id("current-mgr-uuid")
                .email("current.manager@example.com")
                .role(Role.ROLE_USER)
                .build();

        previousManagerUser = UserEntity.builder()
                .id("prev-mgr-uuid")
                .email("prev.manager@example.com")
                .role(Role.ROLE_USER)
                .build();

        orgAdminUser = UserEntity.builder()
                .id("org-admin-uuid")
                .email("admin@example.com")
                .role(Role.ROLE_USER)
                .build();

        orgManagerUser = UserEntity.builder()
                .id("org-manager-uuid")
                .email("org.manager@example.com")
                .role(Role.ROLE_USER)
                .build();

        workspaceManagerUser = UserEntity.builder()
                .id("ws-manager-uuid")
                .email("ws.manager@example.com")
                .role(Role.ROLE_USER)
                .build();

        unrelatedManagerUser = UserEntity.builder()
                .id("unrelated-mgr-uuid")
                .email("unrelated.mgr@example.com")
                .role(Role.ROLE_USER)
                .build();

        superAdminUser = UserEntity.builder()
                .id("super-admin-uuid")
                .email("super@example.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        testTeam = GroupEntity.builder()
                .id(100L)
                .name("Accounting Team")
                .organization(testOrg)
                .createdBy(creatorUser)
                .status(GroupStatus.ACTIVE)
                .build();

        lenient().when(groupRepository.findById(100L)).thenReturn(Optional.of(testTeam));
    }

    @Test
    @DisplayName("SUPER_ADMIN is allowed to view team audit logs")
    void testSuperAdminAllowed() {
        boolean canView = evaluatorService.canViewTeamAuditLogs(superAdminUser, 100L);
        assertThat(canView).isTrue();
    }

    @Test
    @DisplayName("ORG_ADMIN of owning organization is allowed to view team audit logs")
    void testOrgAdminOfOwningOrgAllowed() {
        UserOrganizationEntity adminMembership = UserOrganizationEntity.builder()
                .user(orgAdminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("org-admin-uuid", "FINANCE_CORP"))
                .thenReturn(Optional.of(adminMembership));

        boolean canView = evaluatorService.canViewTeamAuditLogs(orgAdminUser, 100L);
        assertThat(canView).isTrue();
    }

    @Test
    @DisplayName("CURRENT Team Manager is allowed to view team audit logs")
    void testCurrentTeamManagerAllowed() {
        UserGroupEntity managerGroupMembership = UserGroupEntity.builder()
                .user(currentManagerUser)
                .group(testTeam)
                .role(GroupMemberRole.MANAGER)
                .build();

        when(userGroupRepository.findByUser_IdAndGroup_Id("current-mgr-uuid", 100L))
                .thenReturn(Optional.of(managerGroupMembership));

        boolean canView = evaluatorService.canViewTeamAuditLogs(currentManagerUser, 100L);
        assertThat(canView).isTrue();
    }

    @Test
    @DisplayName("Previous Team Manager after transfer is denied team audit logs")
    void testPreviousTeamManagerAfterTransferDenied() {
        UserGroupEntity demotedGroupMembership = UserGroupEntity.builder()
                .user(previousManagerUser)
                .group(testTeam)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(userGroupRepository.findByUser_IdAndGroup_Id("prev-mgr-uuid", 100L))
                .thenReturn(Optional.of(demotedGroupMembership));

        boolean canView = evaluatorService.canViewTeamAuditLogs(previousManagerUser, 100L);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("Team creator who is no longer manager is denied team audit logs")
    void testTeamCreatorNotManagerDenied() {
        UserGroupEntity creatorAsMember = UserGroupEntity.builder()
                .user(creatorUser)
                .group(testTeam)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(userGroupRepository.findByUser_IdAndGroup_Id("creator-uuid", 100L))
                .thenReturn(Optional.of(creatorAsMember));

        boolean canView = evaluatorService.canViewTeamAuditLogs(creatorUser, 100L);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("ORG_MANAGER is denied generic team audit access")
    void testOrgManagerDenied() {
        UserOrganizationEntity orgManagerMembership = UserOrganizationEntity.builder()
                .user(orgManagerUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_MANAGER)
                .status(MembershipStatus.ACTIVE)
                .build();

        lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("org-manager-uuid", "FINANCE_CORP"))
                .thenReturn(Optional.of(orgManagerMembership));
        when(userGroupRepository.findByUser_IdAndGroup_Id("org-manager-uuid", 100L))
                .thenReturn(Optional.empty());

        boolean canView = evaluatorService.canViewTeamAuditLogs(orgManagerUser, 100L);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("WORKSPACE_MANAGER without team manager role is denied team audit logs")
    void testWorkspaceManagerDenied() {
        when(userGroupRepository.findByUser_IdAndGroup_Id("ws-manager-uuid", 100L))
                .thenReturn(Optional.empty());

        boolean canView = evaluatorService.canViewTeamAuditLogs(workspaceManagerUser, 100L);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("Manager of an unrelated team is denied team audit logs")
    void testUnrelatedTeamManagerDenied() {
        when(userGroupRepository.findByUser_IdAndGroup_Id("unrelated-mgr-uuid", 100L))
                .thenReturn(Optional.empty());

        boolean canView = evaluatorService.canViewTeamAuditLogs(unrelatedManagerUser, 100L);
        assertThat(canView).isFalse();
    }
}
