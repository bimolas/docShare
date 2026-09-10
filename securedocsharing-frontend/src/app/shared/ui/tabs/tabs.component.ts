import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  contentChildren,
  effect,
  signal
} from '@angular/core';

import { TabComponent } from './tab.component';

export type TabsVariant = 'underline' | 'pills' | 'segmented';
export type TabsSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'sd-tabs',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sd-tabs-wrapper">
      <div
        class="sd-tablist"
        [class]="tablistClasses()"
        role="tablist"
        (keydown)="onKeydown($event)"
      >
        @for (tab of tabs(); track tab.id()) {
          <button
            type="button"
            role="tab"
            [id]="'sd-tab-' + tab.id()"
            [attr.aria-selected]="selectedId() === tab.id()"
            [attr.aria-controls]="'sd-tabpanel-' + tab.id()"
            [attr.tabindex]="selectedId() === tab.id() ? 0 : -1"
            [disabled]="tab.disabled()"
            [class]="tabButtonClasses(tab)"
            (click)="selectTab(tab.id())"
          >
            <span class="sd-tab-label">{{ tab.label() }}</span>
            @if (tab.badge() !== undefined) {
              <span class="sd-tab-badge">{{ tab.badge() }}</span>
            }
          </button>
        }
      </div>

      <div class="sd-tabs-content">
        <ng-content></ng-content>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-tabs-wrapper {
      display: flex;
      flex-direction: column;
      width: 100%;
    }

    .sd-tablist {
      display: flex;
      align-items: center;
      gap: var(--sd-space-2xs);
      position: relative;
    }

    /* ── Underline Variant ────────────────────────────────────────── */
    .sd-tablist--underline {
      border-bottom: 1px solid var(--sd-color-border);
    }
    .sd-tablist--underline .sd-tab-btn {
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      color: var(--sd-color-text-secondary);
      border-radius: 0;
      margin-bottom: -1px;
    }
    .sd-tablist--underline .sd-tab-btn:hover:not(:disabled) {
      color: var(--sd-color-text-primary);
      border-bottom-color: var(--sd-color-border-hover);
    }
    .sd-tablist--underline .sd-tab-btn[aria-selected="true"] {
      color: var(--sd-color-primary);
      border-bottom-color: var(--sd-color-primary);
      font-weight: var(--sd-weight-semibold);
    }

    /* ── Pills Variant ───────────────────────────────────────────── */
    .sd-tablist--pills .sd-tab-btn {
      background: transparent;
      border: 1px solid transparent;
      color: var(--sd-color-text-secondary);
      border-radius: var(--sd-radius-lg);
    }
    .sd-tablist--pills .sd-tab-btn:hover:not(:disabled) {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-primary);
    }
    .sd-tablist--pills .sd-tab-btn[aria-selected="true"] {
      background-color: var(--sd-color-primary-subtle);
      color: var(--sd-color-primary);
      border-color: var(--sd-color-primary-border);
      font-weight: var(--sd-weight-semibold);
    }

    /* ── Segmented Variant ────────────────────────────────────────── */
    .sd-tablist--segmented {
      background-color: var(--sd-color-surface-hover);
      padding: var(--sd-space-3xs);
      border-radius: var(--sd-radius-lg);
      border: 1px solid var(--sd-color-border-subtle);
    }
    .sd-tablist--segmented .sd-tab-btn {
      flex: 1;
      background: transparent;
      border: none;
      color: var(--sd-color-text-secondary);
      border-radius: var(--sd-radius-md);
      justify-content: center;
    }
    .sd-tablist--segmented .sd-tab-btn:hover:not(:disabled) {
      color: var(--sd-color-text-primary);
    }
    .sd-tablist--segmented .sd-tab-btn[aria-selected="true"] {
      background-color: var(--sd-color-surface);
      color: var(--sd-color-text-primary);
      box-shadow: var(--sd-shadow-sm);
      font-weight: var(--sd-weight-semibold);
    }

    /* ── Base Tab Button Styles ───────────────────────────────────── */
    .sd-tab-btn {
      display: inline-flex;
      align-items: center;
      gap: var(--sd-space-xs);
      cursor: pointer;
      font-family: var(--sd-font-sans);
      transition: all var(--sd-duration-fast) var(--sd-ease-standard);
      outline: none;
      white-space: nowrap;
      user-select: none;
    }

    .sd-tab-btn:focus-visible {
      outline: 2px solid var(--sd-color-brand-focus);
      outline-offset: 2px;
      z-index: 1;
    }

    .sd-tab-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    /* ── Sizes ────────────────────────────────────────────────────── */
    .sd-tab-btn--sm {
      padding: var(--sd-space-2xs) var(--sd-space-sm);
      font-size: var(--sd-font-size-xs);
      height: var(--sd-size-control-sm);
    }

    .sd-tab-btn--md {
      padding: var(--sd-space-xs) var(--sd-space-md);
      font-size: var(--sd-font-size-sm);
      height: var(--sd-size-control-md);
    }

    .sd-tab-btn--lg {
      padding: var(--sd-space-sm) var(--sd-space-lg);
      font-size: var(--sd-font-size-base);
      height: var(--sd-size-control-lg);
    }

    /* ── Tab Badge ───────────────────────────────────────────────── */
    .sd-tab-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 0 var(--sd-space-2xs);
      min-width: 18px;
      height: 18px;
      border-radius: var(--sd-radius-full);
      background-color: var(--sd-color-border);
      color: var(--sd-color-text-secondary);
      font-size: 11px;
      font-weight: var(--sd-weight-semibold);
    }

    .sd-tab-btn[aria-selected="true"] .sd-tab-badge {
      background-color: var(--sd-color-primary);
      color: var(--sd-color-text-inverse);
    }

    .sd-tabs-content {
      width: 100%;
    }
  `]
})
export class TabsComponent {
  activeTabId = input<string | undefined>();
  variant = input<TabsVariant>('underline');
  size = input<TabsSize>('md');

  activeTabIdChange = output<string>();
  tabChange = output<string>();

  tabs = contentChildren(TabComponent);
  selectedId = signal<string>('');

  constructor() {
    effect(() => {
      const explicitId = this.activeTabId();
      const allTabs = this.tabs();

      if (explicitId) {
        this.selectedId.set(explicitId);
      } else if (allTabs.length > 0 && !this.selectedId()) {
        this.selectedId.set(allTabs[0].id());
      }

      // Update active state on children
      const currentSelected = this.selectedId();
      allTabs.forEach(tab => {
        tab.isActive.set(tab.id() === currentSelected);
      });
    });
  }

  tablistClasses(): string {
    return `sd-tablist--${this.variant()}`;
  }

  tabButtonClasses(tab: TabComponent): string {
    return `sd-tab-btn sd-tab-btn--${this.size()}`;
  }

  selectTab(tabId: string): void {
    this.selectedId.set(tabId);
    this.activeTabIdChange.emit(tabId);
    this.tabChange.emit(tabId);

    this.tabs().forEach(tab => {
      tab.isActive.set(tab.id() === tabId);
    });
  }

  onKeydown(event: KeyboardEvent): void {
    const allTabs = this.tabs().filter(t => !t.disabled());
    if (allTabs.length === 0) return;

    const currentIndex = allTabs.findIndex(t => t.id() === this.selectedId());
    let targetIndex = -1;

    switch (event.key) {
      case 'ArrowRight':
        targetIndex = (currentIndex + 1) % allTabs.length;
        break;
      case 'ArrowLeft':
        targetIndex = (currentIndex - 1 + allTabs.length) % allTabs.length;
        break;
      case 'Home':
        targetIndex = 0;
        break;
      case 'End':
        targetIndex = allTabs.length - 1;
        break;
      default:
        return;
    }

    if (targetIndex !== -1) {
      event.preventDefault();
      const targetTab = allTabs[targetIndex];
      this.selectTab(targetTab.id());

      // Focus the selected button element
      const tabEl = document.getElementById(`sd-tab-${targetTab.id()}`);
      tabEl?.focus();
    }
  }
}
