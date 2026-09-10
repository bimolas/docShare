package com.subject77.docshare.modules.workspace.services;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.CreatePersonalWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.dto.UpdateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceAssignmentResponseDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceTeamAssignmentRequestDto;
import com.subject77.docshare.modules.workspace.dto.WorkspaceUserAssignmentRequestDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class PostgresPersonalWorkspaceDatabaseVerificationTest {

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.subject77.docshare.modules.policy.services.WorkspacePolicyService workspacePolicyService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @Test
    @DisplayName("Real PostgreSQL database verification for personal workspace lifecycle and schema state")
    void verifyPostgresPersonalWorkspaceLifecycleAndSchema() {
        String adminUserId = "73830aed-0aed-4169-9666-f96d71afa620";
        UserEntity user1 = userRepository.findById(adminUserId).orElseThrow();

        // Find or use a second user in the database
        UserEntity user2 = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(user1.getId()))
                .findFirst()
                .orElse(null);

        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        String wsName = "PG-Personal-" + uniqueSuffix;

        // 1. Create personal workspace
        CreatePersonalWorkspaceRequestDto createDto = CreatePersonalWorkspaceRequestDto.builder()
                .name(wsName)
                .description("Postgres verification test personal workspace")
                .build();

        ProjectWorkspaceResponseDto created = projectWorkspaceService.createPersonalWorkspace(createDto, user1);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getOrgCode()).isNull();
        assertThat(created.getName()).isEqualTo(wsName);
        assertThat(created.getCreatedByUserId()).isEqualTo(user1.getId());

        // 2. Direct SQL assertion on PostgreSQL project_workspaces table
        Map<String, Object> wsRow = jdbcTemplate.queryForMap(
                "SELECT id, name, org_code, created_by_user_id, status, root_folder_id FROM project_workspaces WHERE id = ?",
                created.getId()
        );

        assertThat(wsRow.get("org_code")).isNull();
        assertThat(wsRow.get("created_by_user_id")).isEqualTo(user1.getId());
        assertThat(wsRow.get("name")).isEqualTo(wsName);
        assertThat(wsRow.get("status")).isEqualTo("ACTIVE");

        Long rootFolderId = ((Number) wsRow.get("root_folder_id")).longValue();

        // Direct SQL assertion on PostgreSQL folders table
        Map<String, Object> folderRow = jdbcTemplate.queryForMap(
                "SELECT id, name, owner_org_code, created_by_user_id, subspace_type FROM folders WHERE id = ?",
                rootFolderId
        );

        assertThat(folderRow.get("owner_org_code")).isNull();
        assertThat(folderRow.get("created_by_user_id")).isEqualTo(user1.getId());
        assertThat(folderRow.get("name")).isEqualTo(wsName);
        assertThat(folderRow.get("subspace_type")).isEqualTo("PERSONAL");

        // 3. Duplicate name prevention for the SAME user
        assertThatThrownBy(() -> projectWorkspaceService.createPersonalWorkspace(createDto, user1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        // 4. Same name for a DIFFERENT user succeeds (uniqueness scoped to created_by)
        if (user2 != null) {
            ProjectWorkspaceResponseDto createdUser2 = projectWorkspaceService.createPersonalWorkspace(createDto, user2);
            assertThat(createdUser2).isNotNull();
            assertThat(createdUser2.getName()).isEqualTo(wsName);
            assertThat(createdUser2.getCreatedByUserId()).isEqualTo(user2.getId());

            // Verify both exist in DB with same name but different created_by_user_id
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM project_workspaces WHERE name = ? AND org_code IS NULL",
                    Integer.class,
                    wsName
            );
            assertThat(count).isEqualTo(2);
        }

        // 5. Team assignment must be rejected
        WorkspaceTeamAssignmentRequestDto teamReq = WorkspaceTeamAssignmentRequestDto.builder()
                .teamId(999L)
                .folderId(rootFolderId)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> projectWorkspaceService.assignTeamToWorkspace(created.getId(), teamReq, user1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Team assignments are not supported for personal workspaces");

        // 6. User assignment via USER_PERSONAL recipient type
        if (user2 != null) {
            workspacePolicyService.updatePersonalPolicy(
                    user1.getId(),
                    com.subject77.docshare.modules.policy.entities.ExternalSharingMode.ON,
                    com.subject77.docshare.modules.policy.entities.DiscoverabilityMode.PRIVATE
            );

            WorkspaceUserAssignmentRequestDto userReq = WorkspaceUserAssignmentRequestDto.builder()
                    .userEmail(user2.getEmail())
                    .folderId(rootFolderId)
                    .permissionLevel(PermissionLevel.VIEW)
                    .build();

            WorkspaceAssignmentResponseDto assignRes = projectWorkspaceService.assignUserToWorkspace(created.getId(), userReq, user1);
            assertThat(assignRes).isNotNull();
            assertThat(assignRes.getPermissionId()).isNotNull();

            // Verify permission in DB
            Map<String, Object> permRow = jdbcTemplate.queryForMap(
                    "SELECT id, recipient_type, target_org_code, folder_id FROM permissions WHERE id = ?",
                    assignRes.getPermissionId()
            );
            assertThat(permRow.get("recipient_type")).isEqualTo("USER_PERSONAL");
            assertThat(permRow.get("target_org_code")).isNull();
            assertThat(((Number) permRow.get("folder_id")).longValue()).isEqualTo(rootFolderId);
        }

        // 7. Update / rename personal workspace & root folder synchronization
        String renamedWsName = wsName + "-Renamed";
        UpdateProjectWorkspaceRequestDto updateDto = UpdateProjectWorkspaceRequestDto.builder()
                .name(renamedWsName)
                .description("Updated description")
                .build();

        ProjectWorkspaceResponseDto updated = projectWorkspaceService.updateWorkspace(created.getId(), updateDto, user1);
        assertThat(updated.getName()).isEqualTo(renamedWsName);

        // Verify root folder name synced in DB
        String updatedDbFolderName = jdbcTemplate.queryForObject(
                "SELECT name FROM folders WHERE id = ?",
                String.class,
                rootFolderId
        );
        assertThat(updatedDbFolderName).isEqualTo(renamedWsName);

        // 8. Archive & Restore
        ProjectWorkspaceResponseDto archived = projectWorkspaceService.archiveWorkspace(created.getId(), user1);
        assertThat(archived.getStatus().name()).isEqualTo("ARCHIVED");

        String dbStatusAfterArchive = jdbcTemplate.queryForObject(
                "SELECT status FROM project_workspaces WHERE id = ?",
                String.class,
                created.getId()
        );
        assertThat(dbStatusAfterArchive).isEqualTo("ARCHIVED");

        ProjectWorkspaceResponseDto restored = projectWorkspaceService.restoreWorkspace(created.getId(), user1);
        assertThat(restored.getStatus().name()).isEqualTo("ACTIVE");

        String dbStatusAfterRestore = jdbcTemplate.queryForObject(
                "SELECT status FROM project_workspaces WHERE id = ?",
                String.class,
                created.getId()
        );
        assertThat(dbStatusAfterRestore).isEqualTo("ACTIVE");
    }
}
