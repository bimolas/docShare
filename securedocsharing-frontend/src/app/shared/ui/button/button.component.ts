import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';


export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'sd-button, button[sd-button], a[sd-button]',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      [class]="buttonClasses()"
      (click)="onClick($event)"
    >
      @if (loading()) {
        <svg class="sd-spinner" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      }
      <span class="sd-button-content" [class.opacity-0]="loading()">
        <ng-content></ng-content>
      </span>
    </button>
  `,
  styles: [`
    :host {
      display: inline-block;
    }

    :host(.w-full), :host([fullWidth="true"]) {
      display: block;
      width: 100%;
    }

    .sd-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--sd-space-sm);
      font-family: var(--sd-font-sans);
      font-weight: var(--sd-weight-medium);
      line-height: 1;
      white-space: nowrap;
      border: 1px solid transparent;
      cursor: pointer;
      user-select: none;
      transition: background-color var(--sd-duration-fast) var(--sd-ease-standard),
                  border-color var(--sd-duration-fast) var(--sd-ease-standard),
                  color var(--sd-duration-fast) var(--sd-ease-standard),
                  box-shadow var(--sd-duration-fast) var(--sd-ease-standard),
                  transform var(--sd-duration-instant) var(--sd-ease-standard);
      position: relative;
      outline: none;
    }

    .sd-btn:active:not(:disabled) {
      transform: translateY(1px);
    }

    .sd-btn:focus-visible {
      outline: 2px solid var(--sd-color-brand-focus);
      outline-offset: 2px;
    }

    .sd-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      box-shadow: none !important;
      transform: none !important;
    }

    /* ── Sizes ────────────────────────────────────────────────────── */
    .sd-btn--sm {
      height: var(--sd-size-control-sm);
      padding: 0 var(--sd-space-md);
      font-size: var(--sd-font-size-xs);
      border-radius: var(--sd-radius-md);
    }

    .sd-btn--md {
      height: var(--sd-size-control-md);
      padding: 0 var(--sd-space-lg);
      font-size: var(--sd-font-size-base);
      border-radius: var(--sd-radius-md);
    }

    .sd-btn--lg {
      height: var(--sd-size-control-lg);
      padding: 0 var(--sd-space-xl);
      font-size: var(--sd-font-size-base);
      border-radius: var(--sd-radius-lg);
    }

    .sd-btn--full {
      width: 100%;
    }

    /* ── Variants ─────────────────────────────────────────────────── */
    /* Primary: #c93c00 for 5.02:1 WCAG AA contrast against white text */
    .sd-btn--primary {
      background-color: var(--sd-color-primary);
      color: var(--sd-color-text-inverse);
      box-shadow: var(--sd-shadow-sm);
    }
    .sd-btn--primary:hover:not(:disabled) {
      background-color: var(--sd-color-primary-hover);
    }
    .sd-btn--primary:active:not(:disabled) {
      background-color: var(--sd-color-primary-active);
    }

    /* Secondary */
    .sd-btn--secondary {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-primary);
      border-color: var(--sd-color-border);
    }
    .sd-btn--secondary:hover:not(:disabled) {
      background-color: var(--sd-color-border-subtle);
      border-color: var(--sd-color-border-hover);
    }

    /* Outline */
    .sd-btn--outline {
      background-color: transparent;
      color: var(--sd-color-text-primary);
      border-color: var(--sd-color-border);
    }
    .sd-btn--outline:hover:not(:disabled) {
      background-color: var(--sd-color-surface-hover);
      border-color: var(--sd-color-border-hover);
    }

    /* Ghost */
    .sd-btn--ghost {
      background-color: transparent;
      color: var(--sd-color-text-secondary);
      border-color: transparent;
    }
    .sd-btn--ghost:hover:not(:disabled) {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-primary);
    }

    /* Danger */
    .sd-btn--danger {
      background-color: var(--sd-color-danger);
      color: var(--sd-color-text-inverse);
    }
    .sd-btn--danger:hover:not(:disabled) {
      background-color: #b91c1c;
    }

    /* ── Spinner ──────────────────────────────────────────────────── */
    .sd-spinner {
      position: absolute;
      width: 1.1em;
      height: 1.1em;
      animation: sd-spin 0.8s linear infinite;
    }

    @keyframes sd-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }

    .sd-button-content {
      display: inline-flex;
      align-items: center;
      gap: var(--sd-space-sm);
    }
  `],
})
export class ButtonComponent {
  variant = input<ButtonVariant>('primary');
  size = input<ButtonSize>('md');
  fullWidth = input<boolean>(false);
  disabled = input<boolean>(false);
  loading = input<boolean>(false);
  type = input<'button' | 'submit' | 'reset'>('button');

  clicked = output<MouseEvent>();

  buttonClasses(): string {
    const classes = [
      'sd-btn',
      `sd-btn--${this.variant()}`,
      `sd-btn--${this.size()}`,
    ];
    if (this.fullWidth()) {
      classes.push('sd-btn--full');
    }
    return classes.join(' ');
  }

  onClick(event: MouseEvent): void {
    if (!this.disabled() && !this.loading()) {
      this.clicked.emit(event);
    }
  }
}
