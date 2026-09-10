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
import { FormsModule } from '@angular/forms';
import {
  LucidePencil,
  LucideX,
  LucideSave,
  LucideAlertCircle,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../../core/services/organization-api.service';
import { GroupResponseDto } from '../../../../core/models/organization.model';

/**
 * Edit Team Modal Component.
 * Allows Org Admins/Managers and Team Managers to update a team's name and description.
 * Styled in SecureDoc light-mode aesthetic (white card surface, cloudy borders, orange accent).
 */
@Component({
  selector: 'sd-edit-team-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucidePencil,
    LucideX,
    LucideSave,
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
          aria-labelledby="edit-modal-title"
          aria-modal="true"
        >
          <!-- Header -->
          <div class="modal-header">
            <div class="modal-header-left">
              <div class="icon-badge">
                <svg lucidePencil class="header-lucide-icon"></svg>
              </div>
              <div>
                <h2 id="edit-modal-title" class="modal-title">Edit Team Details</h2>
                <p class="modal-subtitle">
                  Update team name and description for team members.
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
              <label for="edit-team-name" class="form-label">
                Team Name <span class="required">*</span>
              </label>
              <input
                id="edit-team-name"
                type="text"
                class="form-input"
                [ngModel]="name()"
                (ngModelChange)="onNameInput($event)"
                name="teamName"
                placeholder="e.g. Engineering, Product Design"
                autocomplete="off"
                [disabled]="isLoading()"
              />
            </div>

            <!-- Team Description -->
            <div class="form-group">
              <label for="edit-team-description" class="form-label">
                Description <span class="optional">(optional)</span>
              </label>
              <textarea
                id="edit-team-description"
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
                [disabled]="isLoading() || !isValid() || !isDirty()"
              >
                @if (isLoading()) {
                  <span class="spinner"></span>
                  <span>Saving Changes...</span>
                } @else {
                  <svg lucideSave class="btn-icon"></svg>
                  <span>Save Changes</span>
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
export class EditTeamModalComponent {
  private readonly organizationApiService = inject(OrganizationApiService);

  @Input() isOpen = false;
  @Input() orgCode = '';

  private currentTeam: GroupResponseDto | null = null;

  @Input() set team(val: GroupResponseDto | null) {
    this.currentTeam = val;
    if (val) {
      this.initialName.set(val.name || '');
      this.initialDescription.set(val.description || '');
      this.name.set(val.name || '');
      this.description.set(val.description || '');
    } else {
      this.resetForm();
    }
  }

  @Output() closed = new EventEmitter<void>();
  @Output() teamUpdated = new EventEmitter<GroupResponseDto>();

  // State Signals
  readonly initialName = signal<string>('');
  readonly initialDescription = signal<string>('');
  readonly name = signal<string>('');
  readonly description = signal<string>('');
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isValid = computed(() => this.name().trim().length > 0);
  readonly isDirty = computed(
    () =>
      this.name().trim() !== this.initialName().trim() ||
      this.description().trim() !== this.initialDescription().trim()
  );

  onNameInput(val: string): void {
    this.name.set(val);
    this.errorMessage.set(null);
  }

  onDescriptionInput(val: string): void {
    this.description.set(val);
    this.errorMessage.set(null);
  }

  onClose(): void {
    if (this.isLoading()) return;
    this.errorMessage.set(null);
    this.closed.emit();
  }

  onSubmit(): void {
    if (!this.isValid() || !this.isDirty() || this.isLoading() || !this.currentTeam) return;

    if (!this.orgCode) {
      this.errorMessage.set('No organization context available.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const nameVal = this.name().trim();
    const descVal = this.description().trim();

    this.organizationApiService
      .updateGroup(this.orgCode, this.currentTeam.id, {
        name: nameVal,
        description: descVal || undefined,
      })
      .subscribe({
        next: (updatedTeam) => {
          this.isLoading.set(false);
          this.teamUpdated.emit(updatedTeam);
          this.closed.emit();
        },
        error: (err) => {
          this.isLoading.set(false);
          const backendMessage =
            err?.error?.message ||
            err?.error?.error ||
            err?.message ||
            'Failed to update team. Please try again.';
          this.errorMessage.set(backendMessage);
        },
      });
  }

  private resetForm(): void {
    this.initialName.set('');
    this.initialDescription.set('');
    this.name.set('');
    this.description.set('');
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
