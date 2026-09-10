package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharedByMeServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionAuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PermissionEventPublisher eventPublisher;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private UserEntity grantor;
    private UserEntity recipientUser;

    @BeforeEach
    void setUp() {
        grantor = UserEntity.builder()
                .id("grantor-123")
                .email("grantor@example.com")
                .firstName("Alice")
                .lastName("Grantor")
                .build();

        recipientUser = UserEntity.builder()
                .id("recipient-456")
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Recipient")
                .build();
    }

    @Test
    @DisplayName("Should return folders and documents granted by current user")
    void getSharedByMe_returnsFoldersAndDocumentsGrantedByCurrentUser() {
        FolderEntity folder = FolderEntity.builder()
                .id(101L)
                .name("Shared Project Folder")
                .build();

        DocumentEntity document = DocumentEntity.builder()
                .id(202L)
                .fileName("Quarterly_Report.pdf")
                .fileHash("hash-abc-123")
                .fileSize(1048576L)
                .contentType("application/pdf")
                .status(DocumentStatus.ACTIVE)
                .build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(1L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        PermissionEntity docPerm = PermissionEntity.builder()
                .id(2L)
                .grantedBy(grantor)
                .document(document)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(folderPerm, docPerm));
        when(userRepository.findByEmailInIgnoreCase(anyCollection()))
                .thenReturn(List.of(recipientUser));

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getPermissionId()).isEqualTo(1L);
        assertThat(folderDto.getResourceType()).isEqualTo("FOLDER");
        assertThat(folderDto.getResourceId()).isEqualTo(101L);
        assertThat(folderDto.getName()).isEqualTo("Shared Project Folder");
        assertThat(folderDto.getRecipientType()).isEqualTo("USER");
        assertThat(folderDto.getTargetUserEmail()).isEqualTo("bob@example.com");
        assertThat(folderDto.getTargetUserName()).isEqualTo("Bob Recipient");
        assertThat(folderDto.getStatus()).isEqualTo("ACTIVE");

        assertThat(response.getSharedDocuments()).hasSize(1);
        SharedItemDto docDto = response.getSharedDocuments().get(0);
        assertThat(docDto.getPermissionId()).isEqualTo(2L);
        assertThat(docDto.getResourceType()).isEqualTo("DOCUMENT");
        assertThat(docDto.getResourceId()).isEqualTo(202L);
        assertThat(docDto.getName()).isEqualTo("Quarterly_Report.pdf");
        assertThat(docDto.getFileHash()).isEqualTo("hash-abc-123");
        assertThat(docDto.getFileSize()).isEqualTo(1048576L);
        assertThat(docDto.getContentType()).isEqualTo("application/pdf");
        assertThat(docDto.getRecipientType()).isEqualTo("USER");
        assertThat(docDto.getTargetUserEmail()).isEqualTo("bob@example.com");
        assertThat(docDto.getTargetUserName()).isEqualTo("Bob Recipient");
        assertThat(docDto.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should strictly exclude bearer share-links")
    void getSharedByMe_excludesBearerShareLinks() {
        DocumentEntity document = DocumentEntity.builder()
                .id(202L)
                .fileName("Secret.pdf")
                .build();

        PermissionEntity linkPerm = PermissionEntity.builder()
                .id(99L)
                .grantedBy(grantor)
                .document(document)
                .shareLinkToken("raw-secret-token-abc")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(linkPerm));

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Should preserve recipient distinctions for USER, GROUP, ORGANIZATION, and COLLABORATION")
    void getSharedByMe_preservesRecipientDistinctions() {
        FolderEntity folder = FolderEntity.builder().id(10L).name("F").build();

        // 1. USER
        PermissionEntity userPerm = PermissionEntity.builder()
                .id(1L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        // 2. GROUP
        GroupEntity team = GroupEntity.builder().id(50L).name("Dev Team").build();
        PermissionEntity groupPerm = PermissionEntity.builder()
                .id(2L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.GROUP)
                .targetGroup(team)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        // 3. ORGANIZATION
        PermissionEntity orgPerm = PermissionEntity.builder()
                .id(3L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode("ACME")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        // 4. COLLABORATION
        CollaborationEntity collab = CollaborationEntity.builder().id(70L).name("Joint Project").build();
        PermissionEntity collabPerm = PermissionEntity.builder()
                .id(4L)
                .grantedBy(grantor)
                .folder(folder)
                .collaboration(collab)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(userPerm, groupPerm, orgPerm, collabPerm));
        when(userRepository.findByEmailInIgnoreCase(anyCollection())).thenReturn(List.of(recipientUser));
        when(organizationRepository.findAllById(anyCollection())).thenReturn(List.of(OrganizationEntity.builder().orgCode("ACME").name("Acme Corporation").build()));

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        assertThat(response.getSharedFolders()).hasSize(4);

        SharedItemDto uDto = response.getSharedFolders().stream().filter(d -> d.getPermissionId() == 1L).findFirst().orElseThrow();
        assertThat(uDto.getRecipientType()).isEqualTo("USER");
        assertThat(uDto.getTargetUserEmail()).isEqualTo("bob@example.com");
        assertThat(uDto.getTargetUserName()).isEqualTo("Bob Recipient");
        assertThat(uDto.getSourceLabel()).isEqualTo("DIRECT SHARE");
        assertThat(uDto.getSourceLabels()).containsExactly("DIRECT SHARE");
        assertThat(uDto.getSourceDescription()).isEqualTo("Shared with Bob Recipient");
        assertThat(uDto.getMatchingGrantCount()).isEqualTo(1);
        assertThat(uDto.getMatchedPermissionIds()).containsExactly(1L);

        SharedItemDto gDto = response.getSharedFolders().stream().filter(d -> d.getPermissionId() == 2L).findFirst().orElseThrow();
        assertThat(gDto.getRecipientType()).isEqualTo("GROUP");
        assertThat(gDto.getTargetGroupId()).isEqualTo(50L);
        assertThat(gDto.getTargetGroupName()).isEqualTo("Dev Team");
        assertThat(gDto.getSourceLabel()).isEqualTo("TEAM SHARE");
        assertThat(gDto.getSourceLabels()).containsExactly("TEAM SHARE");
        assertThat(gDto.getSourceDescription()).isEqualTo("Shared with team 'Dev Team'");
        assertThat(gDto.getMatchingGrantCount()).isEqualTo(1);
        assertThat(gDto.getMatchedPermissionIds()).containsExactly(2L);

        SharedItemDto oDto = response.getSharedFolders().stream().filter(d -> d.getPermissionId() == 3L).findFirst().orElseThrow();
        assertThat(oDto.getRecipientType()).isEqualTo("ORGANIZATION");
        assertThat(oDto.getTargetOrgCode()).isEqualTo("ACME");
        assertThat(oDto.getTargetOrgName()).isEqualTo("Acme Corporation");
        assertThat(oDto.getSourceLabel()).isEqualTo("ORGANIZATION SHARE");
        assertThat(oDto.getSourceLabels()).containsExactly("ORGANIZATION SHARE");
        assertThat(oDto.getSourceDescription()).isEqualTo("Shared with organization 'Acme Corporation'");
        assertThat(oDto.getMatchingGrantCount()).isEqualTo(1);
        assertThat(oDto.getMatchedPermissionIds()).containsExactly(3L);

        SharedItemDto cDto = response.getSharedFolders().stream().filter(d -> d.getPermissionId() == 4L).findFirst().orElseThrow();
        assertThat(cDto.getRecipientType()).isEqualTo("COLLABORATION");
        assertThat(cDto.getCollaborationId()).isEqualTo(70L);
        assertThat(cDto.getCollaborationName()).isEqualTo("Joint Project");
        assertThat(cDto.getSourceLabel()).isEqualTo("COLLABORATION SHARE");
        assertThat(cDto.getSourceLabels()).containsExactly("COLLABORATION SHARE");
        assertThat(cDto.getSourceDescription()).isEqualTo("Shared via collaboration 'Joint Project'");
        assertThat(cDto.getMatchingGrantCount()).isEqualTo(1);
        assertThat(cDto.getMatchedPermissionIds()).containsExactly(4L);
    }

    @Test
    @DisplayName("Lifecycle status handling: includeInactive true vs false")
    void getSharedByMe_lifecycleStatusHandling() {
        FolderEntity folder = FolderEntity.builder().id(10L).name("Folder").build();

        // Active
        PermissionEntity activePerm = PermissionEntity.builder()
                .id(1L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .revoked(false)
                .suspended(false)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();

        // Suspended
        PermissionEntity suspendedPerm = PermissionEntity.builder()
                .id(2L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .revoked(false)
                .suspended(true)
                .createdAt(Instant.now())
                .build();

        // Revoked
        PermissionEntity revokedPerm = PermissionEntity.builder()
                .id(3L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .revoked(true)
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .build();

        // Expired
        PermissionEntity expiredPerm = PermissionEntity.builder()
                .id(4L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .revoked(false)
                .suspended(false)
                .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(activePerm, suspendedPerm, revokedPerm, expiredPerm));

        // 1. With includeInactive = true: all 4 are returned with correct status
        SharedByMeResponseDto fullResponse = permissionService.getSharedByMe(grantor, true);
        assertThat(fullResponse.getSharedFolders()).hasSize(4);

        SharedItemDto activeDto = fullResponse.getSharedFolders().stream().filter(d -> d.getPermissionId() == 1L).findFirst().orElseThrow();
        assertThat(activeDto.getStatus()).isEqualTo("ACTIVE");
        assertThat(activeDto.getRevoked()).isFalse();
        assertThat(activeDto.getSuspended()).isFalse();

        SharedItemDto suspendedDto = fullResponse.getSharedFolders().stream().filter(d -> d.getPermissionId() == 2L).findFirst().orElseThrow();
        assertThat(suspendedDto.getStatus()).isEqualTo("SUSPENDED");
        assertThat(suspendedDto.getSuspended()).isTrue();

        SharedItemDto revokedDto = fullResponse.getSharedFolders().stream().filter(d -> d.getPermissionId() == 3L).findFirst().orElseThrow();
        assertThat(revokedDto.getStatus()).isEqualTo("REVOKED");
        assertThat(revokedDto.getRevoked()).isTrue();
        assertThat(revokedDto.getRevokedAt()).isNotNull();

        SharedItemDto expiredDto = fullResponse.getSharedFolders().stream().filter(d -> d.getPermissionId() == 4L).findFirst().orElseThrow();
        assertThat(expiredDto.getStatus()).isEqualTo("EXPIRED");

        // 2. With includeInactive = false: ONLY the active grant is returned
        SharedByMeResponseDto activeOnlyResponse = permissionService.getSharedByMe(grantor, false);
        assertThat(activeOnlyResponse.getSharedFolders()).hasSize(1);
        assertThat(activeOnlyResponse.getSharedFolders().get(0).getPermissionId()).isEqualTo(1L);
        assertThat(activeOnlyResponse.getSharedFolders().get(0).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should deduplicate rows if permission entity appears multiple times")
    void getSharedByMe_deduplicatesRows() {
        FolderEntity folder = FolderEntity.builder().id(10L).name("Folder").build();
        PermissionEntity perm = PermissionEntity.builder()
                .id(1L)
                .grantedBy(grantor)
                .folder(folder)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        // Same permission returned twice
        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(perm, perm));

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        assertThat(response.getSharedFolders()).hasSize(1);
    }

    @Test
    @DisplayName("Should return empty lists when user is null")
    void getSharedByMe_nullUser_returnsEmpty() {
        SharedByMeResponseDto response = permissionService.getSharedByMe(null, true);
        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Status Filtering: ACTIVE doc included, PENDING_UPLOAD and FAILED excluded, folders unaffected")
    void getSharedByMe_documentStatusFiltering() {
        FolderEntity folder = FolderEntity.builder().id(101L).name("Active Folder").build();
        DocumentEntity activeDoc = DocumentEntity.builder().id(201L).fileName("Active.pdf").status(DocumentStatus.ACTIVE).build();
        DocumentEntity pendingDoc = DocumentEntity.builder().id(202L).fileName("Pending.pdf").status(DocumentStatus.PENDING_UPLOAD).build();
        DocumentEntity failedDoc = DocumentEntity.builder().id(203L).fileName("Failed.pdf").status(DocumentStatus.FAILED).build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(1L).grantedBy(grantor).folder(folder).recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        PermissionEntity activeDocPerm = PermissionEntity.builder()
                .id(2L).grantedBy(grantor).document(activeDoc).recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        PermissionEntity pendingDocPerm = PermissionEntity.builder()
                .id(3L).grantedBy(grantor).document(pendingDoc).recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        PermissionEntity failedDocPerm = PermissionEntity.builder()
                .id(4L).grantedBy(grantor).document(failedDoc).recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(folderPerm, activeDocPerm, pendingDocPerm, failedDocPerm));
        when(userRepository.findByEmailInIgnoreCase(anyCollection()))
                .thenReturn(List.of(recipientUser));

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        // Folders are unaffected
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(1L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Active Folder");

        // Only ACTIVE document is included; PENDING_UPLOAD and FAILED are strictly excluded
        assertThat(response.getSharedDocuments()).hasSize(1);
        assertThat(response.getSharedDocuments().get(0).getPermissionId()).isEqualTo(2L);
        assertThat(response.getSharedDocuments().get(0).getName()).isEqualTo("Active.pdf");
        assertThat(response.getSharedDocuments()).noneMatch(d -> d.getPermissionId() == 3L || d.getPermissionId() == 4L);
    }

    @Test
    @DisplayName("Should strictly exclude workspace root folder container allocations from Shared By Me")
    void getSharedByMe_excludesWorkspaceRootFolderAllocation() {
        FolderEntity wsRootFolder = FolderEntity.builder().id(501L).name("Workspace Root").build();
        FolderEntity normalFolder = FolderEntity.builder().id(502L).name("Project Folder").build();

        PermissionEntity wsPerm = PermissionEntity.builder()
                .id(1L).grantedBy(grantor).folder(wsRootFolder)
                .recipientType(RecipientType.GROUP).targetGroup(GroupEntity.builder().id(10L).name("Team A").build())
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        PermissionEntity normalPerm = PermissionEntity.builder()
                .id(2L).grantedBy(grantor).folder(normalFolder)
                .recipientType(RecipientType.GROUP).targetGroup(GroupEntity.builder().id(10L).name("Team A").build())
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(wsPerm, normalPerm));
        when(projectWorkspaceRepository.findByRootFolder_IdIn(anyCollection())).thenAnswer(invocation -> {
            java.util.Collection<Long> ids = invocation.getArgument(0);
            List<com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity> result = new java.util.ArrayList<>();
            if (ids.contains(501L)) {
                result.add(com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity.builder()
                        .id(999L).rootFolder(FolderEntity.builder().id(501L).build()).build());
            }
            return result;
        });

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(2L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Project Folder");
    }

    @Test
    @DisplayName("Should strictly exclude collaboration root folder containers from Shared By Me")
    void getSharedByMe_excludesCollaborationRootFolderAllocation() {
        FolderEntity collabRootFolder = FolderEntity.builder().id(601L).name("Collaboration Root").build();
        FolderEntity normalFolder = FolderEntity.builder().id(602L).name("Shared Subfolder").build();

        CollaborationEntity collab = CollaborationEntity.builder()
                .id(99L).name("Partner Collab").rootFolder(collabRootFolder).build();

        PermissionEntity collabRootPerm = PermissionEntity.builder()
                .id(1L).grantedBy(grantor).folder(collabRootFolder).collaboration(collab)
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        PermissionEntity normalPerm = PermissionEntity.builder()
                .id(2L).grantedBy(grantor).folder(normalFolder).collaboration(collab)
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDesc("grantor-123"))
                .thenReturn(List.of(collabRootPerm, normalPerm));

        SharedByMeResponseDto response = permissionService.getSharedByMe(grantor, true);

        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(2L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Shared Subfolder");
    }
}
