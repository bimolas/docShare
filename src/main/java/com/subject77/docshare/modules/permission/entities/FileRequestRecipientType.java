package com.subject77.docshare.modules.permission.entities;

/**
 * Discriminator for the recipient of a FileRequest.
 *
 * <ul>
 *   <li>{@link #USER} — the request targets a single identified user.</li>
 *   <li>{@link #TEAM} — the request targets a group; any current, active member
 *       of the group who also has an active organisation membership is eligible
 *       to upload.  Membership is resolved dynamically at upload time (not
 *       snapshotted at creation time).</li>
 * </ul>
 */
public enum FileRequestRecipientType {
    USER,
    TEAM
}
