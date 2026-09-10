package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.repository.CollaborationRepository;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.DocumentStatus;
import com.subject77.docshare.modules.document.repository.DocumentRepository;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.GroupRepository;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.SharedByMeResponseDto;
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
@Transactional
public class SharedByMeQueryBoundIntegrationTest {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserOrganizationRepository userOrganizationRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private CollaborationRepository collaborationRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UserEntity grantorAlice;
    private UserEntity grantorEve;
    private UserEntity recipientBob;
    private UserEntity recipientCarol;
    private OrganizationEntity acmeOrg;
    private GroupEntity devGroup;
    private CollaborationEntity jointCollab;

    private final List<Long> activeFolderPermIds = new ArrayList<>();
    private final List<Long> activeDocPermIds = new ArrayList<>();
    private final List<Long> evePermIds = new ArrayList<>();

    private Long revokedPermId;
    private Long suspendedPermId;
    private Long expiredPermId;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // 1. Users
        grantorAlice = userRepository.save(UserEntity.builder()
                .id("alice-" + uid)
                .email("alice_" + uid + "@example.com")
                .firstName("Alice")
                .lastName("Grantor")
                .role(Role.ROLE_USER)
                .build());

        grantorEve = userRepository.save(UserEntity.builder()
                .id("eve-" + uid)
                .email("eve_" + uid + "@example.com")
                .firstName("Eve")
                .lastName("OtherGrantor")
                .role(Role.ROLE_USER)
                .build());

        recipientBob = userRepository.save(UserEntity.builder()
                .id("bob-" + uid)
                .email("bob_" + uid + "@example.com")
                .firstName("Bob")
                .lastName("Recipient")
                .role(Role.ROLE_USER)
                .build());

        recipientCarol = userRepository.save(UserEntity.builder()
                .id("carol-" + uid)
                .email("carol_" + uid + "@example.com")
                .firstName("Carol")
                .lastName("Member")
                .role(Role.ROLE_USER)
                .build());

        // 2. Org and Group
        acmeOrg = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ACME_" + uid.toUpperCase())
                .name("Acme Corporation")
                .createdBy(grantorAlice)
                .build());

        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(recipientCarol)
                .organization(acmeOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        devGroup = groupRepository.save(GroupEntity.builder()
                .name("Dev Team " + uid)
                .organization(acmeOrg)
                .createdBy(grantorAlice)
                .build());

        // 3. Collaboration
        FolderEntity collabFolder = folderRepository.save(FolderEntity.builder()
                .name("CollabRoot_" + uid)
                .subspaceType(SubspaceType.COLLABORATION)
                .createdBy(grantorAlice)
                .build());

        jointCollab = collaborationRepository.save(CollaborationEntity.builder()
                .name("Joint Project " + uid)
                .ownerOrganization(acmeOrg)
                .partnerOrganization(acmeOrg)
                .rootFolder(collabFolder)
                .createdBy(grantorAlice)
                .status(com.subject77.docshare.modules.collaboration.entities.CollaborationStatus.ACTIVE)
                .startsAt(Instant.now())
                .build());

        // 4. Create 10 Folders granted by Alice with mixed recipient types
        for (int i = 0; i < 10; i++) {
            FolderEntity folder = folderRepository.save(FolderEntity.builder()
                    .name("Folder_" + i + "_" + uid)
                    .subspaceType(SubspaceType.PERSONAL)
                    .createdBy(grantorAlice)
                    .build());

            PermissionEntity.PermissionEntityBuilder builder = PermissionEntity.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .folder(folder)
                    .grantedBy(grantorAlice)
                    .createdAt(Instant.now().minus(i + 1, ChronoUnit.HOURS));

            if (i < 3) {
                // USER_PERSONAL
                builder.recipientType(RecipientType.USER_PERSONAL)
                        .targetUserEmail(recipientBob.getEmail());
            } else if (i < 5) {
                // USER_ORGANIZATION_MEMBER
                builder.recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                        .targetUserEmail(recipientCarol.getEmail())
                        .targetOrgCode(acmeOrg.getOrgCode());
            } else if (i < 7) {
                // GROUP
                builder.recipientType(RecipientType.GROUP)
                        .targetGroup(devGroup)
                        .targetOrgCode(acmeOrg.getOrgCode());
            } else if (i < 9) {
                // ORGANIZATION
                builder.recipientType(RecipientType.ORGANIZATION)
                        .targetOrgCode(acmeOrg.getOrgCode());
            } else {
                // COLLABORATION
                builder.collaboration(jointCollab);
            }

            PermissionEntity perm = permissionRepository.save(builder.build());
            activeFolderPermIds.add(perm.getId());
        }

        // 5. Create 10 Documents granted by Alice with mixed recipient types
        for (int i = 0; i < 10; i++) {
            DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                    .fileName("Doc_" + i + "_" + uid + ".pdf")
                    .fileHash("hash_doc_" + i + "_" + uid)
                    .contentType("application/pdf")
                    .s3Bucket("test-bucket")
                    .objectKey("obj_doc_" + i + "_" + uid)
                    .fileSize(2048L * (i + 1))
                    .status(DocumentStatus.ACTIVE)
                    .uploadedBy(grantorAlice)
                    .build());

            PermissionEntity.PermissionEntityBuilder builder = PermissionEntity.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .document(doc)
                    .grantedBy(grantorAlice)
                    .createdAt(Instant.now().minus(i + 1, ChronoUnit.HOURS));

            if (i % 2 == 0) {
                builder.recipientType(RecipientType.USER_PERSONAL)
                        .targetUserEmail(recipientBob.getEmail());
            } else {
                builder.recipientType(RecipientType.GROUP)
                        .targetGroup(devGroup)
                        .targetOrgCode(acmeOrg.getOrgCode());
            }

            PermissionEntity perm = permissionRepository.save(builder.build());
            activeDocPermIds.add(perm.getId());
        }

        // 6. Inactive permissions granted by Alice
        FolderEntity dummyFolder = folderRepository.save(FolderEntity.builder()
                .name("DummyFolder_" + uid)
                .createdBy(grantorAlice)
                .build());

        // Revoked
        PermissionEntity revokedPerm = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(recipientBob.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorAlice)
                .revoked(true)
                .revokedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());
        revokedPermId = revokedPerm.getId();

        // Suspended
        PermissionEntity suspendedPerm = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(recipientBob.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorAlice)
                .suspended(true)
                .build());
        suspendedPermId = suspendedPerm.getId();

        // Expired
        PermissionEntity expiredPerm = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(recipientBob.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorAlice)
                .expiresAt(Instant.now().minus(3, ChronoUnit.HOURS))
                .build());
        expiredPermId = expiredPerm.getId();

        // Share link tokens (MUST always be excluded, even when includeInactive = true)
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(recipientBob.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorAlice)
                .shareLinkToken("share-token-" + uid)
                .build());

        // 7. Grants by Eve (Grantor isolation test)
        for (int i = 0; i < 5; i++) {
            FolderEntity eveFolder = folderRepository.save(FolderEntity.builder()
                    .name("EveFolder_" + i + "_" + uid)
                    .createdBy(grantorEve)
                    .build());
            PermissionEntity perm = permissionRepository.save(PermissionEntity.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail(recipientBob.getEmail())
                    .folder(eveFolder)
                    .grantedBy(grantorEve)
                    .build());
            evePermIds.add(perm.getId());
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Verify bounded query behavior, recipient metadata, grantor isolation, and includeInactive semantics")
    void testSharedByMe_boundedQueriesAndSemantics() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        long startQueries = stats.getPrepareStatementCount();

        // Call with includeInactive = false
        SharedByMeResponseDto activeResponse = permissionService.getSharedByMe(grantorAlice, false);

        long endQueries = stats.getPrepareStatementCount();
        long queriesExecuted = endQueries - startQueries;

        // 1. Verify bounded bulk query behavior (O(1) query categories, <= 10)
        assertThat(queriesExecuted)
                .as("Query count must be bounded and not cascade per row")
                .isLessThanOrEqualTo(10);

        // 2. Active count: 10 folders, 10 documents
        assertThat(activeResponse.getSharedFolders()).hasSize(10);
        assertThat(activeResponse.getSharedDocuments()).hasSize(10);

        // 3. Grantor isolation: NO permissions granted by Eve should appear
        List<Long> allReturnedIds = new ArrayList<>();
        activeResponse.getSharedFolders().forEach(f -> allReturnedIds.add(f.getPermissionId()));
        activeResponse.getSharedDocuments().forEach(d -> allReturnedIds.add(d.getPermissionId()));
        assertThat(allReturnedIds).doesNotContainAnyElementsOf(evePermIds);

        // 4. Inactive items excluded when includeInactive = false
        assertThat(allReturnedIds).doesNotContain(revokedPermId, suspendedPermId, expiredPermId);

        // 5. Verify recipient metadata distinctions
        // Check GROUP recipient
        SharedItemDto groupDto = activeResponse.getSharedFolders().stream()
                .filter(f -> "GROUP".equals(f.getRecipientType()))
                .findFirst().orElseThrow();
        assertThat(groupDto.getTargetGroupId()).isEqualTo(devGroup.getId());
        assertThat(groupDto.getTargetGroupName()).isEqualTo(devGroup.getName());
        assertThat(groupDto.getTargetOrgCode()).isEqualTo(acmeOrg.getOrgCode());
        assertThat(groupDto.getTargetOrgName()).isEqualTo(acmeOrg.getName());

        // Check COLLABORATION recipient
        SharedItemDto collabDto = activeResponse.getSharedFolders().stream()
                .filter(f -> "COLLABORATION".equals(f.getRecipientType()))
                .findFirst().orElseThrow();
        assertThat(collabDto.getCollaborationId()).isEqualTo(jointCollab.getId());
        assertThat(collabDto.getCollaborationName()).isEqualTo(jointCollab.getName());

        // Check ORGANIZATION recipient
        SharedItemDto orgDto = activeResponse.getSharedFolders().stream()
                .filter(f -> "ORGANIZATION".equals(f.getRecipientType()))
                .findFirst().orElseThrow();
        assertThat(orgDto.getTargetOrgCode()).isEqualTo(acmeOrg.getOrgCode());
        assertThat(orgDto.getTargetOrgName()).isEqualTo(acmeOrg.getName());

        // 6. Test includeInactive = true
        SharedByMeResponseDto allResponse = permissionService.getSharedByMe(grantorAlice, true);
        // 10 active folders + 3 inactive folders = 13 folders
        assertThat(allResponse.getSharedFolders()).hasSize(13);
        assertThat(allResponse.getSharedDocuments()).hasSize(10);

        // Verify inactive statuses
        SharedItemDto revokedDto = allResponse.getSharedFolders().stream()
                .filter(f -> f.getPermissionId().equals(revokedPermId))
                .findFirst().orElseThrow();
        assertThat(revokedDto.getStatus()).isEqualTo("REVOKED");
        assertThat(revokedDto.getRevoked()).isTrue();

        SharedItemDto suspendedDto = allResponse.getSharedFolders().stream()
                .filter(f -> f.getPermissionId().equals(suspendedPermId))
                .findFirst().orElseThrow();
        assertThat(suspendedDto.getStatus()).isEqualTo("SUSPENDED");
        assertThat(suspendedDto.getSuspended()).isTrue();

        SharedItemDto expiredDto = allResponse.getSharedFolders().stream()
                .filter(f -> f.getPermissionId().equals(expiredPermId))
                .findFirst().orElseThrow();
        assertThat(expiredDto.getStatus()).isEqualTo("EXPIRED");
    }
}
