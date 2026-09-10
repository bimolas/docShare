package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.services.NotificationService;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.entities.UserGroupEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.events.PermissionEventPublisher;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.services.ExternalSharingPolicyEvaluator;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SharedWithMeVerificationTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private PermissionEvaluatorService evaluatorService;

    @Mock
    private ExternalSharingPolicyEvaluator externalSharingPolicyEvaluator;

    @Mock
    private PermissionCacheService cacheService;

    @Mock
    private PermissionEventPublisher eventPublisher;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private UserEntity currentUser;
    private UserEntity grantorAlice;
    private OrganizationEntity orgAcme;
    private OrganizationEntity orgBeta;

    @BeforeEach
    void setUp() {
        currentUser = UserEntity.builder()
                .id("user-bob")
                .email("bob@example.com")
                .firstName("Bob")
                .lastName("Builder")
                .build();

        grantorAlice = UserEntity.builder()
                .id("user-alice")
                .email("alice@example.com")
                .firstName("Alice")
                .lastName("Smith")
                .build();

        orgAcme = OrganizationEntity.builder()
                .orgCode("ACME")
                .name("Acme Corporation")
                .build();

        orgBeta = OrganizationEntity.builder()
                .orgCode("BETA")
                .name("Beta Corporation")
                .build();
    }

    // =========================================================================
    // Invariant 1: COLLABORATION RESOLUTION
    // =========================================================================

    @Test
    @DisplayName("Invariant 1: Collaboration share is returned ONLY when ACTIVE and not expired")
    void invariant1_collaborationResolution_activeOnly() {
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Collab Project").build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(1L).name("Joint Venture").ownerOrganization(orgAcme).partnerOrganization(orgBeta)
                .status(CollaborationStatus.ACTIVE).endsAt(Instant.now().plus(10, ChronoUnit.DAYS)).build();

        CollaborationEntity expiredCollab = CollaborationEntity.builder()
                .id(2L).name("Expired Venture").ownerOrganization(orgAcme).partnerOrganization(orgBeta)
                .status(CollaborationStatus.ACTIVE).endsAt(Instant.now().minus(1, ChronoUnit.DAYS)).build();

        CollaborationEntity revokedCollab = CollaborationEntity.builder()
                .id(3L).name("Revoked Venture").ownerOrganization(orgAcme).partnerOrganization(orgBeta)
                .status(CollaborationStatus.REVOKED).endsAt(Instant.now().plus(10, ChronoUnit.DAYS)).build();

        PermissionEntity pActive = PermissionEntity.builder()
                .id(10L).folder(sharedFolder).grantedBy(grantorAlice).collaboration(activeCollab)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        PermissionEntity pExpired = PermissionEntity.builder()
                .id(20L).folder(sharedFolder).grantedBy(grantorAlice).collaboration(expiredCollab)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        PermissionEntity pRevoked = PermissionEntity.builder()
                .id(30L).folder(sharedFolder).grantedBy(grantorAlice).collaboration(revokedCollab)
                .targetUserEmail("bob@example.com").permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(List.of(pActive, pExpired, pRevoked));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(10L);
        assertThat(response.getSharedFolders().get(0).getSourceLabel()).isEqualTo("COLLABORATION SHARE");
    }

    @Test
    @DisplayName("Invariant 1: Collaboration org/team share fails if participant org membership is suspended")
    void invariant1_collaborationResolution_suspendedOrgDoesNotQualify() {
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Collab Project").build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(1L).name("Joint Venture").ownerOrganization(orgAcme).partnerOrganization(orgBeta)
                .status(CollaborationStatus.ACTIVE).build();

        // Target is organization BETA, but user has NO active membership in BETA
        PermissionEntity pOrgShare = PermissionEntity.builder()
                .id(10L).folder(sharedFolder).grantedBy(grantorAlice).collaboration(activeCollab)
                .recipientType(RecipientType.ORGANIZATION).targetOrgCode("BETA")
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        // User is only active in ACME, not BETA
        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(currentUser).organization(orgAcme).status(MembershipStatus.ACTIVE).build()));

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(List.of(pOrgShare));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    @Test
    @DisplayName("Invariant 1: Collaboration share targeting non-participating organization is strictly excluded")
    void invariant1_collaborationResolution_nonParticipantOrgDoesNotQualify() {
        FolderEntity sharedFolder = FolderEntity.builder().id(101L).name("Collab Project").build();

        CollaborationEntity activeCollab = CollaborationEntity.builder()
                .id(1L).name("Joint Venture").ownerOrganization(orgAcme).partnerOrganization(orgBeta)
                .status(CollaborationStatus.ACTIVE).build();

        OrganizationEntity orgGamma = OrganizationEntity.builder().orgCode("GAMMA").name("Gamma Corp").build();

        // Grant targets GAMMA, which is NOT in this collaboration (only ACME and BETA are)
        PermissionEntity pGammaShare = PermissionEntity.builder()
                .id(10L).folder(sharedFolder).grantedBy(grantorAlice).collaboration(activeCollab)
                .recipientType(RecipientType.ORGANIZATION).targetOrgCode("GAMMA")
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        // User is active in GAMMA
        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(currentUser).organization(orgGamma).status(MembershipStatus.ACTIVE).build()));

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(List.of(pGammaShare));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
    }

    // =========================================================================
    // Invariant 2: QUERY SCOPE
    // =========================================================================

    @Test
    @DisplayName("Invariant 2: Query candidate retrieval passes exact bounded trust scopes (userEmail, teamIds, orgCodes)")
    void invariant2_queryScope_boundedParameters() {
        GroupEntity team1 = GroupEntity.builder().id(1001L).organization(orgAcme).build();
        GroupEntity team2 = GroupEntity.builder().id(1002L).organization(orgAcme).build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(currentUser).organization(orgAcme).status(MembershipStatus.ACTIVE).build()));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(
                        UserGroupEntity.builder().user(currentUser).group(team1).build(),
                        UserGroupEntity.builder().user(currentUser).group(team2).build()
                ));

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(Collections.emptyList());

        permissionService.getSharedWithMe(currentUser);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> teamCaptor = ArgumentCaptor.forClass(Collection.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> orgCaptor = ArgumentCaptor.forClass(Collection.class);

        verify(permissionRepository).findSharedWithMeCandidates(
                eq("bob@example.com"),
                eq("user-bob"),
                teamCaptor.capture(),
                eq(true),
                orgCaptor.capture(),
                eq(true),
                any(Instant.class)
        );

        assertThat(teamCaptor.getValue()).containsExactlyInAnyOrder(1001L, 1002L);
        assertThat(orgCaptor.getValue()).containsExactly("ACME");
    }

    // =========================================================================
    // Invariant 3: LEGACY FALLBACK
    // =========================================================================

    @Test
    @DisplayName("Invariant 3: An empty candidate result NEVER triggers fallback to findByTargetUserEmail")
    void invariant3_legacyFallback_cannotReintroduceUserPersonalOnlyBehavior() {
        // findSharedWithMeCandidates returns authoritative empty list
        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(Collections.emptyList());

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).isEmpty();

        // Older legacy method must NEVER be invoked when authoritative query returned empty list
        verify(permissionRepository, never()).findByTargetUserEmail(anyString());
        verify(permissionRepository, never()).findByTargetUserEmailOrderByCreatedAtDescIdDesc(anyString());
    }

    @Test
    @DisplayName("Invariant 3: Candidate query database failure propagates rather than degrading to personal-only")
    void invariant3_queryFailure_throwsAndDoesNotFallBack() {
        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenThrow(new RuntimeException("Postgres connection timeout"));

        assertThatThrownBy(() -> permissionService.getSharedWithMe(currentUser))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Postgres connection timeout");

        verify(permissionRepository, never()).findByTargetUserEmail(anyString());
        verify(permissionRepository, never()).findByTargetUserEmailOrderByCreatedAtDescIdDesc(anyString());
    }

    // =========================================================================
    // Invariant 4: WORKSPACE ALLOCATION
    // =========================================================================

    @Test
    @DisplayName("Invariant 4: Team assigned to workspace root is EXCLUDED; explicit team share of normal folder is INCLUDED")
    void invariant4_workspaceAllocation_teamRootExcluded_normalFolderIncluded() {
        FolderEntity wsRootFolder = FolderEntity.builder().id(501L).name("Workspace Root Container").build();
        FolderEntity normalFolder = FolderEntity.builder().id(502L).name("Normal Project Folder").build();
        GroupEntity team = GroupEntity.builder().id(100L).name("Eng Team").organization(orgAcme).build();

        PermissionEntity wsRootPerm = PermissionEntity.builder()
                .id(1L).folder(wsRootFolder).grantedBy(grantorAlice).targetGroup(team)
                .recipientType(RecipientType.GROUP).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        PermissionEntity normalFolderPerm = PermissionEntity.builder()
                .id(2L).folder(normalFolder).grantedBy(grantorAlice).targetGroup(team)
                .recipientType(RecipientType.GROUP).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(currentUser).organization(orgAcme).status(MembershipStatus.ACTIVE).build()));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(UserGroupEntity.builder().user(currentUser).group(team).build()));

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(List.of(wsRootPerm, normalFolderPerm));

        when(projectWorkspaceRepository.findByRootFolder_IdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            List<com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity> result = new java.util.ArrayList<>();
            if (ids.contains(501L)) {
                result.add(com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity.builder()
                        .id(999L).rootFolder(FolderEntity.builder().id(501L).build()).build());
            }
            return result;
        });

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        // Workspace root container is strictly excluded; normal folder is included
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getResourceId()).isEqualTo(502L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Normal Project Folder");
        assertThat(response.getSharedFolders().get(0).getSourceLabel()).isEqualTo("TEAM SHARE");
    }

    // =========================================================================
    // Invariant 5: COLLABORATION ROOT
    // =========================================================================

    @Test
    @DisplayName("Invariant 5: Collaboration root container is EXCLUDED; content inside collaboration is INCLUDED")
    void invariant5_collaborationRoot_rootExcluded_contentInsideIncluded() {
        FolderEntity collabRootFolder = FolderEntity.builder().id(601L).name("Collab Root Container").build();
        FolderEntity insideFolder = FolderEntity.builder().id(602L).name("Quarterly Deliverables").build();

        CollaborationEntity collab = CollaborationEntity.builder()
                .id(88L).name("Joint Venture").rootFolder(collabRootFolder)
                .ownerOrganization(orgAcme).partnerOrganization(orgBeta)
                .status(CollaborationStatus.ACTIVE).build();

        PermissionEntity rootPerm = PermissionEntity.builder()
                .id(1L).folder(collabRootFolder).grantedBy(grantorAlice).collaboration(collab)
                .targetUserEmail("bob@example.com").recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        PermissionEntity insidePerm = PermissionEntity.builder()
                .id(2L).folder(insideFolder).grantedBy(grantorAlice).collaboration(collab)
                .targetUserEmail("bob@example.com").recipientType(RecipientType.USER_PERSONAL)
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(List.of(rootPerm, insidePerm));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        // Collaboration root container is strictly excluded; content folder is included
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getResourceId()).isEqualTo(602L);
        assertThat(response.getSharedFolders().get(0).getName()).isEqualTo("Quarterly Deliverables");
        assertThat(response.getSharedFolders().get(0).getSourceLabel()).isEqualTo("COLLABORATION SHARE");
    }

    // =========================================================================
    // Invariant 6: MULTI-GRANT (direct VIEW + team EDIT + organization VIEW)
    // =========================================================================

    @Test
    @DisplayName("Invariant 6: direct VIEW + team EDIT + organization VIEW => 1 card, effective EDIT, all 3 source labels preserved")
    void invariant6_multiGrant_directView_teamEdit_orgView() {
        FolderEntity sharedFolder = FolderEntity.builder().id(700L).name("Tri-Grant Folder").build();
        GroupEntity devTeam = GroupEntity.builder().id(50L).name("Dev Team").organization(orgAcme).build();

        UserOrganizationEntity userOrg = UserOrganizationEntity.builder()
                .user(currentUser).organization(orgAcme).status(MembershipStatus.ACTIVE).build();
        UserGroupEntity userGroup = UserGroupEntity.builder()
                .user(currentUser).group(devTeam).build();

        Instant t0 = Instant.now().minus(3, ChronoUnit.HOURS);
        Instant t1 = Instant.now().minus(2, ChronoUnit.HOURS);
        Instant t2 = Instant.now().minus(1, ChronoUnit.HOURS);

        // 1. Direct VIEW grant
        PermissionEntity directView = PermissionEntity.builder()
                .id(101L).folder(sharedFolder).grantedBy(grantorAlice).targetUserEmail("bob@example.com")
                .recipientType(RecipientType.USER_PERSONAL).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(t0).build();

        // 2. Team EDIT grant
        PermissionEntity teamEdit = PermissionEntity.builder()
                .id(102L).folder(sharedFolder).grantedBy(grantorAlice).targetGroup(devTeam)
                .recipientType(RecipientType.GROUP).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT).createdAt(t1).build();

        // 3. Organization VIEW grant
        PermissionEntity orgView = PermissionEntity.builder()
                .id(103L).folder(sharedFolder).grantedBy(grantorAlice).targetOrgCode("ACME")
                .recipientType(RecipientType.ORGANIZATION).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(t2).build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(userOrg));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(userGroup));

        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(List.of(directView, teamEdit, orgView));

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        // Exactly ONE resource card
        assertThat(response.getSharedFolders()).hasSize(1);
        SharedItemDto card = response.getSharedFolders().get(0);

        // Resource identifier
        assertThat(card.getResourceId()).isEqualTo(700L);

        // Effective permission resolves to EDIT (highest level among VIEW, EDIT, VIEW)
        assertThat(card.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);

        // Primary sourceLabel is assigned to the grant conferring highest level (team EDIT)
        assertThat(card.getSourceLabel()).isEqualTo("TEAM SHARE");

        // All THREE distinct source labels preserved
        assertThat(card.getSourceLabels()).containsExactlyInAnyOrder("DIRECT SHARE", "TEAM SHARE", "ORGANIZATION SHARE");

        // Total contributing grants count and IDs
        assertThat(card.getMatchingGrantCount()).isEqualTo(3);
        assertThat(card.getMatchedPermissionIds()).containsExactlyInAnyOrder(101L, 102L, 103L);

        // Multi-grant explanation in sourceDescription
        assertThat(card.getSourceDescription()).contains("3 grants, highest: EDIT");
    }

    // =========================================================================
    // Invariant 7: SHARED BY ME
    // =========================================================================

    @Test
    @DisplayName("Invariant 7: Shared By Me returns explicit shares, excludes workspace roots, excludes share links")
    void invariant7_sharedByMe_explicitAppears_workspaceRootExcluded_shareLinksExcluded() {
        FolderEntity normalFolder = FolderEntity.builder().id(801L).name("Shared Folder").build();
        FolderEntity wsRootFolder = FolderEntity.builder().id(802L).name("Workspace Container").build();
        DocumentEntity doc = DocumentEntity.builder().id(803L).fileName("Doc.pdf").status(DocumentStatus.ACTIVE).build();

        // 1. Explicit share created by current user -> INCLUDED
        PermissionEntity explicitPerm = PermissionEntity.builder()
                .id(1L).grantedBy(currentUser).folder(normalFolder).targetUserEmail("alice@example.com")
                .recipientType(RecipientType.USER_PERSONAL).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        // 2. Workspace container allocation created by current user -> EXCLUDED
        PermissionEntity wsPerm = PermissionEntity.builder()
                .id(2L).grantedBy(currentUser).folder(wsRootFolder)
                .recipientType(RecipientType.GROUP).permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        // 3. Bearer share link created by current user -> EXCLUDED
        PermissionEntity linkPerm = PermissionEntity.builder()
                .id(3L).grantedBy(currentUser).document(doc).shareLinkToken("raw-secret-token")
                .permissionType(PermissionType.ALLOW).permissionLevel(PermissionLevel.VIEW).createdAt(Instant.now()).build();

        when(permissionRepository.findByGrantedBy_IdOrderByCreatedAtDescIdDesc("user-bob"))
                .thenReturn(List.of(explicitPerm, wsPerm, linkPerm));
        when(projectWorkspaceRepository.findByRootFolder_IdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            List<com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity> result = new java.util.ArrayList<>();
            if (ids.contains(802L)) {
                result.add(com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity.builder()
                        .id(998L).rootFolder(FolderEntity.builder().id(802L).build()).build());
            }
            return result;
        });

        SharedByMeResponseDto response = permissionService.getSharedByMe(currentUser, true);

        // Only explicit share appears; workspace root and bearer share link are excluded
        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(1L);
        assertThat(response.getSharedFolders().get(0).getResourceId()).isEqualTo(801L);
        assertThat(response.getSharedDocuments()).isEmpty();
    }

    // =========================================================================
    // Invariant 8: SECURITY (Membership alone does not expose metadata)
    // =========================================================================

    @Test
    @DisplayName("Invariant 8: Membership alone does not expose resource metadata in Shared With Me")
    void invariant8_security_membershipAloneDoesNotExposeResource() {
        // User belongs to org ACME and team "Dev Team"
        GroupEntity devTeam = GroupEntity.builder().id(50L).name("Dev Team").organization(orgAcme).build();

        when(userOrganizationRepository.findByUser_IdAndStatus("user-bob", MembershipStatus.ACTIVE))
                .thenReturn(List.of(UserOrganizationEntity.builder().user(currentUser).organization(orgAcme).status(MembershipStatus.ACTIVE).build()));
        when(userGroupRepository.findByUser_Id("user-bob"))
                .thenReturn(List.of(UserGroupEntity.builder().user(currentUser).group(devTeam).build()));

        // No explicit permissions target this user, their team, or ACME organization
        when(permissionRepository.findSharedWithMeCandidates(any(), any(), any(), anyBoolean(), any(), anyBoolean(), any()))
                .thenReturn(Collections.emptyList());

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(currentUser);

        // Shared With Me is strictly empty; ambient organization / team files do NOT enter
        assertThat(response.getSharedFolders()).isEmpty();
        assertThat(response.getSharedDocuments()).isEmpty();
    }
}
