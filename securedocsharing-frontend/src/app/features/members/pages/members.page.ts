import {
  Component,
  ChangeDetectionStrategy,
  signal,
  computed,
  inject,
  effect,
  OnInit,
  OnDestroy,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import {
  LucideUsers,
  LucideUserPlus,
  LucideSearch,
  LucideRefreshCw,
  LucideX,
  LucideMail,
  LucideAlertCircle,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { AuthStore } from '../../../state/auth/auth.store';
import { NotificationService } from '../../../core/services/notification.service';
import {
  OrganizationMemberDetailResponseDto,
  OrganizationInvitationResponseDto,
} from '../../../core/models/organization.model';
import { hasOrganizationCapability } from '../../../core/models/organization-capability.model';
import { extractErrorMessage } from '../../../core/models/app-error.model';

import { IconComponent } from '../../../shared/components/icon/icon.component';

import { MemberTableComponent } from '../components/member-table/member-table.component';
import { InvitationsTableComponent } from '../components/invitations-table/invitations-table.component';
import { MemberDetailsDrawerComponent } from '../components/member-details-drawer/member-details-drawer.component';
import { InviteMemberModalComponent } from '../components/invite-member-modal/invite-member-modal.component';
import { ChangeRoleModalComponent } from '../components/modals/change-role-modal.component';
import { SuspendMemberModalComponent } from '../components/modals/suspend-member-modal.component';
import { RemoveMemberModalComponent } from '../components/modals/remove-member-modal.component';
import { TransferAdminModalComponent } from '../components/modals/transfer-admin-modal.component';
import { ConfirmationModalComponent } from '../../../shared/ui/modal/confirmation-modal.component';

type ActiveTab = 'MEMBERS' | 'INVITATIONS';
type MemberStatusFilter = 'ALL' | 'ACTIVE' | 'SUSPENDED' | 'REMOVED';

@Component({
  selector: 'app-members-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IconComponent,
    MemberTableComponent,
    InvitationsTableComponent,
    MemberDetailsDrawerComponent,
    InviteMemberModalComponent,
    ChangeRoleModalComponent,
    SuspendMemberModalComponent,
    RemoveMemberModalComponent,
    TransferAdminModalComponent,
    ConfirmationModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="members-page-container">
      <!-- ── Page Header ── -->
      <header class="page-header">
        <div class="header-main">
          <div class="header-icon-box">
            <app-icon name="users" [size]="24" />
          </div>
          <div class="header-titles">
            <div class="context-eyebrow">
              <span>{{ activeOrgName() || orgCode() }}</span>
              <span class="eyebrow-sep">•</span>
              <span>Organization Governance</span>
            </div>
            <div class="title-with-pill">
              <h1 class="page-title">Members & Governance</h1>
              <span class="count-pill">{{ totalMemberCount() }} members</span>
            </div>
            <p class="page-subtitle">
              Manage organization roster, administrative roles, and pending invitations.
            </p>
          </div>
        </div>

        <div class="header-actions">
          <!-- Refresh Button -->
          <button
            type="button"
            class="refresh-btn"
            (click)="refreshCurrentTab()"
            [disabled]="isLoading() || isLoadingInvitations()"
            title="Refresh current list"
            aria-label="Refresh members and invitations"
          >
            <app-icon name="refresh-cw" [size]="14" class="refresh-icon" [class.spin]="isLoading() || isLoadingInvitations()" />
            <span>Refresh</span>
          </button>

          <!-- Primary Action: Invite Member -->
          @if (canInviteMembers()) {
            <button
              type="button"
              class="btn-primary-action"
              (click)="isInviteModalOpen.set(true)"
            >
              <app-icon name="user-plus" [size]="15" />
              <span>Invite Member</span>
            </button>
          }
        </div>
      </header>

      <!-- ── Integrated Tab Navigation ── -->
      <nav class="tabs-nav-bar" role="tablist" aria-label="Members section tabs">
        <button
          type="button"
          role="tab"
          class="nav-tab-btn"
          [class.active]="activeTab() === 'MEMBERS'"
          [attr.aria-selected]="activeTab() === 'MEMBERS'"
          (click)="setActiveTab('MEMBERS')"
        >
          <app-icon name="users" [size]="15" />
          <span>Organization Members</span>
          <span class="tab-count-pill">{{ totalMemberCount() }}</span>
        </button>
        <button
          type="button"
          role="tab"
          class="nav-tab-btn"
          [class.active]="activeTab() === 'INVITATIONS'"
          [attr.aria-selected]="activeTab() === 'INVITATIONS'"
          (click)="setActiveTab('INVITATIONS')"
        >
          <app-icon name="mail" [size]="15" />
          <span>Pending Invitations</span>
          <span class="tab-count-pill" [class.tab-count-highlight]="pendingInvitations().length > 0">
            {{ pendingInvitations().length }}
          </span>
        </button>
      </nav>

      <!-- ── Content Area ── -->
      <main class="main-content">
        <!-- Tab 1: Organization Members View -->
        @if (activeTab() === 'MEMBERS') {
          <!-- Filter & Search Toolbar -->
          <section class="toolbar-section">
            <!-- Search Box -->
            <div class="search-box">
              <app-icon name="search" [size]="15" class="search-icon" />
              <input
                type="text"
                class="search-input"
                placeholder="Search members by name or email..."
                [value]="searchQuery()"
                (input)="onSearchInput($event)"
              />
              @if (searchQuery().length > 0) {
                <button
                  type="button"
                  class="clear-search-btn"
                  (click)="clearSearch()"
                  aria-label="Clear search input"
                >
                  <app-icon name="x" [size]="13" />
                </button>
              }
            </div>

            <!-- Status Filter Chips -->
            <div class="filters-row">
              <div class="chip-group" role="group" aria-label="Status filter">
                <button
                  type="button"
                  class="filter-chip"
                  [class.active]="selectedStatus() === 'ALL'"
                  (click)="setStatusFilter('ALL')"
                >
                  All ({{ totalMemberCount() }})
                </button>
                <button
                  type="button"
                  class="filter-chip"
                  [class.active]="selectedStatus() === 'ACTIVE'"
                  (click)="setStatusFilter('ACTIVE')"
                >
                  <span class="status-dot dot-active"></span>
                  Active ({{ activeCount() }})
                </button>
                <button
                  type="button"
                  class="filter-chip"
                  [class.active]="selectedStatus() === 'SUSPENDED'"
                  (click)="setStatusFilter('SUSPENDED')"
                >
                  <span class="status-dot dot-suspended"></span>
                  Suspended ({{ suspendedCount() }})
                </button>
                @if (removedCount() > 0) {
                  <button
                    type="button"
                    class="filter-chip"
                    [class.active]="selectedStatus() === 'REMOVED'"
                    (click)="setStatusFilter('REMOVED')"
                  >
                    <span class="status-dot dot-removed"></span>
                    Removed ({{ removedCount() }})
                  </button>
                }
              </div>

              @if (hasActiveFilters()) {
                <button
                  type="button"
                  class="clear-all-btn"
                  (click)="resetFilters()"
                >
                  <app-icon name="x" [size]="12" />
                  <span>Reset Filters</span>
                </button>
              }
            </div>
          </section>

          <!-- Members Table Card Surface -->
          <div class="table-surface-card">
            @if (isLoading() && members().length === 0) {
              <div class="loading-container">
                <div class="loading-spinner"></div>
                <p class="loading-text">Loading organization members...</p>
              </div>
            } @else if (error()) {
              <div class="state-container">
                <div class="state-icon-box error-icon-box">
                  <app-icon name="alert-circle" [size]="24" />
                </div>
                <h3 class="state-title">Unable to Load Members</h3>
                <p class="state-desc">{{ error() }}</p>
                <button
                  type="button"
                  class="btn-secondary-action"
                  (click)="loadMembers()"
                >
                  <app-icon name="refresh-cw" [size]="13" />
                  <span>Retry</span>
                </button>
              </div>
            } @else if (members().length === 0) {
              <div class="state-container">
                <div class="state-icon-box">
                  <app-icon name="users" [size]="24" />
                </div>
                <h3 class="state-title">No Matching Members Found</h3>
                <p class="state-desc">
                  No members match your current search query or filter selection.
                </p>
                @if (searchQuery() || selectedStatus() !== 'ALL') {
                  <button
                    type="button"
                    class="state-action-link"
                    (click)="resetFilters()"
                  >
                    Clear Search & Filters
                  </button>
                }
              </div>
            } @else {
              <app-member-table
                [members]="members()"
                [currentUserId]="currentUserId()"
                [currentUserRole]="currentUserOrgRole()"
                [activeAdminCount]="activeAdminCount()"
                (selectMember)="openDrawerForMember($event)"
                (changeRole)="openChangeRoleModal($event)"
                (suspendMember)="openSuspendModal($event)"
                (reactivateMember)="openReactivateConfirm($event)"
                (removeMember)="openRemoveModal($event)"
                (transferAdmin)="openTransferAdminModal($event)"
              />

              <!-- Table Summary Footer -->
              <footer class="table-footer">
                <span>Showing {{ members().length }} of {{ totalMemberCount() }} member{{ totalMemberCount() === 1 ? '' : 's' }}</span>
                @if (searchQuery()) {
                  <span class="font-mono text-[11px]">Filtered by "{{ searchQuery() }}"</span>
                }
              </footer>
            }
          </div>
        }

        <!-- Tab 2: Pending Invitations View -->
        @if (activeTab() === 'INVITATIONS') {
          <div class="table-surface-card">
            <app-invitations-table
              [invitations]="pendingInvitations()"
              [isLoading]="isLoadingInvitations()"
              [canInvite]="canInviteMembers()"
              (revoke)="openRevokeConfirm($event)"
              (openInvite)="isInviteModalOpen.set(true)"
            />

            @if (pendingInvitations().length > 0) {
              <footer class="table-footer">
                <span>{{ pendingInvitations().length }} pending invitation{{ pendingInvitations().length === 1 ? '' : 's' }}</span>
                <span class="text-[11px] text-muted-foreground">Invitations remain valid for 7 days</span>
              </footer>
            }
          </div>
        }
      </main>

      <!-- ── Member Details Slide-Over Drawer ── -->
      <app-member-details-drawer
        [isOpen]="isDetailDrawerOpen()"
        [orgCode]="orgCode()"
        [memberId]="selectedMemberId()"
        [currentUserRole]="currentUserOrgRole()"
        [activeAdminCount]="activeAdminCount()"
        (closeDrawer)="closeDetailDrawer()"
        (changeRoleClicked)="openChangeRoleModal($event)"
        (suspendClicked)="openSuspendModal($event)"
        (reactivateClicked)="openReactivateConfirm($event)"
        (removeClicked)="openRemoveModal($event)"
        (transferAdminClicked)="openTransferAdminModal($event)"
      />

      <!-- ── Standalone Governance Modals ── -->
      <app-invite-member-modal
        [isOpen]="isInviteModalOpen()"
        [orgCode]="orgCode()"
        [currentUserRole]="currentUserOrgRole()"
        [existingMemberEmails]="existingMemberEmails()"
        (closeModal)="isInviteModalOpen.set(false)"
        (invitationSent)="onInvitationSent()"
      />

      <app-change-role-modal
        [open]="isChangeRoleModalOpen()"
        [member]="activeModalMember()"
        [orgCode]="orgCode()"
        [currentUserRole]="currentUserOrgRole()"
        [activeAdminCount]="activeAdminCount()"
        (closed)="isChangeRoleModalOpen.set(false)"
        (roleChanged)="onMemberUpdated($event)"
      />

      <app-suspend-member-modal
        [open]="isSuspendModalOpen()"
        [member]="activeModalMember()"
        [orgCode]="orgCode()"
        [activeAdminCount]="activeAdminCount()"
        (closed)="isSuspendModalOpen.set(false)"
        (memberSuspended)="onMemberUpdated($event)"
      />

      <sd-confirmation-modal
        [open]="isReactivateConfirmOpen()"
        title="Reactivate Member"
        [message]="'Reactivate ' + (activeModalMember() ? getDisplayName(activeModalMember()!) : 'this member') + '? They will immediately regain active organization access and team permissions.'"
        confirmText="Reactivate Member"
        variant="primary"
        [loading]="isReactivating()"
        (confirmed)="confirmReactivate()"
        (cancelled)="isReactivateConfirmOpen.set(false)"
      />

      <app-remove-member-modal
        [open]="isRemoveModalOpen()"
        [member]="activeModalMember()"
        [orgCode]="orgCode()"
        [activeAdminCount]="activeAdminCount()"
        (closed)="isRemoveModalOpen.set(false)"
        (memberRemoved)="onMemberRemoved($event)"
      />

      <app-transfer-admin-modal
        [open]="isTransferAdminModalOpen()"
        [orgCode]="orgCode()"
        [members]="allFetchedMembers()"
        [preselectedTarget]="activeModalMember()"
        (closed)="isTransferAdminModalOpen.set(false)"
        (adminTransferred)="onAdminTransferred()"
      />

      <sd-confirmation-modal
        [open]="isRevokeConfirmOpen()"
        title="Revoke Invitation"
        [message]="'Revoke invitation for ' + (activeRevokeInvite()?.inviteeEmail || 'this user') + '? The sent invitation link will immediately become invalid.'"
        confirmText="Revoke Invitation"
        variant="danger"
        [loading]="isRevoking()"
        (confirmed)="confirmRevokeInvitation()"
        (cancelled)="isRevokeConfirmOpen.set(false)"
      />
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100%;
    }

    .members-page-container {
      max-width: 1440px;
      margin: 0 auto;
      padding: 24px 28px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* ── Header ── */
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 20px;
      flex-wrap: wrap;
    }

    .header-main {
      display: flex;
      align-items: flex-start;
      gap: 16px;
    }

    .header-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: var(--sd-color-primary-subtle, #fff7ed);
      color: var(--sd-color-primary, #f97316);
      border: 1px solid var(--sd-color-primary-border, #fed7aa);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .header-titles {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .context-eyebrow {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 11px;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .eyebrow-sep {
      color: #cbd5e1;
    }

    .title-with-pill {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .page-title {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.02em;
      margin: 0;
      line-height: 1.2;
    }

    .count-pill {
      padding: 2px 8px;
      border-radius: 9999px;
      background: #f1f5f9;
      color: #475569;
      font-size: 12px;
      font-weight: 600;
    }

    .page-subtitle {
      font-size: 13.5px;
      color: #64748b;
      margin: 0;
      line-height: 1.4;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .refresh-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      color: #334155;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 38px;
    }

    .refresh-btn:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .refresh-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .btn-primary-action {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 16px;
      background: var(--sd-color-primary, #f97316);
      border: 1px solid transparent;
      border-radius: 8px;
      color: #ffffff;
      font-size: 13px;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 38px;
      box-shadow: 0 1px 2px rgba(249, 115, 22, 0.2);
    }

    .btn-primary-action:hover {
      background: var(--sd-color-primary-hover, #ea580c);
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      100% { transform: rotate(360deg); }
    }

    /* ── Tabs Navigation ── */
    .tabs-nav-bar {
      display: flex;
      align-items: center;
      gap: 8px;
      border-bottom: 1px solid #e2e8f0;
      padding-bottom: 2px;
    }

    .nav-tab-btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 8px 14px;
      border: none;
      background: transparent;
      font-size: 13px;
      font-weight: 500;
      color: #64748b;
      cursor: pointer;
      position: relative;
      transition: all 0.15s ease;
    }

    .nav-tab-btn:hover {
      color: #0f172a;
    }

    .nav-tab-btn.active {
      color: var(--sd-color-primary, #f97316);
      font-weight: 600;
    }

    .nav-tab-btn.active::after {
      content: '';
      position: absolute;
      bottom: -3px;
      left: 0;
      right: 0;
      height: 2px;
      background: var(--sd-color-primary, #f97316);
      border-radius: 2px;
    }

    .tab-count-pill {
      padding: 2px 7px;
      border-radius: 9999px;
      background: #f1f5f9;
      color: #475569;
      font-size: 11px;
      font-weight: 600;
    }

    .tab-count-highlight {
      background: rgba(249, 115, 22, 0.12);
      color: var(--sd-color-text-brand, #ea580c);
    }

    /* ── Toolbar ── */
    .toolbar-section {
      display: flex;
      flex-direction: column;
      gap: 12px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 14px 16px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
      margin-bottom: 16px;
    }

    .search-box {
      position: relative;
      width: 100%;
    }

    .search-icon {
      position: absolute;
      left: 12px;
      top: 50%;
      transform: translateY(-50%);
      color: #94a3b8;
      pointer-events: none;
    }

    .search-input {
      width: 100%;
      height: 38px;
      padding: 0 36px 0 38px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 13.5px;
      color: #0f172a;
      background: #f8fafc;
      transition: all 0.15s ease;
      box-sizing: border-box;
    }

    .search-input:focus {
      outline: none;
      background: #ffffff;
      border-color: var(--sd-color-brand-focus, #fb923c);
      box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.15);
    }

    .clear-search-btn {
      position: absolute;
      right: 10px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      border: none;
      background: #e2e8f0;
      color: #475569;
      cursor: pointer;
    }

    .clear-search-btn:hover {
      background: #cbd5e1;
      color: #0f172a;
    }

    .filters-row {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .chip-group {
      display: inline-flex;
      gap: 6px;
      flex-wrap: wrap;
    }

    .filter-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 5px 11px;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #475569;
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 32px;
    }

    .filter-chip:hover {
      background: #f1f5f9;
      border-color: #cbd5e1;
      color: #0f172a;
    }

    .filter-chip.active {
      background: rgba(249, 115, 22, 0.08);
      border-color: rgba(249, 115, 22, 0.3);
      color: var(--sd-color-text-brand, #ea580c);
      font-weight: 600;
    }

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .dot-active { background: #16a34a; }
    .dot-suspended { background: #d97706; }
    .dot-removed { background: #94a3b8; }

    .clear-all-btn {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 5px 10px;
      background: transparent;
      border: 1px dashed #cbd5e1;
      border-radius: 6px;
      color: #64748b;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      margin-left: auto;
      min-height: 32px;
    }

    .clear-all-btn:hover {
      background: #fee2e2;
      border-color: #fca5a5;
      color: #b91c1c;
    }

    /* ── Table Card Surface ── */
    .table-surface-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      overflow: hidden;
    }

    .table-footer {
      padding: 10px 16px;
      background: #f8fafc;
      border-top: 1px solid #e2e8f0;
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 12px;
      color: #64748b;
    }

    /* ── States ── */
    .loading-container {
      padding: 80px 20px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }

    .loading-spinner {
      width: 32px;
      height: 32px;
      border: 2px solid var(--sd-color-primary, #f97316);
      border-top-color: transparent;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .loading-text {
      font-size: 13px;
      color: #64748b;
    }

    .state-container {
      padding: 64px 20px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }

    .state-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: #f1f5f9;
      color: #64748b;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 4px;
    }

    .error-icon-box {
      background: #fef2f2;
      color: #dc2626;
      border: 1px solid #fecaca;
    }

    .state-title {
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
      margin: 0;
    }

    .state-desc {
      font-size: 13px;
      color: #64748b;
      max-width: 400px;
      margin: 0;
      line-height: 1.4;
    }

    .btn-secondary-action {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      background: #ffffff;
      color: #334155;
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;
      margin-top: 6px;
    }

    .btn-secondary-action:hover {
      background: #f8fafc;
      color: #0f172a;
    }

    .state-action-link {
      background: transparent;
      border: none;
      color: var(--sd-color-primary, #f97316);
      font-size: 12.5px;
      font-weight: 600;
      cursor: pointer;
      margin-top: 4px;
    }

    .state-action-link:hover {
      text-decoration: underline;
    }
  `],
})
export class MembersPageComponent implements OnInit, OnDestroy {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly notificationService = inject(NotificationService);
  private readonly authStore = inject(AuthStore);

  // Tab & Filter Signals
  readonly activeTab = signal<ActiveTab>('MEMBERS');
  readonly searchQuery = signal<string>('');
  readonly selectedStatus = signal<MemberStatusFilter>('ALL');

  // Member Data Signals
  readonly members = signal<OrganizationMemberDetailResponseDto[]>([]);
  readonly allFetchedMembers = signal<OrganizationMemberDetailResponseDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly error = signal<string | null>(null);

  // Invitation Data Signals
  readonly pendingInvitations = signal<OrganizationInvitationResponseDto[]>([]);
  readonly isLoadingInvitations = signal<boolean>(false);

  // Drawer Signals
  readonly selectedMemberId = signal<string | null>(null);
  readonly isDetailDrawerOpen = signal<boolean>(false);

  // Modal Signals
  readonly isInviteModalOpen = signal<boolean>(false);
  readonly isChangeRoleModalOpen = signal<boolean>(false);
  readonly isSuspendModalOpen = signal<boolean>(false);
  readonly isReactivateConfirmOpen = signal<boolean>(false);
  readonly isReactivating = signal<boolean>(false);
  readonly isRemoveModalOpen = signal<boolean>(false);
  readonly isTransferAdminModalOpen = signal<boolean>(false);
  readonly isRevokeConfirmOpen = signal<boolean>(false);
  readonly isRevoking = signal<boolean>(false);

  // Active Context Target for Modals
  readonly activeModalMember = signal<OrganizationMemberDetailResponseDto | null>(null);
  readonly activeRevokeInvite = signal<OrganizationInvitationResponseDto | null>(null);

  // Auth & Context Computed Signals
  readonly currentUser = this.authStore.currentUser;
  readonly currentUserId = computed(() => this.currentUser()?.id || null);
  readonly activeWorkspace = this.workspaceContextStore.activeWorkspace;
  readonly orgCode = computed<string>(() => {
    const storeCode =
      typeof this.workspaceContextStore.activeOrgCode === 'function'
        ? this.workspaceContextStore.activeOrgCode()
        : null;
    if (storeCode) return storeCode;

    const org =
      typeof this.workspaceContextStore.activeOrganization === 'function'
        ? this.workspaceContextStore.activeOrganization()
        : null;
    if (org?.orgCode) return org.orgCode;

    const ws = this.activeWorkspace();
    return (ws?.organizationCode || ws?.orgCode || '')?.toString();
  });

  readonly existingMemberEmails = computed<string[]>(() =>
    this.allFetchedMembers()
      .map((m) => m.email)
      .filter((e): e is string => !!e)
  );

  readonly activeOrgName = computed(() => {
    const org = typeof this.workspaceContextStore.activeOrganization === 'function'
      ? this.workspaceContextStore.activeOrganization()
      : null;
    return (
      org?.name ||
      this.workspaceContextStore.activeWorkspace?.()?.name ||
      this.orgCode()
    );
  });

  readonly currentUserOrgRole = computed(() => {
    const user = this.currentUser();
    if (!user) return null;
    const match = this.allFetchedMembers().find(
      (m) => m.userId === user.id || (m.email && m.email.toLowerCase() === user.email?.toLowerCase())
    );
    return match?.role || null;
  });

  readonly canInviteMembers = computed(() => {
    const user = this.currentUser();
    const org = this.orgCode();
    if (!user || !org) return false;
    if (user.role === 'ROLE_SUPER_ADMIN') return true;
    if (hasOrganizationCapability(user, org, 'ORG_MEMBERS_INVITE')) return true;

    const match = this.allFetchedMembers().find(
      (m) => m.userId === user.id || (m.email && m.email.toLowerCase() === user.email?.toLowerCase())
    );
    if (!match || match.status !== 'ACTIVE') return false;
    const role = (match.role || '').toUpperCase();
    return role.includes('ADMIN') || role.includes('MANAGER');
  });

  readonly totalMemberCount = computed(() => this.allFetchedMembers().length);

  readonly activeCount = computed(() =>
    this.allFetchedMembers().filter((m) => m.status === 'ACTIVE').length
  );

  readonly suspendedCount = computed(() =>
    this.allFetchedMembers().filter((m) => m.status === 'SUSPENDED').length
  );

  readonly removedCount = computed(() =>
    this.allFetchedMembers().filter((m) => m.status === 'REMOVED').length
  );

  readonly activeAdminCount = computed(() =>
    this.allFetchedMembers().filter((m) => {
      const r = (m.role || '').toUpperCase();
      return r.includes('ADMIN') && m.status === 'ACTIVE';
    }).length
  );

  readonly hasActiveFilters = computed(() => {
    return this.searchQuery().trim().length > 0 || this.selectedStatus() !== 'ALL';
  });

  private searchDebounceTimer: any = null;
  private previousOrgCode: string | null = null;
  private membersSubscription: Subscription | null = null;
  private invitationsSubscription: Subscription | null = null;

  constructor() {
    effect(
      () => {
        const code = this.orgCode();
        if (code !== this.previousOrgCode) {
          this.previousOrgCode = code;

          this.cancelSubscriptions();
          this.searchQuery.set('');
          this.selectedStatus.set('ALL');
          this.isDetailDrawerOpen.set(false);
          this.selectedMemberId.set(null);
          this.members.set([]);
          this.allFetchedMembers.set([]);
          this.pendingInvitations.set([]);
          this.error.set(null);

          if (code) {
            this.loadMembers();
            this.loadInvitations();
          } else {
            this.isLoading.set(false);
          }
        }
      },
      { allowSignalWrites: true }
    );
  }

  ngOnInit(): void {
    // Handled by effect()
  }

  ngOnDestroy(): void {
    this.cancelSubscriptions();
    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer);
    }
  }

  private cancelSubscriptions(): void {
    if (this.membersSubscription) {
      this.membersSubscription.unsubscribe();
      this.membersSubscription = null;
    }
    if (this.invitationsSubscription) {
      this.invitationsSubscription.unsubscribe();
      this.invitationsSubscription = null;
    }
  }

  setActiveTab(tab: ActiveTab): void {
    this.activeTab.set(tab);
    if (tab === 'INVITATIONS' && this.pendingInvitations().length === 0) {
      this.loadInvitations();
    }
  }

  refreshCurrentTab(): void {
    if (this.activeTab() === 'MEMBERS') {
      this.loadMembers();
    } else {
      this.loadInvitations();
    }
  }

  loadMembers(): void {
    const code = this.orgCode();
    if (!code) {
      this.isLoading.set(false);
      return;
    }

    if (this.membersSubscription) {
      this.membersSubscription.unsubscribe();
      this.membersSubscription = null;
    }

    this.isLoading.set(true);
    this.error.set(null);

    const statusParam = this.selectedStatus() === 'ALL' ? undefined : this.selectedStatus();
    const queryParam = this.searchQuery().trim() || undefined;

    this.membersSubscription = this.orgApiService
      .getMemberDetails(code, statusParam, queryParam)
      .subscribe({
        next: (data) => {
          this.membersSubscription = null;
          const list = data || [];
          this.members.set(list);
          if (!queryParam && (!statusParam || statusParam === 'ALL')) {
            this.allFetchedMembers.set(list);
          } else if (this.allFetchedMembers().length === 0) {
            this.allFetchedMembers.set(list);
          }
          this.isLoading.set(false);
        },
        error: (err) => {
          this.membersSubscription = null;
          this.error.set(
            extractErrorMessage(
              err,
              'Failed to retrieve member roster. Please verify administrative permissions.'
            )
          );
          this.isLoading.set(false);
        },
      });
  }

  loadInvitations(): void {
    const code = this.orgCode();
    if (!code) return;

    if (this.invitationsSubscription) {
      this.invitationsSubscription.unsubscribe();
      this.invitationsSubscription = null;
    }

    this.isLoadingInvitations.set(true);

    this.invitationsSubscription = this.orgApiService
      .getOrganizationInvitations(code)
      .subscribe({
        next: (invites) => {
          this.invitationsSubscription = null;
          this.pendingInvitations.set(invites || []);
          this.isLoadingInvitations.set(false);
        },
        error: (err) => {
          this.invitationsSubscription = null;
          console.warn('Could not load organization invitations', err);
          this.isLoadingInvitations.set(false);
        },
      });
  }

  onSearchInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.searchQuery.set(target.value);

    if (this.searchDebounceTimer) {
      clearTimeout(this.searchDebounceTimer);
    }
    this.searchDebounceTimer = setTimeout(() => {
      this.loadMembers();
    }, 300);
  }

  clearSearch(): void {
    this.searchQuery.set('');
    this.loadMembers();
  }

  setStatusFilter(status: MemberStatusFilter): void {
    if (this.selectedStatus() === status) return;
    this.selectedStatus.set(status);
    this.loadMembers();
  }

  resetFilters(): void {
    this.searchQuery.set('');
    this.selectedStatus.set('ALL');
    this.loadMembers();
  }

  // Drawer Controls
  openDrawerForMember(member: OrganizationMemberDetailResponseDto): void {
    if (member.userId) {
      this.selectedMemberId.set(member.userId);
      this.isDetailDrawerOpen.set(true);
    }
  }

  closeDetailDrawer(): void {
    this.isDetailDrawerOpen.set(false);
    this.selectedMemberId.set(null);
  }

  // Modal Open Triggers
  openChangeRoleModal(member: OrganizationMemberDetailResponseDto): void {
    this.activeModalMember.set(member);
    this.isChangeRoleModalOpen.set(true);
  }

  openSuspendModal(member: OrganizationMemberDetailResponseDto): void {
    this.activeModalMember.set(member);
    this.isSuspendModalOpen.set(true);
  }

  openReactivateConfirm(member: OrganizationMemberDetailResponseDto): void {
    this.activeModalMember.set(member);
    this.isReactivateConfirmOpen.set(true);
  }

  openRemoveModal(member: OrganizationMemberDetailResponseDto): void {
    this.activeModalMember.set(member);
    this.isRemoveModalOpen.set(true);
  }

  openTransferAdminModal(member: OrganizationMemberDetailResponseDto): void {
    this.activeModalMember.set(member);
    this.isTransferAdminModalOpen.set(true);
  }

  openRevokeConfirm(invite: OrganizationInvitationResponseDto): void {
    this.activeRevokeInvite.set(invite);
    this.isRevokeConfirmOpen.set(true);
  }

  // Workflow Handlers
  onInvitationSent(): void {
    this.loadInvitations();
    this.activeTab.set('INVITATIONS');
  }

  onMemberUpdated(updated: OrganizationMemberDetailResponseDto): void {
    this.members.update((list) =>
      list.map((m) => (m.userId === updated.userId ? updated : m))
    );
    this.allFetchedMembers.update((list) =>
      list.map((m) => (m.userId === updated.userId ? updated : m))
    );
  }

  confirmReactivate(): void {
    const mem = this.activeModalMember();
    const org = this.orgCode();
    if (!mem || !mem.userId || !org || this.isReactivating()) return;

    this.isReactivating.set(true);

    this.orgApiService.reactivateMember(org, mem.userId).subscribe({
      next: (updated) => {
        this.isReactivating.set(false);
        this.isReactivateConfirmOpen.set(false);
        this.notificationService.success(
          `${this.getDisplayName(updated)} has been reactivated.`
        );
        this.onMemberUpdated(updated);
      },
      error: (err) => {
        this.isReactivating.set(false);
        this.notificationService.error(
          extractErrorMessage(err, 'Failed to reactivate member.')
        );
      },
    });
  }

  onMemberRemoved(removed: OrganizationMemberDetailResponseDto): void {
    this.members.update((list) => list.filter((m) => m.userId !== removed.userId));
    this.allFetchedMembers.update((list) => list.filter((m) => m.userId !== removed.userId));
    if (this.selectedMemberId() === removed.userId) {
      this.closeDetailDrawer();
    }
  }

  onAdminTransferred(): void {
    this.loadMembers();
    if (this.isDetailDrawerOpen()) {
      this.closeDetailDrawer();
    }
  }

  confirmRevokeInvitation(): void {
    const inv = this.activeRevokeInvite();
    const org = this.orgCode();
    if (!inv || !inv.id || !org || this.isRevoking()) return;

    this.isRevoking.set(true);

    this.orgApiService.revokeInvitation(org, inv.id).subscribe({
      next: () => {
        this.isRevoking.set(false);
        this.isRevokeConfirmOpen.set(false);
        this.notificationService.success(
          `Invitation for ${inv.inviteeEmail} has been revoked.`
        );
        this.pendingInvitations.update((list) => list.filter((item) => item.id !== inv.id));
      },
      error: (err) => {
        this.isRevoking.set(false);
        this.notificationService.error(
          extractErrorMessage(err, 'Failed to revoke invitation.')
        );
      },
    });
  }

  getDisplayName(member: OrganizationMemberDetailResponseDto): string {
    const fn = member.firstName?.trim() || '';
    const ln = member.lastName?.trim() || '';
    if (fn || ln) return `${fn} ${ln}`.trim();
    return member.email;
  }
}
