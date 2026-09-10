import { Component, OnInit, signal, ChangeDetectionStrategy, inject } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { IconComponent } from '../../../../shared/components/icon/icon.component';

export interface InAppNotificationPreferences {
  showNavBadge: boolean;
  actionRequiredAlerts: boolean;
  invitationAlerts: boolean;
  sharingAlerts: boolean;
  securityAlerts: boolean;
  autoMarkReadOnReview: boolean;
}

export const DEFAULT_NOTIFICATION_PREFERENCES: InAppNotificationPreferences = {
  showNavBadge: true,
  actionRequiredAlerts: true,
  invitationAlerts: true,
  sharingAlerts: true,
  securityAlerts: true,
  autoMarkReadOnReview: false,
};

const STORAGE_KEY = 'securedoc_in_app_notification_preferences';

@Component({
  selector: 'app-notification-settings',
  standalone: true,
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="settings-section">
      <div class="settings-header">
        <h2>Notification Preferences</h2>
        <p>Configure how in-app alerts and notifications are delivered to your attention inbox.</p>
      </div>

      <!-- Information Notice -->
      <div class="info-banner" role="note">
        <app-icon name="info" [size]="16" class="info-icon" />
        <div class="info-text">
          <span>In-app notifications are delivered to your global attention inbox. External channels (email/push) are managed by organization administrators.</span>
        </div>
      </div>

      <!-- Card 1: Attention & Badging -->
      <div class="settings-card">
        <div class="card-header">
          <div class="card-icon-wrapper">
            <app-icon name="bell" [size]="18" class="card-icon" />
          </div>
          <div class="card-title">
            <h3>Inbox & Navigation</h3>
            <p>Control visual indicators and attention badging across your workspace</p>
          </div>
        </div>

        <div class="setting-group">
          <!-- Show unread badge -->
          <div class="setting-item">
            <div class="setting-info">
              <label for="show-nav-badge">Show unread count badge</label>
              <p>Display real-time unread badges on the sidebar and top navigation bell.</p>
            </div>
            <div class="setting-action">
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  id="show-nav-badge"
                  [ngModel]="prefs().showNavBadge"
                  (ngModelChange)="updatePref('showNavBadge', $event)"
                />
                <span class="slider"></span>
              </label>
            </div>
          </div>

          <!-- Action Required Priority -->
          <div class="setting-item">
            <div class="setting-info">
              <label for="action-required-alerts">Action Required alerts</label>
              <p>Highlight pending access requests, invitations, and file requests in a dedicated section until resolved.</p>
            </div>
            <div class="setting-action">
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  id="action-required-alerts"
                  [ngModel]="prefs().actionRequiredAlerts"
                  (ngModelChange)="updatePref('actionRequiredAlerts', $event)"
                />
                <span class="slider"></span>
              </label>
            </div>
          </div>

          <!-- Auto-mark as read -->
          <div class="setting-item">
            <div class="setting-info">
              <label for="auto-mark-read">Auto-mark as read on review</label>
              <p>Automatically mark actionable notifications as read when clicking through to review them.</p>
            </div>
            <div class="setting-action">
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  id="auto-mark-read"
                  [ngModel]="prefs().autoMarkReadOnReview"
                  (ngModelChange)="updatePref('autoMarkReadOnReview', $event)"
                />
                <span class="slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- Card 2: Notification Categories -->
      <div class="settings-card">
        <div class="card-header">
          <div class="card-icon-wrapper">
            <app-icon name="layers" [size]="18" class="card-icon" />
          </div>
          <div class="card-title">
            <h3>Notification Types</h3>
            <p>Select which event categories generate alerts in your personal feed</p>
          </div>
        </div>

        <div class="setting-group">
          <!-- Invitations & Teams -->
          <div class="setting-item">
            <div class="setting-info">
              <label for="invitation-alerts">Invitations & Team assignments</label>
              <p>Receive notifications when invited to organizations or assigned to teams.</p>
            </div>
            <div class="setting-action">
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  id="invitation-alerts"
                  [ngModel]="prefs().invitationAlerts"
                  (ngModelChange)="updatePref('invitationAlerts', $event)"
                />
                <span class="slider"></span>
              </label>
            </div>
          </div>

          <!-- Sharing & Documents -->
          <div class="setting-item">
            <div class="setting-info">
              <label for="sharing-alerts">Sharing & Collaboration</label>
              <p>Receive alerts when documents, folders, or file requests are directly shared with you.</p>
            </div>
            <div class="setting-action">
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  id="sharing-alerts"
                  [ngModel]="prefs().sharingAlerts"
                  (ngModelChange)="updatePref('sharingAlerts', $event)"
                />
                <span class="slider"></span>
              </label>
            </div>
          </div>

          <!-- Security & System -->
          <div class="setting-item">
            <div class="setting-info">
              <label for="security-alerts">Security & Governance notices</label>
              <p>Receive critical updates regarding security policies, access changes, and administrative actions.</p>
            </div>
            <div class="setting-action">
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  id="security-alerts"
                  [ngModel]="prefs().securityAlerts"
                  (ngModelChange)="updatePref('securityAlerts', $event)"
                />
                <span class="slider"></span>
              </label>
            </div>
          </div>
        </div>
      </div>

      <!-- Card 3: Quick Navigation & Actions -->
      <div class="settings-card actions-card">
        <div class="card-header">
          <div class="card-icon-wrapper">
            <app-icon name="arrow-right" [size]="18" class="card-icon" />
          </div>
          <div class="card-title">
            <h3>Notification Center</h3>
            <p>Access your global personal notification inbox and message archives</p>
          </div>
        </div>

        <div class="actions-content">
          <button class="primary-btn" (click)="openNotificationCenter()">
            <app-icon name="bell" [size]="16" />
            <span>Open Notification Center</span>
          </button>

          <button class="secondary-btn" (click)="resetDefaults()">
            <app-icon name="refresh-cw" [size]="14" />
            <span>Reset to defaults</span>
          </button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .settings-section {
      width: 100%;
      max-width: 680px;
    }

    .settings-header {
      margin-bottom: 1.5rem;
    }

    .settings-header h2 {
      font-size: 1.35rem;
      font-weight: 600;
      color: #09090b;
      margin: 0 0 0.35rem 0;
      letter-spacing: -0.02em;
    }

    .settings-header p {
      font-size: 0.875rem;
      color: #71717a;
      margin: 0;
      line-height: 1.45;
    }

    .info-banner {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      background: #fff7ed;
      border: 1px solid #ffedd5;
      border-radius: 8px;
      padding: 0.75rem 1rem;
      margin-bottom: 1.5rem;
      color: #c2410c;
      font-size: 0.8125rem;
      line-height: 1.4;
    }

    .info-icon {
      flex-shrink: 0;
      margin-top: 1px;
    }

    .settings-card {
      background: #ffffff;
      border: 1px solid #e4e4e7;
      border-radius: 12px;
      margin-bottom: 1.5rem;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      overflow: hidden;
    }

    .card-header {
      display: flex;
      align-items: flex-start;
      gap: 1rem;
      padding: 1.25rem 1.5rem;
      background: #ffffff;
      border-bottom: 1px solid #f4f4f5;
    }

    .card-icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 36px;
      height: 36px;
      border-radius: 8px;
      background: #fff7ed;
      color: #f97316;
      flex-shrink: 0;
    }

    .card-title h3 {
      font-size: 1rem;
      font-weight: 600;
      color: #18181b;
      margin: 0 0 0.25rem 0;
      letter-spacing: -0.01em;
    }

    .card-title p {
      font-size: 0.8125rem;
      color: #71717a;
      margin: 0;
    }

    .setting-group {
      display: flex;
      flex-direction: column;
    }

    .setting-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1.15rem 1.5rem;
      gap: 1.5rem;
      border-bottom: 1px solid #f4f4f5;
      transition: background-color 0.15s ease;
    }

    .setting-item:last-child {
      border-bottom: none;
    }

    .setting-item:hover {
      background-color: #fafafa;
    }

    .setting-info {
      flex: 1;
      min-width: 0;
    }

    .setting-info label {
      display: block;
      font-size: 0.875rem;
      font-weight: 500;
      color: #18181b;
      margin-bottom: 0.2rem;
      cursor: pointer;
    }

    .setting-info p {
      font-size: 0.8125rem;
      color: #71717a;
      margin: 0;
      line-height: 1.4;
    }

    .setting-action {
      flex-shrink: 0;
    }

    /* Toggle Switch Styles */
    .toggle-switch {
      position: relative;
      display: inline-block;
      width: 44px;
      height: 24px;
      flex-shrink: 0;
    }

    .toggle-switch input {
      opacity: 0;
      width: 0;
      height: 0;
    }

    .slider {
      position: absolute;
      cursor: pointer;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background-color: #e4e4e7;
      transition: 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      border-radius: 24px;
    }

    .slider:before {
      position: absolute;
      content: "";
      height: 20px;
      width: 20px;
      left: 2px;
      bottom: 2px;
      background-color: white;
      transition: 0.2s cubic-bezier(0.16, 1, 0.3, 1);
      border-radius: 50%;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.1), 0 1px 1px rgba(0, 0, 0, 0.06);
    }

    input:checked + .slider {
      background-color: #f97316;
    }

    input:focus-visible + .slider {
      outline: 2px solid #f97316;
      outline-offset: 2px;
    }

    input:checked + .slider:before {
      transform: translateX(20px);
    }

    /* Actions Content */
    .actions-content {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1.25rem 1.5rem;
      flex-wrap: wrap;
    }

    .primary-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.6rem 1.1rem;
      background: #f97316;
      color: #ffffff;
      border: 1px solid transparent;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: background-color 0.15s ease, box-shadow 0.15s ease;
    }

    .primary-btn:hover {
      background: #ea580c;
      box-shadow: 0 2px 6px rgba(249, 115, 22, 0.25);
    }

    .secondary-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.6rem 1rem;
      background: #ffffff;
      color: #52525b;
      border: 1px solid #e4e4e7;
      border-radius: 8px;
      font-size: 0.8125rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .secondary-btn:hover {
      background: #f4f4f5;
      color: #18181b;
      border-color: #d4d4d8;
    }
  `],
})
export class NotificationSettingsComponent implements OnInit {
  private readonly router = inject(Router);

  readonly prefs = signal<InAppNotificationPreferences>({ ...DEFAULT_NOTIFICATION_PREFERENCES });

  ngOnInit(): void {
    this.loadPreferences();
  }

  loadPreferences(): void {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        const parsed = JSON.parse(stored);
        this.prefs.set({ ...DEFAULT_NOTIFICATION_PREFERENCES, ...parsed });
      }
    } catch {
      this.prefs.set({ ...DEFAULT_NOTIFICATION_PREFERENCES });
    }
  }

  updatePref<K extends keyof InAppNotificationPreferences>(key: K, value: InAppNotificationPreferences[K]): void {
    this.prefs.update((prev) => {
      const next = { ...prev, [key]: value };
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } catch {
        // Local storage write fallback
      }
      return next;
    });
  }

  resetDefaults(): void {
    this.prefs.set({ ...DEFAULT_NOTIFICATION_PREFERENCES });
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      // ignore
    }
  }

  openNotificationCenter(): void {
    this.router.navigate(['/w', 'personal', 'notifications']);
  }
}
