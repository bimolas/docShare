package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantMemberAccessRequestDto {
    @NotBlank(message = "Member email is required")
    private String userEmail;

    private Long folderId;
    private Long documentId;

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    private String note;
}
