package com.subject77.docshare.modules.organization.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.organization.entities.*;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
public class GroupManagerConcurrencyIntegrationTest {

    @Autowired
    private GroupService groupService;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserGroupRepository userGroupRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private UserRepository userRepository;

    private static class TestGroupContext {
        String orgCode;
        OrganizationEntity org;
        GroupEntity group;
        UserEntity adminUser;
        UserEntity managerA;
        UserEntity memberB;
        UserEntity memberC;
        UserGroupEntity ugA;
        UserGroupEntity ugB;
        UserGroupEntity ugC;
    }

    private TestGroupContext createTestGroupContext(String prefix) {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        TestGroupContext ctx = new TestGroupContext();
        ctx.orgCode = (prefix + "_" + unique).toUpperCase();

        ctx.adminUser = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_" + unique + "@test.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.managerA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("manager_a_" + unique + "@test.com")
                .firstName("ManagerA")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.memberB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("member_b_" + unique + "@test.com")
                .firstName("MemberB")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.memberC = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("member_c_" + unique + "@test.com")
                .firstName("MemberC")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        ctx.org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(ctx.orgCode)
                .name("Group Concurrency Test Org " + unique)
                .createdBy(ctx.adminUser)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.adminUser)
                .organization(ctx.org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.managerA)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.memberB)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(ctx.memberC)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        ctx.group = groupRepository.save(GroupEntity.builder()
                .name("Team " + unique)
                .organization(ctx.org)
                .createdBy(ctx.adminUser)
                .status(GroupStatus.ACTIVE)
                .build());

        ctx.ugA = userGroupRepository.save(UserGroupEntity.builder()
                .user(ctx.managerA)
                .group(ctx.group)
                .role(GroupMemberRole.MANAGER)
                .joinedAt(Instant.now())
                .build());

        ctx.ugB = userGroupRepository.save(UserGroupEntity.builder()
                .user(ctx.memberB)
                .group(ctx.group)
                .role(GroupMemberRole.MEMBER)
                .joinedAt(Instant.now())
                .build());

        ctx.ugC = userGroupRepository.save(UserGroupEntity.builder()
                .user(ctx.memberC)
                .group(ctx.group)
                .role(GroupMemberRole.MEMBER)
                .joinedAt(Instant.now())
                .build());

        return ctx;
    }

    @Test
    @DisplayName("Scenario 1: Concurrent transfer A -> B and A -> C preserves single-manager integrity")
    void testConcurrentTransfer_MaintainsSingleManager() throws Exception {
        TestGroupContext ctx = createTestGroupContext("MGR_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        // Thread 1: transfer A -> B
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.adminUser);
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: transfer A -> C
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberC.getId(), ctx.adminUser);
            } catch (Throwable t) {
                error2.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Fire both threads concurrently
        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Database row verification: exactly 1 active Team Manager exists!
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);

        String activeManagerId = managers.get(0).getUser().getId();
        assertThat(activeManagerId).isIn(ctx.memberB.getId(), ctx.memberC.getId());

        // Old manager A is now MEMBER
        UserGroupEntity ugA = userGroupRepository.findByUser_IdAndGroup_Id(ctx.managerA.getId(), ctx.group.getId()).orElseThrow();
        assertThat(ugA.getRole()).isEqualTo(GroupMemberRole.MEMBER);

        // The other target who was not the final manager must be MEMBER
        String otherMemberId = activeManagerId.equals(ctx.memberB.getId()) ? ctx.memberC.getId() : ctx.memberB.getId();
        UserGroupEntity ugOther = userGroupRepository.findByUser_IdAndGroup_Id(otherMemberId, ctx.group.getId()).orElseThrow();
        assertThat(ugOther.getRole()).isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    @DisplayName("Scenario 2: Concurrent transfer A -> B and member removal of B safely serializes")
    void testConcurrentTransferAndMemberRemoval() throws Exception {
        TestGroupContext ctx = createTestGroupContext("MGR_REM_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> errorTransfer = new AtomicReference<>();
        AtomicReference<Throwable> errorRemove = new AtomicReference<>();

        // Thread 1: transfer A -> B
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.adminUser);
            } catch (Throwable t) {
                errorTransfer.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: remove B from group
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.removeMemberFromGroup(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.adminUser);
            } catch (Throwable t) {
                errorRemove.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Verify that the team is never corrupted:
        // Case 1: Transfer won -> B became MANAGER, then remove failed with IllegalArgumentException (cannot remove manager).
        // Case 2: Remove won -> B was deleted, then transfer failed with IllegalArgumentException (target is not a member).
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);

        if (errorTransfer.get() == null) {
            // Transfer succeeded: B is manager, remove must have failed
            assertThat(managers).hasSize(1);
            assertThat(managers.get(0).getUser().getId()).isEqualTo(ctx.memberB.getId());
            assertThat(errorRemove.get()).isInstanceOf(IllegalArgumentException.class);
            assertThat(errorRemove.get().getMessage()).contains("Cannot remove the Team Manager from the team");
        } else {
            // Remove succeeded: B was deleted, transfer must have failed
            assertThat(errorTransfer.get()).isInstanceOf(IllegalArgumentException.class);
            assertThat(errorTransfer.get().getMessage()).contains("is not a member of group");
            // B is not in group
            assertThat(userGroupRepository.findByUser_IdAndGroup_Id(ctx.memberB.getId(), ctx.group.getId())).isEmpty();
            // Old manager A remained manager
            assertThat(managers).hasSize(1);
            assertThat(managers.get(0).getUser().getId()).isEqualTo(ctx.managerA.getId());
        }
    }

    @Test
    @DisplayName("Scenario 3: Concurrent manager assignment to a group with zero managers")
    void testConcurrentManagerAssignment_WhenZeroManagersExist() throws Exception {
        TestGroupContext ctx = createTestGroupContext("ZERO_MGR_RACE");

        // Set current manager A to MEMBER so the group has 0 managers
        ctx.ugA.setRole(GroupMemberRole.MEMBER);
        userGroupRepository.save(ctx.ugA);

        assertThat(userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER)).isEmpty();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        // Thread 1: assign B as manager
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.adminUser);
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: assign C as manager
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberC.getId(), ctx.adminUser);
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

        // Exactly one manager exists at the end
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).getUser().getId()).isIn(ctx.memberB.getId(), ctx.memberC.getId());
    }

    @Test
    @DisplayName("Scenario 4: Suspended Team Manager cannot transfer manager or manage roster")
    void testSuspendedTeamManager_CannotTransferOrManage() {
        TestGroupContext ctx = createTestGroupContext("SUSP_MGR");

        // Suspend Manager A in organization
        UserOrganizationEntity uoA = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(ctx.managerA.getId(), ctx.orgCode).orElseThrow();
        uoA.setStatus(MembershipStatus.SUSPENDED);
        userOrganizationRepository.save(uoA);

        // Manager A attempts transferTeamManager -> AccessDeniedException
        assertThatThrownBy(() -> groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.managerA))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membership is not active in organization");

        // Manager A attempts removeMemberFromGroup -> AccessDeniedException
        assertThatThrownBy(() -> groupService.removeMemberFromGroup(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.managerA))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membership is not active in organization");

        // Manager A attempts addMemberToGroup -> AccessDeniedException
        assertThatThrownBy(() -> groupService.addMemberToGroup(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.managerA))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("membership is not active in organization");
    }

    @Test
    @DisplayName("Scenario 5: Sequential manager transfers maintain single manager throughout")
    void testSequentialTransfers_MaintainsSingleManager() {
        TestGroupContext ctx = createTestGroupContext("SEQ_TRANS");

        // A -> B
        groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.adminUser);
        List<UserGroupEntity> managers1 = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers1).hasSize(1);
        assertThat(managers1.get(0).getUser().getId()).isEqualTo(ctx.memberB.getId());

        // B -> C
        groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberC.getId(), ctx.adminUser);
        List<UserGroupEntity> managers2 = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers2).hasSize(1);
        assertThat(managers2.get(0).getUser().getId()).isEqualTo(ctx.memberC.getId());

        // C -> A
        groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.managerA.getId(), ctx.adminUser);
        List<UserGroupEntity> managers3 = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers3).hasSize(1);
        assertThat(managers3.get(0).getUser().getId()).isEqualTo(ctx.managerA.getId());
    }

    @Test
    @DisplayName("Scenario 6: Transfer to user who is not a member of the group is rejected")
    void testTransferToNonMember_Rejected() {
        TestGroupContext ctx = createTestGroupContext("NON_MEMBER");

        UserEntity nonMember = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("nonmember_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .firstName("Non")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(nonMember)
                .organization(ctx.org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        assertThatThrownBy(() -> groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), nonMember.getId(), ctx.adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not a member of group");

        // Manager A remains unchanged
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);
        assertThat(managers.get(0).getUser().getId()).isEqualTo(ctx.managerA.getId());
    }

    @Test
    @DisplayName("Scenario 7: Concurrent legacy role promotion (B vs C) preserves single-manager integrity")
    void testConcurrentLegacyPromotion_MaintainsSingleManager() throws Exception {
        TestGroupContext ctx = createTestGroupContext("LEG_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> error1 = new AtomicReference<>();
        AtomicReference<Throwable> error2 = new AtomicReference<>();

        // Thread 1: legacy promotion A -> B
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
            } catch (Throwable t) {
                error1.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: legacy promotion A -> C
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberC.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
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

        // Database row verification: exactly 1 active Team Manager exists
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);

        String activeManagerId = managers.get(0).getUser().getId();
        assertThat(activeManagerId).isIn(ctx.memberB.getId(), ctx.memberC.getId());

        // Old manager A is now MEMBER
        UserGroupEntity ugA = userGroupRepository.findByUser_IdAndGroup_Id(ctx.managerA.getId(), ctx.group.getId()).orElseThrow();
        assertThat(ugA.getRole()).isEqualTo(GroupMemberRole.MEMBER);

        // The target who did not end up as manager must be MEMBER
        String otherMemberId = activeManagerId.equals(ctx.memberB.getId()) ? ctx.memberC.getId() : ctx.memberB.getId();
        UserGroupEntity ugOther = userGroupRepository.findByUser_IdAndGroup_Id(otherMemberId, ctx.group.getId()).orElseThrow();
        assertThat(ugOther.getRole()).isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    @DisplayName("Scenario 8: Concurrent legacy promotion vs canonical transfer serializes safely")
    void testConcurrentLegacyPromotionVsCanonicalTransfer_MaintainsSingleManager() throws Exception {
        TestGroupContext ctx = createTestGroupContext("LEG_CAN_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> errorLegacy = new AtomicReference<>();
        AtomicReference<Throwable> errorCanonical = new AtomicReference<>();

        // Thread 1: legacy updateMemberRole to MANAGER (target B)
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
            } catch (Throwable t) {
                errorLegacy.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: canonical transferTeamManager (target C)
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.transferTeamManager(ctx.orgCode, ctx.group.getId(), ctx.memberC.getId(), ctx.adminUser);
            } catch (Throwable t) {
                errorCanonical.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Exactly one manager exists at all times
        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);
        assertThat(managers).hasSize(1);

        String activeManagerId = managers.get(0).getUser().getId();
        assertThat(activeManagerId).isIn(ctx.memberB.getId(), ctx.memberC.getId());

        // Old manager A is now MEMBER
        UserGroupEntity ugA = userGroupRepository.findByUser_IdAndGroup_Id(ctx.managerA.getId(), ctx.group.getId()).orElseThrow();
        assertThat(ugA.getRole()).isEqualTo(GroupMemberRole.MEMBER);
    }

    @Test
    @DisplayName("Scenario 9: Concurrent legacy promotion vs member removal of target")
    void testConcurrentLegacyPromotionVsMemberRemoval() throws Exception {
        TestGroupContext ctx = createTestGroupContext("LEG_REM_RACE");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Throwable> errorPromotion = new AtomicReference<>();
        AtomicReference<Throwable> errorRemove = new AtomicReference<>();

        // Thread 1: legacy promotion A -> B
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
            } catch (Throwable t) {
                errorPromotion.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: remove B from group
        executor.submit(() -> {
            try {
                startLatch.await();
                groupService.removeMemberFromGroup(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), ctx.adminUser);
            } catch (Throwable t) {
                errorRemove.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        List<UserGroupEntity> managers = userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER);

        if (errorPromotion.get() == null) {
            // Promotion won: B is manager, remove failed
            assertThat(managers).hasSize(1);
            assertThat(managers.get(0).getUser().getId()).isEqualTo(ctx.memberB.getId());
            assertThat(errorRemove.get()).isInstanceOf(IllegalArgumentException.class);
            assertThat(errorRemove.get().getMessage()).contains("Cannot remove the Team Manager from the team");
        } else {
            // Remove won: B was deleted, promotion failed (resource not found)
            assertThat(errorPromotion.get()).isInstanceOf(ResourceNotFoundException.class);
            assertThat(userGroupRepository.findByUser_IdAndGroup_Id(ctx.memberB.getId(), ctx.group.getId())).isEmpty();
            assertThat(managers).hasSize(1);
            assertThat(managers.get(0).getUser().getId()).isEqualTo(ctx.managerA.getId());
        }
    }

    @Test
    @DisplayName("Scenario 10: Repeated and same-role operations are idempotent and preserve single manager")
    void testRepeatedAndSameRoleOperations_IdempotentAndSafe() {
        TestGroupContext ctx = createTestGroupContext("IDEM_ROLES");

        // 1. MEMBER -> MEMBER is no-op
        groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), GroupMemberRole.MEMBER, ctx.adminUser);
        UserGroupEntity ugB = userGroupRepository.findByUser_IdAndGroup_Id(ctx.memberB.getId(), ctx.group.getId()).orElseThrow();
        assertThat(ugB.getRole()).isEqualTo(GroupMemberRole.MEMBER);

        // 2. MANAGER -> MANAGER is no-op
        groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.managerA.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
        UserGroupEntity ugA = userGroupRepository.findByUser_IdAndGroup_Id(ctx.managerA.getId(), ctx.group.getId()).orElseThrow();
        assertThat(ugA.getRole()).isEqualTo(GroupMemberRole.MANAGER);

        // Exactly one manager exists
        assertThat(userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER)).hasSize(1);

        // 3. MANAGER -> MEMBER is rejected
        assertThatThrownBy(() -> groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.managerA.getId(), GroupMemberRole.MEMBER, ctx.adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot demote Team Manager to Member directly");

        // Manager A still manager
        ugA = userGroupRepository.findByUser_IdAndGroup_Id(ctx.managerA.getId(), ctx.group.getId()).orElseThrow();
        assertThat(ugA.getRole()).isEqualTo(GroupMemberRole.MANAGER);
        assertThat(userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER)).hasSize(1);

        // 4. Multiple sequential promotions
        groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberB.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
        assertThat(userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER)).hasSize(1);

        groupService.updateMemberRole(ctx.orgCode, ctx.group.getId(), ctx.memberC.getId(), GroupMemberRole.MANAGER, ctx.adminUser);
        assertThat(userGroupRepository.findByGroup_IdAndRole(ctx.group.getId(), GroupMemberRole.MANAGER)).hasSize(1);
    }
}
