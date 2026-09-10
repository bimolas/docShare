package com.subject77.docshare.modules.folder.services;

import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class PostgresLegacySubspaceDatabaseVerificationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @Test
    @DisplayName("Real PostgreSQL database verification for legacy /subspaces creation and lifecycle")
    void verifyPostgreSqlLegacySubspaceFlow() {
        String orgCode = "pg-verification-org";
        String adminUserId = "73830aed-0aed-4169-9666-f96d71afa620";
        UserEntity orgAdmin = userRepository.findById(adminUserId).orElseThrow();

        String subspaceName = "PG-Audit-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Baseline counts before creation
        Integer permissionsBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM permissions", Integer.class);
        Integer managersBefore = jdbcTemplate.queryForObject("SELECT count(*) FROM workspace_managers", Integer.class);

        // 1. Create legacy subspace through folderService.createSubspace (POST /subspaces flow)
        FolderResponseDto response = folderService.createSubspace(orgCode,
                FolderRequestDto.builder().name(subspaceName).build(), orgAdmin);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo(subspaceName);
        assertThat(response.getParentFolderId()).isNull();

        Long rootFolderId = response.getId();

        // 2. Database verification via read-only SQL queries
        // - Exactly 1 ProjectWorkspaceEntity created for that name
        List<Map<String, Object>> workspaces = jdbcTemplate.queryForList(
                "SELECT * FROM project_workspaces WHERE org_code = ? AND name = ?", orgCode, subspaceName);
        assertThat(workspaces).hasSize(1);
        Map<String, Object> wsRow = workspaces.get(0);
        Long workspaceId = ((Number) wsRow.get("id")).longValue();
        Long wsRootFolderId = ((Number) wsRow.get("root_folder_id")).longValue();
        String wsStatus = (String) wsRow.get("status");

        assertThat(wsStatus).isEqualTo("ACTIVE");

        // - Exactly 1 corresponding root FolderEntity
        List<Map<String, Object>> rootFolders = jdbcTemplate.queryForList(
                "SELECT * FROM folders WHERE owner_org_code = ? AND name = ? AND parent_folder_id IS NULL", orgCode, subspaceName);
        assertThat(rootFolders).hasSize(1);
        Map<String, Object> folderRow = rootFolders.get(0);
        Long actualFolderId = ((Number) folderRow.get("id")).longValue();

        // - workspace.rootFolder.id == root folder.id
        assertThat(wsRootFolderId).isEqualTo(rootFolderId);
        assertThat(actualFolderId).isEqualTo(rootFolderId);

        // - root.parentFolder == null
        assertThat(folderRow.get("parent_folder_id")).isNull();

        // - no second root exists for that name
        Integer rootCountForName = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM folders WHERE owner_org_code = ? AND name = ? AND parent_folder_id IS NULL",
                Integer.class, orgCode, subspaceName);
        assertThat(rootCountForName).isEqualTo(1);

        // - no PermissionEntity created unexpectedly
        Integer permissionsAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM permissions", Integer.class);
        assertThat(permissionsAfter).isEqualTo(permissionsBefore);

        // - no WorkspaceManagerEntity created unexpectedly
        Integer managersAfter = jdbcTemplate.queryForObject("SELECT count(*) FROM workspace_managers", Integer.class);
        assertThat(managersAfter).isEqualTo(managersBefore);

        // 3. Verify GET /subspaces contains the new workspace root
        List<FolderResponseDto> listBeforeArchive = folderService.getOrganizationSubspaces(orgCode, orgAdmin);
        assertThat(listBeforeArchive).extracting(FolderResponseDto::getName).contains(subspaceName);

        // 4. Archive the workspace
        projectWorkspaceService.archiveWorkspace(workspaceId, orgAdmin);

        // Verify status in DB is ARCHIVED
        String updatedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM project_workspaces WHERE id = ?", String.class, workspaceId);
        assertThat(updatedStatus).isEqualTo("ARCHIVED");

        // 5. Verify GET /subspaces excludes the archived workspace root
        List<FolderResponseDto> listAfterArchive = folderService.getOrganizationSubspaces(orgCode, orgAdmin);
        assertThat(listAfterArchive).extracting(FolderResponseDto::getName).doesNotContain(subspaceName);

        // 6. Restore the workspace
        projectWorkspaceService.restoreWorkspace(workspaceId, orgAdmin);

        // Verify status in DB is ACTIVE
        String restoredStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM project_workspaces WHERE id = ?", String.class, workspaceId);
        assertThat(restoredStatus).isEqualTo("ACTIVE");

        // 7. Verify GET /subspaces includes the restored workspace root
        List<FolderResponseDto> listAfterRestore = folderService.getOrganizationSubspaces(orgCode, orgAdmin);
        assertThat(listAfterRestore).extracting(FolderResponseDto::getName).contains(subspaceName);
    }
}
