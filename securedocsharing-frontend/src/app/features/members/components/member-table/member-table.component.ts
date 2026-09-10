import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import {
  LucideArrowUpDown,
  LucideArrowUp,
  LucideArrowDown,
} from '@lucide/angular';
import { IconComponent } from '../../../../shared/components/icon/icon.component';
import { DropdownComponent } from '../../../../shared/ui/dropdown/dropdown.component';
import { OrganizationMemberDetailResponseDto } from '../../../../core/models/organization.model';

type SortField = 'name' | 'role' | 'status' | 'joinedAt';
type SortDirection = 'asc' | 'desc';

@Component({
  selector: 'app-member-table',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    LucideArrowUpDown,
    LucideArrowUp,
    LucideArrowDown,
    IconComponent,
    DropdownComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="table-scroll-wrapper">
      <table class="members-table" role="table" aria-label="Organization Members Roster">
        <thead>
          <tr>
            <th scope="col" class="th-member">
              <button
                type="button"
                class="th-sort-btn"
                (click)="toggleSort('name')"
              >
                <span>Member</span>
                @if (sortField() === 'name') {
                  @if (sortDirection() === 'asc') {
                    <svg lucideArrowUp class="sort-icon-active"></svg>
                  } @else {
                    <svg lucideArrowDown class="sort-icon-active"></svg>
                  }
                } @else {
                  <svg lucideArrowUpDown class="sort-icon-idle"></svg>
                }
              </button>
            </th>
            <th scope="col" class="th-role">
              <button
                type="button"
                class="th-sort-btn"
                (click)="toggleSort('role')"
              >
                <span>Organization Role</span>
                @if (sortField() === 'role') {
                  @if (sortDirection() === 'asc') {
                    <svg lucideArrowUp class="sort-icon-active"></svg>
                  } @else {
                    <svg lucideArrowDown class="sort-icon-active"></svg>
                  }
                } @else {
                  <svg lucideArrowUpDown class="sort-icon-idle"></svg>
                }
              </button>
            </th>
            <th scope="col" class="th-status">
              <button
                type="button"
                class="th-sort-btn"
                (click)="toggleSort('status')"
              >
                <span>Status</span>
                @if (sortField() === 'status') {
                  @if (sortDirection() === 'asc') {
                    <svg lucideArrowUp class="sort-icon-active"></svg>
                  } @else {
                    <svg lucideArrowDown class="sort-icon-active"></svg>
                  }
                } @else {
                  <svg lucideArrowUpDown class="sort-icon-idle"></svg>
                }
              </button>
            </th>
            <th scope="col" class="th-joined">
              <button
                type="button"
                class="th-sort-btn"
                (click)="toggleSort('joinedAt')"
              >
                <span>Joined Date</span>
                @if (sortField() === 'joinedAt') {
                  @if (sortDirection() === 'asc') {
                    <svg lucideArrowUp class="sort-icon-active"></svg>
                  } @else {
                    <svg lucideArrowDown class="sort-icon-active"></svg>
                  }
                } @else {
                  <svg lucideArrowUpDown class="sort-icon-idle"></svg>
                }
              </button>
            </th>
            <th scope="col" class="th-actions">Actions</th>
          </tr>
        </thead>
        <tbody>
          @for (member of sortedMembers(); track member.userId || member.email) {
            <tr
              class="table-row"
              [class.row-suspended]="member.status === 'SUSPENDED'"
              (click)="onRowClick(member)"
              tabindex="0"
              role="row"
              (keydown.enter)="onRowClick(member)"
            >
              <!-- Member Identity -->
              <td>
                <div class="member-identity-cell">
                  <div class="avatar-circle">
                    {{ getInitials(member) }}
                  </div>
                  <div class="member-info">
                    <div class="member-name-row">
                      <span class="member-name">{{ getDisplayName(member) }}</span>
                      @if (isSelf(member)) {
                        <span class="self-tag">(You)</span>
                      }
                      @if (isSoleAdmin(member)) {
                        <span class="sole-admin-shield" title="Sole active administrator">
                          <app-icon name="shield-check" [size]="14" />
                        </span>
                      }
                    </div>
                    <div class="member-email">{{ member.email }}</div>
                  </div>
                </div>
              </td>

              <!-- Organization Role -->
              <td>
                <div class="role-cell">
                  @if (isRoleAdmin(member.role)) {
                    <span class="role-badge role-admin">
                      <app-icon name="shield" [size]="12" />
                      <span>{{ formatRole(member.role) }}</span>
                    </span>
                  } @else if (isRoleManager(member.role)) {
                    <span class="role-badge role-manager">
                      <app-icon name="user" [size]="12" />
                      <span>{{ formatRole(member.role) }}</span>
                    </span>
                  } @else {
                    <span class="role-badge role-member">
                      <span>{{ formatRole(member.role) }}</span>
                    </span>
                  }
                </div>
              </td>

              <!-- Status -->
              <td>
                <div class="status-cell">
                  <span class="status-dot" [class]="getStatusDotClass(member.status)"></span>
                  <span class="status-label" [class]="getStatusLabelClass(member.status)">
                    {{ formatStatus(member.status) }}
                  </span>
                  @if (member.statusReason) {
                    <span
                      class="status-reason-icon"
                      [title]="'Reason: ' + member.statusReason"
                    >
                      <app-icon name="info" [size]="13" />
                    </span>
                  }
                </div>
              </td>

              <!-- Joined Date -->
              <td>
                <span class="date-cell">
                  {{ member.joinedAt | date: 'MMM d, yyyy' }}
                </span>
              </td>

              <!-- Quick Action Buttons -->
              <td class="td-actions" (click)="$event.stopPropagation()">
                <div class="actions-wrapper">
                  <button
                    type="button"
                    class="action-btn-details"
                    title="View Member Details"
                    aria-label="View Member Details"
                    (click)="onRowClick(member)"
                  >
                    <app-icon name="info" [size]="13" />
                    <span>Details</span>
                  </button>

                  <sd-dropdown placement="bottom-end">
                    <button
                      dropdown-trigger
                      type="button"
                      class="action-btn-more"
                      title="Governance Actions"
                      [attr.aria-label]="'Manage options for ' + getDisplayName(member)"
                    >
                      <app-icon name="more-vertical" [size]="15" />
                    </button>
                    <div dropdown-menu class="dropdown-action-menu">
                      <button
                        type="button"
                        class="dropdown-menu-item"
                        (click)="onRowClick(member)"
                      >
                        <app-icon name="eye" [size]="14" class="menu-icon" />
                        <span>View Details</span>
                      </button>

                      @if (canChangeRole(member)) {
                        <button
                          type="button"
                          class="dropdown-menu-item"
                          (click)="changeRole.emit(member)"
                        >
                          <app-icon name="user" [size]="14" class="menu-icon" />
                          <span>Change Role</span>
                        </button>
                      }

                      @if (canSuspend(member)) {
                        <button
                          type="button"
                          class="dropdown-menu-item item-warning"
                          (click)="suspendMember.emit(member)"
                        >
                          <app-icon name="alert-circle" [size]="14" class="menu-icon" />
                          <span>Suspend Member</span>
                        </button>
                      }

                      @if (canReactivate(member)) {
                        <button
                          type="button"
                          class="dropdown-menu-item item-success"
                          (click)="reactivateMember.emit(member)"
                        >
                          <app-icon name="check" [size]="14" class="menu-icon" />
                          <span>Reactivate Member</span>
                        </button>
                      }

                      @if (canTransferAdmin(member)) {
                        <button
                          type="button"
                          class="dropdown-menu-item item-warning"
                          (click)="transferAdmin.emit(member)"
                        >
                          <app-icon name="brand-shield" [size]="14" class="menu-icon" />
                          <span>Transfer Admin</span>
                        </button>
                      }

                      @if (canRemove(member)) {
                        <div class="dropdown-menu-divider"></div>
                        <button
                          type="button"
                          class="dropdown-menu-item item-danger"
                          (click)="removeMember.emit(member)"
                        >
                          <app-icon name="trash-2" [size]="14" class="menu-icon" />
                          <span>Remove Member</span>
                        </button>
                      }
                    </div>
                  </sd-dropdown>
                </div>
              </td>
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .table-scroll-wrapper {
      overflow-x: auto;
      width: 100%;
    }

    .members-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }

    .members-table thead th {
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

    .th-member { width: 34%; }
    .th-role { width: 20%; }
    .th-status { width: 18%; }
    .th-joined { width: 16%; }
    .th-actions { width: 12%; text-align: right; }

    .th-sort-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: transparent;
      border: none;
      padding: 0;
      font: inherit;
      color: inherit;
      text-transform: inherit;
      letter-spacing: inherit;
      cursor: pointer;
      transition: color 0.12s ease;
    }

    .th-sort-btn:hover {
      color: #0f172a;
    }

    .sort-icon-active {
      width: 13px;
      height: 13px;
      color: var(--sd-color-primary, #f97316);
    }

    .sort-icon-idle {
      width: 13px;
      height: 13px;
      opacity: 0;
      transition: opacity 0.15s ease;
    }

    .th-sort-btn:hover .sort-icon-idle {
      opacity: 0.5;
    }

    /* ── Table Row ── */
    .table-row {
      border-bottom: 1px solid #f1f5f9;
      height: 52px;
      transition: background-color 0.12s ease;
      cursor: pointer;
    }

    .table-row:hover {
      background-color: #f8fafc;
    }

    .table-row.row-suspended {
      background-color: rgba(245, 158, 11, 0.03);
    }

    .table-row:focus-visible {
      outline: 2px solid var(--sd-color-primary, #f97316);
      outline-offset: -2px;
    }

    .table-row td {
      padding: 10px 16px;
      vertical-align: middle;
      color: #334155;
    }

    /* ── Member Identity Cell ── */
    .member-identity-cell {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 220px;
    }

    .avatar-circle {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #e2e8f0;
      font-size: 11px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      text-transform: uppercase;
      user-select: none;
    }

    .member-info {
      display: flex;
      flex-direction: column;
      gap: 1px;
      overflow: hidden;
    }

    .member-name-row {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .member-name {
      font-weight: 600;
      color: #0f172a;
      font-size: 13px;
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

    .member-email {
      font-size: 11.5px;
      color: #64748b;
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 240px;
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

    /* ── Status Cell ── */
    .status-cell {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 500;
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

    .status-label {
      color: #334155;
    }

    .status-label-suspended {
      color: #b45309;
    }

    .status-label-removed {
      color: #64748b;
    }

    .status-reason-icon {
      color: #64748b;
      cursor: help;
      display: inline-flex;
      align-items: center;
      transition: color 0.12s ease;
    }

    .status-reason-icon:hover {
      color: #0f172a;
    }

    /* ── Date Cell ── */
    .date-cell {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 11.5px;
      color: #64748b;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }

    /* ── Actions Cell ── */
    .td-actions {
      text-align: right;
    }

    .actions-wrapper {
      display: inline-flex;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
    }

    .action-btn-details {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 4px;
      padding: 5px 10px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 30px;
      border: 1px solid #e2e8f0;
      background: #ffffff;
      color: #475569;
      user-select: none;
    }

    .action-btn-details:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #cbd5e1;
    }

    .action-btn-more {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 30px;
      height: 30px;
      border-radius: 6px;
      cursor: pointer;
      transition: all 0.15s ease;
      border: 1px solid transparent;
      background: transparent;
      color: #64748b;
      user-select: none;
    }

    .action-btn-more:hover {
      background: #f1f5f9;
      color: #0f172a;
      border-color: #e2e8f0;
    }

    /* ── Dropdown Action Menu ── */
    .dropdown-action-menu {
      display: flex;
      flex-direction: column;
      gap: 2px;
      min-width: 175px;
    }

    .dropdown-menu-item {
      width: 100%;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 6px 10px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 500;
      color: #334155;
      background: transparent;
      border: none;
      cursor: pointer;
      text-align: left;
      transition: background-color 0.12s ease;
      user-select: none;
    }

    .dropdown-menu-item:hover {
      background-color: #f1f5f9;
      color: #0f172a;
    }

    .menu-icon {
      color: #64748b;
      flex-shrink: 0;
    }

    .dropdown-menu-item:hover .menu-icon {
      color: inherit;
    }

    .dropdown-menu-item.item-warning {
      color: #b45309;
    }

    .dropdown-menu-item.item-warning:hover {
      background-color: #fffbeb;
      color: #92400e;
    }

    .dropdown-menu-item.item-warning .menu-icon {
      color: #d97706;
    }

    .dropdown-menu-item.item-success {
      color: #047857;
    }

    .dropdown-menu-item.item-success:hover {
      background-color: #ecfdf5;
      color: #065f46;
    }

    .dropdown-menu-item.item-success .menu-icon {
      color: #059669;
    }

    .dropdown-menu-item.item-danger {
      color: #dc2626;
    }

    .dropdown-menu-item.item-danger:hover {
      background-color: #fef2f2;
      color: #b91c1c;
    }

    .dropdown-menu-item.item-danger .menu-icon {
      color: #dc2626;
    }

    .dropdown-menu-divider {
      height: 1px;
      background-color: #e2e8f0;
      margin: 4px 0;
    }
  `],
})
export class MemberTableComponent {
  readonly members = input<OrganizationMemberDetailResponseDto[]>([]);
  readonly currentUserId = input<string | null>(null);
  readonly currentUserRole = input<string | null>(null);
  readonly activeAdminCount = input<number>(1);

  readonly selectMember = output<OrganizationMemberDetailResponseDto>();
  readonly changeRole = output<OrganizationMemberDetailResponseDto>();
  readonly suspendMember = output<OrganizationMemberDetailResponseDto>();
  readonly reactivateMember = output<OrganizationMemberDetailResponseDto>();
  readonly removeMember = output<OrganizationMemberDetailResponseDto>();
  readonly transferAdmin = output<OrganizationMemberDetailResponseDto>();

  readonly sortField = signal<SortField>('name');
  readonly sortDirection = signal<SortDirection>('asc');

  readonly sortedMembers = computed(() => {
    const list = [...(this.members() || [])];
    const field = this.sortField();
    const dir = this.sortDirection() === 'asc' ? 1 : -1;

    return list.sort((a, b) => {
      if (field === 'name') {
        const nameA = this.getDisplayName(a).toLowerCase();
        const nameB = this.getDisplayName(b).toLowerCase();
        return nameA.localeCompare(nameB) * dir;
      }
      if (field === 'role') {
        return (a.role || '').localeCompare(b.role || '') * dir;
      }
      if (field === 'status') {
        return (a.status || '').localeCompare(b.status || '') * dir;
      }
      if (field === 'joinedAt') {
        const dateA = new Date(a.joinedAt).getTime();
        const dateB = new Date(b.joinedAt).getTime();
        return (dateA - dateB) * dir;
      }
      return 0;
    });
  });

  toggleSort(field: SortField): void {
    if (this.sortField() === field) {
      this.sortDirection.set(this.sortDirection() === 'asc' ? 'desc' : 'asc');
    } else {
      this.sortField.set(field);
      this.sortDirection.set('asc');
    }
  }

  onRowClick(member: OrganizationMemberDetailResponseDto): void {
    this.selectMember.emit(member);
  }

  isSelf(member: OrganizationMemberDetailResponseDto): boolean {
    const me = this.currentUserId();
    return !!me && member.userId === me;
  }

  isSoleAdmin(member: OrganizationMemberDetailResponseDto): boolean {
    const role = (member.role || '').toUpperCase();
    const isAdmin = role.includes('ADMIN');
    return isAdmin && member.status === 'ACTIVE' && this.activeAdminCount() <= 1;
  }

  isActorAdmin(): boolean {
    const r = (this.currentUserRole() || '').toUpperCase();
    return r.includes('ADMIN');
  }

  isActorManager(): boolean {
    const r = (this.currentUserRole() || '').toUpperCase();
    return r.includes('MANAGER');
  }

  canChangeRole(member: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelf(member)) return false;
    const targetIsAdmin = this.isRoleAdmin(member.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    if (this.isActorAdmin() && targetIsAdmin && this.isSoleAdmin(member)) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canSuspend(member: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelf(member)) return false;
    if (member.status !== 'ACTIVE') return false;
    const targetIsAdmin = this.isRoleAdmin(member.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    if (this.isActorAdmin() && targetIsAdmin && this.isSoleAdmin(member)) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canReactivate(member: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelf(member)) return false;
    if (member.status !== 'SUSPENDED') return false;
    const targetIsAdmin = this.isRoleAdmin(member.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canRemove(member: OrganizationMemberDetailResponseDto): boolean {
    if (this.isSelf(member)) return false;
    const targetIsAdmin = this.isRoleAdmin(member.role);
    if (this.isActorManager() && targetIsAdmin) return false;
    if (this.isActorAdmin() && targetIsAdmin && this.isSoleAdmin(member)) return false;
    return this.isActorAdmin() || this.isActorManager();
  }

  canTransferAdmin(member: OrganizationMemberDetailResponseDto): boolean {
    if (!this.isActorAdmin()) return false;
    if (this.isSelf(member)) return false;
    if (member.status !== 'ACTIVE') return false;
    if (this.isRoleAdmin(member.role)) return false;
    return true;
  }

  getDisplayName(member: OrganizationMemberDetailResponseDto): string {
    const fn = member.firstName?.trim() || '';
    const ln = member.lastName?.trim() || '';
    if (fn || ln) return `${fn} ${ln}`.trim();
    return member.email;
  }

  getInitials(member: OrganizationMemberDetailResponseDto): string {
    const fn = member.firstName?.trim() || '';
    const ln = member.lastName?.trim() || '';
    if (fn && ln) return `${fn.charAt(0)}${ln.charAt(0)}`.toUpperCase();
    if (fn) return fn.slice(0, 2).toUpperCase();
    if (member.email) return member.email.slice(0, 2).toUpperCase();
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

  getStatusLabelClass(status: string): string {
    switch (status) {
      case 'SUSPENDED':
        return 'status-label-suspended';
      case 'REMOVED':
        return 'status-label-removed';
      default:
        return '';
    }
  }
}
