import { Component, ChangeDetectionStrategy, input } from '@angular/core';


export type CardVariant = 'cloudy' | 'surface' | 'flat';
export type CardPadding = 'none' | 'sm' | 'md' | 'lg';

@Component({
  selector: 'sd-card',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [class]="cardClasses()">
      <ng-content></ng-content>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-card {
      border-radius: var(--sd-radius-lg);
      transition: border-color var(--sd-duration-fast) var(--sd-ease-spring),
                  box-shadow var(--sd-duration-fast) var(--sd-ease-spring),
                  transform var(--sd-duration-fast) var(--sd-ease-spring);
    }

    /* ── Variants ─────────────────────────────────────────────────── */
    .sd-card--cloudy {
      background: var(--sd-color-surface);
      border: 1px solid var(--sd-color-border-subtle);
      box-shadow: var(--sd-shadow-card);
    }
    .sd-card--cloudy.sd-card--interactive:hover {
      border-color: var(--sd-color-primary-border);
      box-shadow: var(--sd-shadow-floating);
    }

    .sd-card--surface {
      background: var(--sd-color-surface);
      border: 1px solid var(--sd-color-border);
      box-shadow: var(--sd-shadow-sm);
    }
    .sd-card--surface.sd-card--interactive:hover {
      border-color: var(--sd-color-border-hover);
      box-shadow: var(--sd-shadow-card);
    }

    .sd-card--flat {
      background: var(--sd-color-bg);
      border: 1px solid var(--sd-color-border-subtle);
    }
    .sd-card--flat.sd-card--interactive:hover {
      background: var(--sd-color-surface-hover);
      border-color: var(--sd-color-border);
    }

    /* ── Padding Scale ────────────────────────────────────────────── */
    .sd-card--padding-none { padding: 0; }
    .sd-card--padding-sm { padding: var(--sd-space-sm); }
    .sd-card--padding-md { padding: var(--sd-space-lg); }
    .sd-card--padding-lg { padding: var(--sd-space-xl); }
  `],
})
export class CardComponent {
  variant = input<CardVariant>('cloudy');
  padding = input<CardPadding>('md');
  interactive = input<boolean>(false);

  cardClasses(): string {
    const classes = [
      'sd-card',
      `sd-card--${this.variant()}`,
      `sd-card--padding-${this.padding()}`,
    ];
    if (this.interactive()) {
      classes.push('sd-card--interactive');
    }
    return classes.join(' ');
  }
}
