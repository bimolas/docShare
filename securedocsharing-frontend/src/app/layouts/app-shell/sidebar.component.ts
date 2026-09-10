import {
  Component,
  ChangeDetectionStrategy,
  inject,
  computed,
  signal,
  HostListener,
  ElementRef,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive, NavigationEnd } from '@angular/router';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { AuthStore } from '../../state/auth/auth.store';
import { LayoutStore } from '../../state/layout/layout.store';

import { IconComponent, IconName } from '../../shared/components/icon/icon.component';
import { filter } from 'rxjs';
import { OrganizationApiService } from '../../core/services/organization-api.service';
import { AccessRequestApiService } from '../../core/services/access-request-api.service';
import { ProjectWorkspaceModel } from '../../core/models/project-workspace.model';
import { hasOrganizationCapability } from '../../core/models/organization-capability.model';
import { NotificationStore } from '../../features/notifications/state/notification.store';
import {
  isWorkspaceContentFeature,
  isPersonalSpaceFeature,
  isOrganizationAdminFeature,
  isSharedAccountFeature,
  isOrgOnlyFeature,
  buildCanonicalRoute,
  WORKSPACE_CONTENT_FEATURES,
  PERSONAL_SPACE_FEATURES,
  ORGANIZATION_ADMIN_FEATURES,
  SHARED_ACCOUNT_FEATURES,
} from '../../core/models/feature-route.model';

interface NavSubItem {
  label: string;
  icon: IconName;
  route: string;
}

interface NavItem {
  label: string;
  icon: IconName;
  route?: string;
  orgOnly?: boolean;
  children?: NavSubItem[];
}

interface NavGroup {
  title: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    title: 'CORE',
    items: [
      { label: 'Dashboard', icon: 'layout-dashboard', route: 'dashboard' },
      { label: 'Explorer', icon: 'folder', route: 'explorer' },
    ],
  },
  {
    title: 'DISCOVER',
    items: [
      { label: 'Shared With Me', icon: 'inbox', route: 'shared-with-me' },
      { label: 'Shared By Me', icon: 'send', route: 'shared-by-me' },
    ],
  },
  {
    title: 'STRUCTURAL ACCESS',
    items: [
      { label: 'Teams', icon: 'users-round', route: 'teams', orgOnly: true },
      { label: 'Members', icon: 'users', route: 'members', orgOnly: true },
      { label: 'Invitations', icon: 'mail', route: 'invitations' },
    ],
  },
  {
    title: 'MANAGEMENT',
    items: [
      { label: 'Access Requests', icon: 'key', route: 'access-requests' },
      { label: 'Notifications', icon: 'bell', route: 'notifications' },
      { label: 'Audit Trail', icon: 'clipboard-list', route: 'audit' },
      { label: 'Settings', icon: 'settings', route: 'settings' },
    ],
  },
];

type PopoverType = 'organization' | 'workspace' | 'shared-collapsed' | 'account' | null;

/**
 * Enterprise Navigation Shell Sidebar Component.
 * Minimalist, typography-driven SaaS aesthetics, visual context hierarchy, 64px icon rail,
 * standardized Lucide SVG iconography, and subtle orange active indicators.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, IconComponent],
  template: `
    <aside
      class="sidebar"
      [class.collapsed]="isCollapsed()"
      [class.mobile-open]="isMobileOpen()"
    >
      <!-- Sidebar Header (Wordmark & Collapse Toggle) -->
      <div class="sidebar-header">
        @if (!isCollapsed()) {
          <div class="brand-container" routerLink="/dashboard" (click)="closeMobileDrawer()">
            <div class="brand-mark">
              <img src="logo.svg" alt="SecureDoc Logo" class="brand-logo-img" />
            </div>
            <div class="brand-text">
              <span class="brand-name">SecureDoc</span>
            </div>
          </div>

          <button
            class="sidebar-toggle-btn"
            (click)="toggleCollapse()"
            aria-label="Collapse sidebar"
            title="Collapse sidebar"
          >
            <app-icon name="panel-left-close" [size]="18" />
          </button>
        } @else {
          <button
            class="sidebar-toggle-btn collapsed-header-toggle"
            (click)="toggleCollapse()"
            aria-label="Expand sidebar"
            title="Expand sidebar"
          >
            <app-icon name="panel-left-open" [size]="18" />
          </button>
        }
      </div>

      <!-- Context Hierarchy Section (Organization & Workspace Tree) -->
      <div class="context-tree-section">
        <div class="context-tree">
          <!-- Level 1: Organization & Context Selector -->
          <div class="tree-item workspace-level">
            <button
              class="context-node workspace-node"
              [class.active-popover]="activePopover() === 'organization'"
              (click)="togglePopover('organization', $event)"
              (mouseenter)="setTooltip(isCollapsed() ? 'Context: ' + currentContextName() : null)"
              (mouseleave)="setTooltip(null)"
              [attr.title]="isCollapsed() ? 'Context: ' + currentContextName() : null"
              aria-label="Select Organization or Context"
            >
              <div class="node-icon workspace-icon">
                <app-icon [name]="isOrgSpace() ? 'building-2' : 'user-round'" [size]="15" />
              </div>
              @if (!isCollapsed()) {
                <div class="node-info">
                  <span class="node-title">{{ currentContextName() }}</span>
                  <span class="node-sub">{{ isOrgSpace() ? 'Organization' : 'Personal' }}</span>
                </div>
                <app-icon name="chevron-down" [size]="14" class="node-chevron" />
              }

              @if (isCollapsed() && activeTooltip() === 'Context: ' + currentContextName()) {
                <div class="collapsed-tooltip">Context: {{ currentContextName() }}</div>
              }
            </button>

            <!-- Organization Popover Card -->
            @if (activePopover() === 'organization') {
              <div class="popover-card" [class.collapsed-anchored]="isCollapsed()" (click)="$event.stopPropagation()">
                <div class="popover-header">ORGANIZATIONS & CONTEXT</div>
                <div class="popover-list">
                  <!-- Personal Space Option -->
                  <button
                    class="popover-item"
                    [class.selected]="!isOrgSpace()"
                    (click)="onSelectPersonal()"
                  >
                    <div class="popover-item-left">
                      <div class="popover-icon">
                        <app-icon name="user-round" [size]="15" />
                      </div>
                      <div class="popover-text">
                        <span class="popover-title">Personal Space</span>
                        <span class="popover-sub">Personal</span>
                      </div>
                    </div>
                    @if (!isOrgSpace()) {
                      <app-icon name="check" [size]="14" class="check-icon" />
                    }
                  </button>

                  <div class="popover-divider"></div>

                  <!-- Available Organizations List -->
                  @for (org of availableOrganizations(); track org.orgCode) {
                    <button
                      class="popover-item"
                      [class.selected]="activeOrgCode() === org.orgCode"
                      (click)="onSelectOrganization(org.orgCode)"
                    >
                      <div class="popover-item-left">
                        <div class="popover-icon">
                          <app-icon name="building-2" [size]="15" />
                        </div>
                        <div class="popover-text">
                          <span class="popover-title">{{ org.name }}</span>
                          <span class="popover-sub">Organization</span>
                        </div>
                      </div>
                      @if (activeOrgCode() === org.orgCode) {
                        <app-icon name="check" [size]="14" class="check-icon" />
                      }
                    </button>
                  }
                </div>
                <div class="popover-divider"></div>
                <button class="popover-action-btn" (click)="onCreateOrganization()">
                  <app-icon name="plus" [size]="15" />
                  <span>Create organization</span>
                </button>
              </div>
            }
          </div>

          <!-- Level 2: Project Workspace Selector (Both Personal and Organization Contexts) -->
          <div class="tree-item subspace-level">
            @if (!isCollapsed()) {
              <div class="tree-connector-line"></div>
              <div class="tree-branch-line"></div>
            }
            <button
              class="context-node subspace-node"
              [class.active-popover]="activePopover() === 'workspace'"
              (click)="togglePopover('workspace', $event)"
              (mouseenter)="setTooltip(isCollapsed() ? 'Workspace: ' + activeWorkspaceName() : null)"
              (mouseleave)="setTooltip(null)"
              [attr.title]="isCollapsed() ? 'Workspace: ' + activeWorkspaceName() : null"
              aria-label="Select Workspace"
            >
              <div class="node-icon subspace-icon">
                <app-icon name="briefcase" [size]="15" />
              </div>
              @if (!isCollapsed()) {
                <div class="node-info">
                  <span class="node-title">{{ activeWorkspaceName() }}</span>
                  <span class="node-sub">Workspace</span>
                </div>
                <app-icon name="chevron-down" [size]="14" class="node-chevron" />
              }

              @if (isCollapsed() && activeTooltip() === 'Workspace: ' + activeWorkspaceName()) {
                <div class="collapsed-tooltip">Workspace: {{ activeWorkspaceName() }}</div>
              }
            </button>

            <!-- Workspace Popover Card -->
            @if (activePopover() === 'workspace') {
              <div class="popover-card" [class.collapsed-anchored]="isCollapsed()" (click)="$event.stopPropagation()">
                <div class="popover-header">{{ isOrgSpace() ? 'WORKSPACES' : 'PERSONAL WORKSPACES' }}</div>
                <div class="popover-list">
                  @if (isWorkspaceLoading()) {
                    <div class="popover-empty-state">
                      <app-icon name="clock" [size]="18" class="empty-icon" />
                      <span>Loading workspaces...</span>
                    </div>
                  } @else if (availableWorkspaces().length === 0) {
                    <div class="popover-empty-state">
                      <app-icon name="briefcase" [size]="18" class="empty-icon" />
                      <span>No workspaces yet</span>
                    </div>
                  } @else {
                    @for (ws of availableWorkspaces(); track ws.id) {
                      <button
                        class="popover-item"
                        [class.selected]="activeWorkspaceNumericId() === ws.id"
                        [class.archived-item]="ws.status === 'ARCHIVED'"
                        [disabled]="ws.status === 'ARCHIVED'"
                        (click)="onSelectWorkspace(ws)"
                      >
                        <div class="popover-item-left">
                          <div class="popover-icon subspace-icon">
                            <app-icon name="briefcase" [size]="15" />
                          </div>
                          <div class="popover-text">
                            <span class="popover-title">{{ ws.name }}</span>
                            <span class="popover-sub">{{ ws.isDefault ? 'Default workspace' : (ws.status === 'ARCHIVED' ? 'Archived' : 'Project workspace') }}</span>
                          </div>
                        </div>
                        @if (activeWorkspaceNumericId() === ws.id) {
                          <app-icon name="check" [size]="14" class="check-icon" />
                        }
                      </button>
                    }
                  }
                </div>
                <div class="popover-divider"></div>
                <button class="popover-action-btn" (click)="onCreateWorkspace()">
                  <app-icon name="plus" [size]="15" />
                  <span>Create workspace</span>
                </button>
              </div>
            }
          </div>
        </div>
      </div>

      <!-- Navigation List -->
      <nav class="sidebar-nav" aria-label="Main Navigation">
        @for (group of visibleNavGroups(); track group.title) {
          <div class="nav-group">
            @if (!isCollapsed()) {
              <div class="nav-group-header">{{ group.title }}</div>
            } @else {
              <div class="nav-group-divider"></div>
            }

            @for (item of group.items; track item.label) {
              <!-- Case A: Simple Navigation Item -->
              @if (!item.children) {
                <a
                  class="nav-item"
                  [routerLink]="getNavItemRoute(item.route!)"
                  routerLinkActive="active"
                  (click)="onNavItemClick()"
                  (mouseenter)="setTooltip(item.label)"
                  (mouseleave)="setTooltip(null)"
                  [attr.title]="isCollapsed() ? item.label : null"
                >
                  <div class="nav-item-left">
                    <div class="nav-item-icon">
                      <app-icon [name]="item.icon" [size]="18" />
                    </div>
                    @if (!isCollapsed()) {
                      <span class="nav-item-label">{{ item.label }}</span>
                    }
                  </div>

                  @if (!isCollapsed() && getItemBadgeCount(item) > 0) {
                    <span class="nav-badge">{{ getItemBadgeCount(item) }}</span>
                  }

                  @if (isCollapsed() && getItemBadgeCount(item) > 0) {
                    <span class="collapsed-nav-badge">{{ getItemBadgeCount(item) }}</span>
                  }

                  <div class="active-indicator"></div>

                  <!-- Collapsed Tooltip -->
                  @if (isCollapsed() && activeTooltip() === item.label) {
                    <div class="collapsed-tooltip">{{ item.label }}</div>
                  }
                </a>
              }

              <!-- Case B: Expandable Parent Item ("Shared") -->
              @if (item.children) {
                <!-- Expanded Mode Parent Button -->
                @if (!isCollapsed()) {
                  <div class="nested-parent-container">
                    <button
                      class="nav-item nested-parent-btn"
                      [class.child-active]="isSharedChildActive()"
                      (click)="toggleSharedExpand()"
                    >
                      <div class="nav-item-left">
                        <div class="nav-item-icon">
                          <app-icon [name]="item.icon" [size]="18" />
                        </div>
                        <span class="nav-item-label">{{ item.label }}</span>
                      </div>
                      <app-icon
                        name="chevron-down"
                        [size]="14"
                        class="parent-chevron"
                        [class.expanded]="isSharedExpanded()"
                      />
                    </button>

                    <!-- Nested Children List -->
                    @if (isSharedExpanded()) {
                      <div class="nested-children-list">
                        <div class="nested-guide-line"></div>
                        @for (child of item.children; track child.route) {
                          <a
                            class="nav-item nested-child-item"
                            [routerLink]="getNavItemRoute(child.route)"
                            routerLinkActive="active"
                            (click)="onNavItemClick()"
                          >
                            <div class="nav-item-left">
                              <div class="nav-item-icon child-icon">
                                <app-icon [name]="child.icon" [size]="15" />
                              </div>
                              <span class="nav-item-label child-label">{{ child.label }}</span>
                            </div>
                            <div class="active-indicator"></div>
                          </a>
                        }
                      </div>
                    }
                  </div>
                }

                <!-- Collapsed Mode Parent Button (Opens Floating Side Popover) -->
                @if (isCollapsed()) {
                  <div class="collapsed-popover-wrapper">
                    <button
                      class="nav-item collapsed-nested-trigger"
                      [class.child-active]="isSharedChildActive()"
                      [class.active-popover]="activePopover() === 'shared-collapsed'"
                      (click)="togglePopover('shared-collapsed', $event)"
                      (mouseenter)="setTooltip(item.label)"
                      (mouseleave)="setTooltip(null)"
                    >
                      <div class="nav-item-icon">
                        <app-icon [name]="item.icon" [size]="18" />
                      </div>
                      <div class="active-indicator"></div>

                      @if (activeTooltip() === item.label && activePopover() !== 'shared-collapsed') {
                        <div class="collapsed-tooltip">{{ item.label }}</div>
                      }
                    </button>

                    <!-- Collapsed Side Submenu Popover -->
                    @if (activePopover() === 'shared-collapsed') {
                      <div class="popover-card collapsed-anchored" (click)="$event.stopPropagation()">
                        <div class="popover-header">SHARED</div>
                        <div class="popover-list">
                          @for (child of item.children; track child.route) {
                            <a
                              class="popover-item"
                              [routerLink]="getNavItemRoute(child.route)"
                              routerLinkActive="selected"
                              (click)="onNavItemClick(); closePopovers()"
                            >
                              <div class="popover-item-left">
                                <div class="popover-icon">
                                  <app-icon [name]="child.icon" [size]="15" />
                                </div>
                                <span class="popover-title">{{ child.label }}</span>
                              </div>
                            </a>
                          }
                        </div>
                      </div>
                    }
                  </div>
                }
              }
            }
          </div>
        }
      </nav>

      <!-- Sidebar Footer: Account Profile Area -->
      <div class="sidebar-footer">
        <div class="account-container">
          <button
            class="account-trigger"
            [class.active-popover]="activePopover() === 'account'"
            (click)="togglePopover('account', $event)"
            (mouseenter)="setTooltip(isCollapsed() ? userEmail() : null)"
            (mouseleave)="setTooltip(null)"
            aria-label="User Account Menu"
          >
            <div class="account-left">
              <div class="account-avatar">
                {{ userInitials() }}
              </div>
              @if (!isCollapsed()) {
                <div class="account-details">
                  <span class="account-name">{{ userName() }}</span>
                  <span class="account-scope">{{ isOrgSpace() ? 'Organization' : 'Personal' }}</span>
                </div>
              }
            </div>
            @if (!isCollapsed()) {
              <app-icon name="chevron-down" [size]="14" class="chevron-icon" />
            }

            @if (isCollapsed() && activeTooltip() === userEmail()) {
              <div class="collapsed-tooltip">{{ userEmail() }}</div>
            }
          </button>

          <!-- Account Popover Card -->
          @if (activePopover() === 'account') {
            <div class="popover-card account-popover" [class.collapsed-anchored]="isCollapsed()" (click)="$event.stopPropagation()">
              <div class="account-popover-header">
                <div class="account-avatar large">
                  {{ userInitials() }}
                </div>
                <div class="account-header-info">
                  <span class="header-name">{{ userName() }}</span>
                  <span class="header-email">{{ userEmail() }}</span>
                </div>
              </div>
              <div class="popover-divider"></div>
              <div class="popover-list">
                <button class="popover-item" (click)="closePopovers(); navigateTo('profile')">
                  <div class="popover-item-left">
                    <app-icon name="circle-user-round" [size]="15" class="popover-icon" />
                    <span class="popover-title">Profile</span>
                  </div>
                </button>
                <button class="popover-item" (click)="closePopovers(); navigateTo('settings')">
                  <div class="popover-item-left">
                    <app-icon name="settings" [size]="15" class="popover-icon" />
                    <span class="popover-title">Account Settings</span>
                  </div>
                </button>
              </div>
              <div class="popover-divider"></div>
              <button class="popover-action-btn logout-btn" (click)="onLogout()">
                <app-icon name="log-out" [size]="15" />
                <span>Sign out</span>
              </button>
            </div>
          }
        </div>
      </div>
    </aside>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
      z-index: 40;
    }

    /* ── Main Sidebar Layout ─────────────────────────────────── */
    .sidebar {
      width: 240px;
      height: 100%;
      background: var(--color-surface);
      border-right: 1px solid var(--color-border);
      display: flex;
      flex-direction: column;
      transition: width 0.2s cubic-bezier(0.16, 1, 0.3, 1), transform 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      flex-shrink: 0;
      user-select: none;
      position: relative;
    }

    .sidebar.collapsed {
      width: 64px !important;
      overflow: visible;
    }

    .sidebar.collapsed .sidebar-nav {
      overflow: visible;
    }

    @media (max-width: 1023px) {
      .sidebar {
        position: fixed;
        top: 0;
        bottom: 0;
        left: 0;
        z-index: 50;
        width: 240px !important;
        transform: translateX(-100%);
        box-shadow: 4px 0 24px rgba(0, 0, 0, 0.12);
      }

      .sidebar.mobile-open {
        transform: translateX(0);
      }
    }

    /* ── Header ─────────────────────────────────────────────── */
    .sidebar-header {
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 0.75rem;
      border-bottom: 1px solid var(--color-border);
      flex-shrink: 0;
    }

    .brand-container {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      cursor: pointer;
      overflow: hidden;
    }

    .brand-mark {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      background: transparent !important;
    }

    .brand-logo-img {
      width: 24px;
      height: 24px;
      object-fit: contain;
    }

    .brand-container:hover .brand-mark {
      opacity: 0.9;
    }

    .brand-text {
      display: flex;
      align-items: center;
      white-space: nowrap;
    }

    .brand-name {
      font-weight: 700;
      font-size: 0.9rem;
      color: var(--color-text);
      letter-spacing: -0.02em;
    }

    .sidebar-toggle-btn {
      background: transparent;
      border: none;
      cursor: pointer;
      padding: 0.3rem;
      border-radius: 6px;
      color: var(--color-text-muted);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s, color 0.15s;
    }

    .sidebar-toggle-btn:hover {
      background: var(--color-bg-hover, #f3f4f6);
      color: var(--color-accent, #f97316);
    }

    .sidebar.collapsed .sidebar-header {
      padding: 0;
      justify-content: center;
    }

    .collapsed-header-toggle {
      width: 36px;
      height: 36px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--color-text-muted);
    }

    .collapsed-header-toggle:hover {
      background: var(--color-bg-hover, #f3f4f6);
      color: var(--color-accent, #f97316);
    }

    /* ── Context Hierarchy Tree Section ─────────────────────── */
    .context-tree-section {
      padding: 0.5rem 0.4rem;
      border-bottom: 1px solid var(--color-border);
      flex-shrink: 0;
    }

    .context-tree {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      position: relative;
    }

    .tree-item {
      position: relative;
      width: 100%;
    }

    .workspace-level {
      padding: 0;
    }

    .workspace-node {
      padding: 0.35rem 0.5rem;
    }

    .subspace-level {
      margin-left: 1.25rem;
      width: calc(100% - 1.25rem);
      padding-left: 0.65rem;
      position: relative;
      box-sizing: border-box;
    }

    .tree-connector-line {
      position: absolute;
      left: 0;
      top: -0.45rem;
      height: calc(1.1rem + 0.45rem);
      width: 1px;
      background: var(--color-border);
    }

    .tree-branch-line {
      position: absolute;
      left: 0;
      top: 1.1rem;
      width: 0.65rem;
      height: 1px;
      background: var(--color-border);
      transform: translateY(-50%);
    }

    .context-node {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.35rem 0.45rem;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.15s ease;
      outline: none;
      color: var(--color-text);
      position: relative;
      box-sizing: border-box;
    }

    .context-node:hover:not(:disabled) {
      background: var(--color-bg-hover, #f3f4f6);
    }

    .context-node.active-popover {
      background: rgba(249, 115, 22, 0.06);
      border-color: rgba(249, 115, 22, 0.2);
    }

    .context-node:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .node-icon {
      width: 24px;
      height: 24px;
      border-radius: 5px;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      background: transparent !important;
      background-color: transparent !important;
      border: none !important;
      box-shadow: none !important;
      outline: none !important;
    }

    .workspace-icon {
      background: transparent !important;
      background-color: transparent !important;
      color: var(--color-text-muted);
    }

    .subspace-icon {
      background: transparent !important;
      background-color: transparent !important;
      color: var(--color-accent, #f97316);
    }

    .node-info {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      min-width: 0;
      flex: 1;
      padding: 0 0.4rem;
      text-align: left !important;
    }

    .node-title {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--color-text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 130px;
      text-align: left !important;
    }

    .node-sub {
      font-size: 0.6875rem;
      font-weight: 500;
      color: var(--color-text-muted);
      line-height: 1.1;
      text-align: left !important;
    }

    .node-chevron {
      color: var(--color-text-muted);
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
      background: transparent !important;
      background-color: transparent !important;
      border: none !important;
      outline: none !important;
      box-shadow: none !important;
      padding: 0 !important;
      overflow: visible !important;
    }

    /* Collapsed Context Tree */
    .sidebar.collapsed .context-tree-section {
      padding: 0.4rem 0;
    }

    .sidebar.collapsed .context-tree {
      gap: 0.4rem;
      align-items: center;
    }

    .sidebar.collapsed .subspace-level {
      margin-left: 0;
      padding-left: 0;
      width: 100%;
    }

    .sidebar.collapsed .context-node {
      width: 40px;
      height: 40px;
      padding: 0;
      justify-content: center;
      margin: 0 auto;
      border-radius: 8px;
    }

    .sidebar.collapsed .node-icon {
      width: 24px;
      height: 24px;
      background: transparent !important;
      background-color: transparent !important;
    }

    /* ── Reusable Popover Card ───────────────────────────────── */
    .popover-card {
      position: absolute;
      top: 100%;
      left: 0;
      right: 0;
      margin-top: 0.35rem;
      background: var(--color-surface);
      border: 1px solid var(--color-border);
      border-radius: 8px;
      box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.05);
      z-index: 100;
      padding: 0.4rem;
      box-sizing: border-box;
      animation: popoverFadeIn 0.15s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .sidebar:not(.collapsed) .subspace-level .popover-card {
      left: 0;
      right: 0;
      width: 100%;
      min-width: 0;
      box-sizing: border-box;
    }

    .popover-card.collapsed-anchored,
    .sidebar.collapsed .popover-card {
      top: 0 !important;
      bottom: auto !important;
      left: 68px !important;
      right: auto !important;
      width: 220px !important;
      min-width: 220px !important;
      margin-top: 0 !important;
      margin-bottom: 0 !important;
    }

    @keyframes popoverFadeIn {
      from {
        opacity: 0;
        transform: translateY(-4px) scale(0.98);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }

    .popover-header {
      font-size: 0.625rem;
      font-weight: 700;
      color: var(--color-text-muted);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      padding: 0.35rem 0.5rem 0.25rem;
      text-align: left !important;
    }

    .popover-list {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      max-height: 220px;
      overflow-y: auto;
    }

    .popover-empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.35rem;
      padding: 1.25rem 0.75rem;
      color: var(--color-text-muted);
      font-size: 0.8125rem;
      text-align: center;
    }

    .popover-empty-state .empty-icon {
      opacity: 0.4;
    }

    .popover-item {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.4rem 0.5rem;
      background: transparent;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      text-align: left !important;
      transition: background 0.15s;
      color: var(--color-text);
      text-decoration: none;
    }

    .popover-item:hover {
      background: var(--color-bg-hover, #f3f4f6);
    }

    .popover-item.selected {
      background: rgba(249, 115, 22, 0.08);
      color: var(--color-accent, #f97316);
      font-weight: 600;
    }

    .popover-item.archived-item {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .popover-item-left {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-width: 0;
      text-align: left !important;
    }

    .popover-icon {
      color: var(--color-text-muted);
      flex-shrink: 0;
      background: transparent !important;
      background-color: transparent !important;
    }

    .popover-item.selected .popover-icon {
      color: var(--color-accent, #f97316);
    }

    .popover-text {
      display: flex;
      flex-direction: column;
      min-width: 0;
      text-align: left !important;
    }

    .popover-title {
      font-size: 0.8125rem;
      font-weight: 500;
      color: inherit;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      text-align: left !important;
    }

    .popover-sub {
      font-size: 0.6875rem;
      color: var(--color-text-muted);
      text-align: left !important;
    }

    .check-icon {
      color: var(--color-accent, #f97316);
      flex-shrink: 0;
      background: transparent !important;
    }

    .popover-divider {
      height: 1px;
      background: var(--color-border);
      margin: 0.35rem 0;
    }

    .popover-action-btn {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 0.5rem;
      background: transparent;
      border: none;
      border-radius: 6px;
      cursor: pointer;
      font-size: 0.8125rem;
      font-weight: 500;
      color: var(--color-text-muted);
      transition: background 0.15s, color 0.15s;
      text-align: left !important;
    }

    .popover-action-btn:hover {
      background: var(--color-bg-hover, #f3f4f6);
      color: var(--color-accent, #f97316);
    }

    /* ── Navigation List ─────────────────────────────────────── */
    .sidebar-nav {
      flex: 1;
      padding: 0.5rem 0.4rem;
      overflow-y: auto;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }

    .nav-group {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      align-items: flex-start;
      width: 100%;
    }

    .nav-group-header {
      font-size: 0.625rem;
      font-weight: 700;
      color: var(--color-text-muted);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      padding: 0.4rem 0.5rem 0.2rem;
      text-align: left !important;
      align-self: flex-start;
      width: 100%;
    }

    .nav-group-divider {
      height: 1px;
      background: var(--color-border);
      margin: 0.35rem 0.4rem;
      width: calc(100% - 0.8rem);
    }

    .nav-item {
      position: relative;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.45rem 0.5rem;
      color: var(--color-text-muted);
      text-decoration: none;
      font-size: 0.8125rem;
      font-weight: 500;
      border-radius: 6px;
      transition: background 0.15s, color 0.15s;
      cursor: pointer;
      border: none;
      background: transparent;
      width: 100%;
      text-align: left !important;
    }

    .nav-item-left {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      min-width: 0;
      text-align: left !important;
    }

    .nav-item-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
      color: var(--color-text-muted);
      transition: color 0.15s;
      background: transparent !important;
      background-color: transparent !important;
    }

    .nav-item-label {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      text-align: left !important;
    }

    .nav-item:hover {
      background: var(--color-bg-hover, #f3f4f6);
      color: var(--color-text);
    }

    .nav-item:hover .nav-item-icon {
      color: var(--color-text);
    }

    .nav-item.active,
    .nav-item.selected {
      background: rgba(249, 115, 22, 0.08);
      color: var(--color-accent, #f97316);
      font-weight: 600;
    }

    .nav-item.active .nav-item-icon,
    .nav-item.selected .nav-item-icon {
      color: var(--color-accent, #f97316);
    }

    .active-indicator {
      display: none;
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 16px;
      background: var(--color-accent, #f97316);
      border-radius: 0 3px 3px 0;
    }

    .nav-item.active .active-indicator {
      display: block;
    }

    /* Badge Styles */
    .nav-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 18px;
      height: 18px;
      padding: 0 6px;
      font-size: 0.6875rem;
      font-weight: 600;
      line-height: 1;
      color: var(--color-accent, #f97316);
      background: rgba(249, 115, 22, 0.12);
      border-radius: 9999px;
      margin-left: auto;
      flex-shrink: 0;
      transition: all 0.15s ease;
    }

    .nav-item.active .nav-badge {
      background: var(--color-accent, #f97316);
      color: #ffffff;
    }

    .collapsed-nav-badge {
      position: absolute;
      top: 3px;
      right: 3px;
      min-width: 16px;
      height: 16px;
      padding: 0 4px;
      font-size: 0.625rem;
      font-weight: 700;
      line-height: 1;
      color: #ffffff;
      background: var(--color-accent, #f97316);
      border-radius: 9999px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 0 0 2px var(--color-surface);
      z-index: 10;
      pointer-events: none;
    }

    /* Nested Shared Items */
    .nested-parent-container {
      display: flex;
      flex-direction: column;
      width: 100%;
    }

    .nested-parent-btn {
      width: 100%;
    }

    .parent-chevron {
      color: var(--color-text-muted);
      transition: transform 0.2s ease;
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
      background: transparent !important;
      background-color: transparent !important;
      border: none !important;
      outline: none !important;
      box-shadow: none !important;
      padding: 0 !important;
      overflow: visible !important;
    }

    .parent-chevron.expanded {
      transform: rotate(180deg);
    }

    .nested-children-list {
      position: relative;
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      padding-left: 1.5rem;
      margin-top: 0.15rem;
    }

    .nested-guide-line {
      position: absolute;
      left: 0.9rem;
      top: 0;
      bottom: 0.4rem;
      width: 1px;
      background: var(--color-border);
    }

    .nested-child-item {
      padding: 0.35rem 0.5rem;
      font-size: 0.775rem;
    }

    .child-icon {
      width: 16px;
      height: 16px;
      background: transparent !important;
    }

    /* Collapsed Sidebar Specifics */
    .sidebar.collapsed .sidebar-nav {
      padding: 0.4rem 0;
      align-items: center;
    }

    .sidebar.collapsed .nav-item {
      justify-content: center;
      padding: 0;
      width: 40px;
      height: 40px;
      margin: 0 auto;
      border-radius: 8px;
    }

    .sidebar.collapsed .nav-item-icon {
      width: 20px;
      height: 20px;
      background: transparent !important;
    }

    .sidebar.collapsed .active-indicator {
      left: 2px;
      height: 20px;
    }

    .collapsed-popover-wrapper {
      position: relative;
      width: 100%;
      display: flex;
      justify-content: center;
    }

    /* Collapsed Hover Tooltips */
    .collapsed-tooltip {
      position: absolute;
      left: 56px;
      top: 50%;
      transform: translateY(-50%);
      background: #1e293b;
      color: #ffffff;
      font-size: 0.75rem;
      font-weight: 500;
      padding: 0.3rem 0.6rem;
      border-radius: 6px;
      white-space: nowrap;
      z-index: 120;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
      pointer-events: none;
      animation: tooltipFade 0.15s ease;
    }

    @keyframes tooltipFade {
      from { opacity: 0; transform: translateY(-50%) translateX(-4px); }
      to { opacity: 1; transform: translateY(-50%) translateX(0); }
    }

    /* ── Account Footer ──────────────────────────────────────── */
    .sidebar-footer {
      padding: 0.4rem 0.5rem;
      border-top: 1px solid var(--color-border);
      background: var(--color-surface);
      flex-shrink: 0;
      position: relative;
    }

    .account-container {
      position: relative;
    }

    .account-trigger {
      width: 100%;
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.35rem 0.45rem;
      background: transparent;
      border: 1px solid transparent;
      border-radius: 6px;
      cursor: pointer;
      transition: background 0.15s, border-color 0.15s;
      outline: none;
      position: relative;
    }

    .account-trigger:hover {
      background: var(--color-bg-hover, #f3f4f6);
    }

    .account-trigger.active-popover {
      background: var(--color-surface);
      border-color: var(--color-border);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
    }

    .account-left {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      min-width: 0;
      text-align: left !important;
    }

    .account-avatar {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: var(--color-accent, #f97316);
      color: white;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.75rem;
      font-weight: 700;
      flex-shrink: 0;
    }

    .account-avatar.large {
      width: 34px;
      height: 34px;
      font-size: 0.85rem;
    }

    .account-details {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      min-width: 0;
      text-align: left !important;
    }

    .account-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: var(--color-text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 120px;
      text-align: left !important;
    }

    .account-scope {
      font-size: 0.6875rem;
      color: var(--color-text-muted);
      text-align: left !important;
    }

    .chevron-icon {
      color: var(--color-text-muted);
      flex-shrink: 0;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 14px;
      height: 14px;
      background: transparent !important;
      background-color: transparent !important;
      border: none !important;
      outline: none !important;
      box-shadow: none !important;
      padding: 0 !important;
      overflow: visible !important;
    }

    .sidebar.collapsed .sidebar-footer {
      padding: 0.4rem 0;
      display: flex;
      justify-content: center;
    }

    .sidebar.collapsed .account-trigger {
      width: 40px;
      height: 40px;
      padding: 0;
      justify-content: center;
      margin: 0 auto;
    }

    /* Account Popover Card */
    .account-popover {
      bottom: 100%;
      top: auto;
      margin-bottom: 0.35rem;
    }

    .account-popover.collapsed-anchored {
      bottom: 0;
      top: auto;
      left: 68px;
      right: auto;
      margin-bottom: 0;
    }

    .account-popover-header {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.4rem 0.5rem;
    }

    .account-header-info {
      display: flex;
      flex-direction: column;
      min-width: 0;
      text-align: left !important;
    }

    .header-name {
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--color-text);
      text-align: left !important;
    }

    .header-email {
      font-size: 0.725rem;
      color: var(--color-text-muted);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 140px;
      text-align: left !important;
    }

    .logout-btn {
      color: #ef4444;
    }

    .logout-btn:hover {
      background: #fef2f2;
      color: #dc2626;
    }
  `],
})
export class SidebarComponent {
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly authStore = inject(AuthStore);
  private readonly layoutStore = inject(LayoutStore);
  private readonly router = inject(Router);
  private readonly elementRef = inject(ElementRef);
  private readonly organizationApiService = inject(OrganizationApiService);
  private readonly accessRequestApiService = inject(AccessRequestApiService);
  private readonly notificationStore = inject(NotificationStore);

  readonly pendingInvitationsCount = this.organizationApiService.pendingInvitationsCount;
  readonly pendingReviewsCount = signal<number>(0);

  readonly isCollapsed = this.layoutStore.isSidebarCollapsed;
  readonly isMobileOpen = this.layoutStore.isMobileDrawerOpen;

  // Canonical context signals from WorkspaceContextStore
  readonly activeOrganization = this.workspaceContextStore.activeOrganization;
  readonly activeWorkspace = this.workspaceContextStore.activeWorkspace;
  readonly availableOrganizations = this.workspaceContextStore.availableOrganizations;
  readonly availableWorkspaces = this.workspaceContextStore.availableWorkspaces;
  readonly isWorkspaceLoading = this.workspaceContextStore.isLoading;

  readonly isOrgSpace = computed(() => !!this.activeOrganization());
  readonly activeOrgCode = computed(() => this.activeOrganization()?.orgCode ?? null);

  readonly currentContextName = computed(() => {
    const org = this.activeOrganization();
    return org ? org.name : 'Personal Space';
  });

  readonly activeWorkspaceName = computed(() => {
    const ws = this.activeWorkspace();
    if (ws) return ws.name;
    if (this.isWorkspaceLoading()) return 'Loading...';
    return 'No workspace';
  });

  readonly activeWorkspaceNumericId = computed<number | null>(() => {
    const ws = this.activeWorkspace();
    return ws && typeof ws.id === 'number' ? ws.id : null;
  });

  readonly userEmail = computed(() => this.authStore.currentUser()?.email ?? 'user@securedoc.com');
  readonly userName = computed(() => this.authStore.currentUser()?.fullName ?? 'User Account');
  readonly userInitials = this.authStore.userInitials;

  readonly activePopover = signal<PopoverType>(null);
  readonly isSharedExpanded = signal<boolean>(false);
  readonly activeTooltip = signal<string | null>(null);

  readonly visibleNavGroups = computed(() => {
    const isOrg = this.isOrgSpace();
    const user = this.authStore.currentUser();
    const orgCode = this.activeOrgCode();
    return NAV_GROUPS.map((group) => ({
      ...group,
      items: group.items.filter((item) => {
        if (item.orgOnly && !isOrg) return false;
        if (item.route === 'members') {
          if (!isOrg) return false;
          if (user?.role === 'ROLE_SUPER_ADMIN') return true;
          return hasOrganizationCapability(user, orgCode, 'ORG_MEMBERS_MANAGE');
        }
        return true;
      }),
    })).filter((group) => group.items.length > 0);
  });

  constructor() {
    this.organizationApiService.refreshPendingCount();
    this.refreshPendingReviewsCount();
    this.notificationStore.refreshUnreadCount();

    // Automatically expand Shared nested group if navigating to a shared child route
    this.router.events
      .pipe(filter((event): event is NavigationEnd => event instanceof NavigationEnd))
      .subscribe((event) => {
        if (event.url.includes('shared-with-me') || event.url.includes('shared-by-me')) {
          this.isSharedExpanded.set(true);
        }
      });
  }

  refreshPendingReviewsCount(): void {
    if (!this.authStore.isAuthenticated()) return;
    this.accessRequestApiService.getPendingReviews(0, 1).subscribe({
      next: (res) => this.pendingReviewsCount.set(res.totalElements ?? res.content?.length ?? 0),
      error: () => this.pendingReviewsCount.set(0),
    });
  }

  getItemBadgeCount(item: NavItem): number {
    if (item.route === 'invitations') {
      return this.pendingInvitationsCount();
    }
    if (item.route === 'access-requests') {
      return this.pendingReviewsCount();
    }
    if (item.route === 'notifications') {
      return this.notificationStore.unreadCount();
    }
    return 0;
  }

  toggleCollapse(): void {
    this.closePopovers();
    this.layoutStore.toggleSidebar();
  }

  closeMobileDrawer(): void {
    this.closePopovers();
    this.layoutStore.closeMobileDrawer();
  }

  togglePopover(type: PopoverType, event: Event): void {
    event.stopPropagation();
    if (this.activePopover() === type) {
      this.activePopover.set(null);
    } else {
      this.activePopover.set(type);
    }
  }

  closePopovers(): void {
    this.activePopover.set(null);
  }

  toggleSharedExpand(): void {
    this.isSharedExpanded.set(!this.isSharedExpanded());
  }

  isSharedChildActive(): boolean {
    return this.router.url.includes('shared-with-me') || this.router.url.includes('shared-by-me');
  }

  setTooltip(label: string | null): void {
    if (this.isCollapsed()) {
      this.activeTooltip.set(label);
    }
  }

  onSelectPersonal(): void {
    this.closePopovers();
    const currentFeature = this.getCurrentFeature();
    const targetFeature = isOrgOnlyFeature(currentFeature) ? 'dashboard' : currentFeature;
    this.router.navigate(['/w', 'personal', targetFeature]);
    this.layoutStore.closeMobileDrawer();
  }

  onSelectOrganization(orgCode: string): void {
    this.closePopovers();
    const currentFeature = this.getCurrentFeature();

    // Shared discovery views and notifications are strictly personal-level views
    if (currentFeature === 'shared-with-me' || currentFeature === 'shared-by-me' || currentFeature === 'notifications') {
      this.router.navigate(['/w', 'personal', currentFeature]);
      this.layoutStore.closeMobileDrawer();
      return;
    }

    // Organization-level features navigate directly without workspace resolution
    if (isOrganizationAdminFeature(currentFeature) || isSharedAccountFeature(currentFeature)) {
      this.router.navigate(['/org', orgCode, currentFeature]);
      this.layoutStore.closeMobileDrawer();
      return;
    }

    // For content features: resolve default workspace via store
    this.workspaceContextStore.resolveDefaultWorkspace(orgCode).subscribe({
      next: (resolvedWsId) => {
        this.router.navigate(['/org', orgCode, 'w', resolvedWsId.toString(), currentFeature]);
      },
      error: (err) => {
        console.warn(`[Sidebar] Default workspace resolution failed for "${orgCode}", falling back to route resolver`, err);
        this.router.navigate(['/org', orgCode, 'w', 'default', currentFeature]);
      },
    });
    this.layoutStore.closeMobileDrawer();
  }

  onSelectWorkspace(ws: ProjectWorkspaceModel): void {
    if (ws.status === 'ARCHIVED') {
      return;
    }
    this.closePopovers();
    const currentFeature = this.getCurrentFeature();
    const targetFeature = isWorkspaceContentFeature(currentFeature) ? currentFeature : 'explorer';
    const orgCode = this.activeOrgCode();
    if (orgCode) {
      this.router.navigate(['/org', orgCode, 'w', ws.id.toString(), targetFeature]);
    } else {
      this.router.navigate(['/w', 'personal', 'w', ws.id.toString(), targetFeature]);
    }
    this.layoutStore.closeMobileDrawer();
  }

  getNavItemRoute(featureRoute: string): string[] {
    const orgCode = this.activeOrgCode();
    const wsId = this.activeWorkspaceNumericId();
    return buildCanonicalRoute(featureRoute, orgCode, wsId);
  }

  getCurrentFeature(): string {
    const urlWithoutQuery = this.router.url.split('?')[0];
    const segments = urlWithoutQuery.split('/').filter(Boolean);
    const lastSegment = segments[segments.length - 1];
    const knownFeatures = [
      ...WORKSPACE_CONTENT_FEATURES,
      ...PERSONAL_SPACE_FEATURES,
      ...ORGANIZATION_ADMIN_FEATURES,
      ...SHARED_ACCOUNT_FEATURES,
    ];
    return knownFeatures.includes(lastSegment as any) ? lastSegment : 'dashboard';
  }

  onNavItemClick(): void {
    this.closePopovers();
    this.layoutStore.closeMobileDrawer();
  }

  navigateTo(featureRoute: string): void {
    this.closePopovers();
    this.router.navigate(this.getNavItemRoute(featureRoute));
  }

  onCreateOrganization(): void {
    this.closePopovers();
    this.layoutStore.openModal('CREATE_ORGANIZATION');
  }

  onCreateWorkspace(): void {
    this.closePopovers();
    this.layoutStore.openModal('CREATE_WORKSPACE');
  }

  onLogout(): void {
    this.closePopovers();
    this.authStore.logout();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.closePopovers();
    }
  }

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    this.closePopovers();
  }
}
