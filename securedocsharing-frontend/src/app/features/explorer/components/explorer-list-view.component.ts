import {
  Component, ChangeDetectionStrategy, input, output, signal, inject, ViewChild, ElementRef, HostListener
} from '@angular/core';

import { FormsModule } from '@angular/forms';
import { LucideFolderSearch } from '@lucide/angular';
import { FolderResponseDto, FolderContentResponseDto } from '../../../core/models/folder.model';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { DocumentGridItem } from './explorer-grid-view.component';
import { ExplorerItemSelection } from '../state/explorer.store';
import { ExplorerContextMenuComponent, ContextMenuAction } from './explorer-context-menu.component';

@Component({
  selector: 'app-explorer-list-view',
  standalone: true,
  imports: [FormsModule, ExplorerContextMenuComponent, LucideFolderSearch],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      #listContainer
      class="list-container"
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

      @if (subfolders().length > 0 || documents().length > 0) {
        <table class="explorer-table">
          <thead>
            <tr>
              <th class="col-name" [style.width.px]="colWidths().name">
                Name
                <div class="resizer" (mousedown)="startResize($event, 'name')"></div>
              </th>
              <th class="col-type" [style.width.px]="colWidths().type">
                Type
                <div class="resizer" (mousedown)="startResize($event, 'type')"></div>
              </th>
              <th class="col-size" [style.width.px]="colWidths().size">
                Size
                <div class="resizer" (mousedown)="startResize($event, 'size')"></div>
              </th>
              <th class="col-date" [style.width.px]="colWidths().date">
                Modified
                <div class="resizer" (mousedown)="startResize($event, 'date')"></div>
              </th>
              <th class="col-actions"></th>
            </tr>
          </thead>
          <tbody>
            <!-- Top Level Folders -->
            @for (folder of subfolders(); track folder.id) {
              <tr
                class="explorer-row folder-row"
                [class.locked-row]="folder.isLocked"
                [attr.data-type]="'folder'"
                [attr.data-id]="folder.id"
                [class.selected]="isItemSelected('folder', folder.id)"
                [class.drag-target]="!folder.isLocked && dragOverFolderId() === folder.id"
                [class.renaming]="renamingId() === 'folder-' + folder.id"
                [draggable]="!folder.isLocked"
                (dragstart)="onDragStart($event, 'folder', folder)"
                (dragover)="!folder.isLocked && onFolderDragOver($event, folder.id)"
                (dragleave)="!folder.isLocked && onFolderDragLeave($event, folder.id)"
                (drop)="!folder.isLocked && onFolderDrop($event, folder)"
                (click)="onFolderClick(folder, $event)"
                (dblclick)="onFolderDblClick(folder, $event)"
                (contextmenu)="onContextMenu($event, 'folder', folder.id, folder.name, folder)"
              >
                <td class="col-name">
                  <div class="name-cell">
                    @if (!folder.isLocked) {
                      <button
                        type="button"
                        class="chevron-btn"
                        [class.expanded]="expandedFolderIds().has(folder.id)"
                        (click)="onToggleExpand($event, folder.id)"
                        title="Toggle folder contents"
                      >
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <path d="m9 18 6-6-6-6"/>
                        </svg>
                      </button>
                    } @else {
                      <div class="lock-indicator" title="Access restricted">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                          <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                        </svg>
                      </div>
                    }
                    <div class="icon-wrap" [class.locked-icon]="folder.isLocked">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                      </svg>
                    </div>
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
                      <span class="item-name" [title]="folder.name">{{ folder.name }}</span>
                      @if (folder.isLocked) {
                        <span class="locked-badge">
                          <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                          </svg>
                          <span>LOCKED</span>
                        </span>
                      }
                    }
                  </div>
                </td>
                <td class="col-type">Folder</td>
                <td class="col-size">—</td>
                <td class="col-date">{{ folder.createdAt ? formatDate(folder.createdAt) : '—' }}</td>
                <td class="col-actions" (click)="$event.stopPropagation()">
                  <div class="action-group">
                    <button class="icon-btn" (click)="openOverview.emit({type: 'folder', id: folder.id, data: folder})" title="Overview">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                    </button>
                    @if (folder.isLocked) {
                      @if (folder.canRequestAccess && folder.requestStatus === 'NONE') {
                        <button class="list-request-btn" (click)="requestAccess.emit({type: 'folder', id: folder.id, name: folder.name})">
                          Request Access
                        </button>
                      } @else if (folder.requestStatus === 'PENDING') {
                        <span class="list-pending-badge">
                          <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"/>
                            <polyline points="12 6 12 12 16 14"/>
                          </svg>
                          <span>Requested</span>
                        </span>
                      }
                    } @else {
                      <button class="icon-btn" (click)="openPermissions.emit({type: 'folder', id: folder.id, data: folder})" title="Permissions">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/></svg>
                      </button>
                      <button class="icon-btn" (click)="onFolderDblClick(folder, $event)" title="Open folder">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
                      </button>
                      <button class="icon-btn" (click)="downloadFolder.emit(folder.id)" title="Download">
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13h10"/><path d="M8 3v7"/><polyline points="5 7 8 10 11 7"/></svg>
                      </button>
                      <button class="icon-btn danger" (click)="deleteFolder.emit(folder.id)" title="Delete">
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="2 4 14 4"/><path d="M5.5 4V3a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1"/><path d="M13 4l-.867 9.143A1 1 0 0 1 11.14 14H4.86a1 1 0 0 1-.993-.857L3 4"/></svg>
                      </button>
                    }
                  </div>
                </td>
              </tr>

              <!-- Level 1 Expanded Child Content -->
              @if (expandedFolderIds().has(folder.id)) {
                @if (childLoadingMap()[folder.id]) {
                  <tr class="child-loading-row">
                    <td colspan="5">
                      <div class="loading-cell" style="padding-left: 2rem;">
                        <span class="spinner-small"></span>
                        <span>Loading contents...</span>
                      </div>
                    </td>
                  </tr>
                } @else if (childContentsMap()[folder.id]) {
                  <!-- Child Subfolders -->
                  @for (sub of childContentsMap()[folder.id].subFolders; track sub.id) {
                    <tr
                      class="explorer-row folder-row child-row"
                      [class.locked-row]="sub.isLocked"
                      [attr.data-type]="'folder'"
                      [attr.data-id]="sub.id"
                      [class.selected]="isItemSelected('folder', sub.id)"
                      [class.drag-target]="!sub.isLocked && dragOverFolderId() === sub.id"
                      [class.renaming]="renamingId() === 'folder-' + sub.id"
                      [draggable]="!sub.isLocked"
                      (dragstart)="onDragStart($event, 'folder', sub)"
                      (dragover)="!sub.isLocked && onFolderDragOver($event, sub.id)"
                      (dragleave)="!sub.isLocked && onFolderDragLeave($event, sub.id)"
                      (drop)="!sub.isLocked && onFolderDrop($event, sub)"
                      (click)="onFolderClick(sub, $event)"
                      (dblclick)="onFolderDblClick(sub, $event)"
                      (contextmenu)="onContextMenu($event, 'folder', sub.id, sub.name, sub)"
                    >
                      <td class="col-name">
                        <div class="name-cell" style="padding-left: 1.25rem;">
                          @if (!sub.isLocked) {
                            <button
                              type="button"
                              class="chevron-btn"
                              [class.expanded]="expandedFolderIds().has(sub.id)"
                              (click)="onToggleExpand($event, sub.id)"
                              title="Toggle folder contents"
                            >
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="m9 18 6-6-6-6"/>
                              </svg>
                            </button>
                          } @else {
                            <div class="lock-indicator" title="Access restricted">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                              </svg>
                            </div>
                          }
                          <div class="icon-wrap" [class.locked-icon]="sub.isLocked">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"/>
                            </svg>
                          </div>
                          @if (renamingId() === 'folder-' + sub.id) {
                            <input
                              class="rename-input"
                              [value]="renameValue()"
                              (input)="renameValue.set($any($event.target).value)"
                              (keydown.enter)="confirmRename('folder', sub.id)"
                              (keydown.escape)="cancelRename()"
                              (blur)="confirmRename('folder', sub.id)"
                              (click)="$event.stopPropagation()"
                              autofocus
                            />
                          } @else {
                            <span class="item-name" [title]="sub.name">{{ sub.name }}</span>
                            @if (sub.isLocked) {
                              <span class="locked-badge">
                                <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                  <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                                </svg>
                                <span>LOCKED</span>
                              </span>
                            }
                          }
                        </div>
                      </td>
                      <td class="col-type">Folder</td>
                      <td class="col-size">—</td>
                      <td class="col-date">{{ sub.createdAt ? formatDate(sub.createdAt) : '—' }}</td>
                      <td class="col-actions" (click)="$event.stopPropagation()">
                        <div class="action-group">
                          <button class="icon-btn" (click)="openOverview.emit({type: 'folder', id: sub.id, data: sub})" title="Overview">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                          </button>
                          @if (sub.isLocked) {
                            @if (sub.canRequestAccess && sub.requestStatus === 'NONE') {
                              <button class="list-request-btn" (click)="requestAccess.emit({type: 'folder', id: sub.id, name: sub.name})">
                                Request Access
                              </button>
                            } @else if (sub.requestStatus === 'PENDING') {
                              <span class="list-pending-badge">
                                <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                  <circle cx="12" cy="12" r="10"/>
                                  <polyline points="12 6 12 12 16 14"/>
                                </svg>
                                <span>Requested</span>
                              </span>
                            }
                          } @else {
                            <button class="icon-btn" (click)="openPermissions.emit({type: 'folder', id: sub.id, data: sub})" title="Permissions">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/></svg>
                            </button>
                            <button class="icon-btn" (click)="onFolderDblClick(sub, $event)" title="Open folder">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="M5 12h14"/><path d="m12 5 7 7-7 7"/></svg>
                            </button>
                            <button class="icon-btn" (click)="downloadFolder.emit(sub.id)" title="Download">
                              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13h10"/><path d="M8 3v7"/><polyline points="5 7 8 10 11 7"/></svg>
                            </button>
                            <button class="icon-btn danger" (click)="deleteFolder.emit(sub.id)" title="Delete">
                              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="2 4 14 4"/><path d="M5.5 4V3a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1"/><path d="M13 4l-.867 9.143A1 1 0 0 1 11.14 14H4.86a1 1 0 0 1-.993-.857L3 4"/></svg>
                            </button>
                          }
                        </div>
                      </td>
                    </tr>
                  }

                  <!-- Child Documents -->
                  @for (cDoc of childContentsMap()[folder.id].documents; track cDoc.id) {
                    <tr
                      class="explorer-row doc-row child-row"
                      [class.locked-row]="cDoc.isLocked"
                      [attr.data-type]="'document'"
                      [attr.data-id]="cDoc.id"
                      [class.selected]="isItemSelected('document', cDoc.id)"
                      [class.renaming]="renamingId() === 'doc-' + cDoc.id"
                      [draggable]="!cDoc.isLocked"
                      (dragstart)="onDragStart($event, 'document', cDoc)"
                      (click)="onDocClick($any(cDoc), $event)"
                      (dblclick)="onDocDblClick($any(cDoc), $event)"
                      (contextmenu)="onContextMenu($event, 'document', +cDoc.id, cDoc.fileName || cDoc.name || '', cDoc)"
                    >
                      <td class="col-name">
                        <div class="name-cell" style="padding-left: 1.25rem;">
                          @if (cDoc.isLocked) {
                            <div class="lock-indicator" title="Access restricted">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                                <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                              </svg>
                            </div>
                          } @else {
                            <div class="chevron-spacer"></div>
                          }
                          <div class="icon-wrap" [class.locked-icon]="cDoc.isLocked">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                              <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
                              <polyline points="14 2 14 8 20 8"/>
                            </svg>
                          </div>
                          @if (renamingId() === 'doc-' + cDoc.id) {
                            <input
                              class="rename-input"
                              [value]="renameValue()"
                              (input)="renameValue.set($any($event.target).value)"
                              (keydown.enter)="confirmRename('document', +cDoc.id)"
                              (keydown.escape)="cancelRename()"
                              (blur)="confirmRename('document', +cDoc.id)"
                              (click)="$event.stopPropagation()"
                              autofocus
                            />
                          } @else {
                            <span class="item-name" [title]="cDoc.fileName || cDoc.name">{{ cDoc.fileName || cDoc.name }}</span>
                            @if (cDoc.isLocked) {
                              <span class="locked-badge">
                                <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                  <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                                  <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                                </svg>
                                <span>LOCKED</span>
                              </span>
                            }
                          }
                        </div>
                      </td>
                      <td class="col-type">{{ cDoc.isLocked ? 'Restricted File' : getFileTypeLabel(cDoc.contentType || cDoc.mimeType, cDoc.fileName || cDoc.name) }}</td>
                      <td class="col-size">{{ cDoc.isLocked ? '—' : formatBytes(cDoc.fileSize || cDoc.fileSizeBytes) }}</td>
                      <td class="col-date">{{ (!cDoc.isLocked && (cDoc.uploadedAt || cDoc.createdAt)) ? formatDate(cDoc.uploadedAt || cDoc.createdAt) : '—' }}</td>
                      <td class="col-actions" (click)="$event.stopPropagation()">
                        <div class="action-group">
                          <button class="icon-btn" (click)="openOverview.emit({type: 'document', id: +cDoc.id, data: cDoc})" title="Overview">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                          </button>
                          @if (cDoc.isLocked) {
                            @if (cDoc.canRequestAccess && cDoc.requestStatus === 'NONE') {
                              <button class="list-request-btn" (click)="requestAccess.emit({type: 'document', id: +cDoc.id, name: cDoc.fileName || cDoc.name || ''})">
                                Request Access
                              </button>
                            } @else if (cDoc.requestStatus === 'PENDING') {
                              <span class="list-pending-badge">
                                <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                  <circle cx="12" cy="12" r="10"/>
                                  <polyline points="12 6 12 12 16 14"/>
                                </svg>
                                <span>Requested</span>
                              </span>
                            }
                          } @else {
                            <button class="icon-btn" (click)="openPermissions.emit({type: 'document', id: +cDoc.id, data: cDoc})" title="Permissions">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/></svg>
                            </button>
                            <button class="icon-btn" (click)="previewDocument.emit($any(cDoc))" title="Preview">
                              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M1 8s2.5-5 7-5 7 5"/><path d="M1 8s2.5 5 7 5 7-5 7-5"/><circle cx="8" cy="8" r="2"/></svg>
                            </button>
                            <button class="icon-btn" (click)="downloadDocument.emit($any(cDoc))" title="Download">
                              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13h10"/><path d="M8 3v7"/><polyline points="5 7 8 10 11 7"/></svg>
                            </button>
                            <button class="icon-btn danger" (click)="deleteDocument.emit(+cDoc.id)" title="Delete">
                              <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="2 4 14 4"/><path d="M5.5 4V3a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1"/><path d="M13 4l-.867 9.143A1 1 0 0 1 11.14 14H4.86a1 1 0 0 1-.993-.857L3 4"/></svg>
                            </button>
                          }
                        </div>
                      </td>
                    </tr>
                  }
                }
              }
            }

            <!-- Top Level Documents -->
            @for (doc of documents(); track doc.id) {
              <tr
                class="explorer-row doc-row"
                [class.locked-row]="doc.isLocked"
                [attr.data-type]="'document'"
                [attr.data-id]="doc.id"
                [class.selected]="isItemSelected('document', doc.id)"
                [class.renaming]="renamingId() === 'doc-' + doc.id"
                [draggable]="!doc.isLocked"
                (dragstart)="onDragStart($event, 'document', doc)"
                (click)="onDocClick(doc, $event)"
                (dblclick)="onDocDblClick(doc, $event)"
                (contextmenu)="onContextMenu($event, 'document', +doc.id, doc.fileName || doc.name || '', doc)"
              >
                <td class="col-name">
                  <div class="name-cell">
                    @if (doc.isLocked) {
                      <div class="lock-indicator" title="Access restricted">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                          <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                          <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                        </svg>
                      </div>
                    } @else {
                      <div class="chevron-spacer"></div>
                    }
                    <div class="icon-wrap" [class.locked-icon]="doc.isLocked">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/>
                        <polyline points="14 2 14 8 20 8"/>
                      </svg>
                    </div>
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
                      <span class="item-name" [title]="doc.fileName || doc.name">{{ doc.fileName || doc.name }}</span>
                      @if (doc.isLocked) {
                        <span class="locked-badge">
                          <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <rect width="18" height="11" x="3" y="11" rx="2" ry="2"/>
                            <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                          </svg>
                          <span>LOCKED</span>
                        </span>
                      }
                    }
                  </div>
                </td>
                <td class="col-type">{{ doc.isLocked ? 'Restricted File' : getFileTypeLabel(doc.contentType || doc.mimeType, doc.fileName || doc.name) }}</td>
                <td class="col-size">{{ doc.isLocked ? '—' : formatBytes(doc.fileSize || doc.fileSizeBytes) }}</td>
                <td class="col-date">{{ (!doc.isLocked && (doc.uploadedAt || doc.createdAt)) ? formatDate(doc.uploadedAt || doc.createdAt) : '—' }}</td>
                <td class="col-actions" (click)="$event.stopPropagation()">
                  <div class="action-group">
                    <button class="icon-btn" (click)="openOverview.emit({type: 'document', id: +doc.id, data: doc})" title="Overview">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><line x1="12" x2="12" y1="20" y2="10"/><line x1="18" x2="18" y1="20" y2="4"/><line x1="6" x2="6" y1="20" y2="14"/></svg>
                    </button>
                    @if (doc.isLocked) {
                      @if (doc.canRequestAccess && doc.requestStatus === 'NONE') {
                        <button class="list-request-btn" (click)="requestAccess.emit({type: 'document', id: +doc.id, name: doc.fileName || doc.name || ''})">
                          Request Access
                        </button>
                      } @else if (doc.requestStatus === 'PENDING') {
                        <span class="list-pending-badge">
                          <svg class="badge-svg-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <circle cx="12" cy="12" r="10"/>
                            <polyline points="12 6 12 12 16 14"/>
                          </svg>
                          <span>Requested</span>
                        </span>
                      }
                    } @else {
                      <button class="icon-btn" (click)="openPermissions.emit({type: 'document', id: +doc.id, data: doc})" title="Permissions">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="m15.5 7.5 2.3 2.3a1 1 0 0 0 1.4 0l2.1-2.1a1 1 0 0 0 0-1.4L19 4"/><path d="m21 2-9.6 9.6"/><circle cx="7.5" cy="15.5" r="5.5"/></svg>
                      </button>
                      <button class="icon-btn" (click)="previewDocument.emit(doc)" title="Preview">
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M1 8s2.5-5 7-5 7 5"/><path d="M1 8s2.5 5 7 5 7-5 7-5"/><circle cx="8" cy="8" r="2"/></svg>
                      </button>
                      <button class="icon-btn" (click)="downloadDocument.emit(doc)" title="Download">
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3 13h10"/><path d="M8 3v7"/><polyline points="5 7 8 10 11 7"/></svg>
                      </button>
                      <button class="icon-btn danger" (click)="deleteDocument.emit(+doc.id)" title="Delete">
                        <svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="2 4 14 4"/><path d="M5.5 4V3a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v1"/><path d="M13 4l-.867 9.143A1 1 0 0 1 11.14 14H4.86a1 1 0 0 1-.993-.857L3 4"/></svg>
                      </button>
                    }
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      } @else {
        <div class="empty-state">
          <div class="empty-icon-box">
            <svg lucideFolderSearch></svg>
          </div>
          <h3 class="empty-title">No folders or files found in this workspace space.</h3>
          <p class="empty-subtitle">Use the upload chips above to insert sample folders and documents!</p>
        </div>
      }
    </div>

    <app-explorer-context-menu
      #ctxMenu
      (actionDispatched)="onMenuAction($event)"
    />
  `,
  styles: [`
    .list-container {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 0.5rem;
      overflow: hidden;
      position: relative;
      user-select: none;
    }

    .marquee-selection-box {
      position: absolute;
      background: rgba(255, 85, 0, 0.08);
      border: 1.5px dashed #ff5500;
      border-radius: 0.25rem;
      pointer-events: none;
      z-index: 50;
      box-sizing: border-box;
    }

    .explorer-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.8125rem;
      table-layout: fixed;
    }
    .explorer-table thead tr {
      background: #f8fafc;
      border-bottom: 1px solid #e2e8f0;
    }
    .explorer-table th {
      padding: 0.625rem 1rem;
      text-align: left;
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748b;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      user-select: none;
      position: relative;
    }
    .resizer {
      position: absolute;
      right: 0;
      top: 0;
      bottom: 0;
      width: 10px;
      cursor: col-resize;
      background: transparent;
      z-index: 10;
    }
    .resizer::after {
      content: '';
      position: absolute;
      top: 25%;
      bottom: 25%;
      right: 4px;
      width: 2px;
      background: #cbd5e1;
      border-radius: 1px;
      transition: background 0.15s, transform 0.15s;
    }
    .resizer:hover::after {
      background: #ff5500;
      transform: scaleX(2);
    }
    .col-actions { text-align: right; width: 120px; }

    .explorer-table td {
      padding: 0.4rem 1rem;
      border-bottom: 1px solid #f1f5f9;
      vertical-align: middle;
      color: #334155;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .col-type, .col-size, .col-date {
      font-size: 0.8125rem;
      color: #64748b;
      font-weight: 400;
    }

    .explorer-table tbody tr.explorer-row {
      height: 2.75rem;
      cursor: pointer;
      transition: background-color 0.15s ease;
      position: relative;
    }
    .explorer-table tbody tr.explorer-row:hover {
      background-color: #f8fafc;
    }
    .explorer-table tbody tr.explorer-row.selected {
      background-color: rgba(249, 115, 22, 0.08);
      box-shadow: inset 3px 0 0 var(--color-accent, #f97316);
    }
    .explorer-table tbody tr.explorer-row.selected:hover {
      background-color: rgba(249, 115, 22, 0.12);
    }
    .explorer-table tbody tr.explorer-row.selected td {
      color: var(--color-accent, #f97316);
    }
    .explorer-table tbody tr.explorer-row.selected .item-name {
      font-weight: 600;
      color: var(--color-accent, #f97316);
    }
    .explorer-table tbody tr.explorer-row.selected .icon-wrap {
      background: rgba(249, 115, 22, 0.14);
      color: var(--color-accent, #f97316);
    }
    .explorer-table tbody tr.explorer-row.drag-target {
      background-color: rgba(249, 115, 22, 0.14) !important;
      box-shadow: inset 3px 0 0 #f97316, inset 0 0 0 1.5px rgba(249, 115, 22, 0.4) !important;
    }
    .explorer-table tbody tr.explorer-row.renaming {
      background-color: rgba(249, 115, 22, 0.08);
      box-shadow: inset 3px 0 0 #f97316;
    }
    .explorer-table tbody tr.explorer-row:last-child td {
      border-bottom: none;
    }

    .name-cell {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      overflow: hidden;
      min-width: 0;
    }
    .chevron-btn {
      background: transparent;
      border: none;
      padding: 0;
      width: 1.25rem;
      height: 1.25rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: #94a3b8;
      cursor: pointer;
      border-radius: 0.25rem;
      flex-shrink: 0;
      transition: color 0.15s ease, background-color 0.15s ease;
    }
    .chevron-btn:hover {
      color: #0f172a;
      background-color: #e2e8f0;
    }
    .chevron-btn svg {
      width: 0.875rem;
      height: 0.875rem;
      transition: transform 0.15s ease;
    }
    .chevron-btn.expanded svg {
      transform: rotate(90deg);
    }
    .chevron-spacer {
      width: 1.25rem;
      height: 1.25rem;
      flex-shrink: 0;
    }

    .icon-wrap {
      width: 1.75rem;
      height: 1.75rem;
      border-radius: 0.375rem;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      background: #f1f5f9;
      color: #475569;
    }
    .icon-wrap svg {
      width: 1rem;
      height: 1rem;
    }

    .item-name {
      font-size: 0.8125rem;
      font-weight: 500;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
    .rename-input {
      font-size: 0.8125rem;
      font-weight: 600;
      border: none;
      border-bottom: 2px solid #ff5500;
      outline: none;
      background: transparent;
      color: #0f172a;
      width: 200px;
      padding: 0 0 0.125rem;
    }

    .action-group {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.25rem;
      opacity: 0;
      transition: opacity 0.12s ease;
    }
    tr.explorer-row:hover .action-group, tr.explorer-row.selected .action-group {
      opacity: 1;
    }
    .icon-btn {
      background: transparent;
      border: none;
      padding: 0.3rem;
      border-radius: 0.375rem;
      color: #64748b;
      cursor: pointer;
      transition: background-color 0.15s ease, color 0.15s ease;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .icon-btn svg { width: 0.875rem; height: 0.875rem; }
    .icon-btn:hover { background: #e2e8f0; color: #0f172a; }
    .icon-btn.danger:hover { background: #fef2f2; color: #ef4444; }

    .locked-row {
      background: #fafaf9;
      opacity: 0.85;
    }
    .locked-row:hover {
      background: #f5f5f4 !important;
    }
    .lock-indicator {
      width: 1.25rem;
      height: 1.25rem;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      color: #ea580c;
      flex-shrink: 0;
    }
    .lock-indicator svg {
      width: 0.875rem;
      height: 0.875rem;
    }
    .icon-wrap.locked-icon {
      background: #fff7ed;
      color: #ea580c;
    }
    .locked-badge {
      font-size: 0.625rem;
      font-weight: 700;
      letter-spacing: 0.05em;
      color: #c2410c;
      background: #ffedd5;
      padding: 0.125rem 0.375rem;
      border-radius: 9999px;
      margin-left: 0.5rem;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }
    .badge-svg-icon {
      width: 10px;
      height: 10px;
      flex-shrink: 0;
    }
    .list-request-btn {
      padding: 0.25rem 0.625rem;
      font-size: 0.75rem;
      font-weight: 600;
      color: #ffffff;
      background: #ea580c;
      border: none;
      border-radius: 0.375rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      transition: background 0.15s ease;
      white-space: nowrap;
    }
    .list-request-btn:hover {
      background: #c2410c;
    }
    .list-pending-badge {
      font-size: 0.75rem;
      font-weight: 600;
      color: #9a3412;
      background: #ffedd5;
      padding: 0.2rem 0.5rem;
      border-radius: 0.375rem;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      white-space: nowrap;
    }

    .child-loading-row td {
      padding: 0.5rem 1rem;
      background: #fafafa;
    }
    .loading-cell {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.75rem;
      color: #64748b;
    }
    .spinner-small {
      width: 0.875rem;
      height: 0.875rem;
      border: 2px solid #e2e8f0;
      border-top-color: #ff5500;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
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
  `],
})
export class ExplorerListViewComponent {
  private readonly folderApiService = inject(FolderApiService);

  subfolders    = input<FolderResponseDto[]>([]);
  documents     = input<DocumentGridItem[]>([]);
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
  @ViewChild('listContainer') listContainerRef!: ElementRef<HTMLDivElement>;

  expandedFolderIds = signal<Set<number>>(new Set());
  childContentsMap  = signal<Record<number, FolderContentResponseDto>>({});
  childLoadingMap   = signal<Record<number, boolean>>({});

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

  onDocClick(doc: DocumentGridItem, e: MouseEvent): void {
    if (this.renamingId()) return;
    this.handleItemSelection('document', doc.id, e);
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
      // Do NOT update lastClickIndex on shift-click, keep anchor point
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

  onToggleExpand(e: MouseEvent, folderId: number): void {
    e.stopPropagation();
    const current = new Set(this.expandedFolderIds());
    if (current.has(folderId)) {
      current.delete(folderId);
      this.expandedFolderIds.set(current);
    } else {
      current.add(folderId);
      this.expandedFolderIds.set(current);

      if (!this.childContentsMap()[folderId]) {
        this.childLoadingMap.update(m => ({ ...m, [folderId]: true }));
        this.folderApiService.getFolderContents(folderId).subscribe({
          next: (res) => {
            this.childContentsMap.update(m => ({ ...m, [folderId]: res }));
            this.childLoadingMap.update(m => ({ ...m, [folderId]: false }));
          },
          error: (err) => {
            console.error(`Failed to load contents for folder ${folderId}:`, err);
            this.childLoadingMap.update(m => ({ ...m, [folderId]: false }));
          }
        });
      }
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
    if (item.isLocked) {
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
    const folder = this.subfolders().find(f => f.id === folderId);
    if (folder?.isLocked) {
      if (e.dataTransfer) {
        e.dataTransfer.dropEffect = 'none';
      }
      return;
    }
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
    if (folder.isLocked) return;

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
    if (e.button !== 0) return;
    const targetEl = e.target as HTMLElement;
    if (targetEl.closest('.explorer-row') || targetEl.closest('.resizer') || targetEl.closest('input') || targetEl.closest('button')) {
      return;
    }

    this.isMouseDownOnContainer = true;
    const rect = this.listContainerRef.nativeElement.getBoundingClientRect();
    this.startX = e.clientX - rect.left;
    this.startY = e.clientY - rect.top;

    if (!e.ctrlKey && !e.metaKey && !e.shiftKey) {
      this.setSelectedItems.emit([]);
    }
  }

  @HostListener('document:mousemove', ['$event'])
  onDocumentMouseMove(e: MouseEvent): void {
    if (this.resizingCol) {
      const diff = e.clientX - this.startColX;
      let newWidth = this.startWidth + diff;
      if (newWidth < 80) newWidth = 80;
      this.colWidths.update(w => ({ ...w, [this.resizingCol as string]: newWidth }));
      return;
    }

    if (!this.isMouseDownOnContainer || !this.listContainerRef) return;

    const rect = this.listContainerRef.nativeElement.getBoundingClientRect();
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
    if (this.resizingCol) {
      this.resizingCol = null;
    }
    if (this.isMouseDownOnContainer) {
      this.isMouseDownOnContainer = false;
      this.marqueeBox.set(null);
    }
  }

  private updateSelectionFromMarquee(boxX: number, boxY: number, boxWidth: number, boxHeight: number, isToggle: boolean): void {
    if (!this.listContainerRef) return;
    const container = this.listContainerRef.nativeElement;
    const containerRect = container.getBoundingClientRect();

    const marqueeRect = {
      left: containerRect.left + boxX,
      top: containerRect.top + boxY,
      right: containerRect.left + boxX + boxWidth,
      bottom: containerRect.top + boxY + boxHeight
    };

    const rowElements = container.querySelectorAll<HTMLElement>('.explorer-row');
    const selected: ExplorerItemSelection[] = isToggle ? [...this.selectedItems()] : [];

    rowElements.forEach(row => {
      const rowRect = row.getBoundingClientRect();
      const intersects = !(
        rowRect.right < marqueeRect.left ||
        rowRect.left > marqueeRect.right ||
        rowRect.bottom < marqueeRect.top ||
        rowRect.top > marqueeRect.bottom
      );

      const type = row.getAttribute('data-type') as 'folder' | 'document';
      const id = Number(row.getAttribute('data-id'));

      if (type && id && intersects) {
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
      case 'requestAccess':
        this.requestAccess.emit({ type: action.type, id: action.id, name: action.name });
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

  formatBytes(bytes?: number): string {
    if (!bytes) return '0 B';
    const k = 1024, s = ['B','KB','MB','GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
  }

  formatDate(dateStr?: string): string {
    if (!dateStr) return '—';
    return new Date(dateStr).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
  }

  getFileTypeLabel(mimeType?: string, filename?: string): string {
    const mime = (mimeType || '').toLowerCase();
    const ext  = (filename || '').split('.').pop()?.toUpperCase() ?? '';
    if (mime.includes('pdf') || ext === 'PDF') return 'PDF Document';
    if (mime.includes('image') || ['PNG','JPG','JPEG','SVG','WEBP'].includes(ext)) return `${ext || 'Image'} File`;
    if (['JS','TS','HTML','CSS','JSON','JAVA','PY'].includes(ext)) return `${ext} Code`;
    if (mime.includes('spreadsheet') || mime.includes('excel') || ['CSV','XLSX','XLS'].includes(ext)) return `${ext || 'Spreadsheet'} File`;
    return ext ? `${ext} File` : 'File';
  }

  // --- Column Resizing Logic ---
  colWidths = signal<{ name: number; type: number; size: number; date: number }>({
    name: 400, type: 150, size: 120, date: 150
  });
  private resizingCol: keyof ReturnType<typeof this.colWidths> | null = null;
  private startColX = 0;
  private startWidth = 0;

  startResize(e: MouseEvent, col: keyof ReturnType<typeof this.colWidths>): void {
    e.preventDefault();
    e.stopPropagation();
    this.resizingCol = col;
    this.startColX = e.clientX;
    this.startWidth = this.colWidths()[col];
  }
}



