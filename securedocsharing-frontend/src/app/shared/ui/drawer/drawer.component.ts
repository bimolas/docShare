import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  HostListener,
  ElementRef,
  inject
} from '@angular/core';

import { IconButtonComponent } from '../icon-button/icon-button.component';

export type DrawerPosition = 'right' | 'left' | 'bottom' | 'top';
export type DrawerSize = 'sm' | 'md' | 'lg' | 'full';

let drawerIdCounter = 0;

@Component({
  selector: 'sd-drawer',
  standalone: true,
  imports: [IconButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div
        class="sd-drawer-overlay"
        (click)="onBackdropClick($event)"
        aria-hidden="true"
      ></div>

      <div
        class="sd-drawer-panel"
        [class]="panelClasses()"
        role="dialog"
        aria-modal="true"
        [attr.aria-labelledby]="title() ? titleId : null"
        [attr.aria-describedby]="subtitle() ? subtitleId : null"
      >
        <!-- Header -->
        @if (title() || showCloseButton()) {
          <div class="sd-drawer-header">
            <div class="sd-drawer-header-content">
              @if (title()) {
                <h3 [id]="titleId" class="sd-drawer-title">{{ title() }}</h3>
              }
              @if (subtitle()) {
                <p [id]="subtitleId" class="sd-drawer-subtitle">{{ subtitle() }}</p>
              }
            </div>
            @if (showCloseButton()) {
              <sd-icon-button
                ariaLabel="Close drawer"
                variant="ghost"
                size="sm"
                (clicked)="close()"
              >
                <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M18 6L6 18M6 6l12 12"/>
                </svg>
              </sd-icon-button>
            }
          </div>
        }

        <!-- Body -->
        <div class="sd-drawer-body custom-scrollbar">
          <ng-content></ng-content>
        </div>

        <!-- Footer Slot -->
        <div class="sd-drawer-footer">
          <ng-content select="[drawer-footer]"></ng-content>
        </div>
      </div>
    }
  `,
  styles: [`
    :host {
      display: contents;
    }

    .sd-drawer-overlay {
      position: fixed;
      inset: 0;
      z-index: var(--sd-z-drawer-backdrop, 2000);
      background-color: var(--sd-color-backdrop);
      backdrop-filter: blur(2px);
      animation: sd-fade-in var(--sd-duration-fast) var(--sd-ease-standard);
    }

    .sd-drawer-panel {
      position: fixed;
      z-index: var(--sd-z-drawer, 2001);
      display: flex;
      flex-direction: column;
      background-color: var(--sd-color-surface);
      box-shadow: var(--sd-shadow-modal);
      outline: none;
      transition: transform var(--sd-duration-normal) var(--sd-ease-spring);
    }

    /* ── Positions ────────────────────────────────────────────────── */
    .sd-drawer--right {
      top: 0;
      right: 0;
      bottom: 0;
      border-left: 1px solid var(--sd-color-border);
      animation: sd-slide-left var(--sd-duration-normal) var(--sd-ease-spring);
    }

    .sd-drawer--left {
      top: 0;
      left: 0;
      bottom: 0;
      border-right: 1px solid var(--sd-color-border);
      animation: sd-slide-right var(--sd-duration-normal) var(--sd-ease-spring);
    }

    .sd-drawer--bottom {
      bottom: 0;
      left: 0;
      right: 0;
      border-top: 1px solid var(--sd-color-border);
      border-top-left-radius: var(--sd-radius-xl);
      border-top-right-radius: var(--sd-radius-xl);
      animation: sd-slide-up var(--sd-duration-normal) var(--sd-ease-spring);
    }

    .sd-drawer--top {
      top: 0;
      left: 0;
      right: 0;
      border-bottom: 1px solid var(--sd-color-border);
      border-bottom-left-radius: var(--sd-radius-xl);
      border-bottom-right-radius: var(--sd-radius-xl);
      animation: sd-slide-down var(--sd-duration-normal) var(--sd-ease-spring);
    }

    /* ── Sizes (for Right & Left) ─────────────────────────────────── */
    .sd-drawer--right.sd-drawer--sm, .sd-drawer--left.sd-drawer--sm { width: 360px; max-width: 100vw; }
    .sd-drawer--right.sd-drawer--md, .sd-drawer--left.sd-drawer--md { width: 480px; max-width: 100vw; }
    .sd-drawer--right.sd-drawer--lg, .sd-drawer--left.sd-drawer--lg { width: 640px; max-width: 100vw; }
    .sd-drawer--right.sd-drawer--full, .sd-drawer--left.sd-drawer--full { width: 100vw; }

    /* ── Sizes (for Top & Bottom) ─────────────────────────────────── */
    .sd-drawer--bottom.sd-drawer--sm, .sd-drawer--top.sd-drawer--sm { height: 280px; max-height: 100vh; }
    .sd-drawer--bottom.sd-drawer--md, .sd-drawer--top.sd-drawer--md { height: 440px; max-height: 100vh; }
    .sd-drawer--bottom.sd-drawer--lg, .sd-drawer--top.sd-drawer--lg { height: 620px; max-height: 100vh; }
    .sd-drawer--bottom.sd-drawer--full, .sd-drawer--top.sd-drawer--full { height: 100vh; }

    /* ── Inner Sections ────────────────────────────────────────────── */
    .sd-drawer-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      padding: var(--sd-space-lg);
      border-bottom: 1px solid var(--sd-color-border-subtle);
      background-color: var(--sd-color-surface);
      gap: var(--sd-space-md);
    }

    .sd-drawer-header-content {
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-3xs);
    }

    .sd-drawer-title {
      font-size: var(--sd-font-size-lg);
      font-weight: var(--sd-weight-semibold);
      color: var(--sd-color-text-primary);
      margin: 0;
    }

    .sd-drawer-subtitle {
      font-size: var(--sd-font-size-xs);
      color: var(--sd-color-text-muted);
      margin: 0;
    }

    .sd-drawer-body {
      flex: 1;
      overflow-y: auto;
      padding: var(--sd-space-lg);
    }

    .sd-drawer-footer:has(*) {
      padding: var(--sd-space-md) var(--sd-space-lg);
      border-top: 1px solid var(--sd-color-border-subtle);
      background-color: var(--sd-color-bg);
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: var(--sd-space-sm);
    }

    /* ── Animations ───────────────────────────────────────────────── */
    @keyframes sd-fade-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }
    @keyframes sd-slide-left {
      from { transform: translateX(100%); }
      to { transform: translateX(0); }
    }
    @keyframes sd-slide-right {
      from { transform: translateX(-100%); }
      to { transform: translateX(0); }
    }
    @keyframes sd-slide-up {
      from { transform: translateY(100%); }
      to { transform: translateY(0); }
    }
    @keyframes sd-slide-down {
      from { transform: translateY(-100%); }
      to { transform: translateY(0); }
    }
  `]
})
export class DrawerComponent {
  isOpen = input<boolean>(false);
  position = input<DrawerPosition>('right');
  size = input<DrawerSize>('md');
  title = input<string | undefined>();
  subtitle = input<string | undefined>();
  showCloseButton = input<boolean>(true);
  closeOnBackdrop = input<boolean>(true);
  closeOnEsc = input<boolean>(true);

  closed = output<void>();

  private uniqueId = ++drawerIdCounter;
  titleId = `sd-drawer-title-${this.uniqueId}`;
  subtitleId = `sd-drawer-subtitle-${this.uniqueId}`;

  panelClasses(): string {
    return `sd-drawer--${this.position()} sd-drawer--${this.size()}`;
  }

  close(): void {
    this.closed.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if (this.closeOnBackdrop()) {
      this.close();
    }
  }

  @HostListener('window:keydown.escape', ['$event'])
  onEscKey(event: Event): void {
    if (this.isOpen() && this.closeOnEsc()) {
      event.preventDefault();
      this.close();
    }
  }
}
