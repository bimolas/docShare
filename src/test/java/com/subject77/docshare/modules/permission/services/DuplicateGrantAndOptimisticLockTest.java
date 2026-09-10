package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.exceptions.GlobalExceptionHandler;
import com.subject77.docshare.exceptions.dto.ErrorResponseDto;
import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.*;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import jakarta.persistence.Version;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DuplicateGrantAndOptimisticLockTest {

    @Mock private PermissionRepository permissionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private UserRepository userRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private UserGroupRepository userGroupRepository;
    @Mock private UserOrganizationRepository userOrganizationRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private CollaborationRepository collaborationRepository;
    @Mock private PermissionEventPublisher eventPublisher;
    @Mock private PermissionCacheService cacheService;
    @Mock private PermissionEvaluatorService evaluatorService;
    @Mock private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;
    @Mock private NotificationService notificationService;
    @Mock private HttpServletRequest httpServletRequest;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private UserEntity orgAdmin;
    private UserEntity wm1;
    private UserEntity wm2;
    private OrganizationEntity org;
    private FolderEntity folder;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        org = OrganizationEntity.builder()
                .orgCode("ORG1")
                .name("Organization 1")
                .build();

        orgAdmin = UserEntity.builder()
                .id("org-admin-id")
                .email("admin@org1.com")
                .role(Role.ROLE_ORG_ADMIN)
                .build();

        wm1 = UserEntity.builder()
                .id("wm-1-id")
                .email("wm1@org1.com")
                .role(Role.ROLE_USER)
                .build();

        wm2 = UserEntity.builder()
                .id("wm-2-id")
                .email("wm2@org1.com")
                .role(Role.ROLE_USER)
                .build();

        folder = FolderEntity.builder()
                .id(100L)
                .name("Workspace Folder")
                .ownerOrg(org)
                .build();

        document = DocumentEntity.builder()
                .id(200L)
                .fileName("Workspace Doc.pdf")
                .fileHash("hash-doc-200")
                .folder(folder)
                .ownerOrg(org)
                .build();

        UserEntity alice = UserEntity.builder()
                .id("alice-id")
                .email("alice@org1.com")
                .role(Role.ROLE_USER)
                .build();

        when(userRepository.findById("org-admin-id")).thenReturn(Optional.of(orgAdmin));
        when(userRepository.findById("wm-1-id")).thenReturn(Optional.of(wm1));
        when(userRepository.findById("wm-2-id")).thenReturn(Optional.of(wm2));
        when(userRepository.findByEmail("alice@org1.com")).thenReturn(Optional.of(alice));
        when(userRepository.existsByEmail("alice@org1.com")).thenReturn(true);
        when(folderRepository.findById(100L)).thenReturn(Optional.of(folder));
        when(documentRepository.findById(200L)).thenReturn(Optional.of(document));

        when(userOrganizationRepository.existsByUser_EmailAndOrganization_OrgCodeAndStatus(
                anyString(), eq("ORG1"), eq(MembershipStatus.ACTIVE))).thenReturn(true);
        when(organizationRepository.existsByOrgCode("ORG1")).thenReturn(true);
    }

    @Nested
    @DisplayName("1. Duplicate User Grant Tests")
    class DuplicateUserGrantTests {

        @Test
        @DisplayName("A. Existing user VIEW grant blocks unauthorized lower-tier EDIT insertion")
        void existingUserViewGrant_BlocksUnauthorizedLowerTierEditInsertion() {
            // ORG_ADMIN created Alice VIEW on folder 100
            PermissionEntity existingOrgAdminGrant = PermissionEntity.builder()
                    .id(10L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(existingOrgAdminGrant));

            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            // WM1 cannot override ORG_ADMIN grant
            when(evaluatorService.canActorOverrideGrant(wm1, existingOrgAdminGrant, folder, null)).thenReturn(false);

            PermissionRequestDto wmEditReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(wmEditReq, "wm-1-id"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to override existing permission created with tier ORG_ADMIN");

            // Verify no entity is saved
            verify(permissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("B. Same-scope authorized peer can modify according to existing rules")
        void sameScopeAuthorizedPeer_CanModifyAccordingToRules() {
            // WM1 created Alice VIEW on folder 100 with workspaceId 55
            PermissionEntity existingWmGrant = PermissionEntity.builder()
                    .id(11L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(wm1)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(55L)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(existingWmGrant));

            when(evaluatorService.canAdministerFolderPermissions(wm2, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm2, folder, null)).thenReturn(PermissionLevel.EDIT);
            // WM2 has same workspace authority and can modify
            when(evaluatorService.canActorOverrideGrant(wm2, existingWmGrant, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto wm2EditReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(wm2EditReq, "wm-2-id");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(11L);
            assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(response.getGrantedByUserId()).isEqualTo("wm-1-id"); // Original creator preserved!
            assertThat(response.getWorkspaceId()).isEqualTo(55L); // Workspace scope preserved!
        }

        @Test
        @DisplayName("C. ORG_ADMIN can modify lower-tier user grants in scope")
        void orgAdmin_CanModifyLowerTierUserGrantsInScope() {
            // WM1 created Alice VIEW on folder 100
            PermissionEntity existingWmGrant = PermissionEntity.builder()
                    .id(12L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(wm1)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(55L)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(existingWmGrant));

            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, existingWmGrant, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto adminEditReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(adminEditReq, "org-admin-id");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(12L);
            assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(response.getGrantedByUserId()).isEqualTo("wm-1-id"); // Original creator preserved!
        }

        @Test
        @DisplayName("D. Existing provenance remains unchanged on allowed overwrite")
        void existingProvenance_RemainsUnchangedOnAllowedOverwrite() {
            PermissionEntity existingGrant = PermissionEntity.builder()
                    .id(15L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(wm1)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(77L)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(existingGrant));

            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, existingGrant, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto adminReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(adminReq, "org-admin-id");

            assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(response.getGrantedByUserId()).isEqualTo("wm-1-id");
            assertThat(response.getWorkspaceId()).isEqualTo(77L);
        }

        @Test
        @DisplayName("E1. Revoked permissions behave according to lifecycle rules (permits new grant)")
        void revokedPermission_PermitsNewGrantWithNewProvenance() {
            // Revoked permission is NOT returned by findBy...AndRevokedFalse
            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of());

            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(wm1, folder, null, null)).thenReturn(AuthorityTier.WORKSPACE_MANAGER);
            when(evaluatorService.resolveWorkspaceIdForManager(wm1, folder, null)).thenReturn(55L);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity entity = inv.getArgument(0);
                entity.setId(99L);
                return entity;
            });

            PermissionRequestDto wmReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(wmReq, "wm-1-id");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(99L);
            assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(response.getGrantedByUserId()).isEqualTo("wm-1-id");
            assertThat(response.getWorkspaceId()).isEqualTo(55L);
        }

        @Test
        @DisplayName("E2. Expired permissions behave according to lifecycle rules (permits new grant)")
        void expiredPermission_PermitsNewGrantWithNewProvenance() {
            // Old grant has expired
            PermissionEntity expiredGrant = PermissionEntity.builder()
                    .id(16L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .expiresAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(expiredGrant));

            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(wm1, folder, null, null)).thenReturn(AuthorityTier.WORKSPACE_MANAGER);
            when(evaluatorService.resolveWorkspaceIdForManager(wm1, folder, null)).thenReturn(55L);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity entity = inv.getArgument(0);
                entity.setId(101L);
                return entity;
            });

            PermissionRequestDto wmReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(wmReq, "wm-1-id");

            // Brand new grant created because old one was expired
            assertThat(response.getId()).isEqualTo(101L);
            assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(response.getGrantedByUserId()).isEqualTo("wm-1-id");
        }

        @Test
        @DisplayName("E3. Suspended grant blocks unauthorized lower-tier override")
        void suspendedGrant_BlocksLowerTierOverride() {
            PermissionEntity suspendedGrant = PermissionEntity.builder()
                    .id(17L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .suspended(true)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(suspendedGrant));

            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(wm1, suspendedGrant, folder, null)).thenReturn(false);

            PermissionRequestDto wmReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(wmReq, "wm-1-id"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to override existing permission created with tier ORG_ADMIN");

            verify(permissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("F. Group duplicate behavior remains unchanged")
        void groupDuplicateBehavior_RemainsUnchanged() {
            GroupEntity targetGroup = GroupEntity.builder().id(50L).name("Dev Team").organization(org).build();
            when(groupRepository.findById(50L)).thenReturn(Optional.of(targetGroup));

            PermissionEntity existingGroupGrant = PermissionEntity.builder()
                    .id(30L)
                    .folder(folder)
                    .targetGroup(targetGroup)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.GROUP)
                    .grantedBy(wm1)
                    .grantorAuthorityTier(AuthorityTier.WORKSPACE_MANAGER)
                    .workspaceId(55L)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetGroup_IdAndFolder_IdAndRevokedFalse(50L, 100L))
                    .thenReturn(List.of(existingGroupGrant));

            when(evaluatorService.canAdministerFolderPermissions(wm2, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm2, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(wm2, existingGroupGrant, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto groupReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetGroupId(50L)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.GROUP)
                    .build();

            PermissionResponseDto response = permissionService.grantPermission(groupReq, "wm-2-id");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(30L);
            assertThat(response.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            assertThat(response.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.WORKSPACE_MANAGER);
            assertThat(response.getGrantedByUserId()).isEqualTo("wm-1-id"); // Preserved!
        }

        @Test
        @DisplayName("Document user duplicate blocks unauthorized lower-tier override")
        void documentUserDuplicate_BlocksLowerTierOverride() {
            PermissionEntity existingDocGrant = PermissionEntity.builder()
                    .id(40L)
                    .document(document)
                    .documentFileHash(document.getFileHash())
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndDocument_IdAndRevokedFalse("alice@org1.com", 200L))
                    .thenReturn(List.of(existingDocGrant));

            when(evaluatorService.canAdministerDocumentPermissions(wm1, document)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, null, document)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(wm1, existingDocGrant, null, document)).thenReturn(false);

            PermissionRequestDto docReq = PermissionRequestDto.builder()
                    .documentId(200L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(docReq, "wm-1-id"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to override existing permission created with tier ORG_ADMIN");

            verify(permissionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("2. Optimistic Locking Tests")
    class OptimisticLockingTests {

        @Test
        @DisplayName("PermissionEntity has @Version annotated field")
        void permissionEntity_HasVersionFieldAnnotatedWithVersion() throws NoSuchFieldException {
            Field versionField = PermissionEntity.class.getDeclaredField("version");
            assertThat(versionField).isNotNull();
            assertThat(versionField.getType()).isEqualTo(Long.class);
            assertThat(versionField.isAnnotationPresent(Version.class)).isTrue();
        }

        @Test
        @DisplayName("OptimisticLockingFailureException fails safely on stale entity update without silent overwrite")
        void optimisticLocking_FailsSafelyOnStaleUpdate() {
            PermissionEntity existingPerm = PermissionEntity.builder()
                    .id(50L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .version(1L)
                    .suspended(false)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByTargetUserEmailIgnoreCaseAndFolder_IdAndRevokedFalse("alice@org1.com", 100L))
                    .thenReturn(List.of(existingPerm));

            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, existingPerm, folder, null)).thenReturn(true);

            // Simulate concurrent write conflict triggering OptimisticLockingFailureException
            when(permissionRepository.save(any(PermissionEntity.class)))
                    .thenThrow(new OptimisticLockingFailureException("Row was updated or deleted by another transaction (or unsaved-value mapping was incorrect)"));

            PermissionRequestDto updateReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(updateReq, "org-admin-id"))
                    .isInstanceOf(OptimisticLockingFailureException.class)
                    .hasMessageContaining("Row was updated or deleted by another transaction");
        }

        @Test
        @DisplayName("GlobalExceptionHandler maps OptimisticLockingFailureException to HTTP 409 CONFLICT")
        void globalExceptionHandler_MapsOptimisticLockingFailure_ToConflict409() {
            GlobalExceptionHandler handler = new GlobalExceptionHandler();
            when(httpServletRequest.getRequestURI()).thenReturn("/api/v1/permissions");

            OptimisticLockingFailureException ex = new OptimisticLockingFailureException("Conflict on entity version");
            ResponseEntity<ErrorResponseDto> response = handler.handleOptimisticLockingFailureException(ex, httpServletRequest);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(409);
            assertThat(response.getBody().getMessage()).contains("The resource was updated concurrently by another operation");
            assertThat(response.getBody().getPath()).isEqualTo("/api/v1/permissions");
        }
    }

    @Nested
    @DisplayName("3. Duplicate Organization Grant Tests")
    class DuplicateOrganizationGrantTests {

        @Test
        @DisplayName("Scenario A: First ORGANIZATION grant succeeds on org resource")
        void scenarioA_FirstOrganizationGrant_Succeeds() {
            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                if (p.getId() == null) {
                    p.setId(101L);
                }
                return p;
            });

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp).isNotNull();
            assertThat(resp.getId()).isEqualTo(101L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
            assertThat(resp.getTargetOrgCode()).isEqualTo("ORG1");
            assertThat(resp.getRecipientType()).isEqualTo(RecipientType.ORGANIZATION);
            assertThat(resp.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
        }

        @Test
        @DisplayName("Scenario B: Second ORGANIZATION grant on same resource updates existing active grant instead of creating duplicate")
        void scenarioB_SecondOrganizationGrant_UpdatesExistingActiveGrant() {
            PermissionEntity existing = PermissionEntity.builder()
                    .id(101L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .suspended(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(existing));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, existing, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp).isNotNull();
            assertThat(resp.getId()).isEqualTo(101L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            verify(permissionRepository, times(1)).save(existing);
        }

        @Test
        @DisplayName("Scenario C: ORGANIZATION VIEW -> EDIT preserves original grantor and provenance")
        void scenarioC_ViewToEdit_PreservesGrantorProvenance() {
            PermissionEntity existing = PermissionEntity.builder()
                    .id(102L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .workspaceId(77L)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(existing));
            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(wm1, existing, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "wm-1-id");

            assertThat(resp).isNotNull();
            assertThat(resp.getId()).isEqualTo(102L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            assertThat(resp.getGrantedByUserId()).isEqualTo("org-admin-id");
            assertThat(resp.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.ORG_ADMIN);
            assertThat(resp.getWorkspaceId()).isEqualTo(77L);
        }

        @Test
        @DisplayName("Scenario D: ORGANIZATION EDIT -> VIEW actually downgrades access")
        void scenarioD_EditToView_DowngradesAccess() {
            PermissionEntity existing = PermissionEntity.builder()
                    .id(103L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(existing));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, existing, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
            assertThat(existing.getPermissionLevel()).isEqualTo(PermissionLevel.VIEW);
        }

        @Test
        @DisplayName("Scenario E: Same organization + different resource remains separate")
        void scenarioE_SameOrgDifferentResource_RemainsSeparate() {
            FolderEntity otherFolder = FolderEntity.builder().id(105L).name("Other Folder").ownerOrg(org).build();
            when(folderRepository.findById(105L)).thenReturn(Optional.of(otherFolder));

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 105L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, otherFolder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, otherFolder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, otherFolder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(1055L);
                return p;
            });

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(105L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(1055L);
            verify(permissionRepository).findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 105L);
        }

        @Test
        @DisplayName("Scenario F: Different organizations + same resource remain separate")
        void scenarioF_DifferentOrgsSameResource_RemainsSeparate() {
            when(organizationRepository.existsByOrgCode("ORG2")).thenReturn(true);
            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG2", 100L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(106L);
                return p;
            });

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG2")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(106L);
            assertThat(resp.getTargetOrgCode()).isEqualTo("ORG2");
        }

        @Test
        @DisplayName("Scenario G: Revoked historical grant allows new grant and preserves audit history")
        void scenarioG_RevokedHistoricalGrant_AllowsNewGrant() {
            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(107L);
                return p;
            });

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(107L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        }

        @Test
        @DisplayName("Scenario H1: Suspended matching grant blocks reactivation if caller cannot override inactivator")
        void scenarioH1_SuspendedGrant_BlocksReactivation_WithoutInactivatorAuthority() {
            PermissionEntity suspendedGrant = PermissionEntity.builder()
                    .id(108L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .suspended(true)
                    .revoked(false)
                    .revokedBy(orgAdmin)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(suspendedGrant));
            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(wm1, suspendedGrant, folder, null)).thenReturn(true);
            when(evaluatorService.canActorOverrideInactivator(wm1, orgAdmin, suspendedGrant, folder, null)).thenReturn(false);

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "wm-1-id"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to reactivate permission suspended by user admin@org1.com");
        }

        @Test
        @DisplayName("Scenario H2: Suspended matching grant is reactivated when caller has authority")
        void scenarioH2_SuspendedGrant_Reactivated_WithAuthority() {
            PermissionEntity suspendedGrant = PermissionEntity.builder()
                    .id(108L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .suspended(true)
                    .revoked(false)
                    .revokedBy(orgAdmin)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(suspendedGrant));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, suspendedGrant, folder, null)).thenReturn(true);
            when(evaluatorService.canActorOverrideInactivator(orgAdmin, orgAdmin, suspendedGrant, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(108L);
            assertThat(suspendedGrant.isSuspended()).isFalse();
            assertThat(suspendedGrant.getRevokedBy()).isNull();
            assertThat(suspendedGrant.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        }

        @Test
        @DisplayName("Scenario I: Deduplication does not merge ALLOW with EXCLUDE")
        void scenarioI_ExcludeBehavior_RemainsCorrect() {
            PermissionEntity excludeGrant = PermissionEntity.builder()
                    .id(109L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.EXCLUDE)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(excludeGrant));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(110L);
                return p;
            });

            PermissionRequestDto allowReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(allowReq, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(110L);
            assertThat(excludeGrant.getPermissionType()).isEqualTo(PermissionType.EXCLUDE);
        }

        @Test
        @DisplayName("Scenario J: Folder organization grant deduplication")
        void scenarioJ_FolderOrganizationGrant_Deduplication() {
            PermissionEntity folderGrant = PermissionEntity.builder()
                    .id(111L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(folderGrant));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, folderGrant, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(111L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        }

        @Test
        @DisplayName("Scenario K: Document organization grant deduplication by document ID")
        void scenarioK_DocumentOrganizationGrant_Deduplication() {
            PermissionEntity docGrant = PermissionEntity.builder()
                    .id(112L)
                    .document(document)
                    .documentFileHash(document.getFileHash())
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocument_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 200L)).thenReturn(List.of(docGrant));
            when(evaluatorService.canAdministerDocumentPermissions(orgAdmin, document)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, null, document)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, docGrant, null, document)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .documentId(200L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(112L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        }

        @Test
        @DisplayName("Scenario L: Document grant deduplication with file hash compatibility")
        void scenarioL_DocumentFileHashCompatibility() {
            PermissionEntity hashGrant = PermissionEntity.builder()
                    .id(113L)
                    .document(null)
                    .documentFileHash("hash-doc-200")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocument_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 200L)).thenReturn(List.of());
            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndDocumentFileHashAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", "hash-doc-200")).thenReturn(List.of(hashGrant));

            when(evaluatorService.canAdministerDocumentPermissions(orgAdmin, document)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, null, document)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, hashGrant, null, document)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .documentId(200L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(113L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            assertThat(hashGrant.getDocument()).isEqualTo(document);
        }

        @Test
        @DisplayName("Scenario M: OptimisticLockingFailureException on organization grant update is not swallowed")
        void scenarioM_OptimisticLockingBehavior_Intact() {
            PermissionEntity existing = PermissionEntity.builder()
                    .id(114L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .version(1L)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(existing));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, existing, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class)))
                    .thenThrow(new OptimisticLockingFailureException("Row updated concurrently"));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "org-admin-id"))
                    .isInstanceOf(OptimisticLockingFailureException.class);
        }

        @Test
        @DisplayName("Scenario N: Lower-tier user cannot override higher-tier organization grant")
        void scenarioN_LowerTierCannotOverrideHigherTierOrgGrant() {
            PermissionEntity orgGrant = PermissionEntity.builder()
                    .id(115L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(orgGrant));
            when(evaluatorService.canAdministerFolderPermissions(wm1, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(wm1, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(wm1, orgGrant, folder, null)).thenReturn(false);

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            assertThatThrownBy(() -> permissionService.grantPermission(req, "wm-1-id"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("User does not have authority to override existing permission created with tier ORG_ADMIN");
        }

        @Test
        @DisplayName("Scenario O: ORGANIZATION grant does not collide with USER_ORGANIZATION_MEMBER")
        void scenarioO_DoesNotCollideWithUserOrgMember() {
            PermissionEntity userOrgMemberGrant = PermissionEntity.builder()
                    .id(116L)
                    .folder(folder)
                    .targetUserEmail("alice@org1.com")
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(117L);
                return p;
            });

            PermissionRequestDto orgReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(orgReq, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(117L);
            assertThat(userOrgMemberGrant.getId()).isEqualTo(116L);
            assertThat(userOrgMemberGrant.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        }

        @Test
        @DisplayName("Scenario P: ORGANIZATION grant does not collide with GROUP")
        void scenarioP_DoesNotCollideWithGroup() {
            GroupEntity group = GroupEntity.builder().id(50L).name("Team A").build();
            PermissionEntity groupGrant = PermissionEntity.builder()
                    .id(118L)
                    .folder(folder)
                    .targetGroup(group)
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.GROUP)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(119L);
                return p;
            });

            PermissionRequestDto orgReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(orgReq, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(119L);
            assertThat(groupGrant.getId()).isEqualTo(118L);
        }

        @Test
        @DisplayName("Scenario Q: ORGANIZATION grant does not collide with USER_PERSONAL")
        void scenarioQ_DoesNotCollideWithUserPersonal() {
            PermissionEntity personalGrant = PermissionEntity.builder()
                    .id(120L)
                    .folder(folder)
                    .targetUserEmail("bob@external.com")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of());
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.resolveAuthorityTierAtCreation(orgAdmin, folder, null, null)).thenReturn(AuthorityTier.ORG_ADMIN);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> {
                PermissionEntity p = inv.getArgument(0);
                p.setId(121L);
                return p;
            });

            PermissionRequestDto orgReq = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(orgReq, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(121L);
            assertThat(personalGrant.getId()).isEqualTo(120L);
        }

        @Test
        @DisplayName("Scenario R: Multiple existing active duplicate organization grants are consolidated and siblings revoked")
        void scenarioR_MultipleExistingDuplicates_ConsolidatedAndSiblingsRevoked() {
            PermissionEntity canonical = PermissionEntity.builder()
                    .id(122L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            PermissionEntity sibling1 = PermissionEntity.builder()
                    .id(123L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            PermissionEntity sibling2 = PermissionEntity.builder()
                    .id(124L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(canonical, sibling1, sibling2));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.resolveDelegationCeiling(orgAdmin, folder, null)).thenReturn(PermissionLevel.EDIT);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, canonical, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionRequestDto req = PermissionRequestDto.builder()
                    .folderId(100L)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .build();

            PermissionResponseDto resp = permissionService.grantPermission(req, "org-admin-id");

            assertThat(resp.getId()).isEqualTo(122L);
            assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
            assertThat(canonical.isRevoked()).isFalse();
            assertThat(sibling1.isRevoked()).isTrue();
            assertThat(sibling1.getRevokedBy()).isEqualTo(orgAdmin);
            assertThat(sibling2.isRevoked()).isTrue();
            assertThat(sibling2.getRevokedBy()).isEqualTo(orgAdmin);
            verify(auditLogService, times(2)).logPermissionChange(argThat(cmd -> "REVOKE_DUPLICATE_ORGANIZATION_GRANT".equals(cmd.getAction())));
        }

        @Test
        @DisplayName("Scenario S: Revoking the organization grant revokes it and redundant siblings")
        void scenarioS_RevokingOrganizationGrant_RevokesCanonicalAndSiblings() {
            PermissionEntity canonical = PermissionEntity.builder()
                    .id(125L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.EDIT)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            PermissionEntity sibling = PermissionEntity.builder()
                    .id(126L)
                    .folder(folder)
                    .targetOrgCode("ORG1")
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.ORGANIZATION)
                    .grantedBy(orgAdmin)
                    .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                    .revoked(false)
                    .build();

            when(permissionRepository.findById(125L)).thenReturn(Optional.of(canonical));
            when(permissionRepository.findByRecipientTypeAndTargetOrgCodeIgnoreCaseAndFolder_IdAndRevokedFalse(
                    RecipientType.ORGANIZATION, "ORG1", 100L)).thenReturn(List.of(canonical, sibling));
            when(evaluatorService.canAdministerFolderPermissions(orgAdmin, folder)).thenReturn(true);
            when(evaluatorService.canActorOverrideGrant(orgAdmin, canonical, folder, null)).thenReturn(true);
            when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            PermissionResponseDto resp = permissionService.revokePermission(125L, "org-admin-id");

            assertThat(resp.isRevoked()).isTrue();
            assertThat(canonical.isRevoked()).isTrue();
            assertThat(sibling.isRevoked()).isTrue();
        }
    }
}

