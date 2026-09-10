import { Component, ChangeDetectionStrategy, input } from '@angular/core';


export type SkeletonVariant = 'text' | 'circular' | 'rectangular' | 'avatar' | 'card';

@Component({
  selector: 'sd-skeleton',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="status"
      aria-busy="true"
      aria-live="polite"
      class="sd-skeleton-group"
    >
      <span class="sr-only">Loading content...</span>
      @for (item of itemsArray(); track $index) {
        <div
          class="sd-skeleton"
          [class]="skeletonClasses()"
          [style.width]="width()"
          [style.height]="height()"
        ></div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-skeleton-group {
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-xs);
      width: 100%;
    }

    .sd-skeleton {
      background-color: var(--sd-color-surface-hover);
      position: relative;
      overflow: hidden;
    }

    .sd-skeleton--animated::after {
      content: '';
      position: absolute;
      inset: 0;
      transform: translateX(-100%);
      background: linear-gradient(
        90deg,
        transparent 0%,
        rgba(255, 255, 255, 0.6) 50%,
        transparent 100%
      );
      animation: sd-shimmer 1.6s infinite;
    }

    /* ── Variants ─────────────────────────────────────────────────── */
    .sd-skeleton--text {
      height: 1em;
      border-radius: var(--sd-radius-sm);
      width: 100%;
    }

    .sd-skeleton--circular,
    .sd-skeleton--avatar {
      border-radius: 9999px;
      width: 40px;
      height: 40px;
    }

    .sd-skeleton--rectangular {
      border-radius: var(--sd-radius-md);
      height: 120px;
      width: 100%;
    }

    .sd-skeleton--card {
      border-radius: var(--sd-radius-lg);
      height: 180px;
      width: 100%;
    }

    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border-width: 0;
    }

    @keyframes sd-shimmer {
      100% {
        transform: translateX(100%);
      }
    }
  `]
})
export class SkeletonComponent {
  variant = input<SkeletonVariant>('text');
  width = input<string | undefined>();
  height = input<string | undefined>();
  count = input<number>(1);
  animated = input<boolean>(true);

  itemsArray(): number[] {
    return Array.from({ length: Math.max(1, this.count()) });
  }

  skeletonClasses(): string {
    const base = `sd-skeleton--${this.variant()}`;
    return this.animated() ? `${base} sd-skeleton--animated` : base;
  }
}
