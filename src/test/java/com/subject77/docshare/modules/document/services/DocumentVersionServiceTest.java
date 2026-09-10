package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.dto.*;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.entities.DocumentVersionEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentVersionServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @InjectMocks
    private DocumentServiceImpl documentService;

    private UserEntity user;
    private DocumentEntity document;
    private DocumentVersionEntity v1Entity;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id("user-123")
                .email("test@example.com")
                .build();

        document = DocumentEntity.builder()
                .id(100L)
                .fileName("spec.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .fileHash("hash-v1")
                .s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v1-spec.pdf")
                .status(DocumentStatus.ACTIVE)
                .uploadedBy(user)
                .uploadedAt(Instant.now())
                .version(1)
                .versions(new ArrayList<>())
                .build();

        v1Entity = DocumentVersionEntity.builder()
                .id(1L)
                .document(document)
                .versionNumber(1)
                .s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v1-spec.pdf")
                .fileName("spec.pdf")
                .contentType("application/pdf")
                .fileSize(2048L)
                .checksumSha256("hash-v1")
                .uploadedBy(user)
                .uploadedAt(Instant.now())
                .build();

        document.getVersions().add(v1Entity);
        document.setCurrentVersion(v1Entity);
    }

    @Test
    @DisplayName("Should successfully update document metadata (rename and move folder)")
    void updateMetadata_Success() {
        FolderEntity newFolder = FolderEntity.builder().id(5L).name("New Folder").build();

        UpdateDocumentMetadataRequest request = UpdateDocumentMetadataRequest.builder()
                .fileName("updated_spec.pdf")
                .folderId(5L)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);
        when(folderRepository.findById(5L)).thenReturn(Optional.of(newFolder));
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = documentService.updateMetadata(100L, request, user, "127.0.0.1", "Mozilla");

        assertThat(response).isNotNull();
        assertThat(response.getFileName()).isEqualTo("updated_spec.pdf");
        assertThat(response.getFolderId()).isEqualTo(5L);

        verify(auditLogService, times(2)).logDocumentActivity(any(), eq(100L), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should throw AccessDeniedException on metadata update if permission denied")
    void updateMetadata_Denied() {
        UpdateDocumentMetadataRequest request = UpdateDocumentMetadataRequest.builder()
                .fileName("unauthorized.pdf")
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(false);

        assertThatThrownBy(() -> documentService.updateMetadata(100L, request, user, "127.0.0.1", "Agent"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should initiate upload for new version v2")
    void initiateNewVersion_Success() {
        InitiateNewVersionRequest request = InitiateNewVersionRequest.builder()
                .fileName("spec_v2.pdf")
                .contentType("application/pdf")
                .fileSize(4096L)
                .checksumSha256("hash-v2")
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(100L)).thenReturn(List.of(v1Entity));
        when(storageService.generatePresignedUploadUrl(anyString(), eq("application/pdf"), anyLong()))
                .thenReturn("https://presigned-upload-v2.url");
        when(storageService.getDefaultBucketName()).thenReturn("test-bucket");

        InitiateUploadResponse response = documentService.initiateNewVersion(100L, request, user);

        assertThat(response).isNotNull();
        assertThat(response.getDocumentId()).isEqualTo(100L);
        assertThat(response.getPresignedUploadUrl()).isEqualTo("https://presigned-upload-v2.url");
        verify(auditLogService).logDocumentActivity(any(), eq(100L), eq("hash-v2"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should confirm new version upload and update document current state")
    void confirmNewVersion_Success() {
        ConfirmUploadRequest request = ConfirmUploadRequest.builder()
                .objectKey("users/user-123/key-v2-spec.pdf")
                .fileName("spec_v2.pdf")
                .contentType("application/pdf")
                .fileSize(4096L)
                .clientHash("hash-v2")
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);
        when(storageService.doesObjectExist("users/user-123/key-v2-spec.pdf")).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(100L)).thenReturn(new ArrayList<>(List.of(v1Entity)));

        DocumentVersionEntity v2Entity = DocumentVersionEntity.builder()
                .id(2L)
                .document(document)
                .versionNumber(2)
                .s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v2-spec.pdf")
                .fileName("spec_v2.pdf")
                .contentType("application/pdf")
                .fileSize(4096L)
                .checksumSha256("hash-v2")
                .uploadedBy(user)
                .uploadedAt(Instant.now())
                .build();

        when(documentVersionRepository.save(any(DocumentVersionEntity.class))).thenReturn(v2Entity);
        when(storageService.getDefaultBucketName()).thenReturn("test-bucket");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = documentService.confirmNewVersion(100L, request, user, "127.0.0.1", "Agent");

        assertThat(response).isNotNull();
        assertThat(document.getFileName()).isEqualTo("spec_v2.pdf");
        assertThat(document.getFileHash()).isEqualTo("hash-v2");
        assertThat(document.getCurrentVersion().getVersionNumber()).isEqualTo(2);

        verify(documentVersionRepository).save(any(DocumentVersionEntity.class));
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Should retrieve version history list")
    void getDocumentVersions_Success() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canViewDocument(user, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(100L)).thenReturn(List.of(v1Entity));

        List<DocumentVersionResponse> versions = documentService.getDocumentVersions(100L, user);

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0).getVersionNumber()).isEqualTo(1);
        assertThat(versions.get(0).isCurrent()).isTrue();
    }

    @Test
    @DisplayName("Should generate download URL for historical version")
    void generateVersionDownloadUrl_Success() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canDownloadDocument(user, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1Entity));
        when(storageService.generatePresignedDownloadUrl(v1Entity.getObjectKey(), v1Entity.getFileName(), 15L))
                .thenReturn("https://presigned-download-v1.url");

        DownloadUrlResponse response = documentService.generateVersionDownloadUrl(100L, 1, user);

        assertThat(response).isNotNull();
        assertThat(response.getDownloadUrl()).isEqualTo("https://presigned-download-v1.url");
        assertThat(response.getFileName()).isEqualTo("spec.pdf");
    }

    @Test
    @DisplayName("Should successfully restore historical version v1 creating new version v3 and updating currentVersion")
    void restoreVersion_Success_CreatesNewMonotonicVersion() {
        // Current active version is v2
        DocumentVersionEntity v2Entity = DocumentVersionEntity.builder()
                .id(2L)
                .document(document)
                .versionNumber(2)
                .s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v2-spec.pdf")
                .fileName("spec_v2.pdf")
                .contentType("application/pdf")
                .fileSize(4096L)
                .checksumSha256("hash-v2")
                .uploadedBy(user)
                .uploadedAt(Instant.now())
                .build();
        document.getVersions().add(v2Entity);
        document.setCurrentVersion(v2Entity);

        UserEntity restorer = UserEntity.builder()
                .id("restorer-456")
                .email("restorer@example.com")
                .build();

        RestoreVersionRequest request = RestoreVersionRequest.builder()
                .reason("Reverting incorrect modifications from v2")
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(restorer, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1Entity));
        when(storageService.doesObjectExist("test-bucket", "users/user-123/key-v1-spec.pdf")).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(100L)).thenReturn(List.of(v2Entity, v1Entity));

        DocumentVersionEntity v3Saved = DocumentVersionEntity.builder()
                .id(3L)
                .document(document)
                .versionNumber(3)
                .s3Bucket(v1Entity.getS3Bucket())
                .objectKey(v1Entity.getObjectKey())
                .fileName(v1Entity.getFileName())
                .contentType(v1Entity.getContentType())
                .fileSize(v1Entity.getFileSize())
                .checksumSha256(v1Entity.getChecksumSha256())
                .uploadedBy(restorer)
                .uploadedAt(Instant.now())
                .build();

        when(documentVersionRepository.save(any(DocumentVersionEntity.class))).thenReturn(v3Saved);
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = documentService.restoreVersion(100L, 1, request, restorer, "127.0.0.1", "TestAgent");

        assertThat(response).isNotNull();
        // Active pointer updated
        assertThat(document.getCurrentVersion()).isEqualTo(v3Saved);
        // Byte/hash equivalence preserved from source version v1
        assertThat(document.getFileHash()).isEqualTo("hash-v1");
        assertThat(document.getFileName()).isEqualTo("spec.pdf");
        assertThat(document.getFileSize()).isEqualTo(2048L);
        assertThat(document.getLastModifiedBy()).isEqualTo(restorer);

        // Verify source version v1 was never mutated
        assertThat(v1Entity.getVersionNumber()).isEqualTo(1);
        assertThat(v1Entity.getChecksumSha256()).isEqualTo("hash-v1");
        assertThat(v1Entity.getUploadedBy()).isEqualTo(user);

        // Verify exact 2-argument storage verification
        verify(storageService).doesObjectExist("test-bucket", "users/user-123/key-v1-spec.pdf");

        // Verify governance audit logged via REQUIRED propagation path
        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        AuditPermissionChangeCommand capturedCmd = auditCaptor.getValue();
        assertThat(capturedCmd.getAction()).isEqualTo("DOCUMENT_VERSION_RESTORED");
        assertThat(capturedCmd.getTargetId()).isEqualTo(100L);
        assertThat(capturedCmd.getTargetFileHash()).isEqualTo("hash-v1");
        assertThat(capturedCmd.getActorUserId()).isEqualTo("restorer-456");
        assertThat(capturedCmd.getPreviousValue()).contains("v2");
        assertThat(capturedCmd.getNewValue()).contains("v3");
        assertThat(capturedCmd.getReason()).isEqualTo("Reverting incorrect modifications from v2");

        // Verify notification dispatched to document owner and previous version author (excluding restorer)
        verify(notificationService).createNotification(
                eq(user),
                eq(restorer),
                eq(NotificationType.DOCUMENT_VERSION_RESTORED),
                eq("Document Version Restored"),
                contains("Version v1 of 'spec.pdf' was restored as version v3"),
                eq(NotificationSourceType.DOCUMENT),
                eq("100"),
                eq(NotificationCategory.DOCUMENT),
                eq(NotificationPriority.MEDIUM),
                eq("DOCUMENT_VERSION_RESTORED:3:user-123")
        );
    }

    @Test
    @DisplayName("Should reject restore when requested version is already currently active")
    void restoreVersion_SameVersionActive_ThrowsInvalidDocumentStateException() {
        // v1Entity is already the active version
        document.setCurrentVersion(v1Entity);

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1Entity));

        assertThatThrownBy(() -> documentService.restoreVersion(100L, 1, null, user, "127.0.0.1", "Agent"))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("already the active version");

        verify(documentVersionRepository, never()).save(any());
        verify(documentRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any());
    }

    @Test
    @DisplayName("Should reject restore when document is not in ACTIVE state")
    void restoreVersion_DocumentNotActive_ThrowsInvalidDocumentStateException() {
        document.setStatus(DocumentStatus.PENDING_UPLOAD);

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);

        assertThatThrownBy(() -> documentService.restoreVersion(100L, 1, null, user, "127.0.0.1", "Agent"))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("not in ACTIVE state");

        verify(documentVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject restore when user does not have EDIT permission")
    void restoreVersion_PermissionDenied_ThrowsAccessDeniedException() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(false);

        assertThatThrownBy(() -> documentService.restoreVersion(100L, 1, null, user, "127.0.0.1", "Agent"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to restore versions");

        verify(documentVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject restore when source version does not exist")
    void restoreVersion_VersionNotFound_ThrowsResourceNotFoundException() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.restoreVersion(100L, 99, null, user, "127.0.0.1", "Agent"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Version 99 not found");

        verify(documentVersionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject restore when storage object is missing without modifying database")
    void restoreVersion_StorageFileNotFound_ThrowsStorageFileNotFoundException() {
        DocumentVersionEntity v2Entity = DocumentVersionEntity.builder()
                .id(2L).document(document).versionNumber(2).s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v2-spec.pdf").fileName("spec_v2.pdf")
                .contentType("application/pdf").fileSize(4096L).checksumSha256("hash-v2")
                .uploadedBy(user).uploadedAt(Instant.now()).build();
        document.setCurrentVersion(v2Entity);

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1Entity));
        when(storageService.doesObjectExist("test-bucket", "users/user-123/key-v1-spec.pdf")).thenReturn(false);

        assertThatThrownBy(() -> documentService.restoreVersion(100L, 1, null, user, "127.0.0.1", "Agent"))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("File object not found in storage");

        // Absolute guarantee: zero database modifications when storage check fails
        verify(documentVersionRepository, never()).save(any());
        verify(documentRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any());
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should exclude actor and suspended users from receiving notifications")
    void restoreVersion_NotificationExclusions_ActorAndInactiveUsersExcluded() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ORG_EXCL").build();
        document.setOwnerOrg(org);

        UserEntity actor = user; // Actor is owner
        DocumentVersionEntity v2Entity = DocumentVersionEntity.builder()
                .id(2L).document(document).versionNumber(2).s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v2-spec.pdf").fileName("spec_v2.pdf")
                .contentType("application/pdf").fileSize(4096L).checksumSha256("hash-v2")
                .uploadedBy(user).uploadedAt(Instant.now()).build();
        document.setCurrentVersion(v2Entity);

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(actor, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1Entity));
        when(storageService.doesObjectExist("test-bucket", "users/user-123/key-v1-spec.pdf")).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(100L)).thenReturn(List.of(v2Entity, v1Entity));

        DocumentVersionEntity v3Saved = DocumentVersionEntity.builder().id(3L).versionNumber(3)
                .s3Bucket("test-bucket").objectKey("k").fileName("f").contentType("c").fileSize(10L).checksumSha256("h")
                .uploadedBy(actor).build();
        when(documentVersionRepository.save(any())).thenReturn(v3Saved);
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        documentService.restoreVersion(100L, 1, null, actor, "127.0.0.1", "Agent");

        // Actor is the owner, and previous active author is also actor -> 0 notifications dispatched
        verify(notificationService, never()).createNotification(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should not query or notify workspace managers in Personal Space")
    void restoreVersion_PersonalWorkspace_DoesNotNotifyWorkspaceManagers() {
        document.setOwnerOrg(null); // Personal Space
        UserEntity restorer = UserEntity.builder().id("restorer-789").email("restorer789@example.com").build();

        DocumentVersionEntity v2Entity = DocumentVersionEntity.builder()
                .id(2L).document(document).versionNumber(2).s3Bucket("test-bucket")
                .objectKey("users/user-123/key-v2-spec.pdf").fileName("spec_v2.pdf")
                .contentType("application/pdf").fileSize(4096L).checksumSha256("hash-v2")
                .uploadedBy(user).uploadedAt(Instant.now()).build();
        document.setCurrentVersion(v2Entity);

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(restorer, document)).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdAndVersionNumber(100L, 1)).thenReturn(Optional.of(v1Entity));
        when(storageService.doesObjectExist("test-bucket", "users/user-123/key-v1-spec.pdf")).thenReturn(true);
        when(documentVersionRepository.findByDocument_IdOrderByVersionNumberDesc(100L)).thenReturn(List.of(v2Entity, v1Entity));

        DocumentVersionEntity v3Saved = DocumentVersionEntity.builder().id(3L).versionNumber(3)
                .s3Bucket("test-bucket").objectKey("k").fileName("f").contentType("c").fileSize(10L).checksumSha256("h")
                .uploadedBy(restorer).build();
        when(documentVersionRepository.save(any())).thenReturn(v3Saved);
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        documentService.restoreVersion(100L, 1, null, restorer, "127.0.0.1", "Agent");

        // Workspace manager repository should never be touched for personal space
        verify(workspaceManagerRepository, never()).findByWorkspace_Id(any());
        // Only owner notified
        verify(notificationService, times(1)).createNotification(
                eq(user), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
