package com.subject77.docshare.modules.document.mapper;

import com.subject77.docshare.modules.document.dto.DocumentRequestDto;
import com.subject77.docshare.modules.document.dto.DocumentResponse;
import com.subject77.docshare.modules.document.dto.DocumentResponseDto;
import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.document.entities.SpaceType;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;

public class DocumentMapper {

    public static DocumentResponseDto toResponseDto(DocumentEntity entity) {
        if (entity == null)
            return null;

        return DocumentResponseDto.builder()
                .id(entity.getId())
                .fileHash(entity.getFileHash())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .s3Bucket(entity.getS3Bucket())
                .objectKey(entity.getObjectKey())
                .fileSize(entity.getFileSize())
                .folderId(entity.getFolder() != null ? entity.getFolder().getId() : null)
                .ownerOrgCode(entity.getOwnerOrg() != null ? entity.getOwnerOrg().getOrgCode() : null)
                .uploadedByUserId(entity.getUploadedBy() != null ? entity.getUploadedBy().getId() : null)
                .uploadedAt(entity.getUploadedAt())
                .version(entity.getVersion())
                .lastModifiedAt(entity.getLastModifiedAt())
                .lastModifiedByUserId(entity.getLastModifiedBy() != null ? entity.getLastModifiedBy().getId() : null)
                .lastModifiedByOrgCode(
                        entity.getLastModifiedByOrg() != null ? entity.getLastModifiedByOrg().getOrgCode() : null)
                .build();
    }

    public static DocumentEntity toEntity(DocumentRequestDto dto, String fileHash, String contentType, String s3Bucket,
            String objectKey, Long fileSize, FolderEntity folder, OrganizationEntity ownerOrg, UserEntity uploadedBy) {
        if (dto == null)
            return null;

        return DocumentEntity.builder()
                .fileHash(fileHash)
                .fileName(dto.getFileName())
                .contentType(contentType)
                .s3Bucket(s3Bucket)
                .objectKey(objectKey)
                .fileSize(fileSize)
                .folder(folder)
                .ownerOrg(ownerOrg)
                .uploadedBy(uploadedBy)
                // version and uploadedAt handled by JPA/Hibernate
                .build();
    }

    public static DocumentResponse toDocumentResponse(DocumentEntity entity) {
        if (entity == null)
            return null;

        return DocumentResponse.builder()
                .id(entity.getId())
                .fileName(entity.getFileName())
                .contentType(entity.getContentType())
                .fileSize(entity.getFileSize())
                .fileHash(entity.getFileHash())
                .s3Bucket(entity.getS3Bucket())
                .objectKey(entity.getObjectKey())
                .status(entity.getStatus())
                .spaceType(entity.getOwnerOrg() == null ? SpaceType.PERSONAL : SpaceType.ORGANIZATION)
                .ownerOrgCode(entity.getOwnerOrg() != null ? entity.getOwnerOrg().getOrgCode() : null)
                .uploadedByUserId(entity.getUploadedBy() != null ? entity.getUploadedBy().getId() : null)
                .uploadedByUserEmail(entity.getUploadedBy() != null ? entity.getUploadedBy().getEmail() : null)
                .folderId(entity.getFolder() != null ? entity.getFolder().getId() : null)
                .folderName(entity.getFolder() != null ? entity.getFolder().getName() : null)
                .uploadedAt(entity.getUploadedAt())
                .version(entity.getVersion())
                .build();
    }
}


