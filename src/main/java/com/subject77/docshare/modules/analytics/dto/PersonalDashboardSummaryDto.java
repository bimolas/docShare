package com.subject77.docshare.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalDashboardSummaryDto {

    // Storage metrics
    private long usedStorageBytes;
    private long storageLimitBytes;
    private double storageUsagePercentage;
    private String usedStorageFormatted;

    // Content counts
    private long documentCount;
    private long folderCount;

    // Collaboration & Action pressure
    private long pendingAccessReviewsCount;
    private long myPendingAccessRequestsCount;
    private long activeDirectSharesCount;

    // 14-day activity trend for compact area chart / sparkline
    private List<DailyActivitySummaryDto> recentActivityTrend;
}
