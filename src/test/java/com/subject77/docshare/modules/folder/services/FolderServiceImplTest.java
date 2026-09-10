package com.subject77.docshare.modules.folder.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.dto.FolderContentResponseDto;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FolderServiceImplTest {

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StorageService storageService;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private ProjectWorkspaceService projectWorkspaceService;

    @Mock
    private com.subject77.docshare.modules.policy.services.WorkspacePolicyService workspacePolicyService;

    @Mock
    private com.subject77.docshare.modules.permission.repository.AccessRequestRepository accessRequestRepository;

    @InjectMocks
    private FolderServiceImpl folderService;

    private UserEntity managerUser;
    private UserEntity regularUser;
    private OrganizationEntity testOrg;
    private FolderEntity subspaceRoot;

    @BeforeEach
    void setUp() {
        managerUser = UserEntity.builder()
                .id("mgr-123")
                .email("mgr@acme.com")
                .role(Role.ROLE_USER)
                .build();

        regularUser = UserEntity.builder()
                .id("user-456")
                .email("user@acme.com")
                .role(Role.ROLE_USER)
                .build();

        testOrg = OrganizationEntity.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .build();

        subspaceRoot = FolderEntity.builder()
                .id(1L)
                .name("Finance Subspace")
                .parentFolder(null)
                .ownerOrg(testOrg)
                .createdBy(managerUser)
                .build();
    }

    @Test
    @DisplayName("Should create canonical workspace when createSubspace is called")
    void createSubspace_Success() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Finance Subspace")
                .build();

        ProjectWorkspaceResponseDto wsResponse = ProjectWorkspaceResponseDto.builder()
                .id(100L)
                .name("Finance Subspace")
                .orgCode("ACME")
                .rootFolderId(1L)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build();

        when(projectWorkspaceService.createWorkspace(any(CreateProjectWorkspaceRequestDto.class), eq(managerUser)))
                .thenReturn(wsResponse);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));

        FolderResponseDto response = folderService.createSubspace("ACME", request, managerUser);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Finance Subspace");
        assertThat(response.getOwnerOrgCode()).isEqualTo("ACME");
        assertThat(response.getParentFolderId()).isNull();
        assertThat(response.getSubspaceType()).isEqualTo(SubspaceType.ORGANIZATION_GENERAL);

        verify(projectWorkspaceService).createWorkspace(
                argThat(req -> req.getOrgCode().equals("ACME") && req.getName().equals("Finance Subspace")),
                eq(managerUser));
    }

    @Test
    @DisplayName("Should propagate AccessDeniedException when projectWorkspaceService denies workspace creation")
    void createSubspace_DeniedWhenUnauthorized() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Finance Subspace")
                .build();

        when(projectWorkspaceService.createWorkspace(any(CreateProjectWorkspaceRequestDto.class), eq(regularUser)))
                .thenThrow(new AccessDeniedException("User does not have admin permissions in organization: ACME"));

        assertThatThrownBy(() -> folderService.createSubspace("ACME", request, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have admin permissions in organization: ACME");
    }

    @Test
    @DisplayName("getOrganizationSubspaces should return only ACTIVE workspaces mapped to FolderResponseDto")
    void getOrganizationSubspaces_ReturnsActiveWorkspacesOnly() {
        ProjectWorkspaceResponseDto activeWs = ProjectWorkspaceResponseDto.builder()
                .id(10L)
                .name("Active WS")
                .orgCode("ACME")
                .rootFolderId(1L)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build();

        ProjectWorkspaceResponseDto archivedWs = ProjectWorkspaceResponseDto.builder()
                .id(20L)
                .name("Archived WS")
                .orgCode("ACME")
                .rootFolderId(2L)
                .status(ProjectWorkspaceStatus.ARCHIVED)
                .build();

        when(projectWorkspaceService.getWorkspacesForOrganization("ACME", regularUser))
                .thenReturn(List.of(activeWs, archivedWs));
        when(folderRepository.findAllById(List.of(1L))).thenReturn(List.of(subspaceRoot));

        List<FolderResponseDto> result = folderService.getOrganizationSubspaces("ACME", regularUser);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("Active WS");
        assertThat(result.get(0).getParentFolderId()).isNull();
        assertThat(result.get(0).getOwnerOrgCode()).isEqualTo("ACME");
    }

    @Test
    @DisplayName("createFolder with parentFolderId == null should delegate to createSubspace / projectWorkspaceService")
    void createFolder_RootDelegatesToWorkspaceCreation() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("New Workspace via Folder API")
                .parentFolderId(null)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("mgr-123", "ACME")).thenReturn(true);

        ProjectWorkspaceResponseDto wsResponse = ProjectWorkspaceResponseDto.builder()
                .id(100L)
                .name("New Workspace via Folder API")
                .orgCode("ACME")
                .rootFolderId(1L)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build();

        when(projectWorkspaceService.createWorkspace(any(CreateProjectWorkspaceRequestDto.class), eq(managerUser)))
                .thenReturn(wsResponse);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));

        FolderResponseDto response = folderService.createFolder("ACME", request, managerUser);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("New Workspace via Folder API");
        verify(projectWorkspaceService).createWorkspace(
                argThat(req -> req.getOrgCode().equals("ACME") && req.getName().equals("New Workspace via Folder API")),
                eq(managerUser));
    }

    @Test
    @DisplayName("Should fetch contents of a folder (subfolders and documents)")
    void getFolderContents_Success() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(permissionEvaluatorService.canViewFolder(regularUser, subspaceRoot)).thenReturn(true);
        when(folderRepository.findByParentFolder_Id(1L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(1L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto contents = folderService.getFolderContents(1L, regularUser);

        assertThat(contents.getCurrentFolder().getId()).isEqualTo(1L);
        assertThat(contents.getSubFolders()).isEmpty();
        assertThat(contents.getDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when getting folder contents without VIEW permission")
    void getFolderContents_DeniedWithoutViewPermission() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(permissionEvaluatorService.canViewFolder(regularUser, subspaceRoot)).thenReturn(false);

        assertThatThrownBy(() -> folderService.getFolderContents(1L, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to view folder");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when creating subfolder without EDIT permission on parent")
    void createFolder_DeniedWithoutEditPermission() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("New Folder")
                .parentFolderId(1L)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-456", "ACME"))
                .thenReturn(true);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(permissionEvaluatorService.canEditFolder(regularUser, subspaceRoot)).thenReturn(false);

        assertThatThrownBy(() -> folderService.createFolder("ACME", request, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to create a subfolder");
    }

    @Test
    @DisplayName("Should create child folder when parentFolderId is provided and user has EDIT permission")
    void createFolder_ChildFolder_Success() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Sub-folder")
                .parentFolderId(1L)
                .build();

        FolderEntity childFolder = FolderEntity.builder()
                .id(2L)
                .name("Sub-folder")
                .parentFolder(subspaceRoot)
                .ownerOrg(testOrg)
                .createdBy(regularUser)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-456", "ACME")).thenReturn(true);
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(permissionEvaluatorService.canEditFolder(regularUser, subspaceRoot)).thenReturn(true);
        when(folderRepository.existsByNameAndParentFolder_IdAndOwnerOrg_OrgCode("Sub-folder", 1L, "ACME")).thenReturn(false);
        when(folderRepository.save(any(FolderEntity.class))).thenReturn(childFolder);

        FolderResponseDto response = folderService.createFolder("ACME", request, regularUser);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getName()).isEqualTo("Sub-folder");
        assertThat(response.getParentFolderId()).isEqualTo(1L);
        verify(projectWorkspaceService, never()).createWorkspace(any(), any());
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when deleting folder without EDIT permission")
    void deleteFolder_DeniedWithoutEditPermission() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-456", "ACME"))
                .thenReturn(true);
        when(permissionEvaluatorService.canEditFolder(regularUser, subspaceRoot)).thenReturn(false);

        assertThatThrownBy(() -> folderService.deleteFolder(1L, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to delete folder");
    }

    @Test
    @DisplayName("Should delete folder with EDIT permission")
    void deleteFolder_SuccessWithEditPermission() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-456", "ACME"))
                .thenReturn(true);
        when(permissionEvaluatorService.canEditFolder(regularUser, subspaceRoot)).thenReturn(true);

        folderService.deleteFolder(1L, regularUser);

        verify(folderRepository).delete(subspaceRoot);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when downloading folder without DOWNLOAD permission")
    void downloadFolderAsZip_DeniedWithoutDownloadPermission() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(permissionEvaluatorService.canDownloadFolder(regularUser, subspaceRoot)).thenReturn(false);

        assertThatThrownBy(() -> folderService.downloadFolderAsZip(1L, regularUser, System.out))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to download folder");
    }

    @Test
    @DisplayName("ORG_MANAGER does not receive automatic content access for viewing folder")
    void getFolderById_ManagerDeniedWithoutContentPermission() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        // Explicitly mocking that the manager does not have content permission
        when(permissionEvaluatorService.canViewFolder(managerUser, subspaceRoot)).thenReturn(false);

        assertThatThrownBy(() -> folderService.getFolderById(1L, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to view folder");
    }

    @Test
    @DisplayName("Should create personal subspace for user")
    void createPersonalSubspace_Success() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("My Private Subspace")
                .build();

        FolderEntity personalSubspace = FolderEntity.builder()
                .id(10L)
                .name("My Private Subspace")
                .parentFolder(null)
                .ownerOrg(null)
                .createdBy(regularUser)
                .build();

        when(folderRepository.save(any(FolderEntity.class))).thenReturn(personalSubspace);

        FolderResponseDto response = folderService.createPersonalSubspace(request, regularUser);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("My Private Subspace");
        assertThat(response.getOwnerOrgCode()).isNull();
        assertThat(response.getCreatedByUserId()).isEqualTo("user-456");
        assertThat(response.getSubspaceType()).isEqualTo(SubspaceType.PERSONAL);
    }

    @Test
    @DisplayName("Should fetch personal subspaces for user")
    void getPersonalSubspaces_Success() {
        FolderEntity personalSubspace = FolderEntity.builder()
                .id(10L)
                .name("My Private Subspace")
                .parentFolder(null)
                .ownerOrg(null)
                .createdBy(regularUser)
                .build();

        when(folderRepository.findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id("user-456"))
                .thenReturn(List.of(personalSubspace));

        List<FolderResponseDto> response = folderService.getPersonalSubspaces(regularUser);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getName()).isEqualTo("My Private Subspace");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when attempting to delete a workspace root folder")
    void deleteFolder_WorkspaceRoot_ThrowsException() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(projectWorkspaceRepository.existsByRootFolder_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> folderService.deleteFolder(1L, regularUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("It is the root folder of a workspace");

        verify(folderRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should delete child folder when user has EDIT permission")
    void deleteFolder_ChildFolder_SuccessWithEditPermission() {
        FolderEntity childFolder = FolderEntity.builder()
                .id(2L)
                .name("Child Folder")
                .parentFolder(subspaceRoot)
                .ownerOrg(testOrg)
                .createdBy(regularUser)
                .build();

        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));
        when(projectWorkspaceRepository.existsByRootFolder_Id(2L)).thenReturn(false);
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-456", "ACME"))
                .thenReturn(true);
        when(permissionEvaluatorService.canEditFolder(regularUser, childFolder)).thenReturn(true);

        folderService.deleteFolder(2L, regularUser);

        verify(folderRepository).delete(childFolder);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when moving workspace root folder")
    void moveFolder_WorkspaceRoot_ThrowsException() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(projectWorkspaceRepository.existsByRootFolder_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> folderService.moveFolder(1L, 10L, regularUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace root folders cannot be moved");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when moving org root folder without parent")
    void moveFolder_OrgRootWithoutParent_ThrowsException() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(projectWorkspaceRepository.existsByRootFolder_Id(1L)).thenReturn(false);

        assertThatThrownBy(() -> folderService.moveFolder(1L, 10L, regularUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Workspace root folders cannot be moved");
    }

    @Test
    @DisplayName("Should move ordinary child folder when user has EDIT permission on source and target")
    void moveFolder_ChildFolder_SuccessWithEditPermission() {
        FolderEntity childFolder = FolderEntity.builder()
                .id(2L)
                .name("Child Folder")
                .parentFolder(subspaceRoot)
                .ownerOrg(testOrg)
                .build();

        FolderEntity targetFolder = FolderEntity.builder()
                .id(3L)
                .name("Target Folder")
                .parentFolder(subspaceRoot)
                .ownerOrg(testOrg)
                .build();

        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));
        when(folderRepository.findById(3L)).thenReturn(Optional.of(targetFolder));
        when(projectWorkspaceRepository.existsByRootFolder_Id(2L)).thenReturn(false);
        when(permissionEvaluatorService.canEditFolder(regularUser, childFolder)).thenReturn(true);
        when(permissionEvaluatorService.canEditFolder(regularUser, targetFolder)).thenReturn(true);
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> i.getArgument(0));

        FolderResponseDto result = folderService.moveFolder(2L, 3L, regularUser);

        assertThat(result.getId()).isEqualTo(2L);
        assertThat(childFolder.getParentFolder()).isEqualTo(targetFolder);
        verify(folderRepository).save(childFolder);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when attempting to directly rename a workspace root folder")
    void renameFolder_WorkspaceRoot_ThrowsException() {
        when(folderRepository.findById(1L)).thenReturn(Optional.of(subspaceRoot));
        when(projectWorkspaceRepository.existsByRootFolder_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> folderService.renameFolder(1L, "Divergent Name", regularUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot rename a workspace root folder directly; rename the workspace instead.");

        verify(folderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should rename ordinary child folder when user has EDIT permission")
    void renameFolder_ChildFolder_SuccessWithEditPermission() {
        FolderEntity childFolder = FolderEntity.builder()
                .id(2L)
                .name("Old Child Name")
                .parentFolder(subspaceRoot)
                .ownerOrg(testOrg)
                .build();

        when(folderRepository.findById(2L)).thenReturn(Optional.of(childFolder));
        when(projectWorkspaceRepository.existsByRootFolder_Id(2L)).thenReturn(false);
        when(permissionEvaluatorService.canEditFolder(regularUser, childFolder)).thenReturn(true);
        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(i -> i.getArgument(0));

        FolderResponseDto result = folderService.renameFolder(2L, "New Child Name", regularUser);

        assertThat(result.getName()).isEqualTo("New Child Name");
        assertThat(childFolder.getName()).isEqualTo("New Child Name");
        verify(folderRepository).save(childFolder);
    }

    @Test
    @DisplayName("Non-owner with valid personal VIEW can read personal folder")
    void getFolderById_PersonalFolder_NonOwnerWithViewPermission_Success() {
        UserEntity ownerUser = UserEntity.builder().id("owner-999").email("owner@personal.com").build();
        UserEntity recipientUser = UserEntity.builder().id("recipient-888").email("recipient@personal.com").build();
        FolderEntity personalFolder = FolderEntity.builder()
                .id(50L)
                .name("Shared Personal Notes")
                .ownerOrg(null)
                .createdBy(ownerUser)
                .build();

        when(folderRepository.findById(50L)).thenReturn(Optional.of(personalFolder));
        when(permissionEvaluatorService.canViewFolder(recipientUser, personalFolder)).thenReturn(true);

        FolderResponseDto response = folderService.getFolderById(50L, recipientUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getName()).isEqualTo("Shared Personal Notes");
        verify(permissionEvaluatorService).canViewFolder(recipientUser, personalFolder);
    }

    @Test
    @DisplayName("External user with valid direct permission can read organization folder contents")
    void getFolderContents_OrgFolder_ExternalUserWithViewPermission_Success() {
        UserEntity externalUser = UserEntity.builder().id("ext-777").email("ext@external.org").build();
        FolderEntity orgFolder = FolderEntity.builder()
                .id(60L)
                .name("Partner Shared Space")
                .ownerOrg(testOrg)
                .createdBy(managerUser)
                .build();

        when(folderRepository.findById(60L)).thenReturn(Optional.of(orgFolder));
        when(permissionEvaluatorService.canViewFolder(externalUser, orgFolder)).thenReturn(true);
        when(folderRepository.findByParentFolder_Id(60L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(60L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto contents = folderService.getFolderContents(60L, externalUser);

        assertThat(contents).isNotNull();
        assertThat(contents.getCurrentFolder().getId()).isEqualTo(60L);
        verify(permissionEvaluatorService).canViewFolder(externalUser, orgFolder);
    }

    @Test
    @DisplayName("Folder download uses DOWNLOAD authorization")
    void downloadFolderAsZip_UsesDownloadAuthorization() {
        FolderEntity folder = FolderEntity.builder()
                .id(70L)
                .name("Downloadable Assets")
                .ownerOrg(testOrg)
                .createdBy(managerUser)
                .build();

        when(folderRepository.findById(70L)).thenReturn(Optional.of(folder));
        when(permissionEvaluatorService.canDownloadFolder(regularUser, folder)).thenReturn(false);

        assertThatThrownBy(() -> folderService.downloadFolderAsZip(70L, regularUser, System.out))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to download folder");

        verify(permissionEvaluatorService).canDownloadFolder(regularUser, folder);
    }

    @Test
    @DisplayName("Delete does not become available with VIEW permission")
    void deleteFolder_ViewPermissionDoesNotGrantDeleteAuthority() {
        FolderEntity personalFolder = FolderEntity.builder()
                .id(80L)
                .name("Owner Private Stash")
                .ownerOrg(null)
                .createdBy(managerUser)
                .build();

        when(folderRepository.findById(80L)).thenReturn(Optional.of(personalFolder));
        when(projectWorkspaceRepository.existsByRootFolder_Id(80L)).thenReturn(false);

        // Regular user attempts to delete manager's personal folder
        assertThatThrownBy(() -> folderService.deleteFolder(80L, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access denied: You are not the owner of this personal folder.");

        verify(folderRepository, never()).delete(any());
    }

    // ── Breadcrumb Security Regression Tests ───────────────────────────────

    @Test
    @DisplayName("Breadcrumbs: Owner sees full breadcrumb hierarchy when all ancestors are accessible")
    void getFolderContents_OwnerSeesFullBreadcrumbHierarchy() {
        FolderEntity root = FolderEntity.builder().id(100L).name("Root Space").createdBy(managerUser).build();
        FolderEntity child = FolderEntity.builder().id(101L).name("Mid Folder").parentFolder(root).createdBy(managerUser).build();
        FolderEntity grandchild = FolderEntity.builder().id(102L).name("Deep Leaf").parentFolder(child).createdBy(managerUser).build();

        when(folderRepository.findById(102L)).thenReturn(Optional.of(grandchild));
        when(permissionEvaluatorService.canViewFolder(managerUser, grandchild)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(managerUser, child)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(managerUser, root)).thenReturn(true);
        when(folderRepository.findByParentFolder_Id(102L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(102L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto result = folderService.getFolderContents(102L, managerUser);

        assertThat(result.getBreadcrumbs()).hasSize(3);
        assertThat(result.getBreadcrumbs().get(0).getId()).isEqualTo(100L);
        assertThat(result.getBreadcrumbs().get(0).getName()).isEqualTo("Root Space");
        assertThat(result.getBreadcrumbs().get(1).getId()).isEqualTo(101L);
        assertThat(result.getBreadcrumbs().get(1).getName()).isEqualTo("Mid Folder");
        assertThat(result.getBreadcrumbs().get(2).getId()).isEqualTo(102L);
        assertThat(result.getBreadcrumbs().get(2).getName()).isEqualTo("Deep Leaf");
    }

    @Test
    @DisplayName("Breadcrumbs: Shared folder recipient sees only shared folder when parent is inaccessible")
    void getFolderContents_SharedFolderStopsAtInaccessibleParent() {
        FolderEntity privateRoot = FolderEntity.builder().id(200L).name("Private Company Root").createdBy(managerUser).build();
        FolderEntity sharedFolder = FolderEntity.builder().id(201L).name("Shared Project Folder").parentFolder(privateRoot).createdBy(managerUser).build();

        when(folderRepository.findById(201L)).thenReturn(Optional.of(sharedFolder));
        when(permissionEvaluatorService.canViewFolder(regularUser, sharedFolder)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(regularUser, privateRoot)).thenReturn(false);
        when(folderRepository.findByParentFolder_Id(201L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(201L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto result = folderService.getFolderContents(201L, regularUser);

        // The shared folder itself remains the visible root; privateRoot is completely absent
        assertThat(result.getBreadcrumbs()).hasSize(1);
        assertThat(result.getBreadcrumbs().get(0).getId()).isEqualTo(201L);
        assertThat(result.getBreadcrumbs().get(0).getName()).isEqualTo("Shared Project Folder");

        // Verify private ancestor ID and name are never leaked
        assertThat(result.getBreadcrumbs()).noneMatch(b -> b.getId().equals(200L) || "Private Company Root".equals(b.getName()));
    }

    @Test
    @DisplayName("Breadcrumbs: Descendant of shared folder stops traversal at first inaccessible ancestor")
    void getFolderContents_DescendantOfSharedFolderStopsAtInaccessibleAncestor() {
        FolderEntity privateRoot = FolderEntity.builder().id(300L).name("Top Secret Root").createdBy(managerUser).build();
        FolderEntity sharedParent = FolderEntity.builder().id(301L).name("Shared Subtree Root").parentFolder(privateRoot).createdBy(managerUser).build();
        FolderEntity childInShared = FolderEntity.builder().id(302L).name("Subfolder In Shared").parentFolder(sharedParent).createdBy(managerUser).build();

        when(folderRepository.findById(302L)).thenReturn(Optional.of(childInShared));
        when(permissionEvaluatorService.canViewFolder(regularUser, childInShared)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(regularUser, sharedParent)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(regularUser, privateRoot)).thenReturn(false);
        when(folderRepository.findByParentFolder_Id(302L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(302L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto result = folderService.getFolderContents(302L, regularUser);

        // Traversal includes childInShared and sharedParent, but terminates at privateRoot
        assertThat(result.getBreadcrumbs()).hasSize(2);
        assertThat(result.getBreadcrumbs().get(0).getId()).isEqualTo(301L);
        assertThat(result.getBreadcrumbs().get(0).getName()).isEqualTo("Shared Subtree Root");
        assertThat(result.getBreadcrumbs().get(1).getId()).isEqualTo(302L);
        assertThat(result.getBreadcrumbs().get(1).getName()).isEqualTo("Subfolder In Shared");

        // Inaccessible ancestor is absent
        assertThat(result.getBreadcrumbs()).noneMatch(b -> b.getId().equals(300L) || "Top Secret Root".equals(b.getName()));
    }

    @Test
    @DisplayName("Breadcrumbs: Normal personal navigation is unchanged")
    void getFolderContents_NormalPersonalNavigationUnchanged() {
        FolderEntity personalRoot = FolderEntity.builder().id(400L).name("My Documents").createdBy(regularUser).build();
        FolderEntity personalChild = FolderEntity.builder().id(401L).name("Photos").parentFolder(personalRoot).createdBy(regularUser).build();

        when(folderRepository.findById(401L)).thenReturn(Optional.of(personalChild));
        when(permissionEvaluatorService.canViewFolder(regularUser, personalChild)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(regularUser, personalRoot)).thenReturn(true);
        when(folderRepository.findByParentFolder_Id(401L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(401L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto result = folderService.getFolderContents(401L, regularUser);

        assertThat(result.getBreadcrumbs()).hasSize(2);
        assertThat(result.getBreadcrumbs().get(0).getId()).isEqualTo(400L);
        assertThat(result.getBreadcrumbs().get(1).getId()).isEqualTo(401L);
    }

    @Test
    @DisplayName("Breadcrumbs: Normal organization navigation is unchanged")
    void getFolderContents_NormalOrganizationNavigationUnchanged() {
        FolderEntity orgRoot = FolderEntity.builder().id(500L).name("ACME Root").ownerOrg(testOrg).createdBy(managerUser).build();
        FolderEntity orgSubfolder = FolderEntity.builder().id(501L).name("General").ownerOrg(testOrg).parentFolder(orgRoot).createdBy(managerUser).build();

        when(folderRepository.findById(501L)).thenReturn(Optional.of(orgSubfolder));
        when(permissionEvaluatorService.canViewFolder(regularUser, orgSubfolder)).thenReturn(true);
        when(permissionEvaluatorService.canViewFolder(regularUser, orgRoot)).thenReturn(true);
        when(folderRepository.findByParentFolder_Id(501L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByFolder_Id(501L)).thenReturn(Collections.emptyList());

        FolderContentResponseDto result = folderService.getFolderContents(501L, regularUser);

        assertThat(result.getBreadcrumbs()).hasSize(2);
        assertThat(result.getBreadcrumbs().get(0).getId()).isEqualTo(500L);
        assertThat(result.getBreadcrumbs().get(1).getId()).isEqualTo(501L);
    }
}
