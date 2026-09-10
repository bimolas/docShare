package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.dto.ShareLinkRequestDto;
import com.subject77.docshare.modules.permission.dto.ShareLinkResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.modules.document.dto.DownloadUrlResponse;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.document.services.StorageService;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class ShareServiceImplTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private PermissionEventPublisher eventPublisher;

    @Mock
    private StorageService storageService;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @InjectMocks
    private ShareServiceImpl shareService;

    private UserEntity currentUser;
    private FolderEntity testFolder;
    private DocumentEntity testDocument;

    @BeforeEach
    void setUp() {
        currentUser = UserEntity.builder()
                .id("usr-123")
                .email("user@test.com")
                .role(Role.ROLE_USER)
                .build();

        testFolder = FolderEntity.builder()
                .id(1L)
                .name("Public Shared Folder")
                .createdBy(currentUser)
                .build();

        testDocument = DocumentEntity.builder()
                .id(100L)
                .fileName("Confidential.pdf")
                .fileHash("hash-100")
                .contentType("application/pdf")
                .s3Bucket("test-bucket")
                .objectKey("s3-key-100")
                .fileSize(1024L)
                .folder(testFolder)
                .uploadedBy(currentUser)
                .build();

        lenient().when(evaluatorService.resolveDelegationCeiling(any(), any(), any())).thenReturn(PermissionLevel.EDIT);
        lenient().when(evaluatorService.canActorOverrideGrant(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Should create share link for folder successfully")
    void createShareLink_Folder_Success() {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(1L)
                .permissionLevel(PermissionLevel.VIEW)
                .expiresInHours(24)
                .build();

        when(folderRepository.findById(1L)).thenReturn(Optional.of(testFolder));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, testFolder)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity p = invocation.getArgument(0);
            p.setId(50L);
            return p;
        });

        ShareLinkResponseDto response = shareService.createShareLink(request, currentUser);

        assertThat(response.getPermissionId()).isEqualTo(50L);
        assertThat(response.getFolderId()).isEqualTo(1L);
        assertThat(response.getFolderName()).isEqualTo("Public Shared Folder");
        assertThat(response.getShareLinkToken()).isNotNull();
        assertThat(response.isRequiresPassword()).isFalse();
        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should create share link for document successfully with documentName and active status")
    void createShareLink_Document_Success() {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .documentId(100L)
                .permissionLevel(PermissionLevel.VIEW)
                .expiresInHours(24)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(testDocument));
        when(evaluatorService.canAdministerDocumentPermissions(currentUser, testDocument)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity p = invocation.getArgument(0);
            p.setId(51L);
            return p;
        });

        ShareLinkResponseDto response = shareService.createShareLink(request, currentUser);

        assertThat(response.getPermissionId()).isEqualTo(51L);
        assertThat(response.getDocumentId()).isEqualTo(100L);
        assertThat(response.getDocumentName()).isEqualTo("Confidential.pdf");
        assertThat(response.getShareLinkToken()).isNotNull();
        assertThat(response.isRequiresPassword()).isFalse();
        assertThat(response.getExpiresAt()).isNotNull();
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should create share link with note field successfully")
    void createShareLink_WithNote_Success() {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(1L)
                .permissionLevel(PermissionLevel.VIEW)
                .expiresInHours(24)
                .note("Please review sections 3 and 4 before Friday.")
                .build();

        when(folderRepository.findById(1L)).thenReturn(Optional.of(testFolder));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, testFolder)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity p = invocation.getArgument(0);
            p.setId(50L);
            return p;
        });

        ShareLinkResponseDto response = shareService.createShareLink(request, currentUser);

        assertThat(response.getPermissionId()).isEqualTo(50L);
        assertThat(response.getNote()).isEqualTo("Please review sections 3 and 4 before Friday.");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user cannot administer folder permissions")
    void createShareLink_Folder_AccessDenied() {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(1L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(folderRepository.findById(1L)).thenReturn(Optional.of(testFolder));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, testFolder)).thenReturn(false);

        assertThatThrownBy(() -> shareService.createShareLink(request, currentUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should create password protected share link")
    void createShareLink_PasswordProtected_Success() {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(1L)
                .permissionLevel(PermissionLevel.VIEW)
                .password("secret123")
                .build();

        when(folderRepository.findById(1L)).thenReturn(Optional.of(testFolder));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, testFolder)).thenReturn(true);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encodedPasswordHash");
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkResponseDto response = shareService.createShareLink(request, currentUser);

        assertThat(response.isRequiresPassword()).isTrue();
        verify(passwordEncoder).encode("secret123");
    }

    @Test
    @DisplayName("Should throw exception when accessing password protected share link with wrong password")
    void accessSharedResource_WrongPassword_ThrowsException() {
        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("token123")
                .folder(testFolder)
                .passwordHash("$2a$10$encodedPasswordHash")
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token123")).thenReturn(Optional.of(permission));
        when(passwordEncoder.matches("wrong", "$2a$10$encodedPasswordHash")).thenReturn(false);

        assertThatThrownBy(() -> shareService.accessSharedResource("token123", "wrong", null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Invalid or missing password");
    }

    @Test
    @DisplayName("Should throw exception when accessing expired share link")
    void accessSharedResource_Expired_ThrowsException() {
        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("token123")
                .folder(testFolder)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token123")).thenReturn(Optional.of(permission));

        assertThatThrownBy(() -> shareService.accessSharedResource("token123", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("Should revoke share link successfully")
    void revokeShareLink_Success() {
        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("token123")
                .folder(testFolder)
                .revoked(false)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token123")).thenReturn(Optional.of(permission));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, testFolder)).thenReturn(true);
        when(permissionRepository.save(permission)).thenReturn(permission);

        shareService.revokeShareLink("token123", currentUser);

        assertThat(permission.isRevoked()).isTrue();
        assertThat(permission.getRevokedBy()).isEqualTo(currentUser);
        verify(permissionRepository).save(permission);
    }

    @Test
    @DisplayName("Should exclude file from share link by creating EXCLUDE permission")
    void excludeFileFromShare_Success() {
        PermissionEntity parentShare = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("token123")
                .permissionLevel(PermissionLevel.VIEW)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("token123")).thenReturn(Optional.of(parentShare));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(testDocument));
        when(evaluatorService.canAdministerDocumentPermissions(currentUser, testDocument)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShareLinkResponseDto response = shareService.excludeFileFromShare("token123", 100L, currentUser);

        assertThat(response.getPermissionType()).isEqualTo(PermissionType.EXCLUDE);
        assertThat(response.getDocumentId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Should generate presigned download URL for document share link successfully")
    void downloadSharedDocument_DocumentShareLink_Success() {
        testDocument.setStatus(DocumentStatus.ACTIVE);
        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("doc-token-123")
                .document(testDocument)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("doc-token-123")).thenReturn(Optional.of(permission));
        when(storageService.generatePresignedDownloadUrl(eq(testDocument.getObjectKey()), eq(testDocument.getFileName()), anyLong()))
                .thenReturn("https://s3.fake-url.com/download/Confidential.pdf");

        DownloadUrlResponse response = shareService.downloadSharedDocument("doc-token-123", null, 100L, currentUser);

        assertThat(response.getDocumentId()).isEqualTo(100L);
        assertThat(response.getDownloadUrl()).contains("https://s3.fake-url.com/download/");
        assertThat(response.getFileName()).isEqualTo("Confidential.pdf");
    }

    @Test
    @DisplayName("Should generate presigned download URL for folder share link document in hierarchy successfully")
    void downloadSharedDocument_FolderShareLink_Success() {
        testDocument.setStatus(DocumentStatus.ACTIVE);
        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("folder-token-123")
                .folder(testFolder)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("folder-token-123")).thenReturn(Optional.of(permission));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(testDocument));
        when(permissionRepository.findByShareLinkTokenAndPermissionTypeAndRevokedFalse("folder-token-123", PermissionType.EXCLUDE))
                .thenReturn(List.of());
        when(storageService.generatePresignedDownloadUrl(eq(testDocument.getObjectKey()), eq(testDocument.getFileName()), anyLong()))
                .thenReturn("https://s3.fake-url.com/download/Confidential.pdf");

        DownloadUrlResponse response = shareService.downloadSharedDocument("folder-token-123", null, 100L, currentUser);

        assertThat(response.getDocumentId()).isEqualTo(100L);
        assertThat(response.getDownloadUrl()).isNotNull();
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when downloading excluded document under folder share link")
    void downloadSharedDocument_FolderShareLink_ExcludedFile_ThrowsException() {
        testDocument.setStatus(DocumentStatus.ACTIVE);
        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("folder-token-123")
                .folder(testFolder)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        PermissionEntity exclusion = PermissionEntity.builder()
                .id(51L)
                .shareLinkToken("folder-token-123")
                .permissionType(PermissionType.EXCLUDE)
                .document(testDocument)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("folder-token-123")).thenReturn(Optional.of(permission));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(testDocument));
        when(permissionRepository.findByShareLinkTokenAndPermissionTypeAndRevokedFalse("folder-token-123", PermissionType.EXCLUDE))
                .thenReturn(List.of(exclusion));

        assertThatThrownBy(() -> shareService.downloadSharedDocument("folder-token-123", null, 100L, currentUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("excluded");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when downloading document from an excluded parent folder")
    void downloadSharedDocument_FolderShareLink_ExcludedParentFolder_ThrowsException() {
        testDocument.setStatus(DocumentStatus.ACTIVE);
        FolderEntity subFolder = FolderEntity.builder()
                .id(2L)
                .name("SubFolder")
                .parentFolder(testFolder)
                .build();
        testDocument.setFolder(subFolder);

        PermissionEntity permission = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("folder-token-123")
                .folder(testFolder)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        PermissionEntity folderExclusion = PermissionEntity.builder()
                .id(52L)
                .shareLinkToken("folder-token-123")
                .permissionType(PermissionType.EXCLUDE)
                .folder(subFolder)
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("folder-token-123")).thenReturn(Optional.of(permission));
        when(documentRepository.findById(100L)).thenReturn(Optional.of(testDocument));
        when(permissionRepository.findByShareLinkTokenAndPermissionTypeAndRevokedFalse("folder-token-123", PermissionType.EXCLUDE))
                .thenReturn(List.of(folderExclusion));

        assertThatThrownBy(() -> shareService.downloadSharedDocument("folder-token-123", null, 100L, currentUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("containing folder is excluded");
    }

    @Test
    @DisplayName("Should exclude a folder from a share link successfully")
    void excludeResourceFromShare_Folder_Success() {
        FolderEntity subFolder = FolderEntity.builder()
                .id(2L)
                .name("SubFolder")
                .parentFolder(testFolder)
                .build();

        PermissionEntity parentShare = PermissionEntity.builder()
                .id(50L)
                .shareLinkToken("folder-token-123")
                .folder(testFolder)
                .permissionLevel(PermissionLevel.VIEW)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        when(permissionRepository.findByShareLinkTokenAndRevokedFalse("folder-token-123")).thenReturn(Optional.of(parentShare));
        when(folderRepository.findById(2L)).thenReturn(Optional.of(subFolder));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, subFolder)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity p = invocation.getArgument(0);
            p.setId(99L);
            return p;
        });

        ShareLinkResponseDto response = shareService.excludeResourceFromShare("folder-token-123", null, 2L, currentUser);

        assertThat(response).isNotNull();
        assertThat(response.getFolderId()).isEqualTo(2L);
        assertThat(response.getPermissionType()).isEqualTo(PermissionType.EXCLUDE);
        verify(permissionRepository).save(any(PermissionEntity.class));
        verify(auditLogService).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("Phase 3A: createShareLink respects external-sharing policy")
    void createShareLink_RespectsExternalSharingPolicy() {
        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(1L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(folderRepository.findById(1L)).thenReturn(Optional.of(testFolder));
        when(evaluatorService.canAdministerFolderPermissions(currentUser, testFolder)).thenReturn(true);
        doThrow(new AccessDeniedException("External sharing is disabled for the source workspace"))
                .when(externalSharingPolicyEvaluator).evaluateAndValidateSharingPolicy(eq(testFolder), isNull(), any(), isNull());

        assertThatThrownBy(() -> shareService.createShareLink(request, currentUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("External sharing is disabled");
    }

    @Test
    @DisplayName("Phase 3A: createShareLink respects organization isolation - rejects mismatched orgs")
    void createShareLink_RespectsOrganizationIsolation_MismatchedOrgs() {
        com.subject77.docshare.modules.organization.entities.OrganizationEntity orgA =
                com.subject77.docshare.modules.organization.entities.OrganizationEntity.builder().orgCode("ORG_A").build();
        com.subject77.docshare.modules.organization.entities.OrganizationEntity orgB =
                com.subject77.docshare.modules.organization.entities.OrganizationEntity.builder().orgCode("ORG_B").build();

        FolderEntity folder = FolderEntity.builder().id(1L).ownerOrg(orgA).build();
        DocumentEntity doc = DocumentEntity.builder().id(2L).ownerOrg(orgB).build();

        ShareLinkRequestDto request = ShareLinkRequestDto.builder()
                .folderId(1L)
                .documentId(2L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(folderRepository.findById(1L)).thenReturn(Optional.of(folder));
        when(documentRepository.findById(2L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> shareService.createShareLink(request, currentUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Folder and document belong to different organizations");
    }
}
