import { Injectable, signal, computed, inject } from '@angular/core';
import { Observable, tap, catchError, of, forkJoin } from 'rxjs';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { FolderContentResponseDto, FolderResponseDto } from '../../../core/models/folder.model';
import { DocumentResponse } from '../../../core/models/document.model';

export type ExplorerViewMode = 'grid' | 'list';

export interface ExplorerItemSelection {
  type: 'folder' | 'document';
  id: number | string;
}

@Injectable({ providedIn: 'root' })
export class ExplorerStore {
  private readonly folderApiService = inject(FolderApiService);
  private readonly documentApiService = inject(DocumentApiService);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);

  // State Signals
  private readonly _currentFolderId = signal<number | null>(null);
  private readonly _contents = signal<FolderContentResponseDto | null>(null);
  private readonly _viewMode = signal<ExplorerViewMode>('list');
  private readonly _searchQuery = signal<string>('');
  private readonly _dateFilter = signal<string>('all');
  private readonly _typeFilter = signal<string>('all');
  private readonly _sizeFilter = signal<string>('all');
  private readonly _sortOption = signal<string>('name');
  private readonly _selectedItems = signal<ExplorerItemSelection[]>([]);
  private readonly _activeDetailItem = signal<{ type: 'folder' | 'document'; data: any; tab?: 'overview' | 'access' } | null>(null);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _error = signal<string | null>(null);

  // Readonly Signals
  readonly currentFolderId = this._currentFolderId.asReadonly();
  readonly contents = this._contents.asReadonly();
  readonly viewMode = this._viewMode.asReadonly();
  readonly searchQuery = this._searchQuery.asReadonly();
  readonly dateFilter = this._dateFilter.asReadonly();
  readonly typeFilter = this._typeFilter.asReadonly();
  readonly sizeFilter = this._sizeFilter.asReadonly();
  readonly sortOption = this._sortOption.asReadonly();
  readonly selectedItems = this._selectedItems.asReadonly();
  readonly activeDetailItem = this._activeDetailItem.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly error = this._error.asReadonly();

  // Computed Signals
  readonly currentFolder = computed(() => this._contents()?.currentFolder || null);
  readonly breadcrumbs = computed(() => this._contents()?.breadcrumbs || []);
  
  readonly filteredSubfolders = computed(() => {
    const type = this._typeFilter();
    if (type === 'documents' || type === 'pdf' || type === 'images') {
      return [];
    }

    const query = this._searchQuery().trim().toLowerCase();
    let subFolders = [...(this._contents()?.subFolders || [])];

    if (query) {
      subFolders = subFolders.filter((f) => f.name.toLowerCase().includes(query));
    }

    const date = this._dateFilter();
    if (date !== 'all') {
      const now = new Date().getTime();
      subFolders = subFolders.filter((f) => {
        if (!f.createdAt) return true;
        const created = new Date(f.createdAt).getTime();
        const diffDays = (now - created) / (1000 * 3600 * 24);
        if (date === 'today') return diffDays <= 1;
        if (date === '7days') return diffDays <= 7;
        if (date === '30days') return diffDays <= 30;
        return true;
      });
    }

    const sort = this._sortOption();
    subFolders.sort((a, b) => {
      if (sort === 'date') {
        return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
      }
      return a.name.localeCompare(b.name);
    });

    return subFolders;
  });

  readonly filteredDocuments = computed(() => {
    const type = this._typeFilter();
    if (type === 'folders') {
      return [];
    }

    const query = this._searchQuery().trim().toLowerCase();
    let docs = [...(this._contents()?.documents || [])];

    if (query) {
      docs = docs.filter((d) => (d.fileName || d.name || '').toLowerCase().includes(query));
    }

    if (type === 'pdf') {
      docs = docs.filter((d) => {
        const mime = (d.contentType || d.mimeType || '').toLowerCase();
        const name = (d.fileName || d.name || '').toLowerCase();
        return mime.includes('pdf') || name.endsWith('.pdf');
      });
    } else if (type === 'images') {
      docs = docs.filter((d) => {
        const mime = (d.contentType || d.mimeType || '').toLowerCase();
        const name = (d.fileName || d.name || '').toLowerCase();
        return mime.includes('image') || /\.(png|jpg|jpeg|svg|webp|gif)$/.test(name);
      });
    }

    const date = this._dateFilter();
    if (date !== 'all') {
      const now = new Date().getTime();
      docs = docs.filter((d) => {
        const dateStr = d.uploadedAt || d.createdAt;
        if (!dateStr) return true;
        const time = new Date(dateStr).getTime();
        const diffDays = (now - time) / (1000 * 3600 * 24);
        if (date === 'today') return diffDays <= 1;
        if (date === '7days') return diffDays <= 7;
        if (date === '30days') return diffDays <= 30;
        return true;
      });
    }

    const size = this._sizeFilter();
    if (size !== 'all') {
      docs = docs.filter((d) => {
        const bytes = d.fileSize || d.fileSizeBytes || 0;
        const mb = bytes / (1024 * 1024);
        if (size === 'small') return mb < 1;
        if (size === 'medium') return mb >= 1 && mb <= 10;
        if (size === 'large') return mb > 10;
        return true;
      });
    }

    const sort = this._sortOption();
    docs.sort((a, b) => {
      if (sort === 'date') {
        const dateA = new Date(a.uploadedAt || a.createdAt || 0).getTime();
        const dateB = new Date(b.uploadedAt || b.createdAt || 0).getTime();
        return dateB - dateA;
      }
      if (sort === 'size') {
        const sizeA = a.fileSize || a.fileSizeBytes || 0;
        const sizeB = b.fileSize || b.fileSizeBytes || 0;
        return sizeB - sizeA;
      }
      const nameA = a.fileName || a.name || '';
      const nameB = b.fileName || b.name || '';
      return nameA.localeCompare(nameB);
    });

    return docs;
  });

  readonly totalItemCount = computed(() => {
    return (this.filteredSubfolders().length || 0) + (this.filteredDocuments().length || 0);
  });

  readonly rawTotalCount = computed(() => {
    const contents = this._contents();
    if (!contents) return 0;
    return (contents.subFolders?.length || 0) + (contents.documents?.length || 0);
  });

  readonly isFolderEmpty = computed(() => {
    return !this._isLoading() && !!this._contents() && this.rawTotalCount() === 0;
  });

  /**
   * Resets the store state.
   */
  reset(): void {
    this._currentFolderId.set(null);
    this._contents.set(null);
    this._searchQuery.set('');
    this._dateFilter.set('all');
    this._typeFilter.set('all');
    this._sizeFilter.set('all');
    this._sortOption.set('name');
    this._selectedItems.set([]);
    this._activeDetailItem.set(null);
    this._isLoading.set(false);
    this._error.set(null);
  }

  /**
   * Loads folder contents by folder ID.
   */
  loadFolderContents(folderId: number): Observable<FolderContentResponseDto | null> {
    this._isLoading.set(true);
    this._error.set(null);
    this._currentFolderId.set(folderId);

    return this.folderApiService.getFolderContents(folderId).pipe(
      tap((res) => {
        this._contents.set(res);
        this._isLoading.set(false);
      }),
      catchError((err) => {
        console.error(`Failed to load contents for folder ${folderId}:`, err);
        this._error.set('Failed to load folder contents.');
        this._isLoading.set(false);
        return of(null);
      })
    );
  }

  /**
   * Reloads current folder contents.
   */
  refresh(): void {
    const folderId = this._currentFolderId();
    if (folderId != null) {
      this.loadFolderContents(folderId).subscribe();
    }
  }

  /**
   * Updates view mode ('grid' | 'list').
   */
  setViewMode(mode: ExplorerViewMode): void {
    this._viewMode.set(mode);
  }

  /**
   * Updates inline search query filter.
   */
  setSearchQuery(query: string): void {
    this._searchQuery.set(query);
  }

  setDateFilter(date: string): void {
    this._dateFilter.set(date);
  }

  setTypeFilter(type: string): void {
    this._typeFilter.set(type);
  }

  setSizeFilter(size: string): void {
    this._sizeFilter.set(size);
  }

  setSortOption(sort: string): void {
    this._sortOption.set(sort);
  }

  /**
   * Sets detail drawer active item.
   */
  openDetailDrawer(type: 'folder' | 'document', data: any, tab?: 'overview' | 'access'): void {
    this._activeDetailItem.set({ type, data, tab });
  }

  /**
   * Closes detail drawer.
   */
  closeDetailDrawer(): void {
    this._activeDetailItem.set(null);
  }

  /**
   * Creates a subfolder inside the currently active folder.
   */
  createSubfolder(name: string): Observable<FolderResponseDto> {
    const activeWs = this.workspaceContextStore.activeWorkspace();
    const currentFolderId = this._currentFolderId();

    const request = {
      name,
      parentFolderId: currentFolderId,
    };

    const orgCode = activeWs?.organizationCode || activeWs?.orgCode;
    const create$ = orgCode
      ? this.folderApiService.createFolder(orgCode, request)
      : this.folderApiService.createPersonalFolder(request);

    return create$.pipe(
      tap(() => this.refresh())
    );
  }

  /**
   * Deletes a subfolder.
   */
  deleteFolder(folderId: number): Observable<void> {
    return this.folderApiService.deleteFolder(folderId).pipe(
      tap(() => {
        if (this._activeDetailItem()?.data?.id === folderId) {
          this.closeDetailDrawer();
        }
        this.refresh();
      })
    );
  }

  /**
   * Deletes a document.
   */
  deleteDocument(documentId: number): Observable<void> {
    return this.documentApiService.deleteDocument(documentId).pipe(
      tap(() => {
        if (this._activeDetailItem()?.data?.id === documentId) {
          this.closeDetailDrawer();
        }
        this.refresh();
      })
    );
  }

  /**
   * Renames a subfolder.
   */
  renameFolder(folderId: number, newName: string): Observable<FolderResponseDto> {
    return this.folderApiService.renameFolder(folderId, newName).pipe(
      tap((updatedFolder) => {
        if (this._activeDetailItem()?.type === 'folder' && Number(this._activeDetailItem()?.data?.id) === folderId) {
          this._activeDetailItem.set({ type: 'folder', data: updatedFolder });
        }
        this.refresh();
      })
    );
  }

  /**
   * Renames a document.
   */
  /**
   * Renames a document.
   */
  renameDocument(documentId: number, newName: string): Observable<DocumentResponse> {
    return this.documentApiService.updateMetadata(documentId, newName).pipe(
      tap((updatedDoc) => {
        if (this._activeDetailItem()?.type === 'document' && Number(this._activeDetailItem()?.data?.id) === documentId) {
          this._activeDetailItem.set({ type: 'document', data: updatedDoc });
        }
        this.refresh();
      })
    );
  }

  // Selection actions
  setSelectedItems(items: ExplorerItemSelection[]): void {
    this._selectedItems.set(items);
  }

  clearSelection(): void {
    this._selectedItems.set([]);
  }

  toggleSelection(type: 'folder' | 'document', id: number | string, isMulti = false): void {
    const numId = Number(id);
    const current = this._selectedItems();
    const exists = current.some(item => item.type === type && Number(item.id) === numId);

    if (!isMulti) {
      this._selectedItems.set([{ type, id: numId }]);
      return;
    }

    if (exists) {
      this._selectedItems.set(current.filter(item => !(item.type === type && Number(item.id) === numId)));
    } else {
      this._selectedItems.set([...current, { type, id: numId }]);
    }
  }

  isSelected(type: 'folder' | 'document', id: number | string): boolean {
    const numId = Number(id);
    return this._selectedItems().some(item => item.type === type && Number(item.id) === numId);
  }

  /**
   * Performs bulk move of selected folders and documents to target folder ID.
   */
  bulkMoveItems(items: ExplorerItemSelection[], targetFolderId: number | null): Observable<any> {
    const folderIds = items.filter(i => i.type === 'folder').map(i => Number(i.id));
    const docIds = items.filter(i => i.type === 'document').map(i => Number(i.id));

    const tasks: Observable<any>[] = [];
    if (folderIds.length > 0) {
      tasks.push(this.folderApiService.bulkMoveFolders(folderIds, targetFolderId));
    }
    if (docIds.length > 0) {
      tasks.push(this.documentApiService.bulkMoveDocuments(docIds, targetFolderId));
    }

    if (tasks.length === 0) return of(null);

    return forkJoin(tasks).pipe(
      tap(() => {
        this.clearSelection();
        this.refresh();
      })
    );
  }
}
