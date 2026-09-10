package com.subject77.docshare.modules.collaboration.services;

import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.dto.AddMemberRequestDto;
import com.subject77.docshare.modules.organization.dto.MemberStatusChangeRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationRequestDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.services.OrganizationService;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.enums.ScopeType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import com.subject77.docshare.modules.permission.services.PermissionCacheService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
public class CollaborationMembershipLifecycleIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private CapabilityEvaluatorService capabilityEvaluatorService;

    @MockitoSpyBean
    private PermissionCacheService permissionCacheService;

    @MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @MockitoBean
    private com.subject77.docshare.modules.audit.services.AuditLogService auditLogService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    private UserEntity adminA;
    private UserEntity adminB;
    private UserEntity userA1;
    private UserEntity userA2;
    private UserEntity userB1;
    private UserEntity crossOrgUser;

    private OrganizationEntity orgA;
    private OrganizationEntity orgB;
    private CollaborationEntity collaboration;
    private FolderEntity collabFolder;

    @BeforeEach
    void setUp() {
        String unique = UUID.randomUUID().toString().substring(0, 8);

        adminA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_a_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("A")
                .role(Role.ROLE_USER)
                .build());

        adminB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_b_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("B")
                .role(Role.ROLE_USER)
                .build());

        userA1 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("usera1_" + unique + "@test.com")
                .firstName("User")
                .lastName("A1")
                .role(Role.ROLE_USER)
                .build());

        userA2 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("usera2_" + unique + "@test.com")
                .firstName("User")
                .lastName("A2")
                .role(Role.ROLE_USER)
                .build());

        userB1 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("userb1_" + unique + "@test.com")
                .firstName("User")
                .lastName("B1")
                .role(Role.ROLE_USER)
                .build());

        crossOrgUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("cross_" + unique + "@test.com")
                .firstName("Cross")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        String orgCodeA = "ORGA_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCodeA)
                .name("Organization Alpha")
                .build(), adminA);
        orgA = organizationRepository.findById(orgCodeA).orElseThrow();

        String orgCodeB = "ORGB_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        organizationService.createOrganization(OrganizationRequestDto.builder()
                .orgCode(orgCodeB)
                .name("Organization Beta")
                .build(), adminB);
        orgB = organizationRepository.findById(orgCodeB).orElseThrow();

        // Add userA1 and userA2 to Org A
        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(userA1.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminA);

        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(userA2.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminA);

        // Add userB1 to Org B
        organizationService.addMemberToOrganization(orgB.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(userB1.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminB);

        // Add crossOrgUser to both Org A and Org B
        organizationService.addMemberToOrganization(orgA.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(crossOrgUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminA);

        organizationService.addMemberToOrganization(orgB.getOrgCode(), AddMemberRequestDto.builder()
                .userEmail(crossOrgUser.getEmail())
                .role(Role.ROLE_USER)
                .build(), adminB);

        collabFolder = folderRepository.save(FolderEntity.builder()
                .name("Collab Root")
                .createdBy(adminA)
                .ownerOrg(orgA)
                .build());

        collaboration = collaborationRepository.save(CollaborationEntity.builder()
                .name("Alpha-Beta Collab")
                .ownerOrganization(orgA)
                .partnerOrganization(orgB)
                .rootFolder(collabFolder)
                .status(CollaborationStatus.ACTIVE)
                .startsAt(Instant.now())
                .createdBy(adminA)
                .build());
    }

    // =========================================================================
    // Requirement A: Active organization member can use legitimate collaboration functionality
    // =========================================================================
    @Test
    @DisplayName("A: Active organization member can be added as participant, promoted to manager, and exercise collaboration authority")
    void testA_ActiveOrgMember_CanUseCollaborationFunctionality() {
        CollaborationParticipantResponseDto participant = collaborationService.addParticipant(
                collaboration.getId(), userA1.getId(), adminA);
        assertThat(participant).isNotNull();
        assertThat(participant.getUserId()).isEqualTo(userA1.getId());
        assertThat(participant.getCollaborationRole()).isEqualTo(CollaborationRole.MEMBER);

        // Promote to MANAGER
        CollaborationParticipantResponseDto manager = collaborationService.assignManager(
                collaboration.getId(), userA1.getId(), adminA);
        assertThat(manager.getCollaborationRole()).isEqualTo(CollaborationRole.MANAGER);

        // Capability check
        boolean canManage = capabilityEvaluatorService.hasCapabilityInScope(
                userA1, OrganizationCapability.COLLAB_PARTICIPANTS_MANAGE, ScopeType.COLLABORATION, collaboration.getId(), orgA.getOrgCode());
        assertThat(canManage).isTrue();

        // Active member can read participants
        List<CollaborationParticipantResponseDto> participants = collaborationService.getParticipants(collaboration.getId(), userA1);
        assertThat(participants).isNotEmpty();
    }

    // =========================================================================
    // Requirement B: Suspended member cannot obtain collaboration authority
    // =========================================================================
    @Test
    @DisplayName("B: Suspended organization member cannot obtain collaboration authority or manage resources")
    void testB_SuspendedMember_CannotObtainCollaborationAuthority() {
        // Suspend userA1 in Org A
        organizationService.suspendMember(orgA.getOrgCode(), userA1.getId(),
                MemberStatusChangeRequestDto.builder().reason("Investigation pending").build(), adminA);

        // 1. Cannot add suspended user to collaboration
        assertThatThrownBy(() -> collaborationService.addParticipant(collaboration.getId(), userA1.getId(), adminA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to either owner or partner organization");

        // 2. Suspended user cannot access participant list
        assertThatThrownBy(() -> collaborationService.getParticipants(collaboration.getId(), userA1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not an active member");

        // 3. For an existing manager whose org membership is subsequently suspended:
        // Set userA2 as manager first while active
        collaborationService.addParticipant(collaboration.getId(), userA2.getId(), adminA);
        collaborationService.assignManager(collaboration.getId(), userA2.getId(), adminA);

        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                userA2, OrganizationCapability.COLLAB_PARTICIPANTS_MANAGE, ScopeType.COLLABORATION, collaboration.getId(), orgA.getOrgCode()))
                .isTrue();

        // Now suspend userA2
        organizationService.suspendMember(orgA.getOrgCode(), userA2.getId(),
                MemberStatusChangeRequestDto.builder().reason("Policy breach").build(), adminA);

        // Authority is immediately denied
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                userA2, OrganizationCapability.COLLAB_PARTICIPANTS_MANAGE, ScopeType.COLLABORATION, collaboration.getId(), orgA.getOrgCode()))
                .isFalse();

        // Admin cannot reassign manager to suspended user
        assertThatThrownBy(() -> collaborationService.assignManager(collaboration.getId(), userA2.getId(), adminA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active member");
    }

    // =========================================================================
    // Requirement C: Removed member cannot obtain collaboration authority
    // =========================================================================
    @Test
    @DisplayName("C: Removed organization member cannot obtain collaboration authority")
    void testC_RemovedMember_CannotObtainCollaborationAuthority() {
        // Remove userA1 from Org A
        organizationService.removeMember(orgA.getOrgCode(), userA1.getId(),
                MemberStatusChangeRequestDto.builder().reason("Offboarded").build(), adminA);

        // 1. Cannot add removed member as participant
        assertThatThrownBy(() -> collaborationService.addParticipant(collaboration.getId(), userA1.getId(), adminA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to either owner or partner organization");

        // 2. Cannot get participants
        assertThatThrownBy(() -> collaborationService.getParticipants(collaboration.getId(), userA1))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("not an active member");

        // 3. Capability check returns false
        assertThat(capabilityEvaluatorService.hasCapabilityInScope(
                userA1, OrganizationCapability.COLLAB_PARTICIPANTS_MANAGE, ScopeType.COLLABORATION, collaboration.getId(), orgA.getOrgCode()))
                .isFalse();
    }

    // =========================================================================
    // Requirements D, E, F: Org member removal cleans participant and permissions, preserves others
    // =========================================================================
    @Test
    @DisplayName("D, E, F: Org member removal cleans affected collaboration participant & permissions; preserves other users and orgs")
    void testD_E_F_RemovalCleansParticipantAndPermissions_PreservesOthers() {
        // Add userA1, userA2, and userB1 as participants
        collaborationService.addParticipant(collaboration.getId(), userA1.getId(), adminA);
        collaborationService.assignManager(collaboration.getId(), userA1.getId(), adminA);

        collaborationService.addParticipant(collaboration.getId(), userA2.getId(), adminA);
        collaborationService.addParticipant(collaboration.getId(), userB1.getId(), adminA);

        // Create an active collaboration permission granted to userA1
        PermissionEntity collabPerm = permissionRepository.save(PermissionEntity.builder()
                .grantedBy(adminA)
                .targetUserEmail(userA1.getEmail())
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .permissionType(PermissionType.ALLOW)
                .folder(collabFolder)
                .permissionLevel(PermissionLevel.EDIT)
                .collaboration(collaboration)
                .revoked(false)
                .build());

        // Verify initial state
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userA1.getId())).isPresent();
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userA2.getId())).isPresent();
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userB1.getId())).isPresent();

        // Action: Remove userA1 from Org A
        organizationService.removeMember(orgA.getOrgCode(), userA1.getId(),
                MemberStatusChangeRequestDto.builder().reason("Resigned").build(), adminA);

        // D: userA1's participant record is deleted and collaboration permission revoked
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userA1.getId())).isEmpty();
        PermissionEntity refreshedPerm = permissionRepository.findById(collabPerm.getId()).orElseThrow();
        assertThat(refreshedPerm.isRevoked()).isTrue();
        assertThat(refreshedPerm.getRevokedAt()).isNotNull();

        // E: userA2 (same org) is preserved
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userA2.getId())).isPresent();

        // F: userB1 (partner org) is preserved
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userB1.getId())).isPresent();

        // Collaboration status is still ACTIVE
        CollaborationEntity refreshedCollab = collaborationRepository.findById(collaboration.getId()).orElseThrow();
        assertThat(refreshedCollab.getStatus()).isEqualTo(CollaborationStatus.ACTIVE);
    }

    // =========================================================================
    // Requirement G: Re-adding user does not silently restore stale collaboration authority
    // =========================================================================
    @Test
    @DisplayName("G: Re-adding a user to organization does not silently restore stale collaboration participation")
    void testG_ReaddingUser_DoesNotRestoreStaleCollaborationAuthority() {
        // Setup userA1 as collaboration manager
        collaborationService.addParticipant(collaboration.getId(), userA1.getId(), adminA);
        collaborationService.assignManager(collaboration.getId(), userA1.getId(), adminA);

        // Remove userA1 from Org A
        organizationService.removeMember(orgA.getOrgCode(), userA1.getId(),
                MemberStatusChangeRequestDto.builder().reason("Offboarded").build(), adminA);

        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userA1.getId())).isEmpty();

        // Re-add userA1 to Org A
        UserOrganizationEntity readdedMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(userA1.getId(), orgA.getOrgCode()).orElseThrow();
        readdedMembership.setStatus(MembershipStatus.ACTIVE);
        readdedMembership.setRole(Role.ROLE_USER);
        userOrganizationRepository.save(readdedMembership);

        // Collaboration participation is NOT restored
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), userA1.getId())).isEmpty();

        // Collaboration management capability remains false
        boolean canManage = capabilityEvaluatorService.hasCapabilityInScope(
                userA1, OrganizationCapability.COLLAB_PARTICIPANTS_MANAGE, ScopeType.COLLABORATION, collaboration.getId(), orgA.getOrgCode());
        assertThat(canManage).isFalse();
    }

    // =========================================================================
    // Requirement H: Cross-org collaboration behavior remains valid
    // =========================================================================
    @Test
    @DisplayName("H: Cross-organization participant is preserved when removed from Org A if still active in Org B")
    void testH_CrossOrgCollaboration_ParticipantPreservedIfActiveInPartnerOrg() {
        // crossOrgUser is in both Org A and Org B
        collaborationService.addParticipant(collaboration.getId(), crossOrgUser.getId(), adminA);

        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), crossOrgUser.getId())).isPresent();

        // Remove from Org A only
        organizationService.removeMember(orgA.getOrgCode(), crossOrgUser.getId(),
                MemberStatusChangeRequestDto.builder().reason("Left Org A").build(), adminA);

        // Record is PRESERVED because crossOrgUser is still ACTIVE in Org B
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), crossOrgUser.getId())).isPresent();

        // Now remove crossOrgUser from Org B as well
        organizationService.removeMember(orgB.getOrgCode(), crossOrgUser.getId(),
                MemberStatusChangeRequestDto.builder().reason("Left Org B").build(), adminB);

        // Now that user is no longer active in either participating org, the participant record is deleted
        assertThat(collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(collaboration.getId(), crossOrgUser.getId())).isEmpty();
    }

    // =========================================================================
    // Requirement I: Permission cache is evicted after membership changes
    // =========================================================================
    @Test
    @DisplayName("I: Permission cache is evicted after member suspension, removal, and reactivation")
    void testI_CacheEvictedAfterMembershipChanges() {
        reset(permissionCacheService);

        // Suspend
        organizationService.suspendMember(orgA.getOrgCode(), userA1.getId(),
                MemberStatusChangeRequestDto.builder().reason("Suspended").build(), adminA);
        verify(permissionCacheService, atLeastOnce()).evictUserPermissions(userA1.getEmail());

        reset(permissionCacheService);

        // Reactivate
        organizationService.reactivateMember(orgA.getOrgCode(), userA1.getId(), adminA);
        verify(permissionCacheService, atLeastOnce()).evictUserPermissions(userA1.getEmail());

        reset(permissionCacheService);

        // Remove
        organizationService.removeMember(orgA.getOrgCode(), userA1.getId(),
                MemberStatusChangeRequestDto.builder().reason("Removed").build(), adminA);
        verify(permissionCacheService, atLeastOnce()).evictUserPermissions(userA1.getEmail());
    }
}
