import {
  Component, ChangeDetectionStrategy, input, output, signal, ElementRef, ViewChild, HostListener
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FolderResponseDto } from '../../../core/models/folder.model';
import { ExplorerItemSelection } from '../state/explorer.store';
import { ExplorerContextMenuComponent, ContextMenuAction } from './explorer-context-menu.component';
import { LucideFolderSearch } from '@lucide/angular';

export interface DocumentGridItem {
  id: number | string;
  name?: string;
  fileName?: string;
  folderId?: number;
  fileSizeBytes?: number;
  fileSize?: number;
  mimeType?: string;
  contentType?: string;
  createdAt?: string;
  uploadedAt?: string;
  updatedAt?: string;
  fileHash?: string;
  uploadedByUserId?: string;
  uploadedByUserEmail?: string;
  createdByUserId?: string;
  status?: string;
  version?: number;
  spaceType?: 'PERSONAL' | 'ORGANIZATION';
  ownerOrgCode?: string | null;
  accessStatus?: 'ACCESSIBLE' | 'LOCKED' | string;
  isLocked?: boolean;
  requestStatus?: 'NONE' | 'PENDING' | string;
  canRequestAccess?: boolean;
  pendingRequestId?: number | null;
}

@Component({
  selector: 'app-explorer-grid-view',
  standalone: true,
  imports: [CommonModule, FormsModule, ExplorerContextMenuComponent, LucideFolderSearch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #gridContainer
      class="grid-container"
      (mousedown)="onContainerMouseDown($event)"
    >
      <!-- Marquee Selection Box -->
      @if (marqueeBox()) {
        <div
          class="marquee-selection-box"
          [style.left.px]="marqueeBox()!.x"
          [style.top.px]="marqueeBox()!.y"
          [style.width.px]="marqueeBox()!.width"
          [style.height.px]="marqueeBox()!.height"
        ></div>
      }

      <!-- Folders Section -->
      @if (subfolders().length > 0) {
        <div class="section-label">
          <span class="section-title">Folders</span>
          <span class="section-count">{{ subfolders().length }}</span>
        </div>
        <div class="grid-layout">
          @for (folder of subfolders(); track folder.id; let i = $index) {
            <div
              class="grid-card folder-card"
              [attr.data-type]="'folder'"
              [attr.data-id]="folder.id"
              [class.selected]="isItemSelected('folder', folder.id)"
              [class.drag-target]="dragOverFolderId() === folder.id"
              [class.renaming]="renamingId() === 'folder-' + folder.id"
              [class.locked-card]="folder.isLocked"
              [style.animation-delay.ms]="i * 30"
              [draggable]="!folder.isLocked"
              (dragstart)="onDragStart($event, 'folder', folder)"
              (dragover)="onFolderDragOver($event, folder.id)"
              (dragleave)="onFolderDragLeave($event, folder.id)"
              (drop)="onFolderDrop($event, folder)"
              (click)="onFolderClick(folder, $event)"
              (dblclick)="onFolderDblClick(folder, $event)"
              (contextmenu)="onContextMenu($event, 'folder', folder.id, folder.name, folder)"
            >
              <!-- Selection Checkmark Badge -->
              @if (isItemSelected('folder', folder.id)) {
                <div class="selection-badge">
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3.5 8.5 6.5 11.5 12.5 4.5"/>
                  </svg>
                </div>
              }

              <!-- Preview area -->
              <div class="card-preview" [class.preview-folder]="!folder.isLocked" [class.preview-locked]="folder.isLocked">
                @if (folder.isLocked) {
                  <svg class="preview-icon lock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <span class="preview-badge badge-locked">
                    <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                    <span>LOCKED</span>
                  </span>
                  <div class="preview-overlay">
                    <button class="overlay-btn" (click)="$event.stopPropagation(); openOverview.emit({type: 'folder', id: folder.id, data: folder})" title="Overview">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                    </button>
                    @if (folder.canRequestAccess && folder.requestStatus === 'NONE') {
                      <button class="overlay-btn btn-action-request" (click)="$event.stopPropagation(); requestAccess.emit({type: 'folder', id: folder.id, name: folder.name})" title="Request Access">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6"/><path d="m15.5 7.5 2.3 2.3"/></svg>
                      </button>
                    } @else if (folder.requestStatus === 'PENDING') {
                      <span class="preview-badge badge-pending" (click)="$event.stopPropagation(); openOverview.emit({type: 'folder', id: folder.id, data: folder})" style="cursor: pointer;" title="View pending request">
                        <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <circle cx="12" cy="12" r="10"/>
                          <polyline points="12 6 12 12 16 14"/>
                        </svg>
                        <span>Requested</span>
                      </span>
                    }
                  </div>
                } @else {
                  <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                  </svg>
                  <span class="preview-badge badge-folder">DIR</span>
                  <div class="preview-overlay">
                    <button class="overlay-btn" (click)="$event.stopPropagation(); openOverview.emit({type: 'folder', id: folder.id, data: folder})" title="Overview">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                    </button>
                    <button class="overlay-btn" (click)="$event.stopPropagation(); openPermissions.emit({type: 'folder', id: folder.id, data: folder})" title="Permissions">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/></svg>
                    </button>
                    <button class="overlay-btn" (click)="$event.stopPropagation(); folderSelected.emit(folder.id)" title="Open folder">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
                    </button>
                  </div>
                  <button class="more-btn" (click)="$event.stopPropagation(); onContextMenu($event, 'folder', folder.id, folder.name, folder)" title="More">
                    <svg viewBox="0 0 16 16" fill="currentColor"><circle cx="8" cy="3.5" r="1.2"/><circle cx="8" cy="8" r="1.2"/><circle cx="8" cy="12.5" r="1.2"/></svg>
                  </button>
                }
              </div>
              <!-- Info strip -->
              <div class="card-info">
                @if (renamingId() === 'folder-' + folder.id) {
                  <input
                    class="rename-input"
                    [value]="renameValue()"
                    (input)="renameValue.set($any($event.target).value)"
                    (keydown.enter)="confirmRename('folder', folder.id)"
                    (keydown.escape)="cancelRename()"
                    (blur)="confirmRename('folder', folder.id)"
                    (click)="$event.stopPropagation()"
                    autofocus
                  />
                } @else {
                  <span class="card-name" [title]="folder.name">{{ folder.name }}</span>
                }
                @if (folder.isLocked) {
                  @if (folder.requestStatus === 'PENDING') {
                    <span class="card-sub pending-sub">
                      <svg class="sub-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"/>
                        <polyline points="12 6 12 12 16 14"/>
                      </svg>
                      <span>Access Requested</span>
                    </span>
                  } @else if (folder.canRequestAccess) {
                    <button class="card-request-btn" (click)="$event.stopPropagation(); requestAccess.emit({type: 'folder', id: folder.id, name: folder.name})">
                      Request Access
                    </button>
                  } @else {
                    <span class="card-sub locked-sub">Access Restricted</span>
                  }
                } @else {
                  <span class="card-sub">{{ formatDate(folder.createdAt) }}</span>
                }
              </div>
            </div>
          }
        </div>
      }

      <!-- Documents Section -->
      @if (documents().length > 0) {
        <div class="section-label" [class.mt-section]="subfolders().length > 0">
          <span class="section-title">Documents</span>
          <span class="section-count">{{ documents().length }}</span>
        </div>
        <div class="grid-layout">
          @for (doc of documents(); track doc.id; let i = $index) {
            <div
              class="grid-card doc-card"
              [attr.data-type]="'document'"
              [attr.data-id]="doc.id"
              [class.selected]="isItemSelected('document', doc.id)"
              [class.renaming]="renamingId() === 'doc-' + doc.id"
              [class.locked-card]="doc.isLocked"
              [style.animation-delay.ms]="i * 30"
              [draggable]="!doc.isLocked"
              (dragstart)="onDragStart($event, 'document', doc)"
              (click)="onDocClick(doc, $event)"
              (dblclick)="onDocDblClick(doc, $event)"
              (contextmenu)="onContextMenu($event, 'document', +doc.id, getDocName(doc), doc)"
            >
              <!-- Selection Checkmark Badge -->
              @if (isItemSelected('document', doc.id)) {
                <div class="selection-badge">
                  <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <polyline points="3.5 8.5 6.5 11.5 12.5 4.5"/>
                  </svg>
                </div>
              }

              <!-- Preview area -->
              <div class="card-preview" [class.preview-locked]="doc.isLocked" [ngClass]="!doc.isLocked ? ('preview-' + getFileCategory(getDocMime(doc), getDocName(doc))) : ''">
                @if (doc.isLocked) {
                  <svg class="preview-icon lock-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                    <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                    <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                  </svg>
                  <span class="preview-badge badge-locked">
                    <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                    <span>LOCKED</span>
                  </span>
                  <div class="preview-overlay">
                    <button class="overlay-btn" (click)="$event.stopPropagation(); openOverview.emit({type: 'document', id: +doc.id, data: doc})" title="Overview">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                    </button>
                    @if (doc.canRequestAccess && doc.requestStatus === 'NONE') {
                      <button class="overlay-btn btn-action-request" (click)="$event.stopPropagation(); requestAccess.emit({type: 'document', id: +doc.id, name: getDocName(doc)})" title="Request Access">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6"/><path d="m15.5 7.5 2.3 2.3"/></svg>
                      </button>
                    } @else if (doc.requestStatus === 'PENDING') {
                      <span class="preview-badge badge-pending" (click)="$event.stopPropagation(); openOverview.emit({type: 'document', id: +doc.id, data: doc})" style="cursor: pointer;" title="View pending request">
                        <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <circle cx="12" cy="12" r="10"/>
                          <polyline points="12 6 12 12 16 14"/>
                        </svg>
                        <span>Requested</span>
                      </span>
                    }
                  </div>
                } @else {
                  @switch (getFileCategory(getDocMime(doc), getDocName(doc))) {
                    @case ('pdf') {
                      <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                        <polyline points="14 2 14 8 20 8"/>
                        <line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/>
                      </svg>
                    }
                    @case ('image') {
                      <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="18" height="18" rx="2"/>
                        <circle cx="8.5" cy="8.5" r="1.5"/>
                        <polyline points="21 15 16 10 5 21"/>
                      </svg>
                    }
                    @case ('spreadsheet') {
                      <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                        <polyline points="14 2 14 8 20 8"/>
                        <line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="16" y2="17"/>
                      </svg>
                    }
                    @case ('code') {
                      <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/>
                      </svg>
                    }
                    @default {
                      <svg class="preview-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.25" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/>
                      </svg>
                    }
                  }
                  <span class="preview-badge" [ngClass]="'badge-' + getFileCategory(getDocMime(doc), getDocName(doc))">
                    {{ getFileBadgeLabel(getDocMime(doc), getDocName(doc)) }}
                  </span>
                  <div class="preview-overlay">
                    <button class="overlay-btn" (click)="$event.stopPropagation(); openOverview.emit({type: 'document', id: +doc.id, data: doc})" title="Overview">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                    </button>
                    <button class="overlay-btn" (click)="$event.stopPropagation(); openPermissions.emit({type: 'document', id: +doc.id, data: doc})" title="Permissions">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/></svg>
                    </button>
                    <button class="overlay-btn" (click)="$event.stopPropagation(); previewDocument.emit(doc)" title="Preview Document">
                      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M1 8s2.5-5 7-5 7 5 7 5"/><path d="M1 8s2.5 5 7 5 7-5 7-5"/><circle cx="8" cy="8" r="2"/>
                      </svg>
                    </button>
                    <button class="overlay-btn" (click)="$event.stopPropagation(); downloadDocument.emit(doc)" title="Download">
                      <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M3 13h10"/><path d="M8 3v7"/><polyline points="5 7 8 10 11 7"/>
                      </svg>
                    </button>
                  </div>
                  <button class="more-btn" (click)="$event.stopPropagation(); onContextMenu($event, 'document', +doc.id, getDocName(doc), doc)" title="More">
                    <svg viewBox="0 0 16 16" fill="currentColor"><circle cx="8" cy="3.5" r="1.2"/><circle cx="8" cy="8" r="1.2"/><circle cx="8" cy="12.5" r="1.2"/></svg>
                  </button>
                }
              </div>
              <!-- Info strip -->
              <div class="card-info">
                @if (renamingId() === 'doc-' + doc.id) {
                  <input
                    class="rename-input"
                    [value]="renameValue()"
                    (input)="renameValue.set($any($event.target).value)"
                    (keydown.enter)="confirmRename('document', +doc.id)"
                    (keydown.escape)="cancelRename()"
                    (blur)="confirmRename('document', +doc.id)"
                    (click)="$event.stopPropagation()"
                    autofocus
                  />
                } @else {
                  <span class="card-name" [title]="getDocName(doc)">{{ getDocName(doc) }}</span>
                }
                @if (doc.isLocked) {
                  @if (doc.requestStatus === 'PENDING') {
                    <span class="card-sub pending-sub">
                      <svg class="sub-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"/>
                        <polyline points="12 6 12 12 16 14"/>
                      </svg>
                      <span>Access Requested</span>
                    </span>
                  } @else if (doc.canRequestAccess) {
                    <button class="card-request-btn" (click)="$event.stopPropagation(); requestAccess.emit({type: 'document', id: +doc.id, name: getDocName(doc)})">
                      Request Access
                    </button>
                  } @else {
                    <span class="card-sub locked-sub">Access Restricted</span>
                  }
                } @else {
                  <span class="card-sub">{{ formatBytes(getDocSize(doc)) }}</span>
                }
              </div>
            </div>
          }
        </div>
      }

      <!-- Empty State -->
      @if (subfolders().length === 0 && documents().length === 0) {
        <div class="empty-state">
          <div class="empty-icon-box">
            <svg lucideFolderSearch></svg>
          </div>
          <h3 class="empty-title">No folders or files found in this workspace space.</h3>
          <p class="empty-subtitle">Use the upload chips above to insert sample folders and documents!</p>
        </div>
      }
    </div>

    <app-explorer-context-menu #ctxMenu (actionDispatched)="onMenuAction($event)" />
  `,
  styles: [`
    .grid-container {
      padding: 0.25rem 0;
      position: relative;
      user-select: none;
      min-height: 200px;
    }

    /* Marquee Selection Box */
    .marquee-selection-box {
      position: absolute;
      background: rgba(255, 85, 0, 0.12);
      border: 1.5px solid #ff5500;
      border-radius: 0.375rem;
      pointer-events: none;
      z-index: 100;
    }

    /* Section Header */
    .section-label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.875rem;
      padding-bottom: 0.5rem;
      border-bottom: 1px solid #f1f5f9;
    }
    .mt-section { margin-top: 1.75rem; }
    .section-title {
      font-size: 0.6875rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.07em;
      color: #64748b;
    }
    .section-count {
      font-size: 0.625rem;
      font-weight: 700;
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #e2e8f0;
      padding: 0.0625rem 0.4rem;
      border-radius: 9999px;
    }

    /* Grid Layout */
    .grid-layout {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(155px, 1fr));
      gap: 0.875rem;
    }

    /* Card */
    .grid-card {
      background: #ffffff;
      border: 1px solid #e8edf3;
      border-radius: 0.875rem;
      overflow: hidden;
      cursor: pointer;
      display: flex;
      flex-direction: column;
      position: relative;
      transition: box-shadow 0.2s ease, border-color 0.2s ease, transform 0.18s ease, background-color 0.18s ease;
      animation: fadeInCard 0.2s ease-out both;
    }
    @keyframes fadeInCard {
      from { opacity: 0; transform: translateY(5px); }
      to   { opacity: 1; transform: translateY(0); }
    }
    .grid-card:hover {
      border-color: #d1dae5;
      box-shadow: 0 6px 18px rgba(0,0,0,0.08);
      transform: translateY(-2px);
    }
    .grid-card.selected {
      border-color: #ffb38a !important;
      background-color: #fff5ef !important;
      box-shadow: 0 0 0 2px rgba(255, 85, 0, 0.2), 0 4px 12px rgba(255, 85, 0, 0.1) !important;
      transform: translateY(-1px) !important;
    }
    .grid-card.selected .card-info {
      background: #fff5ef;
    }
    .grid-card.selected .card-name {
      color: #7c2200;
    }
    .grid-card.drag-target {
      border-color: #ff8c55 !important;
      background-color: #fff5ef !important;
      box-shadow: 0 0 0 2.5px #ff5500, 0 6px 20px rgba(255, 85, 0, 0.15) !important;
      outline: none;
    }
    .grid-card.renaming {
      border-color: #ffb38a;
      box-shadow: 0 0 0 3px rgba(255,85,0,0.1);
    }

    /* Selection Badge */
    .selection-badge {
      position: absolute;
      top: 0.5rem;
      left: 0.5rem;
      width: 1.25rem;
      height: 1.25rem;
      background: #ff5500;
      color: #ffffff;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 10;
      box-shadow: 0 2px 6px rgba(255,85,0,0.3);
    }
    .selection-badge svg {
      width: 0.75rem;
      height: 0.75rem;
    }

    /* Preview Area */
    .card-preview {
      position: relative;
      height: 130px;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
    }
    .preview-folder     { background: linear-gradient(145deg, #fff7ed, #ffedd5); }
    .preview-pdf        { background: linear-gradient(145deg, #fef2f2, #fee2e2); }
    .preview-image      { background: linear-gradient(145deg, #faf5ff, #ede9fe); }
    .preview-spreadsheet{ background: linear-gradient(145deg, #f0fdf4, #dcfce7); }
    .preview-code       { background: linear-gradient(145deg, #ecfdf5, #d1fae5); }
    .preview-default    { background: linear-gradient(145deg, #f0f9ff, #e0f2fe); }

    .preview-icon {
      width: 3.25rem;
      height: 3.25rem;
      opacity: 0.8;
    }
    .preview-folder .preview-icon { color: #ff5500; }
    .preview-pdf .preview-icon    { color: #dc2626; }
    .preview-image .preview-icon  { color: #7c3aed; }
    .preview-spreadsheet .preview-icon { color: #16a34a; }
    .preview-code .preview-icon   { color: #059669; }
    .preview-default .preview-icon{ color: #0284c7; }

    /* Type badge overlaid on preview */
    .preview-badge {
      position: absolute;
      bottom: 0.5rem;
      left: 0.5rem;
      display: inline-flex;
      align-items: center;
      gap: 3px;
      font-size: 0.55rem;
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 0.125rem 0.375rem;
      border-radius: 0.25rem;
    }
    .badge-svg-icon {
      width: 10px;
      height: 10px;
      flex-shrink: 0;
    }
    .sub-svg-icon {
      width: 12px;
      height: 12px;
      flex-shrink: 0;
    }
    .badge-folder     { background: rgba(255,85,0,0.12); color: #c2410c; }
    .badge-pdf        { background: #fecaca; color: #991b1b; }
    .badge-image      { background: #ddd6fe; color: #5b21b6; }
    .badge-spreadsheet{ background: #bbf7d0; color: #14532d; }
    .badge-code       { background: #a7f3d0; color: #064e3b; }
    .badge-default    { background: #bae6fd; color: #075985; }

    /* Hover Overlay */
    .preview-overlay {
      position: absolute;
      inset: 0;
      background: rgba(15,23,42,0.5);
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      opacity: 0;
      transition: opacity 0.18s ease;
    }
    .grid-card:hover .preview-overlay { opacity: 1; }

    .overlay-btn {
      width: 2rem; height: 2rem;
      border-radius: 50%;
      background: rgba(255,255,255,0.92);
      border: none;
      color: #1e293b;
      cursor: pointer;
      display: flex; align-items: center; justify-content: center;
      transition: background 0.12s, transform 0.12s;
    }
    .overlay-btn:hover { background: #ffffff; transform: scale(1.1); }
    .overlay-btn svg { width: 0.875rem; height: 0.875rem; }

    /* More Button */
    .more-btn {
      position: absolute;
      top: 0.375rem; right: 0.375rem;
      background: rgba(255,255,255,0.85);
      border: none;
      color: #64748b;
      cursor: pointer;
      padding: 0.25rem;
      border-radius: 0.375rem;
      opacity: 0;
      transition: opacity 0.15s, color 0.15s, background 0.15s;
      display: flex; align-items: center;
    }
    .grid-card:hover .more-btn { opacity: 1; }
    .more-btn:hover { background: #ffffff; color: #0f172a; }
    .more-btn svg { width: 0.9375rem; height: 0.9375rem; display: block; }

    /* Info Strip */
    .card-info {
      padding: 0.625rem 0.75rem 0.75rem;
      border-top: 1px solid #f1f5f9;
      display: flex;
      flex-direction: column;
      gap: 0.1875rem;
    }
    .card-name {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      line-height: 1.3;
    }
    .card-sub { font-size: 0.6875rem; color: #94a3b8; }
    .card-sub.pending-sub { display: inline-flex; align-items: center; gap: 4px; color: #d97706; font-weight: 600; }
    .card-sub.locked-sub { color: #94a3b8; font-style: italic; }

    /* Locked Card Specific Styles */
    .locked-card {
      border-color: #f1f5f9;
      opacity: 0.88;
    }
    .preview-locked {
      background: linear-gradient(145deg, #f8fafc, #f1f5f9) !important;
    }
    .lock-icon {
      color: #f59e0b !important;
    }
    .badge-locked {
      background: #fef3c7 !important;
      color: #92400e !important;
    }
    .card-request-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: fit-content;
      margin-top: 0.125rem;
      padding: 0.1875rem 0.5rem;
      font-size: 0.6875rem;
      font-weight: 600;
      border-radius: 0.25rem;
      background: #fff7ed;
      color: #ea580c;
      border: 1px solid #fdba74;
      cursor: pointer;
      transition: background 0.15s, color 0.15s;
    }
    .card-request-btn:hover {
      background: #ea580c;
      color: #ffffff;
      border-color: #ea580c;
    }
    .btn-action-request {
      background: #fff7ed !important;
      color: #ea580c !important;
      border: 1px solid #ea580c !important;
    }
    .btn-action-request:hover {
      background: #ea580c !important;
      color: #ffffff !important;
    }

    /* Inline Rename */
    .rename-input {
      width: 100%;
      font-size: 0.8125rem; font-weight: 600;
      border: none; border-bottom: 2px solid #ff5500;
      border-radius: 0; outline: none;
      background: transparent; color: #0f172a;
      padding: 0 0 0.125rem;
    }

    /* Empty State */
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
  `],
})
export class ExplorerGridViewComponent {
  subfolders = input<FolderResponseDto[]>([]);
  documents  = input<DocumentGridItem[]>([]);
  selectedItems = input<ExplorerItemSelection[]>([]);

  folderSelected   = output<number>();
  documentSelected = output<DocumentGridItem>();
  previewDocument  = output<DocumentGridItem>();
  previewFolder    = output<number>();
  downloadDocument = output<DocumentGridItem>();
  downloadFolder   = output<number>();
  deleteFolder     = output<number>();
  deleteDocument   = output<number>();
  renameItem       = output<{ type: 'folder' | 'document'; id: number; newName: string }>();
  openOverview     = output<{ type: 'folder' | 'document'; id: number; data: any }>();
  openPermissions  = output<{ type: 'folder' | 'document'; id: number; data: any }>();
  requestAccess    = output<{ type: 'folder' | 'document'; id: number; name: string }>();

  setSelectedItems = output<ExplorerItemSelection[]>();
  initiateMove     = output<{ items: ExplorerItemSelection[]; targetFolderId: number; targetFolderName: string }>();

  @ViewChild('ctxMenu') ctxMenu!: ExplorerContextMenuComponent;
  @ViewChild('gridContainer') gridContainerRef!: ElementRef<HTMLDivElement>;

  renamingId  = signal<string | null>(null);
  renameValue = signal<string>('');
  dragOverFolderId = signal<number | null>(null);

  // Marquee selection state
  marqueeBox = signal<{ x: number; y: number; width: number; height: number } | null>(null);
  private isMouseDownOnContainer = false;
  private startX = 0;
  private startY = 0;
  private lastClickIndex = -1;

  isItemSelected(type: 'folder' | 'document', id: number | string): boolean {
    const numId = Number(id);
    return this.selectedItems().some(item => item.type === type && Number(item.id) === numId);
  }

  onFolderClick(folder: FolderResponseDto, e: MouseEvent): void {
    if (this.renamingId()) return;
    this.handleItemSelection('folder', folder.id, e);
  }

  onFolderDblClick(folder: FolderResponseDto, e: MouseEvent): void {
    e.stopPropagation();
    if (this.renamingId()) return;
    if (folder.isLocked) {
      this.openOverview.emit({ type: 'folder', id: folder.id, data: folder });
      return;
    }
    this.folderSelected.emit(folder.id);
  }

  onDocDblClick(doc: DocumentGridItem, e: MouseEvent): void {
    e.stopPropagation();
    if (this.renamingId()) return;
    if (doc.isLocked) {
      this.openOverview.emit({ type: 'document', id: +doc.id, data: doc });
      return;
    }
    this.documentSelected.emit(doc);
  }

  onDocClick(doc: DocumentGridItem, e: MouseEvent): void {
    if (this.renamingId()) return;
    this.handleItemSelection('document', doc.id, e);
  }

  private getAllOrderedItems(): ExplorerItemSelection[] {
    const folderSelections: ExplorerItemSelection[] = this.subfolders().map(f => ({ type: 'folder', id: Number(f.id) }));
    const docSelections: ExplorerItemSelection[] = this.documents().map(d => ({ type: 'document', id: Number(d.id) }));
    return [...folderSelections, ...docSelections];
  }

  private handleItemSelection(type: 'folder' | 'document', id: number | string, e: MouseEvent): void {
    const numId = Number(id);
    const allItems = this.getAllOrderedItems();
    const currentIndex = allItems.findIndex(i => i.type === type && Number(i.id) === numId);

    if (e.shiftKey && this.lastClickIndex !== -1 && currentIndex !== -1) {
      const start = Math.min(this.lastClickIndex, currentIndex);
      const end = Math.max(this.lastClickIndex, currentIndex);
      const range = allItems.slice(start, end + 1);

      if (e.ctrlKey || e.metaKey) {
        // Add range to existing
        const combined = [...this.selectedItems()];
        range.forEach(item => {
          if (!combined.some(c => c.type === item.type && Number(c.id) === Number(item.id))) {
            combined.push(item);
          }
        });
        this.setSelectedItems.emit(combined);
      } else {
        this.setSelectedItems.emit(range);
      }
    } else if (e.ctrlKey || e.metaKey) {
      const exists = this.isItemSelected(type, numId);
      let updated: ExplorerItemSelection[];
      if (exists) {
        updated = this.selectedItems().filter(i => !(i.type === type && Number(i.id) === numId));
      } else {
        updated = [...this.selectedItems(), { type, id: numId }];
      }
      this.setSelectedItems.emit(updated);
      this.lastClickIndex = currentIndex;
    } else {
      this.setSelectedItems.emit([{ type, id: numId }]);
      this.lastClickIndex = currentIndex;
    }
  }

  onContextMenu(e: MouseEvent, type: 'folder' | 'document', id: number, name: string, data: any): void {
    e.preventDefault();
    e.stopPropagation();
    if (!this.isItemSelected(type, id)) {
      this.setSelectedItems.emit([{ type, id }]);
    }
    this.ctxMenu.open({ type, id, name, x: e.clientX, y: e.clientY, data });
  }

  // --- Drag & Drop handlers ---
  onDragStart(e: DragEvent, type: 'folder' | 'document', item: any): void {
    if (item?.isLocked) {
      e.preventDefault();
      return;
    }
    const itemId = Number(item.id);
    if (!this.isItemSelected(type, itemId)) {
      this.setSelectedItems.emit([{ type, id: itemId }]);
    }

    const itemsToDrag = this.isItemSelected(type, itemId) ? this.selectedItems() : [{ type, id: itemId }];

    if (e.dataTransfer) {
      e.dataTransfer.setData('application/json', JSON.stringify(itemsToDrag));
      e.dataTransfer.effectAllowed = 'move';
    }
  }

  onFolderDragOver(e: DragEvent, folderId: number): void {
    e.preventDefault();
    e.stopPropagation();
    if (e.dataTransfer) {
      e.dataTransfer.dropEffect = 'move';
    }
    this.dragOverFolderId.set(folderId);
  }

  onFolderDragLeave(e: DragEvent, folderId: number): void {
    e.preventDefault();
    if (this.dragOverFolderId() === folderId) {
      this.dragOverFolderId.set(null);
    }
  }

  onFolderDrop(e: DragEvent, folder: FolderResponseDto): void {
    e.preventDefault();
    e.stopPropagation();
    this.dragOverFolderId.set(null);

    let itemsToMove: ExplorerItemSelection[] = [];
    try {
      const json = e.dataTransfer?.getData('application/json');
      if (json) itemsToMove = JSON.parse(json);
    } catch {
      itemsToMove = this.selectedItems();
    }

    if (itemsToMove.length === 0) {
      itemsToMove = this.selectedItems();
    }

    // Exclude moving the target folder into itself
    const validItems = itemsToMove.filter(i => !(i.type === 'folder' && Number(i.id) === Number(folder.id)));

    if (validItems.length > 0) {
      this.initiateMove.emit({
        items: validItems,
        targetFolderId: folder.id,
        targetFolderName: folder.name
      });
    }
  }

  // --- Marquee Drag Box Selection ---
  onContainerMouseDown(e: MouseEvent): void {
    if (e.button !== 0) return; // Only left click
    const targetEl = e.target as HTMLElement;
    if (targetEl.closest('.grid-card') || targetEl.closest('.more-btn') || targetEl.closest('.overlay-btn') || targetEl.closest('input')) {
      return;
    }

    this.isMouseDownOnContainer = true;
    const rect = this.gridContainerRef.nativeElement.getBoundingClientRect();
    this.startX = e.clientX - rect.left;
    this.startY = e.clientY - rect.top;

    if (!e.ctrlKey && !e.metaKey && !e.shiftKey) {
      this.setSelectedItems.emit([]);
    }
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(e: MouseEvent): void {
    if (!this.isMouseDownOnContainer || !this.gridContainerRef) return;

    const rect = this.gridContainerRef.nativeElement.getBoundingClientRect();
    const currentX = e.clientX - rect.left;
    const currentY = e.clientY - rect.top;

    const x = Math.min(this.startX, currentX);
    const y = Math.min(this.startY, currentY);
    const width = Math.abs(currentX - this.startX);
    const height = Math.abs(currentY - this.startY);

    if (width > 4 || height > 4) {
      this.marqueeBox.set({ x, y, width, height });
      this.updateSelectionFromMarquee(x, y, width, height, e.ctrlKey || e.metaKey);
    }
  }

  @HostListener('document:mouseup')
  onDocumentMouseUp(): void {
    if (this.isMouseDownOnContainer) {
      this.isMouseDownOnContainer = false;
      this.marqueeBox.set(null);
    }
  }

  private updateSelectionFromMarquee(boxX: number, boxY: number, boxWidth: number, boxHeight: number, isToggle: boolean): void {
    if (!this.gridContainerRef) return;
    const container = this.gridContainerRef.nativeElement;
    const containerRect = container.getBoundingClientRect();

    const marqueeRect = {
      left: containerRect.left + boxX,
      top: containerRect.top + boxY,
      right: containerRect.left + boxX + boxWidth,
      bottom: containerRect.top + boxY + boxHeight
    };

    const cardElements = container.querySelectorAll<HTMLElement>('.grid-card');
    const selected: ExplorerItemSelection[] = isToggle ? [...this.selectedItems()] : [];

    cardElements.forEach(card => {
      const cardRect = card.getBoundingClientRect();
      const intersects = !(
        cardRect.right < marqueeRect.left ||
        cardRect.left > marqueeRect.right ||
        cardRect.bottom < marqueeRect.top ||
        cardRect.top > marqueeRect.bottom
      );

      const type = card.getAttribute('data-type') as 'folder' | 'document';
      const id = Number(card.getAttribute('data-id'));

      if (intersects) {
        if (!selected.some(s => s.type === type && Number(s.id) === id)) {
          selected.push({ type, id });
        }
      }
    });

    this.setSelectedItems.emit(selected);
  }

  onMenuAction(action: ContextMenuAction): void {
    switch (action.action) {
      case 'preview':
        action.type === 'folder'
          ? this.previewFolder.emit(action.id)
          : this.previewDocument.emit(action.data as DocumentGridItem);
        break;
      case 'view':
        action.type === 'folder'
          ? this.folderSelected.emit(action.id)
          : this.documentSelected.emit(action.data as DocumentGridItem);
        break;
      case 'rename':
        this.startRename(action.type, action.id, action.name);
        break;
      case 'download':
        if (action.type === 'folder') {
          this.downloadFolder.emit(action.id);
        } else {
          this.downloadDocument.emit(action.data as DocumentGridItem);
        }
        break;
      case 'delete':
        action.type === 'folder'
          ? this.deleteFolder.emit(action.id)
          : this.deleteDocument.emit(action.id);
        break;
      case 'overview':
        this.openOverview.emit({ type: action.type, id: action.id, data: action.data });
        break;
      case 'permissions':
        this.openPermissions.emit({ type: action.type, id: action.id, data: action.data });
        break;
    }
  }

  startRename(type: 'folder' | 'document', id: number, currentName: string): void {
    const prefix = type === 'document' ? 'doc' : 'folder';
    this.renamingId.set(`${prefix}-${id}`);
    this.renameValue.set(currentName);
  }

  confirmRename(type: 'folder' | 'document', id: number): void {
    const newName = this.renameValue().trim();
    if (newName) this.renameItem.emit({ type, id, newName });
    this.renamingId.set(null);
    this.renameValue.set('');
  }

  cancelRename(): void {
    this.renamingId.set(null);
    this.renameValue.set('');
  }

  getDocName(doc: DocumentGridItem): string { return doc?.fileName || doc?.name || 'Untitled'; }
  getDocMime(doc: DocumentGridItem): string { return doc?.contentType || doc?.mimeType || ''; }
  getDocSize(doc: DocumentGridItem): number { return doc?.fileSize ?? doc?.fileSizeBytes ?? 0; }

  getFileCategory(mimeType: string, filename: string): string {
    const mime = (mimeType || '').toLowerCase();
    const ext  = (filename || '').split('.').pop()?.toLowerCase() ?? '';
    if (mime.includes('pdf') || ext === 'pdf') return 'pdf';
    if (mime.includes('image') || ['png','jpg','jpeg','svg','webp','gif'].includes(ext)) return 'image';
    if (['js','ts','html','css','json','java','py','sh'].includes(ext)) return 'code';
    if (mime.includes('spreadsheet') || mime.includes('excel') || ['csv','xlsx','xls'].includes(ext)) return 'spreadsheet';
    return 'default';
  }

  getFileBadgeLabel(mimeType: string, filename: string): string {
    const cat = this.getFileCategory(mimeType, filename);
    const ext = (filename || '').split('.').pop()?.toUpperCase() ?? '';
    if (cat === 'pdf') return 'PDF';
    if (cat === 'image') return ext || 'IMG';
    if (cat === 'spreadsheet') return ext || 'XLS';
    if (cat === 'code') return ext || 'CODE';
    return ext || 'FILE';
  }

  formatBytes(bytes: number): string {
    if (!bytes) return '—';
    const k = 1024, s = ['B','KB','MB','GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
  }

  formatDate(dateStr?: string | null): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  }
}
