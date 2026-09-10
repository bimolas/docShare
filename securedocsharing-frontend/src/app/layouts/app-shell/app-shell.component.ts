import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  HostListener,
} from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SidebarComponent } from './sidebar.component';
import { TopNavComponent } from './top-nav.component';
import { DetailDrawerComponent } from './detail-drawer.component';
import { ToastContainerComponent } from '../../shared/ui';
import { CreateOrganizationModalComponent } from '../../shared/dialogs/create-organization-modal/create-organization-modal.component';
import { CreateWorkspaceModalComponent } from '../../shared/dialogs/create-workspace-modal/create-workspace-modal.component';
import { LayoutStore } from '../../state/layout/layout.store';

/**
 * AppShellComponent: Root layout container.
 * Integrates LayoutStore to control sidebar collapse, mobile off-canvas drawer,
 * detail inspector drawer, and global toast notifications.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterOutlet,
    SidebarComponent,
    TopNavComponent,
    DetailDrawerComponent,
    ToastContainerComponent,
    CreateOrganizationModalComponent,
    CreateWorkspaceModalComponent,
  ],
  template: `
    <div
      class="app-shell"
      [class.sidebar-collapsed]="isSidebarCollapsed()"
      [class.detail-drawer-open]="isDetailDrawerOpen()"
    >
      @if (isMobileOpen()) {
        <div class="mobile-backdrop" (click)="closeMobileDrawer()"></div>
      }

      <app-sidebar />

      <div class="app-main">
        <app-top-nav
          (toggleSidebar)="onTopNavMenuToggle()"
        />

        <main class="app-content">
          <router-outlet />
        </main>
      </div>

      <app-detail-drawer />
      <sd-toast-container />
      <app-create-organization-modal />
      <app-create-workspace-modal />
    </div>
  `,
  styles: [`
    .app-shell {
      display: flex;
      height: 100vh;
      width: 100vw;
      overflow: hidden;
      background: var(--color-bg);
      position: relative;
    }

    .app-main {
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
      min-width: 0;
    }

    .app-content {
      flex: 1;
      overflow-y: auto;
      padding: 1.5rem;
    }

    .mobile-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.45);
      backdrop-filter: blur(2px);
      z-index: 45;
      animation: fadeIn 0.2s ease;
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
  `],
})
export class AppShellComponent {
  readonly layoutStore = inject(LayoutStore);

  readonly isSidebarCollapsed = this.layoutStore.isSidebarCollapsed;
  readonly isMobileOpen = this.layoutStore.isMobileDrawerOpen;
  readonly isDetailDrawerOpen = this.layoutStore.isDetailDrawerOpen;

  closeMobileDrawer(): void {
    this.layoutStore.closeMobileDrawer();
  }

  onTopNavMenuToggle(): void {
    if (typeof window !== 'undefined' && window.innerWidth < 1024) {
      this.layoutStore.toggleMobileDrawer();
    } else {
      this.layoutStore.toggleSidebar();
    }
  }

  @HostListener('window:keydown.escape')
  onEscapeKey(): void {
    if (this.isMobileOpen()) {
      this.layoutStore.closeMobileDrawer();
    }
    if (this.isDetailDrawerOpen()) {
      this.layoutStore.closeDetailDrawer();
    }
  }
}

