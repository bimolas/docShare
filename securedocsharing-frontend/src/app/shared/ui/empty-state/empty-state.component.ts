import { Component, ChangeDetectionStrategy, input } from '@angular/core';


@Component({
  selector: 'sd-empty-state',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="sd-empty-state cloudy-border-surface"
      [class.sd-empty-state--compact]="compact()"
    >
      <div class="sd-empty-state-icon">
        <ng-content select="[icon]"></ng-content>
      </div>

      <div class="sd-empty-state-content">
        <h4 class="sd-empty-state-title">{{ title() }}</h4>
        @if (description()) {
          <p class="sd-empty-state-description">{{ description() }}</p>
        }
      </div>

      <div class="sd-empty-state-actions">
        <ng-content select="[actions]"></ng-content>
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: var(--sd-space-3xl) var(--sd-space-xl);
      gap: var(--sd-space-md);
      width: 100%;
    }

    .sd-empty-state--compact {
      padding: var(--sd-space-xl) var(--sd-space-lg);
      gap: var(--sd-space-sm);
    }

    .sd-empty-state-icon {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: var(--sd-radius-full);
      background-color: var(--sd-color-primary-subtle);
      color: var(--sd-color-primary);
    }

    .sd-empty-state--compact .sd-empty-state-icon {
      width: 44px;
      height: 44px;
    }

    .sd-empty-state-content {
      max-width: 420px;
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-xs);
    }

    .sd-empty-state-title {
      font-size: var(--sd-font-size-lg);
      font-weight: var(--sd-weight-semibold);
      color: var(--sd-color-text-primary);
      margin: 0;
    }

    .sd-empty-state-description {
      font-size: var(--sd-font-size-sm);
      color: var(--sd-color-text-secondary);
      margin: 0;
      line-height: var(--sd-leading-relaxed);
    }

    .sd-empty-state-actions:has(*) {
      display: flex;
      align-items: center;
      gap: var(--sd-space-sm);
      margin-top: var(--sd-space-xs);
    }
  `]
})
export class EmptyStateComponent {
  title = input.required<string>();
  description = input<string | undefined>();
  icon = input<string | undefined>();
  compact = input<boolean>(false);
}
