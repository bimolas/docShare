import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  effect,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  AccessRequestResponseDto,
  AccessPermissionLevel,
} from '../../../core/models/access-request.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-access-request-detail-drawer',
  standalone: true,
  imports: [CommonModule, FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen() && request(); as req) {
      <div class="drawer-backdrop" (click)="onBackdropClick($event)">
        <div
          class="drawer-panel"
          role="dialog"
          aria-modal="true"
          aria-labelledby="drawer-title"
        >
          <!-- Drawer Header -->
          <div class="drawer-header">
            <div class="header-left">
              <span class="header-pre">Access Governance</span>
              <h2 id="drawer-title" class="drawer-title">Request Details #{{ req.id }}</h2>
            </div>
            <div class="header-right">
              <span class="status-badge" [ngClass]="'status-' + req.status.toLowerCase()">
                @switch (req.status) {
                  @case ('PENDING') {
                    <app-icon name="clock" [size]="13" />
                    <span>Pending Review</span>
                  }
                  @case ('APPROVED') {
                    <app-icon name="check" [size]="13" />
                    <span>Approved</span>
                  }
                  @case ('DENIED') {
                    <app-icon name="x" [size]="13" />
                    <span>Denied</span>
                  }
                  @case ('CANCELLED') {
                    <app-icon name="alert-circle" [size]="13" />
                    <span>Cancelled</span>
                  }
                }
              </span>
              <button
                type="button"
                class="close-btn"
                (click)="closeDrawer()"
                aria-label="Close drawer"
              >
                <app-icon name="x" [size]="16" />
              </button>
            </div>
          </div>

          <!-- Drawer Body -->
          <div class="drawer-body">
            <!-- Reviewer Authority Context Banner -->
            @if (req.reviewerAuthorityExplanation) {
              <div class="authority-banner">
                <div class="banner-icon">
                  <app-icon name="shield-check" [size]="16" />
                </div>
                <div class="banner-content">
                  <span class="banner-label">Review Authority</span>
                  <p class="banner-text">{{ req.reviewerAuthorityExplanation }}</p>
                </div>
              </div>
            }

            <!-- Resource Section -->
            <section class="section-card">
              <h3 class="section-heading">
                <app-icon [name]="req.targetType === 'FOLDER' ? 'folder' : 'file-text'" [size]="15" />
                Target Resource
              </h3>
              <div class="meta-grid">
                <div class="meta-item">
                  <span class="meta-key">Resource Name</span>
                  <span class="meta-val font-semibold text-stone-900 dark:text-stone-100">
                    {{ req.documentName || req.folderName || 'Unnamed resource' }}
                  </span>
                </div>
                <div class="meta-item">
                  <span class="meta-key">Resource Type</span>
                  <span class="meta-val">
                    {{ req.targetType === 'FOLDER' ? 'Folder' : 'Document' }}
                  </span>
                </div>
                <div class="meta-item">
                  <span class="meta-key">Requested Permission</span>
                  <span class="perm-tag" [ngClass]="'perm-' + req.requestedPermissionLevel.toLowerCase()">
                    {{ req.requestedPermissionLevel }}
                  </span>
                </div>
                <div class="meta-item">
                  <span class="meta-key">Submitted</span>
                  <span class="meta-val">{{ formatDate(req.createdAt) }}</span>
                </div>
              </div>
            </section>

            <!-- Requester Section -->
            <section class="section-card">
              <h3 class="section-heading">
                <app-icon name="user" [size]="15" />
                Requester Information
              </h3>
              <div class="meta-grid">
                <div class="meta-item">
                  <span class="meta-key">Requester</span>
                  <span class="meta-val font-medium">
                    {{ req.requesterUserName || req.requesterUserEmail || 'User' }}
                  </span>
                </div>
                <div class="meta-item">
                  <span class="meta-key">Email</span>
                  <span class="meta-val">{{ req.requesterUserEmail || '—' }}</span>
                </div>
              </div>
            </section>

            <!-- Justification / Reason Section -->
            <section class="section-card">
              <h3 class="section-heading">
                <app-icon name="clipboard-list" [size]="15" />
                Business Justification
              </h3>
              @if (req.reason) {
                <p class="justification-text">{{ req.reason }}</p>
              } @else {
                <p class="text-stone-400 dark:text-stone-500 italic text-sm">
                  No justification provided (optional).
                </p>
              }
            </section>

            <!-- Cancellation Information (if Cancelled) -->
            @if (req.status === 'CANCELLED') {
              <section class="section-card cancelled-section">
                <h3 class="section-heading text-stone-600 dark:text-stone-400">
                  <app-icon name="alert-circle" [size]="15" />
                  Cancellation Reason
                </h3>
                <p class="justification-text">
                  @if (req.cancellationReason === 'POLICY_DISABLED') {
                    <strong>Policy Disabled:</strong> Access requests for this resource or workspace were disabled by policy governance. All pending requests were invalidated automatically.
                  } @else {
                    {{ req.cancellationReason || 'Cancelled by requester.' }}
                  }
                </p>
              </section>
            }

            <!-- Decision Audit (if Processed) -->
            @if (req.status === 'APPROVED' || req.status === 'DENIED') {
              <section class="section-card audit-section">
                <h3 class="section-heading">
                  <app-icon name="shield" [size]="15" />
                  Review Decision
                </h3>
                <div class="meta-grid">
                  <div class="meta-item">
                    <span class="meta-key">Reviewed By</span>
                    <span class="meta-val">{{ req.processedByUserEmail || 'Administrator' }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="meta-key">Decision Date</span>
                    <span class="meta-val">{{ formatDate(req.processedAt) }}</span>
                  </div>
                  @if (req.reviewerNote) {
                    <div class="meta-item col-span-2">
                      <span class="meta-key">Reviewer Note</span>
                      <p class="justification-text mt-1">{{ req.reviewerNote }}</p>
                    </div>
                  }
                </div>
              </section>
            }

            <!-- Reviewer Decision Form (Only if PENDING and user is reviewer) -->
            @if (req.status === 'PENDING' && isReviewer()) {
              <section class="section-card decision-section">
                <h3 class="section-heading text-amber-600 dark:text-amber-400">
                  <app-icon name="key" [size]="15" />
                  Review & Grant Access
                </h3>

                <!-- Permission Level Adjustment -->
                <div class="form-group mb-4">
                  <label class="form-label">
                    Granted Permission Level
                    <span class="text-xs text-stone-400 font-normal ml-1">
                      (You may adjust before approving)
                    </span>
                  </label>
                  <div class="perm-options-grid">
                    <button
                      type="button"
                      class="perm-option"
                      [class.active]="selectedPermission() === 'VIEW'"
                      [disabled]="!isLevelAllowed('VIEW')"
                      (click)="selectedPermission.set('VIEW')"
                    >
                      <div class="perm-opt-header">
                        <span class="perm-title">VIEW</span>
                        @if (selectedPermission() === 'VIEW') {
                          <app-icon name="check" [size]="14" class="text-amber-500" />
                        }
                      </div>
                      <p class="perm-desc">Browse folder or preview document metadata.</p>
                    </button>

                    <button
                      type="button"
                      class="perm-option"
                      [class.active]="selectedPermission() === 'DOWNLOAD'"
                      [disabled]="!isLevelAllowed('DOWNLOAD')"
                      (click)="selectedPermission.set('DOWNLOAD')"
                    >
                      <div class="perm-opt-header">
                        <span class="perm-title">DOWNLOAD</span>
                        @if (selectedPermission() === 'DOWNLOAD') {
                          <app-icon name="check" [size]="14" class="text-amber-500" />
                        }
                      </div>
                      <p class="perm-desc">
                        @if (isLevelAllowed('DOWNLOAD')) {
                          Preview & download document contents.
                        } @else {
                          Exceeds requested level ({{ req.requestedPermissionLevel }})
                        }
                      </p>
                    </button>

                    <button
                      type="button"
                      class="perm-option"
                      [class.active]="selectedPermission() === 'EDIT'"
                      [disabled]="!isLevelAllowed('EDIT')"
                      (click)="selectedPermission.set('EDIT')"
                    >
                      <div class="perm-opt-header">
                        <span class="perm-title">EDIT</span>
                        @if (selectedPermission() === 'EDIT') {
                          <app-icon name="check" [size]="14" class="text-amber-500" />
                        }
                      </div>
                      <p class="perm-desc">
                        @if (isLevelAllowed('EDIT')) {
                          Upload, modify, and manage contents.
                        } @else {
                          Exceeds requested level ({{ req.requestedPermissionLevel }})
                        }
                      </p>
                    </button>
                  </div>
                </div>

                <!-- Reviewer Note -->
                <div class="form-group mb-4">
                  <label for="reviewer-note" class="form-label">
                    Reviewer Note <span class="text-xs text-stone-400 font-normal">(Optional)</span>
                  </label>
                  <textarea
                    id="reviewer-note"
                    class="form-textarea"
                    rows="2"
                    placeholder="Add an optional decision note for the requester..."
                    [value]="reviewerNote()"
                    (input)="reviewerNote.set($any($event.target).value)"
                  ></textarea>
                </div>

                <!-- Action Buttons -->
                <div class="action-buttons-row">
                  <button
                    type="button"
                    class="btn-deny"
                    [disabled]="isSubmitting()"
                    (click)="onDeny(req.id)"
                  >
                    @if (isSubmitting()) {
                      <span class="spinner-sm"></span>
                    } @else {
                      <app-icon name="x" [size]="14" />
                    }
                    <span>Deny Request</span>
                  </button>

                  <button
                    type="button"
                    class="btn-approve"
                    [disabled]="isSubmitting()"
                    (click)="onApprove(req.id)"
                  >
                    @if (isSubmitting()) {
                      <span class="spinner-sm"></span>
                    } @else {
                      <app-icon name="check" [size]="14" />
                    }
                    <span>Approve & Grant {{ selectedPermission() }}</span>
                  </button>
                </div>
              </section>
            }

            <!-- Requester Cancel Action (If PENDING and user is requester) -->
            @if (req.status === 'PENDING' && !isReviewer()) {
              <div class="requester-cancel-box">
                <p class="text-xs text-stone-500 mb-2">
                  This request is awaiting review by authorized managers or administrators. You can cancel it if you no longer need access.
                </p>
                <button
                  type="button"
                  class="btn-cancel-req"
                  [disabled]="isSubmitting()"
                  (click)="onCancel(req.id)"
                >
                  <app-icon name="alert-circle" [size]="14" />
                  <span>Cancel My Request</span>
                </button>
              </div>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .drawer-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.45);
      backdrop-filter: blur(2px);
      z-index: 9999;
      display: flex;
      justify-content: flex-end;
      animation: fadeIn 0.15s ease-out;
    }

    .drawer-panel {
      width: 100%;
      max-width: 520px;
      height: 100%;
      background: #ffffff;
      box-shadow: -4px 0 24px rgba(0, 0, 0, 0.12);
      display: flex;
      flex-direction: column;
      animation: slideInRight 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }

    :host-context(.dark) .drawer-panel {
      background: #1c1917;
      border-left: 1px solid #292524;
      box-shadow: -4px 0 24px rgba(0, 0, 0, 0.4);
    }

    .drawer-header {
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid #e7e5e4;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }

    :host-context(.dark) .drawer-header {
      border-bottom-color: #292524;
    }

    .header-pre {
      font-size: 0.6875rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      color: #ea580c;
    }

    .drawer-title {
      font-size: 1.125rem;
      font-weight: 600;
      color: #1c1917;
      margin: 0.125rem 0 0;
    }

    :host-context(.dark) .drawer-title {
      color: #fafaf9;
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .close-btn {
      width: 32px;
      height: 32px;
      border-radius: 6px;
      border: 1px solid #e7e5e4;
      background: transparent;
      color: #78716c;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.15s;
    }

    .close-btn:hover {
      background: #f5f5f4;
      color: #1c1917;
    }

    :host-context(.dark) .close-btn {
      border-color: #292524;
      color: #a8a29e;
    }

    :host-context(.dark) .close-btn:hover {
      background: #292524;
      color: #fafaf9;
    }

    .drawer-body {
      flex: 1;
      overflow-y: auto;
      padding: 1.25rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .authority-banner {
      display: flex;
      gap: 0.75rem;
      padding: 0.875rem 1rem;
      border-radius: 8px;
      background: #fffbeb;
      border: 1px solid #fef3c7;
      color: #92400e;
    }

    :host-context(.dark) .authority-banner {
      background: rgba(180, 83, 9, 0.15);
      border-color: rgba(180, 83, 9, 0.3);
      color: #fde68a;
    }

    .banner-icon {
      flex-shrink: 0;
      padding-top: 0.125rem;
    }

    .banner-label {
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .banner-text {
      font-size: 0.8125rem;
      margin: 0.125rem 0 0;
      line-height: 1.4;
    }

    .section-card {
      border-radius: 8px;
      border: 1px solid #e7e5e4;
      background: #fafaf9;
      padding: 1rem;
    }

    :host-context(.dark) .section-card {
      background: #201d1b;
      border-color: #292524;
    }

    .section-heading {
      font-size: 0.75rem;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      color: #78716c;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin: 0 0 0.75rem;
    }

    :host-context(.dark) .section-heading {
      color: #a8a29e;
    }

    .meta-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 0.75rem;
    }

    .col-span-2 {
      grid-column: span 2;
    }

    .meta-item {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }

    .meta-key {
      font-size: 0.6875rem;
      color: #a8a29e;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      font-weight: 500;
    }

    .meta-val {
      font-size: 0.875rem;
      color: #292524;
      word-break: break-word;
    }

    :host-context(.dark) .meta-val {
      color: #e7e5e4;
    }

    .justification-text {
      font-size: 0.875rem;
      color: #44403c;
      line-height: 1.5;
      margin: 0;
      background: #ffffff;
      padding: 0.75rem;
      border-radius: 6px;
      border: 1px solid #f5f5f4;
    }

    :host-context(.dark) .justification-text {
      background: #171514;
      border-color: #292524;
      color: #d6d3d1;
    }

    .cancelled-section {
      background: #fdf2f2;
      border-color: #fecaca;
    }

    :host-context(.dark) .cancelled-section {
      background: rgba(239, 68, 68, 0.08);
      border-color: rgba(239, 68, 68, 0.2);
    }

    .decision-section {
      background: #ffffff;
      border-color: #fed7aa;
      box-shadow: 0 1px 3px rgba(234, 88, 12, 0.06);
    }

    :host-context(.dark) .decision-section {
      background: #1e1b19;
      border-color: rgba(234, 88, 12, 0.3);
    }

    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }

    .form-label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #292524;
    }

    :host-context(.dark) .form-label {
      color: #e7e5e4;
    }

    .perm-options-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 0.5rem;
    }

    .perm-option {
      padding: 0.625rem;
      border-radius: 6px;
      border: 1px solid #e7e5e4;
      background: #ffffff;
      text-align: left;
      cursor: pointer;
      transition: all 0.15s;
    }

    .perm-option:hover {
      border-color: #d6d3d1;
      background: #fafaf9;
    }

    .perm-option.active {
      border-color: #ea580c;
      background: #fff7ed;
      box-shadow: 0 0 0 1px #ea580c;
    }

    :host-context(.dark) .perm-option {
      background: #292524;
      border-color: #44403c;
    }

    :host-context(.dark) .perm-option.active {
      border-color: #ea580c;
      background: rgba(234, 88, 12, 0.12);
    }

    .perm-option:disabled {
      opacity: 0.45;
      cursor: not-allowed;
      background: #f5f5f4;
      border-color: #e7e5e4;
    }

    :host-context(.dark) .perm-option:disabled {
      background: #1c1917;
      border-color: #292524;
      opacity: 0.4;
    }

    .perm-opt-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.25rem;
    }

    .perm-title {
      font-size: 0.75rem;
      font-weight: 700;
      color: #1c1917;
    }

    :host-context(.dark) .perm-title {
      color: #fafaf9;
    }

    .perm-desc {
      font-size: 0.6875rem;
      color: #78716c;
      margin: 0;
      line-height: 1.3;
    }

    .form-textarea {
      padding: 0.625rem 0.75rem;
      font-size: 0.8125rem;
      border-radius: 6px;
      border: 1px solid #d6d3d1;
      background: #ffffff;
      color: #1c1917;
      resize: vertical;
    }

    .form-textarea:focus {
      outline: none;
      border-color: #ea580c;
      box-shadow: 0 0 0 1px #ea580c;
    }

    :host-context(.dark) .form-textarea {
      background: #292524;
      border-color: #44403c;
      color: #fafaf9;
    }

    .action-buttons-row {
      display: grid;
      grid-template-columns: 1fr 1.6fr;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }

    .btn-deny {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.375rem;
      padding: 0.5rem 1rem;
      font-size: 0.8125rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #fca5a5;
      background: #fef2f2;
      color: #dc2626;
      cursor: pointer;
      transition: all 0.15s;
    }

    .btn-deny:hover:not(:disabled) {
      background: #fee2e2;
      border-color: #f87171;
    }

    :host-context(.dark) .btn-deny {
      background: rgba(220, 38, 38, 0.15);
      border-color: rgba(220, 38, 38, 0.3);
      color: #fca5a5;
    }

    .btn-approve {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 0.375rem;
      padding: 0.5rem 1rem;
      font-size: 0.8125rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #ea580c;
      background: #ea580c;
      color: #ffffff;
      cursor: pointer;
      transition: all 0.15s;
    }

    .btn-approve:hover:not(:disabled) {
      background: #c2410c;
    }

    .requester-cancel-box {
      margin-top: 0.5rem;
      padding: 0.875rem;
      border-radius: 6px;
      border: 1px dashed #d6d3d1;
      background: #fafaf9;
    }

    :host-context(.dark) .requester-cancel-box {
      border-color: #44403c;
      background: #201d1b;
    }

    .btn-cancel-req {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.375rem 0.75rem;
      font-size: 0.75rem;
      font-weight: 600;
      border-radius: 6px;
      border: 1px solid #d6d3d1;
      background: #ffffff;
      color: #dc2626;
      cursor: pointer;
    }

    :host-context(.dark) .btn-cancel-req {
      background: #292524;
      border-color: #44403c;
    }

    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.75rem;
      font-weight: 600;
      padding: 0.25rem 0.625rem;
      border-radius: 9999px;
    }

    .status-pending {
      background: #fef3c7;
      color: #92400e;
    }

    .status-approved {
      background: #d1fae5;
      color: #065f46;
    }

    .status-denied {
      background: #fee2e2;
      color: #991b1b;
    }

    .status-cancelled {
      background: #f5f5f4;
      color: #78716c;
    }

    :host-context(.dark) .status-pending {
      background: rgba(245, 158, 11, 0.2);
      color: #fcd34d;
    }

    :host-context(.dark) .status-approved {
      background: rgba(16, 185, 129, 0.2);
      color: #6ee7b7;
    }

    :host-context(.dark) .status-denied {
      background: rgba(239, 68, 68, 0.2);
      color: #fca5a5;
    }

    :host-context(.dark) .status-cancelled {
      background: #292524;
      color: #a8a29e;
    }

    .perm-tag {
      display: inline-block;
      font-size: 0.6875rem;
      font-weight: 700;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      width: fit-content;
    }

    .perm-view { background: #e0f2fe; color: #0369a1; }
    .perm-download { background: #ede9fe; color: #6d28d9; }
    .perm-edit { background: #fef3c7; color: #b45309; }

    @keyframes slideInRight {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
  `]
})
export class AccessRequestDetailDrawerComponent {
  request = input<AccessRequestResponseDto | null>(null);
  isOpen = input<boolean>(false);
  isReviewer = input<boolean>(false);

  close = output<void>();
  approve = output<{ id: number; grantedPermissionLevel: AccessPermissionLevel; reviewerNote?: string }>();
  deny = output<{ id: number; reviewerNote?: string }>();
  cancel = output<number>();

  selectedPermission = signal<AccessPermissionLevel>('VIEW');
  reviewerNote = signal<string>('');
  isSubmitting = signal<boolean>(false);

  constructor() {
    effect(() => {
      const req = this.request();
      if (req) {
        this.selectedPermission.set(req.requestedPermissionLevel || 'VIEW');
        this.reviewerNote.set('');
        this.isSubmitting.set(false);
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen()) {
      this.closeDrawer();
    }
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('drawer-backdrop')) {
      this.closeDrawer();
    }
  }

  closeDrawer(): void {
    this.close.emit();
  }

  isLevelAllowed(level: AccessPermissionLevel): boolean {
    const requested = this.request()?.requestedPermissionLevel;
    if (!requested) return true;
    const hierarchy: Record<AccessPermissionLevel, number> = {
      VIEW: 1,
      DOWNLOAD: 2,
      EDIT: 3,
    };
    return (hierarchy[level] ?? 1) <= (hierarchy[requested] ?? 3);
  }

  onApprove(id: number): void {
    if (!this.isLevelAllowed(this.selectedPermission())) {
      return;
    }
    this.isSubmitting.set(true);
    this.approve.emit({
      id,
      grantedPermissionLevel: this.selectedPermission(),
      reviewerNote: this.reviewerNote().trim() || undefined,
    });
  }

  onDeny(id: number): void {
    this.isSubmitting.set(true);
    this.deny.emit({
      id,
      reviewerNote: this.reviewerNote().trim() || undefined,
    });
  }

  onCancel(id: number): void {
    this.isSubmitting.set(true);
    this.cancel.emit(id);
  }

  formatDate(iso?: string): string {
    if (!iso) return '—';
    try {
      const date = new Date(iso);
      return date.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return iso;
    }
  }
}
