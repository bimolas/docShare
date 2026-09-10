import {
  Component, ChangeDetectionStrategy, inject, signal, computed, effect, ViewChild
} from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ExplorerStore } from '../state/explorer.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { NotificationService } from '../../../core/services/notification.service';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { ExplorerBreadcrumbsComponent } from '../components/explorer-breadcrumbs.component';
import { ExplorerToolbarComponent } from '../components/explorer-toolbar.component';
import { ExplorerGridViewComponent, DocumentGridItem } from '../components/explorer-grid-view.component';
import { ExplorerListViewComponent } from '../components/explorer-list-view.component';
import { ExplorerDropZoneComponent } from '../components/explorer-drop-zone.component';
import { CreateFolderModalComponent } from '../components/create-folder-modal.component';
import { UploadFileModalComponent, FileUploadItem } from '../components/upload-file-modal.component';
import { ItemDetailDrawerComponent } from '../components/item-detail-drawer.component';
import { FilePreviewModalComponent } from '../components/file-preview-modal.component';
import { MoveConfirmationModalComponent, ItemToMovePreview, TargetLocation } from '../components/move-confirmation-modal.component';
import { AccessRequestModalComponent, AccessRequestTarget } from '../components/access-request-modal.component';
import { AccessRequestResponseDto } from '../../../core/models/access-request.model';
import { ExplorerItemSelection } from '../state/explorer.store';
import { FolderResponseDto } from '../../../core/models/folder.model';
import { DocumentResponse } from '../../../core/models/document.model';
import { HttpEventType } from '@angular/common/http';
import { LucideFolderSearch } from '@lucide/angular';

@Component({
  selector: 'app-explorer-page',
  standalone: true,
  imports: [
    ExplorerBreadcrumbsComponent,
    ExplorerToolbarComponent,
    ExplorerGridViewComponent,
    ExplorerListViewComponent,
    ExplorerDropZoneComponent,
    CreateFolderModalComponent,
    UploadFileModalComponent,
    ItemDetailDrawerComponent,
    FilePreviewModalComponent,
    MoveConfirmationModalComponent,
    AccessRequestModalComponent,
    LucideFolderSearch
],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="explorer-page-container">

      <!-- Inline Drop Zone (always visible above content) -->
      <app-explorer-drop-zone
        [currentFolderId]="explorerStore.currentFolderId()"
        (filesSelected)="onOpenUploadModalWithFiles($event)"
      />

      <!-- Unified Explorer Surface Container -->
      <div class="explorer-surface-card cloudy-border-surface">
        <!-- Explorer Header: Breadcrumbs & Toolbar -->
        <header class="explorer-surface-header">
          <div class="header-breadcrumbs-row">
            <app-explorer-breadcrumbs
              [breadcrumbs]="explorerStore.breadcrumbs()"
              [workspaceName]="activeWorkspaceName()"
              [rootFolderId]="activeRootFolderId() || 0"
              (folderSelected)="onNavigateFolder($event)"
              (breadcrumbDrop)="onBreadcrumbDrop($event)"
            />
          </div>

          <app-explorer-toolbar
            [viewMode]="explorerStore.viewMode()"
            [searchQuery]="explorerStore.searchQuery()"
            [totalItems]="explorerStore.totalItemCount()"
            [dateFilter]="explorerStore.dateFilter()"
            [typeFilter]="explorerStore.typeFilter()"
            [sizeFilter]="explorerStore.sizeFilter()"
            [sortOption]="explorerStore.sortOption()"
            (viewModeChanged)="explorerStore.setViewMode($event)"
            (searchQueryChanged)="explorerStore.setSearchQuery($event)"
            (dateFilterChanged)="explorerStore.setDateFilter($event)"
            (typeFilterChanged)="explorerStore.setTypeFilter($event)"
            (sizeFilterChanged)="explorerStore.setSizeFilter($event)"
            (sortOptionChanged)="explorerStore.setSortOption($event)"
            (newFolderClicked)="onOpenCreateFolder()"
            (uploadFileClicked)="openUploadModal()"
          />
        </header>

        <!-- Main Surface Body -->
        <main class="explorer-surface-body">
          @if (isWorkspaceLoading() && !hasWorkspace()) {
            <div class="loading-state">
              <div class="spinner"></div>
              <span>Loading workspace...</span>
            </div>
          } @else if (!hasWorkspace()) {
            <div class="empty-state">
              <div class="empty-icon-box">
                <svg lucideFolderSearch></svg>
              </div>
              <h3 class="empty-title">No Workspace Selected</h3>
              <p class="empty-subtitle">Please select a workspace from the sidebar to view folders and files.</p>
            </div>
          } @else if (!hasRootFolder()) {
            <div class="empty-state">
              <div class="empty-icon-box">
                <svg lucideFolderSearch></svg>
              </div>
              <h3 class="empty-title">Workspace Root Folder Unavailable</h3>
              <p class="empty-subtitle">The selected workspace does not have a root folder configured.</p>
            </div>
          } @else if (explorerStore.isLoading() && !explorerStore.contents()) {
            <div class="loading-state">
              <div class="spinner"></div>
              <span>Loading workspace contents...</span>
            </div>
          } @else if (explorerStore.error()) {
            <div class="error-state">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
              <p>{{ explorerStore.error() }}</p>
              <button class="btn btn-secondary" (click)="explorerStore.refresh()">Try Again</button>
            </div>
          } @else if (explorerStore.totalItemCount() === 0) {
            <div class="empty-state">
              <div class="empty-icon-box">
                <svg lucideFolderSearch></svg>
              </div>
              <h3 class="empty-title">This workspace folder is empty</h3>
              <p class="empty-subtitle">Use the drop zone above or the toolbar actions to add folders and files.</p>
            </div>
          } @else {
            @if (explorerStore.viewMode() === 'grid') {
              <app-explorer-grid-view
                [subfolders]="explorerStore.filteredSubfolders()"
                [documents]="explorerStore.filteredDocuments()"
                [selectedItems]="explorerStore.selectedItems()"
                (setSelectedItems)="explorerStore.setSelectedItems($event)"
                (folderSelected)="onNavigateFolder($event)"
                (documentSelected)="openDocumentDetail($event)"
                (previewDocument)="onOpenDocumentPreview($event)"
                (previewFolder)="onOpenFolderPreview($event)"
                (deleteFolder)="onDeleteFolder($event)"
                (deleteDocument)="onDeleteDocument($event)"
                (downloadDocument)="onDownloadDocument($event)"
                (downloadFolder)="onDownloadFolder($event)"
                (renameItem)="onRenameItem($event)"
                (openOverview)="onOpenOverview($event)"
                (openPermissions)="onOpenPermissions($event)"
                (initiateMove)="onInitiateMove($event)"
                (requestAccess)="onRequestAccess($event)"
              />
            } @else {
              <app-explorer-list-view
                [subfolders]="explorerStore.filteredSubfolders()"
                [documents]="explorerStore.filteredDocuments()"
                [selectedItems]="explorerStore.selectedItems()"
                (setSelectedItems)="explorerStore.setSelectedItems($event)"
                (folderSelected)="onNavigateFolder($event)"
                (documentSelected)="openDocumentDetail($event)"
                (previewDocument)="onOpenDocumentPreview($event)"
                (previewFolder)="onOpenFolderPreview($event)"
                (deleteFolder)="onDeleteFolder($event)"
                (deleteDocument)="onDeleteDocument($event)"
                (downloadDocument)="onDownloadDocument($event)"
                (downloadFolder)="onDownloadFolder($event)"
                (renameItem)="onRenameItem($event)"
                (openOverview)="onOpenOverview($event)"
                (openPermissions)="onOpenPermissions($event)"
                (initiateMove)="onInitiateMove($event)"
                (requestAccess)="onRequestAccess($event)"
              />
            }
          }
        </main>
      </div>

      <!-- Modals & Drawers -->
      <app-create-folder-modal
        [isOpen]="isCreateFolderOpen()"
        (closed)="isCreateFolderOpen.set(false)"
        (created)="onFolderCreated($event)"
      />

      <!-- Pre-Upload File Preview Centered Modal -->
      <app-upload-file-modal
        [isOpen]="isUploadFileOpen()"
        [initialFiles]="pendingUploadFiles()"
        [currentFolderId]="explorerStore.currentFolderId()"
        (closed)="isUploadFileOpen.set(false)"
        (uploadCompleted)="onUploadSuccess()"
      />

      <app-item-detail-drawer
        [isOpen]="!!explorerStore.activeDetailItem()"
        [item]="explorerStore.activeDetailItem()"
        (closed)="explorerStore.closeDetailDrawer()"
        (openPreview)="onOpenItemPreview($event)"
        (delete)="onItemDeleteFromDrawer($event)"
        (download)="onDownloadFromDrawer($event)"
        (downloadFolder)="onDownloadFolder($event)"
        (requestAccess)="onRequestAccess($event)"
        (requestCancelled)="explorerStore.refresh()"
      />

      <app-file-preview-modal
        [isOpen]="isPreviewOpen()"
        [item]="previewItem()"
        [hasPrevious]="hasPreviousPreview()"
        [hasNext]="hasNextPreview()"
        (closed)="isPreviewOpen.set(false)"
        (download)="onDownloadFromDrawer($event)"
        (openFolder)="onNavigateFolder($event)"
        (previous)="onPreviousPreview()"
        (next)="onNextPreview()"
      />

      <app-move-confirmation-modal
        [isOpen]="isMoveModalOpen()"
        [items]="itemsToMovePreview()"
        [targetLocation]="moveTargetLocation()"
        [isMoving]="isMovingItems()"
        (closed)="isMoveModalOpen.set(false)"
        (confirmed)="onConfirmMove()"
      />

      <app-access-request-modal
        [isOpen]="isRequestModalOpen()"
        [target]="requestTargetResource()"
        (closed)="isRequestModalOpen.set(false)"
        (requestSubmitted)="onRequestSubmitted($event)"
      />
    </div>
  `,
  styles: [`
    .explorer-page-container {
      display: flex;
      flex-direction: column;
      height: 100%;
      padding: 1.25rem 1.5rem;
      box-sizing: border-box;
      gap: 1rem;
      background-color: #f8fafc;
    }

    .explorer-surface-card {
      display: flex;
      flex-direction: column;
      flex: 1;
      overflow: hidden;
      min-height: 0;
    }

    .explorer-surface-header {
      display: flex;
      flex-direction: column;
      gap: 0.875rem;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #f1f5f9;
      background: #ffffff;
    }

    .header-breadcrumbs-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .explorer-surface-body {
      flex: 1;
      overflow-y: auto;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      background: #ffffff;
    }

    /* Loading / Error / Empty States */
    .loading-state, .error-state, .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 4rem 1.5rem;
      text-align: center;
      flex: 1;
    }
    .loading-state {
      gap: 1rem;
      color: #64748b;
      font-weight: 500;
      font-size: 0.875rem;
    }
    .spinner {
      width: 2rem;
      height: 2rem;
      border: 2px solid #e2e8f0;
      border-top-color: #ff5500;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin { to { transform: rotate(360deg); } }

    .error-state {
      color: #ef4444;
      gap: 0.75rem;
    }
    .error-state svg {
      width: 2.5rem;
      height: 2.5rem;
    }

    .empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 4rem 2rem;
      text-align: center;
      background: transparent;
      border: none;
    }
    .empty-icon-box {
      color: #94a3b8;
      display: flex;
      justify-content: center;
      margin-bottom: 0.75rem;
      background: transparent;
    }
    .empty-icon-box svg {
      width: 3.5rem;
      height: 3.5rem;
      stroke-width: 1.2;
      opacity: 0.8;
    }
    .empty-title {
      font-size: 1.0625rem;
      font-weight: 600;
      color: #64748b;
      margin: 0 0 0.375rem;
    }
    .empty-subtitle {
      font-size: 0.875rem;
      color: #94a3b8;
      margin: 0;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5625rem 1rem;
      font-size: 0.8125rem;
      font-weight: 600;
      border-radius: 0.375rem;
      cursor: pointer;
      border: none;
      transition: all 0.15s ease;
    }
    .btn svg {
      width: 0.9375rem;
      height: 0.9375rem;
    }
    .btn-secondary {
      background: #ffffff;
      color: #334155;
      border: 1px solid #cbd5e1;
    }
    .btn-secondary:hover {
      background: #f8fafc;
      border-color: #94a3b8;
    }
    .btn-primary {
      background: #ff5500;
      color: #ffffff;
    }
    .btn-primary:hover {
      background: #e04b00;
    }
  `],
})
export class ExplorerPageComponent {
  readonly explorerStore          = inject(ExplorerStore);
  readonly workspaceContextStore  = inject(WorkspaceContextStore);
  private readonly route          = inject(ActivatedRoute);
  private readonly notificationService = inject(NotificationService);
  private readonly documentApiService  = inject(DocumentApiService);
  private readonly folderApiService    = inject(FolderApiService);

  readonly activeWorkspace = this.workspaceContextStore.activeWorkspace;
  readonly activeRootFolderId = this.workspaceContextStore.activeRootFolderId;
  readonly isWorkspaceLoading = this.workspaceContextStore.isLoading;

  readonly activeWorkspaceName = computed(() => {
    return this.activeWorkspace()?.name || 'Workspace';
  });

  readonly hasWorkspace = computed(() => {
    return !!this.activeWorkspace();
  });

  readonly hasRootFolder = computed(() => {
    const rootId = this.activeRootFolderId();
    return typeof rootId === 'number' && rootId > 0;
  });

  isCreateFolderOpen = signal<boolean>(false);
  isUploadFileOpen   = signal<boolean>(false);
  pendingUploadFiles = signal<File[]>([]);

  // Move confirmation modal state
  isMoveModalOpen    = signal<boolean>(false);
  itemsToMovePreview = signal<ItemToMovePreview[]>([]);
  moveTargetLocation = signal<TargetLocation | null>(null);
  isMovingItems      = signal<boolean>(false);
  private itemsToMove = signal<ExplorerItemSelection[]>([]);

  // Access Request modal state
  isRequestModalOpen = signal<boolean>(false);
  requestTargetResource = signal<AccessRequestTarget | null>(null);

  openUploadModal(): void {
    if (!this.hasRootFolder()) {
      this.notificationService.error('Cannot upload: workspace root folder is not available.');
      return;
    }
    this.pendingUploadFiles.set([]);
    this.isUploadFileOpen.set(true);
  }

  onOpenUploadModalWithFiles(files: File[]): void {
    if (!files || files.length === 0) return;
    if (!this.hasRootFolder()) {
      this.notificationService.error('Cannot upload: workspace root folder is not available.');
      return;
    }
    this.pendingUploadFiles.set(files);
    this.isUploadFileOpen.set(true);
  }

  onOpenCreateFolder(): void {
    if (!this.hasRootFolder()) {
      this.notificationService.error('Cannot create folder: workspace root folder is not available.');
      return;
    }
    this.isCreateFolderOpen.set(true);
  }

  isPreviewOpen = signal<boolean>(false);
  previewItem   = signal<{ type: 'folder' | 'document'; data: any } | null>(null);

  hasPreviousPreview = computed(() => {
    const current = this.previewItem();
    if (!current || current.type !== 'document') return false;
    const docs = this.explorerStore.filteredDocuments();
    const idx = docs.findIndex(d => Number(d.id) === Number(current.data.id));
    return idx > 0;
  });

  hasNextPreview = computed(() => {
    const current = this.previewItem();
    if (!current || current.type !== 'document') return false;
    const docs = this.explorerStore.filteredDocuments();
    const idx = docs.findIndex(d => Number(d.id) === Number(current.data.id));
    return idx >= 0 && idx < docs.length - 1;
  });

  constructor() {
    this.route.queryParamMap.subscribe((params) => {
      const qFolderId = params.get('folderId');
      if (qFolderId) {
        const folderIdNum = Number(qFolderId);
        if (!isNaN(folderIdNum) && folderIdNum > 0 && this.explorerStore.currentFolderId() !== folderIdNum) {
          this.explorerStore.loadFolderContents(folderIdNum).subscribe();
        }
      }
    });

    effect(() => {
      const ws = this.activeWorkspace();
      const rootFolderId = this.activeRootFolderId();
      const queryFolderId = this.route.snapshot.queryParamMap.get('folderId');

      if (queryFolderId) {
        const folderIdNum = Number(queryFolderId);
        if (!isNaN(folderIdNum) && folderIdNum > 0) {
          if (this.explorerStore.currentFolderId() !== folderIdNum) {
            this.explorerStore.loadFolderContents(folderIdNum).subscribe();
          }
          return;
        }
      }

      if (ws && typeof rootFolderId === 'number' && rootFolderId > 0) {
        const breadcrumbs    = this.explorerStore.breadcrumbs();
        const currentRootBreadcrumbId = breadcrumbs.length > 0 ? breadcrumbs[0].id : null;
        if (currentRootBreadcrumbId !== rootFolderId || this.explorerStore.currentFolderId() === null) {
          this.explorerStore.loadFolderContents(rootFolderId).subscribe();
        }
      } else if (!ws && !this.isWorkspaceLoading()) {
        this.explorerStore.reset();
      }
    }, { allowSignalWrites: true });
  }

  onNavigateFolder(folderId: number): void {
    if (folderId) this.explorerStore.loadFolderContents(folderId).subscribe();
  }

  openDocumentDetail(document: DocumentGridItem | DocumentResponse): void {
    this.explorerStore.openDetailDrawer('document', document);
  }

  onOpenDocumentPreview(doc: DocumentGridItem | DocumentResponse | any): void {
    this.previewItem.set({ type: 'document', data: doc });
    this.isPreviewOpen.set(true);
  }

  onOpenOverview(item: { type: 'folder' | 'document'; data: any }): void {
    this.explorerStore.openDetailDrawer(item.type, item.data, 'overview');
  }

  onOpenPermissions(item: { type: 'folder' | 'document'; data: any }): void {
    this.explorerStore.openDetailDrawer(item.type, item.data, 'access');
  }

  onOpenFolderPreview(folderId: number): void {
    const folder = this.explorerStore.filteredSubfolders().find(f => f.id === folderId);
    this.previewItem.set({ type: 'folder', data: folder || { id: folderId, name: `Folder ${folderId}` } });
    this.isPreviewOpen.set(true);
  }

  onOpenItemPreview(item: { type: 'folder' | 'document'; data: any }): void {
    if (item && item.data) {
      // Close the detail drawer first so the preview modal has full focus
      this.explorerStore.closeDetailDrawer();
      this.previewItem.set(item);
      this.isPreviewOpen.set(true);
    }
  }

  onPreviousPreview(): void {
    const current = this.previewItem();
    if (!current || current.type !== 'document') return;
    const docs = this.explorerStore.filteredDocuments();
    const idx = docs.findIndex(d => Number(d.id) === Number(current.data.id));
    if (idx > 0) {
      this.previewItem.set({ type: 'document', data: docs[idx - 1] });
    }
  }

  onNextPreview(): void {
    const current = this.previewItem();
    if (!current || current.type !== 'document') return;
    const docs = this.explorerStore.filteredDocuments();
    const idx = docs.findIndex(d => Number(d.id) === Number(current.data.id));
    if (idx >= 0 && idx < docs.length - 1) {
      this.previewItem.set({ type: 'document', data: docs[idx + 1] });
    }
  }

  onDeleteFolder(folderId: number): void {
    if (confirm('Are you sure you want to delete this folder?')) {
      this.explorerStore.deleteFolder(folderId).subscribe({
        next: () => this.notificationService.success('Folder deleted successfully.'),
        error: () => this.notificationService.error('Failed to delete folder. Please try again.'),
      });
    }
  }

  onDeleteDocument(documentId: number): void {
    if (confirm('Are you sure you want to delete this document?')) {
      this.explorerStore.deleteDocument(documentId).subscribe({
        next: () => this.notificationService.success('Document deleted successfully.'),
        error: () => this.notificationService.error('Failed to delete document. Please try again.'),
      });
    }
  }

  onItemDeleteFromDrawer(item: { type: 'folder' | 'document'; id: number }): void {
    if (item.type === 'folder') {
      this.onDeleteFolder(item.id);
    } else {
      this.onDeleteDocument(item.id);
    }
  }

  onDownloadFromDrawer(documentData: any): void {
    if (documentData?.id) this.onDownloadDocument(Number(documentData.id));
  }

  onDownloadFolder(folderId: number): void {
    if (isNaN(folderId)) { this.notificationService.error('Invalid folder ID.'); return; }
    this.notificationService.info('Preparing folder download...');
    this.folderApiService.downloadFolder(folderId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        const folder = this.explorerStore.filteredSubfolders().find(f => f.id === folderId);
        const folderName = folder ? folder.name : `folder_${folderId}`;
        a.download = `${folderName}.zip`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: () => this.notificationService.error('Failed to download folder.'),
    });
  }

  onDownloadDocument(doc: DocumentGridItem | number): void {
    const documentId = typeof doc === 'number' ? doc : Number(doc.id);
    if (isNaN(documentId)) { this.notificationService.error('Invalid document ID.'); return; }
    this.notificationService.info('Generating download link...');
    this.documentApiService.getDownloadUrl(documentId).subscribe({
      next: (res) => {
        if (res.downloadUrl) {
          window.open(res.downloadUrl, '_blank');
        } else {
          this.notificationService.error('Download link not available.');
        }
      },
      error: () => this.notificationService.error('Failed to generate download URL.'),
    });
  }

  onFolderCreated(folderName: string): void {
    this.isCreateFolderOpen.set(false);
    this.explorerStore.createSubfolder(folderName).subscribe({
      next: (folder) => this.notificationService.success(`Folder "${folder.name}" created successfully.`),
      error: () => this.notificationService.error('Failed to create folder. Please try again.'),
    });
  }

  onUploadSuccess(): void {
    this.notificationService.success('File uploaded successfully.');
    this.explorerStore.refresh();
  }

  onRenameItem(event: { type: 'folder' | 'document'; id: number; newName: string }): void {
    const trimmedName = event.newName ? event.newName.trim() : '';
    if (!trimmedName) {
      this.notificationService.error('Name cannot be empty.');
      return;
    }

    if (event.type === 'folder') {
      this.explorerStore.renameFolder(event.id, trimmedName).subscribe({
        next: () => this.notificationService.success(`Folder renamed to "${trimmedName}".`),
        error: (err) => this.notificationService.error(err?.error?.message || 'Failed to rename folder.'),
      });
    } else {
      this.explorerStore.renameDocument(event.id, trimmedName).subscribe({
        next: () => this.notificationService.success(`Document renamed to "${trimmedName}".`),
        error: (err) => this.notificationService.error(err?.error?.message || 'Failed to rename document.'),
      });
    }
  }

  onInitiateMove(data: { items: ExplorerItemSelection[]; targetFolderId: number; targetFolderName: string }): void {
    if (!data.items || data.items.length === 0) return;

    // Check if target folder is the same as current folder
    if (data.targetFolderId === this.explorerStore.currentFolderId()) {
      this.notificationService.info('Items are already in this folder.');
      return;
    }

    // Check if trying to move a folder into itself
    const invalidMove = data.items.some(item => item.type === 'folder' && Number(item.id) === Number(data.targetFolderId));
    if (invalidMove) {
      this.notificationService.error('Cannot move a folder into itself.');
      return;
    }

    this.itemsToMove.set(data.items);
    this.moveTargetLocation.set({ id: data.targetFolderId, name: data.targetFolderName });

    // Build preview list
    const subfolders = this.explorerStore.filteredSubfolders();
    const documents = this.explorerStore.filteredDocuments();

    const previews: ItemToMovePreview[] = data.items.map(item => {
      if (item.type === 'folder') {
        const folder = subfolders.find(f => Number(f.id) === Number(item.id));
        return {
          type: 'folder',
          id: item.id,
          name: folder ? folder.name : `Folder #${item.id}`
        };
      } else {
        const doc: any = documents.find(d => Number(d.id) === Number(item.id));
        return {
          type: 'document',
          id: item.id,
          name: doc ? (doc.title || doc.fileName || doc.name || `Document #${item.id}`) : `Document #${item.id}`,
          size: doc ? (doc.fileSize || doc.fileSizeBytes) : undefined,
          contentType: doc ? (doc.contentType || doc.mimeType) : undefined
        };
      }
    });

    this.itemsToMovePreview.set(previews);
    this.isMoveModalOpen.set(true);
  }

  onBreadcrumbDrop(data: { targetFolderId: number; targetName: string; items?: any[] }): void {
    let itemsToProcess: ExplorerItemSelection[] = [];
    if (data.items && data.items.length > 0) {
      itemsToProcess = data.items.map(i => ({ type: i.type, id: i.id }));
    } else if (this.explorerStore.selectedItems().length > 0) {
      itemsToProcess = [...this.explorerStore.selectedItems()];
    }

    if (itemsToProcess.length === 0) return;

    this.onInitiateMove({
      items: itemsToProcess,
      targetFolderId: data.targetFolderId,
      targetFolderName: data.targetName
    });
  }

  onConfirmMove(): void {
    const target = this.moveTargetLocation();
    const items = this.itemsToMove();
    if (!target || items.length === 0) return;

    this.isMovingItems.set(true);
    this.explorerStore.bulkMoveItems(items, target.id).subscribe({
      next: () => {
        this.isMovingItems.set(false);
        this.isMoveModalOpen.set(false);
        this.itemsToMove.set([]);
        this.notificationService.success(`Moved ${items.length} item${items.length > 1 ? 's' : ''} to "${target.name}".`);
      },
      error: (err) => {
        this.isMovingItems.set(false);
        this.notificationService.error(err?.error?.message || 'Failed to move items. Please try again.');
      }
    });
  }

  onRequestAccess(event: AccessRequestTarget): void {
    this.requestTargetResource.set(event);
    this.isRequestModalOpen.set(true);
  }

  onRequestSubmitted(event: AccessRequestResponseDto): void {
    this.isRequestModalOpen.set(false);
    const resName = event.documentName || event.folderName || this.requestTargetResource()?.name || 'resource';
    this.notificationService.success(`Access request submitted for "${resName}".`);
    this.explorerStore.refresh();
  }
}
