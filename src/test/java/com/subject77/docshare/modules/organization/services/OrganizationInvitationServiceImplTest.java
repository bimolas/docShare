package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationInvitationServiceImplTest {

    @Mock
    private OrganizationInvitationRepository invitationRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @InjectMocks
    private OrganizationInvitationServiceImpl invitationService;

    private UserEntity adminUser;
    private UserEntity inviterUser;
    private UserEntity inviteeUser;
    private OrganizationEntity testOrg;
    private UserOrganizationEntity adminMembership;

    @BeforeEach
    void setUp() {
        adminUser = UserEntity.builder()
                .id("admin-1")
                .email("admin@acme.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        inviterUser = UserEntity.builder()
                .id("inviter-1")
                .email("inviter@acme.com")
                .firstName("Inviter")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        inviteeUser = UserEntity.builder()
                .id("invitee-1")
                .email("invitee@acme.com")
                .firstName("Invitee")
                .lastName("User")
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
                .build();

        lenient().when(organizationRepository.findByOrgCodeForUpdate(anyString())).thenReturn(Optional.of(testOrg));
        lenient().when(invitationRepository.findByTokenForUpdate(anyString()))
                .thenAnswer(invocation -> invitationRepository.findByToken(invocation.getArgument(0)));
        lenient().when(invitationRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(invocation -> invitationRepository.findById(invocation.getArgument(0)));
        lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient().when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(anyString(), anyString(), any()))
                .thenReturn(false);
    }

    @Test
    @DisplayName("Should create invitation successfully without onboarding assignments")
    void createInvitation_Basic_Success() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .note("Welcome to Acme!")
                .build();

        when(invitationRepository.existsByOrganization_OrgCodeAndInviteeEmailAndStatus("ACME", "invitee@acme.com", OrganizationInvitationStatus.PENDING))
                .thenReturn(false);
        when(userRepository.findByEmail("invitee@acme.com")).thenReturn(Optional.of(inviteeUser));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(invocation -> {
            OrganizationInvitationEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        OrganizationInvitationResponseDto response = invitationService.createInvitation("ACME", request, adminUser);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getOrgCode()).isEqualTo("ACME");
        assertThat(response.getInviteeEmail()).isEqualTo("invitee@acme.com");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.PENDING);
        assertThat(response.getToken()).isNotNull();

        verify(notificationService).createNotification(
                eq(inviteeUser),
                eq(adminUser),
                eq(NotificationType.ORG_INVITATION_SENT),
                eq("Organization Invitation"),
                contains("Acme Corp"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("100"),
                eq(NotificationCategory.INVITATION),
                eq(NotificationPriority.MEDIUM),
                eq("ORG_INVITATION_SENT:100:invitee-1")
        );
        verify(auditLogService).logPermissionChange(
                eq("CREATE_INVITATION"),
                eq("ORGANIZATION"),
                eq(100L),
                any(), any(), any(),
                eq("ACME"),
                eq(adminUser),
                eq("invitee@acme.com"),
                any(), any(),
                isNull(),
                eq("PENDING"),
                eq("Welcome to Acme!"),
                any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should create invitation successfully with group and workspace onboarding assignments")
    void createInvitation_WithOnboarding_Success() {
        GroupEntity group = GroupEntity.builder()
                .id(20L)
                .name("Engineering")
                .organization(testOrg)
                .status(GroupStatus.ACTIVE)
                .build();

        FolderEntity rootFolder = FolderEntity.builder().id(300L).name("Root").build();

        ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                .id(30L)
                .name("Dev Workspace")
                .organization(testOrg)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .rootFolder(rootFolder)
                .build();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_ORG_MANAGER)
                .targetGroupId(20L)
                .targetGroupRole(GroupMemberRole.MEMBER)
                .targetWorkspaceId(30L)
                .onboardingPermissionLevel(PermissionLevel.EDIT)
                .build();

        when(invitationRepository.existsByOrganization_OrgCodeAndInviteeEmailAndStatus("ACME", "invitee@acme.com", OrganizationInvitationStatus.PENDING))
                .thenReturn(false);
        when(groupRepository.findByIdAndOrganization_OrgCode(20L, "ACME")).thenReturn(Optional.of(group));
        when(projectWorkspaceRepository.findById(30L)).thenReturn(Optional.of(workspace));
        when(userRepository.findByEmail("invitee@acme.com")).thenReturn(Optional.of(inviteeUser));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> {
            OrganizationInvitationEntity entity = i.getArgument(0);
            entity.setId(101L);
            return entity;
        });

        OrganizationInvitationResponseDto response = invitationService.createInvitation("ACME", request, adminUser);

        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getTargetGroupId()).isEqualTo(20L);
        assertThat(response.getTargetGroupRole()).isEqualTo(GroupMemberRole.MEMBER);
        assertThat(response.getTargetWorkspaceId()).isEqualTo(30L);
        assertThat(response.getOnboardingPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
    }

    @Test
    @DisplayName("Should reject invitation creation when targetGroupRole is MANAGER")
    void createInvitation_CannotAssignTeamManagerRole() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .targetGroupId(20L)
                .targetGroupRole(GroupMemberRole.MANAGER)
                .build();

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot assign Team Manager role via organization invitation");
    }

    @Test
    @DisplayName("Should throw exception when creating invitation for active member")
    void createInvitation_AlreadyMember() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("already@acme.com")
                .build();

        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("already@acme.com", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already an active member");
    }

    @Test
    @DisplayName("Should throw exception when creating invitation for suspended member")
    void createInvitation_SuspendedMember() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("suspended@acme.com")
                .build();

        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("suspended@acme.com", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(false);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("suspended@acme.com", "ACME", MembershipStatus.SUSPENDED))
                .thenReturn(true);

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is suspended in organization");
    }

    @Test
    @DisplayName("Should allow creating invitation for removed member")
    void createInvitation_RemovedMember_Allowed() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("removed@acme.com")
                .build();

        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("removed@acme.com", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(false);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("removed@acme.com", "ACME", MembershipStatus.SUSPENDED))
                .thenReturn(false);
        when(invitationRepository.existsByOrganization_OrgCodeAndInviteeEmailAndStatus("ACME", "removed@acme.com", OrganizationInvitationStatus.PENDING))
                .thenReturn(false);
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(inv -> {
            OrganizationInvitationEntity entity = inv.getArgument(0);
            entity.setId(105L);
            return entity;
        });

        OrganizationInvitationResponseDto response = invitationService.createInvitation("ACME", request, adminUser);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(105L);
        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw exception when pending invitation already exists")
    void createInvitation_PendingExists() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("pending@acme.com")
                .build();

        when(invitationRepository.existsByOrganization_OrgCodeAndInviteeEmailAndStatus("ACME", "pending@acme.com", OrganizationInvitationStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending invitation already exists");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when actor lacks invitation capability or role authority")
    void createInvitation_NotAdmin() {
        UserEntity regularUser = UserEntity.builder().id("regular-1").email("user@acme.com").role(Role.ROLE_USER).build();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("someone@acme.com")
                .build();

        doThrow(new AccessDeniedException("Missing required organization capability: ORG_MEMBERS_INVITE"))
                .when(capabilityEvaluatorService)
                .validateInvitationTargetRole(eq(regularUser), eq("ACME"), any());

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_MEMBERS_INVITE");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when manager attempts to invite an admin role")
    void createInvitation_ManagerCannotInviteAdmin() {
        UserEntity managerUser = UserEntity.builder().id("manager-1").email("mgr@acme.com").role(Role.ROLE_USER).build();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("admin-candidate@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        doThrow(new AccessDeniedException("Organization Managers cannot invite members with Organization Admin role."))
                .when(capabilityEvaluatorService)
                .validateInvitationTargetRole(managerUser, "ACME", Role.ROLE_ORG_ADMIN.name());

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot invite members with Organization Admin role");
    }

    @Test
    @DisplayName("Should accept invitation, create org membership, apply onboarding assignments, and notify users")
    void acceptInvitation_Success() {
        GroupEntity group = GroupEntity.builder().id(20L).name("Engineering").status(GroupStatus.ACTIVE).build();
        FolderEntity rootFolder = FolderEntity.builder().id(500L).name("RootFolder").build();
        ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder().id(30L).rootFolder(rootFolder).status(ProjectWorkspaceStatus.ACTIVE).build();

        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(1L)
                .token("valid-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .targetGroup(group)
                .targetGroupRole(GroupMemberRole.MEMBER)
                .targetWorkspace(workspace)
                .onboardingPermissionLevel(PermissionLevel.EDIT)
                .build();

        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(userGroupRepository.existsByUser_IdAndGroup_Id(inviteeUser.getId(), 20L)).thenReturn(false);
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("valid-token", inviteeUser);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);
        assertThat(invitation.getRespondedAt()).isNotNull();

        // Verify Org membership created
        verify(userOrganizationRepository).save(argThat(membership ->
                membership.getUser().getId().equals(inviteeUser.getId()) &&
                membership.getOrganization().getOrgCode().equals("ACME") &&
                membership.getRole() == Role.ROLE_USER
        ));

        // Verify PermissionCache evicted
        verify(permissionCacheService).evictUserPermissions("invitee@acme.com");

        // Verify Group membership created
        verify(userGroupRepository).save(argThat(userGroup ->
                userGroup.getUser().getId().equals(inviteeUser.getId()) &&
                userGroup.getGroup().getId().equals(20L) &&
                userGroup.getRole() == GroupMemberRole.MEMBER
        ));

        // Verify Folder permission granted via root folder
        ArgumentCaptor<PermissionRequestDto> permCaptor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        verify(permissionService).grantPermission(permCaptor.capture(), eq(adminUser.getId()));
        assertThat(permCaptor.getValue().getFolderId()).isEqualTo(500L);
        assertThat(permCaptor.getValue().getTargetUserEmail()).isEqualTo("invitee@acme.com");
        assertThat(permCaptor.getValue().getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        assertThat(permCaptor.getValue().getRecipientType()).isEqualTo(RecipientType.USER_ORGANIZATION_MEMBER);
        assertThat(permCaptor.getValue().getTargetOrgCode()).isEqualTo("ACME");
        assertThat(permCaptor.getValue().getRecipientType()).isNotEqualTo(RecipientType.USER_PERSONAL);

        // Verify Notifications sent
        verify(notificationService).createNotification(
                eq(adminUser),
                eq(inviteeUser),
                eq(NotificationType.ORG_INVITATION_ACCEPTED),
                eq("Invitation Accepted"),
                contains("accepted your invitation"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("1"),
                eq(NotificationCategory.INVITATION),
                eq(NotificationPriority.MEDIUM),
                eq("ORG_INVITATION_ACCEPTED:1:admin-1")
        );
        verify(notificationService).createNotification(
                eq(inviteeUser),
                eq(inviteeUser),
                eq(NotificationType.ONBOARDING_COMPLETED),
                eq("Onboarding Completed"),
                contains("Onboarding completed"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("1"),
                eq(NotificationCategory.INVITATION),
                eq(NotificationPriority.LOW),
                eq("ONBOARDING_COMPLETED:1:invitee-1")
        );

        // Verify Audit Log
        verify(auditLogService).logPermissionChange(
                eq("ACCEPT_INVITATION"),
                eq("ORGANIZATION"),
                eq(1L),
                any(), any(), any(),
                eq("ACME"),
                eq(inviteeUser),
                eq("invitee@acme.com"),
                any(), any(),
                eq("PENDING"),
                eq("ACCEPTED"),
                any(),
                contains("AssignedRole=ROLE_USER"),
                any(), any()
        );
    }

    @Test
    @DisplayName("Should normalize legacy MANAGER role to MEMBER upon invitation acceptance")
    void acceptInvitation_WithLegacyManagerRole_NormalizesToMember() {
        GroupEntity group = GroupEntity.builder().id(20L).name("Engineering").status(GroupStatus.ACTIVE).build();

        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(2L)
                .token("legacy-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .targetGroup(group)
                .targetGroupRole(GroupMemberRole.MANAGER)
                .build();

        when(invitationRepository.findByToken("legacy-token")).thenReturn(Optional.of(invitation));
        when(userGroupRepository.existsByUser_IdAndGroup_Id(inviteeUser.getId(), 20L)).thenReturn(false);
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("legacy-token", inviteeUser);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);

        // Verify Group membership is created with MEMBER role, never MANAGER
        verify(userGroupRepository).save(argThat(userGroup ->
                userGroup.getUser().getId().equals(inviteeUser.getId()) &&
                userGroup.getGroup().getId().equals(20L) &&
                userGroup.getRole() == GroupMemberRole.MEMBER
        ));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when accepting invitation with mismatched user email")
    void acceptInvitation_EmailMismatch() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(1L)
                .token("valid-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        UserEntity wrongUser = UserEntity.builder().id("wrong-1").email("wrong@acme.com").build();

        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation("valid-token", wrongUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not match invitation invitee email");
    }

    @Test
    @DisplayName("Should expire invitation lazily if expiresAt is in the past during accept")
    void acceptInvitation_ExpiredLazy() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(1L)
                .token("expired-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("expired-token")).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation("expired-token", inviteeUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invitation has expired");

        assertThat(invitation.getStatus()).isEqualTo(OrganizationInvitationStatus.EXPIRED);
        verify(invitationRepository).save(invitation);
    }

    @Test
    @DisplayName("Should decline invitation successfully")
    void declineInvitation_Success() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(1L)
                .token("valid-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("valid-token")).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.declineInvitation("valid-token", inviteeUser);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.DECLINED);
        verify(notificationService).createNotification(
                eq(adminUser),
                eq(inviteeUser),
                eq(NotificationType.ORG_INVITATION_DECLINED),
                eq("Invitation Declined"),
                contains("declined your invitation"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("1"),
                eq(NotificationCategory.INVITATION),
                eq(NotificationPriority.MEDIUM),
                eq("ORG_INVITATION_DECLINED:1:admin-1")
        );
        verify(auditLogService).logPermissionChange(
                eq("DECLINE_INVITATION"),
                eq("ORGANIZATION"),
                eq(1L),
                any(), any(), any(),
                eq("ACME"),
                eq(inviteeUser),
                eq("invitee@acme.com"),
                any(), any(),
                eq("PENDING"),
                eq("DECLINED"),
                any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should revoke invitation successfully by inviter")
    void revokeInvitation_ByInviter_Success() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(10L)
                .organization(testOrg)
                .inviter(inviterUser)
                .inviteeEmail("invitee@acme.com")
                .invitee(inviteeUser)
                .status(OrganizationInvitationStatus.PENDING)
                .build();

        when(invitationRepository.findById(10L)).thenReturn(Optional.of(invitation));

        invitationService.revokeInvitation("ACME", 10L, inviterUser);

        assertThat(invitation.getStatus()).isEqualTo(OrganizationInvitationStatus.REVOKED);
        verify(notificationService).createNotification(
                eq(inviteeUser),
                eq(inviterUser),
                eq(NotificationType.ORG_INVITATION_REVOKED),
                eq("Invitation Revoked"),
                contains("was revoked"),
                eq(NotificationSourceType.ORGANIZATION),
                eq("10"),
                eq(NotificationCategory.INVITATION),
                eq(NotificationPriority.MEDIUM),
                eq("ORG_INVITATION_REVOKED:10:invitee-1")
        );
        verify(auditLogService).logPermissionChange(
                eq("REVOKE_INVITATION"),
                eq("ORGANIZATION"),
                eq(10L),
                any(), any(), any(),
                eq("ACME"),
                eq(inviterUser),
                eq("invitee@acme.com"),
                any(), any(),
                eq("PENDING"),
                eq("REVOKED"),
                any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should process expired invitations in batch")
    void processExpiredInvitations_Success() {
        OrganizationInvitationEntity inv1 = OrganizationInvitationEntity.builder()
                .id(1L)
                .organization(testOrg)
                .inviteeEmail("inv1@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        OrganizationInvitationEntity inv2 = OrganizationInvitationEntity.builder()
                .id(2L)
                .organization(testOrg)
                .inviteeEmail("inv2@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByStatusAndExpiresAtBefore(eq(OrganizationInvitationStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(inv1, inv2));
        when(invitationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(inv1));
        when(invitationRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(inv2));

        int count = invitationService.processExpiredInvitations();

        assertThat(count).isEqualTo(2);
        assertThat(inv1.getStatus()).isEqualTo(OrganizationInvitationStatus.EXPIRED);
        assertThat(inv2.getStatus()).isEqualTo(OrganizationInvitationStatus.EXPIRED);
        verify(invitationRepository).save(inv1);
        verify(invitationRepository).save(inv2);
        verify(auditLogService).logPermissionChange(
                eq("EXPIRED_INVITATION"),
                eq("ORGANIZATION"),
                eq(1L),
                any(), any(), any(),
                eq("ACME"),
                isNull(),
                eq("inv1@acme.com"),
                any(), any(),
                eq("PENDING"),
                eq("EXPIRED"),
                eq("Invitation expired"),
                any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should grant USER_ORGANIZATION_MEMBER permission on targetFolderId during invitation acceptance")
    void acceptInvitation_WithTargetFolder_GrantsUserOrganizationMemberPermission() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(10L)
                .token("folder-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .targetFolderId(600L)
                .onboardingPermissionLevel(PermissionLevel.VIEW)
                .build();

        when(invitationRepository.findByToken("folder-token")).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("folder-token", inviteeUser);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);

        ArgumentCaptor<PermissionRequestDto> permCaptor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        verify(permissionService).grantPermission(permCaptor.capture(), eq(adminUser.getId()));
        assertThat(permCaptor.getValue().getFolderId()).isEqualTo(600L);
        assertThat(permCaptor.getValue().getTargetUserEmail()).isEqualTo("invitee@acme.com");
        assertThat(permCaptor.getValue().getTargetOrgCode()).isEqualTo("ACME");
        assertThat(permCaptor.getValue().getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
        assertThat(permCaptor.getValue().getRecipientType()).isEqualTo(RecipientType.USER_ORGANIZATION_MEMBER);
        assertThat(permCaptor.getValue().getRecipientType()).isNotEqualTo(RecipientType.USER_PERSONAL);
    }

    @Test
    @DisplayName("Should grant USER_ORGANIZATION_MEMBER permission on targetDocumentHash during invitation acceptance")
    void acceptInvitation_WithTargetDocument_GrantsUserOrganizationMemberPermission() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(11L)
                .token("doc-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .targetDocumentHash("sha256-hash-abc")
                .onboardingPermissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        when(invitationRepository.findByToken("doc-token")).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("doc-token", inviteeUser);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);

        ArgumentCaptor<PermissionRequestDto> permCaptor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        verify(permissionService).grantPermission(permCaptor.capture(), eq(adminUser.getId()));
        assertThat(permCaptor.getValue().getDocumentFileHash()).isEqualTo("sha256-hash-abc");
        assertThat(permCaptor.getValue().getTargetUserEmail()).isEqualTo("invitee@acme.com");
        assertThat(permCaptor.getValue().getTargetOrgCode()).isEqualTo("ACME");
        assertThat(permCaptor.getValue().getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        assertThat(permCaptor.getValue().getRecipientType()).isEqualTo(RecipientType.USER_ORGANIZATION_MEMBER);
        assertThat(permCaptor.getValue().getRecipientType()).isNotEqualTo(RecipientType.USER_PERSONAL);
    }

    @Test
    @DisplayName("createInvitation: ORG_ADMIN cannot create invitation with ROLE_SUPER_ADMIN")
    void createInvitation_OrgAdminCannotInviteSuperAdmin() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("supercandidate@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvitation: ORG_MANAGER cannot create invitation with ROLE_SUPER_ADMIN")
    void createInvitation_OrgManagerCannotInviteSuperAdmin() {
        UserEntity managerUser = UserEntity.builder().id("manager-1").email("mgr@acme.com").role(Role.ROLE_USER).build();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("supercandidate@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        doThrow(new AccessDeniedException("Organization Managers cannot invite users with administrative roles."))
                .when(capabilityEvaluatorService)
                .validateInvitationTargetRole(managerUser, "ACME", Role.ROLE_SUPER_ADMIN.name());

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot invite users with administrative roles");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvitation: Standard USER cannot create invitation with ROLE_SUPER_ADMIN")
    void createInvitation_UserCannotInviteSuperAdmin() {
        UserEntity regularUser = UserEntity.builder().id("regular-1").email("user@acme.com").role(Role.ROLE_USER).build();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("supercandidate@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        doThrow(new AccessDeniedException("Missing required organization capability: ORG_MEMBERS_INVITE"))
                .when(capabilityEvaluatorService)
                .validateInvitationTargetRole(regularUser, "ACME", Role.ROLE_SUPER_ADMIN.name());

        assertThatThrownBy(() -> invitationService.createInvitation("ACME", request, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_MEMBERS_INVITE");
        verify(invitationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvitation: ORG_ADMIN can create invitation with valid ROLE_ORG_ADMIN")
    void createInvitation_ValidOrgAdminInvitation_Success() {
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail("admin2@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        when(invitationRepository.existsByOrganization_OrgCodeAndInviteeEmailAndStatus("ACME", "admin2@acme.com", OrganizationInvitationStatus.PENDING)).thenReturn(false);
        when(userRepository.findByEmail("admin2@acme.com")).thenReturn(Optional.empty());
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> {
            OrganizationInvitationEntity entity = i.getArgument(0);
            entity.setId(102L);
            return entity;
        });

        OrganizationInvitationResponseDto response = invitationService.createInvitation("ACME", request, adminUser);

        assertThat(response.getId()).isEqualTo(102L);
        assertThat(response.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.PENDING);
        verify(capabilityEvaluatorService).validateInvitationTargetRole(adminUser, "ACME", Role.ROLE_ORG_ADMIN.name());
        verify(invitationRepository).save(any(OrganizationInvitationEntity.class));
    }

    @Test
    @DisplayName("acceptInvitation: Accepting invitation with ROLE_ORG_ADMIN correctly creates org admin membership")
    void acceptInvitation_WithOrgAdminRole_CreatesOrgAdminMembership() {
        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(12L)
                .token("admin-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("admin-token")).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("admin-token", inviteeUser);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);

        ArgumentCaptor<UserOrganizationEntity> membershipCaptor = ArgumentCaptor.forClass(UserOrganizationEntity.class);
        verify(userOrganizationRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(membershipCaptor.getValue().getUser()).isEqualTo(inviteeUser);
        assertThat(membershipCaptor.getValue().getOrganization()).isEqualTo(testOrg);
    }

    @Test
    @DisplayName("acceptInvitation: Accepting invitation with ROLE_SUPER_ADMIN is rejected and never writes to UserOrganizationEntity")
    void acceptInvitation_CannotWriteSuperAdminToUserOrganizationEntity() {
        OrganizationInvitationEntity corruptInvitation = OrganizationInvitationEntity.builder()
                .id(13L)
                .token("corrupt-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("corrupt-token")).thenReturn(Optional.of(corruptInvitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation("corrupt-token", inviteeUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");

        verify(userOrganizationRepository, never()).save(any(UserOrganizationEntity.class));
    }

    @Test
    @DisplayName("acceptInvitation: Global Super Admin invitee retains global role when accepting organization invitation")
    void acceptInvitation_GlobalSuperAdmin_GlobalRoleUnaffected() {
        UserEntity saInvitee = UserEntity.builder()
                .id("sa-invitee-1")
                .email("sa-invitee@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(14L)
                .token("sa-token")
                .organization(testOrg)
                .inviter(adminUser)
                .inviteeEmail("sa-invitee@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("sa-token")).thenReturn(Optional.of(invitation));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("sa-token", saInvitee);

        assertThat(response.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);

        ArgumentCaptor<UserOrganizationEntity> membershipCaptor = ArgumentCaptor.forClass(UserOrganizationEntity.class);
        verify(userOrganizationRepository).save(membershipCaptor.capture());
        assertThat(membershipCaptor.getValue().getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);

        // Global UserEntity.role remains unaffected
        assertThat(saInvitee.getRole()).isEqualTo(Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("Should reactivate REMOVED membership to ACTIVE on acceptance")
    void acceptInvitation_ReactivatesRemovedMembership() {
        UserOrganizationEntity removedMembership = UserOrganizationEntity.builder()
                .id(999L)
                .user(inviteeUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.REMOVED)
                .build();

        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(1L)
                .token("reactivate-token")
                .organization(testOrg)
                .inviter(inviterUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_ORG_MANAGER)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("reactivate-token")).thenReturn(Optional.of(invitation));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(inviteeUser.getId(), "ACME"))
                .thenReturn(Optional.of(removedMembership));
        when(invitationRepository.save(any(OrganizationInvitationEntity.class))).thenAnswer(i -> i.getArgument(0));

        OrganizationInvitationResponseDto response = invitationService.acceptInvitation("reactivate-token", inviteeUser);

        assertThat(response).isNotNull();
        assertThat(removedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(removedMembership.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
        verify(userOrganizationRepository).save(removedMembership);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when accepting invitation while membership is SUSPENDED")
    void acceptInvitation_ThrowsWhenSuspended() {
        UserOrganizationEntity suspendedMembership = UserOrganizationEntity.builder()
                .id(999L)
                .user(inviteeUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.SUSPENDED)
                .build();

        OrganizationInvitationEntity invitation = OrganizationInvitationEntity.builder()
                .id(1L)
                .token("suspended-token")
                .organization(testOrg)
                .inviter(inviterUser)
                .inviteeEmail("invitee@acme.com")
                .role(Role.ROLE_USER)
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findByToken("suspended-token")).thenReturn(Optional.of(invitation));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(inviteeUser.getId(), "ACME"))
                .thenReturn(Optional.of(suspendedMembership));

        assertThatThrownBy(() -> invitationService.acceptInvitation("suspended-token", inviteeUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot accept invitation while membership is suspended");
    }

    @Test
    @DisplayName("Should lazily expire invitation and reject revocation when expired")
    void revokeInvitation_ThrowsWhenAlreadyExpired() {
        OrganizationInvitationEntity expiredInvitation = OrganizationInvitationEntity.builder()
                .id(10L)
                .organization(testOrg)
                .inviter(inviterUser)
                .inviteeEmail("invitee@acme.com")
                .status(OrganizationInvitationStatus.PENDING)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();

        when(invitationRepository.findById(10L)).thenReturn(Optional.of(expiredInvitation));

        assertThatThrownBy(() -> invitationService.revokeInvitation("ACME", 10L, inviterUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invitation has expired and cannot be revoked");

        assertThat(expiredInvitation.getStatus()).isEqualTo(OrganizationInvitationStatus.EXPIRED);
        verify(invitationRepository).save(expiredInvitation);
    }
}
