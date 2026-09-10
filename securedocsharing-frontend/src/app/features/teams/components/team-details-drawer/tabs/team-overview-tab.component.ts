import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import {
  LucideUsers,
  LucideShield,
  LucideHardDrive,
  LucideShieldCheck,
  LucideArrowRight,
  LucideFolder,
  LucideHistory,
  LucidePlus,
} from '@lucide/angular';
import { GroupResponseDto, OrganizationMemberResponseDto } from '../../../../../core/models/organization.model';
import { TeamAccessBoundaryDto } from '../../../models/team-delegation.models';

export interface TeamActivityItem {
  id: string;
  timestamp: string;
  actorName: string;
  actorEmail?: string;
  description: string;
  iconType: 'TEAM' | 'MEMBER' | 'WORKSPACE' | 'ACCESS';
  badgeText?: string;
}

@Component({
  selector: 'sd-team-overview-tab',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    LucideUsers,
    LucideShield,
    LucideHardDrive,
    LucideShieldCheck,
    LucideArrowRight,
    LucideFolder,
    LucideHistory,
    LucidePlus,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="overview-tab-container">
      <!-- 1. Team Identity & Description Card -->
      <section class="overview-card identity-card">
        <div class="identity-header">
          <div class="identity-avatar" [class.is-archived]="isArchived()">
            {{ teamInitials() }}
          </div>
          <div class="identity-titles">
            <div class="title-row">
              <h3 class="team-name">{{ team().name }}</h3>
              <span
                class="status-pill"
                [class.pill-active]="!isArchived()"
                [class.pill-archived]="isArchived()"
              >
                <span class="dot"></span>
                {{ isArchived() ? 'Archived' : 'Active' }}
              </span>
            </div>
            <p class="team-desc">
              {{ team().description || 'No description provided for this team.' }}
            </p>
          </div>
        </div>

        <div class="identity-footer-meta">
          <span class="meta-item font-tabular">
            Created: {{ (team().createdAt | date: 'MMM d, yyyy') || '—' }}
          </span>
          <span class="meta-sep">·</span>
          <span class="meta-item">
            Relationship: <strong>{{ myRole() }}</strong>
          </span>
        </div>
      </section>

      <!-- 2. Governance KPI Cards Grid -->
      <section class="kpi-grid">
        <!-- KPI 1: Manager -->
        <div class="kpi-card">
          <div class="kpi-header">
            <div class="kpi-icon-wrap is-manager">
              <svg lucideShield class="kpi-icon"></svg>
            </div>
            <span class="kpi-label">Team Manager</span>
          </div>
          @if (manager(); as mgr) {
            <div class="kpi-value-block">
              <span class="manager-name" [title]="mgr.email">{{ getDisplayName(mgr) }}</span>
              <span class="manager-email">{{ mgr.email }}</span>
            </div>
          } @else {
            <div class="kpi-value-block">
              <span class="no-manager-text">No manager assigned</span>
            </div>
          }
        </div>

        <!-- KPI 2: Members Count -->
        <div class="kpi-card clickable" (click)="tabChange.emit('MEMBERS')">
          <div class="kpi-header">
            <div class="kpi-icon-wrap is-members">
              <svg lucideUsers class="kpi-icon"></svg>
            </div>
            <span class="kpi-label">Team Members</span>
          </div>
          <div class="kpi-value-row">
            <span class="kpi-number">{{ memberCount() }}</span>
            <span class="kpi-subtext">active members</span>
          </div>
          <button type="button" class="kpi-jump-link">
            <span>View roster</span>
            <svg lucideArrowRight class="jump-arrow"></svg>
          </button>
        </div>

        <!-- KPI 3: Workspace Access -->
        <div class="kpi-card clickable" (click)="tabChange.emit('WORKSPACE')">
          <div class="kpi-header">
            <div class="kpi-icon-wrap is-workspace">
              <svg lucideHardDrive class="kpi-icon"></svg>
            </div>
            <span class="kpi-label">Workspace Access</span>
          </div>
          @if (boundary(); as b) {
            <div class="kpi-value-block">
              <span class="workspace-allocated-name" [title]="b.workspaceName + ' / ' + b.rootFolderName">
                {{ b.workspaceName }}
              </span>
              <span class="workspace-perm-ceiling">Maximum: {{ b.permissionLevel }}</span>
            </div>
          } @else {
            <div class="kpi-value-block">
              <span class="unallocated-badge">Not Allocated</span>
            </div>
          }
          <button type="button" class="kpi-jump-link">
            <span>Manage workspace</span>
            <svg lucideArrowRight class="jump-arrow"></svg>
          </button>
        </div>

        <!-- KPI 4: Member Access Delegations -->
        <div class="kpi-card clickable" (click)="tabChange.emit('MEMBER_ACCESS')">
          <div class="kpi-header">
            <div class="kpi-icon-wrap is-access">
              <svg lucideShieldCheck class="kpi-icon"></svg>
            </div>
            <span class="kpi-label">Member Access</span>
          </div>
          <div class="kpi-value-row">
            <span class="kpi-number">{{ grantsCount() }}</span>
            <span class="kpi-subtext">granted delegations</span>
          </div>
          <button type="button" class="kpi-jump-link">
            <span>View ledger</span>
            <svg lucideArrowRight class="jump-arrow"></svg>
          </button>
        </div>
      </section>

      <!-- 3. Workspace Access Snapshot Card -->
      <section class="overview-card workspace-snapshot-card">
        <div class="card-section-header">
          <div class="header-left">
            <svg lucideHardDrive class="section-lucide"></svg>
            <h4>Workspace Access Snapshot</h4>
          </div>
          <button
            type="button"
            class="btn-section-action"
            (click)="tabChange.emit('WORKSPACE')"
          >
            <span>Details</span>
            <svg lucideArrowRight class="btn-arrow"></svg>
          </button>
        </div>

        @if (boundary(); as b) {
          <div class="snapshot-content-box">
            <div class="snapshot-resource-row">
              <div class="resource-icon-box">
                <svg lucideFolder class="folder-lucide"></svg>
              </div>
              <div class="resource-meta">
                <span class="resource-title">{{ b.workspaceName }} / {{ b.rootFolderName }}</span>
                <span class="resource-path font-mono">{{ b.rootFolderPath || '/' + b.rootFolderName }}</span>
              </div>
              <div class="resource-perm-badge">
                <span class="perm-label">Maximum Permission</span>
                <span class="perm-value">{{ b.permissionLevel }}</span>
              </div>
            </div>

            <p class="zero-access-reminder">
              <strong>Zero-Default Content Access:</strong> Workspace access grants the team an operational boundary. Team members receive zero default content access until explicitly allocated in the <strong>Member Access</strong> ledger.
            </p>
          </div>
        } @else {
          <div class="snapshot-empty-box">
            <p class="empty-text">No workspace has been allocated to this team.</p>
            @if (canManageOrg()) {
              <button
                type="button"
                class="btn-allocate-sm"
                (click)="allocateWorkspace.emit()"
              >
                <svg lucidePlus class="btn-plus-icon"></svg>
                <span>Allocate Workspace Access</span>
              </button>
            }
          </div>
        }
      </section>

      <!-- 4. Recent Activity Preview -->
      <section class="overview-card activity-snapshot-card">
        <div class="card-section-header">
          <div class="header-left">
            <svg lucideHistory class="section-lucide"></svg>
            <h4>Recent Activity</h4>
          </div>
          <button
            type="button"
            class="btn-section-action"
            (click)="tabChange.emit('ACTIVITY')"
          >
            <span>All Activity</span>
            <svg lucideArrowRight class="btn-arrow"></svg>
          </button>
        </div>

        @if (recentActivity().length === 0) {
          <div class="activity-empty-box">
            <p>No recent activity recorded for this team.</p>
          </div>
        } @else {
          <div class="recent-events-list">
            @for (event of recentActivity(); track event.id) {
              <div class="event-mini-row">
                <span class="event-dot"></span>
                <div class="event-body">
                  <span class="event-desc">{{ event.description }}</span>
                  <span class="event-time font-tabular">
                    {{ (event.timestamp | date: 'MMM d, yyyy · h:mm a') || '—' }}
                  </span>
                </div>
              </div>
            }
          </div>
        }
      </section>
    </div>
  `,
  styles: [
    `
      .overview-tab-container {
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      /* ── Identity Card ─────────────────────────── */
      .overview-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        padding: 1.25rem 1.5rem;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
      }

      .identity-header {
        display: flex;
        align-items: flex-start;
        gap: 1rem;
      }

      .identity-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        border-radius: 10px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        font-size: 1rem;
        font-weight: 700;
        flex-shrink: 0;

        &.is-archived {
          background: #f4f4f5;
          color: #71717a;
          border-color: #e4e4e7;
        }
      }

      .identity-titles {
        flex: 1;
        min-width: 0;
      }

      .title-row {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        margin-bottom: 0.35rem;
      }

      .team-name {
        font-size: 1.125rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0;
      }

      .status-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.15rem 0.45rem;
        border-radius: 9999px;

        &.pill-active {
          background: #f0fdf4;
          color: #15803d;
          border: 1px solid #bbf7d0;

          .dot {
            background-color: #16a34a;
          }
        }

        &.pill-archived {
          background: #f4f4f5;
          color: #71717a;
          border: 1px solid #e4e4e7;

          .dot {
            background-color: #a1a1aa;
          }
        }

        .dot {
          width: 5px;
          height: 5px;
          border-radius: 50%;
        }
      }

      .team-desc {
        font-size: 0.875rem;
        color: #475569;
        margin: 0;
        line-height: 1.4;
      }

      .identity-footer-meta {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-top: 1rem;
        padding-top: 0.75rem;
        border-top: 1px solid #f1f5f9;
        font-size: 0.75rem;
        color: #64748b;
      }

      .meta-sep {
        color: #cbd5e1;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }

      /* ── Governance KPI Cards Grid ──────────────── */
      .kpi-grid {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
        gap: 1rem;
      }

      .kpi-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        padding: 1rem 1.125rem;
        display: flex;
        flex-direction: column;
        justify-content: space-between;
        min-height: 110px;
        transition: all 0.15s ease;

        &.clickable {
          cursor: pointer;

          &:hover {
            border-color: #fdba74;
            box-shadow: 0 3px 10px rgba(249, 115, 22, 0.08);
            transform: translateY(-1px);

            .jump-arrow {
              transform: translateX(2px);
              color: #ea580c;
            }
          }
        }
      }

      .kpi-header {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-bottom: 0.5rem;
      }

      .kpi-icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 26px;
        height: 26px;
        border-radius: 6px;
        flex-shrink: 0;

        &.is-manager {
          background: #fff7ed;
          color: #ea580c;
        }

        &.is-members {
          background: #eff6ff;
          color: #2563eb;
        }

        &.is-workspace {
          background: #f0fdf4;
          color: #16a34a;
        }

        &.is-access {
          background: #faf5ff;
          color: #9333ea;
        }
      }

      .kpi-icon {
        width: 14px;
        height: 14px;
      }

      .kpi-label {
        font-size: 0.75rem;
        font-weight: 600;
        color: #64748b;
        text-transform: uppercase;
        letter-spacing: 0.03em;
      }

      .kpi-value-block {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }

      .manager-name {
        font-size: 0.9375rem;
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .manager-email {
        font-size: 0.75rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .no-manager-text {
        font-size: 0.8125rem;
        color: #94a3b8;
        font-style: italic;
      }

      .kpi-value-row {
        display: flex;
        align-items: baseline;
        gap: 0.4rem;
      }

      .kpi-number {
        font-size: 1.5rem;
        font-weight: 700;
        color: #0f172a;
        line-height: 1;
      }

      .kpi-subtext {
        font-size: 0.75rem;
        color: #64748b;
      }

      .workspace-allocated-name {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .workspace-perm-ceiling {
        font-size: 0.75rem;
        color: #15803d;
        font-weight: 600;
      }

      .unallocated-badge {
        font-size: 0.75rem;
        color: #b45309;
        font-weight: 500;
      }

      .kpi-jump-link {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        background: transparent;
        border: none;
        padding: 0;
        margin-top: 0.5rem;
        font-size: 0.75rem;
        font-weight: 500;
        color: #64748b;
        cursor: pointer;
      }

      .jump-arrow {
        width: 12px;
        height: 12px;
        transition: transform 0.15s ease;
      }

      /* ── Card Header Common ────────────────────── */
      .card-section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.875rem;
      }

      .header-left {
        display: flex;
        align-items: center;
        gap: 0.5rem;

        h4 {
          margin: 0;
          font-size: 0.875rem;
          font-weight: 600;
          color: #0f172a;
        }
      }

      .section-lucide {
        width: 16px;
        height: 16px;
        color: #64748b;
      }

      .btn-section-action {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        background: transparent;
        border: none;
        color: #ea580c;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        padding: 0.2rem 0.4rem;
        border-radius: 4px;

        &:hover {
          background: #fff7ed;
        }
      }

      .btn-arrow {
        width: 12px;
        height: 12px;
      }

      /* ── Workspace Snapshot ────────────────────── */
      .snapshot-content-box {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }

      .snapshot-resource-row {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
      }

      .resource-icon-box {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 32px;
        height: 32px;
        border-radius: 6px;
        background: #e0f2fe;
        color: #0284c7;
        flex-shrink: 0;
      }

      .folder-lucide {
        width: 16px;
        height: 16px;
      }

      .resource-meta {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }

      .resource-title {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
      }

      .resource-path {
        font-size: 0.75rem;
        color: #64748b;
      }

      .resource-perm-badge {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.15rem;
      }

      .perm-label {
        font-size: 0.65rem;
        color: #64748b;
        text-transform: uppercase;
      }

      .perm-value {
        font-size: 0.75rem;
        font-weight: 700;
        color: #c2410c;
        background: #fff7ed;
        border: 1px solid #fed7aa;
        padding: 0.1rem 0.45rem;
        border-radius: 4px;
      }

      .zero-access-reminder {
        font-size: 0.8125rem;
        color: #475569;
        margin: 0;
        line-height: 1.4;
      }

      .snapshot-empty-box {
        padding: 1.25rem;
        background: #f8fafc;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        text-align: center;
      }

      .empty-text {
        font-size: 0.8125rem;
        color: #64748b;
        margin: 0 0 0.75rem 0;
      }

      .btn-allocate-sm {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.4rem 0.8rem;
        background: #f97316;
        color: #ffffff;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;

        &:hover {
          background: #ea580c;
        }
      }

      .btn-plus-icon {
        width: 14px;
        height: 14px;
      }

      /* ── Recent Activity Snapshot ──────────────── */
      .recent-events-list {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }

      .event-mini-row {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
      }

      .event-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background-color: #f97316;
        margin-top: 0.35rem;
        flex-shrink: 0;
      }

      .event-body {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
      }

      .event-desc {
        font-size: 0.8125rem;
        color: #1e293b;
      }

      .event-time {
        font-size: 0.6875rem;
        color: #94a3b8;
      }

      .activity-empty-box {
        padding: 1rem;
        text-align: center;
        color: #94a3b8;
        font-size: 0.8125rem;
      }
    `,
  ],
})
export class TeamOverviewTabComponent {
  team = input.required<GroupResponseDto>();
  manager = input<OrganizationMemberResponseDto | null>(null);
  memberCount = input<number>(0);
  boundary = input<TeamAccessBoundaryDto | null>(null);
  grantsCount = input<number>(0);
  myRole = input<'Team Manager' | 'Member' | '—'>('—');
  canManageOrg = input<boolean>(false);
  recentActivity = input<TeamActivityItem[]>([]);

  tabChange = output<'OVERVIEW' | 'MEMBERS' | 'WORKSPACE' | 'MEMBER_ACCESS' | 'ACTIVITY'>();
  allocateWorkspace = output<void>();
  addMember = output<void>();
  giveAccess = output<void>();

  teamInitials(): string {
    const name = this.team()?.name || '';
    if (!name) return 'TM';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  }

  isArchived(): boolean {
    const s = (this.team().status || '').toUpperCase();
    return s === 'ARCHIVED' || s === 'INACTIVE';
  }

  getDisplayName(member: OrganizationMemberResponseDto): string {
    if (member.firstName || member.lastName) {
      return `${member.firstName || ''} ${member.lastName || ''}`.trim();
    }
    return member.email;
  }
}
