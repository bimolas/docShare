package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.organization.dto.MemberStatusChangeRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberDetailResponseDto;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
public class OrganizationAdminTransferConcurrencyIntegrationTest {

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @MockitoSpyBean
    private AuditLogService auditLogService;

    private static class TestOrgContext {
        String orgCode;
        OrganizationEntity org;
        UserEntity adminA;
        UserEntity adminX;
        UserEntity memberB;
        UserEntity memberC;
        UserEntity memberD;
        UserOrganizationEntity membershipA;
        UserOrganizationEntity membershipX;
        UserOrganizationEntity membershipB;
        UserOrganizationEntity membershipC;
        UserOrganizationEntity membershipD;
    }

    private TestOrgContext createTestOrganizationWithOneAdminAndTwoMembers(String prefix) {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
        TestOrgContext ctx = new TestOrgContext();
        ctx.orgCode = (prefix + "_" + uniqueSuffix).toUpperCase();

        ctx.adminA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_a_" + uniqueSuffix + "@test.com")
                .firstName("AdminA")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.memberB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("member_b_" + uniqueSuffix + "@test.com")
                .firstName("MemberB")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.memberC = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("member_c_" + uniqueSuffix + "@test.com")
                .firstName("MemberC")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(ctx.orgCode)
                .name("Transfer Concurrency Org " + uniqueSuffix)
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
                .user(ctx.memberB)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ctx.membershipC = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.memberC)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        return ctx;
    }

    private TestOrgContext createTestOrganizationWithTwoAdminsAndTwoMembers(String prefix) {
        TestOrgContext ctx = createTestOrganizationWithOneAdminAndTwoMembers(prefix);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        ctx.adminX = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_x_" + uniqueSuffix + "@test.com")
                .firstName("AdminX")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.membershipX = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.adminX)
                .organization(ctx.org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ctx.memberD = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("member_d_" + uniqueSuffix + "@test.com")
                .firstName("MemberD")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.membershipD = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.memberD)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        return ctx;
    }

    @Test
    @DisplayName("Scenario A: Two concurrent transfers from same admin (A->B vs A->C) - Exactly one winner, one admin remains")
    void testScenarioA_ConcurrentTransfersFromSameAdmin_ExactlyOneWinner() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithOneAdminAndTwoMembers("XFER_RACE_SAME_ADMIN");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberB.getId(), ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberC.getId(), ctx.adminA));
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

        int successCount = 0;
        int failureCount = 0;
        if (result1.get() != null) successCount++;
        if (error1.get() != null) failureCount++;
        if (result2.get() != null) successCount++;
        if (error2.get() != null) failureCount++;

        // Exactly one transaction must win and one must fail
        assertThat(successCount).isEqualTo(1);
        assertThat(failureCount).isEqualTo(1);

        // Verify loser failed with AccessDeniedException (since Admin A became ORG_MANAGER upon winner's commit)
        Throwable failedError = error1.get() != null ? error1.get() : error2.get();
        assertThat(failedError).hasMessageContaining("Only Organization Administrators can transfer administration");

        // Verify in PostgreSQL that exactly ONE active ORG_ADMIN exists
        long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(activeAdminCount).isEqualTo(1L);

        // Verify Admin A is now ROLE_ORG_MANAGER
        UserOrganizationEntity postAdminA = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.adminA.getId(), ctx.orgCode).orElseThrow();
        assertThat(postAdminA.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);

        // Verify either B or C is ORG_ADMIN, and the other is still USER
        UserOrganizationEntity postB = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.memberB.getId(), ctx.orgCode).orElseThrow();
        UserOrganizationEntity postC = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.memberC.getId(), ctx.orgCode).orElseThrow();

        if (result1.get() != null) {
            assertThat(postB.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postC.getRole()).isEqualTo(Role.ROLE_USER);
        } else {
            assertThat(postC.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postB.getRole()).isEqualTo(Role.ROLE_USER);
        }
    }

    @Test
    @DisplayName("Scenario B: Two distinct ORG_ADMIN actors concurrently transferring (A->B vs X->D) - Serialized, invariants preserved")
    void testScenarioB_ConcurrentTransfersDistinctAdmins_SerializedProperly() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdminsAndTwoMembers("XFER_RACE_DISTINCT_ADMINS");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberB.getId(), ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberD.getId(), ctx.adminX));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        // Serialization and Invariant Verification:
        // Under pessimistic aggregate locking, both threads attempt to acquire lock on the same OrganizationEntity.
        // Either they serialize and both succeed, or one thread times out waiting for the lock (CannotAcquireLockException).
        boolean thread1Success = (result1.get() != null && error1.get() == null);
        boolean thread2Success = (result2.get() != null && error2.get() == null);

        assertThat(thread1Success || thread2Success).isTrue();

        if (error1.get() != null) {
            assertThat(error1.get()).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
        }
        if (error2.get() != null) {
            assertThat(error2.get()).isInstanceOf(org.springframework.dao.CannotAcquireLockException.class);
        }

        // Verify in PostgreSQL that the organization maintains valid admin count
        long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(activeAdminCount).isEqualTo(2L);

        UserOrganizationEntity postA = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.adminA.getId(), ctx.orgCode).orElseThrow();
        UserOrganizationEntity postX = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.adminX.getId(), ctx.orgCode).orElseThrow();
        UserOrganizationEntity postB = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.memberB.getId(), ctx.orgCode).orElseThrow();
        UserOrganizationEntity postD = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.memberD.getId(), ctx.orgCode).orElseThrow();

        if (thread1Success && thread2Success) {
            assertThat(postA.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
            assertThat(postX.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
            assertThat(postB.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postD.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        } else if (thread1Success) {
            assertThat(postA.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
            assertThat(postB.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postX.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postD.getRole()).isEqualTo(Role.ROLE_USER);
        } else {
            assertThat(postX.getRole()).isEqualTo(Role.ROLE_ORG_MANAGER);
            assertThat(postD.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postA.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
            assertThat(postB.getRole()).isEqualTo(Role.ROLE_USER);
        }
    }

    @Test
    @DisplayName("Scenario C: Transfer A->B vs removing B concurrently - Serialized, no invalid state")
    void testScenarioC_TransferVsRemoveTarget_SerializedSafe() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdminsAndTwoMembers("XFER_VS_REMOVE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> transferResult = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> removeResult = new AtomicReference<>();
        AtomicReference<Throwable> transferError = new AtomicReference<>();
        AtomicReference<Throwable> removeError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                transferResult.set(organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberB.getId(), ctx.adminA));
            } catch (Throwable t) {
                transferError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MemberStatusChangeRequestDto removeReq = MemberStatusChangeRequestDto.builder().reason("Offboarding").build();
                removeResult.set(organizationService.removeMember(ctx.orgCode, ctx.memberB.getId(), removeReq, ctx.adminX));
            } catch (Throwable t) {
                removeError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Either transfer ran first (B promoted to ADMIN, then removeMember ran on B),
        // or removeMember ran first (B marked REMOVED, then transferOrgAdmin rejected B as non-active).
        UserOrganizationEntity postB = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.memberB.getId(), ctx.orgCode).orElseThrow();

        if (removeError.get() != null) {
            // Removal failed (e.g. if last-admin guard or capability check triggered)
            assertThat(transferResult.get()).isNotNull();
        } else if (transferError.get() != null) {
            // Transfer failed because B was removed before transfer executed
            assertThat(transferError.get()).hasMessageContaining("Target user must be an active member");
            assertThat(postB.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        } else {
            // Both executed serially: transfer made B admin, then remove made B removed
            assertThat(postB.getStatus()).isEqualTo(MembershipStatus.REMOVED);
        }

        // Invariant: Organization retains at least one active administrator
        long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(activeAdminCount).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Scenario D: Transfer A->B vs suspending B concurrently - If suspended first, transfer rejected")
    void testScenarioD_TransferVsSuspendTarget_SerializedSafe() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdminsAndTwoMembers("XFER_VS_SUSPEND");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> transferResult = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> suspendResult = new AtomicReference<>();
        AtomicReference<Throwable> transferError = new AtomicReference<>();
        AtomicReference<Throwable> suspendError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                transferResult.set(organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberB.getId(), ctx.adminA));
            } catch (Throwable t) {
                transferError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                MemberStatusChangeRequestDto suspendReq = MemberStatusChangeRequestDto.builder().reason("Investigation").build();
                suspendResult.set(organizationService.suspendMember(ctx.orgCode, ctx.memberB.getId(), suspendReq, ctx.adminX));
            } catch (Throwable t) {
                suspendError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        UserOrganizationEntity postB = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.memberB.getId(), ctx.orgCode).orElseThrow();

        if (transferError.get() != null) {
            // Suspension ran first; transfer rejected B because B is SUSPENDED
            assertThat(transferError.get()).hasMessageContaining("Target user must be an active member");
            assertThat(postB.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
            assertThat(postB.getRole()).isEqualTo(Role.ROLE_USER);
        } else {
            // Transfer ran first; B became ORG_ADMIN, then suspend ran
            assertThat(transferResult.get()).isNotNull();
            assertThat(suspendResult.get()).isNotNull();
            assertThat(postB.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        }

        // Invariant: Organization retains at least one active administrator (Admin X is still active)
        long activeAdminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(activeAdminCount).isGreaterThanOrEqualTo(1L);
    }

    @Test
    @DisplayName("Scenario E: Rollback integrity in PostgreSQL - Failed mutation rolls back all entity and audit states")
    void testScenarioE_RollbackIntegrity_VerifiedDirectlyInPostgres() {
        TestOrgContext ctx = createTestOrganizationWithOneAdminAndTwoMembers("ROLLBACK_INTEGRITY");

        long initialAuditCount = permissionAuditLogRepository.count();
        long initialNotificationCount = notificationRepository.count();

        // Configure spy to simulate an unhandled failure during audit logging inside the transaction
        doThrow(new RuntimeException("Simulated audit infrastructure failure"))
                .when(auditLogService).logPermissionChange(
                        anyString(), anyString(), any(), any(), any(), any(),
                        anyString(), any(), anyString(), anyString(), any(),
                        any(), any(), anyString(), any(), any(), any()
                );

        try {
            organizationService.transferOrgAdmin(ctx.orgCode, ctx.memberB.getId(), ctx.adminA);
        } catch (RuntimeException ex) {
            assertThat(ex.getMessage()).isEqualTo("Simulated audit infrastructure failure");
        }

        // Direct PostgreSQL verification:
        // 1. Actor role must remain ROLE_ORG_ADMIN
        UserOrganizationEntity postAdminA = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.adminA.getId(), ctx.orgCode).orElseThrow();
        assertThat(postAdminA.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);

        // 2. Target role must remain ROLE_USER
        UserOrganizationEntity postMemberB = userOrganizationRepository
                .findByUser_IdAndOrganization_OrgCode(ctx.memberB.getId(), ctx.orgCode).orElseThrow();
        assertThat(postMemberB.getRole()).isEqualTo(Role.ROLE_USER);

        // 3. No partial audit record written in PostgreSQL
        long postAuditCount = permissionAuditLogRepository.count();
        assertThat(postAuditCount).isEqualTo(initialAuditCount);

        // 4. No partial notification record written in PostgreSQL
        long postNotificationCount = notificationRepository.count();
        assertThat(postNotificationCount).isEqualTo(initialNotificationCount);
    }
}
