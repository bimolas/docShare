import { Component, ChangeDetectionStrategy, input } from '@angular/core';


export type LoadingType = 'spinner' | 'dots' | 'bar' | 'card';
export type LoadingSize = 'sm' | 'md' | 'lg';

/**
 * LoadingStateComponent: Shared UI primitive for loading indicators, full-screen spinners, and skeletal page states.
 * Fully supports SecureDoc design system tokens and WCAG live region guidelines.
 */
@Component({
  selector: 'sd-loading-state',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="sd-loading-container"
      [class.sd-loading-container--fullscreen]="fullScreen()"
      [class]="sizeClass()"
      role="status"
      aria-live="polite"
    >
      <div class="sd-loading-content">
        @switch (type()) {
          @case ('spinner') {
            <div class="sd-spinner" aria-hidden="true">
              <svg class="sd-spinner-svg" viewBox="0 0 50 50">
                <circle
                  class="sd-spinner-path"
                  cx="25"
                  cy="25"
                  r="20"
                  fill="none"
                  stroke-width="5"
                ></circle>
              </svg>
            </div>
          }
          @case ('dots') {
            <div class="sd-loading-dots" aria-hidden="true">
              <span class="sd-dot"></span>
              <span class="sd-dot"></span>
              <span class="sd-dot"></span>
            </div>
          }
          @case ('bar') {
            <div class="sd-loading-bar-wrapper" aria-hidden="true">
              <div class="sd-loading-bar-fill"></div>
            </div>
          }
          @case ('card') {
            <div class="sd-loading-card-skeleton" aria-hidden="true">
              <div class="sd-skeleton-header"></div>
              <div class="sd-skeleton-line short"></div>
              <div class="sd-skeleton-line long"></div>
            </div>
          }
        }

        @if (message()) {
          <p class="sd-loading-message">{{ message() }}</p>
        }
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-loading-container {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: var(--sd-space-xl);
      width: 100%;
    }

    .sd-loading-container--fullscreen {
      position: fixed;
      inset: 0;
      z-index: 1000;
      background-color: var(--sd-color-backdrop);
      backdrop-filter: blur(4px);
    }

    .sd-loading-content {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: var(--sd-space-md);
      text-align: center;
    }

    /* ── Spinner ─────────────────────────────────────────────────── */
    .sd-spinner {
      display: inline-block;
    }

    .sd-spinner-svg {
      animation: sd-spin 1.4s linear infinite;
      transform-origin: center center;
    }

    .sd-spinner-path {
      stroke: var(--sd-color-primary);
      stroke-linecap: round;
      animation: sd-dash 1.4s ease-in-out infinite;
    }

    .sd-loading--sm .sd-spinner-svg { width: 24px; height: 24px; }
    .sd-loading--md .sd-spinner-svg { width: 40px; height: 40px; }
    .sd-loading--lg .sd-spinner-svg { width: 56px; height: 56px; }

    @keyframes sd-spin {
      100% { transform: rotate(360deg); }
    }

    @keyframes sd-dash {
      0% {
        stroke-dasharray: 1, 200;
        stroke-dashoffset: 0;
      }
      50% {
        stroke-dasharray: 89, 200;
        stroke-dashoffset: -35;
      }
      100% {
        stroke-dasharray: 89, 200;
        stroke-dashoffset: -124;
      }
    }

    /* ── Dots ────────────────────────────────────────────────────── */
    .sd-loading-dots {
      display: flex;
      align-items: center;
      gap: var(--sd-space-xs);
    }

    .sd-dot {
      border-radius: 50%;
      background-color: var(--sd-color-primary);
      animation: sd-bounce 1.4s infinite ease-in-out both;
    }

    .sd-loading--sm .sd-dot { width: 6px; height: 6px; }
    .sd-loading--md .sd-dot { width: 10px; height: 10px; }
    .sd-loading--lg .sd-dot { width: 14px; height: 14px; }

    .sd-dot:nth-child(1) { animation-delay: -0.32s; }
    .sd-dot:nth-child(2) { animation-delay: -0.16s; }

    @keyframes sd-bounce {
      0%, 80%, 100% { transform: scale(0); }
      40% { transform: scale(1.0); }
    }

    /* ── Bar ─────────────────────────────────────────────────────── */
    .sd-loading-bar-wrapper {
      width: 200px;
      height: 4px;
      background-color: var(--sd-color-surface-hover);
      border-radius: var(--sd-radius-full);
      overflow: hidden;
      position: relative;
    }

    .sd-loading-bar-fill {
      position: absolute;
      top: 0;
      left: 0;
      bottom: 0;
      background-color: var(--sd-color-primary);
      animation: sd-bar-slide 1.5s infinite ease-in-out;
    }

    @keyframes sd-bar-slide {
      0% { left: -35%; width: 35%; }
      60% { left: 100%; width: 100%; }
      100% { left: 100%; width: 100%; }
    }

    /* ── Card Skeleton ───────────────────────────────────────────── */
    .sd-loading-card-skeleton {
      width: 280px;
      padding: var(--sd-space-lg);
      background-color: var(--sd-color-surface);
      border: 1px solid var(--sd-color-border-subtle);
      border-radius: var(--sd-radius-lg);
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-sm);
    }

    .sd-skeleton-header {
      height: 20px;
      width: 60%;
      background: linear-gradient(90deg, var(--sd-color-surface-hover) 25%, var(--sd-color-surface) 50%, var(--sd-color-surface-hover) 75%);
      background-size: 200% 100%;
      animation: sd-shimmer 1.5s infinite;
      border-radius: var(--sd-radius-sm);
    }

    .sd-skeleton-line {
      height: 12px;
      background: linear-gradient(90deg, var(--sd-color-surface-hover) 25%, var(--sd-color-surface) 50%, var(--sd-color-surface-hover) 75%);
      background-size: 200% 100%;
      animation: sd-shimmer 1.5s infinite;
      border-radius: var(--sd-radius-sm);
    }

    .sd-skeleton-line.short { width: 40%; }
    .sd-skeleton-line.long { width: 85%; }

    @keyframes sd-shimmer {
      0% { background-position: -200% 0; }
      100% { background-position: 200% 0; }
    }

    /* ── Message ─────────────────────────────────────────────────── */
    .sd-loading-message {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-sm);
      color: var(--sd-color-text-secondary);
      margin: 0;
    }
  `],
})
export class LoadingStateComponent {
  type = input<LoadingType>('spinner');
  message = input<string | undefined>('Loading...');
  size = input<LoadingSize>('md');
  fullScreen = input<boolean>(false);

  sizeClass(): string {
    return `sd-loading--${this.size()}`;
  }
}
