import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  inject,
  effect,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  LucideX,
  LucideUsers,
  LucideShield,
  LucideAlertCircle,
  LucideRefreshCw,
  LucideHardDrive,
  LucideShieldCheck,
  LucideMaximize,
  LucideMinimize,
  LucideArrowLeft,
  LucideHistory,
  LucideLayoutDashboard,
} from '@lucide/angular';
import { AuthStore } from '../../../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../../../state/workspace/workspace-context.store';
import { TeamDelegationService } from '../../services/team-delegation.service';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import {
  TeamAccessBoundaryDto,
  TeamMemberAccessDto,
} from '../../models/team-delegation.models';
import { PermissionApiService } from '../../../permissions/services/permission-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  GroupResponseDto,
  OrganizationMemberResponseDto,
} from '../../../../core/models/organization.model';
import { hasOrganizationCapability } from '../../../../core/models/organization-capability.model';
import { UnifiedAuditApiService } from '../../../../core/services/unified-audit-api.service';
import { UnifiedAuditEventResponseDto } from '../../../../core/models/unified-audit.model';

import { TeamOverviewTabComponent, TeamActivityItem } from './tabs/team-overview-tab.component';
import { TeamMembersTabComponent } from './tabs/team-members-tab.component';
import { TeamWorkspaceAccessTabComponent } from './tabs/team-workspace-access-tab.component';
import { TeamMemberAccessTabComponent } from './tabs/team-member-access-tab.component';
import { TeamActivityTabComponent } from './tabs/team-activity-tab.component';

import { AddTeamMemberModalComponent } from '../add-team-member-modal/add-team-member-modal.component';
import { SelectWorkspaceModalComponent } from '../select-workspace-modal/select-workspace-modal.component';
import { GrantMemberAccessModalComponent } from '../grant-member-access-modal/grant-member-access-modal.component';

export type TeamDrawerWorkflow =
  | 'NONE'
  | 'GRANT_ACCESS'
  | 'EDIT_ACCESS'
  | 'TRANSFER_MANAGER';

export type TeamDrawerTab =
  | 'OVERVIEW'
  | 'MEMBERS'
  | 'WORKSPACE'
  | 'MEMBER_ACCESS'
  | 'ACTIVITY';

@Component({
  selector: 'app-team-details-drawer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideX,
    LucideUsers,
    LucideShield,
    LucideAlertCircle,
    LucideRefreshCw,
    LucideHardDrive,
    LucideShieldCheck,
    LucideMaximize,
    LucideMinimize,
    LucideArrowLeft,
    LucideHistory,
    LucideLayoutDashboard,
    TeamOverviewTabComponent,
    TeamMembersTabComponent,
    TeamWorkspaceAccessTabComponent,
    TeamMemberAccessTabComponent,
    TeamActivityTabComponent,
    AddTeamMemberModalComponent,
    SelectWorkspaceModalComponent,
    GrantMemberAccessModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div class="window-backdrop" (click)="close()">
        <div
          class="window-panel"
          [class.is-fullscreen]="isFullScreen()"
          role="dialog"
          aria-modal="true"
          aria-labelledby="drawer-title"
          (click)="$event.stopPropagation()"
        >
        <!-- ── Sheet Header ────────────────────────────── -->
        <header class="drawer-header">
          <div class="header-content">
            @if (activeWorkflow() !== 'NONE') {
              <!-- Workflow Header Mode -->
              <button
                type="button"
                class="btn-back-workflow"
                (click)="activeWorkflow.set('NONE')"
                title="Return to team overview"
              >
                <svg lucideArrowLeft class="icon-back"></svg>
                <span>Back to Team</span>
              </button>
              <div class="workflow-header-title">
                <span class="workflow-badge">Workflow</span>
                <h2 id="drawer-title">{{ getWorkflowTitle() }}</h2>
              </div>
            } @else {
              <!-- Standard Header Mode -->
              @let currentTeam = teamDetails() || team();
              <div class="team-monogram" aria-hidden="true">
                {{ (currentTeam?.name || 'T').charAt(0).toUpperCase() }}
              </div>
              <div class="header-text-group">
                <div class="header-title-row">
                  <h2 id="drawer-title">{{ currentTeam?.name || 'Team Details' }}</h2>
                  @if (currentTeam?.status) {
                    <span
                      class="status-badge"
                      [class.status-active]="currentTeam?.status === 'ACTIVE'"
                      [class.status-archived]="currentTeam?.status === 'ARCHIVED'"
                    >
                      <span
                        class="status-dot"
                        [class.dot-active]="currentTeam?.status === 'ACTIVE'"
                        [class.dot-archived]="currentTeam?.status === 'ARCHIVED'"
                      ></span>
                      {{ formatStatus(currentTeam?.status) }}
                    </span>
                  }
                </div>
                <div class="header-meta-row">
                  <span class="meta-manager">
                    Managed by <strong>{{ currentManager() ? getDisplayName(currentManager()!) : 'Unassigned' }}</strong>
                  </span>
                  <span class="meta-sep">·</span>
                  <span class="meta-my-role">
                    My Role: <strong>{{ myRole() }}</strong>
                  </span>
                </div>
              </div>
            }
          </div>

          <!-- Header Action Buttons -->
          <div class="header-controls">
            <!-- Fullscreen / Wide Toggle -->
            <button
              type="button"
              class="btn-header-icon"
              (click)="toggleFullScreen()"
              [title]="isFullScreen() ? 'Restore sheet size' : 'Expand full screen'"
              aria-label="Toggle full screen"
            >
              @if (isFullScreen()) {
                <svg lucideMinimize class="icon-header"></svg>
              } @else {
                <svg lucideMaximize class="icon-header"></svg>
              }
            </button>

            <!-- Close Sheet -->
            <button
              type="button"
              class="btn-header-icon is-close"
              (click)="close()"
              title="Close sheet (Esc)"
              aria-label="Close team details"
            >
              <svg lucideX class="icon-header"></svg>
            </button>
          </div>
        </header>

        <!-- ── Navigation Tabs (Pill Style matching Explorer) ── -->
        @if (activeWorkflow() === 'NONE' && !isLoading() && !error()) {
          <div class="drawer-tabs-wrapper">
            <div class="drawer-tabs" role="tablist">
              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="activeTab() === 'OVERVIEW'"
                [attr.aria-selected]="activeTab() === 'OVERVIEW'"
                (click)="activeTab.set('OVERVIEW')"
              >
                <svg lucideLayoutDashboard class="tab-icon"></svg>
                <span>Overview</span>
              </button>

              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="activeTab() === 'MEMBERS'"
                [attr.aria-selected]="activeTab() === 'MEMBERS'"
                (click)="activeTab.set('MEMBERS')"
              >
                <svg lucideUsers class="tab-icon"></svg>
                <span>Members</span>
                <span class="tab-badge">{{ members().length }}</span>
              </button>

              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="activeTab() === 'WORKSPACE'"
                [attr.aria-selected]="activeTab() === 'WORKSPACE'"
                (click)="activeTab.set('WORKSPACE')"
              >
                <svg lucideHardDrive class="tab-icon"></svg>
                <span>Workspace Access</span>
                @if (teamBoundary()) {
                  <span class="tab-badge active-badge">1</span>
                }
              </button>

              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="activeTab() === 'MEMBER_ACCESS'"
                [attr.aria-selected]="activeTab() === 'MEMBER_ACCESS'"
                (click)="activeTab.set('MEMBER_ACCESS')"
              >
                <svg lucideShieldCheck class="tab-icon"></svg>
                <span>Member Access</span>
                <span class="tab-badge" [class.highlight]="memberAccessList().length > 0">
                  {{ memberAccessList().length }}
                </span>
              </button>

              <button
                type="button"
                role="tab"
                class="tab-btn"
                [class.active]="activeTab() === 'ACTIVITY'"
                [attr.aria-selected]="activeTab() === 'ACTIVITY'"
                (click)="activeTab.set('ACTIVITY')"
              >
                <svg lucideHistory class="tab-icon"></svg>
                <span>Activity</span>
                <span class="tab-badge">{{ teamActivities().length }}</span>
              </button>
            </div>
          </div>
        }

        <!-- ── Sheet Body Container ────────────────────── -->
        <div class="drawer-body">
          @if (isLoading()) {
            <div class="state-container loading-state">
              <div class="spinner"></div>
              <p>Loading team governance data...</p>
            </div>
          } @else if (error()) {
            <div class="state-container error-state">
              <div class="state-icon-box error-box">
                <svg lucideAlertCircle class="state-icon"></svg>
              </div>
              <h3>Unable to load team details</h3>
              <p>{{ error() }}</p>
              <button type="button" class="btn-retry" (click)="loadData()">
                <svg lucideRefreshCw class="btn-icon"></svg>
                Retry
              </button>
            </div>
          } @else {
            @let currentTeam = teamDetails() || team();
            @if (currentTeam) {

              <!-- ── In-Sheet Workflows (Zero Modal Stacking) ── -->
              @if (activeWorkflow() === 'GRANT_ACCESS' || activeWorkflow() === 'EDIT_ACCESS') {
                <sd-grant-member-access-modal
                  [isOpen]="true"
                  [inline]="true"
                  [orgCode]="effectiveOrgCode()"
                  [teamId]="currentTeam.id"
                  [teamName]="currentTeam.name"
                  [boundary]="teamBoundary()"
                  [availableMembers]="members()"
                  [editingAccess]="editingAccess()"
                  [preSelectedEmail]="preSelectedMemberEmail()"
                  (close)="onWorkflowClosed()"
                  (accessSaved)="onAccessSaved($event)"
                />
              } @else if (activeWorkflow() === 'TRANSFER_MANAGER') {
                <!-- Dedicated In-Sheet Manager Transfer Workflow -->
                <div class="workflow-inline-panel">
                  <div class="workflow-card">
                    <div class="workflow-info-banner">
                      <svg lucideShield class="banner-icon"></svg>
                      <div class="banner-text">
                        <span class="banner-title">Transfer Team Delegation Authority</span>
                        <p class="banner-desc">
                          Assigning a new Team Manager transfers roster and delegation authority to the selected member.
                          The current manager will remain an active member of this team. Existing member access grants remain intact.
                        </p>
                      </div>
                    </div>

                    @if (transferError()) {
                      <div class="alert-banner error-banner">
                        <svg lucideAlertCircle class="alert-icon"></svg>
                        <span>{{ transferError() }}</span>
                      </div>
                    }

                    <div class="current-mgr-box">
                      <span class="box-label">Current Manager</span>
                      @if (currentManager(); as mgr) {
                        <div class="mgr-display">
                          <div class="mgr-avatar">{{ getInitials(mgr.firstName, mgr.lastName, mgr.email) }}</div>
                          <div class="mgr-text">
                            <span class="mgr-name">{{ getDisplayName(mgr) }}</span>
                            <span class="mgr-email">{{ mgr.email }}</span>
                          </div>
                        </div>
                      } @else {
                        <span class="no-mgr">No active manager designated</span>
                      }
                    </div>

                    <div class="form-group">
                      <label class="form-label" for="new-manager-select">
                        Select New Team Manager <span class="required">*</span>
                      </label>
                      <div class="select-wrap">
                        <select
                          id="new-manager-select"
                          class="form-select"
                          [ngModel]="selectedNewManagerUserId()"
                          (ngModelChange)="selectedNewManagerUserId.set($event)"
                          [disabled]="isTransferringManager()"
                        >
                          <option value="" disabled>Select an active team member...</option>
                          @for (m of eligibleNewManagers(); track m.userId) {
                            <option [value]="m.userId">
                              {{ getDisplayName(m) }} ({{ m.email }})
                            </option>
                          }
                        </select>
                      </div>
                    </div>

                    <div class="workflow-actions">
                      <button
                        type="button"
                        class="btn-secondary-action"
                        (click)="activeWorkflow.set('NONE')"
                        [disabled]="isTransferringManager()"
                      >
                        Cancel
                      </button>
                      <button
                        type="button"
                        class="btn-primary-action"
                        (click)="onConfirmTransferManager()"
                        [disabled]="isTransferringManager() || !selectedNewManagerUserId()"
                      >
                        @if (isTransferringManager()) {
                          <span class="spinner-tiny"></span>
                          <span>Transferring...</span>
                        } @else {
                          <span>Confirm Transfer</span>
                        }
                      </button>
                    </div>
                  </div>
                </div>
              } @else {
                <!-- ── Tab Views ───────────────────────────────── -->
                <div class="tab-content-area">
                  @if (activeTab() === 'OVERVIEW') {
                    <sd-team-overview-tab
                      [team]="currentTeam"
                      [manager]="currentManager()"
                      [memberCount]="members().length"
                      [boundary]="teamBoundary()"
                      [grantsCount]="memberAccessList().length"
                      [myRole]="myRole()"
                      [canManageOrg]="isCurrentUserOrgAdmin()"
                      [recentActivity]="teamActivities().slice(0, 4)"
                      (tabChange)="activeTab.set($event)"
                      (allocateWorkspace)="isSelectWorkspaceModalOpen.set(true)"
                      (addMember)="isAddMemberModalOpen.set(true)"
                      (giveAccess)="openGrantMemberAccessWorkflow()"
                    />
                  } @else if (activeTab() === 'MEMBERS') {
                    <sd-team-members-tab
                      [members]="members()"
                      [manager]="currentManager()"
                      [memberGrantsMap]="memberGrantsMap()"
                      [canManageMembers]="canManageMemberAccess()"
                      [canTransferManager]="canManageTeam() || isCurrentUserManager()"
                      [isTeamActive]="currentTeam.status === 'ACTIVE'"
                      (addMember)="isAddMemberModalOpen.set(true)"
                      (giveAccess)="openGrantForMember($event.email)"
                      (changeManager)="openTransferManagerWorkflow()"
                      (removeMember)="onConfirmRemoveMember($event)"
                      (viewMemberGrants)="jumpToMemberAccess($event)"
                    />
                  } @else if (activeTab() === 'WORKSPACE') {
                    <sd-team-workspace-access-tab
                      [boundary]="teamBoundary()"
                      [canManageOrg]="isCurrentUserOrgAdmin()"
                      [isTeamActive]="currentTeam.status === 'ACTIVE'"
                      (allocateWorkspace)="isSelectWorkspaceModalOpen.set(true)"
                      (changeWorkspace)="isSelectWorkspaceModalOpen.set(true)"
                      (revokeWorkspace)="onConfirmRevokeBoundary()"
                    />
                  } @else if (activeTab() === 'MEMBER_ACCESS') {
                    <sd-team-member-access-tab
                      [accessList]="displayedAccessList()"
                      [canManageAccess]="canManageMemberAccess()"
                      [isTeamActive]="currentTeam.status === 'ACTIVE'"
                      [initialSearchQuery]="memberAccessSearchQuery()"
                      (giveAccess)="openGrantMemberAccessWorkflow()"
                      (editAccess)="openEditMemberAccessWorkflow($event)"
                      (revokeAccess)="onRevokeMemberAccess($event)"
                    />
                  } @else if (activeTab() === 'ACTIVITY') {
                    <sd-team-activity-tab
                      [activities]="teamActivities()"
                      [isLoading]="isLoading()"
                      (refresh)="loadData()"
                    />
                  }
                </div>
              }
            }
          }
        </div>
      </div>
    </div>

      <!-- Standalone Add Team Member Modal -->
      @if (isAddMemberModalOpen()) {
        <sd-add-team-member-modal
          [isOpen]="isAddMemberModalOpen()"
          [orgCode]="effectiveOrgCode()"
          [teamId]="((teamDetails() || team())?.id || '') + ''"
          [teamName]="(teamDetails() || team())?.name || ''"
          [existingMemberUserIds]="existingMemberUserIds()"
          (closed)="isAddMemberModalOpen.set(false)"
          (membersAdded)="onMemberAdded()"
        />
      }

      <!-- Standalone Select Workspace Modal -->
      @if (isSelectWorkspaceModalOpen()) {
        <sd-select-workspace-modal
          [isOpen]="isSelectWorkspaceModalOpen()"
          [orgCode]="effectiveOrgCode()"
          [team]="teamDetails() || team()"
          (close)="isSelectWorkspaceModalOpen.set(false)"
          (workspaceAllocated)="onWorkspaceAllocated($event)"
        />
      }
    }
  `,
  styles: [
    `
      .window-backdrop {
        position: fixed;
        inset: 0;
        background-color: rgba(15, 23, 42, 0.55);
        backdrop-filter: blur(4px);
        z-index: 1000;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1.5rem;
        animation: fadeIn 0.2s ease-out;
      }

      .window-panel {
        width: 100%;
        max-width: 1060px;
        height: min(88vh, 880px);
        background-color: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 1rem;
        box-shadow: 0 25px 50px -12px rgba(15, 23, 42, 0.25);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        animation: windowScaleIn 0.2s cubic-bezier(0.16, 1, 0.3, 1);
        transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);

        &.is-fullscreen {
          max-width: calc(100vw - 2rem);
          height: calc(100vh - 2rem);
          border-radius: 0.75rem;
        }
      }

      @keyframes windowScaleIn {
        from {
          opacity: 0;
          transform: scale(0.96) translateY(8px);
        }
        to {
          opacity: 1;
          transform: scale(1) translateY(0);
        }
      }

      @keyframes fadeIn {
        from {
          opacity: 0;
        }
        to {
          opacity: 1;
        }
      }

      /* ── Sheet Header ────────────────────────────── */
      .drawer-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 1.25rem 1.75rem;
        background: #ffffff;
        border-bottom: 1px solid #e2e8f0;
      }

      .header-content {
        display: flex;
        align-items: center;
        gap: 0.875rem;
        min-width: 0;
      }

      .team-monogram {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: 10px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        font-size: 1.125rem;
        font-weight: 700;
        flex-shrink: 0;
      }

      .header-text-group {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        min-width: 0;
      }

      .header-title-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;

        h2 {
          margin: 0;
          font-size: 1.25rem;
          font-weight: 600;
          color: #0f172a;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
      }

      .status-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.15rem 0.45rem;
        border-radius: 9999px;

        &.status-active {
          background: #f0fdf4;
          color: #15803d;
          border: 1px solid #bbf7d0;

          .status-dot {
            background-color: #16a34a;
          }
        }

        &.status-archived {
          background: #f4f4f5;
          color: #71717a;
          border: 1px solid #e4e4e7;

          .status-dot {
            background-color: #a1a1aa;
          }
        }

        .status-dot {
          width: 5px;
          height: 5px;
          border-radius: 50%;
        }
      }

      .header-meta-row {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.75rem;
        color: #64748b;
      }

      .meta-sep {
        color: #cbd5e1;
      }

      .header-controls {
        display: flex;
        align-items: center;
        gap: 0.4rem;
      }

      .btn-header-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        background: transparent;
        border: 1px solid #e2e8f0;
        border-radius: 7px;
        color: #64748b;
        cursor: pointer;
        transition: all 0.12s ease;

        &:hover {
          background: #f1f5f9;
          color: #0f172a;
        }

        &.is-close:hover {
          background: #fef2f2;
          color: #dc2626;
          border-color: #fecaca;
        }
      }

      .icon-header {
        width: 16px;
        height: 16px;
      }

      /* ── Workflow Header ───────────────────────── */
      .btn-back-workflow {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.4rem 0.75rem;
        background: #f8fafc;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        color: #334155;
        cursor: pointer;

        &:hover {
          background: #f1f5f9;
        }
      }

      .icon-back {
        width: 14px;
        height: 14px;
      }

      .workflow-header-title {
        display: flex;
        align-items: center;
        gap: 0.5rem;

        h2 {
          margin: 0;
          font-size: 1.125rem;
          font-weight: 600;
          color: #0f172a;
        }
      }

      .workflow-badge {
        font-size: 0.65rem;
        font-weight: 700;
        background: #fff7ed;
        color: #c2410c;
        border: 1px solid #fed7aa;
        padding: 0.1rem 0.4rem;
        border-radius: 4px;
        text-transform: uppercase;
      }

      /* ── Navigation Tabs (Segmented Pill Style matching Explorer) ── */
      .drawer-tabs-wrapper {
        padding: 0.625rem 1.75rem 0.375rem;
        background: #ffffff;
        border-bottom: 1px solid #f1f5f9;
        flex-shrink: 0;
      }

      .drawer-tabs {
        display: flex;
        background: #f1f5f9;
        border-radius: 0.625rem;
        padding: 0.25rem;
        gap: 0.25rem;
        overflow-x: auto;
      }

      .tab-btn {
        flex: 1;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.4rem;
        padding: 0.5rem 0.85rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: #64748b;
        border: none;
        background: transparent;
        border-radius: 0.5rem;
        cursor: pointer;
        transition: all 0.15s ease;
        white-space: nowrap;

        &:hover {
          color: #0f172a;
        }

        &.active {
          color: #ea580c;
          background: #ffffff;
          font-weight: 600;
          box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
          border: 1px solid #fde8db;

          .tab-icon {
            color: #ea580c;
          }
        }
      }

      .tab-icon {
        width: 15px;
        height: 15px;
        color: #94a3b8;
      }

      .tab-badge {
        font-size: 0.6875rem;
        font-weight: 700;
        background: #e2e8f0;
        color: #475569;
        padding: 0.05rem 0.4rem;
        border-radius: 9999px;

        &.active-badge {
          background: #f0fdf4;
          color: #15803d;
        }

        &.highlight {
          background: #fff7ed;
          color: #c2410c;
        }
      }

      /* ── Drawer Body & Content ─────────────────── */
      .drawer-body {
        flex: 1;
        overflow-y: auto;
        padding: 1.5rem 1.75rem;
        display: flex;
        flex-direction: column;
      }

      .tab-content-area {
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      /* Loading & Error */
      .state-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 4rem 1.5rem;
        text-align: center;
        gap: 0.75rem;
      }

      .spinner {
        width: 32px;
        height: 32px;
        border: 3px solid rgba(249, 115, 22, 0.2);
        border-top-color: #f97316;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
      }

      .spinner-tiny {
        width: 12px;
        height: 12px;
        border: 2px solid rgba(255, 255, 255, 0.3);
        border-top-color: #ffffff;
        border-radius: 50%;
        animation: spin 0.8s linear infinite;
      }

      @keyframes spin {
        to { transform: rotate(360deg); }
      }

      .btn-retry {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.45rem 0.875rem;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 500;
        color: #334155;
        cursor: pointer;
      }

      /* ── Workflow Transfer Panel ───────────────── */
      .workflow-inline-panel {
        display: flex;
        flex-direction: column;
      }

      .workflow-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        padding: 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      .workflow-info-banner {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.875rem 1rem;
        background: #fff7ed;
        border: 1px solid #fed7aa;
        border-radius: 8px;
      }

      .banner-icon {
        width: 18px;
        height: 18px;
        color: #ea580c;
        flex-shrink: 0;
        margin-top: 0.1rem;
      }

      .banner-text {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }

      .banner-title {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #9a3412;
      }

      .banner-desc {
        font-size: 0.75rem;
        color: #7c2d12;
        margin: 0;
        line-height: 1.4;
      }

      .alert-banner {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem 1rem;
        border-radius: 8px;
        font-size: 0.8125rem;

        &.error-banner {
          background: #fef2f2;
          color: #b91c1c;
          border: 1px solid #fecaca;
        }
      }

      .alert-icon {
        width: 16px;
        height: 16px;
        flex-shrink: 0;
      }

      .current-mgr-box {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }

      .box-label {
        font-size: 0.6875rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: #64748b;
      }

      .mgr-display {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.625rem 0.875rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
      }

      .mgr-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 0.75rem;
        font-weight: 700;
        color: #334155;
      }

      .mgr-text {
        display: flex;
        flex-direction: column;
      }

      .mgr-name {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
      }

      .mgr-email {
        font-size: 0.75rem;
        color: #64748b;
      }

      .no-mgr {
        font-size: 0.8125rem;
        color: #94a3b8;
        font-style: italic;
      }

      .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
      }

      .form-label {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #334155;
      }

      .required {
        color: #ef4444;
      }

      .select-wrap {
        position: relative;
      }

      .form-select {
        width: 100%;
        padding: 0.6rem 0.875rem;
        border: 1px solid #cbd5e1;
        border-radius: 8px;
        font-size: 0.875rem;
        color: #0f172a;
        background: #ffffff;
        transition: border-color 0.15s ease;

        &:focus {
          outline: none;
          border-color: #f97316;
        }
      }

      .workflow-actions {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.75rem;
        padding-top: 0.5rem;
        border-top: 1px solid #f1f5f9;
      }

      .btn-secondary-action {
        padding: 0.45rem 0.85rem;
        background: #ffffff;
        color: #475569;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 500;
        cursor: pointer;

        &:hover {
          background: #f8fafc;
        }
      }

      .btn-primary-action {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.45rem 0.95rem;
        background: #f97316;
        color: #ffffff;
        border: none;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 600;
        cursor: pointer;

        &:hover {
          background: #ea580c;
        }
      }
    `,
  ],
})
export class TeamDetailsDrawerComponent {
  private readonly organizationApiService = inject(OrganizationApiService);
  private readonly teamDelegationService = inject(TeamDelegationService);
  private readonly authStore = inject(AuthStore);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly notificationService = inject(NotificationService);
  private readonly permissionApiService = inject(PermissionApiService);
  private readonly auditApiService = inject(UnifiedAuditApiService);

  isOpen = input<boolean>(false);
  orgCode = input.required<string>();
  team = input<GroupResponseDto | null>(null);

  readonly effectiveOrgCode = computed<string>(() => {
    const direct = this.orgCode();
    if (direct) return direct;
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
    return '';
  });

  closeDrawer = output<void>();
  teamUpdated = output<void>();

  // View & Workflow State Signals
  readonly isFullScreen = signal<boolean>(false);
  readonly activeWorkflow = signal<TeamDrawerWorkflow>('NONE');
  readonly activeTab = signal<TeamDrawerTab>('OVERVIEW');

  // Data Signals
  readonly teamDetails = signal<GroupResponseDto | null>(null);
  readonly members = signal<OrganizationMemberResponseDto[]>([]);
  readonly teamBoundary = signal<TeamAccessBoundaryDto | null>(null);
  readonly memberAccessList = signal<TeamMemberAccessDto[]>([]);
  readonly liveTeamAuditEvents = signal<UnifiedAuditEventResponseDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly error = signal<string | null>(null);

  // Search filter signals
  readonly memberAccessSearchQuery = signal<string>('');

  // Delegation Workflow State
  readonly editingAccess = signal<TeamMemberAccessDto | null>(null);
  readonly preSelectedMemberEmail = signal<string>('');

  // Manager Transfer Workflow State
  readonly selectedNewManagerUserId = signal<string>('');
  readonly isTransferringManager = signal<boolean>(false);
  readonly transferError = signal<string | null>(null);

  // Standalone Modal Signals
  readonly isAddMemberModalOpen = signal<boolean>(false);
  readonly isSelectWorkspaceModalOpen = signal<boolean>(false);

  // Context & Capability Signals
  readonly currentUser = this.authStore.currentUser;

  readonly isCurrentUserOrgAdmin = computed(() => {
    const user = this.currentUser();
    const org = this.effectiveOrgCode();
    if (!user || !org) return false;
    return (
      hasOrganizationCapability(user, org, 'ORG_TEAMS_MANAGE') ||
      hasOrganizationCapability(user, org, 'ORG_WORKSPACES_MANAGE')
    );
  });

  readonly isCurrentUserManager = computed(() => {
    const user = this.currentUser();
    if (!user) return false;
    const userId = user.id;
    const email = user.email?.toLowerCase();
    return this.members().some(
      (m) =>
        (userId && m.userId === userId && m.groupRole === 'MANAGER') ||
        (email && m.email?.toLowerCase() === email && m.groupRole === 'MANAGER')
    );
  });

  readonly canManageTeam = computed(() => this.isCurrentUserOrgAdmin());
  readonly canManageMemberAccess = computed(() => this.isCurrentUserOrgAdmin() || this.isCurrentUserManager());
  readonly isOrdinaryMember = computed(() => !this.canManageMemberAccess());

  readonly currentManager = computed<OrganizationMemberResponseDto | null>(() => {
    return this.members().find((m) => m.groupRole === 'MANAGER') || null;
  });

  readonly myRole = computed<'Team Manager' | 'Member' | '—'>(() => {
    const currentUserId = this.currentUser()?.id;
    if (!currentUserId) return '—';
    const m = this.members().find((mem) => mem.userId === currentUserId);
    if (!m) return '—';
    return m.groupRole === 'MANAGER' ? 'Team Manager' : 'Member';
  });

  readonly eligibleNewManagers = computed<OrganizationMemberResponseDto[]>(() => {
    return this.members().filter((m) => m.groupRole !== 'MANAGER');
  });

  readonly existingMemberUserIds = computed<string[]>(() => {
    return this.members().map((m) => m.userId);
  });

  readonly memberGrantsMap = computed<Record<string, number>>(() => {
    const map: Record<string, number> = {};
    for (const a of this.memberAccessList()) {
      map[a.userEmail] = (map[a.userEmail] || 0) + 1;
    }
    return map;
  });

  readonly userOwnedAccessList = computed(() => {
    const userEmail = this.currentUser()?.email;
    if (!userEmail) return [];
    return this.memberAccessList().filter((a) => a.userEmail === userEmail);
  });

  readonly displayedAccessList = computed(() => {
    if (this.isOrdinaryMember()) {
      return this.userOwnedAccessList();
    }
    return this.memberAccessList();
  });

  readonly teamActivities = computed<TeamActivityItem[]>(() => {
    const live = this.liveTeamAuditEvents();
    if (live && live.length > 0) {
      return live.map((ev) => {
        let iconType: 'TEAM' | 'MEMBER' | 'WORKSPACE' | 'ACCESS' = 'TEAM';
        const actionUpper = (ev.action || '').toUpperCase();
        if (actionUpper.includes('MEMBER')) iconType = 'MEMBER';
        else if (actionUpper.includes('WORKSPACE') || actionUpper.includes('ALLOCAT')) iconType = 'WORKSPACE';
        else if (actionUpper.includes('PERMISSION') || actionUpper.includes('ACCESS')) iconType = 'ACCESS';

        const desc = ev.actionDisplay || (ev.changes?.details ? ev.changes.details : ev.action);
        return {
          id: ev.id,
          timestamp: ev.timestamp,
          actorName: ev.actor?.displayName || ev.actor?.email || 'System',
          actorEmail: ev.actor?.email,
          description: desc,
          iconType,
          badgeText: ev.changes?.newValue || ev.result?.status,
        };
      });
    }

    return [];
  });

  constructor() {
    effect(() => {
      const open = this.isOpen();
      const currentTeam = this.team();
      if (open && currentTeam) {
        this.resetState();
        this.loadData();
      }
    });
  }

  private resetState() {
    this.isFullScreen.set(false);
    this.activeWorkflow.set('NONE');
    this.activeTab.set('OVERVIEW');
    this.memberAccessSearchQuery.set('');
    this.editingAccess.set(null);
    this.preSelectedMemberEmail.set('');
    this.selectedNewManagerUserId.set('');
    this.transferError.set(null);
  }

  loadData(): void {
    const t = this.team();
    const code = this.effectiveOrgCode();
    if (!t || !code) return;

    this.isLoading.set(true);
    this.error.set(null);

    forkJoin({
      team: this.organizationApiService.getGroupById(code, t.id).pipe(catchError(() => of(t))),
      members: this.organizationApiService.getGroupMembers(code, t.id).pipe(catchError(() => of([]))),
      boundary: this.teamDelegationService.getBoundary(code, t.id).pipe(catchError(() => of(null))),
      delegations: this.teamDelegationService.getMemberAccessList(code, t.id).pipe(catchError(() => of([]))),
      auditEvents: this.auditApiService.getEvents({ scopeType: 'TEAM', teamId: t.id }, 0, 50).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ team, members, boundary, delegations, auditEvents }) => {
        this.teamDetails.set(team);
        this.members.set(members || []);
        this.teamBoundary.set(boundary);
        this.memberAccessList.set(delegations || []);
        this.liveTeamAuditEvents.set(auditEvents?.content || []);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.error.set(err?.error?.message || 'Failed to load team governance details');
        this.isLoading.set(false);
      },
    });
  }

  toggleFullScreen() {
    this.isFullScreen.update((v) => !v);
  }

  getWorkflowTitle(): string {
    switch (this.activeWorkflow()) {
      case 'GRANT_ACCESS':
        return 'Give Member Access';
      case 'EDIT_ACCESS':
        return 'Edit Member Access';
      case 'TRANSFER_MANAGER':
        return 'Change Team Manager';
      default:
        return 'Workflow';
    }
  }

  openGrantMemberAccessWorkflow(preSelectedEmail = '') {
    this.editingAccess.set(null);
    this.preSelectedMemberEmail.set(preSelectedEmail);
    this.activeWorkflow.set('GRANT_ACCESS');
  }

  openGrantForMember(email: string) {
    this.openGrantMemberAccessWorkflow(email);
  }

  openEditMemberAccessWorkflow(access: TeamMemberAccessDto) {
    this.editingAccess.set(access);
    this.preSelectedMemberEmail.set(access.userEmail);
    this.activeWorkflow.set('EDIT_ACCESS');
  }

  openTransferManagerWorkflow() {
    this.selectedNewManagerUserId.set('');
    this.transferError.set(null);
    this.activeWorkflow.set('TRANSFER_MANAGER');
  }

  onWorkflowClosed() {
    this.activeWorkflow.set('NONE');
    this.editingAccess.set(null);
    this.preSelectedMemberEmail.set('');
    this.selectedNewManagerUserId.set('');
    this.transferError.set(null);
    this.isAddMemberModalOpen.set(false);
    this.isSelectWorkspaceModalOpen.set(false);
  }

  onAccessSaved(_savedAccess: TeamMemberAccessDto) {
    this.activeWorkflow.set('NONE');
    this.editingAccess.set(null);
    this.preSelectedMemberEmail.set('');
    this.loadData();
    this.teamUpdated.emit();
  }

  onMemberAdded() {
    this.isAddMemberModalOpen.set(false);
    this.notificationService.success('Team member added successfully.');
    this.loadData();
    this.teamUpdated.emit();
  }

  onWorkspaceAllocated(_event: any) {
    this.isSelectWorkspaceModalOpen.set(false);
    this.notificationService.success('Team Workspace Access updated successfully.');
    this.loadData();
    this.teamUpdated.emit();
  }

  onConfirmTransferManager() {
    const t = this.teamDetails() || this.team();
    const newUserId = this.selectedNewManagerUserId();
    const code = this.effectiveOrgCode();
    if (!t || !newUserId || !code) return;

    this.isTransferringManager.set(true);
    this.transferError.set(null);

    this.organizationApiService
      .transferTeamManager(code, t.id, newUserId)
      .subscribe({
        next: () => {
          this.isTransferringManager.set(false);
          this.notificationService.success('Team Manager authority transferred successfully.');
          this.activeWorkflow.set('NONE');
          this.activeTab.set('MEMBERS');
          this.loadData();
          this.teamUpdated.emit();
        },
        error: (err) => {
          this.isTransferringManager.set(false);
          this.transferError.set(err?.error?.message || 'Failed to transfer manager role');
        },
      });
  }

  onConfirmRemoveMember(member: OrganizationMemberResponseDto) {
    const t = this.teamDetails() || this.team();
    const code = this.effectiveOrgCode();
    if (!t || !code) return;

    this.organizationApiService
      .removeGroupMember(code, t.id, member.userId)
      .subscribe({
        next: () => {
          this.notificationService.success(`${this.getDisplayName(member)} removed from team.`);
          this.loadData();
          this.teamUpdated.emit();
        },
        error: (err) => {
          this.notificationService.error(err?.error?.message || 'Failed to remove member from team');
        },
      });
  }

  onConfirmRevokeBoundary() {
    const t = this.teamDetails() || this.team();
    const b = this.teamBoundary();
    if (!t || !b) return;

    const permId = b.permissionId;
    if (!permId) {
      this.notificationService.error('Cannot revoke: Workspace Permission ID is missing.');
      return;
    }

    this.permissionApiService
      .revokePermission(permId)
      .subscribe({
        next: () => {
          this.notificationService.success('Team Workspace Access revoked successfully.');
          this.teamBoundary.set(null);
          this.loadData();
          this.teamUpdated.emit();
        },
        error: (err) => {
          this.notificationService.error(err?.error?.message || 'Failed to revoke workspace access');
        },
      });
  }

  onRevokeMemberAccess(item: TeamMemberAccessDto) {
    const t = this.teamDetails() || this.team();
    const code = this.effectiveOrgCode();
    if (!t || !code) return;

    this.teamDelegationService
      .revokeMemberAccess(code, t.id, item.permissionId)
      .pipe(
        catchError((err) => {
          this.notificationService.error(err?.error?.message || 'Failed to revoke member access');
          return of(null);
        })
      )
      .subscribe(() => {
        this.notificationService.success(`Delegation for ${item.userEmail} revoked.`);
        this.loadData();
        this.teamUpdated.emit();
      });
  }

  jumpToMemberAccess(email: string) {
    this.memberAccessSearchQuery.set(email);
    this.activeTab.set('MEMBER_ACCESS');
  }

  getDisplayName(member: OrganizationMemberResponseDto): string {
    if (member.firstName || member.lastName) {
      return `${member.firstName || ''} ${member.lastName || ''}`.trim();
    }
    return member.email;
  }

  getInitials(firstName?: string, lastName?: string, email?: string): string {
    if (firstName && lastName) {
      return `${firstName.charAt(0)}${lastName.charAt(0)}`.toUpperCase();
    }
    if (firstName) return firstName.slice(0, 2).toUpperCase();
    if (email) return email.slice(0, 2).toUpperCase();
    return 'U';
  }

  formatStatus(status?: string): string {
    if (!status) return 'Active';
    return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
  }

  close() {
    this.closeDrawer.emit();
  }

  @HostListener('document:keydown.escape')
  onEscape() {
    if (this.isOpen()) {
      if (this.activeWorkflow() !== 'NONE') {
        this.activeWorkflow.set('NONE');
      } else {
        this.close();
      }
    }
  }
}
