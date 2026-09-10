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
  LucideShieldCheck,
  LucideSearch,
  LucideX,
  LucidePlus,
  LucideFolder,
  LucideFileText,
  LucidePencil,
  LucideTrash2,
} from '@lucide/angular';
import { TeamMemberAccessDto } from '../../../models/team-delegation.models';

@Component({
  selector: 'sd-team-member-access-tab',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    FormsModule,
    LucideShieldCheck,
    LucideSearch,
    LucideX,
    LucidePlus,
    LucideFolder,
    LucideFileText,
    LucidePencil,
    LucideTrash2,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="member-access-tab-container">
      <!-- Top Bar -->
      <div class="section-top-bar">
        <div class="section-title-group">
          <svg lucideShieldCheck class="top-icon"></svg>
          <h4>Member Access Ledger</h4>
          <span class="count-pill">{{ filteredList().length }}</span>
        </div>

        @if (canManageAccess() && isTeamActive()) {
          <button
            type="button"
            class="btn-give-access-top"
            (click)="giveAccess.emit()"
            title="Grant specific workspace access to a team member"
          >
            <svg lucidePlus class="btn-icon"></svg>
            <span>Give Member Access</span>
          </button>
        }
      </div>

      <!-- Search & Filter Bar -->
      @if (accessList().length > 0) {
        <div class="search-input-wrap">
          <svg lucideSearch class="search-icon"></svg>
          <input
            type="text"
            class="search-field"
            placeholder="Filter by member, resource name, or path..."
            [ngModel]="searchQuery()"
            (ngModelChange)="searchQuery.set($event)"
          />
          @if (searchQuery()) {
            <button
              type="button"
              class="btn-clear-search"
              (click)="searchQuery.set('')"
              aria-label="Clear filter"
            >
              <svg lucideX class="clear-icon"></svg>
            </button>
          }
        </div>
      }

      <!-- Access Ledger Table -->
      <div class="ledger-surface">
        @if (accessList().length === 0) {
          <div class="empty-ledger-card">
            <div class="empty-icon-wrap">
              <svg lucideShieldCheck class="empty-icon"></svg>
            </div>
            <h5>No Member Access Granted</h5>
            <p>
              Workspace access grants zero default content access to team members.
              Team managers can grant individual member access to specific folders or documents within the workspace maximum permission.
            </p>
            @if (canManageAccess() && isTeamActive()) {
              <button
                type="button"
                class="btn-give-access-empty"
                (click)="giveAccess.emit()"
              >
                <svg lucidePlus class="btn-icon"></svg>
                <span>Give First Member Access</span>
              </button>
            }
          </div>
        } @else if (filteredList().length === 0) {
          <div class="empty-filter-box">
            <svg lucideSearch class="empty-icon-sm"></svg>
            <p>No member access grants match "{{ searchQuery() }}".</p>
          </div>
        } @else {
          <div class="table-responsive">
            <table class="access-table" role="table">
              <thead>
                <tr>
                  <th scope="col" class="th-member">Team Member</th>
                  <th scope="col" class="th-resource">Resource</th>
                  <th scope="col" class="th-permission">Permission</th>
                  <th scope="col" class="th-meta">Granted Date & By</th>
                  @if (canManageAccess() && isTeamActive()) {
                    <th scope="col" class="th-actions">Actions</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (grant of filteredList(); track grant.permissionId) {
                  <tr class="access-row">
                    <!-- Member Identity -->
                    <td class="td-member">
                      <div class="member-cell">
                        <div class="member-avatar">
                          {{ getInitials(grant.userName, grant.userEmail) }}
                        </div>
                        <div class="member-meta">
                          <span class="member-name">{{ grant.userName || grant.userEmail }}</span>
                          <span class="member-email">{{ grant.userEmail }}</span>
                        </div>
                      </div>
                    </td>

                    <!-- Resource -->
                    <td class="td-resource">
                      <div class="resource-cell">
                        <div class="resource-icon-wrap" [class.is-folder]="grant.resourceType === 'FOLDER'">
                          @if (grant.resourceType === 'FOLDER') {
                            <svg lucideFolder class="resource-icon"></svg>
                          } @else {
                            <svg lucideFileText class="resource-icon"></svg>
                          }
                        </div>
                        <div class="resource-meta">
                          <span class="resource-name" [title]="grant.resourceName">{{ grant.resourceName }}</span>
                          @if (grant.resourcePath) {
                            <span class="resource-path font-mono" [title]="grant.resourcePath">{{ grant.resourcePath }}</span>
                          }
                        </div>
                      </div>
                    </td>

                    <!-- Permission Badge -->
                    <td class="td-permission">
                      <span
                        class="perm-badge"
                        [class.is-edit]="grant.permissionLevel === 'EDIT'"
                        [class.is-download]="grant.permissionLevel === 'DOWNLOAD'"
                        [class.is-view]="grant.permissionLevel === 'VIEW'"
                      >
                        {{ grant.permissionLevel }}
                      </span>
                    </td>

                    <!-- Granted Date & By -->
                    <td class="td-meta font-tabular">
                      <div class="meta-cell">
                        <span class="granted-date">
                          {{ (grant.grantedAt | date: 'MMM d, yyyy') || '—' }}
                        </span>
                        @if (grant.grantedByUserEmail) {
                          <span class="granted-by" [title]="grant.grantedByUserEmail">
                            by {{ grant.grantedByUserEmail }}
                          </span>
                        }
                      </div>
                    </td>

                    <!-- Actions -->
                    @if (canManageAccess() && isTeamActive()) {
                      <td class="td-actions">
                        @if (confirmRevokeId() === grant.permissionId) {
                          <div class="confirm-revoke-pop">
                            <span class="revoke-prompt">Revoke?</span>
                            <button
                              type="button"
                              class="btn-pop-yes"
                              (click)="onConfirmRevoke(grant)"
                            >
                              Yes
                            </button>
                            <button
                              type="button"
                              class="btn-pop-no"
                              (click)="confirmRevokeId.set(null)"
                            >
                              No
                            </button>
                          </div>
                        } @else {
                          <div class="action-buttons-group">
                            <button
                              type="button"
                              class="btn-action-icon"
                              (click)="editAccess.emit(grant)"
                              title="Edit Member Permission"
                            >
                              <svg lucidePencil class="action-icon"></svg>
                            </button>
                            <button
                              type="button"
                              class="btn-action-icon is-danger"
                              (click)="confirmRevokeId.set(grant.permissionId)"
                              title="Remove Access"
                            >
                              <svg lucideTrash2 class="action-icon"></svg>
                            </button>
                          </div>
                        }
                      </td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .member-access-tab-container {
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

      .btn-give-access-top {
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

      /* ── Ledger Surface & Table ────────────────── */
      .ledger-surface {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        overflow: hidden;
      }

      .table-responsive {
        width: 100%;
        overflow-x: auto;
      }

      .access-table {
        width: 100%;
        border-collapse: collapse;
        font-size: 0.8125rem;
        text-align: left;

        thead {
          background-color: #f8fafc;
          border-bottom: 1px solid #e2e8f0;

          th {
            padding: 0.65rem 1rem;
            font-size: 0.7rem;
            font-weight: 600;
            color: #64748b;
            text-transform: uppercase;
            letter-spacing: 0.04em;
          }
        }

        tbody {
          tr.access-row {
            border-bottom: 1px solid #f1f5f9;
            transition: background-color 0.12s ease;

            &:last-child {
              border-bottom: none;
            }

            &:hover {
              background-color: #f8fafc;
            }

            td {
              padding: 0.75rem 1rem;
              vertical-align: middle;
            }
          }
        }
      }

      /* Column specifics */
      .member-cell {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        min-width: 160px;
      }

      .member-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #cbd5e1;
        font-size: 0.6875rem;
        font-weight: 700;
        flex-shrink: 0;
      }

      .member-meta {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .member-name {
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .member-email {
        font-size: 0.6875rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .resource-cell {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        min-width: 180px;
      }

      .resource-icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 26px;
        height: 26px;
        border-radius: 6px;
        background: #f1f5f9;
        color: #64748b;
        flex-shrink: 0;

        &.is-folder {
          background: #eff6ff;
          color: #2563eb;
        }
      }

      .resource-icon {
        width: 14px;
        height: 14px;
      }

      .resource-meta {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .resource-name {
        font-weight: 600;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .resource-path {
        font-size: 0.6875rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      /* Permission badges */
      .perm-badge {
        display: inline-flex;
        align-items: center;
        font-size: 0.6875rem;
        font-weight: 700;
        padding: 0.15rem 0.45rem;
        border-radius: 4px;

        &.is-edit {
          background: #fff7ed;
          color: #c2410c;
          border: 1px solid #fed7aa;
        }

        &.is-download {
          background: #eff6ff;
          color: #1d4ed8;
          border: 1px solid #bfdbfe;
        }

        &.is-view {
          background: #f8fafc;
          color: #475569;
          border: 1px solid #e2e8f0;
        }
      }

      .meta-cell {
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
      }

      .granted-date {
        font-size: 0.75rem;
        color: #1e293b;
      }

      .granted-by {
        font-size: 0.6875rem;
        color: #94a3b8;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 160px;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }

      /* Actions */
      .action-buttons-group {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        justify-content: flex-end;
      }

      .btn-action-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        background: transparent;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        color: #64748b;
        cursor: pointer;
        transition: all 0.12s ease;

        &:hover {
          background: #f8fafc;
          color: #0f172a;
          border-color: #cbd5e1;
        }

        &.is-danger:hover {
          background: #fef2f2;
          color: #dc2626;
          border-color: #fecaca;
        }
      }

      .action-icon {
        width: 13px;
        height: 13px;
      }

      .confirm-revoke-pop {
        display: flex;
        align-items: center;
        gap: 0.3rem;
        background: #fef2f2;
        border: 1px solid #fecaca;
        padding: 0.2rem 0.4rem;
        border-radius: 5px;
        justify-content: flex-end;
      }

      .revoke-prompt {
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
      .empty-ledger-card {
        padding: 2.5rem 1.5rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 0.75rem;

        h5 {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #0f172a;
        }

        p {
          max-width: 460px;
          margin: 0;
          font-size: 0.8125rem;
          color: #64748b;
          line-height: 1.45;
        }
      }

      .empty-icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 46px;
        height: 46px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #64748b;
      }

      .empty-icon {
        width: 22px;
        height: 22px;
      }

      .btn-give-access-empty {
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

      .empty-filter-box {
        padding: 2rem;
        text-align: center;
        color: #64748b;
        font-size: 0.8125rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.5rem;
      }

      .empty-icon-sm {
        width: 20px;
        height: 20px;
        color: #94a3b8;
      }
    `,
  ],
})
export class TeamMemberAccessTabComponent {
  accessList = input<TeamMemberAccessDto[]>([]);
  canManageAccess = input<boolean>(false);
  isTeamActive = input<boolean>(true);
  initialSearchQuery = input<string>('');

  giveAccess = output<void>();
  editAccess = output<TeamMemberAccessDto>();
  revokeAccess = output<TeamMemberAccessDto>();

  readonly searchQuery = signal<string>('');
  readonly confirmRevokeId = signal<number | null>(null);

  constructor() {
    // Handle initial search query if passed (e.g. jumping from member card)
    const initial = this.initialSearchQuery();
    if (initial) {
      this.searchQuery.set(initial);
    }
  }

  readonly filteredList = computed(() => {
    const q = this.searchQuery().trim().toLowerCase();
    const list = this.accessList();
    if (!q) return list;
    return list.filter((a) => {
      const name = (a.userName || '').toLowerCase();
      const email = (a.userEmail || '').toLowerCase();
      const resName = (a.resourceName || '').toLowerCase();
      const resPath = (a.resourcePath || '').toLowerCase();
      return name.includes(q) || email.includes(q) || resName.includes(q) || resPath.includes(q);
    });
  });

  getInitials(name?: string, email?: string): string {
    if (name) {
      const parts = name.trim().split(/\s+/);
      if (parts.length >= 2) {
        return (parts[0][0] + parts[1][0]).toUpperCase();
      }
      return name.slice(0, 2).toUpperCase();
    }
    if (email) return email.slice(0, 2).toUpperCase();
    return 'U';
  }

  onConfirmRevoke(grant: TeamMemberAccessDto): void {
    this.confirmRevokeId.set(null);
    this.revokeAccess.emit(grant);
  }
}
