package com.subject77.docshare.modules.permission.utils;

import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import com.subject77.docshare.modules.permission.entities.AuthorityTier;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

public class PermissionHashUtils {

    /**
     * V1 Hash calculation (legacy format without recipientType).
     */
    public static String calculateRecordHashV1(
            PermissionType permissionType,
            PermissionLevel permissionLevel,
            String targetUserEmail,
            String targetOrgCode,
            Long targetGroupId,
            Long documentId,
            String documentFileHash,
            Long folderId,
            Instant validFrom,
            Instant expiresAt,
            String grantorEmail,
            String note
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(permissionType != null ? permissionType.name() : "").append("|");
        sb.append("level=").append(permissionLevel != null ? permissionLevel.name() : "").append("|");
        sb.append("targetUser=").append(targetUserEmail != null ? targetUserEmail.toLowerCase() : "").append("|");
        sb.append("targetOrg=").append(targetOrgCode != null ? targetOrgCode.toUpperCase() : "").append("|");
        sb.append("targetGroup=").append(targetGroupId != null ? targetGroupId : "").append("|");
        sb.append("docId=").append(documentId != null ? documentId : "").append("|");
        sb.append("docHash=").append(documentFileHash != null ? documentFileHash : "").append("|");
        sb.append("folderId=").append(folderId != null ? folderId : "").append("|");
        sb.append("validFrom=").append(validFrom != null ? validFrom.getEpochSecond() : "").append("|");
        sb.append("expiresAt=").append(expiresAt != null ? expiresAt.getEpochSecond() : "").append("|");
        sb.append("grantor=").append(grantorEmail != null ? grantorEmail.toLowerCase() : "").append("|");
        sb.append("note=").append(note != null ? note : "");

        return sha256Hex(sb.toString());
    }

    public static String calculateRecordHashV1(PermissionEntity entity) {
        if (entity == null) return null;
        return calculateRecordHashV1(
                entity.getPermissionType(),
                entity.getPermissionLevel(),
                entity.getTargetUserEmail(),
                entity.getTargetOrgCode(),
                entity.getTargetGroup() != null ? entity.getTargetGroup().getId() : null,
                entity.getDocument() != null ? entity.getDocument().getId() : null,
                entity.getDocumentFileHash(),
                entity.getFolder() != null ? entity.getFolder().getId() : null,
                entity.getValidFrom(),
                entity.getExpiresAt(),
                entity.getGrantedBy() != null ? entity.getGrantedBy().getEmail() : null,
                entity.getNote()
        );
    }

    /**
     * V2 Hash calculation (canonical format including recipientType).
     */
    public static String calculateRecordHashV2(
            PermissionType permissionType,
            PermissionLevel permissionLevel,
            RecipientType recipientType,
            String targetUserEmail,
            String targetOrgCode,
            Long targetGroupId,
            Long documentId,
            String documentFileHash,
            Long folderId,
            Instant validFrom,
            Instant expiresAt,
            String grantorEmail,
            String note
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(permissionType != null ? permissionType.name() : "").append("|");
        sb.append("level=").append(permissionLevel != null ? permissionLevel.name() : "").append("|");
        sb.append("recipientType=").append(recipientType != null ? recipientType.name() : "").append("|");
        sb.append("targetUser=").append(targetUserEmail != null ? targetUserEmail.toLowerCase() : "").append("|");
        sb.append("targetOrg=").append(targetOrgCode != null ? targetOrgCode.toUpperCase() : "").append("|");
        sb.append("targetGroup=").append(targetGroupId != null ? targetGroupId : "").append("|");
        sb.append("docId=").append(documentId != null ? documentId : "").append("|");
        sb.append("docHash=").append(documentFileHash != null ? documentFileHash : "").append("|");
        sb.append("folderId=").append(folderId != null ? folderId : "").append("|");
        sb.append("validFrom=").append(validFrom != null ? validFrom.getEpochSecond() : "").append("|");
        sb.append("expiresAt=").append(expiresAt != null ? expiresAt.getEpochSecond() : "").append("|");
        sb.append("grantor=").append(grantorEmail != null ? grantorEmail.toLowerCase() : "").append("|");
        sb.append("note=").append(note != null ? note : "");

        return sha256Hex(sb.toString());
    }

    public static String calculateRecordHashV2(PermissionEntity entity) {
        if (entity == null) return null;
        return calculateRecordHashV2(
                entity.getPermissionType(),
                entity.getPermissionLevel(),
                entity.getRecipientType(),
                entity.getTargetUserEmail(),
                entity.getTargetOrgCode(),
                entity.getTargetGroup() != null ? entity.getTargetGroup().getId() : null,
                entity.getDocument() != null ? entity.getDocument().getId() : null,
                entity.getDocumentFileHash(),
                entity.getFolder() != null ? entity.getFolder().getId() : null,
                entity.getValidFrom(),
                entity.getExpiresAt(),
                entity.getGrantedBy() != null ? entity.getGrantedBy().getEmail() : null,
                entity.getNote()
        );
    }

    /**
     * V3 Hash calculation (canonical format including recipientType and immutable provenance: grantorAuthorityTier, workspaceId).
     */
    public static String calculateRecordHashV3(
            PermissionType permissionType,
            PermissionLevel permissionLevel,
            RecipientType recipientType,
            String targetUserEmail,
            String targetOrgCode,
            Long targetGroupId,
            Long documentId,
            String documentFileHash,
            Long folderId,
            Instant validFrom,
            Instant expiresAt,
            String grantorEmail,
            AuthorityTier grantorAuthorityTier,
            Long workspaceId,
            String note
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("type=").append(permissionType != null ? permissionType.name() : "").append("|");
        sb.append("level=").append(permissionLevel != null ? permissionLevel.name() : "").append("|");
        sb.append("recipientType=").append(recipientType != null ? recipientType.name() : "").append("|");
        sb.append("targetUser=").append(targetUserEmail != null ? targetUserEmail.toLowerCase() : "").append("|");
        sb.append("targetOrg=").append(targetOrgCode != null ? targetOrgCode.toUpperCase() : "").append("|");
        sb.append("targetGroup=").append(targetGroupId != null ? targetGroupId : "").append("|");
        sb.append("docId=").append(documentId != null ? documentId : "").append("|");
        sb.append("docHash=").append(documentFileHash != null ? documentFileHash : "").append("|");
        sb.append("folderId=").append(folderId != null ? folderId : "").append("|");
        sb.append("validFrom=").append(validFrom != null ? validFrom.getEpochSecond() : "").append("|");
        sb.append("expiresAt=").append(expiresAt != null ? expiresAt.getEpochSecond() : "").append("|");
        sb.append("grantor=").append(grantorEmail != null ? grantorEmail.toLowerCase() : "").append("|");
        sb.append("grantorTier=").append(grantorAuthorityTier != null ? grantorAuthorityTier.name() : "").append("|");
        sb.append("workspaceId=").append(workspaceId != null ? workspaceId : "").append("|");
        sb.append("note=").append(note != null ? note : "");

        return sha256Hex(sb.toString());
    }

    public static String calculateRecordHashV3(PermissionEntity entity) {
        if (entity == null) return null;
        return calculateRecordHashV3(
                entity.getPermissionType(),
                entity.getPermissionLevel(),
                entity.getRecipientType(),
                entity.getTargetUserEmail(),
                entity.getTargetOrgCode(),
                entity.getTargetGroup() != null ? entity.getTargetGroup().getId() : null,
                entity.getDocument() != null ? entity.getDocument().getId() : null,
                entity.getDocumentFileHash(),
                entity.getFolder() != null ? entity.getFolder().getId() : null,
                entity.getValidFrom(),
                entity.getExpiresAt(),
                entity.getGrantedBy() != null ? entity.getGrantedBy().getEmail() : null,
                entity.getGrantorAuthorityTier(),
                entity.getWorkspaceId(),
                entity.getNote()
        );
    }

    /**
     * Default calculation method: uses V3 format if provenance tier/workspaceId is present,
     * falls back to V2 format if recipientType is present, otherwise falls back to V1.
     */
    public static String calculateRecordHash(PermissionEntity entity) {
        if (entity == null) return null;
        if (entity.getGrantorAuthorityTier() != null || entity.getWorkspaceId() != null) {
            return calculateRecordHashV3(entity);
        }
        if (entity.getRecipientType() != null) {
            return calculateRecordHashV2(entity);
        }
        return calculateRecordHashV1(entity);
    }

    public static String calculateRecordHash(
            PermissionType permissionType,
            PermissionLevel permissionLevel,
            String targetUserEmail,
            String targetOrgCode,
            Long targetGroupId,
            Long documentId,
            String documentFileHash,
            Long folderId,
            Instant validFrom,
            Instant expiresAt,
            String grantorEmail,
            String note
    ) {
        return calculateRecordHashV1(permissionType, permissionLevel, targetUserEmail, targetOrgCode, targetGroupId, documentId, documentFileHash, folderId, validFrom, expiresAt, grantorEmail, note);
    }

    public static String calculateRecordHash(
            PermissionType permissionType,
            PermissionLevel permissionLevel,
            String targetUserEmail,
            String targetOrgCode,
            Long targetGroupId,
            Long documentId,
            String documentFileHash,
            Long folderId,
            Instant validFrom,
            Instant expiresAt,
            String grantorEmail
    ) {
        return calculateRecordHashV1(permissionType, permissionLevel, targetUserEmail, targetOrgCode, targetGroupId, documentId, documentFileHash, folderId, validFrom, expiresAt, grantorEmail, null);
    }

    public static String calculateRecordHash(
            PermissionType permissionType,
            PermissionLevel permissionLevel,
            String targetUserEmail,
            String targetOrgCode,
            Long documentId,
            String documentFileHash,
            Long folderId,
            Instant validFrom,
            Instant expiresAt,
            String grantorEmail
    ) {
        return calculateRecordHashV1(permissionType, permissionLevel, targetUserEmail, targetOrgCode, null, documentId, documentFileHash, folderId, validFrom, expiresAt, grantorEmail, null);
    }

    /**
     * Safely verifies permission record hash against V3 format first (if provenance present),
     * then V2 format, falling back to V1 legacy format.
     */
    public static boolean verifyRecordHash(PermissionEntity entity) {
        if (entity == null || entity.getRecordHash() == null) {
            return false;
        }
        String storedHash = entity.getRecordHash();
        if (entity.getGrantorAuthorityTier() != null || entity.getWorkspaceId() != null) {
            String v3Hash = calculateRecordHashV3(entity);
            if (storedHash.equalsIgnoreCase(v3Hash)) {
                return true;
            }
        }
        String v2Hash = calculateRecordHashV2(entity);
        if (storedHash.equalsIgnoreCase(v2Hash)) {
            return true;
        }
        String v1Hash = calculateRecordHashV1(entity);
        return storedHash.equalsIgnoreCase(v1Hash);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
