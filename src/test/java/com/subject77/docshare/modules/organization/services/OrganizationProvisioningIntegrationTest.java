package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationResponseDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
public class OrganizationProvisioningIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private WorkspaceManagerRepository workspaceManagerRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private UserEntity creatorUser;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        creatorUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("creator_" + unique + "@test.com")
                .firstName("Creator")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());
    }

    @Test
    @DisplayName("Organization creation provisions canonical General workspace and root folder")
    void testCanonicalOrganizationProvisioning_FullFlow() {
        String orgCode = "PROV_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        OrganizationRequestDto request = OrganizationRequestDto.builder()
                .orgCode(orgCode)
                .name("Provisioning Test Org")
                .build();

        long permissionsBefore = permissionRepository.count();
        long managersBefore = workspaceManagerRepository.count();

        OrganizationResponseDto response = organizationService.createOrganization(request, creatorUser);

        assertThat(response).isNotNull();
        assertThat(response.getOrgCode()).isEqualTo(orgCode);
        assertThat(response.getName()).isEqualTo("Provisioning Test Org");

        // A & B: Verify Organization exists
        Optional<OrganizationEntity> orgOpt = organizationRepository.findById(orgCode);
        assertThat(orgOpt).isPresent();
        OrganizationEntity org = orgOpt.get();
        assertThat(org.getCreatedBy().getId()).isEqualTo(creatorUser.getId());

        // E: Verify Creator Membership has ROLE_ORG_ADMIN
        Optional<UserOrganizationEntity> membershipOpt = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(creatorUser.getId(), orgCode);
        assertThat(membershipOpt).isPresent();
        UserOrganizationEntity membership = membershipOpt.get();
        assertThat(membership.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // D: Verify exactly one ProjectWorkspaceEntity exists for the organization
        List<ProjectWorkspaceEntity> workspaces = projectWorkspaceRepository.findByOrganization_OrgCode(orgCode);
        assertThat(workspaces).hasSize(1);
        ProjectWorkspaceEntity workspace = workspaces.get(0);

        // B: Verify workspace attributes
        assertThat(workspace.getName()).isEqualTo("General");
        assertThat(workspace.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);
        assertThat(workspace.getOrganization().getOrgCode()).isEqualTo(orgCode);
        assertThat(workspace.getCreatedBy().getId()).isEqualTo(creatorUser.getId());
        assertThat(workspace.getRootFolder()).isNotNull();

        // B: Verify canonical root folder attributes via repository
        Long rootFolderId = workspace.getRootFolder().getId();
        FolderEntity rootFolder = folderRepository.findById(rootFolderId).orElseThrow();
        assertThat(rootFolder.getName()).isEqualTo("General");
        assertThat(rootFolder.getParentFolder()).isNull();
        assertThat(rootFolder.getOwnerOrg().getOrgCode()).isEqualTo(orgCode);
        assertThat(rootFolder.getCreatedBy().getId()).isEqualTo(creatorUser.getId());
        assertThat(rootFolder.isDefaultSubspace()).isTrue();
        assertThat(rootFolder.getSubspaceType()).isEqualTo(SubspaceType.ORGANIZATION_GENERAL);

        // C: Verify no duplicate General root folder exists in the organization
        List<FolderEntity> orgRoots = folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode);
        assertThat(orgRoots).hasSize(1);
        assertThat(orgRoots.get(0).getId()).isEqualTo(rootFolder.getId());

        // F: Verify workspace creation does NOT create WorkspaceManagerEntity or PermissionEntity
        assertThat(permissionRepository.count()).isEqualTo(permissionsBefore);
        assertThat(workspaceManagerRepository.count()).isEqualTo(managersBefore);
    }

    @Test
    @DisplayName("Second workspace root folder is not marked as default subspace")
    void testSecondWorkspace_IsNotDefaultSubspace() {
        String orgCode = "PROV2_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        OrganizationRequestDto orgRequest = OrganizationRequestDto.builder()
                .orgCode(orgCode)
                .name("Multi Workspace Org")
                .build();

        organizationService.createOrganization(orgRequest, creatorUser);

        // First workspace root is default
        List<ProjectWorkspaceEntity> workspacesFirst = projectWorkspaceRepository.findByOrganization_OrgCode(orgCode);
        assertThat(workspacesFirst).hasSize(1);
        Long firstRootId = workspacesFirst.get(0).getRootFolder().getId();
        assertThat(folderRepository.findById(firstRootId).orElseThrow().isDefaultSubspace()).isTrue();

        // Create second workspace
        CreateProjectWorkspaceRequestDto secondWsRequest = CreateProjectWorkspaceRequestDto.builder()
                .orgCode(orgCode)
                .name("Engineering")
                .build();

        ProjectWorkspaceResponseDto secondWs = projectWorkspaceService.createWorkspace(secondWsRequest, creatorUser);

        // Verify second workspace
        assertThat(secondWs).isNotNull();
        assertThat(secondWs.getName()).isEqualTo("Engineering");

        ProjectWorkspaceEntity secondEntity = projectWorkspaceRepository.findById(secondWs.getId()).orElseThrow();
        Long secondRootId = secondEntity.getRootFolder().getId();
        FolderEntity secondRoot = folderRepository.findById(secondRootId).orElseThrow();
        assertThat(secondRoot.getName()).isEqualTo("Engineering");
        assertThat(secondRoot.isDefaultSubspace()).isFalse();

        // Verify first workspace remains the only default root
        List<FolderEntity> allOrgRoots = folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode);
        assertThat(allOrgRoots).hasSize(2);

        long defaultRootsCount = allOrgRoots.stream().filter(FolderEntity::isDefaultSubspace).count();
        assertThat(defaultRootsCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Transaction rollback: if workspace creation fails, organization and creator membership are rolled back")
    void testTransactionRollback_WhenWorkspaceCreationFailed() {
        String orgCode = "ROLLBACK_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        OrganizationRequestDto request = OrganizationRequestDto.builder()
                .orgCode(orgCode)
                .name("Rollback Test Org")
                .build();

        doThrow(new RuntimeException("Simulated workspace creation failure"))
                .when(projectWorkspaceService)
                .createWorkspace(argThat(dto -> dto != null && orgCode.equals(dto.getOrgCode())), any());

        assertThatThrownBy(() -> organizationService.createOrganization(request, creatorUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated workspace creation failure");

        // Verify organization was rolled back and does not exist
        assertThat(organizationRepository.findById(orgCode)).isEmpty();

        // Verify creator membership was rolled back
        assertThat(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(creatorUser.getId(), orgCode)).isEmpty();

        // Verify no workspace was created
        assertThat(projectWorkspaceRepository.findByOrganization_OrgCode(orgCode)).isEmpty();

        // Verify no root folder was created
        assertThat(folderRepository.findByParentFolderIsNullAndOwnerOrg_OrgCode(orgCode)).isEmpty();
    }
}
