package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.organization.dto.CreateOrganizationInvitationRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationInvitationResponseDto;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.*;
import com.subject77.docshare.modules.organization.scheduler.OrganizationInvitationExpirationScheduler;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
public class OrganizationInvitationConcurrencyIntegrationTest {

    @Autowired
    private OrganizationInvitationService invitationService;

    @Autowired
    private OrganizationInvitationRepository invitationRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OrganizationInvitationExpirationScheduler expirationScheduler;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private static class TestOrgContext {
        String suffix;
        String orgCode;
        OrganizationEntity org;
        UserEntity adminA;
        UserEntity adminB;
        UserEntity inviteeUser;
        GroupEntity group;
        UserOrganizationEntity membershipA;
        UserOrganizationEntity membershipB;
    }

    private TestOrgContext createTestContext(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        TestOrgContext ctx = new TestOrgContext();
        ctx.suffix = suffix;
        ctx.orgCode = (prefix + "_" + suffix).toUpperCase();

        ctx.adminA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_a_" + suffix + "@concurrency.com")
                .firstName("AdminA")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.adminB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_b_" + suffix + "@concurrency.com")
                .firstName("AdminB")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.inviteeUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("invitee_" + suffix + "@concurrency.com")
                .firstName("Invitee")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(ctx.orgCode)
                .name("Concurrency Test Org " + suffix)
                .createdBy(ctx.adminA)
                .build());

        ctx.membershipA = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.adminA)
                .organization(ctx.org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ctx.membershipB = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.adminB)
                .organization(ctx.org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ctx.group = groupRepository.save(GroupEntity.builder()
                .name("Group_" + suffix)
                .organization(ctx.org)
                .createdBy(ctx.adminA)
                .build());

        return ctx;
    }

    // =========================================================================
    // SCENARIO 1: Concurrent duplicate invitation creation
    // =========================================================================
    @Test
    @DisplayName("Scenario 1: Concurrent duplicate invitation creation - exactly 1 PENDING invitation row")
    void testScenario1_ConcurrentDuplicateInvitationCreation() throws Exception {
        TestOrgContext ctx = createTestContext("INV_CREATE_RACE");
        String targetEmail = ctx.inviteeUser.getEmail();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationInvitationResponseDto> resultA = new AtomicReference<>();
        AtomicReference<OrganizationInvitationResponseDto> resultB = new AtomicReference<>();
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(targetEmail)
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        // Admin A creates invitation
        executor.submit(() -> {
            try {
                startLatch.await();
                resultA.set(invitationService.createInvitation(ctx.orgCode, request, ctx.adminA));
            } catch (Throwable t) {
                errorA.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Admin B creates invitation concurrently
        executor.submit(() -> {
            try {
                startLatch.await();
                resultB.set(invitationService.createInvitation(ctx.orgCode, request, ctx.adminB));
            } catch (Throwable t) {
                errorB.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Exactly one should succeed and one should fail
        boolean successA = (resultA.get() != null && errorA.get() == null);
        boolean successB = (resultB.get() != null && errorB.get() == null);
        assertThat(successA ^ successB)
                .as("Exactly one creation thread must succeed")
                .isTrue();

        Throwable failedError = successA ? errorB.get() : errorA.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOf(IllegalArgumentException.class);
        assertThat(failedError.getMessage()).contains("already exists");

        // Direct PostgreSQL state verification: exactly 1 PENDING invitation in database
        List<OrganizationInvitationEntity> pendingInvitations = invitationRepository
                .findByOrganization_OrgCodeAndStatus(ctx.orgCode, OrganizationInvitationStatus.PENDING);
        assertThat(pendingInvitations).hasSize(1);
        assertThat(pendingInvitations.get(0).getInviteeEmail()).isEqualTo(targetEmail.toLowerCase());

        // Verify audit log has exactly 1 CREATE_INVITATION
        List<PermissionAuditLogEntity> audits = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(ctx.orgCode);
        long createAuditCount = audits.stream()
                .filter(a -> "CREATE_INVITATION".equals(a.getAction()))
                .count();
        assertThat(createAuditCount).isEqualTo(1L);

        // Verify exactly 1 notification sent for invitation creation
        Long winningInvId = pendingInvitations.get(0).getId();
        String expectedNotifKey = "ORG_INVITATION_SENT:" + winningInvId + ":" + ctx.inviteeUser.getId();
        boolean notifExists = notificationRepository.existsByIdempotencyKey(expectedNotifKey);
        assertThat(notifExists).isTrue();
    }

    // =========================================================================
    // SCENARIO 2: Concurrent accept same token
    // =========================================================================
    @Test
    @DisplayName("Scenario 2: Concurrent accept same token - exactly 1 succeeds, 1 membership, 1 onboarding")
    void testScenario2_ConcurrentAcceptSameToken() throws Exception {
        TestOrgContext ctx = createTestContext("INV_ACCEPT_RACE");

        // Create invitation with onboarding group assignment
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_ORG_MANAGER)
                .expirationDays(7)
                .targetGroupId(ctx.group.getId())
                .targetGroupRole(GroupMemberRole.MEMBER)
                .build();

        OrganizationInvitationResponseDto created = invitationService.createInvitation(ctx.orgCode, request, ctx.adminA);
        String token = created.getToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationInvitationResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationInvitationResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(invitationService.acceptInvitation(token, ctx.inviteeUser));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(invitationService.acceptInvitation(token, ctx.inviteeUser));
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

        boolean success1 = (result1.get() != null && error1.get() == null);
        boolean success2 = (result2.get() != null && error2.get() == null);
        assertThat(success1 ^ success2)
                .as("Exactly one accept thread must succeed")
                .isTrue();

        Throwable failedError = success1 ? error2.get() : error1.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOf(IllegalStateException.class);
        assertThat(failedError.getMessage()).contains("no longer pending");

        // Direct PostgreSQL verification
        OrganizationInvitationEntity dbInv = invitationRepository.findByToken(token).orElseThrow();
        assertThat(dbInv.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);
        assertThat(dbInv.getRespondedAt()).isNotNull();

        // Exactly 1 membership row in user_organizations
        List<UserOrganizationEntity> memberships = userOrganizationRepository
                .findAll().stream()
                .filter(m -> m.getOrganization().getOrgCode().equals(ctx.orgCode)
                        && m.getUser().getId().equals(ctx.inviteeUser.getId()))
                .toList();
        assertThat(memberships).hasSize(1);
        assertThat(memberships.get(0).getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(memberships.get(0).getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);

        // Exactly 1 group membership from onboarding
        List<UserGroupEntity> groupMemberships = userGroupRepository.findByGroup_Id(ctx.group.getId());
        long userInGroupCount = groupMemberships.stream()
                .filter(ug -> ug.getUser().getId().equals(ctx.inviteeUser.getId()))
                .count();
        assertThat(userInGroupCount).isEqualTo(1L);

        // Exactly 1 ACCEPT_INVITATION audit event
        List<PermissionAuditLogEntity> audits = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(ctx.orgCode);
        long acceptAudits = audits.stream().filter(a -> "ACCEPT_INVITATION".equals(a.getAction())).count();
        assertThat(acceptAudits).isEqualTo(1L);

        // Exactly 1 acceptance notification sent to inviter
        String acceptNotifKey = "ORG_INVITATION_ACCEPTED:" + dbInv.getId() + ":" + dbInv.getInviter().getId();
        boolean acceptNotifExists = notificationRepository.existsByIdempotencyKey(acceptNotifKey);
        assertThat(acceptNotifExists).isTrue();
    }

    // =========================================================================
    // SCENARIO 3: Concurrent accept vs revoke
    // =========================================================================
    @Test
    @DisplayName("Scenario 3: Concurrent accept vs revoke - deterministic terminal state (ACCEPTED or REVOKED)")
    void testScenario3_ConcurrentAcceptVsRevoke() throws Exception {
        TestOrgContext ctx = createTestContext("INV_ACC_VS_REV");

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto created = invitationService.createInvitation(ctx.orgCode, request, ctx.adminA);
        String token = created.getToken();
        Long invId = created.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationInvitationResponseDto> acceptResult = new AtomicReference<>();
        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        AtomicReference<Throwable> revokeError = new AtomicReference<>();

        // Thread 1: Revoke by admin
        executor.submit(() -> {
            try {
                startLatch.await();
                invitationService.revokeInvitation(ctx.orgCode, invId, ctx.adminA);
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Accept by invitee
        executor.submit(() -> {
            try {
                startLatch.await();
                acceptResult.set(invitationService.acceptInvitation(token, ctx.inviteeUser));
            } catch (Throwable t) {
                acceptError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Exactly one must succeed and one must fail
        boolean acceptSuccess = (acceptResult.get() != null && acceptError.get() == null);
        boolean revokeSuccess = (revokeError.get() == null);
        assertThat(acceptSuccess ^ revokeSuccess)
                .as("Either accept or revoke must succeed, never both")
                .isTrue();

        OrganizationInvitationEntity dbInv = invitationRepository.findById(invId).orElseThrow();
        if (acceptSuccess) {
            assertThat(dbInv.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);
            assertThat(revokeError.get()).isInstanceOf(IllegalStateException.class);
            // Member should be ACTIVE
            UserOrganizationEntity uo = userOrganizationRepository
                    .findByUser_IdAndOrganization_OrgCode(ctx.inviteeUser.getId(), ctx.orgCode).orElseThrow();
            assertThat(uo.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        } else {
            assertThat(dbInv.getStatus()).isEqualTo(OrganizationInvitationStatus.REVOKED);
            assertThat(acceptError.get()).isInstanceOf(IllegalStateException.class);
            // Member should not exist
            assertThat(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.inviteeUser.getId(), ctx.orgCode))
                    .isEmpty();
        }
    }

    // =========================================================================
    // SCENARIO 4: Concurrent accept vs decline
    // =========================================================================
    @Test
    @DisplayName("Scenario 4: Concurrent accept vs decline - deterministic terminal state (ACCEPTED or DECLINED)")
    void testScenario4_ConcurrentAcceptVsDecline() throws Exception {
        TestOrgContext ctx = createTestContext("INV_ACC_VS_DEC");

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto created = invitationService.createInvitation(ctx.orgCode, request, ctx.adminA);
        String token = created.getToken();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationInvitationResponseDto> acceptResult = new AtomicReference<>();
        AtomicReference<OrganizationInvitationResponseDto> declineResult = new AtomicReference<>();
        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        AtomicReference<Throwable> declineError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                acceptResult.set(invitationService.acceptInvitation(token, ctx.inviteeUser));
            } catch (Throwable t) {
                acceptError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                declineResult.set(invitationService.declineInvitation(token, ctx.inviteeUser));
            } catch (Throwable t) {
                declineError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean acceptSuccess = (acceptResult.get() != null && acceptError.get() == null);
        boolean declineSuccess = (declineResult.get() != null && declineError.get() == null);
        assertThat(acceptSuccess ^ declineSuccess)
                .as("Either accept or decline must succeed, never both")
                .isTrue();

        OrganizationInvitationEntity dbInv = invitationRepository.findByToken(token).orElseThrow();
        if (acceptSuccess) {
            assertThat(dbInv.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);
            assertThat(declineError.get()).isInstanceOf(IllegalStateException.class);
        } else {
            assertThat(dbInv.getStatus()).isEqualTo(OrganizationInvitationStatus.DECLINED);
            assertThat(acceptError.get()).isInstanceOf(IllegalStateException.class);
        }
    }

    // =========================================================================
    // SCENARIO 5: Concurrent accept vs expiration
    // =========================================================================
    @Test
    @DisplayName("Scenario 5: Concurrent accept vs expiration - valid terminal state")
    void testScenario5_ConcurrentAcceptVsExpiration() throws Exception {
        TestOrgContext ctx = createTestContext("INV_ACC_VS_EXP");

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto created = invitationService.createInvitation(ctx.orgCode, request, ctx.adminA);
        String token = created.getToken();

        // Expire the invitation row directly in DB
        OrganizationInvitationEntity dbInv = invitationRepository.findByToken(token).orElseThrow();
        dbInv.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));
        invitationRepository.saveAndFlush(dbInv);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationInvitationResponseDto> acceptResult = new AtomicReference<>();
        AtomicReference<Throwable> acceptError = new AtomicReference<>();
        AtomicReference<Integer> schedulerResult = new AtomicReference<>();
        AtomicReference<Throwable> schedulerError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                acceptResult.set(invitationService.acceptInvitation(token, ctx.inviteeUser));
            } catch (Throwable t) {
                acceptError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                schedulerResult.set(invitationService.processExpiredInvitations());
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

        // Since the invitation is expired, accept MUST fail because lazy expiration or scheduler detects expiry
        assertThat(acceptError.get()).isNotNull();
        assertThat(acceptError.get()).isInstanceOf(IllegalStateException.class);
        assertThat(acceptError.get().getMessage()).contains("expired");

        // DB state must be EXPIRED
        OrganizationInvitationEntity finalInv = invitationRepository.findByToken(token).orElseThrow();
        assertThat(finalInv.getStatus()).isEqualTo(OrganizationInvitationStatus.EXPIRED);

        // Invitee must NOT have ACTIVE membership
        assertThat(userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.inviteeUser.getId(), ctx.orgCode))
                .isEmpty();
    }

    // =========================================================================
    // SCENARIO 6: Re-invite REMOVED member
    // =========================================================================
    @Test
    @DisplayName("Scenario 6: Re-invite REMOVED member - reactivates existing membership to ACTIVE with invitation role")
    void testScenario6_ReinviteRemovedMember_ReactivatesToActive() {
        TestOrgContext ctx = createTestContext("INV_REINVITE_REM");

        // 1. Create a REMOVED membership row
        UserOrganizationEntity removedMembership = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.inviteeUser)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.REMOVED)
                .joinedAt(Instant.now().minus(30, ChronoUnit.DAYS))
                .build());

        // 2. Admin creates invitation with ROLE_ORG_MANAGER
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_ORG_MANAGER)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto invitationDto = invitationService.createInvitation(ctx.orgCode, request, ctx.adminA);
        assertThat(invitationDto).isNotNull();
        assertThat(invitationDto.getStatus()).isEqualTo(OrganizationInvitationStatus.PENDING);

        // 3. User accepts invitation
        OrganizationInvitationResponseDto acceptedDto = invitationService.acceptInvitation(invitationDto.getToken(), ctx.inviteeUser);
        assertThat(acceptedDto.getStatus()).isEqualTo(OrganizationInvitationStatus.ACCEPTED);

        // 4. Assert direct PostgreSQL state: exactly 1 UserOrganization row, status ACTIVE, role ROLE_ORG_MANAGER
        List<UserOrganizationEntity> memberships = userOrganizationRepository
                .findAll().stream()
                .filter(m -> m.getOrganization().getOrgCode().equals(ctx.orgCode)
                        && m.getUser().getId().equals(ctx.inviteeUser.getId()))
                .toList();

        assertThat(memberships).hasSize(1);
        UserOrganizationEntity reactivated = memberships.get(0);
        assertThat(reactivated.getId()).isEqualTo(removedMembership.getId());
        assertThat(reactivated.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(reactivated.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
    }

    // =========================================================================
    // SCENARIO 7: Re-invite SUSPENDED member
    // =========================================================================
    @Test
    @DisplayName("Scenario 7: Re-invite SUSPENDED member - rejected with IllegalStateException")
    void testScenario7_ReinviteSuspendedMember_Rejected() {
        TestOrgContext ctx = createTestContext("INV_REINVITE_SUS");

        // 1. Create a SUSPENDED membership row
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.inviteeUser)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.SUSPENDED)
                .joinedAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build());

        // 2. Admin attempts to create invitation
        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        assertThatThrownBy(() -> invitationService.createInvitation(ctx.orgCode, request, ctx.adminA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suspended");

        // Verify direct PostgreSQL state: no invitation created
        List<OrganizationInvitationEntity> invitations = invitationRepository
                .findByOrganization_OrgCodeAndStatus(ctx.orgCode, OrganizationInvitationStatus.PENDING);
        assertThat(invitations).isEmpty();
    }

    // =========================================================================
    // SCENARIO 8: Concurrent revoke vs expiration
    // =========================================================================
    @Test
    @DisplayName("Scenario 8: Concurrent revoke vs expiration - deterministic terminal state EXPIRED")
    void testScenario8_ConcurrentRevokeVsExpiration() throws Exception {
        TestOrgContext ctx = createTestContext("INV_REV_VS_EXP");

        CreateOrganizationInvitationRequestDto request = CreateOrganizationInvitationRequestDto.builder()
                .inviteeEmail(ctx.inviteeUser.getEmail())
                .role(Role.ROLE_USER)
                .expirationDays(7)
                .build();

        OrganizationInvitationResponseDto created = invitationService.createInvitation(ctx.orgCode, request, ctx.adminA);
        Long invId = created.getId();

        // Expire the invitation row directly in DB
        OrganizationInvitationEntity dbInv = invitationRepository.findById(invId).orElseThrow();
        dbInv.setExpiresAt(Instant.now().minus(2, ChronoUnit.HOURS));
        invitationRepository.saveAndFlush(dbInv);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> revokeError = new AtomicReference<>();
        AtomicReference<Integer> schedulerResult = new AtomicReference<>();
        AtomicReference<Throwable> schedulerError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                invitationService.revokeInvitation(ctx.orgCode, invId, ctx.adminA);
            } catch (Throwable t) {
                revokeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                schedulerResult.set(invitationService.processExpiredInvitations());
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

        // In all cases, because expiresAt is in the past:
        // - If revoke runs first, lazy expiration transitions to EXPIRED and throws "has expired and cannot be revoked"
        // - If scheduler runs first, it transitions to EXPIRED and revoke throws "has expired and cannot be revoked"
        assertThat(revokeError.get()).isNotNull();
        assertThat(revokeError.get()).isInstanceOf(IllegalStateException.class);
        assertThat(revokeError.get().getMessage()).contains("expired");

        // DB state must be EXPIRED
        OrganizationInvitationEntity finalInv = invitationRepository.findById(invId).orElseThrow();
        assertThat(finalInv.getStatus()).isEqualTo(OrganizationInvitationStatus.EXPIRED);
        assertThat(finalInv.getRespondedAt()).isNull();
    }
}
