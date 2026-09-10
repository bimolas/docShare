package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.subject77.docshare.exceptions.PermissionTamperedException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionEvaluatorServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private PermissionCacheService permissionCacheService;

    @Mock
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationRepository collaborationRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository collaborationParticipantRepository;

    @Mock
    private WorkspaceManagerService workspaceManagerService;

    @InjectMocks
    private PermissionEvaluatorService permissionEvaluatorService;

    private UserEntity user;
    private OrganizationEntity org;
    private DocumentEntity personalDocument;
    private DocumentEntity orgDocument;

    @BeforeEach
    void setUp() {
        org = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();

        user = UserEntity.builder()
                .id("user-1")
                .email("user1@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();

        personalDocument = DocumentEntity.builder()
                .id(100L)
                .fileHash("hash123")
                .uploadedBy(user)
                .ownerOrg(null)
                .build();

        orgDocument = DocumentEntity.builder()
                .id(200L)
                .fileHash("hash456")
                .uploadedBy(UserEntity.builder().id("other-user").email("other@example.com").build())
                .ownerOrg(org)
                .build();

        // Inject the lazily-wired WorkspaceManagerService mock via reflection
        // (the field uses @Lazy @Autowired, not @RequiredArgsConstructor)
        ReflectionTestUtils.setField(permissionEvaluatorService, "workspaceManagerService", workspaceManagerService);

        lenient().when(userOrganizationRepository.findByUser_IdAndStatus(anyString(), eq(MembershipStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    String userId = invocation.getArgument(0);
                    if ("user-ext".equals(userId) || "super-1".equals(userId) || "other-user".equals(userId) || "other".equals(userId)) {
                        return List.of();
                    }
                    return List.of(UserOrganizationEntity.builder()
                            .user(UserEntity.builder().id(userId).organization(org).build())
                            .organization(org)
                            .status(MembershipStatus.ACTIVE)
                            .build());
                });

        lenient().when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(anyString(), anyString(), eq(MembershipStatus.ACTIVE)))
                .thenAnswer(invocation -> {
                    String userId = invocation.getArgument(0);
                    String orgCode = invocation.getArgument(1);
                    if ("user-ext".equals(userId) || "super-1".equals(userId) || "other-user".equals(userId) || "other".equals(userId)) {
                        return false;
                    }
                    return "ORG1".equalsIgnoreCase(orgCode);
                });

        lenient().when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String userId = invocation.getArgument(0);
                    String orgCode = invocation.getArgument(1);
                    if ("user-ext".equals(userId) || "super-1".equals(userId) || "other-user".equals(userId) || "other".equals(userId)) {
                        return Optional.empty();
                    }
                    if ("ORG1".equalsIgnoreCase(orgCode)) {
                        Role role = Role.ROLE_USER;
                        if ("admin-1".equals(userId)) {
                            role = Role.ROLE_ORG_ADMIN;
                        } else if ("manager-1".equals(userId)) {
                            role = Role.ROLE_ORG_MANAGER;
                        }
                        return Optional.of(UserOrganizationEntity.builder()
                                .user(UserEntity.builder().id(userId).organization(org).role(role).build())
                                .organization(org)
                                .role(role)
                                .status(MembershipStatus.ACTIVE)
                                .build());
                    }
                    return Optional.empty();
                });
    }

    @Test
    @DisplayName("Should allow upload to Root in Personal space")
    void canUploadToFolder_PersonalRoot_Allowed() {
        boolean canUpload = permissionEvaluatorService.canUploadToFolder(user, null, null);
        assertThat(canUpload).isTrue();
    }

    @Test
    @DisplayName("Should allow upload to Root in Organization space if user is member")
    void canUploadToFolder_OrgRoot_Allowed_WhenMember() {
        boolean canUpload = permissionEvaluatorService.canUploadToFolder(user, null, "ORG1");
        assertThat(canUpload).isTrue();
    }

    @Test
    @DisplayName("Should deny upload to Root in Organization space if user is not member")
    void canUploadToFolder_OrgRoot_Denied_WhenNotMember() {
        boolean canUpload = permissionEvaluatorService.canUploadToFolder(user, null, "ORG2");
        assertThat(canUpload).isFalse();
    }

    @Test
    @DisplayName("Should allow owner to view personal document")
    void canViewDocument_Owner_Allowed() {
        boolean canView = permissionEvaluatorService.canViewDocument(user, personalDocument);
        assertThat(canView).isTrue();
    }

    @Test
    @DisplayName("Should deny standard org member without explicit permission to view org document")
    void canViewDocument_OrgMember_Denied_WithoutExplicitPermission() {
        boolean canView = permissionEvaluatorService.canViewDocument(user, orgDocument);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("Should allow org admin to view org document")
    void canViewDocument_OrgAdmin_Allowed() {
        UserEntity adminUser = UserEntity.builder()
                .id("admin-1")
                .email("admin@example.com")
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        boolean canView = permissionEvaluatorService.canViewDocument(adminUser, orgDocument);
        assertThat(canView).isTrue();
    }

    @Test
    @DisplayName("Should deny org manager to view org document without explicit permission")
    void canViewDocument_OrgManager_Denied() {
        UserEntity managerUser = UserEntity.builder()
                .id("manager-1")
                .email("manager@example.com")
                .organization(org)
                .role(Role.ROLE_ORG_MANAGER)
                .build();
        boolean canView = permissionEvaluatorService.canViewDocument(managerUser, orgDocument);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("Should allow document owner, org admin, or super admin to administer document permissions")
    void canAdministerDocumentPermissions_Verification() {
        UserEntity adminUser = UserEntity.builder().id("admin-1").organization(org).role(Role.ROLE_ORG_ADMIN).build();
        UserEntity superAdmin = UserEntity.builder().id("super-1").role(Role.ROLE_SUPER_ADMIN).build();

        assertThat(permissionEvaluatorService.canAdministerDocumentPermissions(user, personalDocument)).isTrue(); // Owner
        assertThat(permissionEvaluatorService.canAdministerDocumentPermissions(adminUser, orgDocument)).isTrue(); // Org Admin
        assertThat(permissionEvaluatorService.canAdministerDocumentPermissions(superAdmin, orgDocument)).isTrue(); // Super Admin
        assertThat(permissionEvaluatorService.canAdministerDocumentPermissions(user, orgDocument)).isFalse(); // Standard org member
    }

    @Test
    @DisplayName("Should allow folder owner, org admin, or super admin to administer folder permissions")
    void canAdministerFolderPermissions_Verification() {
        FolderEntity orgFolder = FolderEntity.builder().id(300L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();
        FolderEntity personalFolder = FolderEntity.builder().id(301L).createdBy(user).build();
        UserEntity adminUser = UserEntity.builder().id("admin-1").organization(org).role(Role.ROLE_ORG_ADMIN).build();
        UserEntity superAdmin = UserEntity.builder().id("super-1").role(Role.ROLE_SUPER_ADMIN).build();

        assertThat(permissionEvaluatorService.canAdministerFolderPermissions(user, personalFolder)).isTrue(); // Owner
        assertThat(permissionEvaluatorService.canAdministerFolderPermissions(adminUser, orgFolder)).isTrue(); // Org Admin
        assertThat(permissionEvaluatorService.canAdministerFolderPermissions(superAdmin, orgFolder)).isTrue(); // Super Admin
        assertThat(permissionEvaluatorService.canAdministerFolderPermissions(user, orgFolder)).isFalse(); // Standard org member
    }

    @Test
    @DisplayName("Should evaluate explicit permission on document for non-owner non-org member")
    void canDownloadDocument_ExplicitPermission_Allowed() {
        UserEntity externalUser = UserEntity.builder()
                .id("user-ext")
                .email("external@example.com")
                .build();

        PermissionEntity permission = PermissionEntity.builder()
                .targetUserEmail("external@example.com")
                .documentFileHash("hash123")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        when(permissionCacheService.getCachedDocumentPermission(anyString(), anyLong())).thenReturn(Optional.empty());
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(permission));

        boolean canDownload = permissionEvaluatorService.canDownloadDocument(externalUser, personalDocument);
        assertThat(canDownload).isTrue();
    }

    @Test
    @DisplayName("Should deny edit if user has only DOWNLOAD permission")
    void canEditDocument_OnlyDownloadPermission_Denied() {
        UserEntity externalUser = UserEntity.builder()
                .id("user-ext")
                .email("external@example.com")
                .build();

        PermissionEntity permission = PermissionEntity.builder()
                .targetUserEmail("external@example.com")
                .documentFileHash("hash123")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        when(permissionCacheService.getCachedDocumentPermission(anyString(), anyLong())).thenReturn(Optional.empty());
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(permission));

        boolean canEdit = permissionEvaluatorService.canEditDocument(externalUser, personalDocument);
        assertThat(canEdit).isFalse();
    }

    @Test
    @DisplayName("Should detect tampered permission record with invalid hash, trigger alert, and deny access")
    void evaluatePermission_TamperedRecord_AlertAndDenied() {
        UserEntity externalUser = UserEntity.builder()
                .id("user-ext")
                .email("external@example.com")
                .build();

        PermissionEntity tamperedPermission = PermissionEntity.builder()
                .id(99L)
                .targetUserEmail("external@example.com")
                .documentFileHash("hash123")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recordHash("INVALID_CORRUPTED_HASH_TAMPERED")
                .build();

        when(permissionCacheService.getCachedDocumentPermission(anyString(), anyLong())).thenReturn(Optional.empty());
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(tamperedPermission));

        assertThatThrownBy(() -> permissionEvaluatorService.canEditDocument(externalUser, personalDocument))
                .isInstanceOf(PermissionTamperedException.class)
                .hasMessageContaining("cryptographic checksum mismatch");
    }

    @Test
    @DisplayName("Should deny document access if user has active EXCLUDE permission on it")
    void canViewDocument_ExcludePermission_Denied() {
        UserEntity externalUser = UserEntity.builder()
                .id("user-ext")
                .email("external@example.com")
                .build();

        PermissionEntity excludePerm = PermissionEntity.builder()
                .id(101L)
                .targetUserEmail("external@example.com")
                .documentFileHash("hash123")
                .permissionType(PermissionType.EXCLUDE)
                .build();

        when(permissionCacheService.getCachedDocumentPermission("external@example.com", 100L))
                .thenReturn(Optional.empty());
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(excludePerm));

        boolean canView = permissionEvaluatorService.canViewDocument(externalUser, personalDocument);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("Scenario A: Standard org member without explicit permission should be DENIED edit and view on org folder")
    void scenarioA_OrgMember_Denied_WithoutExplicitPermission() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").email("other@example.com").build())
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), "ORG1"))
                .thenReturn(List.of());

        boolean canEdit = permissionEvaluatorService.canEditFolder(user, orgFolder);
        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, orgFolder);

        assertThat(canEdit).isFalse();
        assertThat(level).isNull();
    }

    @Test
    @DisplayName("Scenario B: Folder creator retains full control (EDIT) over created folder")
    void scenarioB_FolderCreator_Allowed() {
        FolderEntity personalFolder = FolderEntity.builder()
                .id(501L)
                .createdBy(user)
                .build();

        boolean canEdit = permissionEvaluatorService.canEditFolder(user, personalFolder);
        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, personalFolder);

        assertThat(canEdit).isTrue();
        assertThat(level).isEqualTo(PermissionLevel.EDIT);
    }

    @Test
    @DisplayName("Scenario C: SUPER_ADMIN retains organization administrative access to edit folder")
    void scenarioC_SuperAdmin_Allowed() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").build())
                .build();

        UserEntity superAdmin = UserEntity.builder()
                .id("super-1")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        boolean canEdit = permissionEvaluatorService.canEditFolder(superAdmin, orgFolder);
        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(superAdmin, orgFolder);

        assertThat(canEdit).isTrue();
        assertThat(canAdmin).isTrue();
    }

    @Test
    @DisplayName("Scenario D: ORG_ADMIN of owner org retains administrative access to edit folder")
    void scenarioD_OrgAdmin_Allowed() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").build())
                .build();

        UserEntity orgAdmin = UserEntity.builder()
                .id("admin-1")
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        boolean canEdit = permissionEvaluatorService.canEditFolder(orgAdmin, orgFolder);
        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(orgAdmin, orgFolder);

        assertThat(canEdit).isTrue();
        assertThat(canAdmin).isTrue();
    }

    @Test
    @DisplayName("Scenario E: ORG_MANAGER of owner org is DENIED administrative and edit access without explicit permission")
    void scenarioE_OrgManager_Denied() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").build())
                .build();

        UserEntity orgManager = UserEntity.builder()
                .id("manager-1")
                .organization(org)
                .role(Role.ROLE_ORG_MANAGER)
                .build();

        boolean canEdit = permissionEvaluatorService.canEditFolder(orgManager, orgFolder);
        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(orgManager, orgFolder);

        assertThat(canEdit).isFalse();
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Scenario F: Regular org member with explicit VIEW permission receives VIEW, not EDIT")
    void scenarioF_ExplicitViewPermission_ViewGranted_EditDenied() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").build())
                .build();

        PermissionEntity viewPerm = PermissionEntity.builder()
                .id(201L)
                .targetUserEmail(user.getEmail())
                .folder(orgFolder)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), "ORG1"))
                .thenReturn(List.of(viewPerm));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, orgFolder);
        boolean canEdit = permissionEvaluatorService.canEditFolder(user, orgFolder);

        assertThat(level).isEqualTo(PermissionLevel.VIEW);
        assertThat(canEdit).isFalse();
    }

    @Test
    @DisplayName("Scenario G & J: Regular org member with explicit EDIT permission gets EDIT content but DENIED permission administration")
    void scenarioGJ_ExplicitEditPermission_ContentEditGranted_PermissionAdminDenied() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").build())
                .build();

        PermissionEntity editPerm = PermissionEntity.builder()
                .id(202L)
                .targetUserEmail(user.getEmail())
                .folder(orgFolder)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), "ORG1"))
                .thenReturn(List.of(editPerm));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, orgFolder);
        boolean canEdit = permissionEvaluatorService.canEditFolder(user, orgFolder);
        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(user, orgFolder);

        assertThat(level).isEqualTo(PermissionLevel.EDIT);
        assertThat(canEdit).isTrue();
        assertThat(canAdmin).isFalse(); // Critical: EDIT level does NOT grant administration rights!
    }

    @Test
    @DisplayName("Scenario H: EXCLUDE permission overrides ALLOW permission")
    void scenarioH_ExcludePermission_OverridesAllow() {
        FolderEntity orgFolder = FolderEntity.builder()
                .id(500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other-user").build())
                .build();

        PermissionEntity orgAllowPerm = PermissionEntity.builder()
                .id(203L)
                .targetOrgCode("ORG1")
                .folder(orgFolder)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        PermissionEntity userExcludePerm = PermissionEntity.builder()
                .id(204L)
                .targetUserEmail(user.getEmail())
                .folder(orgFolder)
                .permissionType(PermissionType.EXCLUDE)
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), "ORG1"))
                .thenReturn(List.of(orgAllowPerm, userExcludePerm));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, orgFolder);
        boolean canEdit = permissionEvaluatorService.canEditFolder(user, orgFolder);

        assertThat(level).isNull();
        assertThat(canEdit).isFalse();
    }

    @Test
    @DisplayName("Scenario I: Document created by User A in Org folder grants User A creator access; User B without permission is DENIED")
    void scenarioI_DocumentInOrgFolder_CreatorAccessVsOtherMember() {
        UserEntity userA = UserEntity.builder().id("user-a").email("usera@example.com").organization(org).role(Role.ROLE_USER).build();
        UserEntity userB = UserEntity.builder().id("user-b").email("userb@example.com").organization(org).role(Role.ROLE_USER).build();

        DocumentEntity docInOrgFolder = DocumentEntity.builder()
                .id(600L)
                .fileHash("hash600")
                .uploadedBy(userA)
                .ownerOrg(org)
                .build();

        // User A (creator/uploader)
        assertThat(permissionEvaluatorService.canViewDocument(userA, docInOrgFolder)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(userA, docInOrgFolder)).isTrue();

        // User B (other org member, no explicit permission)
        when(permissionCacheService.getCachedDocumentPermission("userb@example.com", 600L)).thenReturn(Optional.empty());
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("userb@example.com", "ORG1")).thenReturn(List.of());

        assertThat(permissionEvaluatorService.canViewDocument(userB, docInOrgFolder)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(userB, docInOrgFolder)).isFalse();
    }

    @Test
    @DisplayName("Team Manager authority: Team Manager with EDIT access cannot administer folder permissions")
    void canAdministerFolderPermissions_TeamManager_EditAccess_Denied() {
        GroupEntity engGroup = GroupEntity.builder().id(10L).name("Engineering").organization(org).build();
        UserEntity engManager = UserEntity.builder().id("eng-mgr").email("engmgr@example.com").organization(org).role(Role.ROLE_USER).build();
        FolderEntity engFolder = FolderEntity.builder().id(700L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .id(100L)
                .user(engManager)
                .group(engGroup)
                .role(GroupMemberRole.MANAGER)
                .build();

        PermissionEntity engPermission = PermissionEntity.builder()
                .id(701L)
                .targetGroup(engGroup)
                .folder(engFolder)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("eng-mgr", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().user(engManager).organization(org).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).build()));

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(engManager, engFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Team Manager authority: Team Manager with DOWNLOAD access cannot administer folder permissions")
    void canAdministerFolderPermissions_TeamManager_DownloadAccess_Denied() {
        GroupEntity engGroup = GroupEntity.builder().id(10L).name("Engineering").organization(org).build();
        UserEntity engManager = UserEntity.builder().id("eng-mgr").email("engmgr@example.com").organization(org).role(Role.ROLE_USER).build();
        FolderEntity engFolder = FolderEntity.builder().id(700L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("eng-mgr", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().user(engManager).organization(org).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).build()));

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(engManager, engFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Team Manager authority: Team Manager with VIEW access cannot administer folder permissions")
    void canAdministerFolderPermissions_TeamManager_ViewAccess_Denied() {
        GroupEntity engGroup = GroupEntity.builder().id(10L).name("Engineering").organization(org).build();
        UserEntity engManager = UserEntity.builder().id("eng-mgr").email("engmgr@example.com").organization(org).role(Role.ROLE_USER).build();
        FolderEntity engFolder = FolderEntity.builder().id(700L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();

        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("eng-mgr", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().user(engManager).organization(org).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).build()));

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(engManager, engFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Team Manager authority: Marketing manager cannot administer Engineering folder")
    void canAdministerFolderPermissions_MarketingManager_DeniedForEngineeringResource() {
        GroupEntity mktGroup = GroupEntity.builder().id(20L).name("Marketing").organization(org).build();
        UserEntity mktManager = UserEntity.builder().id("mkt-mgr").email("mktmgr@example.com").organization(org).role(Role.ROLE_USER).build();
        FolderEntity engFolder = FolderEntity.builder().id(700L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(mktManager, engFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Team Manager authority: Manager of unrelated team without any permissions is denied")
    void canAdministerFolderPermissions_UnrelatedTeamManager_Denied() {
        GroupEntity salesGroup = GroupEntity.builder().id(30L).name("Sales").organization(org).build();
        UserEntity salesManager = UserEntity.builder().id("sales-mgr").email("salesmgr@example.com").organization(org).role(Role.ROLE_USER).build();
        FolderEntity engFolder = FolderEntity.builder().id(700L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(salesManager, engFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Team Manager authority: Normal team member (not manager) is denied folder administration")
    void canAdministerFolderPermissions_NormalMember_Denied() {
        GroupEntity engGroup = GroupEntity.builder().id(10L).name("Engineering").organization(org).build();
        UserEntity normalMember = UserEntity.builder().id("eng-dev").email("engdev@example.com").organization(org).role(Role.ROLE_USER).build();
        FolderEntity engFolder = FolderEntity.builder().id(700L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build()).build();

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(normalMember, engFolder);
        assertThat(canAdmin).isFalse();
    }

    // =========================================================================
    // Workspace Manager authority integration tests
    // =========================================================================

    @Test
    @DisplayName("Workspace manager for Workspace A can administer Workspace A folder")
    void canAdministerFolderPermissions_WorkspaceManagerA_AllowedForWorkspaceAFolder() {
        UserEntity wmUser = UserEntity.builder()
                .id("wm-1").email("wm@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity wsAFolder = FolderEntity.builder()
                .id(900L).ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        // Workspace manager service says yes
        when(workspaceManagerService.isWorkspaceManagerForFolder(wmUser, wsAFolder)).thenReturn(true);

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(wmUser, wsAFolder);
        assertThat(canAdmin).isTrue();
    }

    @Test
    @DisplayName("Workspace A manager cannot administer Workspace B folder")
    void canAdministerFolderPermissions_WorkspaceManagerA_DeniedForWorkspaceBFolder() {
        UserEntity wmUser = UserEntity.builder()
                .id("wm-1").email("wm@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity wsBFolder = FolderEntity.builder()
                .id(901L).ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        // Workspace manager service says no (different workspace)
        when(workspaceManagerService.isWorkspaceManagerForFolder(wmUser, wsBFolder)).thenReturn(false);

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(wmUser, wsBFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Workspace manager cannot administer folder in unrelated organization")
    void canAdministerFolderPermissions_WorkspaceManager_DeniedForUnrelatedOrgFolder() {
        OrganizationEntity otherOrg = OrganizationEntity.builder().orgCode("ORG2").name("Org Two").build();
        UserEntity wmUser = UserEntity.builder()
                .id("wm-1").email("wm@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity otherOrgFolder = FolderEntity.builder()
                .id(902L).ownerOrg(otherOrg)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        // isUserAdminOrManagerOfOrg will call userOrganizationRepository for ORG2 and get empty →
        // method returns false before reaching userGroupRepository, so no group stub needed.
        // Workspace manager service also says no.
        when(workspaceManagerService.isWorkspaceManagerForFolder(wmUser, otherOrgFolder)).thenReturn(false);

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(wmUser, otherOrgFolder);
        assertThat(canAdmin).isFalse();
    }

    @Test
    @DisplayName("Inactive workspace manager (service returns false) cannot administer workspace folder")
    void canAdministerFolderPermissions_InactiveWorkspaceManager_Denied() {
        UserEntity inactiveWm = UserEntity.builder()
                .id("wm-inactive").email("inactive@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity wsFolder = FolderEntity.builder()
                .id(903L).ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        // Service returns false because membership is suspended
        when(workspaceManagerService.isWorkspaceManagerForFolder(inactiveWm, wsFolder)).thenReturn(false);

        boolean canAdmin = permissionEvaluatorService.canAdministerFolderPermissions(inactiveWm, wsFolder);
        assertThat(canAdmin).isFalse();
    }

    // =========================================================================
    // Collaboration Manager authority tests
    // =========================================================================

    @Test
    @DisplayName("Scenario CM-1: Active Collaboration Manager can administer collaboration root folder")
    void isCollaborationManagerForFolder_ActiveManager_Authorized() {
        UserEntity collabManager = UserEntity.builder()
                .id("collab-mgr").email("collabmgr@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity rootFolder = FolderEntity.builder()
                .id(1000L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build())
                .build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(10L)
                .ownerOrganization(org)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();
        CollaborationParticipantEntity managerParticipant = CollaborationParticipantEntity.builder()
                .id(1L)
                .collaboration(activeCollab)
                .user(collabManager)
                .collaborationRole(CollaborationRole.MANAGER)
                .build();

        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE))
                .thenReturn(List.of(activeCollab));
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(10L, "collab-mgr"))
                .thenReturn(Optional.of(managerParticipant));

        boolean result = permissionEvaluatorService.isCollaborationManagerForFolder(collabManager, rootFolder);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Scenario CM-2: Normal collaboration participant (MEMBER role) is denied folder administration")
    void isCollaborationManagerForFolder_NormalMember_Denied() {
        UserEntity member = UserEntity.builder()
                .id("collab-member").email("member@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity rootFolder = FolderEntity.builder()
                .id(1000L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build())
                .build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(10L).ownerOrganization(org).rootFolder(rootFolder).status(CollaborationStatus.ACTIVE)
                .build();
        CollaborationParticipantEntity memberParticipant = CollaborationParticipantEntity.builder()
                .id(2L).collaboration(activeCollab).user(member)
                .collaborationRole(CollaborationRole.MEMBER)
                .build();

        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE))
                .thenReturn(List.of(activeCollab));
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(10L, "collab-member"))
                .thenReturn(Optional.of(memberParticipant));

        boolean result = permissionEvaluatorService.isCollaborationManagerForFolder(member, rootFolder);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Scenario CM-3: Collaboration Manager of REVOKED collaboration is denied folder administration")
    void isCollaborationManagerForFolder_InactiveCollaboration_Denied() {
        UserEntity collabManager = UserEntity.builder()
                .id("collab-mgr").email("collabmgr@example.com").organization(org).role(Role.ROLE_USER)
                .build();
        FolderEntity rootFolder = FolderEntity.builder()
                .id(1001L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build())
                .build();

        // collaborationRepository.findByStatus(ACTIVE) returns empty — no active collaborations
        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE))
                .thenReturn(List.of());

        boolean result = permissionEvaluatorService.isCollaborationManagerForFolder(collabManager, rootFolder);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Scenario CM-4: Manager of Collaboration A cannot administer Collaboration B folder")
    void isCollaborationManagerForFolder_CrossCollaborationManager_Denied() {
        FolderEntity collabARoot = FolderEntity.builder()
                .id(2000L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build())
                .build();
        FolderEntity collabBRoot = FolderEntity.builder()
                .id(3000L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build())
                .build();

        UserEntity collabAManager = UserEntity.builder()
                .id("collab-a-mgr").email("a-mgr@example.com").organization(org).role(Role.ROLE_USER)
                .build();

        CollaborationEntity collabA = CollaborationEntity.builder()
                .id(20L).ownerOrganization(org).rootFolder(collabARoot).status(CollaborationStatus.ACTIVE)
                .build();
        CollaborationEntity collabB = CollaborationEntity.builder()
                .id(21L).ownerOrganization(org).rootFolder(collabBRoot).status(CollaborationStatus.ACTIVE)
                .build();

        CollaborationParticipantEntity participantInA = CollaborationParticipantEntity.builder()
                .id(3L).collaboration(collabA).user(collabAManager)
                .collaborationRole(CollaborationRole.MANAGER)
                .build();

        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE))
                .thenReturn(List.of(collabA, collabB));
        // collabA: user is manager, but target folder is collabBRoot — isSameOrAncestorFolder returns false for A→B
        // collabB: user is NOT a participant
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(21L, "collab-a-mgr"))
                .thenReturn(Optional.empty());

        // Target is collabBRoot — manager of collabA is denied
        boolean result = permissionEvaluatorService.isCollaborationManagerForFolder(collabAManager, collabBRoot);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Scenario CM-5: Collaboration Manager cannot administer unrelated organization resource")
    void isCollaborationManagerForFolder_UnrelatedOrgResource_Denied() {
        OrganizationEntity unrelatedOrg = OrganizationEntity.builder().orgCode("ORG-UNRELATED").name("Unrelated").build();
        FolderEntity unrelatedFolder = FolderEntity.builder()
                .id(9000L).ownerOrg(unrelatedOrg).createdBy(UserEntity.builder().id("other").build())
                .build();
        UserEntity collabManager = UserEntity.builder()
                .id("collab-mgr").email("collabmgr@example.com").organization(org).role(Role.ROLE_USER)
                .build();

        // No active collaboration has unrelatedFolder as its root folder
        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(10L).ownerOrganization(org)
                .rootFolder(FolderEntity.builder().id(1000L).ownerOrg(org).build())
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE))
                .thenReturn(List.of(activeCollab));

        // unrelatedFolder (id=9000) is not the same as or ancestor of collabRoot (id=1000)
        // so isSameOrAncestorFolder returns false; participant check never reached
        boolean result = permissionEvaluatorService.isCollaborationManagerForFolder(collabManager, unrelatedFolder);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Scenario CM-6: ROLE_ORG_MANAGER cannot administer collaboration permissions through org-manager status alone")
    void isCollaborationManagerForFolder_OrgManagerOnly_Denied() {
        UserEntity orgManager = UserEntity.builder()
                .id("org-mgr").email("orgmgr@example.com").organization(org).role(Role.ROLE_ORG_MANAGER)
                .build();
        FolderEntity rootFolder = FolderEntity.builder()
                .id(1000L).ownerOrg(org).createdBy(UserEntity.builder().id("other").build())
                .build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(10L)
                .ownerOrganization(org)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE))
                .thenReturn(List.of(activeCollab));
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(10L, "org-mgr"))
                .thenReturn(Optional.empty());

        boolean result = permissionEvaluatorService.isCollaborationManagerForFolder(orgManager, rootFolder);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("No Fallback: User with UserEntity.organization set but no ACTIVE UserOrganizationEntity membership is DENIED organization permission")
    void organizationPermission_WithoutActiveUserOrganizationEntity_Denied() {
        UserEntity userWithoutActiveOrgMember = UserEntity.builder()
                .id("no-active-user")
                .email("noactive@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();

        FolderEntity targetFolder = FolderEntity.builder()
                .id(9500L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        PermissionEntity orgPermission = PermissionEntity.builder()
                .id(9501L)
                .folder(targetFolder)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode(org.getOrgCode())
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("no-active-user", MembershipStatus.ACTIVE))
                .thenReturn(List.of());
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("noactive@example.com", null))
                .thenReturn(List.of(orgPermission));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(userWithoutActiveOrgMember, targetFolder);
        assertThat(level).isNull();
    }

    @Test
    @DisplayName("Organization Evaluation: VIEW grant downgrades effective access compared to EDIT")
    void organizationPermission_DowngradeFromEditToView_EvaluatesToView() {
        FolderEntity targetFolder = FolderEntity.builder()
                .id(9510L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        PermissionEntity orgPermission = PermissionEntity.builder()
                .id(9511L)
                .folder(targetFolder)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode(org.getOrgCode())
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .revoked(false)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus(user.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(user).organization(org).status(MembershipStatus.ACTIVE).build()));
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), org.getOrgCode()))
                .thenReturn(List.of(orgPermission));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, targetFolder);
        assertThat(level).isEqualTo(PermissionLevel.VIEW);
    }

    @Test
    @DisplayName("Organization Evaluation: Revoked organization grant removes effective access")
    void organizationPermission_RevokedGrant_DeniesAccess() {
        FolderEntity targetFolder = FolderEntity.builder()
                .id(9520L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        PermissionEntity revokedOrgPerm = PermissionEntity.builder()
                .id(9521L)
                .folder(targetFolder)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode(org.getOrgCode())
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .revoked(true)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus(user.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(user).organization(org).status(MembershipStatus.ACTIVE).build()));
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), org.getOrgCode()))
                .thenReturn(List.of(revokedOrgPerm));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(user, targetFolder);
        assertThat(level).isNull();
    }

    @Test
    @DisplayName("Organization Evaluation: EXCLUDE on child document blocks access despite organization ALLOW on folder")
    void organizationPermission_DocumentExclude_DeniesAccess() {
        FolderEntity parentFolder = FolderEntity.builder()
                .id(9530L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        DocumentEntity childDoc = DocumentEntity.builder()
                .id(9531L)
                .fileName("doc.pdf")
                .folder(parentFolder)
                .ownerOrg(org)
                .uploadedBy(UserEntity.builder().id("other").build())
                .build();

        PermissionEntity folderAllow = PermissionEntity.builder()
                .id(9532L)
                .folder(parentFolder)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode(org.getOrgCode())
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .revoked(false)
                .build();

        PermissionEntity docExclude = PermissionEntity.builder()
                .id(9533L)
                .document(childDoc)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode(org.getOrgCode())
                .permissionType(PermissionType.EXCLUDE)
                .permissionLevel(PermissionLevel.EDIT)
                .revoked(false)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus(user.getId(), MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(user).organization(org).status(MembershipStatus.ACTIVE).build()));
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), org.getOrgCode()))
                .thenReturn(List.of(folderAllow, docExclude));

        boolean canView = permissionEvaluatorService.canViewDocument(user, childDoc);
        assertThat(canView).isFalse();
    }

    @Test
    @DisplayName("GROUP evaluation: Permission for ARCHIVED group is DENIED")
    void groupPermission_ArchivedGroup_Denied() {
        GroupEntity archivedGroup = GroupEntity.builder()
                .id(55L)
                .name("Archived Team")
                .organization(org)
                .status(GroupStatus.ARCHIVED)
                .build();

        UserEntity userInArchivedGroup = UserEntity.builder()
                .id("archived-group-user")
                .email("archivedgroupuser@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .id(555L)
                .user(userInArchivedGroup)
                .group(archivedGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        FolderEntity targetFolder = FolderEntity.builder()
                .id(9600L)
                .ownerOrg(org)
                .createdBy(UserEntity.builder().id("other").build())
                .build();

        PermissionEntity groupPermission = PermissionEntity.builder()
                .id(9601L)
                .folder(targetFolder)
                .recipientType(RecipientType.GROUP)
                .targetGroup(archivedGroup)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        UserOrganizationEntity activeOrgMember = UserOrganizationEntity.builder()
                .user(userInArchivedGroup)
                .organization(org)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("archived-group-user", MembershipStatus.ACTIVE))
                .thenReturn(List.of(activeOrgMember));
        when(userGroupRepository.findByUser_Id("archived-group-user"))
                .thenReturn(List.of(userGroup));
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("archivedgroupuser@example.com", org.getOrgCode()))
                .thenReturn(List.of(groupPermission));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(userInArchivedGroup, targetFolder);
        assertThat(level).isNull();
    }

    @Test
    @DisplayName("Zero-Default Access: PermissionType.BOUNDARY on group grants zero content access to group member")
    void groupPermission_BoundaryOnly_GrantsZeroContentAccess() {
        FolderEntity targetFolder = FolderEntity.builder().id(777L).name("Test Folder").ownerOrg(org).build();

        GroupEntity activeGroup = GroupEntity.builder()
                .id(888L)
                .name("Alpha Team")
                .organization(org)
                .status(GroupStatus.ACTIVE)
                .build();

        UserEntity teamMember = UserEntity.builder()
                .id("team-member-1")
                .email("member@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .id(8881L)
                .user(teamMember)
                .group(activeGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        PermissionEntity boundaryPermission = PermissionEntity.builder()
                .id(8882L)
                .folder(targetFolder)
                .recipientType(RecipientType.GROUP)
                .targetGroup(activeGroup)
                .permissionType(PermissionType.BOUNDARY)
                .permissionLevel(PermissionLevel.EDIT)
                .revoked(false)
                .suspended(false)
                .build();

        UserOrganizationEntity activeOrgMember = UserOrganizationEntity.builder()
                .user(teamMember)
                .organization(org)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("team-member-1", MembershipStatus.ACTIVE))
                .thenReturn(List.of(activeOrgMember));
        when(userGroupRepository.findByUser_Id("team-member-1"))
                .thenReturn(List.of(userGroup));
        when(permissionRepository.findPermissionsForUserOrOrgOrGroups("member@example.com", org.getOrgCode(), List.of(888L)))
                .thenReturn(List.of(boundaryPermission));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(teamMember, targetFolder);
        assertThat(level).isNull();
    }

    @Test
    @DisplayName("Team-Linked Member Access: USER_ORGANIZATION_MEMBER with active group membership grants content access")
    void teamLinkedMemberAccess_ActiveMember_GrantsAccess() {
        FolderEntity targetFolder = FolderEntity.builder().id(777L).name("Test Folder").ownerOrg(org).build();

        GroupEntity activeGroup = GroupEntity.builder()
                .id(888L)
                .name("Alpha Team")
                .organization(org)
                .status(GroupStatus.ACTIVE)
                .build();

        UserEntity teamMember = UserEntity.builder()
                .id("team-member-1")
                .email("member@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();

        UserGroupEntity userGroup = UserGroupEntity.builder()
                .id(8881L)
                .user(teamMember)
                .group(activeGroup)
                .role(GroupMemberRole.MEMBER)
                .build();

        PermissionEntity memberDelegation = PermissionEntity.builder()
                .id(8883L)
                .folder(targetFolder)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetGroup(activeGroup)
                .targetUserEmail("member@example.com")
                .targetOrgCode(org.getOrgCode())
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .revoked(false)
                .suspended(false)
                .build();

        UserOrganizationEntity activeOrgMember = UserOrganizationEntity.builder()
                .user(teamMember)
                .organization(org)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("team-member-1", MembershipStatus.ACTIVE))
                .thenReturn(List.of(activeOrgMember));
        when(userGroupRepository.findByUser_Id("team-member-1"))
                .thenReturn(List.of(userGroup));
        when(permissionRepository.findPermissionsForUserOrOrgOrGroups("member@example.com", org.getOrgCode(), List.of(888L)))
                .thenReturn(List.of(memberDelegation));

        PermissionLevel level = permissionEvaluatorService.resolveMaxPermissionLevel(teamMember, targetFolder);
        assertThat(level).isEqualTo(PermissionLevel.DOWNLOAD);
    }

    @Test
    @DisplayName("Phase 3B.2: resolveDelegationCeiling verification across administrative and uploader roles")
    void resolveDelegationCeiling_Verification() {
        // 1. Super Admin
        UserEntity superUser = UserEntity.builder().id("super-user").role(Role.ROLE_SUPER_ADMIN).build();
        assertThat(permissionEvaluatorService.resolveDelegationCeiling(superUser, null, orgDocument))
                .isEqualTo(PermissionLevel.EDIT);

        // 2. Org Admin
        UserEntity orgAdmin = UserEntity.builder().id("org-admin").role(Role.ROLE_USER).build();
        UserOrganizationEntity adminOrgMember = UserOrganizationEntity.builder()
                .user(orgAdmin).organization(org).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("org-admin", "ORG1"))
                .thenReturn(Optional.of(adminOrgMember));
        assertThat(permissionEvaluatorService.resolveDelegationCeiling(orgAdmin, null, orgDocument))
                .isEqualTo(PermissionLevel.EDIT);

        // 3. Personal Resource Owner
        assertThat(permissionEvaluatorService.resolveDelegationCeiling(user, null, personalDocument))
                .isEqualTo(PermissionLevel.EDIT);

        // 4. Standard Org Member (no admin, not uploader)
        UserOrganizationEntity standardMember = UserOrganizationEntity.builder()
                .user(user).organization(org).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-1", "ORG1"))
                .thenReturn(Optional.of(standardMember));
        assertThat(permissionEvaluatorService.resolveDelegationCeiling(user, null, orgDocument))
                .isNull();

        // 5. Org Resource Creator / Uploader
        UserEntity uploader = UserEntity.builder().id("uploader-user").email("uploader@example.com").role(Role.ROLE_USER).build();
        DocumentEntity docUploaded = DocumentEntity.builder()
                .id(777L)
                .fileName("doc.pdf")
                .ownerOrg(org)
                .uploadedBy(uploader)
                .build();

        // 5a. Inactive membership -> null
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("uploader-user", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(false);
        assertThat(permissionEvaluatorService.resolveDelegationCeiling(uploader, null, docUploaded))
                .isNull();

        // 5b. Active membership but effective access is DOWNLOAD -> null
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("uploader-user", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByUser_IdAndStatus("uploader-user", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(uploader).organization(org).status(MembershipStatus.ACTIVE).build()));

        PermissionEntity downloadPerm = PermissionEntity.builder()
                .id(881L)
                .document(docUploaded)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("uploader@example.com")
                .targetOrgCode("ORG1")
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("uploader@example.com", "ORG1"))
                .thenReturn(List.of(downloadPerm));

        assertThat(permissionEvaluatorService.resolveDelegationCeiling(uploader, null, docUploaded))
                .isNull();

        // 5c. Active membership and effective access is EDIT -> EDIT
        PermissionEntity editPerm = PermissionEntity.builder()
                .id(882L)
                .document(docUploaded)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("uploader@example.com")
                .targetOrgCode("ORG1")
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("uploader@example.com", "ORG1"))
                .thenReturn(List.of(editPerm));

        assertThat(permissionEvaluatorService.resolveDelegationCeiling(uploader, null, docUploaded))
                .isEqualTo(PermissionLevel.EDIT);

        // 5d. Effective EDIT but EXCLUDE applies -> null
        PermissionEntity excludePerm = PermissionEntity.builder()
                .id(883L)
                .document(docUploaded)
                .permissionType(PermissionType.EXCLUDE)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("uploader@example.com")
                .targetOrgCode("ORG1")
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("uploader@example.com", "ORG1"))
                .thenReturn(List.of(editPerm, excludePerm));

        assertThat(permissionEvaluatorService.resolveDelegationCeiling(uploader, null, docUploaded))
                .isNull();
    }

    @Test
    @DisplayName("Scenario A: External user + explicit USER_PERSONAL VIEW on org document -> view allowed, download/edit denied")
    void externalUser_ExplicitViewOnOrgDocument_Allowed() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity viewPerm = PermissionEntity.builder()
                .id(501L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(viewPerm));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario B: External user + explicit USER_PERSONAL DOWNLOAD on org document -> view/download allowed, edit denied")
    void externalUser_ExplicitDownloadOnOrgDocument_Allowed() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity dlPerm = PermissionEntity.builder()
                .id(502L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(dlPerm));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario C: External user + explicit USER_PERSONAL EDIT on org document -> view/download/edit allowed")
    void externalUser_ExplicitEditOnOrgDocument_Allowed() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity editPerm = PermissionEntity.builder()
                .id(503L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(editPerm));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isTrue();
    }

    @Test
    @DisplayName("Scenario D: External user + explicit USER_PERSONAL VIEW on org folder -> view allowed, download/edit denied")
    void externalUser_ExplicitViewOnOrgFolder_Allowed() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        FolderEntity orgFolder = FolderEntity.builder().id(300L).name("OrgFolder").ownerOrg(org).build();
        PermissionEntity viewPerm = PermissionEntity.builder()
                .id(504L)
                .folder(orgFolder)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(viewPerm));

        assertThat(permissionEvaluatorService.canViewFolder(externalUser, orgFolder)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadFolder(externalUser, orgFolder)).isFalse();
        assertThat(permissionEvaluatorService.canEditFolder(externalUser, orgFolder)).isFalse();
    }

    @Test
    @DisplayName("Scenario E: External user + explicit USER_PERSONAL DOWNLOAD on shared folder -> child folder & doc inherited")
    void externalUser_InheritedAccessOnChildResources_Allowed() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        FolderEntity parentFolder = FolderEntity.builder().id(310L).name("ParentFolder").ownerOrg(org).build();
        FolderEntity subFolder = FolderEntity.builder().id(311L).name("SubFolder").ownerOrg(org).parentFolder(parentFolder).build();
        DocumentEntity childDoc = DocumentEntity.builder().id(210L).fileName("child.pdf").ownerOrg(org).folder(parentFolder).build();
        DocumentEntity nestedDoc = DocumentEntity.builder().id(211L).fileName("nested.pdf").ownerOrg(org).folder(subFolder).build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(505L)
                .folder(parentFolder)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(folderPerm));

        assertThat(permissionEvaluatorService.canViewFolder(externalUser, subFolder)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadFolder(externalUser, subFolder)).isTrue();
        assertThat(permissionEvaluatorService.canEditFolder(externalUser, subFolder)).isFalse();

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, childDoc)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, childDoc)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, childDoc)).isFalse();

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, nestedDoc)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, nestedDoc)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, nestedDoc)).isFalse();
    }

    @Test
    @DisplayName("Scenario F: Non-member with no explicit external permission -> denied on all operations")
    void nonMember_NoExplicitPermission_Denied() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of());

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();

        FolderEntity orgFolder = FolderEntity.builder().id(300L).name("OrgFolder").ownerOrg(org).build();
        assertThat(permissionEvaluatorService.canViewFolder(externalUser, orgFolder)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadFolder(externalUser, orgFolder)).isFalse();
        assertThat(permissionEvaluatorService.canEditFolder(externalUser, orgFolder)).isFalse();
    }

    @Test
    @DisplayName("Scenario G: User whose external permission is revoked -> denied")
    void externalUser_RevokedPermission_Denied() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity revokedPerm = PermissionEntity.builder()
                .id(506L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .revoked(true)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(revokedPerm));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario H: User whose external permission is suspended -> denied")
    void externalUser_SuspendedPermission_Denied() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity suspendedPerm = PermissionEntity.builder()
                .id(507L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .suspended(true)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(suspendedPerm));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario I: User whose external permission is expired -> denied")
    void externalUser_ExpiredPermission_Denied() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity expiredPerm = PermissionEntity.builder()
                .id(508L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(expiredPerm));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario J: Existing active organization member with explicit grant behaves normally")
    void activeOrgMember_ExplicitGrant_BehavesNormally() {
        PermissionEntity memberPerm = PermissionEntity.builder()
                .id(509L)
                .document(orgDocument)
                .targetUserEmail(user.getEmail())
                .targetOrgCode("ORG1")
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), "ORG1"))
                .thenReturn(List.of(memberPerm));

        assertThat(permissionEvaluatorService.canViewDocument(user, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canDownloadDocument(user, orgDocument)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(user, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario K: Workspace Manager for folder hierarchy administers without automatic EDIT content escalation")
    void workspaceManager_AdministersWithoutAutomaticEditContentEscalation() {
        FolderEntity wsFolder = FolderEntity.builder().id(320L).name("WorkspaceFolder").ownerOrg(org).build();
        when(workspaceManagerService.isWorkspaceManagerForFolder(user, wsFolder)).thenReturn(true);

        // Admin authority granted
        assertThat(permissionEvaluatorService.canAdministerFolderPermissions(user, wsFolder)).isTrue();

        // Content access without explicit grant remains denied
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode(user.getEmail(), "ORG1"))
                .thenReturn(List.of());
        assertThat(permissionEvaluatorService.canEditFolder(user, wsFolder)).isFalse();
    }

    @Test
    @DisplayName("Scenario L: Collaboration Manager authority remains scoped to collaboration root")
    void collaborationManager_AuthorityRemainsScoped() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        FolderEntity collabRoot = FolderEntity.builder().id(330L).name("CollabRoot").ownerOrg(org).build();
        FolderEntity unrelated = FolderEntity.builder().id(331L).name("Unrelated").ownerOrg(org).build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(30L).rootFolder(collabRoot).status(CollaborationStatus.ACTIVE).ownerOrganization(org).build();
        CollaborationParticipantEntity mgr = CollaborationParticipantEntity.builder()
                .collaborationRole(CollaborationRole.MANAGER).build();

        when(collaborationRepository.findByStatus(CollaborationStatus.ACTIVE)).thenReturn(List.of(activeCollab));
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(30L, "user-ext"))
                .thenReturn(Optional.of(mgr));

        assertThat(permissionEvaluatorService.isCollaborationManagerForFolder(externalUser, collabRoot)).isTrue();
        assertThat(permissionEvaluatorService.isCollaborationManagerForFolder(externalUser, unrelated)).isFalse();
    }

    @Test
    @DisplayName("Scenario M: Document exclusion still overrides inherited external folder permission")
    void externalUser_DocumentExclusion_OverridesInheritedFolderPermission() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        FolderEntity parentFolder = FolderEntity.builder().id(340L).name("ParentFolder").ownerOrg(org).build();
        DocumentEntity excludedDoc = DocumentEntity.builder().id(240L).fileName("excluded.pdf").ownerOrg(org).folder(parentFolder).build();

        PermissionEntity allowFolder = PermissionEntity.builder()
                .id(510L)
                .folder(parentFolder)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        PermissionEntity excludeDoc = PermissionEntity.builder()
                .id(511L)
                .document(excludedDoc)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.EXCLUDE)
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(allowFolder, excludeDoc));

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, excludedDoc)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, excludedDoc)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, excludedDoc)).isFalse();
    }

    @Test
    @DisplayName("Scenario N: Trust relationship by itself does NOT grant resource access without explicit permission")
    void trustedOrgUser_WithoutExplicitPermission_Denied() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of());

        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
    }

    @Test
    @DisplayName("Scenario O: External user authorized for Doc X cannot access unrelated Doc Y")
    void externalUser_AuthorizedForDocX_CannotAccessUnrelatedDocY() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        DocumentEntity docX = DocumentEntity.builder().id(250L).fileName("DocX.pdf").ownerOrg(org).build();
        DocumentEntity docY = DocumentEntity.builder().id(251L).fileName("DocY.pdf").ownerOrg(org).build();

        PermissionEntity permX = PermissionEntity.builder()
                .id(512L)
                .document(docX)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(permX));

        // Allowed on Doc X
        assertThat(permissionEvaluatorService.canViewDocument(externalUser, docX)).isTrue();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, docX)).isTrue();

        // Denied on Doc Y
        assertThat(permissionEvaluatorService.canViewDocument(externalUser, docY)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, docY)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, docY)).isFalse();
    }

    @Test
    @DisplayName("Scenario P: Share-link permission must NOT satisfy external direct-sharing organization-membership bypass")
    void shareLinkPermission_DoesNotSatisfyExternalDirectSharingBypass() {
        UserEntity externalUser = UserEntity.builder().id("user-ext").email("external@example.com").role(Role.ROLE_USER).build();
        PermissionEntity shareLinkPerm = PermissionEntity.builder()
                .id(513L)
                .document(orgDocument)
                .targetUserEmail("external@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .shareLinkToken("share-token-xyz-123")
                .build();

        when(permissionRepository.findByTargetUserEmailOrTargetOrgCode("external@example.com", null))
                .thenReturn(List.of(shareLinkPerm));

        // Normal internal document authorization API must remain denied
        assertThat(permissionEvaluatorService.canViewDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canDownloadDocument(externalUser, orgDocument)).isFalse();
        assertThat(permissionEvaluatorService.canEditDocument(externalUser, orgDocument)).isFalse();
    }
}
