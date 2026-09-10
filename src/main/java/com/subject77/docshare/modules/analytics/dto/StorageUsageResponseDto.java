package com.subject77.docshare.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StorageUsageResponseDto {
    private long totalStorageBytes;
    private long documentCount;
    private long folderCount;
    private String usedSpaceFormatted;
    private long storageLimitBytes;
}
