package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.audit.entities.PermissionAuditLogEntity;
import com.subject77.docshare.modules.audit.repository.PermissionAuditLogRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.notification.entities.NotificationType;
import com.subject77.docshare.modules.notification.repository.NotificationRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.AccessRequestCreateDto;
import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestEntity;
import com.subject77.docshare.modules.permission.entities.AccessRequestStatus;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.repository.AccessRequestRepository;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
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
import java.util.ArrayList;
import java.util.Collections;
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
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.datasource.hikari.maximum-pool-size=25"
})
public class AccessRequestConcurrencyIntegrationTest {

    @Autowired
    private AccessRequestService accessRequestService;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private WorkspacePolicyRepository workspacePolicyRepository;

    @Autowired
    private PermissionAuditLogRepository permissionAuditLogRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.subject77.docshare.modules.permission.events.PermissionEventPublisher permissionEventPublisher;

    private void enablePersonalAccessRequests(UserEntity user) {
        workspacePolicyRepository.save(WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.PERSONAL)
                .user(user)
                .externalSharingMode(ExternalSharingMode.ON)
                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                .allowAccessRequests(true)
                .allowExternalAccessRequests(true)
                .requireRequestReason(false)
                .build());
    }

    private void enableOrgAccessRequests(OrganizationEntity org) {
        workspacePolicyRepository.save(WorkspacePolicyEntity.builder()
                .workspaceType(WorkspaceType.ORGANIZATION)
                .organization(org)
                .externalSharingMode(ExternalSharingMode.ON)
                .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                .allowAccessRequests(true)
                .allowExternalAccessRequests(true)
                .requireRequestReason(false)
                .build());
    }

    // =========================================================================
    // SCENARIO A: Concurrent duplicate creation race (10 parallel submissions)
    // =========================================================================
    @Test
    @DisplayName("Scenario A: 10 concurrent requests by same requester for same resource - exactly 1 succeeds, 9 fail")
    void testScenarioA_DuplicateCreationRace() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity owner = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("owner_a_" + suffix + "@concurrency.com")
                .firstName("Owner")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        enablePersonalAccessRequests(owner);

        UserEntity requester = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("requester_a_" + suffix + "@concurrency.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        DocumentEntity document = documentRepository.save(DocumentEntity.builder()
                .fileName("DocA_" + suffix + ".pdf")
                .fileHash("hash_a_" + suffix)
                .contentType("application/pdf")
                .s3Bucket("docshare-bucket")
                .objectKey("docs/a/" + suffix + "/file.pdf")
                .fileSize(1024L)
                .uploadedBy(owner)
                .status(DocumentStatus.ACTIVE)
                .build());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<AccessRequestResponseDto> successList = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errorList = Collections.synchronizedList(new ArrayList<>());

        AccessRequestCreateDto createDto = AccessRequestCreateDto.builder()
                .documentId(document.getId())
                .requestedPermissionLevel(PermissionLevel.VIEW)
                .reason("Need view access for project")
                .build();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    AccessRequestResponseDto resp = accessRequestService.createAccessRequest(createDto, requester);
                    successList.add(resp);
                } catch (Throwable t) {
                    errorList.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        // Exactly 1 succeeds, exactly 9 fail
        assertThat(successList).as("Exactly 1 submission must succeed").hasSize(1);
        assertThat(errorList).as("Exactly 9 submissions must fail").hasSize(threadCount - 1);
        for (Throwable err : errorList) {
            assertThat(err)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already pending");
        }

        // Direct PostgreSQL verification: exactly 1 PENDING request in DB
        List<AccessRequestEntity> allRequests = accessRequestRepository.findAll().stream()
                .filter(r -> r.getRequester().getId().equals(requester.getId()) &&
                             r.getDocument() != null &&
                             r.getDocument().getId().equals(document.getId()))
                .toList();
        assertThat(allRequests).hasSize(1);
        assertThat(allRequests.get(0).getStatus()).isEqualTo(AccessRequestStatus.PENDING);
    }

    // =========================================================================
    // SCENARIO B: Concurrent approve vs deny
    // =========================================================================
    @Test
    @DisplayName("Scenario B: Concurrent approve vs deny - exactly one terminal transition wins")
    void testScenarioB_ApproveVsDenyRace() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity owner = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("owner_b_" + suffix + "@concurrency.com")
                .firstName("Owner")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        enablePersonalAccessRequests(owner);

        UserEntity requester = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("requester_b_" + suffix + "@concurrency.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        DocumentEntity document = documentRepository.save(DocumentEntity.builder()
                .fileName("DocB_" + suffix + ".pdf")
                .fileHash("hash_b_" + suffix)
                .contentType("application/pdf")
                .s3Bucket("docshare-bucket")
                .objectKey("docs/b/" + suffix + "/file.pdf")
                .fileSize(1024L)
                .uploadedBy(owner)
                .status(DocumentStatus.ACTIVE)
                .build());

        AccessRequestResponseDto created = accessRequestService.createAccessRequest(
                AccessRequestCreateDto.builder()
                        .documentId(document.getId())
                        .requestedPermissionLevel(PermissionLevel.VIEW)
                        .build(),
                requester
        );
        Long requestId = created.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<AccessRequestResponseDto> approveResult = new AtomicReference<>();
        AtomicReference<AccessRequestResponseDto> denyResult = new AtomicReference<>();
        AtomicReference<Throwable> approveError = new AtomicReference<>();
        AtomicReference<Throwable> denyError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                approveResult.set(accessRequestService.approveAccessRequest(requestId, null, owner));
            } catch (Throwable t) {
                approveError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                denyResult.set(accessRequestService.denyAccessRequest(requestId, null, owner));
            } catch (Throwable t) {
                denyError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean approveSuccess = (approveResult.get() != null && approveError.get() == null);
        boolean denySuccess = (denyResult.get() != null && denyError.get() == null);
        assertThat(approveSuccess ^ denySuccess)
                .as("Exactly one of approve or deny must succeed")
                .isTrue();

        Throwable loserError = approveSuccess ? denyError.get() : approveError.get();
        assertThat(loserError)
                .isNotNull()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not pending");

        AccessRequestEntity entityInDb = accessRequestRepository.findById(requestId).orElseThrow();
        List<PermissionEntity> perms = permissionRepository.findByDocument_IdAndRevokedFalse(document.getId());

        if (approveSuccess) {
            assertThat(entityInDb.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
            assertThat(perms).hasSize(1);
            assertThat(perms.get(0).getTargetUserEmail()).isEqualTo(requester.getEmail().toLowerCase());

            String approveNotifKey = NotificationType.ACCESS_REQUEST_APPROVED.name() + ":" + requestId + ":" + requester.getId();
            assertThat(notificationRepository.existsByIdempotencyKey(approveNotifKey)).isTrue();
        } else {
            assertThat(entityInDb.getStatus()).isEqualTo(AccessRequestStatus.DENIED);
            assertThat(perms).isEmpty();

            String denyNotifKey = NotificationType.ACCESS_REQUEST_DENIED.name() + ":" + requestId + ":" + requester.getId();
            assertThat(notificationRepository.existsByIdempotencyKey(denyNotifKey)).isTrue();
        }
    }

    // =========================================================================
    // SCENARIO C: Concurrent approve vs cancel
    // =========================================================================
    @Test
    @DisplayName("Scenario C: Concurrent approve vs cancel - exactly one terminal transition wins, no leaked permission if cancel wins")
    void testScenarioC_ApproveVsCancelRace() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        UserEntity owner = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("owner_c_" + suffix + "@concurrency.com")
                .firstName("Owner")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        enablePersonalAccessRequests(owner);

        UserEntity requester = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("requester_c_" + suffix + "@concurrency.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        DocumentEntity document = documentRepository.save(DocumentEntity.builder()
                .fileName("DocC_" + suffix + ".pdf")
                .fileHash("hash_c_" + suffix)
                .contentType("application/pdf")
                .s3Bucket("docshare-bucket")
                .objectKey("docs/c/" + suffix + "/file.pdf")
                .fileSize(1024L)
                .uploadedBy(owner)
                .status(DocumentStatus.ACTIVE)
                .build());

        AccessRequestResponseDto created = accessRequestService.createAccessRequest(
                AccessRequestCreateDto.builder()
                        .documentId(document.getId())
                        .requestedPermissionLevel(PermissionLevel.VIEW)
                        .build(),
                requester
        );
        Long requestId = created.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<AccessRequestResponseDto> approveResult = new AtomicReference<>();
        AtomicReference<AccessRequestResponseDto> cancelResult = new AtomicReference<>();
        AtomicReference<Throwable> approveError = new AtomicReference<>();
        AtomicReference<Throwable> cancelError = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                approveResult.set(accessRequestService.approveAccessRequest(requestId, null, owner));
            } catch (Throwable t) {
                approveError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                cancelResult.set(accessRequestService.cancelAccessRequest(requestId, requester));
            } catch (Throwable t) {
                cancelError.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        boolean approveSuccess = (approveResult.get() != null && approveError.get() == null);
        boolean cancelSuccess = (cancelResult.get() != null && cancelError.get() == null);
        assertThat(approveSuccess ^ cancelSuccess)
                .as("Exactly one of approve or cancel must succeed")
                .isTrue();

        Throwable loserError = approveSuccess ? cancelError.get() : approveError.get();
        assertThat(loserError)
                .isNotNull()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not pending");

        AccessRequestEntity entityInDb = accessRequestRepository.findById(requestId).orElseThrow();
        List<PermissionEntity> perms = permissionRepository.findByDocument_IdAndRevokedFalse(document.getId());

        if (cancelSuccess) {
            assertThat(entityInDb.getStatus()).isEqualTo(AccessRequestStatus.CANCELLED);
            assertThat(perms)
                    .as("No permission must exist if cancel wins")
                    .isEmpty();

            String cancelNotifKey = NotificationType.ACCESS_REQUEST_CANCELLED.name() + ":" + requestId + ":" + owner.getId();
            assertThat(notificationRepository.existsByIdempotencyKey(cancelNotifKey)).isTrue();
        } else {
            assertThat(entityInDb.getStatus()).isEqualTo(AccessRequestStatus.APPROVED);
            assertThat(perms).hasSize(1);
        }
    }

    // =========================================================================
    // SCENARIO D: Duplicate approve race (2 reviewers simultaneously)
    // =========================================================================
    @Test
    @DisplayName("Scenario D: Duplicate approve race - two reviewers approve simultaneously, exactly one succeeds, 1 permission, 1 audit, 1 notification")
    void testScenarioD_DuplicateApproveRace() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgCode = ("ORG_APP_" + suffix).toUpperCase();

        UserEntity adminA = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_a_" + suffix + "@concurrency.com")
                .firstName("AdminA")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        UserEntity adminB = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_b_" + suffix + "@concurrency.com")
                .firstName("AdminB")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        OrganizationEntity org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCode)
                .name("Approve Org " + suffix)
                .createdBy(adminA)
                .build());

        enableOrgAccessRequests(org);

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(adminA)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(adminB)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        UserEntity requester = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("requester_d_" + suffix + "@concurrency.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(requester)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        DocumentEntity document = documentRepository.save(DocumentEntity.builder()
                .fileName("OrgDocD_" + suffix + ".pdf")
                .fileHash("hash_org_d_" + suffix)
                .contentType("application/pdf")
                .s3Bucket("docshare-bucket")
                .objectKey("docs/org/d/" + suffix + "/file.pdf")
                .fileSize(1024L)
                .ownerOrg(org)
                .uploadedBy(adminA)
                .status(DocumentStatus.ACTIVE)
                .build());

        AccessRequestResponseDto created = accessRequestService.createAccessRequest(
                AccessRequestCreateDto.builder()
                        .documentId(document.getId())
                        .requestedPermissionLevel(PermissionLevel.VIEW)
                        .build(),
                requester
        );
        Long requestId = created.getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<AccessRequestResponseDto> resultA = new AtomicReference<>();
        AtomicReference<AccessRequestResponseDto> resultB = new AtomicReference<>();
        AtomicReference<Throwable> errorA = new AtomicReference<>();
        AtomicReference<Throwable> errorB = new AtomicReference<>();

        executor.submit(() -> {
            try {
                startLatch.await();
                resultA.set(accessRequestService.approveAccessRequest(requestId, null, adminA));
            } catch (Throwable t) {
                errorA.set(t);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                resultB.set(accessRequestService.approveAccessRequest(requestId, null, adminB));
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

        boolean successA = (resultA.get() != null && errorA.get() == null);
        boolean successB = (resultB.get() != null && errorB.get() == null);
        assertThat(successA ^ successB)
                .as("Exactly one admin approval must succeed")
                .isTrue();

        Throwable failedError = successA ? errorB.get() : errorA.get();
        assertThat(failedError)
                .isNotNull()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not pending");

        // Exactly 1 permission in DB
        List<PermissionEntity> perms = permissionRepository.findByDocument_IdAndRevokedFalse(document.getId());
        assertThat(perms).hasSize(1);
        assertThat(perms.get(0).getTargetUserEmail()).isEqualTo(requester.getEmail().toLowerCase());

        // Exactly 1 approval audit event
        List<PermissionAuditLogEntity> audits = permissionAuditLogRepository.findByOrgCodeOrderByTimestampDesc(orgCode);
        long approveAudits = audits.stream()
                .filter(a -> "ACCESS_REQUEST_APPROVED".equals(a.getAction()))
                .count();
        assertThat(approveAudits).isEqualTo(1L);

        // Exactly 1 approval notification
        String approveNotifKey = NotificationType.ACCESS_REQUEST_APPROVED.name() + ":" + requestId + ":" + requester.getId();
        boolean notifExists = notificationRepository.existsByIdempotencyKey(approveNotifKey);
        assertThat(notifExists).isTrue();
    }

    // =========================================================================
    // SCENARIO E: Request vs member removal/suspension before approval
    // =========================================================================
    @Test
    @DisplayName("Scenario E: Requester suspended before approval - approval fails during revalidation, no permission granted")
    void testScenarioE_RequestVsMemberRemoval() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String orgCode = ("ORG_SUSP_" + suffix).toUpperCase();

        UserEntity admin = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("admin_e_" + suffix + "@concurrency.com")
                .firstName("Admin")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        OrganizationEntity org = organizationRepository.save(OrganizationEntity.builder()
                .orgCode(orgCode)
                .name("Suspended Org " + suffix)
                .createdBy(admin)
                .build());

        enableOrgAccessRequests(org);

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(admin)
                .organization(org)
                .role(Role.ROLE_ORG_ADMIN)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        UserEntity requester = userRepository.save(UserEntity.builder()
                .id(UUID.randomUUID().toString())
                .email("requester_e_" + suffix + "@concurrency.com")
                .firstName("Requester")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        UserOrganizationEntity requesterMembership = userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(requester)
                .organization(org)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build());

        DocumentEntity document = documentRepository.save(DocumentEntity.builder()
                .fileName("OrgDocE_" + suffix + ".pdf")
                .fileHash("hash_org_susp_" + suffix)
                .contentType("application/pdf")
                .s3Bucket("docshare-bucket")
                .objectKey("docs/org_susp/" + suffix + "/file.pdf")
                .fileSize(1024L)
                .ownerOrg(org)
                .uploadedBy(admin)
                .status(DocumentStatus.ACTIVE)
                .build());

        // 1. Requester creates access request while ACTIVE
        AccessRequestResponseDto created = accessRequestService.createAccessRequest(
                AccessRequestCreateDto.builder()
                        .documentId(document.getId())
                        .requestedPermissionLevel(PermissionLevel.VIEW)
                        .build(),
                requester
        );
        Long requestId = created.getId();

        // 2. Requester membership is SUSPENDED before approval
        requesterMembership.setStatus(MembershipStatus.SUSPENDED);
        userOrganizationRepository.save(requesterMembership);

        // 3. Admin attempts to approve
        assertThatThrownBy(() -> accessRequestService.approveAccessRequest(requestId, null, admin))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Requester organization membership is not active");

        // 4. Invariant: request remains PENDING and no permission is granted
        AccessRequestEntity entity = accessRequestRepository.findById(requestId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(AccessRequestStatus.PENDING);

        List<PermissionEntity> perms = permissionRepository.findByDocument_IdAndRevokedFalse(document.getId());
        assertThat(perms).isEmpty();
    }
}
