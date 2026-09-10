import {
  Directive,
  ElementRef,
  HostListener,
  ComponentRef,
  ViewContainerRef,
  OnDestroy,
  input,
  inject,
  effect
} from '@angular/core';
import { TooltipComponent, TooltipPosition } from './tooltip.component';

let tooltipIdCounter = 0;

@Directive({
  selector: '[sdTooltip]',
  standalone: true
})
export class TooltipDirective implements OnDestroy {
  private elementRef = inject(ElementRef<HTMLElement>);
  private viewContainerRef = inject(ViewContainerRef);

  sdTooltip = input.required<string>();
  sdTooltipPosition = input<TooltipPosition>('top');
  sdTooltipDelay = input<number>(200);
  sdTooltipDisabled = input<boolean>(false);

  private componentRef: ComponentRef<TooltipComponent> | null = null;
  private timeoutId: any = null;
  private uniqueId = `sd-tooltip-${++tooltipIdCounter}`;

  constructor() {
    effect(() => {
      if (this.componentRef && this.sdTooltip()) {
        this.componentRef.setInput('text', this.sdTooltip());
      }
    });
  }

  @HostListener('mouseenter')
  @HostListener('focus')
  onMouseEnter(): void {
    if (this.sdTooltipDisabled() || !this.sdTooltip()?.trim()) {
      return;
    }
    this.scheduleShow();
  }

  @HostListener('mouseleave')
  @HostListener('blur')
  @HostListener('keydown.escape')
  onMouseLeave(): void {
    this.hide();
  }

  private scheduleShow(): void {
    this.clearTimer();
    this.timeoutId = setTimeout(() => {
      this.show();
    }, this.sdTooltipDelay());
  }

  private show(): void {
    if (this.componentRef) {
      return;
    }

    this.componentRef = this.viewContainerRef.createComponent(TooltipComponent);
    this.componentRef.setInput('text', this.sdTooltip());
    this.componentRef.setInput('position', this.sdTooltipPosition());
    this.componentRef.setInput('id', this.uniqueId);

    const hostEl = this.elementRef.nativeElement;
    hostEl.setAttribute('aria-describedby', this.uniqueId);

    document.body.appendChild(this.componentRef.location.nativeElement);
    this.updatePosition();
  }

  private updatePosition(): void {
    if (!this.componentRef) return;

    const hostRect = this.elementRef.nativeElement.getBoundingClientRect();
    const tooltipEl = this.componentRef.location.nativeElement as HTMLElement;
    const tooltipRect = tooltipEl.getBoundingClientRect();

    const position = this.sdTooltipPosition();
    let top = 0;
    let left = 0;
    const offset = 8;

    switch (position) {
      case 'top':
        top = hostRect.top - tooltipRect.height - offset + window.scrollY;
        left = hostRect.left + (hostRect.width - tooltipRect.width) / 2 + window.scrollX;
        break;
      case 'bottom':
        top = hostRect.bottom + offset + window.scrollY;
        left = hostRect.left + (hostRect.width - tooltipRect.width) / 2 + window.scrollX;
        break;
      case 'left':
        top = hostRect.top + (hostRect.height - tooltipRect.height) / 2 + window.scrollY;
        left = hostRect.left - tooltipRect.width - offset + window.scrollX;
        break;
      case 'right':
        top = hostRect.top + (hostRect.height - tooltipRect.height) / 2 + window.scrollY;
        left = hostRect.right + offset + window.scrollX;
        break;
    }

    tooltipEl.style.top = `${Math.max(4, top)}px`;
    tooltipEl.style.left = `${Math.max(4, left)}px`;
  }

  private hide(): void {
    this.clearTimer();
    if (this.componentRef) {
      const hostEl = this.elementRef.nativeElement;
      hostEl.removeAttribute('aria-describedby');
      this.componentRef.destroy();
      this.componentRef = null;
    }
  }

  private clearTimer(): void {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
      this.timeoutId = null;
    }
  }

  ngOnDestroy(): void {
    this.hide();
  }
}
