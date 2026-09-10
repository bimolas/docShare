package com.subject77.docshare.modules.audit;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.enums.AuditCategory;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.enums.AuditViewType;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.UnifiedAuditService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class UnifiedAuditReadEngineIntegrationTest {

    @Autowired
    private UnifiedAuditService unifiedAuditService;

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
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private EntityManager entityManager;

    private UserEntity userA;
    private UserEntity userB;
    private UserEntity orgAdminA;
    private OrganizationEntity orgA;
    private OrganizationEntity orgB;
    private String uniqueSuffix;

    @BeforeEach
    void setUp() {
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        userA = userRepository.save(UserEntity.builder()
                .id("usr-auditA-" + uniqueSuffix)
                .email("userA." + uniqueSuffix + "@example.com")
                .role(Role.ROLE_USER)
                .build());

        userB = userRepository.save(UserEntity.builder()
                .id("usr-auditB-" + uniqueSuffix)
                .email("userB." + uniqueSuffix + "@example.com")
                .role(Role.ROLE_USER)
                .build());

        orgAdminA = userRepository.save(UserEntity.builder()
                .id("usr-orgAdminA-" + uniqueSuffix)
                .email("adminA." + uniqueSuffix + "@example.com")
                .role(Role.ROLE_USER)
                .build());

        orgA = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORGA_" + uniqueSuffix)
                .name("Audit Org A " + uniqueSuffix)
                .createdBy(orgAdminA)
                .build());

        orgB = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORGB_" + uniqueSuffix)
                .name("Audit Org B " + uniqueSuffix)
                .createdBy(userB)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdminA)
                .organization(orgA)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(userA)
                .organization(orgA)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(userB)
                .organization(orgB)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        entityManager.flush();
    }

    @Test
    @DisplayName("Requirement A & B & C: Mixed-store ordering, pagination boundary split, and exact count")
    void testMixedStoreOrderingPaginationAndCount() {
        Instant baseTime = Instant.now().minusSeconds(1000);

        // T1: Access log
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .actorUserId(orgAdminA.getId())
                .accessorEmail(orgAdminA.getEmail())
                .orgCode(orgA.getOrgCode())
                .timestamp(baseTime.plusSeconds(10))
                .details("Uploaded doc1.pdf")
                .build());

        // T2: Permission log
        permissionAuditLogRepository.save(PermissionAuditLogEntity.builder()
                .action("GRANTED_USER")
                .targetType("FOLDER")
                .targetId(101L)
                .actorUserId(orgAdminA.getId())
                .orgCode(orgA.getOrgCode())
                .timestamp(baseTime.plusSeconds(20))
                .details("Granted read access to userA")
                .build());

        // T3: Access log
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.DOWNLOAD)
                .actorUserId(userA.getId())
                .accessorEmail(userA.getEmail())
                .orgCode(orgA.getOrgCode())
                .timestamp(baseTime.plusSeconds(30))
                .details("Downloaded doc1.pdf")
                .build());

        // T4: Permission log
        permissionAuditLogRepository.save(PermissionAuditLogEntity.builder()
                .action("CREATE_TEAM")
                .targetType("GROUP")
                .targetId(202L)
                .actorUserId(orgAdminA.getId())
                .orgCode(orgA.getOrgCode())
                .timestamp(baseTime.plusSeconds(40))
                .details("Created Team Alpha")
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria criteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(orgA.getOrgCode())
                .build();

        // Exact Count Verification (Requirement C)
        Page<UnifiedAuditEventResponseDto> pageFull = unifiedAuditService.queryEvents(
                criteria, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "timestamp")), orgAdminA);

        assertThat(pageFull.getTotalElements()).isEqualTo(4);
        List<UnifiedAuditEventResponseDto> content = pageFull.getContent();

        // Mixed-store Ordering Verification (Requirement A)
        // Order must be: T4 (GOV, 40s), T3 (TEL, 30s), T2 (GOV, 20s), T1 (TEL, 10s)
        assertThat(content.get(0).getAction()).isEqualTo("TEAM_CREATE");
        assertThat(content.get(0).getSource()).isEqualTo("GOVERNANCE");

        assertThat(content.get(1).getAction()).isEqualTo("DOCUMENT_DOWNLOAD");
        assertThat(content.get(1).getSource()).isEqualTo("TELEMETRY");

        assertThat(content.get(2).getAction()).isEqualTo("PERMISSION_GRANTED");
        assertThat(content.get(2).getSource()).isEqualTo("GOVERNANCE");

        assertThat(content.get(3).getAction()).isEqualTo("DOCUMENT_UPLOAD");
        assertThat(content.get(3).getSource()).isEqualTo("TELEMETRY");

        // Pagination Boundary Split Verification (Requirement B)
        Page<UnifiedAuditEventResponseDto> page0 = unifiedAuditService.queryEvents(
                criteria, PageRequest.of(0, 2), orgAdminA);
        Page<UnifiedAuditEventResponseDto> page1 = unifiedAuditService.queryEvents(
                criteria, PageRequest.of(1, 2), orgAdminA);

        assertThat(page0.getContent()).hasSize(2);
        assertThat(page1.getContent()).hasSize(2);

        assertThat(page0.getContent().get(0).getId()).isEqualTo(content.get(0).getId());
        assertThat(page0.getContent().get(1).getId()).isEqualTo(content.get(1).getId());
        assertThat(page1.getContent().get(0).getId()).isEqualTo(content.get(2).getId());
        assertThat(page1.getContent().get(1).getId()).isEqualTo(content.get(3).getId());
    }

    @Test
    @DisplayName("Requirement D: Personal Space isolation — User A cannot see User B's files, but sees accesses on own files")
    void testPersonalSpaceIsolation() {
        // Document owned by User A in personal space
        DocumentEntity docA = documentRepository.save(DocumentEntity.builder()
                .fileName("MySecret_A.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash_A_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("key_A_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .uploadedBy(userA)
                .ownerOrg(null)
                .build());

        // Document owned by User B in personal space
        DocumentEntity docB = documentRepository.save(DocumentEntity.builder()
                .fileName("MySecret_B.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .fileHash("hash_B_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("key_B_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .uploadedBy(userB)
                .ownerOrg(null)
                .build());

        // User A uploaded docA
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .documentId(docA.getId())
                .actorUserId(userA.getId())
                .accessorEmail(userA.getEmail())
                .timestamp(Instant.now().minusSeconds(50))
                .details("Uploaded personal file")
                .build());

        // User B downloaded User A's docA (e.g. shared via direct link)
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.DOWNLOAD)
                .documentId(docA.getId())
                .actorUserId(userB.getId())
                .accessorEmail(userB.getEmail())
                .timestamp(Instant.now().minusSeconds(30))
                .details("User B downloaded shared personal file")
                .build());

        // User B uploaded docB in their own personal space
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .documentId(docB.getId())
                .actorUserId(userB.getId())
                .accessorEmail(userB.getEmail())
                .timestamp(Instant.now().minusSeconds(10))
                .details("Uploaded personal file B")
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria personalCriteriaA = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.PERSONAL)
                .build();

        Page<UnifiedAuditEventResponseDto> pageA = unifiedAuditService.queryEvents(
                personalCriteriaA, PageRequest.of(0, 10), userA);

        // User A must see 2 events (own upload and User B's access to DocA), but NOT DocB
        assertThat(pageA.getTotalElements()).isEqualTo(2);
        assertThat(pageA.getContent())
                .allMatch(e -> docA.getId().equals(e.getTarget().getId()))
                .noneMatch(e -> docB.getId().equals(e.getTarget().getId()));

        // User A sees User B's download on User A's personal document
        boolean userBDownloadSeen = pageA.getContent().stream()
                .anyMatch(e -> "DOCUMENT_DOWNLOAD".equals(e.getAction()) && userB.getEmail().equals(e.getActor().getEmail()));
        assertThat(userBDownloadSeen).isTrue();

        // Check User B's perspective: User B must see ONLY Doc B, never User A's Doc A
        UnifiedAuditQueryCriteria personalCriteriaB = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.PERSONAL)
                .build();
        Page<UnifiedAuditEventResponseDto> pageB = unifiedAuditService.queryEvents(
                personalCriteriaB, PageRequest.of(0, 10), userB);
        assertThat(pageB.getTotalElements()).isEqualTo(1);
        assertThat(pageB.getContent().get(0).getTarget().getId()).isEqualTo(docB.getId());

        // Verify Org resources & Team activity NEVER enter Personal Space even if userA is the actor
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.DOWNLOAD)
                .documentId(99999L)
                .actorUserId(userA.getId())
                .accessorEmail(userA.getEmail())
                .orgCode(orgA.getOrgCode())
                .timestamp(Instant.now().minusSeconds(5))
                .details("Org download by User A")
                .build());

        permissionAuditLogRepository.save(PermissionAuditLogEntity.builder()
                .action("TEAM_MEMBER_ADD")
                .actorUserId(userA.getId())
                .targetType("GROUP")
                .targetId(88888L)
                .groupId(88888L)
                .timestamp(Instant.now().minusSeconds(2))
                .details("Team membership add")
                .build());

        entityManager.flush();

        Page<UnifiedAuditEventResponseDto> pageARecheck = unifiedAuditService.queryEvents(
                personalCriteriaA, PageRequest.of(0, 10), userA);
        assertThat(pageARecheck.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Requirement E: Organization isolation — Org A cannot query Org B audit logs")
    void testOrganizationIsolation() {
        UnifiedAuditQueryCriteria criteriaOrgB = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(orgB.getOrgCode())
                .build();

        // User A (member of Org A only) attempting to query Org B
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(criteriaOrgB, PageRequest.of(0, 10), userA))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Requirement F: Workspace isolation — Workspace A does not expose Workspace B")
    void testWorkspaceIsolation() {
        FolderEntity rootA = folderRepository.save(FolderEntity.builder()
                .name("RootA_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        FolderEntity rootB = folderRepository.save(FolderEntity.builder()
                .name("RootB_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        ProjectWorkspaceEntity wsA = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace A " + uniqueSuffix)
                .organization(orgA)
                .rootFolder(rootA)
                .createdBy(orgAdminA)
                .build());

        ProjectWorkspaceEntity wsB = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace B " + uniqueSuffix)
                .organization(orgA)
                .rootFolder(rootB)
                .createdBy(orgAdminA)
                .build());

        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .workspaceId(wsA.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(20))
                .build());

        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .workspaceId(wsB.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(10))
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria wsCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.WORKSPACE)
                .workspaceId(wsA.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> page = unifiedAuditService.queryEvents(wsCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getLocation().getWorkspaceId()).isEqualTo(wsA.getId());
    }

    @Test
    @DisplayName("Requirement G: Folder descendants — includeDescendants=false vs includeDescendants=true via recursive CTE")
    void testFolderDescendantsQuery() {
        FolderEntity parentFolder = folderRepository.save(FolderEntity.builder()
                .name("Parent_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        FolderEntity childFolder = folderRepository.save(FolderEntity.builder()
                .name("Child_" + uniqueSuffix)
                .parentFolder(parentFolder)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .build());

        DocumentEntity childDoc = documentRepository.save(DocumentEntity.builder()
                .fileName("NestedDoc_" + uniqueSuffix + ".pdf")
                .contentType("application/pdf")
                .fileSize(500L)
                .fileHash("hash_nested_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("nested_key_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .folder(childFolder)
                .ownerOrg(orgA)
                .uploadedBy(orgAdminA)
                .build());

        // Event on parent
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CREATE_FOLDER)
                .folder(parentFolder)
                .resourceType("FOLDER")
                .resourceId(parentFolder.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(30))
                .build());

        // Event on child folder
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CREATE_FOLDER)
                .folder(childFolder)
                .resourceType("FOLDER")
                .resourceId(childFolder.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(20))
                .build());

        // Event on document in child folder
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .documentId(childDoc.getId())
                .folder(childFolder)
                .resourceType("DOCUMENT")
                .resourceId(childDoc.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(10))
                .build());

        entityManager.flush();

        // 1. Direct folder query (includeDescendants = false)
        UnifiedAuditQueryCriteria directCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(parentFolder.getId())
                .includeDescendants(false)
                .build();

        Page<UnifiedAuditEventResponseDto> directPage = unifiedAuditService.queryEvents(
                directCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(directPage.getTotalElements()).isEqualTo(1);
        assertThat(directPage.getContent().get(0).getTarget().getId()).isEqualTo(parentFolder.getId());

        // 2. Recursive descendant query (includeDescendants = true)
        UnifiedAuditQueryCriteria recursiveCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(parentFolder.getId())
                .includeDescendants(true)
                .build();

        Page<UnifiedAuditEventResponseDto> recursivePage = unifiedAuditService.queryEvents(
                recursiveCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(recursivePage.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("Requirement H: Document scope — Storage-specific predicates include both access telemetry and permissions")
    void testDocumentScopeQuery() {
        DocumentEntity testDoc = documentRepository.save(DocumentEntity.builder()
                .fileName("Contract_" + uniqueSuffix + ".pdf")
                .contentType("application/pdf")
                .fileSize(1000L)
                .fileHash("hash_doc_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("contract_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .ownerOrg(orgA)
                .uploadedBy(orgAdminA)
                .build());

        // Telemetry in access_logs
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .documentId(testDoc.getId())
                .resourceType("DOCUMENT")
                .resourceId(testDoc.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(20))
                .build());

        // Governance in permission_audit_logs
        permissionAuditLogRepository.save(PermissionAuditLogEntity.builder()
                .action("GRANTED_USER")
                .targetType("DOCUMENT")
                .targetId(testDoc.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(orgAdminA.getId())
                .timestamp(Instant.now().minusSeconds(10))
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria docCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.DOCUMENT)
                .documentId(testDoc.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> docPage = unifiedAuditService.queryEvents(
                docCriteria, PageRequest.of(0, 10), orgAdminA);

        assertThat(docPage.getTotalElements()).isEqualTo(2);
        assertThat(docPage.getContent()).extracting(UnifiedAuditEventResponseDto::getSource)
                .containsExactlyInAnyOrder("TELEMETRY", "GOVERNANCE");
    }

    @Test
    @DisplayName("Requirement I: Team authorization & governance boundary — only team governance returned, routine telemetry excluded")
    void testTeamAuthorizationAndGovernanceBoundary() {
        GroupEntity team = groupRepository.save(GroupEntity.builder()
                .name("Engineering Team " + uniqueSuffix)
                .organization(orgA)
                .createdBy(orgAdminA)
                .build());

        UserEntity teamManager = userRepository.save(UserEntity.builder()
                .id("usr-teamMgr-" + uniqueSuffix)
                .email("teamMgr." + uniqueSuffix + "@example.com")
                .role(Role.ROLE_USER)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(teamManager)
                .organization(orgA)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        // Assign teamManager as current MANAGER
        userGroupRepository.save(UserGroupEntity.builder()
                .user(teamManager)
                .group(team)
                .role(GroupMemberRole.MANAGER)
                .build());

        // Team governance event in permission_audit_logs
        permissionAuditLogRepository.save(PermissionAuditLogEntity.builder()
                .action("TEAM_MEMBER_ADD")
                .targetType("GROUP")
                .targetId(team.getId())
                .groupId(team.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(teamManager.getId())
                .timestamp(Instant.now().minusSeconds(20))
                .build());

        // Routine telemetry with group_id should be excluded from team governance activity
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.DOWNLOAD)
                .groupId(team.getId())
                .orgCode(orgA.getOrgCode())
                .actorUserId(teamManager.getId())
                .timestamp(Instant.now().minusSeconds(10))
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(team.getId())
                .build();

        // 1. Current Team Manager can view
        Page<UnifiedAuditEventResponseDto> tmPage = unifiedAuditService.queryEvents(teamCriteria, PageRequest.of(0, 10), teamManager);
        assertThat(tmPage.getTotalElements()).isEqualTo(1);
        assertThat(tmPage.getContent().get(0).getAction()).isEqualTo("TEAM_MEMBER_ADD");

        // 2. Org Admin can view
        Page<UnifiedAuditEventResponseDto> adminPage = unifiedAuditService.queryEvents(teamCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(adminPage.getTotalElements()).isEqualTo(1);

        // 3. Regular user denied
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(teamCriteria, PageRequest.of(0, 10), userA))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Requirement J: Filter security — Category, action, actor filters cannot bypass scope boundary")
    void testFilterSecurityCannotBypassScope() {
        UnifiedAuditQueryCriteria criteriaBypass = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(orgB.getOrgCode())
                .category(AuditCategory.CONTENT)
                .view(AuditViewType.CONTENT)
                .build();

        // Unauthorized user attempts to query Org B by applying category=CONTENT and view=CONTENT
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(criteriaBypass, PageRequest.of(0, 10), userA))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Requirement K: Deleted resource authorization — Authoritative provenance lookup works; probing denied")
    void testDeletedResourceAuthorization() {
        Long fakeDocId = 999999L;
        Long deletedDocId = 888888L;

        // Save historical audit record for deleted personal document
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.DELETE_DOCUMENT)
                .documentId(deletedDocId)
                .resourceType("DOCUMENT")
                .resourceId(deletedDocId)
                .actorUserId(userA.getId())
                .accessorEmail(userA.getEmail())
                .timestamp(Instant.now().minusSeconds(10))
                .details("Deleted personal document")
                .build());

        entityManager.flush();

        // 1. Probing completely non-existent ID -> 404 ResourceNotFoundException
        UnifiedAuditQueryCriteria fakeDocCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.DOCUMENT)
                .documentId(fakeDocId)
                .build();

        assertThatThrownBy(() -> unifiedAuditService.queryEvents(fakeDocCriteria, PageRequest.of(0, 10), userA))
                .isInstanceOf(ResourceNotFoundException.class);

        // 2. Authorized owner of deleted personal document -> 200 OK with historical records
        UnifiedAuditQueryCriteria deletedDocCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.DOCUMENT)
                .documentId(deletedDocId)
                .build();

        Page<UnifiedAuditEventResponseDto> page = unifiedAuditService.queryEvents(
                deletedDocCriteria, PageRequest.of(0, 10), userA);
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getAction()).isEqualTo("DOCUMENT_DELETE");

        // 3. Unauthorized caller attempting to query deleted document -> 403 AccessDeniedException
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(deletedDocCriteria, PageRequest.of(0, 10), userB))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Requirement L: Technical metadata redaction — Non-privileged callers receive redacted IP/User-Agent; Org Admin receives unredacted")
    void testTechnicalMetadataRedaction() {
        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .actorUserId(orgAdminA.getId())
                .accessorEmail(orgAdminA.getEmail())
                .orgCode(orgA.getOrgCode())
                .ipAddress("192.168.1.100")
                .userAgent("Mozilla/5.0 TestBrowser")
                .timestamp(Instant.now().minusSeconds(10))
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria criteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(orgA.getOrgCode())
                .build();

        // 1. Org Admin querying -> receives actual IP and User-Agent
        Page<UnifiedAuditEventResponseDto> adminPage = unifiedAuditService.queryEvents(
                criteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(adminPage.getContent().get(0).getMetadata().getIpAddress()).isEqualTo("192.168.1.100");
        assertThat(adminPage.getContent().get(0).getMetadata().getUserAgent()).isEqualTo("Mozilla/5.0 TestBrowser");

        // 2. Regular user querying Personal Space -> receives redacted (null) IP and User-Agent
        DocumentEntity personalDoc = documentRepository.save(DocumentEntity.builder()
                .fileName("UserA_Doc.pdf")
                .contentType("application/pdf")
                .fileSize(100L)
                .fileHash("hash_userA_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("key_userA_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .uploadedBy(userA)
                .ownerOrg(null)
                .build());

        accessLogRepository.save(AccessLogEntity.builder()
                .action(Action.CONFIRM_UPLOAD)
                .documentId(personalDoc.getId())
                .actorUserId(userA.getId())
                .accessorEmail(userA.getEmail())
                .ipAddress("10.0.0.1")
                .userAgent("PersonalBrowser/1.0")
                .timestamp(Instant.now())
                .build());

        entityManager.flush();

        UnifiedAuditQueryCriteria personalCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.PERSONAL)
                .build();

        Page<UnifiedAuditEventResponseDto> userAPage = unifiedAuditService.queryEvents(
                personalCriteria, PageRequest.of(0, 10), userA);
        assertThat(userAPage.getContent()).isNotEmpty();
        assertThat(userAPage.getContent().get(0).getMetadata().getIpAddress()).isNull();
        assertThat(userAPage.getContent().get(0).getMetadata().getUserAgent()).isNull();
    }

    @Test
    @DisplayName("Requirement: Audit visibility != Content permission — Content viewer cannot view audit logs")
    void testAuditVisibilitySeparateFromContentPermission() {
        // Create folder in Org A
        FolderEntity orgFolder = folderRepository.save(FolderEntity.builder()
                .name("Finance_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .createdAt(Instant.now())
                .isDefaultSubspace(false)
                .build());

        // Create document in orgFolder
        DocumentEntity orgDoc = documentRepository.save(DocumentEntity.builder()
                .fileName("Report_" + uniqueSuffix + ".pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash_report_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("key_report_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .uploadedBy(orgAdminA)
                .ownerOrg(orgA)
                .folder(orgFolder)
                .build());

        // User B is a regular user (not org admin, not workspace manager, not creator)
        // User B attempts to view FOLDER audit logs -> 403 Forbidden
        UnifiedAuditQueryCriteria folderCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(orgFolder.getId())
                .build();
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(folderCriteria, PageRequest.of(0, 10), userB))
                .isInstanceOf(AccessDeniedException.class);

        // User B attempts to view DOCUMENT audit logs -> 403 Forbidden
        UnifiedAuditQueryCriteria docCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.DOCUMENT)
                .documentId(orgDoc.getId())
                .build();
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(docCriteria, PageRequest.of(0, 10), userB))
                .isInstanceOf(AccessDeniedException.class);

        // User B attempts to view ORGANIZATION audit logs -> 403 Forbidden
        UnifiedAuditQueryCriteria orgCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(orgA.getOrgCode())
                .build();
        assertThatThrownBy(() -> unifiedAuditService.queryEvents(orgCriteria, PageRequest.of(0, 10), userB))
                .isInstanceOf(AccessDeniedException.class);

        // Org Admin A CAN view all of them
        Page<UnifiedAuditEventResponseDto> folderAudit = unifiedAuditService.queryEvents(
                folderCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(folderAudit).isNotNull();

        Page<UnifiedAuditEventResponseDto> docAudit = unifiedAuditService.queryEvents(
                docCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(docAudit).isNotNull();

        Page<UnifiedAuditEventResponseDto> orgAudit = unifiedAuditService.queryEvents(
                orgCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(orgAudit).isNotNull();
    }

    @Test
    @DisplayName("Requirement: Explicit REST contract — all 7 scopes resolve with dedicated explicit parameters")
    void testExplicitScopeParametersResolution() {
        // 1. PERSONAL: scopeType=PERSONAL
        UnifiedAuditQueryCriteria personalCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.PERSONAL)
                .build();
        Page<UnifiedAuditEventResponseDto> pagePersonal = unifiedAuditService.queryEvents(
                personalCriteria, PageRequest.of(0, 10), userA);
        assertThat(pagePersonal).isNotNull();

        // 2. ORGANIZATION: scopeType=ORGANIZATION, orgCode=...
        UnifiedAuditQueryCriteria orgCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(orgA.getOrgCode())
                .build();
        Page<UnifiedAuditEventResponseDto> pageOrg = unifiedAuditService.queryEvents(
                orgCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(pageOrg).isNotNull();

        // 3. WORKSPACE: scopeType=WORKSPACE, workspaceId=...
        FolderEntity wsRoot = folderRepository.save(FolderEntity.builder()
                .name("WsRoot_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .createdAt(Instant.now())
                .isDefaultSubspace(false)
                .build());
        ProjectWorkspaceEntity ws = projectWorkspaceRepository.save(ProjectWorkspaceEntity.builder()
                .name("Workspace Alpha " + uniqueSuffix)
                .organization(orgA)
                .createdBy(orgAdminA)
                .rootFolder(wsRoot)
                .status(ProjectWorkspaceStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
        UnifiedAuditQueryCriteria wsCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.WORKSPACE)
                .workspaceId(ws.getId())
                .build();
        Page<UnifiedAuditEventResponseDto> pageWs = unifiedAuditService.queryEvents(
                wsCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(pageWs).isNotNull();

        // 4. FOLDER: scopeType=FOLDER, folderId=...
        FolderEntity folder = folderRepository.save(FolderEntity.builder()
                .name("ScopedFolder_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .createdAt(Instant.now())
                .isDefaultSubspace(false)
                .build());
        UnifiedAuditQueryCriteria folderCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(folder.getId())
                .build();
        Page<UnifiedAuditEventResponseDto> pageFolder = unifiedAuditService.queryEvents(
                folderCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(pageFolder).isNotNull();

        // 5. DOCUMENT: scopeType=DOCUMENT, documentId=...
        DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                .fileName("ScopedDoc_" + uniqueSuffix + ".pdf")
                .contentType("application/pdf")
                .fileSize(500L)
                .fileHash("hash_scoped_" + uniqueSuffix)
                .s3Bucket("test-bucket")
                .objectKey("key_scoped_" + uniqueSuffix)
                .status(DocumentStatus.ACTIVE)
                .uploadedBy(orgAdminA)
                .ownerOrg(orgA)
                .build());
        UnifiedAuditQueryCriteria docCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.DOCUMENT)
                .documentId(doc.getId())
                .build();
        Page<UnifiedAuditEventResponseDto> pageDoc = unifiedAuditService.queryEvents(
                docCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(pageDoc).isNotNull();

        // 6. TEAM: scopeType=TEAM, teamId=...
        GroupEntity team = groupRepository.save(GroupEntity.builder()
                .name("TeamScope_" + uniqueSuffix)
                .organization(orgA)
                .createdBy(orgAdminA)
                .status(GroupStatus.ACTIVE)
                .build());
        UnifiedAuditQueryCriteria teamCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.TEAM)
                .teamId(team.getId())
                .build();
        Page<UnifiedAuditEventResponseDto> pageTeam = unifiedAuditService.queryEvents(
                teamCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(pageTeam).isNotNull();

        // 7. COLLABORATION: scopeType=COLLABORATION, collaborationId=...
        FolderEntity collabFolder = folderRepository.save(FolderEntity.builder()
                .name("CollabFolder_" + uniqueSuffix)
                .ownerOrg(orgA)
                .createdBy(orgAdminA)
                .createdAt(Instant.now())
                .isDefaultSubspace(false)
                .build());
        CollaborationEntity collab = collaborationRepository.save(CollaborationEntity.builder()
                .name("CollabScope_" + uniqueSuffix)
                .ownerOrganization(orgA)
                .partnerOrganization(orgB)
                .rootFolder(collabFolder)
                .createdBy(orgAdminA)
                .status(CollaborationStatus.ACTIVE)
                .startsAt(Instant.now())
                .createdAt(Instant.now())
                .build());
        UnifiedAuditQueryCriteria collabCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.COLLABORATION)
                .collaborationId(collab.getId())
                .build();
        Page<UnifiedAuditEventResponseDto> pageCollab = unifiedAuditService.queryEvents(
                collabCriteria, PageRequest.of(0, 10), orgAdminA);
        assertThat(pageCollab).isNotNull();
    }
}
