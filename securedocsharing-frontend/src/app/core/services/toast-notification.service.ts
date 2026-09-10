import { Injectable, signal } from '@angular/core';

export type ToastNotificationType = 'success' | 'error' | 'warning' | 'info';

export interface ToastNotification {
  id: string;
  type: ToastNotificationType;
  message: string;
  duration?: number;
  timestamp: number;
}

/**
 * ToastNotificationService: Manages in-app toast notification state.
 * Components subscribe to the notifications signal to render toasts.
 */
@Injectable({ providedIn: 'root' })
export class ToastNotificationService {
  private readonly _notifications = signal<ToastNotification[]>([]);

  readonly notifications = this._notifications.asReadonly();

  success(message: string, duration = 4000): void {
    this.add({ type: 'success', message, duration });
  }

  error(message: string, duration = 6000): void {
    this.add({ type: 'error', message, duration });
  }

  warning(message: string, duration = 5000): void {
    this.add({ type: 'warning', message, duration });
  }

  info(message: string, duration = 4000): void {
    this.add({ type: 'info', message, duration });
  }

  dismiss(id: string): void {
    this._notifications.update((list) => list.filter((n) => n.id !== id));
  }

  private add(notification: Omit<ToastNotification, 'id' | 'timestamp'>): void {
    const id = crypto.randomUUID();
    const newNotification: ToastNotification = { ...notification, id, timestamp: Date.now() };

    this._notifications.update((list) => [...list, newNotification]);

    if (notification.duration) {
      setTimeout(() => this.dismiss(id), notification.duration);
    }
  }
}
