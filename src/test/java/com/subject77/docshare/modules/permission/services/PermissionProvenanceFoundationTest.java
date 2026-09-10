package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.dto.PermissionUpdateRequestDto;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.utils.PermissionHashUtils;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PermissionProvenanceFoundationTest {

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

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private UserEntity actor;
    private OrganizationEntity org;
    private FolderEntity folder;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        actor = UserEntity.builder()
                .id("actor-1")
                .email("actor@docshare.com")
                .role(Role.ROLE_USER)
                .build();

        org = OrganizationEntity.builder()
                .orgCode("CORP")
                .name("Corporate")
                .build();

        folder = FolderEntity.builder()
                .id(10L)
                .name("Finance")
                .ownerOrg(org)
                .build();

        document = DocumentEntity.builder()
                .id(20L)
                .fileName("Quarterly.pdf")
                .fileHash("hash-pdf-20")
                .ownerOrg(org)
                .folder(folder)
                .build();

        lenient().when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        lenient().when(userRepository.existsByEmail(anyString())).thenReturn(true);
        lenient().when(organizationRepository.existsByOrgCode(anyString())).thenReturn(true);
        lenient().when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(anyString(), anyString(), any())).thenReturn(true);
        lenient().when(evaluatorService.resolveDelegationCeiling(any(), any(), any())).thenReturn(PermissionLevel.EDIT);
        lenient().when(evaluatorService.canActorOverrideGrant(any(), any(), any(), any())).thenReturn(true);
        lenient().when(evaluatorService.canActorOverrideInactivator(any(), any(), any(), any(), any())).thenReturn(true);
        lenient().when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Scenario A: New ORG_ADMIN grant stamps ORG_ADMIN tier and null workspaceId")
    void testNewOrgAdminGrant_StampsOrgAdminTier() {
        actor.setRole(Role.ROLE_USER);
        when(folderRepository.findById(10L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);
        when(evaluatorService.resolveAuthorityTierAtCreation(actor, folder, null, null))
                .thenReturn(AuthorityTier.ORG_ADMIN);

        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(10L)
                .targetUserEmail("bob@corp.com")
                .targetOrgCode("CORP")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        assertThat(saved.getWorkspaceId()).isNull();
        assertThat(saved.getGrantedBy()).isEqualTo(actor);
        assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        assertThat(response.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Scenario B: New SUPER_ADMIN grant stamps SUPER_ADMIN tier and null workspaceId")
    void testNewSuperAdminGrant_StampsSuperAdminTier() {
        actor.setRole(Role.ROLE_SUPER_ADMIN);
        when(folderRepository.findById(10L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);
        when(evaluatorService.resolveAuthorityTierAtCreation(actor, folder, null, null))
                .thenReturn(AuthorityTier.SUPER_ADMIN);

        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(10L)
                .targetUserEmail("bob@corp.com")
                .targetOrgCode("CORP")
                .build();

        PermissionResponseDto response = permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.SUPER_ADMIN);
        assertThat(saved.getWorkspaceId()).isNull();
        assertThat(saved.getGrantedBy()).isEqualTo(actor);
    }

    @Test
    @DisplayName("Scenario C: Workspace Manager grant stamps WORKSPACE_MANAGER tier and verified workspaceId")
    void testWorkspaceManagerGrant_StampsWorkspaceManagerTierAndWorkspaceId() {
        when(folderRepository.findById(10L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);
        when(evaluatorService.resolveAuthorityTierAtCreation(actor, folder, null, null))
                .thenReturn(AuthorityTier.WORKSPACE_MANAGER);
        when(evaluatorService.resolveWorkspaceIdForManager(actor, folder, 55L))
                .thenReturn(55L);

        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.GROUP)
                .folderId(10L)
                .targetGroupId(99L)
                .workspaceId(55L)
                .build();

        GroupEntity group = GroupEntity.builder().id(99L).organization(org).build();
        when(groupRepository.findById(99L)).thenReturn(Optional.of(group));

        PermissionResponseDto response = permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
        assertThat(saved.getWorkspaceId()).isEqualTo(55L);
        assertThat(saved.getGrantedBy()).isEqualTo(actor);
        assertThat(response.getWorkspaceId()).isEqualTo(55L);
        assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
    }

    @Test
    @DisplayName("Scenario D: Collaboration Manager grant stamps COLLABORATION_MANAGER tier and null workspaceId")
    void testCollaborationManagerGrant_StampsCollaborationManagerTier() {
        CollaborationEntity collab = CollaborationEntity.builder().id(77L).name("Joint Project").build();
        when(collaborationRepository.findById(77L)).thenReturn(Optional.of(collab));
        when(folderRepository.findById(10L)).thenReturn(Optional.of(folder));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);
        when(evaluatorService.resolveAuthorityTierAtCreation(actor, folder, null, collab))
                .thenReturn(AuthorityTier.COLLABORATION_MANAGER);

        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folderId(10L)
                .collaborationId(77L)
                .targetUserEmail("collab_partner@external.com")
                .targetOrgCode("CORP")
                .build();

        permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.COLLABORATION_MANAGER);
        assertThat(saved.getWorkspaceId()).isNull();
        assertThat(saved.getCollaboration()).isEqualTo(collab);
    }

    @Test
    @DisplayName("Scenario E: Personal resource owner grant stamps PERSONAL_RESOURCE_OWNER tier")
    void testPersonalOwnerGrant_StampsPersonalResourceOwnerTier() {
        FolderEntity personalFolder = FolderEntity.builder()
                .id(30L)
                .name("My Personal Docs")
                .ownerOrg(null)
                .createdBy(actor)
                .build();

        when(folderRepository.findById(30L)).thenReturn(Optional.of(personalFolder));
        when(evaluatorService.canAdministerFolderPermissions(actor, personalFolder)).thenReturn(true);
        when(evaluatorService.resolveAuthorityTierAtCreation(actor, personalFolder, null, null))
                .thenReturn(AuthorityTier.PERSONAL_RESOURCE_OWNER);

        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .folderId(30L)
                .targetUserEmail("friend@external.com")
                .build();

        permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.PERSONAL_RESOURCE_OWNER);
        assertThat(saved.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Scenario F: Organization uploader grant stamps ORG_RESOURCE_CREATOR tier")
    void testOrgUploaderGrant_StampsOrgResourceCreatorTier() {
        when(documentRepository.findByFileHash("hash-pdf-20")).thenReturn(List.of(document));
        when(evaluatorService.canAdministerDocumentPermissions(actor, document)).thenReturn(true);
        when(evaluatorService.resolveAuthorityTierAtCreation(actor, null, document, null))
                .thenReturn(AuthorityTier.ORG_RESOURCE_CREATOR);

        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .documentFileHash("hash-pdf-20")
                .targetUserEmail("colleague@corp.com")
                .targetOrgCode("CORP")
                .build();

        permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_RESOURCE_CREATOR);
        assertThat(saved.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Scenario G: Duplicate overwrite preserves original grantedBy, grantorAuthorityTier, and workspaceId")
    void testDuplicateOverwrite_PreservesOriginalProvenance() {
        UserEntity originalCreator = UserEntity.builder().id("original-creator-id").email("original@corp.com").build();
        GroupEntity team = GroupEntity.builder().id(99L).organization(org).build();

        PermissionEntity existing = PermissionEntity.builder()
                .id(501L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .targetGroup(team)
                .folder(folder)
                .grantedBy(originalCreator)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .workspaceId(null)
                .revoked(false)
                .build();

        when(folderRepository.findById(10L)).thenReturn(Optional.of(folder));
        when(groupRepository.findById(99L)).thenReturn(Optional.of(team));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);
        when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(99L, 10L))
                .thenReturn(List.of(existing));

        // Modifier "actor" attempts to overwrite existing grant to EDIT level
        PermissionRequestDto request = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.GROUP)
                .folderId(10L)
                .targetGroupId(99L)
                .workspaceId(12345L) // Modifier tries to pass a workspaceId
                .build();

        permissionService.grantPermission(request, actor.getId());

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();

        // Level is updated
        assertThat(saved.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        // Original provenance is IMMUTABLY PRESERVED
        assertThat(saved.getGrantedBy()).isEqualTo(originalCreator);
        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        assertThat(saved.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Scenario H: updatePermission leaves original grantedBy, grantorAuthorityTier, and workspaceId unchanged")
    void testUpdatePermission_LeavesProvenanceUnchanged() {
        UserEntity originalCreator = UserEntity.builder().id("creator-id").email("creator@corp.com").build();

        PermissionEntity existing = PermissionEntity.builder()
                .id(601L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folder(folder)
                .grantedBy(originalCreator)
                .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(88L)
                .revoked(false)
                .build();

        when(permissionRepository.findById(601L)).thenReturn(Optional.of(existing));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);

        PermissionUpdateRequestDto updateReq = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        permissionService.updatePermission(601L, updateReq, actor.getId());

        assertThat(existing.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        assertThat(existing.getGrantedBy()).isEqualTo(originalCreator);
        assertThat(existing.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
        assertThat(existing.getWorkspaceId()).isEqualTo(88L);
    }

    @Test
    @DisplayName("Scenario I: revoke, suspend, and activate leave original provenance unchanged")
    void testRevokeSuspendActivate_LeavesProvenanceUnchanged() {
        UserEntity originalCreator = UserEntity.builder().id("creator-id").email("creator@corp.com").build();

        PermissionEntity existing = PermissionEntity.builder()
                .id(701L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folder(folder)
                .grantedBy(originalCreator)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .workspaceId(null)
                .revoked(false)
                .suspended(false)
                .build();

        when(permissionRepository.findById(701L)).thenReturn(Optional.of(existing));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);

        // Suspend
        permissionService.suspendPermission(701L, actor.getId());
        assertThat(existing.isSuspended()).isTrue();
        assertThat(existing.getGrantedBy()).isEqualTo(originalCreator);
        assertThat(existing.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);

        // Activate
        permissionService.activatePermission(701L, actor.getId());
        assertThat(existing.isSuspended()).isFalse();
        assertThat(existing.getGrantedBy()).isEqualTo(originalCreator);
        assertThat(existing.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);

        // Revoke
        permissionService.revokePermission(701L, actor.getId());
        assertThat(existing.isRevoked()).isTrue();
        assertThat(existing.getGrantedBy()).isEqualTo(originalCreator);
        assertThat(existing.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
    }

    @Test
    @DisplayName("Scenario J: Legacy permission with null grantorAuthorityTier remains valid and unmutated")
    void testLegacyPermission_PreservesNullGrantorAuthorityTier() {
        UserEntity legacyCreator = UserEntity.builder().id("legacy-id").email("legacy@corp.com").build();

        PermissionEntity legacy = PermissionEntity.builder()
                .id(801L)
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .folder(folder)
                .grantedBy(legacyCreator)
                .grantorAuthorityTier(null) // Legacy row: null provenance
                .workspaceId(null)
                .revoked(false)
                .build();

        when(permissionRepository.findById(801L)).thenReturn(Optional.of(legacy));
        when(evaluatorService.canAdministerFolderPermissions(actor, folder)).thenReturn(true);

        PermissionUpdateRequestDto updateReq = PermissionUpdateRequestDto.builder()
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        permissionService.updatePermission(801L, updateReq, actor.getId());

        // Verify null provenance was not replaced by current runtime role
        assertThat(legacy.getGrantorAuthorityTier()).isNull();
        assertThat(legacy.getWorkspaceId()).isNull();
    }

    @Test
    @DisplayName("Scenario K: Hash integrity incorporates grantorAuthorityTier and workspaceId")
    void testHashIntegrity_IncorporatesProvenanceFields() {
        UserEntity creator = UserEntity.builder().id("u1").email("creator@corp.com").build();

        PermissionEntity entity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.GROUP)
                .folder(folder)
                .grantedBy(creator)
                .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(42L)
                .build();

        String v3Hash = PermissionHashUtils.calculateRecordHash(entity);
        entity.setRecordHash(v3Hash);

        assertThat(PermissionHashUtils.verifyRecordHash(entity)).isTrue();

        // Altering grantorAuthorityTier causes hash verification failure
        entity.setGrantorAuthorityTier(AuthorityTier.ORG_ADMIN);
        assertThat(PermissionHashUtils.verifyRecordHash(entity)).isFalse();

        // Restoring grantorAuthorityTier and altering workspaceId causes hash verification failure
        entity.setGrantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER);
        entity.setWorkspaceId(999L);
        assertThat(PermissionHashUtils.verifyRecordHash(entity)).isFalse();
    }
}
