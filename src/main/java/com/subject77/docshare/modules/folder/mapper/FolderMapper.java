package com.subject77.docshare.modules.folder.mapper;

import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.folder.dto.FolderRequestDto;
import com.subject77.docshare.modules.folder.dto.FolderResponseDto;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;

import com.subject77.docshare.modules.folder.entities.SubspaceType;

public class FolderMapper {

    public static FolderResponseDto toResponseDto(FolderEntity entity) {
        if (entity == null)
            return null;

        SubspaceType computedType = entity.getSubspaceType();
        if (computedType == null && entity.getParentFolder() == null) {
            computedType = entity.getOwnerOrg() != null ? SubspaceType.ORGANIZATION_GENERAL : SubspaceType.PERSONAL;
        }

        return FolderResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .parentFolderId(entity.getParentFolder() != null ? entity.getParentFolder().getId() : null)
                .ownerOrgCode(entity.getOwnerOrg() != null ? entity.getOwnerOrg().getOrgCode() : null)
                .createdByUserId(entity.getCreatedBy() != null ? entity.getCreatedBy().getId() : null)
                .createdAt(entity.getCreatedAt())
                .subspaceType(computedType)
                .isDefaultSubspace(entity.isDefaultSubspace())
                .build();
    }

    public static FolderResponseDto toResponseDto(com.subject77.docshare.modules.workspace.dto.ProjectWorkspaceResponseDto workspace, FolderEntity rootFolder) {
        if (workspace == null) {
            return null;
        }

        SubspaceType computedType = rootFolder != null ? rootFolder.getSubspaceType() : null;
        if (computedType == null) {
            computedType = SubspaceType.ORGANIZATION_GENERAL;
        }

        return FolderResponseDto.builder()
                .id(workspace.getRootFolderId())
                .name(workspace.getName())
                .parentFolderId(null)
                .ownerOrgCode(workspace.getOrgCode())
                .createdByUserId(rootFolder != null && rootFolder.getCreatedBy() != null
                        ? rootFolder.getCreatedBy().getId()
                        : workspace.getCreatedByUserId())
                .createdAt(rootFolder != null && rootFolder.getCreatedAt() != null
                        ? rootFolder.getCreatedAt()
                        : workspace.getCreatedAt())
                .subspaceType(computedType)
                .isDefaultSubspace(rootFolder != null && rootFolder.isDefaultSubspace())
                .build();
    }

    public static FolderEntity toEntity(FolderRequestDto dto, FolderEntity parentFolder, OrganizationEntity ownerOrg,
            UserEntity createdBy) {
        if (dto == null)
            return null;

        return FolderEntity.builder()
                .name(dto.getName())
                .parentFolder(parentFolder)
                .ownerOrg(ownerOrg)
                .createdBy(createdBy)
                // createdAt handled by @PrePersist
                .build();
    }
}
