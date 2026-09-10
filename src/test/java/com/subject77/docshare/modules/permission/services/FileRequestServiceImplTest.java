package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.InvalidDocumentStateException;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.exceptions.StorageFileNotFoundException;
import com.subject77.docshare.modules.audit.entities.Action;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.dto.ConfirmUploadRequest;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.InitiateUploadRequest;
import com.subject77.docshare.modules.document.dto.InitiateUploadResponse;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.document.repository.DocumentVersionRepository;
import com.subject77.docshare.modules.document.services.StorageService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.FileRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.FileRequestEntity;
import com.subject77.docshare.modules.permission.entities.FileRequestRecipientType;
import com.subject77.docshare.modules.permission.entities.FileRequestStatus;
import com.subject77.docshare.modules.permission.mapper.FileRequestMapper;
import com.subject77.docshare.modules.permission.repository.FileRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileRequestServiceImplTest {

    @Mock
    private FileRequestRepository fileRequestRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentVersionRepository documentVersionRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PermissionRepository permissionRepository;

    @Spy
    private FileRequestMapper fileRequestMapper = new FileRequestMapper();

    @InjectMocks
    private FileRequestServiceImpl fileRequestService;

    private UserEntity requester;
    private UserEntity recipient;
    private UserEntity thirdParty;
    private FolderEntity destinationFolder;
    private FileRequestEntity pendingRequest;

    @BeforeEach
    void setUp() {
        requester = UserEntity.builder()
                .id("1")
                .email("requester@example.com")
                .firstName("Alice")
                .lastName("Requester")
                .build();

        recipient = UserEntity.builder()
                .id("2")
                .email("recipient@example.com")
                .firstName("Bob")
                .lastName("Recipient")
                .build();

        thirdParty = UserEntity.builder()
                .id("3")
                .email("charlie@example.com")
                .firstName("Charlie")
                .build();

        destinationFolder = FolderEntity.builder()
                .id(100L)
                .name("Secure Uploads Folder")
                .build();

        pendingRequest = FileRequestEntity.builder()
                .id(50L)
                .requester(requester)
                .recipientType(FileRequestRecipientType.USER)
                .recipientUser(recipient)
                .destinationFolder(destinationFolder)
                .title("Tax Returns 2026")
                .note("Please upload W2 and 1099")
                .status(FileRequestStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createFileRequest: success when requester has folder edit/admin rights")
    void createFileRequest_success() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientUserId("2")
                .title("Tax Returns 2026")
                .note("Please upload W2")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(userRepository.findById("2")).thenReturn(Optional.of(recipient));
        when(fileRequestRepository.save(any(FileRequestEntity.class))).thenAnswer(invocation -> {
            FileRequestEntity entity = invocation.getArgument(0);
            entity.setId(50L);
            return entity;
        });

        FileRequestResponseDto response = fileRequestService.createFileRequest(dto, requester);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(50L);
        assertThat(response.getRequesterId()).isEqualTo("1");
        assertThat(response.getRecipientId()).isEqualTo("2");
        assertThat(response.getDestinationFolderId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(FileRequestStatus.PENDING);

        verify(auditLogService).logDocumentActivity(
                eq(Action.FILE_REQUEST_CREATED),
                isNull(), isNull(), eq(100L), eq("requester@example.com"),
                isNull(), anyString(), anyString(), anyString(), isNull()
        );
        verify(notificationService).createNotification(
                eq(recipient), eq(requester), any(), anyString(), anyString(), any(), eq("50"), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("createFileRequest: throws AccessDeniedException when requester lacks folder rights")
    void createFileRequest_unauthorizedRequester_throwsException() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientUserId("2")
                .title("Financial Docs")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(false);
        when(permissionEvaluatorService.canAdministerFolderPermissions(requester, destinationFolder)).thenReturn(false);

        assertThatThrownBy(() -> fileRequestService.createFileRequest(dto, requester))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have permission");

        verify(fileRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFileRequest: throws ResourceNotFoundException when recipient user not found")
    void createFileRequest_recipientNotFound_throwsException() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientUserId("999")
                .title("Upload File")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(userRepository.findById("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileRequestService.createFileRequest(dto, requester))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Recipient user not found");
    }

    @Test
    @DisplayName("getRequestsMine: returns requests created by requester")
    void getRequestsMine_returnsRequesterRequests() {
        when(fileRequestRepository.findByRequesterIdOrderByCreatedAtDesc("1"))
                .thenReturn(List.of(pendingRequest));

        List<FileRequestResponseDto> results = fileRequestService.getRequestsMine(requester);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getRequestsReceived: returns requests received by recipient")
    void getRequestsReceived_returnsRecipientRequests() {
        when(fileRequestRepository.findByRecipientUserIdOrderByCreatedAtDesc("2"))
                .thenReturn(List.of(pendingRequest));
        when(fileRequestRepository.findTeamRequestsForUser("2")).thenReturn(List.of());

        List<FileRequestResponseDto> results = fileRequestService.getRequestsReceived(recipient);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getFileRequestById: authorized user (requester or recipient) gets details")
    void getFileRequestById_authorized_returnsDetails() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        FileRequestResponseDto resp1 = fileRequestService.getFileRequestById(50L, requester);
        assertThat(resp1.getId()).isEqualTo(50L);

        FileRequestResponseDto resp2 = fileRequestService.getFileRequestById(50L, recipient);
        assertThat(resp2.getId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getFileRequestById: unauthorized third party throws AccessDeniedException")
    void getFileRequestById_unauthorizedThirdParty_throwsException() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> fileRequestService.getFileRequestById(50L, thirdParty))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("initiateUpload: valid recipient initiates upload successfully")
    void initiateUpload_validRecipientPendingRequest_success() {
        InitiateUploadRequest initReq = InitiateUploadRequest.builder()
                .fileName("statement.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(documentRepository.findByFileHashAndUploadedBy_IdAndStatusAndFolder_Id(
                "hash123", "2", DocumentStatus.PENDING_UPLOAD, 100L
        )).thenReturn(Optional.empty());
        when(storageService.getDefaultBucketName()).thenReturn("docshare-bucket");
        when(storageService.generatePresignedUploadUrl(anyString(), eq("application/pdf"), eq(15L)))
                .thenReturn("https://s3.amazonaws.com/presigned-url");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> {
            DocumentEntity d = i.getArgument(0);
            d.setId(200L);
            return d;
        });

        InitiateUploadResponse response = fileRequestService.initiateUpload(50L, initReq, recipient);

        assertThat(response).isNotNull();
        assertThat(response.getDocumentId()).isEqualTo(200L);
        assertThat(response.getPresignedUploadUrl()).isEqualTo("https://s3.amazonaws.com/presigned-url");

        verify(auditLogService).logDocumentActivity(
                eq(Action.INITIATE_UPLOAD), eq(200L), eq("hash123"), eq(100L),
                eq("recipient@example.com"), isNull(), anyString(), anyString(), anyString(), isNull()
        );
    }

    @Test
    @DisplayName("initiateUpload: non-recipient throws AccessDeniedException")
    void initiateUpload_unauthorizedUser_throwsException() {
        InitiateUploadRequest initReq = InitiateUploadRequest.builder()
                .fileName("statement.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> fileRequestService.initiateUpload(50L, initReq, requester))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("initiateUpload: non-pending request throws InvalidDocumentStateException")
    void initiateUpload_nonPendingRequest_throwsException() {
        pendingRequest.setStatus(FileRequestStatus.FULFILLED);
        InitiateUploadRequest initReq = InitiateUploadRequest.builder()
                .fileName("statement.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> fileRequestService.initiateUpload(50L, initReq, recipient))
                .isInstanceOf(InvalidDocumentStateException.class);
    }

    @Test
    @DisplayName("confirmUpload: recipient confirms upload and document becomes ACTIVE without folder permissions granted")
    void confirmUpload_validRecipientPendingRequest_success() {
        ConfirmUploadRequest confirmReq = ConfirmUploadRequest.builder()
                .documentId(200L)
                .clientHash("hash123")
                .build();

        DocumentEntity pendingDoc = DocumentEntity.builder()
                .id(200L)
                .fileName("statement.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .objectKey("users/2/uuid-statement.pdf")
                .s3Bucket("docshare-bucket")
                .status(DocumentStatus.PENDING_UPLOAD)
                .uploadedBy(recipient)
                .folder(destinationFolder)
                .fileRequest(pendingRequest)
                .version(1)
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(documentRepository.findById(200L)).thenReturn(Optional.of(pendingDoc));
        when(storageService.doesObjectExist(pendingDoc.getObjectKey())).thenReturn(true);
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = fileRequestService.confirmUpload(50L, confirmReq, recipient);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(200L);
        assertThat(response.getStatus()).isEqualTo(DocumentStatus.ACTIVE);

        // Verify recipient was NOT granted any PermissionEntity records
        verifyNoInteractions(permissionRepository);

        verify(auditLogService).logDocumentActivity(
                eq(Action.FILE_REQUEST_UPLOADED), eq(200L), eq("hash123"), eq(100L),
                eq("recipient@example.com"), isNull(), anyString(), anyString(), anyString(), isNull()
        );
        verify(notificationService).createNotification(
                eq(requester), eq(recipient), any(), anyString(), anyString(), any(), eq("50"), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("completeFileRequest: by recipient marks status FULFILLED")
    void completeFileRequest_byRecipient_success() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(fileRequestRepository.markFulfilledIfPending(eq(50L), any())).thenReturn(1);

        FileRequestResponseDto response = fileRequestService.completeFileRequest(50L, recipient);

        assertThat(response.getStatus()).isEqualTo(FileRequestStatus.FULFILLED);
        assertThat(response.getFulfilledAt()).isNotNull();

        verify(notificationService).createNotification(
                eq(requester), eq(recipient), any(), anyString(), anyString(), any(), eq("50"), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("cancelFileRequest: by requester marks status CANCELLED")
    void cancelFileRequest_byRequester_success() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(fileRequestRepository.markCancelledIfPending(eq(50L), any())).thenReturn(1);

        FileRequestResponseDto response = fileRequestService.cancelFileRequest(50L, requester);

        assertThat(response.getStatus()).isEqualTo(FileRequestStatus.CANCELLED);
        assertThat(response.getCancelledAt()).isNotNull();

        verify(notificationService).createNotification(
                eq(recipient), eq(requester), any(), anyString(), anyString(), any(), eq("50"), any(), any(), anyString()
        );
    }

    @Test
    @DisplayName("cancelFileRequest: by recipient throws AccessDeniedException")
    void cancelFileRequest_byRecipient_throwsException() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(permissionEvaluatorService.canEditFolder(recipient, destinationFolder)).thenReturn(false);
        when(permissionEvaluatorService.canAdministerFolderPermissions(recipient, destinationFolder)).thenReturn(false);

        assertThatThrownBy(() -> fileRequestService.cancelFileRequest(50L, recipient))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── Phase 7D.1: Lifecycle Expiration Consistency ──────────────────────────

    @Test
    @DisplayName("confirmUpload: throws InvalidDocumentStateException when file request is expired")
    void confirmUpload_expiredRequest_throwsException() {
        pendingRequest.setExpiresAt(Instant.now().minusSeconds(60));
        ConfirmUploadRequest confirmReq = ConfirmUploadRequest.builder()
                .documentId(200L)
                .clientHash("hash123")
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> fileRequestService.confirmUpload(50L, confirmReq, recipient))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("expired");

        assertThat(pendingRequest.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        verify(fileRequestRepository).save(pendingRequest);
        verifyNoInteractions(documentRepository);
    }

    @Test
    @DisplayName("completeFileRequest: throws InvalidDocumentStateException when file request is expired")
    void completeFileRequest_expiredRequest_throwsException() {
        pendingRequest.setExpiresAt(Instant.now().minusSeconds(60));
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> fileRequestService.completeFileRequest(50L, recipient))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("expired");

        assertThat(pendingRequest.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        verify(fileRequestRepository).save(pendingRequest);
        verify(fileRequestRepository, never()).markFulfilledIfPending(any(), any());
        verifyNoInteractions(notificationService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("cancelFileRequest: throws InvalidDocumentStateException when file request is expired")
    void cancelFileRequest_expiredRequest_throwsException() {
        pendingRequest.setExpiresAt(Instant.now().minusSeconds(60));
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        assertThatThrownBy(() -> fileRequestService.cancelFileRequest(50L, requester))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("expired");

        assertThat(pendingRequest.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        verify(fileRequestRepository).save(pendingRequest);
        verify(fileRequestRepository, never()).markCancelledIfPending(any(), any());
        verifyNoInteractions(notificationService);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("getFileRequestById: lazily marks expired request as EXPIRED")
    void getFileRequestById_expiredRequest_lazilyTransitionsToExpired() {
        pendingRequest.setExpiresAt(Instant.now().minusSeconds(60));
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));

        FileRequestResponseDto dto = fileRequestService.getFileRequestById(50L, requester);

        assertThat(dto.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        assertThat(pendingRequest.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        verify(fileRequestRepository).save(pendingRequest);
    }

    @Test
    @DisplayName("getRequestsMine: lazily marks expired requests as EXPIRED")
    void getRequestsMine_expiredRequest_lazilyTransitionsToExpired() {
        pendingRequest.setExpiresAt(Instant.now().minusSeconds(60));
        when(fileRequestRepository.findByRequesterIdOrderByCreatedAtDesc("1"))
                .thenReturn(List.of(pendingRequest));

        List<FileRequestResponseDto> dtos = fileRequestService.getRequestsMine(requester);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        assertThat(pendingRequest.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        verify(fileRequestRepository).save(pendingRequest);
    }

    @Test
    @DisplayName("getRequestsReceived: lazily marks expired requests as EXPIRED")
    void getRequestsReceived_expiredRequest_lazilyTransitionsToExpired() {
        pendingRequest.setExpiresAt(Instant.now().minusSeconds(60));
        when(fileRequestRepository.findByRecipientUserIdOrderByCreatedAtDesc("2"))
                .thenReturn(List.of(pendingRequest));
        when(fileRequestRepository.findTeamRequestsForUser("2")).thenReturn(List.of());

        List<FileRequestResponseDto> dtos = fileRequestService.getRequestsReceived(recipient);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        assertThat(pendingRequest.getStatus()).isEqualTo(FileRequestStatus.EXPIRED);
        verify(fileRequestRepository).save(pendingRequest);
    }

    // ── Phase 7D.1: Suspended Direct Recipient ───────────────────────────────

    @Test
    @DisplayName("initiateUpload: suspended direct user recipient throws AccessDeniedException")
    void initiateUpload_suspendedDirectRecipient_throwsAccessDenied() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        destinationFolder.setOwnerOrg(org);
        recipient.setOrganization(org);

        InitiateUploadRequest initReq = InitiateUploadRequest.builder()
                .fileName("statement.pdf")
                .contentType("application/pdf")
                .fileSize(1024L)
                .fileHash("hash123")
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("2", "ACME"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().status(MembershipStatus.SUSPENDED).build()));

        assertThatThrownBy(() -> fileRequestService.initiateUpload(50L, initReq, recipient))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Suspended users cannot upload");

        verifyNoInteractions(documentRepository);
        verifyNoInteractions(storageService);
    }

    @Test
    @DisplayName("confirmUpload: suspended direct user recipient throws AccessDeniedException")
    void confirmUpload_suspendedDirectRecipient_throwsAccessDenied() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        destinationFolder.setOwnerOrg(org);
        recipient.setOrganization(org);

        ConfirmUploadRequest confirmReq = ConfirmUploadRequest.builder()
                .documentId(200L)
                .clientHash("hash123")
                .build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("2", "ACME"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().status(MembershipStatus.SUSPENDED).build()));

        assertThatThrownBy(() -> fileRequestService.confirmUpload(50L, confirmReq, recipient))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Suspended users cannot upload");

        verifyNoInteractions(documentRepository);
    }

    // ── Phase 7D.1: Complete vs Cancel State Safety ──────────────────────────

    @Test
    @DisplayName("completeFileRequest: losing atomic transition race throws InvalidDocumentStateException and does not audit or notify")
    void completeFileRequest_concurrentRaceLost_throwsException() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(fileRequestRepository.markFulfilledIfPending(eq(50L), any())).thenReturn(0);

        assertThatThrownBy(() -> fileRequestService.completeFileRequest(50L, recipient))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("Only PENDING file requests can be completed");

        verify(auditLogService, never()).logDocumentActivity(eq(Action.FILE_REQUEST_FULFILLED), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("cancelFileRequest: losing atomic transition race throws InvalidDocumentStateException and does not audit or notify")
    void cancelFileRequest_concurrentRaceLost_throwsException() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(pendingRequest));
        when(fileRequestRepository.markCancelledIfPending(eq(50L), any())).thenReturn(0);

        assertThatThrownBy(() -> fileRequestService.cancelFileRequest(50L, requester))
                .isInstanceOf(InvalidDocumentStateException.class)
                .hasMessageContaining("Only PENDING file requests can be cancelled");

        verify(auditLogService, never()).logDocumentActivity(eq(Action.FILE_REQUEST_CANCELLED), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(notificationService);
    }
}
