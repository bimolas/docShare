import { Component, ChangeDetectionStrategy, input, output, signal, OnInit, OnDestroy } from '@angular/core';

import { IconButtonComponent } from '../icon-button/icon-button.component';

export type ToastType = 'success' | 'error' | 'warning' | 'info';

@Component({
  selector: 'sd-toast',
  standalone: true,
  imports: [IconButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="sd-toast"
      [class]="toastClasses()"
      role="alert"
      aria-live="polite"
    >
      <div class="sd-toast-icon">
        @switch (type()) {
          @case ('success') {
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          }
          @case ('error') {
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
          }
          @case ('warning') {
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126zM12 15.75h.008v.008H12v-.008z" />
            </svg>
          }
          @default {
            <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M11.25 11.25l.041-.02a.75.75 0 011.063.852l-.708 2.836a.75.75 0 001.063.853l.041-.021M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9-3.75h.008v.008H12v-.008z" />
            </svg>
          }
        }
      </div>

      <div class="sd-toast-content">
        @if (title()) {
          <h5 class="sd-toast-title">{{ title() }}</h5>
        }
        <p class="sd-toast-message">{{ message() }}</p>
      </div>

      @if (dismissible()) {
        <sd-icon-button
          ariaLabel="Dismiss notification"
          variant="ghost"
          size="sm"
          (clicked)="dismiss()"
        >
          <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M18 6L6 18M6 6l12 12"/>
          </svg>
        </sd-icon-button>
      }

      @if (duration() > 0) {
        <div class="sd-toast-progress-bar">
          <div
            class="sd-toast-progress-fill"
            [style.animationDuration]="duration() + 'ms'"
          ></div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      max-width: 400px;
    }

    .sd-toast {
      position: relative;
      display: flex;
      align-items: flex-start;
      gap: var(--sd-space-md);
      padding: var(--sd-space-md) var(--sd-space-lg);
      background-color: var(--sd-color-surface-elevated);
      border-radius: var(--sd-radius-lg);
      box-shadow: var(--sd-shadow-floating);
      border: 1px solid var(--sd-color-border-subtle);
      overflow: hidden;
      animation: sd-toast-enter var(--sd-duration-normal) var(--sd-ease-spring);
    }

    /* ── Types ────────────────────────────────────────────────────── */
    .sd-toast--success {
      border-left: 4px solid var(--sd-color-success);
    }
    .sd-toast--success .sd-toast-icon {
      color: var(--sd-color-success);
    }

    .sd-toast--error {
      border-left: 4px solid var(--sd-color-danger);
    }
    .sd-toast--error .sd-toast-icon {
      color: var(--sd-color-danger);
    }

    .sd-toast--warning {
      border-left: 4px solid var(--sd-color-warning);
    }
    .sd-toast--warning .sd-toast-icon {
      color: var(--sd-color-warning);
    }

    .sd-toast--info {
      border-left: 4px solid var(--sd-color-info);
    }
    .sd-toast--info .sd-toast-icon {
      color: var(--sd-color-info);
    }

    .sd-toast-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .sd-toast-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-3xs);
    }

    .sd-toast-title {
      font-size: var(--sd-font-size-sm);
      font-weight: var(--sd-weight-semibold);
      color: var(--sd-color-text-primary);
      margin: 0;
    }

    .sd-toast-message {
      font-size: var(--sd-font-size-xs);
      color: var(--sd-color-text-secondary);
      margin: 0;
      line-height: var(--sd-leading-normal);
    }

    .sd-toast-progress-bar {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
      height: 3px;
      background-color: var(--sd-color-surface-hover);
    }

    .sd-toast-progress-fill {
      height: 100%;
      width: 100%;
      background-color: currentColor;
      opacity: 0.6;
      animation: sd-toast-shrink linear forwards;
    }

    .sd-toast--success .sd-toast-progress-fill { background-color: var(--sd-color-success); }
    .sd-toast--error .sd-toast-progress-fill { background-color: var(--sd-color-danger); }
    .sd-toast--warning .sd-toast-progress-fill { background-color: var(--sd-color-warning); }
    .sd-toast--info .sd-toast-progress-fill { background-color: var(--sd-color-info); }

    @keyframes sd-toast-enter {
      from {
        opacity: 0;
        transform: translateY(12px) scale(0.96);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }

    @keyframes sd-toast-shrink {
      from { transform: translateX(0); }
      to { transform: translateX(-100%); }
    }
  `]
})
export class ToastComponent implements OnInit, OnDestroy {
  type = input<ToastType>('info');
  title = input<string | undefined>();
  message = input.required<string>();
  duration = input<number>(5000);
  dismissible = input<boolean>(true);

  dismissed = output<void>();

  private timerId: any = null;

  toastClasses(): string {
    return `sd-toast--${this.type()}`;
  }

  ngOnInit(): void {
    if (this.duration() > 0) {
      this.timerId = setTimeout(() => {
        this.dismiss();
      }, this.duration());
    }
  }

  dismiss(): void {
    this.clearTimer();
    this.dismissed.emit();
  }

  private clearTimer(): void {
    if (this.timerId) {
      clearTimeout(this.timerId);
      this.timerId = null;
    }
  }

  ngOnDestroy(): void {
    this.clearTimer();
  }
}
