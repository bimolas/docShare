package com.subject77.docshare.modules.organization.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMemberAccessRequestDto {
    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;
}
