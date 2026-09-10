package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.dto.UpdateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.WorkspaceAssignmentResponseDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceTeamAssignmentRequestDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceUserAssignmentRequestDto;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectWorkspaceServiceImplTest {

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private ProjectWorkspaceServiceImpl workspaceService;

    private OrganizationEntity testOrg;
    private UserEntity adminUser;
    private UserEntity regularUser;
    private UserOrganizationEntity adminMembership;
    private UserOrganizationEntity regularMembership;
    private FolderEntity rootFolder;

    @BeforeEach
    void setUp() {
        testOrg = OrganizationEntity.builder()
                .orgCode("ORG-ALPHA")
                .name("Alpha Org")
                .build();

        adminUser = UserEntity.builder()
                .id("admin-id")
                .email("admin@alpha.com")
                .build();

        regularUser = UserEntity.builder()
                .id("user-id")
                .email("user@alpha.com")
                .build();

        adminMembership = UserOrganizationEntity.builder()
                .id(1L)
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        regularMembership = UserOrganizationEntity.builder()
                .id(2L)
                .user(regularUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .build();

        rootFolder = FolderEntity.builder()
                .id(100L)
                .name("Project Alpha Root")
                .ownerOrg(testOrg)
                .parentFolder(null)
                .createdBy(adminUser)
                .build();
    }

    @Nested
    @DisplayName("createWorkspace tests")
    class CreateWorkspaceTests {

        @Test
        @DisplayName("Should create workspace successfully with explicit root folder")
        void createWorkspace_WithExplicitRootFolder_Success() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Project Alpha")
                    .description("Alpha project workspace")
                    .rootFolderId(100L)
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "Project Alpha"))
                    .thenReturn(false);
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(projectWorkspaceRepository.existsByRootFolder_Id(100L)).thenReturn(false);

            ProjectWorkspaceEntity savedEntity = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .description("Alpha project workspace")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .createdBy(adminUser)
                    .build();

            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenReturn(savedEntity);

            ProjectWorkspaceResponseDto result = workspaceService.createWorkspace(request, adminUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            assertThat(result.getName()).isEqualTo("Project Alpha");
            assertThat(result.getOrgCode()).isEqualTo("ORG-ALPHA");
            assertThat(result.getRootFolderId()).isEqualTo(100L);
            assertThat(result.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);

            verify(auditLogService).logPermissionChange(
                    eq("WORKSPACE_CREATED"), eq("WORKSPACE"), eq(10L),
                    isNull(), isNull(), isNull(), eq("ORG-ALPHA"),
                    eq(adminUser), isNull(), isNull(), any(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("Should create new root folder if none specified")
        void createWorkspace_WithoutRootFolder_CreatesFolder() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Project Beta")
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "Project Beta"))
                    .thenReturn(false);
            when(folderRepository.findByNameAndParentFolderIsNullAndOwnerOrg_OrgCode("Project Beta", "ORG-ALPHA"))
                    .thenReturn(Optional.empty());

            FolderEntity createdFolder = FolderEntity.builder()
                    .id(200L)
                    .name("Project Beta")
                    .ownerOrg(testOrg)
                    .createdBy(adminUser)
                    .build();
            when(folderRepository.save(any(FolderEntity.class))).thenReturn(createdFolder);

            ProjectWorkspaceEntity savedEntity = ProjectWorkspaceEntity.builder()
                    .id(11L)
                    .organization(testOrg)
                    .name("Project Beta")
                    .rootFolder(createdFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .createdBy(adminUser)
                    .build();
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenReturn(savedEntity);

            ProjectWorkspaceResponseDto result = workspaceService.createWorkspace(request, adminUser);

            assertThat(result.getRootFolderId()).isEqualTo(200L);
            verify(folderRepository).save(any(FolderEntity.class));
        }

        @Test
        @DisplayName("Should mark root folder as default subspace with ORGANIZATION_GENERAL when first workspace")
        void createWorkspace_FirstWorkspace_MarksRootFolderAsDefaultSubspaceAndGeneral() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("General")
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "General"))
                    .thenReturn(false);
            when(folderRepository.findByNameAndParentFolderIsNullAndOwnerOrg_OrgCode("General", "ORG-ALPHA"))
                    .thenReturn(Optional.empty());
            when(projectWorkspaceRepository.existsByOrganization_OrgCode("ORG-ALPHA"))
                    .thenReturn(false);

            org.mockito.ArgumentCaptor<FolderEntity> folderCaptor = org.mockito.ArgumentCaptor.forClass(FolderEntity.class);
            FolderEntity savedFolder = FolderEntity.builder()
                    .id(300L)
                    .name("General")
                    .ownerOrg(testOrg)
                    .createdBy(adminUser)
                    .isDefaultSubspace(true)
                    .subspaceType(SubspaceType.ORGANIZATION_GENERAL)
                    .build();
            when(folderRepository.save(folderCaptor.capture())).thenReturn(savedFolder);

            ProjectWorkspaceEntity savedWorkspace = ProjectWorkspaceEntity.builder()
                    .id(12L)
                    .organization(testOrg)
                    .name("General")
                    .rootFolder(savedFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .createdBy(adminUser)
                    .build();
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenReturn(savedWorkspace);

            ProjectWorkspaceResponseDto result = workspaceService.createWorkspace(request, adminUser);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("General");
            assertThat(result.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);

            FolderEntity capturedFolder = folderCaptor.getValue();
            assertThat(capturedFolder.getName()).isEqualTo("General");
            assertThat(capturedFolder.getParentFolder()).isNull();
            assertThat(capturedFolder.getOwnerOrg()).isEqualTo(testOrg);
            assertThat(capturedFolder.getCreatedBy()).isEqualTo(adminUser);
            assertThat(capturedFolder.isDefaultSubspace()).isTrue();
            assertThat(capturedFolder.getSubspaceType()).isEqualTo(SubspaceType.ORGANIZATION_GENERAL);

            // Verify no permissions, managers, or team assignments were created
            verifyNoInteractions(permissionRepository);
            verifyNoInteractions(permissionService);
            verifyNoInteractions(groupRepository);
        }

        @Test
        @DisplayName("Should not mark root folder as default subspace when creating a second workspace")
        void createWorkspace_SecondWorkspace_DoesNotMarkRootFolderAsDefaultSubspace() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Engineering")
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "Engineering"))
                    .thenReturn(false);
            when(folderRepository.findByNameAndParentFolderIsNullAndOwnerOrg_OrgCode("Engineering", "ORG-ALPHA"))
                    .thenReturn(Optional.empty());
            when(projectWorkspaceRepository.existsByOrganization_OrgCode("ORG-ALPHA"))
                    .thenReturn(true);

            org.mockito.ArgumentCaptor<FolderEntity> folderCaptor = org.mockito.ArgumentCaptor.forClass(FolderEntity.class);
            FolderEntity savedFolder = FolderEntity.builder()
                    .id(301L)
                    .name("Engineering")
                    .ownerOrg(testOrg)
                    .createdBy(adminUser)
                    .isDefaultSubspace(false)
                    .build();
            when(folderRepository.save(folderCaptor.capture())).thenReturn(savedFolder);

            ProjectWorkspaceEntity savedWorkspace = ProjectWorkspaceEntity.builder()
                    .id(13L)
                    .organization(testOrg)
                    .name("Engineering")
                    .rootFolder(savedFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .createdBy(adminUser)
                    .build();
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenReturn(savedWorkspace);

            ProjectWorkspaceResponseDto result = workspaceService.createWorkspace(request, adminUser);

            assertThat(result).isNotNull();
            FolderEntity capturedFolder = folderCaptor.getValue();
            assertThat(capturedFolder.getName()).isEqualTo("Engineering");
            assertThat(capturedFolder.isDefaultSubspace()).isFalse();
            assertThat(capturedFolder.getSubspaceType()).isNull();
        }

        @Test
        @DisplayName("Should throw exception if workspace name is empty")
        void createWorkspace_EmptyName_ThrowsException() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("  ")
                    .build();

            assertThatThrownBy(() -> workspaceService.createWorkspace(request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Workspace name is required");
        }

        @Test
        @DisplayName("Should throw exception if user is not org admin")
        void createWorkspace_NonAdminUser_ThrowsAccessDenied() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Project Gamma")
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            doThrow(new AccessDeniedException("Organization Admin privileges required in organization: ORG-ALPHA"))
                    .when(capabilityEvaluatorService)
                    .requireCapability(regularUser, "ORG-ALPHA", OrganizationCapability.ORG_WORKSPACES_MANAGE);

            assertThatThrownBy(() -> workspaceService.createWorkspace(request, regularUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Organization Admin privileges required");
        }

        @Test
        @DisplayName("Should throw exception if root folder belongs to another org")
        void createWorkspace_RootFolderWrongOrg_ThrowsException() {
            OrganizationEntity otherOrg = OrganizationEntity.builder().orgCode("ORG-BETA").build();
            FolderEntity otherOrgFolder = FolderEntity.builder()
                    .id(300L)
                    .name("Other Folder")
                    .ownerOrg(otherOrg)
                    .build();

            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Project Delta")
                    .rootFolderId(300L)
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "Project Delta"))
                    .thenReturn(false);
            when(folderRepository.findById(300L)).thenReturn(Optional.of(otherOrgFolder));

            assertThatThrownBy(() -> workspaceService.createWorkspace(request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Root folder does not belong to organization");
        }

        @Test
        @DisplayName("Should throw exception if root folder is a subfolder")
        void createWorkspace_RootFolderIsSubfolder_ThrowsException() {
            FolderEntity parentFolder = FolderEntity.builder().id(99L).build();
            FolderEntity subfolder = FolderEntity.builder()
                    .id(301L)
                    .name("Sub Folder")
                    .ownerOrg(testOrg)
                    .parentFolder(parentFolder)
                    .build();

            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Project Epsilon")
                    .rootFolderId(301L)
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "Project Epsilon"))
                    .thenReturn(false);
            when(folderRepository.findById(301L)).thenReturn(Optional.of(subfolder));

            assertThatThrownBy(() -> workspaceService.createWorkspace(request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Specified root folder must be a top-level root folder");
        }

        @Test
        @DisplayName("Should throw exception if root folder is already assigned to another workspace")
        void createWorkspace_RootFolderAlreadyAssigned_ThrowsException() {
            CreateProjectWorkspaceRequestDto request = CreateProjectWorkspaceRequestDto.builder()
                    .orgCode("ORG-ALPHA")
                    .name("Project Zeta")
                    .rootFolderId(100L)
                    .build();

            when(organizationRepository.findById("ORG-ALPHA")).thenReturn(Optional.of(testOrg));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndName("ORG-ALPHA", "Project Zeta"))
                    .thenReturn(false);
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(projectWorkspaceRepository.existsByRootFolder_Id(100L)).thenReturn(true);

            assertThatThrownBy(() -> workspaceService.createWorkspace(request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is already assigned to another workspace");
        }
    }

    @Nested
    @DisplayName("getWorkspaceById tests")
    class GetWorkspaceByIdTests {

        @Test
        @DisplayName("Should return workspace for super admin without permission check")
        void getWorkspaceById_SuperAdmin_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            UserEntity superAdmin = UserEntity.builder().id("super-id").role(Role.ROLE_SUPER_ADMIN).build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("super-id", "ORG-ALPHA"))
                    .thenReturn(true);

            ProjectWorkspaceResponseDto result = workspaceService.getWorkspaceById(10L, superAdmin);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            verifyNoInteractions(permissionEvaluatorService);
        }

        @Test
        @DisplayName("Should return workspace for org admin without permission check")
        void getWorkspaceById_OrgAdmin_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            ProjectWorkspaceResponseDto result = workspaceService.getWorkspaceById(10L, adminUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
            verifyNoInteractions(permissionEvaluatorService);
        }

        @Test
        @DisplayName("Should return workspace for user with permissions")
        void getWorkspaceById_UserWithPermission_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA"))
                    .thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(regularMembership));
            when(permissionEvaluatorService.resolveMaxPermissionLevel(regularUser, rootFolder))
                    .thenReturn(PermissionLevel.VIEW);

            ProjectWorkspaceResponseDto result = workspaceService.getWorkspaceById(10L, regularUser);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException if user has no permissions")
        void getWorkspaceById_UserWithoutPermission_ThrowsAccessDenied() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA"))
                    .thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(regularMembership));
            when(permissionEvaluatorService.resolveMaxPermissionLevel(regularUser, rootFolder))
                    .thenReturn(null);

            assertThatThrownBy(() -> workspaceService.getWorkspaceById(10L, regularUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have access to this workspace");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException if user is not in workspace org")
        void getWorkspaceById_NonMember_ThrowsAccessDenied() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA"))
                    .thenReturn(false);

            assertThatThrownBy(() -> workspaceService.getWorkspaceById(10L, regularUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User is not a member of organization");
        }
    }

    @Nested
    @DisplayName("getWorkspacesForOrganization tests")
    class GetWorkspacesForOrganizationTests {

        @Test
        @DisplayName("Should return all workspaces for super admin")
        void getWorkspacesForOrganization_SuperAdmin_ReturnsAll() {
            ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(10L).organization(testOrg).name("WS 1").build();
            ProjectWorkspaceEntity ws2 = ProjectWorkspaceEntity.builder().id(11L).organization(testOrg).name("WS 2").build();
            UserEntity superAdmin = UserEntity.builder().id("super-id").role(Role.ROLE_SUPER_ADMIN).build();

            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("super-id", "ORG-ALPHA")).thenReturn(true);
            when(projectWorkspaceRepository.findByOrganization_OrgCode("ORG-ALPHA")).thenReturn(List.of(ws1, ws2));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getWorkspacesForOrganization("ORG-ALPHA", superAdmin);

            assertThat(results).hasSize(2);
            verifyNoInteractions(permissionEvaluatorService);
        }

        @Test
        @DisplayName("Should return all workspaces for org admin")
        void getWorkspacesForOrganization_OrgAdmin_ReturnsAll() {
            ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(10L).organization(testOrg).name("WS 1").build();
            ProjectWorkspaceEntity ws2 = ProjectWorkspaceEntity.builder().id(11L).organization(testOrg).name("WS 2").build();

            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA")).thenReturn(true);
            when(capabilityEvaluatorService.hasCapability(adminUser, "ORG-ALPHA", OrganizationCapability.ORG_SETTINGS_MANAGE)).thenReturn(true);
            when(projectWorkspaceRepository.findByOrganization_OrgCode("ORG-ALPHA")).thenReturn(List.of(ws1, ws2));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getWorkspacesForOrganization("ORG-ALPHA", adminUser);

            assertThat(results).hasSize(2);
            verifyNoInteractions(permissionEvaluatorService);
        }

        @Test
        @DisplayName("Should return filtered workspaces for org member")
        void getWorkspacesForOrganization_OrgMember_ReturnsFiltered() {
            FolderEntity rootFolder1 = FolderEntity.builder().id(100L).build();
            FolderEntity rootFolder2 = FolderEntity.builder().id(101L).build();
            ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(10L).organization(testOrg).name("WS 1").rootFolder(rootFolder1).build();
            ProjectWorkspaceEntity ws2 = ProjectWorkspaceEntity.builder().id(11L).organization(testOrg).name("WS 2").rootFolder(rootFolder2).build();

            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(Optional.of(regularMembership));
            when(projectWorkspaceRepository.findByOrganization_OrgCode("ORG-ALPHA")).thenReturn(List.of(ws1, ws2));
            
            when(permissionEvaluatorService.resolveMaxPermissionLevelsForFolders(eq(regularUser), anyList()))
                    .thenReturn(java.util.Map.of(100L, PermissionLevel.VIEW));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getWorkspacesForOrganization("ORG-ALPHA", regularUser);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("WS 1");
        }

        @Test
        @DisplayName("Should return filtered workspaces for org manager")
        void getWorkspacesForOrganization_OrgManager_ReturnsFiltered() {
            UserOrganizationEntity managerMembership = UserOrganizationEntity.builder()
                    .user(regularUser)
                    .organization(testOrg)
                    .role(Role.ROLE_ORG_MANAGER)
                    .build();

            FolderEntity rootFolder1 = FolderEntity.builder().id(100L).build();
            FolderEntity rootFolder2 = FolderEntity.builder().id(101L).build();
            ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(10L).organization(testOrg).name("WS 1").rootFolder(rootFolder1).build();
            ProjectWorkspaceEntity ws2 = ProjectWorkspaceEntity.builder().id(11L).organization(testOrg).name("WS 2").rootFolder(rootFolder2).build();

            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(Optional.of(managerMembership));
            when(projectWorkspaceRepository.findByOrganization_OrgCode("ORG-ALPHA")).thenReturn(List.of(ws1, ws2));
            
            when(permissionEvaluatorService.resolveMaxPermissionLevelsForFolders(eq(regularUser), anyList()))
                    .thenReturn(java.util.Map.of(101L, PermissionLevel.EDIT));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getWorkspacesForOrganization("ORG-ALPHA", regularUser);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("WS 2");
        }

        @Test
        @DisplayName("Should return workspace with direct user permission")
        void getWorkspacesForOrganization_DirectPermission_ReturnsWorkspace() {
            FolderEntity rootFolder1 = FolderEntity.builder().id(100L).build();
            ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(10L).organization(testOrg).name("WS 1").rootFolder(rootFolder1).build();

            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(Optional.of(regularMembership));
            when(projectWorkspaceRepository.findByOrganization_OrgCode("ORG-ALPHA")).thenReturn(List.of(ws1));
            
            when(permissionEvaluatorService.resolveMaxPermissionLevelsForFolders(eq(regularUser), anyList()))
                    .thenReturn(java.util.Map.of(100L, PermissionLevel.DOWNLOAD));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getWorkspacesForOrganization("ORG-ALPHA", regularUser);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("WS 1");
        }

        @Test
        @DisplayName("Should return workspace with team-derived permission")
        void getWorkspacesForOrganization_TeamDerivedPermission_ReturnsWorkspace() {
            FolderEntity rootFolder1 = FolderEntity.builder().id(100L).build();
            ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(10L).organization(testOrg).name("WS 1").rootFolder(rootFolder1).build();

            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(Optional.of(regularMembership));
            when(projectWorkspaceRepository.findByOrganization_OrgCode("ORG-ALPHA")).thenReturn(List.of(ws1));
            
            when(permissionEvaluatorService.resolveMaxPermissionLevelsForFolders(eq(regularUser), anyList()))
                    .thenReturn(java.util.Map.of(100L, PermissionLevel.VIEW));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getWorkspacesForOrganization("ORG-ALPHA", regularUser);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("WS 1");
        }
    }

    @Nested
    @DisplayName("updateWorkspace tests")
    class UpdateWorkspaceTests {

        @Test
        @DisplayName("Should update workspace successfully by org admin")
        void updateWorkspace_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Old Name")
                    .description("Old Desc")
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                    .name("New Name")
                    .description("New Desc")
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndNameAndIdNot("ORG-ALPHA", "New Name", 10L))
                    .thenReturn(false);
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(i -> i.getArgument(0));

            ProjectWorkspaceResponseDto result = workspaceService.updateWorkspace(10L, request, adminUser);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getDescription()).isEqualTo("New Desc");
            verify(auditLogService).logPermissionChange(
                    eq("WORKSPACE_UPDATED"), eq("WORKSPACE"), eq(10L),
                    isNull(), isNull(), isNull(), eq("ORG-ALPHA"),
                    eq(adminUser), isNull(), isNull(), any(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("Should throw exception when updating an archived workspace")
        void updateWorkspace_Archived_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Old Name")
                    .status(ProjectWorkspaceStatus.ARCHIVED)
                    .build();

            UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                    .name("New Name")
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            assertThatThrownBy(() -> workspaceService.updateWorkspace(10L, request, adminUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Workspace is archived and cannot be modified");
        }
        @Test
        @DisplayName("Should synchronize root folder name when workspace is renamed")
        void updateWorkspace_SynchronizesRootFolderName() {
            FolderEntity root = FolderEntity.builder()
                    .id(100L)
                    .name("Old Name")
                    .parentFolder(null)
                    .ownerOrg(testOrg)
                    .build();

            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Old Name")
                    .rootFolder(root)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                    .name("New Name")
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.existsByOrganization_OrgCodeAndNameAndIdNot("ORG-ALPHA", "New Name", 10L))
                    .thenReturn(false);
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(i -> i.getArgument(0));

            ProjectWorkspaceResponseDto result = workspaceService.updateWorkspace(10L, request, adminUser);

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(root.getName()).isEqualTo("New Name");
            verify(folderRepository).save(root);
        }
    }

    @Nested
    @DisplayName("archiveWorkspace tests")
    class ArchiveWorkspaceTests {

        @Test
        @DisplayName("Should archive workspace successfully by org admin")
        void archiveWorkspace_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(i -> i.getArgument(0));

            ProjectWorkspaceResponseDto result = workspaceService.archiveWorkspace(10L, adminUser);

            assertThat(result.getStatus()).isEqualTo(ProjectWorkspaceStatus.ARCHIVED);
            verify(auditLogService).logPermissionChange(
                    eq("WORKSPACE_ARCHIVED"), eq("WORKSPACE"), eq(10L),
                    isNull(), isNull(), isNull(), eq("ORG-ALPHA"),
                    eq(adminUser), isNull(), isNull(), any(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("Should throw exception when archiving an already archived workspace")
        void archiveWorkspace_AlreadyArchived_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .status(ProjectWorkspaceStatus.ARCHIVED)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            assertThatThrownBy(() -> workspaceService.archiveWorkspace(10L, adminUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Workspace is archived and cannot be modified");
        }
    }

    @Nested
    @DisplayName("restoreWorkspace tests")
    class RestoreWorkspaceTests {

        @Test
        @DisplayName("Should restore archived workspace successfully by org admin")
        void restoreWorkspace_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .status(ProjectWorkspaceStatus.ARCHIVED)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(i -> i.getArgument(0));

            ProjectWorkspaceResponseDto result = workspaceService.restoreWorkspace(10L, adminUser);

            assertThat(result.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);
            verify(auditLogService).logPermissionChange(
                    eq("WORKSPACE_RESTORED"), eq("WORKSPACE"), eq(10L),
                    isNull(), isNull(), isNull(), eq("ORG-ALPHA"),
                    eq(adminUser), isNull(), isNull(), any(), isNull(), isNull()
            );
        }

        @Test
        @DisplayName("Should throw exception when restoring an already active workspace")
        void restoreWorkspace_AlreadyActive_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            assertThatThrownBy(() -> workspaceService.restoreWorkspace(10L, adminUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Workspace is already active: 10");
        }

        @Test
        @DisplayName("Should throw exception when restoring workspace if user is not org admin")
        void restoreWorkspace_NotOrgAdmin_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .status(ProjectWorkspaceStatus.ARCHIVED)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            doThrow(new AccessDeniedException("Organization Admin privileges required in organization: ORG-ALPHA"))
                    .when(capabilityEvaluatorService)
                    .requireCapability(regularUser, "ORG-ALPHA", OrganizationCapability.ORG_WORKSPACES_MANAGE);

            assertThatThrownBy(() -> workspaceService.restoreWorkspace(10L, regularUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Organization Admin privileges required in organization: ORG-ALPHA");
        }

        @Test
        @DisplayName("Should throw exception when restoring workspace by wrong-org admin")
        void restoreWorkspace_WrongOrgAdmin_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .status(ProjectWorkspaceStatus.ARCHIVED)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            doThrow(new AccessDeniedException("User is not a member of organization: ORG-ALPHA"))
                    .when(capabilityEvaluatorService)
                    .requireCapability(adminUser, "ORG-ALPHA", OrganizationCapability.ORG_WORKSPACES_MANAGE);

            assertThatThrownBy(() -> workspaceService.restoreWorkspace(10L, adminUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User is not a member of organization: ORG-ALPHA");
        }
    }

    @Nested
    @DisplayName("assignTeamToWorkspace tests")
    class AssignTeamToWorkspaceTests {

        @Test
        @DisplayName("Should assign team to workspace root folder successfully by org admin")
        void assignTeamToWorkspace_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            GroupEntity team = GroupEntity.builder()
                    .id(50L)
                    .name("Engineering Team")
                    .organization(testOrg)
                    .status(GroupStatus.ACTIVE)
                    .build();

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(50L)
                    .permissionLevel(PermissionLevel.EDIT)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(groupRepository.findById(50L)).thenReturn(Optional.of(team));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            PermissionResponseDto permResponse = PermissionResponseDto.builder()
                    .id(500L)
                    .permissionLevel(PermissionLevel.EDIT)
                    .permissionType(PermissionType.ALLOW)
                    .build();

            when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("admin-id")))
                    .thenReturn(permResponse);

            WorkspaceAssignmentResponseDto result = workspaceService.assignTeamToWorkspace(10L, request, adminUser);

            assertThat(result).isNotNull();
            assertThat(result.getPermissionId()).isEqualTo(500L);
            assertThat(result.getWorkspaceId()).isEqualTo(10L);
            assertThat(result.getFolderId()).isEqualTo(100L);
            assertThat(result.getTeamId()).isEqualTo(50L);
            assertThat(result.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        }

        @Test
        @DisplayName("Should throw exception if workspace is archived")
        void assignTeamToWorkspace_ArchivedWorkspace_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .status(ProjectWorkspaceStatus.ARCHIVED)
                    .build();

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(50L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));

            assertThatThrownBy(() -> workspaceService.assignTeamToWorkspace(10L, request, adminUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Workspace is archived and cannot be modified");
        }

        @Test
        @DisplayName("Should throw exception if target folder is outside workspace root folder hierarchy")
        void assignTeamToWorkspace_FolderOutsideHierarchy_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            FolderEntity unassignedFolder = FolderEntity.builder()
                    .id(999L)
                    .ownerOrg(testOrg)
                    .parentFolder(null)
                    .build();

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(50L)
                    .folderId(999L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(999L)).thenReturn(Optional.of(unassignedFolder));

            assertThatThrownBy(() -> workspaceService.assignTeamToWorkspace(10L, request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not within workspace root folder hierarchy");
        }

        @Test
        @DisplayName("Should throw exception if team belongs to another organization")
        void assignTeamToWorkspace_TeamWrongOrg_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            OrganizationEntity otherOrg = OrganizationEntity.builder().orgCode("ORG-BETA").build();
            GroupEntity otherOrgTeam = GroupEntity.builder()
                    .id(51L)
                    .organization(otherOrg)
                    .status(GroupStatus.ACTIVE)
                    .build();

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(51L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(groupRepository.findById(51L)).thenReturn(Optional.of(otherOrgTeam));

            assertThatThrownBy(() -> workspaceService.assignTeamToWorkspace(10L, request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("does not belong to organization");
        }

        @Test
        @DisplayName("Should throw exception if team is archived")
        void assignTeamToWorkspace_ArchivedTeam_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            GroupEntity archivedTeam = GroupEntity.builder()
                    .id(52L)
                    .organization(testOrg)
                    .status(GroupStatus.ARCHIVED)
                    .build();

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(52L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(groupRepository.findById(52L)).thenReturn(Optional.of(archivedTeam));

            assertThatThrownBy(() -> workspaceService.assignTeamToWorkspace(10L, request, adminUser))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Archived team cannot be assigned permissions");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when Team Manager without org admin attempts to assign team")
        void assignTeamToWorkspace_TeamManagerMerelyInOrg_ThrowsAccessDenied() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .name("Project Alpha")
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            GroupEntity team = GroupEntity.builder()
                    .id(50L)
                    .name("Engineering Team")
                    .organization(testOrg)
                    .status(GroupStatus.ACTIVE)
                    .build();

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(50L)
                    .permissionLevel(PermissionLevel.EDIT)
                    .build();

            UserEntity teamManagerUser = UserEntity.builder()
                    .id("mgr-id")
                    .email("mgr@alpha.com")
                    .role(Role.ROLE_USER)
                    .build();

            UserOrganizationEntity memberRole = UserOrganizationEntity.builder()
                    .id(20L)
                    .user(teamManagerUser)
                    .organization(testOrg)
                    .role(Role.ROLE_USER)
                    .status(MembershipStatus.ACTIVE)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(groupRepository.findById(50L)).thenReturn(Optional.of(team));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(memberRole));
            when(permissionEvaluatorService.canAdministerFolderPermissions(teamManagerUser, rootFolder))
                    .thenReturn(false);

            assertThatThrownBy(() -> workspaceService.assignTeamToWorkspace(10L, request, teamManagerUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have permission to manage workspace assignments");
        }
    }

    @Nested
    @DisplayName("assignUserToWorkspace tests")
    class AssignUserToWorkspaceTests {

        @Test
        @DisplayName("Should assign user to workspace folder successfully")
        void assignUserToWorkspace_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            WorkspaceUserAssignmentRequestDto request = WorkspaceUserAssignmentRequestDto.builder()
                    .userEmail("user@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(userRepository.findByEmail("user@alpha.com")).thenReturn(Optional.of(regularUser));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(true);
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            PermissionResponseDto permResponse = PermissionResponseDto.builder()
                    .id(600L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .build();

            when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("admin-id")))
                    .thenReturn(permResponse);

            WorkspaceAssignmentResponseDto result = workspaceService.assignUserToWorkspace(10L, request, adminUser);

            assertThat(result).isNotNull();
            assertThat(result.getPermissionId()).isEqualTo(600L);
            assertThat(result.getUserEmail()).isEqualTo("user@alpha.com");
            assertThat(result.getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
        }

        @Test
        @DisplayName("Should throw exception if user is not member of workspace org")
        void assignUserToWorkspace_NonMember_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            UserEntity externalUser = UserEntity.builder()
                    .id("ext-id")
                    .email("ext@external.com")
                    .build();

            WorkspaceUserAssignmentRequestDto request = WorkspaceUserAssignmentRequestDto.builder()
                    .userEmail("ext@external.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
            when(userRepository.findByEmail("ext@external.com")).thenReturn(Optional.of(externalUser));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("ext-id", "ORG-ALPHA")).thenReturn(false);

            assertThatThrownBy(() -> workspaceService.assignUserToWorkspace(10L, request, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not a member of organization");
        }
    }

    @Nested
    @DisplayName("revokeWorkspaceAssignment tests")
    class RevokeWorkspaceAssignmentTests {

        @Test
        @DisplayName("Should revoke workspace assignment successfully")
        void revokeWorkspaceAssignment_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            PermissionEntity perm = PermissionEntity.builder()
                    .id(700L)
                    .folder(rootFolder)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(permissionRepository.findById(700L)).thenReturn(Optional.of(perm));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));

            workspaceService.revokeWorkspaceAssignment(10L, 700L, adminUser);

            verify(permissionService).revokePermission(700L, "admin-id");
        }

        @Test
        @DisplayName("Should cascade revoke all member delegations when team boundary assignment is revoked")
        void revokeWorkspaceAssignment_TeamBoundary_RevokesAllMemberDelegations() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            GroupEntity team = GroupEntity.builder()
                    .id(50L)
                    .name("Engineering Team")
                    .organization(testOrg)
                    .build();

            PermissionEntity boundaryPerm = PermissionEntity.builder()
                    .id(700L)
                    .folder(rootFolder)
                    .recipientType(RecipientType.GROUP)
                    .targetGroup(team)
                    .permissionType(PermissionType.BOUNDARY)
                    .permissionLevel(PermissionLevel.EDIT)
                    .revoked(false)
                    .build();

            PermissionEntity memberDelegation = PermissionEntity.builder()
                    .id(701L)
                    .folder(rootFolder)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .targetGroup(team)
                    .targetUserEmail("member@alpha.com")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .revoked(false)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(permissionRepository.findById(700L)).thenReturn(Optional.of(boundaryPerm));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-id", "ORG-ALPHA"))
                    .thenReturn(Optional.of(adminMembership));
            when(permissionRepository.findByTargetGroup_Id(50L)).thenReturn(List.of(memberDelegation));

            workspaceService.revokeWorkspaceAssignment(10L, 700L, adminUser);

            assertThat(memberDelegation.isRevoked()).isTrue();
            verify(permissionRepository).save(memberDelegation);
            verify(permissionService).revokePermission(700L, "admin-id");
        }

        @Test
        @DisplayName("Should throw exception if permission does not belong to workspace folder tree")
        void revokeWorkspaceAssignment_OutsideWorkspace_ThrowsException() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            FolderEntity unrelatedFolder = FolderEntity.builder().id(888L).build();
            PermissionEntity perm = PermissionEntity.builder()
                    .id(701L)
                    .folder(unrelatedFolder)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(permissionRepository.findById(701L)).thenReturn(Optional.of(perm));

            assertThatThrownBy(() -> workspaceService.revokeWorkspaceAssignment(10L, 701L, adminUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not associated with workspace");
        }
    }

    @Nested
    @DisplayName("getWorkspaceAssignments tests")
    class GetWorkspaceAssignmentsTests {

        @Test
        @DisplayName("Should list all workspace assignments across folders")
        void getWorkspaceAssignments_Success() {
            ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                    .id(10L)
                    .organization(testOrg)
                    .rootFolder(rootFolder)
                    .status(ProjectWorkspaceStatus.ACTIVE)
                    .build();

            FolderEntity subfolder = FolderEntity.builder()
                    .id(101L)
                    .name("Subfolder A")
                    .parentFolder(rootFolder)
                    .ownerOrg(testOrg)
                    .build();

            GroupEntity team = GroupEntity.builder().id(50L).name("Team Alpha").build();

            PermissionEntity permRoot = PermissionEntity.builder()
                    .id(800L)
                    .folder(rootFolder)
                    .targetGroup(team)
                    .permissionLevel(PermissionLevel.EDIT)
                    .permissionType(PermissionType.ALLOW)
                    .revoked(false)
                    .build();

            PermissionEntity permSub = PermissionEntity.builder()
                    .id(801L)
                    .folder(subfolder)
                    .targetUserEmail("user@alpha.com")
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .revoked(false)
                    .build();

            when(projectWorkspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-id", "ORG-ALPHA")).thenReturn(true);
            when(folderRepository.findByParentFolder_Id(100L)).thenReturn(List.of(subfolder));
            when(folderRepository.findByParentFolder_Id(101L)).thenReturn(Collections.emptyList());

            when(permissionRepository.findByFolder_IdAndRevokedFalse(100L)).thenReturn(List.of(permRoot));
            when(permissionRepository.findByFolder_IdAndRevokedFalse(101L)).thenReturn(List.of(permSub));

            List<WorkspaceAssignmentResponseDto> assignments = workspaceService.getWorkspaceAssignments(10L, regularUser);

            assertThat(assignments).hasSize(2);
            assertThat(assignments.get(0).getPermissionId()).isEqualTo(800L);
            assertThat(assignments.get(0).getTeamId()).isEqualTo(50L);
            assertThat(assignments.get(1).getPermissionId()).isEqualTo(801L);
            assertThat(assignments.get(1).getUserEmail()).isEqualTo("user@alpha.com");
        }
    }
}
