package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.WorkspaceManagerResponseDto;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkspaceManagerServiceImplTest {

    // -----------------------------------------------------------------------
    // Mocks
    // -----------------------------------------------------------------------

    @Mock private WorkspaceManagerRepository workspaceManagerRepository;
    @Mock private ProjectWorkspaceRepository  projectWorkspaceRepository;
    @Mock private UserOrganizationRepository  userOrganizationRepository;
    @Mock private UserRepository              userRepository;
    @Mock private AuditLogService             auditLogService;
    @Mock private NotificationService         notificationService;
    @Mock private com.subject77.docshare.modules.permission.services.PermissionCacheService permissionCacheService;

    @InjectMocks
    private WorkspaceManagerServiceImpl workspaceManagerService;

    // -----------------------------------------------------------------------
    // Shared fixtures
    // -----------------------------------------------------------------------

    private OrganizationEntity org;
    private OrganizationEntity otherOrg;
    private FolderEntity       rootFolder;
    private ProjectWorkspaceEntity workspace;
    private UserEntity         orgAdmin;
    private UserEntity         normalMember;
    private UserEntity         targetUser;

    @BeforeEach
    void setUp() {
        org      = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        otherOrg = OrganizationEntity.builder().orgCode("ORG2").name("Org Two").build();

        rootFolder = FolderEntity.builder()
                .id(10L)
                .name("Root")
                .createdBy(UserEntity.builder().id("sys").build())
                .build();

        workspace = ProjectWorkspaceEntity.builder()
                .id(1L)
                .name("Alpha")
                .organization(org)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(UserEntity.builder().id("sys").build())
                .build();

        orgAdmin = UserEntity.builder()
                .id("admin-1")
                .email("admin@example.com")
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        normalMember = UserEntity.builder()
                .id("member-1")
                .email("member@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();

        targetUser = UserEntity.builder()
                .id("target-1")
                .email("target@example.com")
                .organization(org)
                .role(Role.ROLE_USER)
                .build();
    }

    // -----------------------------------------------------------------------
    // Helper: stub org admin membership check
    // -----------------------------------------------------------------------

    private void stubOrgAdmin(UserEntity admin, String orgCode) {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(admin.getId(), orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(admin)
                        .organization(org)
                        .role(Role.ROLE_ORG_ADMIN)
                        .status(MembershipStatus.ACTIVE)
                        .build()));
    }

    private void stubActiveMember(UserEntity user, String orgCode) {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(user)
                        .organization(org)
                        .role(Role.ROLE_USER)
                        .status(MembershipStatus.ACTIVE)
                        .build()));
    }

    private void stubInactiveMember(UserEntity user, String orgCode) {
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(user.getId(), orgCode))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(user)
                        .organization(org)
                        .role(Role.ROLE_USER)
                        .status(MembershipStatus.SUSPENDED)
                        .build()));
    }

    // -----------------------------------------------------------------------
    // 1. Org admin can assign workspace manager
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("1. Org admin can assign a workspace manager")
    void assignWorkspaceManager_OrgAdmin_Succeeds() {
        stubOrgAdmin(orgAdmin, "ORG1");
        stubActiveMember(targetUser, "ORG1");

        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById("target-1")).thenReturn(Optional.of(targetUser));
        when(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(1L, "target-1")).thenReturn(false);
        when(workspaceManagerRepository.save(any(WorkspaceManagerEntity.class))).thenAnswer(inv -> {
            WorkspaceManagerEntity e = inv.getArgument(0);
            e.setId(100L);
            return e;
        });

        WorkspaceManagerResponseDto dto = workspaceManagerService.assignWorkspaceManager(1L, "target-1", orgAdmin);

        assertThat(dto).isNotNull();
        assertThat(dto.getUserId()).isEqualTo("target-1");
        assertThat(dto.getWorkspaceId()).isEqualTo(1L);
        verify(workspaceManagerRepository).save(any(WorkspaceManagerEntity.class));
        verify(auditLogService).logPermissionChange(eq("WORKSPACE_MANAGER_ASSIGNED"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationService).createNotification(eq(targetUser), eq(orgAdmin), any(), any(), any(), any(), any(), any(), any(), any());
        verify(permissionCacheService).evictUserPermissions(targetUser.getEmail());
    }

    // -----------------------------------------------------------------------
    // 2. Normal member cannot assign
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("2. Normal member cannot assign workspace manager")
    void assignWorkspaceManager_NormalMember_ThrowsAccessDenied() {
        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("member-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(normalMember)
                        .organization(org)
                        .role(Role.ROLE_USER)
                        .status(MembershipStatus.ACTIVE)
                        .build()));

        assertThatThrownBy(() -> workspaceManagerService.assignWorkspaceManager(1L, "target-1", normalMember))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Admin privileges required");
    }

    // -----------------------------------------------------------------------
    // 3. Team manager (ROLE_ORG_MANAGER) cannot assign
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("3. Org Manager (team manager role) cannot assign workspace manager")
    void assignWorkspaceManager_OrgManager_ThrowsAccessDenied() {
        UserEntity orgManager = UserEntity.builder()
                .id("mgr-1").email("mgr@example.com").organization(org).role(Role.ROLE_ORG_MANAGER)
                .build();

        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("mgr-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(orgManager)
                        .organization(org)
                        .role(Role.ROLE_ORG_MANAGER)
                        .status(MembershipStatus.ACTIVE)
                        .build()));

        assertThatThrownBy(() -> workspaceManagerService.assignWorkspaceManager(1L, "target-1", orgManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Admin privileges required");
    }

    // -----------------------------------------------------------------------
    // 4. Cross-organization target user rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("4. Cross-organization target user is rejected during assignment")
    void assignWorkspaceManager_CrossOrgTargetUser_ThrowsIllegalArgument() {
        // target belongs to ORG2, not ORG1
        UserEntity foreignUser = UserEntity.builder()
                .id("foreign-1").email("foreign@example.com").organization(otherOrg).role(Role.ROLE_USER)
                .build();

        stubOrgAdmin(orgAdmin, "ORG1");

        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById("foreign-1")).thenReturn(Optional.of(foreignUser));
        // No active membership record in ORG1 for this user
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("foreign-1", "ORG1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceManagerService.assignWorkspaceManager(1L, "foreign-1", orgAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an active member of organization");
    }

    // -----------------------------------------------------------------------
    // 5. Duplicate assignment rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("5. Duplicate workspace manager assignment is rejected")
    void assignWorkspaceManager_Duplicate_ThrowsIllegalArgument() {
        stubOrgAdmin(orgAdmin, "ORG1");
        stubActiveMember(targetUser, "ORG1");

        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById("target-1")).thenReturn(Optional.of(targetUser));
        when(workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(1L, "target-1")).thenReturn(true);

        assertThatThrownBy(() -> workspaceManagerService.assignWorkspaceManager(1L, "target-1", orgAdmin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already a manager of workspace");
    }

    // -----------------------------------------------------------------------
    // 6. Remove manager succeeds
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("6. Org admin can remove a workspace manager")
    void removeWorkspaceManager_OrgAdmin_Succeeds() {
        stubOrgAdmin(orgAdmin, "ORG1");

        WorkspaceManagerEntity assignment = WorkspaceManagerEntity.builder()
                .id(200L).workspace(workspace).user(targetUser).build();

        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceManagerRepository.findByWorkspace_IdAndUser_Id(1L, "target-1"))
                .thenReturn(Optional.of(assignment));

        workspaceManagerService.removeWorkspaceManager(1L, "target-1", orgAdmin);

        verify(workspaceManagerRepository).delete(assignment);
        verify(notificationService).createNotification(eq(targetUser), eq(orgAdmin), any(), any(), any(), any(), any(), any(), any(), any());
        verify(auditLogService).logPermissionChange(eq("WORKSPACE_MANAGER_REMOVED"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(permissionCacheService).evictUserPermissions(targetUser.getEmail());
    }

    // -----------------------------------------------------------------------
    // 7. Unauthorized removal rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("7. Non-admin cannot remove a workspace manager")
    void removeWorkspaceManager_NonAdmin_ThrowsAccessDenied() {
        when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("member-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(normalMember).organization(org)
                        .role(Role.ROLE_USER).status(MembershipStatus.ACTIVE)
                        .build()));

        assertThatThrownBy(() -> workspaceManagerService.removeWorkspaceManager(1L, "target-1", normalMember))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Organization Admin privileges required");
    }

    // -----------------------------------------------------------------------
    // 8. Workspace manager is recognized for workspace root
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("8. Workspace manager is recognized for the workspace root folder")
    void isWorkspaceManagerForFolder_RootFolder_ReturnsTrue() {
        WorkspaceManagerEntity assignment = WorkspaceManagerEntity.builder()
                .workspace(workspace).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_Id("target-1")).thenReturn(List.of(assignment));
        // Active membership lookup for targetUser in ORG1
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(targetUser).organization(org)
                        .role(Role.ROLE_USER).status(MembershipStatus.ACTIVE)
                        .build()));

        boolean result = workspaceManagerService.isWorkspaceManagerForFolder(targetUser, rootFolder);

        assertThat(result).isTrue();
    }

    // -----------------------------------------------------------------------
    // 9. Workspace manager is recognized for descendant folder
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("9. Workspace manager is recognized for a descendant folder inside the workspace")
    void isWorkspaceManagerForFolder_DescendantFolder_ReturnsTrue() {
        FolderEntity child = FolderEntity.builder()
                .id(20L).name("Child").parentFolder(rootFolder)
                .createdBy(UserEntity.builder().id("sys").build())
                .build();
        FolderEntity grandChild = FolderEntity.builder()
                .id(30L).name("Grand").parentFolder(child)
                .createdBy(UserEntity.builder().id("sys").build())
                .build();

        WorkspaceManagerEntity assignment = WorkspaceManagerEntity.builder()
                .workspace(workspace).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_Id("target-1")).thenReturn(List.of(assignment));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(targetUser).organization(org)
                        .role(Role.ROLE_USER).status(MembershipStatus.ACTIVE)
                        .build()));

        boolean result = workspaceManagerService.isWorkspaceManagerForFolder(targetUser, grandChild);

        assertThat(result).isTrue();
    }

    // -----------------------------------------------------------------------
    // 10. Manager is NOT recognized for another workspace
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("10. Workspace A manager is not recognized for Workspace B folder")
    void isWorkspaceManagerForFolder_OtherWorkspace_ReturnsFalse() {
        FolderEntity otherRoot = FolderEntity.builder()
                .id(99L).name("OtherRoot")
                .createdBy(UserEntity.builder().id("sys").build())
                .build();
        // targetUser is manager of workspace 1 (rootFolder id=10), NOT of otherRoot (id=99)
        WorkspaceManagerEntity assignment = WorkspaceManagerEntity.builder()
                .workspace(workspace).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_Id("target-1")).thenReturn(List.of(assignment));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(targetUser).organization(org)
                        .role(Role.ROLE_USER).status(MembershipStatus.ACTIVE)
                        .build()));

        boolean result = workspaceManagerService.isWorkspaceManagerForFolder(targetUser, otherRoot);

        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // 11. Manager is NOT recognized for unrelated organization
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("11. Manager is not recognized as effective manager for an unrelated organization")
    void isWorkspaceManagerForFolder_NoAssignments_ReturnsFalse() {
        when(workspaceManagerRepository.findByUser_Id("target-1")).thenReturn(List.of());

        boolean result = workspaceManagerService.isWorkspaceManagerForFolder(targetUser, rootFolder);

        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // 12. Inactive organization member is not an effective workspace manager
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("12. Suspended/inactive org member is not an effective workspace manager")
    void isWorkspaceManagerForFolder_InactiveMember_ReturnsFalse() {
        WorkspaceManagerEntity assignment = WorkspaceManagerEntity.builder()
                .workspace(workspace).user(targetUser).build();

        when(workspaceManagerRepository.findByUser_Id("target-1")).thenReturn(List.of(assignment));
        // Membership exists but is SUSPENDED
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("target-1", "ORG1"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder()
                        .user(targetUser).organization(org)
                        .role(Role.ROLE_USER).status(MembershipStatus.SUSPENDED)
                        .build()));
        // Fall-back: targetUser.getOrganization() is ORG1 but MembershipStatus check already fails

        // Override the primary org to be different so the fallback doesn't accidentally pass
        targetUser.setOrganization(otherOrg);

        boolean result = workspaceManagerService.isWorkspaceManagerForFolder(targetUser, rootFolder);

        assertThat(result).isFalse();
    }

    // -----------------------------------------------------------------------
    // Bonus: workspace not found throws ResourceNotFoundException
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Assign throws ResourceNotFoundException when workspace does not exist")
    void assignWorkspaceManager_WorkspaceNotFound_ThrowsResourceNotFound() {
        when(projectWorkspaceRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceManagerService.assignWorkspaceManager(999L, "target-1", orgAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Workspace not found");
    }
}
