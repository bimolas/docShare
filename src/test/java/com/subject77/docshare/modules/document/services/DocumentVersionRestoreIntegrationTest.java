package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
public class DocumentVersionRestoreIntegrationTest {

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

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private PermissionEvaluatorService permissionEvaluatorService;

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
                .id("owner_" + suffix)
                .email("owner_" + suffix + "@example.com")
                .firstName("Owner")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        prevAuthor = userRepository.save(UserEntity.builder()
                .id("prev_" + suffix)
                .email("prev_" + suffix + "@example.com")
                .firstName("Prev")
                .lastName("Author")
                .role(Role.ROLE_USER)
                .build());

        managerUser = userRepository.save(UserEntity.builder()
                .id("mgr_" + suffix)
                .email("mgr_" + suffix + "@example.com")
                .firstName("Manager")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        restorer = userRepository.save(UserEntity.builder()
                .id("restorer_" + suffix)
                .email("restorer_" + suffix + "@example.com")
                .firstName("Restorer")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        // 2. Create Organization
        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_" + suffix)
                .name("Test Org " + suffix)
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
                .name("RootFolder_" + suffix)
                .ownerOrg(org)
                .createdBy(owner)
                .build());

        workspace = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace_" + suffix)
                .organization(org)
                .rootFolder(rootFolder)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(owner)
                .build());

        workspaceManagerRepository.save(WorkspaceManagerEntity.builder()
                .workspace(workspace)
                .user(managerUser)
                .build());

        // 5. Create Document with v1 and v2 (v2 active)
        document = documentRepository.save(DocumentEntity.builder()
                .fileName("financial_report.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .fileHash("hash-v2")
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
                .checksumSha256("hash-v1")
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
                .checksumSha256("hash-v2")
                .uploadedBy(prevAuthor)
                .uploadedAt(Instant.now().minusSeconds(1800))
                .build());

        document.setCurrentVersion(v2);
        document = documentRepository.save(document);

        // Default mock behaviors
        when(permissionEvaluatorService.canEditDocument(any(), any())).thenReturn(true);
        when(storageService.doesObjectExist(anyString(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("Should successfully restore v1: creates v3, updates active, atomical governance audit, and notifications")
    void testSuccessfulRestore_DatabaseOutcome_And_GovernanceAuditAtomicity() {
        RestoreVersionRequest request = RestoreVersionRequest.builder()
                .reason("Reverting quarterly report formula error")
                .build();

        DocumentResponse response = documentService.restoreVersion(
                document.getId(), 1, request, restorer, "10.0.0.1", "JUnit-Agent");

        assertThat(response).isNotNull();

        // 1. Verify Database Outcome for Versions
        List<DocumentVersionEntity> allVersions = documentVersionRepository
                .findByDocument_IdOrderByVersionNumberDesc(document.getId());
        assertThat(allVersions).hasSize(3);

        DocumentVersionEntity v3 = allVersions.get(0);
        assertThat(v3.getVersionNumber()).isEqualTo(3);
        // Byte/hash equivalence from v1
        assertThat(v3.getChecksumSha256()).isEqualTo("hash-v1");
        assertThat(v3.getFileSize()).isEqualTo(1024L);
        assertThat(v3.getObjectKey()).isEqualTo(v1.getObjectKey());
        assertThat(v3.getS3Bucket()).isEqualTo("test-bucket");
        assertThat(v3.getUploadedBy().getId()).isEqualTo(restorer.getId());

        // 2. Verify Historical v1 and v2 are completely unchanged
        DocumentVersionEntity dbV1 = documentVersionRepository.findByDocument_IdAndVersionNumber(document.getId(), 1).orElseThrow();
        assertThat(dbV1.getVersionNumber()).isEqualTo(1);
        assertThat(dbV1.getChecksumSha256()).isEqualTo("hash-v1");
        assertThat(dbV1.getUploadedBy().getId()).isEqualTo(owner.getId());

        DocumentVersionEntity dbV2 = documentVersionRepository.findByDocument_IdAndVersionNumber(document.getId(), 2).orElseThrow();
        assertThat(dbV2.getVersionNumber()).isEqualTo(2);
        assertThat(dbV2.getChecksumSha256()).isEqualTo("hash-v2");
        assertThat(dbV2.getUploadedBy().getId()).isEqualTo(prevAuthor.getId());

        // 3. Verify DocumentEntity current active pointer
        DocumentEntity updatedDoc = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(updatedDoc.getCurrentVersion().getId()).isEqualTo(v3.getId());
        assertThat(updatedDoc.getFileHash()).isEqualTo("hash-v1");
        assertThat(updatedDoc.getLastModifiedBy().getId()).isEqualTo(restorer.getId());

        // 4. Verify Governance Audit row in permission_audit_logs
        List<PermissionAuditLogEntity> audits = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "DOCUMENT_VERSION_RESTORED".equals(a.getAction()) && document.getId().equals(a.getTargetId()))
                .toList();
        assertThat(audits).hasSize(1);
        PermissionAuditLogEntity audit = audits.get(0);
        assertThat(audit.getActorUserId()).isEqualTo(restorer.getId());
        assertThat(audit.getOrgCode()).isEqualTo(org.getOrgCode());
        assertThat(audit.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(audit.getFolderId()).isEqualTo(rootFolder.getId());
        assertThat(audit.getTargetFileHash()).isEqualTo("hash-v1");
        assertThat(audit.getPreviousValue()).contains("v2");
        assertThat(audit.getNewValue()).contains("v3");
        assertThat(audit.getReason()).isEqualTo("Reverting quarterly report formula error");
        assertThat(audit.getDetails()).contains("previous active version: v2");

        // 5. Verify Notifications: owner, prevAuthor, and managerUser received, restorer excluded
        List<NotificationEntity> notifs = notificationRepository.findAll().stream()
                .filter(n -> NotificationType.DOCUMENT_VERSION_RESTORED.equals(n.getType())
                        && String.valueOf(document.getId()).equals(n.getSourceId()))
                .toList();

        List<String> recipientIds = notifs.stream().map(n -> n.getRecipient().getId()).toList();
        assertThat(recipientIds).containsExactlyInAnyOrder(owner.getId(), prevAuthor.getId(), managerUser.getId());
        assertThat(recipientIds).doesNotContain(restorer.getId());

        // Verify idempotency keys
        for (NotificationEntity n : notifs) {
            assertThat(n.getIdempotencyKey()).isEqualTo("DOCUMENT_VERSION_RESTORED:" + v3.getId() + ":" + n.getRecipient().getId());
        }
    }

    @Test
    @DisplayName("Should reject restore of currently active version with zero database changes")
    void testSameVersionRestore_ThrowsBadRequest_ZeroDbChanges() {
        assertThatThrownBy(() -> documentService.restoreVersion(
                document.getId(), 2, null, restorer, "10.0.0.1", "Agent"))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("already the active version");

        // No new versions in database
        List<DocumentVersionEntity> versions = documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(document.getId());
        assertThat(versions).hasSize(2);
    }

    @Test
    @DisplayName("Should reject restore when storage object is missing with zero database modifications")
    void testStorageObjectMissing_ThrowsNotFound_ZeroDbChanges() {
        when(storageService.doesObjectExist(v1.getS3Bucket(), v1.getObjectKey())).thenReturn(false);

        assertThatThrownBy(() -> documentService.restoreVersion(
                document.getId(), 1, null, restorer, "10.0.0.1", "Agent"))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("File object not found in storage");

        // Exactly 2 versions remain
        assertThat(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(document.getId())).hasSize(2);
        // Active version remains v2
        DocumentEntity currentDoc = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(currentDoc.getCurrentVersion().getId()).isEqualTo(v2.getId());
    }

    @Test
    @DisplayName("Audit scope reflects current workspace and folder when document was moved")
    void testRestoreAfterDocumentMovedWorkspaces_AuditReflectsCurrentScope() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        FolderEntity newRoot = folderRepository.save(FolderEntity.builder()
                .name("NewRootFolder_" + suffix)
                .ownerOrg(org)
                .createdBy(owner)
                .build());

        ProjectWorkspaceEntity newWorkspace = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("NewWorkspace_" + suffix)
                .organization(org)
                .rootFolder(newRoot)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(owner)
                .build());

        // Move document to new folder
        document.setFolder(newRoot);
        document = documentRepository.save(document);

        documentService.restoreVersion(document.getId(), 1, null, restorer, "10.0.0.1", "Agent");

        // Inspect audit row
        PermissionAuditLogEntity audit = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "DOCUMENT_VERSION_RESTORED".equals(a.getAction()) && document.getId().equals(a.getTargetId()))
                .findFirst().orElseThrow();

        assertThat(audit.getWorkspaceId()).isEqualTo(newWorkspace.getId());
        assertThat(audit.getFolderId()).isEqualTo(newRoot.getId());
    }

    @Test
    @DisplayName("Repeated restore produces distinct new monotonic version and distinct notifications")
    void testRepeatedRestore_DistinctIdentity_And_NotificationIdempotency() {
        // First restore v1 -> produces v3
        documentService.restoreVersion(document.getId(), 1, null, restorer, "10.0.0.1", "Agent");
        DocumentEntity docAfterFirst = documentRepository.findById(document.getId()).orElseThrow();
        Long v3Id = docAfterFirst.getCurrentVersion().getId();
        DocumentVersionEntity dbV3 = documentVersionRepository.findById(v3Id).orElseThrow();
        assertThat(dbV3.getVersionNumber()).isEqualTo(3);

        // Upload new version v4
        DocumentVersionEntity v4 = documentVersionRepository.save(DocumentVersionEntity.builder()
                .document(docAfterFirst)
                .versionNumber(4)
                .s3Bucket("test-bucket")
                .objectKey("orgs/" + org.getOrgCode() + "/doc-v4.pdf")
                .fileName("v4.pdf")
                .contentType("application/pdf")
                .fileSize(3000L)
                .checksumSha256("hash-v4")
                .uploadedBy(prevAuthor)
                .uploadedAt(Instant.now())
                .build());
        docAfterFirst.setCurrentVersion(v4);
        documentRepository.save(docAfterFirst);

        // Second restore of v1 -> produces v5!
        documentService.restoreVersion(document.getId(), 1, null, restorer, "10.0.0.1", "Agent");
        DocumentEntity docAfterSecond = documentRepository.findById(document.getId()).orElseThrow();
        Long v5Id = docAfterSecond.getCurrentVersion().getId();
        DocumentVersionEntity dbV5 = documentVersionRepository.findById(v5Id).orElseThrow();
        assertThat(dbV5.getVersionNumber()).isEqualTo(5);
        assertThat(v5Id).isNotEqualTo(v3Id);

        // Notifications for v3 and v5 exist distinctly
        List<NotificationEntity> notifs = notificationRepository.findAll().stream()
                .filter(n -> NotificationType.DOCUMENT_VERSION_RESTORED.equals(n.getType())
                        && String.valueOf(document.getId()).equals(n.getSourceId()))
                .toList();

        boolean hasV3Notif = notifs.stream().anyMatch(n -> n.getIdempotencyKey().contains(":" + v3Id + ":"));
        boolean hasV5Notif = notifs.stream().anyMatch(n -> n.getIdempotencyKey().contains(":" + v5Id + ":"));
        assertThat(hasV3Notif).isTrue();
        assertThat(hasV5Notif).isTrue();
    }

    @Test
    @DisplayName("Suspended organization member is excluded from restore notifications")
    void testSuspendedOrgMember_ExcludedFromNotifications() {
        // Suspend prevAuthor
        UserOrganizationEntity uo = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(prevAuthor.getId(), org.getOrgCode()).orElseThrow();
        uo.setStatus(MembershipStatus.SUSPENDED);
        userOrganizationRepository.save(uo);

        documentService.restoreVersion(document.getId(), 1, null, restorer, "10.0.0.1", "Agent");

        List<NotificationEntity> notifs = notificationRepository.findAll().stream()
                .filter(n -> NotificationType.DOCUMENT_VERSION_RESTORED.equals(n.getType())
                        && String.valueOf(document.getId()).equals(n.getSourceId()))
                .toList();

        List<String> recipients = notifs.stream().map(n -> n.getRecipient().getId()).toList();
        // prevAuthor should NOT receive notification because they are SUSPENDED
        assertThat(recipients).doesNotContain(prevAuthor.getId());
        assertThat(recipients).contains(owner.getId());
    }

    @Test
    @DisplayName("Concurrent restores on same document: exactly one succeeds, one rolls back, no orphan version row")
    void testConcurrentRestores_OptimisticLocking_Outcome() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    documentService.restoreVersion(document.getId(), 1, null, restorer, "10.0.0.1", "ThreadTest");
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException ex) {
                    failureCount.incrementAndGet();
                } catch (Exception ex) {
                    if (ex.getCause() instanceof ObjectOptimisticLockingFailureException
                            || ex.getMessage().contains("Row was updated or deleted by another transaction")) {
                        failureCount.incrementAndGet();
                    } else {
                        ex.printStackTrace();
                    }
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        // Verify Database Outcome:
        // Exactly one new version row (v3) was committed
        List<DocumentVersionEntity> versions = documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(document.getId());
        assertThat(versions).hasSize(3);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(3);

        // Active version points to the single winning version
        DocumentEntity updatedDoc = documentRepository.findById(document.getId()).orElseThrow();
        assertThat(updatedDoc.getCurrentVersion().getId()).isEqualTo(versions.get(0).getId());

        // Exactly one audit record was committed
        List<PermissionAuditLogEntity> audits = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "DOCUMENT_VERSION_RESTORED".equals(a.getAction()) && document.getId().equals(a.getTargetId()))
                .toList();
        assertThat(audits).hasSize(1);
    }
}
