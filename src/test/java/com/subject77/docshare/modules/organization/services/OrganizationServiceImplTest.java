package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.dto.AddMemberRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberResponseDto;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationResponseDto;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspacePolicyService workspacePolicyService;

    @Mock
    private OrgTrustRelationshipService orgTrustRelationshipService;

    @Mock
    private ProjectWorkspaceService projectWorkspaceService;

    @Mock
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationRepository collaborationRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository collaborationParticipantRepository;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    private UserEntity adminUser;
    private UserEntity regularUser;
    private OrganizationEntity testOrg;

    @BeforeEach
    void setUp() {
        adminUser = UserEntity.builder()
                .id("admin-123")
                .email("admin@acme.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        regularUser = UserEntity.builder()
                .id("user-456")
                .email("user@acme.com")
                .firstName("Regular")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        testOrg = OrganizationEntity.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .createdBy(adminUser)
                .build();
    }

    @Test
    @DisplayName("Should create organization, assign creator as ROLE_ORG_ADMIN, and create initial canonical General workspace")
    void createOrganization_Success() {
        OrganizationRequestDto request = OrganizationRequestDto.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .build();

        when(organizationRepository.existsByOrgCode("ACME")).thenReturn(false);
        when(organizationRepository.save(any(OrganizationEntity.class))).thenReturn(testOrg);

        OrganizationResponseDto response = organizationService.createOrganization(request, adminUser);

        assertThat(response.getOrgCode()).isEqualTo("ACME");
        assertThat(response.getName()).isEqualTo("Acme Corp");

        org.mockito.ArgumentCaptor<UserOrganizationEntity> membershipCaptor = org.mockito.ArgumentCaptor.forClass(UserOrganizationEntity.class);
        verify(userOrganizationRepository, times(1)).save(membershipCaptor.capture());
        UserOrganizationEntity savedMembership = membershipCaptor.getValue();
        assertThat(savedMembership.getUser()).isEqualTo(adminUser);
        assertThat(savedMembership.getOrganization()).isEqualTo(testOrg);
        assertThat(savedMembership.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(savedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        org.mockito.ArgumentCaptor<CreateProjectWorkspaceRequestDto> workspaceCaptor = org.mockito.ArgumentCaptor.forClass(CreateProjectWorkspaceRequestDto.class);
        verify(projectWorkspaceService, times(1)).createWorkspace(workspaceCaptor.capture(), eq(adminUser));
        CreateProjectWorkspaceRequestDto capturedWs = workspaceCaptor.getValue();
        assertThat(capturedWs.getName()).isEqualTo("General");
        assertThat(capturedWs.getOrgCode()).isEqualTo("ACME");
        assertThat(capturedWs.getDescription()).isNull();
    }

    @Test
    @DisplayName("Should propagate exception when initial workspace creation fails")
    void createOrganization_WorkspaceCreationFailed_ThrowsException() {
        OrganizationRequestDto request = OrganizationRequestDto.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .build();

        when(organizationRepository.existsByOrgCode("ACME")).thenReturn(false);
        when(organizationRepository.save(any(OrganizationEntity.class))).thenReturn(testOrg);
        when(projectWorkspaceService.createWorkspace(any(), any()))
                .thenThrow(new IllegalStateException("Failed to create workspace"));

        assertThatThrownBy(() -> organizationService.createOrganization(request, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to create workspace");
    }

    @Test
    @DisplayName("Should throw exception when org code already exists")
    void createOrganization_AlreadyExists() {
        OrganizationRequestDto request = OrganizationRequestDto.builder()
                .orgCode("ACME")
                .name("Acme Corp")
                .build();

        when(organizationRepository.existsByOrgCode("ACME")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Should add member to organization when requested by admin")
    void addMemberToOrganization_Success() {
        AddMemberRequestDto request = AddMemberRequestDto.builder()
                .userEmail("user@acme.com")
                .role(Role.ROLE_ORG_MANAGER)
                .build();

        UserOrganizationEntity savedMembership = UserOrganizationEntity.builder()
                .user(regularUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_MANAGER)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        when(userRepository.findByEmail("user@acme.com")).thenReturn(Optional.of(regularUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("user-456", "ACME"))
                .thenReturn(false);
        when(userOrganizationRepository.save(any(UserOrganizationEntity.class))).thenReturn(savedMembership);

        OrganizationMemberResponseDto response = organizationService.addMemberToOrganization("ACME", request, adminUser);

        assertThat(response.getUserId()).isEqualTo("user-456");
        assertThat(response.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when adding member without ORG_MEMBERS_MANAGE capability")
    void addMemberToOrganization_AccessDenied() {
        AddMemberRequestDto request = AddMemberRequestDto.builder()
                .userEmail("user@acme.com")
                .role(Role.ROLE_USER)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));
        doThrow(new AccessDeniedException("Missing required capability: ORG_MEMBERS_MANAGE"))
                .when(capabilityEvaluatorService)
                .requireCapability(regularUser, "ACME", OrganizationCapability.ORG_MEMBERS_MANAGE);

        assertThatThrownBy(() -> organizationService.addMemberToOrganization("ACME", request, regularUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("ORG_MEMBERS_MANAGE");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when manager attempts to add admin role")
    void addMemberToOrganization_ManagerCannotAddAdmin() {
        UserEntity managerUser = UserEntity.builder()
                .id("manager-789")
                .email("manager@acme.com")
                .role(Role.ROLE_USER)
                .build();

        AddMemberRequestDto request = AddMemberRequestDto.builder()
                .userEmail("user@acme.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));

        assertThatThrownBy(() -> organizationService.addMemberToOrganization("ACME", request, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Managers cannot add members with administrative roles");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when attempting to add member with ROLE_SUPER_ADMIN")
    void addMemberToOrganization_CannotAddSuperAdmin() {
        AddMemberRequestDto request = AddMemberRequestDto.builder()
                .userEmail("user@acme.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(organizationRepository.findById("ACME")).thenReturn(Optional.of(testOrg));

        assertThatThrownBy(() -> organizationService.addMemberToOrganization("ACME", request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPER_ADMIN cannot be assigned as an organization role");
    }

    @Test
    @DisplayName("Should get organization members")
    void getOrganizationMembers_Success() {
        UserOrganizationEntity adminMembership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("admin-123", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByOrganization_OrgCodeAndStatus(eq("ACME"), eq(MembershipStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adminMembership)));

        List<OrganizationMemberResponseDto> members = organizationService.getOrganizationMembers("ACME", adminUser);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).getUserId()).isEqualTo("admin-123");
    }

    @Test
    @DisplayName("Should get paginated organization members")
    void getOrganizationMembers_Paginated_Success() {
        UserOrganizationEntity adminMembership = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(testOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("admin-123", "ACME", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(userOrganizationRepository.findByOrganization_OrgCodeAndStatus("ACME", MembershipStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(adminMembership), pageable, 1));

        Page<OrganizationMemberResponseDto> memberPage = organizationService.getOrganizationMembers("ACME", pageable, adminUser);

        assertThat(memberPage.getContent()).hasSize(1);
        assertThat(memberPage.getTotalElements()).isEqualTo(1);
        assertThat(memberPage.getContent().get(0).getUserId()).isEqualTo("admin-123");
    }

    @Test
    @DisplayName("Should search organizations successfully when discoverable")
    void searchOrganizations_Success() {
        when(organizationRepository.findByNameContainingIgnoreCaseOrOrgCodeContainingIgnoreCase(eq("acme"), eq("acme"), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(testOrg));
        when(userOrganizationRepository.findByUser_IdAndStatus(regularUser.getId(), MembershipStatus.ACTIVE)).thenReturn(List.of());
        when(workspacePolicyService.getPolicyForOrganization("ACME")).thenReturn(Optional.empty());

        List<OrganizationResponseDto> results = organizationService.searchOrganizations("acme", regularUser);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrgCode()).isEqualTo("ACME");
        assertThat(results.get(0).getName()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("Should hide private organization from external requester")
    void searchOrganizations_Private_Hidden() {
        when(organizationRepository.findByNameContainingIgnoreCaseOrOrgCodeContainingIgnoreCase(eq("acme"), eq("acme"), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(testOrg));
        when(userOrganizationRepository.findByUser_IdAndStatus(regularUser.getId(), MembershipStatus.ACTIVE)).thenReturn(List.of());
        com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity privatePolicy = com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity.builder()
                .organization(testOrg)
                .discoverabilityMode(com.subject77.docshare.modules.policy.entities.DiscoverabilityMode.PRIVATE)
                .build();
        when(workspacePolicyService.getPolicyForOrganization("ACME")).thenReturn(Optional.of(privatePolicy));

        List<OrganizationResponseDto> results = organizationService.searchOrganizations("acme", regularUser);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should allow member to discover their own private organization")
    void searchOrganizations_Private_VisibleToMember() {
        UserOrganizationEntity membership = UserOrganizationEntity.builder()
                .user(regularUser)
                .organization(testOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(organizationRepository.findByNameContainingIgnoreCaseOrOrgCodeContainingIgnoreCase(eq("acme"), eq("acme"), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(testOrg));
        when(userOrganizationRepository.findByUser_IdAndStatus(regularUser.getId(), MembershipStatus.ACTIVE)).thenReturn(List.of(membership));

        List<OrganizationResponseDto> results = organizationService.searchOrganizations("acme", regularUser);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getOrgCode()).isEqualTo("ACME");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unauthenticated user searches organizations")
    void searchOrganizations_Unauthenticated() {
        assertThatThrownBy(() -> organizationService.searchOrganizations("acme", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when search query is blank")
    void searchOrganizations_BlankQuery() {
        assertThatThrownBy(() -> organizationService.searchOrganizations("   ", regularUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Search query cannot be blank");
    }
}

