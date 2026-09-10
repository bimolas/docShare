import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';


export type IconButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'danger';
export type IconButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'sd-icon-button, button[sd-icon-button]',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [attr.aria-label]="ariaLabel()"
      [attr.title]="title() || ariaLabel()"
      [disabled]="disabled() || loading()"
      [class]="buttonClasses()"
      (click)="onClick($event)"
    >
      @if (loading()) {
        <svg class="sd-spinner" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      } @else {
        <ng-content></ng-content>
      }
    </button>
  `,
  styles: [`
    :host {
      display: inline-block;
    }

    .sd-icon-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border: 1px solid transparent;
      cursor: pointer;
      user-select: none;
      transition: background-color var(--sd-duration-fast) var(--sd-ease-standard),
                  border-color var(--sd-duration-fast) var(--sd-ease-standard),
                  color var(--sd-duration-fast) var(--sd-ease-standard),
                  box-shadow var(--sd-duration-fast) var(--sd-ease-standard),
                  transform var(--sd-duration-instant) var(--sd-ease-standard);
      outline: none;
      position: relative;
    }

    .sd-icon-btn:active:not(:disabled) {
      transform: translateY(1px);
    }

    .sd-icon-btn:focus-visible {
      outline: 2px solid var(--sd-color-brand-focus);
      outline-offset: 2px;
    }

    .sd-icon-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      box-shadow: none !important;
      transform: none !important;
    }

    /* ── Sizes (Square aspect ratio) ──────────────────────────────── */
    .sd-icon-btn--sm {
      width: var(--sd-size-control-sm);
      height: var(--sd-size-control-sm);
      border-radius: var(--sd-radius-md);
      font-size: var(--sd-font-size-xs);
    }

    .sd-icon-btn--md {
      width: var(--sd-size-control-md);
      height: var(--sd-size-control-md);
      border-radius: var(--sd-radius-md);
      font-size: var(--sd-font-size-base);
    }

    .sd-icon-btn--lg {
      width: var(--sd-size-control-lg);
      height: var(--sd-size-control-lg);
      border-radius: var(--sd-radius-lg);
      font-size: var(--sd-font-size-lg);
    }

    /* ── Variants ─────────────────────────────────────────────────── */
    .sd-icon-btn--primary {
      background-color: var(--sd-color-primary);
      color: var(--sd-color-text-inverse);
      box-shadow: var(--sd-shadow-sm);
    }
    .sd-icon-btn--primary:hover:not(:disabled) {
      background-color: var(--sd-color-primary-hover);
    }

    .sd-icon-btn--secondary {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-primary);
      border-color: var(--sd-color-border);
    }
    .sd-icon-btn--secondary:hover:not(:disabled) {
      background-color: var(--sd-color-border-subtle);
      border-color: var(--sd-color-border-hover);
    }

    .sd-icon-btn--outline {
      background-color: transparent;
      color: var(--sd-color-text-primary);
      border-color: var(--sd-color-border);
    }
    .sd-icon-btn--outline:hover:not(:disabled) {
      background-color: var(--sd-color-surface-hover);
      border-color: var(--sd-color-border-hover);
    }

    .sd-icon-btn--ghost {
      background-color: transparent;
      color: var(--sd-color-text-secondary);
      border-color: transparent;
    }
    .sd-icon-btn--ghost:hover:not(:disabled) {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-primary);
    }

    .sd-icon-btn--danger {
      background-color: var(--sd-color-danger);
      color: var(--sd-color-text-inverse);
    }
    .sd-icon-btn--danger:hover:not(:disabled) {
      background-color: #b91c1c;
    }

    .sd-spinner {
      width: 1.1em;
      height: 1.1em;
      animation: sd-spin 0.8s linear infinite;
    }

    @keyframes sd-spin {
      from { transform: rotate(0deg); }
      to { transform: rotate(360deg); }
    }
  `],
})
export class IconButtonComponent {
  ariaLabel = input.required<string>();
  title = input<string | undefined>();
  variant = input<IconButtonVariant>('ghost');
  size = input<IconButtonSize>('md');
  disabled = input<boolean>(false);
  loading = input<boolean>(false);
  type = input<'button' | 'submit' | 'reset'>('button');

  clicked = output<MouseEvent>();

  buttonClasses(): string {
    return `sd-icon-btn sd-icon-btn--${this.variant()} sd-icon-btn--${this.size()}`;
  }

  onClick(event: MouseEvent): void {
    if (!this.disabled() && !this.loading()) {
      this.clicked.emit(event);
    }
  }
}
