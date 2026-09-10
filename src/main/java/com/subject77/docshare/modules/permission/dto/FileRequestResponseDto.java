package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.FileRequestRecipientType;
import com.subject77.docshare.modules.permission.entities.FileRequestStatus;
import lombok.*;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for File Request resources.
 *
 * <h3>Recipient fields</h3>
 * <ul>
 *   <li>{@code recipientType} — always present; indicates USER or TEAM.</li>
 *   <li>For USER requests: {@code recipientId}, {@code recipientName},
 *       {@code recipientEmail} are populated.</li>
 *   <li>For TEAM requests: {@code recipientGroupId} and
 *       {@code recipientGroupName} are populated; user-level recipient
 *       fields are {@code null}.</li>
 * </ul>
 * Membership internals, secrets, permission hashes, and storage keys are
 * never exposed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRequestResponseDto {

    private Long id;

    // ── Requester ────────────────────────────────────────────────────────────

    private String requesterId;
    private String requesterName;
    private String requesterEmail;

    // ── Recipient discriminator ──────────────────────────────────────────────

    private FileRequestRecipientType recipientType;

    // USER recipient fields
    private String recipientId;
    private String recipientName;
    private String recipientEmail;

    // TEAM recipient fields
    private Long recipientGroupId;
    private String recipientGroupName;

    // ── Destination ──────────────────────────────────────────────────────────

    private Long destinationFolderId;
    private String destinationFolderName;

    // ── Content ──────────────────────────────────────────────────────────────

    private String title;
    private String note;
    private FileRequestStatus status;

    private Instant expiresAt;
    private Instant fulfilledAt;
    private Instant cancelledAt;

    private Instant createdAt;
    private Instant updatedAt;

    private List<UploadedDocumentSummaryDto> uploadedDocuments;

    // ── Nested ───────────────────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UploadedDocumentSummaryDto {
        private Long documentId;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private Instant uploadedAt;
    }
}
