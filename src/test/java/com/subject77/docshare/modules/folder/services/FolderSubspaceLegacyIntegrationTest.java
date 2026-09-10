package com.subject77.docshare.modules.folder.services;

import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.services.OrganizationService;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class FolderSubspaceLegacyIntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.audit.services.AuditLogService auditLogService;

    private UserEntity orgAdmin;
    private UserEntity superAdmin;
    private UserEntity orgManager;
    private UserEntity memberWithAccess;
    private UserEntity memberWithoutAccess;
    private UserEntity otherOrgUser;

    private String orgCode;
    private OrganizationEntity organization;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        orgAdmin = userRepository.save(UserEntity.builder()
                .id("admin-" + uid)
                .email("admin_" + uid + "@test.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        superAdmin = userRepository.save(UserEntity.builder()
                .id("super-" + uid)
                .email("super_" + uid + "@test.com")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.ROLE_SUPER_ADMIN)
                .build());

        orgManager = userRepository.save(UserEntity.builder()
                .id("mgr-" + uid)
                .email("mgr_" + uid + "@test.com")
                .firstName("Manager")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        memberWithAccess = userRepository.save(UserEntity.builder()
                .id("member1-" + uid)
                .email("member1_" + uid + "@test.com")
                .firstName("Member")
                .lastName("One")
                .role(Role.ROLE_USER)
                .build());

        memberWithoutAccess = userRepository.save(UserEntity.builder()
                .id("member2-" + uid)
                .email("member2_" + uid + "@test.com")
                .firstName("Member")
                .lastName("Two")
                .role(Role.ROLE_USER)
                .build());

        otherOrgUser = userRepository.save(UserEntity.builder()
                .id("other-" + uid)
                .email("other_" + uid + "@test.com")
                .firstName("Other")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        // Provision main test organization
        orgCode = "ORG_" + uid.toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCode)
                .name("Legacy Test Org " + uid)
                .build(), orgAdmin);

        organization = organizationRepository.findById(orgCode).orElseThrow();

        // Add memberships to main test organization
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgManager)
                .organization(organization)
                .role(Role.ROLE_ORG_MANAGER)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(memberWithAccess)
                .organization(organization)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(memberWithoutAccess)
                .organization(organization)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(superAdmin)
                .organization(organization)
                .role(Role.ROLE_SUPER_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        // Provision other organization for cross-org testing
        String otherOrgCode = "OTH_" + uid.toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(otherOrgCode)
                .name("Other Test Org " + uid)
                .build(), otherOrgUser);
    }

    private Optional<ProjectWorkspaceEntity> findWorkspaceByRootFolderId(Long rootFolderId) {
        return projectWorkspaceRepository.findAll().stream()
                .filter(w -> w.getRootFolder() != null && w.getRootFolder().getId().equals(rootFolderId))
                .findFirst();
    }

    // =========================================================================
    // POST /subspaces Tests (Scenarios 9-16)
    // =========================================================================

    @Test
    @DisplayName("9. ORG_ADMIN successfully creates canonical workspace via legacy createSubspace")
    void test09_OrgAdminSuccessfullyCreatesCanonicalWorkspace() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Finance Subspace")
                .build();

        FolderResponseDto response = folderService.createSubspace(orgCode, request, orgAdmin);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Finance Subspace");
        assertThat(response.getParentFolderId()).isNull();
        assertThat(response.getOwnerOrgCode()).isEqualTo(orgCode);
        assertThat(response.getSubspaceType()).isEqualTo(SubspaceType.ORGANIZATION_GENERAL);
        assertThat(response.isDefaultSubspace()).isFalse();

        // 13. created root is attached to ProjectWorkspaceEntity
        Optional<ProjectWorkspaceEntity> wsOpt = findWorkspaceByRootFolderId(response.getId());
        assertThat(wsOpt).isPresent();
        ProjectWorkspaceEntity ws = wsOpt.get();
        assertThat(ws.getName()).isEqualTo("Finance Subspace");
        assertThat(ws.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);
        assertThat(ws.getRootFolder().getId()).isEqualTo(response.getId());
    }

    @Test
    @DisplayName("10. SUPER_ADMIN successfully creates canonical workspace via legacy createSubspace")
    void test10_SuperAdminSuccessfullyCreatesCanonicalWorkspace() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Executive Subspace")
                .build();

        FolderResponseDto response = folderService.createSubspace(orgCode, request, superAdmin);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Executive Subspace");
        assertThat(response.getParentFolderId()).isNull();

        Optional<ProjectWorkspaceEntity> wsOpt = findWorkspaceByRootFolderId(response.getId());
        assertThat(wsOpt).isPresent();
    }

    @Test
    @DisplayName("11. ORG_MANAGER can create subspace/workspace via legacy createSubspace")
    void test11_OrgManagerCanCreateSubspace() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Manager Subspace")
                .build();

        FolderResponseDto response = folderService.createSubspace(orgCode, request, orgManager);
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("Manager Subspace");

        Optional<ProjectWorkspaceEntity> wsOpt = findWorkspaceByRootFolderId(response.getId());
        assertThat(wsOpt).isPresent();
    }

    @Test
    @DisplayName("12. normal USER receives 403 AccessDeniedException on legacy createSubspace")
    void test12_NormalUserReceives403() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("User Subspace")
                .build();

        assertThatThrownBy(() -> folderService.createSubspace(orgCode, request, memberWithoutAccess))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_WORKSPACES_MANAGE");
    }

    @Test
    @DisplayName("14. No standalone root remains after legacy createSubspace")
    void test14_NoStandaloneRootRemains() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Audited Subspace")
                .build();

        folderService.createSubspace(orgCode, request, orgAdmin);

        List<FolderEntity> roots = folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode);
        assertThat(roots).isNotEmpty();
        for (FolderEntity root : roots) {
            assertThat(projectWorkspaceRepository.existsByRootFolder_Id(root.getId()))
                    .as("Root folder ID " + root.getId() + " must have a corresponding ProjectWorkspaceEntity")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("15. Name collision handled correctly on createSubspace")
    void test15_NameCollisionHandledCorrectly() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Duplicate Subspace")
                .build();

        folderService.createSubspace(orgCode, request, orgAdmin);

        assertThatThrownBy(() -> folderService.createSubspace(orgCode, request, orgAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists in organization: " + orgCode);
    }

    @Test
    @DisplayName("16. Default-root semantics remain correct")
    void test16_DefaultRootSemanticsRemainCorrect() {
        // The provisioned "General" workspace root has isDefaultSubspace = true
        List<FolderEntity> roots = folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode);
        FolderEntity generalRoot = roots.stream()
                .filter(FolderEntity::isDefaultSubspace)
                .findFirst()
                .orElseThrow();
        assertThat(generalRoot.getName()).isEqualTo("General");

        // Newly created subspace has isDefaultSubspace = false
        FolderResponseDto newSubspace = folderService.createSubspace(orgCode,
                FolderRequestDto.builder().name("Custom Subspace").build(), orgAdmin);
        assertThat(newSubspace.isDefaultSubspace()).isFalse();

        FolderEntity customRoot = folderRepository.findById(newSubspace.getId()).orElseThrow();
        assertThat(customRoot.isDefaultSubspace()).isFalse();
    }

    // =========================================================================
    // POST /folders Tests (Scenarios 17-19)
    // =========================================================================

    @Test
    @DisplayName("17. POST /folders with parentFolderId != null still creates child folder")
    void test17_CreateFolder_ChildFolderSuccess() {
        List<FolderEntity> roots = folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode);
        FolderEntity generalRoot = roots.get(0);

        FolderRequestDto request = FolderRequestDto.builder()
                .name("Finance Reports")
                .parentFolderId(generalRoot.getId())
                .build();

        FolderResponseDto response = folderService.createFolder(orgCode, request, orgAdmin);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Finance Reports");
        assertThat(response.getParentFolderId()).isEqualTo(generalRoot.getId());
        assertThat(response.getOwnerOrgCode()).isEqualTo(orgCode);

        FolderEntity child = folderRepository.findById(response.getId()).orElseThrow();
        assertThat(child.getParentFolder()).isNotNull();
        assertThat(child.getParentFolder().getId()).isEqualTo(generalRoot.getId());
        assertThat(projectWorkspaceRepository.existsByRootFolder_Id(child.getId())).isFalse();
    }

    @Test
    @DisplayName("18. POST /folders with parentFolderId == null creates canonical workspace")
    void test18_CreateFolder_NullParentCreatesCanonicalWorkspace() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Root Via Folder API")
                .parentFolderId(null)
                .build();

        FolderResponseDto response = folderService.createFolder(orgCode, request, orgAdmin);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getName()).isEqualTo("Root Via Folder API");
        assertThat(response.getParentFolderId()).isNull();

        Optional<ProjectWorkspaceEntity> wsOpt = findWorkspaceByRootFolderId(response.getId());
        assertThat(wsOpt).isPresent();
        assertThat(wsOpt.get().getName()).isEqualTo("Root Via Folder API");
    }

    @Test
    @DisplayName("19. No unattached organizational root is produced by createFolder")
    void test19_NoUnattachedOrganizationalRootProduced() {
        folderService.createFolder(orgCode, FolderRequestDto.builder()
                .name("Workspace A")
                .parentFolderId(null)
                .build(), orgAdmin);

        List<FolderEntity> allRoots = folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode);
        for (FolderEntity r : allRoots) {
            assertThat(projectWorkspaceRepository.existsByRootFolder_Id(r.getId()))
                    .as("Root " + r.getName() + " must be associated with a ProjectWorkspaceEntity")
                    .isTrue();
        }
    }

    // =========================================================================
    // GET /subspaces Tests (Scenarios 1-8)
    // =========================================================================

    @Test
    @DisplayName("1. ORG_ADMIN sees all ACTIVE workspaces in organization")
    void test01_OrgAdminSeesAllActiveWorkspaces() {
        folderService.createSubspace(orgCode, FolderRequestDto.builder().name("WS 1").build(), orgAdmin);
        folderService.createSubspace(orgCode, FolderRequestDto.builder().name("WS 2").build(), orgAdmin);

        List<FolderResponseDto> subspaces = folderService.getOrganizationSubspaces(orgCode, orgAdmin);

        // General + WS 1 + WS 2 = 3
        assertThat(subspaces).hasSize(3);
        assertThat(subspaces).extracting(FolderResponseDto::getName)
                .containsExactlyInAnyOrder("General", "WS 1", "WS 2");
    }

    @Test
    @DisplayName("2. SUPER_ADMIN sees appropriate workspaces")
    void test02_SuperAdminSeesAppropriateWorkspaces() {
        folderService.createSubspace(orgCode, FolderRequestDto.builder().name("WS Super").build(), orgAdmin);

        List<FolderResponseDto> subspaces = folderService.getOrganizationSubspaces(orgCode, superAdmin);

        assertThat(subspaces).extracting(FolderResponseDto::getName)
                .contains("General", "WS Super");
    }

    @Test
    @DisplayName("3 & 4. Member with VIEW access sees authorized workspace; member without access does NOT see workspace")
    void test03_04_MemberVisibilityBasedOnAuthorization() {
        FolderResponseDto created = folderService.createSubspace(orgCode,
                FolderRequestDto.builder().name("Secret Projects").build(), orgAdmin);

        // Member without access sees 0 workspaces (no permission granted yet)
        List<FolderResponseDto> beforePerm = folderService.getOrganizationSubspaces(orgCode, memberWithAccess);
        assertThat(beforePerm).isEmpty();

        // Grant VIEW permission on "Secret Projects" root folder to memberWithAccess
        ProjectWorkspaceEntity ws = findWorkspaceByRootFolderId(created.getId()).orElseThrow();
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(memberWithAccess.getEmail())
                .targetOrgCode(orgCode)
                .folder(ws.getRootFolder())
                .workspaceId(ws.getId())
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .grantedBy(orgAdmin)
                .createdAt(java.time.Instant.now())
                .build());

        // Member with access now sees only "Secret Projects"
        List<FolderResponseDto> afterPerm = folderService.getOrganizationSubspaces(orgCode, memberWithAccess);
        assertThat(afterPerm).hasSize(1);
        assertThat(afterPerm.get(0).getName()).isEqualTo("Secret Projects");

        // Member WITHOUT access still sees 0 workspaces
        List<FolderResponseDto> unauthorizedUserView = folderService.getOrganizationSubspaces(orgCode, memberWithoutAccess);
        assertThat(unauthorizedUserView).isEmpty();
    }

    @Test
    @DisplayName("5. Archived workspace is excluded from GET /subspaces")
    void test05_ArchivedWorkspaceIsExcluded() {
        FolderResponseDto wsDto = folderService.createSubspace(orgCode,
                FolderRequestDto.builder().name("To Archive").build(), orgAdmin);

        ProjectWorkspaceEntity ws = findWorkspaceByRootFolderId(wsDto.getId()).orElseThrow();

        // Initially visible
        List<FolderResponseDto> beforeArchive = folderService.getOrganizationSubspaces(orgCode, orgAdmin);
        assertThat(beforeArchive).extracting(FolderResponseDto::getName).contains("To Archive");

        // Archive
        projectWorkspaceService.archiveWorkspace(ws.getId(), orgAdmin);

        // Excluded after archive
        List<FolderResponseDto> afterArchive = folderService.getOrganizationSubspaces(orgCode, orgAdmin);
        assertThat(afterArchive).extracting(FolderResponseDto::getName).doesNotContain("To Archive");
    }

    @Test
    @DisplayName("6. Cross-organization request is rejected")
    void test06_CrossOrgRequestIsRejected() {
        assertThatThrownBy(() -> folderService.getOrganizationSubspaces(orgCode, otherOrgUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not a member of organization: " + orgCode);
    }

    @Test
    @DisplayName("7. No metadata leakage for unauthorized user")
    void test07_NoMetadataLeakage() {
        folderService.createSubspace(orgCode, FolderRequestDto.builder().name("Hidden WS").build(), orgAdmin);

        List<FolderResponseDto> visible = folderService.getOrganizationSubspaces(orgCode, memberWithoutAccess);
        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("8 & 22. FolderResponseDto compatibility preserved and no sensitive data exposed")
    void test08_22_FolderResponseDtoCompatibilityPreserved() {
        FolderResponseDto created = folderService.createSubspace(orgCode,
                FolderRequestDto.builder().name("Compatibility Test").build(), orgAdmin);

        List<FolderResponseDto> list = folderService.getOrganizationSubspaces(orgCode, orgAdmin);
        FolderResponseDto found = list.stream()
                .filter(f -> f.getId().equals(created.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getName()).isEqualTo("Compatibility Test");
        assertThat(found.getParentFolderId()).isNull();
        assertThat(found.getOwnerOrgCode()).isEqualTo(orgCode);
        assertThat(found.getCreatedByUserId()).isEqualTo(orgAdmin.getId());
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getSubspaceType()).isEqualTo(SubspaceType.ORGANIZATION_GENERAL);
        assertThat(found.isDefaultSubspace()).isFalse();
    }

    // =========================================================================
    // Lifecycle Tests (Scenarios 20-21)
    // =========================================================================

    @Test
    @DisplayName("20 & 21. Archived workspace disappears from /subspaces; restored workspace reappears")
    void test20_21_LifecycleArchiveAndRestore() {
        FolderResponseDto created = folderService.createSubspace(orgCode,
                FolderRequestDto.builder().name("Lifecycle Subspace").build(), orgAdmin);

        ProjectWorkspaceEntity ws = findWorkspaceByRootFolderId(created.getId()).orElseThrow();

        // Verify initially present
        assertThat(folderService.getOrganizationSubspaces(orgCode, orgAdmin))
                .extracting(FolderResponseDto::getName)
                .contains("Lifecycle Subspace");

        // Archive: disappears
        projectWorkspaceService.archiveWorkspace(ws.getId(), orgAdmin);
        assertThat(folderService.getOrganizationSubspaces(orgCode, orgAdmin))
                .extracting(FolderResponseDto::getName)
                .doesNotContain("Lifecycle Subspace");

        // Restore: reappears
        projectWorkspaceService.restoreWorkspace(ws.getId(), orgAdmin);
        assertThat(folderService.getOrganizationSubspaces(orgCode, orgAdmin))
                .extracting(FolderResponseDto::getName)
                .contains("Lifecycle Subspace");
    }
}
