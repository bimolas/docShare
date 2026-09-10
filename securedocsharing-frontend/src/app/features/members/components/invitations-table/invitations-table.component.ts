import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { OrganizationInvitationResponseDto } from '../../../../core/models/organization.model';

@Component({
  selector: 'app-invitations-table',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    IconComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (invitations().length === 0 && !isLoading()) {
      <div class="empty-invitations-container">
        <div class="empty-icon-box">
          <app-icon name="mail" [size]="24" />
        </div>
        <h3 class="empty-title">No Pending Invitations</h3>
        <p class="empty-desc">
          There are currently no outstanding invitations for this organization. All issued invitations have been accepted or expired.
        </p>
        @if (canInvite()) {
          <div class="empty-action-row">
            <button
              type="button"
              class="btn-primary-action"
              (click)="openInvite.emit()"
            >
              <app-icon name="user-plus" [size]="14" />
              <span>Invite New Member</span>
            </button>
          </div>
        }
      </div>
    } @else {
      <div class="table-scroll-wrapper">
        <table class="invitations-table" role="table" aria-label="Organization Pending Invitations">
          <thead>
            <tr>
              <th scope="col" class="th-invitee">Invitee Email</th>
              <th scope="col" class="th-role">Invited Role</th>
              <th scope="col" class="th-inviter">Invited By</th>
              <th scope="col" class="th-date">Sent Date</th>
              <th scope="col" class="th-expires">Expires</th>
              <th scope="col" class="th-actions">Actions</th>
            </tr>
          </thead>
          <tbody>
            @for (invite of invitations(); track invite.id) {
              <tr class="table-row">
                <!-- Invitee Email with Copy -->
                <td>
                  <div class="invitee-cell">
                    <div class="mail-icon-box">
                      <app-icon name="mail" [size]="14" />
                    </div>
                    <span class="invitee-email">{{ invite.inviteeEmail }}</span>
                    <button
                      type="button"
                      class="copy-btn"
                      [title]="copiedId() === invite.id ? 'Copied to clipboard!' : 'Copy email address'"
                      (click)="copyEmail(invite)"
                    >
                      @if (copiedId() === invite.id) {
                        <app-icon name="check" [size]="12" class="text-emerald-600" />
                      } @else {
                        <app-icon name="copy" [size]="12" />
                      }
                    </button>
                  </div>
                </td>

                <!-- Invited Role -->
                <td>
                  <div class="role-cell">
                    @if (isRoleAdmin(invite.role)) {
                      <span class="role-badge role-admin">
                        <app-icon name="shield" [size]="12" />
                        <span>{{ formatRole(invite.role) }}</span>
                      </span>
                    } @else if (isRoleManager(invite.role)) {
                      <span class="role-badge role-manager">
                        <app-icon name="user" [size]="12" />
                        <span>{{ formatRole(invite.role) }}</span>
                      </span>
                    } @else {
                      <span class="role-badge role-member">
                        <span>{{ formatRole(invite.role) }}</span>
                      </span>
                    }
                  </div>
                </td>

                <!-- Invited By -->
                <td>
                  <span class="inviter-text truncate">
                    {{ invite.inviterEmail || invite.inviterUserId || '—' }}
                  </span>
                </td>

                <!-- Sent Date -->
                <td>
                  <span class="date-cell">
                    {{ invite.createdAt | date: 'MMM d, yyyy' }}
                  </span>
                </td>

                <!-- Expires -->
                <td>
                  @if (invite.expiresAt) {
                    <span class="date-cell">
                      {{ invite.expiresAt | date: 'MMM d, yyyy' }}
                    </span>
                  } @else {
                    <span class="text-muted-foreground font-mono text-[11px]">—</span>
                  }
                </td>

                <!-- Revoke Action -->
                <td class="td-actions">
                  <button
                    type="button"
                    class="action-btn-revoke"
                    [title]="'Revoke invitation for ' + invite.inviteeEmail"
                    (click)="revoke.emit(invite)"
                  >
                    <app-icon name="trash-2" [size]="13" />
                    <span>Revoke</span>
                  </button>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    /* ── Empty State ── */
    .empty-invitations-container {
      padding: 60px 20px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }

    .empty-icon-box {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      color: #64748b;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 4px;
    }

    .empty-title {
      font-size: 15px;
      font-weight: 600;
      color: #0f172a;
      margin: 0;
    }

    .empty-desc {
      font-size: 13px;
      color: #64748b;
      max-width: 380px;
      margin: 0;
      line-height: 1.5;
    }

    .empty-action-row {
      margin-top: 8px;
    }

    .btn-primary-action {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 14px;
      border-radius: 8px;
      background: var(--sd-color-primary, #f97316);
      color: #ffffff;
      border: 1px solid rgba(234, 88, 12, 0.2);
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      box-shadow: 0 1px 2px rgba(249, 115, 22, 0.2);
      transition: all 0.15s ease;
    }

    .btn-primary-action:hover {
      background: #ea580c;
    }

    /* ── Table Layout ── */
    .table-scroll-wrapper {
      overflow-x: auto;
      width: 100%;
    }

    .invitations-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }

    .invitations-table thead th {
      background: #f8fafc;
      color: #475569;
      font-weight: 600;
      font-size: 11.5px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 12px 16px;
      border-bottom: 1px solid #e2e8f0;
      white-space: nowrap;
      user-select: none;
    }

    .th-invitee { width: 34%; }
    .th-role { width: 20%; }
    .th-inviter { width: 20%; }
    .th-date { width: 14%; }
    .th-expires { width: 12%; }
    .th-actions { width: 10%; text-align: right; }

    .table-row {
      border-bottom: 1px solid #f1f5f9;
      height: 52px;
      transition: background-color 0.12s ease;
    }

    .table-row:hover {
      background-color: #f8fafc;
    }

    .table-row td {
      padding: 10px 16px;
      vertical-align: middle;
      color: #334155;
    }

    /* ── Invitee Cell ── */
    .invitee-cell {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 220px;
    }

    .mail-icon-box {
      width: 28px;
      height: 28px;
      border-radius: 6px;
      background: #eff6ff;
      border: 1px solid #dbeafe;
      color: #2563eb;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .invitee-email {
      font-weight: 600;
      color: #0f172a;
      font-size: 13px;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 220px;
    }

    .copy-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      border-radius: 4px;
      background: transparent;
      border: none;
      color: #64748b;
      cursor: pointer;
      transition: all 0.12s ease;
    }

    .copy-btn:hover {
      background: #f1f5f9;
      color: #0f172a;
    }

    /* ── Role Badges ── */
    .role-cell {
      display: flex;
      align-items: center;
    }

    .role-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 11.5px;
      font-weight: 600;
      user-select: none;
    }

    .role-admin {
      background: #fff7ed;
      color: #c2410c;
      border: 1px solid #ffedd5;
    }

    .role-manager {
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #e2e8f0;
    }

    .role-member {
      color: #64748b;
      font-size: 12px;
      font-weight: 500;
      padding: 2px 4px;
      border: none;
      background: transparent;
    }

    .inviter-text {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 11.5px;
      color: #64748b;
      max-width: 200px;
      display: block;
    }

    .date-cell {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 11.5px;
      color: #64748b;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    .td-actions {
      text-align: right;
    }

    .action-btn-revoke {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 5px 9px;
      border-radius: 6px;
      border: 1px solid transparent;
      background: transparent;
      color: #dc2626;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.12s ease;
      user-select: none;
    }

    .action-btn-revoke:hover {
      background: #fef2f2;
      border-color: #fecaca;
      color: #b91c1c;
    }
  `],
})
export class InvitationsTableComponent {
  readonly invitations = input<OrganizationInvitationResponseDto[]>([]);
  readonly isLoading = input<boolean>(false);
  readonly canInvite = input<boolean>(false);

  readonly revoke = output<OrganizationInvitationResponseDto>();
  readonly openInvite = output<void>();

  readonly copiedId = signal<number | null>(null);

  copyEmail(invite: OrganizationInvitationResponseDto): void {
    if (!invite.inviteeEmail) return;
    navigator.clipboard.writeText(invite.inviteeEmail).then(() => {
      this.copiedId.set(invite.id);
      setTimeout(() => {
        if (this.copiedId() === invite.id) {
          this.copiedId.set(null);
        }
      }, 2000);
    });
  }

  isRoleAdmin(role: string): boolean {
    const r = (role || '').toUpperCase();
    return r.includes('ADMIN');
  }

  isRoleManager(role: string): boolean {
    const r = (role || '').toUpperCase();
    return r.includes('MANAGER');
  }

  formatRole(role: string): string {
    if (this.isRoleAdmin(role)) return 'Administrator';
    if (this.isRoleManager(role)) return 'Org Manager';
    return 'Member';
  }
}
