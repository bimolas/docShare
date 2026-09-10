import { Component, ChangeDetectionStrategy, input, signal } from '@angular/core';


@Component({
  selector: 'sd-tab',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isActive()) {
      <div
        role="tabpanel"
        [id]="'sd-tabpanel-' + id()"
        [attr.aria-labelledby]="'sd-tab-' + id()"
        tabindex="0"
        class="sd-tab-panel"
      >
        <ng-content></ng-content>
      </div>
    }
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }
    .sd-tab-panel {
      outline: none;
      padding-top: var(--sd-space-md);
    }
    .sd-tab-panel:focus-visible {
      outline: 2px solid var(--sd-color-brand-focus);
      outline-offset: 2px;
      border-radius: var(--sd-radius-md);
    }
  `]
})
export class TabComponent {
  id = input.required<string>();
  label = input.required<string>();
  disabled = input<boolean>(false);
  badge = input<string | number | undefined>();

  isActive = signal<boolean>(false);
}
