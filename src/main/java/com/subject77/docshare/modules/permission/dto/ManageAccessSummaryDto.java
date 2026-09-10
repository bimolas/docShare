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
public class ManageAccessSummaryDto {

    private Long resourceId;
    private String resourceType; // "DOCUMENT" or "FOLDER"
    private String resourceName;
    private String ownerUserId;
    private String ownerDisplayName;
    private String ownerEmail;
    private String ownerOrgCode;
    private List<EffectiveAccessEntryDto> entries;
}
