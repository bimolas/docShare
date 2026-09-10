package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import com.subject77.docshare.modules.permission.entities.RecipientType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkSelectedMemberShareDto {

    @NotNull(message = "Target organization code is required")
    private String targetOrgCode;

    private RecipientType recipientType;

    private String documentFileHash;
    private Long folderId;

    @NotEmpty(message = "At least one selected member is required")
    @Valid
    private List<MemberShareItemDto> selectedMembers;

    private String note;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberShareItemDto {
        @NotNull(message = "Target user email is required")
        @Email(message = "Invalid email format")
        private String targetUserEmail;

        private RecipientType recipientType;

        @NotNull(message = "Permission level is required")
        private PermissionLevel permissionLevel;

        private PermissionType permissionType;
        private Instant expiresAt;
        private String note;
    }
}
