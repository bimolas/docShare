import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  LucideUsers,
  LucideSearch,
  LucideX,
  LucideUserPlus,
  LucideShield,
  LucideTrash2,
  LucidePlus,
  LucideRefreshCw,
  LucideKey,
} from '@lucide/angular';
import { OrganizationMemberResponseDto } from '../../../../../core/models/organization.model';

@Component({
  selector: 'sd-team-members-tab',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    LucideUsers,
    LucideSearch,
    LucideX,
    LucideUserPlus,
    LucideShield,
    LucideTrash2,
    LucidePlus,
    LucideRefreshCw,
    LucideKey,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="members-tab-container">
      <!-- Section Header -->
      <div class="section-top-bar">
        <div class="section-title-group">
          <svg lucideUsers class="top-icon"></svg>
          <h4>Team Members</h4>
          <span class="count-pill">{{ filteredMembers().length }}</span>
        </div>

        @if (canManageMembers() && isTeamActive()) {
          <button
            type="button"
            class="btn-add-member"
            (click)="addMember.emit()"
            title="Add members to this team"
          >
            <svg lucideUserPlus class="btn-icon"></svg>
            <span>Add Member</span>
          </button>
        }
      </div>

      <!-- Governing Team Manager Banner -->
      <div class="manager-banner-card">
        <div class="banner-left">
          <div class="shield-badge">
            <svg lucideShield class="shield-icon"></svg>
          </div>
          <div class="manager-meta">
            <span class="banner-label">Governing Team Manager</span>
            @if (manager(); as mgr) {
              <div class="manager-info-row">
                <strong class="manager-display-name">{{ getDisplayName(mgr) }}</strong>
                <span class="manager-email-tag">({{ mgr.email }})</span>
              </div>
            } @else {
              <span class="no-manager-alert">No manager assigned — delegation authority is inactive</span>
            }
          </div>
        </div>

        @if (canTransferManager() && isTeamActive()) {
          <button
            type="button"
            class="btn-change-manager"
            (click)="changeManager.emit()"
            title="Change designated Team Manager"
          >
            <svg lucideRefreshCw class="btn-icon-xs"></svg>
            <span>Change Team Manager</span>
          </button>
        }
      </div>

      <!-- Member Search Bar -->
      @if (members().length > 0) {
        <div class="search-input-wrap">
          <svg lucideSearch class="search-icon"></svg>
          <input
            type="text"
            class="search-field"
            placeholder="Search members by name or email..."
            [ngModel]="searchQuery()"
            (ngModelChange)="searchQuery.set($event)"
          />
          @if (searchQuery()) {
            <button
              type="button"
              class="btn-clear-search"
              (click)="searchQuery.set('')"
              aria-label="Clear member search"
            >
              <svg lucideX class="clear-icon"></svg>
            </button>
          }
        </div>
      }

      <!-- Members Roster Cards -->
      <div class="roster-container">
        @if (members().length === 0) {
          <div class="empty-roster-box">
            <div class="empty-icon-wrap">
              <svg lucideUsers class="empty-icon"></svg>
            </div>
            <h5>No Team Members</h5>
            <p>This team currently has no members assigned.</p>
            @if (canManageMembers() && isTeamActive()) {
              <button
                type="button"
                class="btn-add-member-empty"
                (click)="addMember.emit()"
              >
                <svg lucideUserPlus class="btn-icon"></svg>
                <span>Add First Member</span>
              </button>
            }
          </div>
        } @else if (filteredMembers().length === 0) {
          <div class="empty-search-box">
            <svg lucideSearch class="empty-icon-sm"></svg>
            <p>No team members match "{{ searchQuery() }}".</p>
          </div>
        } @else {
          <div class="member-cards-list">
            @for (m of filteredMembers(); track m.userId) {
              <div class="member-card-row" [class.is-manager]="m.groupRole === 'MANAGER'">
                <!-- Identity -->
                <div class="member-identity-col">
                  <div class="member-avatar">
                    {{ getInitials(m.firstName, m.lastName, m.email) }}
                  </div>
                  <div class="member-identity-texts">
                    <div class="member-name-line">
                      <span class="member-name">{{ getDisplayName(m) }}</span>
                      @if (m.groupRole === 'MANAGER') {
                        <span class="manager-tag">
                          <svg lucideShield class="tag-shield"></svg>
                          Team Manager
                        </span>
                      }
                    </div>
                    <span class="member-email">{{ m.email }}</span>
                  </div>
                </div>

                <!-- Assigned Resources Pill -->
                <div class="member-grants-col">
                  @let count = getGrantsCount(m.email);
                  <button
                    type="button"
                    class="grants-pill-btn"
                    [class.has-grants]="count > 0"
                    (click)="viewMemberGrants.emit(m.email)"
                    title="View delegated access for this member"
                  >
                    <svg lucideKey class="pill-key-icon"></svg>
                    <span>{{ count }} {{ count === 1 ? 'access grant' : 'access grants' }}</span>
                  </button>
                </div>

                <!-- Joined Date -->
                <div class="member-joined-col font-tabular">
                  <span class="joined-label">Joined</span>
                  <span class="joined-date">
                    {{ (m.joinedAt | date: 'MMM d, yyyy') || '—' }}
                  </span>
                </div>

                <!-- Actions -->
                @if (canManageMembers() && isTeamActive()) {
                  <div class="member-actions-col">
                    <!-- Quick Give Access -->
                    <button
                      type="button"
                      class="btn-give-access"
                      (click)="giveAccess.emit(m)"
                      title="Give Member Access"
                    >
                      <svg lucidePlus class="btn-icon-xs"></svg>
                      <span>Give Access</span>
                    </button>

                    <!-- Remove Confirmation Popover (Non-Managers Only) -->
                    @if (m.groupRole !== 'MANAGER') {
                      @if (confirmRemoveUserId() === m.userId) {
                        <div class="confirm-remove-pop">
                          <span class="pop-msg">Remove?</span>
                          <button
                            type="button"
                            class="btn-pop-yes"
                            (click)="onConfirmRemove(m)"
                          >
                            Yes
                          </button>
                          <button
                            type="button"
                            class="btn-pop-no"
                            (click)="confirmRemoveUserId.set(null)"
                          >
                            No
                          </button>
                        </div>
                      } @else {
                        <button
                          type="button"
                          class="btn-remove-icon"
                          (click)="confirmRemoveUserId.set(m.userId)"
                          title="Remove from team"
                          aria-label="Remove from team"
                        >
                          <svg lucideTrash2 class="trash-icon"></svg>
                        </button>
                      }
                    }
                  </div>
                }
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .members-tab-container {
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      /* ── Top Bar ───────────────────────────────── */
      .section-top-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .section-title-group {
        display: flex;
        align-items: center;
        gap: 0.5rem;

        h4 {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #0f172a;
        }
      }

      .top-icon {
        width: 18px;
        height: 18px;
        color: #f97316;
      }

      .count-pill {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.6875rem;
        font-weight: 700;
        background: #f1f5f9;
        color: #475569;
        padding: 0.1rem 0.45rem;
        border-radius: 9999px;
      }

      .btn-add-member {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.45rem 0.85rem;
        background: #f97316;
        color: #ffffff;
        border: none;
        border-radius: 7px;
        font-size: 0.8125rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s ease;

        &:hover {
          background: #ea580c;
        }
      }

      .btn-icon {
        width: 15px;
        height: 15px;
      }

      /* ── Governing Team Manager Banner ─────────── */
      .manager-banner-card {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.875rem 1.25rem;
        background: #fff7ed;
        border: 1px solid #fed7aa;
        border-radius: 9px;
      }

      .banner-left {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        min-width: 0;
      }

      .shield-badge {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 34px;
        height: 34px;
        border-radius: 8px;
        background: #ffedd5;
        color: #c2410c;
        flex-shrink: 0;
      }

      .shield-icon {
        width: 18px;
        height: 18px;
      }

      .manager-meta {
        display: flex;
        flex-direction: column;
        gap: 0.15rem;
        min-width: 0;
      }

      .banner-label {
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.04em;
        color: #9a3412;
      }

      .manager-info-row {
        display: flex;
        align-items: baseline;
        gap: 0.4rem;
        min-width: 0;
      }

      .manager-display-name {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .manager-email-tag {
        font-size: 0.75rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .no-manager-alert {
        font-size: 0.8125rem;
        color: #b45309;
        font-style: italic;
      }

      .btn-change-manager {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.4rem 0.75rem;
        background: #ffffff;
        color: #c2410c;
        border: 1px solid #fed7aa;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        white-space: nowrap;
        transition: all 0.15s ease;

        &:hover {
          background: #fff7ed;
          border-color: #f97316;
        }
      }

      .btn-icon-xs {
        width: 13px;
        height: 13px;
      }

      /* ── Search Input ──────────────────────────── */
      .search-input-wrap {
        position: relative;
        display: flex;
        align-items: center;
      }

      .search-icon {
        position: absolute;
        left: 0.875rem;
        width: 15px;
        height: 15px;
        color: #94a3b8;
        pointer-events: none;
      }

      .search-field {
        width: 100%;
        padding: 0.55rem 2.25rem 0.55rem 2.5rem;
        font-size: 0.8125rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        color: #0f172a;
        outline: none;
        transition: border-color 0.15s ease, box-shadow 0.15s ease;

        &:focus {
          border-color: #f97316;
          box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.1);
        }

        &::placeholder {
          color: #94a3b8;
        }
      }

      .btn-clear-search {
        position: absolute;
        right: 0.75rem;
        background: transparent;
        border: none;
        color: #94a3b8;
        cursor: pointer;
        padding: 0.2rem;
        display: flex;
        align-items: center;

        &:hover {
          color: #475569;
        }
      }

      .clear-icon {
        width: 14px;
        height: 14px;
      }

      /* ── Member Cards List ─────────────────────── */
      .roster-container {
        display: flex;
        flex-direction: column;
      }

      .member-cards-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      .member-card-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.75rem 1rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        transition: border-color 0.15s ease;

        &:hover {
          border-color: #cbd5e1;
        }

        &.is-manager {
          border-left: 3px solid #f97316;
        }
      }

      .member-identity-col {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        flex: 1.5;
        min-width: 0;
      }

      .member-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 34px;
        height: 34px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #cbd5e1;
        font-size: 0.75rem;
        font-weight: 700;
        flex-shrink: 0;
      }

      .member-identity-texts {
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
        min-width: 0;
      }

      .member-name-line {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }

      .member-name {
        font-size: 0.875rem;
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .manager-tag {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.65rem;
        font-weight: 700;
        background: #fff7ed;
        color: #c2410c;
        border: 1px solid #fed7aa;
        padding: 0.05rem 0.4rem;
        border-radius: 4px;
      }

      .tag-shield {
        width: 10px;
        height: 10px;
      }

      .member-email {
        font-size: 0.75rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      /* Grants pill */
      .member-grants-col {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .grants-pill-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.25rem 0.65rem;
        border-radius: 9999px;
        background: #f8fafc;
        color: #64748b;
        border: 1px solid #e2e8f0;
        font-size: 0.75rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.15s ease;

        &.has-grants {
          background: #eff6ff;
          color: #1d4ed8;
          border-color: #bfdbfe;
          font-weight: 600;
        }

        &:hover {
          border-color: #3b82f6;
        }
      }

      .pill-key-icon {
        width: 12px;
        height: 12px;
      }

      /* Joined Col */
      .member-joined-col {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.1rem;
        font-size: 0.75rem;
        color: #64748b;
      }

      .joined-label {
        font-size: 0.65rem;
        color: #94a3b8;
        text-transform: uppercase;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }

      /* Actions Col */
      .member-actions-col {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        justify-content: flex-end;
      }

      .btn-give-access {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.3rem 0.6rem;
        background: #ffffff;
        color: #ea580c;
        border: 1px solid #fed7aa;
        border-radius: 5px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;

        &:hover {
          background: #fff7ed;
        }
      }

      .btn-remove-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border: 1px solid transparent;
        border-radius: 5px;
        background: transparent;
        color: #94a3b8;
        cursor: pointer;

        &:hover {
          background: #fef2f2;
          border-color: #fecaca;
          color: #dc2626;
        }
      }

      .trash-icon {
        width: 14px;
        height: 14px;
      }

      .confirm-remove-pop {
        display: flex;
        align-items: center;
        gap: 0.3rem;
        background: #fef2f2;
        border: 1px solid #fecaca;
        padding: 0.2rem 0.4rem;
        border-radius: 5px;
      }

      .pop-msg {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #991b1b;
      }

      .btn-pop-yes {
        background: #dc2626;
        color: #ffffff;
        border: none;
        border-radius: 3px;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.1rem 0.35rem;
        cursor: pointer;
      }

      .btn-pop-no {
        background: #ffffff;
        color: #475569;
        border: 1px solid #cbd5e1;
        border-radius: 3px;
        font-size: 0.6875rem;
        padding: 0.1rem 0.35rem;
        cursor: pointer;
      }

      /* Empty states */
      .empty-roster-box,
      .empty-search-box {
        padding: 2rem;
        text-align: center;
        background: #ffffff;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.5rem;
      }

      .empty-icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 44px;
        height: 44px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #64748b;
      }

      .empty-icon {
        width: 22px;
        height: 22px;
      }

      .empty-icon-sm {
        width: 20px;
        height: 20px;
        color: #94a3b8;
      }

      .empty-roster-box h5 {
        margin: 0;
        font-size: 0.9375rem;
        font-weight: 600;
        color: #0f172a;
      }

      .empty-roster-box p,
      .empty-search-box p {
        margin: 0;
        font-size: 0.8125rem;
        color: #64748b;
      }

      .btn-add-member-empty {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        margin-top: 0.5rem;
        padding: 0.5rem 1rem;
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
export class TeamMembersTabComponent {
  members = input<OrganizationMemberResponseDto[]>([]);
  manager = input<OrganizationMemberResponseDto | null>(null);
  memberGrantsMap = input<Record<string, number>>({});
  canManageMembers = input<boolean>(false);
  canTransferManager = input<boolean>(false);
  isTeamActive = input<boolean>(true);

  addMember = output<void>();
  giveAccess = output<OrganizationMemberResponseDto>();
  changeManager = output<void>();
  removeMember = output<OrganizationMemberResponseDto>();
  viewMemberGrants = output<string>();

  readonly searchQuery = signal<string>('');
  readonly confirmRemoveUserId = signal<string | null>(null);

  readonly filteredMembers = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const list = this.members();
    if (!q) return list;
    return list.filter((m) => {
      const name = `${m.firstName || ''} ${m.lastName || ''}`.toLowerCase();
      const email = (m.email || '').toLowerCase();
      return name.includes(q) || email.includes(q);
    });
  });

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

  getGrantsCount(email: string): number {
    return this.memberGrantsMap()[email] ?? 0;
  }

  onConfirmRemove(member: OrganizationMemberResponseDto): void {
    this.confirmRemoveUserId.set(null);
    this.removeMember.emit(member);
  }
}
