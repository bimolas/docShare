import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { NotificationStore } from '../state/notification.store';
import {
  NotificationResponseDto,
  NotificationStatus,
  NotificationType,
} from '../../../core/models/notification-feed.model';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { buildCanonicalRoute } from '../../../core/models/feature-route.model';

export type NotificationTab =
  | 'ALL'
  | 'ACTION_REQUIRED'
  | 'INVITATIONS'
  | 'ACCESS_PERMISSIONS'
  | 'SHARING'
  | 'SECURITY_ACCOUNT';

export type NotificationStatusFilter = 'ALL' | 'UNREAD' | 'READ' | 'ARCHIVED';

@Component({
  selector: 'app-notification-center-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div class="notification-center-container">
      <!-- Page Header -->
      <header class="page-header">
        <div class="header-content">
          <div class="header-titles">
            <div class="breadcrumb-trail">
              <span class="breadcrumb-item">Personal Space</span>
              <app-icon name="chevron-right" [size]="12" class="breadcrumb-sep" />
              <span class="breadcrumb-current">Notification Center</span>
            </div>
            <div class="title-row">
              <h1 class="page-title">Notification Center</h1>
              @if (store.unreadCount() > 0) {
                <span class="unread-count-pill">{{ store.unreadCount() }} unread</span>
              } @else {
                <span class="caught-up-pill">All caught up</span>
              }
            </div>
            <p class="page-subtitle">
              Manage your personal notifications, pending access requests, invitations, and alerts.
            </p>
          </div>

          <div class="header-actions">
            @if (store.unreadCount() > 0) {
              <button
                class="btn-secondary"
                (click)="onMarkAllRead()"
                aria-label="Mark all notifications as read"
                title="Mark all notifications as read"
              >
                <app-icon name="check" [size]="15" />
                <span>Mark all read</span>
              </button>
            }

            <button
              class="btn-secondary btn-icon-only"
              (click)="onRefresh()"
              aria-label="Refresh notification feed"
              title="Refresh"
            >
              <app-icon name="refresh-cw" [size]="15" [class.spinning]="store.isRefreshing()" />
            </button>
          </div>
        </div>

        <!-- Navigation Tabs -->
        <nav class="tabs-nav" aria-label="Notification Categories">
          <button
            class="tab-btn"
            [class.active]="activeTab() === 'ALL'"
            (click)="selectTab('ALL')"
            role="tab"
            [attr.aria-selected]="activeTab() === 'ALL'"
          >
            <app-icon name="bell" [size]="15" />
            <span>All</span>
            @if (store.totalElements() > 0 && activeTab() === 'ALL') {
              <span class="tab-badge">{{ store.totalElements() }}</span>
            }
          </button>

          <button
            class="tab-btn tab-action-required"
            [class.active]="activeTab() === 'ACTION_REQUIRED'"
            (click)="selectTab('ACTION_REQUIRED')"
            role="tab"
            [attr.aria-selected]="activeTab() === 'ACTION_REQUIRED'"
          >
            <span class="action-required-dot"></span>
            <span>Action Required</span>
            @if (store.actionRequiredNotifications().length > 0) {
              <span class="tab-badge badge-warning">{{ store.actionRequiredNotifications().length }}</span>
            }
          </button>

          <button
            class="tab-btn"
            [class.active]="activeTab() === 'INVITATIONS'"
            (click)="selectTab('INVITATIONS')"
            role="tab"
            [attr.aria-selected]="activeTab() === 'INVITATIONS'"
          >
            <app-icon name="users" [size]="15" />
            <span>Invitations</span>
          </button>

          <button
            class="tab-btn"
            [class.active]="activeTab() === 'ACCESS_PERMISSIONS'"
            (click)="selectTab('ACCESS_PERMISSIONS')"
            role="tab"
            [attr.aria-selected]="activeTab() === 'ACCESS_PERMISSIONS'"
          >
            <app-icon name="key" [size]="15" />
            <span>Access & Permissions</span>
          </button>

          <button
            class="tab-btn"
            [class.active]="activeTab() === 'SHARING'"
            (click)="selectTab('SHARING')"
            role="tab"
            [attr.aria-selected]="activeTab() === 'SHARING'"
          >
            <app-icon name="share-2" [size]="15" />
            <span>Sharing</span>
          </button>

          <button
            class="tab-btn"
            [class.active]="activeTab() === 'SECURITY_ACCOUNT'"
            (click)="selectTab('SECURITY_ACCOUNT')"
            role="tab"
            [attr.aria-selected]="activeTab() === 'SECURITY_ACCOUNT'"
          >
            <app-icon name="shield" [size]="15" />
            <span>Security & Account</span>
          </button>
        </nav>
      </header>

      <!-- Status Filter Toolbar -->
      <section class="toolbar-section">
        <div class="status-filters" role="group" aria-label="Filter notifications by status">
          <span class="filter-label">Status:</span>
          <button
            class="status-chip"
            [class.active]="activeStatusFilter() === 'ALL'"
            (click)="selectStatusFilter('ALL')"
          >
            All
          </button>
          <button
            class="status-chip"
            [class.active]="activeStatusFilter() === 'UNREAD'"
            (click)="selectStatusFilter('UNREAD')"
          >
            Unread
          </button>
          <button
            class="status-chip"
            [class.active]="activeStatusFilter() === 'READ'"
            (click)="selectStatusFilter('READ')"
          >
            Read
          </button>
          <button
            class="status-chip"
            [class.active]="activeStatusFilter() === 'ARCHIVED'"
            (click)="selectStatusFilter('ARCHIVED')"
          >
            Archived
          </button>
        </div>

        <div class="toolbar-summary">
          @if (!store.isLoading()) {
            <span class="summary-text font-tabular">
              Showing {{ displayedNotifications().length }} notification{{ displayedNotifications().length === 1 ? '' : 's' }}
            </span>
          }
        </div>
      </section>

      <!-- Notification Feed List -->
      <main class="feed-main" role="feed" aria-label="Notification list">
        @if (store.isLoading() && displayedNotifications().length === 0) {
          <!-- Skeleton Loading -->
          <div class="skeleton-list" aria-busy="true" aria-label="Loading notifications">
            @for (i of [1, 2, 3, 4]; track i) {
              <div class="skeleton-card">
                <div class="skeleton-avatar pulse-animation"></div>
                <div class="skeleton-content">
                  <div class="skeleton-line line-header pulse-animation"></div>
                  <div class="skeleton-line line-body pulse-animation"></div>
                  <div class="skeleton-line line-meta pulse-animation"></div>
                </div>
              </div>
            }
          </div>
        } @else if (store.error() && displayedNotifications().length === 0) {
          <!-- Error State -->
          <div class="center-state error-card" role="alert">
            <div class="state-icon-circle error-circle">
              <app-icon name="alert-circle" [size]="28" />
            </div>
            <h2 class="state-title">Failed to load notifications</h2>
            <p class="state-description">{{ store.error() }}</p>
            <button class="btn-primary" (click)="onRefresh()">
              <app-icon name="refresh-cw" [size]="15" />
              <span>Retry</span>
            </button>
          </div>
        } @else if (displayedNotifications().length === 0) {
          <!-- Empty State -->
          <div class="center-state empty-card">
            <div class="state-icon-circle empty-circle">
              <app-icon name="bell" [size]="32" />
            </div>
            <h2 class="state-title">{{ getEmptyStateTitle() }}</h2>
            <p class="state-description">{{ getEmptyStateDescription() }}</p>
            @if (activeStatusFilter() !== 'ALL' || activeTab() !== 'ALL') {
              <button class="btn-secondary" (click)="resetFilters()">
                <span>Reset Filters</span>
              </button>
            }
          </div>
        } @else {
          <!-- Items List -->
          <div class="notifications-list">
            @for (item of displayedNotifications(); track item.id) {
              <article
                class="notification-card"
                [class.unread-card]="item.status === 'UNREAD'"
                [class.action-required-card]="isItemActionRequired(item)"
                (click)="onNotificationClick(item)"
                (keydown.enter)="onNotificationClick(item)"
                (keydown.space)="onNotificationClick(item)"
                tabindex="0"
                role="article"
                [attr.aria-label]="item.title"
              >
                <!-- Semantic Icon Col -->
                <div class="card-icon-col">
                  <div
                    class="type-icon-wrapper"
                    [class.action-type-icon]="isItemActionRequired(item)"
                    [class.invite-type-icon]="item.category === 'INVITATION' || item.category === 'COLLABORATION'"
                    [class.access-type-icon]="item.category === 'ACCESS_REQUEST'"
                    [class.sharing-type-icon]="item.category === 'SHARING'"
                    [class.security-type-icon]="item.category === 'SECURITY' || item.category === 'GOVERNANCE'"
                  >
                    <app-icon [name]="getNotificationIcon(item.type)" [size]="18" />
                  </div>
                </div>

                <!-- Main Content Col -->
                <div class="card-content-col">
                  <!-- Header Row: Category & Badges & Time -->
                  <div class="card-header-row">
                    <div class="badges-group">
                      @if (isItemActionRequired(item)) {
                        <span class="badge badge-action-required">Action Required</span>
                      }
                      <span class="badge badge-category">{{ formatCategory(item.category) }}</span>
                      @if (item.priority === 'HIGH' || item.priority === 'URGENT') {
                        <span class="badge badge-priority-high">{{ item.priority }}</span>
                      }
                    </div>

                    <div class="card-meta-right">
                      <span class="meta-time font-tabular" [title]="item.createdAt">
                        {{ formatRelativeTime(item.createdAt) }}
                      </span>
                      @if (item.status === 'UNREAD') {
                        <span class="status-unread-indicator" title="Unread notification"></span>
                      }
                    </div>
                  </div>

                  <!-- Title & Actor -->
                  <h2 class="card-title">{{ item.title }}</h2>

                  @if (item.actorName) {
                    <div class="actor-info-row">
                      <span class="actor-label">From:</span>
                      <span class="actor-name">{{ item.actorName }}</span>
                    </div>
                  }

                  <!-- Body Snippet -->
                  <p class="card-body-text">{{ item.body }}</p>

                  <!-- Actions Row: Direct CTA + Read/Archive/Delete -->
                  <div class="card-footer-actions">
                    <div class="footer-left-cta">
                      @if (getNotificationCtaText(item)) {
                        <button
                          class="cta-action-btn"
                          [class.cta-action-prominent]="isItemActionRequired(item)"
                          (click)="onCtaClick($event, item)"
                          [attr.aria-label]="getNotificationCtaText(item)"
                        >
                          <span>{{ getNotificationCtaText(item) }}</span>
                          <app-icon name="arrow-right" [size]="13" />
                        </button>
                      }
                    </div>

                    <div class="footer-right-actions">
                      <!-- Mark Read / Unread -->
                      <button
                        class="icon-action-button"
                        (click)="onToggleReadStatus($event, item)"
                        [attr.aria-label]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                        [title]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                      >
                        <app-icon [name]="item.status === 'UNREAD' ? 'check' : 'mail'" [size]="15" />
                        <span class="action-label">{{ item.status === 'UNREAD' ? 'Mark read' : 'Mark unread' }}</span>
                      </button>

                      <!-- Archive / Unarchive -->
                      @if (item.status !== 'ARCHIVED') {
                        <button
                          class="icon-action-button"
                          (click)="onArchive($event, item)"
                          aria-label="Archive notification"
                          title="Archive notification"
                        >
                          <app-icon name="archive" [size]="15" />
                          <span class="action-label">Archive</span>
                        </button>
                      } @else {
                        <button
                          class="icon-action-button"
                          (click)="onUnarchive($event, item)"
                          aria-label="Unarchive notification"
                          title="Unarchive notification"
                        >
                          <app-icon name="inbox" [size]="15" />
                          <span class="action-label">Unarchive</span>
                        </button>
                      }

                      <!-- Delete -->
                      <button
                        class="icon-action-button btn-delete"
                        (click)="onDelete($event, item)"
                        aria-label="Delete notification"
                        title="Delete notification"
                      >
                        <app-icon name="trash-2" [size]="15" />
                      </button>
                    </div>
                  </div>
                </div>
              </article>
            }
          </div>

          <!-- Pagination Footer -->
          @if (store.totalPages() > 1) {
            <footer class="pagination-footer">
              <div class="pagination-info font-tabular">
                Page {{ store.page() + 1 }} of {{ store.totalPages() }}
                ({{ store.totalElements() }} total items)
              </div>

              <div class="pagination-controls">
                <button
                  class="pagination-btn"
                  [disabled]="store.page() === 0"
                  (click)="onPageChange(store.page() - 1)"
                  aria-label="Previous page"
                >
                  <app-icon name="chevron-left" [size]="16" />
                  <span>Previous</span>
                </button>

                <button
                  class="pagination-btn"
                  [disabled]="store.page() >= store.totalPages() - 1"
                  (click)="onPageChange(store.page() + 1)"
                  aria-label="Next page"
                >
                  <span>Next</span>
                  <app-icon name="chevron-right" [size]="16" />
                </button>
              </div>
            </footer>
          }
        }
      </main>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100%;
      background: #f8fafc;
    }

    .notification-center-container {
      max-width: 1040px;
      margin: 0 auto;
      padding: 1.5rem 1.25rem 3rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    /* Page Header */
    .page-header {
      background: #ffffff;
      border: 1px solid var(--color-border, #e2e8f0);
      border-radius: 12px;
      padding: 1.25rem 1.5rem 0;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
    }

    .header-content {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1.5rem;
      padding-bottom: 1.25rem;
    }

    .breadcrumb-trail {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.75rem;
      color: #64748b;
      margin-bottom: 0.35rem;
    }

    .breadcrumb-sep {
      color: #94a3b8;
    }

    .breadcrumb-current {
      color: #0f172a;
      font-weight: 500;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .page-title {
      margin: 0;
      font-size: 1.5rem;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.02em;
    }

    .unread-count-pill {
      display: inline-flex;
      align-items: center;
      padding: 0.2rem 0.6rem;
      font-size: 0.75rem;
      font-weight: 700;
      color: #ea580c;
      background: #fff7ed;
      border: 1px solid #ffedd5;
      border-radius: 999px;
    }

    .caught-up-pill {
      display: inline-flex;
      align-items: center;
      padding: 0.2rem 0.6rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 999px;
    }

    .page-subtitle {
      margin: 0.35rem 0 0;
      font-size: 0.84375rem;
      color: #64748b;
      line-height: 1.4;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-shrink: 0;
    }

    .btn-secondary {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.45rem 0.85rem;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-secondary:hover {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .btn-icon-only {
      padding: 0.45rem;
      justify-content: center;
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.5rem 1rem;
      background: #ea580c;
      border: 1px solid #ea580c;
      border-radius: 8px;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #ffffff;
      cursor: pointer;
      transition: background 0.15s ease;
    }

    .btn-primary:hover {
      background: #c2410c;
      border-color: #c2410c;
    }

    .spinning {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    /* Tabs Navigation */
    .tabs-nav {
      display: flex;
      gap: 0.25rem;
      border-top: 1px solid #f1f5f9;
      overflow-x: auto;
      scrollbar-width: none;
    }

    .tabs-nav::-webkit-scrollbar {
      display: none;
    }

    .tab-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.75rem 0.9rem;
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #64748b;
      cursor: pointer;
      white-space: nowrap;
      transition: all 0.15s ease;
    }

    .tab-btn:hover {
      color: #0f172a;
    }

    .tab-btn.active {
      color: #ea580c;
      border-bottom-color: #ea580c;
      font-weight: 600;
    }

    .tab-badge {
      display: inline-flex;
      align-items: center;
      padding: 0.05rem 0.4rem;
      font-size: 0.6875rem;
      font-weight: 600;
      border-radius: 999px;
      background: #e2e8f0;
      color: #475569;
    }

    .tab-badge.badge-warning {
      background: #ea580c;
      color: #ffffff;
      font-weight: 700;
    }

    .action-required-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #f97316;
    }

    /* Toolbar Section */
    .toolbar-section {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }

    .status-filters {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    .filter-label {
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
      margin-right: 0.2rem;
    }

    .status-chip {
      padding: 0.3rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 500;
      border-radius: 6px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      color: #475569;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .status-chip:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    .status-chip.active {
      background: #0f172a;
      border-color: #0f172a;
      color: #ffffff;
      font-weight: 600;
    }

    .toolbar-summary {
      font-size: 0.75rem;
      color: #64748b;
    }

    .font-tabular {
      font-variant-numeric: tabular-nums;
    }

    /* Feed Main */
    .feed-main {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .notifications-list {
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
    }

    /* Notification Card */
    .notification-card {
      display: flex;
      gap: 1rem;
      padding: 1.1rem 1.25rem;
      background: #ffffff;
      border: 1px solid var(--color-border, #e2e8f0);
      border-radius: 10px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
      cursor: pointer;
      transition: all 0.15s ease;
      outline: none;
    }

    .notification-card:hover,
    .notification-card:focus-visible {
      border-color: #cbd5e1;
      box-shadow: 0 4px 12px rgba(15, 23, 42, 0.05);
      transform: translateY(-1px);
    }

    .notification-card:focus-visible {
      outline: 2px solid #ea580c;
      outline-offset: -1px;
    }

    .unread-card {
      border-left: 3px solid #ea580c;
      background: #ffffff;
    }

    .action-required-card {
      background: #fffdfa;
      border-color: #fed7aa;
    }

    .action-required-card:hover {
      border-color: #fdba74;
    }

    /* Icon Column */
    .card-icon-col {
      flex-shrink: 0;
      padding-top: 0.15rem;
    }

    .type-icon-wrapper {
      width: 36px;
      height: 36px;
      border-radius: 9px;
      display: flex;
      align-items: center;
      justify-content: center;
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
    }

    .action-type-icon {
      background: #fff7ed;
      color: #ea580c;
      border-color: #ffedd5;
    }

    .invite-type-icon {
      background: #f0fdf4;
      color: #16a34a;
      border-color: #dcfce7;
    }

    .access-type-icon {
      background: #eff6ff;
      color: #2563eb;
      border-color: #dbeafe;
    }

    .sharing-type-icon {
      background: #faf5ff;
      color: #7c3aed;
      border-color: #f3e8ff;
    }

    .security-type-icon {
      background: #fef2f2;
      color: #dc2626;
      border-color: #fee2e2;
    }

    /* Content Column */
    .card-content-col {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .card-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }

    .badges-group {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      flex-wrap: wrap;
    }

    .badge {
      display: inline-flex;
      align-items: center;
      padding: 0.1rem 0.45rem;
      font-size: 0.6875rem;
      font-weight: 600;
      border-radius: 4px;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .badge-action-required {
      background: #ea580c;
      color: #ffffff;
      font-weight: 700;
    }

    .badge-category {
      background: #f1f5f9;
      color: #475569;
    }

    .badge-priority-high {
      background: #fef2f2;
      color: #b91c1c;
      border: 1px solid #fecaca;
    }

    .card-meta-right {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-shrink: 0;
    }

    .meta-time {
      font-size: 0.75rem;
      color: #94a3b8;
    }

    .status-unread-indicator {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #ea580c;
    }

    .card-title {
      margin: 0;
      font-size: 0.9375rem;
      font-weight: 600;
      color: #0f172a;
      line-height: 1.35;
    }

    .actor-info-row {
      font-size: 0.75rem;
      color: #64748b;
      display: flex;
      gap: 0.3rem;
    }

    .actor-name {
      font-weight: 500;
      color: #334155;
    }

    .card-body-text {
      margin: 0.15rem 0 0;
      font-size: 0.8125rem;
      color: #475569;
      line-height: 1.45;
    }

    /* Card Footer Actions */
    .card-footer-actions {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      margin-top: 0.5rem;
      padding-top: 0.5rem;
      border-top: 1px solid #f1f5f9;
      flex-wrap: wrap;
    }

    .cta-action-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.35rem 0.75rem;
      font-size: 0.78125rem;
      font-weight: 600;
      color: #334155;
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .cta-action-btn:hover {
      background: #0f172a;
      border-color: #0f172a;
      color: #ffffff;
    }

    .cta-action-prominent {
      background: #fff7ed;
      border-color: #fdba74;
      color: #c2410c;
    }

    .cta-action-prominent:hover {
      background: #ea580c;
      border-color: #ea580c;
      color: #ffffff;
    }

    .footer-right-actions {
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }

    .icon-action-button {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      padding: 0.25rem 0.5rem;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 5px;
      font-size: 0.75rem;
      color: #64748b;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .icon-action-button:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    .btn-delete:hover {
      background: #fef2f2;
      color: #dc2626;
    }

    .action-label {
      font-size: 0.75rem;
    }

    /* Skeleton */
    .skeleton-list {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .skeleton-card {
      display: flex;
      gap: 1rem;
      padding: 1.25rem;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
    }

    .skeleton-avatar {
      width: 36px;
      height: 36px;
      border-radius: 9px;
      background: #e2e8f0;
      flex-shrink: 0;
    }

    .skeleton-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
    }

    .skeleton-line {
      border-radius: 4px;
      background: #e2e8f0;
    }

    .line-header {
      width: 40%;
      height: 16px;
    }

    .line-body {
      width: 80%;
      height: 14px;
    }

    .line-meta {
      width: 25%;
      height: 12px;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.45; }
    }

    .pulse-animation {
      animation: pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }

    /* Center State */
    .center-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 4rem 2rem;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      gap: 0.5rem;
    }

    .state-icon-circle {
      width: 60px;
      height: 60px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 0.5rem;
    }

    .empty-circle {
      background: #f8fafc;
      color: #94a3b8;
      border: 1px solid #e2e8f0;
    }

    .error-circle {
      background: #fef2f2;
      color: #ef4444;
      border: 1px solid #fee2e2;
    }

    .state-title {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 600;
      color: #0f172a;
    }

    .state-description {
      margin: 0 0 0.75rem;
      font-size: 0.84375rem;
      color: #64748b;
      max-width: 380px;
      line-height: 1.4;
    }

    /* Pagination Footer */
    .pagination-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 0.5rem;
      border-top: 1px solid #e2e8f0;
      gap: 1rem;
    }

    .pagination-info {
      font-size: 0.8125rem;
      color: #64748b;
    }

    .pagination-controls {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .pagination-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.4rem 0.85rem;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .pagination-btn:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .pagination-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    @media (max-width: 640px) {
      .header-content {
        flex-direction: column;
      }
      .toolbar-section {
        flex-direction: column;
        align-items: flex-start;
      }
      .card-footer-actions {
        flex-direction: column;
        align-items: flex-start;
      }
    }
  `],
})
export class NotificationCenterPageComponent implements OnInit {
  readonly store = inject(NotificationStore);
  private readonly router = inject(Router);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);

  readonly activeTab = signal<NotificationTab>('ALL');
  readonly activeStatusFilter = signal<NotificationStatusFilter>('ALL');

  ngOnInit(): void {
    // Initial fetch of page 0
    this.store.loadNotifications(0);
    this.store.refreshUnreadCount();
  }

  // ── Computed Grouped List ──

  readonly displayedNotifications = computed(() => {
    const tab = this.activeTab();
    const all = this.store.notifications();

    switch (tab) {
      case 'ACTION_REQUIRED':
        return this.store.actionRequiredNotifications();

      case 'INVITATIONS':
        return all.filter(
          (n) =>
            n.category === 'INVITATION' ||
            n.category === 'COLLABORATION' ||
            n.type.startsWith('ORG_INVITATION') ||
            n.type.startsWith('COLLABORATION')
        );

      case 'ACCESS_PERMISSIONS':
        return all.filter(
          (n) =>
            n.category === 'ACCESS_REQUEST' ||
            n.category === 'WORKSPACE' ||
            n.category === 'TEAM' ||
            n.type.startsWith('ACCESS_REQUEST') ||
            n.type.startsWith('PERMISSION') ||
            n.type.startsWith('TEAM') ||
            n.type.startsWith('WORKSPACE')
        );

      case 'SHARING':
        return all.filter(
          (n) =>
            n.category === 'SHARING' ||
            n.category === 'FILE_REQUEST' ||
            n.category === 'DOCUMENT' ||
            n.type.startsWith('FILE_REQUEST')
        );

      case 'SECURITY_ACCOUNT':
        return all.filter(
          (n) =>
            n.category === 'SECURITY' ||
            n.category === 'GOVERNANCE' ||
            n.category === 'SYSTEM' ||
            n.type.startsWith('ORG_MEMBER')
        );

      case 'ALL':
      default:
        return all;
    }
  });

  // ── Tab Navigation ──

  selectTab(tab: NotificationTab): void {
    this.activeTab.set(tab);

    // Apply backend category filter if applicable
    switch (tab) {
      case 'INVITATIONS':
        this.store.setCategoryFilter('INVITATION');
        break;
      case 'ACCESS_PERMISSIONS':
        this.store.setCategoryFilter('ACCESS_REQUEST');
        break;
      case 'SHARING':
        this.store.setCategoryFilter('SHARING');
        break;
      case 'SECURITY_ACCOUNT':
        this.store.setCategoryFilter('SECURITY');
        break;
      case 'ALL':
      case 'ACTION_REQUIRED':
      default:
        this.store.setCategoryFilter(null);
        break;
    }
  }

  // ── Status Filter ──

  selectStatusFilter(filter: NotificationStatusFilter): void {
    this.activeStatusFilter.set(filter);
    const backendStatus: NotificationStatus | null = filter === 'ALL' ? null : filter;
    this.store.setStatusFilter(backendStatus);
  }

  resetFilters(): void {
    this.activeTab.set('ALL');
    this.activeStatusFilter.set('ALL');
    this.store.setCategoryFilter(null);
    this.store.setStatusFilter(null);
  }

  // ── Pagination ──

  onPageChange(targetPage: number): void {
    if (targetPage >= 0 && targetPage < this.store.totalPages()) {
      this.store.loadNotifications(targetPage);
    }
  }

  // ── Actions ──

  onNotificationClick(item: NotificationResponseDto): void {
    if (item.status === 'UNREAD') {
      this.store.markAsRead(item.id);
    }
    this.navigateForNotification(item);
  }

  onCtaClick(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    if (item.status === 'UNREAD') {
      this.store.markAsRead(item.id);
    }
    this.navigateForNotification(item);
  }

  onToggleReadStatus(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    if (item.status === 'UNREAD') {
      this.store.markAsRead(item.id);
    } else {
      this.store.markAsUnread(item.id);
    }
  }

  onArchive(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    this.store.archiveNotification(item.id);
  }

  onUnarchive(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    this.store.unarchiveNotification(item.id);
  }

  onDelete(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    this.store.deleteNotification(item.id);
  }

  onMarkAllRead(): void {
    this.store.markAllAsRead();
  }

  onRefresh(): void {
    this.store.loadNotifications(this.store.page(), true);
    this.store.refreshUnreadCount();
  }

  // ── Navigation & Helpers ──

  isItemActionRequired(item: NotificationResponseDto): boolean {
    return this.store.actionRequiredNotifications().some((n) => n.id === item.id);
  }

  navigateForNotification(item: NotificationResponseDto): void {
    const orgCode = this.workspaceContextStore.activeOrganization()?.orgCode;
    const wsId = this.workspaceContextStore.activeWorkspace()?.id;

    switch (item.type) {
      case 'ACCESS_REQUEST_SUBMITTED':
      case 'ACCESS_REQUEST_APPROVED':
      case 'ACCESS_REQUEST_DENIED':
      case 'ACCESS_REQUEST_CANCELLED': {
        const route = buildCanonicalRoute('access-requests', orgCode, wsId);
        this.router.navigate(route);
        break;
      }

      case 'ORG_INVITATION_SENT':
      case 'ORG_INVITATION_ACCEPTED':
      case 'ORG_INVITATION_DECLINED':
      case 'ORG_INVITATION_REVOKED':
      case 'ONBOARDING_COMPLETED': {
        this.router.navigate(['/w', 'personal', 'invitations']);
        break;
      }

      case 'COLLABORATION_INVITE':
      case 'COLLABORATION_ACCEPTED':
      case 'COLLABORATION_REJECTED':
      case 'COLLABORATION_EXPIRED':
      case 'COLLABORATION_REVOKED': {
        this.router.navigate(['/w', 'personal', 'invitations']);
        break;
      }

      case 'FILE_REQUEST_CREATED':
      case 'FILE_REQUEST_UPLOADED':
      case 'FILE_REQUEST_FULFILLED':
      case 'FILE_REQUEST_CANCELLED': {
        this.router.navigate(['/w', 'personal', 'shared-with-me']);
        break;
      }

      case 'PERMISSION_GRANTED':
      case 'PERMISSION_UPDATED':
      case 'PERMISSION_REVOKED': {
        this.router.navigate(['/w', 'personal', 'shared-with-me']);
        break;
      }

      case 'TEAM_ADDED':
      case 'TEAM_REMOVED':
      case 'TEAM_MANAGER_ASSIGNED': {
        if (orgCode) {
          this.router.navigate(['/org', orgCode, 'teams']);
        } else {
          this.router.navigate(['/w', 'personal', 'dashboard']);
        }
        break;
      }

      default: {
        // Stay on page or route to personal dashboard
        break;
      }
    }
  }

  getNotificationIcon(type: NotificationType): IconName {
    switch (type) {
      case 'ACCESS_REQUEST_SUBMITTED':
        return 'key';
      case 'ACCESS_REQUEST_APPROVED':
        return 'check';
      case 'ACCESS_REQUEST_DENIED':
      case 'ACCESS_REQUEST_CANCELLED':
        return 'x';
      case 'ORG_INVITATION_SENT':
        return 'building-2';
      case 'ORG_INVITATION_ACCEPTED':
        return 'check';
      case 'ORG_INVITATION_DECLINED':
      case 'ORG_INVITATION_REVOKED':
        return 'x';
      case 'COLLABORATION_INVITE':
        return 'users';
      case 'COLLABORATION_ACCEPTED':
        return 'check';
      case 'COLLABORATION_REJECTED':
      case 'COLLABORATION_EXPIRED':
      case 'COLLABORATION_REVOKED':
        return 'x';
      case 'FILE_REQUEST_CREATED':
      case 'FILE_REQUEST_UPLOADED':
      case 'FILE_REQUEST_FULFILLED':
      case 'FILE_REQUEST_CANCELLED':
        return 'file-text';
      case 'PERMISSION_GRANTED':
        return 'shield-check';
      case 'PERMISSION_UPDATED':
      case 'PERMISSION_REVOKED':
        return 'shield';
      case 'TEAM_ADDED':
      case 'TEAM_REMOVED':
        return 'users-round';
      case 'TEAM_MANAGER_ASSIGNED':
        return 'crown';
      case 'DOCUMENT_VERSION_RESTORED':
        return 'refresh-cw';
      default:
        return 'bell';
    }
  }

  getNotificationCtaText(item: NotificationResponseDto): string | null {
    switch (item.type) {
      case 'ACCESS_REQUEST_SUBMITTED':
        return 'Review Request';
      case 'ACCESS_REQUEST_APPROVED':
      case 'ACCESS_REQUEST_DENIED':
        return 'View Access Request';
      case 'ORG_INVITATION_SENT':
        return 'View Invitation';
      case 'COLLABORATION_INVITE':
        return 'View Collaboration';
      case 'FILE_REQUEST_CREATED':
        return 'Upload Document';
      case 'PERMISSION_GRANTED':
        return 'View Shared Resource';
      case 'TEAM_ADDED':
        return 'View Team';
      default:
        return null;
    }
  }

  formatCategory(category: string): string {
    switch (category) {
      case 'ACCESS_REQUEST':
        return 'Access';
      case 'INVITATION':
        return 'Invitation';
      case 'COLLABORATION':
        return 'Collab';
      case 'SHARING':
        return 'Sharing';
      case 'FILE_REQUEST':
        return 'File Request';
      case 'SECURITY':
        return 'Security';
      case 'GOVERNANCE':
        return 'Governance';
      case 'TEAM':
        return 'Team';
      case 'WORKSPACE':
        return 'Workspace';
      default:
        return category;
    }
  }

  formatRelativeTime(isoString?: string): string {
    if (!isoString) return '';
    try {
      const date = new Date(isoString);
      const now = new Date();
      const diffMs = now.getTime() - date.getTime();
      const diffSec = Math.floor(diffMs / 1000);
      const diffMin = Math.floor(diffSec / 60);
      const diffHours = Math.floor(diffMin / 60);
      const diffDays = Math.floor(diffHours / 24);

      if (diffSec < 60) return 'Just now';
      if (diffMin < 60) return `${diffMin}m ago`;
      if (diffHours < 24) return `${diffHours}h ago`;
      if (diffDays === 1) return 'Yesterday';
      if (diffDays < 7) return `${diffDays}d ago`;

      return date.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined,
      });
    } catch {
      return '';
    }
  }

  getEmptyStateTitle(): string {
    if (this.activeTab() === 'ACTION_REQUIRED') {
      return 'No action required';
    }
    if (this.activeStatusFilter() === 'ARCHIVED') {
      return 'No archived notifications';
    }
    if (this.activeStatusFilter() === 'UNREAD') {
      return 'No unread notifications';
    }
    return 'No notifications found';
  }

  getEmptyStateDescription(): string {
    if (this.activeTab() === 'ACTION_REQUIRED') {
      return 'You are all caught up! You have no pending access requests or invitations requiring your attention.';
    }
    if (this.activeStatusFilter() === 'ARCHIVED') {
      return 'Notifications you archive will appear here for reference.';
    }
    if (this.activeStatusFilter() === 'UNREAD') {
      return 'You have read all your notifications.';
    }
    return 'When you receive invitations, access requests, or team updates, they will appear in this feed.';
  }
}
