package com.subject77.docshare.modules.analytics.controllers;

import com.subject77.docshare.config.security.CurrentUser;
import com.subject77.docshare.modules.analytics.dto.DailyActivityDetailDto;
import com.subject77.docshare.modules.analytics.dto.DailyActivitySummaryDto;
import com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.PersonalDashboardSummaryDto;
import com.subject77.docshare.modules.analytics.dto.StorageUsageResponseDto;
import com.subject77.docshare.modules.analytics.services.AnalyticsService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@PreAuthorize("isAuthenticated()")
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/heatmap")
    public ResponseEntity<List<DailyActivitySummaryDto>> getActivityHeatmap(
            @RequestParam(name = "days", defaultValue = "365") int days,
            @RequestParam(name = "orgCode", required = false) String orgCode,
            @CurrentUser UserEntity currentUser) {
        List<DailyActivitySummaryDto> heatmap = analyticsService.getActivityHeatmap(days, currentUser, orgCode);
        return ResponseEntity.ok(heatmap);
    }

    @GetMapping("/daily-detail")
    public ResponseEntity<DailyActivityDetailDto> getDailyActivityDetail(
            @RequestParam(name = "date") String date,
            @RequestParam(name = "orgCode", required = false) String orgCode,
            @CurrentUser UserEntity currentUser) {
        DailyActivityDetailDto detail = analyticsService.getDailyActivityDetail(date, currentUser, orgCode);
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/storage")
    public ResponseEntity<StorageUsageResponseDto> getStorageUsage(
            @RequestParam(name = "orgCode", required = false) String orgCode,
            @CurrentUser UserEntity currentUser) {
        StorageUsageResponseDto storage = analyticsService.getStorageUsage(currentUser, orgCode);
        return ResponseEntity.ok(storage);
    }

    @GetMapping("/dashboard/personal")
    public ResponseEntity<PersonalDashboardSummaryDto> getPersonalDashboardSummary(
            @CurrentUser UserEntity currentUser) {
        PersonalDashboardSummaryDto summary = analyticsService.getPersonalDashboardSummary(currentUser);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/dashboard/organization")
    public ResponseEntity<OrganizationDashboardSummaryDto> getOrganizationDashboardSummary(
            @RequestParam(name = "orgCode") String orgCode,
            @CurrentUser UserEntity currentUser) {
        OrganizationDashboardSummaryDto summary = analyticsService.getOrganizationDashboardSummary(currentUser, orgCode);
        return ResponseEntity.ok(summary);
    }
}

