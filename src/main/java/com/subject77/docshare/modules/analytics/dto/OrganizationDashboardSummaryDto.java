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
public class OrganizationDashboardSummaryDto {

    private String orgCode;
    private String orgName;

    // Storage metrics
    private long usedStorageBytes;
    private long storageLimitBytes;
    private double storageUsagePercentage;
    private String usedStorageFormatted;

    // Content & Structure
    private long documentCount;
    private long folderCount;
    private long workspaceCount;
    private long teamCount;
    private long memberCount;

    // Governance & Access pressure
    private long pendingAccessRequestsCount;
    private long pendingInvitationsCount;

    // 14-day activity trend for compact area chart / sparkline
    private List<DailyActivitySummaryDto> recentActivityTrend;
}
