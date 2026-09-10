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
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  LucideTrash2,
  LucideArchive,
  LucideArchiveRestore,
  LucideAlertTriangle,
  LucideX,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { GroupResponseDto } from '../../../../core/models/organization.model';

export type DeleteModalMode = 'delete' | 'archive' | 'unarchive';

/**
 * Delete / Archive / Unarchive Team Modal Component.
 * Provides explicit warnings and confirmations before removing or archiving a team group.
 * Styled in SecureDoc light-mode aesthetic with accessible contrast and warning indicators.
 */
@Component({
  selector: 'sd-delete-team-modal',
  standalone: true,
  imports: [
    CommonModule,
    LucideTrash2,
    LucideArchive,
    LucideArchiveRestore,
    LucideAlertTriangle,
    LucideX,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen) {
      <div class="modal-backdrop" (click)="onClose()">
        <div
          class="modal-card"
          (click)="$event.stopPropagation()"
          role="dialog"
          aria-labelledby="delete-modal-title"
          aria-modal="true"
        >
          <!-- Header -->
          <div class="modal-header">
            <div class="modal-header-left">
              <div
                class="icon-badge"
                [class.badge-danger]="mode() === 'delete'"
                [class.badge-warning]="mode() === 'archive'"
                [class.badge-info]="mode() === 'unarchive'"
              >
                @if (mode() === 'delete') {
                  <svg lucideTrash2 class="header-lucide-icon"></svg>
                } @else if (mode() === 'archive') {
                  <svg lucideArchive class="header-lucide-icon"></svg>
                } @else {
                  <svg lucideArchiveRestore class="header-lucide-icon"></svg>
                }
              </div>
              <div>
                <h2 id="delete-modal-title" class="modal-title">
                  {{ modalTitle() }}
                </h2>
                <p class="modal-subtitle">
                  Team: <strong class="team-highlight">{{ teamName() }}</strong>
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
              <svg lucideAlertTriangle class="alert-icon"></svg>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <!-- Body Description & Impact Warnings -->
          <div class="modal-body">
            @if (mode() === 'delete') {
              <div class="warning-box danger">
                <svg lucideAlertTriangle class="warning-icon"></svg>
                <div class="warning-text">
                  <p><strong>This action is permanent and cannot be undone.</strong></p>
                  <p>
                    Deleting this team will disassociate all team members and revoke any group-level access permissions granted to this team.
                  </p>
                </div>
              </div>
            } @else if (mode() === 'archive') {
              <div class="warning-box warning">
                <svg lucideAlertTriangle class="warning-icon"></svg>
                <div class="warning-text">
                  <p><strong>Archiving will deactivate this team.</strong></p>
                  <p>
                    Archived teams remain in organization history but members will lose active team features until unarchived.
                  </p>
                </div>
              </div>
            } @else {
              <div class="warning-box info">
                <svg lucideAlertTriangle class="warning-icon"></svg>
                <div class="warning-text">
                  <p><strong>Reactivate Team</strong></p>
                  <p>
                    Unarchiving this team will restore active status and reactivate team member access.
                  </p>
                </div>
              </div>
            }

            <!-- Confirmation Toggle or Info -->
            <div class="mode-switcher">
              <span class="switcher-label">Prefer to archive instead?</span>
              @if (mode() === 'delete') {
                <button
                  type="button"
                  class="btn-switch"
                  (click)="mode.set('archive')"
                  [disabled]="isLoading()"
                >
                  <svg lucideArchive class="switch-icon"></svg>
                  Switch to Archive
                </button>
              } @else if (mode() === 'archive') {
                <button
                  type="button"
                  class="btn-switch danger"
                  (click)="mode.set('delete')"
                  [disabled]="isLoading()"
                >
                  <svg lucideTrash2 class="switch-icon"></svg>
                  Switch to Permanent Delete
                </button>
              }
            </div>
          </div>

          <!-- Footer Actions -->
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
              class="btn"
              [class.btn-danger]="mode() === 'delete'"
              [class.btn-warning]="mode() === 'archive'"
              [class.btn-primary]="mode() === 'unarchive'"
              (click)="onConfirm()"
              [disabled]="isLoading()"
            >
              @if (isLoading()) {
                <span class="spinner"></span>
                <span>Processing...</span>
              } @else if (mode() === 'delete') {
                <svg lucideTrash2 class="btn-icon"></svg>
                <span>Delete Team</span>
              } @else if (mode() === 'archive') {
                <svg lucideArchive class="btn-icon"></svg>
                <span>Archive Team</span>
              } @else {
                <svg lucideArchiveRestore class="btn-icon"></svg>
                <span>Unarchive Team</span>
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
        z-index: 1000;
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
        max-width: 480px;
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
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;

        &.badge-danger {
          background: #fef2f2;
          color: #dc2626;
          border: 1px solid #fecaca;
        }

        &.badge-warning {
          background: #fffbe8;
          color: #d97706;
          border: 1px solid #fde68a;
        }

        &.badge-info {
          background: #eff6ff;
          color: #2563eb;
          border: 1px solid #bfdbfe;
        }
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

      .team-highlight {
        color: #18181b;
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
      }

      .alert-icon {
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
      }

      .warning-box {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.875rem 1rem;
        border-radius: 10px;
        font-size: 0.8125rem;

        &.danger {
          background: #fff5f5;
          border: 1px solid #fed7d7;
          color: #9b2c2c;

          .warning-icon {
            color: #e53e3e;
          }
        }

        &.warning {
          background: #fffbeb;
          border: 1px solid #fef3c7;
          color: #92400e;

          .warning-icon {
            color: #d97706;
          }
        }

        &.info {
          background: #f0f9ff;
          border: 1px solid #bae6fd;
          color: #075985;

          .warning-icon {
            color: #0284c7;
          }
        }
      }

      .warning-icon {
        width: 18px;
        height: 18px;
        flex-shrink: 0;
        margin-top: 2px;
      }

      .warning-text p {
        margin: 0 0 0.25rem 0;
        line-height: 1.45;

        &:last-child {
          margin-bottom: 0;
        }
      }

      .mode-switcher {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.65rem 0.85rem;
        background: #f8fafc;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
      }

      .switcher-label {
        font-size: 0.78125rem;
        color: #64748b;
        font-weight: 500;
      }

      .btn-switch {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        padding: 0.35rem 0.65rem;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 500;
        color: #475569;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover:not(:disabled) {
          background: #f1f5f9;
          color: #0f172a;
        }

        &.danger:hover:not(:disabled) {
          background: #fef2f2;
          color: #dc2626;
          border-color: #fecaca;
        }
      }

      .switch-icon {
        width: 13px;
        height: 13px;
      }

      .modal-footer {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.65rem;
        padding: 1rem 1.25rem;
        border-top: 1px solid #f4f4f5;
        background: #fafafa;
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

      .btn-danger {
        background: #dc2626;
        color: #ffffff;
        box-shadow: 0 1px 2px rgba(220, 38, 38, 0.2);

        &:hover:not(:disabled) {
          background: #b91c1c;
        }
      }

      .btn-warning {
        background: #d97706;
        color: #ffffff;
        box-shadow: 0 1px 2px rgba(217, 119, 6, 0.2);

        &:hover:not(:disabled) {
          background: #b45309;
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
export class DeleteTeamModalComponent {
  private readonly organizationApiService = inject(OrganizationApiService);

  @Input() isOpen = false;
  @Input() orgCode = '';

  private currentTeam: GroupResponseDto | null = null;

  @Input() set team(val: GroupResponseDto | null) {
    this.currentTeam = val;
    if (val) {
      const isArchived = (val.status || '').toUpperCase() === 'ARCHIVED';
      this.mode.set(isArchived ? 'unarchive' : 'archive');
    }
  }

  @Input() set initialMode(val: DeleteModalMode) {
    if (val) {
      this.mode.set(val);
    }
  }

  @Output() closed = new EventEmitter<void>();
  @Output() teamActionCompleted = new EventEmitter<{
    action: DeleteModalMode;
    team: GroupResponseDto;
  }>();

  readonly mode = signal<DeleteModalMode>('archive');
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly teamName = computed(() => this.currentTeam?.name || '');

  readonly modalTitle = computed(() => {
    switch (this.mode()) {
      case 'delete':
        return 'Delete Team Permanently';
      case 'archive':
        return 'Archive Team';
      case 'unarchive':
        return 'Reactivate Team';
    }
  });

  onClose(): void {
    if (this.isLoading()) return;
    this.errorMessage.set(null);
    this.closed.emit();
  }

  onConfirm(): void {
    if (this.isLoading() || !this.currentTeam || !this.orgCode) {
      if (!this.orgCode) this.errorMessage.set('No organization context available.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const m = this.mode();
    const teamId = this.currentTeam.id;

    if (m === 'delete') {
      this.organizationApiService.deleteGroup(this.orgCode, teamId).subscribe({
        next: () => {
          this.isLoading.set(false);
          this.teamActionCompleted.emit({
            action: m,
            team: this.currentTeam!,
          });
          this.closed.emit();
        },
        error: (err: any) => {
          this.isLoading.set(false);
          const msg =
            err?.error?.message ||
            err?.error?.error ||
            err?.message ||
            'Failed to delete team. Please try again.';
          this.errorMessage.set(msg);
        },
      });
    } else {
      const apiCall$ =
        m === 'archive'
          ? this.organizationApiService.archiveGroup(this.orgCode, teamId)
          : this.organizationApiService.unarchiveGroup(this.orgCode, teamId);

      apiCall$.subscribe({
        next: (resultGroup: GroupResponseDto) => {
          this.isLoading.set(false);
          this.teamActionCompleted.emit({
            action: m,
            team: resultGroup || this.currentTeam!,
          });
          this.closed.emit();
        },
        error: (err: any) => {
          this.isLoading.set(false);
          const msg =
            err?.error?.message ||
            err?.error?.error ||
            err?.message ||
            `Failed to ${m} team. Please try again.`;
          this.errorMessage.set(msg);
        },
      });
    }
  }

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen && !this.isLoading()) {
      this.onClose();
    }
  }
}
