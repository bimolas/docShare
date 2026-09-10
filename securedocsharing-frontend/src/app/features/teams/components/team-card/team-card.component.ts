import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  HostListener,
  ElementRef,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  LucideChevronRight,
  LucideMoreVertical,
  LucidePencil,
  LucideArchive,
  LucideArchiveRestore,
  LucideTrash2,
  LucideFolder,
  LucideAlertCircle,
} from '@lucide/angular';
import { GroupResponseDto, OrganizationMemberResponseDto } from '../../../../core/models/organization.model';
import { TeamAccessBoundaryDto } from '../../models/team-delegation.models';

/**
 * Enterprise Team Governance Table Row Component.
 * Displays Team Identity, designated Team Manager, allocated Access Boundary with Ceiling,
 * member capacity and active delegations, creation timestamp, and governance actions.
 */
@Component({
  selector: 'sd-team-card',
  standalone: true,
  imports: [
    CommonModule,
    LucideChevronRight,
    LucideMoreVertical,
    LucidePencil,
    LucideArchive,
    LucideArchiveRestore,
    LucideTrash2,
    LucideFolder,
    LucideAlertCircle,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="team-row"
      role="row"
      tabindex="0"
      [attr.aria-label]="(canManageTeam() ? 'Manage team ' : 'View team ') + group().name"
      (click)="onCardClick()"
      (keydown.enter)="onCardClick()"
      (keydown.space)="$event.preventDefault(); onCardClick()"
    >
      <!-- 1. Team Identity -->
      <div class="col-team" role="cell">
        <div class="team-avatar" [class.avatar-archived]="isArchived()">
          <span class="avatar-initials">{{ teamInitials() }}</span>
        </div>
        <div class="team-info-block">
          <div class="team-name-line">
            <span class="team-name" [title]="group().name">{{ group().name }}</span>
            @if (isArchived()) {
              <span class="status-dot-badge badge-archived">
                <span class="dot dot-archived"></span>
                Archived
              </span>
            } @else {
              <span class="status-dot-badge badge-active">
                <span class="dot dot-active"></span>
                Active
              </span>
            }
          </div>
          <p class="team-description" [title]="group().description || 'No description provided'">
            {{ group().description || 'No description provided' }}
          </p>
        </div>
      </div>

      <!-- 2. My Role -->
      <div class="col-my-role" role="cell">
        @if (myRole() === 'Team Manager') {
          <span class="role-pill pill-manager">Team Manager</span>
        } @else if (myRole() === 'Member') {
          <span class="role-pill pill-member">Member</span>
        } @else {
          <span class="role-empty">—</span>
        }
      </div>

      <!-- 3. Team Manager -->
      <div class="col-manager" role="cell">
        @if (manager(); as mgr) {
          <div class="manager-card" [title]="mgr.email">
            <div class="manager-avatar">
              {{ getInitials(mgr.firstName, mgr.lastName, mgr.email) }}
            </div>
            <div class="manager-info">
              <span class="manager-name">{{ getDisplayName(mgr) }}</span>
              <span class="manager-email-sub">{{ mgr.email }}</span>
            </div>
          </div>
        } @else {
          <div class="no-manager-wrap">
            <span class="no-manager-text">No manager assigned</span>
          </div>
        }
      </div>

      <!-- 4. Workspace Access -->
      <div class="col-workspace" role="cell">
        @if (boundary(); as b) {
          <div class="workspace-pill-wrap" [title]="'Workspace: ' + b.workspaceName + ' / ' + b.rootFolderName">
            <div class="workspace-header-line">
              <svg lucideFolder class="workspace-icon"></svg>
              <span class="workspace-name">{{ b.workspaceName }}</span>
              <span class="workspace-sep">/</span>
              <span class="workspace-folder">{{ b.rootFolderName }}</span>
            </div>
            <div class="workspace-meta-line">
              <span
                class="perm-badge"
                [class.is-edit]="b.permissionLevel === 'EDIT'"
                [class.is-download]="b.permissionLevel === 'DOWNLOAD'"
                [class.is-view]="b.permissionLevel === 'VIEW'"
              >
                <span>Maximum: {{ b.permissionLevel }}</span>
              </span>
              @if (b.status === 'SUSPENDED') {
                <span class="workspace-status-tag suspended">Suspended</span>
              }
            </div>
          </div>
        } @else {
          <div class="unallocated-workspace">
            <svg lucideAlertCircle class="unallocated-icon"></svg>
            <span class="unallocated-text">No workspace allocated</span>
          </div>
        }
      </div>

      <!-- 5. Members -->
      <div class="col-members" role="cell">
        <span class="members-count">{{ memberCount() }} {{ memberCount() === 1 ? 'member' : 'members' }}</span>
      </div>

      <!-- 6. Actions Column -->
      <div class="col-actions" (click)="$event.stopPropagation()" role="cell">
        <button
          type="button"
          class="btn-manage-row"
          (click)="onCardClick()"
          [title]="canManageTeam() ? 'Manage team' : 'View team'"
        >
          <span>{{ canManageTeam() ? 'Manage Team' : 'View Team' }}</span>
          <svg lucideChevronRight class="btn-chevron"></svg>
        </button>

        @if (canManage()) {
          <div class="menu-wrapper">
            <button
              type="button"
              class="btn-menu-trigger"
              [class.active]="isMenuOpen()"
              (click)="toggleMenu($event)"
              title="Team Options"
              aria-label="Team Options"
            >
              <svg lucideMoreVertical class="action-icon"></svg>
            </button>

            @if (isMenuOpen()) {
              <div class="dropdown-menu" role="menu">
                <button
                  type="button"
                  class="dropdown-item"
                  role="menuitem"
                  (click)="onEditClicked($event)"
                >
                  <svg lucidePencil class="menu-icon"></svg>
                  <span>Edit Team</span>
                </button>

                @if (isArchived()) {
                  <button
                    type="button"
                    class="dropdown-item"
                    role="menuitem"
                    (click)="onUnarchiveClicked($event)"
                  >
                    <svg lucideArchiveRestore class="menu-icon"></svg>
                    <span>Reactivate Team</span>
                  </button>
                } @else {
                  <button
                    type="button"
                    class="dropdown-item"
                    role="menuitem"
                    (click)="onArchiveClicked($event)"
                  >
                    <svg lucideArchive class="menu-icon"></svg>
                    <span>Archive Team</span>
                  </button>
                }

                <div class="dropdown-divider"></div>

                <button
                  type="button"
                  class="dropdown-item item-danger"
                  role="menuitem"
                  (click)="onDeleteClicked($event)"
                >
                  <svg lucideTrash2 class="menu-icon"></svg>
                  <span>Delete Team</span>
                </button>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        border-bottom: 1px solid #f1f5f9;
        min-width: 0;

        &:last-child {
          border-bottom: none;
        }
      }

      .team-row {
        display: grid;
        grid-template-columns: minmax(200px, 1.3fr) 110px minmax(160px, 1fr) minmax(200px, 1.2fr) 90px 140px;
        align-items: center;
        gap: 1.25rem;
        padding: 0.875rem 1.5rem;
        cursor: pointer;
        transition: background-color 0.15s cubic-bezier(0.16, 1, 0.3, 1);
        text-decoration: none;
        min-height: 68px;
        background-color: #ffffff;

        &:hover {
          background-color: #f8fafc;

          .btn-manage-row {
            background-color: #fff7ed;
            border-color: #fed7aa;
            color: #ea580c;
          }
        }

        &:focus-visible {
          outline: 2px solid #f97316;
          outline-offset: -2px;
          background-color: #f8fafc;
        }
      }

      /* ── 1. Team Identity ──────────────────────── */
      .col-team {
        display: flex;
        align-items: center;
        gap: 0.875rem;
        min-width: 0;
      }

      .team-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 38px;
        height: 38px;
        border-radius: 10px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        flex-shrink: 0;

        &.avatar-archived {
          background: #f4f4f5;
          color: #71717a;
          border-color: #e4e4e7;
        }
      }

      .avatar-initials {
        font-size: 0.8125rem;
        font-weight: 700;
        letter-spacing: -0.01em;
      }

      .team-info-block {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        min-width: 0;
      }

      .team-name-line {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0;
      }

      .team-name {
        font-size: 0.9375rem;
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        line-height: 1.25;
      }

      .team-description {
        font-size: 0.8125rem;
        color: #64748b;
        margin: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        line-height: 1.2;
      }

      /* ── Status Dot Badges ─────────────────────── */
      .status-dot-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.15rem 0.45rem;
        border-radius: 9999px;
        flex-shrink: 0;

        &.badge-active {
          background: #f0fdf4;
          color: #15803d;
          border: 1px solid #bbf7d0;
        }

        &.badge-archived {
          background: #f4f4f5;
          color: #71717a;
          border: 1px solid #e4e4e7;
        }
      }

      .dot {
        width: 5px;
        height: 5px;
        border-radius: 50%;

        &.dot-active {
          background-color: #16a34a;
        }

        &.dot-archived {
          background-color: #a1a1aa;
        }
      }

      /* ── 2. My Role ────────────────────────────── */
      .col-my-role {
        display: flex;
        align-items: center;
        min-width: 0;
      }

      .role-pill {
        display: inline-flex;
        align-items: center;
        font-size: 0.75rem;
        font-weight: 600;
        padding: 0.15rem 0.55rem;
        border-radius: 9999px;
        white-space: nowrap;

        &.pill-manager {
          background: #fff7ed;
          color: #c2410c;
          border: 1px solid #fed7aa;
        }

        &.pill-member {
          background: #f1f5f9;
          color: #475569;
          border: 1px solid #cbd5e1;
        }
      }

      .role-empty {
        color: #94a3b8;
        font-size: 0.875rem;
        font-weight: 500;
      }

      /* ── 3. Team Manager ───────────────────────── */
      .col-manager {
        display: flex;
        align-items: center;
        min-width: 0;
      }

      .manager-card {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        min-width: 0;
      }

      .manager-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #cbd5e1;
        font-size: 0.6875rem;
        font-weight: 700;
        flex-shrink: 0;
      }

      .manager-info {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .manager-name {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #1e293b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .manager-email-sub {
        font-size: 0.6875rem;
        color: #94a3b8;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .no-manager-text {
        font-size: 0.8125rem;
        color: #94a3b8;
        font-style: italic;
      }

      /* ── 4. Workspace Access ───────────────────── */
      .col-workspace {
        display: flex;
        align-items: center;
        min-width: 0;
      }

      .workspace-pill-wrap {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        min-width: 0;
      }

      .workspace-header-line {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.8125rem;
        color: #1e293b;
        font-weight: 500;
        min-width: 0;
      }

      .workspace-icon {
        width: 14px;
        height: 14px;
        color: #64748b;
        flex-shrink: 0;
      }

      .workspace-name {
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .workspace-sep {
        color: #cbd5e1;
      }

      .workspace-folder {
        color: #475569;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .workspace-meta-line {
        display: flex;
        align-items: center;
        gap: 0.4rem;
      }

      .perm-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.1rem 0.4rem;
        border-radius: 4px;
        background: #f8fafc;
        color: #475569;
        border: 1px solid #cbd5e1;

        &.is-edit {
          background: #fff7ed;
          color: #c2410c;
          border-color: #fed7aa;
        }

        &.is-download {
          background: #eff6ff;
          color: #1d4ed8;
          border-color: #bfdbfe;
        }

        &.is-view {
          background: #f8fafc;
          color: #475569;
          border-color: #e2e8f0;
        }
      }

      .workspace-status-tag {
        font-size: 0.65rem;
        font-weight: 600;
        padding: 0.1rem 0.35rem;
        border-radius: 4px;

        &.suspended {
          background: #fef2f2;
          color: #b91c1c;
          border: 1px solid #fecaca;
        }
      }

      .unallocated-workspace {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.25rem 0.6rem;
        border-radius: 6px;
        background: #fffbeb;
        color: #b45309;
        border: 1px solid #fde68a;
        font-size: 0.75rem;
        font-weight: 500;
      }

      .unallocated-icon {
        width: 13px;
        height: 13px;
        flex-shrink: 0;
      }

      /* ── 5. Members ────────────────────────────── */
      .col-members {
        display: flex;
        align-items: center;
        min-width: 0;
      }

      .members-count {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #1e293b;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }

      /* ── 6. Actions ────────────────────────────── */
      .col-actions {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.5rem;
      }

      .btn-manage-row {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.35rem 0.65rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 500;
        color: #1e293b;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover {
          background: #fff7ed;
          border-color: #fed7aa;
          color: #ea580c;
        }
      }

      .btn-chevron {
        width: 12px;
        height: 12px;
      }

      .menu-wrapper {
        position: relative;
      }

      .btn-menu-trigger {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border: 1px solid transparent;
        background: transparent;
        border-radius: 6px;
        color: #64748b;
        cursor: pointer;
        transition: all 0.12s ease;

        &:hover,
        &.active {
          background-color: #f1f5f9;
          border-color: #e2e8f0;
          color: #0f172a;
        }
      }

      .action-icon {
        width: 15px;
        height: 15px;
      }

      /* ── Dropdown Menu ─────────────────────────── */
      .dropdown-menu {
        position: absolute;
        top: calc(100% + 4px);
        right: 0;
        z-index: 50;
        min-width: 160px;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08), 0 1px 3px rgba(0, 0, 0, 0.04);
        padding: 0.25rem;
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
        animation: dropIn 0.12s cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes dropIn {
        from {
          opacity: 0;
          transform: translateY(-4px);
        }
        to {
          opacity: 1;
          transform: translateY(0);
        }
      }

      .dropdown-item {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        width: 100%;
        padding: 0.45rem 0.65rem;
        font-size: 0.8125rem;
        font-weight: 500;
        color: #334155;
        background: transparent;
        border: none;
        border-radius: 5px;
        cursor: pointer;
        text-align: left;
        transition: all 0.12s ease;

        &:hover {
          background: #f8fafc;
          color: #0f172a;
        }

        &.item-danger {
          color: #dc2626;

          &:hover {
            background: #fef2f2;
            color: #b91c1c;
          }
        }
      }

      .menu-icon {
        width: 14px;
        height: 14px;
        flex-shrink: 0;
      }

      .dropdown-divider {
        height: 1px;
        background: #f1f5f9;
        margin: 0.2rem 0;
      }

      /* ── Mobile Responsive Card Mode ──────────── */
      @media (max-width: 860px) {
        .team-row {
          display: flex;
          flex-direction: column;
          align-items: stretch;
          gap: 0.75rem;
          padding: 1rem 1.25rem;
          min-width: 0;
          border: 1px solid #e2e8f0;
          border-radius: 10px;
          margin: 0.5rem 0;
        }

        .col-team {
          width: 100%;
        }

        .col-my-role {
          width: 100%;
        }

        .col-manager {
          width: 100%;
        }

        .col-workspace {
          width: 100%;
        }

        .col-members {
          width: 100%;
        }

        .col-actions {
          width: 100%;
          justify-content: space-between;
          padding-top: 0.5rem;
          border-top: 1px solid #f1f5f9;
        }
      }
    `,
  ],
})
export class TeamCardComponent {
  private readonly elementRef = inject(ElementRef);

  group = input.required<GroupResponseDto>();
  boundary = input<TeamAccessBoundaryDto | null>(null);
  manager = input<OrganizationMemberResponseDto | null>(null);
  grantsCount = input<number>(0);
  canManage = input<boolean>(false);
  myRole = input<'Team Manager' | 'Member' | '—'>('—');

  readonly canManageTeam = computed(() => this.myRole() === 'Team Manager' || this.canManage());

  selectTeam = output<GroupResponseDto>();
  editTeam = output<GroupResponseDto>();
  archiveTeam = output<GroupResponseDto>();
  unarchiveTeam = output<GroupResponseDto>();
  deleteTeam = output<GroupResponseDto>();

  readonly isMenuOpen = signal<boolean>(false);

  teamInitials(): string {
    const name = this.group()?.name || '';
    if (!name) return 'TM';
    const parts = name.trim().split(/\s+/);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  }

  isArchived(): boolean {
    const s = (this.group().status || '').toUpperCase();
    return s === 'ARCHIVED' || s === 'INACTIVE';
  }

  memberCount(): number {
    return this.group().memberCount ?? 0;
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
    if (firstName) {
      return firstName.slice(0, 2).toUpperCase();
    }
    if (email) {
      return email.slice(0, 2).toUpperCase();
    }
    return 'U';
  }

  onCardClick(): void {
    if (this.isMenuOpen()) {
      this.isMenuOpen.set(false);
      return;
    }
    this.selectTeam.emit(this.group());
  }

  toggleMenu(event: Event): void {
    event.stopPropagation();
    this.isMenuOpen.update((v) => !v);
  }

  onEditClicked(event: Event): void {
    event.stopPropagation();
    this.isMenuOpen.set(false);
    this.editTeam.emit(this.group());
  }

  onArchiveClicked(event: Event): void {
    event.stopPropagation();
    this.isMenuOpen.set(false);
    this.archiveTeam.emit(this.group());
  }

  onUnarchiveClicked(event: Event): void {
    event.stopPropagation();
    this.isMenuOpen.set(false);
    this.unarchiveTeam.emit(this.group());
  }

  onDeleteClicked(event: Event): void {
    event.stopPropagation();
    this.isMenuOpen.set(false);
    this.deleteTeam.emit(this.group());
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.elementRef.nativeElement.contains(event.target)) {
      this.isMenuOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.isMenuOpen.set(false);
  }
}
