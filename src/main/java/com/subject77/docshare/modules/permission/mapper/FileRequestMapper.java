package com.subject77.docshare.modules.permission.mapper;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.permission.dto.FileRequestResponseDto;
import com.subject77.docshare.modules.permission.entities.FileRequestEntity;
import com.subject77.docshare.modules.permission.entities.FileRequestRecipientType;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class FileRequestMapper {

    public FileRequestResponseDto toResponseDto(FileRequestEntity entity) {
        if (entity == null) {
            return null;
        }

        FileRequestResponseDto.FileRequestResponseDtoBuilder builder = FileRequestResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .note(entity.getNote())
                .status(entity.getStatus())
                .recipientType(entity.getRecipientType())
                .expiresAt(entity.getExpiresAt())
                .fulfilledAt(entity.getFulfilledAt())
                .cancelledAt(entity.getCancelledAt())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt());

        if (entity.getRequester() != null) {
            builder.requesterId(entity.getRequester().getId())
                    .requesterEmail(entity.getRequester().getEmail())
                    .requesterName(getDisplayName(
                            entity.getRequester().getFirstName(),
                            entity.getRequester().getLastName(),
                            entity.getRequester().getEmail()));
        }

        // Populate recipient fields based on type
        if (entity.getRecipientType() == FileRequestRecipientType.TEAM) {
            if (entity.getRecipientGroup() != null) {
                builder.recipientGroupId(entity.getRecipientGroup().getId())
                        .recipientGroupName(entity.getRecipientGroup().getName());
            }
        } else {
            // USER (default — handles null recipientType for legacy records too)
            if (entity.getRecipientUser() != null) {
                builder.recipientId(entity.getRecipientUser().getId())
                        .recipientEmail(entity.getRecipientUser().getEmail())
                        .recipientName(getDisplayName(
                                entity.getRecipientUser().getFirstName(),
                                entity.getRecipientUser().getLastName(),
                                entity.getRecipientUser().getEmail()));
            }
        }

        if (entity.getDestinationFolder() != null) {
            builder.destinationFolderId(entity.getDestinationFolder().getId())
                    .destinationFolderName(entity.getDestinationFolder().getName());
        }

        if (entity.getUploadedDocuments() != null && !entity.getUploadedDocuments().isEmpty()) {
            List<FileRequestResponseDto.UploadedDocumentSummaryDto> docDtos = entity.getUploadedDocuments().stream()
                    .map(this::toDocumentSummary)
                    .collect(Collectors.toList());
            builder.uploadedDocuments(docDtos);
        } else {
            builder.uploadedDocuments(Collections.emptyList());
        }

        return builder.build();
    }

    private FileRequestResponseDto.UploadedDocumentSummaryDto toDocumentSummary(DocumentEntity doc) {
        if (doc == null) {
            return null;
        }
        return FileRequestResponseDto.UploadedDocumentSummaryDto.builder()
                .documentId(doc.getId())
                .fileName(doc.getFileName())
                .contentType(doc.getContentType())
                .fileSize(doc.getFileSize())
                .uploadedAt(doc.getUploadedAt())
                .build();
    }

    private String getDisplayName(String firstName, String lastName, String email) {
        if (firstName != null && !firstName.isBlank()) {
            return lastName != null && !lastName.isBlank() ? firstName + " " + lastName : firstName;
        }
        return email;
    }
}
