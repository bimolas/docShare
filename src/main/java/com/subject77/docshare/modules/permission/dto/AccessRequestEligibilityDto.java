package com.subject77.docshare.modules.permission.dto;

import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequestEligibilityDto {

    private boolean eligible;
    private String reasonCode;
    private boolean requireReason;
    private Long existingRequestId;
    private PermissionLevel currentPermissionLevel;
}
