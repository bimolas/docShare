package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.dto.*;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationRepository collaborationRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository collaborationParticipantRepository;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    private UserEntity adminUser;
    private UserEntity targetUser;
    private UserEntity secondAdminUser;
    private OrganizationEntity testOrg;
    private UserOrganizationEntity adminMembership;
    private UserOrganizationEntity targetMembership;

    @BeforeEach
    void setUp() {
        adminUser = UserEntity.builder()
                .id("admin-123")
                .email("admin@acme.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        targetUser = UserEntity.builder()
                .id("target-456")
                .email("target@acme.com")
                .firstName("Target")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .build();

        secondAdminUser = UserEntity.builder()
                .id("admin-789")
                .email("admin2@acme.com")
                .firstName("Second")
                .lastName("Admin")
                .role(Role.ROLE_USER)
                .build();

        testOrg = OrganizationEntity.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .createdBy(adminUser)
                .build();

        adminMembership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        targetMembership = UserOrganizationEntity.builder()
                .user(targetUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        lenient().when(organizationRepository.findByOrgCodeForUpdate(anyString()))
                .thenReturn(Optional.of(testOrg));
    }

    @Test
    @DisplayName("updateMemberRole: Throws ResourceNotFoundException when organization not found")
    void updateMemberRole_ThrowsNotFoundWhenOrganizationDoesNotExist() {
        when(organizationRepository.findByOrgCodeForUpdate("NON_EXISTENT"))
                .thenReturn(Optional.empty());

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();

        assertThatThrownBy(() -> organizationService.updateMemberRole("NON_EXISTENT", "target-456", request, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization not found with code: NON_EXISTENT");
    }

    @Test
    @DisplayName("suspendMember: Throws ResourceNotFoundException when organization not found")
    void suspendMember_ThrowsNotFoundWhenOrganizationDoesNotExist() {
        when(organizationRepository.findByOrgCodeForUpdate("NON_EXISTENT"))
                .thenReturn(Optional.empty());

        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Security review")
                .build();

        assertThatThrownBy(() -> organizationService.suspendMember("NON_EXISTENT", "target-456", request, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization not found with code: NON_EXISTENT");
    }

    @Test
    @DisplayName("removeMember: Throws ResourceNotFoundException when organization not found")
    void removeMember_ThrowsNotFoundWhenOrganizationDoesNotExist() {
        when(organizationRepository.findByOrgCodeForUpdate("NON_EXISTENT"))
                .thenReturn(Optional.empty());

        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Offboarding")
                .build();

        assertThatThrownBy(() -> organizationService.removeMember("NON_EXISTENT", "target-456", request, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Organization not found with code: NON_EXISTENT");
    }

    @Test
    @DisplayName("Should get organization member details successfully for admin")
    void getOrganizationMemberDetails_Success() {
        when(userOrganizationRepository.findByOrganization_OrgCodeAndStatus("ACME", MembershipStatus.ACTIVE))
                .thenReturn(List.of(adminMembership, targetMembership));

        List<OrganizationMemberDetailResponseDto> details = organizationService.getOrganizationMemberDetails(
                "ACME", MembershipStatus.ACTIVE, null, adminUser);

        assertThat(details).hasSize(2);
        assertThat(details.get(0).getUserId()).isEqualTo("admin-123");
        assertThat(details.get(1).getUserId()).isEqualTo("target-456");
    }

    @Test
    @DisplayName("Should filter member details by search query")
    void getOrganizationMemberDetails_FilterSearchQuery() {
        when(userOrganizationRepository.findByOrganization_OrgCode("ACME"))
                .thenReturn(List.of(adminMembership, targetMembership));

        List<OrganizationMemberDetailResponseDto> details = organizationService.getOrganizationMemberDetails(
                "ACME", null, "target", adminUser);

        assertThat(details).hasSize(1);
        assertThat(details.get(0).getUserId()).isEqualTo("target-456");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when non-admin requests member details")
    void getOrganizationMemberDetails_AccessDenied() {
        doThrow(new AccessDeniedException("Missing required capability: ORG_MEMBERS_MANAGE"))
                .when(capabilityEvaluatorService)
                .requireCapability(targetUser, "ACME", OrganizationCapability.ORG_MEMBERS_MANAGE);

        assertThatThrownBy(() -> organizationService.getOrganizationMemberDetails("ACME", null, null, targetUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should get single organization member detail")
    void getOrganizationMemberDetail_Success() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        OrganizationMemberDetailResponseDto detail = organizationService.getOrganizationMemberDetail("ACME", "target-456", adminUser);

        assertThat(detail.getUserId()).isEqualTo("target-456");
        assertThat(detail.getEmail()).isEqualTo("target@acme.com");
        assertThat(detail.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should build access summary for member containing teams, workspaces, and direct permissions")
    void getOrganizationMemberAccessSummary_Success() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        GroupEntity group = GroupEntity.builder()
                .id(100L)
                .name("Engineering")
                .organization(testOrg)
                .build();
        UserGroupEntity userGroup = UserGroupEntity.builder()
                .id(1L)
                .user(targetUser)
                .group(group)
                .role(GroupMemberRole.MEMBER)
                .build();
        when(userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of(userGroup));

        ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                .id(200L)
                .name("Alpha Project")
                .organization(testOrg)
                .build();
        when(projectWorkspaceRepository.findByOrganization_OrgCode("ACME"))
                .thenReturn(List.of(workspace));

        PermissionEntity permission = PermissionEntity.builder()
                .id(300L)
                .targetUserEmail("target@acme.com")
                .permissionLevel(PermissionLevel.EDIT)
                .revoked(false)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("target@acme.com", "ACME"))
                .thenReturn(List.of(permission));

        MemberAccessSummaryResponseDto summary = organizationService.getOrganizationMemberAccessSummary("ACME", "target-456", adminUser);

        assertThat(summary.getUserId()).isEqualTo("target-456");
        assertThat(summary.getTeams()).hasSize(1);
        assertThat(summary.getTeams().get(0).getGroupName()).isEqualTo("Engineering");
        assertThat(summary.getWorkspaces()).hasSize(1);
        assertThat(summary.getWorkspaces().get(0).getName()).isEqualTo("Alpha Project");
        assertThat(summary.getDirectPermissions()).hasSize(1);
        assertThat(summary.getDirectPermissions().get(0).getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
    }

    @Test
    @DisplayName("Should update member role, evict cache, and dispatch notification")
    void updateMemberRole_Success() {
        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_MANAGER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.updateMemberRole("ACME", "target-456", request, adminUser);

        assertThat(response.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
        verify(notificationService).createNotification(
                eq(targetUser), eq(adminUser), eq(NotificationType.ORG_MEMBER_ROLE_UPDATED),
                anyString(), anyString(), eq(NotificationSourceType.ORGANIZATION), eq("ACME"),
                any(), any(), anyString());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when demoting the last active administrator")
    void updateMemberRole_PreventDemotingLastAdmin() {
        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                eq("ACME"), eq(MembershipStatus.ACTIVE), anyList()))
                .thenReturn(1L);

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "admin-123", request, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot demote the last active administrator");
    }

    @Test
    @DisplayName("Should suspend member successfully and record status reason")
    void suspendMember_Success() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Security violation under investigation")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.suspendMember("ACME", "target-456", request, adminUser);

        assertThat(response.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        assertThat(response.getStatusReason()).isEqualTo("Security violation under investigation");
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
        verify(notificationService).createNotification(
                eq(targetUser), eq(adminUser), eq(NotificationType.ORG_MEMBER_SUSPENDED),
                anyString(), anyString(), eq(NotificationSourceType.ORGANIZATION), eq("ACME"),
                any(), any(), anyString());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when suspending the last active administrator")
    void suspendMember_PreventSuspendingLastAdmin() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Temporary suspension")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                eq("ACME"), eq(MembershipStatus.ACTIVE), anyList()))
                .thenReturn(1L);

        assertThatThrownBy(() -> organizationService.suspendMember("ACME", "admin-123", request, secondAdminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot suspend the last active administrator");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when administrator attempts self-suspension")
    void suspendMember_PreventSelfSuspension() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Self suspension attempt")
                .build();

        assertThatThrownBy(() -> organizationService.suspendMember("ACME", "admin-123", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-suspension is not allowed");
    }

    @Test
    @DisplayName("Should reactivate suspended member")
    void reactivateMember_Success() {
        targetMembership.setStatus(MembershipStatus.SUSPENDED);
        targetMembership.setStatusReason("Previous suspension reason");

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.reactivateMember("ACME", "target-456", adminUser);

        assertThat(response.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(response.getStatusReason()).isNull();
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
        verify(notificationService).createNotification(
                eq(targetUser), eq(adminUser), eq(NotificationType.ORG_MEMBER_REACTIVATED),
                anyString(), anyString(), eq(NotificationSourceType.ORGANIZATION), eq("ACME"),
                any(), any(), anyString());
    }

    @Test
    @DisplayName("Should remove member, clean up team memberships, and evict cache")
    void removeMember_Success() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Offboarded employee")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        UserGroupEntity userGroup = UserGroupEntity.builder().id(1L).build();
        when(userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of(userGroup));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.removeMember("ACME", "target-456", request, adminUser);

        assertThat(response.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        assertThat(response.getStatusReason()).isEqualTo("Offboarded employee");
        verify(userGroupRepository).deleteAll(List.of(userGroup));
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
        verify(notificationService).createNotification(
                eq(targetUser), eq(adminUser), eq(NotificationType.ORG_MEMBER_REMOVED),
                anyString(), anyString(), eq(NotificationSourceType.ORGANIZATION), eq("ACME"),
                any(), any(), anyString());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when administrator attempts self-removal")
    void removeMember_PreventSelfRemoval() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Self removal attempt")
                .build();

        assertThatThrownBy(() -> organizationService.removeMember("ACME", "admin-123", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-removal is not allowed");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when removing the last active administrator by another admin")
    void removeMember_PreventRemovingLastAdmin() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Account termination")
                .build();

        UserOrganizationEntity secondAdminMembership = UserOrganizationEntity.builder()
                .user(secondAdminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                eq("ACME"), eq(MembershipStatus.ACTIVE), anyList()))
                .thenReturn(1L);

        assertThatThrownBy(() -> organizationService.removeMember("ACME", "admin-123", request, secondAdminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove the last active administrator");
    }

    @Test
    @DisplayName("Should allow removing a suspended admin when another active admin remains")
    void removeMember_AllowRemovingSuspendedAdminIfActiveAdminRemains() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Offboarding suspended admin")
                .build();

        UserOrganizationEntity suspendedAdminMembership = UserOrganizationEntity.builder()
                .user(targetUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.SUSPENDED)
                .joinedAt(Instant.now())
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(suspendedAdminMembership));
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                eq("ACME"), eq(MembershipStatus.ACTIVE), anyList()))
                .thenReturn(1L); // adminUser is the 1 active admin
        when(userGroupRepository.findByUser_IdAndGroup_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of());
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.removeMember("ACME", "target-456", request, adminUser);

        assertThat(response.getStatus()).isEqualTo(MembershipStatus.REMOVED);
    }

    @Test
    @DisplayName("updateMemberRole: Manager cannot promote member to ORG_ADMIN")
    void updateMemberRole_ManagerCannotPromoteToAdmin() {
        UserEntity managerUser = UserEntity.builder()
                .id("manager-789")
                .email("manager@acme.com")
                .role(Role.ROLE_USER)
                .build();

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(capabilityEvaluatorService.hasCapability(managerUser, "ACME", OrganizationCapability.ORG_SETTINGS_MANAGE))
                .thenReturn(false);

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "target-456", request, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot promote members to Organization Admin");
    }

    @Test
    @DisplayName("updateMemberRole: Manager cannot modify ORG_ADMIN target")
    void updateMemberRole_ManagerCannotModifyAdmin() {
        UserEntity managerUser = UserEntity.builder()
                .id("manager-789")
                .email("manager@acme.com")
                .role(Role.ROLE_USER)
                .build();

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_MANAGER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        doThrow(new AccessDeniedException("Organization Managers cannot manage Organization Admins."))
                .when(capabilityEvaluatorService)
                .validateMemberManagementTarget(managerUser, "ACME", adminMembership);

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "admin-123", request, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot manage Organization Admins");
    }

    @Test
    @DisplayName("suspendMember: Manager cannot suspend ORG_ADMIN target")
    void suspendMember_ManagerCannotSuspendAdmin() {
        UserEntity managerUser = UserEntity.builder()
                .id("manager-789")
                .email("manager@acme.com")
                .role(Role.ROLE_USER)
                .build();

        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Suspension attempt")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        doThrow(new AccessDeniedException("Organization Managers cannot manage Organization Admins."))
                .when(capabilityEvaluatorService)
                .validateMemberManagementTarget(managerUser, "ACME", adminMembership);

        assertThatThrownBy(() -> organizationService.suspendMember("ACME", "admin-123", request, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot manage Organization Admins");
    }

    @Test
    @DisplayName("getOrganizationMemberDetails: Throws AccessDeniedException when actor lacks ORG_MEMBERS_MANAGE")
    void getOrganizationMemberDetails_DeniedWithoutCapability() {
        UserEntity regularActor = UserEntity.builder()
                .id("regular-1")
                .email("regular@acme.com")
                .role(Role.ROLE_USER)
                .build();

        doThrow(new AccessDeniedException("Missing required organization capability: ORG_MEMBERS_MANAGE"))
                .when(capabilityEvaluatorService)
                .requireCapability(regularActor, "ACME", OrganizationCapability.ORG_MEMBERS_MANAGE);

        assertThatThrownBy(() -> organizationService.getOrganizationMemberDetails("ACME", null, null, regularActor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_MEMBERS_MANAGE");
    }

    @Test
    @DisplayName("updateMemberRole: ORG_ADMIN cannot promote another member to ROLE_SUPER_ADMIN")
    void updateMemberRole_OrgAdminCannotPromoteMemberToSuperAdmin() {
        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "target-456", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
        verify(userOrganizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMemberRole: Self-promotion to ROLE_SUPER_ADMIN is rejected")
    void updateMemberRole_SelfPromotionToSuperAdminRejected() {
        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "admin-123", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
        verify(userOrganizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMemberRole: ORG_MANAGER cannot promote member to ROLE_SUPER_ADMIN")
    void updateMemberRole_OrgManagerCannotPromoteToSuperAdmin() {
        UserEntity managerUser = UserEntity.builder()
                .id("manager-789")
                .email("manager@acme.com")
                .role(Role.ROLE_USER)
                .build();

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "target-456", request, managerUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
        verify(userOrganizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMemberRole: Standard USER cannot promote member to ROLE_SUPER_ADMIN")
    void updateMemberRole_UserCannotPromoteToSuperAdmin() {
        UserEntity regularActor = UserEntity.builder()
                .id("user-999")
                .email("user999@acme.com")
                .role(Role.ROLE_USER)
                .build();

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        doThrow(new AccessDeniedException("Insufficient privileges to manage member in organization: ACME"))
                .when(capabilityEvaluatorService)
                .validateMemberManagementTarget(regularActor, "ACME", targetMembership);

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "target-456", request, regularActor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Insufficient privileges to manage member");
        verify(userOrganizationRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateMemberRole: ORG_ADMIN can promote member to ROLE_ORG_ADMIN")
    void updateMemberRole_OrgAdminCanPromoteToOrgAdmin() {
        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(capabilityEvaluatorService.hasCapability(adminUser, "ACME", OrganizationCapability.ORG_SETTINGS_MANAGE))
                .thenReturn(true);
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.updateMemberRole("ACME", "target-456", request, adminUser);

        assertThat(response.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        verify(userOrganizationRepository).save(any(UserOrganizationEntity.class));
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
    }

    @Test
    @DisplayName("updateMemberRole: Assignment of ROLE_USER still works")
    void updateMemberRole_AssignRoleUser_Success() {
        UserOrganizationEntity managerMembership = UserOrganizationEntity.builder()
                .user(targetUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_MANAGER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(managerMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationMemberDetailResponseDto response = organizationService.updateMemberRole("ACME", "target-456", request, adminUser);

        assertThat(response.getRole()).isEqualTo(Role.ROLE_USER);
        verify(userOrganizationRepository).save(managerMembership);
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
    }

    @Test
    @DisplayName("updateMemberRole: Global Super Admin user retains global role, and org role cannot be ROLE_SUPER_ADMIN")
    void updateMemberRole_GlobalSuperAdmin_GlobalRoleUnaffected() {
        UserEntity globalSuperAdmin = UserEntity.builder()
                .id("superadmin-1")
                .email("superadmin@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        UserOrganizationEntity saOrgMembership = UserOrganizationEntity.builder()
                .user(globalSuperAdmin)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("superadmin-1", "ACME"))
                .thenReturn(Optional.of(saOrgMembership));

        // Even for global Super Admin, attempting to assign ROLE_SUPER_ADMIN to UserOrganizationEntity.role is rejected
        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "superadmin-1", request, globalSuperAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");

        // Verify global role on UserEntity remains ROLE_SUPER_ADMIN
        assertThat(globalSuperAdmin.getRole()).isEqualTo(Role.ROLE_SUPER_ADMIN);
        assertThat(saOrgMembership.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
    }

    @Test
    @DisplayName("removeMember: Removes all workspace-manager assignments of member across multiple workspaces in org")
    void removeMember_RemovesAllWorkspaceManagerAssignments() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Member offboarding")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(101L).organization(testOrg).name("WS1").build();
        ProjectWorkspaceEntity ws2 = ProjectWorkspaceEntity.builder().id(102L).organization(testOrg).name("WS2").build();

        WorkspaceManagerEntity wm1 = WorkspaceManagerEntity.builder().id(1L).workspace(ws1).user(targetUser).build();
        WorkspaceManagerEntity wm2 = WorkspaceManagerEntity.builder().id(2L).workspace(ws2).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of(wm1, wm2));

        OrganizationMemberDetailResponseDto response = organizationService.removeMember("ACME", "target-456", request, adminUser);

        assertThat(response.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(workspaceManagerRepository).findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME");
        verify(workspaceManagerRepository).deleteAll(List.of(wm1, wm2));
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
    }

    @Test
    @DisplayName("removeMember: Does not affect workspace-manager assignments belonging to other users")
    void removeMember_DoesNotAffectOtherUsersWorkspaceManagers() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Offboarding")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity ws = ProjectWorkspaceEntity.builder().id(101L).organization(testOrg).name("WS1").build();
        WorkspaceManagerEntity targetWm = WorkspaceManagerEntity.builder().id(1L).workspace(ws).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of(targetWm));

        organizationService.removeMember("ACME", "target-456", request, adminUser);

        verify(workspaceManagerRepository).findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME");
        verify(workspaceManagerRepository).deleteAll(List.of(targetWm));
    }

    @Test
    @DisplayName("removeMember: Removing member from Org A does not remove assignments in Org B")
    void removeMember_ScopedToTargetOrganization_DoesNotDeleteOtherOrgAssignments() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Offboard from Org A")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity wsOrgA = ProjectWorkspaceEntity.builder().id(101L).organization(testOrg).name("WS_A").build();
        WorkspaceManagerEntity wmOrgA = WorkspaceManagerEntity.builder().id(1L).workspace(wsOrgA).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of(wmOrgA));

        organizationService.removeMember("ACME", "target-456", request, adminUser);

        verify(workspaceManagerRepository).findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME");
        verify(workspaceManagerRepository, never()).findByUser_IdAndWorkspace_Organization_OrgCode(eq("target-456"), eq("OTHER_ORG"));
        verify(workspaceManagerRepository).deleteAll(List.of(wmOrgA));
    }

    @Test
    @DisplayName("removeMember: Succeeds normally when member has no workspace-manager assignments")
    void removeMember_NoWorkspaceManagerAssignments_Succeeds() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Normal offboarding")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of());

        OrganizationMemberDetailResponseDto response = organizationService.removeMember("ACME", "target-456", request, adminUser);

        assertThat(response.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        verify(workspaceManagerRepository, never()).deleteAll(anyList());
    }

    @Test
    @DisplayName("removeMember: Transactional integrity - exception during workspace manager cleanup propagates")
    void removeMember_CleanupError_PropagatesException() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Offboarding")
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkspaceManagerEntity wm = WorkspaceManagerEntity.builder().id(1L).user(targetUser).build();
        when(workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode("target-456", "ACME"))
                .thenReturn(List.of(wm));
        doThrow(new RuntimeException("Database error during workspace manager cleanup"))
                .when(workspaceManagerRepository).deleteAll(List.of(wm));

        assertThatThrownBy(() -> organizationService.removeMember("ACME", "target-456", request, adminUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error during workspace manager cleanup");
    }

    // =========================================================================
    // transferOrgAdmin Tests
    // =========================================================================

    @Test
    @DisplayName("transferOrgAdmin: Successful atomic handover from ORG_ADMIN to MEMBER")
    void transferOrgAdmin_Success() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                eq("ACME"), eq(MembershipStatus.ACTIVE), anyList()))
                .thenReturn(1L);

        OrganizationMemberDetailResponseDto response = organizationService.transferOrgAdmin("ACME", "target-456", adminUser);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("target-456");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(targetMembership.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(adminMembership.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);

        // Cache eviction for both
        verify(permissionCacheService).evictUserPermissions("target@acme.com");
        verify(permissionCacheService).evictUserPermissions("admin@acme.com");

        // Audit log
        verify(auditLogService).logPermissionChange(
                eq("TRANSFER_ORG_ADMIN"),
                eq("ORGANIZATION"),
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq("ACME"),
                eq(adminUser),
                eq("target@acme.com"),
                eq("ACME"),
                isNull(),
                eq("ROLE_USER"),
                eq("ROLE_ORG_ADMIN"),
                contains("Handover: admin-123 (ROLE_ORG_ADMIN -> ROLE_ORG_MANAGER) to target-456 (ROLE_USER -> ROLE_ORG_ADMIN)"),
                isNull(),
                isNull(),
                isNull()
        );

        // Notifications for both
        verify(notificationService).createNotification(
                eq(targetUser),
                eq(adminUser),
                eq(NotificationType.ORG_MEMBER_ROLE_UPDATED),
                eq("Organization Administrator Role Assigned"),
                contains("You have been appointed as an Organization Administrator for organization ACME"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("ACME"),
                eq(NotificationCategory.GOVERNANCE),
                eq(NotificationPriority.HIGH),
                startsWith("ORG_ADMIN_TRANSFERRED_TARGET:")
        );

        verify(notificationService).createNotification(
                eq(adminUser),
                eq(adminUser),
                eq(NotificationType.ORG_MEMBER_ROLE_UPDATED),
                eq("Organization Administration Transferred"),
                contains("Your role is now ROLE_ORG_MANAGER"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("ACME"),
                eq(NotificationCategory.GOVERNANCE),
                eq(NotificationPriority.MEDIUM),
                startsWith("ORG_ADMIN_TRANSFERRED_ACTOR:")
        );
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws IllegalArgumentException on self-transfer")
    void transferOrgAdmin_SelfTransfer_ThrowsException() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "admin-123", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-transfer is not allowed");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws ResourceNotFoundException when target not found")
    void transferOrgAdmin_TargetNotFound_ThrowsException() {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("unknown-user", "ACME"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "unknown-user", adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Target member not found in organization ACME");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws IllegalArgumentException when target is suspended")
    void transferOrgAdmin_TargetSuspended_ThrowsException() {
        targetMembership.setStatus(MembershipStatus.SUSPENDED);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "target-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target user must be an active member");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws IllegalArgumentException when target is removed")
    void transferOrgAdmin_TargetRemoved_ThrowsException() {
        targetMembership.setStatus(MembershipStatus.REMOVED);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "target-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target user must be an active member");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws IllegalArgumentException when target is already ORG_ADMIN")
    void transferOrgAdmin_TargetAlreadyAdmin_ThrowsException() {
        targetMembership.setRole(Role.ROLE_ORG_ADMIN);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "target-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target user is already an Organization Administrator");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws IllegalArgumentException when target has ROLE_SUPER_ADMIN")
    void transferOrgAdmin_TargetSuperAdmin_ThrowsException() {
        targetMembership.setRole(Role.ROLE_SUPER_ADMIN);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "target-456", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws AccessDeniedException when actor is not ORG_ADMIN")
    void transferOrgAdmin_ActorNotAdmin_ThrowsException() {
        adminMembership.setRole(Role.ROLE_ORG_MANAGER);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "target-456", adminUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only Organization Administrators can transfer administration");
    }

    @Test
    @DisplayName("transferOrgAdmin: Throws AccessDeniedException when actor is suspended")
    void transferOrgAdmin_ActorSuspended_ThrowsException() {
        adminMembership.setStatus(MembershipStatus.SUSPENDED);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-123", "ACME"))
                .thenReturn(Optional.of(adminMembership));

        assertThatThrownBy(() -> organizationService.transferOrgAdmin("ACME", "target-456", adminUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not an active member");
    }

    @Test
    @DisplayName("updateMemberRole: Rejects promoting non-active member to ROLE_ORG_ADMIN")
    void updateMemberRole_SuspendedMemberToOrgAdmin_ThrowsException() {
        targetMembership.setStatus(MembershipStatus.SUSPENDED);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "target-456", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot assign administrative or managerial role to a non-active member");
    }

    @Test
    @DisplayName("updateMemberRole: Rejects promoting non-active member to ROLE_ORG_MANAGER")
    void updateMemberRole_SuspendedMemberToOrgManager_ThrowsException() {
        targetMembership.setStatus(MembershipStatus.SUSPENDED);
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-456", "ACME"))
                .thenReturn(Optional.of(targetMembership));

        UpdateMemberRoleRequestDto request = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_ORG_MANAGER)
                .build();

        assertThatThrownBy(() -> organizationService.updateMemberRole("ACME", "target-456", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot assign administrative or managerial role to a non-active member");
    }
}

