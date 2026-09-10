package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.organization.dto.GroupRequestDto;
import com.subject77.docshare.modules.organization.dto.CreateOrganizationInvitationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationInvitationResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationInvitationRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.enums.ScopeType;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class OrganizationInvitationTeamManagerInvariantIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationInvitationService invitationService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private OrganizationInvitationRepository invitationRepository;

    @Autowired
    private UserRepository userRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.audit.services.AuditLogService auditLogService;

    private UserEntity adminUser;
    private UserEntity existingManagerUser;
    private UserEntity inviteeUser1;
    private UserEntity inviteeUser2;
    private OrganizationEntity org;
    private GroupEntity group;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        adminUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        existingManagerUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("manager_" + unique + "@test.com")
                .firstName("Existing")
                .lastName("Manager")
                .role(Role.ROLE_USER)
                .build());

        inviteeUser1 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("invitee1_" + unique + "@test.com")
                .firstName("Invitee")
                .lastName("One")
                .role(Role.ROLE_USER)
                .build());

        inviteeUser2 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("invitee2_" + unique + "@test.com")
                .firstName("Invitee")
                .lastName("Two")
                .role(Role.ROLE_USER)
                .build());

        String orgCode = "ORG_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCode)
                .name("Team Invariant Test Org")
                .build(), adminUser);
        org = organizationRepository.findById(orgCode).orElseThrow();

        // Add existingManagerUser to organization as active member
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(existingManagerUser)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        // Create a team with existingManagerUser as initial manager
        GroupRequestDto createGroupRequest = GroupRequestDto.builder()
                .name("Core Team")
                .description("Team invariant test group")
                .initialManagerUserId(existingManagerUser.getId())
                .build();
        groupService.createGroup(org.getOrgCode(), createGroupRequest, adminUser);

        List<GroupEntity> groups = groupRepository.findByOrganization_OrgCode(org.getOrgCode());
        group = groups.stream().filter(g -> "Core Team".equals(g.getName())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Requirement A: Creating an organization invitation cannot assign GroupMemberRole.MANAGER")
    void testA_CreatingInvitationCannotAssignTeamManagerRole() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeUser1.getEmail())
                .role(Role.ROLE_USER)
                .targetGroupId(group.getId())
                .targetGroupRole(GroupMemberRole.MANAGER)
                .build();

        assertThatThrownBy(() -> invitationService.createInvitation(org.getOrgCode(), request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot assign Team Manager role via organization invitation");
    }

    @Test
    @DisplayName("Requirements B, C, E, F: Accepting an invitation creates MEMBER membership, leaves existing manager unchanged, cannot create a second manager")
    void testB_C_E_F_AcceptingInvitationPreservesSingleManagerAndCreatesMember() {
        // Verify pre-condition: exactly one manager exists
        List<UserGroupEntity> managersBefore = userGroupRepository.findByGroup_IdAndRole(group.getId(), GroupMemberRole.MANAGER);
        assertThat(managersBefore).hasSize(1);
        assertThat(managersBefore.get(0).getUser().getId()).isEqualTo(existingManagerUser.getId());

        // Create invitation for inviteeUser1 with target group
        CreateOrganizationInvitationRequestDto inviteRequest = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeUser1.getEmail())
                .role(Role.ROLE_USER)
                .targetGroupId(group.getId())
                .targetGroupRole(GroupMemberRole.MEMBER)
                .build();
        OrganizationInvitationResponseDto inviteResponse = invitationService.createInvitation(org.getOrgCode(), inviteRequest, adminUser);

        // Invitee accepts invitation
        invitationService.acceptInvitation(inviteResponse.getToken(), inviteeUser1);

        // Verification B & F: Exactly one manager still exists, invitee did not become manager
        List<UserGroupEntity> managersAfter = userGroupRepository.findByGroup_IdAndRole(group.getId(), GroupMemberRole.MANAGER);
        assertThat(managersAfter).hasSize(1);
        assertThat(managersAfter.get(0).getUser().getId()).isEqualTo(existingManagerUser.getId());

        // Verification C: Invitee was added as MEMBER
        UserGroupEntity inviteeMembership = userGroupRepository.findByUser_IdAndGroup_Id(inviteeUser1.getId(), group.getId()).orElseThrow();
        assertThat(inviteeMembership.getRole()).isEqualTo(GroupMemberRole.MEMBER);

        // Verification E: Existing manager capabilities remain intact, invitee does not receive TEAM_ROSTER_MANAGE
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                existingManagerUser, OrganizationCapability.TEAM_ROSTER_MANAGE, ScopeType.GROUP, group.getId(), org.getOrgCode())).isTrue();
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                inviteeUser1, OrganizationCapability.TEAM_ROSTER_MANAGE, ScopeType.GROUP, group.getId(), org.getOrgCode())).isFalse();
    }

    @Test
    @DisplayName("Requirement D: Existing team-manager transfer continues to work as authoritative manager assignment")
    void testD_ExistingTeamManagerTransferContinuesToWork() {
        // Onboard inviteeUser1 via invitation
        CreateOrganizationInvitationRequestDto inviteRequest = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeUser1.getEmail())
                .role(Role.ROLE_USER)
                .targetGroupId(group.getId())
                .build();
        OrganizationInvitationResponseDto inviteResponse = invitationService.createInvitation(org.getOrgCode(), inviteRequest, adminUser);
        invitationService.acceptInvitation(inviteResponse.getToken(), inviteeUser1);

        // Confirm invitee is MEMBER
        assertThat(userGroupRepository.findByUser_IdAndGroup_Id(inviteeUser1.getId(), group.getId()).orElseThrow().getRole())
                .isEqualTo(GroupMemberRole.MEMBER);

        // Transfer Team Manager role to inviteeUser1
        groupService.transferTeamManager(org.getOrgCode(), group.getId(), inviteeUser1.getId(), adminUser);

        // Verify transfer succeeded: exactly one manager exists, old manager demoted to MEMBER
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).getUser().getId()).isEqualTo(inviteeUser1.getId());

        assertThat(userGroupRepository.findByUser_IdAndGroup_Id(existingManagerUser.getId(), group.getId()).orElseThrow().getRole())
                .isEqualTo(GroupMemberRole.MEMBER);

        // Capability checks
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                inviteeUser1, OrganizationCapability.TEAM_ROSTER_MANAGE, ScopeType.GROUP, group.getId(), org.getOrgCode())).isTrue();
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                existingManagerUser, OrganizationCapability.TEAM_ROSTER_MANAGE, ScopeType.GROUP, group.getId(), org.getOrgCode())).isFalse();
    }

    @Test
    @DisplayName("Requirement G: Multiple invitation attempts cannot create multiple managers")
    void testG_MultipleInvitationsCannotCreateMultipleManagers() {
        // Invite both users
        OrganizationInvitationResponseDto inv1 = invitationService.createInvitation(org.getOrgCode(), CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeUser1.getEmail())
                .targetGroupId(group.getId())
                .build(), adminUser);

        OrganizationInvitationResponseDto inv2 = invitationService.createInvitation(org.getOrgCode(), CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeUser2.getEmail())
                .targetGroupId(group.getId())
                .build(), adminUser);

        // Both accept
        invitationService.acceptInvitation(inv1.getToken(), inviteeUser1);
        invitationService.acceptInvitation(inv2.getToken(), inviteeUser2);

        // Exactly one manager exists
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).getUser().getId()).isEqualTo(existingManagerUser.getId());

        // Both invitees are members
        assertThat(userGroupRepository.findByUser_IdAndGroup_Id(inviteeUser1.getId(), group.getId()).orElseThrow().getRole())
                .isEqualTo(GroupMemberRole.MEMBER);
        assertThat(userGroupRepository.findByUser_IdAndGroup_Id(inviteeUser2.getId(), group.getId()).orElseThrow().getRole())
                .isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    @DisplayName("Requirement H: ROLE_SUPER_ADMIN remains rejected through invitations")
    void testH_RoleSuperAdminRemainsRejectedThroughInvitations() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(inviteeUser1.getEmail())
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        assertThatThrownBy(() -> invitationService.createInvitation(org.getOrgCode(), request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
    }
}
