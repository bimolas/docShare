import { Component, ChangeDetectionStrategy, input, output, computed, signal } from '@angular/core';

import { FolderBreadcrumbDto } from '../../../core/models/folder.model';

@Component({
  selector: 'app-explorer-breadcrumbs',
  standalone: true,
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <nav class="breadcrumbs-container" aria-label="Folder Path Breadcrumb">
      <!-- Home Segment -->
      <div
        class="breadcrumb-item home-item"
        [class.drag-over]="dragOverBreadcrumbId() === effectiveRootFolderId()"
        (click)="onBreadcrumbClick(effectiveRootFolderId())"
        (dragover)="onDragOver($event, effectiveRootFolderId())"
        (dragleave)="onDragLeave($event, effectiveRootFolderId())"
        (drop)="onDrop($event, effectiveRootFolderId(), effectiveWorkspaceName())"
        title="Home"
      >
        <svg class="home-icon" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>
          <polyline points="9 22 9 12 15 12 15 22"/>
        </svg>
        <span class="breadcrumb-label">Home</span>
      </div>

      <span class="breadcrumb-separator" aria-hidden="true">/</span>

      <!-- Workspace Root Segment -->
      <div
        class="breadcrumb-item space-item"
        [class.active]="displayedBreadcrumbs().length === 0"
        [class.drag-over]="dragOverBreadcrumbId() === effectiveRootFolderId()"
        (click)="displayedBreadcrumbs().length > 0 && onBreadcrumbClick(effectiveRootFolderId())"
        (dragover)="onDragOver($event, effectiveRootFolderId())"
        (dragleave)="onDragLeave($event, effectiveRootFolderId())"
        (drop)="onDrop($event, effectiveRootFolderId(), effectiveWorkspaceName())"
        [title]="effectiveWorkspaceName()"
      >
        <span class="breadcrumb-label">{{ effectiveWorkspaceName() }}</span>
      </div>

      <!-- Nested Folder Breadcrumbs -->
      @for (item of displayedBreadcrumbs(); track item.id; let last = $last) {
        <span class="breadcrumb-separator" aria-hidden="true">/</span>

        <div
          class="breadcrumb-item"
          [class.active]="last"
          [class.drag-over]="dragOverBreadcrumbId() === item.id"
          (click)="!last && onBreadcrumbClick(item.id)"
          (dragover)="onDragOver($event, item.id)"
          (dragleave)="onDragLeave($event, item.id)"
          (drop)="onDrop($event, item.id, item.name)"
          [title]="item.name"
        >
          <span class="breadcrumb-label">{{ item.name }}</span>
        </div>
      }
    </nav>
  `,
  styles: [`
    .breadcrumbs-container {
      display: flex;
      align-items: center;
      gap: 0.125rem;
      padding: 0.125rem 0;
      overflow-x: auto;
      white-space: nowrap;
      scrollbar-width: none;
    }
    .breadcrumbs-container::-webkit-scrollbar {
      display: none;
    }
    .breadcrumb-item {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #64748b;
      cursor: pointer;
      padding: 0.25rem 0.5rem;
      border-radius: 0.375rem;
      border: 1.5px dashed transparent;
      transition: background-color 0.15s ease, color 0.15s ease, border-color 0.15s ease;
      flex-shrink: 0;
    }
    .breadcrumb-item:hover:not(.active) {
      color: #0f172a;
      background-color: #f1f5f9;
    }
    .breadcrumb-item.active {
      color: #0f172a;
      font-weight: 600;
      background-color: transparent;
      cursor: default;
    }
    .breadcrumb-item.drag-over {
      background-color: #fff7ed !important;
      border-color: #ff5500 !important;
      color: #c2410c !important;
    }
    .home-item .home-icon {
      width: 0.875rem;
      height: 0.875rem;
      color: #64748b;
      transition: color 0.15s ease;
    }
    .home-item:hover .home-icon {
      color: #0f172a;
    }
    .breadcrumb-separator {
      font-size: 0.8125rem;
      font-weight: 400;
      color: #cbd5e1;
      user-select: none;
      padding: 0 0.125rem;
    }
    .breadcrumb-label {
      max-width: 200px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  `],
})
export class ExplorerBreadcrumbsComponent {
  breadcrumbs = input<FolderBreadcrumbDto[]>([]);
  workspaceName = input<string>('');
  rootFolderId = input<number>(0);

  // Backward-compatibility aliases for unmigrated callers
  subspaceName = input<string>('');
  subspaceFolderId = input<number>(0);

  folderSelected = output<number>();
  breadcrumbDrop = output<{ targetFolderId: number; targetName: string; items?: any[] }>();

  dragOverBreadcrumbId = signal<number | null>(null);

  readonly effectiveWorkspaceName = computed(() => {
    return this.workspaceName() || this.subspaceName() || 'Workspace';
  });

  readonly effectiveRootFolderId = computed(() => {
    return this.rootFolderId() || this.subspaceFolderId() || 0;
  });

  readonly displayedBreadcrumbs = computed(() => {
    const rootId = this.effectiveRootFolderId();
    const name = this.effectiveWorkspaceName();
    const list = this.breadcrumbs() || [];

    return list.filter((item, index) => {
      if (rootId && item.id === rootId) return false;
      if (index === 0 && name && item.name.toLowerCase() === name.toLowerCase()) return false;
      return true;
    });
  });

  onBreadcrumbClick(folderId: number): void {
    if (folderId) {
      this.folderSelected.emit(folderId);
    }
  }

  onDragOver(event: DragEvent, folderId: number): void {
    if (!folderId) return;
    event.preventDefault();
    event.stopPropagation();
    if (event.dataTransfer) {
      event.dataTransfer.dropEffect = 'move';
    }
    this.dragOverBreadcrumbId.set(folderId);
  }

  onDragLeave(event: DragEvent, folderId: number): void {
    event.preventDefault();
    if (this.dragOverBreadcrumbId() === folderId) {
      this.dragOverBreadcrumbId.set(null);
    }
  }

  onDrop(event: DragEvent, folderId: number, targetName: string): void {
    event.preventDefault();
    event.stopPropagation();
    this.dragOverBreadcrumbId.set(null);
    if (folderId) {
      let items: any[] = [];
      try {
        const json = event.dataTransfer?.getData('application/json');
        if (json) items = JSON.parse(json);
      } catch {}
      this.breadcrumbDrop.emit({ targetFolderId: folderId, targetName, items });
    }
  }
}


