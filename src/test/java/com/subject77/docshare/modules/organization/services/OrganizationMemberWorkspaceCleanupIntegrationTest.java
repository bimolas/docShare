package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.AddMemberRequestDto;
import com.subject77.docshare.modules.organization.dto.MemberStatusChangeRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationResponseDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.enums.ScopeType;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import com.subject77.docshare.modules.workspace.services.WorkspaceManagerService;
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

@SpringBootTest
@Transactional
public class OrganizationMemberWorkspaceCleanupIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private WorkspaceManagerService workspaceManagerService;

    @Autowired
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.audit.services.AuditLogService auditLogService;

    private UserEntity adminUserA;
    private UserEntity adminUserB;
    private UserEntity targetUser;
    private UserEntity otherUser;

    private OrganizationEntity orgA;
    private OrganizationEntity orgB;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        adminUserA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_a_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("A")
                .role(Role.ROLE_USER)
                .build());

        adminUserB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_b_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("B")
                .role(Role.ROLE_USER)
                .build());

        targetUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("target_" + unique + "@test.com")
                .firstName("Target")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        otherUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("other_" + unique + "@test.com")
                .firstName("Other")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        String orgCodeA = "ORGA_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCodeA)
                .name("Org A")
                .build(), adminUserA);
        orgA = organizationRepository.findById(orgCodeA).orElseThrow();

        String orgCodeB = "ORGB_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCodeB)
                .name("Org B")
                .build(), adminUserB);
        orgB = organizationRepository.findById(orgCodeB).orElseThrow();

        // Add targetUser to Org A and Org B
        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(targetUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminUserA);

        organizationService.addMemberToOrganization(orgB.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(targetUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminUserB);

        // Add otherUser to Org A
        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(otherUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminUserA);
    }

    @Test
    @DisplayName("Removing org member cleans up their workspace managers in target org only, preserving other users and other orgs")
    void testRemoveMember_CleansUpWorkspaceManagersInTargetOrgOnly() {
        // Create Workspace 1 and Workspace 2 in Org A
        ProjectWorkspaceEntity ws1 = createWorkspaceInOrg(orgA, adminUserA, "Workspace 1");
        ProjectWorkspaceEntity ws2 = createWorkspaceInOrg(orgA, adminUserA, "Workspace 2");

        // Create Workspace 3 in Org B
        ProjectWorkspaceEntity ws3 = createWorkspaceInOrg(orgB, adminUserB, "Workspace 3");

        // Assign targetUser as manager of ws1 and ws2 in Org A, and ws3 in Org B
        workspaceManagerService.assignWorkspaceManager(ws1.getId(), targetUser.getId(), adminUserA);
        workspaceManagerService.assignWorkspaceManager(ws2.getId(), targetUser.getId(), adminUserA);
        workspaceManagerService.assignWorkspaceManager(ws3.getId(), targetUser.getId(), adminUserB);

        // Assign otherUser as manager of ws1 in Org A
        workspaceManagerService.assignWorkspaceManager(ws1.getId(), otherUser.getId(), adminUserA);

        // Verify pre-conditions
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), targetUser.getId())).isTrue();
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws2.getId(), targetUser.getId())).isTrue();
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws3.getId(), targetUser.getId())).isTrue();
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), otherUser.getId())).isTrue();

        // Pre-condition authorization check: targetUser has WORKSPACE_PERMISSIONS_MANAGE in ws1
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                targetUser, OrganizationCapability.WORKSPACE_PERMISSIONS_MANAGE, ScopeType.WORKSPACE, ws1.getId(), orgA.getOrgCode())).isTrue();

        // EXECUTE: Remove targetUser from Org A
        MemberStatusChangeRequestDto removeRequest = MemberStatusChangeRequestDto.builder()
                .reason("Departing Org A")
                .build();
        organizationService.removeMember(orgA.getOrgCode(), targetUser.getId(), removeRequest, adminUserA);

        // VERIFY:
        // 1. targetUser assignments in Org A (ws1, ws2) are DELETED
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), targetUser.getId())).isFalse();
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws2.getId(), targetUser.getId())).isFalse();
        assertThat(workspaceManagerRepository.findByUser_IdAndWorkspace_Organization_OrgCode(targetUser.getId(), orgA.getOrgCode())).isEmpty();

        // 2. otherUser assignment in ws1 (Org A) is PRESERVED
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), otherUser.getId())).isTrue();

        // 3. targetUser assignment in ws3 (Org B) is PRESERVED
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws3.getId(), targetUser.getId())).isTrue();

        // 4. Authorization check: targetUser no longer has WORKSPACE_PERMISSIONS_MANAGE in ws1
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                targetUser, OrganizationCapability.WORKSPACE_PERMISSIONS_MANAGE, ScopeType.WORKSPACE, ws1.getId(), orgA.getOrgCode())).isFalse();
    }

    @Test
    @DisplayName("Re-adding removed member does NOT restore old workspace manager assignments")
    void testReAddingUser_DoesNotRestoreOldWorkspaceManagerAssignments() {
        ProjectWorkspaceEntity ws1 = createWorkspaceInOrg(orgA, adminUserA, "Workspace 1");
        workspaceManagerService.assignWorkspaceManager(ws1.getId(), targetUser.getId(), adminUserA);

        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), targetUser.getId())).isTrue();

        // Remove from Org A
        organizationService.removeMember(orgA.getOrgCode(), targetUser.getId(), null, adminUserA);

        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), targetUser.getId())).isFalse();

        // Re-activate / re-add member to Org A
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(targetUser.getId(), orgA.getOrgCode()).orElseThrow();
        membership.setStatus(MembershipStatus.ACTIVE);
        userOrganizationRepository.save(membership);

        // Invariant check: targetUser is active again, but workspace manager assignment was permanently deleted
        assertThat(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(ws1.getId(), targetUser.getId())).isFalse();
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                targetUser, OrganizationCapability.WORKSPACE_PERMISSIONS_MANAGE, ScopeType.WORKSPACE, ws1.getId(), orgA.getOrgCode())).isFalse();
    }

    private ProjectWorkspaceEntity createWorkspaceInOrg(OrganizationEntity org, UserEntity creator, String name) {
        FolderEntity rootFolder = folderRepository.save(FolderEntity.builder()
                .name(name + "_root")
                .createdBy(creator)
                .ownerOrg(org)
                .build());

        return projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name(name)
                .organization(org)
                .createdBy(creator)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .build());
    }
}
