import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  inject,
  effect,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccessRequestApiService } from '../../../core/services/access-request-api.service';
import {
  AccessPermissionLevel,
  AccessRequestEligibilityDto,
  AccessRequestResponseDto,
} from '../../../core/models/access-request.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';

export interface AccessRequestTarget {
  type: 'folder' | 'document';
  id: number;
  name: string;
  fileHash?: string;
}

@Component({
  selector: 'app-access-request-modal',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen() && target()) {
      <div class="modal-backdrop" (click)="onBackdropClick($event)">
        <div
          class="modal-panel"
          role="dialog"
          aria-modal="true"
          [attr.aria-labelledby]="'access-request-title'"
        >
          <!-- Header -->
          <div class="modal-header">
            <div class="header-icon-box">
              <app-icon name="key" [size]="18" class="text-amber-500" />
            </div>
            <div class="header-text">
              <h2 id="access-request-title" class="modal-title">Request Access</h2>
              <p class="modal-sub">
                {{ isPersonalSpace() ? 'Ask the document owner for permission to access this ' + (target()?.type ?? 'item') + '.' : 'Ask a workspace administrator for permission to access this ' + (target()?.type ?? 'item') + '.' }}
              </p>
            </div>
            <button
              type="button"
              class="close-btn"
              (click)="close()"
              aria-label="Close"
            >
              <app-icon name="x" [size]="16" />
            </button>
          </div>

          <!-- Body -->
          <div class="modal-body">
            <!-- Resource Info Card -->
            <div class="resource-card">
              <div class="resource-icon">
                @if (target()?.type === 'folder') {
                  <app-icon name="folder" [size]="20" />
                } @else {
                  <app-icon name="file-text" [size]="20" />
                }
              </div>
              <div class="resource-details">
                <span class="resource-name" [title]="target()?.name">{{ target()?.name }}</span>
                <span class="resource-type">
                  {{ target()?.type === 'folder' ? 'Folder' : 'Document' }} • Locked resource
                </span>
              </div>
            </div>

            <!-- Ineligible / Reason Alert -->
            @if (eligibility() && !eligibility()!.eligible) {
              <div class="alert alert-warning">
                <app-icon name="alert-circle" [size]="16" />
                <span>{{ getIneligibleMessage(eligibility()!.reasonCode) }}</span>
              </div>
            }

            @if (errorMessage()) {
              <div class="alert alert-error">
                <app-icon name="alert-circle" [size]="16" />
                <span>{{ errorMessage() }}</span>
              </div>
            }

            <!-- Permission Level Selection -->
            <div class="form-group">
              <label class="form-label">Requested Permission Level</label>
              <div class="level-options">
                <button
                  type="button"
                  class="level-card"
                  [class.active]="selectedLevel() === 'VIEW'"
                  (click)="selectedLevel.set('VIEW')"
                >
                  <div class="level-title-row">
                    <span class="level-name">View</span>
                    @if (selectedLevel() === 'VIEW') {
                      <app-icon name="check" [size]="14" class="text-amber-500" />
                    }
                  </div>
                  <p class="level-desc">
                    {{ target()?.type === 'folder' ? 'Browse allowed folder contents' : 'Preview document contents & view metadata' }}
                  </p>
                </button>

                <button
                  type="button"
                  class="level-card"
                  [class.active]="selectedLevel() === 'DOWNLOAD'"
                  (click)="selectedLevel.set('DOWNLOAD')"
                >
                  <div class="level-title-row">
                    <span class="level-name">Download</span>
                    @if (selectedLevel() === 'DOWNLOAD') {
                      <app-icon name="check" [size]="14" class="text-amber-500" />
                    }
                  </div>
                  <p class="level-desc">
                    {{ target()?.type === 'folder' ? 'Download files within this folder' : 'Download and export document' }}
                  </p>
                </button>

                <button
                  type="button"
                  class="level-card"
                  [class.active]="selectedLevel() === 'EDIT'"
                  (click)="selectedLevel.set('EDIT')"
                >
                  <div class="level-title-row">
                    <span class="level-name">Edit</span>
                    @if (selectedLevel() === 'EDIT') {
                      <app-icon name="check" [size]="14" class="text-amber-500" />
                    }
                  </div>
                  <p class="level-desc">
                    {{ target()?.type === 'folder' ? 'Upload files and create subfolders' : 'Edit, rename, and update file contents' }}
                  </p>
                </button>
              </div>
            </div>

            <!-- Reason Input -->
            <div class="form-group">
              <div class="label-row">
                <label for="request-reason" class="form-label">
                  Reason for Request
                  @if (isReasonRequired()) {
                    <span class="required-star">*</span>
                  }
                </label>
                @if (!isReasonRequired()) {
                  <span class="optional-hint">Optional</span>
                }
              </div>
              <textarea
                id="request-reason"
                class="form-textarea"
                rows="3"
                placeholder="Explain why you need access to this resource..."
                [value]="reason()"
                (input)="reason.set($any($event.target).value)"
              ></textarea>
            </div>

            <!-- Review Scope Footnote -->
            <div class="reviewer-footnote">
              <app-icon name="info" [size]="14" class="footnote-icon" />
              <span>
                {{ isPersonalSpace() ? 'Your request will be sent to the document owner.' : 'Your request will be routed to the workspace administrators.' }}
              </span>
            </div>
          </div>

          <!-- Footer -->
          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary"
              (click)="close()"
              [disabled]="isSubmitting()"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary"
              (click)="onSubmit()"
              [disabled]="isSubmitDisabled()"
            >
              @if (isSubmitting()) {
                <span class="spinner-sm"></span>
                <span>Submitting...</span>
              } @else {
                <app-icon name="send" [size]="14" />
                <span>Submit Request</span>
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
        z-index: 9999;
        background-color: rgba(15, 23, 42, 0.65);
        backdrop-filter: blur(4px);
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1rem;
        animation: fadeIn 0.15s ease-out;
      }

      .modal-panel {
        background: #ffffff;
        border-radius: 12px;
        box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
        width: 100%;
        max-width: 480px;
        overflow: hidden;
        display: flex;
        flex-direction: column;
        animation: slideUp 0.2s ease-out;
      }

      :host-context(.dark) .modal-panel {
        background: #1e293b;
        border: 1px solid #334155;
        color: #f8fafc;
      }

      .modal-header {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 1.25rem 1.5rem;
        border-bottom: 1px solid #f1f5f9;
      }

      :host-context(.dark) .modal-header {
        border-bottom-color: #334155;
      }

      .header-icon-box {
        width: 36px;
        height: 36px;
        border-radius: 8px;
        background: #fff7ed;
        color: #f97316;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }

      :host-context(.dark) .header-icon-box {
        background: rgba(249, 115, 22, 0.15);
      }

      .header-text {
        flex: 1;
      }

      .modal-title {
        font-size: 1.125rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0;
      }

      :host-context(.dark) .modal-title {
        color: #f8fafc;
      }

      .modal-sub {
        font-size: 0.8125rem;
        color: #64748b;
        margin: 0.25rem 0 0;
      }

      :host-context(.dark) .modal-sub {
        color: #94a3b8;
      }

      .close-btn {
        background: transparent;
        border: none;
        color: #94a3b8;
        cursor: pointer;
        padding: 0.25rem;
        border-radius: 6px;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: color 0.15s, background-color 0.15s;
      }

      .close-btn:hover {
        color: #0f172a;
        background-color: #f1f5f9;
      }

      :host-context(.dark) .close-btn:hover {
        color: #f8fafc;
        background-color: #334155;
      }

      .modal-body {
        padding: 1.25rem 1.5rem;
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      .resource-card {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.75rem 1rem;
        background: #f8fafc;
        border-radius: 8px;
        border: 1px solid #e2e8f0;
      }

      :host-context(.dark) .resource-card {
        background: #0f172a;
        border-color: #334155;
      }

      .resource-icon {
        color: #f97316;
        display: flex;
      }

      .resource-details {
        display: flex;
        flex-direction: column;
        overflow: hidden;
      }

      .resource-name {
        font-size: 0.875rem;
        font-weight: 500;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      :host-context(.dark) .resource-name {
        color: #f8fafc;
      }

      .resource-type {
        font-size: 0.75rem;
        color: #64748b;
      }

      :host-context(.dark) .resource-type {
        color: #94a3b8;
      }

      .alert {
        display: flex;
        align-items: flex-start;
        gap: 0.5rem;
        padding: 0.75rem;
        border-radius: 6px;
        font-size: 0.8125rem;
      }

      .alert-warning {
        background: #fffbeb;
        border: 1px solid #fde68a;
        color: #b45309;
      }

      :host-context(.dark) .alert-warning {
        background: rgba(245, 158, 11, 0.1);
        border-color: rgba(245, 158, 11, 0.3);
        color: #fbbf24;
      }

      .alert-error {
        background: #fef2f2;
        border: 1px solid #fecaca;
        color: #b91c1c;
      }

      :host-context(.dark) .alert-error {
        background: rgba(239, 68, 68, 0.1);
        border-color: rgba(239, 68, 68, 0.3);
        color: #f87171;
      }

      .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      .form-label {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #334155;
      }

      :host-context(.dark) .form-label {
        color: #cbd5e1;
      }

      .label-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .required-star {
        color: #ef4444;
        margin-left: 0.125rem;
      }

      .optional-hint {
        font-size: 0.75rem;
        color: #94a3b8;
      }

      .level-options {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 0.5rem;
      }

      .level-card {
        padding: 0.75rem 0.5rem;
        border-radius: 8px;
        border: 1px solid #e2e8f0;
        background: #ffffff;
        cursor: pointer;
        text-align: left;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        transition: border-color 0.15s, background-color 0.15s;
      }

      :host-context(.dark) .level-card {
        background: #0f172a;
        border-color: #334155;
      }

      .level-card:hover {
        border-color: #cbd5e1;
      }

      :host-context(.dark) .level-card:hover {
        border-color: #475569;
      }

      .level-card.active {
        border-color: #f97316;
        background: #fff7ed;
      }

      :host-context(.dark) .level-card.active {
        background: rgba(249, 115, 22, 0.1);
        border-color: #f97316;
      }

      .level-title-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .level-name {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #0f172a;
      }

      :host-context(.dark) .level-name {
        color: #f8fafc;
      }

      .level-card.active .level-name {
        color: #ea580c;
      }

      :host-context(.dark) .level-card.active .level-name {
        color: #fb923c;
      }

      .level-desc {
        font-size: 0.6875rem;
        color: #64748b;
        margin: 0;
        line-height: 1.3;
      }

      :host-context(.dark) .level-desc {
        color: #94a3b8;
      }

      .form-textarea {
        width: 100%;
        padding: 0.625rem 0.75rem;
        border-radius: 6px;
        border: 1px solid #cbd5e1;
        background: #ffffff;
        font-size: 0.8125rem;
        color: #0f172a;
        font-family: inherit;
        resize: vertical;
        box-sizing: border-box;
        outline: none;
        transition: border-color 0.15s, box-shadow 0.15s;
      }

      :host-context(.dark) .form-textarea {
        background: #0f172a;
        border-color: #334155;
        color: #f8fafc;
      }

      .form-textarea:focus {
        border-color: #f97316;
        box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.15);
      }

      .reviewer-footnote {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.75rem;
        color: #64748b;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        padding: 0.5rem 0.75rem;
        line-height: 1.4;
      }

      :host-context(.dark) .reviewer-footnote {
        background: #0f172a;
        border-color: #334155;
        color: #94a3b8;
      }

      .footnote-icon {
        color: #94a3b8;
        flex-shrink: 0;
      }

      .modal-footer {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.75rem;
        padding: 1rem 1.5rem;
        background: #f8fafc;
        border-top: 1px solid #f1f5f9;
      }

      :host-context(.dark) .modal-footer {
        background: #0f172a;
        border-top-color: #334155;
      }

      .btn-secondary {
        padding: 0.5rem 0.875rem;
        border-radius: 6px;
        border: 1px solid #cbd5e1;
        background: #ffffff;
        color: #334155;
        font-size: 0.8125rem;
        font-weight: 500;
        cursor: pointer;
        transition: background-color 0.15s;
      }

      :host-context(.dark) .btn-secondary {
        background: #1e293b;
        border-color: #334155;
        color: #cbd5e1;
      }

      .btn-secondary:hover:not(:disabled) {
        background: #f1f5f9;
      }

      :host-context(.dark) .btn-secondary:hover:not(:disabled) {
        background: #334155;
      }

      .btn-primary {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.5rem 1rem;
        border-radius: 6px;
        border: none;
        background: #f97316;
        color: #ffffff;
        font-size: 0.8125rem;
        font-weight: 500;
        cursor: pointer;
        transition: background-color 0.15s;
      }

      .btn-primary:hover:not(:disabled) {
        background: #ea580c;
      }

      .btn-primary:disabled,
      .btn-secondary:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }

      .spinner-sm {
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

      @keyframes fadeIn {
        from { opacity: 0; }
        to { opacity: 1; }
      }

      @keyframes slideUp {
        from { transform: translateY(8px) scale(0.98); opacity: 0; }
        to { transform: translateY(0) scale(1); opacity: 1; }
      }
    `,
  ],
})
export class AccessRequestModalComponent {
  private readonly accessRequestApi = inject(AccessRequestApiService);
  private readonly workspaceStore = inject(WorkspaceContextStore, { optional: true });

  readonly isOpen = input<boolean>(false);
  readonly target = input<AccessRequestTarget | null>(null);
  readonly isPersonal = input<boolean | null>(null);

  readonly isPersonalSpace = computed(() => {
    const explicit = this.isPersonal();
    if (explicit !== null) return explicit;
    return this.workspaceStore?.isPersonal() ?? false;
  });

  readonly closed = output<void>();
  readonly requestSubmitted = output<AccessRequestResponseDto>();

  readonly selectedLevel = signal<AccessPermissionLevel>('VIEW');
  readonly reason = signal<string>('');
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);
  readonly eligibility = signal<AccessRequestEligibilityDto | null>(null);

  constructor() {
    effect(
      () => {
        if (this.isOpen() && this.target()) {
          this.resetForm();
          this.checkEligibility();
        }
      },
      { allowSignalWrites: true }
    );
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen()) {
      this.close();
    }
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.close();
    }
  }

  close(): void {
    this.closed.emit();
  }

  isReasonRequired(): boolean {
    return Boolean(this.eligibility()?.requireReason);
  }

  isSubmitDisabled(): boolean {
    if (this.isSubmitting()) return true;
    if (this.eligibility() && !this.eligibility()!.eligible) return true;
    if (this.isReasonRequired() && !this.reason().trim()) return true;
    return false;
  }

  private resetForm(): void {
    this.selectedLevel.set('VIEW');
    this.reason.set('');
    this.isSubmitting.set(false);
    this.errorMessage.set(null);
    this.eligibility.set(null);
  }

  private checkEligibility(): void {
    const t = this.target();
    if (!t) return;

    this.accessRequestApi
      .checkEligibility({
        documentId: t.type === 'document' ? t.id : undefined,
        documentFileHash: t.type === 'document' ? t.fileHash : undefined,
        folderId: t.type === 'folder' ? t.id : undefined,
      })
      .subscribe({
        next: (el) => this.eligibility.set(el),
        error: () => this.eligibility.set(null),
      });
  }

  onSubmit(): void {
    const t = this.target();
    if (!t || this.isSubmitDisabled()) return;

    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    this.accessRequestApi
      .createAccessRequest({
        documentId: t.type === 'document' ? t.id : undefined,
        documentFileHash: t.type === 'document' ? t.fileHash : undefined,
        folderId: t.type === 'folder' ? t.id : undefined,
        requestedPermissionLevel: this.selectedLevel(),
        reason: this.reason().trim() || undefined,
      })
      .subscribe({
        next: (resp) => {
          this.isSubmitting.set(false);
          this.requestSubmitted.emit(resp);
          this.close();
        },
        error: (err) => {
          this.isSubmitting.set(false);
          const msg =
            err?.error?.message ||
            err?.message ||
            'Failed to submit access request. Please try again.';
          this.errorMessage.set(msg);
        },
      });
  }

  getIneligibleMessage(reasonCode?: string): string {
    switch (reasonCode) {
      case 'PENDING_REQUEST_EXISTS':
        return 'An access request for this item is already pending review.';
      case 'ALREADY_HAS_ACCESS':
      case 'ALREADY_HAS_MAX_PERMISSION':
        return 'You already have access permissions for this resource.';
      case 'REQUESTS_DISABLED_BY_POLICY':
      case 'ACCESS_REQUESTS_DISABLED':
        return 'Access requests have been disabled by workspace policy for this resource.';
      case 'EXTERNAL_REQUESTS_DISABLED':
        return 'External access requests are disabled by workspace policy.';
      default:
        return 'This resource is not currently eligible for access requests.';
    }
  }
}
