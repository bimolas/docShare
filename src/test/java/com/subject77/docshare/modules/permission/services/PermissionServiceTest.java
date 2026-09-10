package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.dto.BulkShareWithExclusionsDto;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.events.PermissionChangeEvent;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.mapper.PermissionMapper;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.BulkSelectedMemberShareDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.notification.entities.NotificationCategory;
import com.subject77.docshare.modules.notification.entities.NotificationPriority;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.notification.entities.NotificationSourceType;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessEntryDto;
import com.subject77.docshare.modules.permission.dto.EffectiveAccessAuditDto;
import com.subject77.docshare.modules.permission.dto.ManageAccessSummaryDto;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private AuditLogService auditLogService;

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
    private PermissionRequestDto requestDto;

    @BeforeEach
    void setUp() {
        grantor = UserEntity.builder()
                .id("user-admin")
                .email("admin@docshare.com")
                .build();

        requestDto = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("alice@example.com")
                .documentFileHash("hash999")
                .build();

        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(true);
        lenient().when(evaluatorService.resolveDelegationCeiling(any(), any(), any())).thenReturn(PermissionLevel.EDIT);
        lenient().when(evaluatorService.canActorOverrideGrant(any(), any(), any(), any())).thenReturn(true);
        lenient().when(evaluatorService.canActorOverrideInactivator(any(), any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Should successfully grant permission with SHA-256 hash, audit log, Kafka event, and cache eviction")
    void grantPermission_Success() {
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity entity = invocation.getArgument(0);
            entity.setId(10L);
            return entity;
        });

        PermissionResponseDto response = permissionService.grantPermission(requestDto, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getRecordHash()).isNotNull().hasSize(64);
        assertThat(response.getBlockchainStatus()).isEqualTo(BlockchainStatus.PENDING);
        assertThat(response.getShareLinkToken()).isNull();

        // Verify DB Save
        ArgumentCaptor<PermissionEntity> savedPermissionCaptor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(savedPermissionCaptor.capture());
        assertThat(savedPermissionCaptor.getValue().getShareLinkToken()).isNull();

        // Verify Audit Log Save
        verify(auditLogService).logPermissionChange(any(AuditPermissionChangeCommand.class));

        // Verify Kafka Event Published
        ArgumentCaptor<PermissionChangeEvent> eventCaptor = ArgumentCaptor.forClass(PermissionChangeEvent.class);
        verify(eventPublisher).publishPermissionChangeEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("GRANT");
        assertThat(eventCaptor.getValue().getPermissionId()).isEqualTo(10L);

        // Verify Redis Cache Eviction
        verify(cacheService).evictUserPermissions("alice@example.com");
    }

    @Test
    @DisplayName("Should successfully grant permission with optional note field")
    void grantPermission_WithNote_Success() {
        FolderEntity targetFolder = FolderEntity.builder().id(100L).name("Test Folder").build();
        PermissionRequestDto reqWithNote = PermissionRequestDto.builder()
                .targetUserEmail("alice@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .folderId(100L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .note("Please review section 2 before Monday.")
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(targetFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, targetFolder)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            return entity;
        });

        PermissionResponseDto response = permissionService.grantPermission(reqWithNote, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(11L);
        assertThat(response.getNote()).isEqualTo("Please review section 2 before Monday.");
    }

    @Test
    @DisplayName("Should successfully grant permission and link collaboration when collaborationId is provided")
    void grantPermission_WithCollaborationId_Success() {
        CollaborationEntity collaboration = CollaborationEntity.builder()
                .id(500L)
                .name("Test Collaboration")
                .build();

        PermissionRequestDto collabRequestDto = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("alice@example.com")
                .documentFileHash("hash999")
                .collaborationId(500L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(collaborationRepository.findById(500L)).thenReturn(Optional.of(collaboration));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity entity = invocation.getArgument(0);
            entity.setId(15L);
            return entity;
        });

        PermissionResponseDto response = permissionService.grantPermission(collabRequestDto, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(15L);
        assertThat(response.getCollaborationId()).isEqualTo(500L);

        ArgumentCaptor<PermissionEntity> entityCaptor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getCollaboration()).isNotNull();
        assertThat(entityCaptor.getValue().getCollaboration().getId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when granting folder permission without authority")
    void grantPermission_Folder_AccessDenied() {
        FolderEntity targetFolder = FolderEntity.builder().id(55L).name("Folder").build();
        PermissionRequestDto folderRequest = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("alice@example.com")
                .folderId(55L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(55L)).thenReturn(Optional.of(targetFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, targetFolder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.grantPermission(folderRequest, "user-admin"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should successfully update permission level and expiration, recalculating hash and triggering audit/kafka/cache")
    void updatePermission_Success() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileHash("hash999").build();
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("alice@example.com")
                .document(doc)
                .documentFileHash("hash999")
                .grantedBy(grantor)
                .revoked(false)
                .validFrom(Instant.now().minusSeconds(3600))
                .build();

        Instant newExpiry = Instant.now().plusSeconds(86400);
        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .expiresAt(newExpiry)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PermissionResponseDto response = permissionService.updatePermission(10L, updateDto, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        assertThat(response.getExpiresAt()).isEqualTo(newExpiry);
        assertThat(response.getRecordHash()).isNotNull().hasSize(64);

        verify(permissionRepository).save(any(PermissionEntity.class));
        verify(auditLogService).logPermissionChange(any(AuditPermissionChangeCommand.class));

        ArgumentCaptor<PermissionChangeEvent> eventCaptor = ArgumentCaptor.forClass(PermissionChangeEvent.class);
        verify(eventPublisher).publishPermissionChangeEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("UPDATE");
        assertThat(eventCaptor.getValue().getPermissionId()).isEqualTo(10L);

        verify(cacheService).evictUserPermissions("alice@example.com");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when updating permission without administration authority")
    void updatePermission_AccessDenied() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileHash("hash999").build();
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("alice@example.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.updatePermission(10L, updateDto, "user-admin"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when updating a revoked permission")
    void updatePermission_RevokedPermission() {
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .revoked(true)
                .build();

        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));

        assertThatThrownBy(() -> permissionService.updatePermission(10L, updateDto, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot update a revoked permission");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when updating with expiration date in the past")
    void updatePermission_PastExpiration() {
        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.EDIT)
                .expiresAt(Instant.now().minusSeconds(100))
                .build();

        assertThatThrownBy(() -> permissionService.updatePermission(10L, updateDto, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expiration date cannot be in the past");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when permission level is missing in update request")
    void updatePermission_NullPermissionLevel() {
        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder()
                .permissionLevel(null)
                .build();

        assertThatThrownBy(() -> permissionService.updatePermission(10L, updateDto, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Permission level is required");
    }

    @Test
    @DisplayName("Should successfully revoke permission and trigger audit, Kafka event, and cache eviction")
    void revokePermission_Success() {
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .documentFileHash("hash999")
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionResponseDto response = permissionService.revokePermission(10L, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.isRevoked()).isTrue();
        assertThat(response.getRecordHash()).isNotNull().hasSize(64);

        // Verify Audit Log
        verify(auditLogService).logPermissionChange(any(AuditPermissionChangeCommand.class));

        // Verify Kafka Event
        ArgumentCaptor<PermissionChangeEvent> eventCaptor = ArgumentCaptor.forClass(PermissionChangeEvent.class);
        verify(eventPublisher).publishPermissionChangeEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEventType()).isEqualTo("REVOKE");

        // Verify Cache Eviction
        verify(cacheService).evictUserPermissions("alice@example.com");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when revoking folder permission without authority")
    void revokePermission_Folder_AccessDenied() {
        FolderEntity targetFolder = FolderEntity.builder().id(55L).name("Folder").build();
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerFolderPermissions(grantor, targetFolder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.revokePermission(10L, "user-admin"))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should NOT bypass authorization when revokedBy is SYSTEM string and throw ResourceNotFoundException")
    void revokePermission_SystemString_NotBypassed() {
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .documentFileHash("hash999")
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));
        when(userRepository.findById("SYSTEM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.revokePermission(10L, "SYSTEM"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("User not found with ID: SYSTEM");

        verify(permissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should support trusted SYSTEM_SCHEDULER actor without user lookup")
    void revokePermission_SystemScheduler_Success() {
        PermissionEntity existingPermission = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .documentFileHash("hash999")
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(existingPermission));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PermissionResponseDto response = permissionService.revokePermission(10L, "SYSTEM_SCHEDULER");

        assertThat(response).isNotNull();
        assertThat(response.isRevoked()).isTrue();
        verify(userRepository, never()).findById(any());
        verify(evaluatorService, never()).canAdministerFolderPermissions(any(), any());
    }

    @Test
    @DisplayName("Collaboration permission revoke by ORG_MANAGER should be denied")
    void revokePermission_Collaboration_OrgManager_Denied() {
        OrganizationEntity ownerOrg = OrganizationEntity.builder().orgCode("OWNER_ORG").name("Owner Org").build();
        OrganizationEntity partnerOrg = OrganizationEntity.builder().orgCode("PARTNER_ORG").name("Partner Org").build();
        CollaborationEntity collab = CollaborationEntity.builder().id(500L).name("Collab")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .build();
        FolderEntity targetFolder = FolderEntity.builder().id(55L).name("Collab Folder").ownerOrg(ownerOrg).build();
        PermissionEntity collabPermission = PermissionEntity.builder()
                .id(20L)
                .collaboration(collab)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        UserEntity mgr = UserEntity.builder().id("mgr-1").role(Role.ROLE_ORG_MANAGER).email("mgr@owner.com").build();
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(collabPermission));
        when(userRepository.findById("mgr-1")).thenReturn(Optional.of(mgr));
        when(evaluatorService.isUserAdminOfOrg(mgr, "OWNER_ORG")).thenReturn(false);
        when(evaluatorService.isUserAdminOfOrg(mgr, "PARTNER_ORG")).thenReturn(false);
        when(evaluatorService.canAdministerFolderPermissions(mgr, targetFolder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.revokePermission(20L, "mgr-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Collaboration permission revoke by regular USER should be denied")
    void revokePermission_Collaboration_RegularUser_Denied() {
        OrganizationEntity ownerOrg = OrganizationEntity.builder().orgCode("OWNER_ORG").name("Owner Org").build();
        OrganizationEntity partnerOrg = OrganizationEntity.builder().orgCode("PARTNER_ORG").name("Partner Org").build();
        CollaborationEntity collab = CollaborationEntity.builder().id(500L).name("Collab")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .build();
        FolderEntity targetFolder = FolderEntity.builder().id(55L).name("Collab Folder").ownerOrg(ownerOrg).build();
        PermissionEntity collabPermission = PermissionEntity.builder()
                .id(20L)
                .collaboration(collab)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        UserEntity user = UserEntity.builder().id("usr-1").role(Role.ROLE_USER).email("user@partner.com").build();
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(collabPermission));
        when(userRepository.findById("usr-1")).thenReturn(Optional.of(user));
        when(evaluatorService.isUserAdminOfOrg(user, "OWNER_ORG")).thenReturn(false);
        when(evaluatorService.isUserAdminOfOrg(user, "PARTNER_ORG")).thenReturn(false);
        when(evaluatorService.canAdministerFolderPermissions(user, targetFolder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.revokePermission(20L, "usr-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Collaboration permission revoke by suspended ORG_ADMIN should be denied")
    void revokePermission_Collaboration_SuspendedOrgAdmin_Denied() {
        OrganizationEntity ownerOrg = OrganizationEntity.builder().orgCode("OWNER_ORG").name("Owner Org").build();
        OrganizationEntity partnerOrg = OrganizationEntity.builder().orgCode("PARTNER_ORG").name("Partner Org").build();
        CollaborationEntity collab = CollaborationEntity.builder().id(500L).name("Collab")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .build();
        FolderEntity targetFolder = FolderEntity.builder().id(55L).name("Collab Folder").ownerOrg(ownerOrg).build();
        PermissionEntity collabPermission = PermissionEntity.builder()
                .id(20L)
                .collaboration(collab)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        UserEntity suspendedAdmin = UserEntity.builder().id("susp-1").role(Role.ROLE_ORG_ADMIN).email("susp@owner.com").build();
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(collabPermission));
        when(userRepository.findById("susp-1")).thenReturn(Optional.of(suspendedAdmin));
        when(evaluatorService.isUserAdminOfOrg(suspendedAdmin, "OWNER_ORG")).thenReturn(false);
        when(evaluatorService.isUserAdminOfOrg(suspendedAdmin, "PARTNER_ORG")).thenReturn(false);
        when(evaluatorService.canAdministerFolderPermissions(suspendedAdmin, targetFolder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.revokePermission(20L, "susp-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Collaboration permission revoke by removed ORG_ADMIN should be denied")
    void revokePermission_Collaboration_RemovedOrgAdmin_Denied() {
        OrganizationEntity ownerOrg = OrganizationEntity.builder().orgCode("OWNER_ORG").name("Owner Org").build();
        OrganizationEntity partnerOrg = OrganizationEntity.builder().orgCode("PARTNER_ORG").name("Partner Org").build();
        CollaborationEntity collab = CollaborationEntity.builder().id(500L).name("Collab")
                .ownerOrganization(ownerOrg)
                .partnerOrganization(partnerOrg)
                .build();
        FolderEntity targetFolder = FolderEntity.builder().id(55L).name("Collab Folder").ownerOrg(ownerOrg).build();
        PermissionEntity collabPermission = PermissionEntity.builder()
                .id(20L)
                .collaboration(collab)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        UserEntity removedAdmin = UserEntity.builder().id("rem-1").role(Role.ROLE_ORG_ADMIN).email("rem@owner.com").build();
        when(permissionRepository.findById(20L)).thenReturn(Optional.of(collabPermission));
        when(userRepository.findById("rem-1")).thenReturn(Optional.of(removedAdmin));
        when(evaluatorService.isUserAdminOfOrg(removedAdmin, "OWNER_ORG")).thenReturn(false);
        when(evaluatorService.isUserAdminOfOrg(removedAdmin, "PARTNER_ORG")).thenReturn(false);
        when(evaluatorService.canAdministerFolderPermissions(removedAdmin, targetFolder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.revokePermission(20L, "rem-1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should return empty lists when currentUser is null")
    void getSharedWithMe_NullUser() {
        SharedWithMeResponseDto response = permissionService.getSharedWithMe(null);
        assertThat(response).isNotNull();
        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Should return shared folders and documents for valid active personal permissions")
    void getSharedWithMe_Success() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity charlie = UserEntity.builder()
                .id("user-charlie")
                .email("charlie@example.com")
                .build();

        FolderEntity sharedFolder = FolderEntity.builder()
                .id(100L)
                .name("Shared Marketing Assets")
                .build();

        DocumentEntity sharedDocument = DocumentEntity.builder()
                .id(200L)
                .fileName("QuarterlyReport.pdf")
                .fileHash("hash-pdf-123")
                .status(DocumentStatus.ACTIVE)
                .build();

        // 1. Direct USER share
        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(1L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(sharedFolder)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .createdAt(Instant.now())
                .note("Shared folder note context")
                .build();

        // 2. Legacy personal share
        PermissionEntity docPerm = PermissionEntity.builder()
                .id(2L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(null)
                .targetGroup(null)
                .targetOrgCode(null)
                .document(sharedDocument)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // 3. Organization permission (should not appear)
        PermissionEntity orgPerm = PermissionEntity.builder()
                .id(3L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode("ORG1")
                .document(sharedDocument)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com") // even if email is accidentally set
                .revoked(false)
                .build();

        // 4. GROUP/team permission (should not appear)
        PermissionEntity groupPerm = PermissionEntity.builder()
                .id(4L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .targetGroup(GroupEntity.builder().id(99L).build())
                .document(sharedDocument)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(folderPerm, docPerm, orgPerm, groupPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedDocuments()).hasSize(1);

        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getResourceType()).isEqualTo("FOLDER");
        assertThat(folderDto.getResourceId()).isEqualTo(100L);
        assertThat(folderDto.getName()).isEqualTo("Shared Marketing Assets");
        assertThat(folderDto.getGrantedByUserEmail()).isEqualTo("charlie@example.com");
        assertThat(folderDto.getNote()).isEqualTo("Shared folder note context");

        SharedItemDto docDto = response.getSharedDocuments().get(0);
        assertThat(docDto.getResourceType()).isEqualTo("DOCUMENT");
        assertThat(docDto.getResourceId()).isEqualTo(200L);
        assertThat(docDto.getName()).isEqualTo("QuarterlyReport.pdf");
        assertThat(docDto.getFileHash()).isEqualTo("hash-pdf-123");
    }

    @Test
    @DisplayName("Should filter out revoked, expired, EXCLUDE, self-granted, and unrelated permissions")
    void getSharedWithMe_Filtering() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity charlie = UserEntity.builder()
                .id("user-charlie")
                .email("charlie@example.com")
                .build();

        DocumentEntity validDoc = DocumentEntity.builder()
                .id(300L)
                .fileName("ValidDoc.docx")
                .fileHash("hash-valid")
                .status(DocumentStatus.ACTIVE)
                .build();

        // Revoked permission
        PermissionEntity revokedPerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(true)
                .build();

        // Suspended permission
        PermissionEntity suspendedPerm = PermissionEntity.builder()
                .id(16L)
                .permissionType(PermissionType.ALLOW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .suspended(true)
                .build();

        // Exclude permission
        PermissionEntity excludePerm = PermissionEntity.builder()
                .id(11L)
                .permissionType(PermissionType.EXCLUDE)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .build();

        // Expired permission
        PermissionEntity expiredPerm = PermissionEntity.builder()
                .id(12L)
                .permissionType(PermissionType.ALLOW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .expiresAt(Instant.now().minusSeconds(3600))
                .revoked(false)
                .build();

        // Self-granted permission
        PermissionEntity selfGrantedPerm = PermissionEntity.builder()
                .id(13L)
                .permissionType(PermissionType.ALLOW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(currentUser)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .build();

        // Valid active permission
        PermissionEntity validPerm = PermissionEntity.builder()
                .id(14L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // Another user's permission (filter safety check)
        PermissionEntity unrelatedUserPerm = PermissionEntity.builder()
                .id(15L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("dave@example.com")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // User organization member share (scoped to org, not personal Shared With Me)
        PermissionEntity userOrgMemberPerm = PermissionEntity.builder()
                .id(17L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetOrgCode("ACME_CORP")
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // Share link permission (bearer token, MUST NOT appear in Shared With Me)
        PermissionEntity shareLinkPerm = PermissionEntity.builder()
                .id(18L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(validDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken("share-token-xyz-123")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(revokedPerm, suspendedPerm, excludePerm, expiredPerm, selfGrantedPerm, validPerm, unrelatedUserPerm, userOrgMemberPerm, shareLinkPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).hasSize(1);
        assertThat(response.getSharedDocuments().get(0).getPermissionId()).isEqualTo(14L);
    }

    @Test
    @DisplayName("Phase 4B.2.2: Should exclude share-link permissions for folders and documents while keeping direct personal shares")
    void getSharedWithMe_ShouldExcludeShareLinksForFoldersAndDocuments() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity charlie = UserEntity.builder()
                .id("user-charlie")
                .email("charlie@example.com")
                .build();

        FolderEntity sharedFolder = FolderEntity.builder().id(100L).name("Direct Folder").build();
        FolderEntity shareLinkFolder = FolderEntity.builder().id(101L).name("ShareLink Folder").build();
        DocumentEntity sharedDoc = DocumentEntity.builder().id(200L).fileName("DirectDoc.pdf").fileHash("h1").status(DocumentStatus.ACTIVE).build();
        DocumentEntity shareLinkDoc = DocumentEntity.builder().id(201L).fileName("ShareLinkDoc.pdf").fileHash("h2").status(DocumentStatus.ACTIVE).build();

        // 1. Direct personal folder share (shareLinkToken == null) -> INCLUDED
        PermissionEntity directFolderPerm = PermissionEntity.builder()
                .id(1L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(sharedFolder)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken(null)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // 2. Share-link folder permission with target email (shareLinkToken != null) -> EXCLUDED
        PermissionEntity shareLinkFolderPerm = PermissionEntity.builder()
                .id(2L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(shareLinkFolder)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken("token-folder-abc")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // 3. Direct personal document share (shareLinkToken == null) -> INCLUDED
        PermissionEntity directDocPerm = PermissionEntity.builder()
                .id(3L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(sharedDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken(null)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // 4. Share-link document permission with target email (shareLinkToken != null) -> EXCLUDED
        PermissionEntity shareLinkDocPerm = PermissionEntity.builder()
                .id(4L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(shareLinkDoc)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken("token-doc-def")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(directFolderPerm, shareLinkFolderPerm, directDocPerm, shareLinkDocPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        // Only direct folder share is included
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(1L);
        assertThat(response.getSharedFolders().get(0).getResourceId()).isEqualTo(100L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Direct Folder");

        // Only direct document share is included
        assertThat(response.getSharedDocuments()).hasSize(1);
        assertThat(response.getSharedDocuments().get(0).getPermissionId()).isEqualTo(3L);
        assertThat(response.getSharedDocuments().get(0).getResourceId()).isEqualTo(200L);
        assertThat(response.getSharedDocuments().get(0).getName()).isEqualTo("DirectDoc.pdf");
    }

    @Test
    @DisplayName("Phase 4B.2.2: Should verify populated vs null/blank shareLinkToken handling in Shared With Me")
    void getSharedWithMe_ShareLinkTokenDirectShareVariations() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity charlie = UserEntity.builder()
                .id("user-charlie")
                .email("charlie@example.com")
                .build();

        DocumentEntity doc1 = DocumentEntity.builder().id(301L).fileName("DocPopulated.pdf").fileHash("h301").status(DocumentStatus.ACTIVE).build();
        DocumentEntity doc2 = DocumentEntity.builder().id(302L).fileName("DocNull.pdf").fileHash("h302").status(DocumentStatus.ACTIVE).build();
        DocumentEntity doc3 = DocumentEntity.builder().id(303L).fileName("DocLegacyNull.pdf").fileHash("h303").status(DocumentStatus.ACTIVE).build();

        // 1. Populated shareLinkToken -> EXCLUDED
        PermissionEntity permPopulatedToken = PermissionEntity.builder()
                .id(21L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(doc1)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken("550e8400e29b41d4a716446655440000")
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // 2. Null shareLinkToken (USER_PERSONAL) -> INCLUDED
        PermissionEntity permNullToken = PermissionEntity.builder()
                .id(22L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(doc2)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken(null)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        // 3. Null shareLinkToken with legacy personal share (recipientType == null, org == null, group == null) -> INCLUDED
        PermissionEntity permLegacyNull = PermissionEntity.builder()
                .id(23L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(null)
                .targetOrgCode(null)
                .targetGroup(null)
                .document(doc3)
                .grantedBy(charlie)
                .targetUserEmail("bob@example.com")
                .shareLinkToken(null)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(permPopulatedToken, permNullToken, permLegacyNull));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).hasSize(2);
        assertThat(response.getSharedDocuments())
                .extracting(SharedItemDto::getPermissionId)
                .containsExactly(22L, 23L);
    }

    // =========================================================================
    // Phase 4B.2.3 — Shared With Me Metadata Enrichment Tests
    // =========================================================================

    @Test
    @DisplayName("4B.2.3-A: Personal direct share should have sourceType=PERSONAL, null org fields, and external=false")
    void getSharedWithMe_Metadata_PersonalDirectShare() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity alice = UserEntity.builder()
                .id("user-alice")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .build();

        FolderEntity personalFolder = FolderEntity.builder()
                .id(101L)
                .name("Alice Personal Notes")
                .ownerOrg(null)
                .build();

        DocumentEntity personalDoc = DocumentEntity.builder()
                .id(201L)
                .fileName("AlicePersonalDoc.pdf")
                .fileHash("hash-personal-201")
                .ownerOrg(null)
                .fileSize(524288L)
                .contentType("application/pdf")
                .status(DocumentStatus.ACTIVE)
                .build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(11L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(personalFolder)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .createdAt(Instant.now())
                .revoked(false)
                .build();

        PermissionEntity docPerm = PermissionEntity.builder()
                .id(12L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(personalDoc)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .createdAt(Instant.now())
                .revoked(false)
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(folderPerm, docPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedDocuments()).hasSize(1);

        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getSourceType()).isEqualTo("PERSONAL");
        assertThat(folderDto.getOwnerOrgCode()).isNull();
        assertThat(folderDto.getOwnerOrgName()).isNull();
        assertThat(folderDto.getExternal()).isFalse();
        assertThat(folderDto.getGrantedByName()).isEqualTo("Alice Smith");
        assertThat(folderDto.getFileSize()).isNull();
        assertThat(folderDto.getContentType()).isNull();

        SharedItemDto docDto = response.getSharedDocuments().get(0);
        assertThat(docDto.getSourceType()).isEqualTo("PERSONAL");
        assertThat(docDto.getOwnerOrgCode()).isNull();
        assertThat(docDto.getOwnerOrgName()).isNull();
        assertThat(docDto.getExternal()).isFalse();
        assertThat(docDto.getGrantedByName()).isEqualTo("Alice Smith");
        assertThat(docDto.getFileSize()).isEqualTo(524288L);
        assertThat(docDto.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @DisplayName("4B.2.3-B: External direct organization share should have sourceType=ORGANIZATION, populated org metadata, and external=true")
    void getSharedWithMe_Metadata_ExternalOrganizationDirectShare() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        OrganizationEntity acmeOrg = OrganizationEntity.builder()
                .orgCode("ORG_ACME")
                .name("Acme Corporation")
                .build();

        UserEntity acmeAdmin = UserEntity.builder()
                .id("user-acme-admin")
                .email("admin@acme.com")
                .firstName("Acme")
                .lastName("Admin")
                .build();

        FolderEntity orgFolder = FolderEntity.builder()
                .id(102L)
                .name("External Collaboration Folder")
                .ownerOrg(acmeOrg)
                .build();

        DocumentEntity orgDoc = DocumentEntity.builder()
                .id(202L)
                .fileName("ExternalProposal.docx")
                .fileHash("hash-org-202")
                .ownerOrg(acmeOrg)
                .fileSize(1048576L)
                .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .status(DocumentStatus.ACTIVE)
                .build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(13L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(orgFolder)
                .grantedBy(acmeAdmin)
                .targetUserEmail("bob@example.com")
                .createdAt(Instant.now())
                .revoked(false)
                .build();

        PermissionEntity docPerm = PermissionEntity.builder()
                .id(14L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(orgDoc)
                .grantedBy(acmeAdmin)
                .targetUserEmail("bob@example.com")
                .createdAt(Instant.now())
                .revoked(false)
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(folderPerm, docPerm));

        // bob is NOT a member of ORG_ACME (unstubbed bulk lookup returns empty list, meaning not a member)

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getSourceType()).isEqualTo("ORGANIZATION");
        assertThat(folderDto.getOwnerOrgCode()).isEqualTo("ORG_ACME");
        assertThat(folderDto.getOwnerOrgName()).isEqualTo("Acme Corporation");
        assertThat(folderDto.getExternal()).isTrue();

        SharedItemDto docDto = response.getSharedDocuments().get(0);
        assertThat(docDto.getSourceType()).isEqualTo("ORGANIZATION");
        assertThat(docDto.getOwnerOrgCode()).isEqualTo("ORG_ACME");
        assertThat(docDto.getOwnerOrgName()).isEqualTo("Acme Corporation");
        assertThat(docDto.getExternal()).isTrue();
    }

    @Test
    @DisplayName("4B.2.3-C: Internal organization direct share should have sourceType=ORGANIZATION, populated org metadata, and external=false")
    void getSharedWithMe_Metadata_InternalOrganizationDirectShare() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        OrganizationEntity internalOrg = OrganizationEntity.builder()
                .orgCode("ORG_INTERNAL")
                .name("Internal Corp")
                .build();

        UserEntity internalManager = UserEntity.builder()
                .id("user-internal-mgr")
                .email("mgr@internal.com")
                .firstName("Internal")
                .lastName("Manager")
                .build();

        FolderEntity orgFolder = FolderEntity.builder()
                .id(103L)
                .name("Internal Strategy")
                .ownerOrg(internalOrg)
                .build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(15L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(orgFolder)
                .grantedBy(internalManager)
                .targetUserEmail("bob@example.com")
                .createdAt(Instant.now())
                .revoked(false)
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(folderPerm));

        // bob IS an active member of ORG_INTERNAL
        UserOrganizationEntity uo = UserOrganizationEntity.builder()
                .user(currentUser)
                .organization(internalOrg)
                .status(MembershipStatus.ACTIVE)
                .build();
        when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCodeInAndStatus(eq("user-bob"), anyCollection(), eq(MembershipStatus.ACTIVE)))
                .thenReturn(List.of(uo));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response).isNotNull();
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getSourceType()).isEqualTo("ORGANIZATION");
        assertThat(folderDto.getOwnerOrgCode()).isEqualTo("ORG_INTERNAL");
        assertThat(folderDto.getOwnerOrgName()).isEqualTo("Internal Corp");
        assertThat(folderDto.getExternal()).isFalse();
    }

    @Test
    @DisplayName("4B.2.3-D: Grantor display name resolution with first+last, single name, and email fallback")
    void getSharedWithMe_Metadata_GrantorDisplayNameResolution() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity grantorWithBoth = UserEntity.builder()
                .id("u1").email("firstlast@example.com").firstName("John").lastName("Doe").build();
        UserEntity grantorFirstOnly = UserEntity.builder()
                .id("u2").email("firstonly@example.com").firstName("SingleName").lastName(null).build();
        UserEntity grantorNoName = UserEntity.builder()
                .id("u3").email("noname@example.com").firstName(null).lastName(null).build();
        UserEntity grantorBlankName = UserEntity.builder()
                .id("u4").email("blankname@example.com").firstName("   ").lastName("  ").build();

        DocumentEntity d1 = DocumentEntity.builder().id(201L).fileName("d1.pdf").status(DocumentStatus.ACTIVE).build();
        DocumentEntity d2 = DocumentEntity.builder().id(202L).fileName("d2.pdf").status(DocumentStatus.ACTIVE).build();
        DocumentEntity d3 = DocumentEntity.builder().id(203L).fileName("d3.pdf").status(DocumentStatus.ACTIVE).build();
        DocumentEntity d4 = DocumentEntity.builder().id(204L).fileName("d4.pdf").status(DocumentStatus.ACTIVE).build();

        PermissionEntity p1 = PermissionEntity.builder().id(1L).recipientType(RecipientType.USER_PERSONAL)
                .document(d1).grantedBy(grantorWithBoth).targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).build();
        PermissionEntity p2 = PermissionEntity.builder().id(2L).recipientType(RecipientType.USER_PERSONAL)
                .document(d2).grantedBy(grantorFirstOnly).targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).build();
        PermissionEntity p3 = PermissionEntity.builder().id(3L).recipientType(RecipientType.USER_PERSONAL)
                .document(d3).grantedBy(grantorNoName).targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).build();
        PermissionEntity p4 = PermissionEntity.builder().id(4L).recipientType(RecipientType.USER_PERSONAL)
                .document(d4).grantedBy(grantorBlankName).targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(p1, p2, p3, p4));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedDocuments()).hasSize(4);
        assertThat(response.getSharedDocuments().get(0).getGrantedByName()).isEqualTo("John Doe");
        assertThat(response.getSharedDocuments().get(1).getGrantedByName()).isEqualTo("SingleName");
        assertThat(response.getSharedDocuments().get(2).getGrantedByName()).isEqualTo("noname@example.com");
        assertThat(response.getSharedDocuments().get(3).getGrantedByName()).isEqualTo("blankname@example.com");
    }

    @Test
    @DisplayName("4B.2.3-E: Workspace metadata resolution from permission.workspaceId or rootFolder lookup")
    void getSharedWithMe_Metadata_WorkspaceDerivation() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity alice = UserEntity.builder()
                .id("user-alice").email("alice@example.com").build();

        // 1. Permission with explicit workspaceId = 55L
        FolderEntity folderWithWsPerm = FolderEntity.builder().id(104L).name("Folder In WS").build();
        PermissionEntity permExplicitWs = PermissionEntity.builder()
                .id(16L)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(folderWithWsPerm)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .workspaceId(55L)
                .permissionType(PermissionType.ALLOW)
                .build();

        ProjectWorkspaceEntity ws55 = ProjectWorkspaceEntity.builder()
                .id(55L)
                .name("Marketing Workspace")
                .build();
        when(projectWorkspaceRepository.findAllById(anyCollection())).thenReturn(List.of(ws55));

        // 2. Permission with workspaceId=null, but folder is root folder of workspace 77L
        FolderEntity rootFolderWs = FolderEntity.builder().id(105L).name("Root WS Folder").build();
        PermissionEntity permRootWs = PermissionEntity.builder()
                .id(17L)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(rootFolderWs)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .workspaceId(null)
                .permissionType(PermissionType.ALLOW)
                .build();

        ProjectWorkspaceEntity ws77 = ProjectWorkspaceEntity.builder()
                .id(77L)
                .name("Engineering Root WS")
                .rootFolder(rootFolderWs)
                .build();
        when(projectWorkspaceRepository.findByRootFolder_IdIn(anyCollection())).thenReturn(List.of(ws77));

        // 3. Permission with workspaceId=null and folder not a root folder
        FolderEntity normalFolder = FolderEntity.builder().id(106L).name("Normal Folder").build();
        PermissionEntity permNoWs = PermissionEntity.builder()
                .id(18L)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(normalFolder)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .workspaceId(null)
                .permissionType(PermissionType.ALLOW)
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(permExplicitWs, permRootWs, permNoWs));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).hasSize(3);
        // Explicit workspaceId
        assertThat(response.getSharedFolders().get(0).getWorkspaceId()).isEqualTo(55L);
        assertThat(response.getSharedFolders().get(0).getWorkspaceName()).isEqualTo("Marketing Workspace");

        // Derived from root folder
        assertThat(response.getSharedFolders().get(1).getWorkspaceId()).isEqualTo(77L);
        assertThat(response.getSharedFolders().get(1).getWorkspaceName()).isEqualTo("Engineering Root WS");

        // No workspace
        assertThat(response.getSharedFolders().get(2).getWorkspaceId()).isNull();
        assertThat(response.getSharedFolders().get(2).getWorkspaceName()).isNull();
    }

    @Test
    @DisplayName("4B.2.3-H: Privacy and safety guarantees — no parent paths, no share tokens, no sensitive storage info")
    void getSharedWithMe_PrivacyAndSafetyGuarantees() {
        UserEntity currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .build();

        UserEntity alice = UserEntity.builder()
                .id("user-alice").email("alice@example.com").build();

        FolderEntity secretParent = FolderEntity.builder()
                .id(999L)
                .name("Secret Executive Confidential Parent")
                .build();

        FolderEntity sharedSubfolder = FolderEntity.builder()
                .id(107L)
                .name("Public Subfolder")
                .parentFolder(secretParent)
                .build();

        DocumentEntity sharedDoc = DocumentEntity.builder()
                .id(207L)
                .fileName("SharedDoc.pdf")
                .fileHash("hash-safe-doc")
                .s3Bucket("secret-internal-s3-bucket")
                .objectKey("tenants/org1/secret/path/SharedDoc.pdf")
                .folder(secretParent)
                .fileSize(2048L)
                .contentType("application/pdf")
                .status(DocumentStatus.ACTIVE)
                .build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(19L)
                .recipientType(RecipientType.USER_PERSONAL)
                .folder(sharedSubfolder)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .build();

        PermissionEntity docPerm = PermissionEntity.builder()
                .id(20L)
                .recipientType(RecipientType.USER_PERSONAL)
                .document(sharedDoc)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .permissionType(PermissionType.ALLOW)
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(folderPerm, docPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getName()).isEqualTo("Public Subfolder");
        // Crucial privacy rule: shared folder does NOT disclose parent name
        assertThat(folderDto.getName()).doesNotContain("Secret");

        SharedItemDto docDto = response.getSharedDocuments().get(0);
        assertThat(docDto.getName()).isEqualTo("SharedDoc.pdf");
        assertThat(docDto.getFileHash()).isEqualTo("hash-safe-doc");
        assertThat(docDto.getFileSize()).isEqualTo(2048L);
        assertThat(docDto.getContentType()).isEqualTo("application/pdf");
        // No storage leakage in DTO
        assertThat(docDto.getName()).doesNotContain("secret-internal-s3-bucket");
    }

    @Test
    @DisplayName("SharedWithMe: ACTIVE doc included, PENDING_UPLOAD and FAILED excluded, folders unaffected")
    void getSharedWithMe_documentStatusFiltering() {
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").build();
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();

        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Active Shared Folder").build();
        DocumentEntity activeDoc = DocumentEntity.builder().id(201L).fileName("Active.pdf").status(DocumentStatus.ACTIVE).build();
        DocumentEntity pendingDoc = DocumentEntity.builder().id(202L).fileName("Pending.pdf").status(DocumentStatus.PENDING_UPLOAD).build();
        DocumentEntity failedDoc = DocumentEntity.builder().id(203L).fileName("Failed.pdf").status(DocumentStatus.FAILED).build();

        PermissionEntity folderPerm = PermissionEntity.builder()
                .id(1L).recipientType(RecipientType.USER_PERSONAL).folder(sharedFolder).grantedBy(alice)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        PermissionEntity activeDocPerm = PermissionEntity.builder()
                .id(2L).recipientType(RecipientType.USER_PERSONAL).document(activeDoc).grantedBy(alice)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        PermissionEntity pendingDocPerm = PermissionEntity.builder()
                .id(3L).recipientType(RecipientType.USER_PERSONAL).document(pendingDoc).grantedBy(alice)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        PermissionEntity failedDocPerm = PermissionEntity.builder()
                .id(4L).recipientType(RecipientType.USER_PERSONAL).document(failedDoc).grantedBy(alice)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW).createdAt(Instant.now()).build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(folderPerm, activeDocPerm, pendingDocPerm, failedDocPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        // Folders are unaffected
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(1L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Active Shared Folder");

        // Only ACTIVE document is included; PENDING_UPLOAD and FAILED are strictly excluded
        assertThat(response.getSharedDocuments()).hasSize(1);
        assertThat(response.getSharedDocuments().get(0).getPermissionId()).isEqualTo(2L);
        assertThat(response.getSharedDocuments().get(0).getName()).isEqualTo("Active.pdf");
        assertThat(response.getSharedDocuments()).noneMatch(d -> d.getPermissionId() == 3L || d.getPermissionId() == 4L);
    }

    @Test
    @DisplayName("Should grant folder share with exclusions atomically creating ALLOW root and EXCLUDE permissions")
    void grantFolderShareWithExclusions_Success() {
        FolderEntity rootFolder = FolderEntity.builder().id(100L).name("Root Folder").build();
        DocumentEntity excludedDoc = DocumentEntity.builder().id(200L).fileHash("hashEx1").build();
        FolderEntity excludedSubfolder = FolderEntity.builder().id(101L).name("Excluded Sub").build();

        BulkShareWithExclusionsDto bulkDto = BulkShareWithExclusionsDto.builder()
                .folderId(100L)
                .targetUserEmail("alice@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionLevel(PermissionLevel.VIEW)
                .excludedDocumentIds(List.of(200L))
                .excludedSubfolderIds(List.of(101L))
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(rootFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, rootFolder)).thenReturn(true);
        when(documentRepository.findById(200L)).thenReturn(Optional.of(excludedDoc));
        when(documentRepository.findByFileHash("hashEx1")).thenReturn(List.of(excludedDoc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, excludedDoc)).thenReturn(true);
        when(folderRepository.findById(101L)).thenReturn(Optional.of(excludedSubfolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, excludedSubfolder)).thenReturn(true);

        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
            PermissionEntity p = inv.getArgument(0);
            p.setId(99L);
            return p;
        });

        List<PermissionResponseDto> responses = permissionService.grantFolderShareWithExclusions(bulkDto, "user-admin");

        assertThat(responses).hasSize(3); // 1 ALLOW root, 1 EXCLUDE doc, 1 EXCLUDE subfolder
        assertThat(responses.get(0).getPermissionType()).isEqualTo(PermissionType.ALLOW);
        assertThat(responses.get(1).getPermissionType()).isEqualTo(PermissionType.EXCLUDE);
        assertThat(responses.get(2).getPermissionType()).isEqualTo(PermissionType.EXCLUDE);

        verify(permissionRepository, times(3)).save(any(PermissionEntity.class));
        verify(cacheService, times(3)).evictUserPermissions("alice@example.com");
    }

    @Test
    @DisplayName("Should successfully grant permissions to selected organization members")
    void grantSelectedMembers_Success() {
        BulkSelectedMemberShareDto.MemberShareItemDto member1 = BulkSelectedMemberShareDto.MemberShareItemDto.builder()
                .targetUserEmail("alice@example.com")
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .build();
        BulkSelectedMemberShareDto.MemberShareItemDto member2 = BulkSelectedMemberShareDto.MemberShareItemDto.builder()
                .targetUserEmail("bob@example.com")
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .permissionType(PermissionType.ALLOW)
                .build();

        BulkSelectedMemberShareDto bulkDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode("ORG100")
                .documentFileHash("hash999")
                .selectedMembers(List.of(member1, member2))
                .build();

        when(organizationRepository.existsByOrgCode("ORG100")).thenReturn(true);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("alice@example.com", "ORG100", MembershipStatus.ACTIVE)).thenReturn(true);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("bob@example.com", "ORG100", MembershipStatus.ACTIVE)).thenReturn(true);
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
            PermissionEntity p = inv.getArgument(0);
            p.setId(101L);
            return p;
        });

        List<PermissionResponseDto> responses = permissionService.grantSelectedMembers(bulkDto, "user-admin");

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getTargetUserEmail()).isEqualTo("alice@example.com");
        assertThat(responses.get(1).getTargetUserEmail()).isEqualTo("bob@example.com");

        verify(permissionRepository, times(2)).save(any(PermissionEntity.class));
        verify(cacheService).evictUserPermissions("alice@example.com");
        verify(cacheService).evictUserPermissions("bob@example.com");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when target org does not exist for selected members share")
    void grantSelectedMembers_OrgNotFound() {
        BulkSelectedMemberShareDto bulkDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode("INVALID_ORG")
                .selectedMembers(List.of(BulkSelectedMemberShareDto.MemberShareItemDto.builder().targetUserEmail("alice@example.com").permissionLevel(PermissionLevel.VIEW).build()))
                .build();

        when(organizationRepository.existsByOrgCode("INVALID_ORG")).thenReturn(false);

        assertThatThrownBy(() -> permissionService.grantSelectedMembers(bulkDto, "user-admin"))
                .isInstanceOf(com.subject77.docshare.exceptions.ResourceNotFoundException.class)
                .hasMessageContaining("Organization not found with orgCode: INVALID_ORG");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user is not a member of target org")
    void grantSelectedMembers_UserNotMemberOfOrg() {
        BulkSelectedMemberShareDto.MemberShareItemDto member = BulkSelectedMemberShareDto.MemberShareItemDto.builder()
                .targetUserEmail("charlie@example.com")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        BulkSelectedMemberShareDto bulkDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode("ORG100")
                .selectedMembers(List.of(member))
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(organizationRepository.existsByOrgCode("ORG100")).thenReturn(true);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("charlie@example.com", "ORG100", MembershipStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.grantSelectedMembers(bulkDto, "user-admin"))
                .isInstanceOf(com.subject77.docshare.exceptions.ResourceNotFoundException.class)
                .hasMessageContaining("User with email charlie@example.com is not an ACTIVE member of organization ORG100");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when orgCode is missing or members list is empty")
    void grantSelectedMembers_InvalidInput() {
        BulkSelectedMemberShareDto nullOrgDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode(null)
                .selectedMembers(List.of(BulkSelectedMemberShareDto.MemberShareItemDto.builder().targetUserEmail("alice@example.com").permissionLevel(PermissionLevel.VIEW).build()))
                .build();

        assertThatThrownBy(() -> permissionService.grantSelectedMembers(nullOrgDto, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target organization code is required");

        BulkSelectedMemberShareDto emptyMembersDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode("ORG100")
                .selectedMembers(List.of())
                .build();

        assertThatThrownBy(() -> permissionService.grantSelectedMembers(emptyMembersDto, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one selected member is required");
    }

    @Test
    @DisplayName("Should trigger notifications when permission is granted, updated, and revoked")
    void permissionNotifications_Success() {
        UserEntity targetUser = UserEntity.builder().id("user-target").email("alice@example.com").build();
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(targetUser));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(i -> {
            PermissionEntity p = i.getArgument(0);
            if (p.getId() == null) p.setId(100L);
            return p;
        });

        // Grant
        PermissionResponseDto granted = permissionService.grantPermission(requestDto, "user-admin");
        assertThat(granted).isNotNull();
        verify(notificationService).createNotification(
                eq(targetUser), eq(grantor), eq(NotificationType.PERMISSION_GRANTED),
                anyString(), anyString(), eq(NotificationSourceType.PERMISSION), eq("100"),
                eq(NotificationCategory.SHARING), eq(NotificationPriority.MEDIUM), anyString()
        );

        // Update
        PermissionEntity permEntity = PermissionEntity.builder()
                .id(100L)
                .grantedBy(grantor)
                .targetUserEmail("alice@example.com")
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .validFrom(Instant.now())
                .revoked(false)
                .build();
        when(permissionRepository.findById(100L)).thenReturn(Optional.of(permEntity));
        PermissionUpdateRequestDto updateDto = PermissionUpdateRequestDto.builder().permissionLevel(PermissionLevel.EDIT).build();
        permissionService.updatePermission(100L, updateDto, "user-admin");
        verify(notificationService).createNotification(
                eq(targetUser), eq(grantor), eq(NotificationType.PERMISSION_UPDATED),
                anyString(), anyString(), eq(NotificationSourceType.PERMISSION), eq("100"),
                eq(NotificationCategory.SHARING), eq(NotificationPriority.MEDIUM), anyString()
        );

        // Revoke
        permissionService.revokePermission(100L, "user-admin");
        verify(notificationService).createNotification(
                eq(targetUser), eq(grantor), eq(NotificationType.PERMISSION_REVOKED),
                anyString(), anyString(), eq(NotificationSourceType.PERMISSION), eq("100"),
                eq(NotificationCategory.SHARING), eq(NotificationPriority.MEDIUM), anyString()
        );
    }

    // ==========================================
    // STEP 13E: Manage Access Summary Tests (15 tests)
    // ==========================================

    @Test
    @DisplayName("Should throw AccessDeniedException when currentUser is null in getManageAccessSummary")
    void getManageAccessSummary_NullUser_ThrowsAccessDenied() {
        assertThatThrownBy(() -> permissionService.getManageAccessSummary(1L, "DOCUMENT", null))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("User must be authenticated");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when resourceType is invalid in getManageAccessSummary")
    void getManageAccessSummary_InvalidResourceType_ThrowsIllegalArgument() {
        assertThatThrownBy(() -> permissionService.getManageAccessSummary(1L, "INVALID", grantor))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid resourceType");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when document is not found in getManageAccessSummary")
    void getManageAccessSummary_DocumentNotFound_ThrowsResourceNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getManageAccessSummary(999L, "DOCUMENT", grantor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user cannot administer document permissions")
    void getManageAccessSummary_Document_AccessDenied() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Secret.pdf").build();
        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("User does not have authority");
    }

    @Test
    @DisplayName("Should return manage access summary for document with owner, direct, and inherited permissions")
    void getManageAccessSummary_Document_SuccessWithOwnerDirectAndInherited() {
        FolderEntity parentFolder = FolderEntity.builder().id(50L).name("Parent Folder").build();
        DocumentEntity doc = DocumentEntity.builder()
                .id(100L)
                .fileName("Doc.pdf")
                .uploadedBy(grantor)
                .folder(parentFolder)
                .build();

        PermissionEntity directPerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("alice@example.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        PermissionEntity inheritedPerm = PermissionEntity.builder()
                .id(20L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .targetUserEmail("bob@example.com")
                .folder(parentFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(directPerm));
        when(permissionRepository.findByFolder_Id(50L)).thenReturn(List.of(inheritedPerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        assertThat(summary).isNotNull();
        assertThat(summary.getResourceId()).isEqualTo(100L);
        assertThat(summary.getResourceType()).isEqualTo("DOCUMENT");
        assertThat(summary.getOwnerUserId()).isEqualTo("user-admin");
        assertThat(summary.getOwnerEmail()).isEqualTo("admin@docshare.com");
        assertThat(summary.getEntries()).hasSize(3); // 1 OWNER, 1 DIRECT, 1 INHERITED

        EffectiveAccessEntryDto ownerEntry = summary.getEntries().get(0);
        assertThat(ownerEntry.getSource()).isEqualTo("OWNER");
        assertThat(ownerEntry.getEmail()).isEqualTo("admin@docshare.com");

        EffectiveAccessEntryDto directEntry = summary.getEntries().get(1);
        assertThat(directEntry.getSource()).isEqualTo("DIRECT");
        assertThat(directEntry.getEmail()).isEqualTo("alice@example.com");

        EffectiveAccessEntryDto inheritedEntry = summary.getEntries().get(2);
        assertThat(inheritedEntry.getSource()).isEqualTo("INHERITED");
        assertThat(inheritedEntry.getEmail()).isEqualTo("bob@example.com");
        assertThat(inheritedEntry.getInheritedFromResourceId()).isEqualTo(50L);
        assertThat(inheritedEntry.getInheritedFromResourceName()).isEqualTo("Parent Folder");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when folder is not found in getManageAccessSummary")
    void getManageAccessSummary_FolderNotFound_ThrowsResourceNotFound() {
        when(folderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getManageAccessSummary(999L, "FOLDER", grantor))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Folder not found");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user cannot administer folder permissions")
    void getManageAccessSummary_Folder_AccessDenied() {
        FolderEntity folder = FolderEntity.builder().id(50L).name("Restricted").build();
        when(folderRepository.findById(50L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, folder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getManageAccessSummary(50L, "FOLDER", grantor))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("User does not have authority");
    }

    @Test
    @DisplayName("Should return manage access summary for folder with owner, direct, and inherited permissions")
    void getManageAccessSummary_Folder_SuccessWithOwnerDirectAndInherited() {
        FolderEntity rootFolder = FolderEntity.builder().id(10L).name("Root Folder").build();
        FolderEntity childFolder = FolderEntity.builder()
                .id(50L)
                .name("Child Folder")
                .createdBy(grantor)
                .parentFolder(rootFolder)
                .build();

        PermissionEntity directPerm = PermissionEntity.builder()
                .id(101L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("alice@example.com")
                .folder(childFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        PermissionEntity inheritedPerm = PermissionEntity.builder()
                .id(102L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("charlie@example.com")
                .folder(rootFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(folderRepository.findById(50L)).thenReturn(Optional.of(childFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, childFolder)).thenReturn(true);
        when(permissionRepository.findByFolder_Id(50L)).thenReturn(List.of(directPerm));
        when(permissionRepository.findByFolder_Id(10L)).thenReturn(List.of(inheritedPerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(50L, "FOLDER", grantor);

        assertThat(summary).isNotNull();
        assertThat(summary.getResourceId()).isEqualTo(50L);
        assertThat(summary.getResourceType()).isEqualTo("FOLDER");
        assertThat(summary.getEntries()).hasSize(3); // OWNER, DIRECT, INHERITED
        assertThat(summary.getEntries().get(0).getSource()).isEqualTo("OWNER");
        assertThat(summary.getEntries().get(1).getSource()).isEqualTo("DIRECT");
        assertThat(summary.getEntries().get(2).getSource()).isEqualTo("INHERITED");
        assertThat(summary.getEntries().get(2).getInheritedFromResourceId()).isEqualTo(10L);
        assertThat(summary.getEntries().get(2).getInheritedFromResourceName()).isEqualTo("Root Folder");
    }

    @Test
    @DisplayName("Should correctly tag status as REVOKED, EXPIRED, or ACTIVE")
    void getManageAccessSummary_StatusTagging_RevokedAndExpired() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Test.doc").uploadedBy(grantor).build();

        PermissionEntity revokedPerm = PermissionEntity.builder()
                .id(1L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("user1@example.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(true)
                .build();

        PermissionEntity expiredPerm = PermissionEntity.builder()
                .id(2L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("user2@example.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .expiresAt(Instant.now().minusSeconds(3600))
                .build();

        PermissionEntity activePerm = PermissionEntity.builder()
                .id(3L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("user3@example.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(revokedPerm, expiredPerm, activePerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        assertThat(summary.getEntries()).hasSize(4); // OWNER + 3
        assertThat(summary.getEntries().get(1).getStatus()).isEqualTo("REVOKED");
        assertThat(summary.getEntries().get(2).getStatus()).isEqualTo("EXPIRED");
        assertThat(summary.getEntries().get(3).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("Should resolve TEAM source and principal info")
    void getManageAccessSummary_SourceResolution_Team() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Test.doc").uploadedBy(grantor).build();
        GroupEntity team = GroupEntity.builder().id(5L).name("Engineering").build();

        PermissionEntity teamPerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetGroup(team)
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(teamPerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        EffectiveAccessEntryDto teamEntry = summary.getEntries().get(1);
        assertThat(teamEntry.getPrincipalType()).isEqualTo("TEAM");
        assertThat(teamEntry.getPrincipalId()).isEqualTo("5");
        assertThat(teamEntry.getDisplayName()).isEqualTo("Engineering");
        assertThat(teamEntry.getSource()).isEqualTo("TEAM");
    }

    @Test
    @DisplayName("Should resolve ORGANIZATION source and principal info")
    void getManageAccessSummary_SourceResolution_Organization() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Test.doc").uploadedBy(grantor).build();
        OrganizationEntity targetOrg = OrganizationEntity.builder().orgCode("ORG_ACME").name("Acme Corp").build();

        PermissionEntity orgPerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetOrgCode("ORG_ACME")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(orgPerm));
        when(organizationRepository.findByOrgCode("ORG_ACME")).thenReturn(Optional.of(targetOrg));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        EffectiveAccessEntryDto orgEntry = summary.getEntries().get(1);
        assertThat(orgEntry.getPrincipalType()).isEqualTo("ORGANIZATION");
        assertThat(orgEntry.getPrincipalId()).isEqualTo("ORG_ACME");
        assertThat(orgEntry.getDisplayName()).isEqualTo("Acme Corp");
        assertThat(orgEntry.getSource()).isEqualTo("ORGANIZATION");
    }

    @Test
    @DisplayName("Should resolve COLLABORATION source and principal info")
    void getManageAccessSummary_SourceResolution_Collaboration() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Test.doc").uploadedBy(grantor).build();
        CollaborationEntity collab = CollaborationEntity.builder().id(500L).name("Joint Venture").build();

        PermissionEntity collabPerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .collaboration(collab)
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(collabPerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        EffectiveAccessEntryDto collabEntry = summary.getEntries().get(1);
        assertThat(collabEntry.getPrincipalType()).isEqualTo("COLLABORATION");
        assertThat(collabEntry.getPrincipalId()).isEqualTo("500");
        assertThat(collabEntry.getDisplayName()).isEqualTo("Joint Venture");
        assertThat(collabEntry.getSource()).isEqualTo("COLLABORATION");
        assertThat(collabEntry.isExternal()).isTrue();
    }

    @Test
    @DisplayName("Should resolve SHARE_LINK principal info")
    void getManageAccessSummary_SourceResolution_ShareLink() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Test.doc").uploadedBy(grantor).build();

        PermissionEntity shareLinkPerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .shareLinkToken("token-xyz-123")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(shareLinkPerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        EffectiveAccessEntryDto shareLinkEntry = summary.getEntries().get(1);
        assertThat(shareLinkEntry.getPrincipalType()).isEqualTo("SHARE_LINK");
        assertThat(shareLinkEntry.getPrincipalId()).isNull();
        assertThat(shareLinkEntry.getDisplayName()).isEqualTo("Share Link");
        assertThat(shareLinkEntry.isExternal()).isTrue();
    }

    @Test
    @DisplayName("Should resolve EXCLUDED source for exclusion permission entries")
    void getManageAccessSummary_SourceResolution_Excluded() {
        DocumentEntity doc = DocumentEntity.builder().id(100L).fileName("Test.doc").uploadedBy(grantor).build();

        PermissionEntity excludePerm = PermissionEntity.builder()
                .id(10L)
                .permissionType(PermissionType.EXCLUDE)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("excluded@example.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(excludePerm));

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        EffectiveAccessEntryDto excludeEntry = summary.getEntries().get(1);
        assertThat(excludeEntry.getPermissionType()).isEqualTo(PermissionType.EXCLUDE);
        assertThat(excludeEntry.getSource()).isEqualTo("EXCLUDED");
    }

    @Test
    @DisplayName("Should correctly identify external users based on organization code mismatch")
    void getManageAccessSummary_ExternalUserTagging() {
        OrganizationEntity homeOrg = OrganizationEntity.builder().orgCode("HOME_ORG").name("Home Org").build();
        OrganizationEntity otherOrg = OrganizationEntity.builder().orgCode("OTHER_ORG").name("Other Org").build();

        grantor.setOrganization(homeOrg);

        DocumentEntity doc = DocumentEntity.builder()
                .id(100L)
                .fileName("Test.doc")
                .uploadedBy(grantor)
                .ownerOrg(homeOrg)
                .build();

        UserEntity internalUser = UserEntity.builder()
                .id("u-internal")
                .email("internal@home.com")
                .firstName("Internal")
                .lastName("User")
                .organization(homeOrg)
                .build();

        UserEntity externalUser = UserEntity.builder()
                .id("u-external")
                .email("external@other.com")
                .firstName("External")
                .lastName("User")
                .organization(otherOrg)
                .build();

        PermissionEntity internalPerm = PermissionEntity.builder()
                .id(1L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("internal@home.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        PermissionEntity externalPerm = PermissionEntity.builder()
                .id(2L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("external@other.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        PermissionEntity unknownUserPerm = PermissionEntity.builder()
                .id(3L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("unknown@external.com")
                .document(doc)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(documentRepository.findById(100L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_Id(100L)).thenReturn(List.of(internalPerm, externalPerm, unknownUserPerm));
        when(userRepository.findByEmail("internal@home.com")).thenReturn(Optional.of(internalUser));
        when(userRepository.findByEmail("external@other.com")).thenReturn(Optional.of(externalUser));
        when(userRepository.findByEmail("unknown@external.com")).thenReturn(Optional.empty());

        ManageAccessSummaryDto summary = permissionService.getManageAccessSummary(100L, "DOCUMENT", grantor);

        assertThat(summary.getEntries()).hasSize(4); // OWNER + 3
        assertThat(summary.getEntries().get(1).isExternal()).isFalse();
        assertThat(summary.getEntries().get(2).isExternal()).isTrue();
        assertThat(summary.getEntries().get(3).isExternal()).isTrue();
    }

    @Test
    @DisplayName("Should update existing active group folder permission instead of creating duplicate entity on grant")
    void grantPermission_GroupFolder_Deduplication_Success() {
        OrganizationEntity testOrg = OrganizationEntity.builder().orgCode("ORG-TEST").name("Test Org").build();
        FolderEntity targetFolder = FolderEntity.builder().id(100L).name("Test Workspace").ownerOrg(testOrg).build();
        GroupEntity targetGroup = GroupEntity.builder().id(50L).name("Test Team").organization(testOrg).build();

        PermissionRequestDto groupGrantDto = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.GROUP)
                .targetGroupId(50L)
                .folderId(100L)
                .build();

        PermissionEntity existingPerm = PermissionEntity.builder()
                .id(77L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetGroup(targetGroup)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(groupRepository.findById(50L)).thenReturn(Optional.of(targetGroup));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(targetFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, targetFolder)).thenReturn(true);
        when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(50L, 100L))
                .thenReturn(List.of(existingPerm));
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PermissionResponseDto response = permissionService.grantPermission(groupGrantDto, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(77L);
        assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        verify(permissionRepository, times(1)).save(existingPerm);
    }

    @Test
    @DisplayName("Should revoke sibling active permissions when revoking group folder permission")
    void revokePermission_GroupFolder_RevokesSiblings_Success() {
        FolderEntity targetFolder = FolderEntity.builder().id(100L).name("Test Workspace").build();
        GroupEntity targetGroup = GroupEntity.builder().id(50L).name("Test Team").build();

        PermissionEntity targetPerm = PermissionEntity.builder()
                .id(10L)
                .targetGroup(targetGroup)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        PermissionEntity siblingPerm = PermissionEntity.builder()
                .id(11L)
                .targetGroup(targetGroup)
                .folder(targetFolder)
                .grantedBy(grantor)
                .revoked(false)
                .build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(targetPerm));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerFolderPermissions(grantor, targetFolder)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(50L, 100L))
                .thenReturn(List.of(targetPerm, siblingPerm));

        PermissionResponseDto response = permissionService.revokePermission(10L, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.isRevoked()).isTrue();
        assertThat(targetPerm.isRevoked()).isTrue();
        assertThat(siblingPerm.isRevoked()).isTrue();
        verify(permissionRepository).save(targetPerm);
        verify(permissionRepository).save(siblingPerm);
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unauthorized user attempts to activate document permission")
    void activatePermission_Document_Unauthorized_ThrowsAccessDenied() {
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("Secret.pdf").build();
        PermissionEntity perm = PermissionEntity.builder()
                .id(1L)
                .document(doc)
                .revoked(true)
                .build();

        when(permissionRepository.findById(1L)).thenReturn(Optional.of(perm));
        when(userRepository.findById("user-unauth")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.activatePermission(1L, "user-unauth"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to administer document permissions");
    }

    @Test
    @DisplayName("Should successfully activate document permission by authorized owner or admin")
    void activatePermission_Document_AuthorizedOwner_Success() {
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("Secret.pdf").build();
        PermissionEntity perm = PermissionEntity.builder()
                .id(1L)
                .document(doc)
                .revoked(true)
                .build();

        when(permissionRepository.findById(1L)).thenReturn(Optional.of(perm));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PermissionResponseDto response = permissionService.activatePermission(1L, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.isRevoked()).isFalse();
        assertThat(response.isSuspended()).isFalse();
        assertThat(perm.isRevoked()).isFalse();
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unauthorized user attempts to suspend document permission")
    void suspendPermission_Document_Unauthorized_ThrowsAccessDenied() {
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("Secret.pdf").build();
        PermissionEntity perm = PermissionEntity.builder()
                .id(2L)
                .document(doc)
                .suspended(false)
                .build();

        when(permissionRepository.findById(2L)).thenReturn(Optional.of(perm));
        when(userRepository.findById("user-unauth")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.suspendPermission(2L, "user-unauth"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to administer document permissions");
    }

    @Test
    @DisplayName("Should successfully suspend document permission by authorized owner or admin")
    void suspendPermission_Document_AuthorizedOwner_Success() {
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("Secret.pdf").build();
        PermissionEntity perm = PermissionEntity.builder()
                .id(2L)
                .document(doc)
                .suspended(false)
                .build();

        when(permissionRepository.findById(2L)).thenReturn(Optional.of(perm));
        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PermissionResponseDto response = permissionService.suspendPermission(2L, "user-admin");

        assertThat(response).isNotNull();
        assertThat(response.isSuspended()).isTrue();
        assertThat(perm.isSuspended()).isTrue();
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unauthorized user inspects effective permissions for document")
    void getEffectivePermissions_Document_Unauthorized_ThrowsAccessDenied() {
        DocumentEntity doc = DocumentEntity.builder().id(300L).fileName("Confidential.pdf").build();
        when(documentRepository.findById(300L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getEffectivePermissions(300L, "DOCUMENT", grantor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to inspect effective permissions for this document");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when unauthorized user inspects effective permissions for folder")
    void getEffectivePermissions_Folder_Unauthorized_ThrowsAccessDenied() {
        FolderEntity folder = FolderEntity.builder().id(400L).name("RestrictedFolder").build();
        when(folderRepository.findById(400L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, folder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getEffectivePermissions(400L, "FOLDER", grantor))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to inspect effective permissions for this folder");
    }

    @Test
    @DisplayName("Should not expose shareLinkToken in effective permissions response")
    void getEffectivePermissions_Authorized_DoesNotLeakShareLinkToken() {
        DocumentEntity doc = DocumentEntity.builder()
                .id(500L)
                .fileName("SharedDoc.pdf")
                .uploadedBy(grantor)
                .build();

        PermissionEntity directPermWithToken = PermissionEntity.builder()
                .id(99L)
                .document(doc)
                .shareLinkToken("super-secret-share-token-12345")
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .grantedBy(grantor)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        when(documentRepository.findById(500L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(grantor, doc)).thenReturn(true);
        when(permissionRepository.findByDocument_IdAndRevokedFalse(500L))
                .thenReturn(List.of(directPermWithToken));

        EffectiveAccessAuditDto result = permissionService.getEffectivePermissions(500L, "DOCUMENT", grantor);

        assertThat(result).isNotNull();
        assertThat(result.getDirectPermissions()).isNotEmpty();
        assertThat(result.getDirectPermissions().get(0).getShareLinkToken()).isNull();
        assertThat(result.getActiveShareLinks()).isNotEmpty();
        assertThat(result.getActiveShareLinks().get(0).getShareLinkToken()).isNull();
        assertThat(result.getActiveShareLinks().get(0).getCanReveal()).isTrue();
        assertThat(result.getActiveShareLinks().get(0).getCreatedByUserId()).isEqualTo(grantor.getId());
    }

    @Test
    @DisplayName("Should set canReveal=false for non-creator admin in effective permissions")
    void getEffectivePermissions_NonCreatorAdmin_CanRevealIsFalse() {
        OrganizationEntity testOrg = OrganizationEntity.builder().orgCode("TEST_ORG").name("Test Org").build();
        UserEntity otherAdmin = UserEntity.builder().id("admin-99").email("admin@test.org").role(Role.ROLE_ORG_ADMIN).organization(testOrg).build();

        FolderEntity orgFolder = FolderEntity.builder().id(600L).name("Org Projects").ownerOrg(testOrg).build();

        PermissionEntity linkCreatedByGrantor = PermissionEntity.builder()
                .id(100L)
                .folder(orgFolder)
                .shareLinkToken("super-secret-share-token-600")
                .permissionLevel(PermissionLevel.VIEW)
                .permissionType(PermissionType.ALLOW)
                .grantedBy(grantor)
                .revoked(false)
                .createdAt(Instant.now())
                .build();

        when(folderRepository.findById(600L)).thenReturn(Optional.of(orgFolder));
        when(evaluatorService.canAdministerFolderPermissions(otherAdmin, orgFolder)).thenReturn(true);
        when(permissionRepository.findByFolder_IdAndRevokedFalse(600L))
                .thenReturn(List.of(linkCreatedByGrantor));

        EffectiveAccessAuditDto result = permissionService.getEffectivePermissions(600L, "FOLDER", otherAdmin);

        assertThat(result).isNotNull();
        assertThat(result.getActiveShareLinks()).isNotEmpty();
        assertThat(result.getActiveShareLinks().get(0).getShareLinkToken()).isNull();
        assertThat(result.getActiveShareLinks().get(0).getCanReveal()).isFalse();
        assertThat(result.getActiveShareLinks().get(0).getCreatedByUserId()).isEqualTo(grantor.getId());
    }

    // =========================================================================
    // Organization Isolation Tests
    // =========================================================================

    @Test
    @DisplayName("Organization Isolation: GROUP target from same org succeeds")
    void grantPermission_GroupTarget_SameOrg_Success() {
        OrganizationEntity org1 = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        FolderEntity orgFolder = FolderEntity.builder().id(100L).name("Org Folder").ownerOrg(org1).build();
        GroupEntity orgGroup = GroupEntity.builder().id(50L).name("Org Team").organization(org1).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .targetGroupId(50L)
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(groupRepository.findById(50L)).thenReturn(Optional.of(orgGroup));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, orgFolder)).thenReturn(true);
        when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(50L, 100L)).thenReturn(List.of());
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
            PermissionEntity p = inv.getArgument(0);
            p.setId(201L);
            return p;
        });

        PermissionResponseDto response = permissionService.grantPermission(req, "user-admin");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(201L);
    }

    @Test
    @DisplayName("Organization Isolation: GROUP target from different org fails")
    void grantPermission_GroupTarget_DifferentOrg_ThrowsIllegalArgumentException() {
        OrganizationEntity org1 = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        OrganizationEntity org2 = OrganizationEntity.builder().orgCode("ORG2").name("Org Two").build();
        FolderEntity orgFolder = FolderEntity.builder().id(100L).name("Org Folder").ownerOrg(org1).build();
        GroupEntity extGroup = GroupEntity.builder().id(51L).name("Other Team").organization(org2).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .targetGroupId(51L)
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(groupRepository.findById(51L)).thenReturn(Optional.of(extGroup));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));

        assertThatThrownBy(() -> permissionService.grantPermission(req, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Team/Group target must belong to the same organization as the resource: ORG1");
    }

    @Test
    @DisplayName("Organization Isolation: USER_ORGANIZATION_MEMBER matching resource org succeeds")
    void grantPermission_UserOrgMember_MatchingOrg_Success() {
        OrganizationEntity org1 = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        FolderEntity orgFolder = FolderEntity.builder().id(100L).name("Org Folder").ownerOrg(org1).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("alice@example.com")
                .targetOrgCode("ORG1")
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, orgFolder)).thenReturn(true);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("alice@example.com", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
            PermissionEntity p = inv.getArgument(0);
            p.setId(202L);
            return p;
        });

        PermissionResponseDto response = permissionService.grantPermission(req, "user-admin");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("Organization Isolation: USER_ORGANIZATION_MEMBER mismatching resource org fails")
    void grantPermission_UserOrgMember_MismatchingOrg_ThrowsIllegalArgumentException() {
        OrganizationEntity org1 = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        FolderEntity orgFolder = FolderEntity.builder().id(100L).name("Org Folder").ownerOrg(org1).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("alice@example.com")
                .targetOrgCode("ORG2")
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("alice@example.com", "ORG2", MembershipStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> permissionService.grantPermission(req, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USER_ORGANIZATION_MEMBER target organization must match resource organization: ORG1");
    }

    @Test
    @DisplayName("Organization Isolation: personal resource + USER_PERSONAL succeeds")
    void grantPermission_PersonalResource_UserPersonal_Success() {
        FolderEntity personalFolder = FolderEntity.builder().id(100L).name("My Folder").ownerOrg(null).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("alice@example.com")
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(personalFolder));
        when(evaluatorService.canAdministerFolderPermissions(grantor, personalFolder)).thenReturn(true);
        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
            PermissionEntity p = inv.getArgument(0);
            p.setId(203L);
            return p;
        });

        PermissionResponseDto response = permissionService.grantPermission(req, "user-admin");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(203L);
    }

    @Test
    @DisplayName("Organization Isolation: personal resource + GROUP fails")
    void grantPermission_PersonalResource_GroupTarget_ThrowsIllegalArgumentException() {
        FolderEntity personalFolder = FolderEntity.builder().id(100L).name("My Folder").ownerOrg(null).build();
        GroupEntity anyGroup = GroupEntity.builder().id(50L).name("Any Group").build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .targetGroupId(50L)
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(groupRepository.findById(50L)).thenReturn(Optional.of(anyGroup));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(personalFolder));

        assertThatThrownBy(() -> permissionService.grantPermission(req, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Personal resources can only receive direct personal shares (USER_PERSONAL)");
    }

    @Test
    @DisplayName("Organization Isolation: personal resource + ORGANIZATION fails")
    void grantPermission_PersonalResource_OrgTarget_ThrowsIllegalArgumentException() {
        FolderEntity personalFolder = FolderEntity.builder().id(100L).name("My Folder").ownerOrg(null).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode("ORG1")
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(personalFolder));
        when(organizationRepository.existsByOrgCode("ORG1")).thenReturn(true);

        assertThatThrownBy(() -> permissionService.grantPermission(req, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Personal resources can only receive direct personal shares (USER_PERSONAL)");
    }

    @Test
    @DisplayName("Organization Isolation: personal resource + USER_ORGANIZATION_MEMBER fails")
    void grantPermission_PersonalResource_UserOrgMember_ThrowsIllegalArgumentException() {
        FolderEntity personalFolder = FolderEntity.builder().id(100L).name("My Folder").ownerOrg(null).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail("alice@example.com")
                .targetOrgCode("ORG1")
                .folderId(100L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(personalFolder));
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus("alice@example.com", "ORG1", MembershipStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> permissionService.grantPermission(req, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Personal resources can only receive direct personal shares (USER_PERSONAL)");
    }

    @Test
    @DisplayName("Organization Isolation: grantSelectedMembers mismatched organization fails")
    void grantSelectedMembers_MismatchedOrganization_ThrowsIllegalArgumentException() {
        OrganizationEntity org1 = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        FolderEntity orgFolder = FolderEntity.builder().id(100L).name("Org Folder").ownerOrg(org1).build();

        BulkSelectedMemberShareDto.MemberShareItemDto member = BulkSelectedMemberShareDto.MemberShareItemDto.builder()
                .targetUserEmail("alice@example.com")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        BulkSelectedMemberShareDto bulkDto = BulkSelectedMemberShareDto.builder()
                .targetOrgCode("ORG2")
                .folderId(100L)
                .selectedMembers(List.of(member))
                .build();

        when(organizationRepository.existsByOrgCode("ORG2")).thenReturn(true);
        when(folderRepository.findById(100L)).thenReturn(Optional.of(orgFolder));

        assertThatThrownBy(() -> permissionService.grantSelectedMembers(bulkDto, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target folder does not belong to organization: ORG2");
    }

    @Test
    @DisplayName("Organization Isolation: folder/document resource combinations from different organizations are rejected")
    void grantPermission_FolderAndDocument_DifferentOrgs_ThrowsIllegalArgumentException() {
        OrganizationEntity org1 = OrganizationEntity.builder().orgCode("ORG1").name("Org One").build();
        OrganizationEntity org2 = OrganizationEntity.builder().orgCode("ORG2").name("Org Two").build();

        FolderEntity folder = FolderEntity.builder().id(100L).name("Folder 1").ownerOrg(org1).build();
        DocumentEntity doc = DocumentEntity.builder().id(200L).fileName("Doc 2").ownerOrg(org2).build();

        PermissionRequestDto req = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail("alice@example.com")
                .folderId(100L)
                .documentId(200L)
                .build();

        when(userRepository.findById("user-admin")).thenReturn(Optional.of(grantor));
        when(folderRepository.findById(100L)).thenReturn(Optional.of(folder));
        when(documentRepository.findById(200L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> permissionService.grantPermission(req, "user-admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Folder and document belong to different organizations");
    }

    @Test
    @DisplayName("Phase 3A: unauthorized callers cannot inspect document permissions")
    void getActivePermissionsForDocument_Unauthorized_ThrowsAccessDenied() {
        DocumentEntity doc = DocumentEntity.builder().id(200L).build();
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();

        when(documentRepository.findById(200L)).thenReturn(Optional.of(doc));
        when(evaluatorService.canAdministerDocumentPermissions(caller, doc)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getActivePermissionsForDocument(200L, caller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to view permissions for this document");
    }

    @Test
    @DisplayName("Phase 3A: unauthorized callers cannot inspect folder permissions")
    void getActivePermissionsForFolder_Unauthorized_ThrowsAccessDenied() {
        FolderEntity folder = FolderEntity.builder().id(100L).build();
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();

        when(folderRepository.findById(100L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(caller, folder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getActivePermissionsForFolder(100L, caller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to view permissions for this folder");
    }

    @Test
    @DisplayName("Phase 3A: unauthorized callers cannot inspect group permissions")
    void getActivePermissionsForGroup_Unauthorized_ThrowsAccessDenied() {
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ORG1").build();
        GroupEntity group = GroupEntity.builder().id(50L).organization(org).build();
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();

        when(groupRepository.findById(50L)).thenReturn(Optional.of(group));
        when(evaluatorService.isUserAdminOfOrg(caller, "ORG1")).thenReturn(false);
        when(userGroupRepository.findByUser_IdAndGroup_Id("caller-1", 50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> permissionService.getActivePermissionsForGroup(50L, caller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to view permissions for this group");
    }

    @Test
    @DisplayName("Phase 3A: unauthorized callers cannot inspect arbitrary permission by ID")
    void getPermissionById_Unauthorized_ThrowsAccessDenied() {
        FolderEntity folder = FolderEntity.builder().id(100L).build();
        PermissionEntity perm = PermissionEntity.builder().id(10L).folder(folder).targetUserEmail("other@test.com").build();
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(perm));
        when(evaluatorService.canAdministerFolderPermissions(caller, folder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getPermissionById(10L, caller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to view this permission");
    }

    @Test
    @DisplayName("Phase 3A: target-based permission inspection rejects unauthorized inspection of other user")
    void getPermissionsForTarget_TargetUserEmail_UnauthorizedOtherUser_ThrowsAccessDenied() {
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();

        assertThatThrownBy(() -> permissionService.getPermissionsForTarget("other@test.com", null, null, caller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to query permissions for user: other@test.com");
    }

    @Test
    @DisplayName("Phase 3A: target-based permission inspection permits user to query their own permissions")
    void getPermissionsForTarget_TargetUserEmail_Self_Succeeds() {
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();
        PermissionEntity perm = PermissionEntity.builder().id(1L).targetUserEmail("caller@test.com").permissionLevel(PermissionLevel.VIEW).build();

        when(permissionRepository.findByTargetUserEmail("caller@test.com")).thenReturn(List.of(perm));

        List<PermissionResponseDto> result = permissionService.getPermissionsForTarget("caller@test.com", null, null, caller);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("Phase 3A: target-based permission inspection rejects non-admin querying org permissions")
    void getPermissionsForTarget_TargetOrgCode_UnauthorizedNonAdmin_ThrowsAccessDenied() {
        UserEntity caller = UserEntity.builder().id("caller-1").email("caller@test.com").role(Role.ROLE_USER).build();
        when(evaluatorService.isUserAdminOfOrg(caller, "TARGET_ORG")).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getPermissionsForTarget(null, "TARGET_ORG", null, caller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to query permissions for organization: TARGET_ORG");
    }

    @Test
    @DisplayName("Phase 3A: getPermissionsForTarget with email and orgCode queries user in org context and excludes unrelated permissions")
    void getPermissionsForTarget_EmailAndOrgCode_ScopedToOrgContext() {
        UserEntity orgAdminCaller = UserEntity.builder().id("admin-1").email("admin@org1.com").role(Role.ROLE_USER).build();
        String targetEmail = "alice@org1.com";
        String targetOrg = "ORG1";

        when(evaluatorService.isUserAdminOfOrg(orgAdminCaller, targetOrg)).thenReturn(true);
        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(targetEmail, targetOrg, MembershipStatus.ACTIVE)).thenReturn(true);

        PermissionEntity orgPerm = PermissionEntity.builder()
                .id(1L)
                .targetUserEmail(targetEmail)
                .targetOrgCode(targetOrg)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(permissionRepository.findPermissionsForUserInOrg(targetEmail, targetOrg)).thenReturn(List.of(orgPerm));

        List<PermissionResponseDto> result = permissionService.getPermissionsForTarget(targetEmail, targetOrg, null, orgAdminCaller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getTargetUserEmail()).isEqualTo(targetEmail);
        verify(permissionRepository).findPermissionsForUserInOrg(targetEmail, targetOrg);
        verify(permissionRepository, never()).findByTargetUserEmailOrTargetOrgCode(any(), any());
    }

    @Test
    @DisplayName("Phase 3A: getPermissionsForTarget with orgCode only queries org permissions")
    void getPermissionsForTarget_OrgCodeOnly_QueriesOrgPermissions() {
        UserEntity orgAdminCaller = UserEntity.builder().id("admin-1").email("admin@org1.com").role(Role.ROLE_USER).build();
        String targetOrg = "ORG1";

        when(evaluatorService.isUserAdminOfOrg(orgAdminCaller, targetOrg)).thenReturn(true);

        PermissionEntity orgPerm = PermissionEntity.builder()
                .id(2L)
                .targetOrgCode(targetOrg)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        when(permissionRepository.findByTargetOrgCode(targetOrg)).thenReturn(List.of(orgPerm));

        List<PermissionResponseDto> result = permissionService.getPermissionsForTarget(null, targetOrg, null, orgAdminCaller);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(2L);
        verify(permissionRepository).findByTargetOrgCode(targetOrg);
        verify(permissionRepository, never()).findByTargetUserEmailOrTargetOrgCode(any(), any());
    }

    @Test
    @DisplayName("Phase 3A: getPermissionById rejects grantee without resource administration authority")
    void getPermissionById_GranteeOnly_ThrowsAccessDenied() {
        FolderEntity folder = FolderEntity.builder().id(100L).build();
        UserEntity grantorUser = UserEntity.builder().id("grantor-id").build();
        PermissionEntity perm = PermissionEntity.builder()
                .id(10L)
                .folder(folder)
                .targetUserEmail("alice@test.com")
                .grantedBy(grantorUser)
                .build();
        UserEntity granteeCaller = UserEntity.builder().id("grantee-id").email("alice@test.com").role(Role.ROLE_USER).build();

        when(permissionRepository.findById(10L)).thenReturn(Optional.of(perm));
        when(evaluatorService.canAdministerFolderPermissions(granteeCaller, folder)).thenReturn(false);

        assertThatThrownBy(() -> permissionService.getPermissionById(10L, granteeCaller))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User does not have authority to view this permission");
    }

    @Test
    @DisplayName("Phase 3A: PermissionResponseDto never exposes passwordHash")
    void permissionResponseDto_NeverExposesPasswordHash() {
        PermissionEntity permWithPassword = PermissionEntity.builder()
                .id(10L)
                .passwordHash("secret_bcrypt_hash")
                .targetUserEmail("user@test.com")
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        PermissionResponseDto dto = PermissionMapper.toResponseDto(permWithPassword);
        assertThat(dto).isNotNull();
        assertThat(dto.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("Phase 3A: permission mutation methods declare @Transactional boundary")
    void permissionMutations_HaveTransactionalAnnotation() throws Exception {
        assertThat(PermissionServiceImpl.class.getMethod("grantPermission", PermissionRequestDto.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PermissionServiceImpl.class.getMethod("grantFolderShareWithExclusions", BulkShareWithExclusionsDto.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PermissionServiceImpl.class.getMethod("grantSelectedMembers", BulkSelectedMemberShareDto.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PermissionServiceImpl.class.getMethod("updatePermission", Long.class, PermissionUpdateRequestDto.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PermissionServiceImpl.class.getMethod("activatePermission", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PermissionServiceImpl.class.getMethod("suspendPermission", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
        assertThat(PermissionServiceImpl.class.getMethod("revokePermission", Long.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    // =========================================================================
    // Phase 1: Shared With Me & Shared By Me Realignment Tests
    // =========================================================================

    @Test
    @DisplayName("Phase 1: Direct personal share appears with DIRECT SHARE sourceLabel")
    void getSharedWithMe_directPersonalShare() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").firstName("Alice").lastName("Smith").build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Personal Folder").build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(1L)
                .folder(sharedFolder)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getPermissionId()).isEqualTo(1L);
        assertThat(folderDto.getSourceLabel()).isEqualTo("DIRECT SHARE");
        assertThat(folderDto.getSourceLabels()).containsExactly("DIRECT SHARE");
        assertThat(folderDto.getSourceDescription()).contains("Shared directly with you");
        assertThat(folderDto.getMatchingGrantCount()).isEqualTo(1);
        assertThat(folderDto.getMatchedPermissionIds()).containsExactly(1L);
    }

    @Test
    @DisplayName("Phase 1: Direct org-member share appears with DIRECT SHARE when caller is active member")
    void getSharedWithMe_directOrgMemberShare() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").build();
        DocumentEntity sharedDoc = DocumentEntity.builder().id(201L).fileName("Report.pdf").status(DocumentStatus.ACTIVE).build();
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(currentUser).organization(org).status(MembershipStatus.ACTIVE).build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(2L)
                .document(sharedDoc)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .targetOrgCode("ACME")
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .createdAt(Instant.now())
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(userOrg));
        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedDocuments()).hasSize(1);
        SharedItemDto docDto = response.getSharedDocuments().get(0);
        assertThat(docDto.getPermissionId()).isEqualTo(2L);
        assertThat(docDto.getSourceLabel()).isEqualTo("DIRECT SHARE");
        assertThat(docDto.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
    }

    @Test
    @DisplayName("Phase 1: Explicit team share appears for active team member with TEAM SHARE sourceLabel")
    void getSharedWithMe_teamShare_activeMember() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").build();
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        GroupEntity devTeam = GroupEntity.builder().id(50L).name("Dev Team").organization(org).build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Team Folder").build();

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(currentUser).organization(org).status(MembershipStatus.ACTIVE).build();
        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(currentUser).group(devTeam).build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(3L)
                .folder(sharedFolder)
                .grantedBy(alice)
                .recipientType(RecipientType.GROUP)
                .targetGroup(devTeam)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .createdAt(Instant.now())
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(userOrg));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(userGroup));
        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getSourceLabel()).isEqualTo("TEAM SHARE");
        assertThat(folderDto.getTargetGroupName()).isEqualTo("Dev Team");
        assertThat(folderDto.getSourceDescription()).contains("Dev Team");
    }

    @Test
    @DisplayName("Phase 1: Explicit team share is excluded if user's org membership is suspended")
    void getSharedWithMe_teamShare_suspendedOrgMember() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        OrganizationEntity activeOrg = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        OrganizationEntity suspendedOrg = OrganizationEntity.builder().orgCode("BETA").name("Beta Corp").build();
        GroupEntity devTeam = GroupEntity.builder().id(50L).name("Dev Team").organization(suspendedOrg).build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Team Folder").build();

        // User is active in ACME, but NOT in BETA (where devTeam belongs)
        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(currentUser).organization(activeOrg).status(MembershipStatus.ACTIVE).build()));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(UserGroupEntity.builder().user(currentUser).group(devTeam).build()));

        // Even if repository returned candidate with devTeam, service filters it out because activeTeamIds excludes BETA's teams
        PermissionEntity perm = PermissionEntity.builder()
                .id(4L)
                .folder(sharedFolder)
                .recipientType(RecipientType.GROUP)
                .targetGroup(devTeam)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), eq(false), anyCollection(), eq(true), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    @Test
    @DisplayName("Phase 1: Explicit team share is excluded if user is not in that team")
    void getSharedWithMe_teamShare_nonMember() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        GroupEntity otherTeam = GroupEntity.builder().id(99L).name("Other Team").build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Folder").build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(5L)
                .folder(sharedFolder)
                .recipientType(RecipientType.GROUP)
                .targetGroup(otherTeam)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    @Test
    @DisplayName("Phase 1: Workspace root folder allocation is strictly excluded from Shared With Me")
    void getSharedWithMe_workspaceAllocation_excluded() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        FolderEntity wsRootFolder = FolderEntity.builder().id(500L).name("Workspace Container").build();

        PermissionEntity wsPerm = PermissionEntity.builder()
                .id(6L)
                .folder(wsRootFolder)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(wsPerm));
        when(projectWorkspaceRepository.existsByRootFolder_Id(500L)).thenReturn(true);

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    @Test
    @DisplayName("Phase 1: Explicit share to organization appears with ORGANIZATION SHARE sourceLabel")
    void getSharedWithMe_organizationShare_activeMember() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").build();
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Company Wide Folder").build();

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(currentUser).organization(org).status(MembershipStatus.ACTIVE).build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(7L)
                .folder(sharedFolder)
                .grantedBy(alice)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode("ACME")
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(userOrg));
        when(organizationRepository.findAllById(anyCollection()))
                .thenReturn(List.of(org));
        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), eq(true), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getSourceLabel()).isEqualTo("ORGANIZATION SHARE");
        assertThat(folderDto.getTargetOrgCode()).isEqualTo("ACME");
        assertThat(folderDto.getSourceDescription()).contains("Acme Corp");
    }

    @Test
    @DisplayName("Phase 1: Collaboration share appears with COLLABORATION SHARE sourceLabel when active")
    void getSharedWithMe_collaborationShare_activeCollab() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").build();
        CollaborationEntity collab = CollaborationEntity.builder().id(77L).name("Cross-Org Joint Initiative").status(CollaborationStatus.ACTIVE).build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Collaboration Assets").build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(8L)
                .folder(sharedFolder)
                .grantedBy(alice)
                .collaboration(collab)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getSourceLabel()).isEqualTo("COLLABORATION SHARE");
        assertThat(folderDto.getCollaborationId()).isEqualTo(77L);
        assertThat(folderDto.getCollaborationName()).isEqualTo("Cross-Org Joint Initiative");
        assertThat(folderDto.getSourceDescription()).contains("Cross-Org Joint Initiative");
    }

    @Test
    @DisplayName("Phase 1: Collaboration share in terminated collaboration is strictly excluded")
    void getSharedWithMe_collaborationShare_terminatedCollab() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        CollaborationEntity terminatedCollab = CollaborationEntity.builder().id(77L).name("Terminated Initiative").status(CollaborationStatus.REVOKED).build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Old Assets").build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(9L)
                .folder(sharedFolder)
                .collaboration(terminatedCollab)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    @Test
    @DisplayName("Phase 1: Collaboration root container folder is strictly excluded from Shared With Me")
    void getSharedWithMe_collaborationRoot_excluded() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        FolderEntity collabRoot = FolderEntity.builder().id(600L).name("Collaboration Root Container").build();
        CollaborationEntity collab = CollaborationEntity.builder().id(77L).rootFolder(collabRoot).status(CollaborationStatus.ACTIVE).build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(10L)
                .folder(collabRoot)
                .collaboration(collab)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    @Test
    @DisplayName("Phase 1: Multi-grant deduplication returns 1 card with highest permission level and distinct source labels")
    void getSharedWithMe_multiGrantDeduplication() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        UserEntity alice = UserEntity.builder().id("user-alice").email("alice@example.com").build();
        OrganizationEntity org = OrganizationEntity.builder().orgCode("ACME").name("Acme Corp").build();
        GroupEntity devTeam = GroupEntity.builder().id(50L).name("Dev Team").organization(org).build();
        FolderEntity sharedFolder = FolderEntity.builder().id(100L).name("Collaborative Folder").build();

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(currentUser).organization(org).status(MembershipStatus.ACTIVE).build();
        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(currentUser).group(devTeam).build();

        // Direct VIEW grant created 2 hours ago
        PermissionEntity directPerm = PermissionEntity.builder()
                .id(10L)
                .folder(sharedFolder)
                .grantedBy(alice)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now().minusSeconds(7200))
                .build();

        // Team EDIT grant created 1 hour ago
        PermissionEntity teamPerm = PermissionEntity.builder()
                .id(20L)
                .folder(sharedFolder)
                .grantedBy(alice)
                .targetGroup(devTeam)
                .recipientType(RecipientType.GROUP)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(userOrg));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(userGroup));
        when(permissionRepository.findSharedWithMeCandidates(eq("bob@example.com"), eq("user-bob"), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(teamPerm, directPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        // Exactly ONE card for this folder
        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto folderDto = response.getSharedFolders().get(0);
        assertThat(folderDto.getResourceId()).isEqualTo(100L);
        assertThat(folderDto.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        assertThat(folderDto.getSourceLabel()).isEqualTo("TEAM SHARE"); // EDIT grant takes precedence
        assertThat(folderDto.getSourceLabels()).containsExactlyInAnyOrder("DIRECT SHARE", "TEAM SHARE");
        assertThat(folderDto.getMatchingGrantCount()).isEqualTo(2);
        assertThat(folderDto.getMatchedPermissionIds()).containsExactlyInAnyOrder(10L, 20L);
        assertThat(folderDto.getSourceDescription()).contains("2 grants, highest: EDIT");
    }

    @Test
    @DisplayName("Phase 1: Inactive documents (PENDING_UPLOAD / FAILED) are strictly excluded from Shared With Me")
    void getSharedWithMe_inactiveDocument_excluded() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        DocumentEntity failedDoc = DocumentEntity.builder().id(301L).fileName("Corrupted.pdf").status(DocumentStatus.FAILED).build();

        PermissionEntity perm = PermissionEntity.builder()
                .id(11L)
                .document(failedDoc)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(perm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedDocuments()).isEmpty();
    }

    @Test
    @DisplayName("Phase 1: Grants created by current user (self-grants) are excluded from Shared With Me")
    void getSharedWithMe_selfGrant_excluded() {
        UserEntity currentUser = UserEntity.builder().id("user-bob").email("bob@example.com").build();
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("My Folder").build();

        // Granted by currentUser himself
        PermissionEntity selfPerm = PermissionEntity.builder()
                .id(12L)
                .folder(sharedFolder)
                .grantedBy(currentUser)
                .targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .createdAt(Instant.now())
                .build();

        when(permissionRepository.findSharedWithMeCandidates(anyString(), anyString(), anyCollection(), anyBoolean(), anyCollection(), anyBoolean(), any(Instant.class)))
                .thenReturn(List.of(selfPerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }
}


