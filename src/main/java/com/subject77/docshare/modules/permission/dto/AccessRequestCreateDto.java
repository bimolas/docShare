package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequestCreateDto {

    private Long documentId;
    private String documentFileHash;
    private Long folderId;

    @NotNull(message = "Requested permission level is required")
    private PermissionLevel requestedPermissionLevel;

    private String reason;
}
