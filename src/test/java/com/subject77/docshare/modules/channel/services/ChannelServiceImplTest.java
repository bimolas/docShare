package com.subject77.docshare.modules.channel.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.channel.dto.*;
import com.subject77.docshare.modules.channel.entities.*;
import com.subject77.docshare.modules.channel.repository.ChannelMemberRepository;
import com.subject77.docshare.modules.channel.repository.ChannelReadStateRepository;
import com.subject77.docshare.modules.channel.repository.ChannelRepository;
import com.subject77.docshare.modules.channel.repository.MessageRepository;
import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.services.WorkspacePolicyService;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.workspace.entities.ProjectWorkspaceEntity;
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChannelServiceImplTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ChannelMemberRepository channelMemberRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserGroupRepository userGroupRepository;

    @Mock
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Mock
    private CollaborationRepository collaborationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOrganizationRepository userOrganizationRepository;

    @Mock
    private WorkspacePolicyService workspacePolicyService;

    @Mock
    private OrgTrustRelationshipService orgTrustRelationshipService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ChannelReadStateRepository channelReadStateRepository;

    @Mock
    private MessageRepository messageRepository;

    @InjectMocks
    private ChannelServiceImpl channelService;

    private UserEntity userAdmin;
    private UserEntity userMember;
    private UserEntity externalUser;
    private OrganizationEntity orgA;
    private OrganizationEntity orgB;
    private GroupEntity teamA;
    private ProjectWorkspaceEntity workspaceA;
    private CollaborationEntity collabAB;
    private ChannelEntity activeChannel;
    private ChannelEntity archivedChannel;

    @BeforeEach
    void setUp() {
        userAdmin = UserEntity.builder()
                .id("101")
                .email("admin@orga.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        userMember = UserEntity.builder()
                .id("102")
                .email("member@orga.com")
                .firstName("Member")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        externalUser = UserEntity.builder()
                .id("201")
                .email("external@orgb.com")
                .firstName("External")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build();

        orgA = OrganizationEntity.builder()
                .orgCode("ORG-A")
                .name("Organization A")
                .build();

        orgB = OrganizationEntity.builder()
                .orgCode("ORG-B")
                .name("Organization B")
                .build();

        teamA = GroupEntity.builder()
                .id(10L)
                .name("Engineering Team")
                .organization(orgA)
                .build();

        workspaceA = ProjectWorkspaceEntity.builder()
                .id(50L)
                .name("Project Alpha")
                .organization(orgA)
                .build();

        collabAB = CollaborationEntity.builder()
                .id(30L)
                .name("Joint Research")
                .ownerOrganization(orgA)
                .partnerOrganization(orgB)
                .build();

        activeChannel = ChannelEntity.builder()
                .id(1L)
                .name("general")
                .description("General Channel")
                .type(ChannelType.STANDARD)
                .status(ChannelStatus.ACTIVE)
                .organization(orgA)
                .createdBy(userAdmin)
                .build();

        archivedChannel = ChannelEntity.builder()
                .id(2L)
                .name("archived-announcements")
                .description("Old announcements")
                .type(ChannelType.ANNOUNCEMENT)
                .status(ChannelStatus.ARCHIVED)
                .organization(orgA)
                .createdBy(userAdmin)
                .build();
    }

    @Nested
    @DisplayName("Create Channel Tests")
    class CreateChannelTests {

        @Test
        @DisplayName("Should create channel successfully in Organization scope")
        void createChannel_orgScope_success() {
            CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                    .name("general")
                    .description("General discussion")
                    .type(ChannelType.STANDARD)
                    .orgCode("ORG-A")
                    .build();

            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("101", "ORG-A")).thenReturn(true);
            when(channelRepository.existsByOrganization_OrgCodeAndName("ORG-A", "general")).thenReturn(false);
            when(channelRepository.save(any(ChannelEntity.class))).thenAnswer(inv -> {
                ChannelEntity entity = inv.getArgument(0);
                entity.setId(100L);
                return entity;
            });

            ChannelResponseDto result = channelService.createChannel(request, userAdmin);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("general");
            assertThat(result.getOrgCode()).isEqualTo("ORG-A");
            assertThat(result.getType()).isEqualTo(ChannelType.STANDARD);
            assertThat(result.getStatus()).isEqualTo(ChannelStatus.ACTIVE);

            verify(channelMemberRepository, times(1)).save(argThat(member ->
                    member.getUser().getId().equals("101") && member.getRole() == ChannelMemberRole.MANAGER
            ));
        }

        @Test
        @DisplayName("Should throw exception when scope count is not exactly 1")
        void createChannel_invalidScopeCount_throwsException() {
            CreateChannelRequestDto requestNoScope = CreateChannelRequestDto.builder()
                    .name("general")
                    .type(ChannelType.STANDARD)
                    .build();

            assertThatThrownBy(() -> channelService.createChannel(requestNoScope, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must have exactly one parent scope");

            CreateChannelRequestDto requestMultiScope = CreateChannelRequestDto.builder()
                    .name("general")
                    .type(ChannelType.STANDARD)
                    .orgCode("ORG-A")
                    .teamId(10L)
                    .build();

            assertThatThrownBy(() -> channelService.createChannel(requestMultiScope, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must have exactly one parent scope");
        }

        @Test
        @DisplayName("Should throw exception when COLLABORATION channel lacks collaboration scope")
        void createChannel_collabTypeWithoutCollabScope_throwsException() {
            CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                    .name("joint-discussion")
                    .type(ChannelType.COLLABORATION)
                    .orgCode("ORG-A")
                    .build();

            assertThatThrownBy(() -> channelService.createChannel(request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("COLLABORATION channel type requires a Collaboration parent scope");
        }

        @Test
        @DisplayName("Should throw exception when duplicate channel name exists in scope")
        void createChannel_duplicateName_throwsException() {
            CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                    .name("general")
                    .type(ChannelType.STANDARD)
                    .orgCode("ORG-A")
                    .build();

            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("101", "ORG-A")).thenReturn(true);
            when(channelRepository.existsByOrganization_OrgCodeAndName("ORG-A", "general")).thenReturn(true);

            assertThatThrownBy(() -> channelService.createChannel(request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("Should throw exception when SHARED channel is created but external sharing policy is OFF")
        void createChannel_sharedChannelDisabledPolicy_throwsException() {
            CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                    .name("shared-stream")
                    .type(ChannelType.SHARED)
                    .orgCode("ORG-A")
                    .build();

            WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .build();

            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("101", "ORG-A")).thenReturn(true);
            when(channelRepository.existsByOrganization_OrgCodeAndName("ORG-A", "shared-stream")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(policy));

            assertThatThrownBy(() -> channelService.createChannel(request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("External sharing is disabled");
        }
    }

    @Nested
    @DisplayName("Get Channel Tests")
    class GetChannelTests {

        @Test
        @DisplayName("Should return channel details for direct member")
        void getChannel_asMember_success() {
            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "102")).thenReturn(true);

            ChannelResponseDto result = channelService.getChannel(1L, userMember);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getName()).isEqualTo("general");
        }

        @Test
        @DisplayName("Should deny access to PRIVATE channel for non-member")
        void getChannel_privateChannelNonMember_throwsAccessDenied() {
            ChannelEntity privateChannel = ChannelEntity.builder()
                    .id(3L)
                    .name("secret-channel")
                    .type(ChannelType.PRIVATE)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .build();

            when(channelRepository.findById(3L)).thenReturn(Optional.of(privateChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(3L, "102")).thenReturn(false);

            assertThatThrownBy(() -> channelService.getChannel(3L, userMember))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Access denied");
        }
    }

    @Nested
    @DisplayName("Update & Archive Channel Tests")
    class UpdateArchiveChannelTests {

        @Test
        @DisplayName("Should update channel details when called by Channel Manager")
        void updateChannel_asManager_success() {
            UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
                    .name("updated-general")
                    .description("Updated description")
                    .build();

            ChannelMemberEntity managerMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(channelRepository.save(any(ChannelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ChannelResponseDto result = channelService.updateChannel(1L, request, userAdmin);

            assertThat(result.getName()).isEqualTo("updated-general");
            assertThat(result.getDescription()).isEqualTo("Updated description");
        }

        @Test
        @DisplayName("Should throw exception when updating an ARCHIVED channel")
        void updateChannel_archivedChannel_throwsIllegalState() {
            UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
                    .name("new-name")
                    .build();

            when(channelRepository.findById(2L)).thenReturn(Optional.of(archivedChannel));

            assertThatThrownBy(() -> channelService.updateChannel(2L, request, userAdmin))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Channel is archived");
        }

        @Test
        @DisplayName("Should archive channel successfully when called by Manager")
        void archiveChannel_asManager_success() {
            ChannelMemberEntity managerMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(channelRepository.save(any(ChannelEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ChannelResponseDto result = channelService.archiveChannel(1L, userAdmin);

            assertThat(result.getStatus()).isEqualTo(ChannelStatus.ARCHIVED);
            verify(auditLogService, times(1)).logPermissionChange(
                    eq("CHANNEL_ARCHIVED"), eq("CHANNEL"), eq(1L), any(), any(), any(), eq("ORG-A"), eq(userAdmin), eq(userAdmin.getEmail()), any(), any(), any(), any()
            );
        }
    }

    @Nested
    @DisplayName("Membership Management Tests")
    class MembershipManagementTests {

        @Test
        @DisplayName("Should add internal member successfully")
        void addMember_internalUser_success() {
            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("member@orga.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            ChannelMemberEntity managerMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("member@orga.com")).thenReturn(Optional.of(userMember));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "102")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(true);
            when(channelMemberRepository.save(any(ChannelMemberEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ChannelMemberResponseDto result = channelService.addMember(1L, request, userAdmin);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("102");
            assertThat(result.getRole()).isEqualTo(ChannelMemberRole.MEMBER);
        }

        @Test
        @DisplayName("Should enforce trusted relationship policy when adding external user")
        void addMember_externalUserUntrusted_throwsException() {
            // Use a channel that explicitly allows external members, so trust is the blocker
            ChannelEntity externalAllowedChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            ChannelMemberEntity managerMember = ChannelMemberEntity.builder()
                    .channel(externalAllowedChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.ON)
                    .build();

            UserOrganizationEntity uoExternal = UserOrganizationEntity.builder()
                    .user(externalUser)
                    .organization(orgB)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(externalAllowedChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(policy));
            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(policy);
            when(userOrganizationRepository.findByUser_Id("201")).thenReturn(List.of(uoExternal));
            when(orgTrustRelationshipService.hasActiveTrust("ORG-A", "ORG-B")).thenReturn(false);

            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Target user does not belong to an organization with active trust");
        }

        @Test
        @DisplayName("Should allow user to remove self from channel")
        void removeMember_selfRemove_success() {
            ChannelMemberEntity member = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(member));

            channelService.removeMember(1L, "102", userMember);

            verify(channelMemberRepository, times(1)).delete(member);
        }

        @Test
        @DisplayName("Should update member role successfully when performed by Manager")
        void updateMemberRole_asManager_success() {
            UpdateChannelMemberRoleRequestDto request = UpdateChannelMemberRoleRequestDto.builder()
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            ChannelMemberEntity managerMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            ChannelMemberEntity targetMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(targetMember));
            when(channelMemberRepository.save(any(ChannelMemberEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ChannelMemberResponseDto result = channelService.updateMemberRole(1L, "102", request, userAdmin);

            assertThat(result.getRole()).isEqualTo(ChannelMemberRole.MANAGER);
        }
    }

    @Nested
    @DisplayName("Moderator and Capability Flag Tests")
    class ModeratorAndCapabilityTests {

        @Test
        @DisplayName("verifyChannelModeratorOrAdmin should pass for MODERATOR, ADMIN, MANAGER, and Org Admin")
        void verifyChannelModeratorOrAdmin_success() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));

            channelService.verifyChannelModeratorOrAdmin(activeChannel, userMember);
            // No exception thrown
        }

        @Test
        @DisplayName("verifyChannelModeratorOrAdmin should throw AccessDeniedException for standard MEMBER")
        void verifyChannelModeratorOrAdmin_throwsAccessDenied_forMember() {
            ChannelMemberEntity stdMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(stdMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.verifyChannelModeratorOrAdmin(activeChannel, userMember))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("does not have moderator or admin access");
        }

        @Test
        @DisplayName("Moderator should be denied from updating channel")
        void moderator_cannotUpdateChannel() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
                    .name("new-name")
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.updateChannel(1L, request, userMember))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Moderator should be denied from archiving channel")
        void moderator_cannotArchiveChannel() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.archiveChannel(1L, userMember))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Moderator should be denied from adding members")
        void moderator_cannotAddMember() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.addMember(1L, request, userMember))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Moderator should be denied from removing other members")
        void moderator_cannotRemoveOtherMember() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.removeMember(1L, "101", userMember))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Moderator should be denied from updating member role")
        void moderator_cannotUpdateMemberRole() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            UpdateChannelMemberRoleRequestDto request = UpdateChannelMemberRoleRequestDto.builder()
                    .role(ChannelMemberRole.MANAGER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> channelService.updateMemberRole(1L, "101", request, userMember))
                    .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("Should set capability flags correctly for MODERATOR")
        void getChannel_capabilityFlags_forModerator() {
            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "102")).thenReturn(true);
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());
            when(channelMemberRepository.countByChannel_Id(1L)).thenReturn(5L);

            ChannelResponseDto dto = channelService.getChannel(1L, userMember);

            assertThat(dto.getCurrentUserRole()).isEqualTo(ChannelMemberRole.MODERATOR);
            assertThat(dto.isCanManageChannel()).isFalse();
            assertThat(dto.isCanManageMembers()).isFalse();
            assertThat(dto.isCanArchive()).isFalse();
            assertThat(dto.isCanModerateMessages()).isTrue();
            assertThat(dto.isCanPost()).isTrue(); // MODERATOR can post to ANNOUNCEMENT
        }

        @Test
        @DisplayName("Should set capability flags correctly for ADMIN")
        void getChannel_capabilityFlags_forAdmin() {
            ChannelMemberEntity adminMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.ADMIN)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "101")).thenReturn(true);
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(adminMember));
            when(channelMemberRepository.countByChannel_Id(1L)).thenReturn(5L);

            ChannelResponseDto dto = channelService.getChannel(1L, userAdmin);

            assertThat(dto.getCurrentUserRole()).isEqualTo(ChannelMemberRole.ADMIN);
            assertThat(dto.isCanManageChannel()).isTrue();
            assertThat(dto.isCanManageMembers()).isTrue();
            assertThat(dto.isCanArchive()).isTrue();
            assertThat(dto.isCanModerateMessages()).isTrue();
            assertThat(dto.isCanPost()).isTrue(); // ADMIN can post to STANDARD channel
        }

        @Test
        @DisplayName("Should set capability flags correctly for standard MEMBER")
        void getChannel_capabilityFlags_forMember() {
            ChannelMemberEntity stdMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "102")).thenReturn(true);
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(stdMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());
            when(channelMemberRepository.countByChannel_Id(1L)).thenReturn(5L);

            ChannelResponseDto dto = channelService.getChannel(1L, userMember);

            assertThat(dto.getCurrentUserRole()).isEqualTo(ChannelMemberRole.MEMBER);
            assertThat(dto.isCanManageChannel()).isFalse();
            assertThat(dto.isCanManageMembers()).isFalse();
            assertThat(dto.isCanArchive()).isFalse();
            assertThat(dto.isCanModerateMessages()).isFalse();
            assertThat(dto.isCanPost()).isTrue(); // MEMBER can post to STANDARD channel
        }

        @Test
        @DisplayName("canPost should be false for any role when channel is ARCHIVED")
        void getChannel_canPost_archivedChannel_false() {
            ChannelEntity archivedChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("Archived")
                    .type(ChannelType.STANDARD)
                    .status(ChannelStatus.ARCHIVED)
                    .organization(orgA)
                    .createdBy(userAdmin)
                    .build();

            ChannelMemberEntity adminMember = ChannelMemberEntity.builder()
                    .channel(archivedChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.ADMIN)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(archivedChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "101")).thenReturn(true);
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(adminMember));
            when(channelMemberRepository.countByChannel_Id(1L)).thenReturn(3L);

            ChannelResponseDto dto = channelService.getChannel(1L, userAdmin);

            assertThat(dto.isCanPost()).isFalse();
        }

        @Test
        @DisplayName("canPost should be false for MEMBER in ANNOUNCEMENT channel")
        void getChannel_canPost_announcementChannel_memberFalse() {
            ChannelEntity announcementChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("Announcements")
                    .type(ChannelType.ANNOUNCEMENT)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .createdBy(userAdmin)
                    .build();

            ChannelMemberEntity stdMember = ChannelMemberEntity.builder()
                    .channel(announcementChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(announcementChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "102")).thenReturn(true);
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(stdMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());
            when(channelMemberRepository.countByChannel_Id(1L)).thenReturn(10L);

            ChannelResponseDto dto = channelService.getChannel(1L, userMember);

            assertThat(dto.isCanPost()).isFalse();
            assertThat(dto.isCanModerateMessages()).isFalse();
        }

        @Test
        @DisplayName("canPost should be true for MODERATOR in ANNOUNCEMENT channel")
        void getChannel_canPost_announcementChannel_moderatorTrue() {
            ChannelEntity announcementChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("Announcements")
                    .type(ChannelType.ANNOUNCEMENT)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .createdBy(userAdmin)
                    .build();

            ChannelMemberEntity modMember = ChannelMemberEntity.builder()
                    .channel(announcementChannel)
                    .user(userMember)
                    .role(ChannelMemberRole.MODERATOR)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(announcementChannel));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "102")).thenReturn(true);
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "102")).thenReturn(Optional.of(modMember));
            when(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode("102", "ORG-A")).thenReturn(Optional.empty());
            when(channelMemberRepository.countByChannel_Id(1L)).thenReturn(10L);

            ChannelResponseDto dto = channelService.getChannel(1L, userMember);

            assertThat(dto.isCanPost()).isTrue();
            assertThat(dto.isCanModerateMessages()).isTrue();
        }
    }

    // =======================================================================
    // Policy Precedence Tests (14B-2)
    // Enforced hierarchy: Org Policy → Workspace Policy → Trust → allowExternal
    // =======================================================================

    @Nested
    @DisplayName("Policy Precedence Tests")
    class PolicyPrecedenceTests {

        private ChannelMemberEntity managerMember;

        @BeforeEach
        void setUpPolicyTests() {
            managerMember = ChannelMemberEntity.builder()
                    .channel(activeChannel)
                    .user(userAdmin)
                    .role(ChannelMemberRole.MANAGER)
                    .build();
        }

        // ----------------------------------------------------------------
        // 1. Org policy OFF blocks SHARED channel creation
        // ----------------------------------------------------------------
        @Test
        @DisplayName("1. Org policy OFF blocks SHARED channel creation")
        void createSharedChannel_orgPolicyOff_blocked() {
            CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                    .name("cross-org-stream")
                    .type(ChannelType.SHARED)
                    .orgCode("ORG-A")
                    .allowExternal(true)
                    .build();

            WorkspacePolicyEntity orgPolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .build();

            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("101", "ORG-A")).thenReturn(true);
            when(channelRepository.existsByOrganization_OrgCodeAndName("ORG-A", "cross-org-stream")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(orgPolicy));

            assertThatThrownBy(() -> channelService.createChannel(request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("External sharing is disabled for organization");
        }

        // ----------------------------------------------------------------
        // 2. Org policy OFF blocks external member addition
        // ----------------------------------------------------------------
        @Test
        @DisplayName("2. Org policy OFF blocks external member addition")
        void addMember_externalUser_orgPolicyOff_blocked() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity orgPolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(orgPolicy));

            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("organization policy");
        }

        // ----------------------------------------------------------------
        // 3. Workspace policy OFF blocks SHARED channel creation
        // ----------------------------------------------------------------
        @Test
        @DisplayName("3. Workspace policy OFF blocks SHARED channel creation")
        void createSharedChannel_workspacePolicyOff_blocked() {
            CreateChannelRequestDto request = CreateChannelRequestDto.builder()
                    .name("cross-org-stream")
                    .type(ChannelType.SHARED)
                    .orgCode("ORG-A")
                    .allowExternal(true)
                    .build();

            WorkspacePolicyEntity workspacePolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .build();

            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("101", "ORG-A")).thenReturn(true);
            when(channelRepository.existsByOrganization_OrgCodeAndName("ORG-A", "cross-org-stream")).thenReturn(false);
            // Org-level policy returns empty (no org-level restriction)
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.empty());
            // Effective/workspace-level policy is OFF
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(workspacePolicy);

            assertThatThrownBy(() -> channelService.createChannel(request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workspace policy");
        }

        // ----------------------------------------------------------------
        // 4. Workspace policy OFF blocks external member addition
        // ----------------------------------------------------------------
        @Test
        @DisplayName("4. Workspace policy OFF blocks external member addition")
        void addMember_externalUser_workspacePolicyOff_blocked() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity workspacePolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            // Org-level policy returns empty (no org-level restriction)
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.empty());
            // organizationRepository must be stubbed so the workspace layer is reached
            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            // Effective/workspace-level policy is OFF
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(workspacePolicy);

            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workspace policy");
        }

        // ----------------------------------------------------------------
        // 5. channel allowExternal=false blocks external member addition
        //    even when higher-level policies allow it
        // ----------------------------------------------------------------
        @Test
        @DisplayName("5. channel allowExternal=false blocks external member addition even if higher-level policies allow it")
        void addMember_externalUser_channelAllowExternalFalse_blocked() {
            // allowExternal defaults to false on activeChannel
            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(activeChannel)); // allowExternal=false
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);

            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allowExternal is false");
        }

        // ----------------------------------------------------------------
        // 6. channel allowExternal=true cannot override org policy OFF
        // ----------------------------------------------------------------
        @Test
        @DisplayName("6. channel allowExternal=true cannot override org policy OFF")
        void addMember_externalUser_allowExternalTrue_cannotOverrideOrgPolicyOff() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)   // <-- channel says YES
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity orgPolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF) // <-- org says NO
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(orgPolicy));

            // Org policy wins — channel allowExternal is irrelevant
            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("organization policy");
        }

        // ----------------------------------------------------------------
        // 7. channel allowExternal=true cannot override workspace policy OFF
        // ----------------------------------------------------------------
        @Test
        @DisplayName("7. channel allowExternal=true cannot override workspace policy OFF")
        void addMember_externalUser_allowExternalTrue_cannotOverrideWorkspacePolicyOff() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)   // <-- channel says YES
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity workspacePolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.OFF) // <-- workspace says NO
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.empty());
            // organizationRepository must be stubbed so the workspace layer is reached
            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(workspacePolicy);

            // Workspace policy wins — channel allowExternal is irrelevant
            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("workspace policy");
        }

        // ----------------------------------------------------------------
        // 8. Missing trust/collaboration blocks external member addition
        // ----------------------------------------------------------------
        @Test
        @DisplayName("8. Missing trust blocks external member addition")
        void addMember_externalUser_missingTrust_blocked() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity allowPolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.ON)
                    .build();

            UserOrganizationEntity uoExternal = UserOrganizationEntity.builder()
                    .user(externalUser)
                    .organization(orgB)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(allowPolicy));
            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(allowPolicy);
            when(userOrganizationRepository.findByUser_Id("201")).thenReturn(List.of(uoExternal));
            when(orgTrustRelationshipService.hasActiveTrust("ORG-A", "ORG-B")).thenReturn(false);

            assertThatThrownBy(() -> channelService.addMember(1L, request, userAdmin))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("active trust");
        }

        // ----------------------------------------------------------------
        // 9. Valid org + workspace + trust + channel config succeeds
        // ----------------------------------------------------------------
        @Test
        @DisplayName("9. Valid org + workspace + trust + allowExternal=true succeeds")
        void addMember_externalUser_allPoliciesAllow_success() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity allowPolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.ON)
                    .build();

            UserOrganizationEntity uoExternal = UserOrganizationEntity.builder()
                    .user(externalUser)
                    .organization(orgB)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(allowPolicy));
            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(allowPolicy);
            when(userOrganizationRepository.findByUser_Id("201")).thenReturn(List.of(uoExternal));
            when(orgTrustRelationshipService.hasActiveTrust("ORG-A", "ORG-B")).thenReturn(true);
            when(channelMemberRepository.save(any(ChannelMemberEntity.class))).thenAnswer(inv -> {
                ChannelMemberEntity m = inv.getArgument(0);
                m.setId(99L);
                return m;
            });

            ChannelMemberResponseDto result = channelService.addMember(1L, request, userAdmin);

            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo("201");
            assertThat(result.getRole()).isEqualTo(ChannelMemberRole.MEMBER);
        }

        // ----------------------------------------------------------------
        // 10. External membership does not create org membership
        // ----------------------------------------------------------------
        @Test
        @DisplayName("10. External membership does not create org membership")
        void addMember_externalUser_doesNotCreateOrgMembership() {
            ChannelEntity allowExternalChannel = ChannelEntity.builder()
                    .id(1L)
                    .name("general")
                    .type(ChannelType.SHARED)
                    .status(ChannelStatus.ACTIVE)
                    .organization(orgA)
                    .allowExternal(true)
                    .createdBy(userAdmin)
                    .build();

            AddChannelMemberRequestDto request = AddChannelMemberRequestDto.builder()
                    .userEmail("external@orgb.com")
                    .role(ChannelMemberRole.MEMBER)
                    .build();

            WorkspacePolicyEntity allowPolicy = WorkspacePolicyEntity.builder()
                    .externalSharingMode(ExternalSharingMode.ON)
                    .build();

            UserOrganizationEntity uoExternal = UserOrganizationEntity.builder()
                    .user(externalUser)
                    .organization(orgB)
                    .build();

            when(channelRepository.findById(1L)).thenReturn(Optional.of(allowExternalChannel));
            when(channelMemberRepository.findByChannel_IdAndUser_Id(1L, "101")).thenReturn(Optional.of(managerMember));
            when(userRepository.findByEmail("external@orgb.com")).thenReturn(Optional.of(externalUser));
            when(channelMemberRepository.existsByChannel_IdAndUser_Id(1L, "201")).thenReturn(false);
            when(userOrganizationRepository.existsByUser_IdAndOrganization_OrgCode("201", "ORG-A")).thenReturn(false);
            when(workspacePolicyService.getPolicyForOrganization("ORG-A")).thenReturn(Optional.of(allowPolicy));
            when(organizationRepository.findById("ORG-A")).thenReturn(Optional.of(orgA));
            when(workspacePolicyService.getEffectivePolicyForOrganization(orgA)).thenReturn(allowPolicy);
            when(userOrganizationRepository.findByUser_Id("201")).thenReturn(List.of(uoExternal));
            when(orgTrustRelationshipService.hasActiveTrust("ORG-A", "ORG-B")).thenReturn(true);
            when(channelMemberRepository.save(any(ChannelMemberEntity.class))).thenAnswer(inv -> {
                ChannelMemberEntity m = inv.getArgument(0);
                m.setId(99L);
                return m;
            });

            channelService.addMember(1L, request, userAdmin);

            // Must never create a UserOrganizationEntity for the external user in ORG-A
            verify(userOrganizationRepository, never()).save(any(UserOrganizationEntity.class));
        }
    }
}
