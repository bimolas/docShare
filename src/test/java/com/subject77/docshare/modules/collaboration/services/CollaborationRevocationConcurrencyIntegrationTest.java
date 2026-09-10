package com.subject77.docshare.modules.collaboration.services;

import com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand;
import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.collaboration.dto.CollaborationParticipantResponseDto;
import com.subject77.docshare.modules.collaboration.dto.CollaborationResponseDto;
import com.subject77.docshare.modules.collaboration.entities.*;
import com.subject77.docshare.modules.collaboration.repository.CollaborationGroupParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.collaboration.scheduler.CollaborationExpirationScheduler;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;
import com.subject77.docshare.modules.permission.entities.BlockchainStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.trust.entities.OrgTrustRelationshipEntity;
import com.subject77.docshare.modules.trust.entities.TrustStatus;
import com.subject77.docshare.modules.trust.repository.OrgTrustRelationshipRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
public class CollaborationRevocationConcurrencyIntegrationTest {

    @Autowired
    private CollaborationService collaborationService;

    @Autowired
    private CollaborationExpirationScheduler collaborationExpirationScheduler;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private CollaborationParticipantRepository collaborationParticipantRepository;

    @Autowired
    private CollaborationGroupParticipantRepository collaborationGroupParticipantRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private OrgTrustRelationshipRepository orgTrustRelationshipRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    @MockitoSpyBean
    private AuditLogService auditLogService;

    private static class TestCollabContext {
        String suffix;
        OrganizationEntity ownerOrg;
        OrganizationEntity partnerOrg;
        FolderEntity rootFolder;
        UserEntity ownerAdmin;
        UserEntity partnerAdmin;
        UserEntity partnerAdmin2;
        UserEntity ownerMember;
        UserEntity partnerMember;
        UserEntity superAdmin;
        GroupEntity ownerGroup;
        CollaborationEntity collaboration;
    }

    private TestCollabContext createTestCollabContext(String prefix, CollaborationStatus status, boolean expired) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TestCollabContext ctx = new TestCollabContext();
        ctx.suffix = suffix;

        String ownerOrgCode = (prefix + "_OWNER_" + suffix).toUpperCase();
        String partnerOrgCode = (prefix + "_PARTNER_" + suffix).toUpperCase();

        ctx.ownerAdmin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("owner_admin_" + suffix + "@test.com")
                .firstName("Owner")
                .lastName("Admin")
                .role(Role.ROLE_USER)
                .build());

        ctx.partnerAdmin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("partner_admin_" + suffix + "@test.com")
                .firstName("Partner")
                .lastName("Admin")
                .role(Role.ROLE_USER)
                .build());

        ctx.partnerAdmin2 = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("partner_admin2_" + suffix + "@test.com")
                .firstName("Partner")
                .lastName("Admin2")
                .role(Role.ROLE_USER)
                .build());

        ctx.ownerOrg = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(ownerOrgCode)
                .name("Owner Org " + suffix)
                .createdBy(ctx.ownerAdmin)
                .build());

        ctx.partnerOrg = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(partnerOrgCode)
                .name("Partner Org " + suffix)
                .createdBy(ctx.partnerAdmin)
                .build());

        ctx.ownerMember = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("owner_member_" + suffix + "@test.com")
                .firstName("Owner")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .build());

        ctx.partnerMember = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("partner_member_" + suffix + "@test.com")
                .firstName("Partner")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .build());

        ctx.superAdmin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("super_admin_" + suffix + "@test.com")
                .firstName("Super")
                .lastName("Admin")
                .role(Role.ROLE_SUPER_ADMIN)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.ownerAdmin)
                .organization(ctx.ownerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.ownerMember)
                .organization(ctx.ownerOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.partnerAdmin)
                .organization(ctx.partnerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.partnerAdmin2)
                .organization(ctx.partnerOrg)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.partnerMember)
                .organization(ctx.partnerOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        ctx.rootFolder = folderRepository.save(FolderEntity.builder()
                .name("RootFolder_" + suffix)
                .ownerOrg(ctx.ownerOrg)
                .createdBy(ctx.ownerAdmin)
                .build());

        ctx.ownerGroup = groupRepository.save(GroupEntity.builder()
                .name("Group_" + suffix)
                .organization(ctx.ownerOrg)
                .createdBy(ctx.ownerAdmin)
                .build());

        // Establish active trust relationship between owner and partner orgs
        orgTrustRelationshipRepository.save(OrgTrustRelationshipEntity.builder()
                .sourceOrganization(ctx.ownerOrg)
                .targetOrganization(ctx.partnerOrg)
                .status(TrustStatus.ACTIVE)
                .requestedBy(ctx.ownerAdmin)
                .approvedBy(ctx.partnerAdmin)
                .createdAt(Instant.now().minusSeconds(7200))
                .updatedAt(Instant.now().minusSeconds(7200))
                .build());

        Instant now = Instant.now();
        Instant startsAt = now.minusSeconds(3600);
        Instant endsAt = expired ? now.minusSeconds(60) : now.plusSeconds(3600);

        ctx.collaboration = collaborationRepository.save(CollaborationEntity.builder()
                .name("Collab_" + suffix)
                .ownerOrganization(ctx.ownerOrg)
                .partnerOrganization(ctx.partnerOrg)
                .rootFolder(ctx.rootFolder)
                .status(status)
                .createdBy(ctx.ownerAdmin)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .build());

        return ctx;
    }

    private PermissionEntity addCollaborationPermission(TestCollabContext ctx, UserEntity targetUser, PermissionLevel level) {
        return permissionRepository.save(PermissionEntity.builder()
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(targetUser.getEmail())
                .permissionLevel(level)
                .permissionType(PermissionType.ALLOW)
                .folder(ctx.rootFolder)
                .collaboration(ctx.collaboration)
                .grantedBy(ctx.ownerAdmin)
                .grantorAuthorityTier(AuthorityTier.ORG_ADMIN)
                .createdAt(Instant.now().minusSeconds(100))
                .recordHash("COLLAB_PERM_" + UUID.randomUUID())
                .blockchainStatus(BlockchainStatus.ANCHORED)
                .revoked(false)
                .build());
    }

    // =========================================================================
    // SCENARIO 1: revoke vs grant permission
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Revoke vs Grant Permission - Serialized on collaboration lock; zero active permissions survive")
    void testScenario1_RevokeVsGrantPermission_Serialized() throws Exception {
        TestCollabContext ctx = createTestCollabContext("REV_VS_GRANT", CollaborationStatus.ACTIVE, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> revokeResult = new AtomicReference<>();
        AtomicReference<PermissionResponseDto> grantResult = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        AtomicReference<Throwable> grantError = new AtomicReference<>();

        PermissionRequestDto grantRequest = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(ctx.partnerMember.getEmail())
                .folderId(ctx.rootFolder.getId())
                .build();

        // Thread 1: Revoke collaboration
        executor.submit(() -> {
            try {
                startLatch.await();
                revokeResult.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Grant permission
        executor.submit(() -> {
            try {
                startLatch.await();
                grantResult.set(collaborationService.grantCollaborationPermission(ctx.collaboration.getId(), grantRequest, ctx.ownerAdmin));
            } catch (Throwable t) {
                grantError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Revoke must always succeed
        assertThat(revokeError.get()).isNull();
        assertThat(revokeResult.get()).isNotNull();
        assertThat(revokeResult.get().getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        // Verification: If grant acquired lock after revoke, grant threw IllegalStateException
        // If grant ran before revoke, revoke tore it down.
        // In all cases, zero active collaboration permissions survive.
        List<PermissionEntity> activePermissions = permissionRepository.findByCollaboration_IdAndRevokedFalse(ctx.collaboration.getId());
        assertThat(activePermissions).isEmpty();

        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.REVOKED);
    }

    // =========================================================================
    // SCENARIO 2: revoke vs add participant
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Revoke vs Add Participant - Serialized; no participant survives on inactive collaboration")
    void testScenario2_RevokeVsAddParticipant_Serialized() throws Exception {
        TestCollabContext ctx = createTestCollabContext("REV_VS_PARTICIPANT", CollaborationStatus.ACTIVE, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> revokeResult = new AtomicReference<>();
        AtomicReference<CollaborationParticipantResponseDto> addResult = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        AtomicReference<Throwable> addError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                revokeResult.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                addResult.set(collaborationService.addParticipant(ctx.collaboration.getId(), ctx.partnerMember.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                addError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        assertThat(revokeError.get()).isNull();
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        if (addError.get() != null) {
            assertThat(addError.get()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Participants can only be managed on ACTIVE collaborations");
            assertThat(collaborationParticipantRepository.findByCollaboration_Id(ctx.collaboration.getId())).isEmpty();
        }
    }

    // =========================================================================
    // SCENARIO 3: revoke vs add group participant
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: Revoke vs Add Group Participant - Serialized; inactive collaboration rejects group participant")
    void testScenario3_RevokeVsAddGroupParticipant_Serialized() throws Exception {
        TestCollabContext ctx = createTestCollabContext("REV_VS_GROUP", CollaborationStatus.ACTIVE, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> revokeResult = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        AtomicReference<Throwable> addGroupError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                revokeResult.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                collaborationService.addGroupParticipant(ctx.collaboration.getId(), ctx.ownerGroup.getId(), ctx.ownerAdmin);
            } catch (Throwable t) {
                addGroupError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        assertThat(revokeError.get()).isNull();
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        if (addGroupError.get() != null) {
            assertThat(addGroupError.get()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Group participants can only be managed on ACTIVE collaborations");
            assertThat(collaborationGroupParticipantRepository.findByCollaboration_Id(ctx.collaboration.getId())).isEmpty();
        }
    }

    // =========================================================================
    // SCENARIO 4: revoke vs assign manager
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Revoke vs Assign Manager - Serialized; inactive collaboration rejects manager assignment")
    void testScenario4_RevokeVsAssignManager_Serialized() throws Exception {
        TestCollabContext ctx = createTestCollabContext("REV_VS_MGR", CollaborationStatus.ACTIVE, false);

        // Pre-add participant as regular MEMBER
        CollaborationParticipantEntity participant = collaborationParticipantRepository.save(CollaborationParticipantEntity.builder()
                .collaboration(ctx.collaboration)
                .user(ctx.partnerMember)
                .collaborationRole(CollaborationRole.MEMBER)
                .createdAt(Instant.now().minusSeconds(50))
                .build());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        AtomicReference<Throwable> assignError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin);
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                collaborationService.assignManager(ctx.collaboration.getId(), ctx.partnerMember.getId(), ctx.ownerAdmin);
            } catch (Throwable t) {
                assignError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        assertThat(revokeError.get()).isNull();
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        if (assignError.get() != null) {
            assertThat(assignError.get()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Manager roles can only be assigned on ACTIVE collaborations");
            // Participant role remains MEMBER
            CollaborationParticipantEntity reloaded = collaborationParticipantRepository.findById(participant.getId()).orElseThrow();
            assertThat(reloaded.getCollaborationRole()).isEqualTo(CollaborationRole.MEMBER);
        }
    }

    // =========================================================================
    // SCENARIO 5: concurrent revoke owner admin vs partner admin
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: Concurrent Revoke Owner Admin vs Partner Admin - Exactly one teardown and one audit log")
    void testScenario5_ConcurrentRevoke_OwnerAdminVsPartnerAdmin_ExactlyOneTeardown() throws Exception {
        TestCollabContext ctx = createTestCollabContext("REV_OWN_VS_PRT", CollaborationStatus.ACTIVE, false);

        // Pre-create 2 active collaboration-derived permissions
        PermissionEntity p1 = addCollaborationPermission(ctx, ctx.partnerMember, PermissionLevel.VIEW);
        PermissionEntity p2 = addCollaborationPermission(ctx, ctx.ownerMember, PermissionLevel.DOWNLOAD);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> result1 = new AtomicReference<>();
        AtomicReference<CollaborationResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.partnerAdmin));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Both calls should succeed cleanly due to idempotency
        assertThat(error1.get()).isNull();
        assertThat(error2.get()).isNull();
        assertThat(result1.get()).isNotNull();
        assertThat(result2.get()).isNotNull();
        assertThat(result1.get().getStatus()).isEqualTo(CollaborationStatus.REVOKED);
        assertThat(result2.get().getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        // Permissions revoked exactly once
        List<PermissionEntity> activePermissions = permissionRepository.findByCollaboration_IdAndRevokedFalse(ctx.collaboration.getId());
        assertThat(activePermissions).isEmpty();

        // Verify direct permission states
        PermissionEntity postP1 = permissionRepository.findById(p1.getId()).orElseThrow();
        PermissionEntity postP2 = permissionRepository.findById(p2.getId()).orElseThrow();
        assertThat(postP1.isRevoked()).isTrue();
        assertThat(postP2.isRevoked()).isTrue();

        // Audit log: exactly 1 COLLABORATION_REVOKED audit log entry created
        List<PermissionAuditLogEntity> auditLogs = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "COLLABORATION_REVOKED".equals(a.getAction()) && ctx.collaboration.getId().equals(a.getCollaborationId()))
                .toList();
        assertThat(auditLogs).hasSize(1);
    }

    // =========================================================================
    // SCENARIO 6: accept vs revoke
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Accept vs Revoke - Valid lifecycle transitions; final state REVOKED without dangling active state")
    void testScenario6_AcceptVsRevoke_SafeTransitions() throws Exception {
        TestCollabContext ctx = createTestCollabContext("ACC_VS_REV", CollaborationStatus.PENDING, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> acceptResult = new AtomicReference<>();
        AtomicReference<CollaborationResponseDto> revokeResult = new AtomicReference<>();
        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                acceptResult.set(collaborationService.acceptCollaboration(ctx.collaboration.getId(), ctx.partnerAdmin));
            } catch (Throwable t) {
                acceptError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                revokeResult.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Revoke always completes
        assertThat(revokeError.get()).isNull();
        assertThat(revokeResult.get().getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        // Final database state is always REVOKED
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        // If revoke acquired lock first, accept failed with IllegalStateException
        if (acceptError.get() != null) {
            assertThat(acceptError.get()).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Only PENDING collaborations can be accepted");
        }
    }

    // =========================================================================
    // SCENARIO 7: decline vs revoke
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Decline vs Revoke - Both serialize safely; final state REVOKED")
    void testScenario7_DeclineVsRevoke_PENDINGToREVOKED() throws Exception {
        TestCollabContext ctx = createTestCollabContext("DEC_VS_REV", CollaborationStatus.PENDING, false);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> declineResult = new AtomicReference<>();
        AtomicReference<CollaborationResponseDto> revokeResult = new AtomicReference<>();
        AtomicReference<Throwable> declineError = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                declineResult.set(collaborationService.declineCollaboration(ctx.collaboration.getId(), ctx.partnerAdmin));
            } catch (Throwable t) {
                declineError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                revokeResult.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Both drive the collaboration to REVOKED
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.REVOKED);
    }

    // =========================================================================
    // SCENARIO 8: revoke vs scheduler expiration
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Revoke vs Scheduler Expiration - Serialized teardown; zero active permissions remain")
    void testScenario8_RevokeVsSchedulerExpiration_Serialized() throws Exception {
        TestCollabContext ctx = createTestCollabContext("REV_VS_EXP", CollaborationStatus.ACTIVE, true);

        PermissionEntity p1 = addCollaborationPermission(ctx, ctx.partnerMember, PermissionLevel.VIEW);
        PermissionEntity p2 = addCollaborationPermission(ctx, ctx.ownerMember, PermissionLevel.DOWNLOAD);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<CollaborationResponseDto> revokeResult = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        AtomicReference<Throwable> schedulerError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                revokeResult.set(collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin));
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                collaborationExpirationScheduler.processExpiredCollaboration(ctx.collaboration);
            } catch (Throwable t) {
                schedulerError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        assertThat(revokeError.get()).isNull();
        assertThat(schedulerError.get()).isNull();

        // Final status is either REVOKED or EXPIRED
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isIn(CollaborationStatus.REVOKED, CollaborationStatus.EXPIRED);

        // Zero active collaboration permissions survive
        List<PermissionEntity> activePermissions = permissionRepository.findByCollaboration_IdAndRevokedFalse(ctx.collaboration.getId());
        assertThat(activePermissions).isEmpty();

        // Permissions revoked exactly once
        PermissionEntity postP1 = permissionRepository.findById(p1.getId()).orElseThrow();
        PermissionEntity postP2 = permissionRepository.findById(p2.getId()).orElseThrow();
        assertThat(postP1.isRevoked()).isTrue();
        assertThat(postP2.isRevoked()).isTrue();
    }

    // =========================================================================
    // SCENARIO 9: scheduler expiration alone with system actor
    // =========================================================================
    @Test
    @DisplayName("Scenario 9: Scheduler Expiration Alone - Permissions revoked with SYSTEM_SCHEDULER actor; status is EXPIRED")
    void testScenario9_SchedulerExpirationAlone_SuccessWithSystemActor() {
        TestCollabContext ctx = createTestCollabContext("EXP_ALONE", CollaborationStatus.ACTIVE, true);

        PermissionEntity p1 = addCollaborationPermission(ctx, ctx.partnerMember, PermissionLevel.VIEW);
        PermissionEntity p2 = addCollaborationPermission(ctx, ctx.ownerMember, PermissionLevel.DOWNLOAD);

        // Process expiration via scheduler
        collaborationExpirationScheduler.processExpiredCollaboration(ctx.collaboration);

        // Verification: collaboration status is EXPIRED
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);

        // Verification: permissions were torn down
        List<PermissionEntity> activePermissions = permissionRepository.findByCollaboration_IdAndRevokedFalse(ctx.collaboration.getId());
        assertThat(activePermissions).isEmpty();

        PermissionEntity postP1 = permissionRepository.findById(p1.getId()).orElseThrow();
        PermissionEntity postP2 = permissionRepository.findById(p2.getId()).orElseThrow();
        assertThat(postP1.isRevoked()).isTrue();
        assertThat(postP2.isRevoked()).isTrue();
        assertThat(postP1.getRevokedBy()).isNull(); // System actor sets null UserEntity

        // Verification: Audit log contains SYSTEM_SCHEDULER actor entries
        List<PermissionAuditLogEntity> auditLogs = permissionAuditLogRepository.findAll().stream()
                .filter(a -> ctx.collaboration.getId().equals(a.getCollaborationId()))
                .toList();
        assertThat(auditLogs).isNotEmpty();

        boolean hasRevokeAudit = auditLogs.stream()
                .anyMatch(a -> "REVOKE_PERMISSION".equals(a.getAction()) && "SYSTEM_SCHEDULER".equals(a.getActorUserId()));
        assertThat(hasRevokeAudit).isTrue();

        // Verification: Expiration notification sent
        List<NotificationEntity> notifications = notificationRepository.findAll().stream()
                .filter(n -> NotificationType.COLLABORATION_EXPIRED == n.getType() && String.valueOf(ctx.collaboration.getId()).equals(n.getSourceId()))
                .toList();
        assertThat(notifications).isNotEmpty();
    }

    // =========================================================================
    // SCENARIO 10: expiration vs grant permission
    // =========================================================================
    @Test
    @DisplayName("Scenario 10: Expiration vs Grant Permission - Serialized; inactive/expired collaboration rejects grant")
    void testScenario10_ExpirationVsGrantPermission_Serialized() throws Exception {
        TestCollabContext ctx = createTestCollabContext("EXP_VS_GRANT", CollaborationStatus.ACTIVE, true);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> schedulerError = new AtomicReference<>();
        AtomicReference<Throwable> grantError = new AtomicReference<>();

        PermissionRequestDto grantRequest = PermissionRequestDto.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(ctx.partnerMember.getEmail())
                .folderId(ctx.rootFolder.getId())
                .build();

        executor.submit(() -> {
            try {
                startLatch.await();
                collaborationExpirationScheduler.processExpiredCollaboration(ctx.collaboration);
            } catch (Throwable t) {
                schedulerError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                collaborationService.grantCollaborationPermission(ctx.collaboration.getId(), grantRequest, ctx.ownerAdmin);
            } catch (Throwable t) {
                grantError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        assertThat(schedulerError.get()).isNull();

        // Final status is EXPIRED
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);

        // Zero active collaboration permissions survive
        List<PermissionEntity> activePermissions = permissionRepository.findByCollaboration_IdAndRevokedFalse(ctx.collaboration.getId());
        assertThat(activePermissions).isEmpty();
    }

    // =========================================================================
    // SCENARIO 11: rollback during teardown
    // =========================================================================
    @Test
    @DisplayName("Scenario 11: Rollback During Teardown - Transaction rolls back, DB state remains ACTIVE with active permissions")
    void testScenario11_RollbackDuringTeardown_MaintainsActiveState() {
        TestCollabContext ctx = createTestCollabContext("ROLLBACK_TEST", CollaborationStatus.ACTIVE, false);

        PermissionEntity p1 = addCollaborationPermission(ctx, ctx.partnerMember, PermissionLevel.VIEW);

        // Configure spy to simulate unexpected infrastructure failure during collaboration-level audit
        doThrow(new RuntimeException("Simulated audit infrastructure failure on revoke"))
                .when(auditLogService).logPermissionChange(any(AuditPermissionChangeCommand.class));

        try {
            collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin);
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).contains("Simulated audit infrastructure failure on revoke");
        }

        // Direct DB assertions:
        // 1. Collaboration status must remain ACTIVE
        CollaborationEntity postCollab = collaborationRepository.findById(ctx.collaboration.getId()).orElseThrow();
        assertThat(postCollab.getStatus()).isEqualTo(CollaborationStatus.ACTIVE);

        // 2. Permission in DB must remain active
        PermissionEntity postP1 = permissionRepository.findById(p1.getId()).orElseThrow();
        assertThat(postP1.isRevoked()).isFalse();

        // 3. No COLLABORATION_REVOKED audit log persisted
        List<PermissionAuditLogEntity> revokeAudits = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "COLLABORATION_REVOKED".equals(a.getAction()) && ctx.collaboration.getId().equals(a.getCollaborationId()))
                .toList();
        assertThat(revokeAudits).isEmpty();

        // 4. No COLLABORATION_REVOKED notification persisted
        List<NotificationEntity> revokeNotifications = notificationRepository.findAll().stream()
                .filter(n -> NotificationType.COLLABORATION_REVOKED == n.getType() && String.valueOf(ctx.collaboration.getId()).equals(n.getSourceId()))
                .toList();
        assertThat(revokeNotifications).isEmpty();
    }

    // =========================================================================
    // SCENARIO 12: idempotent repeated revoke
    // =========================================================================
    @Test
    @DisplayName("Scenario 12: Idempotent Repeated Revoke - REVOKED and EXPIRED return current state without re-teardown")
    void testScenario12_IdempotentRepeatedRevoke() {
        TestCollabContext ctx = createTestCollabContext("IDEMPOTENT_TEST", CollaborationStatus.ACTIVE, false);

        // First revoke -> transitions ACTIVE -> REVOKED
        CollaborationResponseDto resp1 = collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin);
        assertThat(resp1.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        long auditCountAfterFirstRevoke = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "COLLABORATION_REVOKED".equals(a.getAction()) && ctx.collaboration.getId().equals(a.getCollaborationId()))
                .count();
        assertThat(auditCountAfterFirstRevoke).isEqualTo(1L);

        // Second revoke by same admin -> returns current state, no extra audit log
        CollaborationResponseDto resp2 = collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.ownerAdmin);
        assertThat(resp2.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        // Third revoke by partner admin -> returns current state, no extra audit log
        CollaborationResponseDto resp3 = collaborationService.revokeCollaboration(ctx.collaboration.getId(), ctx.partnerAdmin);
        assertThat(resp3.getStatus()).isEqualTo(CollaborationStatus.REVOKED);

        long auditCountAfterSubsequentRevokes = permissionAuditLogRepository.findAll().stream()
                .filter(a -> "COLLABORATION_REVOKED".equals(a.getAction()) && ctx.collaboration.getId().equals(a.getCollaborationId()))
                .count();
        assertThat(auditCountAfterSubsequentRevokes).isEqualTo(1L);

        // Test with EXPIRED collaboration
        TestCollabContext ctxExpired = createTestCollabContext("IDEMP_EXP", CollaborationStatus.EXPIRED, true);
        CollaborationResponseDto respExpired = collaborationService.revokeCollaboration(ctxExpired.collaboration.getId(), ctxExpired.ownerAdmin);
        assertThat(respExpired.getStatus()).isEqualTo(CollaborationStatus.EXPIRED);
    }
}
