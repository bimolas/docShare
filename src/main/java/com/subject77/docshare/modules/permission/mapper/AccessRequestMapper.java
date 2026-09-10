package com.subject77.docshare.modules.permission.mapper;

import com.subject77.docshare.modules.permission.dto.AccessRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.AccessRequestEntity;
import org.springframework.stereotype.Component;

@Component
public class AccessRequestMapper {

    public AccessRequestResponseDto toResponseDto(AccessRequestEntity entity) {
        if (entity == null) {
            return null;
        }

        AccessRequestResponseDto.AccessRequestResponseDtoBuilder builder = AccessRequestResponseDto.builder()
                .id(entity.getId())
                .requestedPermissionLevel(entity.getRequestedPermissionLevel())
                .status(entity.getStatus())
                .reason(entity.getReason())
                .processedAt(entity.getProcessedAt())
                .reviewerNote(entity.getReviewerNote())
                .cancellationReason(entity.getCancellationReason())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (entity.getRequester() != null) {
            builder.requesterUserId(entity.getRequester().getId())
                    .requesterUserEmail(entity.getRequester().getEmail())
                    .requesterUserName(entity.getRequester().getFirstName() != null ? 
                            entity.getRequester().getFirstName() + " " + entity.getRequester().getLastName() : 
                            entity.getRequester().getEmail());
        }

        if (entity.getProcessedBy() != null) {
            builder.processedByUserId(entity.getProcessedBy().getId())
                    .processedByUserEmail(entity.getProcessedBy().getEmail());
        }

        if (entity.getGrantedPermission() != null) {
            builder.grantedPermissionId(entity.getGrantedPermission().getId())
                    .grantedPermissionLevel(entity.getGrantedPermission().getPermissionLevel());
        }

        if (entity.getDocument() != null) {
            builder.targetType("DOCUMENT")
                    .documentId(entity.getDocument().getId())
                    .documentFileHash(entity.getDocumentFileHash() != null ? entity.getDocumentFileHash() : entity.getDocument().getFileHash())
                    .documentName(entity.getDocument().getFileName());
        } else if (entity.getFolder() != null) {
            builder.targetType("FOLDER")
                    .folderId(entity.getFolder().getId())
                    .folderName(entity.getFolder().getName());
        }

        return builder.build();
    }
}
