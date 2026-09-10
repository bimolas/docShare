import {
  Component,
  ChangeDetectionStrategy,
  Input,
  Output,
  EventEmitter,
  inject,
  signal,
  computed,
  HostListener,
  OnChanges,
  SimpleChanges,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import {
  LucideUserPlus,
  LucideX,
  LucideSearch,
  LucideCheck,
  LucideShield,
  LucideAlertCircle,
  LucideUserCheck,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { OrganizationMemberDetailResponseDto } from '../../../../core/models/organization.model';

/**
 * Add Team Member Modal Component.
 * Allows searching/selecting existing active organization members not already in the team
 * and adding one or multiple members with a designated initial team role (MEMBER / MANAGER).
 */
@Component({
  selector: 'sd-add-team-member-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideUserPlus,
    LucideX,
    LucideSearch,
    LucideCheck,
    LucideShield,
    LucideAlertCircle,
    LucideUserCheck,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen) {
      <div class="modal-backdrop" (click)="onClose()">
        <div
          class="modal-card"
          (click)="$event.stopPropagation()"
          role="dialog"
          aria-labelledby="add-modal-title"
          aria-modal="true"
        >
          <!-- Header -->
          <div class="modal-header">
            <div class="modal-header-left">
              <div class="icon-badge">
                <svg lucideUserPlus class="header-lucide-icon"></svg>
              </div>
              <div>
                <h2 id="add-modal-title" class="modal-title">
                  Add Members to {{ teamName || 'Team' }}
                </h2>
                <p class="modal-subtitle">
                  Select active organization members to join this team.
                </p>
              </div>
            </div>
            <button
              type="button"
              class="close-btn"
              (click)="onClose()"
              aria-label="Close modal"
              [disabled]="isLoading()"
            >
              <svg lucideX class="close-icon"></svg>
            </button>
          </div>

          <!-- Error Alert Banner -->
          @if (errorMessage()) {
            <div class="error-alert">
              <svg lucideAlertCircle class="error-icon"></svg>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <!-- Body Content -->
          <div class="modal-body">
            <!-- Search & Controls Bar -->
            <div class="search-section">
              <div class="search-input-wrapper">
                <svg lucideSearch class="search-icon"></svg>
                <input
                  type="text"
                  class="search-input"
                  placeholder="Search org members by name or email..."
                  [ngModel]="searchQuery()"
                  (ngModelChange)="searchQuery.set($event)"
                  [disabled]="isFetchingOrgMembers() || isLoading()"
                />
                @if (searchQuery()) {
                  <button
                    type="button"
                    class="clear-search-btn"
                    (click)="searchQuery.set('')"
                  >
                    <svg lucideX class="clear-icon"></svg>
                  </button>
                }
              </div>

              <!-- Default Role Selection for Additions -->
              <div class="role-selector-bar">
                <span class="role-selector-label">Assign Initial Role:</span>
                <div class="role-pills">
                  <button
                    type="button"
                    class="role-pill"
                    [class.active]="selectedRole() === 'MEMBER'"
                    (click)="selectedRole.set('MEMBER')"
                    [disabled]="isLoading()"
                  >
                    Member
                  </button>
                  <button
                    type="button"
                    class="role-pill"
                    [class.active]="selectedRole() === 'MANAGER'"
                    (click)="selectedRole.set('MANAGER')"
                    [disabled]="isLoading()"
                  >
                    <svg lucideShield class="pill-icon"></svg>
                    Manager
                  </button>
                </div>
              </div>
            </div>

            <!-- Member Selection List -->
            <div class="members-container">
              @if (isFetchingOrgMembers()) {
                <div class="loading-state">
                  <span class="spinner"></span>
                  <p>Loading organization members...</p>
                </div>
              } @else if (availableMembers().length === 0) {
                <div class="empty-state">
                  <svg lucideUserCheck class="empty-icon"></svg>
                  <p class="empty-title">All members are already in this team</p>
                  <p class="empty-sub">
                    Every active organization member has already been added to {{ teamName }}.
                  </p>
                </div>
              } @else if (filteredMembers().length === 0) {
                <div class="empty-state">
                  <svg lucideSearch class="empty-icon"></svg>
                  <p class="empty-title">No matching members found</p>
                  <p class="empty-sub">
                    No available organization members match "{{ searchQuery() }}".
                  </p>
                </div>
              } @else {
                <!-- Select All Bar -->
                <div class="list-controls-bar">
                  <button
                    type="button"
                    class="btn-text-action"
                    (click)="toggleSelectAll()"
                    [disabled]="isLoading()"
                  >
                    {{ isAllSelected() ? 'Deselect All' : 'Select All Filtered' }}
                  </button>
                  <span class="selection-count">
                    {{ selectedUserIds().size }} of {{ availableMembers().length }} selected
                  </span>
                </div>

                <div class="members-list">
                  @for (member of filteredMembers(); track member.userId) {
                    @let isSelected = selectedUserIds().has(member.userId);
                    <div
                      class="member-item-row"
                      [class.is-selected]="isSelected"
                      (click)="toggleMemberSelection(member.userId)"
                    >
                      <div class="checkbox-wrapper">
                        <div class="custom-checkbox" [class.checked]="isSelected">
                          @if (isSelected) {
                            <svg lucideCheck class="check-icon"></svg>
                          }
                        </div>
                      </div>

                      <div class="user-avatar">
                        {{ getInitials(member.firstName, member.lastName, member.email) }}
                      </div>

                      <div class="user-info">
                        <span class="user-name">{{ getDisplayName(member) }}</span>
                        <span class="user-email">{{ member.email }}</span>
                      </div>

                      <div class="user-org-role">
                        <span class="org-role-badge">
                          {{ formatOrgRole(member.role) }}
                        </span>
                      </div>
                    </div>
                  }
                </div>
              }
            </div>
          </div>

          <!-- Footer -->
          <div class="modal-footer">
            <button
              type="button"
              class="btn btn-secondary"
              (click)="onClose()"
              [disabled]="isLoading()"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn btn-primary"
              (click)="onAddMembers()"
              [disabled]="isLoading() || selectedUserIds().size === 0"
            >
              @if (isLoading()) {
                <span class="spinner"></span>
                <span>Adding Members...</span>
              } @else {
                <svg lucideUserPlus class="btn-icon"></svg>
                <span>
                  Add {{ selectedUserIds().size > 0 ? selectedUserIds().size : '' }}
                  {{ selectedUserIds().size === 1 ? 'Member' : 'Members' }}
                </span>
              }
            </button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .modal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(9, 9, 11, 0.45);
        backdrop-filter: blur(4px);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1200;
        padding: 1rem;
        animation: backdropFade 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes backdropFade {
        from {
          opacity: 0;
        }
        to {
          opacity: 1;
        }
      }

      .modal-card {
        background: #ffffff;
        border: 1px solid #e4e4e7;
        border-radius: 14px;
        width: 100%;
        max-width: 520px;
        max-height: 85vh;
        display: flex;
        flex-direction: column;
        box-shadow:
          0 20px 25px -5px rgba(0, 0, 0, 0.1),
          0 10px 10px -5px rgba(0, 0, 0, 0.04);
        overflow: hidden;
        animation: modalSlideUp 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      }

      @keyframes modalSlideUp {
        from {
          opacity: 0;
          transform: translateY(8px) scale(0.98);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }

      .modal-header {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        padding: 1.25rem 1.25rem 1rem;
        border-bottom: 1px solid #e4e4e7;
        flex-shrink: 0;
      }

      .modal-header-left {
        display: flex;
        align-items: flex-start;
        gap: 0.875rem;
      }

      .icon-badge {
        width: 42px;
        height: 42px;
        border-radius: 10px;
        background: #fff7ed;
        color: #ea580c;
        border: 1px solid #fed7aa;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      .header-lucide-icon {
        width: 20px;
        height: 20px;
      }

      .modal-title {
        font-size: 1.1rem;
        font-weight: 600;
        color: #09090b;
        margin: 0;
        line-height: 1.3;
        letter-spacing: -0.02em;
      }

      .modal-subtitle {
        font-size: 0.8125rem;
        color: #71717a;
        margin: 0.25rem 0 0;
        line-height: 1.4;
      }

      .close-btn {
        background: transparent;
        border: none;
        color: #a1a1aa;
        padding: 0.35rem;
        border-radius: 6px;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.15s ease;

        &:hover:not(:disabled) {
          background: #f4f4f5;
          color: #09090b;
        }

        &:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
      }

      .close-icon {
        width: 18px;
        height: 18px;
      }

      .error-alert {
        margin: 1rem 1.25rem 0;
        padding: 0.65rem 0.85rem;
        background: #fef2f2;
        border: 1px solid #fecaca;
        border-radius: 8px;
        color: #991b1b;
        font-size: 0.8125rem;
        display: flex;
        align-items: center;
        gap: 0.5rem;
        flex-shrink: 0;
      }

      .error-icon {
        width: 16px;
        height: 16px;
        color: #ef4444;
        flex-shrink: 0;
      }

      .modal-body {
        padding: 1.25rem;
        display: flex;
        flex-direction: column;
        gap: 1rem;
        overflow: hidden;
        flex: 1;
      }

      .search-section {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        flex-shrink: 0;
      }

      .search-input-wrapper {
        position: relative;
        display: flex;
        align-items: center;
      }

      .search-icon {
        position: absolute;
        left: 0.75rem;
        width: 16px;
        height: 16px;
        color: #9ca3af;
        pointer-events: none;
      }

      .search-input {
        width: 100%;
        padding: 0.55rem 2.25rem 0.55rem 2.25rem;
        font-size: 0.875rem;
        color: #09090b;
        background: #ffffff;
        border: 1px solid #d1d5db;
        border-radius: 8px;
        outline: none;

        &:focus {
          border-color: #f97316;
          box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
        }

        &::placeholder {
          color: #9ca3af;
        }
      }

      .clear-search-btn {
        position: absolute;
        right: 0.65rem;
        background: transparent;
        border: none;
        color: #9ca3af;
        padding: 0.2rem;
        border-radius: 4px;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;

        &:hover {
          color: #09090b;
          background: #f4f4f5;
        }
      }

      .clear-icon {
        width: 14px;
        height: 14px;
      }

      .role-selector-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.6rem 0.85rem;
        background: #fafafa;
        border: 1px solid #e4e4e7;
        border-radius: 8px;
      }

      .role-selector-label {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #374151;
      }

      .role-pills {
        display: flex;
        align-items: center;
        gap: 0.35rem;
        background: #e4e4e7;
        padding: 0.1875rem;
        border-radius: 7px;
      }

      .role-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        padding: 0.25rem 0.65rem;
        font-size: 0.75rem;
        font-weight: 500;
        border: none;
        background: transparent;
        color: #52525b;
        border-radius: 5px;
        cursor: pointer;
        transition: all 0.15s ease;

        &.active {
          background: #ffffff;
          color: #09090b;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.08);
          font-weight: 600;
        }

        .pill-icon {
          width: 12px;
          height: 12px;
          color: #ea580c;
        }
      }

      .members-container {
        flex: 1;
        overflow-y: auto;
        border: 1px solid #e4e4e7;
        border-radius: 10px;
        background: #ffffff;
        min-height: 200px;
      }

      .list-controls-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.5rem 0.85rem;
        background: #f8fafc;
        border-bottom: 1px solid #e2e8f0;
        font-size: 0.75rem;
      }

      .btn-text-action {
        background: transparent;
        border: none;
        color: #ea580c;
        font-weight: 600;
        font-size: 0.75rem;
        cursor: pointer;
        padding: 0;

        &:hover:not(:disabled) {
          text-decoration: underline;
        }
      }

      .selection-count {
        color: #64748b;
      }

      .members-list {
        display: flex;
        flex-direction: column;
      }

      .member-item-row {
        display: grid;
        grid-template-columns: 24px 32px 1fr auto;
        align-items: center;
        gap: 0.75rem;
        padding: 0.65rem 0.85rem;
        border-bottom: 1px solid #f4f4f5;
        cursor: pointer;
        transition: background 0.15s ease;

        &:last-child {
          border-bottom: none;
        }

        &:hover {
          background: #fafafa;
        }

        &.is-selected {
          background: #fff7ed;
        }
      }

      .checkbox-wrapper {
        display: flex;
        align-items: center;
        justify-content: center;
      }

      .custom-checkbox {
        width: 18px;
        height: 18px;
        border: 1px solid #d1d5db;
        border-radius: 4px;
        background: #ffffff;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.15s ease;

        &.checked {
          background: #ea580c;
          border-color: #ea580c;
          color: #ffffff;
        }
      }

      .check-icon {
        width: 13px;
        height: 13px;
        stroke-width: 3;
      }

      .user-avatar {
        width: 32px;
        height: 32px;
        border-radius: 50%;
        background: #f4f4f5;
        border: 1px solid #e4e4e7;
        color: #3f3f46;
        font-size: 0.75rem;
        font-weight: 600;
        display: flex;
        align-items: center;
        justify-content: center;
        text-transform: uppercase;
      }

      .user-info {
        display: flex;
        flex-direction: column;
        gap: 0.1rem;
        min-width: 0;
      }

      .user-name {
        font-size: 0.8438rem;
        font-weight: 500;
        color: #09090b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .user-email {
        font-size: 0.75rem;
        color: #71717a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .org-role-badge {
        font-size: 0.6875rem;
        font-weight: 500;
        padding: 0.15rem 0.45rem;
        background: #f1f5f9;
        color: #475569;
        border: 1px solid #e2e8f0;
        border-radius: 5px;
      }

      .loading-state,
      .empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 2.5rem 1.5rem;
        text-align: center;
        color: #71717a;

        p {
          font-size: 0.8125rem;
          margin: 0;
        }

        .empty-title {
          font-weight: 600;
          color: #09090b;
          margin-top: 0.5rem;
        }

        .empty-sub {
          color: #71717a;
          margin-top: 0.25rem;
        }
      }

      .empty-icon {
        width: 32px;
        height: 32px;
        color: #a1a1aa;
      }

      .modal-footer {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.65rem;
        padding: 1rem 1.25rem;
        border-top: 1px solid #e4e4e7;
        flex-shrink: 0;
      }

      .btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.4rem;
        padding: 0.5rem 1rem;
        font-size: 0.8125rem;
        font-weight: 500;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
        border: 1px solid transparent;
        outline: none;

        &:active:not(:disabled) {
          transform: scale(0.96);
        }

        &:disabled {
          opacity: 0.6;
          cursor: not-allowed;
        }
      }

      .btn-secondary {
        background: #ffffff;
        border-color: #d1d5db;
        color: #374151;

        &:hover:not(:disabled) {
          background: #f4f4f5;
          border-color: #9ca3af;
        }
      }

      .btn-primary {
        background: #f97316;
        color: #ffffff;
        box-shadow: 0 1px 2px rgba(249, 115, 22, 0.2);

        &:hover:not(:disabled) {
          background: #ea580c;
        }
      }

      .btn-icon {
        width: 15px;
        height: 15px;
      }

      .spinner {
        width: 14px;
        height: 14px;
        border: 2px solid rgba(255, 255, 255, 0.35);
        border-top-color: #ffffff;
        border-radius: 50%;
        animation: spin 0.6s linear infinite;
      }

      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }
    `,
  ],
})
export class AddTeamMemberModalComponent implements OnChanges {
  private readonly orgApiService = inject(OrganizationApiService);

  @Input() isOpen = false;
  @Input() orgCode = '';
  @Input() teamId = '';
  @Input() teamName = '';
  @Input() existingMemberUserIds: string[] = [];

  @Output() closed = new EventEmitter<void>();
  @Output() membersAdded = new EventEmitter<void>();

  // State Signals
  readonly allOrgMembers = signal<OrganizationMemberDetailResponseDto[]>([]);
  readonly searchQuery = signal<string>('');
  readonly selectedUserIds = signal<Set<string>>(new Set());
  readonly selectedRole = signal<'MEMBER' | 'MANAGER'>('MEMBER');
  readonly isFetchingOrgMembers = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  // Available Org Members (Active org members NOT currently in the team)
  readonly availableMembers = computed(() => {
    const existingSet = new Set(this.existingMemberUserIds);
    return this.allOrgMembers().filter(
      (m) => m.status === 'ACTIVE' && !existingSet.has(m.userId)
    );
  });

  // Filtered available members based on search query
  readonly filteredMembers = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const available = this.availableMembers();
    if (!query) return available;

    return available.filter((m) => {
      const name = `${m.firstName || ''} ${m.lastName || ''}`.toLowerCase();
      const email = (m.email || '').toLowerCase();
      return name.includes(query) || email.includes(query);
    });
  });

  readonly isAllSelected = computed(() => {
    const filtered = this.filteredMembers();
    if (filtered.length === 0) return false;
    const selected = this.selectedUserIds();
    return filtered.every((m) => selected.has(m.userId));
  });

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen'] && this.isOpen) {
      this.resetModalState();
      this.fetchOrgMembers();
    }
  }

  fetchOrgMembers(): void {
    if (!this.orgCode) return;

    this.isFetchingOrgMembers.set(true);
    this.errorMessage.set(null);

    this.orgApiService.getMemberDetails(this.orgCode, 'ACTIVE').subscribe({
      next: (members: OrganizationMemberDetailResponseDto[]) => {
        this.allOrgMembers.set(members);
        this.isFetchingOrgMembers.set(false);
      },
      error: (err: unknown) => {
        console.error('Failed to fetch org members for team addition', err);
        this.errorMessage.set('Failed to load organization members.');
        this.isFetchingOrgMembers.set(false);
      },
    });
  }

  toggleMemberSelection(userId: string): void {
    if (this.isLoading()) return;
    const current = new Set(this.selectedUserIds());
    if (current.has(userId)) {
      current.delete(userId);
    } else {
      current.add(userId);
    }
    this.selectedUserIds.set(current);
  }

  toggleSelectAll(): void {
    if (this.isLoading()) return;
    const filtered = this.filteredMembers();
    const current = new Set(this.selectedUserIds());

    if (this.isAllSelected()) {
      filtered.forEach((m) => current.delete(m.userId));
    } else {
      filtered.forEach((m) => current.add(m.userId));
    }

    this.selectedUserIds.set(current);
  }

  onAddMembers(): void {
    const userIds = Array.from(this.selectedUserIds());
    if (userIds.length === 0 || this.isLoading() || !this.orgCode || !this.teamId) return;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const role = this.selectedRole();

    // Parallel multi-add using forkJoin
    const addObservables = userIds.map((userId) =>
      this.orgApiService
        .addGroupMember(this.orgCode, Number(this.teamId), userId, role)
        .pipe(
          catchError((err) => {
            console.error(`Failed to add user ${userId} to team`, err);
            return of({ error: true, userId, err });
          })
        )
    );

    forkJoin(addObservables).subscribe({
      next: (results) => {
        this.isLoading.set(false);
        const failures = results.filter((res) => (res as any)?.error);

        if (failures.length === 0) {
          this.membersAdded.emit();
          this.onClose();
        } else if (failures.length === results.length) {
          this.errorMessage.set('Failed to add selected members to team.');
        } else {
          // Partial success
          this.errorMessage.set(
            `Added ${results.length - failures.length} members. ${failures.length} member(s) could not be added.`
          );
          // Emit membersAdded to refresh the roster for the successful ones
          this.membersAdded.emit();
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set('An unexpected error occurred while adding members.');
      },
    });
  }

  onClose(): void {
    if (this.isLoading()) return;
    this.resetModalState();
    this.closed.emit();
  }

  private resetModalState(): void {
    this.searchQuery.set('');
    this.selectedUserIds.set(new Set());
    this.selectedRole.set('MEMBER');
    this.errorMessage.set(null);
    this.isLoading.set(false);
  }

  getDisplayName(member: OrganizationMemberDetailResponseDto): string {
    if (member.firstName || member.lastName) {
      return `${member.firstName || ''} ${member.lastName || ''}`.trim();
    }
    return member.email;
  }

  getInitials(firstName?: string, lastName?: string, email?: string): string {
    if (firstName && lastName) {
      return `${firstName.charAt(0)}${lastName.charAt(0)}`;
    }
    if (firstName) {
      return firstName.charAt(0);
    }
    if (email) {
      return email.charAt(0);
    }
    return 'U';
  }

  formatOrgRole(role?: string): string {
    if (!role) return 'Member';
    switch (role) {
      case 'ROLE_ORG_ADMIN':
        return 'Org Admin';
      case 'ROLE_ORG_MANAGER':
        return 'Org Manager';
      case 'ROLE_USER':
        return 'Member';
      default:
        return role.replace(/^ROLE_/, '').replace(/_/g, ' ');
    }
  }

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen && !this.isLoading()) {
      this.onClose();
    }
  }
}
