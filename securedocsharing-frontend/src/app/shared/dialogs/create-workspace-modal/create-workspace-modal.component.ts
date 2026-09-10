import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  HostListener,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { LayoutStore } from '../../../state/layout/layout.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { WorkspaceApiService } from '../../../core/services/workspace-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { IconComponent } from '../../components/icon/icon.component';

/**
 * CreateWorkspaceModalComponent: Canonical modal for creating personal or organization project workspaces.
 * Directly integrates with WorkspaceApiService to provision workspaces with real numeric IDs.
 * Automatically synchronizes store state and navigates to the newly created workspace.
 */
@Component({
  selector: 'app-create-workspace-modal',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, IconComponent],
  template: `
    @if (isOpen()) {
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
                <app-icon name="briefcase" [size]="20" />
              </div>
              <div>
                <h2 id="modal-title" class="modal-title">Create Workspace</h2>
                <p class="modal-subtitle">
                  {{ modalSubtitle() }}
                </p>
              </div>
            </div>
            <button
              class="close-btn"
              (click)="onClose()"
              aria-label="Close dialog"
              [disabled]="isLoading()"
            >
              <app-icon name="x" [size]="16" />
            </button>
          </div>

          <!-- Error Alert -->
          @if (errorMessage()) {
            <div class="error-alert">
              <app-icon name="alert-circle" [size]="16" class="error-icon" />
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <!-- Form Content -->
          <form (ngSubmit)="onSubmit()" class="modal-form">
            <div class="form-group">
              <label for="workspace-name" class="form-label">
                Workspace Name <span class="required">*</span>
              </label>
              <input
                id="workspace-name"
                type="text"
                class="form-input"
                [(ngModel)]="name"
                (ngModelChange)="onNameInput($event)"
                name="workspaceName"
                placeholder="e.g. Engineering, Q3 Initiatives, Personal Projects"
                autocomplete="off"
                [disabled]="isLoading()"
                #nameInput
              />
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
                  <span>Creating...</span>
                } @else {
                  <app-icon name="plus" [size]="15" />
                  <span>Create Workspace</span>
                }
              </button>
            </div>
          </form>
        </div>
      </div>
    }
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.5);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
      animation: backdropFade 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes backdropFade {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .modal-card {
      background: var(--color-surface, #ffffff);
      border: 1px solid var(--color-border, #e5e7eb);
      border-radius: 12px;
      width: 100%;
      max-width: 480px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
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
      border-bottom: 1px solid var(--color-border, #e5e7eb);
    }

    .modal-header-left {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
    }

    .icon-badge {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      background: rgba(249, 115, 22, 0.1);
      color: var(--color-accent, #f97316);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .modal-title {
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--color-text, #111827);
      margin: 0;
      line-height: 1.3;
    }

    .modal-subtitle {
      font-size: 0.8125rem;
      color: var(--color-text-muted, #6b7280);
      margin: 0.2rem 0 0;
      line-height: 1.4;
    }

    .close-btn {
      background: transparent;
      border: none;
      color: var(--color-text-muted, #9ca3af);
      padding: 0.35rem;
      border-radius: 6px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s, color 0.15s;
    }

    .close-btn:hover:not(:disabled) {
      background: var(--color-bg-hover, #f3f4f6);
      color: var(--color-text, #111827);
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
      color: #ef4444;
      flex-shrink: 0;
    }

    .modal-form {
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.35rem;
    }

    .form-label {
      font-size: 0.8125rem;
      font-weight: 500;
      color: var(--color-text, #374151);
    }

    .required {
      color: #ef4444;
    }

    .form-input {
      width: 100%;
      padding: 0.55rem 0.75rem;
      font-size: 0.875rem;
      color: var(--color-text, #111827);
      background: var(--color-surface, #ffffff);
      border: 1px solid var(--color-border, #d1d5db);
      border-radius: 8px;
      outline: none;
      transition: border-color 0.15s, box-shadow 0.15s;
    }

    .form-input:focus {
      border-color: var(--color-accent, #f97316);
      box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.15);
    }

    .form-input:disabled {
      background: #f9fafb;
      cursor: not-allowed;
      opacity: 0.7;
    }

    .modal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.65rem;
      margin-top: 0.5rem;
      padding-top: 0.75rem;
      border-top: 1px solid var(--color-border, #f3f4f6);
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
    }

    .btn:disabled {
      opacity: 0.65;
      cursor: not-allowed;
    }

    .btn-secondary {
      background: transparent;
      border-color: var(--color-border, #d1d5db);
      color: var(--color-text, #374151);
    }

    .btn-secondary:hover:not(:disabled) {
      background: var(--color-bg-hover, #f3f4f6);
    }

    .btn-primary {
      background: var(--color-accent, #f97316);
      color: #ffffff;
    }

    .btn-primary:hover:not(:disabled) {
      background: #ea580c;
    }

    .spinner {
      width: 14px;
      height: 14px;
      border: 2px solid rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `],
})
export class CreateWorkspaceModalComponent {
  private readonly layoutStore = inject(LayoutStore);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly workspaceApiService = inject(WorkspaceApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  readonly isOpen = computed(() => {
    const modal = this.layoutStore.activeModal();
    return modal === 'CREATE_WORKSPACE' || modal === 'CREATE_SUBSPACE';
  });

  readonly isPersonal = computed(() => !this.workspaceContextStore.activeOrganization());

  readonly activeOrganization = computed(() => this.workspaceContextStore.activeOrganization());

  readonly modalSubtitle = computed(() => {
    const org = this.activeOrganization();
    if (org) {
      return `Create a new workspace in ${org.name} to organize team documents.`;
    }
    return 'Create a new personal workspace to organize your files and projects.';
  });

  readonly name = signal<string>('');
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isValid = computed(() => this.name().trim().length >= 2);

  onNameInput(val: string): void {
    this.name.set(val);
    this.errorMessage.set(null);
  }

  onClose(): void {
    if (this.isLoading()) return;
    this.resetForm();
    this.layoutStore.closeModal();
  }

  onSubmit(): void {
    if (!this.isValid() || this.isLoading()) return;

    const nameVal = this.name().trim();
    const org = this.workspaceContextStore.activeOrganization();
    const isPersonalContext = !org;

    this.isLoading.set(true);
    this.errorMessage.set(null);

    // Personal vs Organization isolation:
    // Personal workspace creation strictly includes no organization code.
    // Organization workspace creation strictly includes the active organization's code.
    const create$ = isPersonalContext
      ? this.workspaceApiService.createPersonalWorkspace({ name: nameVal })
      : this.workspaceApiService.createWorkspace(org!.orgCode, { name: nameVal });

    create$.subscribe({
      next: (createdWorkspace) => {
        this.isLoading.set(false);
        this.resetForm();
        this.layoutStore.closeModal();

        this.notificationService.success(
          `Workspace "${createdWorkspace.name}" created successfully.`
        );

        // 1. Activate newly created workspace in store & persist its numeric ID
        this.workspaceContextStore.setActiveWorkspace(createdWorkspace);

        // 2. Navigate to the new workspace dashboard
        if (isPersonalContext) {
          this.router.navigate([
            '/w',
            'personal',
            'w',
            createdWorkspace.id.toString(),
            'dashboard',
          ]);
        } else {
          this.router.navigate([
            '/org',
            org!.orgCode,
            'w',
            createdWorkspace.id.toString(),
            'dashboard',
          ]);
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        console.error('[CreateWorkspaceModal] Failed to create workspace:', err);
        const backendMessage =
          err?.error?.message ||
          err?.error?.error ||
          err?.message ||
          'Failed to create workspace. Please try again.';
        this.errorMessage.set(backendMessage);
      },
    });
  }

  private resetForm(): void {
    this.name.set('');
    this.isLoading.set(false);
    this.errorMessage.set(null);
  }

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    if (this.isOpen()) {
      this.onClose();
    }
  }
}
