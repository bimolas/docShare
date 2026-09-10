import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  effect,
  inject,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import {
  FolderResponseDto,
  FolderBreadcrumbDto,
  FolderContentResponseDto,
} from '../../../core/models/folder.model';
import { ExplorerToolbarComponent } from '../../explorer/components/explorer-toolbar.component';
import { ExplorerGridViewComponent, DocumentGridItem } from '../../explorer/components/explorer-grid-view.component';
import { ExplorerListViewComponent } from '../../explorer/components/explorer-list-view.component';
import { FilePreviewModalComponent } from '../../explorer/components/file-preview-modal.component';
import { ItemDetailDrawerComponent } from '../../explorer/components/item-detail-drawer.component';
import { AccessRequestModalComponent, AccessRequestTarget } from '../../explorer/components/access-request-modal.component';
import { AccessRequestResponseDto } from '../../../core/models/access-request.model';
import { ExplorerItemSelection, ExplorerViewMode } from '../../explorer/state/explorer.store';
import { IconComponent } from '../../../shared/components/icon/icon.component';

export interface SharedFolderRoot {
  id: number;
  name: string;
}

@Component({
  selector: 'app-shared-folder-window',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IconComponent,
    ExplorerToolbarComponent,
    ExplorerGridViewComponent,
    ExplorerListViewComponent,
    FilePreviewModalComponent,
    ItemDetailDrawerComponent,
    AccessRequestModalComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen() && rootFolder()) {
      <div class="window-overlay-scrim" (click)="onBackdropClick($event)">
        <div class="shared-window-card" (click)="$event.stopPropagation()" role="dialog" aria-modal="true" aria-label="Shared Folder Explorer Window">
          <!-- Window Header Bar -->
          <header class="window-header">
            <div class="header-left">
              <div class="header-icon-box">
                <app-icon name="folder-open" [size]="20" />
              </div>
              <div class="header-titles">
                <div class="title-row">
                  <h2 class="window-title" [title]="rootFolder()!.name">
                    {{ rootFolder()!.name }}
                  </h2>
                  <span class="shared-scope-pill">Shared Folder</span>
                </div>

                <!-- Scoped Breadcrumb Bar Clamped to Shared Root -->
                <nav class="scoped-breadcrumbs" aria-label="Shared folder breadcrumbs">
                  <ol class="breadcrumb-list">
                    @for (crumb of scopedBreadcrumbs(); track crumb.id; let isLast = $last) {
                      <li class="breadcrumb-item" [class.active]="isLast">
                        @if (!isLast) {
                          <button
                            type="button"
                            class="crumb-link"
                            (click)="onNavigateFolder(crumb.id)"
                            [title]="crumb.name"
                          >
                            {{ crumb.name }}
                          </button>
                          <app-icon name="chevron-right" [size]="12" class="crumb-separator" />
                        } @else {
                          <span class="crumb-current" [title]="crumb.name">
                            {{ crumb.name }}
                          </span>
                        }
                      </li>
                    }
                  </ol>
                </nav>
              </div>
            </div>

            <div class="header-actions">
              <!-- Refresh Button -->
              <button
                type="button"
                class="icon-action-btn"
                (click)="loadFolderContents(currentFolderId())"
                [disabled]="isLoading()"
                title="Refresh contents"
                aria-label="Refresh folder contents"
              >
                <app-icon name="refresh-cw" [size]="15" [class.spin]="isLoading()" />
              </button>

              <!-- Close Window Button -->
              <button
                type="button"
                class="close-btn"
                (click)="closed.emit()"
                title="Close window (Esc)"
                aria-label="Close window"
              >
                <app-icon name="x" [size]="18" />
              </button>
            </div>
          </header>

          <!-- Explorer Toolbar: Search, Filters, View Modes -->
          <div class="window-toolbar-wrap">
            <app-explorer-toolbar
              [viewMode]="viewMode()"
              [searchQuery]="searchQuery()"
              [totalItems]="totalFilteredCount()"
              [dateFilter]="dateFilter()"
              [typeFilter]="typeFilter()"
              [sizeFilter]="sizeFilter()"
              [sortOption]="sortOption()"
              [showCreateActions]="false"
              (viewModeChanged)="viewMode.set($event)"
              (searchQueryChanged)="searchQuery.set($event)"
              (dateFilterChanged)="dateFilter.set($event)"
              (typeFilterChanged)="typeFilter.set($event)"
              (sizeFilterChanged)="sizeFilter.set($event)"
              (sortOptionChanged)="sortOption.set($event)"
            />
          </div>

          <!-- Main Explorer Surface Body -->
          <main class="window-content-body custom-scrollbar">
            @if (isLoading() && !hasLoadedContent()) {
              <div class="window-loading-state">
                <div class="spinner-lg"></div>
                <p>Loading folder contents...</p>
              </div>
            } @else if (error()) {
              <div class="window-error-state">
                <app-icon name="alert-circle" [size]="28" />
                <p>{{ error() }}</p>
                <button type="button" class="btn-retry" (click)="loadFolderContents(currentFolderId())">
                  Try Again
                </button>
              </div>
            } @else if (totalFilteredCount() === 0) {
              <div class="window-empty-state">
                <div class="empty-icon-wrap">
                  <app-icon name="folder-search" [size]="32" />
                </div>
                <h3 class="empty-title">
                  @if (searchQuery()) {
                    No matching items found
                  } @else {
                    This shared folder is empty
                  }
                </h3>
                <p class="empty-subtitle">
                  @if (searchQuery()) {
                    Try clearing or adjusting your search filters.
                  } @else {
                    No accessible subfolders or documents have been shared in this directory.
                  }
                </p>
                @if (searchQuery()) {
                  <button type="button" class="btn-clear-filter" (click)="searchQuery.set('')">
                    Clear Search
                  </button>
                }
              </div>
            } @else {
              @if (viewMode() === 'grid') {
                <app-explorer-grid-view
                  [subfolders]="filteredSubfolders()"
                  [documents]="filteredDocuments()"
                  [selectedItems]="selectedItems()"
                  (setSelectedItems)="selectedItems.set($event)"
                  (folderSelected)="onNavigateFolder($event)"
                  (documentSelected)="onDocumentClick($event)"
                  (previewDocument)="onPreviewDocument($event)"
                  (openOverview)="onOpenOverview($event)"
                  (openPermissions)="onOpenPermissions($event)"
                  (requestAccess)="onRequestAccess($event)"
                  (downloadDocument)="onDownloadDocument($event)"
                  (downloadFolder)="onDownloadFolder($event)"
                />
              } @else {
                <app-explorer-list-view
                  [subfolders]="filteredSubfolders()"
                  [documents]="filteredDocuments()"
                  [selectedItems]="selectedItems()"
                  (setSelectedItems)="selectedItems.set($event)"
                  (folderSelected)="onNavigateFolder($event)"
                  (documentSelected)="onDocumentClick($event)"
                  (previewDocument)="onPreviewDocument($event)"
                  (openOverview)="onOpenOverview($event)"
                  (openPermissions)="onOpenPermissions($event)"
                  (requestAccess)="onRequestAccess($event)"
                  (downloadDocument)="onDownloadDocument($event)"
                  (downloadFolder)="onDownloadFolder($event)"
                />
              }
            }
          </main>

          <!-- Footer Bar -->
          <footer class="window-footer">
            <div class="footer-info">
              <span>{{ filteredSubfolders().length }} folder(s), {{ filteredDocuments().length }} document(s)</span>
            </div>
            <div class="footer-actions">
              <button type="button" class="btn-close-footer" (click)="closed.emit()">
                Close Window
              </button>
            </div>
          </footer>
        </div>
      </div>

      <!-- Nested Modals within Window Context -->
      <app-file-preview-modal
        [isOpen]="isPreviewOpen()"
        [item]="previewItem()"
        (closed)="isPreviewOpen.set(false)"
        (download)="onDownloadDocument($event)"
        (openDetails)="onOpenDetailsFromPreview($event)"
      />

      <app-item-detail-drawer
        [isOpen]="isDetailOpen()"
        [item]="activeDetailItem()"
        (closed)="isDetailOpen.set(false)"
        (openPreview)="onOpenPreviewFromDetail($event)"
        (requestAccess)="onRequestAccess($event)"
        (requestCancelled)="onDetailRequestCancelled()"
      />

      <app-access-request-modal
        [isOpen]="isRequestModalOpen()"
        [target]="requestResource()"
        (closed)="isRequestModalOpen.set(false)"
        (requestSubmitted)="onRequestSubmitted($event)"
      />
    }
  `,
  styles: [`
    .window-overlay-scrim {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.65);
      backdrop-filter: blur(4px);
      z-index: 900;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
      animation: fadeIn 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    .shared-window-card {
      width: 100%;
      max-width: 1360px;
      height: min(94vh, 920px);
      background: #f8fafc;
      border: 1px solid #cbd5e1;
      border-radius: 0.75rem;
      box-shadow: 0 12px 32px rgba(15, 23, 42, 0.2);
      display: flex;
      flex-direction: column;
      overflow: hidden;
      animation: windowAppear 0.22s cubic-bezier(0.16, 1, 0.3, 1);
    }

    @keyframes windowAppear {
      from {
        opacity: 0;
        transform: scale(0.97) translateY(8px);
      }
      to {
        opacity: 1;
        transform: scale(1) translateY(0);
      }
    }

    .window-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      padding: 0.875rem 1.25rem;
      background: #ffffff;
      border-bottom: 1px solid #e2e8f0;
      flex-shrink: 0;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      min-width: 0;
      flex: 1;
    }

    .header-icon-box {
      width: 2.375rem;
      height: 2.375rem;
      border-radius: 0.5rem;
      background: #fff7ed;
      color: #ea580c;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .header-titles {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
      min-width: 0;
      flex: 1;
    }

    .title-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .window-title {
      font-size: 1.0625rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .shared-scope-pill {
      font-size: 0.6875rem;
      font-weight: 600;
      color: #c2410c;
      background: #ffedd5;
      padding: 0.125rem 0.5rem;
      border-radius: 9999px;
      white-space: nowrap;
    }

    /* Scoped Breadcrumbs */
    .scoped-breadcrumbs {
      display: flex;
      align-items: center;
    }

    .breadcrumb-list {
      display: flex;
      align-items: center;
      flex-wrap: wrap;
      gap: 0.25rem;
      margin: 0;
      padding: 0;
      list-style: none;
      font-size: 0.8125rem;
    }

    .breadcrumb-item {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      color: #64748b;
    }

    .crumb-link {
      background: none;
      border: none;
      padding: 0.125rem 0.25rem;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #ea580c;
      cursor: pointer;
      border-radius: 0.25rem;
      transition: background 0.15s ease;
    }

    .crumb-link:hover {
      background: #fff7ed;
      text-decoration: underline;
    }

    .crumb-separator {
      color: #cbd5e1;
    }

    .crumb-current {
      font-weight: 600;
      color: #1e293b;
      padding: 0.125rem 0.25rem;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-shrink: 0;
    }

    .icon-action-btn, .close-btn {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      padding: 0.4375rem;
      border-radius: 0.375rem;
      color: #64748b;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      transition: all 0.15s ease;
    }

    .icon-action-btn:hover, .close-btn:hover {
      background: #f1f5f9;
      color: #0f172a;
      border-color: #cbd5e1;
    }

    .close-btn:hover {
      background: #fef2f2;
      color: #ef4444;
      border-color: #fecaca;
    }

    .spin {
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    .window-toolbar-wrap {
      padding: 0.75rem 1.25rem;
      background: #ffffff;
      border-bottom: 1px solid #e2e8f0;
      flex-shrink: 0;
    }

    .window-content-body {
      flex: 1;
      padding: 1.25rem;
      overflow-y: auto;
      min-height: 0;
      background: #f8fafc;
    }

    .window-loading-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 320px;
      gap: 1rem;
      color: #64748b;
    }

    .spinner-lg {
      width: 2.25rem;
      height: 2.25rem;
      border: 3px solid #e2e8f0;
      border-top-color: #ea580c;
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }

    .window-error-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 320px;
      gap: 0.75rem;
      color: #ef4444;
      text-align: center;
      padding: 2rem;
    }

    .btn-retry {
      padding: 0.4375rem 1rem;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #ffffff;
      background: #ea580c;
      border: none;
      border-radius: 0.375rem;
      cursor: pointer;
    }

    .btn-retry:hover {
      background: #c2410c;
    }

    .window-empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 340px;
      gap: 0.5rem;
      text-align: center;
      color: #64748b;
    }

    .empty-icon-wrap {
      width: 3.5rem;
      height: 3.5rem;
      border-radius: 50%;
      background: #f1f5f9;
      color: #94a3b8;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 0.5rem;
    }

    .empty-title {
      font-size: 1.0625rem;
      font-weight: 600;
      color: #1e293b;
      margin: 0;
    }

    .empty-subtitle {
      font-size: 0.875rem;
      color: #64748b;
      max-width: 360px;
      margin: 0;
    }

    .btn-clear-filter {
      margin-top: 0.75rem;
      padding: 0.375rem 0.875rem;
      font-size: 0.8125rem;
      font-weight: 500;
      color: #ea580c;
      background: #fff7ed;
      border: 1px solid #ffedd5;
      border-radius: 0.375rem;
      cursor: pointer;
    }

    .btn-clear-filter:hover {
      background: #ffedd5;
    }

    .window-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 1.25rem;
      background: #ffffff;
      border-top: 1px solid #e2e8f0;
      font-size: 0.8125rem;
      color: #64748b;
      flex-shrink: 0;
    }

    .btn-close-footer {
      padding: 0.4375rem 1rem;
      font-size: 0.8125rem;
      font-weight: 600;
      color: #475569;
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 0.375rem;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-close-footer:hover {
      background: #f1f5f9;
      color: #0f172a;
    }
  `],
})
export class SharedFolderWindowComponent {
  private readonly folderApiService = inject(FolderApiService);
  private readonly documentApiService = inject(DocumentApiService);
  private readonly notificationService = inject(NotificationService);

  isOpen = input<boolean>(false);
  rootFolder = input<SharedFolderRoot | null>(null);

  closed = output<void>();

  currentFolderId = signal<number>(0);
  breadcrumbs = signal<FolderBreadcrumbDto[]>([]);
  subfolders = signal<FolderResponseDto[]>([]);
  documents = signal<DocumentGridItem[]>([]);
  isLoading = signal<boolean>(false);
  error = signal<string | null>(null);
  hasLoadedContent = signal<boolean>(false);

  viewMode = signal<ExplorerViewMode>('grid');
  searchQuery = signal<string>('');
  dateFilter = signal<string>('all');
  typeFilter = signal<string>('all');
  sizeFilter = signal<string>('all');
  sortOption = signal<string>('name-asc');
  selectedItems = signal<ExplorerItemSelection[]>([]);

  // Nested modals inside window context
  isPreviewOpen = signal<boolean>(false);
  previewItem = signal<any>(null);

  isDetailOpen = signal<boolean>(false);
  activeDetailItem = signal<{ type: 'folder' | 'document'; data: any; tab?: 'overview' | 'access' } | null>(null);

  isRequestModalOpen = signal<boolean>(false);
  requestResource = signal<{ type: 'folder' | 'document'; id: number; name: string } | null>(null);

  /**
   * Scoped breadcrumb bar:
   * Clamped strictly to start from the shared root folder.
   * Parents above the shared folder are filtered out so there is zero hierarchy leakage.
   */
  scopedBreadcrumbs = computed<FolderBreadcrumbDto[]>(() => {
    const root = this.rootFolder();
    if (!root) return [];
    const all = this.breadcrumbs();
    const rootIndex = all.findIndex((b) => b.id === root.id);
    if (rootIndex !== -1) {
      return all.slice(rootIndex);
    }
    // Fallback: start with the shared root folder
    return [{ id: root.id, name: root.name }];
  });

  /**
   * Filtered subfolders according to toolbar search, type filter, and sorting.
   */
  filteredSubfolders = computed<FolderResponseDto[]>(() => {
    const type = this.typeFilter();
    if (type !== 'all' && type !== 'folders') {
      return [];
    }

    let list = [...this.subfolders()];
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      list = list.filter((f) => (f.name || '').toLowerCase().includes(q));
    }

    const sort = this.sortOption();
    if (sort === 'name-asc' || sort === 'name') {
      list.sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    } else if (sort === 'name-desc') {
      list.sort((a, b) => (b.name || '').localeCompare(a.name || ''));
    } else if (sort === 'date-desc') {
      list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    } else if (sort === 'date-asc') {
      list.sort((a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime());
    }

    return list;
  });

  /**
   * Filtered documents according to toolbar search, type filter, date filter, size filter, and sorting.
   */
  filteredDocuments = computed<DocumentGridItem[]>(() => {
    const type = this.typeFilter();
    if (type === 'folders') {
      return [];
    }

    let list = [...this.documents()];
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      list = list.filter(
        (d) =>
          (d.name || '').toLowerCase().includes(q) ||
          (d.fileName || '').toLowerCase().includes(q)
      );
    }

    // Type filter
    if (type !== 'all') {
      list = list.filter((d) => {
        const ext = (d.fileName || d.name || '').split('.').pop()?.toLowerCase() || '';
        const mime = (d.contentType || '').toLowerCase();
        if (type === 'pdf') return ext === 'pdf' || mime.includes('pdf');
        if (type === 'image') return ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg'].includes(ext) || mime.startsWith('image/');
        if (type === 'doc') return ['doc', 'docx', 'txt', 'rtf', 'odt'].includes(ext) || mime.includes('word') || mime.includes('text');
        if (type === 'spreadsheet') return ['xls', 'xlsx', 'csv'].includes(ext) || mime.includes('sheet') || mime.includes('csv');
        return true;
      });
    }

    // Sorting
    const sort = this.sortOption();
    if (sort === 'name-asc' || sort === 'name') {
      list.sort((a, b) => (a.name || a.fileName || '').localeCompare(b.name || b.fileName || ''));
    } else if (sort === 'name-desc') {
      list.sort((a, b) => (b.name || b.fileName || '').localeCompare(a.name || a.fileName || ''));
    } else if (sort === 'size-desc') {
      list.sort((a, b) => (b.fileSizeBytes || b.fileSize || 0) - (a.fileSizeBytes || a.fileSize || 0));
    } else if (sort === 'size-asc') {
      list.sort((a, b) => (a.fileSizeBytes || a.fileSize || 0) - (b.fileSizeBytes || b.fileSize || 0));
    } else if (sort === 'date-desc') {
      list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    } else if (sort === 'date-asc') {
      list.sort((a, b) => new Date(a.createdAt || 0).getTime() - new Date(b.createdAt || 0).getTime());
    }

    return list;
  });

  totalFilteredCount = computed<number>(() => {
    return this.filteredSubfolders().length + this.filteredDocuments().length;
  });

  constructor() {
    effect(
      () => {
        const open = this.isOpen();
        const root = this.rootFolder();
        if (open && root) {
          this.currentFolderId.set(root.id);
          this.loadFolderContents(root.id);
        } else {
          this.subfolders.set([]);
          this.documents.set([]);
          this.breadcrumbs.set([]);
          this.selectedItems.set([]);
          this.hasLoadedContent.set(false);
          this.error.set(null);
          this.searchQuery.set('');
        }
      },
      { allowSignalWrites: true }
    );
  }

  @HostListener('window:keydown.escape')
  onEscape(): void {
    if (this.isPreviewOpen()) {
      this.isPreviewOpen.set(false);
      return;
    }
    if (this.isDetailOpen()) {
      this.isDetailOpen.set(false);
      return;
    }
    if (this.isRequestModalOpen()) {
      this.isRequestModalOpen.set(false);
      return;
    }
    if (this.isOpen()) {
      this.closed.emit();
    }
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('window-overlay-scrim')) {
      this.closed.emit();
    }
  }

  loadFolderContents(folderId: number): void {
    if (!folderId) return;
    this.isLoading.set(true);
    this.error.set(null);

    this.folderApiService.getFolderContents(folderId).subscribe({
      next: (res: FolderContentResponseDto) => {
        this.currentFolderId.set(folderId);
        this.breadcrumbs.set(res.breadcrumbs || []);
        this.subfolders.set(res.subFolders || []);

        const mappedDocs: DocumentGridItem[] = (res.documents || []).map((d) => ({
          ...d,
          fileName: d.fileName || d.name,
          fileSize: d.fileSizeBytes || (d as any).fileSize || 0,
        }));
        this.documents.set(mappedDocs);

        this.isLoading.set(false);
        this.hasLoadedContent.set(true);
        this.selectedItems.set([]);
      },
      error: (err) => {
        console.error('[SharedFolderWindow] Failed to load folder contents:', err);
        this.isLoading.set(false);
        this.error.set(
          err?.userFriendlyMessage || err?.error?.message || 'Failed to load folder contents. Please try again.'
        );
      },
    });
  }

  onNavigateFolder(folderId: number): void {
    this.loadFolderContents(folderId);
  }

  onDocumentClick(doc: DocumentGridItem): void {
    const docId = Number(doc.id);
    if (doc.isLocked) {
      this.onOpenOverview({ type: 'document', id: docId });
      return;
    }
    this.onPreviewDocument(docId);
  }

  onPreviewDocument(docOrId: DocumentGridItem | number): void {
    const docId = typeof docOrId === 'number' ? docOrId : Number(docOrId.id);
    const doc = typeof docOrId === 'object' ? docOrId : this.documents().find((d) => Number(d.id) === docId);
    if (!doc) return;

    if (doc.isLocked) {
      this.onRequestAccess({ type: 'document', id: docId, name: doc.name || doc.fileName });
      return;
    }

    this.previewItem.set({
      type: 'document',
      data: doc,
    });
    this.isPreviewOpen.set(true);
  }

  onOpenOverview(target: { type: 'folder' | 'document'; id: number }): void {
    const itemData = this.resolveItemData(target.type, target.id);
    if (!itemData) return;

    this.activeDetailItem.set({
      type: target.type,
      data: itemData,
      tab: 'overview',
    });
    this.isDetailOpen.set(true);
  }

  onOpenPermissions(target: { type: 'folder' | 'document'; id: number }): void {
    const itemData = this.resolveItemData(target.type, target.id);
    if (!itemData) return;

    this.activeDetailItem.set({
      type: target.type,
      data: itemData,
      tab: 'access',
    });
    this.isDetailOpen.set(true);
  }

  onOpenPreviewFromDetail(item: { type: 'folder' | 'document'; data: any }): void {
    this.isDetailOpen.set(false);
    if (item.type === 'document' && !item.data?.isLocked) {
      this.previewItem.set(item);
      this.isPreviewOpen.set(true);
    }
  }

  onOpenDetailsFromPreview(item: { type: 'folder' | 'document'; data: any }): void {
    this.isPreviewOpen.set(false);
    this.activeDetailItem.set({
      type: item.type,
      data: item.data,
      tab: 'overview',
    });
    this.isDetailOpen.set(true);
  }

  onRequestAccess(resource: { type: 'folder' | 'document'; id: number; name?: string }): void {
    const itemData = this.resolveItemData(resource.type, resource.id);
    const name = resource.name || itemData?.name || itemData?.fileName || 'Resource';

    this.requestResource.set({
      type: resource.type,
      id: resource.id,
      name,
    });
    this.isRequestModalOpen.set(true);
  }

  onRequestSubmitted(response: AccessRequestResponseDto): void {
    const targetName = response.documentName || response.folderName || 'the requested item';
    this.notificationService.success(
      `Access request submitted successfully for "${targetName}". The administrator will review your request.`
    );
    this.loadFolderContents(this.currentFolderId());
  }

  onDetailRequestCancelled(): void {
    const currentFolder = this.currentFolderId();
    if (currentFolder) {
      this.loadFolderContents(currentFolder);
    }
  }

  onDownloadDocument(docData: any): void {
    const docId = Number(docData?.id);
    if (!docId) return;

    this.documentApiService.getDownloadUrl(docId).subscribe({
      next: (res) => {
        if (res?.downloadUrl) {
          window.open(res.downloadUrl, '_blank');
        } else {
          this.notificationService.error('Download URL not found.');
        }
      },
      error: (err) => {
        const msg = err?.userFriendlyMessage || err?.error?.message || 'Download permission denied.';
        this.notificationService.error(msg);
      },
    });
  }

  onDownloadFolder(folderId: number): void {
    this.folderApiService.downloadFolder(folderId).subscribe({
      next: (blob: Blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `folder-${folderId}.zip`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.notificationService.error('Download permission denied for this folder.');
      },
    });
  }

  private resolveItemData(type: 'folder' | 'document', id: number): any {
    if (type === 'folder') {
      return this.subfolders().find((f) => f.id === id);
    }
    return this.documents().find((d) => d.id === id);
  }
}

/**
 * Backward compatibility alias:
 */
export { SharedFolderWindowComponent as SharedFolderDrawerComponent };
