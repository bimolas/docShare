import {
  Component,
  ChangeDetectionStrategy,
  output,
  inject,
  computed,
  HostListener,
  ViewChild,
  ElementRef,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { OverlayModule, ConnectedPosition } from '@angular/cdk/overlay';
import { AuthStore } from '../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { NotificationStore } from '../../features/notifications/state/notification.store';
import { NotificationPopoverComponent } from '../../features/notifications/components/notification-popover/notification-popover.component';
import { IconComponent } from '../../shared/components/icon/icon.component';
import { buildCanonicalRoute } from '../../core/models/feature-route.model';

export const NOTIFICATION_OVERLAY_POSITIONS: ConnectedPosition[] = [
  { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 8 },
  { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 8 },
  { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -8 },
];

/**
 * TopNavComponent: Modern enterprise application top bar.
 * Displays sidebar toggle, contextual breadcrumbs, global search bar, notification badge, and user session actions.
 */
@Component({
  selector: 'app-top-nav',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IconComponent, OverlayModule, NotificationPopoverComponent],
  template: `
    <header class="top-nav">
      <!-- Left Section: Sidebar Toggle & Context Breadcrumb -->
      <div class="top-nav-left">
        <button
          class="menu-toggle-btn"
          (click)="toggleSidebar.emit()"
          aria-label="Toggle sidebar menu"
          title="Toggle Sidebar (Ctrl+B)"
        >
          <app-icon name="panel-left" [size]="18" />
        </button>

        <nav class="breadcrumb-container" aria-label="Breadcrumb navigation">
          @if (currentDiscoveryFeature()) {
            <div class="breadcrumb-item personal-item">
              <app-icon name="user-round" [size]="14" class="breadcrumb-icon" />
              <span class="breadcrumb-label">Personal Space</span>
            </div>
            <app-icon name="chevron-right" [size]="14" class="breadcrumb-arrow" />
            <div class="breadcrumb-item discovery-item">
              <app-icon [name]="currentDiscoveryFeature() === 'Shared With Me' ? 'inbox' : 'send'" [size]="14" class="breadcrumb-icon" />
              <span class="breadcrumb-label">{{ currentDiscoveryFeature() }}</span>
            </div>
          } @else if (isPersonal()) {
            <div class="breadcrumb-item personal-item">
              <app-icon name="user-round" [size]="14" class="breadcrumb-icon" />
              <span class="breadcrumb-label">Personal Space</span>
            </div>

            @if (activeWorkspaceName()) {
              <app-icon name="chevron-right" [size]="14" class="breadcrumb-arrow" />
              <div class="breadcrumb-item workspace-item">
                <app-icon name="briefcase" [size]="14" class="breadcrumb-icon" />
                <span class="breadcrumb-label">{{ activeWorkspaceName() }}</span>
              </div>
            }
          } @else {
            <div class="breadcrumb-item organization-item">
              <app-icon name="building-2" [size]="14" class="breadcrumb-icon" />
              <span class="breadcrumb-label">{{ activeOrganizationName() }}</span>
            </div>

            @if (activeWorkspaceName()) {
              <app-icon name="chevron-right" [size]="14" class="breadcrumb-arrow" />
              <div class="breadcrumb-item workspace-item">
                <app-icon name="briefcase" [size]="14" class="breadcrumb-icon" />
                <span class="breadcrumb-label">{{ activeWorkspaceName() }}</span>
              </div>
            }
          }
        </nav>
      </div>

      <!-- Center Section: Quick Search Trigger -->
      <div class="top-nav-center">
        <button class="search-bar-trigger" aria-label="Quick search">
          <app-icon name="search" [size]="16" class="search-icon" />
          <span class="search-placeholder">Search documents, folders, members...</span>
          <kbd class="search-shortcut">⌘K</kbd>
        </button>
      </div>

      <!-- Right Section: Notifications, Profile & Actions -->
      <div class="top-nav-right">
        <div class="notification-trigger-wrap" cdkOverlayOrigin #notificationTrigger="cdkOverlayOrigin">
          <button
            #bellBtn
            class="icon-action-btn notification-btn"
            [class.has-badge]="notificationCount() > 0"
            [class.is-open]="notificationStore.isPopoverOpen()"
            (click)="toggleNotifications()"
            aria-label="Notifications"
            [attr.aria-expanded]="notificationStore.isPopoverOpen()"
            aria-haspopup="dialog"
            aria-controls="notification-popover-panel"
            title="Notifications"
          >
            <app-icon name="bell" [size]="18" />
            @if (notificationCount() > 0) {
              <span class="notification-badge" aria-hidden="true">{{ notificationCount() > 99 ? '99+' : notificationCount() }}</span>
            }
          </button>

          <ng-template
            cdkConnectedOverlay
            [cdkConnectedOverlayOrigin]="notificationTrigger"
            [cdkConnectedOverlayOpen]="notificationStore.isPopoverOpen()"
            [cdkConnectedOverlayPositions]="overlayPositions"
            [cdkConnectedOverlayHasBackdrop]="true"
            [cdkConnectedOverlayBackdropClass]="'cdk-overlay-transparent-backdrop'"
            (backdropClick)="closeNotifications()"
            (detach)="closeNotifications()"
          >
            <app-notification-popover
              id="notification-popover-panel"
              (close)="closeNotifications()"
            />
          </ng-template>
        </div>

        <div class="divider-v"></div>

        <div class="user-profile-badge" [attr.title]="userFullName()">
          <div class="user-avatar">
            {{ userInitials() }}
          </div>
          <span class="user-name">{{ userFirstName() }}</span>
        </div>

        <button class="logout-action-btn" (click)="logout()" aria-label="Sign out" title="Sign Out">
          <app-icon name="log-out" [size]="16" />
          <span>Sign out</span>
        </button>
      </div>
    </header>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .top-nav {
      height: 60px;
      background: var(--color-surface, #ffffff);
      border-bottom: 1px solid var(--color-border, #e5e7eb);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 1.25rem;
      gap: 1rem;
      user-select: none;
    }

    /* Left Section */
    .top-nav-left {
      display: flex;
      align-items: center;
      gap: 1rem;
      min-width: 0;
    }

    .menu-toggle-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border: 1px solid transparent;
      border-radius: 8px;
      background: transparent;
      color: var(--color-text-muted, #6b7280);
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .menu-toggle-btn:hover {
      background: rgba(0, 0, 0, 0.04);
      color: var(--color-text, #111827);
      border-color: var(--color-border, #e5e7eb);
    }

    .breadcrumb-container {
      display: flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.8125rem;
      color: var(--color-text-muted, #6b7280);
      overflow: hidden;
      white-space: nowrap;
    }

    .breadcrumb-item {
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      padding: 0.25rem 0.5rem;
      border-radius: 6px;
      transition: background 0.15s ease;
    }

    .workspace-item,
    .discovery-item {
      font-weight: 600;
      color: var(--color-text, #111827);
    }

    .subspace-item {
      font-weight: 500;
      color: var(--color-text-muted, #4b5563);
    }

    .breadcrumb-icon {
      color: var(--color-accent, #f97316);
      opacity: 0.9;
    }

    .breadcrumb-arrow {
      color: #9ca3af;
      flex-shrink: 0;
    }

    /* Center Section: Search Bar Trigger */
    .top-nav-center {
      flex: 1;
      max-width: 380px;
      display: flex;
      justify-content: center;
    }

    .search-bar-trigger {
      width: 100%;
      height: 36px;
      display: flex;
      align-items: center;
      gap: 0.6rem;
      padding: 0 0.85rem;
      background: #f8fafc;
      border: 1px solid var(--color-border, #e2e8f0);
      border-radius: 8px;
      color: #94a3b8;
      font-size: 0.8125rem;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .search-bar-trigger:hover {
      background: #ffffff;
      border-color: #cbd5e1;
      color: #64748b;
      box-shadow: 0 1px 3px rgba(0,0,0,0.04);
    }

    .search-icon {
      color: #94a3b8;
      flex-shrink: 0;
    }

    .search-placeholder {
      flex: 1;
      text-align: left;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .search-shortcut {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 0.6875rem;
      font-weight: 600;
      color: #94a3b8;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 4px;
      padding: 0.1rem 0.35rem;
    }

    /* Right Section */
    .top-nav-right {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-shrink: 0;
    }

    .icon-action-btn {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border: 1px solid transparent;
      border-radius: 8px;
      background: transparent;
      color: var(--color-text-muted, #64748b);
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .icon-action-btn:hover {
      background: rgba(0, 0, 0, 0.04);
      color: var(--color-text, #0f172a);
    }

    .notification-badge {
      position: absolute;
      top: 3px;
      right: 3px;
      background: var(--color-accent, #f97316);
      color: #ffffff;
      font-size: 0.65rem;
      font-weight: 700;
      line-height: 1;
      padding: 2px 5px;
      border-radius: 999px;
      border: 2px solid #ffffff;
    }

    .notification-trigger-wrap {
      position: relative;
      display: inline-flex;
      align-items: center;
    }

    .notification-btn.is-open {
      background: rgba(0, 0, 0, 0.06);
      color: var(--color-accent, #f97316);
    }

    .divider-v {
      width: 1px;
      height: 20px;
      background: var(--color-border, #e2e8f0);
      margin: 0 0.1rem;
    }

    .user-profile-badge {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.25rem 0.5rem 0.25rem 0.25rem;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.15s ease;
    }

    .user-profile-badge:hover {
      background: rgba(0, 0, 0, 0.03);
    }

    .user-avatar {
      width: 30px;
      height: 30px;
      border-radius: 50%;
      background: linear-gradient(135deg, var(--color-accent, #f97316), #ea580c);
      color: #ffffff;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      font-weight: 700;
      letter-spacing: 0.02em;
      box-shadow: 0 1px 3px rgba(249, 115, 22, 0.25);
    }

    .user-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--color-text, #1e293b);
    }

    .logout-action-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      background: transparent;
      border: 1px solid var(--color-border, #e2e8f0);
      border-radius: 8px;
      padding: 0.4rem 0.75rem;
      font-size: 0.78125rem;
      font-weight: 500;
      color: var(--color-text-muted, #64748b);
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .logout-action-btn:hover {
      border-color: var(--color-accent, #f97316);
      color: var(--color-accent, #f97316);
      background: rgba(249, 115, 22, 0.04);
    }

    @media (max-width: 768px) {
      .top-nav-center {
        display: none;
      }
      .user-name {
        display: none;
      }
    }
  `],
})
export class TopNavComponent implements OnInit {
  private readonly authStore = inject(AuthStore);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  readonly notificationStore = inject(NotificationStore);
  private readonly router = inject(Router);

  readonly toggleSidebar = output<void>();

  @ViewChild('bellBtn', { read: ElementRef }) bellBtn?: ElementRef<HTMLButtonElement>;

  readonly overlayPositions = NOTIFICATION_OVERLAY_POSITIONS;

  ngOnInit(): void {
    this.notificationStore.refreshUnreadCount();
  }

  toggleNotifications(): void {
    this.notificationStore.togglePopover();
  }

  closeNotifications(): void {
    this.notificationStore.closePopover();
    this.bellBtn?.nativeElement?.focus();
  }

  @HostListener('window:keydown.escape', ['$event'])
  onEscKey(event: Event): void {
    if (this.notificationStore.isPopoverOpen()) {
      event.preventDefault();
      this.closeNotifications();
    }
  }

  readonly currentDiscoveryFeature = computed(() => {
    const url = this.router.url;
    if (url.includes('shared-with-me')) return 'Shared With Me';
    if (url.includes('shared-by-me')) return 'Shared By Me';
    return null;
  });

  readonly userFullName = computed(() => this.authStore.currentUser()?.fullName ?? '');
  readonly userFirstName = computed(() => {
    const name = this.userFullName();
    return name ? name.split(' ')[0] : 'User';
  });
  readonly userInitials = this.authStore.userInitials;

  readonly isPersonal = computed(() => !this.workspaceContextStore.activeOrganization());

  readonly activeOrganizationName = computed(
    () => this.workspaceContextStore.activeOrganization()?.name ?? ''
  );

  readonly activeWorkspaceName = computed(
    () => this.workspaceContextStore.activeWorkspace()?.name ?? null
  );

  readonly notificationCount = computed(
    () => this.notificationStore.unreadCount()
  );

  readonly unreadCount = computed(
    () => this.notificationStore.unreadCount()
  );

  logout(): void {
    this.authStore.logout();
  }
}
