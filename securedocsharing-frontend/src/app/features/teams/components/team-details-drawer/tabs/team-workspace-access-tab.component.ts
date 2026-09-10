import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import {
  LucideHardDrive,
  LucideFolder,
  LucideShield,
  LucidePencil,
  LucideTrash2,
  LucidePlus,
} from '@lucide/angular';
import { TeamAccessBoundaryDto } from '../../../models/team-delegation.models';

@Component({
  selector: 'sd-team-workspace-access-tab',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    LucideHardDrive,
    LucideFolder,
    LucideShield,
    LucidePencil,
    LucideTrash2,
    LucidePlus,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="workspace-tab-container">
      <!-- Section Top Header -->
      <div class="section-top-bar">
        <div class="section-title-group">
          <svg lucideHardDrive class="top-icon"></svg>
          <h4>Workspace Access</h4>
          @if (boundary()) {
            <span class="status-badge-active">Allocated</span>
          } @else {
            <span class="status-badge-unallocated">Not Allocated</span>
          }
        </div>

        @if (!boundary() && canManageOrg() && isTeamActive()) {
          <button
            type="button"
            class="btn-allocate-top"
            (click)="allocateWorkspace.emit()"
            title="Allocate workspace access for this team"
          >
            <svg lucidePlus class="btn-icon"></svg>
            <span>Allocate Workspace Access</span>
          </button>
        }
      </div>

      <!-- Focused Compact Workspace Access Card -->
      @if (boundary(); as b) {
        <div class="workspace-access-card" [class.is-suspended]="b.status === 'SUSPENDED'">
          <div class="card-main">
            <!-- Left: Icon & Details -->
            <div class="workspace-details">
              <div class="folder-avatar">
                <svg lucideFolder class="folder-svg"></svg>
              </div>
              <div class="identity-info">
                <div class="title-row">
                  <h3 class="workspace-name">{{ b.workspaceName }}</h3>
                  <!-- Maximum Permission Badge -->
                  @let perm = b.ceilingLevel || b.permissionLevel || 'VIEW';
                  <span
                    class="perm-badge"
                    [class.is-edit]="perm === 'EDIT'"
                    [class.is-download]="perm === 'DOWNLOAD'"
                    [class.is-view]="perm === 'VIEW'"
                  >
                    <svg lucideShield class="perm-icon"></svg>
                    <span>Maximum: {{ perm }}</span>
                  </span>

                  <!-- Active Status Tag -->
                  <span class="status-tag" [class.active]="b.status !== 'SUSPENDED'" [class.suspended]="b.status === 'SUSPENDED'">
                    <span class="status-dot"></span>
                    {{ b.status === 'SUSPENDED' ? 'Suspended' : 'Active' }}
                  </span>
                </div>

                <div class="folder-path-row">
                  <span class="path-label">Root Folder:</span>
                  <span class="path-value font-mono">
                    {{ b.folderPath || b.rootFolderPath || '/' + (b.folderName || b.rootFolderName || '') }}
                  </span>
                </div>
              </div>
            </div>

            <!-- Right: Action Buttons (Org Admin Only) -->
            @if (canManageOrg() && isTeamActive()) {
              <div class="card-actions-group">
                <button
                  type="button"
                  class="btn-action btn-change"
                  (click)="changeWorkspace.emit()"
                  title="Change workspace allocation or maximum permission"
                >
                  <svg lucidePencil class="action-icon"></svg>
                  <span>Change Access</span>
                </button>

                @if (confirmRevoke()) {
                  <div class="confirm-revoke-pop">
                    <span class="confirm-msg">Revoke access?</span>
                    <button
                      type="button"
                      class="btn-pop-yes"
                      (click)="onConfirmRevoke()"
                    >
                      Yes, Revoke
                    </button>
                    <button
                      type="button"
                      class="btn-pop-no"
                      (click)="confirmRevoke.set(false)"
                    >
                      Cancel
                    </button>
                  </div>
                } @else {
                  <button
                    type="button"
                    class="btn-action btn-revoke"
                    (click)="confirmRevoke.set(true)"
                    title="Revoke workspace access for this team"
                  >
                    <svg lucideTrash2 class="action-icon"></svg>
                    <span>Revoke</span>
                  </button>
                }
              </div>
            }
          </div>

          <!-- Card Bottom Meta Footer -->
          <div class="card-footer font-tabular">
            <span class="footer-item">
              <span class="footer-label">Allocated:</span>
              <span class="footer-value">
                {{ (b.allocatedAt || b.assignedAt | date: 'mediumDate') || '—' }}
              </span>
            </span>
            @if (b.allocatedByUserEmail || b.assignedByUserEmail) {
              <span class="footer-sep">·</span>
              <span class="footer-item">
                <span class="footer-label">By:</span>
                <span class="footer-value">{{ b.allocatedByUserEmail || b.assignedByUserEmail }}</span>
              </span>
            }
            <span class="footer-sep">·</span>
            <span class="footer-item">
              <span class="footer-badge">Delegation Enforced</span>
            </span>
          </div>
        </div>
      } @else {
        <!-- Empty Unallocated State Card -->
        <div class="unallocated-card">
          <div class="empty-icon-wrap">
            <svg lucideHardDrive class="empty-icon"></svg>
          </div>
          <h5>No Workspace Allocated</h5>
          <p class="unallocated-desc">
            This team has not been allocated an operational workspace. Allocating a workspace defines
            the operational boundary and the Maximum Permission ceiling for member access grants.
          </p>

          @if (canManageOrg() && isTeamActive()) {
            <button
              type="button"
              class="btn-allocate-empty"
              (click)="allocateWorkspace.emit()"
            >
              <svg lucidePlus class="btn-icon"></svg>
              <span>Allocate Workspace Access</span>
            </button>
          } @else {
            <p class="notice-admin-only">
              Workspace access can be allocated by an organization administrator.
            </p>
          }
        </div>
      }
    </div>
  `,
  styles: [
    `
      .workspace-tab-container {
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
        color: #64748b;
      }

      .status-badge-active {
        display: inline-flex;
        align-items: center;
        padding: 0.15rem 0.5rem;
        background: #f0fdf4;
        color: #15803d;
        border: 1px solid #bbf7d0;
        border-radius: 9999px;
        font-size: 0.75rem;
        font-weight: 600;
      }

      .status-badge-unallocated {
        display: inline-flex;
        align-items: center;
        padding: 0.15rem 0.5rem;
        background: #fff7ed;
        color: #c2410c;
        border: 1px solid #fed7aa;
        border-radius: 9999px;
        font-size: 0.75rem;
        font-weight: 600;
      }

      .btn-allocate-top {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.4rem 0.85rem;
        background: #ea580c;
        color: #ffffff;
        border: none;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s ease;

        &:hover {
          background: #c2410c;
        }

        .btn-icon {
          width: 15px;
          height: 15px;
        }
      }

      /* ── Redesigned Compact Workspace Access Card ── */
      .workspace-access-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 12px;
        padding: 1.25rem 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
        transition: all 0.15s ease;

        &.is-suspended {
          border-color: #fecaca;
          background: #fffafa;
        }
      }

      .card-main {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: 1.25rem;
        flex-wrap: wrap;
      }

      .workspace-details {
        display: flex;
        align-items: flex-start;
        gap: 1rem;
        min-width: 0;
      }

      .folder-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 42px;
        height: 42px;
        border-radius: 10px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        flex-shrink: 0;
      }

      .folder-svg {
        width: 20px;
        height: 20px;
      }

      .identity-info {
        display: flex;
        flex-direction: column;
        gap: 0.4rem;
        min-width: 0;
      }

      .title-row {
        display: flex;
        align-items: center;
        gap: 0.6rem;
        flex-wrap: wrap;
      }

      .workspace-name {
        font-size: 1.0625rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0;
      }

      .perm-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        padding: 0.2rem 0.55rem;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;

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

      .perm-icon {
        width: 12px;
        height: 12px;
      }

      .status-tag {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.15rem 0.45rem;
        border-radius: 9999px;

        &.active {
          background: #f0fdf4;
          color: #15803d;
          border: 1px solid #bbf7d0;

          .status-dot {
            background-color: #16a34a;
          }
        }

        &.suspended {
          background: #fef2f2;
          color: #b91c1c;
          border: 1px solid #fecaca;

          .status-dot {
            background-color: #dc2626;
          }
        }
      }

      .status-dot {
        width: 5px;
        height: 5px;
        border-radius: 50%;
      }

      .folder-path-row {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.8125rem;
      }

      .path-label {
        color: #64748b;
        font-size: 0.75rem;
      }

      .path-value {
        color: #0f172a;
        background: #f1f5f9;
        padding: 0.15rem 0.5rem;
        border-radius: 4px;
        font-size: 0.75rem;
        font-weight: 500;
      }

      .card-actions-group {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        margin-left: auto;
      }

      .btn-action {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.35rem 0.75rem;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.15s ease;

        .action-icon {
          width: 14px;
          height: 14px;
        }

        &.btn-change {
          background: #ffffff;
          border: 1px solid #cbd5e1;
          color: #334155;

          &:hover {
            background: #f8fafc;
            border-color: #94a3b8;
            color: #0f172a;
          }
        }

        &.btn-revoke {
          background: #ffffff;
          border: 1px solid #fecaca;
          color: #dc2626;

          &:hover {
            background: #fef2f2;
            border-color: #f87171;
          }
        }
      }

      .confirm-revoke-pop {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        background: #fef2f2;
        border: 1px solid #fecaca;
        padding: 0.25rem 0.5rem;
        border-radius: 6px;
      }

      .confirm-msg {
        font-size: 0.75rem;
        font-weight: 600;
        color: #991b1b;
      }

      .btn-pop-yes {
        background: #dc2626;
        color: #ffffff;
        border: none;
        border-radius: 4px;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.2rem 0.5rem;
        cursor: pointer;
        transition: background 0.15s ease;

        &:hover {
          background: #b91c1c;
        }
      }

      .btn-pop-no {
        background: #ffffff;
        color: #475569;
        border: 1px solid #cbd5e1;
        border-radius: 4px;
        font-size: 0.6875rem;
        padding: 0.2rem 0.5rem;
        cursor: pointer;

        &:hover {
          background: #f8fafc;
        }
      }

      /* Card Bottom Footer */
      .card-footer {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding-top: 0.75rem;
        border-top: 1px solid #f1f5f9;
        font-size: 0.75rem;
        color: #64748b;
        flex-wrap: wrap;
      }

      .footer-item {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
      }

      .footer-label {
        color: #94a3b8;
      }

      .footer-value {
        color: #334155;
        font-weight: 500;
      }

      .footer-sep {
        color: #cbd5e1;
      }

      .footer-badge {
        font-size: 0.6875rem;
        color: #475569;
        background: #f1f5f9;
        padding: 0.1rem 0.4rem;
        border-radius: 4px;
        font-weight: 500;
      }

      /* ── Unallocated State ──────────────────────── */
      .unallocated-card {
        padding: 2.5rem 1.5rem;
        text-align: center;
        background: #ffffff;
        border: 1px dashed #cbd5e1;
        border-radius: 12px;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.75rem;

        .empty-icon-wrap {
          display: flex;
          align-items: center;
          justify-content: center;
          width: 44px;
          height: 44px;
          border-radius: 10px;
          background: #fff7ed;
          color: #ea580c;
        }

        .empty-icon {
          width: 22px;
          height: 22px;
        }

        h5 {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #0f172a;
        }

        .unallocated-desc {
          font-size: 0.8125rem;
          color: #64748b;
          max-width: 440px;
          margin: 0;
          line-height: 1.45;
        }

        .btn-allocate-empty {
          display: inline-flex;
          align-items: center;
          gap: 0.4rem;
          padding: 0.45rem 0.95rem;
          background: #ea580c;
          color: #ffffff;
          border: none;
          border-radius: 6px;
          font-size: 0.8125rem;
          font-weight: 600;
          cursor: pointer;
          transition: background 0.15s ease;

          &:hover {
            background: #c2410c;
          }

          .btn-icon {
            width: 15px;
            height: 15px;
          }
        }

        .notice-admin-only {
          font-size: 0.75rem;
          color: #94a3b8;
          font-style: italic;
          margin: 0;
        }
      }

      .font-mono {
        font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class TeamWorkspaceAccessTabComponent {
  boundary = input<TeamAccessBoundaryDto | null>(null);
  canManageOrg = input<boolean>(false);
  isTeamActive = input<boolean>(true);

  allocateWorkspace = output<void>();
  changeWorkspace = output<void>();
  revokeWorkspace = output<void>();

  readonly confirmRevoke = signal<boolean>(false);

  onConfirmRevoke(): void {
    this.confirmRevoke.set(false);
    this.revokeWorkspace.emit();
  }
}
