package com.subject77.docshare.modules.document.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.modules.document.dto.*;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.document.entities.DocumentVersionEntity;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

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

    @InjectMocks
    private DocumentServiceImpl documentService;

    private UserEntity user;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        user = UserEntity.builder()
                .id("user-123")
                .email("user@example.com")
                .build();

        document = DocumentEntity.builder()
                .id(1L)
                .fileName("report.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .s3Bucket("docshare-bucket")
                .objectKey("users/user-123/uuid-report.pdf")
                .status(DocumentStatus.PENDING_UPLOAD)
                .uploadedBy(user)
                .build();
    }

    @Test
    @DisplayName("Should successfully initiate document upload")
    void initiateUpload_Success() {
        InitiateUploadRequest request = InitiateUploadRequest.builder()
                .fileName("report.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .build();

        when(permissionEvaluatorService.canUploadToFolder(eq(user), isNull(), isNull())).thenReturn(true);
        when(storageService.generatePresignedUploadUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://presigned-upload.url");
        when(storageService.getDefaultBucketName()).thenReturn("docshare-bucket");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> {
            DocumentEntity d = i.getArgument(0);
            d.setId(1L);
            return d;
        });

        InitiateUploadResponse response = documentService.initiateUpload(request, user);

        assertThat(response).isNotNull();
        assertThat(response.getDocumentId()).isEqualTo(1L);
        assertThat(response.getPresignedUploadUrl()).isEqualTo("https://presigned-upload.url");
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.PENDING_UPLOAD);

        verify(documentRepository).save(any(DocumentEntity.class));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException on initiate upload if user lacks permission")
    void initiateUpload_Denied() {
        InitiateUploadRequest request = InitiateUploadRequest.builder()
                .fileName("report.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .orgCode("UNAUTHORIZED_ORG")
                .build();

        when(permissionEvaluatorService.canUploadToFolder(eq(user), isNull(), eq("UNAUTHORIZED_ORG"))).thenReturn(false);

        assertThatThrownBy(() -> documentService.initiateUpload(request, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should successfully confirm upload when file exists in S3")
    void confirmUpload_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.doesObjectExist(document.getObjectKey())).thenReturn(true);
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(documentVersionRepository.save(any(DocumentVersionEntity.class))).thenAnswer(i -> i.getArgument(0));

        ConfirmUploadRequest request = ConfirmUploadRequest.builder().clientHash("hash123").build();

        DocumentResponse response = documentService.confirmUpload(1L, request, user);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.ACTIVE);
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Should fail confirm upload when object is missing in S3")
    void confirmUpload_ObjectMissing_Fails() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(storageService.doesObjectExist(document.getObjectKey())).thenReturn(false);

        ConfirmUploadRequest request = ConfirmUploadRequest.builder().build();

        assertThatThrownBy(() -> documentService.confirmUpload(1L, request, user))
                .isInstanceOf(StorageFileNotFoundException.class)
                .hasMessageContaining("File object not found in storage");

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.FAILED);
        verify(documentRepository).save(document);
    }

    @Test
    @DisplayName("Should generate download URL for ACTIVE document")
    void generateDownloadUrl_Success() {
        document.setStatus(DocumentStatus.ACTIVE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canDownloadDocument(user, document)).thenReturn(true);
        when(storageService.generatePresignedDownloadUrl(eq(document.getObjectKey()), eq(document.getFileName()), anyLong()))
                .thenReturn("https://presigned-download.url");

        DownloadUrlResponse response = documentService.generateDownloadUrl(1L, user);

        assertThat(response).isNotNull();
        assertThat(response.getDownloadUrl()).isEqualTo("https://presigned-download.url");
        assertThat(response.getFileName()).isEqualTo("report.pdf");
    }

    @Test
    @DisplayName("Should throw IllegalStateException on download if document is PENDING_UPLOAD")
    void generateDownloadUrl_PendingStatus_ThrowsException() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canDownloadDocument(user, document)).thenReturn(true);

        assertThatThrownBy(() -> documentService.generateDownloadUrl(1L, user))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("Document is not ready for download");
    }

    @Test
    @DisplayName("Should reject download when user only has VIEW permission (backend is authoritative)")
    void generateDownloadUrl_ViewOnlyPermission_ThrowsAccessDenied() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canDownloadDocument(user, document)).thenReturn(false);

        assertThatThrownBy(() -> documentService.generateDownloadUrl(1L, user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to download this document");
    }

    @Test
    @DisplayName("Should successfully generate preview URL for user with VIEW permission")
    void generatePreviewUrl_Success() {
        document.setStatus(DocumentStatus.ACTIVE);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canViewDocument(user, document)).thenReturn(true);
        when(storageService.generatePresignedPreviewUrl(eq(document.getObjectKey()), eq(document.getFileName()), eq(document.getContentType()), anyLong()))
                .thenReturn("https://presigned-preview.url");

        PreviewUrlResponse response = documentService.generatePreviewUrl(1L, user);

        assertThat(response).isNotNull();
        assertThat(response.getPreviewUrl()).isEqualTo("https://presigned-preview.url");
        assertThat(response.getFileName()).isEqualTo("report.pdf");
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        verify(auditLogService).logDocumentActivity(
                eq(com.subject77.docshare.modules.audit.entities.Action.PREVIEW),
                eq(document.getId()),
                any(), any(), eq(user.getId()), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("Should reject preview when user lacks VIEW permission")
    void generatePreviewUrl_AccessDenied() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canViewDocument(user, document)).thenReturn(false);

        assertThatThrownBy(() -> documentService.generatePreviewUrl(1L, user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission to view this document");
    }

    @Test
    @DisplayName("Should delete document and remove from S3")
    void deleteDocument_Success() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(permissionEvaluatorService.canEditDocument(user, document)).thenReturn(true);

        documentService.deleteDocument(1L, user);

        verify(storageService).deleteObject(document.getObjectKey());
        verify(documentRepository).delete(document);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when deleting non-existent document")
    void deleteDocument_NotFound() {
        when(documentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(99L, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
