package com.subject77.docshare.modules.audit;

import com.subject77.docshare.modules.audit.dto.DocumentActivityLogResponse;
import com.subject77.docshare.modules.audit.dto.PermissionAuditLogResponse;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.AuditEventType;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuditQueryEngineAndTaxonomyIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private OrganizationEntity orgA;
    private UserEntity orgAdminA;
    private UserEntity regularUser;
    private ProjectWorkspaceEntity workspaceA;
    private FolderEntity rootFolderA;
    private DocumentEntity documentA;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        orgAdminA = userRepository.save(UserEntity.builder()
                .id("admin-" + uid)
                .email("admin-" + uid + "@test.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        regularUser = userRepository.save(UserEntity.builder()
                .id("user-" + uid)
                .email("user-" + uid + "@test.com")
                .role(Role.ROLE_USER)
                .build());

        orgA = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_" + uid.toUpperCase())
                .name("Org " + uid)
                .createdBy(orgAdminA)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdminA).organization(orgA).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(regularUser).organization(orgA).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());

        rootFolderA = folderRepository.save(FolderEntity.builder()
                .name("Root Folder " + uid)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        workspaceA = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace " + uid)
                .organization(orgA)
                .rootFolder(rootFolderA)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(orgAdminA)
                .build());

        documentA = documentRepository.save(DocumentEntity.builder()
                .fileName("DocumentA.pdf")
                .contentType("application/pdf")
                .s3Bucket("test-bucket")
                .objectKey("orgA/" + uid + "/docA.pdf")
                .fileSize(2048L)
                .fileHash("hash-doc-a-" + uid)
                .folder(rootFolderA)
                .ownerOrg(orgA)
                .uploadedBy(regularUser)
                .status(DocumentStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("6B.3: Bounded pagination clamps page size to max 100")
    void testBoundedPaginationSizeClamping() {
        // Log 5 activity entries
        for (int i = 0; i < 5; i++) {
            auditLogService.logDocumentActivity(
                    Action.VIEW_METADATA,
                    documentA.getId(),
                    documentA.getFileHash(),
                    rootFolderA.getId(),
                    regularUser.getId(),
                    regularUser.getEmail(),
                    orgA.getOrgCode(),
                    "10.0.0.1",
                    "UA",
                    "Batch View " + i,
                    null
            );
        }

        // Request an oversized page: size = 500
        Pageable requested = PageRequest.of(0, 500);
        Page<DocumentActivityLogResponse> page = auditLogService.queryActivityLogs(
                documentA.getId(), null, null, orgA.getOrgCode(), null, null, null, null, null, null, null, null, requested, orgAdminA);

        assertNotNull(page);
        assertEquals(100, page.getPageable().getPageSize(), "Page size must be clamped to max 100");
        assertTrue(page.getTotalElements() >= 5);
    }

    @Test
    @DisplayName("6B.3: Zero hidden 30-day default — historical records >60 days old are discovered")
    void testNoHiddenThirtyDayDefault() {
        // Save an access log from 90 days ago directly to test repository
        Instant ninetyDaysAgo = Instant.now().minus(90, ChronoUnit.DAYS);
        accessLogRepository.save(com.subject77.docshare.modules.audit.entities.AccessLogEntity.builder()
                .action(Action.DOWNLOAD)
                .documentId(documentA.getId())
                .documentFileHash(documentA.getFileHash())
                .folder(rootFolderA)
                .workspaceId(workspaceA.getId())
                .actorUserId(regularUser.getId())
                .accessorEmail(regularUser.getEmail())
                .orgCode(orgA.getOrgCode())
                .ipAddress("10.0.0.99")
                .userAgent("LegacyAgent")
                .details("Historical Download 90 Days Ago")
                .timestamp(ninetyDaysAgo)
                .build());

        // Query without date filters
        Page<DocumentActivityLogResponse> result = auditLogService.queryActivityLogs(
                documentA.getId(), null, null, orgA.getOrgCode(), null, null, null, null, null, null, null, null,
                PageRequest.of(0, 20), orgAdminA);

        boolean foundHistorical = result.getContent().stream()
                .anyMatch(l -> "Historical Download 90 Days Ago".equals(l.getDetails()));
        assertTrue(foundHistorical, "Historical records older than 30 days must be discoverable without hidden truncation");
    }

    @Test
    @DisplayName("6B.3: Explicit from and to date filters narrow results")
    void testExplicitDateFiltering() {
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
        Instant fiveHoursAgo = now.minus(5, ChronoUnit.HOURS);

        // Record at 5 hours ago
        accessLogRepository.save(com.subject77.docshare.modules.audit.entities.AccessLogEntity.builder()
                .action(Action.PREVIEW)
                .documentId(documentA.getId())
                .documentFileHash(documentA.getFileHash())
                .folder(rootFolderA)
                .workspaceId(workspaceA.getId())
                .accessorEmail(regularUser.getEmail())
                .orgCode(orgA.getOrgCode())
                .details("5 Hours Ago")
                .timestamp(fiveHoursAgo)
                .build());

        // Record now
        auditLogService.logDocumentActivity(
                Action.PREVIEW,
                documentA.getId(),
                documentA.getFileHash(),
                rootFolderA.getId(),
                regularUser.getId(),
                regularUser.getEmail(),
                orgA.getOrgCode(),
                "10.0.0.1",
                "UA",
                "Just Now",
                null
        );

        // Query with from = 2 hours ago (should find "Just Now" but exclude "5 Hours Ago")
        Instant twoHoursAgo = now.minus(2, ChronoUnit.HOURS);
        Page<DocumentActivityLogResponse> result = auditLogService.queryActivityLogs(
                documentA.getId(), null, null, orgA.getOrgCode(), null, null, null, null, null, null, twoHoursAgo, null,
                PageRequest.of(0, 20), orgAdminA);

        boolean foundJustNow = result.getContent().stream().anyMatch(l -> "Just Now".equals(l.getDetails()));
        boolean foundFiveHoursAgo = result.getContent().stream().anyMatch(l -> "5 Hours Ago".equals(l.getDetails()));

        assertTrue(foundJustNow, "'Just Now' record must be present");
        assertFalse(foundFiveHoursAgo, "'5 Hours Ago' record must be excluded by date filter");
    }

    @Test
    @DisplayName("6B.3: Historical action taxonomy compatibility — preserves raw action string and maps canonical")
    void testAuditActionTaxonomyCompatibility() {
        // 1. New canonical write
        auditLogService.logPermissionChange(
                "PERMISSION_GRANTED",
                "DOCUMENT",
                documentA.getId(),
                documentA.getFileHash(),
                rootFolderA.getId(),
                null,
                orgA.getOrgCode(),
                orgAdminA,
                regularUser.getEmail(),
                null,
                com.subject77.docshare.modules.permission.entities.PermissionLevel.VIEW,
                Instant.now(),
                null
        );

        // 2. Legacy alias write
        auditLogService.logPermissionChange(
                "UPDATE_MEMBER_ROLE",
                "ORGANIZATION",
                null,
                null,
                null,
                null,
                orgA.getOrgCode(),
                orgAdminA,
                regularUser.getEmail(),
                null,
                null,
                Instant.now(),
                null
        );

        // 3. Unknown legacy action write
        auditLogService.logPermissionChange(
                "CUSTOM_UNKNOWN_HISTORICAL_1999",
                "ORGANIZATION",
                null,
                null,
                null,
                null,
                orgA.getOrgCode(),
                orgAdminA,
                regularUser.getEmail(),
                null,
                null,
                Instant.now(),
                null
        );

        Page<PermissionAuditLogResponse> page = auditLogService.queryGovernanceLogs(
                null, null, null, null, orgA.getOrgCode(), null, null, null, null, null, null,
                PageRequest.of(0, 20), orgAdminA);

        List<PermissionAuditLogResponse> logs = page.getContent();

        PermissionAuditLogResponse canonicalLog = logs.stream()
                .filter(l -> "PERMISSION_GRANTED".equals(l.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals(AuditEventType.PERMISSION_GRANTED, canonicalLog.getCanonicalAction());

        PermissionAuditLogResponse legacyAliasLog = logs.stream()
                .filter(l -> "UPDATE_MEMBER_ROLE".equals(l.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals("UPDATE_MEMBER_ROLE", legacyAliasLog.getAction(), "Raw historical action string must be preserved");
        assertEquals(AuditEventType.ORG_MEMBER_ROLE_UPDATED, legacyAliasLog.getCanonicalAction(), "Legacy alias must map to canonical enum");

        PermissionAuditLogResponse unknownLog = logs.stream()
                .filter(l -> "CUSTOM_UNKNOWN_HISTORICAL_1999".equals(l.getAction()))
                .findFirst()
                .orElseThrow();
        assertEquals("CUSTOM_UNKNOWN_HISTORICAL_1999", unknownLog.getAction(), "Unknown raw string must be preserved");
        assertEquals(AuditEventType.LEGACY_CUSTOM, unknownLog.getCanonicalAction(), "Unknown action maps safely to LEGACY_CUSTOM");
    }

    @Test
    @DisplayName("6B.3: Governance mutation audit participates in business transaction — rollback rolls back audit record")
    void testGovernanceMutationTransactionRollback() {
        long initialAuditCount = permissionAuditLogRepository.count();

        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus status = transactionManager.getTransaction(def);

        try {
            // Write governance audit inside transaction
            auditLogService.logPermissionChange(
                    "REVOKED",
                    "DOCUMENT",
                    documentA.getId(),
                    documentA.getFileHash(),
                    rootFolderA.getId(),
                    null,
                    orgA.getOrgCode(),
                    orgAdminA,
                    regularUser.getEmail(),
                    null,
                    null,
                    Instant.now(),
                    null
            );

            // Trigger rollback
            transactionManager.rollback(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
        }

        long countAfterRollback = permissionAuditLogRepository.count();
        assertEquals(initialAuditCount, countAfterRollback, "Audit record must be rolled back with the transaction");
    }
}
