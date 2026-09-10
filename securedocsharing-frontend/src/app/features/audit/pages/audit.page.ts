import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  OnDestroy,
  inject,
  computed,
  signal,
  effect,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuditStore } from '../state/audit.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { AuditScopeType, AuditViewType, AuditCategory } from '../../../core/models/unified-audit.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';

import { AuditViewTabsComponent } from '../components/audit-view-tabs.component';
import { AuditFilterBarComponent } from '../components/audit-filter-bar.component';
import { AuditTableComponent } from '../components/audit-table.component';
import { AuditSkeletonComponent } from '../components/audit-skeleton.component';
import { AuditEmptyStateComponent } from '../components/audit-empty-state.component';
import { AuditAccessDeniedComponent } from '../components/audit-access-denied.component';
import { AuditEventDetailDrawerComponent } from '../components/audit-event-detail-drawer.component';

@Component({
  selector: 'app-audit-page',
  standalone: true,
  imports: [
    CommonModule,
    IconComponent,
    AuditViewTabsComponent,
    AuditFilterBarComponent,
    AuditTableComponent,
    AuditSkeletonComponent,
    AuditEmptyStateComponent,
    AuditAccessDeniedComponent,
    AuditEventDetailDrawerComponent,
  ],
  providers: [AuditStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="audit-page-container">
      <!-- ── Header ────────────────────────────────────────────── -->
      <header class="page-header">
        <div class="header-main">
          <div class="header-titles">
            <div class="header-eyebrow">
              <span class="eyebrow-dot"></span>
              <span>{{ eyebrowText() }}</span>
            </div>
            <div class="title-with-icon">
              <div class="icon-badge">
                <app-icon name="clipboard-list" [size]="22" class="icon-badge-svg" />
              </div>
              <div>
                <h1 class="page-title">{{ pageTitle() }}</h1>
                <p class="page-subtitle">{{ pageSubtitle() }}</p>
              </div>
            </div>
          </div>

          <div class="header-actions">
            <button
              type="button"
              class="btn-refresh"
              (click)="onRefresh()"
              [disabled]="store.isRefreshing() || store.isLoading()"
              title="Refresh audit trail"
            >
              <app-icon
                name="refresh-cw"
                [size]="15"
                [class]="store.isRefreshing() ? 'spin' : ''"
              />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        <!-- Segmented View Tabs (Organization Scope Only) -->
        @if (resolvedScope() === 'ORGANIZATION') {
          <div class="view-tabs-row">
            <app-audit-view-tabs
              [activeView]="store.activeView()"
              (viewChange)="onViewChange($event)"
            />
          </div>
        }
      </header>

      <!-- ── Filter & Search Bar ───────────────────────────────── -->
      @if (!store.isAccessDenied()) {
        <div class="filter-section">
          <app-audit-filter-bar
            [scopeType]="resolvedScope()"
            [hasActiveFilters]="store.hasActiveFilters()"
            [initialSearch]="store.criteria().search || ''"
            [initialCategory]="store.criteria().category"
            [initialActor]="store.criteria().actor || ''"
            (filtersChange)="onFiltersChange($event)"
            (resetFilters)="onResetFilters()"
          />
        </div>
      }

      <!-- ── Content Surface ───────────────────────────────────── -->
      <main class="content-surface">
        @if (store.isAccessDenied()) {
          <app-audit-access-denied [message]="store.error()" />
        } @else if (store.isLoading() && !store.isRefreshing()) {
          <app-audit-skeleton [count]="8" />
        } @else if (store.error()) {
          <div class="error-banner" role="alert">
            <app-icon name="alert-circle" [size]="18" class="text-rose-600" />
            <div class="error-text">
              <span class="error-title">Failed to load audit events</span>
              <p class="error-desc">{{ store.error() }}</p>
            </div>
            <button type="button" class="btn-retry" (click)="onRefresh()">
              <span>Try Again</span>
            </button>
          </div>
        } @else if (!store.hasEvents()) {
          <app-audit-empty-state
            [hasActiveFilters]="store.hasActiveFilters()"
            (clearFilters)="onResetFilters()"
          />
        } @else {
          <app-audit-table
            [events]="store.events()"
            [currentPage]="store.page()"
            [pageSize]="store.size()"
            [totalElements]="store.totalElements()"
            [totalPages]="store.totalPages()"
            (rowClick)="store.openDetail($event)"
            (pageChange)="store.setPage($event)"
            (pageSizeChange)="store.setPageSize($event)"
          />
        }
      </main>

      <!-- ── Event Detail Drawer ───────────────────────────────── -->
      <app-audit-event-detail-drawer
        [isOpen]="store.isDrawerOpen()"
        [event]="store.selectedEvent()"
        (close)="store.closeDetail()"
      />
    </div>
  `,
  styles: [
    `
      .audit-page-container {
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
        padding: 1.5rem 2rem;
        max-width: 1440px;
        margin: 0 auto;
        width: 100%;
      }

      .page-header {
        display: flex;
        flex-direction: column;
        gap: 1rem;
      }

      .header-main {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 1.5rem;
      }

      .header-titles {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }

      .header-eyebrow {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.08em;
        color: #f97316;
      }

      .eyebrow-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #f97316;
      }

      .title-with-icon {
        display: flex;
        align-items: center;
        gap: 0.85rem;
      }

      .icon-badge {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 42px;
        height: 42px;
        border-radius: 8px;
        background: #fff7ed;
        border: 1px solid #fed7aa;
        color: #ea580c;
        flex-shrink: 0;
      }

      .page-title {
        font-size: 1.35rem;
        font-weight: 700;
        color: #0f172a;
        margin: 0;
        letter-spacing: -0.01em;
      }

      .page-subtitle {
        font-size: 0.8125rem;
        color: #64748b;
        margin: 0.15rem 0 0 0;
      }

      .header-actions {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }

      .btn-refresh {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.45rem 0.85rem;
        background: #ffffff;
        color: #475569;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover:not(:disabled) {
          background: #f8fafc;
          color: #0f172a;
          border-color: #94a3b8;
        }

        &:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }
      }

      .spin {
        animation: spin 1s linear infinite;
      }

      @keyframes spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      .view-tabs-row {
        margin-top: 0.25rem;
      }

      .filter-section {
        margin-top: 0.25rem;
      }

      .content-surface {
        display: flex;
        flex-direction: column;
      }

      .error-banner {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 8px;
        padding: 1rem 1.25rem;
        color: #991b1b;
      }

      .error-text {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }

      .error-title {
        font-weight: 600;
        font-size: 0.875rem;
      }

      .error-desc {
        font-size: 0.8125rem;
        color: #b91c1c;
        margin: 0;
      }

      .btn-retry {
        display: inline-flex;
        align-items: center;
        padding: 0.4rem 0.8rem;
        background: #ffffff;
        border: 1px solid #fca5a5;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        color: #b91c1c;
        cursor: pointer;

        &:hover {
          background: #fff5f5;
        }
      }
    `,
  ],
})
export class AuditPageComponent implements OnInit, OnDestroy {
  readonly store = inject(AuditStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);

  private routeSub?: Subscription;

  // ── Context Resolvers ──
  readonly resolvedScope = signal<AuditScopeType>('PERSONAL');
  readonly currentOrgCode = signal<string | undefined>(undefined);
  readonly currentWorkspaceId = signal<number | undefined>(undefined);

  readonly eyebrowText = computed(() => {
    switch (this.resolvedScope()) {
      case 'ORGANIZATION':
        return 'GOVERNANCE & COMPLIANCE';
      case 'WORKSPACE':
        return 'WORKSPACE AUDIT';
      default:
        return 'PERSONAL ACTIVITY';
    }
  });

  readonly pageTitle = computed(() => {
    switch (this.resolvedScope()) {
      case 'ORGANIZATION':
        return `Organization Audit Trail — ${this.currentOrgCode() || 'Organization'}`;
      case 'WORKSPACE':
        return 'Workspace Audit Trail';
      default:
        return 'Personal Audit Trail';
    }
  });

  readonly pageSubtitle = computed(() => {
    switch (this.resolvedScope()) {
      case 'ORGANIZATION':
        return 'Enterprise-wide activity log, access governance, and document telemetry across this organization.';
      case 'WORKSPACE':
        return 'Audited document activity, team workspace allocations, and member access grants in this workspace.';
      default:
        return 'Track your personal document uploads, downloads, sharing links, and permission requests.';
    }
  });

  ngOnInit(): void {
    this.routeSub = this.route.paramMap.subscribe(() => {
      this.resolveScopeAndLoad();
    });
  }

  ngOnDestroy(): void {
    this.routeSub?.unsubscribe();
  }

  onRefresh(): void {
    this.store.loadEvents(true);
  }

  onViewChange(view: AuditViewType): void {
    this.store.setView(view);
  }

  onFiltersChange(filters: {
    category?: AuditCategory;
    actor?: string;
    search?: string;
    from?: string;
    to?: string;
    includeDescendants?: boolean;
  }): void {
    this.store.updateFilters(filters);
  }

  onResetFilters(): void {
    this.store.clearFilters();
  }

  private resolveScopeAndLoad(): void {
    // 1. Inspect route hierarchy for orgCode and workspaceId
    let r: ActivatedRoute | null = this.route;
    let orgCode: string | undefined;
    let workspaceIdStr: string | undefined;

    while (r) {
      if (!orgCode && r.snapshot.paramMap.has('orgCode')) {
        orgCode = r.snapshot.paramMap.get('orgCode') || undefined;
      }
      if (!workspaceIdStr && r.snapshot.paramMap.has('workspaceId')) {
        workspaceIdStr = r.snapshot.paramMap.get('workspaceId') || undefined;
      }
      r = r.parent;
    }

    const wsNumericId = workspaceIdStr && !isNaN(+workspaceIdStr) ? +workspaceIdStr : undefined;

    let scopeType: AuditScopeType = 'PERSONAL';
    if (wsNumericId) {
      scopeType = 'WORKSPACE';
    } else if (orgCode) {
      scopeType = 'ORGANIZATION';
    } else {
      scopeType = 'PERSONAL';
    }

    this.resolvedScope.set(scopeType);
    this.currentOrgCode.set(orgCode);
    this.currentWorkspaceId.set(wsNumericId);

    // 2. Initialize AuditStore
    this.store.setScopeAndContainer(scopeType, {
      orgCode,
      workspaceId: wsNumericId,
    });
  }
}
