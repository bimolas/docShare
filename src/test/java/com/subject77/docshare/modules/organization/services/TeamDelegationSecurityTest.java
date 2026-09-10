package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.GrantMemberAccessRequestDto;
import com.subject77.docshare.modules.organization.dto.TeamMemberAccessResponseDto;
import com.subject77.docshare.modules.organization.dto.UpdateMemberAccessRequestDto;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.permission.services.PermissionEvaluatorService;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TeamDelegationSecurityTest {

    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private GroupRepository groupRepository;
    @Mock
    private UserGroupRepository userGroupRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FolderRepository folderRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private PermissionEvaluatorService permissionEvaluatorService;
    @Mock
    private PermissionCacheService permissionCacheService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @InjectMocks
    private TeamDelegationServiceImpl teamDelegationService;

    private OrganizationEntity testOrg;
    private GroupEntity teamAlpha;
    private GroupEntity teamBeta;
    private UserEntity managerUser;
    private UserEntity memberUser;
    private UserEntity outsiderUser;
    private FolderEntity boundaryRootFolder;
    private FolderEntity childFolder;
    private FolderEntity outsideFolder;
    private DocumentEntity childDoc;
    private PermissionEntity boundaryPerm;

    @BeforeEach
    void setUp() {
        testOrg = OrganizationEntity.builder()
                .orgCode("ORG-TEST")
                .name("Test Org")
                .build();

        teamAlpha = GroupEntity.builder()
                .id(10L)
                .name("Team Alpha")
                .organization(testOrg)
                .status(GroupStatus.ACTIVE)
                .build();

        teamBeta = GroupEntity.builder()
                .id(20L)
                .name("Team Beta")
                .organization(testOrg)
                .status(GroupStatus.ACTIVE)
                .build();

        managerUser = UserEntity.builder()
                .id("mgr-id")
                .email("manager@test.com")
                .firstName("Alice")
                .lastName("Manager")
                .role(Role.ROLE_USER)
                .organization(testOrg)
                .build();

        memberUser = UserEntity.builder()
                .id("mbr-id")
                .email("member@test.com")
                .firstName("Bob")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .organization(testOrg)
                .build();

        outsiderUser = UserEntity.builder()
                .id("out-id")
                .email("outsider@test.com")
                .firstName("Eve")
                .lastName("Outsider")
                .role(Role.ROLE_USER)
                .organization(testOrg)
                .build();

        boundaryRootFolder = FolderEntity.builder()
                .id(100L)
                .name("Boundary Root")
                .ownerOrg(testOrg)
                .build();

        childFolder = FolderEntity.builder()
                .id(101L)
                .name("Child Folder")
                .parentFolder(boundaryRootFolder)
                .ownerOrg(testOrg)
                .build();

        outsideFolder = FolderEntity.builder()
                .id(999L)
                .name("Unrelated Folder")
                .ownerOrg(testOrg)
                .build();

        childDoc = DocumentEntity.builder()
                .id(201L)
                .fileName("spec.pdf")
                .folder(childFolder)
                .ownerOrg(testOrg)
                .build();

        boundaryPerm = PermissionEntity.builder()
                .id(50L)
                .folder(boundaryRootFolder)
                .recipientType(RecipientType.GROUP)
                .targetGroup(teamAlpha)
                .permissionLevel(PermissionLevel.EDIT)
                .permissionType(PermissionType.BOUNDARY)
                .revoked(false)
                .suspended(false)
                .build();
    }

    private void mockActiveManager() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(teamAlpha));
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ORG-TEST")).thenReturn(Optional.of(teamAlpha));

        UserGroupEntity managerGroupMembership = UserGroupEntity.builder()
                .id(1L)
                .user(managerUser)
                .group(teamAlpha)
                .role(GroupMemberRole.MANAGER)
                .build();

        when(userGroupRepository.findByUser_IdAndGroup_Id("mgr-id", 10L))
                .thenReturn(Optional.of(managerGroupMembership));
        when(permissionEvaluatorService.isUserAdminOfOrg(managerUser, "ORG-TEST")).thenReturn(false);
    }

    private void mockActiveBoundary() {
        when(permissionRepository.findByTargetGroup_Id(10L)).thenReturn(List.of(boundaryPerm));
    }

    @Test
    @DisplayName("1. Team Manager can create Team-linked delegation inside boundary (valid member, within ceiling)")
    void testTeamManager_CreateDelegation_Success() {
        mockActiveManager();
        mockActiveBoundary();

        when(permissionEvaluatorService.isSameOrAncestorFolder(boundaryRootFolder, childFolder)).thenReturn(true);
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(memberUser));
        when(userGroupRepository.findByUser_IdAndGroup_Id("mbr-id", 10L))
                .thenReturn(Optional.of(UserGroupEntity.builder().user(memberUser).group(teamAlpha).role(GroupMemberRole.MEMBER).build()));

        when(folderRepository.findById(101L)).thenReturn(Optional.of(childFolder));

        when(permissionRepository.save(any(PermissionEntity.class))).thenAnswer(invocation -> {
            PermissionEntity p = invocation.getArgument(0);
            p.setId(300L);
            p.setCreatedAt(Instant.now());
            return p;
        });

        GrantMemberAccessRequestDto req = GrantMemberAccessRequestDto.builder()
                .userEmail("member@test.com")
                .folderId(101L)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        TeamMemberAccessResponseDto resp = teamDelegationService.grantMemberAccess("ORG-TEST", 10L, req, managerUser);

        assertThat(resp).isNotNull();
        assertThat(resp.getPermissionId()).isEqualTo(300L);
        assertThat(resp.getUserEmail()).isEqualTo("member@test.com");
        assertThat(resp.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        assertThat(resp.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.TEAM_MANAGER);

        ArgumentCaptor<PermissionEntity> captor = ArgumentCaptor.forClass(PermissionEntity.class);
        verify(permissionRepository).save(captor.capture());
        PermissionEntity saved = captor.getValue();
        assertThat(saved.getRecipientType()).isEqualTo(RecipientType.USER_ORGANIZATION_MEMBER);
        assertThat(saved.getTargetGroup()).isEqualTo(teamAlpha);
        assertThat(saved.getPermissionType()).isEqualTo(PermissionType.ALLOW);
        assertThat(saved.getPermissionLevel()).isEqualTo(PermissionLevel.DOWNLOAD);
        assertThat(saved.getGrantorAuthorityTier()).isEqualTo(AuthorityTier.TEAM_MANAGER);
        assertThat(saved.getGrantedBy()).isEqualTo(managerUser);
    }

    @Test
    @DisplayName("2. Team Manager cannot delegate to a non-member of the team")
    void testTeamManager_CannotDelegateToNonMember() {
        mockActiveManager();
        mockActiveBoundary();

        when(userRepository.findByEmail("outsider@test.com")).thenReturn(Optional.of(outsiderUser));
        when(userGroupRepository.findByUser_IdAndGroup_Id("out-id", 10L)).thenReturn(Optional.empty());

        GrantMemberAccessRequestDto req = GrantMemberAccessRequestDto.builder()
                .userEmail("outsider@test.com")
                .folderId(101L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> teamDelegationService.grantMemberAccess("ORG-TEST", 10L, req, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("is not an active member of team");
    }

    @Test
    @DisplayName("3. Team Manager cannot delegate outside the boundary")
    void testTeamManager_CannotDelegateOutsideBoundary() {
        mockActiveManager();
        mockActiveBoundary();

        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(memberUser));
        when(userGroupRepository.findByUser_IdAndGroup_Id("mbr-id", 10L))
                .thenReturn(Optional.of(UserGroupEntity.builder().user(memberUser).group(teamAlpha).role(GroupMemberRole.MEMBER).build()));

        when(folderRepository.findById(999L)).thenReturn(Optional.of(outsideFolder));
        when(permissionEvaluatorService.isSameOrAncestorFolder(boundaryRootFolder, outsideFolder)).thenReturn(false);

        GrantMemberAccessRequestDto req = GrantMemberAccessRequestDto.builder()
                .userEmail("member@test.com")
                .folderId(999L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> teamDelegationService.grantMemberAccess("ORG-TEST", 10L, req, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Target resource is outside the team access boundary");
    }

    @Test
    @DisplayName("4. Team Manager cannot exceed the boundary ceiling")
    void testTeamManager_CannotExceedBoundaryCeiling() {
        // Boundary ceiling is VIEW
        boundaryPerm.setPermissionLevel(PermissionLevel.VIEW);

        mockActiveManager();
        mockActiveBoundary();

        when(permissionEvaluatorService.isSameOrAncestorFolder(boundaryRootFolder, childFolder)).thenReturn(true);
        when(userRepository.findByEmail("member@test.com")).thenReturn(Optional.of(memberUser));
        when(userGroupRepository.findByUser_IdAndGroup_Id("mbr-id", 10L))
                .thenReturn(Optional.of(UserGroupEntity.builder().user(memberUser).group(teamAlpha).role(GroupMemberRole.MEMBER).build()));

        when(folderRepository.findById(101L)).thenReturn(Optional.of(childFolder));

        GrantMemberAccessRequestDto req = GrantMemberAccessRequestDto.builder()
                .userEmail("member@test.com")
                .folderId(101L)
                .permissionLevel(PermissionLevel.EDIT) // exceeds VIEW
                .build();

        assertThatThrownBy(() -> teamDelegationService.grantMemberAccess("ORG-TEST", 10L, req, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("exceeds boundary ceiling of VIEW");
    }

    @Test
    @DisplayName("5. Team Manager cannot mutate a higher-authority grant (e.g. granted by ORG_ADMIN)")
    void testTeamManager_CannotMutateHigherAuthorityGrant() {
        mockActiveManager();
        mockActiveBoundary();

        PermissionEntity adminGrant = PermissionEntity.builder()
                .id(400L)
                .folder(childFolder)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetGroup(teamAlpha)
                .targetUserEmail("member@test.com")
                .permissionLevel(PermissionLevel.VIEW)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN) // Higher authority!
                .revoked(false)
                .build();

        when(permissionRepository.findById(400L)).thenReturn(Optional.of(adminGrant));

        UpdateMemberAccessRequestDto updateReq = UpdateMemberAccessRequestDto.builder()
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        assertThatThrownBy(() -> teamDelegationService.updateMemberAccess("ORG-TEST", 10L, 400L, updateReq, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Cannot modify permission created with higher authority tier: ORG_ADMIN");
    }

    @Test
    @DisplayName("6. Team Manager cannot mutate a Team delegation belonging to another Team")
    void testTeamManager_CannotMutateOtherTeamDelegation() {
        mockActiveManager();
        mockActiveBoundary();

        PermissionEntity otherTeamDelegation = PermissionEntity.builder()
                .id(405L)
                .folder(childFolder)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetGroup(teamBeta) // Belongs to Team Beta!
                .targetUserEmail("member@test.com")
                .permissionLevel(PermissionLevel.VIEW)
                .grantorAuthorityTier(AuthorityTier.TEAM_MANAGER)
                .revoked(false)
                .build();

        when(permissionRepository.findById(405L)).thenReturn(Optional.of(otherTeamDelegation));

        UpdateMemberAccessRequestDto updateReq = UpdateMemberAccessRequestDto.builder()
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .build();

        assertThatThrownBy(() -> teamDelegationService.updateMemberAccess("ORG-TEST", 10L, 405L, updateReq, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Permission does not belong to team: 10");
    }

    @Test
    @DisplayName("7. Non-manager of the team cannot perform member access mutations")
    void testNonManager_CannotMutateMemberAccess() {
        when(groupRepository.findByIdAndOrganization_OrgCode(10L, "ORG-TEST")).thenReturn(Optional.of(teamAlpha));

        // memberUser is only a MEMBER, not MANAGER
        UserGroupEntity regularMembership = UserGroupEntity.builder()
                .id(2L)
                .user(memberUser)
                .group(teamAlpha)
                .role(GroupMemberRole.MEMBER)
                .build();

        when(userGroupRepository.findByUser_IdAndGroup_Id("mbr-id", 10L))
                .thenReturn(Optional.of(regularMembership));
        when(permissionEvaluatorService.isUserAdminOfOrg(memberUser, "ORG-TEST")).thenReturn(false);

        GrantMemberAccessRequestDto req = GrantMemberAccessRequestDto.builder()
                .userEmail("member@test.com")
                .folderId(101L)
                .permissionLevel(PermissionLevel.VIEW)
                .build();

        assertThatThrownBy(() -> teamDelegationService.grantMemberAccess("ORG-TEST", 10L, req, memberUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("User is not the active Team Manager of team: 10");
    }

    @Test
    @DisplayName("8. Team Manager cannot revoke a normal direct user share (targetGroup == null)")
    void testTeamManager_CannotRevokeDirectShare() {
        mockActiveManager();

        PermissionEntity directShare = PermissionEntity.builder()
                .id(600L)
                .folder(childFolder)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetGroup(null) // Normal direct user share!
                .targetUserEmail("member@test.com")
                .permissionLevel(PermissionLevel.VIEW)
                .grantorAuthorityTier(AuthorityTier.ORG_RESOURCE_CREATOR)
                .revoked(false)
                .build();

        when(permissionRepository.findById(600L)).thenReturn(Optional.of(directShare));

        assertThatThrownBy(() -> teamDelegationService.revokeMemberAccess("ORG-TEST", 10L, 600L, managerUser))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Permission does not belong to team: 10");
    }
}
