import {
  Component,
  ChangeDetectionStrategy,
  output,
  inject,
  computed,
} from '@angular/core';
import { Router } from '@angular/router';
import { NotificationStore } from '../../state/notification.store';
import {
  NotificationResponseDto,
  NotificationType,
} from '../../../../core/models/notification-feed.model';
import { IconComponent, IconName } from '../../../../shared/components/icon/icon.component';
import { WorkspaceContextStore } from '../../../../state/workspace/workspace-context.store';
import { buildCanonicalRoute } from '../../../../core/models/feature-route.model';

@Component({
  selector: 'app-notification-popover',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent],
  template: `
    <div
      class="notification-popover"
      role="dialog"
      aria-label="Notifications"
      tabindex="-1"
      (click)="$event.stopPropagation()"
    >
      <!-- Popover Header -->
      <div class="popover-header">
        <div class="header-left">
          <h3 class="header-title">Notifications</h3>
          @if (store.unreadCount() > 0) {
            <span class="unread-pill">{{ store.unreadCount() }} new</span>
          } @else {
            <span class="caught-up-pill">All caught up</span>
          }
        </div>

        <div class="header-actions">
          @if (store.unreadCount() > 0) {
            <button
              class="action-text-btn"
              (click)="onMarkAllAsRead($event)"
              aria-label="Mark all as read"
              title="Mark all notifications as read"
            >
              <app-icon name="check" [size]="13" />
              <span>Mark all read</span>
            </button>
          }
          <button
            class="header-icon-btn expand-btn"
            (click)="onViewAllNotifications()"
            aria-label="Expand to Notification Center"
            title="Expand to Notification Center"
          >
            <app-icon name="external-link" [size]="14" />
          </button>
          <button
            class="close-btn"
            (click)="close.emit()"
            aria-label="Close notifications popover"
            title="Close"
          >
            <app-icon name="x" [size]="15" />
          </button>
        </div>
      </div>

      <!-- Popover Body / Feed Content -->
      <div class="popover-body custom-scrollbar" role="region" aria-label="Notification list">
        @if (store.isLoading() && !store.hasNotifications()) {
          <!-- Loading Skeleton -->
          <div class="skeleton-container" aria-busy="true" aria-label="Loading notifications">
            @for (i of [1, 2, 3]; track i) {
              <div class="skeleton-card">
                <div class="skeleton-icon pulse-animation"></div>
                <div class="skeleton-text-group">
                  <div class="skeleton-line skeleton-title pulse-animation"></div>
                  <div class="skeleton-line skeleton-desc pulse-animation"></div>
                </div>
              </div>
            }
          </div>
        } @else if (store.error() && !store.hasNotifications()) {
          <!-- Error State -->
          <div class="state-container error-state" role="alert">
            <div class="state-icon-wrap error-icon">
              <app-icon name="alert-circle" [size]="24" />
            </div>
            <p class="state-title">Unable to load notifications</p>
            <p class="state-desc">{{ store.error() }}</p>
            <button class="retry-btn" (click)="onRetry($event)">
              <app-icon name="refresh-cw" [size]="14" />
              <span>Try again</span>
            </button>
          </div>
        } @else if (!store.hasNotifications()) {
          <!-- Empty State -->
          <div class="state-container empty-state">
            <div class="state-icon-wrap empty-icon">
              <app-icon name="bell" [size]="26" />
            </div>
            <p class="state-title">No notifications yet</p>
            <p class="state-desc">
              You're all caught up! New access requests, invitations, and updates will appear here.
            </p>
          </div>
        } @else {
          <!-- Section 1: Action Required -->
          @if (actionRequiredItems().length > 0) {
            <div class="section-group action-required-group">
              <div class="section-header action-header">
                <div class="section-label-wrap">
                  <span class="action-alert-dot"></span>
                  <span class="section-title">Action Required</span>
                  <span class="action-count-badge">{{ actionRequiredItems().length }}</span>
                </div>
              </div>

              <div class="items-list">
                @for (item of actionRequiredItems(); track item.id) {
                  <div
                    class="notification-item action-item"
                    [class.unread-item]="item.status === 'UNREAD'"
                    (click)="onNotificationClick(item)"
                    (keydown.enter)="onNotificationClick(item)"
                    (keydown.space)="onNotificationClick(item)"
                    tabindex="0"
                    role="button"
                    [attr.aria-label]="item.title + ': ' + item.body"
                  >
                    <div class="item-icon-col">
                      <div class="icon-avatar action-icon-avatar">
                        <app-icon [name]="getNotificationIcon(item.type)" [size]="15" />
                      </div>
                    </div>

                    <div class="item-content-col">
                      <div class="item-top-row">
                        <span class="item-title">{{ item.title }}</span>
                        <div class="item-meta-right">
                          <span class="item-time font-tabular">{{ formatRelativeTime(item.createdAt) }}</span>
                          @if (item.status === 'UNREAD') {
                            <span class="unread-dot" title="Unread"></span>
                          }
                        </div>
                      </div>

                      <p class="item-body">{{ item.body }}</p>

                      <div class="item-action-row">
                        <button
                          class="primary-cta-btn"
                          (click)="onCtaClick($event, item)"
                          [attr.aria-label]="getNotificationCtaText(item)"
                        >
                          <span>{{ getNotificationCtaText(item) }}</span>
                          <app-icon name="arrow-right" [size]="12" />
                        </button>

                        <button
                          class="status-toggle-btn"
                          (click)="onToggleReadStatus($event, item)"
                          [attr.aria-label]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                          [title]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                        >
                          <app-icon [name]="item.status === 'UNREAD' ? 'check' : 'mail'" [size]="13" />
                        </button>
                      </div>
                    </div>
                  </div>
                }
              </div>
            </div>
          }

          <!-- Section 2: Invitations & Collaboration -->
          @if (invitationItems().length > 0) {
            <div class="section-group">
              <div class="section-header">
                <span class="section-title">Invitations & Collaboration</span>
                <span class="section-count-badge">{{ invitationItems().length }}</span>
              </div>

              <div class="items-list">
                @for (item of invitationItems(); track item.id) {
                  <div
                    class="notification-item"
                    [class.unread-item]="item.status === 'UNREAD'"
                    (click)="onNotificationClick(item)"
                    (keydown.enter)="onNotificationClick(item)"
                    (keydown.space)="onNotificationClick(item)"
                    tabindex="0"
                    role="button"
                    [attr.aria-label]="item.title + ': ' + item.body"
                  >
                    <div class="item-icon-col">
                      <div class="icon-avatar invitation-icon-avatar">
                        <app-icon [name]="getNotificationIcon(item.type)" [size]="15" />
                      </div>
                    </div>

                    <div class="item-content-col">
                      <div class="item-top-row">
                        <span class="item-title">{{ item.title }}</span>
                        <div class="item-meta-right">
                          <span class="item-time font-tabular">{{ formatRelativeTime(item.createdAt) }}</span>
                          @if (item.status === 'UNREAD') {
                            <span class="unread-dot" title="Unread"></span>
                          }
                        </div>
                      </div>

                      <p class="item-body">{{ item.body }}</p>

                      <div class="item-action-row">
                        <button
                          class="status-toggle-btn"
                          (click)="onToggleReadStatus($event, item)"
                          [attr.aria-label]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                          [title]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                        >
                          <app-icon [name]="item.status === 'UNREAD' ? 'check' : 'mail'" [size]="13" />
                          <span class="toggle-text">{{ item.status === 'UNREAD' ? 'Mark read' : 'Mark unread' }}</span>
                        </button>
                      </div>
                    </div>
                  </div>
                }
              </div>
            </div>
          }

          <!-- Section 3: Recent Activity -->
          @if (recentGeneralItems().length > 0) {
            <div class="section-group">
              <div class="section-header">
                <span class="section-title">Recent Updates</span>
              </div>

              <div class="items-list">
                @for (item of recentGeneralItems(); track item.id) {
                  <div
                    class="notification-item"
                    [class.unread-item]="item.status === 'UNREAD'"
                    (click)="onNotificationClick(item)"
                    (keydown.enter)="onNotificationClick(item)"
                    (keydown.space)="onNotificationClick(item)"
                    tabindex="0"
                    role="button"
                    [attr.aria-label]="item.title + ': ' + item.body"
                  >
                    <div class="item-icon-col">
                      <div class="icon-avatar general-icon-avatar">
                        <app-icon [name]="getNotificationIcon(item.type)" [size]="15" />
                      </div>
                    </div>

                    <div class="item-content-col">
                      <div class="item-top-row">
                        <span class="item-title">{{ item.title }}</span>
                        <div class="item-meta-right">
                          <span class="item-time font-tabular">{{ formatRelativeTime(item.createdAt) }}</span>
                          @if (item.status === 'UNREAD') {
                            <span class="unread-dot" title="Unread"></span>
                          }
                        </div>
                      </div>

                      <p class="item-body">{{ item.body }}</p>

                      <div class="item-action-row">
                        <button
                          class="status-toggle-btn"
                          (click)="onToggleReadStatus($event, item)"
                          [attr.aria-label]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                          [title]="item.status === 'UNREAD' ? 'Mark as read' : 'Mark as unread'"
                        >
                          <app-icon [name]="item.status === 'UNREAD' ? 'check' : 'mail'" [size]="13" />
                          <span class="toggle-text">{{ item.status === 'UNREAD' ? 'Mark read' : 'Mark unread' }}</span>
                        </button>
                      </div>
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        }
      </div>

      <!-- Popover Footer -->
      <div class="popover-footer">
        <button
          class="view-all-btn"
          (click)="onViewAllNotifications()"
          aria-label="View all notifications"
        >
          <span>View all notifications</span>
          <app-icon name="arrow-right" [size]="14" />
        </button>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
    }

    .notification-popover {
      width: 370px;
      max-width: calc(100vw - 2rem);
      max-height: 440px;
      display: flex;
      flex-direction: column;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 12px;
      box-shadow: 0 14px 34px rgba(15, 23, 42, 0.14), 0 4px 10px rgba(15, 23, 42, 0.05);
      outline: none;
      overflow: hidden;
      font-family: inherit;
    }

    /* Header */
    .popover-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.65rem 0.85rem;
      border-bottom: 1px solid #f1f5f9;
      background: #ffffff;
      flex-shrink: 0;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .header-title {
      margin: 0;
      font-size: 0.875rem;
      font-weight: 600;
      color: #0f172a;
      letter-spacing: -0.01em;
    }

    .unread-pill {
      display: inline-flex;
      align-items: center;
      padding: 0.1rem 0.45rem;
      font-size: 0.65rem;
      font-weight: 700;
      color: #ea580c;
      background: #fff7ed;
      border: 1px solid #ffedd5;
      border-radius: 999px;
    }

    .caught-up-pill {
      display: inline-flex;
      align-items: center;
      padding: 0.1rem 0.45rem;
      font-size: 0.65rem;
      font-weight: 600;
      color: #64748b;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 999px;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.25rem;
    }

    .action-text-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 6px;
      padding: 0.2rem 0.4rem;
      font-size: 0.71875rem;
      font-weight: 500;
      color: #64748b;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .action-text-btn:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    .header-icon-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      background: transparent;
      border: none;
      border-radius: 6px;
      color: #64748b;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .header-icon-btn:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    .close-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 26px;
      height: 26px;
      background: transparent;
      border: none;
      border-radius: 6px;
      color: #94a3b8;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .close-btn:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    /* Body */
    .popover-body {
      flex: 1;
      overflow-y: auto;
      max-height: 345px;
    }

    .custom-scrollbar::-webkit-scrollbar {
      width: 6px;
    }
    .custom-scrollbar::-webkit-scrollbar-track {
      background: transparent;
    }
    .custom-scrollbar::-webkit-scrollbar-thumb {
      background: #cbd5e1;
      border-radius: 4px;
    }
    .custom-scrollbar::-webkit-scrollbar-thumb:hover {
      background: #94a3b8;
    }

    /* State Views (Empty / Error / Skeleton) */
    .state-container {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 2.5rem 1.5rem;
    }

    .state-icon-wrap {
      width: 48px;
      height: 48px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 0.75rem;
    }

    .empty-icon {
      background: #f8fafc;
      color: #94a3b8;
      border: 1px solid #e2e8f0;
    }

    .error-icon {
      background: #fef2f2;
      color: #ef4444;
      border: 1px solid #fee2e2;
    }

    .state-title {
      margin: 0 0 0.35rem 0;
      font-size: 0.875rem;
      font-weight: 600;
      color: #1e293b;
    }

    .state-desc {
      margin: 0;
      font-size: 0.78125rem;
      color: #64748b;
      line-height: 1.4;
      max-width: 280px;
    }

    .retry-btn {
      margin-top: 0.85rem;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      padding: 0.35rem 0.75rem;
      font-size: 0.78125rem;
      font-weight: 500;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .retry-btn:hover {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    /* Skeleton Loading */
    .skeleton-container {
      padding: 0.75rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .skeleton-card {
      display: flex;
      gap: 0.75rem;
      align-items: flex-start;
      padding: 0.5rem 0;
    }

    .skeleton-icon {
      width: 32px;
      height: 32px;
      border-radius: 8px;
      background: #e2e8f0;
      flex-shrink: 0;
    }

    .skeleton-text-group {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }

    .skeleton-line {
      border-radius: 4px;
      background: #e2e8f0;
    }

    .skeleton-title {
      height: 14px;
      width: 60%;
    }

    .skeleton-desc {
      height: 12px;
      width: 90%;
    }

    @keyframes pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.4; }
    }

    .pulse-animation {
      animation: pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
    }

    /* Section Group */
    .section-group {
      border-bottom: 1px solid #f1f5f9;
    }

    .section-group:last-child {
      border-bottom: none;
    }

    .action-required-group {
      background: #fffcf7;
      border-bottom: 1px solid #fed7aa;
    }

    .section-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.35rem 0.85rem 0.2rem;
    }

    .section-label-wrap {
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }

    .action-alert-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #f97316;
    }

    .section-title {
      font-size: 0.65rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
    }

    .action-header .section-title {
      color: #c2410c;
    }

    .section-count-badge {
      font-size: 0.625rem;
      font-weight: 600;
      color: #64748b;
      background: #e2e8f0;
      padding: 0.05rem 0.35rem;
      border-radius: 999px;
    }

    .action-count-badge {
      font-size: 0.625rem;
      font-weight: 700;
      color: #ffffff;
      background: #ea580c;
      padding: 0.05rem 0.35rem;
      border-radius: 999px;
    }

    /* Items List */
    .items-list {
      display: flex;
      flex-direction: column;
    }

    .notification-item {
      display: flex;
      gap: 0.6rem;
      padding: 0.5rem 0.85rem;
      border-bottom: 1px solid rgba(241, 245, 249, 0.8);
      cursor: pointer;
      transition: background 0.12s ease;
      outline: none;
    }

    .notification-item:last-child {
      border-bottom: none;
    }

    .notification-item:hover,
    .notification-item:focus-visible {
      background: #f8fafc;
    }

    .action-item {
      background: #fffcf7;
    }

    .action-item:hover,
    .action-item:focus-visible {
      background: #fff7ed;
    }

    .unread-item {
      background: #fefefe;
    }

    .notification-item:focus-visible {
      outline: 2px solid #f97316;
      outline-offset: -2px;
    }

    /* Icon Avatar */
    .item-icon-col {
      flex-shrink: 0;
      padding-top: 0.1rem;
    }

    .icon-avatar {
      width: 26px;
      height: 26px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .action-icon-avatar {
      background: #fff7ed;
      color: #ea580c;
      border: 1px solid #ffedd5;
    }

    .invitation-icon-avatar {
      background: #f0fdf4;
      color: #16a34a;
      border: 1px solid #dcfce7;
    }

    .general-icon-avatar {
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
    }

    /* Content Column */
    .item-content-col {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .item-top-row {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 0.4rem;
    }

    .item-title {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
      line-height: 1.3;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .item-meta-right {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      flex-shrink: 0;
    }

    .item-time {
      font-size: 0.6875rem;
      color: #94a3b8;
    }

    .font-tabular {
      font-variant-numeric: tabular-nums;
    }

    .unread-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #f97316;
      flex-shrink: 0;
    }

    .item-body {
      margin: 0;
      font-size: 0.75rem;
      color: #64748b;
      line-height: 1.3;
      display: -webkit-box;
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }

    .item-action-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
      margin-top: 0.25rem;
    }

    .primary-cta-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      background: #ffffff;
      border: 1px solid #fdba74;
      border-radius: 6px;
      padding: 0.2rem 0.5rem;
      font-size: 0.71875rem;
      font-weight: 600;
      color: #c2410c;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .primary-cta-btn:hover {
      background: #ea580c;
      border-color: #ea580c;
      color: #ffffff;
    }

    .status-toggle-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      background: transparent;
      border: none;
      border-radius: 4px;
      padding: 0.15rem 0.35rem;
      font-size: 0.6875rem;
      color: #94a3b8;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .status-toggle-btn:hover {
      background: #f1f5f9;
      color: #334155;
    }

    .toggle-text {
      font-size: 0.65rem;
    }

    /* Footer */
    .popover-footer {
      padding: 0.5rem 0.85rem;
      border-top: 1px solid #f1f5f9;
      background: #fafafa;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .view-all-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      background: transparent;
      border: none;
      font-size: 0.78125rem;
      font-weight: 600;
      color: #ea580c;
      cursor: pointer;
      transition: color 0.15s ease;
      padding: 0.2rem 0.5rem;
      border-radius: 6px;
    }

    .view-all-btn:hover {
      color: #c2410c;
      background: rgba(234, 88, 12, 0.06);
    }
  `],
})
export class NotificationPopoverComponent {
  readonly store = inject(NotificationStore);
  private readonly router = inject(Router);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);

  readonly close = output<void>();

  // ── Computed Groupings ──

  readonly actionRequiredItems = computed(() => this.store.actionRequiredNotifications());

  readonly invitationItems = computed(() => {
    const actionRequiredIds = new Set(this.actionRequiredItems().map((n) => n.id));
    return this.store
      .invitationNotifications()
      .filter((n) => !actionRequiredIds.has(n.id));
  });

  readonly recentGeneralItems = computed(() => {
    const actionRequiredIds = new Set(this.actionRequiredItems().map((n) => n.id));
    const invitationIds = new Set(this.invitationItems().map((n) => n.id));

    return this.store
      .notifications()
      .filter((n) => !actionRequiredIds.has(n.id) && !invitationIds.has(n.id))
      .slice(0, 8);
  });

  // ── Actions ──

  onNotificationClick(item: NotificationResponseDto): void {
    if (item.status === 'UNREAD') {
      this.store.markAsRead(item.id);
    }
    this.navigateForNotification(item);
    this.close.emit();
  }

  onCtaClick(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    if (item.status === 'UNREAD') {
      this.store.markAsRead(item.id);
    }
    this.navigateForNotification(item);
    this.close.emit();
  }

  onToggleReadStatus(event: MouseEvent, item: NotificationResponseDto): void {
    event.stopPropagation();
    if (item.status === 'UNREAD') {
      this.store.markAsRead(item.id);
    } else {
      this.store.markAsUnread(item.id);
    }
  }

  onMarkAllAsRead(event: MouseEvent): void {
    event.stopPropagation();
    this.store.markAllAsRead();
  }

  onRetry(event: MouseEvent): void {
    event.stopPropagation();
    this.store.loadNotifications(0, true);
  }

  onViewAllNotifications(): void {
    this.router.navigate(['/w', 'personal', 'notifications']);
    this.close.emit();
  }

  // ── Navigation & Helpers ──

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
        this.router.navigate(['/w', 'personal', 'dashboard']);
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

  getNotificationCtaText(item: NotificationResponseDto): string {
    switch (item.type) {
      case 'ACCESS_REQUEST_SUBMITTED':
        return 'Review Request';
      case 'ORG_INVITATION_SENT':
        return 'View Invitation';
      case 'COLLABORATION_INVITE':
        return 'View Collaboration';
      case 'FILE_REQUEST_CREATED':
        return 'Upload Document';
      default:
        return 'View Details';
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

      return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    } catch {
      return '';
    }
  }
}
