import { Component, ChangeDetectionStrategy, input } from '@angular/core';


export type TooltipPosition = 'top' | 'bottom' | 'left' | 'right';

@Component({
  selector: 'sd-tooltip',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="sd-tooltip-bubble"
      [class]="'sd-tooltip-bubble--' + position()"
      role="tooltip"
      [id]="id()"
    >
      {{ text() }}
      <div class="sd-tooltip-arrow" [class]="'sd-tooltip-arrow--' + position()"></div>
    </div>
  `,
  styles: [`
    :host {
      position: absolute;
      z-index: 1000;
      pointer-events: none;
      transition: opacity var(--sd-duration-fast) var(--sd-ease-standard),
                  transform var(--sd-duration-fast) var(--sd-ease-spring);
    }

    .sd-tooltip-bubble {
      background-color: var(--sd-color-text-primary);
      color: var(--sd-color-text-inverse);
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-xs);
      font-weight: var(--sd-weight-medium);
      line-height: var(--sd-leading-tight);
      padding: var(--sd-space-2xs) var(--sd-space-sm);
      border-radius: var(--sd-radius-md);
      box-shadow: var(--sd-shadow-floating);
      white-space: nowrap;
      position: relative;
    }

    .sd-tooltip-arrow {
      position: absolute;
      width: 0;
      height: 0;
      border-style: solid;
    }

    .sd-tooltip-arrow--top {
      bottom: -4px;
      left: 50%;
      transform: translateX(-50%);
      border-width: 4px 4px 0 4px;
      border-color: var(--sd-color-text-primary) transparent transparent transparent;
    }

    .sd-tooltip-arrow--bottom {
      top: -4px;
      left: 50%;
      transform: translateX(-50%);
      border-width: 0 4px 4px 4px;
      border-color: transparent transparent var(--sd-color-text-primary) transparent;
    }

    .sd-tooltip-arrow--left {
      right: -4px;
      top: 50%;
      transform: translateY(-50%);
      border-width: 4px 0 4px 4px;
      border-color: transparent transparent transparent var(--sd-color-text-primary);
    }

    .sd-tooltip-arrow--right {
      left: -4px;
      top: 50%;
      transform: translateY(-50%);
      border-width: 4px 4px 4px 0;
      border-color: transparent var(--sd-color-text-primary) transparent transparent;
    }
  `]
})
export class TooltipComponent {
  text = input.required<string>();
  position = input<TooltipPosition>('top');
  id = input<string>('');
}
