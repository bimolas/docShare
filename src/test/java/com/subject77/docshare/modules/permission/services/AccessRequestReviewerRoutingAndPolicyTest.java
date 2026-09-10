package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.mapper.AccessRequestMapper;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyServiceImpl;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessRequestReviewerRoutingAndPolicyTest {

    @Mock
    private AccessRequestRepository accessRequestRepository;
    @Mock
    private PermissionService permissionService;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private PermissionEvaluatorService evaluatorService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private UserOrganizationRepository userOrganizationRepository;
    @Mock
    private WorkspacePolicyService workspacePolicyService;
    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;
    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;
    @Mock
    private WorkspacePolicyRepository workspacePolicyRepository;
    @Mock
    private OrganizationRepository organizationRepository;

    @Spy
    private AccessRequestMapper accessRequestMapper = new AccessRequestMapper();

    @InjectMocks
    private AccessRequestServiceImpl accessRequestService;

    private WorkspacePolicyServiceImpl directWorkspacePolicyService;

    private UserEntity requester;
    private UserEntity personalOwner;
    private UserEntity orgAdmin;
    private UserEntity workspaceManager;
    private UserEntity originalSharer;
    private UserEntity teamManager;
    private OrganizationEntity acmeOrg;

    @BeforeEach
    void setUp() {
        directWorkspacePolicyService = new WorkspacePolicyServiceImpl(
                workspacePolicyRepository,
                organizationRepository,
                userRepository,
                null,
                null
        );

        acmeOrg = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();

        requester = UserEntity.builder().id("user-req").email("bob@example.com").build();
        personalOwner = UserEntity.builder().id("user-owner").email("alice@example.com").organization(acmeOrg).build();
        orgAdmin = UserEntity.builder().id("user-admin").email("admin@acme.com").role(Role.ROLE_ORG_ADMIN).organization(acmeOrg).build();
        workspaceManager = UserEntity.builder().id("user-wm").email("wm@acme.com").organization(acmeOrg).build();
        originalSharer = UserEntity.builder().id("user-sharer").email("sharer@acme.com").organization(acmeOrg).build();
        teamManager = UserEntity.builder().id("user-tm").email("tm@acme.com").organization(acmeOrg).build();
    }

    // =========================================================================
    // TEST 1: Personal resource uses only personal policy (ignores org policy)
    // =========================================================================
    @Test
    @DisplayName("1. Personal resource uses ONLY personal policy, even when user belongs to an org")
    void test1_PersonalResource_UsesOnlyPersonalPolicy() {
        // Owner belongs to ACME org.
        // ACME org has allowAccessRequests = false.
        // Owner's personal policy has allowAccessRequests = true.
        WorkspacePolicyEntity personalPolicy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(personalOwner)
                .allowAccessRequests(true)
                .requireRequestReason(true)
                .build();

        when(workspacePolicyRepository.findByUser(personalOwner)).thenReturn(Optional.of(personalPolicy));

        WorkspacePolicyEntity effective = directWorkspacePolicyService.getEffectivePolicyForUser(personalOwner);

        assertThat(effective.getAllowAccessRequests()).isTrue();
        assertThat(effective.getRequireRequestReason()).isTrue();
        // Verifies no interaction with org policy repository for personal policy resolution
        verify(workspacePolicyRepository, never()).findByOrganization_OrgCode(any());
    }

    // =========================================================================
    // TEST 2: Organization resource uses organization policy
    // =========================================================================
    @Test
    @DisplayName("2. Organization resource uses organization policy")
    void test2_OrganizationResource_UsesOrgPolicy() {
        FolderEntity orgFolder = FolderEntity.builder().id(10L).name("Org Folder").ownerOrg(acmeOrg).createdBy(personalOwner).build();

        WorkspacePolicyEntity orgPolicy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(acmeOrg)
                .allowAccessRequests(true)
                .build();

        when(workspacePolicyRepository.findByOrganization_OrgCode("ACME")).thenReturn(Optional.of(orgPolicy));

        boolean enabled = directWorkspacePolicyService.isAccessRequestEnabledForFolder(orgFolder);
        assertThat(enabled).isTrue();
        // Verifies owner's personal policy was NOT consulted for the org folder
        verify(workspacePolicyRepository, never()).findByUser(any());
    }

    // =========================================================================
    // TEST 3: Personal policy OFF/ON behavior
    // =========================================================================
    @Test
    @DisplayName("3. Personal policy OFF/ON behavior correctly toggles folder eligibility")
    void test3_PersonalPolicy_OffOnBehavior() {
        FolderEntity personalFolder = FolderEntity.builder().id(11L).name("My Folder").createdBy(personalOwner).build();

        // OFF
        WorkspacePolicyEntity policyOff = WorkspacePolicyEntity.builder().user(personalOwner).allowAccessRequests(false).build();
        when(workspacePolicyRepository.findByUser(personalOwner)).thenReturn(Optional.of(policyOff));
        assertThat(directWorkspacePolicyService.isAccessRequestEnabledForFolder(personalFolder)).isFalse();

        // ON
        WorkspacePolicyEntity policyOn = WorkspacePolicyEntity.builder().user(personalOwner).allowAccessRequests(true).build();
        when(workspacePolicyRepository.findByUser(personalOwner)).thenReturn(Optional.of(policyOn));
        assertThat(directWorkspacePolicyService.isAccessRequestEnabledForFolder(personalFolder)).isTrue();
    }

    // =========================================================================
    // TEST 4: Direct personal resource reviewer = owner
    // =========================================================================
    @Test
    @DisplayName("4. Direct personal resource reviewer is strictly the resource owner")
    void test4_DirectPersonalResource_ReviewerIsOwner() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).uploadedBy(personalOwner).build();
        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(1L)
                .requester(requester)
                .document(doc)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(request));
        when(evaluatorService.canAdministerDocumentPermissions(personalOwner, doc)).thenReturn(true);
        when(evaluatorService.canAdministerDocumentPermissions(orgAdmin, doc)).thenReturn(false);

        // Owner can approve
        PermissionResponseDto granted = PermissionResponseDto.builder().id(10L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(), eq(personalOwner.getId()))).thenReturn(granted);
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(PermissionEntity.builder().id(10L).build()));
        when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto resp = accessRequestService.approveAccessRequest(1L, null, personalOwner);
        assertThat(resp.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);

        // Non-owner cannot approve
        request.setStatus(AccessRequestStatus.PENDING);
        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(1L, null, orgAdmin))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // TEST 5: Direct org share reviewer = authorized original sharer
    // =========================================================================
    @Test
    @DisplayName("5. Direct org share: Original sharer with current authority is authorized to review")
    void test5_DirectOrgShare_AuthorizedOriginalSharerCanReview() {
        FolderEntity orgFolder = FolderEntity.builder().id(20L).name("Sprint").ownerOrg(acmeOrg).build();
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("secret.pdf").folder(orgFolder).ownerOrg(acmeOrg).build();

        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(2L)
                .requester(requester)
                .document(doc)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(request));

        // Original exclusion was created by originalSharer
        PermissionEntity exclusion = PermissionEntity.builder().id(50L).grantedBy(originalSharer).permissionType(PermissionType.EXCLUDE).build();
        when(evaluatorService.findExplicitExclusion(requester, doc)).thenReturn(Optional.of(exclusion));

        // Original sharer STILL has authority
        when(evaluatorService.canAdministerDocumentPermissions(originalSharer, doc)).thenReturn(true);

        PermissionResponseDto granted = PermissionResponseDto.builder().id(25L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(), eq(originalSharer.getId()))).thenReturn(granted);
        when(permissionRepository.findById(25L)).thenReturn(Optional.of(PermissionEntity.builder().id(25L).build()));
        when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto resp = accessRequestService.approveAccessRequest(2L, null, originalSharer);
        assertThat(resp.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    }

    // =========================================================================
    // TEST 6: Original sharer loses authority -> fallback reviewer
    // =========================================================================
    @Test
    @DisplayName("6. Original sharer who lost authority cannot approve; Org Admin can approve as fallback")
    void test6_OriginalSharerLosesAuthority_FallbackReviewer() {
        FolderEntity orgFolder = FolderEntity.builder().id(20L).name("Sprint").ownerOrg(acmeOrg).build();
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("secret.pdf").folder(orgFolder).ownerOrg(acmeOrg).build();

        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(3L)
                .requester(requester)
                .document(doc)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(request));

        PermissionEntity exclusion = PermissionEntity.builder().id(50L).grantedBy(originalSharer).permissionType(PermissionType.EXCLUDE).build();
        when(evaluatorService.findExplicitExclusion(requester, doc)).thenReturn(Optional.of(exclusion));

        // Sharer LOST authority
        when(evaluatorService.canAdministerDocumentPermissions(originalSharer, doc)).thenReturn(false);
        when(evaluatorService.isUserAdminOfOrg(originalSharer, "ACME")).thenReturn(false);

        // Sharer approval rejected
        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(3L, null, originalSharer))
                .isInstanceOf(AccessDeniedException.class);

        // Org Admin fallback succeeds
        when(evaluatorService.isUserAdminOfOrg(orgAdmin, "ACME")).thenReturn(true);
        PermissionResponseDto granted = PermissionResponseDto.builder().id(26L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(), eq(orgAdmin.getId()))).thenReturn(granted);
        when(permissionRepository.findById(26L)).thenReturn(Optional.of(PermissionEntity.builder().id(26L).build()));
        when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto resp = accessRequestService.approveAccessRequest(3L, null, orgAdmin);
        assertThat(resp.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    }

    // =========================================================================
    // TEST 7: Team share -> Workspace Manager eligible where authority exists
    // =========================================================================
    @Test
    @DisplayName("7. Team share: Active Workspace Manager for the containing workspace is eligible")
    void test7_TeamShare_WorkspaceManagerEligible() {
        FolderEntity wsFolder = FolderEntity.builder().id(30L).name("Eng WS").ownerOrg(acmeOrg).build();
        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(4L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(request));
        when(evaluatorService.isUserAdminOfOrg(workspaceManager, "ACME")).thenReturn(false);
        when(evaluatorService.isWorkspaceManagerForFolder(workspaceManager, wsFolder)).thenReturn(true);

        PermissionResponseDto granted = PermissionResponseDto.builder().id(27L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(), eq(workspaceManager.getId()))).thenReturn(granted);
        when(permissionRepository.findById(27L)).thenReturn(Optional.of(PermissionEntity.builder().id(27L).build()));
        when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto resp = accessRequestService.approveAccessRequest(4L, null, workspaceManager);
        assertThat(resp.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    }

    // =========================================================================
    // TEST 8: Team Manager alone cannot approve without content-admin authority
    // =========================================================================
    @Test
    @DisplayName("8. Team Manager alone CANNOT approve without actual content-admin authority")
    void test8_TeamManagerAlone_CannotApprove() {
        FolderEntity wsFolder = FolderEntity.builder().id(30L).name("Eng WS").ownerOrg(acmeOrg).build();
        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(5L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(request));
        // Team manager is not org admin, not workspace manager, not sharer
        when(evaluatorService.isUserAdminOfOrg(teamManager, "ACME")).thenReturn(false);
        when(evaluatorService.isWorkspaceManagerForFolder(teamManager, wsFolder)).thenReturn(false);
        when(evaluatorService.findExplicitExclusion(requester, wsFolder)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(5L, null, teamManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to review access requests for this resource");
    }

    // =========================================================================
    // TEST 9: Org Admin remains eligible where appropriate
    // =========================================================================
    @Test
    @DisplayName("9. Organization Admin remains eligible authority for organizational resources")
    void test9_OrgAdmin_RemainsEligible() {
        FolderEntity wsFolder = FolderEntity.builder().id(30L).name("Eng WS").ownerOrg(acmeOrg).build();
        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(6L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(request));
        when(evaluatorService.isUserAdminOfOrg(orgAdmin, "ACME")).thenReturn(true);

        PermissionResponseDto granted = PermissionResponseDto.builder().id(28L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(), eq(orgAdmin.getId()))).thenReturn(granted);
        when(permissionRepository.findById(28L)).thenReturn(Optional.of(PermissionEntity.builder().id(28L).build()));
        when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto resp = accessRequestService.approveAccessRequest(6L, null, orgAdmin);
        assertThat(resp.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
    }

    // =========================================================================
    // TEST 10: Concurrent reviewers still produce exactly one decision
    // =========================================================================
    @Test
    @DisplayName("10. Multiple eligible reviewers: exactly one decision transitions terminal status")
    void test10_ConcurrentReviewers_ProduceExactlyOneDecision() {
        FolderEntity wsFolder = FolderEntity.builder().id(30L).name("Eng WS").ownerOrg(acmeOrg).build();
        AccessRequestEntity request = AccessRequestEntity.builder()
                .id(7L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(evaluatorService.isUserAdminOfOrg(orgAdmin, "ACME")).thenReturn(true);

        PermissionResponseDto granted = PermissionResponseDto.builder().id(29L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(), eq(orgAdmin.getId()))).thenReturn(granted);
        when(permissionRepository.findById(29L)).thenReturn(Optional.of(PermissionEntity.builder().id(29L).build()));
        when(accessRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // First reviewer approves successfully
        AccessRequestResponseDto resp = accessRequestService.approveAccessRequest(7L, null, orgAdmin);
        assertThat(resp.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);

        // Status is now APPROVED. Second reviewer attempt must fail with IllegalStateException
        assertThatThrownBy(() -> accessRequestService.denyAccessRequest(7L, null, orgAdmin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access request is not in PENDING status");
    }
}
