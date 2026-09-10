package com.subject77.docshare.modules.permission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EffectiveAccessAuditDto {
    private Long resourceId;
    private String resourceType; // "DOCUMENT" or "FOLDER"
    private String resourceName;
    private String ownerEmail;
    private List<UserAccessSummaryDto> userAccessSummaries;
    private List<PermissionResponseDto> directPermissions;
    private List<PermissionResponseDto> inheritedPermissions;
    private List<ShareLinkResponseDto> activeShareLinks;
}
