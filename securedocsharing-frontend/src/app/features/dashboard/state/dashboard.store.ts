import { Injectable, inject, signal, computed, effect, untracked } from '@angular/core';
import { Observable, of, throwError, Subject } from 'rxjs';
import { catchError, tap, takeUntil, finalize, shareReplay } from 'rxjs/operators';
import { EChartsOption } from 'echarts';
import { DashboardApiService } from '../services/dashboard-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import {
  PersonalDashboardSummary,
  OrganizationDashboardSummary,
  DashboardScope,
  StorageGaugeViewModel,
  ActivityTrendViewModel,
  ContentDistributionItem,
  DailyActivitySummary,
} from '../models/dashboard.model';
import {
  buildStorageGaugeOptions,
  buildActivityTrendOptions,
  buildActivityBarChartOptions,
  buildSegmentedHalfGaugeOptions,
  DASHBOARD_COLORS,
} from '../utils/dashboard-chart.util';

/**
 * DashboardStore
 *
 * Signal-based state management for the SecureDocSharing Dashboard domain.
 * Key responsibilities:
 * - Automatically reacts to Personal <-> Organization context changes via WorkspaceContextStore.
 * - Enforces strict state isolation between Personal and Organization metrics.
 * - Prevents race conditions during fast context switching via request sequence tracking and cancellation.
 * - Deduplicates in-flight HTTP requests during navigation transitions.
 * - Manages loading, refreshing, error, and empty states.
 * - Computes chart view models and ECharts options (keeps page component presentation-only).
 * - Treats WORKSPACE scope as explicitly unsupported without mock data or misleading fallbacks.
 */
@Injectable({ providedIn: 'root' })
export class DashboardStore {
  private readonly api = inject(DashboardApiService);
  readonly workspaceContext = inject(WorkspaceContextStore);

  // ── Request Race & Deduplication State ─────────────────────────────────────
  private activeRequestId = 0;
  private inFlightTargetKey: string | null = null;
  private inFlight$: Observable<unknown> | null = null;
  private readonly cancelPendingRequests$ = new Subject<void>();

  // ── State Signals ──────────────────────────────────────────────────────────
  readonly scope = signal<DashboardScope>('PERSONAL');
  readonly personalSummary = signal<PersonalDashboardSummary | null>(null);
  readonly organizationSummary = signal<OrganizationDashboardSummary | null>(null);

  readonly isLoading = signal<boolean>(false);
  readonly isRefreshing = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly lastLoadedAt = signal<number | null>(null);

  /**
   * Control flag to allow pausing auto-sync in unit tests or specific interactive workflows.
   */
  readonly autoSyncEnabled = signal<boolean>(true);

  // ── Computed Context Signals ───────────────────────────────────────────────
  readonly isPersonal = computed<boolean>(() => this.scope() === 'PERSONAL');
  readonly isOrganization = computed<boolean>(() => this.scope() === 'ORGANIZATION');
  readonly isWorkspace = computed<boolean>(() => this.scope() === 'WORKSPACE');
  readonly isUnsupportedScope = computed<boolean>(() => this.scope() === 'WORKSPACE');

  readonly activeOrgCode = computed<string | null>(() => {
    if (this.scope() === 'ORGANIZATION') {
      return this.workspaceContext.activeOrgCode() ?? this.organizationSummary()?.orgCode ?? null;
    }
    return null;
  });

  readonly activeSummary = computed<PersonalDashboardSummary | OrganizationDashboardSummary | null>(() => {
    if (this.isPersonal()) {
      return this.personalSummary();
    }
    if (this.isOrganization()) {
      return this.organizationSummary();
    }
    // WORKSPACE scope is explicitly unsupported; returns null without falling back to Organization
    return null;
  });

  readonly hasData = computed<boolean>(() => !this.isWorkspace() && !!this.activeSummary());

  /**
   * Evaluates if the current active space is completely empty (no documents, folders, or workspaces).
   */
  readonly isEmpty = computed<boolean>(() => {
    if (this.isWorkspace()) {
      return false; // Unsupported workspace scope is not an "empty" space
    }
    const summary = this.activeSummary();
    if (!summary) {
      return false;
    }
    if (this.isPersonal()) {
      const p = summary as PersonalDashboardSummary;
      return p.documentCount === 0 && p.folderCount === 0 && p.usedStorageBytes === 0;
    }
    if (this.isOrganization()) {
      const o = summary as OrganizationDashboardSummary;
      return o.documentCount === 0 && o.folderCount === 0 && o.workspaceCount === 0;
    }
    return false;
  });

  // ── Computed Chart ViewModels & ECharts Options ────────────────────────────
  readonly storageGaugeViewModel = computed<StorageGaugeViewModel | null>(() => {
    if (this.isWorkspace()) {
      return null;
    }
    const summary = this.activeSummary();
    if (!summary) {
      return null;
    }

    const percentage = summary.storageUsagePercentage || 0;
    const isHigh = percentage >= 90;
    const limitBytes = summary.storageLimitBytes || 0;
    const formattedLimit = limitBytes >= 1073741824
      ? `${(limitBytes / 1073741824).toFixed(0)} GB`
      : `${(limitBytes / 1048576).toFixed(0)} MB`;

    return {
      percentage,
      usedBytes: summary.usedStorageBytes,
      limitBytes: summary.storageLimitBytes,
      formattedUsed: summary.usedStorageFormatted || '0 B',
      formattedLimit,
      accentColor: isHigh ? DASHBOARD_COLORS.warningRose : DASHBOARD_COLORS.primaryAmber,
      trackColor: DASHBOARD_COLORS.lightTrack,
    };
  });

  readonly storageGaugeOptions = computed<EChartsOption>(() => {
    if (this.isWorkspace()) {
      return {};
    }
    const vm = this.storageGaugeViewModel();
    if (!vm) {
      return buildStorageGaugeOptions(0, '0 B', true);
    }
    return buildStorageGaugeOptions(vm.percentage, vm.formattedUsed, true);
  });

  readonly activityTrendViewModel = computed<ActivityTrendViewModel | null>(() => {
    if (this.isWorkspace()) {
      return null;
    }
    const summary = this.activeSummary();
    if (!summary || !summary.recentActivityTrend) {
      return null;
    }
    const trend = summary.recentActivityTrend;
    const dates = trend.map((t) => t.date);
    const counts = trend.map((t) => t.count);
    const totalActions = counts.reduce((acc, curr) => acc + curr, 0);
    const peakCount = counts.length > 0 ? Math.max(...counts) : 0;

    return { dates, counts, totalActions, peakCount };
  });

  readonly activityTrendOptions = computed<EChartsOption>(() => {
    if (this.isWorkspace()) {
      return {};
    }
    const summary = this.activeSummary();
    const trend = summary?.recentActivityTrend ?? [];
    return buildActivityTrendOptions(trend, true);
  });

  readonly activityBarChartOptions = computed<EChartsOption>(() => {
    if (this.isWorkspace()) {
      return {};
    }
    const summary = this.activeSummary();
    const trend = summary?.recentActivityTrend ?? [];
    return buildActivityBarChartOptions(trend, true);
  });

  readonly segmentedHalfGaugeOptions = computed<EChartsOption>(() => {
    if (this.isWorkspace()) {
      return {};
    }
    const summary = this.activeSummary();
    const percentage = summary?.storageUsagePercentage ?? 0;
    return buildSegmentedHalfGaugeOptions(percentage, 'Capacity allocated', true);
  });

  readonly contentDistribution = computed<ContentDistributionItem[]>(() => {
    if (this.isWorkspace()) {
      return [];
    }
    const summary = this.activeSummary();
    if (!summary) {
      return [];
    }

    if (this.isPersonal()) {
      const p = summary as PersonalDashboardSummary;
      const docs = p.documentCount || 0;
      const folders = p.folderCount || 0;
      const shares = p.activeDirectSharesCount || 0;
      const total = docs + folders + shares;

      if (total === 0) {
        return [];
      }

      return [
        { label: 'Documents', count: docs, percentage: Math.round((docs / total) * 100), color: '#f97316' },
        { label: 'Folders', count: folders, percentage: Math.round((folders / total) * 100), color: '#0f172a' },
        { label: 'Direct Shares', count: shares, percentage: Math.round((shares / total) * 100), color: '#10b981' },
      ];
    }

    if (this.isOrganization()) {
      const o = summary as OrganizationDashboardSummary;
      const workspaces = o.workspaceCount || 0;
      const docs = o.documentCount || 0;
      const teams = o.teamCount || 0;
      const members = o.memberCount || 0;
      const folders = o.folderCount || 0;
      const total = workspaces + docs + teams + members + folders;

      if (total === 0) {
        return [];
      }

      return [
        { label: 'Documents', count: docs, percentage: Math.round((docs / total) * 100), color: '#f97316' },
        { label: 'Workspaces', count: workspaces, percentage: Math.round((workspaces / total) * 100), color: '#0f172a' },
        { label: 'Members', count: members, percentage: Math.round((members / total) * 100), color: '#0284c7' },
        { label: 'Teams', count: teams, percentage: Math.round((teams / total) * 100), color: '#10b981' },
        { label: 'Folders', count: folders, percentage: Math.round((folders / total) * 100), color: '#64748b' },
      ];
    }

    return [];
  });

  readonly recentActivityLedger = computed<DailyActivitySummary[]>(() => {
    if (this.isWorkspace()) {
      return [];
    }
    const summary = this.activeSummary();
    if (!summary || !summary.recentActivityTrend) {
      return [];
    }
    return [...summary.recentActivityTrend].reverse().slice(0, 5);
  });

  constructor() {
    // Automatically react to Personal <-> Organization context changes from WorkspaceContextStore
    effect(() => {
      // Track external context changes ONLY
      const isPersonal = this.workspaceContext.isPersonal();
      const orgCode = this.workspaceContext.activeOrgCode();

      untracked(() => {
        if (!this.autoSyncEnabled()) {
          return;
        }

        if (isPersonal) {
          if (this.scope() !== 'PERSONAL') {
            this.scope.set('PERSONAL');
          }
          const isAlreadyLoaded = !!this.personalSummary();
          const isInFlight = this.inFlightTargetKey === 'PERSONAL';
          if (!isAlreadyLoaded && !isInFlight) {
            this.loadPersonalSummary().subscribe();
          }
        } else if (orgCode && orgCode.trim()) {
          const cleanCode = orgCode.trim();
          if (this.scope() !== 'ORGANIZATION') {
            this.scope.set('ORGANIZATION');
          }
          const isAlreadyLoaded = this.organizationSummary()?.orgCode === cleanCode;
          const isInFlight = this.inFlightTargetKey === `ORGANIZATION:${cleanCode}`;
          if (!isAlreadyLoaded && !isInFlight) {
            this.loadOrganizationSummary(cleanCode).subscribe();
          }
        }
      });
    });
  }

  // ── Actions ────────────────────────────────────────────────────────────────

  /**
   * Loads dashboard metrics for the current active context.
   *
   * @param forceRefresh If true, ignores cache and marks isRefreshing
   */
  loadCurrentContext(forceRefresh = false): Observable<unknown> {
    if (this.isPersonal()) {
      return this.loadPersonalSummary(forceRefresh);
    }
    if (this.isOrganization()) {
      const orgCode = this.workspaceContext.activeOrgCode();
      if (orgCode) {
        return this.loadOrganizationSummary(orgCode, forceRefresh);
      }
    }
    if (this.isWorkspace()) {
      this.error.set('Workspace dashboard is not supported yet');
      return of(null);
    }
    return of(null);
  }

  /**
   * Fetches personal dashboard metrics.
   * Guarantees race safety and duplicate-request deduplication.
   */
  loadPersonalSummary(forceRefresh = false): Observable<PersonalDashboardSummary | null> {
    if (!forceRefresh && this.personalSummary()) {
      return of(this.personalSummary());
    }

    const targetKey = 'PERSONAL';
    if (!forceRefresh && this.inFlightTargetKey === targetKey && this.inFlight$) {
      return this.inFlight$ as Observable<PersonalDashboardSummary | null>;
    }

    // Cancel any previous in-flight requests for other contexts
    this.cancelPendingRequests();

    const reqId = ++this.activeRequestId;
    this.inFlightTargetKey = targetKey;

    if (forceRefresh) {
      this.isRefreshing.set(true);
    } else {
      this.isLoading.set(true);
    }
    this.error.set(null);

    const req$ = this.api.getPersonalSummary().pipe(
      takeUntil(this.cancelPendingRequests$),
      tap((summary) => {
        // Guard against stale response if another request has started or context switched
        if (reqId !== this.activeRequestId || this.scope() !== 'PERSONAL') {
          return;
        }
        this.personalSummary.set(summary);
        this.lastLoadedAt.set(Date.now());
        this.isLoading.set(false);
        this.isRefreshing.set(false);
      }),
      catchError((err) => {
        if (reqId !== this.activeRequestId || this.scope() !== 'PERSONAL') {
          return of(null);
        }
        console.error('Failed to load personal dashboard summary:', err);
        const errMsg = err?.error?.message || err?.message || 'Failed to load personal dashboard';
        this.error.set(errMsg);
        this.isLoading.set(false);
        this.isRefreshing.set(false);
        return of(null);
      }),
      finalize(() => {
        if (this.inFlightTargetKey === targetKey) {
          this.inFlightTargetKey = null;
          this.inFlight$ = null;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );

    this.inFlight$ = req$;
    return req$;
  }

  /**
   * Fetches organization dashboard metrics for the specified organization.
   * Guarantees race safety and duplicate-request deduplication.
   */
  loadOrganizationSummary(orgCode: string, forceRefresh = false): Observable<OrganizationDashboardSummary | null> {
    if (!orgCode || !orgCode.trim()) {
      this.error.set('Organization code is required');
      return of(null);
    }

    const cleanCode = orgCode.trim();
    if (!forceRefresh && this.organizationSummary()?.orgCode === cleanCode) {
      return of(this.organizationSummary());
    }

    const targetKey = `ORGANIZATION:${cleanCode}`;
    if (!forceRefresh && this.inFlightTargetKey === targetKey && this.inFlight$) {
      return this.inFlight$ as Observable<OrganizationDashboardSummary | null>;
    }

    // Cancel any previous in-flight requests for other contexts
    this.cancelPendingRequests();

    const reqId = ++this.activeRequestId;
    this.inFlightTargetKey = targetKey;

    // If switching to a different organization, clear current organization summary so stale data isn't displayed
    if (this.organizationSummary()?.orgCode !== cleanCode) {
      this.organizationSummary.set(null);
    }

    if (forceRefresh) {
      this.isRefreshing.set(true);
    } else {
      this.isLoading.set(true);
    }
    this.error.set(null);

    const req$ = this.api.getOrganizationSummary(cleanCode).pipe(
      takeUntil(this.cancelPendingRequests$),
      tap((summary) => {
        // Guard against stale response if another request has started or context switched
        if (
          reqId !== this.activeRequestId ||
          this.scope() !== 'ORGANIZATION' ||
          (this.workspaceContext.activeOrgCode() && this.workspaceContext.activeOrgCode() !== cleanCode)
        ) {
          return;
        }
        this.organizationSummary.set(summary);
        this.lastLoadedAt.set(Date.now());
        this.isLoading.set(false);
        this.isRefreshing.set(false);
      }),
      catchError((err) => {
        if (
          reqId !== this.activeRequestId ||
          this.scope() !== 'ORGANIZATION' ||
          (this.workspaceContext.activeOrgCode() && this.workspaceContext.activeOrgCode() !== cleanCode)
        ) {
          return of(null);
        }
        console.error(`Failed to load dashboard summary for org "${cleanCode}":`, err);
        const errMsg = err?.error?.message || err?.message || `Failed to load dashboard for organization ${cleanCode}`;
        this.error.set(errMsg);
        this.isLoading.set(false);
        this.isRefreshing.set(false);
        return of(null);
      }),
      finalize(() => {
        if (this.inFlightTargetKey === targetKey) {
          this.inFlightTargetKey = null;
          this.inFlight$ = null;
        }
      }),
      shareReplay({ bufferSize: 1, refCount: false })
    );

    this.inFlight$ = req$;
    return req$;
  }

  /**
   * Explicit user-triggered refresh of the current dashboard context.
   */
  refresh(): void {
    this.loadCurrentContext(true).subscribe();
  }

  /**
   * Resets all store state.
   */
  reset(): void {
    this.cancelPendingRequests();
    this.scope.set('PERSONAL');
    this.personalSummary.set(null);
    this.organizationSummary.set(null);
    this.isLoading.set(false);
    this.isRefreshing.set(false);
    this.error.set(null);
    this.lastLoadedAt.set(null);
  }

  /**
   * Prepares the store for the future WORKSPACE context scope.
   * Explicitly marks the scope as unsupported without falling back to Organization
   * or exposing misleading dashboard data.
   */
  prepareWorkspaceScope(workspaceId: number): void {
    this.cancelPendingRequests();
    this.scope.set('WORKSPACE');
    this.isLoading.set(false);
    this.isRefreshing.set(false);
    this.error.set('Workspace dashboard is not supported yet');
  }

  private cancelPendingRequests(): void {
    this.cancelPendingRequests$.next();
    this.inFlightTargetKey = null;
    this.inFlight$ = null;
  }
}

