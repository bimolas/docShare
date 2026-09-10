package com.subject77.docshare.modules.audit;

import com.subject77.docshare.modules.audit.dto.UnifiedAuditEventResponseDto;
import com.subject77.docshare.modules.audit.dto.UnifiedAuditQueryCriteria;
import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.entities.AuditEventType;
import com.subject77.docshare.modules.audit.enums.AuditCategory;
import com.subject77.docshare.modules.audit.enums.AuditScopeType;
import com.subject77.docshare.modules.audit.repository.AccessLogRepository;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.UnifiedAuditService;
import com.subject77.docshare.modules.document.dto.UpdateDocumentMetadataRequest;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.document.services.DocumentService;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
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
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.services.ProjectWorkspaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest
public class AuditPhaseCIncrement1IntegrationTest {

    @Autowired
    private FolderService folderService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private UnifiedAuditService unifiedAuditService;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private ProjectWorkspaceService projectWorkspaceService;

    @Autowired
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private UserEntity orgAdmin;
    private UserEntity userA;
    private UserEntity userB;
    private OrganizationEntity org;
    private ProjectWorkspaceResponseDto workspace;
    private FolderEntity rootFolder;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        orgAdmin = userRepository.save(UserEntity.builder()
                .id("admin-" + uid)
                .email("admin-" + uid + "@test.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build());

        userA = userRepository.save(UserEntity.builder()
                .id("usera-" + uid)
                .email("usera-" + uid + "@test.com")
                .role(Role.ROLE_USER)
                .build());

        userB = userRepository.save(UserEntity.builder()
                .id("userb-" + uid)
                .email("userb-" + uid + "@test.com")
                .role(Role.ROLE_USER)
                .build());

        org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_" + uid.toUpperCase())
                .name("Org " + uid)
                .createdBy(orgAdmin)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(orgAdmin)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(userA)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(userB)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        // Create workspace for Org
        workspace = projectWorkspaceService.createWorkspace(
                CreateProjectWorkspaceRequestDto.builder()
                        .orgCode(org.getOrgCode())
                        .name("Workspace " + uid)
                        .description("Test workspace")
                        .build(),
                orgAdmin
        );

        rootFolder = folderRepository.findById(workspace.getRootFolderId()).orElseThrow();
    }

    @Test
    @DisplayName("C1.1: Organizational folder creation logs CREATE_FOLDER with actor_user_id and appears in unified audit")
    void testOrganizationalFolderCreationAudit() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Finance Documents")
                .parentFolderId(rootFolder.getId())
                .build();

        FolderResponseDto createdFolder = folderService.createFolder(org.getOrgCode(), request, orgAdmin);
        assertThat(createdFolder).isNotNull();

        // 1. Verify raw access_log record
        List<AccessLogEntity> logs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(createdFolder.getId());
        assertThat(logs).isNotEmpty();
        AccessLogEntity createLog = logs.stream()
                .filter(l -> l.getAction() == Action.CREATE_FOLDER)
                .findFirst()
                .orElse(null);

        assertThat(createLog).isNotNull();
        assertThat(createLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(createLog.getAccessorEmail()).isEqualTo(orgAdmin.getEmail());
        assertThat(createLog.getOrgCode()).isEqualTo(org.getOrgCode());
        assertThat(createLog.getWorkspaceId()).isEqualTo(workspace.getId());
        assertThat(createLog.getDetails()).contains("Created folder: Finance Documents");

        // 2. Query unified audit at FOLDER scope
        UnifiedAuditQueryCriteria folderCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(createdFolder.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> folderAuditPage = unifiedAuditService.queryEvents(
                folderCriteria, PageRequest.of(0, 10), orgAdmin);

        assertThat(folderAuditPage.getContent()).isNotEmpty();
        UnifiedAuditEventResponseDto event = folderAuditPage.getContent().stream()
                .filter(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(event).isNotNull();
        assertThat(event.getCategory()).isEqualTo(AuditCategory.CONTENT);
        assertThat(event.getActor().getUserId()).isEqualTo(orgAdmin.getId());
        assertThat(event.getActor().getEmail()).isEqualTo(orgAdmin.getEmail());
        assertThat(event.getTarget().getType()).isEqualTo("FOLDER");
        assertThat(event.getTarget().getId()).isEqualTo(createdFolder.getId());
        assertThat(event.getTarget().getName()).isEqualTo("Finance Documents");

        // 3. Query unified audit at ORGANIZATION scope
        UnifiedAuditQueryCriteria orgCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.ORGANIZATION)
                .orgCode(org.getOrgCode())
                .build();

        Page<UnifiedAuditEventResponseDto> orgAuditPage = unifiedAuditService.queryEvents(
                orgCriteria, PageRequest.of(0, 10), orgAdmin);

        boolean foundInOrgScope = orgAuditPage.getContent().stream()
                .anyMatch(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()) && e.getTarget().getId().equals(createdFolder.getId()));
        assertThat(foundInOrgScope).isTrue();

        // 4. Query unified audit at WORKSPACE scope
        UnifiedAuditQueryCriteria wsCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.WORKSPACE)
                .workspaceId(workspace.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> wsAuditPage = unifiedAuditService.queryEvents(
                wsCriteria, PageRequest.of(0, 10), orgAdmin);

        boolean foundInWsScope = wsAuditPage.getContent().stream()
                .anyMatch(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()) && e.getTarget().getId().equals(createdFolder.getId()));
        assertThat(foundInWsScope).isTrue();
    }

    @Test
    @DisplayName("C1.2: Personal subspace and personal folder creation log CREATE_FOLDER and query correctly under PERSONAL scope")
    void testPersonalFolderAndSubspaceCreationAudit() {
        // 1. Create personal subspace
        FolderRequestDto subspaceReq = FolderRequestDto.builder()
                .name("Private Research")
                .subspaceType(SubspaceType.PERSONAL)
                .build();

        FolderResponseDto personalSubspace = folderService.createPersonalSubspace(subspaceReq, userA);
        assertThat(personalSubspace).isNotNull();

        // 2. Create personal subfolder inside it
        FolderRequestDto subfolderReq = FolderRequestDto.builder()
                .name("Tax Receipts")
                .parentFolderId(personalSubspace.getId())
                .build();

        FolderResponseDto personalSubfolder = folderService.createPersonalFolder(subfolderReq, userA);
        assertThat(personalSubfolder).isNotNull();

        // 3. Verify raw access logs
        List<AccessLogEntity> subspaceLogs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(personalSubspace.getId());
        assertThat(subspaceLogs).isNotEmpty();
        AccessLogEntity subspaceCreateLog = subspaceLogs.stream()
                .filter(l -> l.getAction() == Action.CREATE_FOLDER)
                .findFirst()
                .orElse(null);
        assertThat(subspaceCreateLog).isNotNull();
        assertThat(subspaceCreateLog.getActorUserId()).isEqualTo(userA.getId());
        assertThat(subspaceCreateLog.getAccessorEmail()).isEqualTo(userA.getEmail());
        assertThat(subspaceCreateLog.getOrgCode()).isNull();

        List<AccessLogEntity> subfolderLogs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(personalSubfolder.getId());
        assertThat(subfolderLogs).isNotEmpty();
        AccessLogEntity subfolderCreateLog = subfolderLogs.stream()
                .filter(l -> l.getAction() == Action.CREATE_FOLDER)
                .findFirst()
                .orElse(null);
        assertThat(subfolderCreateLog).isNotNull();
        assertThat(subfolderCreateLog.getActorUserId()).isEqualTo(userA.getId());

        // 4. Query Unified Audit with scopeType=PERSONAL by userA
        UnifiedAuditQueryCriteria personalCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.PERSONAL)
                .build();

        Page<UnifiedAuditEventResponseDto> userAPersonalEvents = unifiedAuditService.queryEvents(
                personalCriteria, PageRequest.of(0, 10), userA);

        List<Long> matchedFolderIds = userAPersonalEvents.getContent().stream()
                .filter(e -> AuditEventType.FOLDER_CREATE.name().equals(e.getAction()))
                .map(e -> e.getTarget().getId())
                .toList();

        assertThat(matchedFolderIds).contains(personalSubspace.getId(), personalSubfolder.getId());

        // 5. Query Unified Audit with scopeType=PERSONAL by userB (isolation check)
        Page<UnifiedAuditEventResponseDto> userBPersonalEvents = unifiedAuditService.queryEvents(
                personalCriteria, PageRequest.of(0, 10), userB);

        boolean userBSeesUserAFolders = userBPersonalEvents.getContent().stream()
                .anyMatch(e -> e.getTarget().getId().equals(personalSubspace.getId()) || e.getTarget().getId().equals(personalSubfolder.getId()));
        assertThat(userBSeesUserAFolders).isFalse();
    }

    @Test
    @DisplayName("C1.3: Folder ZIP download logs Action.DOWNLOAD with actor_user_id and maps to DOCUMENT_DOWNLOAD")
    void testFolderZipDownloadAudit() {
        FolderRequestDto request = FolderRequestDto.builder()
                .name("Downloadable Assets")
                .parentFolderId(rootFolder.getId())
                .build();

        FolderResponseDto folder = folderService.createFolder(org.getOrgCode(), request, orgAdmin);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        folderService.downloadFolderAsZip(folder.getId(), orgAdmin, out);

        // Verify raw access_log
        List<AccessLogEntity> logs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(folder.getId());
        AccessLogEntity downloadLog = logs.stream()
                .filter(l -> l.getAction() == Action.DOWNLOAD)
                .findFirst()
                .orElse(null);

        assertThat(downloadLog).isNotNull();
        assertThat(downloadLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(downloadLog.getAccessorEmail()).isEqualTo(orgAdmin.getEmail());
        assertThat(downloadLog.getDetails()).contains("Downloaded folder: Downloadable Assets");

        // Verify Unified Audit query
        UnifiedAuditQueryCriteria criteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(folder.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> auditPage = unifiedAuditService.queryEvents(
                criteria, PageRequest.of(0, 10), orgAdmin);

        UnifiedAuditEventResponseDto downloadEvent = auditPage.getContent().stream()
                .filter(e -> AuditEventType.DOCUMENT_DOWNLOAD.name().equals(e.getAction()) && "FOLDER".equals(e.getTarget().getType()))
                .findFirst()
                .orElse(null);

        assertThat(downloadEvent).isNotNull();
        assertThat(downloadEvent.getCategory()).isEqualTo(AuditCategory.CONTENT);
        assertThat(downloadEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());
        assertThat(downloadEvent.getTarget().getId()).isEqualTo(folder.getId());
    }

    @Test
    @DisplayName("C1.4: Folder rename and move populate actor_user_id and map to FOLDER_RENAME and FOLDER_MOVE")
    void testFolderRenameAndMoveAuditAttribution() {
        FolderRequestDto req1 = FolderRequestDto.builder()
                .name("Original Folder")
                .parentFolderId(rootFolder.getId())
                .build();
        FolderResponseDto folder1 = folderService.createFolder(org.getOrgCode(), req1, orgAdmin);

        FolderRequestDto req2 = FolderRequestDto.builder()
                .name("Target Parent")
                .parentFolderId(rootFolder.getId())
                .build();
        FolderResponseDto folder2 = folderService.createFolder(org.getOrgCode(), req2, orgAdmin);

        // Rename folder
        folderService.renameFolder(folder1.getId(), "Renamed Folder", orgAdmin);

        // Move folder
        folderService.moveFolder(folder1.getId(), folder2.getId(), orgAdmin);

        // Verify raw logs
        List<AccessLogEntity> logs = accessLogRepository.findByFolder_IdOrderByTimestampDesc(folder1.getId());

        AccessLogEntity renameLog = logs.stream()
                .filter(l -> l.getAction() == Action.RENAME_METADATA)
                .findFirst()
                .orElse(null);
        assertThat(renameLog).isNotNull();
        assertThat(renameLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(renameLog.getAccessorEmail()).isEqualTo(orgAdmin.getEmail());

        AccessLogEntity moveLog = logs.stream()
                .filter(l -> l.getAction() == Action.MOVE_FOLDER)
                .findFirst()
                .orElse(null);
        assertThat(moveLog).isNotNull();
        assertThat(moveLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(moveLog.getAccessorEmail()).isEqualTo(orgAdmin.getEmail());

        // Verify Unified Audit query
        UnifiedAuditQueryCriteria criteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.FOLDER)
                .folderId(folder1.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> auditPage = unifiedAuditService.queryEvents(
                criteria, PageRequest.of(0, 10), orgAdmin);

        UnifiedAuditEventResponseDto renameEvent = auditPage.getContent().stream()
                .filter(e -> AuditEventType.FOLDER_RENAME.name().equals(e.getAction()))
                .findFirst()
                .orElse(null);
        assertThat(renameEvent).isNotNull();
        assertThat(renameEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());

        UnifiedAuditEventResponseDto moveEvent = auditPage.getContent().stream()
                .filter(e -> AuditEventType.FOLDER_MOVE.name().equals(e.getAction()))
                .findFirst()
                .orElse(null);
        assertThat(moveEvent).isNotNull();
        assertThat(moveEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());
    }

    @Test
    @DisplayName("C1.5: Document move in updateMetadata logs Action.MOVE_DOCUMENT (not MOVE_FOLDER) with actor_user_id")
    void testDocumentMoveClassificationAndActorAttribution() {
        FolderRequestDto targetFolderReq = FolderRequestDto.builder()
                .name("Target Move Destination")
                .parentFolderId(rootFolder.getId())
                .build();
        FolderResponseDto destinationFolder = folderService.createFolder(org.getOrgCode(), targetFolderReq, orgAdmin);

        DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                .fileName("QuarterlyReport.pdf")
                .contentType("application/pdf")
                .s3Bucket("test-bucket")
                .objectKey("orgs/" + org.getOrgCode() + "/qr.pdf")
                .fileSize(1024L)
                .fileHash("hash-qr-pdf")
                .status(DocumentStatus.ACTIVE)
                .ownerOrg(org)
                .uploadedBy(orgAdmin)
                .folder(rootFolder)
                .uploadedAt(Instant.now())
                .version(1)
                .build());

        // Move document via updateMetadata
        UpdateDocumentMetadataRequest moveReq = UpdateDocumentMetadataRequest.builder()
                .folderId(destinationFolder.getId())
                .build();

        documentService.updateMetadata(doc.getId(), moveReq, orgAdmin, "192.168.1.50", "Mozilla/5.0");

        // Verify raw access_log
        List<AccessLogEntity> docLogs = accessLogRepository.findByDocumentIdOrderByTimestampDesc(doc.getId());
        AccessLogEntity moveLog = docLogs.stream()
                .filter(l -> l.getAction() == Action.MOVE_DOCUMENT)
                .findFirst()
                .orElse(null);

        assertThat(moveLog).isNotNull();
        assertThat(moveLog.getActorUserId()).isEqualTo(orgAdmin.getId());
        assertThat(moveLog.getAccessorEmail()).isEqualTo(orgAdmin.getEmail());
        assertThat(moveLog.getFolder().getId()).isEqualTo(destinationFolder.getId());
        assertThat(moveLog.getDetails()).contains("Moved document to folder ID: " + destinationFolder.getId());

        // Crucial verification: ensure Action.MOVE_FOLDER was NOT used for the document move!
        boolean hasMoveFolder = docLogs.stream().anyMatch(l -> l.getAction() == Action.MOVE_FOLDER);
        assertThat(hasMoveFolder).isFalse();

        // Verify Unified Audit query at DOCUMENT scope
        UnifiedAuditQueryCriteria docCriteria = UnifiedAuditQueryCriteria.builder()
                .scopeType(AuditScopeType.DOCUMENT)
                .documentId(doc.getId())
                .build();

        Page<UnifiedAuditEventResponseDto> auditPage = unifiedAuditService.queryEvents(
                docCriteria, PageRequest.of(0, 10), orgAdmin);

        UnifiedAuditEventResponseDto moveEvent = auditPage.getContent().stream()
                .filter(e -> AuditEventType.DOCUMENT_MOVE.name().equals(e.getAction()))
                .findFirst()
                .orElse(null);

        assertThat(moveEvent).isNotNull();
        assertThat(moveEvent.getCategory()).isEqualTo(AuditCategory.CONTENT);
        assertThat(moveEvent.getActor().getUserId()).isEqualTo(orgAdmin.getId());
        assertThat(moveEvent.getTarget().getType()).isEqualTo("DOCUMENT");
        assertThat(moveEvent.getTarget().getId()).isEqualTo(doc.getId());
    }

    @Test
    @DisplayName("C1.6: Document operations populate actor_user_id across preview, download, view details, rename, and delete")
    void testDocumentOperationsActorAttribution() {
        DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                .fileName("Contract.pdf")
                .contentType("application/pdf")
                .s3Bucket("test-bucket")
                .objectKey("orgs/" + org.getOrgCode() + "/contract.pdf")
                .fileSize(1024L)
                .fileHash("hash-contract-pdf")
                .status(DocumentStatus.ACTIVE)
                .ownerOrg(org)
                .uploadedBy(orgAdmin)
                .folder(rootFolder)
                .uploadedAt(Instant.now())
                .version(1)
                .build());

        when(storageService.generatePresignedPreviewUrl(any(), any(), any(), anyLong()))
                .thenReturn("https://presigned-preview.url");
        when(storageService.generatePresignedDownloadUrl(any(), any(), anyLong()))
                .thenReturn("https://presigned-download.url");
        doNothing().when(storageService).deleteObject(any());

        // 1. Preview
        documentService.generatePreviewUrl(doc.getId(), orgAdmin);

        // 2. Download
        documentService.generateDownloadUrl(doc.getId(), orgAdmin);

        // 3. View details
        documentService.getDocumentDetails(doc.getId(), orgAdmin);

        // 4. Rename
        UpdateDocumentMetadataRequest renameReq = UpdateDocumentMetadataRequest.builder()
                .fileName("RenamedContract.pdf")
                .build();
        documentService.updateMetadata(doc.getId(), renameReq, orgAdmin, "127.0.0.1", "TestClient");

        // 5. Delete
        documentService.deleteDocument(doc.getId(), orgAdmin);

        // Verify raw access logs all have actor_user_id populated
        List<AccessLogEntity> docLogs = accessLogRepository.findByDocumentIdOrderByTimestampDesc(doc.getId());
        assertThat(docLogs).isNotEmpty();

        for (AccessLogEntity log : docLogs) {
            assertThat(log.getActorUserId())
                    .as("Action %s must have actor_user_id populated", log.getAction())
                    .isEqualTo(orgAdmin.getId());
        }

        // Verify actions present: PREVIEW, DOWNLOAD_PRESIGNED, VIEW_METADATA, RENAME_METADATA, DELETE_DOCUMENT
        List<Action> actions = docLogs.stream().map(AccessLogEntity::getAction).toList();
        assertThat(actions).contains(
                Action.PREVIEW,
                Action.DOWNLOAD_PRESIGNED,
                Action.VIEW_METADATA,
                Action.RENAME_METADATA,
                Action.DELETE_DOCUMENT
        );
    }
}
