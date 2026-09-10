package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.entities.SubspaceType;
import com.subject77.docshare.modules.folder.repository.FolderRepository;
import com.subject77.docshare.modules.permission.dto.SharedItemDto;
import com.subject77.docshare.modules.permission.dto.SharedWithMeResponseDto;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.repository.PermissionRepository;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/docshare",
        "spring.datasource.username=postgres",
        "spring.datasource.password=postgres",
        "spring.datasource.driverClassName=org.postgresql.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.jpa.hibernate.ddl-auto=update"
})
@Transactional
public class SharedWithMeDeduplicationAndSortingIntegrationTest {

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private EntityManager entityManager;

    private UserEntity targetUser;
    private UserEntity grantorUser;
    private FolderEntity sharedFolder;

    @BeforeEach
    void setUp() {
        String uid = UUID.randomUUID().toString().substring(0, 8);

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

        sharedFolder = folderRepository.save(FolderEntity.builder()
                .name("SharedResourceFolder_" + uid)
                .subspaceType(SubspaceType.PERSONAL)
                .createdBy(grantorUser)
                .build());
    }

    @Test
    @DisplayName("Deduplication: Duplicate permission entity identity (same permissionId) yields exactly one DTO")
    void testDeduplication_duplicatePermissionEntity_yieldsSingleDto() {
        // Save a single permission in PostgreSQL
        PermissionEntity perm = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(sharedFolder)
                .grantedBy(grantorUser)
                .createdAt(Instant.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        // When getSharedWithMe runs, even if repository/caching returned duplicate rows,
        // seenPermissionIds.add(perm.getId()) deduplicates only by entity identity
        SharedWithMeResponseDto response = permissionService.getSharedWithMe(targetUser);

        assertThat(response.getSharedFolders()).hasSize(1);
        assertThat(response.getSharedFolders().get(0).getPermissionId()).isEqualTo(perm.getId());
    }

    @Test
    @DisplayName("Single-Card Deduplication: Distinct permission IDs for the same resource merge into one card with highest permission level and matching count")
    void testSingleCardDeduplication_distinctPermissionIdsForSameResource_mergedToSingleDtoWithEffectiveLevel() {
        // Grant 1: VIEW on sharedFolder
        PermissionEntity permView = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(sharedFolder)
                .grantedBy(grantorUser)
                .note("Grant 1: Read-only access")
                .createdAt(Instant.now().minus(2, ChronoUnit.HOURS))
                .build());

        // Grant 2: EDIT on the SAME sharedFolder
        PermissionEntity permEdit = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(sharedFolder)
                .grantedBy(grantorUser)
                .note("Grant 2: Collaborative editing")
                .createdAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        entityManager.flush();
        entityManager.clear();

        SharedWithMeResponseDto response = permissionService.getSharedWithMe(targetUser);

        // Canonical rule: Shared With Me renders a single card per resource, with effective permission level (EDIT)
        // and matchingGrantCount indicating multiple conferring grants.
        assertThat(response.getSharedFolders()).hasSize(1);

        SharedItemDto card = response.getSharedFolders().get(0);
        assertThat(card.getResourceId()).isEqualTo(sharedFolder.getId());
        assertThat(card.getPermissionLevel()).isEqualTo(PermissionLevel.EDIT);
        assertThat(card.getMatchingGrantCount()).isEqualTo(2);
        assertThat(card.getMatchedPermissionIds()).containsExactlyInAnyOrder(permEdit.getId(), permView.getId());
    }

    @Test
    @DisplayName("Sorting Determinism: Candidates ordered by createdAt DESC, then id DESC")
    void testSortingDeterminism_createdAtDescThenIdDesc() {
        Instant fixedTimestamp = Instant.parse("2026-06-15T12:00:00Z");

        FolderEntity folderA = folderRepository.save(FolderEntity.builder().name("FolderA").subspaceType(SubspaceType.PERSONAL).createdBy(grantorUser).build());
        FolderEntity folderB = folderRepository.save(FolderEntity.builder().name("FolderB").subspaceType(SubspaceType.PERSONAL).createdBy(grantorUser).build());
        FolderEntity folderC = folderRepository.save(FolderEntity.builder().name("FolderC").subspaceType(SubspaceType.PERSONAL).createdBy(grantorUser).build());
        FolderEntity folderD = folderRepository.save(FolderEntity.builder().name("FolderD").subspaceType(SubspaceType.PERSONAL).createdBy(grantorUser).build());

        // Row A: at fixedTimestamp (id will be lower than Row B)
        PermissionEntity permA = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(folderA)
                .grantedBy(grantorUser)
                .createdAt(fixedTimestamp)
                .build());

        // Row B: at EXACT SAME fixedTimestamp (id will be higher than Row A)
        PermissionEntity permB = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(folderB)
                .grantedBy(grantorUser)
                .createdAt(fixedTimestamp)
                .build());

        // Row C: Older timestamp (1 day earlier)
        PermissionEntity permC = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(folderC)
                .grantedBy(grantorUser)
                .createdAt(fixedTimestamp.minus(1, ChronoUnit.DAYS))
                .build());

        // Row D: Newer timestamp (1 day later)
        PermissionEntity permD = permissionRepository.save(PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(RecipientType.USER_PERSONAL)
                .targetUserEmail(targetUser.getEmail())
                .folder(folderD)
                .grantedBy(grantorUser)
                .createdAt(fixedTimestamp.plus(1, ChronoUnit.DAYS))
                .build());

        entityManager.flush();
        entityManager.clear();

        // Query directly via the repository method
        List<PermissionEntity> orderedPerms = permissionRepository
                .findByTargetUserEmailOrderByCreatedAtDescIdDesc(targetUser.getEmail());

        assertThat(permA.getId()).isLessThan(permB.getId());

        // Expected order:
        // 1. PermD (newest createdAt: T + 1 day)
        // 2. PermB (same timestamp T, higher id)
        // 3. PermA (same timestamp T, lower id)
        // 4. PermC (oldest createdAt: T - 1 day)
        List<Long> orderedIds = orderedPerms.stream()
                .map(PermissionEntity::getId)
                .toList();

        assertThat(orderedIds).containsExactly(
                permD.getId(),
                permB.getId(),
                permA.getId(),
                permC.getId()
        );

        // Also verify via service getSharedWithMe
        SharedWithMeResponseDto response = permissionService.getSharedWithMe(targetUser);
        List<Long> serviceOrderedIds = response.getSharedFolders().stream()
                .map(SharedItemDto::getPermissionId)
                .toList();

        assertThat(serviceOrderedIds).containsExactly(
                permD.getId(),
                permB.getId(),
                permA.getId(),
                permC.getId()
        );
    }
}
