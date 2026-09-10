import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';

import { ModalComponent, ModalSize } from './modal.component';
import { ButtonComponent } from '../button/button.component';

export type ConfirmationVariant = 'danger' | 'warning' | 'primary';

/**
 * ConfirmationModalComponent: Specialized dialog primitive for prompt & action confirmation flows.
 * Built on top of ModalComponent and ButtonComponent design tokens.
 */
@Component({
  selector: 'sd-confirmation-modal',
  standalone: true,
  imports: [ModalComponent, ButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sd-modal
      [open]="open()"
      [size]="size()"
      [closeOnBackdrop]="!loading()"
      (closed)="onCancel()"
    >
      <div class="sd-confirm-body">
        <div class="sd-confirm-icon-wrapper" [class]="iconWrapperClass()">
          @switch (variant()) {
            @case ('danger') {
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"/>
                <line x1="12" y1="9" x2="12" y2="13"/>
                <line x1="12" y1="17" x2="12.01" y2="17"/>
              </svg>
            }
            @case ('warning') {
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="8" x2="12" y2="12"/>
                <line x1="12" y1="16" x2="12.01" y2="16"/>
              </svg>
            }
            @default {
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="12" y1="16" x2="12" y2="12"/>
                <line x1="12" y1="8" x2="12.01" y2="8"/>
              </svg>
            }
          }
        </div>

        <div class="sd-confirm-text">
          <h4 class="sd-confirm-title">{{ title() }}</h4>
          <p class="sd-confirm-message">{{ message() }}</p>
        </div>
      </div>

      <div modal-footer class="sd-confirm-actions">
        <sd-button
          variant="secondary"
          [disabled]="loading()"
          (clicked)="onCancel()"
        >
          {{ cancelText() }}
        </sd-button>

        <sd-button
          [variant]="buttonVariant()"
          [loading]="loading()"
          (clicked)="onConfirm()"
        >
          {{ confirmText() }}
        </sd-button>
      </div>
    </sd-modal>
  `,
  styles: [`
    .sd-confirm-body {
      display: flex;
      align-items: flex-start;
      gap: var(--sd-space-lg);
      padding: var(--sd-space-xs) 0;
    }

    .sd-confirm-icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: var(--sd-radius-lg);
      flex-shrink: 0;
    }

    .sd-confirm-icon--danger {
      background-color: var(--sd-color-danger-subtle);
      color: var(--sd-color-danger);
    }

    .sd-confirm-icon--warning {
      background-color: var(--sd-color-warning-subtle);
      color: var(--sd-color-warning);
    }

    .sd-confirm-icon--primary {
      background-color: var(--sd-color-primary-subtle);
      color: var(--sd-color-primary);
    }

    .sd-confirm-text {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-xs);
    }

    .sd-confirm-title {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-lg);
      font-weight: var(--sd-weight-semibold);
      color: var(--sd-color-text-primary);
      margin: 0;
      line-height: var(--sd-leading-snug);
    }

    .sd-confirm-message {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-sm);
      color: var(--sd-color-text-secondary);
      margin: 0;
      line-height: var(--sd-leading-normal);
    }

    .sd-confirm-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: var(--sd-space-md);
      width: 100%;
    }
  `],
})
export class ConfirmationModalComponent {
  open = input<boolean>(false);
  title = input<string>('Confirm Action');
  message = input<string>('Are you sure you want to proceed?');
  confirmText = input<string>('Confirm');
  cancelText = input<string>('Cancel');
  variant = input<ConfirmationVariant>('danger');
  loading = input<boolean>(false);
  size = input<ModalSize>('sm');

  confirmed = output<void>();
  cancelled = output<void>();

  iconWrapperClass(): string {
    return `sd-confirm-icon-wrapper sd-confirm-icon--${this.variant()}`;
  }

  buttonVariant(): 'danger' | 'primary' {
    return this.variant() === 'danger' ? 'danger' : 'primary';
  }

  onConfirm(): void {
    if (!this.loading()) {
      this.confirmed.emit();
    }
  }

  onCancel(): void {
    if (!this.loading()) {
      this.cancelled.emit();
    }
  }
}
