package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CreateCollaborationRequestDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.collaboration.services.CollaborationService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.AddMemberRequestDto;
import com.subject77.docshare.modules.organization.dto.MemberStatusChangeRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.services.OrganizationService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
public class ManagerRoleCacheAndSelfSuspensionIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private WorkspaceManagerService workspaceManagerService;

    @Autowired
    private CollaborationService collaborationService;

    @MockitoSpyBean
    private PermissionCacheService permissionCacheService;

    @MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @MockitoBean
    private com.subject77.docshare.modules.audit.services.AuditLogService auditLogService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity admin1;
    private UserEntity admin2;
    private UserEntity targetUser;
    private UserEntity unrelatedUser;

    private OrganizationEntity orgA;
    private OrganizationEntity orgB;
    private ProjectWorkspaceEntity workspaceA;
    private CollaborationEntity collaboration;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        admin1 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin1_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("One")
                .role(Role.ROLE_USER)
                .build());

        admin2 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin2_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("Two")
                .role(Role.ROLE_USER)
                .build());

        targetUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("target_" + unique + "@test.com")
                .firstName("Target")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        unrelatedUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("unrelated_" + unique + "@test.com")
                .firstName("Unrelated")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        String orgCodeA = "ORGA_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCodeA)
                .name("Org A")
                .build(), admin1);
        orgA = organizationRepository.findById(orgCodeA).orElseThrow();

        String orgCodeB = "ORGB_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCodeB)
                .name("Org B")
                .build(), admin2);
        orgB = organizationRepository.findById(orgCodeB).orElseThrow();

        // Add targetUser to Org A and Org B
        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(targetUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), admin1);

        organizationService.addMemberToOrganization(orgB.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(targetUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), admin2);

        // Add unrelatedUser to Org A
        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(unrelatedUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), admin1);

        // Create Workspace in Org A
        FolderEntity rootFolder = folderRepository.save(FolderEntity.builder()
                .name("Workspace A Root")
                .createdBy(admin1)
                .ownerOrg(orgA)
                .build());
        workspaceA = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace A")
                .organization(orgA)
                .createdBy(admin1)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build());

        // Create Collaboration
        FolderEntity collabFolder = folderRepository.save(FolderEntity.builder()
                .name("Collab Folder")
                .createdBy(admin1)
                .ownerOrg(orgA)
                .build());
        collaboration = collaborationRepository.save(CollaborationEntity.builder()
                .name("Collab AB")
                .ownerOrganization(orgA)
                .partnerOrganization(orgB)
                .rootFolder(collabFolder)
                .status(com.subject77.docshare.modules.collaboration.entities.CollaborationStatus.ACTIVE)
                .startsAt(Instant.now())
                .createdBy(admin1)
                .build());

        // Add targetUser as participant in collaboration
        collaborationService.addParticipant(collaboration.getId(), targetUser.getId(), admin1);
    }

    // =========================================================================
    // 4B.3.4: Manager Role Cache Invalidation
    // =========================================================================

    @Test
    @DisplayName("4B.3.4-A & E: Workspace Manager assignment evicts target user permission cache and spares unrelated user")
    void testWorkspaceManagerAssignment_EvictsCache() {
        reset(permissionCacheService);

        workspaceManagerService.assignWorkspaceManager(workspaceA.getId(), targetUser.getId(), admin1);

        verify(permissionCacheService).evictUserPermissions(targetUser.getEmail());
        verify(permissionCacheService, never()).evictUserPermissions(unrelatedUser.getEmail());
    }

    @Test
    @DisplayName("4B.3.4-B & E: Workspace Manager removal evicts removed manager permission cache and spares unrelated user")
    void testWorkspaceManagerRemoval_EvictsCache() {
        workspaceManagerService.assignWorkspaceManager(workspaceA.getId(), targetUser.getId(), admin1);
        reset(permissionCacheService);

        workspaceManagerService.removeWorkspaceManager(workspaceA.getId(), targetUser.getId(), admin1);

        verify(permissionCacheService).evictUserPermissions(targetUser.getEmail());
        verify(permissionCacheService, never()).evictUserPermissions(unrelatedUser.getEmail());
    }

    @Test
    @DisplayName("4B.3.4-C & E: Collaboration Manager assignment evicts participant permission cache and spares unrelated user")
    void testCollaborationManagerAssignment_EvictsCache() {
        reset(permissionCacheService);

        CollaborationParticipantResponseDto dto = collaborationService.assignManager(collaboration.getId(), targetUser.getId(), admin1);
        assertThat(dto.getCollaborationRole()).isEqualTo(CollaborationRole.MANAGER);

        verify(permissionCacheService).evictUserPermissions(targetUser.getEmail());
        verify(permissionCacheService, never()).evictUserPermissions(unrelatedUser.getEmail());
    }

    @Test
    @DisplayName("4B.3.4-D & E: Collaboration Manager removal evicts manager permission cache and spares unrelated user")
    void testCollaborationManagerRemoval_EvictsCache() {
        collaborationService.assignManager(collaboration.getId(), targetUser.getId(), admin1);
        reset(permissionCacheService);

        CollaborationParticipantResponseDto dto = collaborationService.removeManager(collaboration.getId(), targetUser.getId(), admin1);
        assertThat(dto.getCollaborationRole()).isEqualTo(CollaborationRole.MEMBER);

        verify(permissionCacheService).evictUserPermissions(targetUser.getEmail());
        verify(permissionCacheService, never()).evictUserPermissions(unrelatedUser.getEmail());
    }

    // =========================================================================
    // 4B.3.5: ORG_ADMIN Self-Suspension Safety
    // =========================================================================

    @Test
    @DisplayName("4B.3.5-A: ORG_ADMIN cannot suspend themselves (single admin)")
    void testAdminCannotSuspendThemselves_SingleAdmin() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Accidental self-suspension")
                .build();

        assertThatThrownBy(() -> organizationService.suspendMember(orgA.getOrgCode(), admin1.getId(), request, admin1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-suspension is not allowed");
    }

    @Test
    @DisplayName("4B.3.5-D: ORG_ADMIN cannot suspend themselves even when multiple admins exist in organization")
    void testAdminCannotSuspendThemselves_MultipleAdmins() {
        // Add admin2 as a second ORG_ADMIN in Org A
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(admin2)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Self-suspension attempt with multi-admin")
                .build();

        assertThatThrownBy(() -> organizationService.suspendMember(orgA.getOrgCode(), admin1.getId(), request, admin1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Self-suspension is not allowed");
    }

    @Test
    @DisplayName("4B.3.5-B: ORG_ADMIN can still suspend another eligible member")
    void testAdminCanSuspendAnotherMember() {
        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Legitimate suspension")
                .build();

        organizationService.suspendMember(orgA.getOrgCode(), targetUser.getId(), request, admin1);

        UserOrganizationEntity membership = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(targetUser.getId(), orgA.getOrgCode()).orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
    }

    @Test
    @DisplayName("4B.3.5-C: Existing last-admin rule prevents suspending last active admin by another actor")
    void testCannotSuspendLastActiveAdmin() {
        // Attempting to suspend admin1 when admin1 is the only admin in Org A
        // If caller is super-admin or another valid caller targeting admin1
        UserEntity superAdmin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("superadmin_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.ROLE_SUPER_ADMIN)
                .build());

        MemberStatusChangeRequestDto request = MemberStatusChangeRequestDto.builder()
                .reason("Suspending last admin")
                .build();

        assertThatThrownBy(() -> organizationService.suspendMember(orgA.getOrgCode(), admin1.getId(), request, superAdmin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot suspend the last active administrator");
    }
}
