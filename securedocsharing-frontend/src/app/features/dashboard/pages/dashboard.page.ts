import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DashboardStore } from '../state/dashboard.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { DashboardKpiCardComponent } from '../components/dashboard-kpi-card.component';
import { DashboardStorageCardComponent } from '../components/dashboard-storage-card.component';
import { DashboardActivityCardComponent } from '../components/dashboard-activity-card.component';
import { DashboardActionCardComponent } from '../components/dashboard-action-card.component';
import { DashboardDistributionCardComponent } from '../components/dashboard-distribution-card.component';
import { DashboardActivityLedgerComponent } from '../components/dashboard-activity-ledger.component';

/**
 * DashboardPageComponent
 *
 * Primary enterprise dashboard for SecureDocSharing.
 * Dynamically adapts to Personal Space vs Organization context.
 * Consumes DashboardStore and presents telemetry, storage consumption, and governance actions.
 */
@Component({
  selector: 'app-dashboard-page',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    IconComponent,
    DashboardKpiCardComponent,
    DashboardStorageCardComponent,
    DashboardActivityCardComponent,
    DashboardActionCardComponent,
    DashboardDistributionCardComponent,
    DashboardActivityLedgerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="max-w-7xl mx-auto space-y-6 pb-12">
      <!-- ── 1. Page Header ────────────────────────────────────────────────── -->
      <header class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-200/80">
        <div class="space-y-1">
          <div class="flex items-center gap-2.5">
            <!-- Context Scope Pill -->
            <span
              class="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold"
              [ngClass]="contextPillClasses()"
            >
              <app-icon [name]="isPersonal() ? 'user' : 'building-2'" [size]="13" />
              <span>{{ contextPillText() }}</span>
            </span>

            @if (isRefreshing()) {
              <span class="inline-flex items-center gap-1 text-xs text-amber-600 animate-pulse font-medium">
                <app-icon name="refresh-cw" [size]="12" class="animate-spin" />
                <span>Syncing metrics…</span>
              </span>
            }
          </div>

          <h1 class="text-2xl lg:text-3xl font-bold tracking-tight text-slate-900">
            {{ pageTitle() }}
          </h1>
          <p class="text-sm text-slate-500">
            {{ pageSubtitle() }}
          </p>
        </div>

        <!-- Controls: Refresh & Timestamp -->
        <div class="flex items-center gap-3 self-start sm:self-auto">
          @if (lastUpdatedFormatted()) {
            <span class="text-xs text-slate-400 hidden md:inline-block">
              Updated {{ lastUpdatedFormatted() }}
            </span>
          }

          <button
            type="button"
            (click)="onRefresh()"
            [disabled]="isLoading() || isRefreshing()"
            class="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl text-xs font-semibold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 hover:border-slate-300 disabled:opacity-50 transition-all shadow-2xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
            title="Refresh dashboard metrics"
            aria-label="Refresh metrics"
          >
            <app-icon
              name="refresh-cw"
              [size]="14"
              [class]="isRefreshing() ? 'animate-spin text-amber-600' : 'text-slate-500'"
            />
            <span>Refresh</span>
          </button>
        </div>
      </header>

      <!-- ── 2. State Renderers: Unsupported, Loading, Error ─────────────────── -->
      @if (isUnsupportedScope()) {
        <!-- Unsupported Scope Notice (Workspace) -->
        <div class="p-8 rounded-2xl bg-white border border-slate-200 shadow-xs text-center max-w-lg mx-auto my-12">
          <div class="w-12 h-12 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-4 border border-amber-100">
            <app-icon name="shield" [size]="24" />
          </div>
          <h2 class="text-lg font-bold text-slate-900">Workspace Dashboard Unavailable</h2>
          <p class="text-sm text-slate-500 mt-2">
            Dashboard aggregation is available at the <strong>Personal Space</strong> and <strong>Organization Space</strong> levels. Workspace-level aggregation is not currently enabled.
          </p>
          <div class="mt-6 flex items-center justify-center gap-3">
            @if (activeOrgCode()) {
              <a
                [routerLink]="['/org', activeOrgCode(), 'dashboard']"
                class="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-xs"
              >
                Go to Organization Dashboard
              </a>
            } @else {
              <a
                routerLink="/w/personal/dashboard"
                class="px-4 py-2 rounded-xl text-xs font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-xs"
              >
                Go to Personal Dashboard
              </a>
            }
          </div>
        </div>
      } @else if (isLoading() && !hasData()) {
        <!-- Skeleton Loading State -->
        <div class="space-y-6 animate-pulse" aria-busy="true" aria-label="Loading dashboard">
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            @for (i of [1, 2, 3, 4]; track i) {
              <div class="h-32 rounded-2xl bg-slate-100 border border-slate-200/60 p-5">
                <div class="w-10 h-10 rounded-xl bg-slate-200 mb-4"></div>
                <div class="w-24 h-7 bg-slate-200 rounded-md mb-2"></div>
                <div class="w-16 h-3 bg-slate-200 rounded-md"></div>
              </div>
            }
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div class="lg:col-span-7 xl:col-span-8 h-80 rounded-2xl bg-slate-100 border border-slate-200/60"></div>
            <div class="lg:col-span-5 xl:col-span-4 h-80 rounded-2xl bg-slate-100 border border-slate-200/60"></div>
          </div>
        </div>
      } @else if (error() && !hasData()) {
        <!-- Error State -->
        <div class="p-8 rounded-2xl bg-rose-50/70 border border-rose-200 text-center max-w-lg mx-auto my-12">
          <div class="w-12 h-12 rounded-2xl bg-rose-100 text-rose-600 flex items-center justify-center mx-auto mb-4 border border-rose-200">
            <app-icon name="alert-circle" [size]="24" />
          </div>
          <h2 class="text-lg font-bold text-rose-900">Failed to Load Dashboard</h2>
          <p class="text-sm text-rose-700 mt-2">
            {{ error() }}
          </p>
          <button
            type="button"
            (click)="onRefresh()"
            class="mt-5 px-4 py-2 rounded-xl text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 transition-colors shadow-xs focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
          >
            Retry Loading
          </button>
        </div>
      } @else if (isEmpty()) {
        <!-- Genuinely Empty Space -->
        <div class="p-12 rounded-2xl bg-white border border-slate-200/80 shadow-xs text-center max-w-lg mx-auto my-12">
          <div class="w-14 h-14 rounded-2xl bg-amber-50 text-amber-600 flex items-center justify-center mx-auto mb-4 border border-amber-100">
            <app-icon name="folder-open" [size]="28" />
          </div>
          <h2 class="text-xl font-bold text-slate-900">
            {{ isPersonal() ? 'Your Personal Space is Empty' : 'No Content in Organization' }}
          </h2>
          <p class="text-sm text-slate-500 mt-2">
            {{ isPersonal() ? 'Upload documents or create folders to start managing personal files securely.' : 'Create workspaces and upload organization documents to begin collaborating.' }}
          </p>
          <div class="mt-6">
            <a
              [routerLink]="isPersonal() ? '/w/personal/explorer' : ['/org', activeOrgCode(), 'explorer']"
              class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-semibold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-xs"
            >
              <app-icon name="folder" [size]="15" />
              <span>Go to File Explorer</span>
            </a>
          </div>
        </div>
      } @else {
        <!-- ── 3. Active Dashboard Layout ──────────────────────────────────────── -->

        <!-- Action-Required Banner (If pending review actions exist) -->
        @if (showActionRequiredBanner()) {
          <div
            class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-4 rounded-2xl bg-gradient-to-r from-amber-50 via-orange-50/60 to-white border border-amber-200/80 shadow-2xs"
            role="alert"
          >
            <div class="flex items-center gap-3">
              <div class="w-9 h-9 rounded-xl bg-amber-500 text-white flex items-center justify-center shrink-0 shadow-xs">
                <app-icon name="key" [size]="18" />
              </div>
              <div>
                <span class="text-xs font-bold text-amber-950 uppercase tracking-wider block">
                  Action Required
                </span>
                <p class="text-sm font-medium text-amber-900">
                  {{ actionRequiredBannerMessage() }}
                </p>
              </div>
            </div>

            <a
              [routerLink]="actionRequiredBannerRoute()"
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold text-white bg-amber-600 hover:bg-amber-700 transition-colors shadow-xs self-start sm:self-auto shrink-0"
            >
              <span>Review Now</span>
              <app-icon name="arrow-right" [size]="14" />
            </a>
          </div>
        }

        <!-- ── 4. Primary KPI Metric Cards Grid ───────────────────────────────── -->
        <section aria-label="Key Performance Indicators">
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            @if (isPersonal()) {
              <!-- Personal KPIs -->
              <app-dashboard-kpi-card
                title="Total Documents"
                [value]="personalSummary()?.documentCount ?? 0"
                icon="file-text"
                badgeText="Personal"
                badgeVariant="slate"
                subtext="Encrypted & indexed files"
                routerLink="/w/personal/explorer"
                iconTone="amber"
              />
              <app-dashboard-kpi-card
                title="Folder Hierarchy"
                [value]="personalSummary()?.folderCount ?? 0"
                icon="folder"
                badgeText="Directories"
                badgeVariant="slate"
                subtext="Personal folder structure"
                routerLink="/w/personal/explorer"
                iconTone="slate"
              />
              <app-dashboard-kpi-card
                title="Direct Shares"
                [value]="personalSummary()?.activeDirectSharesCount ?? 0"
                icon="share-2"
                badgeText="Active"
                badgeVariant="emerald"
                subtext="Documents shared with peers"
                routerLink="/w/personal/shared-by-me"
                iconTone="emerald"
              />
              <app-dashboard-kpi-card
                title="Access Reviews"
                [value]="personalSummary()?.pendingAccessReviewsCount ?? 0"
                icon="key"
                [badgeText]="(personalSummary()?.pendingAccessReviewsCount ?? 0) > 0 ? 'Action required' : 'Clear'"
                [badgeVariant]="(personalSummary()?.pendingAccessReviewsCount ?? 0) > 0 ? 'amber' : 'emerald'"
                subtext="Incoming approval requests"
                routerLink="/w/personal/access-requests"
                [iconTone]="(personalSummary()?.pendingAccessReviewsCount ?? 0) > 0 ? 'amber' : 'slate'"
              />
            } @else {
              <!-- Organization KPIs -->
              <app-dashboard-kpi-card
                title="Workspaces"
                [value]="organizationSummary()?.workspaceCount ?? 0"
                icon="layers"
                badgeText="Organizational"
                badgeVariant="slate"
                subtext="Document boundaries"
                [routerLink]="['/org', activeOrgCode(), 'explorer']"
                iconTone="amber"
              />
              <app-dashboard-kpi-card
                title="Org Documents"
                [value]="organizationSummary()?.documentCount ?? 0"
                icon="file-text"
                badgeText="Total repository"
                badgeVariant="slate"
                subtext="Across all workspaces"
                [routerLink]="['/org', activeOrgCode(), 'explorer']"
                iconTone="slate"
              />
              <app-dashboard-kpi-card
                title="Teams"
                [value]="organizationSummary()?.teamCount ?? 0"
                icon="users"
                badgeText="Collaboration"
                badgeVariant="emerald"
                subtext="Access-allocated groups"
                [routerLink]="['/org', activeOrgCode(), 'teams']"
                iconTone="emerald"
              />
              <app-dashboard-kpi-card
                title="Members"
                [value]="organizationSummary()?.memberCount ?? 0"
                icon="user-round"
                badgeText="Active users"
                badgeVariant="slate"
                subtext="Authorized personnel"
                [routerLink]="['/org', activeOrgCode(), 'members']"
                iconTone="slate"
              />
            }
          </div>
        </section>

        <!-- ── 5. Detailed Telemetry & Storage Row (Original Layout & Old Chart) ── -->
        <section class="grid grid-cols-1 lg:grid-cols-12 gap-6" aria-label="Detailed Analytics">
          <!-- Left Main Column (Activity + Attention) -->
          <div class="lg:col-span-7 xl:col-span-8 flex flex-col gap-6">
            <!-- 14-Day Activity Velocity Chart Card (Old Chart) -->
            <app-dashboard-activity-card
              [viewModel]="activityTrendViewModel()"
              [options]="activityTrendOptions()"
            />

            <!-- Operational Attention & Quick Actions Card -->
            <app-dashboard-action-card
              [scope]="scope()"
              [personalSummary]="personalSummary()"
              [organizationSummary]="organizationSummary()"
              [orgCode]="activeOrgCode()"
            />
          </div>

          <!-- Right Sidebar Column (Storage Radial Gauge) -->
          <div class="lg:col-span-5 xl:col-span-4 flex flex-col gap-6">
            <app-dashboard-storage-card
              [viewModel]="storageGaugeViewModel()"
              [options]="storageGaugeOptions()"
            />
          </div>
        </section>

        <!-- ── 6. Resource Breakdown & Activity Stream (Added After) ────────── -->
        <section class="grid grid-cols-1 lg:grid-cols-12 gap-6" aria-label="Resource Breakdown and Activity Stream">
          <div class="lg:col-span-6">
            <app-dashboard-distribution-card
              [items]="contentDistribution()"
              [scope]="scope()"
              [options]="segmentedHalfGaugeOptions()"
              [percentage]="storageGaugeViewModel()?.percentage ?? 0"
              [formattedValue]="storageGaugeViewModel()?.formattedUsed ?? '0 B'"
              [formattedLimit]="storageGaugeViewModel()?.formattedLimit ?? 'Quota'"
            />
          </div>

          <div class="lg:col-span-6">
            <app-dashboard-activity-ledger
              [ledger]="recentActivityLedger()"
              [scope]="scope()"
              [orgCode]="activeOrgCode()"
            />
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }
  `],
})
export class DashboardPageComponent {
  private readonly store = inject(DashboardStore);
  readonly workspaceContext = inject(WorkspaceContextStore);

  // ── Bound Store Signals ───────────────────────────────────────────────────
  readonly scope = this.store.scope;
  readonly isPersonal = this.store.isPersonal;
  readonly isOrganization = this.store.isOrganization;
  readonly isWorkspace = this.store.isWorkspace;
  readonly isUnsupportedScope = this.store.isUnsupportedScope;

  readonly isLoading = this.store.isLoading;
  readonly isRefreshing = this.store.isRefreshing;
  readonly error = this.store.error;
  readonly hasData = this.store.hasData;
  readonly isEmpty = this.store.isEmpty;

  readonly personalSummary = this.store.personalSummary;
  readonly organizationSummary = this.store.organizationSummary;
  readonly activeOrgCode = this.store.activeOrgCode;

  readonly storageGaugeViewModel = this.store.storageGaugeViewModel;
  readonly storageGaugeOptions = this.store.storageGaugeOptions;

  readonly activityTrendViewModel = this.store.activityTrendViewModel;
  readonly activityTrendOptions = this.store.activityTrendOptions;
  readonly activityBarChartOptions = this.store.activityBarChartOptions;
  readonly segmentedHalfGaugeOptions = this.store.segmentedHalfGaugeOptions;

  readonly contentDistribution = this.store.contentDistribution;
  readonly recentActivityLedger = this.store.recentActivityLedger;

  // ── Computed Display Titles ───────────────────────────────────────────────
  readonly pageTitle = computed(() => {
    if (this.isPersonal()) {
      return 'Personal Space Overview';
    }
    const orgName = this.organizationSummary()?.orgName;
    if (orgName) {
      return `${orgName} Overview`;
    }
    return 'Organization Overview';
  });

  readonly pageSubtitle = computed(() => {
    if (this.isPersonal()) {
      return 'Personal documents, shared resources, and storage consumption metrics.';
    }
    return 'Organization workspaces, teams, active membership, and compliance telemetry.';
  });

  readonly contextPillText = computed(() => {
    if (this.isPersonal()) {
      return 'Personal Space';
    }
    const org = this.organizationSummary();
    if (org?.orgCode) {
      return `Organization: ${org.orgCode}`;
    }
    return 'Organization Space';
  });

  readonly contextPillClasses = computed(() => {
    if (this.isPersonal()) {
      return 'bg-slate-100 text-slate-700 border border-slate-200/80';
    }
    return 'bg-amber-50 text-amber-800 border border-amber-200/80';
  });

  readonly lastUpdatedFormatted = computed(() => {
    const timestamp = this.store.lastLoadedAt();
    if (!timestamp) return null;
    const date = new Date(timestamp);
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  });

  // ── Priority Action Banner Logic ──────────────────────────────────────────
  readonly showActionRequiredBanner = computed(() => {
    if (this.isPersonal()) {
      return (this.personalSummary()?.pendingAccessReviewsCount || 0) > 0;
    }
    if (this.isOrganization()) {
      return (this.organizationSummary()?.pendingAccessRequestsCount || 0) > 0;
    }
    return false;
  });

  readonly actionRequiredBannerMessage = computed(() => {
    if (this.isPersonal()) {
      const count = this.personalSummary()?.pendingAccessReviewsCount || 0;
      return `${count} access review ${count === 1 ? 'request requires' : 'requests require'} your approval.`;
    }
    if (this.isOrganization()) {
      const count = this.organizationSummary()?.pendingAccessRequestsCount || 0;
      return `${count} organization access ${count === 1 ? 'request awaits' : 'requests await'} administrative review.`;
    }
    return '';
  });

  readonly actionRequiredBannerRoute = computed(() => {
    if (this.isPersonal()) {
      return '/w/personal/access-requests';
    }
    const code = this.activeOrgCode();
    return code ? ['/org', code, 'access-requests'] : '/w/personal/access-requests';
  });

  // ── Actions ───────────────────────────────────────────────────────────────
  onRefresh(): void {
    this.store.refresh();
  }
}
