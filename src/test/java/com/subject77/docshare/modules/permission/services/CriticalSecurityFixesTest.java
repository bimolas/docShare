package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.document.dto.DownloadUrlResponse;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CriticalSecurityFixesTest {

    // =========================================================================
    // 1. CRITICAL — VIEW SHARE LINK MUST NOT DOWNLOAD
    // =========================================================================
    @Nested
    @DisplayName("1. Critical: Share Link Download Permission Enforcement")
    class ShareLinkDownloadTests {

        @Mock
        private PermissionRepository permissionRepository;
        @Mock
        private PermissionAuditLogRepository auditLogRepository;
        @Mock
        private DocumentRepository documentRepository;
        @Mock
        private FolderRepository folderRepository;
        @Mock
        private StorageService storageService;
        @Mock
        private PasswordEncoder passwordEncoder;
        @Mock
        private PermissionCacheService cacheService;
        @Mock
        private PermissionEventPublisher eventPublisher;
        @Mock
        private PermissionEvaluatorService evaluatorService;
        @Mock
        private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

        @InjectMocks
        private ShareServiceImpl shareService;

        private DocumentEntity activeDoc;
        private FolderEntity activeFolder;

        @BeforeEach
        void setUp() {
            activeDoc = DocumentEntity.builder()
                    .id(100L)
                    .fileName("Secret.pdf")
                    .objectKey("orgs/1/docs/Secret.pdf")
                    .status(DocumentStatus.ACTIVE)
                    .build();

            activeFolder = FolderEntity.builder()
                    .id(200L)
                    .name("Shared Folder")
                    .build();
        }

        @Test
        @DisplayName("A. VIEW share link download is denied with AccessDeniedException")
        void viewShareLink_Download_Denied() {
            PermissionEntity viewLink = PermissionEntity.builder()
                    .id(1L)
                    .shareLinkToken("view-token-123")
                    .document(activeDoc)
                    .permissionLevel(PermissionLevel.VIEW)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();

            when(permissionRepository.findByShareLinkTokenAndRevokedFalse("view-token-123")).thenReturn(Optional.of(viewLink));

            assertThatThrownBy(() -> shareService.downloadSharedDocument("view-token-123", null, 100L, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("does not permit downloading files");

            verify(storageService, never()).generatePresignedDownloadUrl(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("B. DOWNLOAD share link download succeeds and generates presigned URL")
        void downloadShareLink_Download_Success() {
            PermissionEntity downloadLink = PermissionEntity.builder()
                    .id(2L)
                    .shareLinkToken("download-token-123")
                    .document(activeDoc)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();

            when(permissionRepository.findByShareLinkTokenAndRevokedFalse("download-token-123")).thenReturn(Optional.of(downloadLink));
            when(storageService.generatePresignedDownloadUrl(eq(activeDoc.getObjectKey()), eq(activeDoc.getFileName()), anyLong()))
                    .thenReturn("https://s3.docshare.local/download/Secret.pdf");

            DownloadUrlResponse response = shareService.downloadSharedDocument("download-token-123", null, 100L, null);

            assertThat(response).isNotNull();
            assertThat(response.getDownloadUrl()).isEqualTo("https://s3.docshare.local/download/Secret.pdf");
            assertThat(response.getDocumentId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("C. EDIT share link download succeeds and generates presigned URL")
        void editShareLink_Download_Success() {
            PermissionEntity editLink = PermissionEntity.builder()
                    .id(3L)
                    .shareLinkToken("edit-token-123")
                    .document(activeDoc)
                    .permissionLevel(PermissionLevel.EDIT)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();

            when(permissionRepository.findByShareLinkTokenAndRevokedFalse("edit-token-123")).thenReturn(Optional.of(editLink));
            when(storageService.generatePresignedDownloadUrl(eq(activeDoc.getObjectKey()), eq(activeDoc.getFileName()), anyLong()))
                    .thenReturn("https://s3.docshare.local/download/Secret.pdf");

            DownloadUrlResponse response = shareService.downloadSharedDocument("edit-token-123", null, 100L, null);

            assertThat(response).isNotNull();
            assertThat(response.getDownloadUrl()).isEqualTo("https://s3.docshare.local/download/Secret.pdf");
        }

        @Test
        @DisplayName("D. Expired share link is denied even if DOWNLOAD level")
        void expiredShareLink_Denied() {
            PermissionEntity expiredLink = PermissionEntity.builder()
                    .id(4L)
                    .shareLinkToken("expired-token")
                    .document(activeDoc)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                    .build();

            when(permissionRepository.findByShareLinkTokenAndRevokedFalse("expired-token")).thenReturn(Optional.of(expiredLink));

            assertThatThrownBy(() -> shareService.downloadSharedDocument("expired-token", null, 100L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        @DisplayName("E. Password-protected share link requires correct password before download")
        void passwordProtectedLink_EnforcesPassword() {
            PermissionEntity passLink = PermissionEntity.builder()
                    .id(5L)
                    .shareLinkToken("pass-token")
                    .document(activeDoc)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .passwordHash("hashed-secret")
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();

            when(permissionRepository.findByShareLinkTokenAndRevokedFalse("pass-token")).thenReturn(Optional.of(passLink));
            when(passwordEncoder.matches("wrong-pass", "hashed-secret")).thenReturn(false);

            assertThatThrownBy(() -> shareService.downloadSharedDocument("pass-token", "wrong-pass", 100L, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Invalid or missing password");
        }

        @Test
        @DisplayName("F. Excluded document under folder share link remains denied")
        void excludedDocumentUnderFolder_Denied() {
            PermissionEntity folderLink = PermissionEntity.builder()
                    .id(6L)
                    .shareLinkToken("folder-token")
                    .folder(activeFolder)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                    .build();

            DocumentEntity childDoc = DocumentEntity.builder()
                    .id(101L)
                    .fileName("Private.pdf")
                    .folder(activeFolder)
                    .status(DocumentStatus.ACTIVE)
                    .build();

            PermissionEntity exclusion = PermissionEntity.builder()
                    .id(7L)
                    .shareLinkToken("folder-token")
                    .permissionType(PermissionType.EXCLUDE)
                    .document(childDoc)
                    .build();

            when(permissionRepository.findByShareLinkTokenAndRevokedFalse("folder-token")).thenReturn(Optional.of(folderLink));
            when(documentRepository.findById(101L)).thenReturn(Optional.of(childDoc));
            when(permissionRepository.findByShareLinkTokenAndPermissionTypeAndRevokedFalse("folder-token", PermissionType.EXCLUDE))
                    .thenReturn(List.of(exclusion));

            assertThatThrownBy(() -> shareService.downloadSharedDocument("folder-token", null, 101L, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("excluded under the current share link");
        }
    }

    // =========================================================================
    // 2. HIGH — ORGANIZATION CREATOR OWNER BYPASS FIX
    // =========================================================================
    @Nested
    @DisplayName("2. High: Organization Creator Owner Bypass Enforcement")
    class OrganizationCreatorBypassTests {

        @Mock
        private PermissionRepository permissionRepository;
        @Mock
        private UserOrganizationRepository userOrganizationRepository;
        @Mock
        private UserGroupRepository userGroupRepository;
        @Mock
        private PermissionCacheService permissionCacheService;

        @InjectMocks
        private PermissionEvaluatorService evaluatorService;

        private OrganizationEntity org;
        private UserEntity creatorUser;
        private DocumentEntity orgDocument;
        private FolderEntity orgFolder;

        @BeforeEach
        void setUp() {
            org = OrganizationEntity.builder()
                    .orgCode("CORP1")
                    .build();

            creatorUser = UserEntity.builder()
                    .id("creator-1")
                    .email("creator@corp1.com")
                    .organization(org)
                    .role(Role.ROLE_USER)
                    .build();

            orgFolder = FolderEntity.builder()
                    .id(10L)
                    .name("Engineering")
                    .ownerOrg(org)
                    .createdBy(UserEntity.builder().id("admin-user").email("admin@corp1.com").build())
                    .build();

            orgDocument = DocumentEntity.builder()
                    .id(50L)
                    .fileName("Specs.pdf")
                    .fileHash("hash50")
                    .ownerOrg(org)
                    .folder(orgFolder)
                    .uploadedBy(creatorUser)
                    .build();

            lenient().when(permissionCacheService.getCachedDocumentPermission(anyString(), anyLong())).thenReturn(Optional.empty());
        }

        private void mockOrgMembership(MembershipStatus status) {
            boolean isActive = status == MembershipStatus.ACTIVE;
            lenient().when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(eq("creator-1"), eq("CORP1"), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(isActive);
            if (!isActive) {
                lenient().when(userOrganizationRepository.findByUser_IdAndStatus("creator-1", MembershipStatus.ACTIVE))
                        .thenReturn(List.of());
            } else {
                UserOrganizationEntity uo = UserOrganizationEntity.builder()
                        .user(creatorUser)
                        .organization(org)
                        .status(MembershipStatus.ACTIVE)
                        .build();
                lenient().when(userOrganizationRepository.findByUser_IdAndStatus("creator-1", MembershipStatus.ACTIVE))
                        .thenReturn(List.of(uo));
            }
        }

        @Test
        @DisplayName("A. Active creator with normal EDIT access retains view, download, edit, and administration")
        void activeCreator_NormalEditAccess_Allowed() {
            orgFolder.setCreatedBy(creatorUser);
            mockOrgMembership(MembershipStatus.ACTIVE);
            lenient().when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("creator@corp1.com", "CORP1")).thenReturn(List.of());

            assertThat(evaluatorService.canViewDocument(creatorUser, orgDocument)).isTrue();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, orgDocument)).isTrue();
            assertThat(evaluatorService.canEditDocument(creatorUser, orgDocument)).isTrue();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, orgDocument)).isTrue();

            assertThat(evaluatorService.canViewFolder(creatorUser, orgFolder)).isTrue();
            assertThat(evaluatorService.canDownloadFolder(creatorUser, orgFolder)).isTrue();
            assertThat(evaluatorService.canEditFolder(creatorUser, orgFolder)).isTrue();
            assertThat(evaluatorService.canAdministerFolderPermissions(creatorUser, orgFolder)).isTrue();
        }

        @Test
        @DisplayName("B. Creator downgraded to DOWNLOAD cannot edit document or administer permissions")
        void creatorDowngradedToDownload_CannotEdit() {
            mockOrgMembership(MembershipStatus.ACTIVE);
            PermissionEntity downloadGrant = PermissionEntity.builder()
                    .id(1L)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetUserEmail("creator@corp1.com")
                    .targetOrgCode("CORP1")
                    .document(orgDocument)
                    .build();

            lenient().when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("creator@corp1.com", "CORP1"))
                    .thenReturn(List.of(downloadGrant));

            assertThat(evaluatorService.canViewDocument(creatorUser, orgDocument)).isTrue();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, orgDocument)).isTrue();
            assertThat(evaluatorService.canEditDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, orgDocument)).isFalse();
        }

        @Test
        @DisplayName("C. Creator downgraded to VIEW cannot download, cannot edit, and cannot administer")
        void creatorDowngradedToView_CannotDownloadOrEdit() {
            mockOrgMembership(MembershipStatus.ACTIVE);
            PermissionEntity viewGrant = PermissionEntity.builder()
                    .id(2L)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetUserEmail("creator@corp1.com")
                    .targetOrgCode("CORP1")
                    .document(orgDocument)
                    .build();

            lenient().when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("creator@corp1.com", "CORP1"))
                    .thenReturn(List.of(viewGrant));

            assertThat(evaluatorService.canViewDocument(creatorUser, orgDocument)).isTrue();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canEditDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, orgDocument)).isFalse();
        }

        @Test
        @DisplayName("D. Creator revoked cannot view, download, edit, or administer")
        void creatorRevoked_NoAccess() {
            mockOrgMembership(MembershipStatus.ACTIVE);
            PermissionEntity revokedGrant = PermissionEntity.builder()
                    .id(3L)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetUserEmail("creator@corp1.com")
                    .targetOrgCode("CORP1")
                    .document(orgDocument)
                    .revoked(true)
                    .revokedAt(Instant.now())
                    .build();

            lenient().when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("creator@corp1.com", "CORP1"))
                    .thenReturn(List.of(revokedGrant));

            assertThat(evaluatorService.canViewDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canEditDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, orgDocument)).isFalse();
        }

        @Test
        @DisplayName("E. Creator excluded via EXCLUDE cannot view, download, edit, or administer")
        void creatorExcluded_NoAccess() {
            mockOrgMembership(MembershipStatus.ACTIVE);
            PermissionEntity excludeGrant = PermissionEntity.builder()
                    .id(4L)
                    .permissionType(PermissionType.EXCLUDE)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetUserEmail("creator@corp1.com")
                    .targetOrgCode("CORP1")
                    .document(orgDocument)
                    .build();

            lenient().when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("creator@corp1.com", "CORP1"))
                    .thenReturn(List.of(excludeGrant));

            assertThat(evaluatorService.canViewDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canEditDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, orgDocument)).isFalse();
        }

        @Test
        @DisplayName("F. Creator removed from organization loses all content access and administration")
        void creatorRemovedFromOrg_NoAccess() {
            mockOrgMembership(MembershipStatus.REMOVED);

            assertThat(evaluatorService.canViewDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canEditDocument(creatorUser, orgDocument)).isFalse();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, orgDocument)).isFalse();

            assertThat(evaluatorService.canViewFolder(creatorUser, orgFolder)).isFalse();
            assertThat(evaluatorService.canDownloadFolder(creatorUser, orgFolder)).isFalse();
            assertThat(evaluatorService.canEditFolder(creatorUser, orgFolder)).isFalse();
            assertThat(evaluatorService.canAdministerFolderPermissions(creatorUser, orgFolder)).isFalse();
        }

        @Test
        @DisplayName("G. Personal resource owner retains sovereign control regardless of org membership")
        void personalOwner_RetainsControl() {
            DocumentEntity personalDoc = DocumentEntity.builder()
                    .id(200L)
                    .fileName("PersonalDiary.pdf")
                    .ownerOrg(null)
                    .uploadedBy(creatorUser)
                    .build();

            FolderEntity personalFolder = FolderEntity.builder()
                    .id(300L)
                    .name("Personal Folder")
                    .ownerOrg(null)
                    .createdBy(creatorUser)
                    .build();

            assertThat(evaluatorService.canViewDocument(creatorUser, personalDoc)).isTrue();
            assertThat(evaluatorService.canDownloadDocument(creatorUser, personalDoc)).isTrue();
            assertThat(evaluatorService.canEditDocument(creatorUser, personalDoc)).isTrue();
            assertThat(evaluatorService.canAdministerDocumentPermissions(creatorUser, personalDoc)).isTrue();

            assertThat(evaluatorService.canViewFolder(creatorUser, personalFolder)).isTrue();
            assertThat(evaluatorService.canDownloadFolder(creatorUser, personalFolder)).isTrue();
            assertThat(evaluatorService.canEditFolder(creatorUser, personalFolder)).isTrue();
            assertThat(evaluatorService.canAdministerFolderPermissions(creatorUser, personalFolder)).isTrue();
        }
    }

    // =========================================================================
    // 3. HIGH — collaborationId PROVENANCE SPOOFING FIX
    // =========================================================================
    @Nested
    @DisplayName("3. High: collaborationId Provenance Spoofing Prevention")
    class CollaborationProvenanceSpoofingTests {

        @Mock
        private PermissionRepository permissionRepository;
        @Mock
        private PermissionAuditLogRepository auditLogRepository;
        @Mock
        private UserRepository userRepository;
        @Mock
        private FolderRepository folderRepository;
        @Mock
        private CollaborationRepository collaborationRepository;
        @Mock
        private CollaborationParticipantRepository collaborationParticipantRepository;
        @Mock
        private PermissionCacheService cacheService;
        @Mock
        private PermissionEventPublisher eventPublisher;
        @Mock
        private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
        @Mock
        private DocumentRepository documentRepository;
        @Mock
        private GroupRepository groupRepository;
        @Mock
        private UserGroupRepository userGroupRepository;
        @Mock
        private OrganizationRepository organizationRepository;
        @Mock
        private NotificationService notificationService;
        @Mock
        private UserOrganizationRepository userOrganizationRepository;

        @InjectMocks
        private PermissionEvaluatorService evaluatorService;

        @InjectMocks
        private PermissionServiceImpl permissionService;

        private OrganizationEntity orgA;
        private OrganizationEntity orgB;
        private FolderEntity collabRootFolder;
        private FolderEntity subfolder;
        private FolderEntity outsideFolder;
        private CollaborationEntity activeCollab;
        private UserEntity collabManager;
        private UserEntity regularUser;
        private UserEntity orgAdminA;
        private UserEntity superAdmin;

        @BeforeEach
        void setUp() {
            org.springframework.test.util.ReflectionTestUtils.setField(permissionService, "evaluatorService", evaluatorService);

            orgA = OrganizationEntity.builder().orgCode("ORGA").build();
            orgB = OrganizationEntity.builder().orgCode("ORGB").build();

            collabRootFolder = FolderEntity.builder().id(100L).name("Collab Root").ownerOrg(orgA).build();
            subfolder = FolderEntity.builder().id(101L).name("Subfolder").parentFolder(collabRootFolder).ownerOrg(orgA).build();
            outsideFolder = FolderEntity.builder().id(200L).name("Outside Folder").ownerOrg(orgA).build();

            activeCollab = CollaborationEntity.builder()
                    .id(77L)
                    .name("Joint Operation")
                    .status(CollaborationStatus.ACTIVE)
                    .rootFolder(collabRootFolder)
                    .ownerOrganization(orgA)
                    .partnerOrganization(orgB)
                    .build();

            collabManager = UserEntity.builder().id("mgr-1").email("mgr@orga.com").organization(orgA).role(Role.ROLE_USER).build();
            regularUser = UserEntity.builder().id("user-1").email("user@orga.com").organization(orgA).role(Role.ROLE_USER).build();
            orgAdminA = UserEntity.builder().id("admin-a").email("admin@orga.com").organization(orgA).role(Role.ROLE_ORG_ADMIN).build();
            superAdmin = UserEntity.builder().id("super-1").email("super@docshare.com").role(Role.ROLE_SUPER_ADMIN).build();

            lenient().when(userRepository.findById("mgr-1")).thenReturn(Optional.of(collabManager));
            lenient().when(userRepository.findById("user-1")).thenReturn(Optional.of(regularUser));
            lenient().when(userRepository.findById("admin-a")).thenReturn(Optional.of(orgAdminA));
            lenient().when(userRepository.findById("super-1")).thenReturn(Optional.of(superAdmin));

            lenient().when(folderRepository.findById(101L)).thenReturn(Optional.of(subfolder));
            lenient().when(folderRepository.findById(200L)).thenReturn(Optional.of(outsideFolder));
            lenient().when(collaborationRepository.findById(77L)).thenReturn(Optional.of(activeCollab));
            lenient().when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE)).thenReturn(List.of(activeCollab));

            lenient().when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(77L, "mgr-1"))
                    .thenReturn(Optional.of(CollaborationParticipantEntity.builder()
                            .collaboration(activeCollab)
                            .user(collabManager)
                            .collaborationRole(CollaborationRole.MANAGER)
                            .build()));

            lenient().when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(77L, "user-1"))
                    .thenReturn(Optional.empty());

            lenient().when(userOrganizationRepository.findByUser_IdAndStatus(anyString(), eq(MembershipStatus.ACTIVE)))
                    .thenReturn(List.of(UserOrganizationEntity.builder().organization(orgA).status(MembershipStatus.ACTIVE).build()));

            lenient().when(userRepository.existsByEmail(anyString())).thenReturn(true);
            lenient().when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> {
                PermissionEntity p = i.getArgument(0);
                p.setId(999L);
                return p;
            });
        }


        @Test
        @DisplayName("A. Valid Collaboration Manager gets COLLABORATION_MANAGER provenance tier")
        void validCollaborationManager_GetsCollaborationManagerTier() {
            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(101L)
                    .collaborationId(77L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("partner@orgb.com")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "mgr-1");

            assertThat(resp).isNotNull();
            assertThat(resp.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.COLLABORATION_MANAGER);
            assertThat(resp.getCollaborationId()).isEqualTo(77L);
        }

        @Test
        @DisplayName("B. Non-manager user supplying collaborationId is denied with AccessDeniedException")
        void nonManager_SupplyingCollaborationId_Denied() {
            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(101L)
                    .collaborationId(77L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("target@docshare.local")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "user-1"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User is not an authorized manager of collaboration ID");
        }

        @Test
        @DisplayName("C. Manager of Collaboration A supplying Collaboration B ID is denied")
        void managerOfCollabA_SupplyingCollabB_Denied() {
            CollaborationEntity collabB = CollaborationEntity.builder()
                    .id(88L)
                    .name("Other Collaboration")
                    .status(CollaborationStatus.ACTIVE)
                    .rootFolder(subfolder)
                    .ownerOrganization(orgA)
                    .build();
            when(collaborationRepository.findById(88L)).thenReturn(Optional.of(collabB));
            when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(88L, "mgr-1")).thenReturn(Optional.empty());

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(101L)
                    .collaborationId(88L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("target@docshare.local")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "mgr-1"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User is not an authorized manager of collaboration ID: 88");
        }

        @Test
        @DisplayName("D. Target folder outside collaboration root folder is rejected with IllegalArgumentException")
        void folderOutsideCollaboration_Rejected() {
            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(200L)
                    .collaborationId(77L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("target@docshare.local")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "mgr-1"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target resource is not within collaboration scope");
        }

        @Test
        @DisplayName("E. Inactive collaboration is rejected with IllegalStateException")
        void inactiveCollaboration_Rejected() {
            CollaborationEntity suspendedCollab = CollaborationEntity.builder()
                    .id(99L)
                    .name("Suspended Joint")
                    .status(CollaborationStatus.SUSPENDED)
                    .rootFolder(collabRootFolder)
                    .ownerOrganization(orgA)
                    .build();
            when(collaborationRepository.findById(99L)).thenReturn(Optional.of(suspendedCollab));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(101L)
                    .collaborationId(99L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("target@docshare.local")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "mgr-1"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Collaboration is not active");
        }

        @Test
        @DisplayName("F. ORG_ADMIN granting inside collaboration retains ORG_ADMIN provenance tier")
        void orgAdmin_RetainsOrgAdminTier_InsideCollaboration() {
            UserOrganizationEntity uo = UserOrganizationEntity.builder()
                    .user(orgAdminA)
                    .organization(orgA)
                    .role(Role.ROLE_ORG_ADMIN)
                    .status(MembershipStatus.ACTIVE)
                    .build();
            lenient().when(userOrganizationRepository.findByUser_IdAndStatus("admin-a", MembershipStatus.ACTIVE))
                    .thenReturn(List.of(uo));
            lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-a", "ORGA"))
                    .thenReturn(Optional.of(uo));
            lenient().when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("admin-a", "ORGA", MembershipStatus.ACTIVE))
                    .thenReturn(true);

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(101L)
                    .collaborationId(77L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("target@docshare.local")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "admin-a");

            assertThat(resp).isNotNull();
            assertThat(resp.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        }

        @Test
        @DisplayName("G. SUPER_ADMIN granting inside collaboration retains SUPER_ADMIN provenance tier")
        void superAdmin_RetainsSuperAdminTier_InsideCollaboration() {
            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(101L)
                    .collaborationId(77L)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail("target@docshare.local")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "super-1");

            assertThat(resp).isNotNull();
            assertThat(resp.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.SUPER_ADMIN);
        }
    }
}
