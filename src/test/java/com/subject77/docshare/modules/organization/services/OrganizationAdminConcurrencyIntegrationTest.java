package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.notification.entities.NotificationEntity;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.organization.dto.MemberStatusChangeRequestDto;
import com.subject77.docshare.modules.organization.dto.OrganizationMemberDetailResponseDto;
import com.subject77.docshare.modules.organization.dto.UpdateMemberRoleRequestDto;
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
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class OrganizationAdminConcurrencyIntegrationTest {

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

    private static class TestOrgContext {
        String orgCode;
        OrganizationEntity org;
        UserEntity adminA;
        UserEntity adminB;
        UserEntity adminC;
        UserOrganizationEntity membershipA;
        UserOrganizationEntity membershipB;
        UserOrganizationEntity membershipC;
    }

    private TestOrgContext createTestOrganizationWithTwoAdmins(String prefix) {
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

        ctx.adminB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_b_" + uniqueSuffix + "@test.com")
                .firstName("AdminB")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(ctx.orgCode)
                .name("Concurrency Test Org " + uniqueSuffix)
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

        return ctx;
    }

    private TestOrgContext createTestOrganizationWithThreeAdmins(String prefix) {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins(prefix);
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        ctx.adminC = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_c_" + uniqueSuffix + "@test.com")
                .firstName("AdminC")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.membershipC = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.adminC)
                .organization(ctx.org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        return ctx;
    }

    @Test
    @DisplayName("A. Concurrent Demotion: Two admins demoting each other leaves exactly 1 admin, 1 failure")
    void testConcurrentDemotions_MaintainsAtLeastOneAdmin() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins("DEMOTE_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        UpdateMemberRoleRequestDto demoteRequest = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();

        // Thread 1: Admin A demotes Admin B
        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminB.getId(), demoteRequest, ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Admin B demotes Admin A
        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminA.getId(), demoteRequest, ctx.adminB));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Fire concurrently
        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Exactly one should succeed and one should fail
        boolean thread1Succeeded = (result1.get() != null && error1.get() == null);
        boolean thread2Succeeded = (result2.get() != null && error2.get() == null);
        assertThat(thread1Succeeded ^ thread2Succeeded).isTrue();

        Throwable failedError = thread1Succeeded ? error2.get() : error1.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOfAny(IllegalStateException.class, org.springframework.security.access.AccessDeniedException.class);

        // Database row verification: exactly 1 active admin remains
        long remainingAdmins = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(remainingAdmins).isEqualTo(1L);

        // Verification of clean rollback on rejected thread:
        // The winning demoted user is now ROLE_USER; the losing user remains ROLE_ORG_ADMIN
        String survivingAdminId = thread1Succeeded ? ctx.adminA.getId() : ctx.adminB.getId();
        String demotedUserId = thread1Succeeded ? ctx.adminB.getId() : ctx.adminA.getId();

        UserOrganizationEntity survivingMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(survivingAdminId, ctx.orgCode).orElseThrow();
        assertThat(survivingMembership.getRole()).isEqualTo(Role.ROLE_ORG_ADMIN);
        assertThat(survivingMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        UserOrganizationEntity demotedMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(demotedUserId, ctx.orgCode).orElseThrow();
        assertThat(demotedMembership.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(demotedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // Audit log check: exactly 1 audit entry written for this organization
        List<PermissionAuditLogEntity> auditLogs = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(ctx.orgCode);
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getAction()).isEqualTo("UPDATE_MEMBER_ROLE");

        // Notification check: only the demoted user received a role-updated notification
        List<NotificationEntity> demotedNotifications = notificationRepository.findByRecipient_Id(demotedUserId, Pageable.unpaged()).getContent();
        assertThat(demotedNotifications).isNotEmpty();

        List<NotificationEntity> survivingNotifications = notificationRepository.findByRecipient_Id(survivingAdminId, Pageable.unpaged()).getContent();
        assertThat(survivingNotifications).isEmpty();
    }

    @Test
    @DisplayName("B. Concurrent Suspension: Two admins suspending each other leaves exactly 1 admin, 1 failure")
    void testConcurrentSuspensions_MaintainsAtLeastOneAdmin() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins("SUSPEND_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        MemberStatusChangeRequestDto suspendRequest = MemberStatusChangeRequestDto.builder()
                .reason("Security investigation")
                .build();

        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.suspendMember(ctx.orgCode, ctx.adminB.getId(), suspendRequest, ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.suspendMember(ctx.orgCode, ctx.adminA.getId(), suspendRequest, ctx.adminB));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean thread1Succeeded = (result1.get() != null && error1.get() == null);
        boolean thread2Succeeded = (result2.get() != null && error2.get() == null);
        assertThat(thread1Succeeded ^ thread2Succeeded).isTrue();

        Throwable failedError = thread1Succeeded ? error2.get() : error1.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOfAny(IllegalStateException.class, org.springframework.security.access.AccessDeniedException.class);

        long remainingAdmins = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(remainingAdmins).isEqualTo(1L);

        // Verify rejected admin was not modified to SUSPENDED
        String survivingAdminId = thread1Succeeded ? ctx.adminA.getId() : ctx.adminB.getId();
        UserOrganizationEntity survivingMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(survivingAdminId, ctx.orgCode).orElseThrow();
        assertThat(survivingMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);

        // Exactly 1 audit entry for SUSPEND_MEMBER
        List<PermissionAuditLogEntity> auditLogs = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(ctx.orgCode);
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getAction()).isEqualTo("SUSPEND_MEMBER");
    }

    @Test
    @DisplayName("C. Concurrent Removal: Two admins removing each other leaves exactly 1 admin, 1 failure")
    void testConcurrentRemovals_MaintainsAtLeastOneAdmin() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins("REMOVE_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        MemberStatusChangeRequestDto removeRequest = MemberStatusChangeRequestDto.builder()
                .reason("Departing organization")
                .build();

        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.removeMember(ctx.orgCode, ctx.adminB.getId(), removeRequest, ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.removeMember(ctx.orgCode, ctx.adminA.getId(), removeRequest, ctx.adminB));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean thread1Succeeded = (result1.get() != null && error1.get() == null);
        boolean thread2Succeeded = (result2.get() != null && error2.get() == null);
        assertThat(thread1Succeeded ^ thread2Succeeded).isTrue();

        Throwable failedError = thread1Succeeded ? error2.get() : error1.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOfAny(IllegalStateException.class, org.springframework.security.access.AccessDeniedException.class);

        long remainingAdmins = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(remainingAdmins).isEqualTo(1L);

        // Verify rejected admin remains ACTIVE
        String survivingAdminId = thread1Succeeded ? ctx.adminA.getId() : ctx.adminB.getId();
        UserOrganizationEntity survivingMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(survivingAdminId, ctx.orgCode).orElseThrow();
        assertThat(survivingMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    @DisplayName("D. Demotion vs Suspension: Concurrent cross-operations leave exactly 1 admin, 1 failure")
    void testConcurrentDemotionVsSuspension_MaintainsAtLeastOneAdmin() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins("CROSS_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        UpdateMemberRoleRequestDto demoteRequest = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();
        MemberStatusChangeRequestDto suspendRequest = MemberStatusChangeRequestDto.builder()
                .reason("Concurrent investigation")
                .build();

        // Thread 1: Admin A demotes Admin B
        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminB.getId(), demoteRequest, ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Admin B suspends Admin A
        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.suspendMember(ctx.orgCode, ctx.adminA.getId(), suspendRequest, ctx.adminB));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean thread1Succeeded = (result1.get() != null && error1.get() == null);
        boolean thread2Succeeded = (result2.get() != null && error2.get() == null);
        assertThat(thread1Succeeded ^ thread2Succeeded).isTrue();

        Throwable failedError = thread1Succeeded ? error2.get() : error1.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOfAny(IllegalStateException.class, org.springframework.security.access.AccessDeniedException.class);

        long remainingAdmins = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(remainingAdmins).isEqualTo(1L);
    }

    @Test
    @DisplayName("E. Single Admin Protection: Demotion/Suspension/Removal rejected immediately")
    void testSingleAdminProtection_FailsImmediately() {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins("SINGLE_ADMIN");

        // First, demote admin B normally so only Admin A remains
        UpdateMemberRoleRequestDto demoteRequest = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();
        organizationService.updateMemberRole(ctx.orgCode, ctx.adminB.getId(), demoteRequest, ctx.adminA);

        long adminCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(adminCount).isEqualTo(1L);

        // Attempting to demote the last remaining admin (Admin A) fails
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                organizationService.updateMemberRole(ctx.orgCode, ctx.adminA.getId(), demoteRequest, ctx.adminA));

        // Attempting to suspend the last remaining admin fails (self-suspension blocked upfront)
        MemberStatusChangeRequestDto suspendRequest = MemberStatusChangeRequestDto.builder().reason("Test").build();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                organizationService.suspendMember(ctx.orgCode, ctx.adminA.getId(), suspendRequest, ctx.adminA));

        // Attempting to remove the last remaining admin fails (self-removal blocked upfront)
        MemberStatusChangeRequestDto removeRequest = MemberStatusChangeRequestDto.builder().reason("Test").build();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
                organizationService.removeMember(ctx.orgCode, ctx.adminA.getId(), removeRequest, ctx.adminA));

        // Invariant check: still 1 active admin
        long finalCount = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(finalCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("F. Three-Admin Concurrent Demotions: Serialized demotion of 2 admins succeeds, leaving exactly 1 admin")
    void testThreeAdminConcurrentDemotions_AllowsTwoDemotionsLeavingOneAdmin() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithThreeAdmins("THREE_ADMIN_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        UpdateMemberRoleRequestDto demoteRequest = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();

        // Thread 1: Admin A demotes Admin B
        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminB.getId(), demoteRequest, ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Admin A demotes Admin C
        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminC.getId(), demoteRequest, ctx.adminA));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Both demotions should succeed because initial count is 3 and they serialize: 3 -> 2 -> 1
        assertThat(error1.get()).isNull();
        assertThat(error2.get()).isNull();
        assertThat(result1.get()).isNotNull();
        assertThat(result2.get()).isNotNull();

        // Exactly 1 active admin remains
        long remainingAdmins = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(remainingAdmins).isEqualTo(1L);

        // Demoting the final admin (Admin A) now fails
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () ->
                organizationService.updateMemberRole(ctx.orgCode, ctx.adminA.getId(), demoteRequest, ctx.adminA));
    }

    @Test
    @DisplayName("G. Concurrent Demotions by Same Admin Caller: One succeeds, competing thread fails with IllegalStateException")
    void testConcurrentDemotions_SameAdminCaller_ThrowsIllegalStateException() throws Exception {
        TestOrgContext ctx = createTestOrganizationWithTwoAdmins("SAME_CALLER_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<OrganizationMemberDetailResponseDto> result1 = new AtomicReference<>();
        AtomicReference<OrganizationMemberDetailResponseDto> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        UpdateMemberRoleRequestDto demoteRequest = UpdateMemberRoleRequestDto.builder()
                .role(Role.ROLE_USER)
                .build();

        // Thread 1: Admin A demotes Admin B
        executor.submit(() -> {
            try {
                startLatch.await();
                result1.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminB.getId(), demoteRequest, ctx.adminA));
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Admin A self-demotes Admin A
        executor.submit(() -> {
            try {
                startLatch.await();
                result2.set(organizationService.updateMemberRole(ctx.orgCode, ctx.adminA.getId(), demoteRequest, ctx.adminA));
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean thread1Succeeded = (result1.get() != null && error1.get() == null);
        boolean thread2Succeeded = (result2.get() != null && error2.get() == null);
        assertThat(thread1Succeeded ^ thread2Succeeded).isTrue();

        Throwable failedError = thread1Succeeded ? error2.get() : error1.get();
        assertThat(failedError).isNotNull();
        assertThat(failedError).isInstanceOfAny(IllegalStateException.class, org.springframework.security.access.AccessDeniedException.class);

        // Database row verification: exactly 1 active admin remains
        long remainingAdmins = userOrganizationRepository.countByOrganization_OrgCodeAndStatusAndRoleIn(
                ctx.orgCode, MembershipStatus.ACTIVE, List.of(Role.ROLE_ORG_ADMIN, Role.ROLE_SUPER_ADMIN));
        assertThat(remainingAdmins).isEqualTo(1L);
    }
}
