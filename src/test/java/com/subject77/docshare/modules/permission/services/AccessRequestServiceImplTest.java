package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.permission.dto.*;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.mapper.AccessRequestMapper;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceStatus;
import com.subject77.docshare.modules.workspace.entities.WorkspaceManagerEntity;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;

@ExtendWith(MockitoExtension.class)
class AccessRequestServiceImplTest {

    @Mock
    private AccessRequestRepository accessRequestRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private WorkspacePolicyService workspacePolicyService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;

    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @Mock
    private com.subject77.docshare.modules.collaboration.repository.CollaborationRepository collaborationRepository;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private AccessRequestMapper accessRequestMapper = new AccessRequestMapper();

    @InjectMocks
    private AccessRequestServiceImpl accessRequestService;

    private UserEntity requester;
    private UserEntity owner;
    private DocumentEntity document;
    private FolderEntity folder;

    @BeforeEach
    void setUp() {
        requester = UserEntity.builder()
                .id("user-requester")
                .email("requester@example.com")
                .build();

        owner = UserEntity.builder()
                .id("user-owner")
                .email("owner@example.com")
                .build();

        document = DocumentEntity.builder()
                .id(100L)
                .fileName("SecretReport.docx")
                .fileHash("hash12345")
                .uploadedBy(owner)
                .build();

        folder = FolderEntity.builder()
                .id(200L)
                .name("Project Alpha")
                .createdBy(owner)
                .build();

        org.mockito.Mockito.lenient().when(userRepository.findByIdForUpdate(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> java.util.Optional.of(requester));
        org.mockito.Mockito.lenient().when(accessRequestRepository.findByIdForUpdate(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(inv -> accessRequestRepository.findById(inv.getArgument(0)));
    }

    @Test
    @DisplayName("Should successfully create access request for document in PENDING status")
    void createAccessRequest_Document_Success() {
        AccessRequestCreateDto dto = AccessRequestCreateDto.builder()
                .documentId(100L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("Need for Q3 audit")
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(false);
        when(accessRequestRepository.existsByRequester_IdAndDocument_IdAndStatus("user-requester", 100L, AccessRequestStatus.PENDING))
                .thenReturn(false);
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(inv -> {
            AccessRequestEntity e = inv.getArgument(0);
            e.setId(1L);
            e.setCreatedAt(Instant.now());
            return e;
        });

        AccessRequestResponseDto response = accessRequestService.createAccessRequest(dto, requester);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.PENDING);
        assertThat(response.getRequestedPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
        assertThat(response.getRequesterUserEmail()).isEqualTo("requester@example.com");

        verify(accessRequestRepository).save(any(AccessRequestEntity.class));
        ArgumentCaptor<AuditPermissionChangeCommand> cmdCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(cmdCaptor.capture());
        AuditPermissionChangeCommand captured = cmdCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo("ACCESS_REQUEST_CREATED");
        assertThat(captured.getTargetType()).isEqualTo("DOCUMENT");
        assertThat(captured.getTargetId()).isEqualTo(100L);
        assertThat(captured.getTargetFileHash()).isEqualTo("hash12345");
        assertThat(captured.getSpaceType()).isEqualTo(SpaceType.PERSONAL);
        assertThat(captured.getActorUserId()).isEqualTo("user-requester");
        assertThat(captured.getGrantedToEmail()).isEqualTo("requester@example.com");
        assertThat(captured.getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
        assertThat(captured.getNewValue()).isEqualTo("VIEW");
        assertThat(captured.getReason()).isEqualTo("Need for Q3 audit");
        assertThat(captured.getDetails()).isEqualTo("Requested VIEW access for DOCUMENT ID 100");
        verify(notificationService).createNotification(
                eq(owner), eq(requester), eq(NotificationType.ACCESS_REQUEST_SUBMITTED),
                anyString(), anyString(), eq(NotificationSourceType.ACCESS_REQUEST), eq("1"),
                eq(NotificationCategory.ACCESS_REQUEST), eq(NotificationPriority.HIGH), anyString()
        );
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when duplicate pending request exists for document")
    void createAccessRequest_DuplicateDocument_ThrowsException() {
        AccessRequestCreateDto dto = AccessRequestCreateDto.builder()
                .documentId(100L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(false);
        when(accessRequestRepository.existsByRequester_IdAndDocument_IdAndStatus("user-requester", 100L, AccessRequestStatus.PENDING))
                .thenReturn(true);

        assertThatThrownBy(() -> accessRequestService.createAccessRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("An access request for this document is already pending");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when requester is already admin of the document")
    void createAccessRequest_RequesterIsAdmin_ThrowsException() {
        AccessRequestCreateDto dto = AccessRequestCreateDto.builder()
                .documentId(100L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(true);

        assertThatThrownBy(() -> accessRequestService.createAccessRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Requester is already the owner or administrator of this document");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when target document is missing")
    void createAccessRequest_MissingDocument_ThrowsException() {
        AccessRequestCreateDto dto = AccessRequestCreateDto.builder()
                .documentId(999L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .build();

        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessRequestService.createAccessRequest(dto, requester))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found with ID: 999");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when neither document nor folder target is specified")
    void createAccessRequest_NoTarget_ThrowsException() {
        AccessRequestCreateDto dto = AccessRequestCreateDto.builder()
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> accessRequestService.createAccessRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Either a document or folder target must be specified");
    }

    @Test
    @DisplayName("Should successfully approve access request and delegate permission creation to PermissionService")
    void approveAccessRequest_Success() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .documentFileHash("hash12345")
                .requestedPermissionLevel(PermissionLevel.DOWNLOAD)
                .status(AccessRequestStatus.PENDING)
                .build();

        PermissionResponseDto grantedDto = PermissionResponseDto.builder()
                .id(55L)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("requester@example.com")
                .build();

        PermissionEntity grantedPermission = PermissionEntity.builder().id(55L).build();

        AccessRequestActionDto actionDto = AccessRequestActionDto.builder()
                .reviewerNote("Approved for audit compliance")
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(evaluatorService.canAdministerDocumentPermissions(owner, document)).thenReturn(true);
        when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("user-owner"))).thenReturn(grantedDto);
        when(permissionRepository.findById(55L)).thenReturn(Optional.of(grantedPermission));
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto response = accessRequestService.approveAccessRequest(10L, actionDto, owner);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(response.getProcessedByUserEmail()).isEqualTo("owner@example.com");
        assertThat(response.getReviewerNote()).isEqualTo("Approved for audit compliance");
        assertThat(response.getGrantedPermissionId()).isEqualTo(55L);

        verify(permissionService).grantPermission(any(PermissionRequestDto.class), eq("user-owner"));
        ArgumentCaptor<AuditPermissionChangeCommand> cmdCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(cmdCaptor.capture());
        AuditPermissionChangeCommand captured = cmdCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo("ACCESS_REQUEST_APPROVED");
        assertThat(captured.getTargetType()).isEqualTo("DOCUMENT");
        assertThat(captured.getTargetId()).isEqualTo(100L);
        assertThat(captured.getTargetFileHash()).isEqualTo("hash12345");
        assertThat(captured.getSpaceType()).isEqualTo(SpaceType.PERSONAL);
        assertThat(captured.getActorUserId()).isEqualTo("user-owner");
        assertThat(captured.getGrantedToEmail()).isEqualTo("requester@example.com");
        assertThat(captured.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        assertThat(captured.getPreviousValue()).isEqualTo("PENDING");
        assertThat(captured.getNewValue()).isEqualTo("APPROVED");
        assertThat(captured.getDetails()).isEqualTo("Approved for audit compliance");
        verify(notificationService).createNotification(
                eq(requester), eq(owner), eq(NotificationType.ACCESS_REQUEST_APPROVED),
                anyString(), anyString(), eq(NotificationSourceType.ACCESS_REQUEST), eq("10"),
                eq(NotificationCategory.ACCESS_REQUEST), eq(NotificationPriority.MEDIUM), anyString()
        );
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when requester attempts to approve their own request")
    void approveAccessRequest_SelfApproval_ThrowsAccessDenied() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(10L, null, requester))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Requesters cannot approve their own access requests");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when reviewer lacks administration authority")
    void approveAccessRequest_UnauthorizedReviewer_ThrowsAccessDenied() {
        UserEntity rando = UserEntity.builder().id("user-rando").email("rando@example.com").build();
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(evaluatorService.canAdministerDocumentPermissions(rando, document)).thenReturn(false);

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(10L, null, rando))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to review access requests for this resource");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when approving a request that is not PENDING")
    void approveAccessRequest_NotPending_ThrowsIllegalState() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.APPROVED)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(10L, null, owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access request is not in PENDING status");
    }

    @Test
    @DisplayName("Should successfully deny access request and notify requester")
    void denyAccessRequest_Success() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .requestedPermissionLevel(PermissionLevel.EDIT)
                .status(AccessRequestStatus.PENDING)
                .build();

        AccessRequestActionDto actionDto = AccessRequestActionDto.builder()
                .reviewerNote("Denied due to insufficient justification")
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(evaluatorService.canAdministerDocumentPermissions(owner, document)).thenReturn(true);
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto response = accessRequestService.denyAccessRequest(10L, actionDto, owner);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.DENIED);
        assertThat(response.getProcessedByUserEmail()).isEqualTo("owner@example.com");
        assertThat(response.getReviewerNote()).isEqualTo("Denied due to insufficient justification");

        ArgumentCaptor<AuditPermissionChangeCommand> cmdCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(cmdCaptor.capture());
        AuditPermissionChangeCommand captured = cmdCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo("ACCESS_REQUEST_DENIED");
        assertThat(captured.getTargetType()).isEqualTo("DOCUMENT");
        assertThat(captured.getTargetId()).isEqualTo(100L);
        assertThat(captured.getSpaceType()).isEqualTo(SpaceType.PERSONAL);
        assertThat(captured.getActorUserId()).isEqualTo("user-owner");
        assertThat(captured.getGrantedToEmail()).isEqualTo("requester@example.com");
        assertThat(captured.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        assertThat(captured.getPreviousValue()).isEqualTo("PENDING");
        assertThat(captured.getNewValue()).isEqualTo("DENIED");
        assertThat(captured.getDetails()).isEqualTo("Denied due to insufficient justification");

        verify(notificationService).createNotification(
                eq(requester), eq(owner), eq(NotificationType.ACCESS_REQUEST_DENIED),
                anyString(), anyString(), eq(NotificationSourceType.ACCESS_REQUEST), eq("10"),
                eq(NotificationCategory.ACCESS_REQUEST), eq(NotificationPriority.MEDIUM), anyString()
        );
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when requester attempts to deny their own request")
    void denyAccessRequest_SelfDenial_ThrowsAccessDenied() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> accessRequestService.denyAccessRequest(10L, null, requester))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Requesters cannot deny their own access requests");
    }

    @Test
    @DisplayName("Should successfully cancel pending access request by requester")
    void cancelAccessRequest_Success() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto response = accessRequestService.cancelAccessRequest(10L, requester);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.CANCELLED);

        ArgumentCaptor<AuditPermissionChangeCommand> cmdCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(cmdCaptor.capture());
        AuditPermissionChangeCommand captured = cmdCaptor.getValue();
        assertThat(captured.getAction()).isEqualTo("ACCESS_REQUEST_CANCELLED");
        assertThat(captured.getTargetType()).isEqualTo("DOCUMENT");
        assertThat(captured.getTargetId()).isEqualTo(100L);
        assertThat(captured.getSpaceType()).isEqualTo(SpaceType.PERSONAL);
        assertThat(captured.getActorUserId()).isEqualTo("user-requester");
        assertThat(captured.getGrantedToEmail()).isEqualTo("requester@example.com");
        assertThat(captured.getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
        assertThat(captured.getPreviousValue()).isEqualTo("PENDING");
        assertThat(captured.getNewValue()).isEqualTo("CANCELLED");
        assertThat(captured.getDetails()).isEqualTo("Access request cancelled by requester");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when non-requester attempts to cancel request")
    void cancelAccessRequest_NonRequester_ThrowsAccessDenied() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> accessRequestService.cancelAccessRequest(10L, owner))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Only the requester can cancel this access request");
    }

    @Test
    @DisplayName("Should allow viewing access request by requester")
    void getAccessRequestById_Requester_Success() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));

        AccessRequestResponseDto response = accessRequestService.getAccessRequestById(10L, requester);
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unrelated user attempts to view access request")
    void getAccessRequestById_UnrelatedUser_ThrowsAccessDenied() {
        UserEntity rando = UserEntity.builder().id("user-rando").email("rando@example.com").build();
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(evaluatorService.canAdministerDocumentPermissions(rando, document)).thenReturn(false);

        assertThatThrownBy(() -> accessRequestService.getAccessRequestById(10L, rando))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You are not authorized to view this access request");
    }

    @Test
    @DisplayName("checkEligibility should return RESOURCE_NOT_FOUND_OR_RESTRICTED when target does not exist")
    void checkEligibility_TargetNotFound_ReturnsRestricted() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(999L, null, null, requester);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("RESOURCE_NOT_FOUND_OR_RESTRICTED");
    }

    @Test
    @DisplayName("checkEligibility should return ALREADY_HAS_ACCESS when user is owner/admin")
    void checkEligibility_AlreadyAdmin_ReturnsAlreadyHasAccess() {
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(true);

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(100L, null, null, requester);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("ALREADY_HAS_ACCESS");
        assertThat(result.getCurrentPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
    }

    @Test
    @DisplayName("checkEligibility should return PENDING_REQUEST_EXISTS when pending request exists")
    void checkEligibility_PendingExists_ReturnsPendingExists() {
        AccessRequestEntity pendingRequest = AccessRequestEntity.builder().id(77L).build();
        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(false);
        when(evaluatorService.canViewDocument(requester, document)).thenReturn(false);
        when(accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus("user-requester", 100L, AccessRequestStatus.PENDING))
                .thenReturn(Optional.of(pendingRequest));

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(100L, null, null, requester);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("PENDING_REQUEST_EXISTS");
        assertThat(result.getExistingRequestId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("checkEligibility should return REQUESTS_DISABLED_BY_POLICY when policy disables requests")
    void checkEligibility_DisabledByPolicy_ReturnsDisabled() {
        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .allowAccessRequests(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(false);
        when(evaluatorService.canViewDocument(requester, document)).thenReturn(false);
        when(accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus("user-requester", 100L, AccessRequestStatus.PENDING))
                .thenReturn(Optional.empty());
        when(workspacePolicyService.getEffectivePolicyForUser(owner)).thenReturn(policy);

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(100L, null, null, requester);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("REQUESTS_DISABLED_BY_POLICY");
    }

    @Test
    @DisplayName("createAccessRequest should throw IllegalArgumentException when reason is required by policy but blank")
    void createAccessRequest_ReasonRequiredByPolicy_ThrowsException() {
        AccessRequestCreateDto dto = AccessRequestCreateDto.builder()
                .documentId(100L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("   ")
                .build();

        WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                .allowAccessRequests(true)
                .requireRequestReason(true)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(requester, document)).thenReturn(false);
        when(evaluatorService.canViewDocument(requester, document)).thenReturn(false);
        when(accessRequestRepository.existsByRequester_IdAndDocument_IdAndStatus("user-requester", 100L, AccessRequestStatus.PENDING))
                .thenReturn(false);
        when(workspacePolicyService.getEffectivePolicyForUser(owner)).thenReturn(policy);

        assertThatThrownBy(() -> accessRequestService.createAccessRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Request reason is required by workspace policy");
    }

    @Test
    @DisplayName("approveAccessRequest should throw AccessDeniedException when workspace policy disabled access requests after creation")
    void approveAccessRequest_PolicyChangedToDisabled_ThrowsAccessDenied() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(10L)
                .requester(requester)
                .document(document)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        WorkspacePolicyEntity disabledPolicy = WorkspacePolicyEntity.builder()
                .allowAccessRequests(false)
                .build();

        when(accessRequestRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(evaluatorService.canAdministerDocumentPermissions(owner, document)).thenReturn(true);
        when(workspacePolicyService.getEffectivePolicyForUser(owner)).thenReturn(disabledPolicy);

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(10L, null, owner))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Access requests are currently disabled by workspace policy");
    }

    // =========================================================================
    // Collaboration Manager pending-review / approval / expiry tests (Scenarios 6-10)
    // =========================================================================

    @Test
    @DisplayName("Scenario 6: Collaboration Manager sees valid pending request in pending-for-me")
    void getPendingReviews_CollaborationManager_SeesManagedCollabRequest() {
        FolderEntity collabRoot = FolderEntity.builder().id(5000L).createdBy(owner).build();
        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(30L)
                .status(CollaborationStatus.ACTIVE)
                .rootFolder(collabRoot)
                .build();
        CollaborationParticipantEntity managerEntry = CollaborationParticipantEntity.builder()
                .id(50L)
                .collaboration(activeCollab)
                .user(owner)
                .collaborationRole(CollaborationRole.MANAGER)
                .build();

        AccessRequestEntity pendingRequest = AccessRequestEntity.builder()
                .id(99L)
                .requester(requester)
                .folder(collabRoot)
                .status(AccessRequestStatus.PENDING)
                .build();

        // Org admin path: no admin org codes (owner has no role/org, and no memberships)
        when(userOrganizationRepository.findByUser_Id(owner.getId())).thenReturn(java.util.List.of());
        when(accessRequestRepository.findPendingReviewsForUserDirect(eq(owner.getId()), eq(AccessRequestStatus.PENDING), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        // Workspace manager path: no workspace manager records
        when(workspaceManagerRepository.findByUser_Id(owner.getId())).thenReturn(java.util.List.of());
        // Collaboration manager path: user is MANAGER of activeCollab
        when(collaborationParticipantRepository.findByUser_IdAndCollaborationRole(
                owner.getId(), CollaborationRole.MANAGER))
                .thenReturn(java.util.List.of(managerEntry));
        when(accessRequestRepository.findPendingReviewsForCollaborationManager(
                eq(AccessRequestStatus.PENDING), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(pendingRequest)));
        // Final authority check: request targets a folder — mock canAdministerFolderPermissions
        when(evaluatorService.canAdministerFolderPermissions(owner, collabRoot)).thenReturn(true);

        org.springframework.data.domain.Page<AccessRequestResponseDto> result =
                accessRequestService.getPendingReviews(owner, org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("Scenario 7: Collaboration Manager does NOT see unrelated collaboration's pending request")
    void getPendingReviews_CollaborationManager_DoesNotSeeUnrelatedRequest() {
        // Manager has no managed collaborations at all
        when(userOrganizationRepository.findByUser_Id(owner.getId())).thenReturn(java.util.List.of());
        when(accessRequestRepository.findPendingReviewsForUserDirect(eq(owner.getId()), eq(AccessRequestStatus.PENDING), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(workspaceManagerRepository.findByUser_Id(owner.getId())).thenReturn(java.util.List.of());
        when(collaborationParticipantRepository.findByUser_IdAndCollaborationRole(
                owner.getId(), CollaborationRole.MANAGER))
                .thenReturn(java.util.List.of()); // no managed collaborations

        org.springframework.data.domain.Page<AccessRequestResponseDto> result =
                accessRequestService.getPendingReviews(owner, org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 8: Manager role removed while request is pending — approval denied")
    void approveAccessRequest_CollaborationManagerRoleRemoved_ThrowsAccessDenied() {
        // The collaboration manager had their role removed; evaluatorService now returns false
        UserEntity formerManager = UserEntity.builder().id("ex-mgr").email("exmgr@example.com").build();
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(77L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(77L)).thenReturn(Optional.of(entity));
        when(evaluatorService.canAdministerDocumentPermissions(formerManager, document)).thenReturn(false);

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(77L, null, formerManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to review access requests for this resource");
    }

    @Test
    @DisplayName("Scenario 9: Collaboration revoked while request is pending — approval denied")
    void approveAccessRequest_CollaborationRevoked_ThrowsAccessDenied() {
        // After revocation evaluatorService sees no active collaboration → returns false
        UserEntity collabManager = UserEntity.builder().id("collab-mgr").email("mgr@example.com").build();
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(88L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(88L)).thenReturn(Optional.of(entity));
        // Post-revocation the evaluator finds no active collaboration granting authority
        when(evaluatorService.canAdministerDocumentPermissions(collabManager, document)).thenReturn(false);

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(88L, null, collabManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to review access requests for this resource");
    }

    @Test
    @DisplayName("Scenario 10: Team Manager and Workspace Manager behavior is unchanged alongside Collaboration Manager")
    void getPendingReviews_TeamAndWorkspaceManagerUnchanged_NoDuplicates() {
        // Workspace manager with one managed workspace
        FolderEntity wsRoot = FolderEntity.builder().id(6000L).createdBy(owner).build();
        ProjectWorkspaceEntity workspace = ProjectWorkspaceEntity.builder()
                .id(1L).status(ProjectWorkspaceStatus.ACTIVE).rootFolder(wsRoot)
                .build();
        WorkspaceManagerEntity wmEntry = WorkspaceManagerEntity.builder()
                .id(1L).user(owner).workspace(workspace)
                .build();

        AccessRequestEntity wsRequest = AccessRequestEntity.builder()
                .id(200L).requester(requester).folder(wsRoot).status(AccessRequestStatus.PENDING)
                .build();

        when(userOrganizationRepository.findByUser_Id(owner.getId())).thenReturn(java.util.List.of());
        when(accessRequestRepository.findPendingReviewsForUserDirect(eq(owner.getId()), eq(AccessRequestStatus.PENDING), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());
        when(workspaceManagerRepository.findByUser_Id(owner.getId())).thenReturn(java.util.List.of(wmEntry));
        when(accessRequestRepository.findPendingReviewsForWorkspaceManager(
                eq(AccessRequestStatus.PENDING), any(), any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(wsRequest)));
        // No collaboration manager records
        when(collaborationParticipantRepository.findByUser_IdAndCollaborationRole(
                owner.getId(), CollaborationRole.MANAGER))
                .thenReturn(java.util.List.of());
        // Final authority check: request targets a folder — mock canAdministerFolderPermissions
        when(evaluatorService.canAdministerFolderPermissions(owner, wsRoot)).thenReturn(true);

        org.springframework.data.domain.Page<AccessRequestResponseDto> result =
                accessRequestService.getPendingReviews(owner, org.springframework.data.domain.Pageable.unpaged());

        // Exactly one result — no duplication
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("approveAccessRequest: Workspace Manager can approve within assigned workspace")
    void approveAccessRequest_WorkspaceManager_CanApproveWithinAssignedWorkspace() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        FolderEntity wsFolder = FolderEntity.builder().id(500L).ownerOrg(org).name("Project Root").build();
        ProjectWorkspaceEntity ws = ProjectWorkspaceEntity.builder().id(20L).organization(org).rootFolder(wsFolder).status(ProjectWorkspaceStatus.ACTIVE).build();

        UserEntity wsManager = UserEntity.builder().id("wm-1").email("wm@acme.com").role(Role.ROLE_USER).build();
        WorkspaceManagerEntity wmEntity = WorkspaceManagerEntity.builder().id(1L).user(wsManager).workspace(ws).build();

        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(300L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(300L)).thenReturn(Optional.of(entity));
        when(projectWorkspaceRepository.findByOrganization_OrgCode("ACME"))
                .thenReturn(List.of(ws));
        when(workspaceManagerRepository.findByWorkspace_Id(20L)).thenReturn(List.of(wmEntity));

        PermissionResponseDto grantedDto = PermissionResponseDto.builder().id(99L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("wm-1"))).thenReturn(grantedDto);
        when(permissionRepository.findById(99L)).thenReturn(Optional.of(PermissionEntity.builder().id(99L).build()));
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto response = accessRequestService.approveAccessRequest(300L, null, wsManager);

        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(response.getProcessedByUserEmail()).isEqualTo("wm@acme.com");
    }

    @Test
    @DisplayName("approveAccessRequest: ORG_ADMIN can approve when no Workspace Manager exists")
    void approveAccessRequest_OrgAdmin_CanApproveWhenNoWorkspaceManagerExists() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        FolderEntity wsFolder = FolderEntity.builder().id(500L).ownerOrg(org).name("Project Root").build();
        ProjectWorkspaceEntity ws = ProjectWorkspaceEntity.builder().id(20L).organization(org).rootFolder(wsFolder).status(ProjectWorkspaceStatus.ACTIVE).build();

        UserEntity orgAdmin = UserEntity.builder().id("admin-1").email("admin@acme.com").role(Role.ROLE_ORG_ADMIN).build();

        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(301L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(301L)).thenReturn(Optional.of(entity));
        lenient().when(projectWorkspaceRepository.findByOrganization_OrgCode("ACME"))
                .thenReturn(List.of(ws));
        lenient().when(workspaceManagerRepository.findByWorkspace_Id(20L)).thenReturn(List.of()); // No WM assigned
        when(evaluatorService.isUserAdminOfOrg(orgAdmin, "ACME")).thenReturn(true);


        PermissionResponseDto grantedDto = PermissionResponseDto.builder().id(99L).permissionLevel(PermissionLevel.VIEW).build();
        when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("admin-1"))).thenReturn(grantedDto);
        when(permissionRepository.findById(99L)).thenReturn(Optional.of(PermissionEntity.builder().id(99L).build()));
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AccessRequestResponseDto response = accessRequestService.approveAccessRequest(301L, null, orgAdmin);

        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(response.getProcessedByUserEmail()).isEqualTo("admin@acme.com");
    }

    @Test
    @DisplayName("approveAccessRequest: Ordinary ORG_MANAGER cannot approve unless appointed as Workspace Manager")
    void approveAccessRequest_OrdinaryOrgManager_CannotApproveUnlessWorkspaceManager() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        FolderEntity wsFolder = FolderEntity.builder().id(500L).ownerOrg(org).name("Project Root").build();
        ProjectWorkspaceEntity ws = ProjectWorkspaceEntity.builder().id(20L).organization(org).rootFolder(wsFolder).status(ProjectWorkspaceStatus.ACTIVE).build();

        UserEntity orgManager = UserEntity.builder().id("mgr-1").email("mgr@acme.com").role(Role.ROLE_ORG_MANAGER).build();

        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(302L)
                .requester(requester)
                .folder(wsFolder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(302L)).thenReturn(Optional.of(entity));
        when(projectWorkspaceRepository.findByOrganization_OrgCode("ACME"))
                .thenReturn(List.of(ws));
        when(workspaceManagerRepository.findByWorkspace_Id(20L)).thenReturn(List.of()); // Not appointed as WM
        when(evaluatorService.isUserAdminOfOrg(orgManager, "ACME")).thenReturn(false);

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(302L, null, orgManager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to review access requests for this resource");
    }

    @Test
    @DisplayName("approveAccessRequest: Cross-workspace approval denied")
    void approveAccessRequest_CrossWorkspaceApproval_Denied() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        FolderEntity ws1Folder = FolderEntity.builder().id(501L).ownerOrg(org).name("WS1 Root").build();
        FolderEntity ws2Folder = FolderEntity.builder().id(502L).ownerOrg(org).name("WS2 Root").build();

        ProjectWorkspaceEntity ws1 = ProjectWorkspaceEntity.builder().id(21L).organization(org).rootFolder(ws1Folder).status(ProjectWorkspaceStatus.ACTIVE).build();
        ProjectWorkspaceEntity ws2 = ProjectWorkspaceEntity.builder().id(22L).organization(org).rootFolder(ws2Folder).status(ProjectWorkspaceStatus.ACTIVE).build();

        // User is manager of WS1 only
        UserEntity ws1Manager = UserEntity.builder().id("wm-ws1").email("wm1@acme.com").role(Role.ROLE_USER).build();
        WorkspaceManagerEntity wm2Entity = WorkspaceManagerEntity.builder()
                .id(2L)
                .user(UserEntity.builder().id("wm-ws2").email("wm2@acme.com").build())
                .workspace(ws2)
                .build();

        // Request is for resource in WS2
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(303L)
                .requester(requester)
                .folder(ws2Folder)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findById(303L)).thenReturn(Optional.of(entity));
        when(projectWorkspaceRepository.findByOrganization_OrgCode("ACME"))
                .thenReturn(List.of(ws1, ws2));
        when(workspaceManagerRepository.findByWorkspace_Id(22L)).thenReturn(List.of(wm2Entity));

        // wm-ws1 tries to approve request in WS2 -> denied
        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(303L, null, ws1Manager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to review access requests for this resource");
    }

    @Test
    @DisplayName("External requester approval creates USER_PERSONAL permission")
    void approveAccessRequest_ExternalRequester_CreatesUserPersonalPermission() {
        OrganizationEntity testOrg = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        DocumentEntity orgDoc = DocumentEntity.builder()
                .id(105L)
                .fileName("OrgDoc.pdf")
                .ownerOrg(testOrg)
                .build();

        UserEntity externalRequester = UserEntity.builder()
                .id("ext-user-1")
                .email("external@other.org")
                .build();

        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(401L)
                .requester(externalRequester)
                .document(orgDoc)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(entity));
        when(evaluatorService.isUserAdminOfOrg(owner, "ACME")).thenReturn(true);
        // External user has no active membership in ACME
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("ext-user-1", "ACME", com.subject77.docshare.modules.organization.entities.MembershipStatus.ACTIVE))
                .thenReturn(false);

        PermissionResponseDto grantedDto = PermissionResponseDto.builder().id(99L).build();
        ArgumentCaptor<PermissionRequestDto> captor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        when(permissionService.grantPermission(captor.capture(), eq("user-owner"))).thenReturn(grantedDto);
        when(permissionRepository.findById(99L)).thenReturn(Optional.of(PermissionEntity.builder().id(99L).build()));
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(i -> i.getArgument(0));

        accessRequestService.approveAccessRequest(401L, null, owner);

        PermissionRequestDto capturedReq = captor.getValue();
        assertThat(capturedReq.getRecipientType()).isEqualTo(RecipientType.USER_PERSONAL);
        assertThat(capturedReq.getTargetOrgCode()).isNull();
        assertThat(capturedReq.getTargetUserEmail()).isEqualTo("external@other.org");
    }

    @Test
    @DisplayName("Suspended organization member cannot create access request")
    void createAccessRequest_SuspendedOrgMember_ThrowsAccessDenied() {
        OrganizationEntity testOrg = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        DocumentEntity orgDoc = DocumentEntity.builder()
                .id(106L)
                .fileName("ProtectedPlan.pdf")
                .ownerOrg(testOrg)
                .build();

        when(documentRepository.findById(106L)).thenReturn(Optional.of(orgDoc));
        when(evaluatorService.canAdministerDocumentPermissions(requester, orgDoc)).thenReturn(false);
        when(evaluatorService.canViewDocument(requester, orgDoc)).thenReturn(false);
        when(accessRequestRepository.existsByRequester_IdAndDocument_IdAndStatus("user-requester", 106L, AccessRequestStatus.PENDING))
                .thenReturn(false);

        UserOrganizationEntity suspendedMembership = UserOrganizationEntity.builder()
                .user(requester)
                .organization(testOrg)
                .status(com.subject77.docshare.modules.organization.entities.MembershipStatus.SUSPENDED)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-requester", "ACME"))
                .thenReturn(Optional.of(suspendedMembership));

        AccessRequestCreateDto createDto = AccessRequestCreateDto.builder()
                .documentId(106L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> accessRequestService.createAccessRequest(createDto, requester))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Suspended organization members cannot request access to organization resources");
    }

    @Test
    @DisplayName("Approval revalidation rejects requester suspended or removed after request creation")
    void approveAccessRequest_RequesterSuspendedAfterCreation_ThrowsAccessDenied() {
        OrganizationEntity testOrg = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        DocumentEntity orgDoc = DocumentEntity.builder()
                .id(107L)
                .fileName("AuditFile.pdf")
                .ownerOrg(testOrg)
                .build();

        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(402L)
                .requester(requester)
                .document(orgDoc)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(402L)).thenReturn(Optional.of(entity));
        when(evaluatorService.isUserAdminOfOrg(owner, "ACME")).thenReturn(true);

        UserOrganizationEntity suspendedMembership = UserOrganizationEntity.builder()
                .user(requester)
                .organization(testOrg)
                .status(com.subject77.docshare.modules.organization.entities.MembershipStatus.SUSPENDED)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("user-requester", "ACME"))
                .thenReturn(Optional.of(suspendedMembership));

        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(402L, null, owner))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Requester organization membership is not active");

        verify(permissionService, never()).grantPermission(any(), any());
    }

    @Test
    @DisplayName("Cancellation sends ACCESS_REQUEST_CANCELLED notification to deciders")
    void cancelAccessRequest_DispatchesCancellationNotification() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(403L)
                .requester(requester)
                .document(document) // document owned by owner
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .status(AccessRequestStatus.PENDING)
                .build();

        when(accessRequestRepository.findByIdForUpdate(403L)).thenReturn(Optional.of(entity));
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(i -> i.getArgument(0));

        accessRequestService.cancelAccessRequest(403L, requester);

        verify(notificationService).createNotification(
                eq(owner),
                eq(requester),
                eq(NotificationType.ACCESS_REQUEST_CANCELLED),
                eq("Access Request Cancelled"),
                anyString(),
                eq(NotificationSourceType.ACCESS_REQUEST),
                eq("403"),
                eq(NotificationCategory.ACCESS_REQUEST),
                eq(NotificationPriority.MEDIUM),
                eq("ACCESS_REQUEST_CANCELLED:403:user-owner")
        );
    }

    @Test
    @DisplayName("Decision operations use locked request path findByIdForUpdate")
    void decisionOperations_UseLockedRequestPath() {
        AccessRequestEntity entity = AccessRequestEntity.builder()
                .id(404L)
                .requester(requester)
                .document(document)
                .status(AccessRequestStatus.PENDING)
                .build();

        doReturn(Optional.of(entity)).when(accessRequestRepository).findByIdForUpdate(404L);
        when(evaluatorService.canAdministerDocumentPermissions(owner, document)).thenReturn(true);
        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenAnswer(i -> i.getArgument(0));

        accessRequestService.denyAccessRequest(404L, null, owner);

        verify(accessRequestRepository).findByIdForUpdate(404L);
    }
}

