package com.subject77.docshare.modules.permission.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessRequestActionDto {
    private String reviewerNote;
    private com.subject77.docshare.modules.permission.entities.PermissionLevel grantedPermissionLevel;
}
