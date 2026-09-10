import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  HostListener,
  ElementRef,
  inject,
} from '@angular/core';


export interface ContextMenuEvent {
  type: 'folder' | 'document';
  id: number;
  name: string;
  x: number;
  y: number;
  data: any;
}

export interface ContextMenuAction {
  action: 'preview' | 'view' | 'rename' | 'download' | 'delete' | 'overview' | 'permissions' | 'requestAccess';
  type: 'folder' | 'document';
  id: number;
  name: string;
  data: any;
}

@Component({
  selector: 'app-explorer-context-menu',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isVisible()) {
      <div
        class="ctx-menu"
        [style.top.px]="menuY()"
        [style.left.px]="menuX()"
        (click)="$event.stopPropagation()"
      >
        @if (event()?.data?.isLocked) {
          <!-- Overview -->
          <button class="ctx-item" (click)="dispatch('overview')">
            <span class="ctx-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" x2="12" y1="20" y2="10"/>
                <line x1="18" x2="18" y1="20" y2="4"/>
                <line x1="6" x2="6" y1="20" y2="14"/>
              </svg>
            </span>
            <span>Overview</span>
          </button>

          @if (event()?.data?.canRequestAccess && event()?.data?.requestStatus === 'NONE') {
            <button class="ctx-item ctx-item-brand" (click)="dispatch('requestAccess')">
              <span class="ctx-icon" style="color: #ea580c;">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                </svg>
              </span>
              <span style="color: #ea580c; font-weight: 600;">Request Access</span>
            </button>
          } @else if (event()?.data?.requestStatus === 'PENDING') {
            <div class="ctx-item" style="opacity: 0.6; cursor: default;">
              <span class="ctx-icon" style="color: #f59e0b;">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="10"/>
                  <polyline points="12 6 12 12 16 14"/>
                </svg>
              </span>
              <span>Access Requested</span>
            </div>
          }
        } @else {
          <!-- Preview (documents only) -->
          @if (event()?.type === 'document') {
            <button class="ctx-item" (click)="dispatch('preview')">
              <span class="ctx-icon">
                <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M1 8s2.5-5 7-5 7 5 7 5"/>
                  <path d="M1 8s2.5 5 7 5 7-5 7-5"/>
                  <circle cx="8" cy="8" r="2"/>
                </svg>
              </span>
              <span>Preview</span>
            </button>
          }

          <!-- View Details -->
          <button class="ctx-item" (click)="dispatch('view')">
            <span class="ctx-icon">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                <circle cx="8" cy="8" r="6"/>
                <line x1="8" y1="8.25" x2="8" y2="11.25"/>
                <circle cx="8" cy="5" r="0.75" fill="currentColor"/>
              </svg>
            </span>
            <span>View Details</span>
          </button>

          <!-- Overview -->
          <button class="ctx-item" (click)="dispatch('overview')">
            <span class="ctx-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <line x1="12" x2="12" y1="20" y2="10"/>
                <line x1="18" x2="18" y1="20" y2="4"/>
                <line x1="6" x2="6" y1="20" y2="14"/>
              </svg>
            </span>
            <span>Overview</span>
          </button>

          <!-- Permissions -->
          <button class="ctx-item" (click)="dispatch('permissions')">
            <span class="ctx-icon">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                <path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/>
                <path d="m21 2-9.6 9.6"/>
                <circle cx="7.5" cy="15.5" r="5.5"/>
              </svg>
            </span>
            <span>Permissions</span>
          </button>

          <!-- Rename -->
          <button class="ctx-item" (click)="dispatch('rename')">
            <span class="ctx-icon">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M11.5 2.5l2 2L6 12H4v-2l7.5-7.5z"/>
                <line x1="2" y1="14" x2="14" y2="14"/>
              </svg>
            </span>
            <span>Rename</span>
          </button>

          <!-- Download -->
          <button class="ctx-item" (click)="dispatch('download')">
            <span class="ctx-icon">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M13 10v2a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-2"/>
                <polyline points="5 7 8 10 11 7"/>
                <line x1="8" y1="2" x2="8" y2="10"/>
              </svg>
            </span>
            <span>Download</span>
          </button>

          <div class="ctx-separator"></div>

          <!-- Delete -->
          <button class="ctx-item ctx-item-danger" (click)="dispatch('delete')">
            <span class="ctx-icon">
              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5">
                <polyline points="2 4 3.5 4 14 4"/>
                <path d="M5.5 4V3a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1"/>
                <path d="M13 4l-.867 9.143A1 1 0 0 1 11.14 14H4.86a1 1 0 0 1-.993-.857L3 4"/>
              </svg>
            </span>
            <span>Delete</span>
          </button>
        }
      </div>
    }
  `,
  styles: [`
    :host { position: fixed; z-index: 9999; }

    .ctx-menu {
      position: fixed;
      background: var(--sd-color-surface-elevated);
      border: 1px solid var(--sd-color-border);
      border-radius: var(--sd-radius-lg);
      box-shadow: var(--sd-shadow-floating);
      padding: var(--sd-space-2xs);
      min-width: 180px;
      animation: menuIn var(--sd-duration-fast) var(--sd-ease-spring);
      transform-origin: top left;
    }
    @keyframes menuIn {
      from { opacity: 0; transform: scale(0.95); }
      to   { opacity: 1; transform: scale(1); }
    }

    .ctx-item {
      width: 100%;
      display: flex;
      align-items: center;
      gap: var(--sd-space-sm);
      padding: var(--sd-space-xs) var(--sd-space-md);
      background: none;
      border: none;
      border-radius: var(--sd-radius-md);
      font-size: var(--sd-font-size-sm);
      font-weight: var(--sd-weight-medium);
      color: var(--sd-color-text-primary);
      cursor: pointer;
      text-align: left;
      transition: background var(--sd-duration-fast), color var(--sd-duration-fast);
    }
    .ctx-item:hover { background: var(--sd-color-primary-subtle); color: var(--sd-color-text-brand); }
    .ctx-item-danger { color: var(--sd-color-danger); }
    .ctx-item-danger:hover { background: var(--sd-color-danger-subtle); color: var(--sd-color-danger); }

    .ctx-icon {
      width: 1.25rem; height: 1.25rem;
      display: flex; align-items: center; justify-content: center;
      flex-shrink: 0;
      color: var(--sd-color-text-muted);
      transition: color var(--sd-duration-fast) ease;
    }
    .ctx-icon svg { width: 0.875rem; height: 0.875rem; }
    .ctx-item:hover .ctx-icon { color: var(--sd-color-text-brand); }
    .ctx-item-danger .ctx-icon { color: var(--sd-color-text-muted); }
    .ctx-item-danger:hover .ctx-icon { color: var(--sd-color-danger); }

    .ctx-separator {
      height: 1px;
      background: var(--sd-color-border-subtle);
      margin: var(--sd-space-2xs) 0;
    }
  `],
})
export class ExplorerContextMenuComponent {
  event = signal<ContextMenuEvent | null>(null);
  isVisible = signal<boolean>(false);

  menuX = signal<number>(0);
  menuY = signal<number>(0);

  actionDispatched = output<ContextMenuAction>();

  @HostListener('document:click')
  onDocumentClick(): void { this.close(); }

  @HostListener('document:keydown.escape')
  onEscape(): void { this.close(); }

  open(evt: ContextMenuEvent): void {
    this.event.set(evt);

    // Clamp to viewport
    const menuW = 192;
    const menuH = 160;
    const vw = window.innerWidth;
    const vh = window.innerHeight;
    const x = evt.x + menuW > vw ? evt.x - menuW : evt.x;
    const y = evt.y + menuH > vh ? evt.y - menuH : evt.y;

    this.menuX.set(x);
    this.menuY.set(y);
    this.isVisible.set(true);
  }

  close(): void {
    this.isVisible.set(false);
    this.event.set(null);
  }

  dispatch(action: ContextMenuAction['action']): void {
    const ev = this.event();
    if (!ev) return;
    this.actionDispatched.emit({
      action,
      type: ev.type,
      id: ev.id,
      name: ev.name,
      data: ev.data,
    });
    this.close();
  }
}
