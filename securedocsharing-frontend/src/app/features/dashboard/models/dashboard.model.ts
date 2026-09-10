import { EChartsOption } from 'echarts';

/**
 * Daily activity volume point for sparkline and trend visualizations.
 */
export interface DailyActivitySummary {
  date: string; // YYYY-MM-DD
  count: number;
  intensity: number; // 0 to 4
}

/**
 * Summary metrics for the Personal Space Dashboard.
 * Matches backend com.subject77.docshare.modules.analytics.dto.PersonalDashboardSummaryDto.
 */
export interface PersonalDashboardSummary {
  usedStorageBytes: number;
  storageLimitBytes: number;
  storageUsagePercentage: number;
  usedStorageFormatted: string;
  documentCount: number;
  folderCount: number;
  pendingAccessReviewsCount: number;
  myPendingAccessRequestsCount: number;
  activeDirectSharesCount: number;
  recentActivityTrend: DailyActivitySummary[];
}

/**
 * Summary metrics for the Organization Space Dashboard.
 * Matches backend com.subject77.docshare.modules.analytics.dto.OrganizationDashboardSummaryDto.
 * NOTE: Subspace terminology is retired; uses workspaceCount exclusively.
 */
export interface OrganizationDashboardSummary {
  orgCode: string;
  orgName: string;
  usedStorageBytes: number;
  storageLimitBytes: number;
  storageUsagePercentage: number;
  usedStorageFormatted: string;
  documentCount: number;
  folderCount: number;
  workspaceCount: number;
  teamCount: number;
  memberCount: number;
  pendingAccessRequestsCount: number;
  pendingInvitationsCount: number;
  recentActivityTrend: DailyActivitySummary[];
}

/**
 * Canonical scope of the dashboard.
 * Prepared for future WORKSPACE context.
 */
export type DashboardScope = 'PERSONAL' | 'ORGANIZATION' | 'WORKSPACE';

/**
 * View model for storage radial/semi-circular gauge card.
 */
export interface StorageGaugeViewModel {
  percentage: number;
  usedBytes: number;
  limitBytes: number;
  formattedUsed: string;
  formattedLimit: string;
  accentColor: string;
  trackColor: string;
}

/**
 * View model for 14-day activity trend chart.
 */
export interface ActivityTrendViewModel {
  dates: string[];
  counts: number[];
  totalActions: number;
  peakCount: number;
}

/**
 * Item in the segmented content/resource distribution visualization.
 */
export interface ContentDistributionItem {
  label: string;
  count: number;
  percentage: number;
  color: string;
}

export interface DashboardState {
  scope: DashboardScope;
  personalSummary: PersonalDashboardSummary | null;
  organizationSummary: OrganizationDashboardSummary | null;
  isLoading: boolean;
  isRefreshing: boolean;
  error: string | null;
  lastLoadedAt: number | null;
}

