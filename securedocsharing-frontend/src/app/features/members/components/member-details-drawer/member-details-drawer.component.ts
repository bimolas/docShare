import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  inject,
  effect,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { DrawerComponent } from '../../../../shared/ui/drawer/drawer.component';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { AuthStore } from '../../../../state/auth/auth.store';
import {
  OrganizationMemberDetailResponseDto,
  MemberAccessSummaryResponseDto,
  TeamSummaryDto,
  DirectPermissionSummaryDto,
} from '../../../../core/models/organization.model';
import { extractErrorMessage } from '../../../../core/models/app-error.model';

@Component({
  selector: 'app-member-details-drawer',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    RouterLink,
    DrawerComponent,
    IconComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-drawer
      [isOpen]="isOpen()"
      title="Member Details"
      subtitle="Organization membership and governance inspector"
      size="md"
      position="right"
      (closed)="close()"
    >
      @if (isLoading()) {
        <div class="drawer-loading-container">
          <div class="drawer-loading-spinner"></div>
          <p class="drawer-loading-text">Loading member details and access summary...</p>
        </div>
      } @else if (error()) {
        <div class="drawer-error-container">
          <div class="drawer-error-icon-box">
            <app-icon name="alert-circle" [size]="20" />
          </div>
          <h4 class="drawer-error-title">Unable to Load Details</h4>
          <p class="drawer-error-desc">{{ error() }}</p>
          <button
            type="button"
            class="btn-retry"
            (click)="loadData()"
          >
            <app-icon name="refresh-cw" [size]="13" />
            <span>Retry</span>
          </button>
        </div>
      } @else if (member()) {
        <div class="inspector-content">
          <!-- 1. Profile Hero Card -->
          <div class="hero-card">
            <div class="hero-avatar">
              {{ getInitials(member()!) }}
            </div>
            <div class="hero-details">
              <div class="hero-name-row">
                <h3 class="hero-name">{{ getDisplayName(member()!) }}</h3>
                @if (isSelfMember()) {
                  <span class="self-tag">(You)</span>
                }
                @if (isSoleAdmin(member()!)) {
                  <span class="sole-admin-shield" title="Sole active administrator">
                    <app-icon name="shield-check" [size]="15" />
                  </span>
                }
              </div>

              <div class="hero-meta-row">
                <div class="hero-email">
                  <app-icon name="mail" [size]="13" class="text-slate-400" />
                  <span>{{ member()!.email }}</span>
                </div>
                <div class="status-badge" [class]="getStatusClass(member()!.status)">
                  <span class="status-dot" [class]="getStatusDotClass(member()!.status)"></span>
                  <span>{{ formatStatus(member()!.status) }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 2. Governance Action Bar -->
          <div class="section-group">
            <div class="section-header-label">Governance Actions</div>

            @if (isSelfMember()) {
              <div class="notice-box">
                <app-icon name="shield" [size]="15" class="notice-icon" />
                <span>This is your account. You cannot modify your own role or membership status directly.</span>
              </div>
            } @else if (isActorManager() && isRoleAdmin(member()!.role)) {
              <div class="notice-box">
                <app-icon name="shield" [size]="15" class="notice-icon" />
                <span>Organization Administrators can only be managed by other Administrators.</span>
              </div>
            } @else {
              <div class="actions-grid">
                <!-- Change Role -->
                @if (canChangeRole(member()!)) {
                  <button
                    type="button"
                    class="gov-btn gov-btn-default"
                    (click)="changeRoleClicked.emit(member()!)"
                  >
                    <app-icon name="user" [size]="14" />
                    <span>Change Role</span>
                  </button>
                }

                <!-- Suspend Member -->
                @if (canSuspend(member()!)) {
                  <button
                    type="button"
                    class="gov-btn gov-btn-warning"
                    (click)="suspendClicked.emit(member()!)"
                  >
                    <app-icon name="alert-circle" [size]="14" />
                    <span>Suspend</span>
                  </button>
                }

                <!-- Reactivate Member -->
                @if (canReactivate(member()!)) {
                  <button
                    type="button"
                    class="gov-btn gov-btn-success"
                    (click)="reactivateClicked.emit(member()!)"
                  >
                    <app-icon name="check" [size]="14" />
                    <span>Reactivate</span>
                  </button>
                }

                <!-- Transfer Admin -->
                @if (canTransferAdmin(member()!)) {
                  <button
                    type="button"
                    class="gov-btn gov-btn-warning"
                    (click)="transferAdminClicked.emit(member()!)"
                  >
                    <app-icon name="brand-shield" [size]="14" />
                    <span>Transfer Admin</span>
                  </button>
                }

                <!-- Remove Member -->
                @if (canRemove(member()!)) {
                  <button
                    type="button"
                    class="gov-btn gov-btn-danger full-width"
                    (click)="removeClicked.emit(member()!)"
                  >
                    <app-icon name="trash-2" [size]="14" />
                    <span>Remove from Organization</span>
                  </button>
                }
              </div>
            }
          </div>

          <!-- 3. Account & Role Metadata -->
          <div class="section-group">
            <div class="section-header-label">Membership & Account</div>
            <div class="inspector-card">
              <div class="meta-field">
                <span class="meta-label">Organization Role</span>
                <div class="meta-value">
                  @if (isRoleAdmin(member()!.role)) {
                    <span class="role-badge role-admin">
                      <app-icon name="shield" [size]="12" />
                      <span>{{ formatRole(member()!.role) }}</span>
                    </span>
                  } @else if (isRoleManager(member()!.role)) {
                    <span class="role-badge role-manager">
                      <app-icon name="user" [size]="12" />
                      <span>{{ formatRole(member()!.role) }}</span>
                    </span>
                  } @else {
                    <span class="role-badge role-member">
                      <span>{{ formatRole(member()!.role) }}</span>
                    </span>
                  }
                </div>
              </div>

              <div class="meta-row-two-col">
                <div class="meta-field">
                  <span class="meta-label">Joined Date</span>
                  <span class="meta-value mono-val">
                    {{ member()!.joinedAt | date: 'mediumDate' }}
                  </span>
                </div>
                <div class="meta-field">
                  <span class="meta-label">Status Updated</span>
                  <span class="meta-value mono-val">
                    {{ member()!.statusUpdatedAt ? (member()!.statusUpdatedAt | date: 'mediumDate') : '—' }}
                  </span>
                </div>
              </div>

              <div class="meta-field">
                <span class="meta-label">User Identifier</span>
                <span class="meta-value mono-val text-muted-foreground select-all truncate">
                  {{ member()!.userId }}
                </span>
              </div>

              @if (member()!.statusReason) {
                <div class="meta-field reason-field">
                  <span class="meta-label">Status Reason / Justification</span>
                  <div class="status-reason-box">
                    <app-icon name="info" [size]="13" class="text-amber-600 mt-0.5 shrink-0" />
                    <span>{{ member()!.statusReason }}</span>
                  </div>
                </div>
              }
            </div>
          </div>

          <!-- 4. Team Memberships (Read-Only) -->
          <div class="section-group">
            <div class="section-header-row">
              <div class="section-header-label">
                <span>Assigned Teams</span>
                <span class="count-bubble">{{ teams().length }}</span>
              </div>
              @if (orgCode()) {
                <a
                  [routerLink]="['/org', orgCode(), 'teams']"
                  class="header-action-link"
                >
                  <span>Manage in Teams</span>
                  <app-icon name="external-link" [size]="12" />
                </a>
              }
            </div>

            <div class="inspector-card">
              @if (teams().length === 0) {
                <div class="empty-list-note">No team assignments found for this member.</div>
              } @else {
                <ul class="access-list">
                  @for (team of teams(); track team.groupId || team.groupName) {
                    <li class="access-item">
                      <div class="access-item-details">
                        <div class="access-item-title">{{ team.groupName }}</div>
                        <div class="access-item-sub">Team ID #{{ team.groupId }}</div>
                      </div>
                      <span
                        class="team-role-badge"
                        [class.is-manager]="team.role === 'MANAGER'"
                      >
                        {{ team.role === 'MANAGER' ? 'Team Manager' : 'Team Member' }}
                      </span>
                    </li>
                  }
                </ul>
              }
            </div>
          </div>

          <!-- 5. Direct Resource Permissions (Read-Only) -->
          <div class="section-group">
            <div class="section-header-row">
              <div class="section-header-label">
                <span>Direct Resource Permissions</span>
                <span class="count-bubble">{{ directPermissions().length }}</span>
              </div>
            </div>

            <div class="inspector-card">
              @if (directPermissions().length === 0) {
                <div class="empty-list-note">No direct resource permissions assigned.</div>
              } @else {
                <ul class="access-list">
                  @for (perm of directPermissions(); track perm.permissionId) {
                    <li class="access-item">
                      <div class="access-item-main">
                        @if (perm.targetType === 'DOCUMENT') {
                          <app-icon name="file-text" [size]="16" class="perm-type-icon doc-icon" />
                        } @else {
                          <app-icon name="folder" [size]="16" class="perm-type-icon folder-icon" />
                        }
                        <div class="access-item-details">
                          <div class="access-item-title">{{ perm.targetName }}</div>
                          <div class="access-item-sub">{{ perm.targetType }}</div>
                        </div>
                      </div>
                      <span
                        class="perm-level-badge"
                        [class.level-edit]="perm.permissionLevel === 'EDIT'"
                        [class.level-download]="perm.permissionLevel === 'DOWNLOAD'"
                        [class.level-view]="perm.permissionLevel === 'VIEW'"
                      >
                        {{ perm.permissionLevel }}
                      </span>
                    </li>
                  }
                </ul>
              }
            </div>
          </div>
        </div>
      }
    </sd-drawer>
  `,
  styles: [`
    :host {
      display: contents;
    }

    /* ── Loading and Error States ── */
    .drawer-loading-container {
      padding: 60px 20px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
    }

    .drawer-loading-spinner {
      width: 28px;
      height: 28px;
      border: 2px solid var(--sd-color-primary, #f97316);
      border-top-color: transparent;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .drawer-loading-text {
      font-size: 12.5px;
      color: #64748b;
    }

    .drawer-error-container {
      padding: 40px 16px;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 10px;
    }

    .drawer-error-icon-box {
      width: 36px;
      height: 36px;
      border-radius: 50%;
      background: #fef2f2;
      color: #dc2626;
      border: 1px solid #fecaca;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .drawer-error-title {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      margin: 0;
    }

    .drawer-error-desc {
      font-size: 12px;
      color: #64748b;
      max-width: 300px;
      margin: 0;
      line-height: 1.4;
    }

    .btn-retry {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 6px;
      border: 1px solid #cbd5e1;
      background: #ffffff;
      color: #334155;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      margin-top: 4px;
      transition: all 0.12s ease;
    }

    .btn-retry:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }

    /* ── Inspector Content ── */
    .inspector-content {
      display: flex;
      flex-direction: column;
      gap: 20px;
      padding-bottom: 24px;
    }

    /* ── Hero Profile Card ── */
    .hero-card {
      display: flex;
      align-items: flex-start;
      gap: 14px;
      padding: 16px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
    }

    .hero-avatar {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #e2e8f0;
      font-size: 15px;
      font-weight: 700;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      text-transform: uppercase;
      user-select: none;
    }

    .hero-details {
      min-width: 0;
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: 3px;
    }

    .hero-name-row {
      display: flex;
      align-items: center;
      gap: 6px;
      flex-wrap: wrap;
    }

    .hero-name {
      font-size: 15px;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .self-tag {
      font-size: 11px;
      font-weight: 500;
      color: #64748b;
    }

    .sole-admin-shield {
      display: inline-flex;
      align-items: center;
      color: #d97706;
      cursor: help;
    }

    .hero-meta-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      flex-wrap: wrap;
      margin-top: 4px;
    }

    .hero-email {
      font-size: 11.5px;
      color: #64748b;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      display: flex;
      align-items: center;
      gap: 5px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 220px;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 11px;
      font-weight: 600;
      padding: 2px 8px;
      border-radius: 9999px;
      user-select: none;
    }

    .status-active {
      background: #ecfdf5;
      color: #047857;
      border: 1px solid #a7f3d0;
    }

    .status-suspended {
      background: #fffbeb;
      color: #b45309;
      border: 1px solid #fde68a;
    }

    .status-removed {
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #cbd5e1;
    }

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .dot-active {
      background-color: #10b981;
    }

    .dot-suspended {
      background-color: #f59e0b;
    }

    .dot-removed {
      background-color: #94a3b8;
    }

    /* ── Sections ── */
    .section-group {
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .section-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .section-header-label {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #64748b;
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .count-bubble {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 1px 6px;
      border-radius: 9999px;
      font-size: 10px;
      font-weight: 700;
      background: #f1f5f9;
      color: #475569;
    }

    .header-action-link {
      font-size: 11.5px;
      color: var(--sd-color-primary, #f97316);
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 4px;
      font-weight: 500;
      transition: opacity 0.12s ease;
    }

    .header-action-link:hover {
      text-decoration: underline;
    }

    .notice-box {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 10px 12px;
      border-radius: 8px;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      font-size: 12px;
      color: #64748b;
      line-height: 1.4;
    }

    .notice-icon {
      color: #94a3b8;
      flex-shrink: 0;
      margin-top: 1px;
    }

    /* ── Governance Actions Grid ── */
    .actions-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 8px;
    }

    .gov-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 8px 12px;
      border-radius: 8px;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.12s ease;
      min-height: 36px;
      user-select: none;
    }

    .gov-btn-default {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      color: #334155;
    }

    .gov-btn-default:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }

    .gov-btn-warning {
      background: #fffbeb;
      border: 1px solid #fde68a;
      color: #b45309;
    }

    .gov-btn-warning:hover {
      background: #fef3c7;
      color: #92400e;
      border-color: #fcd34d;
    }

    .gov-btn-success {
      background: #ecfdf5;
      border: 1px solid #a7f3d0;
      color: #047857;
    }

    .gov-btn-success:hover {
      background: #d1fae5;
      color: #065f46;
      border-color: #6ee7b7;
    }

    .gov-btn-danger {
      background: #fef2f2;
      border: 1px solid #fecaca;
      color: #dc2626;
    }

    .gov-btn-danger:hover {
      background: #fee2e2;
      color: #b91c1c;
      border-color: #fca5a5;
    }

    .gov-btn.full-width {
      grid-column: span 2;
    }

    /* ── Inspector Card ── */
    .inspector-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }

    .meta-field {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }

    .meta-label {
      font-size: 11px;
      color: #64748b;
      font-weight: 500;
    }

    .meta-value {
      font-size: 12.5px;
      color: #0f172a;
      font-weight: 500;
    }

    .mono-val {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 12px;
      font-variant-numeric: tabular-nums;
    }

    .meta-row-two-col {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
      padding-top: 4px;
      border-top: 1px solid #f1f5f9;
    }

    .reason-field {
      padding-top: 8px;
      border-top: 1px solid #f1f5f9;
    }

    .status-reason-box {
      display: flex;
      align-items: flex-start;
      gap: 8px;
      padding: 8px 10px;
      border-radius: 6px;
      background: #fffbeb;
      border: 1px solid #fef3c7;
      font-size: 12px;
      color: #92400e;
      line-height: 1.4;
      margin-top: 2px;
    }

    /* ── Role Badges ── */
    .role-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 3px 8px;
      border-radius: 6px;
      font-size: 11.5px;
      font-weight: 600;
      width: fit-content;
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

    /* ── Access Item List ── */
    .empty-list-note {
      text-align: center;
      font-size: 12px;
      color: #94a3b8;
      padding: 12px 0;
    }

    .access-list {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: 6px;
    }

    .access-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 8px 10px;
      background: #f8fafc;
      border: 1px solid #f1f5f9;
      border-radius: 8px;
    }

    .access-item-main {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 0;
      flex: 1;
    }

    .perm-type-icon {
      flex-shrink: 0;
    }

    .doc-icon {
      color: #64748b;
    }

    .folder-icon {
      color: #d97706;
    }

    .access-item-details {
      min-width: 0;
      flex: 1;
    }

    .access-item-title {
      font-size: 12px;
      font-weight: 600;
      color: #1e293b;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .access-item-sub {
      font-size: 10.5px;
      color: #64748b;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    }

    .team-role-badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 7px;
      border-radius: 6px;
      font-size: 10.5px;
      font-weight: 600;
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
      flex-shrink: 0;
    }

    .team-role-badge.is-manager {
      background: #f0fdf4;
      color: #166534;
      border-color: #bbf7d0;
    }

    .perm-level-badge {
      display: inline-flex;
      align-items: center;
      padding: 2px 7px;
      border-radius: 6px;
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 0.03em;
      text-transform: uppercase;
      flex-shrink: 0;
    }

    .level-edit {
      background: #fef3c7;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    .level-download {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
    }

    .level-view {
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #cbd5e1;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `],
})
export class MemberDetailsDrawerComponent {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly authStore = inject(AuthStore);

  readonly isOpen = input<boolean>(false);
  readonly orgCode = input<string>('');
  readonly memberId = input<string | null>(null);
  readonly currentUserRole = input<string | null>(null);
  readonly activeAdminCount = input<number>(1);

  readonly closeDrawer = output<void>();
  readonly changeRoleClicked = output<OrganizationMemberDetailResponseDto>();
  readonly suspendClicked = output<OrganizationMemberDetailResponseDto>();
  readonly reactivateClicked = output<OrganizationMemberDetailResponseDto>();
  readonly removeClicked = output<OrganizationMemberDetailResponseDto>();
  readonly transferAdminClicked = output<OrganizationMemberDetailResponseDto>();

  readonly member = signal<OrganizationMemberDetailResponseDto | null>(null);
  readonly accessSummary = signal<MemberAccessSummaryResponseDto | null>(null);
  readonly isLoading = signal<boolean>(false);
  readonly error = signal<string | null>(null);

  readonly teams = computed<TeamSummaryDto[]>(() => {
    return this.accessSummary()?.teams || [];
  });

  readonly directPermissions = computed<DirectPermissionSummaryDto[]>(() => {
    return this.accessSummary()?.directPermissions || [];
  });

  constructor() {
    effect(
      () => {
        const open = this.isOpen();
        const memId = this.memberId();
        const code = this.orgCode();

        if (open && memId && code) {
          this.loadData();
        } else if (!open) {
          this.member.set(null);
          this.accessSummary.set(null);
          this.error.set(null);
        }
      },
      { allowSignalWrites: true }
    );
  }

  loadData(): void {
    const code = this.orgCode();
    const memId = this.memberId();

    if (!code || !memId) return;

    this.isLoading.set(true);
    this.error.set(null);

    forkJoin({
      member: this.orgApiService.getSingleMemberDetail(code, memId).pipe(
        catchError((err) => {
          console.error('Failed to load member detail', err);
          return of(null);
        })
      ),
      access: this.orgApiService.getMemberAccessSummary(code, memId).pipe(
        catchError((err) => {
          console.error('Failed to load member access summary', err);
          return of(null);
        })
      ),
    }).subscribe({
      next: ({ member, access }) => {
        this.isLoading.set(false);
        if (!member) {
          this.error.set('Member information could not be retrieved from the server.');
          return;
        }
        this.member.set(member);
        this.accessSummary.set(access);
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = extractErrorMessage(
          err,
          'Failed to load member details. Please try again.'
        );
        this.error.set(msg);
      },
    });
  }

  close(): void {
    this.closeDrawer.emit();
  }

  isSelfMember(): boolean {
    const me = this.authStore.currentUser()?.id;
    const mem = this.member();
    return !!me && !!mem && mem.userId === me;
  }

  isSoleAdmin(m: OrganizationMemberDetailResponseDto): boolean {
    const role = (m.role || '').toUpperCase();
    const isAdmin = role.includes('ADMIN');
    return isAdmin && m.status === 'ACTIVE' && this.activeAdminCount() <= 1;
  }

  isActorAdmin(): boolean {
    const r = (this.currentUserRole() || '').toUpperCase();
    return r.includes('ADMIN');
  }

  isActorManager(): boolean {
    const r = (this.currentUserRole() || '').toUpperCase();
    return r.includes('MANAGER');
  }

  canChangeRole(m: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelfMember()) return false;
    const targetIsAdmin = this.isRoleAdmin(m.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    if (this.isActorAdmin() && targetIsAdmin && this.isSoleAdmin(m)) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canSuspend(m: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelfMember()) return false;
    if (m.status !== 'ACTIVE') return false;
    const targetIsAdmin = this.isRoleAdmin(m.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    if (this.isActorAdmin() && targetIsAdmin && this.isSoleAdmin(m)) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canReactivate(m: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelfMember()) return false;
    if (m.status !== 'SUSPENDED') return false;
    const targetIsAdmin = this.isRoleAdmin(m.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canRemove(m: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelfMember()) return false;
    const targetIsAdmin = this.isRoleAdmin(m.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    if (this.isActorAdmin() && targetIsAdmin && this.isSoleAdmin(m)) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canTransferAdmin(m: OrganizationMemberDetailResponseDto): boolean {
    if (!this.isActorAdmin()) return false;
    if (this.isSelfMember()) return false;
    if (m.status !== 'ACTIVE') return false;
    if (this.isRoleAdmin(m.role)) return false;
    return true;
  }

  getDisplayName(m: OrganizationMemberDetailResponseDto): string {
    const fn = m.firstName?.trim() || '';
    const ln = m.lastName?.trim() || '';
    if (fn || ln) return `${fn} ${ln}`.trim();
    return m.email;
  }

  getInitials(m: OrganizationMemberDetailResponseDto): string {
    const fn = m.firstName?.trim() || '';
    const ln = m.lastName?.trim() || '';
    if (fn && ln) return `${fn.charAt(0)}${ln.charAt(0)}`.toUpperCase();
    if (fn) return fn.slice(0, 2).toUpperCase();
    if (m.email) return m.email.slice(0, 2).toUpperCase();
    return 'U';
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

  formatStatus(status: string): string {
    if (!status) return 'Active';
    return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'status-active';
      case 'SUSPENDED':
        return 'status-suspended';
      case 'REMOVED':
        return 'status-removed';
      default:
        return 'status-active';
    }
  }

  getStatusDotClass(status: string): string {
    switch (status) {
      case 'ACTIVE':
        return 'dot-active';
      case 'SUSPENDED':
        return 'dot-suspended';
      case 'REMOVED':
        return 'dot-removed';
      default:
        return 'dot-active';
    }
  }
}
