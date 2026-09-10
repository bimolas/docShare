import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  inject,
  signal,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AccessRequestApiService } from '../../../core/services/access-request-api.service';
import {
  AccessRequestResponseDto,
  AccessRequestStatus,
  AccessPermissionLevel,
} from '../../../core/models/access-request.model';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { NotificationService } from '../../../core/services/notification.service';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { AccessRequestDetailDrawerComponent } from '../components/access-request-detail-drawer.component';

type ActiveTab = 'reviews' | 'my-requests';

@Component({
  selector: 'app-access-requests-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IconComponent,
    AccessRequestDetailDrawerComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="access-requests-container">
      <!-- Page Header -->
      <header class="page-header">
        <div class="header-main">
          <div class="header-titles">
            <div class="header-eyebrow">
              <span class="eyebrow-dot"></span>
              <span>MANAGEMENT & ACCESS</span>
            </div>
            <div class="title-with-icon">
              <div class="icon-badge">
                <app-icon name="key" [size]="22" class="icon-badge-svg" />
              </div>
              <div>
                <h1 class="page-title">Access Requests</h1>
                <p class="page-subtitle">
                  Review pending access requests for your resources or track your submitted permissions.
                </p>
              </div>
            </div>
          </div>

          <div class="header-actions">
            <button
              type="button"
              class="btn-refresh"
              (click)="onRefresh()"
              [disabled]="isRefreshing() || isLoading()"
              title="Refresh requests"
            >
              <app-icon
                name="refresh-cw"
                [size]="15"
                [class]="isRefreshing() ? 'spin' : ''"
              />
              <span>Refresh</span>
            </button>
          </div>
        </div>

        <!-- Segmented Tab Navigation -->
        <div class="tab-strip-row">
          <div class="segmented-control">
            @if (canReview()) {
              <button
                type="button"
                class="segmented-btn"
                [class.active]="activeTab() === 'reviews'"
                (click)="setActiveTab('reviews')"
              >
                <app-icon name="shield-check" [size]="16" />
                <span>Reviewer Inbox</span>
                @if (pendingReviewsCount() > 0) {
                  <span class="pill-badge pill-badge-amber">{{ pendingReviewsCount() }}</span>
                }
              </button>
            }

            <button
              type="button"
              class="segmented-btn"
              [class.active]="activeTab() === 'my-requests'"
              (click)="setActiveTab('my-requests')"
            >
              <app-icon name="inbox" [size]="16" />
              <span>My Requests</span>
              @if (myPendingCount() > 0) {
                <span class="pill-badge pill-badge-neutral">{{ myPendingCount() }}</span>
              }
            </button>
          </div>
        </div>
      </header>

      <!-- Context Notification Banners -->
      @if (!isLoading()) {
        @if (activeTab() === 'reviews' && pendingReviewsCount() > 0) {
          <div class="callout-banner banner-warning">
            <div class="callout-icon-col">
              <app-icon name="shield" [size]="20" />
            </div>
            <div class="callout-text-col">
              <span class="callout-title">Pending Decision Required</span>
              <p class="callout-desc">
                You have {{ pendingReviewsCount() }} pending access request{{ pendingReviewsCount() > 1 ? 's' : '' }} awaiting your decision. Resolving requests updates resource access immediately.
              </p>
            </div>
          </div>
        } @else if (activeTab() === 'my-requests' && myPendingCount() > 0) {
          <div class="callout-banner banner-info">
            <div class="callout-icon-col">
              <app-icon name="clock" [size]="20" />
            </div>
            <div class="callout-text-col">
              <span class="callout-title">Awaiting Review</span>
              <p class="callout-desc">
                You have {{ myPendingCount() }} active request{{ myPendingCount() > 1 ? 's' : '' }} pending reviewer response. You can view progress or cancel anytime before resolution.
              </p>
            </div>
          </div>
        }
      }

      <!-- Main Surface Card -->
      <main class="surface-card">
        <!-- Controls Bar: Search & Status Filters -->
        <div class="controls-bar">
          <!-- Search Input -->
          <div class="search-wrap">
            <app-icon name="search" [size]="15" class="search-icon" />
            <input
              type="text"
              class="search-input"
              placeholder="Search resource, user, or reason..."
              [ngModel]="searchQuery()"
              (ngModelChange)="onSearchChange($event)"
            />
            @if (searchQuery()) {
              <button
                type="button"
                class="search-clear"
                (click)="onSearchChange('')"
                aria-label="Clear search"
              >
                <app-icon name="x" [size]="14" />
              </button>
            }
          </div>

          <!-- Status Filters with Dynamic Counts -->
          <div class="status-filters">
            <button
              type="button"
              class="filter-chip"
              [class.active]="statusFilter() === null"
              (click)="setStatusFilter(null)"
            >
              <span>All</span>
              <span class="chip-count">{{ statusCounts().all }}</span>
            </button>
            <button
              type="button"
              class="filter-chip"
              [class.active]="statusFilter() === 'PENDING'"
              (click)="setStatusFilter('PENDING')"
            >
              <span>Pending</span>
              @if (statusCounts().pending > 0) {
                <span class="chip-count count-amber">{{ statusCounts().pending }}</span>
              }
            </button>
            <button
              type="button"
              class="filter-chip"
              [class.active]="statusFilter() === 'APPROVED'"
              (click)="setStatusFilter('APPROVED')"
            >
              <span>Approved</span>
              <span class="chip-count">{{ statusCounts().approved }}</span>
            </button>
            <button
              type="button"
              class="filter-chip"
              [class.active]="statusFilter() === 'DENIED'"
              (click)="setStatusFilter('DENIED')"
            >
              <span>Denied</span>
              <span class="chip-count">{{ statusCounts().denied }}</span>
            </button>
            @if (activeTab() === 'my-requests') {
              <button
                type="button"
                class="filter-chip"
                [class.active]="statusFilter() === 'CANCELLED'"
                (click)="setStatusFilter('CANCELLED')"
              >
                <span>Cancelled</span>
                <span class="chip-count">{{ statusCounts().cancelled }}</span>
              </button>
            }
          </div>
        </div>

        <!-- Content Area -->
        @if (isLoading()) {
          <!-- Skeleton Loading Rows -->
          <div class="skeleton-container">
            @for (row of [1, 2, 3, 4]; track row) {
              <div class="skeleton-row">
                <div class="skeleton-box skeleton-icon"></div>
                <div class="skeleton-box skeleton-text skeleton-w-md"></div>
                <div class="skeleton-box skeleton-text skeleton-w-sm"></div>
                <div class="skeleton-box skeleton-pill"></div>
                <div class="skeleton-box skeleton-pill"></div>
                <div class="skeleton-box skeleton-btn"></div>
              </div>
            }
          </div>
        } @else if (displayedRequests().length === 0) {
          <!-- Empty State -->
          <div class="empty-state">
            <div class="empty-icon-wrap">
              <app-icon
                [name]="activeTab() === 'reviews' ? 'shield-check' : 'inbox'"
                [size]="28"
                class="empty-icon"
              />
            </div>
            <h3 class="empty-title">
              {{ activeTab() === 'reviews' ? 'No Review Requests' : 'No Access Requests Found' }}
            </h3>
            <p class="empty-desc">
              @if (searchQuery() || statusFilter()) {
                No access requests matched your search query or status filter.
              } @else if (activeTab() === 'reviews') {
                You are all caught up! You have no pending access requests awaiting review in this scope.
              } @else {
                You have not submitted any access requests yet. Locked folders and documents in Shared With Me will offer access requests when restricted.
              }
            </p>

            @if (searchQuery() || statusFilter()) {
              <button
                type="button"
                class="btn-clear-filters"
                (click)="clearFilters()"
              >
                Clear Filters
              </button>
            }
          </div>
        } @else {
          <!-- Request Table -->
          <div class="table-wrap">
            <table class="requests-table">
              <thead>
                <tr>
                  <th class="col-resource">Resource</th>
                  <th class="col-user">{{ activeTab() === 'reviews' ? 'Requester' : 'Context / Reviewer' }}</th>
                  <th class="col-level">Permission</th>
                  <th class="col-status">Status</th>
                  <th class="col-date">Submitted</th>
                  <th class="col-actions">Actions</th>
                </tr>
              </thead>
              <tbody>
                @for (req of displayedRequests(); track req.id) {
                  <tr
                    class="request-row"
                    (click)="openRequestDetail(req)"
                  >
                    <!-- Resource Cell -->
                    <td class="col-resource">
                      <div class="resource-cell">
                        <div
                          class="res-icon"
                          [ngClass]="req.targetType === 'FOLDER' ? 'res-folder' : 'res-doc'"
                        >
                          <app-icon
                            [name]="req.targetType === 'FOLDER' ? 'folder' : 'file-text'"
                            [size]="18"
                          />
                        </div>
                        <div class="res-info">
                          <div class="res-title-row">
                            <span class="res-name" [title]="req.documentName || req.folderName">
                              {{ req.documentName || req.folderName || 'Unnamed Resource' }}
                            </span>
                            <span class="res-kind-tag">
                              {{ req.targetType === 'FOLDER' ? 'DIR' : 'FILE' }}
                            </span>
                          </div>
                          @if (req.reason) {
                            <span class="reason-preview" [title]="req.reason">
                              "{{ req.reason }}"
                            </span>
                          }
                        </div>
                      </div>
                    </td>

                    <!-- User / Context Cell -->
                    <td class="col-user">
                      @if (activeTab() === 'reviews') {
                        <div class="user-cell">
                          <div class="user-primary">
                            <app-icon name="user" [size]="13" class="text-stone-400" />
                            <span class="user-name">
                              {{ req.requesterUserName || req.requesterUserEmail || 'Unknown User' }}
                            </span>
                          </div>
                          @if (req.reviewerAuthorityExplanation) {
                            <div class="authority-badge" [title]="req.reviewerAuthorityExplanation">
                              <app-icon name="shield" [size]="11" />
                              <span>{{ req.reviewerAuthorityExplanation }}</span>
                            </div>
                          }
                        </div>
                      } @else {
                        <div class="context-cell">
                          @if (req.cancellationReason === 'POLICY_DISABLED') {
                            <span class="policy-cancelled-tag">
                              <app-icon name="alert-circle" [size]="12" />
                              <span>Disabled by Policy</span>
                            </span>
                          } @else if (req.processedByUserEmail) {
                            <div class="user-primary">
                              <app-icon name="check" [size]="13" class="text-emerald-500" />
                              <span class="text-xs text-stone-600 dark:text-stone-300">
                                {{ req.processedByUserEmail }}
                              </span>
                            </div>
                          } @else {
                            <div class="user-primary text-stone-400">
                              <app-icon name="clock" [size]="13" />
                              <span class="text-xs">Awaiting Reviewer</span>
                            </div>
                          }
                        </div>
                      }
                    </td>

                    <!-- Requested Permission Level -->
                    <td class="col-level">
                      <span class="perm-pill" [ngClass]="'perm-' + req.requestedPermissionLevel.toLowerCase()">
                        {{ req.requestedPermissionLevel }}
                      </span>
                    </td>

                    <!-- Status Cell -->
                    <td class="col-status">
                      <span class="status-pill" [ngClass]="'status-' + req.status.toLowerCase()">
                        @switch (req.status) {
                          @case ('PENDING') {
                            <app-icon name="clock" [size]="12" />
                            <span>Pending</span>
                          }
                          @case ('APPROVED') {
                            <app-icon name="check" [size]="12" />
                            <span>Approved</span>
                          }
                          @case ('DENIED') {
                            <app-icon name="x" [size]="12" />
                            <span>Denied</span>
                          }
                          @case ('CANCELLED') {
                            <app-icon name="alert-circle" [size]="12" />
                            <span>Cancelled</span>
                          }
                        }
                      </span>
                    </td>

                    <!-- Date Cell -->
                    <td class="col-date">
                      <span class="date-text">{{ formatDate(req.createdAt) }}</span>
                    </td>

                    <!-- Actions Cell -->
                    <td class="col-actions" (click)="$event.stopPropagation()">
                      <div class="row-actions">
                        @if (activeTab() === 'reviews' && req.status === 'PENDING') {
                          <button
                            type="button"
                            class="action-btn-primary"
                            (click)="openRequestDetail(req)"
                          >
                            <span>Review</span>
                            <app-icon name="arrow-right" [size]="13" />
                          </button>
                        } @else if (activeTab() === 'my-requests' && req.status === 'PENDING') {
                          <button
                            type="button"
                            class="action-btn-danger"
                            (click)="onCancelRequest(req.id)"
                          >
                            <span>Cancel</span>
                          </button>
                        } @else {
                          <button
                            type="button"
                            class="action-btn-ghost"
                            (click)="openRequestDetail(req)"
                          >
                            <span>Details</span>
                          </button>
                        }
                      </div>
                    </td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </main>

      <!-- Decision & Detail Drawer -->
      <app-access-request-detail-drawer
        [isOpen]="isDrawerOpen()"
        [request]="selectedRequest()"
        [isReviewer]="activeTab() === 'reviews'"
        (close)="closeDrawer()"
        (approve)="onApproveRequest($event)"
        (deny)="onDenyRequest($event)"
        (cancel)="onCancelRequest($event)"
      />
    </div>
  `,
  styles: [`
    .access-requests-container {
      padding: 2rem 2.25rem 3.5rem;
      max-width: 1280px;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    /* ── Page Header ─────────────────────────────────────────────────────── */
    .page-header {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .header-main {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .header-titles {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }

    .header-eyebrow {
      display: flex;
      align-items: center;
      gap: 0.4375rem;
      font-size: 0.6875rem;
      font-weight: 700;
      letter-spacing: 0.08em;
      color: #78716c;
      text-transform: uppercase;
    }

    .eyebrow-dot {
      width: 6px;
      height: 6px;
      border-radius: 9999px;
      background: #ea580c;
    }

    .title-with-icon {
      display: flex;
      align-items: center;
      gap: 0.875rem;
    }

    .icon-badge {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: linear-gradient(135deg, #fff7ed 0%, #ffedd5 100%);
      border: 1px solid #fed7aa;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #ea580c;
      box-shadow: 0 1px 2px rgba(234, 88, 12, 0.08);
      flex-shrink: 0;
    }

    :host-context(.dark) .icon-badge {
      background: linear-gradient(135deg, rgba(234, 88, 12, 0.18) 0%, rgba(234, 88, 12, 0.08) 100%);
      border-color: rgba(234, 88, 12, 0.3);
      color: #fb923c;
    }

    .page-title {
      font-size: 1.5rem;
      font-weight: 800;
      letter-spacing: -0.025em;
      color: #0f172a;
      margin: 0;
      line-height: 1.2;
    }

    :host-context(.dark) .page-title {
      color: #f8fafc;
    }

    .page-subtitle {
      font-size: 0.875rem;
      color: #64748b;
      margin: 0.25rem 0 0;
      max-width: 600px;
    }

    :host-context(.dark) .page-subtitle {
      color: #94a3b8;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .btn-refresh {
      display: inline-flex;
      align-items: center;
      gap: 0.4375rem;
      padding: 0.5rem 0.875rem;
      border-radius: 8px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      color: #475569;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
    }

    .btn-refresh:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #cbd5e1;
      color: #0f172a;
    }

    .btn-refresh:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    :host-context(.dark) .btn-refresh {
      background: #1e293b;
      border-color: #334155;
      color: #cbd5e1;
    }

    :host-context(.dark) .btn-refresh:hover:not(:disabled) {
      background: #334155;
      color: #f8fafc;
    }

    .spin {
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      100% { transform: rotate(360deg); }
    }

    /* ── Segmented Control Tab Strip ─────────────────────────────────────── */
    .tab-strip-row {
      display: flex;
      align-items: center;
    }

    .segmented-control {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      background: #f1f5f9;
      padding: 4px;
      border-radius: 10px;
      border: 1px solid #e2e8f0;
    }

    :host-context(.dark) .segmented-control {
      background: #1e293b;
      border-color: #334155;
    }

    .segmented-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4375rem 0.875rem;
      border-radius: 7px;
      border: none;
      background: transparent;
      color: #64748b;
      font-size: 0.8125rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .segmented-btn:hover:not(.active) {
      color: #0f172a;
    }

    .segmented-btn.active {
      background: #ffffff;
      color: #0f172a;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    :host-context(.dark) .segmented-btn {
      color: #94a3b8;
    }

    :host-context(.dark) .segmented-btn:hover:not(.active) {
      color: #f8fafc;
    }

    :host-context(.dark) .segmented-btn.active {
      background: #0f172a;
      color: #f8fafc;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
    }

    .pill-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0.125rem 0.4375rem;
      border-radius: 9999px;
      font-size: 0.6875rem;
      font-weight: 700;
      line-height: 1;
      min-width: 1.125rem;
    }

    .pill-badge-amber {
      background: #ea580c;
      color: #ffffff;
    }

    .pill-badge-neutral {
      background: #e2e8f0;
      color: #475569;
    }

    :host-context(.dark) .pill-badge-neutral {
      background: #334155;
      color: #cbd5e1;
    }

    /* ── Notification Callout Banners ───────────────────────────────────── */
    .callout-banner {
      display: flex;
      align-items: flex-start;
      gap: 0.875rem;
      padding: 0.875rem 1.125rem;
      border-radius: 10px;
      font-size: 0.8125rem;
      border: 1px solid transparent;
      animation: fadeIn 0.2s ease-out;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(-4px); }
      to { opacity: 1; transform: translateY(0); }
    }

    .banner-warning {
      background: #fffbeb;
      border-color: #fde68a;
      color: #92400e;
    }

    :host-context(.dark) .banner-warning {
      background: rgba(245, 158, 11, 0.12);
      border-color: rgba(245, 158, 11, 0.25);
      color: #fcd34d;
    }

    .banner-info {
      background: #f0f9ff;
      border-color: #bae6fd;
      color: #0369a1;
    }

    :host-context(.dark) .banner-info {
      background: rgba(14, 165, 233, 0.12);
      border-color: rgba(14, 165, 233, 0.25);
      color: #7dd3fc;
    }

    .callout-icon-col {
      flex-shrink: 0;
      margin-top: 1px;
    }

    .callout-text-col {
      display: flex;
      flex-direction: column;
      gap: 0.125rem;
    }

    .callout-title {
      font-weight: 700;
      font-size: 0.84rem;
    }

    .callout-desc {
      margin: 0;
      line-height: 1.4;
      opacity: 0.92;
    }

    /* ── Surface Card (Main Content Container) ─────────────────────────── */
    .surface-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03), 0 1px 2px -1px rgba(0, 0, 0, 0.03);
      overflow: hidden;
    }

    :host-context(.dark) .surface-card {
      background: #111827;
      border-color: #1f2937;
    }

    /* ── Controls Bar: Search & Status Filters ─────────────────────────── */
    .controls-bar {
      padding: 0.875rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      background: #ffffff;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }

    :host-context(.dark) .controls-bar {
      background: #111827;
      border-bottom-color: #1f2937;
    }

    .search-wrap {
      position: relative;
      flex: 1;
      min-width: 240px;
      max-width: 360px;
    }

    .search-icon {
      position: absolute;
      left: 0.75rem;
      top: 50%;
      transform: translateY(-50%);
      color: #94a3b8;
      pointer-events: none;
    }

    .search-input {
      width: 100%;
      padding: 0.4375rem 2rem 0.4375rem 2.25rem;
      font-size: 0.8125rem;
      border-radius: 7px;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #0f172a;
      outline: none;
      transition: all 0.15s ease;
    }

    .search-input:focus {
      border-color: #ea580c;
      background: #ffffff;
      box-shadow: 0 0 0 3px rgba(234, 88, 12, 0.12);
    }

    :host-context(.dark) .search-input {
      background: #1f2937;
      border-color: #374151;
      color: #f9fafb;
    }

    :host-context(.dark) .search-input:focus {
      background: #111827;
      border-color: #ea580c;
    }

    .search-clear {
      position: absolute;
      right: 0.5rem;
      top: 50%;
      transform: translateY(-50%);
      background: transparent;
      border: none;
      color: #94a3b8;
      cursor: pointer;
      display: flex;
      align-items: center;
      padding: 2px;
    }

    .status-filters {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      flex-wrap: wrap;
    }

    .filter-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.3125rem 0.6875rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #64748b;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .filter-chip:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    .filter-chip.active {
      background: #0f172a;
      border-color: #0f172a;
      color: #ffffff;
    }

    :host-context(.dark) .filter-chip {
      background: #1f2937;
      border-color: #374151;
      color: #9ca3af;
    }

    :host-context(.dark) .filter-chip:hover {
      background: #374151;
      color: #f9fafb;
    }

    :host-context(.dark) .filter-chip.active {
      background: #ea580c;
      border-color: #ea580c;
      color: #ffffff;
    }

    .chip-count {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0.0625rem 0.3125rem;
      border-radius: 9999px;
      background: rgba(0, 0, 0, 0.06);
      font-size: 0.6875rem;
      font-weight: 700;
      line-height: 1;
    }

    .filter-chip.active .chip-count {
      background: rgba(255, 255, 255, 0.2);
      color: #ffffff;
    }

    .count-amber {
      background: #fef3c7;
      color: #b45309;
    }

    /* ── Skeleton Loading ───────────────────────────────────────────────── */
    .skeleton-container {
      padding: 1rem 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.875rem;
    }

    .skeleton-row {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      padding: 0.75rem 0;
      border-bottom: 1px solid #f1f5f9;
    }

    :host-context(.dark) .skeleton-row {
      border-bottom-color: #1f2937;
    }

    .skeleton-box {
      background: #e2e8f0;
      border-radius: 6px;
      animation: pulse 1.5s ease-in-out infinite;
    }

    :host-context(.dark) .skeleton-box {
      background: #1f2937;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.45; }
    }

    .skeleton-icon { width: 34px; height: 34px; border-radius: 8px; flex-shrink: 0; }
    .skeleton-text { height: 16px; border-radius: 4px; }
    .skeleton-pill { width: 70px; height: 22px; border-radius: 9999px; }
    .skeleton-btn { width: 64px; height: 28px; border-radius: 6px; margin-left: auto; }
    .skeleton-w-md { width: 220px; }
    .skeleton-w-sm { width: 140px; }

    /* ── Empty State ────────────────────────────────────────────────────── */
    .empty-state {
      padding: 4rem 2rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
    }

    .empty-icon-wrap {
      width: 56px;
      height: 56px;
      border-radius: 14px;
      background: #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 1rem;
      color: #94a3b8;
    }

    :host-context(.dark) .empty-icon-wrap {
      background: #1f2937;
      color: #6b7280;
    }

    .empty-title {
      font-size: 1.0625rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
    }

    :host-context(.dark) .empty-title {
      color: #f8fafc;
    }

    .empty-desc {
      font-size: 0.8125rem;
      color: #64748b;
      margin: 0.5rem 0 0;
      max-width: 440px;
      line-height: 1.5;
    }

    :host-context(.dark) .empty-desc {
      color: #94a3b8;
    }

    .btn-clear-filters {
      margin-top: 1.25rem;
      padding: 0.375rem 0.875rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #cbd5e1;
      background: #ffffff;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-clear-filters:hover {
      background: #f8fafc;
      border-color: #94a3b8;
    }

    :host-context(.dark) .btn-clear-filters {
      background: #1f2937;
      border-color: #374151;
      color: #e5e7eb;
    }

    /* ── Table & Rows ───────────────────────────────────────────────────── */
    .table-wrap {
      overflow-x: auto;
    }

    .requests-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
    }

    .requests-table th {
      padding: 0.75rem 1.25rem;
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      color: #64748b;
      border-bottom: 1px solid #e2e8f0;
      background: #f8fafc;
    }

    :host-context(.dark) .requests-table th {
      background: #0f172a;
      border-bottom-color: #1f2937;
      color: #94a3b8;
    }

    .request-row {
      border-bottom: 1px solid #f1f5f9;
      cursor: pointer;
      transition: background-color 0.12s ease;
    }

    :host-context(.dark) .request-row {
      border-bottom-color: #1f2937;
    }

    .request-row:hover {
      background: #f8fafc;
    }

    :host-context(.dark) .request-row:hover {
      background: #1e293b;
    }

    .requests-table td {
      padding: 0.875rem 1.25rem;
      font-size: 0.8125rem;
      color: #1e293b;
      vertical-align: middle;
    }

    :host-context(.dark) .requests-table td {
      color: #e2e8f0;
    }

    /* ── Table Cells ────────────────────────────────────────────────────── */
    .resource-cell {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .res-icon {
      width: 36px;
      height: 36px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .res-folder {
      background: #fff7ed;
      color: #ea580c;
    }

    .res-doc {
      background: #ecfdf5;
      color: #059669;
    }

    :host-context(.dark) .res-folder {
      background: rgba(234, 88, 12, 0.15);
      color: #fb923c;
    }

    :host-context(.dark) .res-doc {
      background: rgba(5, 150, 105, 0.15);
      color: #34d399;
    }

    .res-info {
      display: flex;
      flex-direction: column;
      gap: 0.1875rem;
      min-width: 0;
    }

    .res-title-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .res-name {
      font-weight: 600;
      color: #0f172a;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 240px;
    }

    :host-context(.dark) .res-name {
      color: #f8fafc;
    }

    .res-kind-tag {
      font-size: 0.5625rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      padding: 0.0625rem 0.3125rem;
      border-radius: 4px;
      background: #f1f5f9;
      color: #64748b;
    }

    :host-context(.dark) .res-kind-tag {
      background: #1f2937;
      color: #9ca3af;
    }

    .reason-preview {
      font-size: 0.6875rem;
      color: #64748b;
      font-style: italic;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 280px;
    }

    :host-context(.dark) .reason-preview {
      color: #94a3b8;
    }

    .user-cell {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .user-primary {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
    }

    .user-name {
      font-weight: 600;
      font-size: 0.8125rem;
      color: #0f172a;
    }

    :host-context(.dark) .user-name {
      color: #f8fafc;
    }

    .authority-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      font-size: 0.65625rem;
      font-weight: 600;
      color: #b45309;
      background: #fef3c7;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      width: fit-content;
      border: 1px solid #fde68a;
    }

    :host-context(.dark) .authority-badge {
      background: rgba(245, 158, 11, 0.15);
      border-color: rgba(245, 158, 11, 0.3);
      color: #fcd34d;
    }

    .context-cell {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .policy-cancelled-tag {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      font-size: 0.6875rem;
      font-weight: 600;
      color: #dc2626;
      background: #fee2e2;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      width: fit-content;
    }

    :host-context(.dark) .policy-cancelled-tag {
      background: rgba(220, 38, 38, 0.15);
      color: #fca5a5;
    }

    /* ── Permission Pills ───────────────────────────────────────────────── */
    .perm-pill {
      font-size: 0.6875rem;
      font-weight: 700;
      padding: 0.1875rem 0.5rem;
      border-radius: 5px;
      letter-spacing: 0.03em;
      border: 1px solid transparent;
    }

    .perm-view {
      background: #f0f9ff;
      color: #0369a1;
      border-color: #bae6fd;
    }

    .perm-download {
      background: #faf5ff;
      color: #7e22ce;
      border-color: #e9d5ff;
    }

    .perm-edit {
      background: #fff7ed;
      color: #c2410c;
      border-color: #fed7aa;
    }

    :host-context(.dark) .perm-view {
      background: rgba(14, 165, 233, 0.12);
      color: #38bdf8;
      border-color: rgba(14, 165, 233, 0.3);
    }

    :host-context(.dark) .perm-download {
      background: rgba(168, 85, 247, 0.12);
      color: #c084fc;
      border-color: rgba(168, 85, 247, 0.3);
    }

    :host-context(.dark) .perm-edit {
      background: rgba(234, 88, 12, 0.12);
      color: #fb923c;
      border-color: rgba(234, 88, 12, 0.3);
    }

    /* ── Status Pills ───────────────────────────────────────────────────── */
    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.3125rem;
      font-size: 0.6875rem;
      font-weight: 700;
      padding: 0.2rem 0.5rem;
      border-radius: 9999px;
      letter-spacing: 0.02em;
    }

    .status-pending {
      background: #fef3c7;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    .status-approved {
      background: #d1fae5;
      color: #065f46;
      border: 1px solid #a7f3d0;
    }

    .status-denied {
      background: #fee2e2;
      color: #991b1b;
      border: 1px solid #fecaca;
    }

    .status-cancelled {
      background: #f1f5f9;
      color: #64748b;
      border: 1px solid #e2e8f0;
    }

    :host-context(.dark) .status-pending {
      background: rgba(245, 158, 11, 0.15);
      color: #fcd34d;
      border-color: rgba(245, 158, 11, 0.3);
    }

    :host-context(.dark) .status-approved {
      background: rgba(16, 185, 129, 0.15);
      color: #6ee7b7;
      border-color: rgba(16, 185, 129, 0.3);
    }

    :host-context(.dark) .status-denied {
      background: rgba(239, 68, 68, 0.15);
      color: #fca5a5;
      border-color: rgba(239, 68, 68, 0.3);
    }

    :host-context(.dark) .status-cancelled {
      background: #1f2937;
      color: #9ca3af;
      border-color: #374151;
    }

    .date-text {
      font-size: 0.75rem;
      color: #64748b;
      font-variant-numeric: tabular-nums;
    }

    :host-context(.dark) .date-text {
      color: #9ca3af;
    }

    /* ── Action Buttons ─────────────────────────────────────────────────── */
    .row-actions {
      display: flex;
      justify-content: flex-end;
    }

    .action-btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 0.3125rem;
      padding: 0.375rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #ea580c;
      background: #ea580c;
      color: #ffffff;
      cursor: pointer;
      transition: all 0.12s ease;
      box-shadow: 0 1px 2px rgba(234, 88, 12, 0.15);
    }

    .action-btn-primary:hover {
      background: #c2410c;
      border-color: #c2410c;
    }

    .action-btn-danger {
      padding: 0.375rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #fca5a5;
      background: #fef2f2;
      color: #dc2626;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .action-btn-danger:hover {
      background: #fee2e2;
      border-color: #f87171;
    }

    :host-context(.dark) .action-btn-danger {
      background: rgba(220, 38, 38, 0.12);
      border-color: rgba(220, 38, 38, 0.3);
      color: #fca5a5;
    }

    :host-context(.dark) .action-btn-danger:hover {
      background: rgba(220, 38, 38, 0.2);
    }

    .action-btn-ghost {
      padding: 0.375rem 0.6875rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      background: #ffffff;
      color: #475569;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .action-btn-ghost:hover {
      background: #f8fafc;
      border-color: #cbd5e1;
      color: #0f172a;
    }

    :host-context(.dark) .action-btn-ghost {
      background: #1f2937;
      border-color: #374151;
      color: #cbd5e1;
    }

    :host-context(.dark) .action-btn-ghost:hover {
      background: #374151;
      color: #f9fafb;
    }
  `]
})
export class AccessRequestsPageComponent implements OnInit {
  private readonly accessRequestApi = inject(AccessRequestApiService);
  private readonly workspaceContext = inject(WorkspaceContextStore);
  private readonly notificationService = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);

  activeTab = signal<ActiveTab>('reviews');
  statusFilter = signal<AccessRequestStatus | null>(null);
  searchQuery = signal<string>('');
  isLoading = signal<boolean>(false);
  isRefreshing = signal<boolean>(false);

  reviewRequests = signal<AccessRequestResponseDto[]>([]);
  myRequests = signal<AccessRequestResponseDto[]>([]);

  selectedRequest = signal<AccessRequestResponseDto | null>(null);
  isDrawerOpen = signal<boolean>(false);

  canReview = signal<boolean>(true);

  pendingReviewsCount = computed(() => {
    return this.reviewRequests().filter((r) => r.status === 'PENDING').length;
  });

  myPendingCount = computed(() => {
    return this.myRequests().filter((r) => r.status === 'PENDING').length;
  });

  statusCounts = computed(() => {
    const list = this.activeTab() === 'reviews' ? this.reviewRequests() : this.myRequests();
    return {
      all: list.length,
      pending: list.filter((r) => r.status === 'PENDING').length,
      approved: list.filter((r) => r.status === 'APPROVED').length,
      denied: list.filter((r) => r.status === 'DENIED').length,
      cancelled: list.filter((r) => r.status === 'CANCELLED').length,
    };
  });

  displayedRequests = computed(() => {
    const list = this.activeTab() === 'reviews' ? this.reviewRequests() : this.myRequests();
    const status = this.statusFilter();
    const query = this.searchQuery().toLowerCase().trim();

    return list.filter((item) => {
      if (status && item.status !== status) {
        return false;
      }
      if (query) {
        const name = (item.documentName || item.folderName || '').toLowerCase();
        const user = (item.requesterUserName || item.requesterUserEmail || '').toLowerCase();
        const reason = (item.reason || '').toLowerCase();
        return name.includes(query) || user.includes(query) || reason.includes(query);
      }
      return true;
    });
  });

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading.set(true);

    const orgCode = this.workspaceContext.activeOrgCode();
    const workspaceId = this.workspaceContext.activeWorkspace()?.id;

    // 1. Fetch reviewer requests
    this.accessRequestApi
      .getReviews({
        orgCode: orgCode || undefined,
        workspaceId: workspaceId || undefined,
      })
      .subscribe({
        next: (res) => {
          this.reviewRequests.set(res.content || []);
          this.canReview.set(true);
          this.fetchMyRequests();
        },
        error: () => {
          // If 403 or not a reviewer, switch to my-requests
          this.canReview.set(false);
          this.activeTab.set('my-requests');
          this.fetchMyRequests();
        },
      });
  }

  onRefresh(): void {
    this.isRefreshing.set(true);
    const orgCode = this.workspaceContext.activeOrgCode();
    const workspaceId = this.workspaceContext.activeWorkspace()?.id;

    this.accessRequestApi
      .getReviews({
        orgCode: orgCode || undefined,
        workspaceId: workspaceId || undefined,
      })
      .subscribe({
        next: (res) => {
          this.reviewRequests.set(res.content || []);
          this.canReview.set(true);
          this.accessRequestApi.getMyAccessRequests().subscribe({
            next: (myRes) => {
              this.myRequests.set(myRes.content || []);
              this.isRefreshing.set(false);
              this.notificationService.success('Access requests refreshed');
            },
            error: () => this.isRefreshing.set(false),
          });
        },
        error: () => {
          this.canReview.set(false);
          this.activeTab.set('my-requests');
          this.accessRequestApi.getMyAccessRequests().subscribe({
            next: (myRes) => {
              this.myRequests.set(myRes.content || []);
              this.isRefreshing.set(false);
              this.notificationService.success('Access requests refreshed');
            },
            error: () => this.isRefreshing.set(false),
          });
        },
      });
  }

  fetchMyRequests(): void {
    this.accessRequestApi.getMyAccessRequests().subscribe({
      next: (res) => {
        this.myRequests.set(res.content || []);
        this.isLoading.set(false);
        if (!this.canReview()) {
          this.activeTab.set('my-requests');
        }
      },
      error: () => {
        this.isLoading.set(false);
      },
    });
  }

  setActiveTab(tab: ActiveTab): void {
    this.activeTab.set(tab);
    this.statusFilter.set(null);
  }

  setStatusFilter(status: AccessRequestStatus | null): void {
    this.statusFilter.set(status);
  }

  onSearchChange(val: string): void {
    this.searchQuery.set(val);
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.statusFilter.set(null);
  }

  openRequestDetail(req: AccessRequestResponseDto): void {
    this.selectedRequest.set(req);
    this.isDrawerOpen.set(true);
  }

  closeDrawer(): void {
    this.isDrawerOpen.set(false);
    this.selectedRequest.set(null);
  }

  onApproveRequest(event: {
    id: number;
    grantedPermissionLevel: AccessPermissionLevel;
    reviewerNote?: string;
  }): void {
    this.accessRequestApi
      .approveAccessRequest(event.id, {
        reviewerNote: event.reviewerNote,
        grantedPermissionLevel: event.grantedPermissionLevel,
      })
      .subscribe({
        next: (updated) => {
          this.notificationService.success(
            `Request #${event.id} approved with ${event.grantedPermissionLevel} permission.`
          );
          this.closeDrawer();
          this.loadData();
        },
        error: (err) => {
          if (err?.status === 400 || err?.status === 409) {
            this.notificationService.error(
              'This request was already resolved by another reviewer.'
            );
            this.closeDrawer();
            this.loadData();
          } else {
            this.notificationService.error(
              err?.error?.message || 'Failed to approve access request.'
            );
          }
        },
      });
  }

  onDenyRequest(event: { id: number; reviewerNote?: string }): void {
    this.accessRequestApi
      .denyAccessRequest(event.id, { reviewerNote: event.reviewerNote })
      .subscribe({
        next: () => {
          this.notificationService.info(`Request #${event.id} denied.`);
          this.closeDrawer();
          this.loadData();
        },
        error: (err) => {
          if (err?.status === 400 || err?.status === 409) {
            this.notificationService.error(
              'This request was already resolved by another reviewer.'
            );
            this.closeDrawer();
            this.loadData();
          } else {
            this.notificationService.error(
              err?.error?.message || 'Failed to deny access request.'
            );
          }
        },
      });
  }

  onCancelRequest(id: number): void {
    this.accessRequestApi.cancelAccessRequest(id).subscribe({
      next: () => {
        this.notificationService.info(`Access request #${id} cancelled.`);
        this.closeDrawer();
        this.loadData();
      },
      error: (err) => {
        if (err?.status === 400 || err?.status === 409) {
          this.notificationService.error(
            'This request has already been processed.'
          );
          this.closeDrawer();
          this.loadData();
        } else {
          this.notificationService.error(
            err?.error?.message || 'Failed to cancel access request.'
          );
        }
      },
    });
  }

  formatDate(iso?: string): string {
    if (!iso) return '—';
    try {
      const date = new Date(iso);
      return date.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      });
    } catch {
      return iso;
    }
  }
}
