import { Component, ChangeDetectionStrategy, inject } from '@angular/core';

import { ToastComponent } from './toast.component';
import { NotificationService } from '../../../core/services/notification.service';

/**
 * ToastContainerComponent: Renders active toast notifications using the canonical ToastComponent.
 * Subscribes to NotificationService signals for global application notification management.
 */
@Component({
  selector: 'sd-toast-container',
  standalone: true,
  imports: [ToastComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sd-toast-container" aria-live="polite" role="status">
      @for (notification of notificationService.notifications(); track notification.id) {
        <sd-toast
          [type]="notification.type"
          [message]="notification.message"
          [duration]="notification.duration ?? 5000"
          (dismissed)="dismiss(notification.id)"
        />
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      pointer-events: none;
    }

    .sd-toast-container {
      position: fixed;
      bottom: var(--sd-space-lg);
      right: var(--sd-space-lg);
      display: flex;
      flex-direction: column;
      gap: var(--sd-space-sm);
      z-index: var(--sd-z-toast, 9000);
      pointer-events: none;
      max-width: 400px;
      width: calc(100vw - var(--sd-space-xl));
    }

    sd-toast {
      pointer-events: auto;
    }
  `],
})
export class ToastContainerComponent {
  protected readonly notificationService = inject(NotificationService);

  dismiss(id: string): void {
    this.notificationService.dismiss(id);
  }
}
