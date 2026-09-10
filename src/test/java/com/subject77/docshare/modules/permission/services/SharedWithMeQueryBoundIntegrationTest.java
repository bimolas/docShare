package com.subject77.docshare.modules.permission.services;

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
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
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
import com.subject77.docshare.modules.workspace.repository.ProjectWorkspaceRepository;
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
public class SharedWithMeQueryBoundIntegrationTest {

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
    private ProjectWorkspaceRepository projectWorkspaceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UserEntity targetUser;
    private UserEntity grantorUser;
    private OrganizationEntity internalOrg;
    private OrganizationEntity externalOrg;
    private GroupEntity group;

    private final List<Long> qualifyingFolderPermIds = new ArrayList<>();
    private final List<Long> qualifyingDocPermIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

        // 1. Users
        targetUser = userRepository.save(UserEntity.builder()
                .id("target-" + uid)
                .email("target_" + uid + "@example.com")
                .firstName("Target")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        grantorUser = userRepository.save(UserEntity.builder()
                .id("grantor-" + uid)
                .email("grantor_" + uid + "@example.com")
                .firstName("Grantor")
                .lastName("User")
                .role(Role.ROLE_USER)
                .build());

        // 2. Orgs
        internalOrg = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_INT_" + uid.toUpperCase())
                .name("Internal Organization")
                .createdBy(grantorUser)
                .build());

        externalOrg = organizationRepository.save(OrganizationEntity.builder()
                .orgCode("ORG_EXT_" + uid.toUpperCase())
                .name("External Organization")
                .createdBy(grantorUser)
                .build());

        // Target user is active member of internalOrg ONLY
        userOrganizationRepository.save(UserOrganizationEntity.builder()
                .user(targetUser)
                .organization(internalOrg)
                .role(Role.ROLE_USER)
                .status(MembershipStatus.ACTIVE)
                .build());

        // Group in internalOrg
        group = groupRepository.save(GroupEntity.builder()
                .name("Test Group " + uid)
                .organization(internalOrg)
                .createdBy(grantorUser)
                .build());

        // 3. Create 10 Folders (5 internal org, 5 personal)
        for (int i = 0; i < 10; i++) {
            boolean isOrg = (i < 5);
            FolderEntity folder = folderRepository.save(FolderEntity.builder()
                    .name("Folder_" + i + "_" + uid)
                    .subspaceType(isOrg ? SubspaceType.ORGANIZATION_GENERAL : SubspaceType.PERSONAL)
                    .ownerOrg(isOrg ? internalOrg : null)
                    .createdBy(grantorUser)
                    .build());

            PermissionEntity perm = permissionRepository.save(PermissionEntity.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.VIEW)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail(targetUser.getEmail())
                    .folder(folder)
                    .grantedBy(grantorUser)
                    .createdAt(Instant.now().minus(i + 1, ChronoUnit.HOURS))
                    .build());
            qualifyingFolderPermIds.add(perm.getId());
        }

        // 4. Create 10 Documents (5 external org, 5 personal)
        for (int i = 0; i < 10; i++) {
            boolean isExternalOrg = (i < 5);
            DocumentEntity doc = documentRepository.save(DocumentEntity.builder()
                    .fileName("Doc_" + i + "_" + uid + ".pdf")
                    .fileHash("hash_" + i + "_" + uid)
                    .contentType("application/pdf")
                    .s3Bucket("test-bucket")
                    .objectKey("obj_" + i + "_" + uid)
                    .fileSize(1024L * (i + 1))
                    .ownerOrg(isExternalOrg ? externalOrg : null)
                    .status(DocumentStatus.ACTIVE)
                    .uploadedBy(grantorUser)
                    .build());

            PermissionEntity perm = permissionRepository.save(PermissionEntity.builder()
                    .permissionType(PermissionType.ALLOW)
                    .permissionLevel(PermissionLevel.DOWNLOAD)
                    .recipientType(RecipientType.USER_PERSONAL)
                    .targetUserEmail(targetUser.getEmail())
                    .document(doc)
                    .grantedBy(grantorUser)
                    .createdAt(Instant.now().minus(i + 1, ChronoUnit.HOURS))
                    .build());
            qualifyingDocPermIds.add(perm.getId());
        }

        // 5. Non-qualifying grants to test exact security filtering
        FolderEntity dummyFolder = folderRepository.save(FolderEntity.builder()
                .name("Dummy_" + uid)
                .createdBy(grantorUser)
                .build());

        // A. USER_ORGANIZATION_MEMBER without active org membership -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_ORGANIZATION_MEMBER)
                .targetUserEmail(targetUser.getEmail())
                .targetOrgCode(externalOrg.getOrgCode())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .build());

        // B. GROUP grant -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.GROUP)
                .targetGroup(group)
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .build());

        // C. Organization-wide grant -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.ORGANIZATION)
                .targetOrgCode(internalOrg.getOrgCode())
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .build());

        // D. ShareLinkToken present -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .shareLinkToken("token-xyz-" + uid)
                .build());

        // E. Revoked -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .revoked(true)
                .build());

        // F. Suspended -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .suspended(true)
                .build());

        // G. Expired -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(grantorUser)
                .expiresAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build());

        // H. Self-granted -> EXCLUDED
        permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(dummyFolder)
                .grantedBy(targetUser)
                .build());

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Verify bounded bulk query behavior, exact security filtering, and metadata enrichment")
    void testSharedWithMe_boundedQueriesAndExactFiltering() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        long startQueries = stats.getPrepareStatementCount();

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(targetUser);

        long endQueries = stats.getPrepareStatementCount();
        long queriesExecuted = endQueries - startQueries;

        // 1. Verify bounded query behavior (O(1) query categories, strictly not N+1)
        // For 20 returned items + 8 filtered items, an N+1 approach would execute 40-80+ queries.
        // With staged bulk loading, queriesExecuted must be <= 10.
        assertThat(queriesExecuted)
                .as("Query count must be bounded and not cascade per row")
                .isLessThanOrEqualTo(10);

        // 2. Verify exact count: 10 folders, 10 documents
        assertThat(response.getSharedFolders()).hasSize(10);
        assertThat(response.getSharedDocuments()).hasSize(10);

        // 3. Verify exact permission IDs match qualifying grants
        List<Long> actualFolderPermIds = response.getSharedFolders().stream()
                .map(SharedItemDto::getPermissionId)
                .toList();
        List<Long> actualDocPermIds = response.getSharedDocuments().stream()
                .map(SharedItemDto::getPermissionId)
                .toList();

        assertThat(actualFolderPermIds).containsExactlyInAnyOrderElementsOf(qualifyingFolderPermIds);
        assertThat(actualDocPermIds).containsExactlyInAnyOrderElementsOf(qualifyingDocPermIds);

        // 4. Verify metadata enrichment
        // Internal org folder: external = false, sourceType = ORGANIZATION
        SharedItemDto orgFolder = response.getSharedFolders().stream()
                .filter(f -> "ORGANIZATION".equals(f.getSourceType()))
                .findFirst().orElseThrow();
        assertThat(orgFolder.getOwnerOrgCode()).isEqualTo(internalOrg.getOrgCode());
        assertThat(orgFolder.getOwnerOrgName()).isEqualTo(internalOrg.getName());
        assertThat(orgFolder.getExternal()).isFalse();
        assertThat(orgFolder.getGrantedByName()).isEqualTo("Grantor User");

        // Personal folder: external = false, sourceType = PERSONAL, ownerOrgCode = null
        SharedItemDto personalFolder = response.getSharedFolders().stream()
                .filter(f -> "PERSONAL".equals(f.getSourceType()))
                .findFirst().orElseThrow();
        assertThat(personalFolder.getOwnerOrgCode()).isNull();
        assertThat(personalFolder.getExternal()).isFalse();

        // External org document: external = true (targetUser is NOT a member of externalOrg)
        SharedItemDto extOrgDoc = response.getSharedDocuments().stream()
                .filter(d -> "ORGANIZATION".equals(d.getSourceType()))
                .findFirst().orElseThrow();
        assertThat(extOrgDoc.getOwnerOrgCode()).isEqualTo(externalOrg.getOrgCode());
        assertThat(extOrgDoc.getOwnerOrgName()).isEqualTo(externalOrg.getName());
        assertThat(extOrgDoc.getExternal()).isTrue();
    }
}
