import { Component, ChangeDetectionStrategy, input } from '@angular/core';


export type StatusIndicatorState =
  | 'online'
  | 'offline'
  | 'busy'
  | 'away'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'encrypted'
  | 'pending';

export type StatusIndicatorSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'sd-status-indicator',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="sd-status-indicator"
      role="status"
      [attr.aria-label]="computedLabel()"
    >
      <span class="sd-status-dot-wrapper" [class]="dotSizeClass()">
        <span
          class="sd-status-dot"
          [class]="statusClass()"
        ></span>
        @if (pulse()) {
          <span
            class="sd-status-pulse"
            [class]="statusClass()"
          ></span>
        }
      </span>

      @if (showLabel() && computedLabel()) {
        <span class="sd-status-label" [class]="labelSizeClass()">
          {{ computedLabel() }}
        </span>
      }
    </div>
  `,
  styles: [`
    :host {
      display: inline-flex;
      align-items: center;
    }

    .sd-status-indicator {
      display: inline-flex;
      align-items: center;
      gap: var(--sd-space-xs);
      user-select: none;
    }

    .sd-status-dot-wrapper {
      position: relative;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .sd-status-dot-wrapper--sm { width: 8px; height: 8px; }
    .sd-status-dot-wrapper--md { width: 10px; height: 10px; }
    .sd-status-dot-wrapper--lg { width: 12px; height: 12px; }

    .sd-status-dot {
      width: 100%;
      height: 100%;
      border-radius: 9999px;
      z-index: 1;
    }

    .sd-status-pulse {
      position: absolute;
      inset: -2px;
      border-radius: 9999px;
      opacity: 0.75;
      animation: sd-ping 1.8s cubic-bezier(0, 0, 0.2, 1) infinite;
    }

    /* ── Status Colors ───────────────────────────────────────────── */
    .sd-status--online,
    .sd-status--success {
      background-color: var(--sd-color-success);
    }

    .sd-status--offline {
      background-color: var(--sd-color-text-muted);
    }

    .sd-status--busy,
    .sd-status--danger {
      background-color: var(--sd-color-danger);
    }

    .sd-status--away,
    .sd-status--warning,
    .sd-status--pending {
      background-color: var(--sd-color-warning);
    }

    .sd-status--info {
      background-color: var(--sd-color-info);
    }

    .sd-status--encrypted {
      background-color: var(--sd-color-brand);
    }

    /* ── Label Typography ────────────────────────────────────────── */
    .sd-status-label {
      font-weight: var(--sd-weight-medium);
      color: var(--sd-color-text-secondary);
      line-height: var(--sd-leading-tight);
    }

    .sd-status-label--sm { font-size: var(--sd-font-size-xs); }
    .sd-status-label--md { font-size: var(--sd-font-size-sm); }
    .sd-status-label--lg { font-size: var(--sd-font-size-base); }

    @keyframes sd-ping {
      75%, 100% {
        transform: scale(2);
        opacity: 0;
      }
    }
  `]
})
export class StatusIndicatorComponent {
  status = input<StatusIndicatorState>('online');
  size = input<StatusIndicatorSize>('md');
  pulse = input<boolean>(false);
  label = input<string | undefined>();
  showLabel = input<boolean>(true);

  statusClass(): string {
    return `sd-status--${this.status()}`;
  }

  dotSizeClass(): string {
    return `sd-status-dot-wrapper--${this.size()}`;
  }

  labelSizeClass(): string {
    return `sd-status-label--${this.size()}`;
  }

  computedLabel(): string {
    if (this.label()) {
      return this.label()!;
    }
    // Default fallback capitalized text
    const s = this.status();
    return s.charAt(0).toUpperCase() + s.slice(1);
  }
}
