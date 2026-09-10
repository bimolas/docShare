package com.subject77.docshare.modules.notification;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.dto.NotificationResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationStatus;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationInvitationEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationInvitationStatus;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationInvitationRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.organization.services.GroupService;
import com.subject77.docshare.modules.organization.services.OrganizationInvitationService;
import com.subject77.docshare.modules.permission.dto.AccessRequestActionDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.services.AccessRequestService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.subject77.docshare.modules.permission.events.PermissionChangeEvent;

@SpringBootTest
public class NotificationRoutingAndFanOutIntegrationTest {

    @MockitoBean
    private KafkaTemplate<String, PermissionChangeEvent> kafkaTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private GroupService groupService;

    @Autowired
    private OrganizationInvitationService invitationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Autowired
    private OrganizationInvitationRepository invitationRepository;

    @Autowired
    private WorkspacePolicyRepository workspacePolicyRepository;

    private OrganizationEntity org;
    private UserEntity orgAdmin;
    private UserEntity teamManager;
    private UserEntity teamMember;
    private UserEntity regularUser;
    private GroupEntity team;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        orgAdmin = createUser("admin_" + suffix, Role.ROLE_ORG_ADMIN);
        teamManager = createUser("tmgr_" + suffix, Role.ROLE_USER);
        teamMember = createUser("tmem_" + suffix, Role.ROLE_USER);
        regularUser = createUser("reg_" + suffix, Role.ROLE_USER);

        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_" + suffix)
                .name("Routing Org " + suffix)
                .createdBy(orgAdmin)
                .build());

        joinOrg(orgAdmin, org.getOrgCode(), Role.ROLE_ORG_ADMIN);
        joinOrg(teamManager, org.getOrgCode(), Role.ROLE_USER);
        joinOrg(teamMember, org.getOrgCode(), Role.ROLE_USER);
        joinOrg(regularUser, org.getOrgCode(), Role.ROLE_USER);

        team = groupRepository.save(GroupEntity.builder()
                .name("Engineering_" + suffix)
                .organization(org)
                .createdBy(orgAdmin)
                .build());

        workspacePolicyRepository.save(WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(org)
                .externalSharingMode(ExternalSharingMode.OFF)
                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                .allowAccessRequests(true)
                .allowExternalAccessRequests(true)
                .requireRequestReason(false)
                .build());

        userGroupRepository.save(UserGroupEntity.builder()
                .group(team)
                .user(teamManager)
                .role(GroupMemberRole.MANAGER)
                .build());

        userGroupRepository.save(UserGroupEntity.builder()
                .group(team)
                .user(teamMember)
                .role(GroupMemberRole.MEMBER)
                .build());
    }

    private UserEntity createUser(String prefix, Role role) {
        String id = UUID.randomUUID().toString();
        return userRepository.save(UserEntity.builder()
                .id(id)
                .email(prefix + "_" + id.substring(0, 6) + "@example.com")
                .firstName("First")
                .lastName("Last")
                .role(role)
                .build());
    }

    private void joinOrg(UserEntity user, String orgCode, Role role) {
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(user)
                .organization(org)
                .role(role)
                .status(MembershipStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("6B.5: Team permission grant routes to Team Manager only (no fan-out to members)")
    void testTeamPermissionGrantRoutesToTeamManagerOnly() {
        FolderEntity folder = folderRepository.save(FolderEntity.builder()
                .name("Team Folder")
                .ownerOrg(org)
                .createdBy(orgAdmin)
                .build());

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .folderId(folder.getId())
                .targetGroupId(team.getId())
                .build();

        permissionService.grantPermission(req, orgAdmin.getId());

        // Team Manager MUST have received notification
        Page<NotificationResponseDto> mgrNotis = notificationService.getUserNotifications(
                teamManager, null, null, PageRequest.of(0, 20)
        );
        assertEquals(1, mgrNotis.getTotalElements());
        assertEquals(NotificationType.PERMISSION_GRANTED, mgrNotis.getContent().get(0).getType());
        assertTrue(mgrNotis.getContent().get(0).getTitle().contains("Team"));

        // Regular Team Member MUST NOT have received a fan-out notification
        Page<NotificationResponseDto> memNotis = notificationService.getUserNotifications(
                teamMember, null, null, PageRequest.of(0, 20)
        );
        assertEquals(0, memNotis.getTotalElements());
    }

    @Test
    @DisplayName("6B.5: Organization permission grant routes to active Org Admins only")
    void testOrgPermissionGrantRoutesToOrgAdminsOnly() {
        FolderEntity folder = folderRepository.save(FolderEntity.builder()
                .name("Company Policy Folder")
                .ownerOrg(org)
                .createdBy(teamManager)
                .build());

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.ORGANIZATION)
                .folderId(folder.getId())
                .targetOrgCode(org.getOrgCode())
                .build();

        permissionService.grantPermission(req, teamManager.getId());

        // Org Admin MUST have received notification
        Page<NotificationResponseDto> adminNotis = notificationService.getUserNotifications(
                orgAdmin, null, null, PageRequest.of(0, 20)
        );
        assertEquals(1, adminNotis.getTotalElements());
        assertEquals(NotificationType.PERMISSION_GRANTED, adminNotis.getContent().get(0).getType());

        // Regular org member MUST NOT receive it
        Page<NotificationResponseDto> regularNotis = notificationService.getUserNotifications(
                regularUser, null, null, PageRequest.of(0, 20)
        );
        assertEquals(0, regularNotis.getTotalElements());
    }

    @Test
    @DisplayName("6B.5: Access Request routes to Workspace Manager, falling back to Org Admin if unassigned")
    void testAccessRequestRouting() {
        FolderEntity rootFolder = folderRepository.save(FolderEntity.builder()
                .name("Workspace Root")
                .ownerOrg(org)
                .createdBy(orgAdmin)
                .build());

        ProjectWorkspaceEntity ws = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Project Alpha")
                .organization(org)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(orgAdmin)
                .build());

        DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                .fileName("spec.pdf")
                .fileHash("hash-routing-" + UUID.randomUUID())
                .objectKey("spec-" + UUID.randomUUID() + ".pdf")
                .s3Bucket("docshare-bucket")
                .fileSize(100L)
                .contentType("application/pdf")
                .folder(rootFolder)
                .ownerOrg(org)
                .uploadedBy(regularUser)
                .status(DocumentStatus.ACTIVE)
                .build());

        UserEntity wsManager = createUser("wsmgr_" + UUID.randomUUID().toString().substring(0, 6), Role.ROLE_USER);
        joinOrg(wsManager, org.getOrgCode(), Role.ROLE_USER);

        workspaceManagerRepository.save(WorkspaceManagerEntity.builder()
                .workspace(ws)
                .user(wsManager)
                .build());

        // Request access by teamMember
        AccessRequestCreateDto reqDto = AccessRequestCreateDto.builder()
                .documentFileHash(doc.getFileHash())
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("Need for review")
                .build();

        AccessRequestResponseDto submitted = accessRequestService.createAccessRequest(reqDto, teamMember);
        assertNotNull(submitted);

        // Workspace Manager MUST receive notification
        Page<NotificationResponseDto> wsMgrNotis = notificationService.getUserNotifications(
                wsManager, null, null, PageRequest.of(0, 20)
        );
        assertEquals(1, wsMgrNotis.getTotalElements());
        assertEquals(NotificationType.ACCESS_REQUEST_SUBMITTED, wsMgrNotis.getContent().get(0).getType());

        // Requester MUST NOT receive notification
        Page<NotificationResponseDto> requesterNotis = notificationService.getUserNotifications(
                teamMember, null, null, PageRequest.of(0, 20)
        );
        assertEquals(0, requesterNotis.getTotalElements());

        // When approved, requester receives ACCESS_REQUEST_APPROVED
        AccessRequestActionDto actionDto = AccessRequestActionDto.builder()
                .reviewerNote("Approved by manager")
                .build();
        accessRequestService.approveAccessRequest(submitted.getId(), actionDto, wsManager);

        Page<NotificationResponseDto> requesterAfterApprove = notificationService.getUserNotifications(
                teamMember, null, null, PageRequest.of(0, 20)
        );
        assertTrue(requesterAfterApprove.getContent().stream()
                .anyMatch(n -> n.getType() == NotificationType.ACCESS_REQUEST_APPROVED));
        assertTrue(requesterAfterApprove.getContent().stream()
                .anyMatch(n -> n.getType() == NotificationType.PERMISSION_GRANTED));
    }

    @Test
    @DisplayName("6B.5: Onboarding completed notification is delivered to invitee without self-drop")
    void testOnboardingCompletedDeliveredToInvitee() {
        UserEntity invitee = createUser("new_invitee", Role.ROLE_USER);

        OrganizationInvitationEntity inv = invitationRepository.save(OrganizationInvitationEntity.builder()
                .organization(org)
                .inviter(orgAdmin)
                .inviteeEmail(invitee.getEmail())
                .token("inv-token-" + UUID.randomUUID())
                .status(OrganizationInvitationStatus.PENDING)
                .role(Role.ROLE_USER)
                .expiresAt(Instant.now().plusSeconds(86400))
                .build());

        invitationService.acceptInvitation(inv.getToken(), invitee);

        // Invitee MUST have received ONBOARDING_COMPLETED notification
        Page<NotificationResponseDto> inviteeNotis = notificationService.getUserNotifications(
                invitee, null, null, PageRequest.of(0, 20)
        );
        assertEquals(1, inviteeNotis.getTotalElements());
        assertEquals(NotificationType.ONBOARDING_COMPLETED, inviteeNotis.getContent().get(0).getType());

        // Inviter (orgAdmin) MUST have received ORG_INVITATION_ACCEPTED notification
        Page<NotificationResponseDto> adminNotis = notificationService.getUserNotifications(
                orgAdmin, null, null, PageRequest.of(0, 20)
        );
        assertTrue(adminNotis.getContent().stream()
                .anyMatch(n -> n.getType() == NotificationType.ORG_INVITATION_ACCEPTED));
    }

    @Test
    @DisplayName("6B.5: Removing a team member produces a TEAM_REMOVED notification to the removed member")
    void testTeamMemberRemovedRoutesNotificationToTargetUser() {
        groupService.removeMemberFromGroup(org.getOrgCode(), team.getId(), teamMember.getId(), teamManager);

        Page<NotificationResponseDto> notis = notificationService.getUserNotifications(
                teamMember, null, null, NotificationCategory.TEAM, PageRequest.of(0, 20)
        );
        assertEquals(1, notis.getTotalElements());
        NotificationResponseDto noti = notis.getContent().get(0);
        assertEquals(NotificationType.TEAM_REMOVED, noti.getType());
        assertEquals(NotificationCategory.TEAM, noti.getCategory());
        assertTrue(noti.getTitle().contains("Removed"));
        assertTrue(noti.getBody().contains(team.getName()));
    }

    @Test
    @DisplayName("6B.5: Category filtering returns only notifications for the requested category")
    void testCategoryFiltering() {
        // Create TEAM notification
        notificationService.createNotification(
                regularUser, orgAdmin, NotificationType.TEAM_ADDED,
                "Team Added", "Added to team", NotificationSourceType.TEAM, "team-1",
                NotificationCategory.TEAM, NotificationPriority.MEDIUM, "test-cat-team-" + UUID.randomUUID()
        );

        // Create SHARING notification
        notificationService.createNotification(
                regularUser, orgAdmin, NotificationType.PERMISSION_GRANTED,
                "Permission Granted", "Access granted", NotificationSourceType.PERMISSION, "perm-1",
                NotificationCategory.SHARING, NotificationPriority.MEDIUM, "test-cat-sharing-" + UUID.randomUUID()
        );

        // Filter by TEAM
        Page<NotificationResponseDto> teamNotis = notificationService.getUserNotifications(
                regularUser, null, null, NotificationCategory.TEAM, PageRequest.of(0, 20)
        );
        assertEquals(1, teamNotis.getTotalElements());
        assertEquals(NotificationCategory.TEAM, teamNotis.getContent().get(0).getCategory());

        // Filter by SHARING
        Page<NotificationResponseDto> sharingNotis = notificationService.getUserNotifications(
                regularUser, null, null, NotificationCategory.SHARING, PageRequest.of(0, 20)
        );
        assertEquals(1, sharingNotis.getTotalElements());
        assertEquals(NotificationCategory.SHARING, sharingNotis.getContent().get(0).getCategory());

        // All categories
        Page<NotificationResponseDto> allNotis = notificationService.getUserNotifications(
                regularUser, null, null, null, PageRequest.of(0, 20)
        );
        assertTrue(allNotis.getTotalElements() >= 2);
    }
}
