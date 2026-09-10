package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
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
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.GroupStatus;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Focused tests for TEAM/GROUP recipient support in File Requests (STEP 13F-1).
 *
 * <p>Dynamic membership semantics: when a new member joins the group after the
 * request was created, they are eligible to upload while the request is PENDING
 * because eligibility is resolved from current {@code UserGroupEntity} records
 * at upload time.</p>
 */
@ExtendWith(MockitoExtension.class)
class FileRequestServiceImplTeamTest {

    @Mock private FileRequestRepository fileRequestRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserOrganizationRepository userOrganizationRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserGroupRepository userGroupRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentVersionRepository documentVersionRepository;
    @Mock private StorageService storageService;
    @Mock private PermissionEvaluatorService permissionEvaluatorService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private PermissionRepository permissionRepository;

    @Spy
    private FileRequestMapper fileRequestMapper = new FileRequestMapper();

    @InjectMocks
    private FileRequestServiceImpl service;

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private UserEntity requester;
    private UserEntity memberAlice;
    private UserEntity memberBob;
    private UserEntity nonMember;
    private OrganizationEntity org;
    private GroupEntity engineeringTeam;
    private FolderEntity destinationFolder;
    private FileRequestEntity teamPendingRequest;

    @BeforeEach
    void setUp() {
        org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();

        requester = UserEntity.builder().id("req-1").email("manager@acme.com")
                .firstName("Manager").organization(org).build();
        memberAlice = UserEntity.builder().id("alice-1").email("alice@acme.com")
                .firstName("Alice").organization(org).build();
        memberBob = UserEntity.builder().id("bob-1").email("bob@acme.com")
                .firstName("Bob").organization(org).build();
        nonMember = UserEntity.builder().id("ext-1").email("external@other.com")
                .firstName("External").build();

        engineeringTeam = GroupEntity.builder()
                .id(10L).name("Engineering").status(GroupStatus.ACTIVE).organization(org).build();

        destinationFolder = FolderEntity.builder().id(100L).name("Reports").ownerOrg(org).build();

        teamPendingRequest = FileRequestEntity.builder()
                .id(50L)
                .requester(requester)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroup(engineeringTeam)
                .destinationFolder(destinationFolder)
                .title("Weekly Reports")
                .status(FileRequestStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── 1. Create for active team ─────────────────────────────────────────────

    @Test
    @DisplayName("1. createFileRequest: active team in same org succeeds")
    void createTeamRequest_activeTeamSameOrg_success() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroupId(10L)
                .title("Weekly Reports")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(engineeringTeam));

        UserGroupEntity aliceMembership = UserGroupEntity.builder().user(memberAlice).group(engineeringTeam).build();
        when(userGroupRepository.findActiveOrgMembersByGroupId(10L, MembershipStatus.ACTIVE))
                .thenReturn(List.of(aliceMembership));

        when(fileRequestRepository.save(any(FileRequestEntity.class))).thenAnswer(inv -> {
            FileRequestEntity e = inv.getArgument(0);
            e.setId(50L);
            return e;
        });

        FileRequestResponseDto response = service.createFileRequest(dto, requester);

        assertThat(response).isNotNull();
        assertThat(response.getRecipientType()).isEqualTo(FileRequestRecipientType.TEAM);
        assertThat(response.getRecipientGroupId()).isEqualTo(10L);
        assertThat(response.getRecipientGroupName()).isEqualTo("Engineering");

        // Notification sent to each active member
        verify(notificationService).createNotification(
                eq(memberAlice), eq(requester), eq(NotificationType.FILE_REQUEST_CREATED),
                anyString(), anyString(), any(), eq("50"), any(), any(), anyString());
    }

    // ── 2. Inactive / archived team rejected ─────────────────────────────────

    @Test
    @DisplayName("2. createFileRequest: archived team is rejected")
    void createTeamRequest_archivedTeam_throwsException() {
        engineeringTeam.setStatus(GroupStatus.ARCHIVED);

        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroupId(10L)
                .title("Reports")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(engineeringTeam));

        assertThatThrownBy(() -> service.createFileRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inactive/archived");

        verify(fileRequestRepository, never()).save(any());
    }

    // ── 3. Unrelated org team rejected ───────────────────────────────────────

    @Test
    @DisplayName("3. createFileRequest: team from different org is rejected")
    void createTeamRequest_differentOrgTeam_throwsException() {
        OrganizationEntity otherOrg = OrganizationEntity.builder().orgCode("OTHER").name("Other Corp").build();
        GroupEntity otherTeam = GroupEntity.builder()
                .id(99L).name("Other Team").status(GroupStatus.ACTIVE).organization(otherOrg).build();

        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroupId(99L)
                .title("Reports")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(groupRepository.findById(99L)).thenReturn(Optional.of(otherTeam));

        assertThatThrownBy(() -> service.createFileRequest(dto, requester))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("organisation");
    }

    // ── 4. Active team member can upload ─────────────────────────────────────

    @Test
    @DisplayName("4. initiateUpload: active team member succeeds")
    void initiateUpload_activeTeamMember_success() {
        InitiateUploadRequest req = InitiateUploadRequest.builder()
                .fileName("report.pdf").contentType("application/pdf")
                .fileSize(2048L).fileHash("hash-abc").build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(teamPendingRequest));
        when(userGroupRepository.isActiveOrgMemberOfGroup("alice-1", 10L, MembershipStatus.ACTIVE)).thenReturn(true);
        when(documentRepository.findByFileHashAndUploadedBy_IdAndStatusAndFolder_Id(
                "hash-abc", "alice-1", DocumentStatus.PENDING_UPLOAD, 100L)).thenReturn(Optional.empty());
        when(storageService.getDefaultBucketName()).thenReturn("bucket");
        when(storageService.generatePresignedUploadUrl(anyString(), eq("application/pdf"), eq(15L)))
                .thenReturn("https://s3/presigned");
        when(documentRepository.save(any(DocumentEntity.class))).thenAnswer(i -> {
            DocumentEntity d = i.getArgument(0);
            d.setId(200L);
            return d;
        });

        InitiateUploadResponse response = service.initiateUpload(50L, req, memberAlice);

        assertThat(response.getDocumentId()).isEqualTo(200L);
        assertThat(response.getPresignedUploadUrl()).isEqualTo("https://s3/presigned");
    }

    // ── 5. Non-member cannot upload ───────────────────────────────────────────

    @Test
    @DisplayName("5. initiateUpload: non-member is rejected")
    void initiateUpload_nonMember_throwsAccessDenied() {
        InitiateUploadRequest req = InitiateUploadRequest.builder()
                .fileName("report.pdf").contentType("application/pdf")
                .fileSize(1024L).fileHash("hash-xyz").build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(teamPendingRequest));
        when(userGroupRepository.isActiveOrgMemberOfGroup("ext-1", 10L, MembershipStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.initiateUpload(50L, req, nonMember))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("active members of the recipient team");
    }

    // ── 6. Suspended org member cannot upload ────────────────────────────────

    @Test
    @DisplayName("6. initiateUpload: suspended org member is rejected (isActiveOrgMemberOfGroup returns false)")
    void initiateUpload_suspendedOrgMember_throwsAccessDenied() {
        // isActiveOrgMemberOfGroup will return false because suspended user has no ACTIVE org record
        InitiateUploadRequest req = InitiateUploadRequest.builder()
                .fileName("report.pdf").contentType("application/pdf")
                .fileSize(1024L).fileHash("hash-sus").build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(teamPendingRequest));
        when(userGroupRepository.isActiveOrgMemberOfGroup("bob-1", 10L, MembershipStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> service.initiateUpload(50L, req, memberBob))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 7. No PermissionEntity created for team member ────────────────────────

    @Test
    @DisplayName("7. confirmUpload: team member upload does NOT create any PermissionEntity")
    void confirmUpload_teamMember_noPermissionGranted() {
        FileRequestEntity fr = teamPendingRequest;

        DocumentEntity pendingDoc = DocumentEntity.builder()
                .id(200L).fileName("report.pdf").contentType("application/pdf")
                .fileSize(1024L).fileHash("hash-abc").objectKey("orgs/ACME/uuid-report.pdf")
                .s3Bucket("bucket").status(DocumentStatus.PENDING_UPLOAD)
                .uploadedBy(memberAlice).folder(destinationFolder).fileRequest(fr)
                .version(1).build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(fr));
        when(userGroupRepository.isActiveOrgMemberOfGroup("alice-1", 10L, MembershipStatus.ACTIVE)).thenReturn(true);
        when(documentRepository.findById(200L)).thenReturn(Optional.of(pendingDoc));
        when(storageService.doesObjectExist("orgs/ACME/uuid-report.pdf")).thenReturn(true);
        when(documentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        DocumentResponse response = service.confirmUpload(50L,
                ConfirmUploadRequest.builder().documentId(200L).clientHash("hash-abc").build(), memberAlice);

        assertThat(response.getStatus()).isEqualTo(DocumentStatus.ACTIVE);

        // Critical: no permission persistence must have occurred
        verifyNoInteractions(permissionRepository);
    }

    // ── 8. Team member cannot browse folder through file request ─────────────

    @Test
    @DisplayName("8. getFileRequestById: non-member cannot view team file request")
    void getFileRequestById_nonMember_throwsAccessDenied() {
        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(teamPendingRequest));
        when(userGroupRepository.existsByUser_IdAndGroup_Id("ext-1", 10L)).thenReturn(false);

        assertThatThrownBy(() -> service.getFileRequestById(50L, nonMember))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ── 9. Invalid field combination: TEAM with userId ────────────────────────

    @Test
    @DisplayName("9. createFileRequest: TEAM type with recipientUserId is rejected")
    void createTeamRequest_withUserIdField_throwsException() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroupId(10L)
                .recipientUserId("some-user")  // invalid combination
                .title("Reports")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);

        assertThatThrownBy(() -> service.createFileRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipientUserId and recipientEmail must not be set for TEAM requests");

        verify(fileRequestRepository, never()).save(any());
    }

    // ── 9b. Invalid: USER type with groupId ───────────────────────────────────

    @Test
    @DisplayName("9b. createFileRequest: USER type with recipientGroupId is rejected")
    void createUserRequest_withGroupIdField_throwsException() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.USER)
                .recipientUserId("alice-1")
                .recipientGroupId(10L)  // invalid combination
                .title("Reports")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);

        assertThatThrownBy(() -> service.createFileRequest(dto, requester))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipientGroupId must not be set for USER requests");

        verify(fileRequestRepository, never()).save(any());
    }

    // ── 11. Newly-added member is eligible (dynamic membership) ───────────────

    @Test
    @DisplayName("11. dynamic membership: newly added member can upload after joining the group")
    void initiateUpload_newlyJoinedMember_isEligible() {
        // Mike joined after the request was created — but eligibility is dynamic
        UserEntity mike = UserEntity.builder().id("mike-1").email("mike@acme.com")
                .firstName("Mike").organization(org).build();

        InitiateUploadRequest req = InitiateUploadRequest.builder()
                .fileName("mike-report.pdf").contentType("application/pdf")
                .fileSize(512L).fileHash("hash-mike").build();

        when(fileRequestRepository.findById(50L)).thenReturn(Optional.of(teamPendingRequest));
        // Mike is now an active org member of Engineering
        when(userGroupRepository.isActiveOrgMemberOfGroup("mike-1", 10L, MembershipStatus.ACTIVE)).thenReturn(true);
        when(documentRepository.findByFileHashAndUploadedBy_IdAndStatusAndFolder_Id(
                "hash-mike", "mike-1", DocumentStatus.PENDING_UPLOAD, 100L)).thenReturn(Optional.empty());
        when(storageService.getDefaultBucketName()).thenReturn("bucket");
        when(storageService.generatePresignedUploadUrl(anyString(), anyString(), eq(15L))).thenReturn("https://s3/url");
        when(documentRepository.save(any())).thenAnswer(i -> {
            DocumentEntity d = i.getArgument(0); d.setId(300L); return d;
        });

        InitiateUploadResponse response = service.initiateUpload(50L, req, mike);

        assertThat(response.getDocumentId()).isEqualTo(300L);
    }

    // ── 12. Existing individual recipient behavior unchanged ──────────────────

    @Test
    @DisplayName("12. createFileRequest: USER-type request still works correctly")
    void createUserRequest_individualRecipient_success() {
        UserEntity recipient = UserEntity.builder().id("rec-1").email("bob@acme.com")
                .firstName("Bob").lastName("Recipient").build();

        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.USER)
                .recipientUserId("rec-1")
                .title("Tax Docs")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(userRepository.findById("rec-1")).thenReturn(Optional.of(recipient));
        when(fileRequestRepository.save(any())).thenAnswer(inv -> {
            FileRequestEntity e = inv.getArgument(0); e.setId(51L); return e;
        });

        FileRequestResponseDto response = service.createFileRequest(dto, requester);

        assertThat(response.getRecipientType()).isEqualTo(FileRequestRecipientType.USER);
        assertThat(response.getRecipientId()).isEqualTo("rec-1");
        assertThat(response.getRecipientGroupId()).isNull();

        verify(groupRepository, never()).findById(any());
    }

    // ── 13. Group not found ───────────────────────────────────────────────────

    @Test
    @DisplayName("13. createFileRequest: group not found throws ResourceNotFoundException")
    void createTeamRequest_groupNotFound_throwsException() {
        FileRequestCreateDto dto = FileRequestCreateDto.builder()
                .destinationFolderId(100L)
                .recipientType(FileRequestRecipientType.TEAM)
                .recipientGroupId(999L)
                .title("Reports")
                .build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(destinationFolder));
        when(permissionEvaluatorService.canEditFolder(requester, destinationFolder)).thenReturn(true);
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createFileRequest(dto, requester))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Group not found");
    }
}
