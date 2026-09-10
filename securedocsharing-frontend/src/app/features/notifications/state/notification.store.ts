import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NotificationFeedApiService } from '../../../core/services/notification-feed-api.service';
import {
  NotificationResponseDto,
  NotificationStatus,
  NotificationCategory,
  NotificationType,
  NotificationSourceType,
} from '../../../core/models/notification-feed.model';

export const ACTION_REQUIRED_TYPES: ReadonlySet<NotificationType> = new Set<NotificationType>([
  'ACCESS_REQUEST_SUBMITTED',
  'ORG_INVITATION_SENT',
  'COLLABORATION_INVITE',
  'FILE_REQUEST_CREATED',
]);

export const RESOLUTION_COUNTER_EVENT_TYPES: ReadonlySet<NotificationType> = new Set<NotificationType>([
  'ACCESS_REQUEST_APPROVED',
  'ACCESS_REQUEST_DENIED',
  'ACCESS_REQUEST_CANCELLED',
  'ORG_INVITATION_ACCEPTED',
  'ORG_INVITATION_DECLINED',
  'ORG_INVITATION_REVOKED',
  'ONBOARDING_COMPLETED',
  'COLLABORATION_ACCEPTED',
  'COLLABORATION_REJECTED',
  'COLLABORATION_EXPIRED',
  'COLLABORATION_REVOKED',
  'FILE_REQUEST_UPLOADED',
  'FILE_REQUEST_FULFILLED',
  'FILE_REQUEST_CANCELLED',
]);

@Injectable({ providedIn: 'root' })
export class NotificationStore {
  private readonly api = inject(NotificationFeedApiService);

  // ── State Signals ──
  readonly notifications = signal<NotificationResponseDto[]>([]);
  readonly unreadCount = signal<number>(0);
  readonly totalElements = signal<number>(0);
  readonly totalPages = signal<number>(0);
  readonly page = signal<number>(0);
  readonly size = signal<number>(20);

  readonly activeCategory = signal<NotificationCategory | null>(null);
  readonly activeStatus = signal<NotificationStatus | null>(null);

  // In-memory tracking of completed/resolved business actions
  readonly resolvedActionKeys = signal<Set<string>>(new Set());
  readonly resolvedNotificationIds = signal<Set<number>>(new Set());

  readonly isLoading = signal<boolean>(false);
  readonly isRefreshing = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly isPopoverOpen = signal<boolean>(false);

  // ── Computed Signals ──
  readonly hasNotifications = computed(() => this.notifications().length > 0);
  readonly hasUnread = computed(() => this.unreadCount() > 0);

  readonly actionRequiredNotifications = computed(() => {
    const items = this.notifications();
    const resolvedKeys = this.resolvedActionKeys();
    const resolvedIds = this.resolvedNotificationIds();

    // Map source resolution counter-events present in the active feed
    const counterEventKeys = new Set<string>();
    for (const n of items) {
      if (RESOLUTION_COUNTER_EVENT_TYPES.has(n.type) && n.sourceType && n.sourceId) {
        counterEventKeys.add(`${n.sourceType}:${n.sourceId}`);
      }
    }

    return items.filter((n) => {
      // 1. Excluded from primary Action Required if ARCHIVED
      if (n.status === 'ARCHIVED') {
        return false;
      }

      // 2. Must be an actionable notification type
      if (!ACTION_REQUIRED_TYPES.has(n.type)) {
        return false;
      }

      // 3. If explicit actionPending boolean is supplied by backend DTO, honor it
      if (n.actionPending !== undefined && !n.actionPending) {
        return false;
      }

      // 4. Check if marked resolved by notification ID
      if (resolvedIds.has(n.id)) {
        return false;
      }

      // 5. Check if marked resolved by source key or counter-event
      const sourceKey = n.sourceType && n.sourceId ? `${n.sourceType}:${n.sourceId}` : null;
      if (sourceKey && (resolvedKeys.has(sourceKey) || counterEventKeys.has(sourceKey))) {
        return false;
      }

      // 6. Eligible when UNREAD or READ and pending action
      return n.status === 'UNREAD' || n.status === 'READ';
    });
  });

  readonly invitationNotifications = computed(() =>
    this.notifications().filter(
      (n) => n.category === 'INVITATION' || n.category === 'COLLABORATION'
    )
  );

  readonly recentNotifications = computed(() => this.notifications().slice(0, 5));

  // ── Load & Refresh ──

  loadNotifications(page: number = 0, isRefresh: boolean = false): void {
    if (isRefresh) {
      this.isRefreshing.set(true);
    } else {
      this.isLoading.set(true);
    }
    this.error.set(null);
    this.page.set(page);

    this.api
      .getNotifications({
        category: this.activeCategory() || undefined,
        status: this.activeStatus() || undefined,
        page,
        size: this.size(),
        sort: 'createdAt,desc',
      })
      .subscribe({
        next: (res) => {
          this.notifications.set(res.content || []);
          this.totalElements.set(res.totalElements || 0);
          this.totalPages.set(res.totalPages || 0);
          this.isLoading.set(false);
          this.isRefreshing.set(false);
        },
        error: (err: HttpErrorResponse) => {
          this.isLoading.set(false);
          this.isRefreshing.set(false);
          this.error.set(
            err.error?.message || 'Failed to load notifications. Please try again.'
          );
        },
      });

    // Keep unread count fresh
    this.refreshUnreadCount();
  }

  refreshUnreadCount(): void {
    this.api.getUnreadCount().subscribe({
      next: (res) => {
        this.unreadCount.set(res.unreadCount || 0);
      },
      error: () => {
        // Non-blocking error for unread count
      },
    });
  }

  // ── Filters ──

  setCategoryFilter(category: NotificationCategory | null): void {
    this.activeCategory.set(category);
    this.loadNotifications(0);
  }

  setStatusFilter(status: NotificationStatus | null): void {
    this.activeStatus.set(status);
    this.loadNotifications(0);
  }

  // ── Actions ──

  markAsRead(id: number): void {
    this.api.markAsRead(id).subscribe({
      next: (updated) => {
        this.notifications.update((items) =>
          items.map((n) => (n.id === id ? { ...n, status: 'READ' as NotificationStatus, readAt: updated.readAt } : n))
        );
        this.unreadCount.update((c) => Math.max(0, c - 1));
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.error?.message || 'Failed to mark notification as read.');
      },
    });
  }

  markAsUnread(id: number): void {
    this.api.markAsUnread(id).subscribe({
      next: () => {
        this.notifications.update((items) =>
          items.map((n) => (n.id === id ? { ...n, status: 'UNREAD' as NotificationStatus, readAt: undefined } : n))
        );
        this.unreadCount.update((c) => c + 1);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.error?.message || 'Failed to mark notification as unread.');
      },
    });
  }

  markAllAsRead(): void {
    this.api.markAllAsRead().subscribe({
      next: () => {
        this.notifications.update((items) =>
          items.map((n) => ({ ...n, status: 'READ' as NotificationStatus, readAt: new Date().toISOString() }))
        );
        this.unreadCount.set(0);
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.error?.message || 'Failed to mark all as read.');
      },
    });
  }

  archiveNotification(id: number): void {
    const existing = this.notifications().find((n) => n.id === id);
    this.api.archiveNotification(id).subscribe({
      next: (updated) => {
        this.notifications.update((items) =>
          items.map((n) =>
            n.id === id ? { ...n, status: 'ARCHIVED' as NotificationStatus, archivedAt: updated.archivedAt } : n
          )
        );
        if (existing && existing.status === 'UNREAD') {
          this.unreadCount.update((c) => Math.max(0, c - 1));
        }
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.error?.message || 'Failed to archive notification.');
      },
    });
  }

  unarchiveNotification(id: number): void {
    this.api.unarchiveNotification(id).subscribe({
      next: () => {
        this.notifications.update((items) =>
          items.map((n) => (n.id === id ? { ...n, status: 'READ' as NotificationStatus, archivedAt: undefined } : n))
        );
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.error?.message || 'Failed to unarchive notification.');
      },
    });
  }

  deleteNotification(id: number): void {
    const existing = this.notifications().find((n) => n.id === id);
    this.api.deleteNotification(id).subscribe({
      next: () => {
        this.notifications.update((items) => items.filter((n) => n.id !== id));
        this.totalElements.update((t) => Math.max(0, t - 1));
        if (existing && existing.status === 'UNREAD') {
          this.unreadCount.update((c) => Math.max(0, c - 1));
        }
      },
      error: (err: HttpErrorResponse) => {
        this.error.set(err.error?.message || 'Failed to delete notification.');
      },
    });
  }

  // ── Action Resolution Tracking ──

  markActionResolved(sourceType: NotificationSourceType, sourceId: string): void {
    const key = `${sourceType}:${sourceId}`;
    this.resolvedActionKeys.update((keys) => {
      const next = new Set(keys);
      next.add(key);
      return next;
    });
  }

  markNotificationActionResolved(notificationId: number): void {
    this.resolvedNotificationIds.update((ids) => {
      const next = new Set(ids);
      next.add(notificationId);
      return next;
    });
  }

  // ── Popover Controls ──

  openPopover(): void {
    this.isPopoverOpen.set(true);
    if (this.notifications().length === 0) {
      this.loadNotifications(0);
    } else {
      this.refreshUnreadCount();
    }
  }

  closePopover(): void {
    this.isPopoverOpen.set(false);
  }

  togglePopover(): void {
    if (this.isPopoverOpen()) {
      this.closePopover();
    } else {
      this.openPopover();
    }
  }
}
