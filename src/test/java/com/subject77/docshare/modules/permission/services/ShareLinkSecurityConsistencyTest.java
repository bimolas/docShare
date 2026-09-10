package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.dto.ShareLinkRequestDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkSecurityConsistencyTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionAuditLogRepository auditLogRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private PermissionEventPublisher eventPublisher;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private StorageService storageService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @InjectMocks
    private ShareServiceImpl shareService;

    private OrganizationEntity orgAlpha;
    private UserEntity superAdmin;
    private UserEntity orgAdmin;
    private UserEntity workspaceManager1;
    private UserEntity workspaceManager2;
    private UserEntity collabManager1;
    private UserEntity collabManager2;
    private UserEntity uploaderUser;
    private FolderEntity orgFolder;
    private DocumentEntity orgDoc;

    @BeforeEach
    void setUp() {
        orgAlpha = OrganizationEntity.builder().orgCode("ALPHA").name("Org Alpha").build();

        superAdmin = UserEntity.builder().id("super-1").email("super@docshare.com").role(Role.ROLE_SUPER_ADMIN).build();
        orgAdmin = UserEntity.builder().id("admin-1").email("admin@alpha.com").role(Role.ROLE_ORG_ADMIN).organization(orgAlpha).build();
        workspaceManager1 = UserEntity.builder().id("wm-1").email("wm1@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        workspaceManager2 = UserEntity.builder().id("wm-2").email("wm2@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        collabManager1 = UserEntity.builder().id("cm-1").email("cm1@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        collabManager2 = UserEntity.builder().id("cm-2").email("cm2@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        uploaderUser = UserEntity.builder().id("uploader-1").email("uploader@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();

        orgFolder = FolderEntity.builder().id(100L).name("Projects").ownerOrg(orgAlpha).build();
        orgDoc = DocumentEntity.builder().id(200L).fileName("Spec.pdf").fileHash("hash-200").ownerOrg(orgAlpha).folder(orgFolder).uploadedBy(uploaderUser).status(DocumentStatus.ACTIVE).build();

        lenient().when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));
        lenient().when(documentRepository.findById(200L)).thenReturn(Optional.of(orgDoc));
        lenient().when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
            PermissionEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(999L);
            }
            return p;
        });
    }

    // =========================================================================
    // 1. createShareLink Delegation Ceiling Tests
    // =========================================================================

    @Test
    @DisplayName("Scenario A: Restricted uploader with VIEW ceiling cannot create EDIT share link")
    void testRestrictedUploader_CannotCreateEditShareLink() {
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDoc)).thenReturn(true);
        // Uploader ceiling is restricted to VIEW
        when(evaluatorService.resolveDelegationCeiling(uploaderUser, null, orgDoc)).thenReturn(PermissionLevel.VIEW);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .documentId(200L)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        assertThatThrownBy(() -> shareService.createShareLink(request, uploaderUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds delegator ceiling of VIEW");
    }

    @Test
    @DisplayName("Scenario B: Restricted uploader with null ceiling cannot create DOWNLOAD share link")
    void testRestrictedUploader_CannotCreateDownloadShareLink() {
        when(evaluatorService.canAdministerDocumentPermissions(uploaderUser, orgDoc)).thenReturn(true);
        // Downgraded or excluded uploader has null ceiling
        when(evaluatorService.resolveDelegationCeiling(uploaderUser, null, orgDoc)).thenReturn(null);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .documentId(200L)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        assertThatThrownBy(() -> shareService.createShareLink(request, uploaderUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds delegator ceiling of null");
    }

    @Test
    @DisplayName("Scenario C: ORG_ADMIN can create EDIT share link")
    void testOrgAdmin_CanCreateEditShareLink() {
        when(evaluatorService.canAdministerFolderPermissions(orgAdmin, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(orgAdmin, orgFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, orgFolder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        ShareLinkResponseDto response = shareService.createShareLink(request, orgAdmin);

        assertThat(response).isNotNull();
        assertThat(response.getFolderId()).isEqualTo(100L);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();
        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        assertThat(saved.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Scenario D: Valid Workspace Manager can create EDIT share link inside assigned workspace")
    void testWorkspaceManager_CanCreateEditShareLinkInScope() {
        when(evaluatorService.canAdministerFolderPermissions(workspaceManager1, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(workspaceManager1, orgFolder, null)).thenReturn(PermissionLevel.EDIT);
        when(evaluatorService.resolveAuthorityTierAtCreation(workspaceManager1, orgFolder, null, null)).thenReturn(AuthorityTier.WORKSPACE_MANAGER);
        when(evaluatorService.resolveWorkspaceIdForManager(workspaceManager1, orgFolder, null)).thenReturn(10L);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        ShareLinkResponseDto response = shareService.createShareLink(request, workspaceManager1);

        assertThat(response).isNotNull();

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();
        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
        assertThat(saved.getWorkspaceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Scenario E: Invalid/unauthorized Workspace Manager outside scope cannot create share link")
    void testWorkspaceManager_OutsideScopeCannotCreateShareLink() {
        // Outside assigned root hierarchy, canAdministerFolderPermissions returns false
        when(evaluatorService.canAdministerFolderPermissions(workspaceManager1, orgFolder)).thenReturn(false);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> shareService.createShareLink(request, workspaceManager1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to administer folder permissions");
    }

    // =========================================================================
    // 2. Provenance Stamping & Hash Tests
    // =========================================================================

    @Test
    @DisplayName("Scenario F & G: Share-link receives correct grantorAuthorityTier and workspaceId")
    void testShareLinkProvenanceStamping() {
        when(evaluatorService.canAdministerFolderPermissions(workspaceManager1, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(workspaceManager1, orgFolder, null)).thenReturn(PermissionLevel.VIEW);
        when(evaluatorService.resolveAuthorityTierAtCreation(workspaceManager1, orgFolder, null, null)).thenReturn(AuthorityTier.WORKSPACE_MANAGER);
        when(evaluatorService.resolveWorkspaceIdForManager(workspaceManager1, orgFolder, null)).thenReturn(25L);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        shareService.createShareLink(request, workspaceManager1);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
        assertThat(saved.getWorkspaceId()).isEqualTo(25L);
        assertThat(saved.getGrantedBy()).isEqualTo(workspaceManager1);
    }

    @Test
    @DisplayName("Scenario H: Share-link V3 recordHash includes provenance fields")
    void testShareLinkRecordHashIncludesProvenance() {
        when(evaluatorService.canAdministerFolderPermissions(orgAdmin, orgFolder)).thenReturn(true);
        when(evaluatorService.resolveDelegationCeiling(orgAdmin, orgFolder, null)).thenReturn(PermissionLevel.VIEW);
        when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, orgFolder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(100L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        shareService.createShareLink(request, orgAdmin);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getRecordHash()).isNotNull();
        // Verify that recalculating with the same entity matches the stored recordHash
        assertThat(PermissionHashUtils.calculateRecordHash(saved)).isEqualTo(saved.getRecordHash());
    }

    // =========================================================================
    // 3. revokeShareLink Provenance Override Tests
    // =========================================================================

    @Test
    @DisplayName("Scenario I: Workspace Manager cannot revoke ORG_ADMIN-created share link")
    void testWorkspaceManager_CannotRevokeOrgAdminShareLink() {
        PermissionEntity orgAdminLink = PermissionEntity.builder()
                .id(701L)
                .shareLinkToken("token-orgadmin")
                .folder(orgFolder)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .grantedBy(orgAdmin)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token-orgadmin")).thenReturn(Optional.of(orgAdminLink));
        when(evaluatorService.canAdministerFolderPermissions(workspaceManager1, orgFolder)).thenReturn(true);
        // Provenance check fails: WM cannot override ORG_ADMIN
        when(evaluatorService.canActorOverrideGrant(workspaceManager1, orgAdminLink, orgFolder, null)).thenReturn(false);

        assertThatThrownBy(() -> shareService.revokeShareLink("token-orgadmin", workspaceManager1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to revoke share link created with tier ORG_ADMIN");
    }

    @Test
    @DisplayName("Scenario J: Collaboration Manager cannot revoke Workspace Manager-created share link")
    void testCollaborationManager_CannotRevokeWorkspaceManagerShareLink() {
        PermissionEntity wmLink = PermissionEntity.builder()
                .id(702L)
                .shareLinkToken("token-wm")
                .folder(orgFolder)
                .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(10L)
                .grantedBy(workspaceManager1)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token-wm")).thenReturn(Optional.of(wmLink));
        when(evaluatorService.canAdministerFolderPermissions(collabManager1, orgFolder)).thenReturn(true);
        // Disjoint domain: CM cannot override WM
        when(evaluatorService.canActorOverrideGrant(collabManager1, wmLink, orgFolder, null)).thenReturn(false);

        assertThatThrownBy(() -> shareService.revokeShareLink("token-wm", collabManager1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to revoke share link created with tier WORKSPACE_MANAGER");
    }

    @Test
    @DisplayName("Scenario K: Same-scope Workspace Manager peer can revoke peer share link")
    void testSameScopeWorkspaceManager_CanRevokePeerShareLink() {
        PermissionEntity wmLink = PermissionEntity.builder()
                .id(703L)
                .shareLinkToken("token-wm-peer")
                .folder(orgFolder)
                .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(10L)
                .grantedBy(workspaceManager1)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token-wm-peer")).thenReturn(Optional.of(wmLink));
        when(evaluatorService.canAdministerFolderPermissions(workspaceManager2, orgFolder)).thenReturn(true);
        // Peer WM in same workspace: override allowed
        when(evaluatorService.canActorOverrideGrant(workspaceManager2, wmLink, orgFolder, null)).thenReturn(true);

        shareService.revokeShareLink("token-wm-peer", workspaceManager2);

        assertThat(wmLink.isRevoked()).isTrue();
        assertThat(wmLink.getRevokedBy()).isEqualTo(workspaceManager2);
        assertThat(wmLink.getRevokedAt()).isNotNull();
        // Provenance remains immutable
        assertThat(wmLink.getGrantedBy()).isEqualTo(workspaceManager1);
        assertThat(wmLink.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
        assertThat(wmLink.getWorkspaceId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Scenario L: Same-scope Collaboration Manager peer can revoke peer share link")
    void testSameScopeCollaborationManager_CanRevokePeerShareLink() {
        CollaborationEntity collab = CollaborationEntity.builder().id(77L).status(CollaborationStatus.ACTIVE).build();
        PermissionEntity collabLink = PermissionEntity.builder()
                .id(704L)
                .shareLinkToken("token-cm-peer")
                .folder(orgFolder)
                .grantorAuthorityTier(AuthorityTier.COLLABORATION_MANAGER)
                .collaboration(collab)
                .grantedBy(collabManager1)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token-cm-peer")).thenReturn(Optional.of(collabLink));
        when(evaluatorService.canAdministerFolderPermissions(collabManager2, orgFolder)).thenReturn(true);
        when(evaluatorService.canActorOverrideGrant(collabManager2, collabLink, orgFolder, null)).thenReturn(true);

        shareService.revokeShareLink("token-cm-peer", collabManager2);

        assertThat(collabLink.isRevoked()).isTrue();
        assertThat(collabLink.getRevokedBy()).isEqualTo(collabManager2);
        assertThat(collabLink.getGrantedBy()).isEqualTo(collabManager1);
    }

    @Test
    @DisplayName("Scenario M: ORG_ADMIN can revoke lower-tier share link")
    void testOrgAdmin_CanRevokeLowerTierShareLink() {
        PermissionEntity wmLink = PermissionEntity.builder()
                .id(705L)
                .shareLinkToken("token-wm-lower")
                .folder(orgFolder)
                .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(10L)
                .grantedBy(workspaceManager1)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token-wm-lower")).thenReturn(Optional.of(wmLink));
        when(evaluatorService.canAdministerFolderPermissions(orgAdmin, orgFolder)).thenReturn(true);
        when(evaluatorService.canActorOverrideGrant(orgAdmin, wmLink, orgFolder, null)).thenReturn(true);

        shareService.revokeShareLink("token-wm-lower", orgAdmin);

        assertThat(wmLink.isRevoked()).isTrue();
        assertThat(wmLink.getRevokedBy()).isEqualTo(orgAdmin);
    }

    @Test
    @DisplayName("Scenario N & O: SUPER_ADMIN can revoke any share link and original provenance remains unchanged")
    void testSuperAdmin_CanRevokeAnyShareLink_ProvenanceUnchanged() {
        PermissionEntity adminLink = PermissionEntity.builder()
                .id(706L)
                .shareLinkToken("token-admin")
                .folder(orgFolder)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .grantedBy(orgAdmin)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token-admin")).thenReturn(Optional.of(adminLink));
        when(evaluatorService.canAdministerFolderPermissions(superAdmin, orgFolder)).thenReturn(true);
        when(evaluatorService.canActorOverrideGrant(superAdmin, adminLink, orgFolder, null)).thenReturn(true);

        shareService.revokeShareLink("token-admin", superAdmin);

        assertThat(adminLink.isRevoked()).isTrue();
        assertThat(adminLink.getRevokedBy()).isEqualTo(superAdmin);
        // Original provenance immutable
        assertThat(adminLink.getGrantedBy()).isEqualTo(orgAdmin);
        assertThat(adminLink.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
    }
}
