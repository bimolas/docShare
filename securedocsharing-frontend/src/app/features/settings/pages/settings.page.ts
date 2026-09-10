import { Component, ChangeDetectionStrategy, signal, inject, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { PrivacySettingsComponent } from '../components/privacy-settings/privacy-settings.component';
import { NotificationSettingsComponent } from '../components/notification-settings/notification-settings.component';
import {
  LucideUser,
  LucideShield,
  LucideBell
} from '@lucide/angular';

type SettingsTab = 'account' | 'privacy' | 'notifications';

@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [PrivacySettingsComponent, NotificationSettingsComponent, LucideUser, LucideShield, LucideBell],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="settings-layout">
      <!-- Settings Header -->
      <header class="settings-page-header">
        <div class="header-content">
          <h1>Settings</h1>
          <p>Manage your account settings and preferences.</p>
        </div>
      </header>
    
      <div class="settings-container">
        <!-- Sidebar Navigation -->
        <aside class="settings-sidebar">
          <nav class="sidebar-nav">
            <button
              class="nav-item"
              [class.active]="activeTab() === 'account'"
              (click)="setTab('account')">
              <div class="nav-icon-wrapper">
                <svg lucideUser class="nav-icon"></svg>
              </div>
              <div class="nav-text">
                <span class="nav-label">Account</span>
              </div>
            </button>
    
            <button
              class="nav-item"
              [class.active]="activeTab() === 'privacy'"
              (click)="setTab('privacy')">
              <div class="nav-icon-wrapper">
                <svg lucideShield class="nav-icon"></svg>
              </div>
              <div class="nav-text">
                <span class="nav-label">Privacy & Policies</span>
              </div>
            </button>
    
            <button
              class="nav-item"
              [class.active]="activeTab() === 'notifications'"
              (click)="setTab('notifications')">
              <div class="nav-icon-wrapper">
                <svg lucideBell class="nav-icon"></svg>
              </div>
              <div class="nav-text">
                <span class="nav-label">Notifications</span>
              </div>
            </button>
          </nav>
        </aside>
    
        <!-- Main Content Area -->
        <main class="settings-content">
          <!-- Account Placeholder -->
          @if (activeTab() === 'account') {
            <div class="content-section fade-in">
              <div class="empty-state-card">
                <div class="empty-state-icon">
                  <svg lucideUser class="empty-state-lucide"></svg>
                </div>
                <h3>Account Settings</h3>
                <p>Manage your personal information, security, and preferences. Coming soon.</p>
              </div>
            </div>
          }
    
          <!-- Privacy & Safety Component -->
          @if (activeTab() === 'privacy') {
            <div class="content-section fade-in">
              <app-privacy-settings></app-privacy-settings>
            </div>
          }
    
          <!-- Notifications Section -->
          @if (activeTab() === 'notifications') {
            <div class="content-section fade-in">
              <app-notification-settings></app-notification-settings>
            </div>
          }
        </main>
      </div>
    </div>
    `,
  styles: [`
    .settings-layout {
      display: flex;
      flex-direction: column;
      min-height: calc(100vh - 64px);
      background-color: #fafafa;
    }

    .settings-page-header {
      background: #ffffff;
      border-bottom: 1px solid #e4e4e7;
      padding: 2.5rem 0;
    }

    .header-content {
      max-width: 960px;
      margin: 0 auto;
      padding: 0 2rem;
    }

    .header-content h1 {
      font-size: 1.75rem;
      font-weight: 600;
      color: #09090b;
      margin: 0 0 0.25rem 0;
      letter-spacing: -0.025em;
    }

    .header-content p {
      font-size: 0.95rem;
      color: #71717a;
      margin: 0;
    }

    .settings-container {
      display: flex;
      max-width: 960px;
      margin: 0 auto;
      width: 100%;
      padding: 2.5rem 2rem;
      gap: 3.5rem;
    }

    .settings-sidebar {
      width: 240px;
      flex-shrink: 0;
    }

    .sidebar-nav {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      position: sticky;
      top: 2.5rem;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.875rem;
      width: 100%;
      padding: 0.75rem 1rem;
      border: none;
      background: transparent;
      border-radius: 9999px; /* Pill shape */
      color: #52525b;
      cursor: pointer;
      text-align: left;
      transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .nav-item:hover {
      background-color: #f4f4f5;
      color: #18181b;
    }

    .nav-item.active {
      background-color: #f4f4f5;
      color: #09090b;
      font-weight: 600;
    }

    .nav-icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 24px;
      height: 24px;
      color: inherit;
    }

    .nav-item.active .nav-icon-wrapper {
      color: #09090b;
    }

    .nav-icon {
      width: 20px;
      height: 20px;
      stroke-width: 2px;
    }

    .nav-text {
      display: flex;
      flex-direction: column;
    }

    .nav-label {
      font-size: 0.925rem;
    }

    .nav-item:not(.active) .nav-label {
      font-weight: 500;
    }

    .settings-content {
      flex: 1;
      min-width: 0;
    }

    .content-section {
      width: 100%;
      max-width: 640px; /* Keep form elements narrow */
    }

    .empty-state-lucide {
      width: 32px;
      height: 32px;
    }

    .empty-state-card {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 5rem 2rem;
      background: transparent;
      text-align: center;
    }

    .empty-state-icon {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 64px;
      height: 64px;
      border-radius: 50%;
      background: #f4f4f5;
      color: #a1a1aa;
      margin-bottom: 1.5rem;
    }

    .empty-state-card h3 {
      font-size: 1.25rem;
      font-weight: 600;
      color: #18181b;
      margin: 0 0 0.5rem 0;
      letter-spacing: -0.02em;
    }

    .empty-state-card p {
      font-size: 0.95rem;
      color: #71717a;
      margin: 0;
      max-width: 320px;
      line-height: 1.5;
    }

    .fade-in {
      animation: fadeIn 0.4s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(4px); }
      to { opacity: 1; transform: translateY(0); }
    }
    
    @media (max-width: 768px) {
      .settings-container {
        flex-direction: column;
        padding: 1.5rem;
        gap: 2rem;
      }
      .settings-sidebar {
        width: 100%;
      }
      .sidebar-nav {
        flex-direction: row;
        overflow-x: auto;
        position: static;
        padding-bottom: 0.5rem;
      }
      .nav-item {
        width: auto;
        padding: 0.5rem 1rem;
        white-space: nowrap;
      }
    }
  `],
})
export class SettingsPageComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);

  activeTab = signal<SettingsTab>('privacy');

  ngOnInit(): void {
    this.route.queryParams.subscribe((params) => {
      const tab = params['tab'];
      if (tab === 'notifications' || tab === 'privacy' || tab === 'account') {
        this.activeTab.set(tab);
      }
    });
  }

  setTab(tab: SettingsTab) {
    this.activeTab.set(tab);
  }
}
