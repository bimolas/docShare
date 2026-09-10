package com.subject77.docshare.modules.permission.utils;

import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.user.entities.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionHashUtilsTest {

    @Test
    @DisplayName("Should generate deterministic SHA-256 hash for identical permission attributes")
    void calculateRecordHash_Deterministic() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();
        Instant validFrom = Instant.ofEpochSecond(1700000000);
        Instant expiresAt = Instant.ofEpochSecond(1800000000);

        PermissionEntity p1 = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .targetOrgCode("ACME")
                .documentFileHash("docHash123")
                .validFrom(validFrom)
                .expiresAt(expiresAt)
                .grantedBy(grantor)
                .build();

        PermissionEntity p2 = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("ALICE@example.com") // case insensitive targetUserEmail handling
                .targetOrgCode("acme") // case insensitive targetOrgCode handling
                .documentFileHash("docHash123")
                .validFrom(validFrom)
                .expiresAt(expiresAt)
                .grantedBy(grantor)
                .build();

        String hash1 = PermissionHashUtils.calculateRecordHash(p1);
        String hash2 = PermissionHashUtils.calculateRecordHash(p2);

        assertThat(hash1).isNotNull();
        assertThat(hash1).hasSize(64); // 64 Hex characters for SHA-256
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("Should produce different hash when permission level or attributes change")
    void calculateRecordHash_TamperDetection() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();

        PermissionEntity p1 = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .documentFileHash("docHash123")
                .grantedBy(grantor)
                .build();

        PermissionEntity pTampered = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.EDIT) // Tampered permission level
                .targetUserEmail("alice@example.com")
                .documentFileHash("docHash123")
                .grantedBy(grantor)
                .build();

        String originalHash = PermissionHashUtils.calculateRecordHash(p1);
        String tamperedHash = PermissionHashUtils.calculateRecordHash(pTampered);

        assertThat(originalHash).isNotEqualTo(tamperedHash);
    }

    @Test
    @DisplayName("Should produce different hash when note attribute changes")
    void calculateRecordHash_NoteTamperDetection() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();

        PermissionEntity p1 = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .documentFileHash("docHash123")
                .grantedBy(grantor)
                .note("Original Note")
                .build();

        PermissionEntity pModifiedNote = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .targetUserEmail("alice@example.com")
                .documentFileHash("docHash123")
                .grantedBy(grantor)
                .note("Modified Note")
                .build();

        String hash1 = PermissionHashUtils.calculateRecordHash(p1);
        String hash2 = PermissionHashUtils.calculateRecordHash(pModifiedNote);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("Should generate V1 and V2 hashes correctly and detect recipientType differences")
    void calculateRecordHash_V1_V2_Comparison() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();
        Instant validFrom = Instant.ofEpochSecond(1700000000);

        PermissionEntity p = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.USER_PERSONAL)
                .targetUserEmail("alice@example.com")
                .documentFileHash("docHash123")
                .validFrom(validFrom)
                .grantedBy(grantor)
                .build();

        String v1Hash = PermissionHashUtils.calculateRecordHashV1(p);
        String v2Hash = PermissionHashUtils.calculateRecordHashV2(p);

        assertThat(v1Hash).isNotNull().hasSize(64);
        assertThat(v2Hash).isNotNull().hasSize(64);
        assertThat(v1Hash).isNotEqualTo(v2Hash); // V2 includes recipientType, so hash differs
    }

    @Test
    @DisplayName("Should verify legacy V1 record hash using fallback verification")
    void verifyRecordHash_LegacyV1() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();
        Instant validFrom = Instant.ofEpochSecond(1700000000);

        PermissionEntity legacyEntity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("legacy@example.com")
                .documentFileHash("legacyHash")
                .validFrom(validFrom)
                .grantedBy(grantor)
                .build();

        String legacyV1Hash = PermissionHashUtils.calculateRecordHashV1(legacyEntity);
        legacyEntity.setRecordHash(legacyV1Hash);

        assertThat(PermissionHashUtils.verifyRecordHash(legacyEntity)).isTrue();
    }

    @Test
    @DisplayName("Should verify canonical V2 record hash")
    void verifyRecordHash_CanonicalV2() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();
        Instant validFrom = Instant.ofEpochSecond(1700000000);

        PermissionEntity v2Entity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.USER_PERSONAL)
                .targetUserEmail("canonical@example.com")
                .documentFileHash("docHash123")
                .validFrom(validFrom)
                .grantedBy(grantor)
                .build();

        String v2Hash = PermissionHashUtils.calculateRecordHashV2(v2Entity);
        v2Entity.setRecordHash(v2Hash);

        assertThat(PermissionHashUtils.verifyRecordHash(v2Entity)).isTrue();
    }

    @Test
    @DisplayName("Should fail verification when record is tampered")
    void verifyRecordHash_Tampered() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();

        PermissionEntity entity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.USER_PERSONAL)
                .targetUserEmail("user@example.com")
                .documentFileHash("docHash123")
                .grantedBy(grantor)
                .build();

        String v2Hash = PermissionHashUtils.calculateRecordHashV2(entity);
        entity.setRecordHash(v2Hash);

        // Tamper with permission level
        entity.setPermissionLevel(PermissionLevel.EDIT);

        assertThat(PermissionHashUtils.verifyRecordHash(entity)).isFalse();
    }

    @Test
    @DisplayName("Should verify canonical V3 record hash including grantorAuthorityTier and workspaceId")
    void verifyRecordHash_CanonicalV3() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();
        Instant validFrom = Instant.ofEpochSecond(1700000000);

        PermissionEntity v3Entity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.GROUP)
                .targetOrgCode("ACME")
                .grantorAuthorityTier(com.subject77.docshare.modules.permission.entities.AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(101L)
                .validFrom(validFrom)
                .grantedBy(grantor)
                .build();

        String v3Hash = PermissionHashUtils.calculateRecordHash(v3Entity);
        v3Entity.setRecordHash(v3Hash);

        assertThat(PermissionHashUtils.verifyRecordHash(v3Entity)).isTrue();
    }

    @Test
    @DisplayName("Should detect tampering when grantorAuthorityTier is modified")
    void verifyRecordHash_TamperedAuthorityTier() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();

        PermissionEntity entity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.GROUP)
                .targetOrgCode("ACME")
                .grantorAuthorityTier(com.subject77.docshare.modules.permission.entities.AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(101L)
                .grantedBy(grantor)
                .build();

        String originalHash = PermissionHashUtils.calculateRecordHash(entity);
        entity.setRecordHash(originalHash);

        // Tamper authority tier from WORKSPACE_MANAGER to ORG_ADMIN
        entity.setGrantorAuthorityTier(com.subject77.docshare.modules.permission.entities.AuthorityTier.ORG_ADMIN);

        assertThat(PermissionHashUtils.verifyRecordHash(entity)).isFalse();
    }

    @Test
    @DisplayName("Should detect tampering when workspaceId is modified")
    void verifyRecordHash_TamperedWorkspaceId() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();

        PermissionEntity entity = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.DOWNLOAD)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.GROUP)
                .targetOrgCode("ACME")
                .grantorAuthorityTier(com.subject77.docshare.modules.permission.entities.AuthorityTier.WORKSPACE_MANAGER)
                .workspaceId(101L)
                .grantedBy(grantor)
                .build();

        String originalHash = PermissionHashUtils.calculateRecordHash(entity);
        entity.setRecordHash(originalHash);

        // Tamper workspaceId from 101 to 999
        entity.setWorkspaceId(999L);

        assertThat(PermissionHashUtils.verifyRecordHash(entity)).isFalse();
    }

    @Test
    @DisplayName("Should verify legacy V1 and V2 records remain valid without provenance fields")
    void verifyRecordHash_LegacyCompatibility() {
        UserEntity grantor = UserEntity.builder().id("u1").email("grantor@example.com").build();

        // Legacy V1 without recipientType, without provenance
        PermissionEntity v1 = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .targetUserEmail("legacy@example.com")
                .grantedBy(grantor)
                .build();
        v1.setRecordHash(PermissionHashUtils.calculateRecordHashV1(v1));
        assertThat(PermissionHashUtils.verifyRecordHash(v1)).isTrue();

        // Legacy V2 with recipientType, without provenance
        PermissionEntity v2 = PermissionEntity.builder()
                .permissionType(PermissionType.ALLOW)
                .permissionLevel(PermissionLevel.VIEW)
                .recipientType(com.subject77.docshare.modules.permission.entities.RecipientType.USER_PERSONAL)
                .targetUserEmail("legacy@example.com")
                .grantedBy(grantor)
                .build();
        v2.setRecordHash(PermissionHashUtils.calculateRecordHashV2(v2));
        assertThat(PermissionHashUtils.verifyRecordHash(v2)).isTrue();
    }
}
