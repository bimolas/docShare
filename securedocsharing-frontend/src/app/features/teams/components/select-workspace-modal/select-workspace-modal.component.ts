import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  computed,
  inject,
  effect,
  HostListener,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  LucideX,
  LucideFolder,
  LucideFolderOpen,
  LucideFileText,
  LucideChevronRight,
  LucideChevronDown,
  LucidePlus,
  LucideCheck,
  LucideLock,
  LucideShield,
  LucideAlertCircle,
  LucideRefreshCw,
  LucideSearch,
  LucideLayers,
  LucideFolderPlus,
  LucideHardDrive,
} from '@lucide/angular';
import { FolderApiService } from '../../../../core/services/folder-api.service';
import { PermissionApiService } from '../../../permissions/services/permission-api.service';
import { NotificationService } from '../../../../core/services/notification.service';
import {
  FolderResponseDto,
  FolderContentResponseDto,
} from '../../../../core/models/folder.model';
import {
  PermissionRequestDto,
  PermissionResponseDto,
  SharePermissionLevel,
} from '../../../permissions/interfaces/permission.interfaces';
import { GroupResponseDto } from '../../../../core/models/organization.model';

export type TargetType = 'SUBSPACE' | 'FOLDER' | 'DOCUMENT';

export interface SelectedTarget {
  id: number;
  name: string;
  type: TargetType;
  path: string;
  folderId?: number;
  documentId?: number;
}

export interface TreeNode {
  id: number;
  name: string;
  type: 'FOLDER' | 'DOCUMENT';
  parentFolderId?: number | null;
  childrenLoaded: boolean;
  isExpanded: boolean;
  isLoading: boolean;
  children: TreeNode[];
  documents: Array<{
    id: number;
    name: string;
    fileSizeBytes?: number;
    mimeType?: string;
  }>;
}

@Component({
  selector: 'sd-select-workspace-modal',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideX,
    LucideFolder,
    LucideFolderOpen,
    LucideFileText,
    LucideChevronRight,
    LucideChevronDown,
    LucideCheck,
    LucideLock,
    LucideShield,
    LucideAlertCircle,
    LucideRefreshCw,
    LucideSearch,
    LucideLayers,
    LucideFolderPlus,
    LucideHardDrive,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen()) {
      <div class="modal-backdrop" (click)="onClose()">
        <div
          class="modal-card"
          (click)="$event.stopPropagation()"
          role="dialog"
          aria-labelledby="select-workspace-modal-title"
          aria-modal="true"
        >
          <!-- Header -->
          <div class="modal-header">
            <div class="modal-header-left">
              <div class="icon-badge">
                <svg lucideLayers class="header-lucide-icon"></svg>
              </div>
              <div>
                <h2 id="select-workspace-modal-title" class="modal-title">
                  Allocate Workspace Access
                </h2>
                <p class="modal-subtitle">
                  Allocate a workspace or folder to {{ team()?.name || 'this team' }} to establish the operational boundary and Maximum Permission.
                </p>
              </div>
            </div>
            <button
              type="button"
              class="close-btn"
              (click)="onClose()"
              aria-label="Close modal"
              [disabled]="isSaving()"
            >
              <svg lucideX class="close-icon"></svg>
            </button>
          </div>

          <!-- Error Alert Banner -->
          @if (errorMessage()) {
            <div class="error-alert">
              <svg lucideAlertCircle class="error-icon"></svg>
              <span>{{ errorMessage() }}</span>
            </div>
          }

          <!-- Modal Body -->
          <div class="modal-body">
            <!-- Step 1: Workspace Selector Bar -->
            <div class="subspace-selector-section">
              <label class="subspace-label" for="subspace-select">
                <svg lucideHardDrive class="label-icon"></svg>
                <span>Select Organization Workspace:</span>
              </label>

              @if (isLoadingSubspaces()) {
                <div class="subspace-loading">
                  <div class="spinner-sm"></div>
                  <span>Loading organization workspaces...</span>
                </div>
              } @else if (subspaces().length === 0) {
                <div class="no-subspaces-warning">
                  <svg lucideAlertCircle class="warn-icon"></svg>
                  <span>No organization workspaces found. Please create a workspace first in explorer.</span>
                </div>
              } @else {
                <div class="subspace-chips">
                  @for (subspace of subspaces(); track subspace.id) {
                    <button
                      type="button"
                      class="subspace-chip"
                      [class.active]="selectedSubspace()?.id === subspace.id"
                      (click)="onSelectSubspace(subspace)"
                    >
                      <svg lucideFolderOpen class="chip-icon"></svg>
                      <span class="chip-name">{{ subspace.name }}</span>
                      @if (subspace.isDefaultSubspace) {
                        <span class="default-badge">Default</span>
                      }
                    </button>
                  }
                </div>
              }
            </div>

            <!-- Step 2: IDE Explorer Tree & Selection Panel -->
            @if (selectedSubspace()) {
              <div class="explorer-layout-grid">
                <!-- Left: IDE Explorer Tree -->
                <div class="explorer-tree-panel">
                  <div class="tree-header">
                    <div class="tree-header-title">
                      <svg lucideLayers class="tree-title-icon"></svg>
                      <span>IDE File Explorer</span>
                    </div>

                    <div class="tree-actions">
                      <button
                        type="button"
                        class="tree-action-btn"
                        [disabled]="!selectedTarget()"
                        (click)="openNewFolderModal()"
                        title="Create new folder in active location"
                      >
                        <svg lucideFolderPlus class="action-icon"></svg>
                        <span>New Folder</span>
                      </button>
                      <button
                        type="button"
                        class="tree-action-btn"
                        (click)="refreshCurrentSubspace()"
                        title="Refresh explorer content"
                      >
                        <svg lucideRefreshCw class="action-icon"></svg>
                      </button>
                    </div>
                  </div>

                  <!-- Tree Search Bar -->
                  <div class="tree-search-bar">
                    <svg lucideSearch class="search-icon"></svg>
                    <input
                      type="text"
                      class="tree-search-input"
                      placeholder="Search folders or files in tree..."
                      [value]="searchQuery()"
                      (input)="searchQuery.set($any($event.target).value)"
                    />
                    @if (searchQuery()) {
                      <button
                        type="button"
                        class="clear-search-btn"
                        (click)="searchQuery.set('')"
                      >
                        <svg lucideX class="clear-icon"></svg>
                      </button>
                    }
                  </div>

                  <!-- Tree Scroll Area -->
                  <div class="tree-content-scroll">
                    @if (isLoadingTree()) {
                      <div class="tree-loading">
                        <div class="spinner-sm"></div>
                        <span>Loading explorer hierarchy...</span>
                      </div>
                    } @else if (rootNode()) {
                      <!-- Subspace Root Node -->
                      <div
                        class="tree-item root-item"
                        [class.selected]="selectedTarget()?.type === 'SUBSPACE' && selectedTarget()?.id === rootNode()?.id"
                        (click)="selectSubspaceTarget()"
                      >
                        <button
                          type="button"
                          class="toggle-btn"
                          (click)="toggleNodeExpand(rootNode()!, $event)"
                        >
                          @if (rootNode()!.isExpanded) {
                            <svg lucideChevronDown class="chevron-icon"></svg>
                          } @else {
                            <svg lucideChevronRight class="chevron-icon"></svg>
                          }
                        </button>
                        <div class="item-icon-wrapper is-subspace">
                          <svg lucideHardDrive class="item-icon"></svg>
                        </div>
                        <span class="item-name font-semibold">{{ rootNode()!.name }}</span>
                        <span class="item-type-tag">Workspace Root</span>
                      </div>

                      <!-- Subfolders & Files under Root -->
                      @if (rootNode()!.isExpanded) {
                        <div class="tree-children-container">
                          <!-- Subfolders -->
                          @for (folderNode of filterFolders(rootNode()!.children); track folderNode.id) {
                            <ng-container
                              *ngTemplateOutlet="renderFolderTemplate; context: { $implicit: folderNode, depth: 1 }"
                            ></ng-container>
                          }

                          <!-- Direct Documents under Root -->
                          @for (doc of filterDocuments(rootNode()!.documents); track doc.id) {
                            <div
                              class="tree-item doc-item depth-1"
                              [class.selected]="selectedTarget()?.type === 'DOCUMENT' && selectedTarget()?.id === doc.id"
                              (click)="selectDocumentTarget(doc, rootNode()!)"
                            >
                              <div class="item-spacer"></div>
                              <div class="item-icon-wrapper is-doc">
                                <svg lucideFileText class="item-icon"></svg>
                              </div>
                              <span class="item-name">{{ doc.name }}</span>
                              @if (doc.fileSizeBytes) {
                                <span class="file-size-badge">{{ formatFileSize(doc.fileSizeBytes) }}</span>
                              }
                            </div>
                          }

                          @if (rootNode()!.children.length === 0 && rootNode()!.documents.length === 0) {
                            <div class="tree-empty-nested depth-1">
                              <span>(Empty Workspace)</span>
                            </div>
                          }
                        </div>
                      }
                    }
                  </div>
                </div>

                <!-- Right: Active Selection Details & Permission Level Config -->
                <div class="explorer-config-panel">
                  <div class="config-section-title">
                    <svg lucideShield class="config-title-icon"></svg>
                    <span>Allocation Configuration</span>
                  </div>

                  <!-- Active Target Card -->
                  <div class="target-card" [class.has-target]="!!selectedTarget()">
                    @if (selectedTarget(); as target) {
                      <div class="target-card-header">
                        <div class="target-type-badge" [class]="'badge-' + target.type.toLowerCase()">
                          @if (target.type === 'SUBSPACE') {
                            <svg lucideHardDrive class="badge-icon"></svg>
                            <span>Workspace</span>
                          } @else if (target.type === 'FOLDER') {
                            <svg lucideFolder class="badge-icon"></svg>
                            <span>Folder</span>
                          } @else {
                            <svg lucideFileText class="badge-icon"></svg>
                            <span>Document</span>
                          }
                        </div>
                        <span class="target-id-badge">ID: #{{ target.id }}</span>
                      </div>

                      <div class="target-name-row">
                        <span class="target-label">Target Name:</span>
                        <h4 class="target-name-text">{{ target.name }}</h4>
                      </div>

                      <div class="target-path-row">
                        <span class="target-label">Scope Path:</span>
                        <span class="target-path-text font-mono">{{ target.path }}</span>
                      </div>
                    } @else {
                      <div class="target-empty">
                        <svg lucideLayers class="target-empty-icon"></svg>
                        <p class="target-empty-title">No Resource Selected</p>
                        <p class="target-empty-desc">
                          Click on a workspace root, folder, or file in the explorer tree on the left to allocate it to this team.
                        </p>
                      </div>
                    }
                  </div>

                  <!-- Permission Level Selector -->
                  <div class="perm-selector-card">
                    <label class="perm-section-label">
                      <svg lucideLock class="perm-label-icon"></svg>
                      <span>Maximum Permission</span>
                    </label>

                    <div class="perm-options-group">
                      <!-- VIEW Option -->
                      <label
                        class="perm-option-card"
                        [class.selected]="selectedPermissionLevel() === 'VIEW'"
                      >
                        <input
                          type="radio"
                          name="permLevel"
                          value="VIEW"
                          [checked]="selectedPermissionLevel() === 'VIEW'"
                          (change)="selectedPermissionLevel.set('VIEW')"
                        />
                        <div class="option-content">
                          <div class="option-title-row">
                            <span class="option-title">View Only</span>
                            <span class="option-badge badge-view">VIEW</span>
                          </div>
                          <p class="option-desc">
                            Team members can browse and view metadata and content. Downloading or editing is restricted.
                          </p>
                        </div>
                      </label>

                      <!-- DOWNLOAD Option -->
                      <label
                        class="perm-option-card"
                        [class.selected]="selectedPermissionLevel() === 'DOWNLOAD'"
                      >
                        <input
                          type="radio"
                          name="permLevel"
                          value="DOWNLOAD"
                          [checked]="selectedPermissionLevel() === 'DOWNLOAD'"
                          (change)="selectedPermissionLevel.set('DOWNLOAD')"
                        />
                        <div class="option-content">
                          <div class="option-title-row">
                            <span class="option-title">View & Download</span>
                            <span class="option-badge badge-download">DOWNLOAD</span>
                          </div>
                          <p class="option-desc">
                            Team members can view contents and download files directly to their device.
                          </p>
                        </div>
                      </label>

                      <!-- EDIT Option -->
                      <label
                        class="perm-option-card"
                        [class.selected]="selectedPermissionLevel() === 'EDIT'"
                      >
                        <input
                          type="radio"
                          name="permLevel"
                          value="EDIT"
                          [checked]="selectedPermissionLevel() === 'EDIT'"
                          (change)="selectedPermissionLevel.set('EDIT')"
                        />
                        <div class="option-content">
                          <div class="option-title-row">
                            <span class="option-title">Full Access / Edit</span>
                            <span class="option-badge badge-edit">EDIT</span>
                          </div>
                          <p class="option-desc">
                            Team members can view, download, create subfolders, upload, and update documents.
                          </p>
                        </div>
                      </label>
                    </div>
                  </div>
                </div>
              </div>
            }
          </div>

          <!-- Modal Footer -->
          <div class="modal-footer">
            <button
              type="button"
              class="btn-secondary"
              (click)="onClose()"
              [disabled]="isSaving()"
            >
              Cancel
            </button>
            <button
              type="button"
              class="btn-primary"
              [disabled]="!selectedTarget() || isSaving()"
              (click)="onAssignWorkspace()"
            >
              @if (isSaving()) {
                <div class="spinner-sm text-white"></div>
                <span>Assigning Access...</span>
              } @else {
                <svg lucideCheck class="btn-icon"></svg>
                <span>Confirm Workspace Allocation</span>
              }
            </button>
          </div>
        </div>
      </div>

      <!-- Recursive Folder Template Definition -->
      <ng-template #renderFolderTemplate let-folderNode let-depth="depth">
        <div class="tree-item-group">
          <div
            class="tree-item folder-item"
            [style.padding-left.px]="depth * 16 + 12"
            [class.selected]="selectedTarget()?.type === 'FOLDER' && selectedTarget()?.id === folderNode.id"
            (click)="selectFolderTarget(folderNode)"
          >
            <button
              type="button"
              class="toggle-btn"
              (click)="toggleNodeExpand(folderNode, $event)"
            >
              @if (folderNode.isLoading) {
                <div class="spinner-xs"></div>
              } @else if (folderNode.isExpanded) {
                <svg lucideChevronDown class="chevron-icon"></svg>
              } @else {
                <svg lucideChevronRight class="chevron-icon"></svg>
              }
            </button>
            <div class="item-icon-wrapper is-folder">
              @if (folderNode.isExpanded) {
                <svg lucideFolderOpen class="item-icon"></svg>
              } @else {
                <svg lucideFolder class="item-icon"></svg>
              }
            </div>
            <span class="item-name">{{ folderNode.name }}</span>
          </div>

          <!-- Expanded Folder Children -->
          @if (folderNode.isExpanded) {
            <div class="tree-children-container">
              @for (childFolder of filterFolders(folderNode.children); track childFolder.id) {
                <ng-container
                  *ngTemplateOutlet="renderFolderTemplate; context: { $implicit: childFolder, depth: depth + 1 }"
                ></ng-container>
              }

              @for (doc of filterDocuments(folderNode.documents); track doc.id) {
                <div
                  class="tree-item doc-item"
                  [style.padding-left.px]="(depth + 1) * 16 + 12"
                  [class.selected]="selectedTarget()?.type === 'DOCUMENT' && selectedTarget()?.id === doc.id"
                  (click)="selectDocumentTarget(doc, folderNode)"
                >
                  <div class="item-spacer"></div>
                  <div class="item-icon-wrapper is-doc">
                    <svg lucideFileText class="item-icon"></svg>
                  </div>
                  <span class="item-name">{{ doc.name }}</span>
                  @if (doc.fileSizeBytes) {
                    <span class="file-size-badge">{{ formatFileSize(doc.fileSizeBytes) }}</span>
                  }
                </div>
              }

              @if (folderNode.children.length === 0 && folderNode.documents.length === 0 && folderNode.childrenLoaded) {
                <div class="tree-empty-nested" [style.padding-left.px]="(depth + 1) * 16 + 12">
                  <span>(Empty Folder)</span>
                </div>
              }
            </div>
          }
        </div>
      </ng-template>

      <!-- New Folder Sub-Modal / Prompt Overlay -->
      @if (isNewFolderModalOpen()) {
        <div class="submodal-backdrop" (click)="isNewFolderModalOpen.set(false)">
          <div class="submodal-card" (click)="$event.stopPropagation()">
            <div class="submodal-header">
              <h4>Create New Folder</h4>
              <button
                type="button"
                class="close-btn"
                (click)="isNewFolderModalOpen.set(false)"
              >
                <svg lucideX class="close-icon"></svg>
              </button>
            </div>
            <div class="submodal-body">
              <p class="submodal-hint">
                Creating folder inside: <strong class="font-mono">{{ selectedTarget()?.name || selectedSubspace()?.name }}</strong>
              </p>
              <div class="form-field">
                <label for="new-folder-input">Folder Name</label>
                <input
                  id="new-folder-input"
                  type="text"
                  class="form-input"
                  placeholder="e.g. Shared Workspace Documents"
                  [value]="newFolderName()"
                  (input)="newFolderName.set($any($event.target).value)"
                  (keyup.enter)="createSubfolder()"
                />
              </div>
            </div>
            <div class="submodal-footer">
              <button
                type="button"
                class="btn-secondary"
                (click)="isNewFolderModalOpen.set(false)"
              >
                Cancel
              </button>
              <button
                type="button"
                class="btn-primary"
                [disabled]="!newFolderName().trim() || isCreatingFolder()"
                (click)="createSubfolder()"
              >
                @if (isCreatingFolder()) {
                  <div class="spinner-sm text-white"></div>
                  <span>Creating...</span>
                } @else {
                  <span>Create & Select</span>
                }
              </button>
            </div>
          </div>
        </div>
      }
    }
  `,
  styles: [`
    .modal-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1200;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(15, 23, 42, 0.6);
      backdrop-filter: blur(4px);
      padding: 1.5rem;
      animation: fadeIn 0.2s ease-out;
    }

    .modal-card {
      width: 100%;
      max-width: 1040px;
      max-height: 90vh;
      display: flex;
      flex-direction: column;
      background: var(--color-surface, #ffffff);
      border: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
      border-radius: 16px;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
      overflow: hidden;
    }

    .modal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
      background: var(--color-bg-alt, #f8fafc);
    }

    .modal-header-left {
      display: flex;
      align-items: center;
      gap: 0.875rem;
    }

    .icon-badge {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 42px;
      height: 42px;
      border-radius: 10px;
      background: rgba(249, 115, 22, 0.1);
      color: #ea580c;
    }

    .header-lucide-icon {
      width: 22px;
      height: 22px;
    }

    .modal-title {
      font-size: 1.25rem;
      font-weight: 700;
      color: var(--color-text-main, #0f172a);
      margin: 0;
      line-height: 1.3;
    }

    .modal-subtitle {
      font-size: 0.875rem;
      color: var(--color-text-muted, #64748b);
      margin: 0.25rem 0 0 0;
    }

    .close-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 8px;
      border: 1px solid transparent;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .close-btn:hover {
      background: rgba(226, 232, 240, 0.6);
      color: #0f172a;
    }

    .close-icon {
      width: 18px;
      height: 18px;
    }

    .error-alert {
      display: flex;
      align-items: center;
      gap: 0.625rem;
      padding: 0.75rem 1.5rem;
      background: #fef2f2;
      border-bottom: 1px solid #fecaca;
      color: #dc2626;
      font-size: 0.875rem;
    }

    .error-icon {
      width: 18px;
      height: 18px;
      flex-shrink: 0;
    }

    .modal-body {
      flex: 1;
      overflow-y: auto;
      padding: 1.25rem 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }

    /* Subspace Selector Section */
    .subspace-selector-section {
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
      background: var(--color-bg-alt, #f8fafc);
      padding: 1rem;
      border-radius: 12px;
      border: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
    }

    .subspace-label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: var(--color-text-main, #1e293b);
    }

    .label-icon {
      width: 16px;
      height: 16px;
      color: #ea580c;
    }

    .subspace-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.625rem;
    }

    .subspace-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.875rem;
      border-radius: 8px;
      border: 1px solid var(--border-color, #cbd5e1);
      background: #ffffff;
      color: #334155;
      font-size: 0.875rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .subspace-chip:hover {
      border-color: #94a3b8;
      background: #f1f5f9;
    }

    .subspace-chip.active {
      background: #fff7ed;
      border-color: #f97316;
      color: #c2410c;
      font-weight: 600;
      box-shadow: 0 0 0 1px #f97316;
    }

    .chip-icon {
      width: 16px;
      height: 16px;
      color: #ea580c;
    }

    .default-badge {
      font-size: 0.7rem;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      background: #ffedd5;
      color: #9a3412;
      font-weight: 600;
      text-transform: uppercase;
    }

    .subspace-loading, .no-subspaces-warning {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      color: #64748b;
      padding: 0.5rem;
    }

    /* IDE Explorer Layout Grid */
    .explorer-layout-grid {
      display: grid;
      grid-template-columns: 1.2fr 1fr;
      gap: 1.25rem;
      min-height: 400px;
    }

    @media (max-width: 868px) {
      .explorer-layout-grid {
        grid-template-columns: 1fr;
      }
    }

    /* Tree Panel */
    .explorer-tree-panel {
      display: flex;
      flex-direction: column;
      border: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
      border-radius: 12px;
      background: #ffffff;
      overflow: hidden;
    }

    .tree-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.75rem 1rem;
      background: #f8fafc;
      border-bottom: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
    }

    .tree-header-title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: #1e293b;
    }

    .tree-title-icon {
      width: 16px;
      height: 16px;
      color: #ea580c;
    }

    .tree-actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .tree-action-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.375rem 0.625rem;
      border-radius: 6px;
      border: 1px solid #cbd5e1;
      background: #ffffff;
      font-size: 0.75rem;
      font-weight: 600;
      color: #334155;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .tree-action-btn:hover:not(:disabled) {
      background: #f1f5f9;
      border-color: #94a3b8;
    }

    .tree-action-btn:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }

    .action-icon {
      width: 14px;
      height: 14px;
    }

    .tree-search-bar {
      position: relative;
      padding: 0.5rem 0.75rem;
      border-bottom: 1px solid #f1f5f9;
      display: flex;
      align-items: center;
    }

    .tree-search-bar .search-icon {
      position: absolute;
      left: 1.25rem;
      width: 14px;
      height: 14px;
      color: #94a3b8;
    }

    .tree-search-input {
      width: 100%;
      padding: 0.375rem 1.75rem 0.375rem 2rem;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      font-size: 0.8125rem;
      color: #1e293b;
      outline: none;
    }

    .tree-search-input:focus {
      border-color: #f97316;
      box-shadow: 0 0 0 2px rgba(249, 115, 22, 0.15);
    }

    .clear-search-btn {
      position: absolute;
      right: 1.25rem;
      border: none;
      background: transparent;
      color: #94a3b8;
      cursor: pointer;
    }

    .clear-icon {
      width: 14px;
      height: 14px;
    }

    .tree-content-scroll {
      flex: 1;
      max-height: 360px;
      overflow-y: auto;
      padding: 0.5rem 0;
    }

    .tree-loading {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 2rem;
      font-size: 0.875rem;
      color: #64748b;
    }

    /* Tree Items Styling */
    .tree-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4375rem 0.75rem;
      cursor: pointer;
      user-select: none;
      transition: all 0.12s ease;
      border-left: 3px solid transparent;
      font-size: 0.84375rem;
    }

    .tree-item:hover {
      background: #f8fafc;
    }

    .tree-item.selected {
      background: #fff7ed;
      border-left-color: #f97316;
      color: #c2410c;
      font-weight: 600;
    }

    .toggle-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 20px;
      height: 20px;
      border: none;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      padding: 0;
    }

    .chevron-icon {
      width: 14px;
      height: 14px;
    }

    .item-icon-wrapper {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      flex-shrink: 0;
    }

    .item-icon-wrapper.is-subspace {
      color: #ea580c;
    }

    .item-icon-wrapper.is-folder {
      color: #d97706;
    }

    .item-icon-wrapper.is-doc {
      color: #0284c7;
    }

    .item-icon {
      width: 16px;
      height: 16px;
    }

    .item-name {
      flex: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .item-type-tag {
      font-size: 0.6875rem;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
      background: #e0e7ff;
      color: #3730a3;
      font-weight: 600;
    }

    .file-size-badge {
      font-size: 0.71875rem;
      font-family: monospace;
      color: #94a3b8;
    }

    .tree-children-container {
      display: flex;
      flex-direction: column;
    }

    .tree-empty-nested {
      padding: 0.375rem 1rem 0.375rem 2.5rem;
      font-size: 0.75rem;
      color: #94a3b8;
      font-style: italic;
    }

    /* Right Config Panel */
    .explorer-config-panel {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }

    .config-section-title {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: #1e293b;
    }

    .config-title-icon {
      width: 16px;
      height: 16px;
      color: #ea580c;
    }

    /* Target Card */
    .target-card {
      padding: 1rem;
      border-radius: 12px;
      border: 1px dashed #cbd5e1;
      background: #f8fafc;
      transition: all 0.2s ease;
    }

    .target-card.has-target {
      border-style: solid;
      border-color: #f97316;
      background: #ffffff;
      box-shadow: 0 4px 12px rgba(249, 115, 22, 0.08);
    }

    .target-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.75rem;
    }

    .target-type-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.375rem;
      padding: 0.25rem 0.625rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
    }

    .badge-subspace {
      background: #ffedd5;
      color: #9a3412;
    }

    .badge-folder {
      background: #fef3c7;
      color: #b45309;
    }

    .badge-document {
      background: #e0f2fe;
      color: #0369a1;
    }

    .badge-icon {
      width: 14px;
      height: 14px;
    }

    .target-id-badge {
      font-size: 0.75rem;
      font-family: monospace;
      color: #64748b;
    }

    .target-name-row {
      margin-bottom: 0.5rem;
    }

    .target-label {
      font-size: 0.75rem;
      color: #64748b;
      display: block;
      margin-bottom: 0.125rem;
    }

    .target-name-text {
      font-size: 1rem;
      font-weight: 700;
      color: #0f172a;
      margin: 0;
      word-break: break-all;
    }

    .target-path-text {
      font-size: 0.78125rem;
      color: #334155;
      background: #f1f5f9;
      padding: 0.25rem 0.5rem;
      border-radius: 4px;
      display: block;
      word-break: break-all;
    }

    .target-empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 1.5rem 1rem;
      color: #64748b;
    }

    .target-empty-icon {
      width: 32px;
      height: 32px;
      color: #94a3b8;
      margin-bottom: 0.5rem;
    }

    .target-empty-title {
      font-size: 0.9375rem;
      font-weight: 600;
      color: #334155;
      margin: 0 0 0.25rem 0;
    }

    .target-empty-desc {
      font-size: 0.8125rem;
      color: #64748b;
      margin: 0;
      line-height: 1.4;
    }

    /* Permission Selector Card */
    .perm-selector-card {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      background: #ffffff;
      border: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
      border-radius: 12px;
      padding: 1rem;
    }

    .perm-section-label {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.875rem;
      font-weight: 600;
      color: #1e293b;
    }

    .perm-label-icon {
      width: 16px;
      height: 16px;
      color: #ea580c;
    }

    .perm-options-group {
      display: flex;
      flex-direction: column;
      gap: 0.625rem;
    }

    .perm-option-card {
      display: flex;
      align-items: flex-start;
      gap: 0.75rem;
      padding: 0.75rem;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
      background: #ffffff;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .perm-option-card:hover {
      border-color: #cbd5e1;
      background: #f8fafc;
    }

    .perm-option-card.selected {
      border-color: #f97316;
      background: #fff7ed;
      box-shadow: 0 0 0 1px #f97316;
    }

    .perm-option-card input[type="radio"] {
      margin-top: 0.25rem;
      accent-color: #ea580c;
    }

    .option-content {
      flex: 1;
    }

    .option-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.25rem;
    }

    .option-title {
      font-size: 0.875rem;
      font-weight: 600;
      color: #0f172a;
    }

    .option-badge {
      font-size: 0.6875rem;
      font-weight: 700;
      padding: 0.125rem 0.375rem;
      border-radius: 4px;
    }

    .badge-view {
      background: #f1f5f9;
      color: #475569;
    }

    .badge-download {
      background: #ffedd5;
      color: #9a3412;
    }

    .badge-edit {
      background: #dcfce7;
      color: #15803d;
    }

    .option-desc {
      font-size: 0.78125rem;
      color: #64748b;
      margin: 0;
      line-height: 1.35;
    }

    /* Modal Footer */
    .modal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.75rem;
      padding: 1rem 1.5rem;
      border-top: 1px solid var(--border-color, rgba(226, 232, 240, 0.8));
      background: var(--color-bg-alt, #f8fafc);
    }

    .btn-secondary {
      padding: 0.5rem 1rem;
      border-radius: 8px;
      border: 1px solid #cbd5e1;
      background: #ffffff;
      color: #334155;
      font-size: 0.875rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-secondary:hover:not(:disabled) {
      background: #f1f5f9;
    }

    .btn-primary {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1.25rem;
      border-radius: 8px;
      border: none;
      background: #ea580c;
      color: #ffffff;
      font-size: 0.875rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }

    .btn-primary:hover:not(:disabled) {
      background: #c2410c;
    }

    .btn-primary:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .btn-icon {
      width: 16px;
      height: 16px;
    }

    /* Spinners */
    .spinner-sm {
      width: 16px;
      height: 16px;
      border: 2px solid rgba(37, 99, 235, 0.3);
      border-top-color: #2563eb;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    .spinner-sm.text-white {
      border-color: rgba(255, 255, 255, 0.3);
      border-top-color: #ffffff;
    }

    .spinner-xs {
      width: 12px;
      height: 12px;
      border: 2px solid rgba(37, 99, 235, 0.3);
      border-top-color: #2563eb;
      border-radius: 50%;
      animation: spin 0.6s linear infinite;
    }

    @keyframes spin {
      to { transform: rotate(360deg); }
    }

    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }

    /* Submodal / Prompt Overlay */
    .submodal-backdrop {
      position: fixed;
      inset: 0;
      z-index: 1250;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(15, 23, 42, 0.5);
      backdrop-filter: blur(2px);
    }

    .submodal-card {
      width: 100%;
      max-width: 440px;
      background: #ffffff;
      border-radius: 12px;
      box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2);
      border: 1px solid #e2e8f0;
      overflow: hidden;
    }

    .submodal-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 1rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      background: #f8fafc;
    }

    .submodal-header h4 {
      margin: 0;
      font-size: 1rem;
      font-weight: 700;
      color: #0f172a;
    }

    .submodal-body {
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.875rem;
    }

    .submodal-hint {
      font-size: 0.8125rem;
      color: #64748b;
      margin: 0;
    }

    .form-field {
      display: flex;
      flex-direction: column;
      gap: 0.375rem;
    }

    .form-field label {
      font-size: 0.8125rem;
      font-weight: 600;
      color: #334155;
    }

    .form-input {
      padding: 0.5rem 0.75rem;
      border-radius: 8px;
      border: 1px solid #cbd5e1;
      font-size: 0.875rem;
      outline: none;
    }

    .form-input:focus {
      border-color: #2563eb;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15);
    }

    .submodal-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.625rem;
      padding: 0.875rem 1.25rem;
      background: #f8fafc;
      border-top: 1px solid #e2e8f0;
    }
  `],
})
export class SelectWorkspaceModalComponent {
  // Inputs
  isOpen = input<boolean>(false);
  team = input<GroupResponseDto | null>(null);
  orgCode = input<string>('');

  // Outputs
  close = output<void>();
  workspaceAllocated = output<PermissionResponseDto>();

  // Services
  private readonly folderApiService = inject(FolderApiService);
  private readonly permissionApiService = inject(PermissionApiService);
  private readonly notificationService = inject(NotificationService);

  // Signals - State
  subspaces = signal<FolderResponseDto[]>([]);
  selectedSubspace = signal<FolderResponseDto | null>(null);

  isLoadingSubspaces = signal<boolean>(false);
  isLoadingTree = signal<boolean>(false);
  isSaving = signal<boolean>(false);
  errorMessage = signal<string>('');

  // Tree state
  rootNode = signal<TreeNode | null>(null);
  selectedTarget = signal<SelectedTarget | null>(null);
  selectedPermissionLevel = signal<SharePermissionLevel>('VIEW');

  // Search in tree
  searchQuery = signal<string>('');

  // Inline folder creation submodal state
  isNewFolderModalOpen = signal<boolean>(false);
  newFolderName = signal<string>('');
  isCreatingFolder = signal<boolean>(false);

  constructor() {
    // When modal opens and orgCode is available, load organization subspaces
    effect(() => {
      if (this.isOpen() && this.orgCode()) {
        this.loadSubspaces();
      }
    });
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.isOpen() && !this.isSaving()) {
      this.onClose();
    }
  }

  onClose(): void {
    this.errorMessage.set('');
    this.close.emit();
  }

  loadSubspaces(): void {
    const code = this.orgCode();
    if (!code) return;

    this.isLoadingSubspaces.set(true);
    this.errorMessage.set('');

    this.folderApiService.getOrganizationSubspaces(code).subscribe({
      next: (subspaces) => {
        this.subspaces.set(subspaces || []);
        this.isLoadingSubspaces.set(false);

        // Auto-select first subspace if available and none selected
        if (subspaces && subspaces.length > 0 && !this.selectedSubspace()) {
          this.onSelectSubspace(subspaces[0]);
        }
      },
      error: (err) => {
        this.isLoadingSubspaces.set(false);
        this.errorMessage.set(
          err?.error?.message || 'Failed to load organization subspaces.'
        );
      },
    });
  }

  onSelectSubspace(subspace: FolderResponseDto): void {
    this.selectedSubspace.set(subspace);
    this.searchQuery.set('');

    // Pre-select Subspace Root as default target
    this.selectSubspaceTarget();

    // Fetch subspace contents to initialize root node
    this.loadSubspaceTree(subspace.id, subspace.name);
  }

  loadSubspaceTree(subspaceId: number, subspaceName: string): void {
    this.isLoadingTree.set(true);
    this.errorMessage.set('');

    this.folderApiService.getFolderContents(subspaceId).subscribe({
      next: (content) => {
        const root: TreeNode = {
          id: subspaceId,
          name: subspaceName,
          type: 'FOLDER',
          childrenLoaded: true,
          isExpanded: true,
          isLoading: false,
          children: (content.subFolders || []).map((sf) => ({
            id: sf.id,
            name: sf.name,
            type: 'FOLDER',
            parentFolderId: sf.parentFolderId,
            childrenLoaded: false,
            isExpanded: false,
            isLoading: false,
            children: [],
            documents: [],
          })),
          documents: (content.documents || []).map((doc) => ({
            id: Number(doc.id),
            name: doc.name || doc.fileName || `Document #${doc.id}`,
            fileSizeBytes: doc.fileSizeBytes || doc.fileSize,
            mimeType: doc.mimeType || doc.contentType,
          })),
        };

        this.rootNode.set(root);
        this.isLoadingTree.set(false);
      },
      error: (err) => {
        this.isLoadingTree.set(false);
        this.errorMessage.set(
          err?.error?.message || 'Failed to load subspace content.'
        );
      },
    });
  }

  refreshCurrentSubspace(): void {
    const sub = this.selectedSubspace();
    if (sub) {
      this.loadSubspaceTree(sub.id, sub.name);
    }
  }

  // Node Expansion & Lazy Loading
  toggleNodeExpand(node: TreeNode, event: MouseEvent): void {
    event.stopPropagation();
    node.isExpanded = !node.isExpanded;

    if (node.isExpanded && !node.childrenLoaded && node.type === 'FOLDER') {
      node.isLoading = true;
      this.folderApiService.getFolderContents(node.id).subscribe({
        next: (content) => {
          node.children = (content.subFolders || []).map((sf) => ({
            id: sf.id,
            name: sf.name,
            type: 'FOLDER',
            parentFolderId: sf.parentFolderId,
            childrenLoaded: false,
            isExpanded: false,
            isLoading: false,
            children: [],
            documents: [],
          }));
          node.documents = (content.documents || []).map((doc) => ({
            id: Number(doc.id),
            name: doc.name || doc.fileName || `Document #${doc.id}`,
            fileSizeBytes: doc.fileSizeBytes || doc.fileSize,
            mimeType: doc.mimeType || doc.contentType,
          }));
          node.childrenLoaded = true;
          node.isLoading = false;

          // Force signal refresh
          this.rootNode.set({ ...this.rootNode()! });
        },
        error: () => {
          node.isLoading = false;
        },
      });
    }
  }

  // Target Selection Handlers
  selectSubspaceTarget(): void {
    const sub = this.selectedSubspace();
    if (!sub) return;

    this.selectedTarget.set({
      id: sub.id,
      name: sub.name,
      type: 'SUBSPACE',
      path: `Subspace / ${sub.name}`,
      folderId: sub.id,
    });
  }

  selectFolderTarget(folderNode: TreeNode): void {
    const subName = this.selectedSubspace()?.name || 'Subspace';
    this.selectedTarget.set({
      id: folderNode.id,
      name: folderNode.name,
      type: 'FOLDER',
      path: `${subName} / ${folderNode.name}`,
      folderId: folderNode.id,
    });
  }

  selectDocumentTarget(doc: { id: number; name: string; fileSizeBytes?: number }, parentNode: TreeNode): void {
    const subName = this.selectedSubspace()?.name || 'Subspace';
    this.selectedTarget.set({
      id: doc.id,
      name: doc.name,
      type: 'DOCUMENT',
      path: `${subName} / ${parentNode.name} / ${doc.name}`,
      documentId: doc.id,
    });
  }

  // Inline Folder Creation
  openNewFolderModal(): void {
    this.newFolderName.set('');
    this.isNewFolderModalOpen.set(true);
  }

  createSubfolder(): void {
    const folderName = this.newFolderName().trim();
    const code = this.orgCode();
    const currentTarget = this.selectedTarget();

    if (!folderName || !code) return;

    // Parent folder ID is selected target folder/subspace ID
    const parentFolderId = currentTarget?.folderId || this.selectedSubspace()?.id;

    this.isCreatingFolder.set(true);

    this.folderApiService
      .createFolder(code, {
        name: folderName,
        parentFolderId: parentFolderId || null,
        subspaceType: 'ORGANIZATION_GENERAL',
      })
      .subscribe({
        next: (createdFolder) => {
          this.isCreatingFolder.set(false);
          this.isNewFolderModalOpen.set(false);
          this.notificationService.success(
            `Folder "${createdFolder.name}" created successfully.`
          );

          // Refresh current subspace tree and select newly created folder
          this.refreshCurrentSubspace();
        },
        error: (err) => {
          this.isCreatingFolder.set(false);
          this.notificationService.error(
            err?.error?.message || 'Failed to create folder.'
          );
        },
      });
  }

  // Final Permission Grant Assignment
  onAssignWorkspace(): void {
    const target = this.selectedTarget();
    const currentTeam = this.team();
    const permLevel = this.selectedPermissionLevel();

    if (!target || !currentTeam) {
      this.errorMessage.set('Please select a target workspace resource.');
      return;
    }

    const request: PermissionRequestDto = {
      permissionType: 'ALLOW',
      permissionLevel: permLevel,
      recipientType: 'GROUP',
      targetGroupId: currentTeam.id,
    };

    if (target.type === 'DOCUMENT' && target.documentId) {
      request.documentId = target.documentId;
    } else if (target.folderId) {
      request.folderId = target.folderId;
    }

    this.isSaving.set(true);
    this.errorMessage.set('');

    this.permissionApiService.grantPermission(request).subscribe({
      next: (permissionResponse) => {
        this.isSaving.set(false);
        this.notificationService.success(
          `Workspace "${target.name}" allocated to team with ${permLevel} permission.`
        );
        this.workspaceAllocated.emit(permissionResponse);
        this.onClose();
      },
      error: (err) => {
        this.isSaving.set(false);
        this.errorMessage.set(
          err?.error?.message || 'Failed to assign team workspace permission.'
        );
      },
    });
  }

  // Filtering helpers
  filterFolders(folders: TreeNode[]): TreeNode[] {
    const query = this.searchQuery().toLowerCase().trim();
    if (!query) return folders;
    return folders.filter((f) => f.name.toLowerCase().includes(query));
  }

  filterDocuments(docs: Array<{ id: number; name: string; fileSizeBytes?: number }>): Array<{ id: number; name: string; fileSizeBytes?: number }> {
    const query = this.searchQuery().toLowerCase().trim();
    if (!query) return docs;
    return docs.filter((d) => d.name.toLowerCase().includes(query));
  }

  formatFileSize(bytes?: number): string {
    if (!bytes) return '';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  }
}
