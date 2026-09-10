package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.enums.ScopeType;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityEvaluatorServiceTest {

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @InjectMocks
    private CapabilityEvaluatorServiceImpl capabilityEvaluatorService;

    private OrganizationEntity org;
    private UserEntity superAdmin;
    private UserEntity orgAdmin;
    private UserEntity orgManager;
    private UserEntity standardUser;

    private UserOrganizationEntity orgAdminMembership;
    private UserOrganizationEntity orgManagerMembership;
    private UserOrganizationEntity userMembership;

    @BeforeEach
    void setUp() {
        org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();

        superAdmin = UserEntity.builder().id("super-1").email("super@acme.com").role(Role.ROLE_SUPER_ADMIN).build();
        orgAdmin = UserEntity.builder().id("admin-1").email("admin@acme.com").role(Role.ROLE_USER).build();
        orgManager = UserEntity.builder().id("mgr-1").email("mgr@acme.com").role(Role.ROLE_USER).build();
        standardUser = UserEntity.builder().id("user-1").email("user@acme.com").role(Role.ROLE_USER).build();

        orgAdminMembership = UserOrganizationEntity.builder()
                .id(1L).user(orgAdmin).organization(org).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();

        orgManagerMembership = UserOrganizationEntity.builder()
                .id(2L).user(orgManager).organization(org).role(Role.ROLE_ORG_MANAGER).status(MembershipStatus.ACTIVE).build();

        userMembership = UserOrganizationEntity.builder()
                .id(3L).user(standardUser).organization(org).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).build();
    }

    @Test
    @DisplayName("SUPER_ADMIN should possess all organization capabilities unconditionally")
    void superAdmin_HasAllCapabilities() {
        for (OrganizationCapability cap : OrganizationCapability.values()) {
            assertThat(capabilityEvaluatorService.hasCapability(superAdmin, "ACME", cap)).isTrue();
            assertThatCode(() -> capabilityEvaluatorService.requireCapability(superAdmin, "ACME", cap)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ORG_ADMIN should hold all organization capabilities")
    void orgAdmin_HasAllOrganizationCapabilities() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-1", "ACME"))
                .thenReturn(Optional.of(orgAdminMembership));

        for (OrganizationCapability cap : OrganizationCapability.values()) {
            assertThat(capabilityEvaluatorService.hasCapability(orgAdmin, "ACME", cap)).isTrue();
            assertThatCode(() -> capabilityEvaluatorService.requireCapability(orgAdmin, "ACME", cap)).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("ORG_MANAGER should hold allowed operational capabilities")
    void orgManager_HasAllowedCapabilities() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_MEMBERS_MANAGE)).isTrue();
        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_MEMBERS_INVITE)).isTrue();
        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_WORKSPACES_MANAGE)).isTrue();
        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_AUDIT_VIEW)).isTrue();
    }

    @Test
    @DisplayName("ORG_MANAGER should be denied forbidden governance and team lifecycle capabilities")
    void orgManager_DeniedForbiddenCapabilities() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_SETTINGS_MANAGE)).isFalse();
        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_POLICIES_MANAGE)).isFalse();
        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_TEAMS_MANAGE)).isFalse();
        assertThat(capabilityEvaluatorService.hasCapability(orgManager, "ACME", OrganizationCapability.ORG_WORKSPACE_MANAGERS_ASSIGN)).isFalse();

        assertThatThrownBy(() -> capabilityEvaluatorService.requireCapability(orgManager, "ACME", OrganizationCapability.ORG_SETTINGS_MANAGE))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not possess capability ORG_SETTINGS_MANAGE");
    }

    @Test
    @DisplayName("ROLE_USER should hold zero organization-level administrative capabilities")
    void standardUser_ZeroCapabilities() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-1", "ACME"))
                .thenReturn(Optional.of(userMembership));

        for (OrganizationCapability cap : OrganizationCapability.values()) {
            assertThat(capabilityEvaluatorService.hasCapability(standardUser, "ACME", cap)).isFalse();
            assertThatThrownBy(() -> capabilityEvaluatorService.requireCapability(standardUser, "ACME", cap))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    @DisplayName("Inactive or suspended membership should deny all capabilities")
    void inactiveMembership_Denied() {
        UserOrganizationEntity suspended = UserOrganizationEntity.builder()
                .id(4L).user(orgAdmin).organization(org).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.SUSPENDED).build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-1", "ACME"))
                .thenReturn(Optional.of(suspended));

        assertThat(capabilityEvaluatorService.hasCapability(orgAdmin, "ACME", OrganizationCapability.ORG_SETTINGS_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("Cross-org scope should deny access when user is not a member of the target org")
    void crossOrgScope_Denied() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-1", "OTHER_ORG"))
                .thenReturn(Optional.empty());

        assertThat(capabilityEvaluatorService.hasCapability(orgAdmin, "OTHER_ORG", OrganizationCapability.ORG_SETTINGS_MANAGE)).isFalse();
    }

    @Test
    @DisplayName("ORG_MANAGER cannot target ORG_ADMIN for member management")
    void orgManager_CannotTargetOrgAdmin() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThatThrownBy(() -> capabilityEvaluatorService.validateMemberManagementTarget(orgManager, "ACME", orgAdminMembership))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot modify, suspend, or remove Organization Administrators.");
    }

    @Test
    @DisplayName("ORG_MANAGER can target ROLE_USER for member management")
    void orgManager_CanTargetRoleUser() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThatCode(() -> capabilityEvaluatorService.validateMemberManagementTarget(orgManager, "ACME", userMembership))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ORG_MANAGER cannot invite users with ROLE_ORG_ADMIN")
    void orgManager_CannotInviteOrgAdmin() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThatThrownBy(() -> capabilityEvaluatorService.validateInvitationTargetRole(orgManager, "ACME", "ROLE_ORG_ADMIN"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot invite users with administrative roles.");
    }

    @Test
    @DisplayName("ORG_MANAGER can invite users with ROLE_USER or ROLE_ORG_MANAGER")
    void orgManager_CanInviteNonAdmin() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThatCode(() -> capabilityEvaluatorService.validateInvitationTargetRole(orgManager, "ACME", "ROLE_USER"))
                .doesNotThrowAnyException();
        assertThatCode(() -> capabilityEvaluatorService.validateInvitationTargetRole(orgManager, "ACME", "ROLE_ORG_MANAGER"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ORG_MANAGER cannot invite users with ROLE_SUPER_ADMIN")
    void orgManager_CannotInviteSuperAdmin() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ACME"))
                .thenReturn(Optional.of(orgManagerMembership));

        assertThatThrownBy(() -> capabilityEvaluatorService.validateInvitationTargetRole(orgManager, "ACME", "ROLE_SUPER_ADMIN"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot invite users with administrative roles.");
    }

    @Test
    @DisplayName("ORG_ADMIN cannot invite users with ROLE_SUPER_ADMIN")
    void orgAdmin_CannotInviteSuperAdmin() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-1", "ACME"))
                .thenReturn(Optional.of(orgAdminMembership));

        assertThatThrownBy(() -> capabilityEvaluatorService.validateInvitationTargetRole(orgAdmin, "ACME", "ROLE_SUPER_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
    }

    @Test
    @DisplayName("Global Super Admin cannot invite users with ROLE_SUPER_ADMIN into an organization")
    void superAdmin_CannotInviteSuperAdmin() {
        assertThatThrownBy(() -> capabilityEvaluatorService.validateInvitationTargetRole(superAdmin, "ACME", "ROLE_SUPER_ADMIN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
    }

    @Test
    @DisplayName("ORG_ADMIN can invite users with ROLE_ORG_ADMIN")
    void orgAdmin_CanInviteOrgAdmin() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-1", "ACME"))
                .thenReturn(Optional.of(orgAdminMembership));

        assertThatCode(() -> capabilityEvaluatorService.validateInvitationTargetRole(orgAdmin, "ACME", "ROLE_ORG_ADMIN"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Team Manager holds TEAM_ROSTER_MANAGE for own team but denied for other teams")
    void teamManager_ScopeCheck() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-1", "ACME"))
                .thenReturn(Optional.of(userMembership));
        when(userGroupRepository.existsByUser_IdAndGroup_IdAndRole("user-1", 100L, GroupMemberRole.MANAGER))
                .thenReturn(true);
        when(userGroupRepository.existsByUser_IdAndGroup_IdAndRole("user-1", 200L, GroupMemberRole.MANAGER))
                .thenReturn(false);

        // Own team (100L)
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                standardUser, OrganizationCapability.TEAM_ROSTER_MANAGE, ScopeType.GROUP, 100L, "ACME")).isTrue();

        // Other team (200L)
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                standardUser, OrganizationCapability.TEAM_ROSTER_MANAGE, ScopeType.GROUP, 200L, "ACME")).isFalse();
    }

    @Test
    @DisplayName("Workspace Manager holds WORKSPACE_PERMISSIONS_MANAGE for assigned workspace but denied outside")
    void workspaceManager_ScopeCheck() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-1", "ACME"))
                .thenReturn(Optional.of(userMembership));
        when(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(10L, "user-1"))
                .thenReturn(true);
        when(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(20L, "user-1"))
                .thenReturn(false);

        // Assigned workspace (10L)
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                standardUser, OrganizationCapability.WORKSPACE_PERMISSIONS_MANAGE, ScopeType.WORKSPACE, 10L, "ACME")).isTrue();

        // Unassigned workspace (20L)
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                standardUser, OrganizationCapability.WORKSPACE_PERMISSIONS_MANAGE, ScopeType.WORKSPACE, 20L, "ACME")).isFalse();
    }
}
