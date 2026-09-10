package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.*;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Personal Workspace Lifecycle & Security Tests")
class PersonalWorkspaceServiceTest {

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private FolderRepository folderRepository;

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

    private UserEntity ownerUser;
    private UserEntity otherUser;
    private FolderEntity personalRootFolder;
    private ProjectWorkspaceEntity personalWorkspace;

    @BeforeEach
    void setUp() {
        ownerUser = UserEntity.builder()
                .id("owner-user-id")
                .email("owner@example.com")
                .firstName("Alice")
                .lastName("Owner")
                .role(Role.ROLE_USER)
                .build();

        otherUser = UserEntity.builder()
                .id("other-user-id")
                .email("other@example.com")
                .firstName("Bob")
                .lastName("Other")
                .role(Role.ROLE_USER)
                .build();

        personalRootFolder = FolderEntity.builder()
                .id(100L)
                .name("My Personal Space")
                .parentFolder(null)
                .ownerOrg(null)
                .createdBy(ownerUser)
                .subspaceType(SubspaceType.PERSONAL)
                .isDefaultSubspace(true)
                .build();

        personalWorkspace = ProjectWorkspaceEntity.builder()
                .id(1L)
                .organization(null)
                .name("My Personal Space")
                .description("Alice's personal workspace")
                .rootFolder(personalRootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(ownerUser)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── Creation ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("createPersonalWorkspace")
    class CreatePersonalWorkspaceTests {

        @Test
        @DisplayName("should successfully create a personal workspace with new root folder")
        void shouldCreatePersonalWorkspaceSuccessfully() {
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name("Personal Projects")
                    .description("Personal work documents")
                    .build();

            when(projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNull("owner-user-id", "Personal Projects"))
                    .thenReturn(false);
            when(projectWorkspaceRepository.existsByCreatedBy_IdAndOrganizationIsNull("owner-user-id"))
                    .thenReturn(false); // first personal workspace => isDefaultSubspace = true

            when(folderRepository.save(any(FolderEntity.class))).thenAnswer(invocation -> {
                FolderEntity f = invocation.getArgument(0);
                f.setId(200L);
                return f;
            });

            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(invocation -> {
                ProjectWorkspaceEntity ws = invocation.getArgument(0);
                ws.setId(10L);
                return ws;
            });

            ProjectWorkspaceResponseDto response = workspaceService.createPersonalWorkspace(request, ownerUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(10L);
            assertThat(response.getName()).isEqualTo("Personal Projects");
            assertThat(response.getDescription()).isEqualTo("Personal work documents");
            assertThat(response.getOrgCode()).isNull();
            assertThat(response.getCreatedByUserId()).isEqualTo("owner-user-id");
            assertThat(response.getRootFolderId()).isEqualTo(200L);

            verify(folderRepository).save(argThat(folder ->
                    folder.getName().equals("Personal Projects")
                            && folder.getOwnerOrg() == null
                            && folder.getCreatedBy().equals(ownerUser)
                            && folder.isDefaultSubspace()
            ));

            verify(projectWorkspaceRepository).save(argThat(ws ->
                    ws.getOrganization() == null
                            && ws.getCreatedBy().equals(ownerUser)
                            && ws.getStatus() == ProjectWorkspaceStatus.ACTIVE
            ));
        }

        @Test
        @DisplayName("should mark subsequent personal workspaces as isDefaultSubspace = false")
        void shouldMarkSubsequentPersonalWorkspaceAsNotDefault() {
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name("Second Personal Space")
                    .build();

            when(projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNull("owner-user-id", "Second Personal Space"))
                    .thenReturn(false);
            when(projectWorkspaceRepository.existsByCreatedBy_IdAndOrganizationIsNull("owner-user-id"))
                    .thenReturn(true); // already has a personal workspace

            when(folderRepository.save(any(FolderEntity.class))).thenAnswer(inv -> {
                FolderEntity f = inv.getArgument(0);
                f.setId(201L);
                return f;
            });
            when(projectWorkspaceRepository.save(any(ProjectWorkspaceEntity.class))).thenAnswer(inv -> {
                ProjectWorkspaceEntity ws = inv.getArgument(0);
                ws.setId(11L);
                return ws;
            });

            workspaceService.createPersonalWorkspace(request, ownerUser);

            verify(folderRepository).save(argThat(folder -> !folder.isDefaultSubspace()));
        }

        @Test
        @DisplayName("should reject duplicate personal workspace name for same user")
        void shouldRejectDuplicateWorkspaceNameForSameUser() {
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name("My Personal Space")
                    .build();

            when(projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNull("owner-user-id", "My Personal Space"))
                    .thenReturn(true);

            assertThatThrownBy(() -> workspaceService.createPersonalWorkspace(request, ownerUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("should reject creation when unauthenticated")
        void shouldRejectCreationWhenUnauthenticated() {
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name("My Space")
                    .build();

            assertThatThrownBy(() -> workspaceService.createPersonalWorkspace(request, null))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Authentication required");
        }

        @Test
        @DisplayName("should reject creation when workspace name is blank")
        void shouldRejectCreationWhenNameIsBlank() {
            CreatePersonalWorkspaceRequestDto request = CreatePersonalWorkspaceRequestDto.builder()
                    .name("   ")
                    .build();

            assertThatThrownBy(() -> workspaceService.createPersonalWorkspace(request, ownerUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Workspace name is required");
        }
    }

    // ── Listing & Retrieval ──────────────────────────────────────────────────
    @Nested
    @DisplayName("getPersonalWorkspaces & getWorkspaceById")
    class RetrievalTests {

        @Test
        @DisplayName("should list all personal workspaces for current user")
        void shouldListPersonalWorkspacesForUser() {
            when(projectWorkspaceRepository.findByCreatedBy_IdAndOrganizationIsNull("owner-user-id"))
                    .thenReturn(List.of(personalWorkspace));

            List<ProjectWorkspaceResponseDto> results = workspaceService.getPersonalWorkspaces(ownerUser);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getName()).isEqualTo("My Personal Space");
            assertThat(results.get(0).getOrgCode()).isNull();
        }

        @Test
        @DisplayName("should allow owner to get personal workspace by ID")
        void shouldAllowOwnerToGetPersonalWorkspaceById() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));

            ProjectWorkspaceResponseDto response = workspaceService.getWorkspaceById(1L, ownerUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getName()).isEqualTo("My Personal Space");
        }

        @Test
        @DisplayName("should allow shared user with folder permission to view personal workspace")
        void shouldAllowSharedUserWithPermissionToViewPersonalWorkspace() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(permissionEvaluatorService.resolveMaxPermissionLevel(otherUser, personalRootFolder))
                    .thenReturn(PermissionLevel.VIEW);

            ProjectWorkspaceResponseDto response = workspaceService.getWorkspaceById(1L, otherUser);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should deny non-owner without permissions to view personal workspace")
        void shouldDenyNonOwnerWithoutPermissions() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(permissionEvaluatorService.resolveMaxPermissionLevel(otherUser, personalRootFolder))
                    .thenReturn(null);

            assertThatThrownBy(() -> workspaceService.getWorkspaceById(1L, otherUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have access");
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("updateWorkspace on Personal Workspace")
    class UpdatePersonalWorkspaceTests {

        @Test
        @DisplayName("should allow owner to update name and description and sync root folder name")
        void shouldAllowOwnerToUpdatePersonalWorkspace() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNullAndIdNot("owner-user-id", "Renamed Space", 1L))
                    .thenReturn(false);
            when(projectWorkspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                    .name("Renamed Space")
                    .description("Updated description")
                    .build();

            ProjectWorkspaceResponseDto response = workspaceService.updateWorkspace(1L, request, ownerUser);

            assertThat(response.getName()).isEqualTo("Renamed Space");
            assertThat(response.getDescription()).isEqualTo("Updated description");
            assertThat(personalRootFolder.getName()).isEqualTo("Renamed Space");
            verify(folderRepository).save(personalRootFolder);
        }

        @Test
        @DisplayName("should deny non-owner from updating personal workspace")
        void shouldDenyNonOwnerFromUpdatingPersonalWorkspace() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));

            UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                    .name("Hacked Name")
                    .build();

            assertThatThrownBy(() -> workspaceService.updateWorkspace(1L, request, otherUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the owner can update");
        }

        @Test
        @DisplayName("should reject update if new name collides with user's other workspace")
        void shouldRejectUpdateOnNameCollision() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(projectWorkspaceRepository.existsByCreatedBy_IdAndNameAndOrganizationIsNullAndIdNot("owner-user-id", "Existing Space", 1L))
                    .thenReturn(true);

            UpdateProjectWorkspaceRequestDto request = UpdateProjectWorkspaceRequestDto.builder()
                    .name("Existing Space")
                    .build();

            assertThatThrownBy(() -> workspaceService.updateWorkspace(1L, request, ownerUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }
    }

    // ── Archive & Restore ────────────────────────────────────────────────────
    @Nested
    @DisplayName("archiveWorkspace & restoreWorkspace on Personal Workspace")
    class ArchiveRestoreTests {

        @Test
        @DisplayName("should allow owner to archive and restore personal workspace")
        void shouldAllowOwnerToArchiveAndRestore() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(projectWorkspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Archive
            ProjectWorkspaceResponseDto archived = workspaceService.archiveWorkspace(1L, ownerUser);
            assertThat(archived.getStatus()).isEqualTo(ProjectWorkspaceStatus.ARCHIVED);

            // Restore
            ProjectWorkspaceResponseDto restored = workspaceService.restoreWorkspace(1L, ownerUser);
            assertThat(restored.getStatus()).isEqualTo(ProjectWorkspaceStatus.ACTIVE);
        }

        @Test
        @DisplayName("should deny non-owner from archiving personal workspace")
        void shouldDenyNonOwnerFromArchiving() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));

            assertThatThrownBy(() -> workspaceService.archiveWorkspace(1L, otherUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the owner can archive");
        }

        @Test
        @DisplayName("should deny non-owner from restoring personal workspace")
        void shouldDenyNonOwnerFromRestoring() {
            personalWorkspace.setStatus(ProjectWorkspaceStatus.ARCHIVED);
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));

            assertThatThrownBy(() -> workspaceService.restoreWorkspace(1L, otherUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the owner can restore");
        }
    }

    // ── Assignments & Sharing ────────────────────────────────────────────────
    @Nested
    @DisplayName("Assignments & USER_PERSONAL Sharing on Personal Workspace")
    class AssignmentsTests {

        @Test
        @DisplayName("should reject team assignment on personal workspace")
        void shouldRejectTeamAssignmentOnPersonalWorkspace() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));

            WorkspaceTeamAssignmentRequestDto request = WorkspaceTeamAssignmentRequestDto.builder()
                    .teamId(50L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            assertThatThrownBy(() -> workspaceService.assignTeamToWorkspace(1L, request, ownerUser))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Team assignments are not supported for personal workspaces");
        }

        @Test
        @DisplayName("should allow owner to assign user via USER_PERSONAL recipient type")
        void shouldAllowOwnerToAssignUserToPersonalWorkspaceFolder() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(personalRootFolder));
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

            PermissionResponseDto permResponse = PermissionResponseDto.builder()
                    .id(500L)
                    .folderId(100L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .permissionType(PermissionType.ALLOW)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .createdAt(Instant.now())
                    .build();

            when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("owner-user-id")))
                    .thenReturn(permResponse);

            WorkspaceUserAssignmentRequestDto request = WorkspaceUserAssignmentRequestDto.builder()
                    .userEmail("other@example.com")
                    .folderId(100L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            WorkspaceAssignmentResponseDto response = workspaceService.assignUserToWorkspace(1L, request, ownerUser);

            assertThat(response).isNotNull();
            assertThat(response.getPermissionId()).isEqualTo(500L);
            assertThat(response.getUserEmail()).isEqualTo("other@example.com");

            verify(permissionService).grantPermission(argThat(p ->
                    p.getRecipientType() == RecipientType.USER_PERSONAL
                            && p.getTargetOrgCode() == null
                            && p.getTargetUserEmail().equals("other@example.com")
            ), eq("owner-user-id"));
        }

        @Test
        @DisplayName("should deny non-owner from assigning users to personal workspace")
        void shouldDenyNonOwnerFromAssigningUsersToPersonalWorkspace() {
            when(projectWorkspaceRepository.findById(1L)).thenReturn(Optional.of(personalWorkspace));
            when(folderRepository.findById(100L)).thenReturn(Optional.of(personalRootFolder));
            when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

            WorkspaceUserAssignmentRequestDto request = WorkspaceUserAssignmentRequestDto.builder()
                    .userEmail("other@example.com")
                    .folderId(100L)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            assertThatThrownBy(() -> workspaceService.assignUserToWorkspace(1L, request, otherUser))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Only the owner can assign");
        }
    }
}
