package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.permission.entities.PermissionType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareLinkRequestDto {

    private Long documentId;

    private Long folderId;

    @Builder.Default
    private PermissionType permissionType = PermissionType.ALLOW;

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    private String password;

    private Integer expiresInHours;

    private String targetUserEmail;

    private String note;
}
