package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.dto.ShareLinkRevealResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShareLinkRevealSecurityTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AuditLogService auditLogService;

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
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @InjectMocks
    private ShareServiceImpl shareService;

    private OrganizationEntity orgAlpha;
    private UserEntity superAdmin;
    private UserEntity orgAdmin;
    private UserEntity workspaceManager;
    private UserEntity collabManager;
    private UserEntity linkCreator;
    private UserEntity otherMember;
    private UserEntity personalOwner;

    private FolderEntity orgFolder;
    private DocumentEntity orgDoc;
    private FolderEntity personalFolder;
    private DocumentEntity personalDoc;

    private PermissionEntity activeOrgLink;
    private PermissionEntity activePersonalLink;

    @BeforeEach
    void setUp() {
        orgAlpha = OrganizationEntity.builder().orgCode("ALPHA").name("Org Alpha").build();

        superAdmin = UserEntity.builder().id("super-1").email("super@docshare.com").role(Role.ROLE_SUPER_ADMIN).build();
        orgAdmin = UserEntity.builder().id("admin-1").email("admin@alpha.com").role(Role.ROLE_ORG_ADMIN).organization(orgAlpha).build();
        workspaceManager = UserEntity.builder().id("wm-1").email("wm@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        collabManager = UserEntity.builder().id("cm-1").email("cm@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        linkCreator = UserEntity.builder().id("creator-1").email("creator@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        otherMember = UserEntity.builder().id("member-2").email("other@alpha.com").role(Role.ROLE_USER).organization(orgAlpha).build();
        personalOwner = UserEntity.builder().id("personal-user-1").email("personal@example.com").role(Role.ROLE_USER).build();

        orgFolder = FolderEntity.builder().id(100L).name("Org Projects").ownerOrg(orgAlpha).build();
        orgDoc = DocumentEntity.builder().id(200L).fileName("OrgDoc.pdf").ownerOrg(orgAlpha).folder(orgFolder).uploadedBy(linkCreator).status(DocumentStatus.ACTIVE).build();

        personalFolder = FolderEntity.builder().id(300L).name("My Personal Vault").ownerOrg(null).createdBy(personalOwner).build();
        personalDoc = DocumentEntity.builder().id(400L).fileName("PersonalNotes.pdf").ownerOrg(null).uploadedBy(personalOwner).status(DocumentStatus.ACTIVE).build();

        activeOrgLink = PermissionEntity.builder()
                .id(1001L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .folder(orgFolder)
                .grantedBy(linkCreator)
                .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                .shareLinkToken("slt_secret_token_alpha_1001")
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .revoked(false)
                .build();

        activePersonalLink = PermissionEntity.builder()
                .id(2001L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .folder(personalFolder)
                .grantedBy(personalOwner)
                .grantorAuthorityTier(AuthorityTier.PERSONAL_RESOURCE_OWNER)
                .shareLinkToken("slt_personal_secret_2001")
                .expiresAt(Instant.now().plus(48, ChronoUnit.HOURS))
                .revoked(false)
                .build();
    }

    @Test
    @DisplayName("1. Creator reveal succeeds and returns minimal response DTO")
    void testCreatorReveal_Succeeds() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));
        when(evaluatorService.canViewFolder(linkCreator, orgFolder)).thenReturn(true);

        ShareLinkRevealResponseDto response = shareService.revealShareLink(1001L, linkCreator);

        assertThat(response).isNotNull();
        assertThat(response.getPermissionId()).isEqualTo(1001L);
        assertThat(response.getShareLinkToken()).isEqualTo("slt_secret_token_alpha_1001");
        assertThat(response.getShareUrl()).isEqualTo("/share/slt_secret_token_alpha_1001");
        assertThat(response.getExpiresAt()).isEqualTo(activeOrgLink.getExpiresAt());
    }

    @Test
    @DisplayName("2. Creator without current resource access is denied (403)")
    void testCreatorWithoutResourceAccess_Denied() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));
        // Creator lost access (e.g. removed from workspace/org or folder access revoked)
        when(evaluatorService.canViewFolder(linkCreator, orgFolder)).thenReturn(false);

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, linkCreator))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User no longer has access to this resource");
    }

    @Test
    @DisplayName("3. Non-creator ORG_ADMIN is denied (403) - authority separation")
    void testNonCreatorOrgAdmin_Denied() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, orgAdmin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the original creator of this share link");
    }

    @Test
    @DisplayName("4. Non-creator WORKSPACE_MANAGER is denied (403)")
    void testNonCreatorWorkspaceManager_Denied() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, workspaceManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the original creator of this share link");
    }

    @Test
    @DisplayName("5. Non-creator COLLABORATION_MANAGER is denied (403)")
    void testNonCreatorCollabManager_Denied() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, collabManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the original creator of this share link");
    }

    @Test
    @DisplayName("6. SUPER_ADMIN non-creator is denied (403) - zero-trust policy")
    void testSuperAdminNonCreator_Denied() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, superAdmin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the original creator of this share link");
    }

    @Test
    @DisplayName("7. Other organization member non-creator is denied (403)")
    void testOtherMemberNonCreator_Denied() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, otherMember))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the original creator of this share link");
    }

    @Test
    @DisplayName("8. Personal resource owner can reveal personal share link")
    void testPersonalOwner_CanRevealPersonalLink() {
        when(permissionRepository.findById(2001L)).thenReturn(Optional.of(activePersonalLink));
        when(evaluatorService.isFolderOwner(personalOwner, personalFolder)).thenReturn(true);
        when(evaluatorService.canViewFolder(personalOwner, personalFolder)).thenReturn(true);

        ShareLinkRevealResponseDto response = shareService.revealShareLink(2001L, personalOwner);

        assertThat(response).isNotNull();
        assertThat(response.getPermissionId()).isEqualTo(2001L);
        assertThat(response.getShareLinkToken()).isEqualTo("slt_personal_secret_2001");
        assertThat(response.getShareUrl()).isEqualTo("/share/slt_personal_secret_2001");
    }

    @Test
    @DisplayName("9. Revoked share link reveal is rejected")
    void testRevokedLink_Denied() {
        activeOrgLink.setRevoked(true);
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, linkCreator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot reveal a revoked share link");
    }

    @Test
    @DisplayName("10. Expired share link reveal is rejected")
    void testExpiredLink_Denied() {
        activeOrgLink.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));

        assertThatThrownBy(() -> shareService.revealShareLink(1001L, linkCreator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot reveal an expired share link");
    }

    @Test
    @DisplayName("11. Non-share-link permission is rejected")
    void testNonShareLinkPermission_Denied() {
        PermissionEntity directGrant = PermissionEntity.builder()
                .id(3001L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .folder(orgFolder)
                .grantedBy(linkCreator)
                .shareLinkToken(null)
                .build();
        when(permissionRepository.findById(3001L)).thenReturn(Optional.of(directGrant));

        assertThatThrownBy(() -> shareService.revealShareLink(3001L, linkCreator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Permission is not a share link");
    }

    @Test
    @DisplayName("12. Successful reveal logs REVEALED_SHARE_LINK audit command without plaintext token")
    void testRevealAudit_Recorded() {
        when(permissionRepository.findById(1001L)).thenReturn(Optional.of(activeOrgLink));
        when(evaluatorService.canViewFolder(linkCreator, orgFolder)).thenReturn(true);

        shareService.revealShareLink(1001L, linkCreator);

        ArgumentCaptor<AuditPermissionChangeCommand> captor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService, times(1)).logPermissionChange(captor.capture());

        AuditPermissionChangeCommand auditCmd = captor.getValue();
        assertThat(auditCmd.getAction()).isEqualTo("REVEALED_SHARE_LINK");
        assertThat(auditCmd.getActorUserId()).isEqualTo(linkCreator.getId());
        assertThat(auditCmd.getTargetType()).isEqualTo("FOLDER");
        assertThat(auditCmd.getTargetId()).isEqualTo(orgFolder.getId());
        assertThat(auditCmd.getGrantedBy()).isEqualTo(linkCreator);
    }
}
