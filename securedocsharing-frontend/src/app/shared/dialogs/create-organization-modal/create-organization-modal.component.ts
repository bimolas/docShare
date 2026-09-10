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
import { AuthStore } from '../../../state/auth/auth.store';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { IconComponent } from '../../components/icon/icon.component';

@Component({
  selector: 'app-create-organization-modal',
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
                <app-icon name="building-2" [size]="20" />
              </div>
              <div>
                <h2 id="modal-title" class="modal-title">Create Organization</h2>
                <p class="modal-subtitle">
                  Set up a new organization workspace for team collaboration and document sharing.
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
              <label for="org-name" class="form-label">
                Organization Name <span class="required">*</span>
              </label>
              <input
                id="org-name"
                type="text"
                class="form-input"
                [(ngModel)]="name"
                (ngModelChange)="onNameInput($event)"
                name="orgName"
                placeholder="e.g. Acme Corporation"
                autocomplete="off"
                [disabled]="isLoading()"
                #nameInput
              />
            </div>

            <div class="form-group">
              <label for="org-code" class="form-label">
                Organization Code <span class="required">*</span>
              </label>
              <input
                id="org-code"
                type="text"
                class="form-input code-input"
                [(ngModel)]="orgCode"
                (ngModelChange)="onCodeInput($event)"
                name="orgCode"
                placeholder="e.g. acme-corp"
                autocomplete="off"
                [disabled]="isLoading()"
              />
              <span class="form-hint">
                Unique identifier used in your workspace URL (/w/{{ orgCode() || 'code' }}/s/...)
              </span>
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
                  <span>Create Organization</span>
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

    .code-input {
      font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
      font-size: 0.8125rem;
    }

    .form-hint {
      font-size: 0.725rem;
      color: var(--color-text-muted, #6b7280);
      margin-top: 0.15rem;
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
export class CreateOrganizationModalComponent {
  private readonly layoutStore = inject(LayoutStore);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly authStore = inject(AuthStore);
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  readonly isOpen = computed(() => this.layoutStore.activeModal() === 'CREATE_ORGANIZATION');

  readonly name = signal<string>('');
  readonly orgCode = signal<string>('');
  readonly isManualCode = signal<boolean>(false);
  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly isValid = computed(() => {
    const n = this.name().trim();
    const c = this.orgCode().trim();
    return n.length >= 2 && c.length >= 2 && /^[a-zA-Z0-9-]+$/.test(c);
  });

  onNameInput(val: string): void {
    this.name.set(val);
    this.errorMessage.set(null);
    if (!this.isManualCode()) {
      this.orgCode.set(this.slugify(val));
    }
  }

  onCodeInput(val: string): void {
    this.isManualCode.set(true);
    this.errorMessage.set(null);
    this.orgCode.set(val.toLowerCase().replace(/[^a-z0-9-]/g, ''));
  }

  onClose(): void {
    if (this.isLoading()) return;
    this.resetForm();
    this.layoutStore.closeModal();
  }

  onSubmit(): void {
    if (!this.isValid() || this.isLoading()) return;

    const nameVal = this.name().trim();
    const codeVal = this.orgCode().trim().toLowerCase();

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.orgApiService.createOrganization({ name: nameVal, orgCode: codeVal }).subscribe({
      next: (createdOrg) => {
        this.isLoading.set(false);
        this.resetForm();
        this.layoutStore.closeModal();

        this.notificationService.success(
          `Organization "${createdOrg.name}" created successfully.`
        );

        // 1. Reload user profile so currentUser() has the new organization membership & capabilities
        this.authStore.loadUserProfile().subscribe({
          next: () => {
            // 2. Select the newly created organization in workspace store and navigate canonically
            this.workspaceContextStore.selectOrganization(createdOrg.orgCode, true).subscribe({
              next: () => {
                this.router.navigate(['/org', createdOrg.orgCode, 'dashboard']);
              },
              error: () => {
                this.router.navigate(['/org', createdOrg.orgCode, 'dashboard']);
              },
            });
          },
          error: () => {
            this.workspaceContextStore.selectOrganization(createdOrg.orgCode, true).subscribe({
              next: () => {
                this.router.navigate(['/org', createdOrg.orgCode, 'dashboard']);
              },
              error: () => {
                this.router.navigate(['/org', createdOrg.orgCode, 'dashboard']);
              },
            });
          },
        });
      },
      error: (err) => {
        this.isLoading.set(false);
        console.error('Failed to create organization:', err);
        const backendMessage =
          err?.error?.message || err?.error?.error || 'Failed to create organization. Please try again.';
        this.errorMessage.set(backendMessage);
      },
    });
  }

  private slugify(text: string): string {
    return text
      .toLowerCase()
      .trim()
      .replace(/[^\w\s-]/g, '')
      .replace(/[\s_-]+/g, '-')
      .replace(/^-+|-+$/g, '');
  }

  private resetForm(): void {
    this.name.set('');
    this.orgCode.set('');
    this.isManualCode.set(false);
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
