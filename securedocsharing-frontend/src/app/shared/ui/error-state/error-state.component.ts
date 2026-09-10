import { Component, ChangeDetectionStrategy, input, output, signal } from '@angular/core';

import { ButtonComponent } from '../button/button.component';
import { IconComponent } from '../../components/icon/icon.component';

@Component({
  selector: 'sd-error-state',
  standalone: true,
  imports: [ButtonComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sd-error-state" role="alert">
      <div class="sd-error-icon">
        <app-icon name="circle-alert" [size]="22" />
      </div>

      <div class="sd-error-content">
        <div class="sd-error-header">
          <h4 class="sd-error-title">{{ title() }}</h4>
          @if (code()) {
            <span class="sd-error-code">Error {{ code() }}</span>
          }
        </div>

        @if (message()) {
          <p class="sd-error-message">{{ message() }}</p>
        }

        @if (details()) {
          <button
            type="button"
            class="sd-error-details-toggle"
            (click)="toggleDetails()"
            [attr.aria-expanded]="showDetails()"
          >
            <span>{{ showDetails() ? 'Hide technical details' : 'Show technical details' }}</span>
            <app-icon
              name="chevron-down"
              [size]="14"
              class="transform transition-transform"
              [class.rotate-180]="showDetails()"
            />
          </button>

          @if (showDetails()) {
            <pre class="sd-error-details-box custom-scrollbar"><code>{{ details() }}</code></pre>
          }
        }

        @if (showRetry()) {
          <div class="sd-error-actions">
            <sd-button
              variant="danger"
              size="sm"
              (clicked)="onRetry()"
            >
              <app-icon name="refresh-cw" [size]="14" class="mr-1.5" />
              {{ retryLabel() }}
            </sd-button>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-error-state {
      display: flex;
      align-items: flex-start;
      gap: var(--sd-space-md);
      padding: var(--sd-space-lg);
      background-color: var(--sd-color-danger-subtle);
      border: 1px solid var(--sd-color-danger-border);
      border-radius: var(--sd-radius-lg);
      color: var(--sd-color-text-primary);
    }

    .sd-error-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 40px;
      height: 40px;
      border-radius: var(--sd-radius-md);
      background-color: var(--sd-color-surface);
      color: var(--sd-color-danger);
      flex-shrink: 0;
      box-shadow: var(--sd-shadow-sm);
    }

    .sd-error-content {
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-xs);
      flex: 1;
    }

    .sd-error-header {
      display: flex;
      align-items: center;
      gap: var(--sd-space-sm);
      flex-wrap: wrap;
    }

    .sd-error-title {
      font-size: var(--sd-font-size-base);
      font-weight: var(--sd-weight-semibold);
      color: var(--sd-color-danger);
      margin: 0;
    }

    .sd-error-code {
      font-family: var(--sd-font-mono);
      font-size: var(--sd-font-size-xs);
      background-color: rgba(220, 38, 38, 0.1);
      color: var(--sd-color-danger);
      padding: 1px 6px;
      border-radius: var(--sd-radius-sm);
      font-weight: var(--sd-weight-medium);
    }

    .sd-error-message {
      font-size: var(--sd-font-size-sm);
      color: var(--sd-color-text-secondary);
      margin: 0;
      line-height: var(--sd-leading-normal);
    }

    .sd-error-details-toggle {
      display: inline-flex;
      align-items: center;
      gap: var(--sd-space-xs);
      background: none;
      border: none;
      color: var(--sd-color-text-muted);
      font-size: var(--sd-font-size-xs);
      font-weight: var(--sd-weight-medium);
      cursor: pointer;
      padding: 0;
      margin-top: var(--sd-space-2xs);
      outline: none;
    }

    .sd-error-details-toggle:hover {
      color: var(--sd-color-text-primary);
    }

    .sd-error-details-box {
      background-color: var(--sd-color-surface);
      border: 1px solid var(--sd-color-border);
      border-radius: var(--sd-radius-md);
      padding: var(--sd-space-sm);
      font-family: var(--sd-font-mono);
      font-size: var(--sd-font-size-xs);
      color: var(--sd-color-text-secondary);
      max-height: 160px;
      overflow-y: auto;
      margin-top: var(--sd-space-2xs);
      white-space: pre-wrap;
      word-break: break-word;
    }

    .sd-error-actions {
      margin-top: var(--sd-space-sm);
    }
  `]
})
export class ErrorStateComponent {
  title = input<string>('Something went wrong');
  message = input<string | undefined>();
  code = input<string | number | undefined>();
  details = input<string | undefined>();
  showRetry = input<boolean>(true);
  retryLabel = input<string>('Try Again');

  retry = output<void>();

  showDetails = signal<boolean>(false);

  toggleDetails(): void {
    this.showDetails.update(v => !v);
  }

  onRetry(): void {
    this.retry.emit();
  }
}
