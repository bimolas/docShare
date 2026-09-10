import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';

import { LayoutStore } from '../../state/layout/layout.store';

export type DetailTab = 'overview' | 'permissions' | 'audit';

/**
 * DetailDrawerComponent: Contextual slide-over inspector pane for selected items.
 * Integrates with LayoutStore to handle open/close state and transitions.
 */
@Component({
  selector: 'app-detail-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [],
  template: `
    <aside
      class="detail-drawer"
      [class.open]="isOpen()"
      aria-label="Item Inspector"
    >
      <!-- Header -->
      <div class="drawer-header">
        <div class="header-title">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="header-icon">
            <path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"></path>
            <polyline points="13 2 13 9 20 9"></polyline>
          </svg>
          <span class="title-text">Item Details</span>
        </div>
        <button
          class="close-btn"
          (click)="close()"
          aria-label="Close detail pane"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <!-- Navigation Tabs -->
      <div class="drawer-tabs">
        <button
          class="tab-btn"
          [class.active]="activeTab() === 'overview'"
          (click)="setActiveTab('overview')"
        >
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><line x1="3.5" y1="9.5" x2="3.5" y2="13.5"/><line x1="8" y1="6" x2="8" y2="13.5"/><line x1="12.5" y1="2.5" x2="12.5" y2="13.5"/></svg>
          <span>Overview</span>
        </button>
        <button
          class="tab-btn"
          [class.active]="activeTab() === 'permissions'"
          (click)="setActiveTab('permissions')"
        >
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="5.5" cy="10.5" r="3.2"/><line x1="7.8" y1="8.2" x2="13.5" y2="2.5"/><line x1="11.2" y1="4.8" x2="13" y2="6.6"/></svg>
          <span>Permissions</span>
        </button>
        <button
          class="tab-btn"
          [class.active]="activeTab() === 'audit'"
          (click)="setActiveTab('audit')"
        >
          <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"><path d="M12 8v4H4V4h4"/><path d="M8 2h6v6"/><line x1="9" y1="7" x2="15" y2="1"/></svg>
          <span>Audit Log</span>
        </button>
      </div>

      <!-- Tab Content Area -->
      <div class="drawer-body">
        @switch (activeTab()) {
          @case ('overview') {
            <div class="tab-pane">
              <div class="preview-card">
                <div class="file-icon-large">📄</div>
                <div class="file-name-heading">Select an item in Explorer</div>
                <div class="file-subtext">Select a document or folder to inspect metadata, permissions, and audit logs.</div>
              </div>

              <div class="info-group">
                <label class="info-label">PROPERTIES</label>
                <div class="info-row">
                  <span class="info-key">Type</span>
                  <span class="info-val">Document / Folder</span>
                </div>
                <div class="info-row">
                  <span class="info-key">Size</span>
                  <span class="info-val">—</span>
                </div>
                <div class="info-row">
                  <span class="info-key">Location</span>
                  <span class="info-val">Active Workspace</span>
                </div>
                <div class="info-row">
                  <span class="info-key">Created</span>
                  <span class="info-val">—</span>
                </div>
              </div>
            </div>
          }
          @case ('permissions') {
            <div class="tab-pane">
              <div class="info-group">
                <label class="info-label">ACCESS CONTROL</label>
                <p class="empty-state-text">No document currently selected for permission mapping.</p>
              </div>
            </div>
          }
          @case ('audit') {
            <div class="tab-pane">
              <div class="info-group">
                <label class="info-label">RECENT ACTIVITY</label>
                <p class="empty-state-text">Select an item to view activity trail.</p>
              </div>
            </div>
          }
        }
      </div>
    </aside>
  `,
  styles: [`
    :host {
      display: block;
    }

    .detail-drawer {
      position: fixed;
      top: 0;
      right: 0;
      bottom: 0;
      width: 380px;
      max-width: 100vw;
      background: var(--color-surface);
      border-left: 1px solid var(--color-border);
      box-shadow: -4px 0 20px rgba(0, 0, 0, 0.15);
      z-index: 40;
      display: flex;
      flex-direction: column;
      transform: translateX(100%);
      transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .detail-drawer.open {
      transform: translateX(0);
    }

    .drawer-header {
      height: 60px;
      padding: 0 1.25rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      border-bottom: 1px solid var(--color-border);
      flex-shrink: 0;
    }

    .header-title {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      font-weight: 600;
      color: var(--color-text);
      font-size: 0.95rem;
    }

    .header-icon {
      width: 20px;
      height: 20px;
      color: var(--color-accent);
    }

    .close-btn {
      background: transparent;
      border: none;
      cursor: pointer;
      padding: 0.35rem;
      border-radius: var(--radius-md);
      color: var(--color-text-muted);
      display: flex;
      align-items: center;
      justify-content: center;
      transition: background 0.15s, color 0.15s;
    }

    .close-btn:hover {
      background: var(--color-bg-hover);
      color: var(--color-text);
    }

    .close-btn svg {
      width: 18px;
      height: 18px;
    }

    .drawer-tabs {
      display: flex;
      border-bottom: 1px solid var(--color-border);
      background: var(--color-bg);
      padding: 0 0.5rem;
      gap: 0.25rem;
      flex-shrink: 0;
    }

    .tab-btn {
      flex: 1;
      padding: 0.65rem 0.5rem;
      font-size: 0.8rem;
      font-weight: 500;
      color: var(--color-text-muted);
      background: transparent;
      border: none;
      border-bottom: 2px solid transparent;
      cursor: pointer;
      transition: color 0.15s, border-color 0.15s;
    }

    .tab-btn:hover {
      color: var(--color-text);
    }

    .tab-btn.active {
      color: var(--color-accent);
      border-bottom-color: var(--color-accent);
      font-weight: 600;
    }

    .drawer-body {
      flex: 1;
      overflow-y: auto;
      padding: 1.25rem;
    }

    .tab-pane {
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    .preview-card {
      background: var(--color-bg);
      border: 1px dashed var(--color-border);
      border-radius: var(--radius-lg);
      padding: 1.5rem 1rem;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
    }

    .file-icon-large {
      font-size: 2.5rem;
    }

    .file-name-heading {
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--color-text);
    }

    .file-subtext {
      font-size: 0.775rem;
      color: var(--color-text-muted);
      line-height: 1.4;
    }

    .info-group {
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
    }

    .info-label {
      font-size: 0.65rem;
      font-weight: 700;
      color: var(--color-text-muted);
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }

    .info-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 0.825rem;
      padding: 0.4rem 0;
      border-bottom: 1px solid var(--color-border);
    }

    .info-key {
      color: var(--color-text-muted);
    }

    .info-val {
      font-weight: 500;
      color: var(--color-text);
    }

    .empty-state-text {
      font-size: 0.825rem;
      color: var(--color-text-muted);
      font-style: italic;
    }
  `],
})
export class DetailDrawerComponent {
  private readonly layoutStore = inject(LayoutStore);

  readonly isOpen = this.layoutStore.isDetailDrawerOpen;
  readonly activeTab = signal<DetailTab>('overview');

  setActiveTab(tab: DetailTab): void {
    this.activeTab.set(tab);
  }

  close(): void {
    this.layoutStore.closeDetailDrawer();
  }
}
