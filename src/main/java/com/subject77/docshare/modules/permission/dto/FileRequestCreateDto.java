package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.FileRequestRecipientType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

/**
 * Request body for creating a new File Request.
 *
 * <h3>Recipient rules</h3>
 * <ul>
 *   <li>If {@code recipientType == USER}: supply exactly one of
 *       {@code recipientUserId} or {@code recipientEmail}.</li>
 *   <li>If {@code recipientType == TEAM}: supply {@code recipientGroupId}.</li>
 *   <li>Mixing fields from different types (e.g. both {@code recipientUserId}
 *       and {@code recipientGroupId}) is rejected by the service layer.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRequestCreateDto {

    @NotNull(message = "Destination folder ID is required")
    private Long destinationFolderId;

    /**
     * Recipient type discriminator.  Defaults to {@link FileRequestRecipientType#USER}
     * when not provided for backward-compatibility.
     */
    @Builder.Default
    private FileRequestRecipientType recipientType = FileRequestRecipientType.USER;

    // ── USER recipient fields ────────────────────────────────────────────────

    /** User's internal ID.  Used when {@code recipientType == USER}. */
    private String recipientUserId;

    /** User's email address (alternative to ID).  Used when {@code recipientType == USER}. */
    private String recipientEmail;

    // ── TEAM recipient field ─────────────────────────────────────────────────

    /** ID of the target {@code GroupEntity}.  Used when {@code recipientType == TEAM}. */
    private Long recipientGroupId;

    // ── Common fields ────────────────────────────────────────────────────────

    @NotBlank(message = "Title is required")
    private String title;

    private String note;

    private Instant expiresAt;
}
