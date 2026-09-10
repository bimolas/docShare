package com.subject77.docshare.modules.audit;

import com.subject77.docshare.modules.audit.dto.DocumentActivityLogResponse;
import com.subject77.docshare.modules.audit.dto.PermissionAuditLogResponse;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuditPrivacyAndScopeIntegrationTest {

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
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private OrganizationEntity orgA;
    private OrganizationEntity orgB;
    private UserEntity superAdmin;
    private UserEntity orgAdminA;
    private UserEntity orgAdminB;
    private UserEntity wsManagerA;
    private UserEntity resourceOwner;
    private UserEntity regularVisitor;
    private ProjectWorkspaceEntity workspaceA1;
    private ProjectWorkspaceEntity workspaceA2;
    private FolderEntity rootFolderA1;
    private FolderEntity rootFolderA2;
    private DocumentEntity documentX;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        superAdmin = userRepository.save(UserEntity.builder()
                .id("super-admin-" + uid)
                .email("superadmin-" + uid + "@test.com")
                .role(Role.ROLE_SUPER_ADMIN)
                .build());

        orgAdminA = userRepository.save(UserEntity.builder()
                .id("org-admin-a-" + uid)
                .email("admin-a-" + uid + "@test.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        orgAdminB = userRepository.save(UserEntity.builder()
                .id("org-admin-b-" + uid)
                .email("admin-b-" + uid + "@test.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        wsManagerA = userRepository.save(UserEntity.builder()
                .id("ws-mgr-a-" + uid)
                .email("wsmgr-a-" + uid + "@test.com")
                .role(Role.ROLE_USER)
                .build());

        resourceOwner = userRepository.save(UserEntity.builder()
                .id("owner-" + uid)
                .email("owner-" + uid + "@test.com")
                .role(Role.ROLE_USER)
                .build());

        regularVisitor = userRepository.save(UserEntity.builder()
                .id("visitor-" + uid)
                .email("visitor-" + uid + "@test.com")
                .role(Role.ROLE_USER)
                .build());

        orgA = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_A_" + uid.toUpperCase())
                .name("Organization A " + uid)
                .createdBy(orgAdminA)
                .build());

        orgB = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_B_" + uid.toUpperCase())
                .name("Organization B " + uid)
                .createdBy(orgAdminB)
                .build());

        // Memberships in Org A
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdminA).organization(orgA).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(wsManagerA).organization(orgA).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(resourceOwner).organization(orgA).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(regularVisitor).organization(orgA).role(Role.ROLE_USER).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());

        // Membership in Org B
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdminB).organization(orgB).role(Role.ROLE_ORG_ADMIN).status(MembershipStatus.ACTIVE).joinedAt(Instant.now()).build());

        // Workspaces in Org A
        rootFolderA1 = folderRepository.save(FolderEntity.builder()
                .name("Root Folder A1 " + uid)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        workspaceA1 = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace A1 " + uid)
                .organization(orgA)
                .rootFolder(rootFolderA1)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(orgAdminA)
                .build());

        rootFolderA2 = folderRepository.save(FolderEntity.builder()
                .name("Root Folder A2 " + uid)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        workspaceA2 = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace A2 " + uid)
                .organization(orgA)
                .rootFolder(rootFolderA2)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdBy(orgAdminA)
                .build());

        // Assign wsManagerA to workspaceA1 ONLY
        workspaceManagerRepository.save(WorkspaceManagerEntity.builder()
                .workspace(workspaceA1)
                .user(wsManagerA)
                .build());

        // Create Document X in Workspace A1
        documentX = documentRepository.save(DocumentEntity.builder()
                .fileName("DocumentX.pdf")
                .contentType("application/pdf")
                .s3Bucket("test-bucket")
                .objectKey("orgA/" + uid + "/docX.pdf")
                .fileSize(1024L)
                .fileHash("hash-doc-x-" + uid)
                .folder(rootFolderA1)
                .ownerOrg(orgA)
                .uploadedBy(resourceOwner)
                .status(DocumentStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("6B.1: SUPER_ADMIN receives full raw IP and User-Agent")
    void testSuperAdminReceivesFullSensitiveMetadata() {
        auditLogService.logDocumentActivity(
                Action.DOWNLOAD,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "192.168.1.100",
                "Mozilla/5.0 Chrome/120.0",
                "Downloaded by visitor",
                "tx-123"
        );

        List<DocumentActivityLogResponse> logs = auditLogService.getDocumentActivityLogs(
                documentX.getId(), null, orgA.getOrgCode(), superAdmin);

        assertFalse(logs.isEmpty());
        DocumentActivityLogResponse log = logs.get(0);
        assertEquals("192.168.1.100", log.getIpAddress(), "Super admin must see raw IP");
        assertEquals("Mozilla/5.0 Chrome/120.0", log.getUserAgent(), "Super admin must see raw User-Agent");
    }

    @Test
    @DisplayName("6B.1: ORG_ADMIN of own organization receives full raw IP and User-Agent")
    void testOrgAdminReceivesFullSensitiveMetadata() {
        auditLogService.logDocumentActivity(
                Action.DOWNLOAD,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "10.0.0.50",
                "Firefox/120.0",
                "Downloaded by visitor",
                null
        );

        List<DocumentActivityLogResponse> logs = auditLogService.getDocumentActivityLogs(
                documentX.getId(), null, orgA.getOrgCode(), orgAdminA);

        assertFalse(logs.isEmpty());
        DocumentActivityLogResponse log = logs.get(0);
        assertEquals("10.0.0.50", log.getIpAddress(), "Org Admin must see raw IP in own org");
        assertEquals("Firefox/120.0", log.getUserAgent(), "Org Admin must see raw User-Agent in own org");
    }

    @Test
    @DisplayName("6B.1: WORKSPACE_MANAGER receives NULL for raw IP and User-Agent")
    void testWorkspaceManagerReceivesRedactedMetadata() {
        auditLogService.logDocumentActivity(
                Action.DOWNLOAD,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "172.16.0.5",
                "Safari/17.0",
                "Downloaded by visitor",
                null
        );

        List<DocumentActivityLogResponse> logs = auditLogService.getDocumentActivityLogs(
                documentX.getId(), null, orgA.getOrgCode(), wsManagerA);

        assertFalse(logs.isEmpty());
        DocumentActivityLogResponse log = logs.get(0);
        assertNull(log.getIpAddress(), "Workspace Manager must NOT receive raw IP");
        assertNull(log.getUserAgent(), "Workspace Manager must NOT receive raw User-Agent");
        assertEquals(regularVisitor.getEmail(), log.getAccessorEmail(), "Workspace manager can see accessor identity");
    }

    @Test
    @DisplayName("6B.1: RESOURCE_OWNER sees activity and actor email, but raw IP and User-Agent are strictly NULL")
    void testResourceOwnerReceivesRedactedMetadata() {
        auditLogService.logDocumentActivity(
                Action.PREVIEW,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "203.0.113.195",
                "Edge/120.0",
                "Previewed by visitor",
                null
        );

        List<DocumentActivityLogResponse> logs = auditLogService.getDocumentActivityLogs(
                documentX.getId(), null, orgA.getOrgCode(), resourceOwner);

        assertFalse(logs.isEmpty());
        DocumentActivityLogResponse log = logs.get(0);
        assertNull(log.getIpAddress(), "Resource Owner must NOT receive visitor IP address");
        assertNull(log.getUserAgent(), "Resource Owner must NOT receive visitor User-Agent");
        assertEquals(regularVisitor.getEmail(), log.getAccessorEmail(), "Resource owner can see who accessed");
        assertEquals(Action.PREVIEW, log.getAction());
    }

    @Test
    @DisplayName("6B.1: Sensitive tokens in details string are sanitized to [REDACTED]")
    void testSensitiveDetailsSanitization() {
        auditLogService.logDocumentActivity(
                Action.UPDATE,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "192.168.1.1",
                "TestAgent",
                "token=secret_share_token_12345, password=superSecretPassword",
                null
        );

        List<DocumentActivityLogResponse> logs = auditLogService.getDocumentActivityLogs(
                documentX.getId(), null, orgA.getOrgCode(), resourceOwner);

        assertFalse(logs.isEmpty());
        DocumentActivityLogResponse log = logs.get(0);
        assertNotNull(log.getDetails());
        assertFalse(log.getDetails().contains("secret_share_token_12345"), "Secret token must not leak in details");
        assertFalse(log.getDetails().contains("superSecretPassword"), "Secret password must not leak in details");
        assertTrue(log.getDetails().contains("[REDACTED]"), "Details should contain [REDACTED]");
    }

    @Test
    @DisplayName("6B.2: Point-in-Time Scope — Day 1 in WS A1, Day 2 in WS A2: Day 1 event remains scoped to WS A1")
    void testPointInTimeScopePreservation() {
        // Day 1: Document is in Workspace A1 (rootFolderA1)
        auditLogService.logDocumentActivity(
                Action.VIEW_METADATA,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "10.0.0.1",
                "UA-Day1",
                "Day 1 Access in WS A1",
                null
        );

        // Day 2: Document X is moved to Workspace A2 (rootFolderA2)
        documentX.setFolder(rootFolderA2);
        documentRepository.save(documentX);

        // Event on Day 2 in Workspace A2
        auditLogService.logDocumentActivity(
                Action.DOWNLOAD,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA2.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "10.0.0.2",
                "UA-Day2",
                "Day 2 Access in WS A2",
                null
        );

        // Day 3: Query audit log for Workspace A1
        List<DocumentActivityLogResponse> wsA1Logs = auditLogService.getDocumentActivityLogs(
                null, null, workspaceA1.getId(), null, orgAdminA);

        assertEquals(1, wsA1Logs.size(), "Workspace A1 must retain exactly Day 1 event");
        assertEquals(workspaceA1.getId(), wsA1Logs.get(0).getWorkspaceId(), "Event scope must be Workspace A1");
        assertEquals(Action.VIEW_METADATA, wsA1Logs.get(0).getAction());
        assertEquals("Day 1 Access in WS A1", wsA1Logs.get(0).getDetails());

        // Query audit log for Workspace A2
        List<DocumentActivityLogResponse> wsA2Logs = auditLogService.getDocumentActivityLogs(
                null, null, workspaceA2.getId(), null, orgAdminA);

        assertEquals(1, wsA2Logs.size(), "Workspace A2 must retain Day 2 event");
        assertEquals(workspaceA2.getId(), wsA2Logs.get(0).getWorkspaceId(), "Event scope must be Workspace A2");
        assertEquals(Action.DOWNLOAD, wsA2Logs.get(0).getAction());
        assertEquals("Day 2 Access in WS A2", wsA2Logs.get(0).getDetails());
    }

    @Test
    @DisplayName("6B.2: Workspace Manager can audit assigned workspace, but is denied for another workspace")
    void testWorkspaceManagerAuthorizationBoundaries() {
        // Log an event in Workspace A1
        auditLogService.logDocumentActivity(
                Action.VIEW_METADATA,
                documentX.getId(),
                documentX.getFileHash(),
                rootFolderA1.getId(),
                regularVisitor.getId(),
                regularVisitor.getEmail(),
                orgA.getOrgCode(),
                "10.0.0.1",
                "UA",
                "Event in WS A1",
                null
        );

        // wsManagerA can audit assigned Workspace A1
        List<DocumentActivityLogResponse> a1Logs = auditLogService.getDocumentActivityLogs(
                null, null, workspaceA1.getId(), null, wsManagerA);
        assertFalse(a1Logs.isEmpty());

        // wsManagerA cannot audit Workspace A2 (not assigned)
        assertThrows(AccessDeniedException.class, () -> {
            auditLogService.getDocumentActivityLogs(null, null, workspaceA2.getId(), null, wsManagerA);
        }, "Workspace Manager must be denied access to unassigned workspace audit logs");

        // wsManagerA cannot audit entire org
        assertThrows(AccessDeniedException.class, () -> {
            auditLogService.getDocumentActivityLogs(null, null, null, orgA.getOrgCode(), wsManagerA);
        }, "Workspace Manager must not have organization-wide audit visibility");
    }

    @Test
    @DisplayName("6B.2: Cross-Org Isolation — Org Admin of Org B cannot audit Org A's workspace")
    void testCrossOrgIsolation() {
        assertThrows(AccessDeniedException.class, () -> {
            auditLogService.getDocumentActivityLogs(null, null, workspaceA1.getId(), null, orgAdminB);
        }, "Org Admin of Org B must be denied access to Org A's workspace audit log");
    }
}
