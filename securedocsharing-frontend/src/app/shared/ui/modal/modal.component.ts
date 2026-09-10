import { Component, ChangeDetectionStrategy, input, output, HostListener } from '@angular/core';


export type ModalSize = 'sm' | 'md' | 'lg' | 'xl';

@Component({
  selector: 'sd-modal',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (open()) {
      <div class="sd-modal-backdrop" (click)="onBackdropClick($event)" role="dialog" aria-modal="true">
        <div class="sd-modal-container" [class]="containerClasses()">
          <!-- Header -->
          <div class="sd-modal-header">
            <div>
              @if (title()) {
                <h3 class="sd-modal-title">{{ title() }}</h3>
              }
              @if (subtitle()) {
                <p class="sd-modal-subtitle">{{ subtitle() }}</p>
              }
              <ng-content select="[modal-header]"></ng-content>
            </div>

            <button class="sd-modal-close" (click)="closeModal()" aria-label="Close modal">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18"></line>
                <line x1="6" y1="6" x2="18" y2="18"></line>
              </svg>
            </button>
          </div>

          <!-- Body -->
          <div class="sd-modal-body custom-scrollbar">
            <ng-content></ng-content>
          </div>

          <!-- Footer -->
          <div class="sd-modal-footer">
            <ng-content select="[modal-footer]"></ng-content>
          </div>
        </div>
      </div>
    }
  `,
  styles: [`
    .sd-modal-backdrop {
      position: fixed;
      inset: 0;
      z-index: var(--sd-z-modal-backdrop, 3000);
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: var(--sd-color-backdrop);
      padding: var(--sd-space-lg);
      animation: sd-fade-in var(--sd-duration-fast) ease-out;
      backdrop-filter: blur(2px);
    }

    @keyframes sd-fade-in {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .sd-modal-container {
      position: relative;
      width: 100%;
      max-height: 90vh;
      display: flex;
      flex-direction: column;
      background-color: var(--sd-color-surface);
      border-radius: var(--sd-radius-xl);
      border: 1px solid var(--sd-color-border-subtle);
      box-shadow: var(--sd-shadow-modal);
      overflow: hidden;
      animation: sd-modal-pop var(--sd-duration-fast) var(--sd-ease-spring);
    }

    @keyframes sd-modal-pop {
      from { transform: scale(0.96); opacity: 0; }
      to { transform: scale(1); opacity: 1; }
    }

    /* ── Modal Sizes ─────────────────────────────────────────────── */
    .sd-modal--sm { max-width: 420px; }
    .sd-modal--md { max-width: 560px; }
    .sd-modal--lg { max-width: 720px; }
    .sd-modal--xl { max-width: 920px; }

    .sd-modal-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--sd-space-md);
      padding: var(--sd-space-xl) var(--sd-space-xl) var(--sd-space-md);
      border-bottom: 1px solid var(--sd-color-border-subtle);
    }

    .sd-modal-title {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-xl);
      font-weight: var(--sd-weight-semibold);
      color: var(--sd-color-text-primary);
      line-height: var(--sd-leading-snug);
    }

    .sd-modal-subtitle {
      font-family: var(--sd-font-sans);
      font-size: var(--sd-font-size-sm);
      color: var(--sd-color-text-secondary);
      margin-top: var(--sd-space-2xs);
    }

    .sd-modal-close {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: var(--sd-radius-md);
      border: none;
      background: transparent;
      color: var(--sd-color-text-muted);
      cursor: pointer;
      transition: background-color var(--sd-duration-fast) ease, color var(--sd-duration-fast) ease;
    }

    .sd-modal-close:hover {
      background-color: var(--sd-color-surface-hover);
      color: var(--sd-color-text-primary);
    }

    .sd-modal-body {
      padding: var(--sd-space-xl);
      overflow-y: auto;
      flex: 1;
    }

    .sd-modal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: var(--sd-space-md);
      padding: var(--sd-space-md) var(--sd-space-xl) var(--sd-space-xl);
      border-top: 1px solid var(--sd-color-border-subtle);
      background-color: var(--sd-color-bg);
    }
  `],
})
export class ModalComponent {
  open = input<boolean>(false);
  title = input<string | undefined>();
  subtitle = input<string | undefined>();
  size = input<ModalSize>('md');
  closeOnBackdrop = input<boolean>(true);

  closed = output<void>();

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    if (this.open()) {
      this.closeModal();
    }
  }

  containerClasses(): string {
    return `sd-modal-container sd-modal--${this.size()}`;
  }

  onBackdropClick(event: MouseEvent): void {
    if (this.closeOnBackdrop() && event.target === event.currentTarget) {
      this.closeModal();
    }
  }

  closeModal(): void {
    this.closed.emit();
  }
}
