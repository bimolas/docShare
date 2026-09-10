package com.subject77.docshare.modules.analytics.services;

import com.subject77.docshare.modules.analytics.dto.DailyActivityDetailDto;
import com.subject77.docshare.modules.analytics.dto.DailyActivitySummaryDto;
import com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.PersonalDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.StorageUsageResponseDto;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.List;

public interface AnalyticsService {
    List<DailyActivitySummaryDto> getActivityHeatmap(int days, UserEntity currentUser, String orgCode);

    DailyActivityDetailDto getDailyActivityDetail(String date, UserEntity currentUser, String orgCode);

    StorageUsageResponseDto getStorageUsage(UserEntity currentUser, String orgCode);

    PersonalDashboardSummaryDto getPersonalDashboardSummary(UserEntity currentUser);

    OrganizationDashboardSummaryDto getOrganizationDashboardSummary(UserEntity currentUser, String orgCode);
}

