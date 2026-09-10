package com.subject77.docshare.modules.audit.mapper;

import com.subject77.docshare.modules.audit.entities.AccessLogEntity;
import com.subject77.docshare.modules.audit.dto.AccessLogRequestDto;
import com.subject77.docshare.modules.audit.dto.AccessLogResponseDto;
import com.subject77.docshare.modules.folder.entities.FolderEntity;

public class AccessLogMapper {

    public static AccessLogResponseDto toResponseDto(AccessLogEntity entity) {
        if (entity == null)
            return null;

        return AccessLogResponseDto.builder()
                .id(entity.getId())
                .action(entity.getAction())
                .timestamp(entity.getTimestamp())
                .documentFileHash(entity.getDocumentFileHash())
                .folderId(entity.getFolder() != null ? entity.getFolder().getId() : null)
                .accessorEmail(entity.getAccessorEmail())
                .ipAddress(entity.getIpAddress())
                .userAgent(entity.getUserAgent())
                .txHash(entity.getTxHash())
                .build();
    }

    public static AccessLogEntity toEntity(AccessLogRequestDto dto, FolderEntity folder) {
        if (dto == null)
            return null;

        return AccessLogEntity.builder()
                .action(dto.getAction())
                .documentFileHash(dto.getDocumentFileHash())
                .folder(folder)
                .accessorEmail(dto.getAccessorEmail())
                .ipAddress(dto.getIpAddress())
                .userAgent(dto.getUserAgent())
                .txHash(dto.getTxHash())
                // timestamp handled by @PrePersist
                .build();
    }
}
