package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionUpdateRequestDto {

    @NotNull(message = "Permission level is required")
    private PermissionLevel permissionLevel;

    private Instant expiresAt;
}
