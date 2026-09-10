package com.subject77.docshare.modules.permission.mapper;

import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.permission.dto.PermissionResponseDto;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;

import com.subject77.docshare.modules.permission.entities.RecipientType;

public class PermissionMapper {

    public static PermissionResponseDto toResponseDto(PermissionEntity entity) {
        if (entity == null)
            return null;

        RecipientType resolvedRecipientType = entity.getRecipientType();
        if (resolvedRecipientType == null) {
            boolean hasEmail = entity.getTargetUserEmail() != null && !entity.getTargetUserEmail().isBlank();
            boolean hasOrgCode = entity.getTargetOrgCode() != null && !entity.getTargetOrgCode().isBlank();
            boolean hasGroup = entity.getTargetGroup() != null;

            if (hasGroup) {
                resolvedRecipientType = RecipientType.GROUP;
            } else if (hasOrgCode && !hasEmail) {
                resolvedRecipientType = RecipientType.ORGANIZATION;
            } else if (hasEmail && !hasOrgCode) {
                resolvedRecipientType = RecipientType.USER_PERSONAL;
            }
            // Ambiguous legacy records (hasEmail && hasOrgCode with null recipientType) are mapped as null
        }

        FolderEntity folder = entity.getFolder();
        String folderName = folder != null ? folder.getName() : null;
        String subspaceType = (folder != null && folder.getSubspaceType() != null) ? folder.getSubspaceType().name() : null;
        String orgName = null;
        if (folder != null && folder.getOwnerOrg() != null) {
            orgName = folder.getOwnerOrg().getName();
        } else if (entity.getTargetOrgCode() != null) {
            orgName = entity.getTargetOrgCode();
        }

        return PermissionResponseDto.builder()
                .id(entity.getId())
                .permissionType(entity.getPermissionType())
                .permissionLevel(entity.getPermissionLevel())
                .recipientType(resolvedRecipientType)
                .grantedByUserId(entity.getGrantedBy() != null ? entity.getGrantedBy().getId() : null)
                .createdAt(entity.getCreatedAt())
                .documentId(entity.getDocument() != null ? entity.getDocument().getId() : null)
                .documentFileHash(entity.getDocumentFileHash())
                .documentName(entity.getDocument() != null ? entity.getDocument().getFileName() : null)
                .folderId(folder != null ? folder.getId() : null)
                .folderName(folderName)
                .subspaceType(subspaceType)
                .orgName(orgName)
                .targetUserEmail(entity.getTargetUserEmail())
                .targetOrgCode(entity.getTargetOrgCode())
                .targetGroupId(entity.getTargetGroup() != null ? entity.getTargetGroup().getId() : null)
                .collaborationId(entity.getCollaboration() != null ? entity.getCollaboration().getId() : null)
                .workspaceId(entity.getWorkspaceId())
                .grantorAuthorityTier(entity.getGrantorAuthorityTier())
                .shareLinkToken(null) // Sanitized: secret share tokens must never be exposed via general permission responses
                .passwordHash(null) // Sanitized: password hashes must never be exposed via permission responses
                .validFrom(entity.getValidFrom())
                .expiresAt(entity.getExpiresAt())
                .recordHash(entity.getRecordHash())
                .blockchainTxHash(entity.getBlockchainTxHash())
                .blockchainStatus(entity.getBlockchainStatus())
                .revoked(entity.isRevoked())
                .suspended(entity.isSuspended())
                .revokedAt(entity.getRevokedAt())
                .revokedByUserId(entity.getRevokedBy() != null ? entity.getRevokedBy().getId() : null)
                .note(entity.getNote())
                .build();
    }

    public static PermissionEntity toEntity(PermissionRequestDto dto, UserEntity grantedBy, FolderEntity folder, String shareLinkToken) {
        if (dto == null)
            return null;

        return PermissionEntity.builder()
                .permissionType(dto.getPermissionType())
                .permissionLevel(dto.getPermissionLevel())
                .grantedBy(grantedBy)
                .documentFileHash(dto.getDocumentFileHash())
                .folder(folder)
                .recipientType(dto.getRecipientType())
                .targetUserEmail(dto.getTargetUserEmail())
                .targetOrgCode(dto.getTargetOrgCode())
                .shareLinkToken(shareLinkToken)
                .passwordHash(dto.getPasswordHash())
                .expiresAt(dto.getExpiresAt())
                .note(dto.getNote())
                // createdAt handled by @PrePersist
                .build();
    }
}
