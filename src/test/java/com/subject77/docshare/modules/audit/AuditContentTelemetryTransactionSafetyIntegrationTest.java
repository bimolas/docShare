package com.subject77.docshare.modules.audit;

import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.AuditEventType;
import com.subject77.docshare.modules.audit.enums.AuditCategory;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogServiceImpl;
import com.subject77.docshare.modules.audit.services.UnifiedAuditService;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.folder.services.FolderService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.dto.CreateProjectWorkspaceRequestDto;
import com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@SpringBootTest
public class AuditContentTelemetryTransactionSafetyIntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private UnifiedAuditService unifiedAuditService;

    @MockitoSpyBean
    private AccessLogRepository accessLogRepository;

    @MockitoBean
    private StorageService storageService;

    private UserEntity orgAdmin;
    private OrganizationEntity org;
    private ProjectWorkspaceResponseDto workspace;
    private FolderEntity rootFolder;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        orgAdmin = userRepository.save(UserEntity.builder()
                .id("admin-tx-" + uniqueSuffix)
                .email("admin.tx." + uniqueSuffix + "@example.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_TX_" + uniqueSuffix.toUpperCase())
                .name("TX Safety Org " + uniqueSuffix)
                .createdBy(orgAdmin)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdmin)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        workspace = projectWorkspaceService.createWorkspace(
                CreateProjectWorkspaceRequestDto.builder()
                        .orgCode(org.getOrgCode())
                        .name("Workspace TX " + uniqueSuffix)
                        .description("Test workspace for transaction safety")
                        .build(),
                orgAdmin
        );

        rootFolder = folderRepository.findById(workspace.getRootFolderId()).orElseThrow();
    }

    @Test
    @DisplayName("Verification 1: Content telemetry methods in AuditLogServiceImpl explicitly declare Propagation.REQUIRES_NEW")
    void testContentTelemetryUsesRequiresNew() throws Exception {
        Method m10 = AuditLogServiceImpl.class.getMethod("logDocumentActivity",
                Action.class, Long.class, String.class, Long.class, String.class, String.class, String.class, String.class, String.class, String.class);
        Transactional t10 = m10.getAnnotation(Transactional.class);
        assertThat(t10).as("10-arg logDocumentActivity must be @Transactional").isNotNull();
        assertThat(t10.propagation()).as("10-arg logDocumentActivity must be Propagation.REQUIRES_NEW")
                .isEqualTo(Propagation.REQUIRES_NEW);

        Method m11 = AuditLogServiceImpl.class.getMethod("logDocumentActivity",
                Action.class, Long.class, String.class, Long.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class);
        Transactional t11 = m11.getAnnotation(Transactional.class);
        assertThat(t11).as("11-arg logDocumentActivity must be @Transactional").isNotNull();
        assertThat(t11.propagation()).as("11-arg logDocumentActivity must be Propagation.REQUIRES_NEW")
                .isEqualTo(Propagation.REQUIRES_NEW);

        Method m12 = AuditLogServiceImpl.class.getMethod("logDocumentActivity",
                Action.class, Long.class, String.class, Long.class, Long.class, String.class, String.class, String.class, String.class, String.class, String.class, String.class);
        Transactional t12 = m12.getAnnotation(Transactional.class);
        assertThat(t12).as("12-arg logDocumentActivity must be @Transactional").isNotNull();
        assertThat(t12.propagation()).as("12-arg logDocumentActivity must be Propagation.REQUIRES_NEW")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    @DisplayName("Verification 2: Failure during audit persistence does not roll back business operation (non-blocking fail-safe)")
    void testAuditPersistenceFailureDoesNotRollBackBusinessOperation() {
        // Force access_log repository save to throw an unhandled database exception
        doThrow(new RuntimeException("Simulated unexpected database failure during telemetry save"))
                .when(accessLogRepository).save(any(AccessLogEntity.class));

        try {
            FolderRequestDto request = FolderRequestDto.builder()
                    .name("Critical Business Documents")
                    .parentFolderId(rootFolder.getId())
                    .build();

            // Business operation must NOT throw despite telemetry persistence failure
            assertThatCode(() -> folderService.createFolder(org.getOrgCode(), request, orgAdmin))
                    .doesNotThrowAnyException();

            // Folder must actually be committed and exist in the database
            List<FolderEntity> folders = folderRepository.findByParentFolder_Id(rootFolder.getId());
            boolean exists = folders.stream().anyMatch(f -> "Critical Business Documents".equals(f.getName()));
            assertThat(exists).as("Business entity must be persisted despite telemetry failure").isTrue();
        } finally {
            reset(accessLogRepository);
        }
    }

    @Test
    @DisplayName("Verification 3: Newly created folder audit records can be written correctly under REQUIRES_NEW")
    void testNewlyCreatedFolderAuditWrittenCorrectly() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Compliance Audit Folder")
                .parentFolderId(rootFolder.getId())
                .build();

        FolderResponseDto createdFolder = folderService.createFolder(org.getOrgCode(), request, orgAdmin);
        assertThat(createdFolder).isNotNull();

        // 1. Verify access_log record retrieved by folderId (via folder reference OR scalar resource_id)
        List<AccessLogEntity> logs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(createdFolder.getId());
        assertThat(logs).as("Audit records for newly created folder must not be empty").isNotEmpty();

        AccessLogEntity createLog = logs.stream()
                .filter(l -> l.getAction() == Action.CREATE_FOLDER)
                .findFirst()
                .orElse(null);

        assertThat(createLog).isNotNull();
        assertThat(createLog.getResourceType()).isEqualTo("FOLDER");
        assertThat(createLog.getResourceId()).isEqualTo(createdFolder.getId());
        assertThat(createLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(createLog.getAccessorEmail()).isEqualTo(orgAdmin.getEmail());
        assertThat(createLog.getOrgCode()).isEqualTo(org.getOrgCode());
        assertThat(createLog.getWorkspaceId()).isEqualTo(workspace.getId());

        // 2. Query unified audit at FOLDER scope
        UnifiedAuditQueryCriteria folderCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(createdFolder.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> folderAuditPage = unifiedAuditService.queryEvents(
                folderCriteria, PageRequest.of(0, 10), orgAdmin);

        assertThat(folderAuditPage.getContent()).isNotEmpty();
        UnifiedAuditEventResponseDto folderEvent = folderAuditPage.getContent().stream()
                .filter(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(folderEvent).isNotNull();
        assertThat(folderEvent.getCategory()).isEqualTo(AuditCategory.CONTENT);
        assertThat(folderEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());
        assertThat(folderEvent.getTarget().getType()).isEqualTo("FOLDER");
        assertThat(folderEvent.getTarget().getId()).isEqualTo(createdFolder.getId());

        // 3. Query unified audit at WORKSPACE scope
        UnifiedAuditQueryCriteria wsCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.WORKSPACE)
                .workspaceId(workspace.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> wsAuditPage = unifiedAuditService.queryEvents(
                wsCriteria, PageRequest.of(0, 10), orgAdmin);

        boolean foundInWs = wsAuditPage.getContent().stream()
                .anyMatch(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()) && e.getTarget().getId().equals(createdFolder.getId()));
        assertThat(foundInWs).as("Event must appear in WORKSPACE scope audit").isTrue();

        // 4. Query unified audit at ORGANIZATION scope
        UnifiedAuditQueryCriteria orgCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(org.getOrgCode())
                .build();

        Page<UnifiedAuditEventResponseDto> orgAuditPage = unifiedAuditService.queryEvents(
                orgCriteria, PageRequest.of(0, 10), orgAdmin);

        boolean foundInOrg = orgAuditPage.getContent().stream()
                .anyMatch(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()) && e.getTarget().getId().equals(createdFolder.getId()));
        assertThat(foundInOrg).as("Event must appear in ORGANIZATION scope audit").isTrue();
    }
}
