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
import {
  LucideUsersRound,
  LucideX,
  LucidePlus,
  LucideAlertCircle,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { AuthStore } from '../../../../state/auth/auth.store';
import {
  GroupResponseDto,
  OrganizationMemberDetailResponseDto,
} from '../../../../core/models/organization.model';

/**
 * Reusable Create Team Modal Component.
 * Styled in SecureDoc light-mode aesthetic (white card surface, cloudy borders, orange accent).
 * Handles team creation request to POST /api/v1/organizations/{orgCode}/groups.
 */
@Component({
  selector: 'sd-create-team-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideUsersRound,
    LucideX,
    LucidePlus,
    LucideAlertCircle,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen) {
      <div class="modal-backdrop" (click)="onClose()">
        <div
          class="modal-card"
          (click)="$event.stopPropagation()"
          role="dialog"
          aria-labelledby="modal-title"
          aria-modal="true"
        >
          <!-- Modal Header -->
          <div class="modal-header">
            <div class="modal-header-left">
              <div class="icon-badge">
                <svg lucideUsersRound class="header-lucide-icon"></svg>
              </div>
              <div>
                <h2 id="modal-title" class="modal-title">Create Team</h2>
                <p class="modal-subtitle">
                  Add a new group to organize members and manage access permissions.
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

          <!-- Form Body -->
          <form (ngSubmit)="onSubmit()" class="modal-form">
            <!-- Team Name -->
            <div class="form-group">
              <label for="team-name" class="form-label">
                Team Name <span class="required">*</span>
              </label>
              <input
                id="team-name"
                type="text"
                class="form-input"
                [ngModel]="name()"
                (ngModelChange)="onNameInput($event)"
                name="teamName"
                placeholder="e.g. Engineering, Product Design, Security"
                autocomplete="off"
                [disabled]="isLoading()"
                #nameInput
              />
            </div>

            <!-- Team Manager Selector (Required per Architecture) -->
            <div class="form-group">
              <label for="team-manager" class="form-label">
                Team Manager <span class="required">*</span>
              </label>
              @if (isLoadingMembers()) {
                <div class="members-loading-hint">
                  <span class="spinner-inline"></span>
                  <span>Loading organization members...</span>
                </div>
              } @else if (orgMembers().length === 0) {
                <div class="members-empty-hint">
                  <span>No active organization members available to assign as Manager.</span>
                </div>
              } @else {
                <select
                  id="team-manager"
                  class="form-select"
                  [ngModel]="selectedManagerUserId()"
                  (ngModelChange)="onManagerSelected($event)"
                  name="teamManager"
                  [disabled]="isLoading()"
                  required
                >
                  <option value="" disabled>-- Select a Team Manager --</option>
                  @for (m of orgMembers(); track m.userId) {
                    <option [value]="m.userId">
                      {{ m.firstName }} {{ m.lastName }} ({{ m.email }}) — {{ m.role }}
                    </option>
                  }
                </select>
              }
              <span class="field-hint">
                Every team must have exactly one designated Team Manager from active organization members.
              </span>
            </div>

            <!-- Team Description -->
            <div class="form-group">
              <label for="team-description" class="form-label">
                Description <span class="optional">(optional)</span>
              </label>
              <textarea
                id="team-description"
                class="form-textarea"
                [ngModel]="description()"
                (ngModelChange)="onDescriptionInput($event)"
                name="teamDescription"
                placeholder="Briefly describe the purpose or responsibilities of this team..."
                rows="3"
                [disabled]="isLoading()"
              ></textarea>
            </div>

            <!-- Modal Footer Actions -->
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
                type="submit"
                class="btn btn-primary"
                [disabled]="isLoading() || !isValid()"
              >
                @if (isLoading()) {
                  <span class="spinner"></span>
                  <span>Creating Team...</span>
                } @else {
                  <svg lucidePlus class="btn-icon"></svg>
                  <span>Create Team</span>
                }
              </button>
            </div>
          </form>
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
      }

      .error-icon {
        width: 16px;
        height: 16px;
        color: #ef4444;
        flex-shrink: 0;
      }

      .modal-form {
        padding: 1.25rem;
        display: flex;
        flex-direction: column;
        gap: 1.125rem;
      }

      .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }

      .form-label {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #374151;
      }

      .required {
        color: #ef4444;
      }

      .optional {
        color: #9ca3af;
        font-weight: 400;
      }

      .form-input,
      .form-select,
      .form-textarea {
        width: 100%;
        padding: 0.55rem 0.75rem;
        font-size: 0.875rem;
        color: #09090b;
        background: #ffffff;
        border: 1px solid #d1d5db;
        border-radius: 8px;
        outline: none;
        font-family: inherit;
        transition:
          border-color 0.15s ease,
          box-shadow 0.15s ease;

        &:focus {
          border-color: #f97316;
          box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
        }

        &:disabled {
          background: #f9fafb;
          cursor: not-allowed;
          opacity: 0.7;
        }

        &::placeholder {
          color: #9ca3af;
        }
      }

      .field-hint {
        font-size: 0.75rem;
        color: #71717a;
        margin-top: 0.2rem;
      }

      .members-loading-hint,
      .members-empty-hint {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.55rem 0.75rem;
        font-size: 0.8125rem;
        background: #f4f4f5;
        border: 1px solid #e4e4e7;
        border-radius: 8px;
        color: #71717a;
      }

      .members-empty-hint {
        color: #b45309;
        background: #fffbeb;
        border-color: #fde68a;
      }

      .spinner-inline {
        width: 14px;
        height: 14px;
        border: 2px solid #f97316;
        border-top-color: transparent;
        border-radius: 50%;
        animation: spin 0.6s linear infinite;
        flex-shrink: 0;
      }

      .form-textarea {
        resize: vertical;
        min-height: 72px;
      }

      .modal-footer {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.65rem;
        margin-top: 0.25rem;
        padding-top: 0.875rem;
        border-top: 1px solid #f4f4f5;
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
export class CreateTeamModalComponent implements OnChanges {
  private readonly organizationApiService = inject(OrganizationApiService);
  private readonly authStore = inject(AuthStore);

  @Input() isOpen = false;
  @Input() orgCode = '';

  @Output() closed = new EventEmitter<void>();
  @Output() teamCreated = new EventEmitter<GroupResponseDto>();

  // State Signals
  readonly name = signal<string>('');
  readonly description = signal<string>('');
  readonly selectedManagerUserId = signal<string>('');
  readonly orgMembers = signal<OrganizationMemberDetailResponseDto[]>([]);
  readonly isLoadingMembers = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isValid = computed(() => {
    return this.name().trim().length > 0 && this.selectedManagerUserId().trim().length > 0;
  });

  ngOnChanges(changes: SimpleChanges): void {
    if ((changes['isOpen'] && this.isOpen) || (changes['orgCode'] && this.isOpen)) {
      this.resetForm();
      this.fetchOrgMembers();
    }
  }

  fetchOrgMembers(): void {
    if (!this.orgCode) return;

    this.isLoadingMembers.set(true);
    this.errorMessage.set(null);

    this.organizationApiService.getMemberDetails(this.orgCode, 'ACTIVE').subscribe({
      next: (members: OrganizationMemberDetailResponseDto[]) => {
        const activeMembers = members || [];
        this.orgMembers.set(activeMembers);
        this.isLoadingMembers.set(false);

        // Pre-select current user if active, otherwise pre-select first active member
        const currentUserId = this.authStore.currentUser()?.id;
        if (currentUserId && activeMembers.some((m) => m.userId === currentUserId)) {
          this.selectedManagerUserId.set(currentUserId);
        } else if (activeMembers.length > 0) {
          this.selectedManagerUserId.set(activeMembers[0].userId);
        }
      },
      error: (err: unknown) => {
        console.error('Failed to load members for team creation:', err);
        this.isLoadingMembers.set(false);
        this.errorMessage.set('Failed to load organization members. You may not have administrative access.');
      },
    });
  }

  onNameInput(val: string): void {
    this.name.set(val);
    this.errorMessage.set(null);
  }

  onDescriptionInput(val: string): void {
    this.description.set(val);
    this.errorMessage.set(null);
  }

  onManagerSelected(val: string): void {
    this.selectedManagerUserId.set(val);
    this.errorMessage.set(null);
  }

  onClose(): void {
    if (this.isLoading()) return;
    this.resetForm();
    this.closed.emit();
  }

  onSubmit(): void {
    if (!this.isValid() || this.isLoading()) return;

    if (!this.orgCode) {
      this.errorMessage.set('No organization selected for team creation.');
      return;
    }

    const managerId = this.selectedManagerUserId().trim();
    if (!managerId) {
      this.errorMessage.set('Please select a Team Manager.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const nameVal = this.name().trim();
    const descVal = this.description().trim();

    this.organizationApiService
      .createGroup(this.orgCode, {
        name: nameVal,
        description: descVal || undefined,
        initialManagerUserId: managerId,
      })
      .subscribe({
        next: (createdTeam) => {
          this.isLoading.set(false);
          this.resetForm();
          this.teamCreated.emit(createdTeam);
          this.closed.emit();
        },
        error: (err) => {
          this.isLoading.set(false);
          const backendMessage =
            err?.error?.message ||
            err?.error?.error ||
            err?.message ||
            'Failed to create team. Please try again.';
          this.errorMessage.set(backendMessage);
        },
      });
  }

  private resetForm(): void {
    this.name.set('');
    this.description.set('');
    this.selectedManagerUserId.set('');
    this.isLoading.set(false);
    this.errorMessage.set(null);
  }

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen && !this.isLoading()) {
      this.onClose();
    }
  }
}
