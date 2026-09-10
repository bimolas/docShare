package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
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
import com.subject77.docshare.modules.permission.dto.BulkShareWithExclusionsDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DelegationCeilingEnforcementTest {

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
    private NotificationService notificationService;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private UserEntity adminUser;
    private UserEntity superAdmin;
    private UserEntity normalUser;
    private UserEntity uploaderUser;
    private OrganizationEntity org;
    private FolderEntity orgFolder;
    private DocumentEntity orgDocument;

    @BeforeEach
    void setUp() {
        org = OrganizationEntity.builder()
                .orgCode("CORP")
                .name("Corporate")
                .build();

        adminUser = UserEntity.builder()
                .id("admin-1")
                .email("admin@corp.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        superAdmin = UserEntity.builder()
                .id("super-1")
                .email("super@docshare.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        normalUser = UserEntity.builder()
                .id("user-1")
                .email("user1@corp.com")
                .role(Role.ROLE_USER)
                .build();

        uploaderUser = UserEntity.builder()
                .id("uploader-1")
                .email("uploader@corp.com")
                .role(Role.ROLE_USER)
                .build();

        orgFolder = FolderEntity.builder()
                .id(100L)
                .name("Finance")
                .ownerOrg(org)
                .build();

        orgDocument = DocumentEntity.builder()
                .id(200L)
                .fileName("Report.pdf")
                .fileHash("hash-pdf-200")
                .ownerOrg(org)
                .folder(orgFolder)
                .uploadedBy(uploaderUser)
                .build();

        lenient().when(userRepository.findById(anyString())).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            if ("admin-1".equals(id)) return Optional.of(adminUser);
            if ("super-1".equals(id)) return Optional.of(superAdmin);
            if ("user-1".equals(id)) return Optional.of(normalUser);
            if ("uploader-1".equals(id)) return Optional.of(uploaderUser);
            return Optional.of(UserEntity.builder().id(id).email(id + "@corp.com").build());
        });

        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(true);
        lenient().when(organizationRepository.existsByOrgCode(anyString())).thenReturn(true);
        lenient().when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(anyString(), anyString(), any())).thenReturn(true);
        lenient().when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));
        lenient().when(documentRepository.findByFileHash("hash-pdf-200")).thenReturn(List.of(orgDocument));
        lenient().when(documentRepository.findById(200L)).thenReturn(Optional.of(orgDocument));
        lenient().when(evaluatorService.canActorOverrideGrant(any(), any(), any(), any())).thenReturn(true);
        lenient().when(evaluatorService.canActorOverrideInactivator(any(), any(), any(), any(), any())).thenReturn(true);
        lenient().when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // =========================================================================
    // Test A, B, C: Ceiling comparison logic (VIEW cannot grant DOWNLOAD/EDIT, etc.)
    // =========================================================================

    @Test
    @DisplayName("Scenario A & B: User with VIEW-level ceiling cannot grant DOWNLOAD or EDIT")
    void testViewCeiling_CannotGrantDownloadOrEdit() {
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        // Actor's delegation ceiling resolved to VIEW
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.VIEW);

        PermissionRequestDto downloadReq = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(downloadReq, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds grantor delegation ceiling");

        PermissionRequestDto editReq = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(editReq, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds grantor delegation ceiling");
    }

    @Test
    @DisplayName("Scenario C: User with DOWNLOAD ceiling cannot grant EDIT")
    void testDownloadCeiling_CannotGrantEdit() {
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.DOWNLOAD);

        PermissionRequestDto editReq = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(editReq, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds grantor delegation ceiling");
    }

    @Test
    @DisplayName("Scenario D: Normal EDIT user without administration authority has null ceiling and cannot grant anything")
    void testNormalEditUser_NoAdminAuthority_CannotGrant() {
        // Normal user has content EDIT, but is not an admin, not uploader, etc.
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(false);

        PermissionRequestDto viewReq = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(viewReq, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to administer folder permissions");
    }

    // =========================================================================
    // Test E & F: Administrative roles have EDIT ceiling
    // =========================================================================

    @Test
    @DisplayName("Scenario E: ORG_ADMIN can grant EDIT within own organization")
    void testOrgAdmin_CanGrantEdit() {
        when(evaluatorService.canAdministerFolderPermissions(adminUser, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(adminUser, orgFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(adminUser, orgFolder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(req, adminUser.getId());
        assertThat(response).isNotNull();
        assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
    }

    @Test
    @DisplayName("Scenario F: SUPER_ADMIN can grant EDIT globally")
    void testSuperAdmin_CanGrantEdit() {
        when(evaluatorService.canAdministerFolderPermissions(superAdmin, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(superAdmin, orgFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(superAdmin, orgFolder, null, null)).thenReturn(AuthorityTier.SUPER_ADMIN);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(req, superAdmin.getId());
        assertThat(response).isNotNull();
        assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
    }

    // =========================================================================
    // Test G & H: Workspace Manager scope
    // =========================================================================

    @Test
    @DisplayName("Scenario G: Workspace Manager can grant EDIT in own workspace")
    void testWorkspaceManager_CanGrantEditInOwnWorkspace() {
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(normalUser, orgFolder, null, null)).thenReturn(AuthorityTier.WORKSPACE_MANAGER);
        when(evaluatorService.resolveWorkspaceIdForManager(normalUser, orgFolder, 50L)).thenReturn(50L);

        GroupEntity group = GroupEntity.builder().id(10L).organization(org).build();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.GROUP)
                .folderId(100L)
                .targetGroupId(10L)
                .workspaceId(50L)
                .build();

        PermissionResponseDto response = permissionService.grantPermission(req, normalUser.getId());
        assertThat(response).isNotNull();
        assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
        assertThat(response.getWorkspaceId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("Scenario H: Workspace Manager cannot grant outside assigned workspace scope (ceiling is null)")
    void testWorkspaceManager_OutsideScope_CannotGrant() {
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(false);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(req, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // Test I & J: Collaboration Manager and Personal Resource Owner
    // =========================================================================

    @Test
    @DisplayName("Scenario I: Collaboration Manager can grant EDIT within collaboration scope")
    void testCollaborationManager_CanGrantEditInCollaboration() {
        CollaborationEntity collab = CollaborationEntity.builder().id(77L).name("Joint").build();
        when(collaborationRepository.findById(77L)).thenReturn(Optional.of(collab));
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(normalUser, orgFolder, null, collab)).thenReturn(AuthorityTier.COLLABORATION_MANAGER);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(100L)
                .collaborationId(77L)
                .targetUserEmail("partner@corp.com")
                .targetOrgCode("CORP")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(req, normalUser.getId());
        assertThat(response).isNotNull();
        assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.COLLABORATION_MANAGER);
    }

    @Test
    @DisplayName("Scenario J: Personal resource owner can grant EDIT on personal resource")
    void testPersonalResourceOwner_CanGrantEdit() {
        FolderEntity personalFolder = FolderEntity.builder().id(500L).name("Personal").ownerOrg(null).createdBy(normalUser).build();
        when(folderRepository.findById(500L)).thenReturn(Optional.of(personalFolder));
        when(evaluatorService.canAdministerFolderPermissions(normalUser, personalFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(normalUser, personalFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(normalUser, personalFolder, null, null)).thenReturn(AuthorityTier.PERSONAL_RESOURCE_OWNER);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_PERSONAL)
                .folderId(500L)
                .targetUserEmail("friend@external.com")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(req, normalUser.getId());
        assertThat(response).isNotNull();
        assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.PERSONAL_RESOURCE_OWNER);
    }

    // =========================================================================
    // Test K, L, M, N, O: Organization Uploader Delegation Ceiling
    // =========================================================================

    @Test
    @DisplayName("Scenario K: Organization uploader with effective EDIT can grant EDIT")
    void testOrgUploader_WithEffectiveEdit_CanGrantEdit() {
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDocument)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(uploaderUser, null, orgDocument)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(uploaderUser, null, orgDocument, null)).thenReturn(AuthorityTier.ORG_RESOURCE_CREATOR);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .documentFileHash("hash-pdf-200")
                .targetUserEmail("peer@corp.com")
                .targetOrgCode("CORP")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(req, uploaderUser.getId());
        assertThat(response).isNotNull();
        assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_RESOURCE_CREATOR);
    }

    @Test
    @DisplayName("Scenario L: Organization uploader downgraded to DOWNLOAD cannot grant anything (ceiling null)")
    void testOrgUploader_DowngradedToDownload_CannotGrant() {
        // When uploader is downgraded to DOWNLOAD, canAdministerDocumentPermissions returns false and ceiling is null
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDocument)).thenReturn(false);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .documentFileHash("hash-pdf-200")
                .targetUserEmail("peer@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(req, uploaderUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Scenario M: Organization uploader downgraded to VIEW cannot grant anything")
    void testOrgUploader_DowngradedToView_CannotGrant() {
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDocument)).thenReturn(false);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .documentFileHash("hash-pdf-200")
                .targetUserEmail("peer@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(req, uploaderUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Scenario N: Revoked organization uploader cannot grant anything")
    void testOrgUploader_Revoked_CannotGrant() {
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDocument)).thenReturn(false);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .documentFileHash("hash-pdf-200")
                .targetUserEmail("peer@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(req, uploaderUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Scenario O: Excluded organization uploader cannot grant anything")
    void testOrgUploader_Excluded_CannotGrant() {
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDocument)).thenReturn(false);

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .documentFileHash("hash-pdf-200")
                .targetUserEmail("peer@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantPermission(req, uploaderUser.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    // =========================================================================
    // Test P: Bulk grant cannot bypass ceiling
    // =========================================================================

    @Test
    @DisplayName("Scenario P: Bulk folder share cannot bypass delegation ceiling")
    void testBulkShare_CannotBypassCeiling() {
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        // normalUser only has DOWNLOAD ceiling
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.DOWNLOAD);

        BulkShareWithExclusionsDto bulkReq = BulkShareWithExclusionsDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.EDIT) // Requests EDIT, exceeding DOWNLOAD ceiling
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("target@corp.com")
                .targetOrgCode("CORP")
                .build();

        assertThatThrownBy(() -> permissionService.grantFolderShareWithExclusions(bulkReq, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds grantor delegation ceiling");
    }

    // =========================================================================
    // Test Q & R: updatePermission ceiling enforcement & provenance preservation
    // =========================================================================

    @Test
    @DisplayName("Scenario Q: updatePermission cannot raise permissionLevel above caller delegation ceiling")
    void testUpdatePermission_CannotRaiseAboveCeiling() {
        UserEntity originalCreator = UserEntity.builder().id("creator-1").email("creator@corp.com").build();
        PermissionEntity permission = PermissionEntity.builder()
                .id(901L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folder(orgFolder)
                .grantedBy(originalCreator)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .workspaceId(null)
                .revoked(false)
                .build();

        when(permissionRepository.findById(901L)).thenReturn(Optional.of(permission));
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        // Updater only has DOWNLOAD ceiling
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.DOWNLOAD);

        // Attempting to update to EDIT exceeds DOWNLOAD ceiling
        PermissionUpdateRequestDto updateReq = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        assertThatThrownBy(() -> permissionService.updatePermission(901L, updateReq, normalUser.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds updater delegation ceiling");
    }

    @Test
    @DisplayName("Scenario R: updatePermission within ceiling updates level and leaves original provenance unchanged")
    void testUpdatePermission_WithinCeiling_PreservesProvenance() {
        UserEntity originalCreator = UserEntity.builder().id("creator-1").email("creator@corp.com").build();
        PermissionEntity permission = PermissionEntity.builder()
                .id(902L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folder(orgFolder)
                .grantedBy(originalCreator)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .workspaceId(null)
                .revoked(false)
                .build();

        when(permissionRepository.findById(902L)).thenReturn(Optional.of(permission));
        when(evaluatorService.canAdministerFolderPermissions(normalUser, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(normalUser, orgFolder, null)).thenReturn(PermissionLevel.DOWNLOAD);

        // Updating to DOWNLOAD is within DOWNLOAD ceiling
        PermissionUpdateRequestDto updateReq = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        PermissionResponseDto response = permissionService.updatePermission(902L, updateReq, normalUser.getId());

        assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        // Original provenance fields remain IMMUTABLY PRESERVED
        assertThat(permission.getGrantedBy()).isEqualTo(originalCreator);
        assertThat(permission.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        assertThat(permission.getWorkspaceId()).isNull();
    }
}
