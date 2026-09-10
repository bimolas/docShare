package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.workspace.dto.CreatePersonalWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalWorkspaceProvisioningTest {

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private FolderRepository folderRepository;

    @InjectMocks
    private ProjectWorkspaceServiceImpl workspaceService;

    private UserEntity newUser;
    private final String userId = "user-brand-new-123";

    @BeforeEach
    void setUp() {
        newUser = UserEntity.builder()
                .id(userId)
                .email("brandnew@test.com")
                .firstName("Brand")
                .lastName("New")
                .role(Role.ROLE_USER)
                .build();
    }

    @Test
    @DisplayName("1 & 2. Brand-new user provisioning creates exactly one workspace with one root folder")
    void ensureDefaultPersonalWorkspace_BrandNewUser_CreatesOneWorkspaceAndOneRootFolder() {
        when(projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(List.of());
        when(folderRepository.findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(Optional.empty());
        when(folderRepository.findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(List.of());
        when(projectWorkspaceRepository.existsByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(false);

        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity f = invocation.getArgument(0);
            f.setId(101L);
            return f;
        });

        when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(invocation -> {
            ProjectWorkspaceEntity w = invocation.getArgument(0);
            w.setId(201L);
            return w;
        });

        ProjectWorkspaceResponseDto result = workspaceService.ensureDefaultPersonalWorkspace(newUser);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(201L);
        assertThat(result.getName()).isEqualTo("Personal");
        assertThat(result.getOrgCode()).isNull();
        assertThat(result.getRootFolderId()).isEqualTo(101L);

        ArgumentCaptor<FolderEntity> folderCaptor = ArgumentCaptor.forClass(FolderEntity.class);
        verify(folderRepository).save(folderCaptor.capture());
        FolderEntity savedFolder = folderCaptor.getValue();
        assertThat(savedFolder.getName()).isEqualTo("Personal");
        assertThat(savedFolder.isDefaultSubspace()).isTrue();
        assertThat(savedFolder.getSubspaceType()).isEqualTo(SubspaceType.PERSONAL);
        assertThat(savedFolder.getOwnerOrg()).isNull();
        assertThat(savedFolder.getParentFolder()).isNull();

        ArgumentCaptor<ProjectWorkspaceEntity> wsCaptor = ArgumentCaptor.forClass(ProjectWorkspaceEntity.class);
        verify(projectWorkspaceRepository).save(wsCaptor.capture());
        ProjectWorkspaceEntity savedWorkspace = wsCaptor.getValue();
        assertThat(savedWorkspace.getOrganization()).isNull();
        assertThat(savedWorkspace.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);
        assertThat(savedWorkspace.getRootFolder().getId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("3. Repeated provisioning remains exactly one (idempotent)")
    void ensureDefaultPersonalWorkspace_RepeatedCall_IsIdempotent() {
        FolderEntity existingRoot = FolderEntity.builder()
                .id(101L)
                .name("Personal")
                .isDefaultSubspace(true)
                .createdBy(newUser)
                .build();

        ProjectWorkspaceEntity existingWs = ProjectWorkspaceEntity.builder()
                .id(201L)
                .name("Personal")
                .organization(null)
                .rootFolder(existingRoot)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(newUser)
                .build();

        when(projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(List.of(existingWs));

        ProjectWorkspaceResponseDto firstCall = workspaceService.ensureDefaultPersonalWorkspace(newUser);
        ProjectWorkspaceResponseDto secondCall = workspaceService.ensureDefaultPersonalWorkspace(newUser);

        assertThat(firstCall).isNotNull();
        assertThat(secondCall).isNotNull();
        assertThat(firstCall.getId()).isEqualTo(201L);
        assertThat(secondCall.getId()).isEqualTo(201L);

        verify(folderRepository, never()).save(any());
        verify(projectWorkspaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. getPersonalWorkspaces returns exactly one workspace (self-heals if empty)")
    void getPersonalWorkspaces_WhenInitiallyEmpty_SelfHealsAndReturnsOne() {
        ProjectWorkspaceEntity provisionedWs = ProjectWorkspaceEntity.builder()
                .id(201L)
                .name("Personal")
                .organization(null)
                .rootFolder(FolderEntity.builder().id(101L).name("Personal").build())
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(newUser)
                .build();

        when(projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(List.of()) // first check in getPersonalWorkspaces: empty
                .thenReturn(List.of()) // second check in ensureDefaultPersonalWorkspace: empty
                .thenReturn(List.of(provisionedWs)); // third check in getPersonalWorkspaces after creation: populated

        when(folderRepository.findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(Optional.empty());
        when(folderRepository.findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(List.of());
        when(projectWorkspaceRepository.existsByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(false);

        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity f = invocation.getArgument(0);
            f.setId(101L);
            return f;
        });
        when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(invocation -> {
            ProjectWorkspaceEntity w = invocation.getArgument(0);
            w.setId(201L);
            return w;
        });

        List<ProjectWorkspaceResponseDto> list = workspaceService.getPersonalWorkspaces(newUser);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(201L);
        assertThat(list.get(0).getName()).isEqualTo("Personal");
    }

    @Test
    @DisplayName("5. Legacy orphan Home folder is reused as rootFolder (no orphan left behind)")
    void ensureDefaultPersonalWorkspace_ReusesLegacyOrphanHomeFolder() {
        FolderEntity legacyHome = FolderEntity.builder()
                .id(88L)
                .name("Home")
                .subspaceType(SubspaceType.PERSONAL)
                .parentFolder(null)
                .ownerOrg(null)
                .createdBy(newUser)
                .isDefaultSubspace(true)
                .build();

        when(projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(List.of());
        when(folderRepository.findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(Optional.of(legacyHome));
        when(projectWorkspaceRepository.existsByRootFolder_Id(88L))
                .thenReturn(false);
        when(projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNull(userId, "Home"))
                .thenReturn(false);
        when(folderRepository.findById(88L))
                .thenReturn(Optional.of(legacyHome));

        when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(invocation -> {
            ProjectWorkspaceEntity w = invocation.getArgument(0);
            w.setId(202L);
            return w;
        });

        ProjectWorkspaceResponseDto result = workspaceService.ensureDefaultPersonalWorkspace(newUser);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(202L);
        assertThat(result.getName()).isEqualTo("Home");
        assertThat(result.getRootFolderId()).isEqualTo(88L);

        // Verify no new folder was created
        verify(folderRepository, never()).save(any());
        // Verify workspace links the legacy folder
        ArgumentCaptor<ProjectWorkspaceEntity> captor = ArgumentCaptor.forClass(ProjectWorkspaceEntity.class);
        verify(projectWorkspaceRepository).save(captor.capture());
        assertThat(captor.getValue().getRootFolder().getId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("6. Organization membership does not affect personal workspace ownership (ownerOrg is null)")
    void ensureDefaultPersonalWorkspace_WithOrgMembership_RemainsPurelyPersonal() {
        OrganizationEntity org = OrganizationEntity.builder()
                .orgCode("CORP")
                .name("Corporation")
                .build();

        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(newUser)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(List.of());
        when(folderRepository.findByIsDefaultSubspaceTrueAndParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(Optional.empty());
        when(folderRepository.findByParentFolderIsNullAndOwnerOrgIsNullAndCreatedBy_Id(userId))
                .thenReturn(List.of());
        when(projectWorkspaceRepository.existsByCreatedBy_IdAndOrganizationIsNull(userId))
                .thenReturn(false);

        when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
            FolderEntity f = invocation.getArgument(0);
            f.setId(301L);
            return f;
        });
        when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(invocation -> {
            ProjectWorkspaceEntity w = invocation.getArgument(0);
            w.setId(401L);
            return w;
        });

        ProjectWorkspaceResponseDto result = workspaceService.ensureDefaultPersonalWorkspace(newUser);

        assertThat(result).isNotNull();
        assertThat(result.getOrgCode()).isNull();

        ArgumentCaptor<ProjectWorkspaceEntity> captor = ArgumentCaptor.forClass(ProjectWorkspaceEntity.class);
        verify(projectWorkspaceRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganization())
                .as("Personal workspace organization must remain strictly null despite org membership")
                .isNull();
        assertThat(captor.getValue().getRootFolder().getOwnerOrg())
                .as("Personal root folder ownerOrg must remain strictly null")
                .isNull();
    }
}
