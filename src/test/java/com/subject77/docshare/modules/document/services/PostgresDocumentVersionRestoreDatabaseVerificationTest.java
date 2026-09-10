package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.RestoreVersionRequest;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.DocumentVersionEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class PostgresDocumentVersionRestoreDatabaseVerificationTest {

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private PermissionEvaluatorService permissionEvaluatorService;

    @MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private UserEntity owner;
    private UserEntity prevAuthor;
    private UserEntity managerUser;
    private UserEntity restorer;
    private OrganizationEntity org;
    private ProjectWorkspaceEntity workspace;
    private FolderEntity rootFolder;
    private DocumentEntity document;
    private DocumentVersionEntity v1;
    private DocumentVersionEntity v2;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Users
        owner = userRepository.save(UserEntity.builder()
                .id("pg_owner_" + suffix)
                .email("pg_owner_" + suffix + "@example.com")
                .firstName("PgOwner")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        prevAuthor = userRepository.save(UserEntity.builder()
                .id("pg_prev_" + suffix)
                .email("pg_prev_" + suffix + "@example.com")
                .firstName("PgPrev")
                .lastName("Author")
                .role(Role.ROLE_USER)
                .build());

        managerUser = userRepository.save(UserEntity.builder()
                .id("pg_mgr_" + suffix)
                .email("pg_mgr_" + suffix + "@example.com")
                .firstName("PgManager")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        restorer = userRepository.save(UserEntity.builder()
                .id("pg_restorer_" + suffix)
                .email("pg_restorer_" + suffix + "@example.com")
                .firstName("PgRestorer")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        // 2. Create Organization
        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("PG_ORG_" + suffix)
                .name("Postgres Org " + suffix)
                .createdBy(owner)
                .build());

        // 3. Register Active Org Memberships
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(owner).organization(org).status(MembershipStatus.ACTIVE).role(Role.ROLE_USER).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(prevAuthor).organization(org).status(MembershipStatus.ACTIVE).role(Role.ROLE_USER).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(managerUser).organization(org).status(MembershipStatus.ACTIVE).role(Role.ROLE_USER).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(restorer).organization(org).status(MembershipStatus.ACTIVE).role(Role.ROLE_USER).build());

        // 4. Create Folder & Workspace
        rootFolder = folderRepository.save(FolderEntity.builder()
                .name("PgRootFolder_" + suffix)
                .ownerOrg(org)
                .createdBy(owner)
                .build());

        workspace = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("PgWorkspace_" + suffix)
                .organization(org)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(owner)
                .build());

        workspaceManagerRepository.save(WorkspaceManagerEntity.builder()
                .workspace(workspace)
                .user(managerUser)
                .build());

        // 5. Create Document with v1 and v2 (v2 active) in Postgres
        document = documentRepository.save(DocumentEntity.builder()
                .fileName("financial_report_pg.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .fileHash("hash-v2-pg")
                .s3Bucket("test-bucket")
                .objectKey("orgs/" + org.getOrgCode() + "/doc-" + suffix + "-v2.pdf")
                .status(DocumentStatus.ACTIVE)
                .ownerOrg(org)
                .uploadedBy(owner)
                .folder(rootFolder)
                .version(1)
                .build());

        v1 = documentVersionRepository.save(DocumentVersionEntity.builder()
                .document(document)
                .versionNumber(1)
                .s3Bucket("test-bucket")
                .objectKey("orgs/" + org.getOrgCode() + "/doc-" + suffix + "-v1.pdf")
                .fileName("financial_report_v1.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .checksumSha256("hash-v1-pg")
                .uploadedBy(owner)
                .uploadedAt(Instant.now().minusSeconds(3600))
                .build());

        v2 = documentVersionRepository.save(DocumentVersionEntity.builder()
                .document(document)
                .versionNumber(2)
                .s3Bucket("test-bucket")
                .objectKey("orgs/" + org.getOrgCode() + "/doc-" + suffix + "-v2.pdf")
                .fileName("financial_report_v2.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .checksumSha256("hash-v2-pg")
                .uploadedBy(prevAuthor)
                .uploadedAt(Instant.now().minusSeconds(1800))
                .build());

        document.setCurrentVersion(v2);
        document = documentRepository.save(document);

        when(permissionEvaluatorService.canEditDocument(any(), any())).thenReturn(true);
        when(storageService.doesObjectExist(anyString(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Verify real PostgreSQL: schema constraint, append-only version, atomic governance audit, and notification routing")
    void verifyPostgreSqlVersionRestore_DatabaseState_AuditRows_And_Notifications() {
        RestoreVersionRequest request = RestoreVersionRequest.builder()
                .reason("PostgreSQL end-to-end verification")
                .build();

        DocumentResponse response = documentService.restoreVersion(
                document.getId(), 1, request, restorer, "192.168.1.100", "Postgres-Verifier");

        assertThat(response).isNotNull();

        // 1. Inspect PostgreSQL document_versions rows directly with raw SQL
        List<Map<String, Object>> pgVersions = jdbcTemplate.queryForList(
                "SELECT id, version_number, checksum_sha256, file_size, s3_bucket, object_key, uploaded_by_user_id " +
                "FROM document_versions WHERE document_id = ? ORDER BY version_number ASC",
                document.getId());

        assertThat(pgVersions).hasSize(3);

        Map<String, Object> pgV1 = pgVersions.get(0);
        assertThat(pgV1.get("version_number")).isEqualTo(1);
        assertThat(pgV1.get("checksum_sha256")).isEqualTo("hash-v1-pg");
        assertThat(pgV1.get("uploaded_by_user_id")).isEqualTo(owner.getId());

        Map<String, Object> pgV2 = pgVersions.get(1);
        assertThat(pgV2.get("version_number")).isEqualTo(2);
        assertThat(pgV2.get("checksum_sha256")).isEqualTo("hash-v2-pg");
        assertThat(pgV2.get("uploaded_by_user_id")).isEqualTo(prevAuthor.getId());

        Map<String, Object> pgV3 = pgVersions.get(2);
        assertThat(pgV3.get("version_number")).isEqualTo(3);
        assertThat(pgV3.get("checksum_sha256")).isEqualTo("hash-v1-pg");
        assertThat(((Number) pgV3.get("file_size")).longValue()).isEqualTo(1024L);
        assertThat(pgV3.get("object_key")).isEqualTo(v1.getObjectKey());
        assertThat(pgV3.get("s3_bucket")).isEqualTo("test-bucket");
        assertThat(pgV3.get("uploaded_by_user_id")).isEqualTo(restorer.getId());

        Long v3Id = ((Number) pgV3.get("id")).longValue();

        // 2. Inspect PostgreSQL documents row directly
        Map<String, Object> pgDoc = jdbcTemplate.queryForMap(
                "SELECT id, current_version_id, file_hash, last_modified_by_user_id, version " +
                "FROM documents WHERE id = ?", document.getId());

        assertThat(((Number) pgDoc.get("current_version_id")).longValue()).isEqualTo(v3Id);
        assertThat(pgDoc.get("file_hash")).isEqualTo("hash-v1-pg");
        assertThat(pgDoc.get("last_modified_by_user_id")).isEqualTo(restorer.getId());
        assertThat(((Number) pgDoc.get("version")).intValue()).isGreaterThanOrEqualTo(2);

        // 3. Inspect PostgreSQL permission_audit_logs row directly
        List<Map<String, Object>> pgAudits = jdbcTemplate.queryForList(
                "SELECT id, action, target_id, actor_user_id, org_code, workspace_id, folder_id, target_file_hash, reason, details " +
                "FROM permission_audit_logs WHERE action = 'DOCUMENT_VERSION_RESTORED' AND target_id = ?",
                document.getId());

        assertThat(pgAudits).hasSize(1);
        Map<String, Object> audit = pgAudits.get(0);
        assertThat(audit.get("actor_user_id")).isEqualTo(restorer.getId());
        assertThat(audit.get("org_code")).isEqualTo(org.getOrgCode());
        assertThat(((Number) audit.get("workspace_id")).longValue()).isEqualTo(workspace.getId());
        assertThat(((Number) audit.get("folder_id")).longValue()).isEqualTo(rootFolder.getId());
        assertThat(audit.get("target_file_hash")).isEqualTo("hash-v1-pg");
        assertThat(audit.get("reason")).isEqualTo("PostgreSQL end-to-end verification");
        assertThat((String) audit.get("details")).contains("previous active version: v2");

        // 4. Inspect PostgreSQL notifications rows directly
        List<Map<String, Object>> pgNotifs = jdbcTemplate.queryForList(
                "SELECT id, recipient_id, idempotency_key, type, source_type, source_id " +
                "FROM notifications WHERE type = 'DOCUMENT_VERSION_RESTORED' AND source_id = ?",
                String.valueOf(document.getId()));

        assertThat(pgNotifs).hasSize(3);
        List<String> recipients = pgNotifs.stream()
                .map(n -> (String) n.get("recipient_id"))
                .toList();
        assertThat(recipients).containsExactlyInAnyOrder(owner.getId(), prevAuthor.getId(), managerUser.getId());
        assertThat(recipients).doesNotContain(restorer.getId());

        for (Map<String, Object> n : pgNotifs) {
            String recipientId = (String) n.get("recipient_id");
            assertThat((String) n.get("idempotency_key"))
                    .isEqualTo("DOCUMENT_VERSION_RESTORED:" + v3Id + ":" + recipientId);
        }
    }

    @Test
    @DisplayName("Verify real PostgreSQL concurrency & optimistic locking: exactly one winner, zero duplicate/orphan versions, atomic audit")
    void verifyPostgreSqlUniqueConstraint_And_OptimisticLockingConcurrency() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    documentService.restoreVersion(document.getId(), 1, null, restorer, "10.0.0.1", "PgThreadTest");
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    if (ex.getCause() instanceof ObjectOptimisticLockingFailureException
                            || ex.getMessage().contains("Row was updated or deleted by another transaction")
                            || ex.getMessage().contains("duplicate key value violates unique constraint")) {
                        failureCount.incrementAndGet();
                    } else {
                        ex.printStackTrace();
                    }
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(1);

        // Verify Real PostgreSQL Database Outcome:
        // 1. Exactly 3 version rows in PostgreSQL (v1, v2, and the single winner v3)
        List<Map<String, Object>> pgVersions = jdbcTemplate.queryForList(
                "SELECT id, version_number FROM document_versions WHERE document_id = ? ORDER BY version_number ASC",
                document.getId());
        assertThat(pgVersions).hasSize(3);
        assertThat(pgVersions.get(2).get("version_number")).isEqualTo(3);
        Long winnerId = ((Number) pgVersions.get(2).get("id")).longValue();

        // 2. Documents table current_version_id in PostgreSQL points to the winning v3
        Map<String, Object> pgDoc = jdbcTemplate.queryForMap(
                "SELECT current_version_id FROM documents WHERE id = ?", document.getId());
        assertThat(((Number) pgDoc.get("current_version_id")).longValue()).isEqualTo(winnerId);

        // 3. Exactly one audit record was committed to PostgreSQL; the failing thread left NO partial audit record
        List<Map<String, Object>> pgAudits = jdbcTemplate.queryForList(
                "SELECT id FROM permission_audit_logs WHERE action = 'DOCUMENT_VERSION_RESTORED' AND target_id = ?",
                document.getId());
        assertThat(pgAudits).hasSize(1);
    }
}
