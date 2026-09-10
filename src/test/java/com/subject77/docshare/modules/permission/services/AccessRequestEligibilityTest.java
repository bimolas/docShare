package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestEligibilityDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestEntity;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.mapper.AccessRequestMapper;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessRequestEligibilityTest {

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
    private AccessRequestMapper accessRequestMapper;
    @Mock
    private WorkspacePolicyService workspacePolicyService;
    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;
    @Mock
    private CollaborationRepository collaborationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;
    @Mock
    private WorkspaceManagerRepository workspaceManagerRepository;

    @InjectMocks
    private AccessRequestServiceImpl accessRequestService;

    private UserEntity ownerUser;
    private UserEntity requesterUser;
    private OrganizationEntity requesterOrg;
    private DocumentEntity personalDocument;
    private WorkspacePolicyEntity personalPolicy;

    @BeforeEach
    void setUp() {
        ownerUser = UserEntity.builder()
                .id("owner-123")
                .email("owner@personal.com")
                .build();

        requesterOrg = OrganizationEntity.builder()
                .orgCode("ORG_BETA")
                .name("Beta Org")
                .build();

        requesterUser = UserEntity.builder()
                .id("requester-456")
                .email("requester@beta.com")
                .organization(requesterOrg)
                .build();

        personalDocument = DocumentEntity.builder()
                .id(1001L)
                .fileName("secret-report.pdf")
                .fileHash("hash-abc-123")
                .uploadedBy(ownerUser)
                .ownerOrg(null)
                .build();

        personalPolicy = WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(ownerUser)
                .externalSharingMode(ExternalSharingMode.ON)
                .allowAccessRequests(true)
                .allowExternalAccessRequests(true)
                .requireRequestReason(false)
                .build();
    }

    @Test
    @DisplayName("checkEligibility: personal resource with org-affiliated requester should be ELIGIBLE with targetOrgCode=null")
    void checkEligibility_PersonalResource_RequesterHasOrg_Eligible() {
        when(documentRepository.findById(1001L)).thenReturn(Optional.of(personalDocument));
        when(evaluatorService.canAdministerDocumentPermissions(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canEditDocument(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canDownloadDocument(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canViewDocument(requesterUser, personalDocument)).thenReturn(false);

        when(accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus(
                "requester-456", 1001L, AccessRequestStatus.PENDING)).thenReturn(Optional.empty());

        when(workspacePolicyService.getEffectivePolicyForUser(ownerUser)).thenReturn(personalPolicy);

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(1001L, null, null, requesterUser);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getReasonCode()).isEqualTo("ELIGIBLE");

        ArgumentCaptor<PermissionRequestDto> captor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        verify(externalSharingPolicyEvaluator).evaluateAndValidateSharingPolicy(
                isNull(), eq(personalDocument), captor.capture(), isNull());

        PermissionRequestDto capturedDto = captor.getValue();
        assertThat(capturedDto.getTargetUserEmail()).isEqualTo("requester@beta.com");
        assertThat(capturedDto.getTargetOrgCode()).isNull(); // Must NOT inject requesterOrg.getOrgCode()
        assertThat(capturedDto.getRecipientType()).isEqualTo(RecipientType.USER_PERSONAL);
    }

    @Test
    @DisplayName("createAccessRequest: personal resource with org-affiliated requester passes validation and saves PENDING request")
    void createAccessRequest_PersonalResource_RequesterHasOrg_Success() {
        when(userRepository.findByIdForUpdate("requester-456")).thenReturn(Optional.of(requesterUser));
        when(documentRepository.findById(1001L)).thenReturn(Optional.of(personalDocument));
        when(accessRequestRepository.existsByRequester_IdAndDocument_IdAndStatus(
                "requester-456", 1001L, AccessRequestStatus.PENDING)).thenReturn(false);
        when(workspacePolicyService.getEffectivePolicyForUser(ownerUser)).thenReturn(personalPolicy);

        AccessRequestEntity savedEntity = AccessRequestEntity.builder()
                .id(501L)
                .requester(requesterUser)
                .document(personalDocument)
                .status(AccessRequestStatus.PENDING)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .build();

        when(accessRequestRepository.save(any(AccessRequestEntity.class))).thenReturn(savedEntity);
        when(accessRequestMapper.toResponseDto(savedEntity)).thenReturn(
                AccessRequestResponseDto.builder()
                        .id(501L)
                        .status(AccessRequestStatus.PENDING)
                        .build());

        AccessRequestCreateDto createDto = AccessRequestCreateDto.builder()
                .documentId(1001L)
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("Need this for analysis")
                .build();

        AccessRequestResponseDto response = accessRequestService.createAccessRequest(createDto, requesterUser);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(AccessRequestStatus.PENDING);

        ArgumentCaptor<PermissionRequestDto> captor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        verify(externalSharingPolicyEvaluator).evaluateAndValidateSharingPolicy(
                isNull(), eq(personalDocument), captor.capture(), isNull());

        PermissionRequestDto capturedDto = captor.getValue();
        assertThat(capturedDto.getTargetUserEmail()).isEqualTo("requester@beta.com");
        assertThat(capturedDto.getTargetOrgCode()).isNull();
        assertThat(capturedDto.getRecipientType()).isEqualTo(RecipientType.USER_PERSONAL);
    }

    @Test
    @DisplayName("checkEligibility: returns EXTERNAL_REQUESTS_DISABLED when policy disables external requests")
    void checkEligibility_PersonalResource_ExternalDisabled() {
        personalPolicy.setAllowExternalAccessRequests(false);

        when(documentRepository.findById(1001L)).thenReturn(Optional.of(personalDocument));
        when(evaluatorService.canAdministerDocumentPermissions(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canEditDocument(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canDownloadDocument(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canViewDocument(requesterUser, personalDocument)).thenReturn(false);

        when(accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus(
                "requester-456", 1001L, AccessRequestStatus.PENDING)).thenReturn(Optional.empty());

        when(workspacePolicyService.getEffectivePolicyForUser(ownerUser)).thenReturn(personalPolicy);

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(1001L, null, null, requesterUser);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("EXTERNAL_REQUESTS_DISABLED");
    }

    @Test
    @DisplayName("checkEligibility: returns REQUESTS_DISABLED_BY_POLICY when policy disables all access requests")
    void checkEligibility_PersonalResource_RequestsDisabled() {
        personalPolicy.setAllowAccessRequests(false);

        when(documentRepository.findById(1001L)).thenReturn(Optional.of(personalDocument));
        when(evaluatorService.canAdministerDocumentPermissions(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canEditDocument(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canDownloadDocument(requesterUser, personalDocument)).thenReturn(false);
        when(evaluatorService.canViewDocument(requesterUser, personalDocument)).thenReturn(false);

        when(accessRequestRepository.findFirstByRequester_IdAndDocument_IdAndStatus(
                "requester-456", 1001L, AccessRequestStatus.PENDING)).thenReturn(Optional.empty());

        when(workspacePolicyService.getEffectivePolicyForUser(ownerUser)).thenReturn(personalPolicy);

        AccessRequestEligibilityDto result = accessRequestService.checkEligibility(1001L, null, null, requesterUser);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getReasonCode()).isEqualTo("REQUESTS_DISABLED_BY_POLICY");
    }
}
