package com.subject77.docshare.modules.collaboration.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.collaboration.dto.CollaborationResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CreateCollaborationRequestDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.dto.CollaborationGroupParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationGroupParticipantEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationGroupParticipantRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionService;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollaborationServiceImplTest {

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private CollaborationGroupParticipantRepository collaborationGroupParticipantRepository;

    @Mock
    private OrgTrustRelationshipService orgTrustRelationshipService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private PermissionService permissionService;

    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PermissionCacheService permissionCacheService;

    @InjectMocks
    private CollaborationServiceImpl collaborationService;

    private UserEntity adminUser;
    private UserEntity memberUser;
    private OrganizationEntity ownerOrg;
    private OrganizationEntity partnerOrg;
    private FolderEntity rootFolder;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();

        adminUser = UserEntity.builder()
                .id("admin-user-id")
                .email("admin@owner.com")
                .role(Role.ROLE_USER)
                .build();

        memberUser = UserEntity.builder()
                .id("member-user-id")
                .email("member@owner.com")
                .role(Role.ROLE_USER)
                .build();

        ownerOrg = OrganizationEntity.builder()
                .orgCode("ORG_OWNER")
                .name("Owner Organization")
                .build();

        partnerOrg = OrganizationEntity.builder()
                .orgCode("ORG_PARTNER")
                .name("Partner Organization")
                .build();

        rootFolder = FolderEntity.builder()
                .id(100L)
                .name("Collaboration Root Folder")
                .ownerOrg(ownerOrg)
                .createdBy(adminUser)
                .build();
    }

    @Test
    @DisplayName("createCollaboration succeeds when active trust exists and user is Org Admin")
    void createCollaboration_Success_WithActiveTrust() {
        CreateCollaborationRequestDto request = CreateCollaborationRequestDto.builder()
                .name("Project Alpha Collaboration")
                .ownerOrgCode("ORG_OWNER")
                .partnerOrgCode("ORG_PARTNER")
                .rootFolderId(100L)
                .startsAt(now)
                .endsAt(now.plusSeconds(86400))
                .build();

        when(organizationRepository.findById("ORG_OWNER")).thenReturn(Optional.of(ownerOrg));
        when(organizationRepository.findById("ORG_PARTNER")).thenReturn(Optional.of(partnerOrg));
        when(orgTrustRelationshipService.hasActiveTrust("ORG_OWNER", "ORG_PARTNER")).thenReturn(true);

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(userOrg));

        when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));

        when(collaborationRepository.save(any(CollaborationEntity.class))).thenAnswer(invocation -> {
            CollaborationEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            return entity;
        });

        CollaborationResponseDto response = collaborationService.createCollaboration(request, adminUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Project Alpha Collaboration");
        assertThat(response.getOwnerOrgCode()).isEqualTo("ORG_OWNER");
        assertThat(response.getPartnerOrgCode()).isEqualTo("ORG_PARTNER");
        assertThat(response.getRootFolderId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(CollaborationStatus.PENDING);

        // Verify audit log recorded
        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        AuditPermissionChangeCommand auditLog = auditCaptor.getValue();
        assertThat(auditLog.getAction()).isEqualTo("COLLABORATION_CREATED");
        assertThat(auditLog.getTargetId()).isEqualTo(100L);
        assertThat(auditLog.getGrantedToOrgCode()).isEqualTo("ORG_PARTNER");
    }

    @Test
    @DisplayName("createCollaboration fails when active trust does not exist")
    void createCollaboration_Fails_WithoutActiveTrust() {
        CreateCollaborationRequestDto request = CreateCollaborationRequestDto.builder()
                .name("Project Alpha")
                .ownerOrgCode("ORG_OWNER")
                .partnerOrgCode("ORG_PARTNER")
                .rootFolderId(100L)
                .startsAt(now)
                .build();

        when(organizationRepository.findById("ORG_OWNER")).thenReturn(Optional.of(ownerOrg));
        when(organizationRepository.findById("ORG_PARTNER")).thenReturn(Optional.of(partnerOrg));
        when(orgTrustRelationshipService.hasActiveTrust("ORG_OWNER", "ORG_PARTNER")).thenReturn(false);

        assertThatThrownBy(() -> collaborationService.createCollaboration(request, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active trust relationship is required");

        verify(collaborationRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("createCollaboration fails when owner and partner organizations are the same")
    void createCollaboration_Fails_SameOrganization() {
        CreateCollaborationRequestDto request = CreateCollaborationRequestDto.builder()
                .name("Self Collaboration")
                .ownerOrgCode("ORG_OWNER")
                .partnerOrgCode("ORG_OWNER")
                .rootFolderId(100L)
                .startsAt(now)
                .build();

        assertThatThrownBy(() -> collaborationService.createCollaboration(request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be the same");

        verify(collaborationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCollaboration fails when creator is not an Org Admin")
    void createCollaboration_Fails_NonAdminUser() {
        CreateCollaborationRequestDto request = CreateCollaborationRequestDto.builder()
                .name("Project Beta")
                .ownerOrgCode("ORG_OWNER")
                .partnerOrgCode("ORG_PARTNER")
                .rootFolderId(100L)
                .startsAt(now)
                .build();

        when(organizationRepository.findById("ORG_OWNER")).thenReturn(Optional.of(ownerOrg));
        when(organizationRepository.findById("ORG_PARTNER")).thenReturn(Optional.of(partnerOrg));
        when(orgTrustRelationshipService.hasActiveTrust("ORG_OWNER", "ORG_PARTNER")).thenReturn(true);

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(memberUser)
                .organization(ownerOrg)
                .role(Role.ROLE_USER)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("member-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(userOrg));

        assertThatThrownBy(() -> collaborationService.createCollaboration(request, memberUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("administrator");

        verify(collaborationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCollaboration fails when endsAt is before startsAt")
    void createCollaboration_Fails_EndsAtBeforeStartsAt() {
        CreateCollaborationRequestDto request = CreateCollaborationRequestDto.builder()
                .name("Project Gamma")
                .ownerOrgCode("ORG_OWNER")
                .partnerOrgCode("ORG_PARTNER")
                .rootFolderId(100L)
                .startsAt(now)
                .endsAt(now.minusSeconds(3600))
                .build();

        assertThatThrownBy(() -> collaborationService.createCollaboration(request, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endsAt cannot be before startsAt");

        verify(collaborationRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCollaboration sets initial status to PENDING")
    void createCollaboration_InitialStatusIsPending() {
        CreateCollaborationRequestDto request = CreateCollaborationRequestDto.builder()
                .name("Project Delta")
                .ownerOrgCode("ORG_OWNER")
                .partnerOrgCode("ORG_PARTNER")
                .rootFolderId(100L)
                .startsAt(now)
                .build();

        when(organizationRepository.findById("ORG_OWNER")).thenReturn(Optional.of(ownerOrg));
        when(organizationRepository.findById("ORG_PARTNER")).thenReturn(Optional.of(partnerOrg));
        when(orgTrustRelationshipService.hasActiveTrust("ORG_OWNER", "ORG_PARTNER")).thenReturn(true);

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(userOrg));

        when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));

        ArgumentCaptor<CollaborationEntity> entityCaptor = ArgumentCaptor.forClass(CollaborationEntity.class);
        when(collaborationRepository.save(entityCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        collaborationService.createCollaboration(request, adminUser);

        CollaborationEntity savedEntity = entityCaptor.getValue();
        assertThat(savedEntity.getStatus()).isEqualTo(CollaborationStatus.PENDING);
    }

    @Test
    @DisplayName("addParticipant succeeds when collaboration is ACTIVE, user is Org Admin, target user is in owner/partner org")
    void addParticipant_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .name("Project Alpha")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .startsAt(now)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        when(userRepository.findById("member-user-id")).thenReturn(Optional.of(memberUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("member-user-id", "ORG_OWNER", MembershipStatus.ACTIVE)).thenReturn(true);
        when(collaborationParticipantRepository.existsByCollaboration_IdAndUser_Id(1L, "member-user-id")).thenReturn(false);

        when(collaborationParticipantRepository.save(any(CollaborationParticipantEntity.class))).thenAnswer(invocation -> {
            CollaborationParticipantEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        CollaborationParticipantResponseDto response = collaborationService.addParticipant(1L, "member-user-id", adminUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getCollaborationId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo("member-user-id");

        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("COLLABORATION_PARTICIPANT_ADDED");
    }

    @Test
    @DisplayName("addParticipant fails when collaboration is not ACTIVE")
    void addParticipant_Fails_NotActive() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.PENDING)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        assertThatThrownBy(() -> collaborationService.addParticipant(1L, "member-user-id", adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE collaborations");

        verify(collaborationParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("addParticipant fails when user is not Org Admin")
    void addParticipant_Fails_NonAdmin() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("member-user-id", "ORG_OWNER")).thenReturn(Optional.empty());
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("member-user-id", "ORG_PARTNER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.addParticipant(1L, "member-user-id", memberUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("administrator");
    }

    @Test
    @DisplayName("addParticipant fails when target user does not belong to owner or partner org")
    void addParticipant_Fails_TargetUserOutsideOrgs() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        UserEntity outsideUser = UserEntity.builder().id("outside-user-id").build();
        when(userRepository.findById("outside-user-id")).thenReturn(Optional.of(outsideUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("outside-user-id", "ORG_OWNER", MembershipStatus.ACTIVE)).thenReturn(false);
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("outside-user-id", "ORG_PARTNER", MembershipStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> collaborationService.addParticipant(1L, "outside-user-id", adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to either owner or partner organization");
    }

    @Test
    @DisplayName("addParticipant fails when target user is already a participant")
    void addParticipant_Fails_AlreadyParticipant() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        when(userRepository.findById("member-user-id")).thenReturn(Optional.of(memberUser));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("member-user-id", "ORG_OWNER", MembershipStatus.ACTIVE)).thenReturn(true);
        when(collaborationParticipantRepository.existsByCollaboration_IdAndUser_Id(1L, "member-user-id")).thenReturn(true);

        assertThatThrownBy(() -> collaborationService.addParticipant(1L, "member-user-id", adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a participant");
    }

    @Test
    @DisplayName("removeParticipant succeeds when collaboration is ACTIVE and caller is Org Admin")
    void removeParticipant_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        CollaborationParticipantEntity participant = CollaborationParticipantEntity.builder()
                .id(10L)
                .collaboration(collaboration)
                .user(memberUser)
                .createdAt(now)
                .build();

        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(1L, "member-user-id"))
                .thenReturn(Optional.of(participant));

        PermissionEntity activePermission = PermissionEntity.builder()
                .id(200L)
                .collaboration(collaboration)
                .targetUserEmail(memberUser.getEmail())
                .revoked(false)
                .build();
        when(permissionRepository.findByCollaboration_IdAndTargetUserEmailIgnoreCaseAndRevokedFalse(1L, memberUser.getEmail()))
                .thenReturn(List.of(activePermission));

        collaborationService.removeParticipant(1L, "member-user-id", adminUser);

        verify(permissionService).revokePermission(200L, adminUser.getId());
        verify(collaborationParticipantRepository).delete(participant);
        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("COLLABORATION_PARTICIPANT_REMOVED");
    }

    @Test
    @DisplayName("getParticipants succeeds for member of owner organization")
    void getParticipants_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findById(1L)).thenReturn(Optional.of(collaboration));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("member-user-id", "ORG_OWNER", MembershipStatus.ACTIVE)).thenReturn(true);

        CollaborationParticipantEntity participant = CollaborationParticipantEntity.builder()
                .id(10L)
                .collaboration(collaboration)
                .user(memberUser)
                .createdAt(now)
                .build();

        when(collaborationParticipantRepository.findByCollaboration_Id(1L)).thenReturn(List.of(participant));

        List<CollaborationParticipantResponseDto> participants = collaborationService.getParticipants(1L, memberUser);

        assertThat(participants).hasSize(1);
        assertThat(participants.get(0).getUserId()).isEqualTo("member-user-id");
    }

    @Test
    @DisplayName("addGroupParticipant succeeds for group in owner organization")
    void addGroupParticipant_Success_OwnerOrgGroup() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .name("Project Alpha")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        GroupEntity ownerGroup = GroupEntity.builder()
                .id(50L)
                .name("Engineering Team")
                .organization(ownerOrg)
                .build();

        when(groupRepository.findById(50L)).thenReturn(Optional.of(ownerGroup));
        when(collaborationGroupParticipantRepository.existsByCollaboration_IdAndGroup_Id(1L, 50L)).thenReturn(false);

        when(collaborationGroupParticipantRepository.save(any(CollaborationGroupParticipantEntity.class))).thenAnswer(invocation -> {
            CollaborationGroupParticipantEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return entity;
        });

        CollaborationGroupParticipantResponseDto response = collaborationService.addGroupParticipant(1L, 50L, adminUser);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getCollaborationId()).isEqualTo(1L);
        assertThat(response.getGroupId()).isEqualTo(50L);
        assertThat(response.getGroupName()).isEqualTo("Engineering Team");

        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("COLLABORATION_GROUP_ADDED");
    }

    @Test
    @DisplayName("addGroupParticipant succeeds for group in partner organization")
    void addGroupParticipant_Success_PartnerOrgGroup() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .name("Project Alpha")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        GroupEntity partnerGroup = GroupEntity.builder()
                .id(51L)
                .name("Partner QA Team")
                .organization(partnerOrg)
                .build();

        when(groupRepository.findById(51L)).thenReturn(Optional.of(partnerGroup));
        when(collaborationGroupParticipantRepository.existsByCollaboration_IdAndGroup_Id(1L, 51L)).thenReturn(false);

        when(collaborationGroupParticipantRepository.save(any(CollaborationGroupParticipantEntity.class))).thenAnswer(invocation -> {
            CollaborationGroupParticipantEntity entity = invocation.getArgument(0);
            entity.setId(101L);
            return entity;
        });

        CollaborationGroupParticipantResponseDto response = collaborationService.addGroupParticipant(1L, 51L, adminUser);

        assertThat(response).isNotNull();
        assertThat(response.getGroupId()).isEqualTo(51L);
        assertThat(response.getGroupName()).isEqualTo("Partner QA Team");
    }

    @Test
    @DisplayName("addGroupParticipant fails when group belongs to unrelated organization")
    void addGroupParticipant_Fails_UnrelatedOrgGroup() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        OrganizationEntity unrelatedOrg = OrganizationEntity.builder().orgCode("ORG_UNRELATED").build();
        GroupEntity unrelatedGroup = GroupEntity.builder()
                .id(99L)
                .name("External Group")
                .organization(unrelatedOrg)
                .build();

        when(groupRepository.findById(99L)).thenReturn(Optional.of(unrelatedGroup));

        assertThatThrownBy(() -> collaborationService.addGroupParticipant(1L, 99L, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to either owner or partner organization");

        verify(collaborationGroupParticipantRepository, never()).save(any());
    }

    @Test
    @DisplayName("addGroupParticipant fails when group is already a participant")
    void addGroupParticipant_Fails_DuplicateGroup() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        GroupEntity ownerGroup = GroupEntity.builder().id(50L).organization(ownerOrg).build();
        when(groupRepository.findById(50L)).thenReturn(Optional.of(ownerGroup));
        when(collaborationGroupParticipantRepository.existsByCollaboration_IdAndGroup_Id(1L, 50L)).thenReturn(true);

        assertThatThrownBy(() -> collaborationService.addGroupParticipant(1L, 50L, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a participant");
    }

    @Test
    @DisplayName("addGroupParticipant fails when collaboration is not ACTIVE")
    void addGroupParticipant_Fails_NotActive() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.PENDING)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        assertThatThrownBy(() -> collaborationService.addGroupParticipant(1L, 50L, adminUser))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACTIVE collaborations");
    }

    @Test
    @DisplayName("removeGroupParticipant succeeds when collaboration is ACTIVE and user is Org Admin")
    void removeGroupParticipant_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));

        UserOrganizationEntity adminUserOrg = UserOrganizationEntity.builder()
                .user(adminUser)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("admin-user-id", "ORG_OWNER"))
                .thenReturn(Optional.of(adminUserOrg));

        GroupEntity group = GroupEntity.builder().id(50L).organization(ownerOrg).build();
        CollaborationGroupParticipantEntity participant = CollaborationGroupParticipantEntity.builder()
                .id(100L)
                .collaboration(collaboration)
                .group(group)
                .createdAt(now)
                .build();

        when(collaborationGroupParticipantRepository.findByCollaboration_IdAndGroup_Id(1L, 50L))
                .thenReturn(Optional.of(participant));

        PermissionEntity activeGroupPermission = PermissionEntity.builder()
                .id(300L)
                .collaboration(collaboration)
                .targetGroup(group)
                .revoked(false)
                .build();
        when(permissionRepository.findByCollaboration_IdAndTargetGroup_IdAndRevokedFalse(1L, 50L))
                .thenReturn(List.of(activeGroupPermission));

        collaborationService.removeGroupParticipant(1L, 50L, adminUser);

        verify(permissionService).revokePermission(300L, adminUser.getId());
        verify(collaborationGroupParticipantRepository).delete(participant);
        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor = ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("COLLABORATION_GROUP_REMOVED");
    }

    @Test
    @DisplayName("getGroupParticipants succeeds for authorized user in owner org")
    void getGroupParticipants_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .status(CollaborationStatus.ACTIVE)
                .build();

        when(collaborationRepository.findById(1L)).thenReturn(Optional.of(collaboration));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus("member-user-id", "ORG_OWNER", MembershipStatus.ACTIVE)).thenReturn(true);

        GroupEntity group = GroupEntity.builder().id(50L).name("Dev Group").organization(ownerOrg).build();
        CollaborationGroupParticipantEntity participant = CollaborationGroupParticipantEntity.builder()
                .id(100L)
                .collaboration(collaboration)
                .group(group)
                .createdAt(now)
                .build();

        when(collaborationGroupParticipantRepository.findByCollaboration_Id(1L)).thenReturn(List.of(participant));

        List<CollaborationGroupParticipantResponseDto> list = collaborationService.getGroupParticipants(1L, memberUser);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getGroupId()).isEqualTo(50L);
        assertThat(list.get(0).getGroupName()).isEqualTo("Dev Group");
    }

    @Test
    @DisplayName("grantCollaborationPermission sets collaborationId on request and calls permissionService")
    void grantCollaborationPermission_SetsCollaborationId_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(1L)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        PermissionRequestDto requestDto = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("alice@partner.com")
                .folderId(10L)
                .build();

        FolderEntity targetFolder = FolderEntity.builder().id(10L).build();
        UserEntity superAdmin = UserEntity.builder().id("admin-id").role(Role.ROLE_SUPER_ADMIN).build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));
        when(folderRepository.findById(10L)).thenReturn(Optional.of(targetFolder));
        when(permissionEvaluatorService.isSameOrAncestorFolder(rootFolder, targetFolder)).thenReturn(true);
        when(userRepository.findByEmail("alice@partner.com")).thenReturn(Optional.of(memberUser));
        when(collaborationParticipantRepository.existsByCollaboration_IdAndUser_Id(1L, memberUser.getId())).thenReturn(true);

        PermissionResponseDto expectedResponse = PermissionResponseDto.builder().id(100L).collaborationId(1L).build();
        when(permissionService.grantPermission(any(PermissionRequestDto.class), eq("admin-id"))).thenReturn(expectedResponse);

        PermissionResponseDto result = collaborationService.grantCollaborationPermission(1L, requestDto, superAdmin);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);

        ArgumentCaptor<PermissionRequestDto> requestCaptor = ArgumentCaptor.forClass(PermissionRequestDto.class);
        verify(permissionService).grantPermission(requestCaptor.capture(), eq("admin-id"));
        assertThat(requestCaptor.getValue().getCollaborationId()).isEqualTo(1L);
    }

    // =========================================================================
    // Step 11G-C: Collaboration-Wide Lifecycle Teardown Tests
    // =========================================================================

    private CollaborationEntity buildActiveCollaboration(Long id) {
        return CollaborationEntity.builder()
                .id(id)
                .name("Test Collaboration")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .createdBy(adminUser)
                .status(CollaborationStatus.ACTIVE)
                .startsAt(now.minusSeconds(3600))
                .endsAt(now.plusSeconds(86400))
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private PermissionEntity buildActivePermission(Long id, Long collaborationId) {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(collaborationId)
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .rootFolder(rootFolder)
                .status(CollaborationStatus.ACTIVE)
                .build();

        return PermissionEntity.builder()
                .id(id)
                .revoked(false)
                .collaboration(collaboration)
                .folder(rootFolder)
                .build();
    }

    @Test
    @DisplayName("revokeCollaboration: ACTIVE collaboration is revoked, permissions torn down, audit written")
    void revokeCollaboration_ActiveCollaboration_RevokesPermissionsAndStatus() {
        CollaborationEntity collaboration = buildActiveCollaboration(5L);
        UserEntity orgAdmin = UserEntity.builder()
                .id("org-admin-id")
                .email("admin@owner.com")
                .role(Role.ROLE_USER)
                .build();

        UserOrganizationEntity orgMembership = UserOrganizationEntity.builder()
                .user(orgAdmin)
                .organization(ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        List<PermissionEntity> activePerms = List.of(
                buildActivePermission(10L, 5L),
                buildActivePermission(11L, 5L)
        );

        when(collaborationRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(collaboration));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("org-admin-id", "ORG_OWNER"))
                .thenReturn(Optional.of(orgMembership));
        when(permissionRepository.findByCollaboration_IdAndRevokedFalse(5L)).thenReturn(activePerms);
        when(permissionService.revokePermission(anyLong(), eq("org-admin-id")))
                .thenReturn(PermissionResponseDto.builder().id(10L).build());
        when(collaborationRepository.save(collaboration)).thenReturn(collaboration);

        CollaborationResponseDto result = collaborationService.revokeCollaboration(5L, orgAdmin);

        assertThat(result).isNotNull();
        assertThat(collaboration.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        // Both permissions must be revoked through the centralized pipeline
        verify(permissionService).revokePermission(10L, "org-admin-id");
        verify(permissionService).revokePermission(11L, "org-admin-id");

        // COLLABORATION_REVOKED audit record must be persisted
        ArgumentCaptor<AuditPermissionChangeCommand> auditCaptor =
                ArgumentCaptor.forClass(AuditPermissionChangeCommand.class);
        verify(auditLogService).logPermissionChange(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getAction()).isEqualTo("COLLABORATION_REVOKED");
    }

    @Test
    @DisplayName("revokeCollaboration: already REVOKED collaboration is idempotent — no further action")
    void revokeCollaboration_AlreadyRevoked_IsIdempotent() {
        CollaborationEntity collaboration = buildActiveCollaboration(6L);
        collaboration.setStatus(CollaborationStatus.REVOKED);

        UserEntity superAdmin = UserEntity.builder()
                .id("super-admin-id")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(collaborationRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(collaboration));

        CollaborationResponseDto result = collaborationService.revokeCollaboration(6L, superAdmin);

        assertThat(result).isNotNull();
        // No permissions touched, no save, no audit
        verify(permissionRepository, never()).findByCollaboration_IdAndRevokedFalse(anyLong());
        verify(collaborationRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("revokeCollaboration: already EXPIRED collaboration is idempotent — no further action")
    void revokeCollaboration_AlreadyExpired_IsIdempotent() {
        CollaborationEntity collaboration = buildActiveCollaboration(7L);
        collaboration.setStatus(CollaborationStatus.EXPIRED);

        UserEntity superAdmin = UserEntity.builder()
                .id("super-admin-id")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(collaborationRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(collaboration));

        CollaborationResponseDto result = collaborationService.revokeCollaboration(7L, superAdmin);

        assertThat(result).isNotNull();
        verify(permissionRepository, never()).findByCollaboration_IdAndRevokedFalse(anyLong());
        verify(collaborationRepository, never()).save(any());
        verify(auditLogService, never()).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("revokeCollaboration: CollaborationManager (non-admin) is denied")
    void revokeCollaboration_CollaborationManager_IsDenied() {
        CollaborationEntity collaboration = buildActiveCollaboration(8L);
        UserEntity manager = UserEntity.builder()
                .id("manager-user-id")
                .role(Role.ROLE_USER)
                .build();

        when(collaborationRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(collaboration));
        // manager is NOT an org admin in either org
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("manager-user-id", "ORG_OWNER"))
                .thenReturn(Optional.empty());
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("manager-user-id", "ORG_PARTNER"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.revokeCollaboration(8L, manager))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("organization administrators");

        verify(permissionRepository, never()).findByCollaboration_IdAndRevokedFalse(anyLong());
        verify(collaborationRepository, never()).save(any());
    }

    @Test
    @DisplayName("revokeCollaboration: SUPER_ADMIN can always revoke any collaboration")
    void revokeCollaboration_SuperAdmin_IsAlwaysAuthorized() {
        CollaborationEntity collaboration = buildActiveCollaboration(9L);
        UserEntity superAdmin = UserEntity.builder()
                .id("super-admin-id")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(collaborationRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(collaboration));
        when(permissionRepository.findByCollaboration_IdAndRevokedFalse(9L)).thenReturn(List.of());
        when(collaborationRepository.save(collaboration)).thenReturn(collaboration);

        CollaborationResponseDto result = collaborationService.revokeCollaboration(9L, superAdmin);

        assertThat(result).isNotNull();
        assertThat(collaboration.getStatus()).isEqualTo(CollaborationStatus.REVOKED);
        verify(auditLogService).logPermissionChange(any(AuditPermissionChangeCommand.class));
    }

    @Test
    @DisplayName("revokeCollaboration: ACTIVE collaboration with no active permissions still changes status")
    void revokeCollaboration_NoActivePermissions_StatusIsStillRevoked() {
        CollaborationEntity collaboration = buildActiveCollaboration(10L);
        UserEntity superAdmin = UserEntity.builder()
                .id("super-admin-id")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(collaborationRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(collaboration));
        when(permissionRepository.findByCollaboration_IdAndRevokedFalse(10L)).thenReturn(List.of());
        when(collaborationRepository.save(collaboration)).thenReturn(collaboration);

        collaborationService.revokeCollaboration(10L, superAdmin);

        assertThat(collaboration.getStatus()).isEqualTo(CollaborationStatus.REVOKED);
        verify(permissionService, never()).revokePermission(anyLong(), any());
    }

    @Test
    @DisplayName("performCollaborationTeardown: revokes all active collaboration-derived permissions")
    void performCollaborationTeardown_RevokesAllActivePermissions() {
        CollaborationEntity collaboration = buildActiveCollaboration(11L);
        List<PermissionEntity> activePerms = List.of(
                buildActivePermission(20L, 11L),
                buildActivePermission(21L, 11L),
                buildActivePermission(22L, 11L)
        );

        when(permissionRepository.findByCollaboration_IdAndRevokedFalse(11L)).thenReturn(activePerms);
        when(permissionService.revokePermission(anyLong(), any()))
                .thenReturn(PermissionResponseDto.builder().id(20L).build());

        collaborationService.performCollaborationTeardown(collaboration, "actor-id");

        verify(permissionService, times(3)).revokePermission(anyLong(), eq("actor-id"));
        verify(permissionService).revokePermission(20L, "actor-id");
        verify(permissionService).revokePermission(21L, "actor-id");
        verify(permissionService).revokePermission(22L, "actor-id");
    }

    @Test
    @DisplayName("performCollaborationTeardown: partial permission failure does not abort remaining revocations")
    void performCollaborationTeardown_PartialFailure_ContinuesProcessing() {
        CollaborationEntity collaboration = buildActiveCollaboration(12L);
        List<PermissionEntity> activePerms = List.of(
                buildActivePermission(30L, 12L),
                buildActivePermission(31L, 12L)
        );

        when(permissionRepository.findByCollaboration_IdAndRevokedFalse(12L)).thenReturn(activePerms);
        when(permissionService.revokePermission(eq(30L), any()))
                .thenThrow(new RuntimeException("Simulated revocation failure"));
        when(permissionService.revokePermission(eq(31L), any()))
                .thenReturn(PermissionResponseDto.builder().id(31L).build());

        // Must NOT throw — failures are caught and logged individually
        assertThatCode(() -> collaborationService.performCollaborationTeardown(collaboration, "actor-id"))
                .doesNotThrowAnyException();

        verify(permissionService).revokePermission(30L, "actor-id");
        verify(permissionService).revokePermission(31L, "actor-id");
    }

    @Test
    @DisplayName("performCollaborationTeardown: empty permission list is a no-op")
    void performCollaborationTeardown_EmptyPermissions_IsNoOp() {
        CollaborationEntity collaboration = buildActiveCollaboration(13L);

        when(permissionRepository.findByCollaboration_IdAndRevokedFalse(13L)).thenReturn(List.of());

        assertThatCode(() -> collaborationService.performCollaborationTeardown(collaboration, "actor-id"))
                .doesNotThrowAnyException();

        verify(permissionService, never()).revokePermission(anyLong(), any());
    }

    @Test
    @DisplayName("revokeCollaboration: unauthenticated call throws AccessDeniedException")
    void revokeCollaboration_NullCurrentUser_ThrowsAccessDenied() {
        assertThatThrownBy(() -> collaborationService.revokeCollaboration(1L, null))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("revokeCollaboration: non-existent collaboration throws ResourceNotFoundException")
    void revokeCollaboration_NotFound_ThrowsResourceNotFoundException() {
        UserEntity superAdmin = UserEntity.builder()
                .id("super-admin-id")
                .role(Role.ROLE_SUPER_ADMIN)
                .build();

        when(collaborationRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> collaborationService.revokeCollaboration(999L, superAdmin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assignManager: promotes participant to MANAGER and evicts user permission cache")
    void assignManager_PromotesToManager_AndEvictsCache() {
        CollaborationEntity collaboration = buildActiveCollaboration(1L);
        CollaborationParticipantEntity participant = CollaborationParticipantEntity.builder()
                .id(50L)
                .collaboration(collaboration)
                .user(memberUser)
                .collaborationRole(CollaborationRole.MEMBER)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(adminUser.getId(), "ORG_OWNER"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().role(Role.ROLE_ORG_ADMIN).build()));
        when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(memberUser.getId(), "ORG_OWNER", MembershipStatus.ACTIVE)).thenReturn(true);
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(1L, memberUser.getId()))
                .thenReturn(Optional.of(participant));
        when(collaborationParticipantRepository.save(any(CollaborationParticipantEntity.class))).thenAnswer(i -> i.getArgument(0));

        CollaborationParticipantResponseDto response = collaborationService.assignManager(1L, memberUser.getId(), adminUser);

        assertThat(response.getCollaborationRole()).isEqualTo(CollaborationRole.MANAGER);
        verify(permissionCacheService).evictUserPermissions(memberUser.getEmail());
    }

    @Test
    @DisplayName("removeManager: demotes participant to MEMBER and evicts user permission cache")
    void removeManager_DemotesToMember_AndEvictsCache() {
        CollaborationEntity collaboration = buildActiveCollaboration(1L);
        CollaborationParticipantEntity participant = CollaborationParticipantEntity.builder()
                .id(50L)
                .collaboration(collaboration)
                .user(memberUser)
                .collaborationRole(CollaborationRole.MANAGER)
                .build();

        when(collaborationRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(collaboration));
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(adminUser.getId(), "ORG_OWNER"))
                .thenReturn(Optional.of(UserOrganizationEntity.builder().role(Role.ROLE_ORG_ADMIN).build()));
        when(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(1L, memberUser.getId()))
                .thenReturn(Optional.of(participant));
        when(collaborationParticipantRepository.save(any(CollaborationParticipantEntity.class))).thenAnswer(i -> i.getArgument(0));

        CollaborationParticipantResponseDto response = collaborationService.removeManager(1L, memberUser.getId(), adminUser);

        assertThat(response.getCollaborationRole()).isEqualTo(CollaborationRole.MEMBER);
        verify(permissionCacheService).evictUserPermissions(memberUser.getEmail());
    }
}

