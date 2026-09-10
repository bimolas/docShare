package com.subject77.docshare.modules.permission.services;

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
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessEntryDto;
import com.subject77.docshare.modules.permission.dto.ManageAccessSummaryDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationConsistencyFixesTest {

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private PermissionAuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private com.subject77.docshare.modules.organization.repository.GroupRepository groupRepository;
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private UserOrganizationRepository userOrganizationRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CollaborationRepository collaborationRepository;
    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;
    @Mock
    private PermissionEventPublisher eventPublisher;
    @Mock
    private PermissionCacheService cacheService;
    @Mock
    private PermissionEvaluatorService evaluatorService;
    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    @Mock
    private WorkspaceManagerService workspaceManagerService;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private OrganizationEntity orgAlpha;
    private OrganizationEntity orgBeta;

    private UserEntity superAdmin;
    private UserEntity orgAdminAlpha;
    private UserEntity orgAdminBeta;
    private UserEntity workspaceManagerAlpha1;
    private UserEntity workspaceManagerAlpha2;
    private UserEntity collabManager1;
    private UserEntity creatorA;
    private UserEntity ordinaryUser;

    private FolderEntity wsFolderAlpha;
    private FolderEntity otherWsFolderAlpha;
    private FolderEntity collabFolderAlpha;

    private DocumentEntity wsDocAlpha;
    private DocumentEntity otherWsDocAlpha;
    private DocumentEntity collabDocAlpha;

    private CollaborationEntity activeCollab;

    @BeforeEach
    void setUp() {
        orgAlpha = OrganizationEntity.builder().orgCode("ALPHA").name("Organization Alpha").build();
        orgBeta = OrganizationEntity.builder().orgCode("BETA").name("Organization Beta").build();

        superAdmin = UserEntity.builder().id("super-1").email("super@docshare.com").role(Role.ROLE_SUPER_ADMIN).build();
        orgAdminAlpha = UserEntity.builder().id("admin-alpha").email("admin@alpha.com").role(Role.ROLE_ORG_ADMIN).organization(orgAlpha).build();
        orgAdminBeta = UserEntity.builder().id("admin-beta").email("admin@beta.com").role(Role.ROLE_ORG_ADMIN).organization(orgBeta).build();

        workspaceManagerAlpha1 = UserEntity.builder().id("wm-1").email("wm1@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        workspaceManagerAlpha2 = UserEntity.builder().id("wm-2").email("wm2@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

        collabManager1 = UserEntity.builder().id("cm-1").email("cm1@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        creatorA = UserEntity.builder().id("creator-a").email("creator@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        ordinaryUser = UserEntity.builder().id("user-regular").email("regular@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

        wsFolderAlpha = FolderEntity.builder().id(100L).name("Workspace Folder").ownerOrg(orgAlpha).build();
        otherWsFolderAlpha = FolderEntity.builder().id(200L).name("Other Workspace Folder").ownerOrg(orgAlpha).build();
        collabFolderAlpha = FolderEntity.builder().id(300L).name("Collab Folder").ownerOrg(orgAlpha).build();

        wsDocAlpha = DocumentEntity.builder().id(101L).fileName("ws-doc.pdf").fileHash("hash-ws-doc-101").ownerOrg(orgAlpha).folder(wsFolderAlpha).uploadedBy(creatorA).build();
        otherWsDocAlpha = DocumentEntity.builder().id(201L).fileName("other-ws-doc.pdf").fileHash("hash-other-ws-201").ownerOrg(orgAlpha).folder(otherWsFolderAlpha).uploadedBy(creatorA).build();
        collabDocAlpha = DocumentEntity.builder().id(301L).fileName("collab-doc.pdf").fileHash("hash-collab-301").ownerOrg(orgAlpha).folder(collabFolderAlpha).uploadedBy(creatorA).build();

        activeCollab = CollaborationEntity.builder().id(50L).name("Collab Alpha").status(CollaborationStatus.ACTIVE).ownerOrganization(orgAlpha).rootFolder(collabFolderAlpha).build();
    }

    // =========================================================================
    // 1. WORKSPACE MANAGER DOCUMENT-LEVEL GRANTS (canActorOverrideGrant)
    // =========================================================================

    @Nested
    @DisplayName("1. Workspace Manager Document-Level Grants (Evaluator canActorOverrideGrant)")
    class WorkspaceManagerDocumentLevelGrantsTests {

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
            ReflectionTestUtils.setField(realEvaluator, "workspaceManagerService", workspaceManagerService);

            // Mock active membership for ALPHA users
            lenient().when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(anyString(), eq("ALPHA"), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(true);

            // Mock org admin membership for orgAdminAlpha
            UserOrganizationEntity adminMember = UserOrganizationEntity.builder().user(orgAdminAlpha).organization(orgAlpha).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-alpha", "ALPHA")).thenReturn(Optional.of(adminMember));

            // Mock org admin membership for orgAdminBeta
            UserOrganizationEntity adminMemberBeta = UserOrganizationEntity.builder().user(orgAdminBeta).organization(orgBeta).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-beta", "BETA")).thenReturn(Optional.of(adminMemberBeta));
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-beta", "ALPHA")).thenReturn(Optional.empty());

            // Collaboration manager mock
            lenient().when(collaborationRepository.findById(50L)).thenReturn(Optional.of(activeCollab));
            CollaborationParticipantEntity participant = CollaborationParticipantEntity.builder()
                    .collaboration(activeCollab)
                    .user(collabManager1)
                    .collaborationRole(CollaborationRole.MANAGER)
                    .build();
            lenient().when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(50L, "cm-1")).thenReturn(Optional.of(participant));
        }

        @Test
        @DisplayName("Scenario A: Workspace Manager CAN manage document-level grant inside own workspace")
        void scenarioA_WorkspaceManagerCanManageDocumentLevelGrantInsideOwnWorkspace() {
            // Target permission is directly attached to document (folder is null on permission)
            PermissionEntity docGrant = PermissionEntity.builder()
                    .id(1L)
                    .document(wsDocAlpha)
                    .folder(null) // attached directly to DocumentEntity with folder == null
                    .workspaceId(10L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .grantedBy(workspaceManagerAlpha1)
                    .build();

            // Setup WM2 as authorized manager for wsFolderAlpha (workspace ID 10L)
            when(workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(workspaceManagerAlpha2, wsFolderAlpha, 10L))
                    .thenReturn(10L);

            // Both with folder == null (relying on doc.getFolder()) and explicit doc parameter
            boolean canOverrideWithDoc = realEvaluator.canActorOverrideGrant(workspaceManagerAlpha2, docGrant, null, wsDocAlpha);
            assertThat(canOverrideWithDoc).isTrue();

            // Also test with folder == null and doc == null (resolving directly from docGrant.getDocument())
            boolean canOverrideFromGrant = realEvaluator.canActorOverrideGrant(workspaceManagerAlpha2, docGrant, null, null);
            assertThat(canOverrideFromGrant).isTrue();

            // Lower-tier ORG_RESOURCE_CREATOR grant on the document can also be overridden by WM
            PermissionEntity creatorGrant = PermissionEntity.builder()
                    .id(2L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                    .grantedBy(creatorA)
                    .build();

            when(workspaceManagerService.isWorkspaceManagerForFolder(workspaceManagerAlpha2, wsFolderAlpha))
                    .thenReturn(true);

            assertThat(realEvaluator.canActorOverrideGrant(workspaceManagerAlpha2, creatorGrant, null, null)).isTrue();
        }

        @Test
        @DisplayName("Scenario B: Workspace Manager CANNOT manage document-level grant in another workspace")
        void scenarioB_WorkspaceManagerCannotManageDocumentLevelGrantInAnotherWorkspace() {
            // Target permission is on a document inside otherWsFolderAlpha (workspace ID 20L)
            PermissionEntity otherWsDocGrant = PermissionEntity.builder()
                    .id(3L)
                    .document(otherWsDocAlpha)
                    .folder(null)
                    .workspaceId(20L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .build();

            // WM1 is NOT the manager for otherWsFolderAlpha (resolve returns null)
            when(workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(workspaceManagerAlpha1, otherWsFolderAlpha, 20L))
                    .thenReturn(null);

            boolean result = realEvaluator.canActorOverrideGrant(workspaceManagerAlpha1, otherWsDocGrant, null, otherWsDocAlpha);
            assertThat(result).isFalse();

            // For ORG_RESOURCE_CREATOR in other workspace, WM1 is not manager
            PermissionEntity otherCreatorGrant = PermissionEntity.builder()
                    .id(4L)
                    .document(otherWsDocAlpha)
                    .folder(null)
                    .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                    .grantedBy(creatorA)
                    .build();

            when(workspaceManagerService.isWorkspaceManagerForFolder(workspaceManagerAlpha1, otherWsFolderAlpha))
                    .thenReturn(false);

            assertThat(realEvaluator.canActorOverrideGrant(workspaceManagerAlpha1, otherCreatorGrant, null, null)).isFalse();
        }

        @Test
        @DisplayName("Scenario C: Collaboration Manager behavior remains unchanged")
        void scenarioC_CollaborationManagerBehaviorRemainsUnchanged() {
            // Collab grant on document
            PermissionEntity collabDocGrant = PermissionEntity.builder()
                    .id(5L)
                    .document(collabDocAlpha)
                    .folder(null)
                    .collaboration(activeCollab)
                    .grantorAuthorityTier(AuthorityTier.COLLABORATION_MANAGER)
                    .grantedBy(collabManager1)
                    .build();

            // Collab manager can override collab grant within scope
            boolean canCollabManagerOverride = realEvaluator.canActorOverrideGrant(collabManager1, collabDocGrant, null, collabDocAlpha);
            assertThat(canCollabManagerOverride).isTrue();

            // Collab manager CANNOT override a Workspace Manager document grant
            PermissionEntity wsDocGrant = PermissionEntity.builder()
                    .id(6L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .workspaceId(10L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .build();

            when(workspaceManagerService.resolveWorkspaceIdForManagerAndFolder(collabManager1, wsFolderAlpha, 10L))
                    .thenReturn(null);

            boolean canCollabOverrideWm = realEvaluator.canActorOverrideGrant(collabManager1, wsDocGrant, null, wsDocAlpha);
            assertThat(canCollabOverrideWm).isFalse();
        }

        @Test
        @DisplayName("Scenario D: ORG_ADMIN behavior remains unchanged")
        void scenarioD_OrgAdminBehaviorRemainsUnchanged() {
            // Document grant inside Org Alpha
            PermissionEntity wsDocGrant = PermissionEntity.builder()
                    .id(7L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .workspaceId(10L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .build();

            // Org Admin of Alpha CAN override document grant inside own org
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminAlpha, wsDocGrant, null, wsDocAlpha)).isTrue();

            // Org Admin of Beta CANNOT override document grant in Org Alpha
            assertThat(realEvaluator.canActorOverrideGrant(orgAdminBeta, wsDocGrant, null, wsDocAlpha)).isFalse();

            // Org Admin CANNOT override SUPER_ADMIN document grant
            PermissionEntity superDocGrant = PermissionEntity.builder()
                    .id(8L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .grantorAuthorityTier(AuthorityTier.SUPER_ADMIN)
                    .build();

            assertThat(realEvaluator.canActorOverrideGrant(orgAdminAlpha, superDocGrant, null, wsDocAlpha)).isFalse();
        }

        @Test
        @DisplayName("Scenario E: SUPER_ADMIN behavior remains unchanged")
        void scenarioE_SuperAdminBehaviorRemainsUnchanged() {
            PermissionEntity superDocGrant = PermissionEntity.builder()
                    .id(9L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .grantorAuthorityTier(AuthorityTier.SUPER_ADMIN)
                    .build();

            PermissionEntity wsDocGrant = PermissionEntity.builder()
                    .id(10L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .workspaceId(10L)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .build();

            // SUPER_ADMIN can override everything
            assertThat(realEvaluator.canActorOverrideGrant(superAdmin, superDocGrant, null, wsDocAlpha)).isTrue();
            assertThat(realEvaluator.canActorOverrideGrant(superAdmin, wsDocGrant, null, wsDocAlpha)).isTrue();
        }
    }

    // =========================================================================
    // 2. getPermissionById WORKSPACE MANAGER VISIBILITY
    // =========================================================================

    @Nested
    @DisplayName("2. getPermissionById Workspace Manager Visibility")
    class GetPermissionByIdWorkspaceManagerVisibilityTests {

        @Test
        @DisplayName("getPermissionById: allowed for properly scoped Workspace Manager on folder permission in own workspace")
        void getPermissionById_Allowed_ForWorkspaceManagerOnFolderInOwnWorkspace() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(100L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(orgAdminAlpha) // granted by org admin
                    .build();

            when(permissionRepository.findById(100L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(workspaceManagerAlpha1, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(100L, workspaceManagerAlpha1);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getFolderId()).isEqualTo(wsFolderAlpha.getId());
        }

        @Test
        @DisplayName("getPermissionById: allowed for properly scoped Workspace Manager on document-level permission in own workspace")
        void getPermissionById_Allowed_ForWorkspaceManagerOnDocumentInOwnWorkspace() {
            // Document grant where permission.folder is null, but document.folder is wsFolderAlpha
            PermissionEntity perm = PermissionEntity.builder()
                    .id(101L)
                    .document(wsDocAlpha)
                    .folder(null)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(creatorA)
                    .build();

            when(permissionRepository.findById(101L)).thenReturn(Optional.of(perm));
            // Folder resolved from wsDocAlpha.getFolder()
            when(evaluatorService.canAdministerFolderPermissions(workspaceManagerAlpha1, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(101L, workspaceManagerAlpha1);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(101L);
            assertThat(response.getDocumentId()).isEqualTo(wsDocAlpha.getId());
        }

        @Test
        @DisplayName("getPermissionById: allowed for properly scoped Workspace Manager when document is resolved via file hash")
        void getPermissionById_Allowed_WhenDocumentResolvedViaFileHash() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(102L)
                    .document(null)
                    .documentFileHash("hash-ws-doc-101")
                    .folder(null)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(creatorA)
                    .build();

            when(permissionRepository.findById(102L)).thenReturn(Optional.of(perm));
            when(documentRepository.findByFileHash("hash-ws-doc-101")).thenReturn(List.of(wsDocAlpha));
            when(evaluatorService.canAdministerFolderPermissions(workspaceManagerAlpha1, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(102L, workspaceManagerAlpha1);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(102L);
        }

        @Test
        @DisplayName("getPermissionById: denied for Workspace Manager outside workspace scope (different workspace)")
        void getPermissionById_Denied_ForOutOfScopeWorkspaceManager() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(103L)
                    .folder(otherWsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(103L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(workspaceManagerAlpha1, otherWsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(103L, workspaceManagerAlpha1))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: denied for inactive Workspace Manager")
        void getPermissionById_Denied_ForInactiveWorkspaceManager() {
            UserEntity inactiveWm = UserEntity.builder().id("inactive-wm").email("inactive@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

            PermissionEntity perm = PermissionEntity.builder()
                    .id(104L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(104L)).thenReturn(Optional.of(perm));
            // Evaluator denies inactive workspace managers
            when(evaluatorService.canAdministerFolderPermissions(inactiveWm, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(104L, inactiveWm))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: denied for Workspace Manager in another organization")
        void getPermissionById_Denied_ForWorkspaceManagerInAnotherOrg() {
            UserEntity wmBeta = UserEntity.builder().id("wm-beta").email("wm@beta.com").role(Role.ROLE_USER).organization(orgBeta).build();

            PermissionEntity perm = PermissionEntity.builder()
                    .id(105L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(105L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(wmBeta, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(105L, wmBeta))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: Collaboration Manager cannot gain Workspace Manager access")
        void getPermissionById_Denied_CollaborationManagerAccessingWorkspaceFolder() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(106L)
                    .folder(wsFolderAlpha) // workspace folder, not a collaboration folder
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(106L)).thenReturn(Optional.of(perm));
            // Collab manager has no authority over workspace folder
            when(evaluatorService.canAdministerFolderPermissions(collabManager1, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(106L, collabManager1))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: still denies ordinary content users / grantees")
        void getPermissionById_Denied_ForOrdinaryContentUser() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(107L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail(ordinaryUser.getEmail())
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(107L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(ordinaryUser, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(107L, ordinaryUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: secret fields (passwordHash, shareLinkToken) remain sanitized (null)")
        void getPermissionById_SecretFieldsRemainSanitized() {
            PermissionEntity permWithSecrets = PermissionEntity.builder()
                    .id(108L)
                    .folder(wsFolderAlpha)
                    .passwordHash("$2a$10$e8wD1o5V2P6T9u8mX...secret")
                    .shareLinkToken("super-secret-token-uuid-12345")
                    .targetUserEmail("external@partner.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .grantedBy(workspaceManagerAlpha1)
                    .build();

            when(permissionRepository.findById(108L)).thenReturn(Optional.of(permWithSecrets));
            when(evaluatorService.canAdministerFolderPermissions(workspaceManagerAlpha1, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(108L, workspaceManagerAlpha1);

            assertThat(response).isNotNull();
            assertThat(response.getPasswordHash()).isNull();
            assertThat(response.getShareLinkToken()).isNull();
        }
    }

    // =========================================================================
    // 3. getPermissionById CURRENT AUTHORIZATION ENFORCEMENT (Phase 3B.7)
    // =========================================================================

    @Nested
    @DisplayName("3. getPermissionById Current Authorization Enforcement (Phase 3B.7)")
    class GetPermissionByIdCurrentAuthorizationTests {

        @Test
        @DisplayName("getPermissionById: denied for former grantor who left organization")
        void getPermissionById_Denied_ForFormerGrantorWhoLeftOrganization() {
            UserEntity formerGrantor = UserEntity.builder().id("former-grantor").email("former@alpha.com").role(Role.ROLE_USER).build();
            PermissionEntity perm = PermissionEntity.builder()
                    .id(201L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(formerGrantor)
                    .build();

            when(permissionRepository.findById(201L)).thenReturn(Optional.of(perm));
            // Former grantor has left the org, evaluator denies
            when(evaluatorService.canAdministerFolderPermissions(formerGrantor, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(201L, formerGrantor))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: denied for grantor who lost EDIT authority")
        void getPermissionById_Denied_ForGrantorWhoLostEditAuthority() {
            UserEntity grantorLostEdit = UserEntity.builder().id("grantor-no-edit").email("noedit@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
            PermissionEntity perm = PermissionEntity.builder()
                    .id(202L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(grantorLostEdit)
                    .build();

            when(permissionRepository.findById(202L)).thenReturn(Optional.of(perm));
            // Grantor lost EDIT on the resource, evaluator denies
            when(evaluatorService.canAdministerFolderPermissions(grantorLostEdit, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(202L, grantorLostEdit))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: denied for excluded grantor")
        void getPermissionById_Denied_ForExcludedGrantor() {
            UserEntity excludedGrantor = UserEntity.builder().id("grantor-excluded").email("excluded@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
            PermissionEntity perm = PermissionEntity.builder()
                    .id(203L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(excludedGrantor)
                    .build();

            when(permissionRepository.findById(203L)).thenReturn(Optional.of(perm));
            // Grantor was excluded from the resource, evaluator denies
            when(evaluatorService.canAdministerFolderPermissions(excludedGrantor, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(203L, excludedGrantor))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: allowed for original grantor who still has valid admin authority")
        void getPermissionById_Allowed_ForGrantorWithCurrentValidAdminAuthority() {
            UserEntity validGrantor = UserEntity.builder().id("valid-grantor").email("valid@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
            PermissionEntity perm = PermissionEntity.builder()
                    .id(204L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(validGrantor)
                    .build();

            when(permissionRepository.findById(204L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(validGrantor, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(204L, validGrantor);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(204L);
        }

        @Test
        @DisplayName("getPermissionById: allowed for SUPER_ADMIN without resource evaluation")
        void getPermissionById_Allowed_ForSuperAdmin() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(205L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(creatorA)
                    .build();

            when(permissionRepository.findById(205L)).thenReturn(Optional.of(perm));

            PermissionResponseDto response = permissionService.getPermissionById(205L, superAdmin);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(205L);
            verify(evaluatorService, never()).canAdministerFolderPermissions(any(), any());
        }

        @Test
        @DisplayName("getPermissionById: allowed for Org Admin with current valid authority")
        void getPermissionById_Allowed_ForOrgAdmin() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(206L)
                    .folder(wsFolderAlpha)
                    .targetUserEmail("grantee@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(creatorA)
                    .build();

            when(permissionRepository.findById(206L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(orgAdminAlpha, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(206L, orgAdminAlpha);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(206L);
        }
    }

    // =========================================================================
    // 4. getPermissionById TEAM MANAGER BYPASS PREVENTION (Phase 3B.8)
    // =========================================================================

    @Nested
    @DisplayName("4. getPermissionById Team Manager Bypass Prevention (Phase 3B.8)")
    class GetPermissionByIdTeamManagerBypassTests {

        private GroupEntity targetTeamAlpha;
        private UserEntity teamManagerAlpha;

        @BeforeEach
        void setUpTeam() {
            targetTeamAlpha = GroupEntity.builder()
                    .id(500L)
                    .name("Team Alpha")
                    .organization(orgAlpha)
                    .build();

            teamManagerAlpha = UserEntity.builder()
                    .id("tm-alpha-1")
                    .email("tm@alpha.com")
                    .role(Role.ROLE_USER)
                    .organization(orgAlpha)
                    .build();
        }

        @Test
        @DisplayName("getPermissionById: denied for Team Manager of target group without resource authority")
        void getPermissionById_Denied_ForTeamManagerOfTargetGroup() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(301L)
                    .folder(wsFolderAlpha)
                    .targetGroup(targetTeamAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(301L)).thenReturn(Optional.of(perm));
            // Team Manager has no resource administration authority on the folder
            when(evaluatorService.canAdministerFolderPermissions(teamManagerAlpha, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(301L, teamManagerAlpha))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: denied for Team Manager even when team has EDIT content access")
        void getPermissionById_Denied_ForTeamManagerEvenWithContentEdit() {
            // Team has EDIT content access on the folder, but Team Manager is still NOT a permission administrator
            PermissionEntity perm = PermissionEntity.builder()
                    .id(302L)
                    .folder(wsFolderAlpha)
                    .targetGroup(targetTeamAlpha)
                    .permissionLevel(PermissionLevel.EDIT)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(302L)).thenReturn(Optional.of(perm));
            // Architectural rule: Team Manager receives content permission, NOT permission-admin authority
            when(evaluatorService.canAdministerFolderPermissions(teamManagerAlpha, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(302L, teamManagerAlpha))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }

        @Test
        @DisplayName("getPermissionById: valid Workspace Manager still allowed")
        void getPermissionById_Allowed_ForValidWorkspaceManager() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(303L)
                    .folder(wsFolderAlpha)
                    .targetGroup(targetTeamAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(303L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(workspaceManagerAlpha1, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(303L, workspaceManagerAlpha1);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(303L);
        }

        @Test
        @DisplayName("getPermissionById: valid Org Admin still allowed")
        void getPermissionById_Allowed_ForValidOrgAdmin() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(304L)
                    .folder(wsFolderAlpha)
                    .targetGroup(targetTeamAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(creatorA)
                    .build();

            when(permissionRepository.findById(304L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(orgAdminAlpha, wsFolderAlpha)).thenReturn(true);

            PermissionResponseDto response = permissionService.getPermissionById(304L, orgAdminAlpha);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(304L);
        }

        @Test
        @DisplayName("getPermissionById: valid Super Admin still allowed")
        void getPermissionById_Allowed_ForValidSuperAdmin() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(305L)
                    .folder(wsFolderAlpha)
                    .targetGroup(targetTeamAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(creatorA)
                    .build();

            when(permissionRepository.findById(305L)).thenReturn(Optional.of(perm));

            PermissionResponseDto response = permissionService.getPermissionById(305L, superAdmin);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(305L);
            verify(evaluatorService, never()).canAdministerFolderPermissions(any(), any());
        }

        @Test
        @DisplayName("getPermissionById: ordinary grantee still denied")
        void getPermissionById_Denied_ForOrdinaryGrantee() {
            PermissionEntity perm = PermissionEntity.builder()
                    .id(306L)
                    .folder(wsFolderAlpha)
                    .targetGroup(targetTeamAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .grantedBy(orgAdminAlpha)
                    .build();

            when(permissionRepository.findById(306L)).thenReturn(Optional.of(perm));
            when(evaluatorService.canAdministerFolderPermissions(ordinaryUser, wsFolderAlpha)).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getPermissionById(306L, ordinaryUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view this permission");
        }
    }

    // =========================================================================
    // 5. PERMISSION READ SECURITY FIXES (Phase 3B.9)
    // =========================================================================

    @Nested
    @DisplayName("5. Permission Read Security Fixes (Phase 3B.9)")
    class PermissionReadSecurityFixesTests {

        private GroupEntity activeGroupAlpha;
        private GroupEntity archivedGroupAlpha;
        private GroupEntity activeGroupBeta;
        private UserEntity teamManagerUser;
        private UserEntity regularMemberUser;

        @BeforeEach
        void setUpGroupsAndUsers() {
            activeGroupAlpha = GroupEntity.builder()
                    .id(601L)
                    .name("Active Team Alpha")
                    .organization(orgAlpha)
                    .status(GroupStatus.ACTIVE)
                    .build();

            archivedGroupAlpha = GroupEntity.builder()
                    .id(602L)
                    .name("Archived Team Alpha")
                    .organization(orgAlpha)
                    .status(GroupStatus.ARCHIVED)
                    .build();

            activeGroupBeta = GroupEntity.builder()
                    .id(603L)
                    .name("Active Team Beta")
                    .organization(orgBeta)
                    .status(GroupStatus.ACTIVE)
                    .build();

            teamManagerUser = UserEntity.builder()
                    .id("tm-sec-1")
                    .email("tm-sec@alpha.com")
                    .role(Role.ROLE_USER)
                    .organization(orgAlpha)
                    .build();

            regularMemberUser = UserEntity.builder()
                    .id("member-sec-1")
                    .email("member-sec@alpha.com")
                    .role(Role.ROLE_USER)
                    .organization(orgAlpha)
                    .build();
        }

        // --- Finding 1: Share Link Token Secret Leak ---

        @Test
        @DisplayName("Finding 1: getManageAccessSummary does NOT expose bearer shareLinkToken")
        void getManageAccessSummary_DoesNotExposeShareLinkToken() {
            String secretToken = "bearer-secret-token-xyz-98765";
            PermissionEntity shareLinkPerm = PermissionEntity.builder()
                    .id(701L)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .shareLinkToken(secretToken)
                    .document(wsDocAlpha)
                    .grantedBy(creatorA)
                    .revoked(false)
                    .build();

            when(documentRepository.findById(101L)).thenReturn(Optional.of(wsDocAlpha));
            when(evaluatorService.canAdministerDocumentPermissions(creatorA, wsDocAlpha)).thenReturn(true);
            when(permissionRepository.findByDocument_Id(101L)).thenReturn(List.of(shareLinkPerm));

            ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(101L, "DOCUMENT", creatorA);

            assertThat(summary).isNotNull();
            assertThat(summary.getEntries()).isNotEmpty();

            EffectiveAccessEntryDto shareLinkEntry = summary.getEntries().stream()
                    .filter(e -> "SHARE_LINK".equals(e.getPrincipalType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Expected SHARE_LINK entry"));

            assertThat(shareLinkEntry.getPrincipalType()).isEqualTo("SHARE_LINK");
            assertThat(shareLinkEntry.getPrincipalId()).isNull();
            assertThat(shareLinkEntry.getDisplayName()).isEqualTo("Share Link");
            assertThat(shareLinkEntry.isExternal()).isTrue();
            assertThat(shareLinkEntry.getPermissionId()).isEqualTo(701L);

            // Verify the secret token never appears anywhere in the summary entries
            for (EffectiveAccessEntryDto entry : summary.getEntries()) {
                assertThat(entry.getPrincipalId()).isNotEqualTo(secretToken);
                assertThat(entry.getDisplayName()).isNotEqualTo(secretToken);
                assertThat(entry.getEmail()).isNotEqualTo(secretToken);
            }
        }

        // --- Finding 2: Stale Team Manager Membership in getActivePermissionsForGroup ---

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup allowed for active Team Manager with active org membership")
        void getActivePermissionsForGroup_AllowedForActiveTeamManager() {
            PermissionEntity groupPerm = PermissionEntity.builder()
                    .id(702L)
                    .targetGroup(activeGroupAlpha)
                    .folder(wsFolderAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .revoked(false)
                    .build();

            when(groupRepository.findById(601L)).thenReturn(Optional.of(activeGroupAlpha));
            when(evaluatorService.isUserAdminOfOrg(teamManagerUser, "ALPHA")).thenReturn(false);

            UserGroupEntity managerMembership = UserGroupEntity.builder()
                    .user(teamManagerUser)
                    .group(activeGroupAlpha)
                    .role(GroupMemberRole.MANAGER)
                    .build();
            when(userGroupRepository.findByUser_IdAndGroup_Id("tm-sec-1", 601L)).thenReturn(Optional.of(managerMembership));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("tm-sec-1", "ALPHA", MembershipStatus.ACTIVE))
                    .thenReturn(true);
            when(permissionRepository.findByTargetGroup_IdAndRevokedFalse(601L)).thenReturn(List.of(groupPerm));

            List<PermissionResponseDto> result = permissionService.getActivePermissionsForGroup(601L, teamManagerUser);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(702L);
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup denied for Team Manager whose org membership is SUSPENDED")
        void getActivePermissionsForGroup_DeniedForSuspendedOrgMembership() {
            when(groupRepository.findById(601L)).thenReturn(Optional.of(activeGroupAlpha));
            when(evaluatorService.isUserAdminOfOrg(teamManagerUser, "ALPHA")).thenReturn(false);

            UserGroupEntity managerMembership = UserGroupEntity.builder()
                    .user(teamManagerUser)
                    .group(activeGroupAlpha)
                    .role(GroupMemberRole.MANAGER)
                    .build();
            when(userGroupRepository.findByUser_IdAndGroup_Id("tm-sec-1", 601L)).thenReturn(Optional.of(managerMembership));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("tm-sec-1", "ALPHA", MembershipStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> permissionService.getActivePermissionsForGroup(601L, teamManagerUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view permissions for this group");
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup denied for Team Manager whose org membership is REVOKED/INACTIVE")
        void getActivePermissionsForGroup_DeniedForRevokedOrgMembership() {
            when(groupRepository.findById(601L)).thenReturn(Optional.of(activeGroupAlpha));
            when(evaluatorService.isUserAdminOfOrg(teamManagerUser, "ALPHA")).thenReturn(false);

            UserGroupEntity managerMembership = UserGroupEntity.builder()
                    .user(teamManagerUser)
                    .group(activeGroupAlpha)
                    .role(GroupMemberRole.MANAGER)
                    .build();
            when(userGroupRepository.findByUser_IdAndGroup_Id("tm-sec-1", 601L)).thenReturn(Optional.of(managerMembership));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("tm-sec-1", "ALPHA", MembershipStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> permissionService.getActivePermissionsForGroup(601L, teamManagerUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view permissions for this group");
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup denied for Team Manager when group is ARCHIVED")
        void getActivePermissionsForGroup_DeniedForArchivedGroup() {
            when(groupRepository.findById(602L)).thenReturn(Optional.of(archivedGroupAlpha));
            when(evaluatorService.isUserAdminOfOrg(teamManagerUser, "ALPHA")).thenReturn(false);

            assertThatThrownBy(() -> permissionService.getActivePermissionsForGroup(602L, teamManagerUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view permissions for this group");
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup denied for cross-organization group")
        void getActivePermissionsForGroup_DeniedForCrossOrgGroup() {
            // Group belongs to BETA, user only in ALPHA
            when(groupRepository.findById(603L)).thenReturn(Optional.of(activeGroupBeta));
            when(evaluatorService.isUserAdminOfOrg(teamManagerUser, "BETA")).thenReturn(false);

            UserGroupEntity managerMembership = UserGroupEntity.builder()
                    .user(teamManagerUser)
                    .group(activeGroupBeta)
                    .role(GroupMemberRole.MANAGER)
                    .build();
            when(userGroupRepository.findByUser_IdAndGroup_Id("tm-sec-1", 603L)).thenReturn(Optional.of(managerMembership));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("tm-sec-1", "BETA", MembershipStatus.ACTIVE))
                    .thenReturn(false);

            assertThatThrownBy(() -> permissionService.getActivePermissionsForGroup(603L, teamManagerUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view permissions for this group");
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup denied for ordinary team member")
        void getActivePermissionsForGroup_DeniedForOrdinaryMember() {
            when(groupRepository.findById(601L)).thenReturn(Optional.of(activeGroupAlpha));
            when(evaluatorService.isUserAdminOfOrg(regularMemberUser, "ALPHA")).thenReturn(false);

            UserGroupEntity regularMembership = UserGroupEntity.builder()
                    .user(regularMemberUser)
                    .group(activeGroupAlpha)
                    .role(GroupMemberRole.MEMBER)
                    .build();
            when(userGroupRepository.findByUser_IdAndGroup_Id("member-sec-1", 601L)).thenReturn(Optional.of(regularMembership));

            assertThatThrownBy(() -> permissionService.getActivePermissionsForGroup(601L, regularMemberUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to view permissions for this group");
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup allowed for Org Admin")
        void getActivePermissionsForGroup_AllowedForOrgAdmin() {
            PermissionEntity groupPerm = PermissionEntity.builder()
                    .id(703L)
                    .targetGroup(activeGroupAlpha)
                    .folder(wsFolderAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .revoked(false)
                    .build();

            when(groupRepository.findById(601L)).thenReturn(Optional.of(activeGroupAlpha));
            when(evaluatorService.isUserAdminOfOrg(orgAdminAlpha, "ALPHA")).thenReturn(true);
            when(permissionRepository.findByTargetGroup_IdAndRevokedFalse(601L)).thenReturn(List.of(groupPerm));

            List<PermissionResponseDto> result = permissionService.getActivePermissionsForGroup(601L, orgAdminAlpha);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(703L);
        }

        @Test
        @DisplayName("Finding 2: getActivePermissionsForGroup allowed for Super Admin")
        void getActivePermissionsForGroup_AllowedForSuperAdmin() {
            PermissionEntity groupPerm = PermissionEntity.builder()
                    .id(704L)
                    .targetGroup(activeGroupAlpha)
                    .folder(wsFolderAlpha)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .revoked(false)
                    .build();

            when(groupRepository.findById(601L)).thenReturn(Optional.of(activeGroupAlpha));
            when(permissionRepository.findByTargetGroup_IdAndRevokedFalse(601L)).thenReturn(List.of(groupPerm));

            List<PermissionResponseDto> result = permissionService.getActivePermissionsForGroup(601L, superAdmin);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(704L);
        }
    }
}
