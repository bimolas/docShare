import { Component, ChangeDetectionStrategy, input } from '@angular/core';


export type BadgeVariant =
  | 'internal'
  | 'external'
  | 'trusted'
  | 'restricted'
  | 'confidential'
  | 'success'
  | 'warning'
  | 'danger'
  | 'info'
  | 'neutral'
  | 'primary';

export type BadgeSize = 'sm' | 'md';

@Component({
  selector: 'sd-badge',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span [class]="badgeClasses()">
      @if (dot()) {
        <span class="sd-badge-dot"></span>
      }
      <ng-content></ng-content>
    </span>
  `,
  styles: [`
    :host {
      display: inline-flex;
    }

    .sd-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-family: var(--sd-font-sans);
      font-weight: var(--sd-weight-medium);
      line-height: 1;
      white-space: nowrap;
      border-radius: var(--sd-radius-full);
      border: 1px solid transparent;
      user-select: none;
    }

    /* ── Sizes ────────────────────────────────────────────────────── */
    .sd-badge--sm {
      padding: 2px 8px;
      font-size: var(--sd-font-size-xs);
    }

    .sd-badge--md {
      padding: 4px 10px;
      font-size: var(--sd-font-size-xs);
    }

    .sd-badge-dot {
      width: 6px;
      height: 6px;
      border-radius: var(--sd-radius-full);
      background-color: currentColor;
      flex-shrink: 0;
    }

    /* ── Governance & Security Classifications ────────────────────── */
    .sd-badge--internal {
      background-color: var(--sd-color-sec-internal-bg);
      color: var(--sd-color-sec-internal-text);
      border-color: var(--sd-color-sec-internal-border);
    }

    .sd-badge--external {
      background-color: var(--sd-color-sec-external-bg);
      color: var(--sd-color-sec-external-text);
      border-color: var(--sd-color-sec-external-border);
    }

    .sd-badge--trusted {
      background-color: var(--sd-color-sec-trusted-bg);
      color: var(--sd-color-sec-trusted-text);
      border-color: var(--sd-color-sec-trusted-border);
    }

    .sd-badge--restricted {
      background-color: var(--sd-color-sec-restricted-bg);
      color: var(--sd-color-sec-restricted-text);
      border-color: var(--sd-color-sec-restricted-border);
    }

    .sd-badge--confidential {
      background-color: var(--sd-color-sec-confidential-bg);
      color: var(--sd-color-sec-confidential-text);
      border-color: var(--sd-color-sec-confidential-border);
    }

    /* ── Status Semantics ─────────────────────────────────────────── */
    .sd-badge--success {
      background-color: var(--sd-color-success-subtle);
      color: var(--sd-color-success);
      border-color: var(--sd-color-success-border);
    }

    .sd-badge--warning {
      background-color: var(--sd-color-warning-subtle);
      color: var(--sd-color-warning);
      border-color: var(--sd-color-warning-border);
    }

    .sd-badge--danger {
      background-color: var(--sd-color-danger-subtle);
      color: var(--sd-color-danger);
      border-color: var(--sd-color-danger-border);
    }

    .sd-badge--info {
      background-color: var(--sd-color-info-subtle);
      color: var(--sd-color-info);
      border-color: var(--sd-color-info-border);
    }

    .sd-badge--neutral {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-secondary);
      border-color: var(--sd-color-border);
    }

    .sd-badge--primary {
      background-color: var(--sd-color-primary-subtle);
      color: var(--sd-color-text-brand);
      border-color: var(--sd-color-primary-border);
    }
  `],
})
export class BadgeComponent {
  variant = input<BadgeVariant>('neutral');
  size = input<BadgeSize>('md');
  dot = input<boolean>(false);

  badgeClasses(): string {
    return `sd-badge sd-badge--${this.variant()} sd-badge--${this.size()}`;
  }
}
