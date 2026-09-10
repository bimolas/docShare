package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProvenanceHierarchyOverrideTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionAuditLogRepository auditLogRepository;
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PermissionEventPublisher eventPublisher;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @Mock
    private WorkspaceManagerService workspaceManagerService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private OrganizationEntity orgAlpha;
    private OrganizationEntity orgBeta;
    private UserEntity superAdmin;
    private UserEntity orgAdminAlphaA;
    private UserEntity orgAdminAlphaB;
    private UserEntity orgAdminBeta;
    private UserEntity workspaceManager1;
    private UserEntity workspaceManager2;
    private UserEntity collabManager1;
    private UserEntity collabManager2;
    private UserEntity creatorA;
    private UserEntity creatorB;
    private FolderEntity orgFolderAlpha;
    private DocumentEntity orgDocAlpha;

    @BeforeEach
    void setUp() {
        orgAlpha = OrganizationEntity.builder().orgCode("ALPHA").name("Org Alpha").build();
        orgBeta = OrganizationEntity.builder().orgCode("BETA").name("Org Beta").build();

        superAdmin = UserEntity.builder().id("super-1").email("super@docshare.com").role(Role.ROLE_SUPER_ADMIN).build();
        orgAdminAlphaA = UserEntity.builder().id("admin-a").email("admin-a@alpha.com").role(Role.ROLE_ORG_ADMIN).organization(orgAlpha).build();
        orgAdminAlphaB = UserEntity.builder().id("admin-b").email("admin-b@alpha.com").role(Role.ROLE_ORG_ADMIN).organization(orgAlpha).build();
        orgAdminBeta = UserEntity.builder().id("admin-beta").email("admin@beta.com").role(Role.ROLE_ORG_ADMIN).organization(orgBeta).build();

        workspaceManager1 = UserEntity.builder().id("wm-1").email("wm-1@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        workspaceManager2 = UserEntity.builder().id("wm-2").email("wm-2@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

        collabManager1 = UserEntity.builder().id("cm-1").email("cm-1@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        collabManager2 = UserEntity.builder().id("cm-2").email("cm-2@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

        creatorA = UserEntity.builder().id("creator-a").email("creator-a@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        creatorB = UserEntity.builder().id("creator-b").email("creator-b@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

        orgFolderAlpha = FolderEntity.builder().id(100L).name("Projects").ownerOrg(orgAlpha).build();
        orgDocAlpha = DocumentEntity.builder().id(200L).fileName("Spec.pdf").fileHash("hash-spec-200").ownerOrg(orgAlpha).folder(orgFolderAlpha).uploadedBy(creatorA).build();

        lenient().when(userRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if ("super-1".equals(id)) return Optional.of(superAdmin);
            if ("admin-a".equals(id)) return Optional.of(orgAdminAlphaA);
            if ("admin-b".equals(id)) return Optional.of(orgAdminAlphaB);
            if ("admin-beta".equals(id)) return Optional.of(orgAdminBeta);
            if ("wm-1".equals(id)) return Optional.of(workspaceManager1);
            if ("wm-2".equals(id)) return Optional.of(workspaceManager2);
            if ("cm-1".equals(id)) return Optional.of(collabManager1);
            if ("cm-2".equals(id)) return Optional.of(collabManager2);
            if ("creator-a".equals(id)) return Optional.of(creatorA);
            if ("creator-b".equals(id)) return Optional.of(creatorB);
            return Optional.of(UserEntity.builder().id(id).email(id + "@alpha.com").build());
        });

        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(true);
        lenient().when(organizationRepository.existsByOrgCode(anyString())).thenReturn(true);
        lenient().when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(anyString(), anyString(), any())).thenReturn(true);
        lenient().when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolderAlpha));
        lenient().when(documentRepository.findByFileHash("hash-spec-200")).thenReturn(List.of(orgDocAlpha));
        lenient().when(documentRepository.findById(200L)).thenReturn(Optional.of(orgDocAlpha));
        lenient().when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        // Allow delegation ceiling by default in these tests so tests specifically assert override authority
        lenient().when(evaluatorService.resolveDelegationCeiling(any(), any(), any())).thenReturn(PermissionLevel.EDIT);
        lenient().when(evaluatorService.canAdministerFolderPermissions(any(), any())).thenReturn(true);
        lenient().when(evaluatorService.canAdministerDocumentPermissions(any(), any())).thenReturn(true);
    }

    // =========================================================================
    // 1. Evaluator Service Unit Tests (Testing canActorOverrideGrant & canActorOverrideInactivator directly)
    // =========================================================================

    @Nested
    @DisplayName("Evaluator Service Logic Tests")
    class EvaluatorLogicTests {

        private PermissionEvaluatorService realEvaluator;

        @BeforeEach
        void initRealEvaluator() {
            realEvaluator = new PermissionEvaluatorService(
                    permissionRepository,
                    userOrganizationRepository,
                    userGroupRepository,
                    cacheService,
                    auditLogRepository,
                    externalSharingPolicyEvaluator,
                    collaborationRepository,
                    collaborationParticipantRepository
            );
            org.springframework.test.util.ReflectionTestUtils.setField(realEvaluator, "workspaceManagerService", workspaceManagerService);

            // Mock active membership for users in ALPHA
            lenient().when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(anyString(), eq("ALPHA"), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(true);

            // Mock org admin membership for admin-a and admin-b in ALPHA
            UserOrganizationEntity adminMemberA = UserOrganizationEntity.builder().user(orgAdminAlphaA).organization(orgAlpha).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-a", "ALPHA")).thenReturn(Optional.of(adminMemberA));
            UserOrganizationEntity adminMemberB = UserOrganizationEntity.builder().user(orgAdminAlphaB).organization(orgAlpha).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-b", "ALPHA")).thenReturn(Optional.of(adminMemberB));

            // Mock org admin for admin-beta in BETA
            UserOrganizationEntity adminMemberBeta = UserOrganizationEntity.builder().user(orgAdminBeta).organization(orgBeta).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-beta", "BETA")).thenReturn(Optional.of(adminMemberBeta));
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-beta", "ALPHA")).thenReturn(Optional.empty());
            lenient().when(externalSharingPolicyEvaluator.isExternalPermissionRuntimeValid(any(), any())).thenReturn(true);
        }

        @Test
        @DisplayName("ORG_ADMIN: can modify lower-tier grant in own org, cannot modify SUPER_ADMIN grant, peer can modify peer")
        void testOrgAdminHierarchy() {
            PermissionEntity superAdminGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.SUPER_ADMIN)
                    .folder(orgFolderAlpha)
                    .grantedBy(superAdmin)
                    .build();

            PermissionEntity wmGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .folder(orgFolderAlpha)
                    .workspaceId(10L)
                    .grantedBy(workspaceManager1)
                    .build();

            PermissionEntity orgAdminAGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .folder(orgFolderAlpha)
                    .grantedBy(orgAdminAlphaA)
                    .build();

            // ORG_ADMIN can modify lower-tier grant
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminAlphaA, wmGrant, orgFolderAlpha, null)).isTrue();

            // ORG_ADMIN cannot modify SUPER_ADMIN grant
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminAlphaA, superAdminGrant, orgFolderAlpha, null)).isFalse();

            // ORG_ADMIN B can modify ORG_ADMIN A in same org
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminAlphaB, orgAdminAGrant, orgFolderAlpha, null)).isTrue();

            // ORG_ADMIN from another org cannot modify grant in ALPHA
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminBeta, wmGrant, orgFolderAlpha, null)).isFalse();
        }

        @Test
        @DisplayName("Workspace Manager: can modify lower-tier, peer in same workspace can modify, cannot modify cross-workspace, cannot modify ORG_ADMIN or SUPER_ADMIN")
        void testWorkspaceManagerHierarchy() {
            PermissionEntity superAdminGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.SUPER_ADMIN)
                    .folder(orgFolderAlpha)
                    .build();

            PermissionEntity orgAdminGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .folder(orgFolderAlpha)
                    .build();

            PermissionEntity wm1Grant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(10L)
                    .folder(orgFolderAlpha)
                    .grantedBy(workspaceManager1)
                    .build();

            PermissionEntity creatorGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                    .folder(orgFolderAlpha)
                    .grantedBy(creatorA)
                    .build();

            PermissionEntity collabGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.COLLABORATION_MANAGER)
                    .folder(orgFolderAlpha)
                    .collaboration(CollaborationEntity.builder().id(77L).build())
                    .grantedBy(collabManager1)
                    .build();

            // wm-2 is workspace manager for workspace 10 and folder
            when(workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(workspaceManager2, orgFolderAlpha, 10L)).thenReturn(10L);
            when(workspaceManagerService.isWorkspaceManagerForFolder(workspaceManager2, orgFolderAlpha)).thenReturn(true);

            // Workspace Manager can modify lower-tier creator grant
            assertThat(realEvaluator.canActorOverrideGrant(workspaceManager2, creatorGrant, orgFolderAlpha, null)).isTrue();

            // Workspace Manager cannot modify ORG_ADMIN grant
            assertThat(realEvaluator.canActorOverrideGrant(workspaceManager2, orgAdminGrant, orgFolderAlpha, null)).isFalse();

            // Workspace Manager cannot modify SUPER_ADMIN grant
            assertThat(realEvaluator.canActorOverrideGrant(workspaceManager2, superAdminGrant, orgFolderAlpha, null)).isFalse();

            // Workspace Manager cannot modify Collaboration Manager grant
            assertThat(realEvaluator.canActorOverrideGrant(workspaceManager2, collabGrant, orgFolderAlpha, null)).isFalse();

            // Peer in SAME workspace can modify peer
            assertThat(realEvaluator.canActorOverrideGrant(workspaceManager2, wm1Grant, orgFolderAlpha, null)).isTrue();

            // Manager in DIFFERENT workspace cannot modify
            UserEntity otherWm = UserEntity.builder().id("wm-other").role(Role.ROLE_USER).build();
            when(workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(otherWm, orgFolderAlpha, 10L)).thenReturn(null);
            assertThat(realEvaluator.canActorOverrideGrant(otherWm, wm1Grant, orgFolderAlpha, null)).isFalse();
        }

        @Test
        @DisplayName("Collaboration Manager: same-collaboration peer override allowed, different collaboration denied, cannot override WM or ORG_ADMIN")
        void testCollaborationManagerHierarchy() {
            CollaborationEntity collab77 = CollaborationEntity.builder().id(77L).status(CollaborationStatus.ACTIVE).rootFolder(orgFolderAlpha).build();
            when(collaborationRepository.findById(77L)).thenReturn(Optional.of(collab77));

            PermissionEntity collabGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.COLLABORATION_MANAGER)
                    .collaboration(collab77)
                    .folder(orgFolderAlpha)
                    .grantedBy(collabManager1)
                    .build();

            PermissionEntity wmGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(10L)
                    .folder(orgFolderAlpha)
                    .build();

            PermissionEntity orgAdminGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .folder(orgFolderAlpha)
                    .build();

            // cm-2 is manager in collab 77
            when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(77L, "cm-2"))
                    .thenReturn(Optional.of(CollaborationParticipantEntity.builder().collaborationRole(CollaborationRole.MANAGER).build()));

            // Same-collaboration peer override allowed
            assertThat(realEvaluator.canActorOverrideGrant(collabManager2, collabGrant, orgFolderAlpha, null)).isTrue();

            // User not manager in collab 77 denied
            UserEntity nonManager = UserEntity.builder().id("non-mgr").role(Role.ROLE_USER).build();
            when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(77L, "non-mgr")).thenReturn(Optional.empty());
            assertThat(realEvaluator.canActorOverrideGrant(nonManager, collabGrant, orgFolderAlpha, null)).isFalse();

            // Collaboration Manager cannot override Workspace Manager
            assertThat(realEvaluator.canActorOverrideGrant(collabManager2, wmGrant, orgFolderAlpha, null)).isFalse();

            // Collaboration Manager cannot override ORG_ADMIN
            assertThat(realEvaluator.canActorOverrideGrant(collabManager2, orgAdminGrant, orgFolderAlpha, null)).isFalse();
        }

        @Test
        @DisplayName("Org Resource Creator: can modify own grant, cannot modify another creator, cannot modify higher tiers")
        void testOrgResourceCreatorHierarchy() {
            PermissionEntity creatorAGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                    .document(orgDocAlpha)
                    .grantedBy(creatorA)
                    .build();

            PermissionEntity creatorBGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                    .document(orgDocAlpha)
                    .grantedBy(creatorB)
                    .build();

            PermissionEntity wmGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .document(orgDocAlpha)
                    .workspaceId(10L)
                    .build();

            PermissionEntity orgAdminGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .document(orgDocAlpha)
                    .build();

            // Mock creatorA has effective EDIT on orgDocAlpha
            when(userOrganizationRepository.findByUser_IdAndStatus("creator-a", MembershipStatus.ACTIVE))
                    .thenReturn(List.of(UserOrganizationEntity.builder().user(creatorA).organization(orgAlpha).status(MembershipStatus.ACTIVE).build()));
            PermissionEntity editPerm = PermissionEntity.builder().document(orgDocAlpha).permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.EDIT).recipientType(RecipientType.USER_ORGANIZATION_MEMBER).targetUserEmail("creator-a@alpha.com").targetOrgCode("ALPHA").build();
            when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("creator-a@alpha.com", "ALPHA")).thenReturn(List.of(editPerm));

            // Creator A can modify own grant
            assertThat(realEvaluator.canActorOverrideGrant(creatorA, creatorAGrant, null, orgDocAlpha)).isTrue();

            // Creator A cannot modify Creator B's grant
            assertThat(realEvaluator.canActorOverrideGrant(creatorA, creatorBGrant, null, orgDocAlpha)).isFalse();

            // Creator A cannot modify Workspace Manager grant
            assertThat(realEvaluator.canActorOverrideGrant(creatorA, wmGrant, null, orgDocAlpha)).isFalse();

            // Creator A cannot modify ORG_ADMIN grant
            assertThat(realEvaluator.canActorOverrideGrant(creatorA, orgAdminGrant, null, orgDocAlpha)).isFalse();
        }

        @Test
        @DisplayName("Legacy Protected Permissions: grantorAuthorityTier == null cannot be modified by WM, CM, or creator; allowed for ORG_ADMIN and SUPER_ADMIN")
        void testLegacyProtectedPermissions() {
            PermissionEntity legacyGrant = PermissionEntity.builder()
                    .grantorAuthorityTier(null) // null represents PROTECTED_LEGACY
                    .folder(orgFolderAlpha)
                    .grantedBy(UserEntity.builder().id("historical-user").build())
                    .build();

            // SUPER_ADMIN can modify legacy
            assertThat(realEvaluator.canActorOverrideGrant(superAdmin, legacyGrant, orgFolderAlpha, null)).isTrue();

            // ORG_ADMIN of owning org can modify legacy
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminAlphaA, legacyGrant, orgFolderAlpha, null)).isTrue();

            // Workspace Manager cannot modify legacy
            assertThat(realEvaluator.canActorOverrideGrant(workspaceManager1, legacyGrant, orgFolderAlpha, null)).isFalse();

            // Collaboration Manager cannot modify legacy
            assertThat(realEvaluator.canActorOverrideGrant(collabManager1, legacyGrant, orgFolderAlpha, null)).isFalse();

            // Creator cannot modify legacy
            assertThat(realEvaluator.canActorOverrideGrant(creatorA, legacyGrant, orgFolderAlpha, null)).isFalse();
        }
    }

    // =========================================================================
    // 2. Lifecycle Enforcement Tests (Revoke, Suspend, Activate)
    // =========================================================================

    @Nested
    @DisplayName("Lifecycle Operation Enforcement Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Revoke: lower-tier administrator cannot revoke higher-tier grant")
        void testRevoke_LowerTierCannotRevokeHigherTier() {
            PermissionEntity orgAdminGrant = PermissionEntity.builder()
                    .id(501L)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .folder(orgFolderAlpha)
                    .grantedBy(orgAdminAlphaA)
                    .revoked(false)
                    .build();

            when(permissionRepository.findById(501L)).thenReturn(Optional.of(orgAdminGrant));
            when(evaluatorService.canActorOverrideGrant(workspaceManager1, orgAdminGrant, orgFolderAlpha, null))
                    .thenReturn(false); // Evaluator rejects lower-tier override

            assertThatThrownBy(() -> permissionService.revokePermission(501L, workspaceManager1.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to revoke permission created with tier ORG_ADMIN");
        }

        @Test
        @DisplayName("Suspend: lower-tier administrator cannot suspend higher-tier grant")
        void testSuspend_LowerTierCannotSuspendHigherTier() {
            PermissionEntity superAdminGrant = PermissionEntity.builder()
                    .id(502L)
                    .grantorAuthorityTier(AuthorityTier.SUPER_ADMIN)
                    .folder(orgFolderAlpha)
                    .grantedBy(superAdmin)
                    .suspended(false)
                    .build();

            when(permissionRepository.findById(502L)).thenReturn(Optional.of(superAdminGrant));
            when(evaluatorService.canActorOverrideGrant(orgAdminAlphaA, superAdminGrant, orgFolderAlpha, null))
                    .thenReturn(false);

            assertThatThrownBy(() -> permissionService.suspendPermission(502L, orgAdminAlphaA.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to suspend permission created with tier SUPER_ADMIN");
        }

        @Test
        @DisplayName("Activate: Check A fails when lower-tier attempts to activate higher-tier original grant")
        void testActivate_LowerTierCannotActivateHigherTier() {
            PermissionEntity orgAdminGrant = PermissionEntity.builder()
                    .id(503L)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .folder(orgFolderAlpha)
                    .grantedBy(orgAdminAlphaA)
                    .suspended(true)
                    .revokedBy(orgAdminAlphaA)
                    .build();

            when(permissionRepository.findById(503L)).thenReturn(Optional.of(orgAdminGrant));
            when(evaluatorService.canActorOverrideGrant(workspaceManager1, orgAdminGrant, orgFolderAlpha, null))
                    .thenReturn(false);

            assertThatThrownBy(() -> permissionService.activatePermission(503L, workspaceManager1.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to activate permission created with tier ORG_ADMIN");
        }

        @Test
        @DisplayName("Activate: Check B fails when Workspace Manager attempts to activate grant suspended by ORG_ADMIN")
        void testActivate_CannotActivateGrantSuspendedByHigherAuthority() {
            // Workspace Manager created the grant, but ORG_ADMIN suspended it
            PermissionEntity wmGrant = PermissionEntity.builder()
                    .id(504L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(10L)
                    .folder(orgFolderAlpha)
                    .grantedBy(workspaceManager1)
                    .suspended(true)
                    .revokedBy(orgAdminAlphaA) // Suspended by ORG_ADMIN
                    .build();

            when(permissionRepository.findById(504L)).thenReturn(Optional.of(wmGrant));
            // Check A passes (wm-1 has authority over original wm grant)
            when(evaluatorService.canActorOverrideGrant(workspaceManager1, wmGrant, orgFolderAlpha, null)).thenReturn(true);
            // Check B FAILS (wm-1 cannot override ORG_ADMIN inactivator)
            when(evaluatorService.canActorOverrideInactivator(workspaceManager1, orgAdminAlphaA, wmGrant, orgFolderAlpha, null)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.activatePermission(504L, workspaceManager1.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to activate permission suspended/revoked by user admin-a@alpha.com");
        }

        @Test
        @DisplayName("Activate: Peer in same workspace can activate grant suspended by peer")
        void testActivate_PeerCanActivateGrantSuspendedByPeer() {
            PermissionEntity wmGrant = PermissionEntity.builder()
                    .id(505L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(10L)
                    .folder(orgFolderAlpha)
                    .grantedBy(workspaceManager1)
                    .suspended(true)
                    .revokedBy(workspaceManager1) // Suspended by peer WM1
                    .build();

            when(permissionRepository.findById(505L)).thenReturn(Optional.of(wmGrant));
            when(evaluatorService.canActorOverrideGrant(workspaceManager2, wmGrant, orgFolderAlpha, null)).thenReturn(true);
            when(evaluatorService.canActorOverrideInactivator(workspaceManager2, workspaceManager1, wmGrant, orgFolderAlpha, null)).thenReturn(true);

            PermissionResponseDto response = permissionService.activatePermission(505L, workspaceManager2.getId());

            assertThat(response).isNotNull();
            assertThat(wmGrant.isSuspended()).isFalse();
            assertThat(wmGrant.getRevokedBy()).isNull();
            // Original provenance preserved
            assertThat(wmGrant.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(wmGrant.getWorkspaceId()).isEqualTo(10L);
            assertThat(wmGrant.getGrantedBy()).isEqualTo(workspaceManager1);
        }
    }

    // =========================================================================
    // 3. Duplicate Overwrite & Update Provenance Enforcement
    // =========================================================================

    @Nested
    @DisplayName("Duplicate Overwrite & Update Tests")
    class DuplicateAndUpdateTests {

        @Test
        @DisplayName("Duplicate Overwrite: lower-tier administrator cannot overwrite higher-tier grant")
        void testDuplicateOverwrite_LowerTierBlocked() {
            GroupEntity group = GroupEntity.builder().id(50L).organization(orgAlpha).build();
            when(groupRepository.findById(50L)).thenReturn(Optional.of(group));

            PermissionEntity existingOrgAdminGrant = PermissionEntity.builder()
                    .id(601L)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .folder(orgFolderAlpha)
                    .targetGroup(group)
                    .grantedBy(orgAdminAlphaA)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(50L, 100L))
                    .thenReturn(List.of(existingOrgAdminGrant));
            when(evaluatorService.canActorOverrideGrant(workspaceManager1, existingOrgAdminGrant, orgFolderAlpha, null))
                    .thenReturn(false); // WM cannot override ORG_ADMIN

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.GROUP)
                    .folderId(100L)
                    .targetGroupId(50L)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, workspaceManager1.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to override existing permission created with tier ORG_ADMIN");
        }

        @Test
        @DisplayName("Duplicate Overwrite: Peer same-scope overwrite succeeds and preserves immutable provenance")
        void testDuplicateOverwrite_PeerAllowedAndProvenancePreserved() {
            GroupEntity group = GroupEntity.builder().id(50L).organization(orgAlpha).build();
            when(groupRepository.findById(50L)).thenReturn(Optional.of(group));

            PermissionEntity existingWmGrant = PermissionEntity.builder()
                    .id(602L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(10L)
                    .folder(orgFolderAlpha)
                    .targetGroup(group)
                    .grantedBy(workspaceManager1)
                    .permissionLevel(PermissionLevel.VIEW)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(50L, 100L))
                    .thenReturn(List.of(existingWmGrant));
            // Peer WM2 has authority to override WM1 in same workspace
            when(evaluatorService.canActorOverrideGrant(workspaceManager2, existingWmGrant, orgFolderAlpha, null))
                    .thenReturn(true);

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.GROUP)
                    .folderId(100L)
                    .targetGroupId(50L)
                    .workspaceId(10L)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(req, workspaceManager2.getId());

            assertThat(response).isNotNull();
            // Invariant: original provenance MUST NOT change
            assertThat(existingWmGrant.getGrantedBy()).isEqualTo(workspaceManager1);
            assertThat(existingWmGrant.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(existingWmGrant.getWorkspaceId()).isEqualTo(10L);
            assertThat(existingWmGrant.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);

            // Audit record logs modifier WM2
            ArgumentCaptor<AuditPermissionChangeCommand> cmdCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
            verify(auditLogService).logPermissionChange(cmdCaptor.capture());
            assertThat(cmdCaptor.getValue().getGrantedBy()).isEqualTo(workspaceManager2);
        }

        @Test
        @DisplayName("updatePermission: cannot modify permission if caller fails provenance override check")
        void testUpdatePermission_FailsProvenanceCheck() {
            PermissionEntity superAdminGrant = PermissionEntity.builder()
                    .id(701L)
                    .grantorAuthorityTier(AuthorityTier.SUPER_ADMIN)
                    .folder(orgFolderAlpha)
                    .grantedBy(superAdmin)
                    .permissionLevel(PermissionLevel.VIEW)
                    .revoked(false)
                    .build();

            when(permissionRepository.findById(701L)).thenReturn(Optional.of(superAdminGrant));
            when(evaluatorService.canActorOverrideGrant(orgAdminAlphaA, superAdminGrant, orgFolderAlpha, null))
                    .thenReturn(false);

            PermissionUpdateRequestDto updateReq = PermissionUpdateRequestDto.builder()
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .build();

            assertThatThrownBy(() -> permissionService.updatePermission(701L, updateReq, orgAdminAlphaA.getId()))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to modify permission created with tier SUPER_ADMIN");
        }
    }
}
