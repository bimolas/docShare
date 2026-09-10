import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  HostListener,
  ElementRef,
  inject,
  ViewChild
} from '@angular/core';
import { OverlayModule, ConnectedPosition } from '@angular/cdk/overlay';

export type DropdownPlacement = 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end';

let dropdownCounter = 0;

export const DROPDOWN_POSITIONS: Record<DropdownPlacement, ConnectedPosition[]> = {
  'bottom-start': [
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 },
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 }
  ],
  'bottom-end': [
    { originX: 'end', originY: 'bottom', overlayX: 'end', overlayY: 'top', offsetY: 6 },
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 }
  ],
  'top-start': [
    { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -6 },
    { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 }
  ],
  'top-end': [
    { originX: 'end', originY: 'top', overlayX: 'end', overlayY: 'bottom', offsetY: -6 },
    { originX: 'end', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 6 }
  ]
};

@Component({
  selector: 'sd-dropdown',
  standalone: true,
  imports: [OverlayModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sd-dropdown-container" cdkOverlayOrigin #trigger="cdkOverlayOrigin" #triggerEl>
      <div
        class="sd-dropdown-trigger"
        (click)="toggle()"
        [attr.aria-expanded]="isOpenSignal()"
        aria-haspopup="true"
        [attr.aria-controls]="menuId"
      >
        <ng-content select="[dropdown-trigger]"></ng-content>
      </div>

      <ng-template
        cdkConnectedOverlay
        [cdkConnectedOverlayOrigin]="trigger"
        [cdkConnectedOverlayOpen]="isOpenSignal()"
        [cdkConnectedOverlayPositions]="getOverlayPositions()"
        [cdkConnectedOverlayMinWidth]="triggerWidth()"
        [cdkConnectedOverlayHasBackdrop]="true"
        [cdkConnectedOverlayBackdropClass]="'cdk-overlay-transparent-backdrop'"
        (backdropClick)="close()"
        (detach)="close()"
      >
        <div
          [id]="menuId"
          class="sd-dropdown-menu custom-scrollbar"
          role="menu"
          (click)="onMenuClick($event)"
        >
          <ng-content select="[dropdown-menu]"></ng-content>
          <ng-content></ng-content>
        </div>
      </ng-template>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }

    .sd-dropdown-container {
      display: block;
      width: 100%;
    }

    .sd-dropdown-trigger {
      display: block;
      width: 100%;
      cursor: pointer;
    }

    .sd-dropdown-menu {
      z-index: var(--sd-z-dropdown, 1000);
      min-width: 180px;
      padding: 0.375rem;
      background-color: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 0.75rem;
      box-shadow: 0 12px 28px rgba(15, 23, 42, 0.15);
      animation: sd-dropdown-fade-in 0.15s cubic-bezier(0.16, 1, 0.3, 1);
      white-space: nowrap;
      max-height: 280px;
      overflow-y: auto;
    }

    @keyframes sd-dropdown-fade-in {
      from {
        opacity: 0;
        transform: translateY(-4px) scale(0.97);
      }
      to {
        opacity: 1;
        transform: translateY(0) scale(1);
      }
    }
  `]
})
export class DropdownComponent {
  private elementRef = inject(ElementRef);

  @ViewChild('triggerEl', { read: ElementRef }) triggerEl?: ElementRef<HTMLElement>;

  isOpen = input<boolean>(false);
  placement = input<DropdownPlacement>('bottom-start');
  closeOnClickOutside = input<boolean>(true);
  closeOnItemClick = input<boolean>(true);

  isOpenChange = output<boolean>();
  opened = output<void>();
  closed = output<void>();

  isOpenSignal = signal<boolean>(false);
  triggerWidth = signal<number | string>('auto');
  menuId = `sd-dropdown-menu-${++dropdownCounter}`;

  getOverlayPositions(): ConnectedPosition[] {
    return DROPDOWN_POSITIONS[this.placement()] || DROPDOWN_POSITIONS['bottom-start'];
  }

  toggle(): void {
    if (this.isOpenSignal()) {
      this.close();
    } else {
      this.open();
    }
  }

  open(): void {
    if (this.triggerEl?.nativeElement) {
      const width = this.triggerEl.nativeElement.getBoundingClientRect().width;
      if (width > 0) {
        this.triggerWidth.set(width);
      }
    }
    this.isOpenSignal.set(true);
    this.isOpenChange.emit(true);
    this.opened.emit();
  }

  close(): void {
    if (this.isOpenSignal()) {
      this.isOpenSignal.set(false);
      this.isOpenChange.emit(false);
      this.closed.emit();
    }
  }

  onMenuClick(event: MouseEvent): void {
    if (this.closeOnItemClick()) {
      const target = event.target as HTMLElement;
      if (target.closest('a, button, [role="menuitem"], .sd-dropdown-item, .custom-select-option')) {
        this.close();
      }
    }
  }

  @HostListener('window:keydown.escape', ['$event'])
  onEscKey(event: Event): void {
    if (this.isOpenSignal()) {
      event.preventDefault();
      this.close();
    }
  }
}
