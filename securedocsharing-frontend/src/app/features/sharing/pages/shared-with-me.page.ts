import {
  Component,
  ChangeDetectionStrategy,
  OnInit,
  signal,
  computed,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DropdownComponent } from '../../../shared/ui/dropdown/dropdown.component';

import {
  SharedItem,
  SharedWithMeResponse,
  SharingResourceTypeFilter,
  SharingSourceFilter,
  SharingLevelFilter,
  SharingViewMode,
} from '../models/sharing.model';
import { SharingApiService } from '../services/sharing-api.service';
import { SourceBadgeComponent } from '../components/source-badge.component';
import { MultiGrantPopoverComponent } from '../components/multi-grant-popover.component';
import {
  DrawerItemAdapter,
  sharedItemToDrawerItem,
  getSharedItemIcon,
  formatBytes,
  formatSharingDate,
} from '../utils/sharing-adapter.util';

import { ItemDetailDrawerComponent } from '../../explorer/components/item-detail-drawer.component';
import { FilePreviewModalComponent } from '../../explorer/components/file-preview-modal.component';
import { SharedFolderWindowComponent, SharedFolderRoot } from '../components/shared-folder-window.component';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { buildCanonicalRoute } from '../../../core/models/feature-route.model';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';
import {
  SkeletonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
} from '../../../shared/ui';

@Component({
  selector: 'app-shared-with-me-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IconComponent,
    DropdownComponent,
    SkeletonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    SourceBadgeComponent,
    MultiGrantPopoverComponent,
    ItemDetailDrawerComponent,
    FilePreviewModalComponent,
    SharedFolderWindowComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="sharing-page-container">
      <!-- ── Page Header ── -->
      <header class="page-header">
        <div class="header-main">
          <div class="header-icon-box">
            <app-icon name="inbox" [size]="24" />
          </div>
          <div class="header-titles">
            <div class="title-with-pill">
              <h1 class="page-title">Shared With Me</h1>
              <span class="count-pill">{{ totalCount() }} items</span>
            </div>
            <p class="page-subtitle">
              Resources explicitly shared with you directly, or through your teams, organization, or collaborations.
            </p>
          </div>
        </div>

        <div class="header-actions">
          <!-- View Toggle -->
          <div class="view-toggle" role="radiogroup" aria-label="View format">
            <button
              type="button"
              class="toggle-btn"
              [class.active]="viewMode() === 'table'"
              (click)="viewMode.set('table')"
              title="Table View"
              aria-label="Table View"
            >
              <app-icon name="clipboard-list" [size]="16" />
            </button>
            <button
              type="button"
              class="toggle-btn"
              [class.active]="viewMode() === 'cards'"
              (click)="viewMode.set('cards')"
              title="Cards Grid View"
              aria-label="Cards Grid View"
            >
              <app-icon name="layers" [size]="16" />
            </button>
          </div>

          <!-- Refresh Button -->
          <button
            type="button"
            class="refresh-btn"
            (click)="loadData()"
            [disabled]="isLoading()"
            title="Refresh list"
            aria-label="Refresh shared items"
          >
            <app-icon name="refresh-cw" [size]="14" class="refresh-icon" [class.spin]="isLoading()" />
            <span>Refresh</span>
          </button>
        </div>
      </header>

      <!-- ── Filter & Search Toolbar ── -->
      <section class="toolbar-section">
        <!-- Search Input -->
        <div class="search-box">
          <app-icon name="search" [size]="16" class="search-icon" />
          <input
            type="text"
            class="search-input"
            placeholder="Search by resource, grantor name or email..."
            [ngModel]="searchQuery()"
            (ngModelChange)="searchQuery.set($event)"
          />
          @if (searchQuery()) {
            <button
              type="button"
              class="clear-search-btn"
              (click)="searchQuery.set('')"
              aria-label="Clear search"
            >
              <app-icon name="x" [size]="14" />
            </button>
          }
        </div>

        <!-- Filter Chips Row -->
        <div class="filters-row">
          <!-- Resource Type Chips -->
          <div class="chip-group" role="group" aria-label="Resource type filter">
            <button
              type="button"
              class="filter-chip"
              [class.active]="resourceTypeFilter() === 'ALL'"
              (click)="resourceTypeFilter.set('ALL')"
            >
              All Types
            </button>
            <button
              type="button"
              class="filter-chip"
              [class.active]="resourceTypeFilter() === 'FOLDERS'"
              (click)="resourceTypeFilter.set('FOLDERS')"
            >
              <app-icon name="folder" [size]="13" />
              Folders ({{ folderCount() }})
            </button>
            <button
              type="button"
              class="filter-chip"
              [class.active]="resourceTypeFilter() === 'DOCUMENTS'"
              (click)="resourceTypeFilter.set('DOCUMENTS')"
            >
              <app-icon name="file-text" [size]="13" />
              Documents ({{ documentCount() }})
            </button>
          </div>

          <div class="filter-separator"></div>

          <!-- Source Channel Filter -->
          <sd-dropdown class="filter-dropdown-host" placement="bottom-start">
            <button dropdown-trigger type="button" class="filter-select-trigger" aria-label="Filter by sharing channel">
              <span class="select-label-text">{{ getSourceFilterLabel() }}</span>
              <app-icon name="chevron-down" [size]="13" class="select-arrow" />
            </button>
            <div dropdown-menu class="filter-select-menu">
              <button type="button" class="filter-select-option" [class.selected]="sourceFilter() === 'ALL'" (click)="sourceFilter.set('ALL')">
                <span>All Channels</span>
                @if (sourceFilter() === 'ALL') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="sourceFilter() === 'DIRECT'" (click)="sourceFilter.set('DIRECT')">
                <span>Direct Share</span>
                @if (sourceFilter() === 'DIRECT') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="sourceFilter() === 'TEAM'" (click)="sourceFilter.set('TEAM')">
                <span>Team Share</span>
                @if (sourceFilter() === 'TEAM') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="sourceFilter() === 'ORGANIZATION'" (click)="sourceFilter.set('ORGANIZATION')">
                <span>Organization Share</span>
                @if (sourceFilter() === 'ORGANIZATION') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="sourceFilter() === 'COLLABORATION'" (click)="sourceFilter.set('COLLABORATION')">
                <span>Collaboration Share</span>
                @if (sourceFilter() === 'COLLABORATION') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
            </div>
          </sd-dropdown>

          <!-- Permission Level Filter -->
          <sd-dropdown class="filter-dropdown-host" placement="bottom-start">
            <button dropdown-trigger type="button" class="filter-select-trigger" aria-label="Filter by permission level">
              <span class="select-label-text">{{ getLevelFilterLabel() }}</span>
              <app-icon name="chevron-down" [size]="13" class="select-arrow" />
            </button>
            <div dropdown-menu class="filter-select-menu">
              <button type="button" class="filter-select-option" [class.selected]="levelFilter() === 'ALL'" (click)="levelFilter.set('ALL')">
                <span>All Permissions</span>
                @if (levelFilter() === 'ALL') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="levelFilter() === 'EDIT'" (click)="levelFilter.set('EDIT')">
                <span>Can Edit</span>
                @if (levelFilter() === 'EDIT') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="levelFilter() === 'DOWNLOAD'" (click)="levelFilter.set('DOWNLOAD')">
                <span>Can Download</span>
                @if (levelFilter() === 'DOWNLOAD') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="levelFilter() === 'VIEW'" (click)="levelFilter.set('VIEW')">
                <span>View Only</span>
                @if (levelFilter() === 'VIEW') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
            </div>
          </sd-dropdown>

          <!-- Clear All Filters -->
          @if (hasActiveFilters()) {
            <button
              type="button"
              class="clear-all-btn"
              (click)="clearFilters()"
            >
              <app-icon name="x" [size]="13" />
              <span>Reset Filters</span>
            </button>
          }
        </div>
      </section>

      <!-- ── Content Area ── -->
      <main class="main-content">
        <!-- 1. Loading Skeleton -->
        @if (isLoading()) {
          <div class="skeleton-container" aria-label="Loading shared items">
            <div class="skeleton-header-row">
              <sd-skeleton width="25%" height="24px" />
              <sd-skeleton width="15%" height="24px" />
              <sd-skeleton width="20%" height="24px" />
              <sd-skeleton width="15%" height="24px" />
              <sd-skeleton width="15%" height="24px" />
            </div>
            @for (i of [1, 2, 3, 4, 5]; track i) {
              <div class="skeleton-table-row">
                <sd-skeleton width="30%" height="18px" />
                <sd-skeleton width="18%" height="18px" />
                <sd-skeleton width="16%" height="18px" />
                <sd-skeleton width="12%" height="18px" />
                <sd-skeleton width="14%" height="18px" />
              </div>
            }
          </div>
        }

        <!-- 2. Error State -->
        @else if (errorMessage()) {
          <div class="error-container">
            <sd-error-state
              title="Unable to load shared items"
              [message]="errorMessage()!"
              [showRetry]="true"
              (retry)="loadData()"
            />
          </div>
        }

        <!-- 3. Empty State (No items or filtered out) -->
        @else if (filteredItems().length === 0) {
          @if (hasActiveFilters()) {
            <sd-empty-state
              title="No matching shared items found"
              description="Try adjusting or resetting your search keywords and filter criteria."
            >
              <app-icon icon name="search" [size]="24" />
              <div actions class="empty-actions">
                <button type="button" class="btn btn-secondary" (click)="clearFilters()">
                  Reset All Filters
                </button>
              </div>
            </sd-empty-state>
          } @else {
            <sd-empty-state
              title="Nothing shared with you yet"
              description="When team members, organizations, or collaborators share folders or files with you, they will appear in this space."
            >
              <app-icon icon name="folder-search" [size]="24" />
              <div actions class="empty-actions">
                <button type="button" class="btn btn-secondary" (click)="loadData()">
                  Check Again
                </button>
              </div>
            </sd-empty-state>
          }
        }

        <!-- 4. Table View -->
        @else if (viewMode() === 'table') {
          <div class="table-card">
            <div class="table-scroll-wrapper">
              <table class="sharing-table">
                <thead>
                  <tr>
                    <th scope="col" class="th-resource">Resource</th>
                    <th scope="col" class="th-granter">Shared By</th>
                    <th scope="col" class="th-channel">Channel</th>
                    <th scope="col" class="th-access">Access</th>
                    <th scope="col" class="th-origin">Origin</th>
                    <th scope="col" class="th-date">Date</th>
                    <th scope="col" class="th-actions text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of filteredItems(); track item.permissionId + '-' + item.resourceId) {
                    <tr class="table-row" (click)="onRowClick(item)">
                      <!-- Resource -->
                      <td class="td-resource">
                        <div class="resource-cell">
                          <div class="resource-icon-box" [class.folder-box]="item.resourceType === 'FOLDER'">
                            <app-icon [name]="getItemIcon(item)" [size]="18" />
                          </div>
                          <div class="resource-details">
                            <span class="resource-name" [title]="item.name">{{ item.name }}</span>
                            <span class="resource-meta">
                              {{ item.resourceType === 'FOLDER' ? 'Folder' : formatItemSize(item.fileSize) }}
                            </span>
                          </div>
                        </div>
                      </td>

                      <!-- Shared By -->
                      <td class="td-granter">
                        <div class="granter-cell">
                          <div class="avatar-circle">
                            {{ getInitials(item.grantedByName || item.grantedByUserEmail) }}
                          </div>
                          <div class="granter-details">
                            <span class="granter-name">{{ item.grantedByName || 'Direct User' }}</span>
                            @if (item.grantedByUserEmail) {
                              <span class="granter-email">{{ item.grantedByUserEmail }}</span>
                            }
                          </div>
                        </div>
                      </td>

                      <!-- Channel -->
                      <td class="td-channel">
                        <app-source-badge
                          [item]="item"
                          (multiGrantClicked)="openProvenance($event)"
                        />
                      </td>

                      <!-- Access -->
                      <td class="td-access">
                        <span class="access-badge" [class]="getLevelClass(item.permissionLevel)">
                          <app-icon [name]="getLevelIcon(item.permissionLevel)" [size]="12" />
                          <span>{{ item.permissionLevel }}</span>
                        </span>
                      </td>

                      <!-- Origin -->
                      <td class="td-origin">
                        <span class="origin-tag" [title]="getOriginTooltip(item)">
                          {{ getOriginLabel(item) }}
                        </span>
                      </td>

                      <!-- Date -->
                      <td class="td-date">
                        <span class="date-text">{{ formatSharingDate(item.grantedAt) }}</span>
                      </td>

                      <!-- Actions -->
                      <td class="td-actions text-right" (click)="$event.stopPropagation()">
                        <div class="row-actions">
                          <button
                            type="button"
                            class="action-btn action-btn-open"
                            (click)="openItem(item)"
                            [title]="item.resourceType === 'FOLDER' ? 'Open Folder' : 'Preview Document'"
                          >
                            <app-icon [name]="item.resourceType === 'FOLDER' ? 'folder-open' : 'eye'" [size]="14" />
                            <span>Open</span>
                          </button>

                          <button
                            type="button"
                            class="action-btn action-btn-details"
                            (click)="openDetails(item)"
                            title="Inspect Details & Metadata"
                            aria-label="Inspect Details and Metadata"
                          >
                            <app-icon name="info" [size]="14" />
                            <span>Details</span>
                          </button>

                          <button
                            type="button"
                            class="action-btn action-btn-access"
                            (click)="openDetailsAndAccess(item)"
                            title="View Your Access"
                            aria-label="View Your Access"
                          >
                            <app-icon name="shield" [size]="14" />
                            <span>Access</span>
                          </button>
                        </div>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        }

        <!-- 5. Cards Grid View -->
        @else {
          <div class="cards-grid">
            @for (item of filteredItems(); track item.permissionId + '-' + item.resourceId) {
              <div class="sharing-card" (click)="onRowClick(item)">
                <div class="card-header">
                  <div class="card-resource-lead">
                    <div class="resource-icon-box" [class.folder-box]="item.resourceType === 'FOLDER'">
                      <app-icon [name]="getItemIcon(item)" [size]="18" />
                    </div>
                    <div class="card-title-box">
                      <span class="card-item-name" [title]="item.name">{{ item.name }}</span>
                      <span class="card-item-sub">
                        {{ item.resourceType === 'FOLDER' ? 'Folder' : formatItemSize(item.fileSize) }}
                      </span>
                    </div>
                  </div>
                  <span class="access-badge" [class]="getLevelClass(item.permissionLevel)">
                    <app-icon [name]="getLevelIcon(item.permissionLevel)" [size]="11" />
                    <span>{{ item.permissionLevel }}</span>
                  </span>
                </div>

                <div class="card-body">
                  <div class="card-info-row">
                    <span class="info-label">Channel</span>
                    <app-source-badge
                      [item]="item"
                      (multiGrantClicked)="openProvenance($event)"
                    />
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Shared By</span>
                    <span class="info-value">{{ item.grantedByName || item.grantedByUserEmail || 'User' }}</span>
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Origin</span>
                    <span class="info-value origin-text">{{ getOriginLabel(item) }}</span>
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Shared On</span>
                    <span class="info-value">{{ formatSharingDate(item.grantedAt) }}</span>
                  </div>
                </div>

                <div class="card-footer" (click)="$event.stopPropagation()">
                  <button
                    type="button"
                    class="action-btn action-btn-open flex-1"
                    (click)="openItem(item)"
                  >
                    <app-icon [name]="item.resourceType === 'FOLDER' ? 'folder-open' : 'eye'" [size]="14" />
                    <span>Open</span>
                  </button>
                  <button
                    type="button"
                    class="action-btn action-btn-details flex-1"
                    (click)="openDetails(item)"
                    title="Inspect Details & Metadata"
                    aria-label="Inspect Details and Metadata"
                  >
                    <app-icon name="info" [size]="14" />
                    <span>Details</span>
                  </button>
                  <button
                    type="button"
                    class="action-btn action-btn-access flex-1"
                    (click)="openDetailsAndAccess(item)"
                    title="View Your Access"
                    aria-label="View Your Access"
                  >
                    <app-icon name="shield" [size]="14" />
                    <span>Access</span>
                  </button>
                </div>
              </div>
            }
          </div>
        }
      </main>

      <!-- ── Modals & Drawers ── -->

      <!-- Item Detail Drawer -->
      <app-item-detail-drawer
        [isOpen]="isDrawerOpen()"
        [item]="selectedItemForDrawer()"
        (closed)="closeDrawer()"
        (openPreview)="onOpenPreviewFromDrawer($event)"
        (download)="onDownloadDocFromDrawer($event)"
        (downloadFolder)="onDownloadFolderFromDrawer($event)"
      />

      <!-- Multi-Grant Provenance Popover -->
      <app-multi-grant-popover
        [open]="isProvenanceOpen()"
        [item]="selectedItemForProvenance()"
        (closed)="isProvenanceOpen.set(false)"
        (manageAccess)="openDetailsAndAccess($event)"
      />

      <!-- File Preview Modal -->
      <app-file-preview-modal
        [isOpen]="isPreviewOpen()"
        [item]="previewItem()"
        (closed)="isPreviewOpen.set(false)"
        (download)="onDownloadDocFromDrawer($event)"
        (openDetails)="onOpenDetailsFromPreview($event)"
      />

      <!-- Shared Folder Window -->
      <app-shared-folder-window
        [isOpen]="isFolderDrawerOpen()"
        [rootFolder]="selectedFolderForDrawer()"
        (closed)="isFolderDrawerOpen.set(false)"
      />
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      min-height: 100%;
    }

    .sharing-page-container {
      max-width: 1440px;
      margin: 0 auto;
      padding: 24px 28px;
      display: flex;
      flex-direction: column;
      gap: 20px;
    }

    /* ── Header ── */
    .page-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 20px;
      flex-wrap: wrap;
    }

    .header-main {
      display: flex;
      align-items: flex-start;
      gap: 16px;
    }

    .header-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: var(--sd-color-primary-subtle, #fff7ed);
      color: var(--sd-color-primary, #f97316);
      border: 1px solid var(--sd-color-primary-border, #fed7aa);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .header-titles {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .title-with-pill {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .page-title {
      font-size: 22px;
      font-weight: 700;
      color: #0f172a;
      letter-spacing: -0.02em;
      margin: 0;
      line-height: 1.2;
    }

    .count-pill {
      padding: 2px 8px;
      border-radius: 9999px;
      background: #f1f5f9;
      color: #475569;
      font-size: 12px;
      font-weight: 600;
    }

    .page-subtitle {
      font-size: 13.5px;
      color: #64748b;
      margin: 0;
      line-height: 1.4;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .view-toggle {
      display: inline-flex;
      background: #f1f5f9;
      padding: 3px;
      border-radius: 8px;
      border: 1px solid #e2e8f0;
    }

    .toggle-btn {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      border-radius: 6px;
      border: 1px solid transparent;
      background: transparent;
      color: #64748b;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .toggle-btn:hover:not(.active) {
      color: #0f172a;
      background-color: rgba(255, 255, 255, 0.5);
    }

    .toggle-btn.active {
      background: #ffffff;
      color: var(--color-accent, #f97316);
      border-color: #cbd5e1;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }

    .refresh-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      background: #ffffff;
      border: 1px solid #cbd5e1;
      border-radius: 8px;
      color: #334155;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 38px;
    }

    .refresh-btn:hover:not(:disabled) {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .refresh-btn:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .spin {
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      100% { transform: rotate(360deg); }
    }

    /* ── Toolbar ── */
    .toolbar-section {
      display: flex;
      flex-direction: column;
      gap: 12px;
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 14px 16px;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
    }

    .search-box {
      position: relative;
      width: 100%;
    }

    .search-icon {
      position: absolute;
      left: 12px;
      top: 50%;
      transform: translateY(-50%);
      color: #94a3b8;
      pointer-events: none;
    }

    .search-input {
      width: 100%;
      height: 38px;
      padding: 0 36px 0 38px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      font-size: 13.5px;
      color: #0f172a;
      background: #f8fafc;
      transition: all 0.15s ease;
      box-sizing: border-box;
    }

    .search-input:focus {
      outline: none;
      background: #ffffff;
      border-color: var(--sd-color-brand-focus, #fb923c);
      box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.15);
    }

    .clear-search-btn {
      position: absolute;
      right: 10px;
      top: 50%;
      transform: translateY(-50%);
      display: flex;
      align-items: center;
      justify-content: center;
      width: 22px;
      height: 22px;
      border-radius: 50%;
      border: none;
      background: #e2e8f0;
      color: #475569;
      cursor: pointer;
    }

    .clear-search-btn:hover {
      background: #cbd5e1;
      color: #0f172a;
    }

    .filters-row {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .chip-group {
      display: inline-flex;
      gap: 6px;
    }

    .filter-chip {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 5px 10px;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
      background: #f8fafc;
      color: #475569;
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 32px;
    }

    .filter-chip:hover {
      background: #f1f5f9;
      border-color: #cbd5e1;
      color: #0f172a;
    }

    .filter-chip.active {
      background: rgba(249, 115, 22, 0.08);
      border-color: rgba(249, 115, 22, 0.3);
      color: var(--sd-color-text-brand, #ea580c);
      font-weight: 600;
    }

    .filter-separator {
      width: 1px;
      height: 20px;
      background: #e2e8f0;
      margin: 0 2px;
    }

    .filter-dropdown-host {
      display: inline-block;
      width: auto;
    }

    ::ng-deep .filter-dropdown-host,
    ::ng-deep .filter-dropdown-host .sd-dropdown-container {
      display: inline-block;
      width: auto;
    }

    .filter-select-trigger {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 10px 6px 12px;
      border: 1px solid #cbd5e1;
      border-radius: 6px;
      background: #ffffff;
      color: #334155;
      font-size: 12.5px;
      font-weight: 500;
      cursor: pointer;
      min-height: 32px;
      outline: none;
      transition: border-color 0.15s, box-shadow 0.15s;
      white-space: nowrap;
      font-family: inherit;
    }

    .filter-select-trigger:hover {
      border-color: #94a3b8;
      background: #f8fafc;
    }

    .filter-select-trigger[aria-expanded="true"] {
      border-color: var(--sd-color-brand-focus, #fb923c);
      box-shadow: 0 0 0 2px rgba(251, 146, 60, 0.15);
    }

    .filter-select-trigger[aria-expanded="true"] .select-arrow {
      transform: rotate(180deg);
    }

    .select-arrow {
      color: #64748b;
      transition: transform 0.15s ease;
      flex-shrink: 0;
    }

    ::ng-deep .filter-dropdown-host .sd-dropdown-menu {
      min-width: 180px;
    }

    .filter-select-menu {
      display: flex;
      flex-direction: column;
      gap: 2px;
      width: 100%;
    }

    .filter-select-option {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      width: 100%;
      padding: 7px 10px;
      border: none;
      background: transparent;
      border-radius: 6px;
      cursor: pointer;
      text-align: left;
      font-size: 13px;
      color: #334155;
      font-family: inherit;
      transition: background 0.1s;
    }

    .filter-select-option:hover {
      background: #f1f5f9;
    }

    .filter-select-option.selected {
      background: rgba(249, 115, 22, 0.07);
      color: var(--sd-color-primary, #f97316);
      font-weight: 600;
    }

    .option-check {
      color: var(--sd-color-primary, #f97316);
      flex-shrink: 0;
    }

    .clear-all-btn {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 5px 10px;
      background: transparent;
      border: 1px dashed #cbd5e1;
      border-radius: 6px;
      color: #64748b;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      margin-left: auto;
      min-height: 32px;
    }

    .clear-all-btn:hover {
      background: #fee2e2;
      border-color: #fca5a5;
      color: #b91c1c;
    }

    /* ── Table Layout ── */
    .table-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      overflow: hidden;
    }

    .table-scroll-wrapper {
      overflow-x: auto;
      width: 100%;
    }

    .sharing-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 13px;
    }

    .sharing-table thead th {
      background: #f8fafc;
      color: #475569;
      font-weight: 600;
      font-size: 11.5px;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      padding: 12px 16px;
      border-bottom: 1px solid #e2e8f0;
      white-space: nowrap;
    }

    .table-row {
      border-bottom: 1px solid #f1f5f9;
      transition: background-color 0.12s ease;
      cursor: pointer;
    }

    .table-row:hover {
      background-color: #f8fafc;
    }

    .table-row td {
      padding: 12px 16px;
      vertical-align: middle;
      color: #334155;
    }

    /* Cell Styles */
    .resource-cell {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 200px;
    }

    .resource-icon-box {
      width: 34px;
      height: 34px;
      border-radius: 8px;
      background: #f1f5f9;
      color: #475569;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .resource-icon-box.folder-box {
      background: #fef3c7;
      color: #d97706;
    }

    .resource-details {
      display: flex;
      flex-direction: column;
      gap: 1px;
      overflow: hidden;
    }

    .resource-name {
      font-weight: 600;
      color: #0f172a;
      max-width: 260px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .resource-meta {
      font-size: 11.5px;
      color: #64748b;
    }

    .granter-cell {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 160px;
    }

    .avatar-circle {
      width: 28px;
      height: 28px;
      border-radius: 50%;
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #e2e8f0;
      font-size: 11px;
      font-weight: 600;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      text-transform: uppercase;
    }

    .granter-details {
      display: flex;
      flex-direction: column;
      gap: 1px;
      overflow: hidden;
    }

    .granter-name {
      font-weight: 500;
      color: #1e293b;
      font-size: 12.5px;
    }

    .granter-email {
      font-size: 11px;
      color: #64748b;
      max-width: 160px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .access-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px 8px;
      border-radius: 9999px;
      font-size: 11px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .access-edit {
      background: #fef3c7;
      color: #92400e;
      border: 1px solid #fde68a;
    }

    .access-download {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
    }

    .access-view {
      background: #f1f5f9;
      color: #334155;
      border: 1px solid #cbd5e1;
    }

    .origin-tag {
      font-size: 12px;
      color: #475569;
      background: #f8fafc;
      padding: 2px 8px;
      border-radius: 4px;
      border: 1px solid #e2e8f0;
      white-space: nowrap;
      display: inline-block;
      max-width: 140px;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .date-text {
      font-size: 12px;
      color: #64748b;
      white-space: nowrap;
    }

    .row-actions {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 6px;
    }

    .action-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 5px;
      padding: 6px 10px;
      border-radius: 6px;
      font-size: 12px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 32px;
      border: 1px solid transparent;
      user-select: none;
    }

    .action-btn-open {
      background: #ffffff;
      color: #334155;
      border-color: #cbd5e1;
    }

    .action-btn-open:hover {
      background: rgba(249, 115, 22, 0.06);
      color: var(--color-accent, #f97316);
      border-color: rgba(249, 115, 22, 0.3);
    }

    .action-btn-details,
    .action-btn-access,
    .action-btn-security {
      background: #f8fafc;
      color: #475569;
      border-color: #cbd5e1;
    }

    .action-btn-details:hover,
    .action-btn-access:hover,
    .action-btn-security:hover {
      background: #f1f5f9;
      color: #0f172a;
      border-color: #94a3b8;
    }

    /* ── Cards Grid View ── */
    .cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 16px;
    }

    .sharing-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 14px;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .sharing-card:hover {
      border-color: #cbd5e1;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
      transform: translateY(-1px);
    }

    .card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 12px;
    }

    .card-resource-lead {
      display: flex;
      align-items: center;
      gap: 10px;
      overflow: hidden;
    }

    .card-title-box {
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    .card-item-name {
      font-size: 14px;
      font-weight: 600;
      color: #0f172a;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-item-sub {
      font-size: 11.5px;
      color: #64748b;
    }

    .card-body {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 10px 12px;
      background: #f8fafc;
      border-radius: 8px;
      border: 1px solid #f1f5f9;
      font-size: 12px;
    }

    .card-info-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .info-label {
      color: #64748b;
    }

    .info-value {
      font-weight: 500;
      color: #1e293b;
    }

    .origin-text {
      max-width: 140px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .card-footer {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-top: auto;
    }

    .flex-1 {
      flex: 1;
    }

    /* ── States ── */
    .skeleton-container {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .skeleton-header-row,
    .skeleton-table-row {
      display: flex;
      gap: 16px;
      align-items: center;
    }

    .error-container {
      padding: 20px 0;
    }

    .empty-actions {
      display: flex;
      justify-content: center;
      margin-top: 14px;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 8px 16px;
      border-radius: 6px;
      font-size: 13px;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
      min-height: 36px;
    }

    .btn-secondary {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      color: #334155;
    }

    .btn-secondary:hover {
      background: #f8fafc;
      border-color: #94a3b8;
      color: #0f172a;
    }

    .text-right {
      text-align: right;
    }

    /* ── Responsive adjustments ── */
    @media (max-width: 768px) {
      .sharing-page-container {
        padding: 16px;
        gap: 16px;
      }

      .page-header {
        flex-direction: column;
        align-items: stretch;
      }

      .header-actions {
        justify-content: space-between;
      }

      .filters-row {
        flex-direction: column;
        align-items: stretch;
      }

      .filter-separator {
        display: none;
      }

      .chip-group {
        overflow-x: auto;
        padding-bottom: 4px;
      }

      .action-btn {
        min-height: 44px;
        padding: 8px 12px;
      }
    }
  `],
})
export class SharedWithMePageComponent implements OnInit {
  private readonly sharingApiService = inject(SharingApiService);
  private readonly documentApiService = inject(DocumentApiService);
  private readonly folderApiService = inject(FolderApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);

  // ── State Signals ──
  isLoading = signal<boolean>(true);
  errorMessage = signal<string | null>(null);
  sharedFolders = signal<SharedItem[]>([]);
  sharedDocuments = signal<SharedItem[]>([]);

  // ── Filter Signals ──
  searchQuery = signal<string>('');
  resourceTypeFilter = signal<SharingResourceTypeFilter>('ALL');
  sourceFilter = signal<SharingSourceFilter>('ALL');
  levelFilter = signal<SharingLevelFilter>('ALL');
  viewMode = signal<SharingViewMode>('table');

  // ── Modals / Drawer State ──
  selectedItemForProvenance = signal<SharedItem | null>(null);
  isProvenanceOpen = signal<boolean>(false);

  selectedItemForDrawer = signal<DrawerItemAdapter | null>(null);
  isDrawerOpen = signal<boolean>(false);

  previewItem = signal<{ type: 'folder' | 'document'; data: any } | null>(null);
  isPreviewOpen = signal<boolean>(false);

  isFolderDrawerOpen = signal<boolean>(false);
  selectedFolderForDrawer = signal<SharedFolderRoot | null>(null);

  // ── Computeds ──
  readonly allItems = computed<SharedItem[]>(() => {
    return [...this.sharedFolders(), ...this.sharedDocuments()];
  });

  readonly folderCount = computed(() => this.sharedFolders().length);
  readonly documentCount = computed(() => this.sharedDocuments().length);
  readonly totalCount = computed(() => this.allItems().length);

  readonly hasActiveFilters = computed<boolean>(() => {
    return (
      this.searchQuery().trim().length > 0 ||
      this.resourceTypeFilter() !== 'ALL' ||
      this.sourceFilter() !== 'ALL' ||
      this.levelFilter() !== 'ALL'
    );
  });

  readonly filteredItems = computed<SharedItem[]>(() => {
    let items = this.allItems();

    // 1. Resource Type filter
    const typeFilter = this.resourceTypeFilter();
    if (typeFilter === 'FOLDERS') {
      items = items.filter((i) => i.resourceType === 'FOLDER');
    } else if (typeFilter === 'DOCUMENTS') {
      items = items.filter((i) => i.resourceType === 'DOCUMENT');
    }

    // 2. Source Channel filter
    const srcFilter = this.sourceFilter();
    if (srcFilter !== 'ALL') {
      items = items.filter((i) => {
        const lbl = (i.sourceLabel || '').toUpperCase();
        return lbl.includes(srcFilter);
      });
    }

    // 3. Permission Level filter
    const lvlFilter = this.levelFilter();
    if (lvlFilter !== 'ALL') {
      items = items.filter((i) => i.permissionLevel === lvlFilter);
    }

    // 4. Search query
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      items = items.filter((i) => {
        const nameMatch = (i.name || '').toLowerCase().includes(q);
        const granterNameMatch = (i.grantedByName || '').toLowerCase().includes(q);
        const granterEmailMatch = (i.grantedByUserEmail || '').toLowerCase().includes(q);
        const sourceMatch = (i.sourceLabel || '').toLowerCase().includes(q);
        const orgMatch = (i.ownerOrgName || i.ownerOrgCode || '').toLowerCase().includes(q);
        return nameMatch || granterNameMatch || granterEmailMatch || sourceMatch || orgMatch;
      });
    }

    return items;
  });

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.sharingApiService.getSharedWithMe().subscribe({
      next: (res: SharedWithMeResponse) => {
        this.sharedFolders.set(res.sharedFolders || []);
        this.sharedDocuments.set(res.sharedDocuments || []);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('[SharedWithMe] Failed to load shared items:', err);
        this.isLoading.set(false);
        this.errorMessage.set(
          err?.userFriendlyMessage || err?.message || err?.error?.message || 'Failed to load shared resources. Please check your connection and try again.'
        );
      },
    });
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.resourceTypeFilter.set('ALL');
    this.sourceFilter.set('ALL');
    this.levelFilter.set('ALL');
  }

  getSourceFilterLabel(): string {
    const map: Record<string, string> = {
      ALL: 'All Channels',
      DIRECT: 'Direct Share',
      TEAM: 'Team Share',
      ORGANIZATION: 'Organization Share',
      COLLABORATION: 'Collaboration Share',
    };
    return map[this.sourceFilter()] ?? 'All Channels';
  }

  getLevelFilterLabel(): string {
    const map: Record<string, string> = {
      ALL: 'All Permissions',
      EDIT: 'Can Edit',
      DOWNLOAD: 'Can Download',
      VIEW: 'View Only',
    };
    return map[this.levelFilter()] ?? 'All Permissions';
  }

  onRowClick(item: SharedItem): void {
    this.openItem(item);
  }

  openItem(item: SharedItem): void {
    if (item.resourceType === 'FOLDER') {
      this.selectedFolderForDrawer.set({ id: item.resourceId, name: item.name });
      this.isFolderDrawerOpen.set(true);
    } else {
      // Document preview directly
      const drawerItem = sharedItemToDrawerItem(item, 'overview');
      this.previewItem.set({
        type: 'document',
        data: drawerItem.data,
      });
      this.isPreviewOpen.set(true);
    }
  }

  openDetails(item: SharedItem): void {
    const drawerItem = sharedItemToDrawerItem(item, 'overview');
    this.selectedItemForDrawer.set(drawerItem);
    this.isDrawerOpen.set(true);
  }

  openDetailsAndAccess(item: SharedItem): void {
    const drawerItem = sharedItemToDrawerItem(item, 'access');
    this.selectedItemForDrawer.set(drawerItem);
    this.isDrawerOpen.set(true);
  }

  navigateToFolder(item: SharedItem): void {
    const wsId = this.workspaceContextStore.activeWorkspace()?.id;
    const targetRoute = buildCanonicalRoute(
      'explorer',
      null,
      wsId
    );
    this.router.navigate(targetRoute, {
      queryParams: { folderId: item.resourceId },
    });
  }

  openProvenance(item: SharedItem): void {
    this.selectedItemForProvenance.set(item);
    this.isProvenanceOpen.set(true);
  }

  closeDrawer(): void {
    this.isDrawerOpen.set(false);
    this.selectedItemForDrawer.set(null);
  }

  onOpenPreviewFromDrawer(event: { type: 'folder' | 'document'; data: any }): void {
    this.closeDrawer();
    this.previewItem.set(event);
    this.isPreviewOpen.set(true);
  }

  onOpenDetailsFromPreview(event: { type: 'folder' | 'document'; data: any }): void {
    this.isPreviewOpen.set(false);
    this.selectedItemForDrawer.set({
      type: event.type,
      data: event.data,
      tab: 'overview',
    });
    this.isDrawerOpen.set(true);
  }

  onDownloadDocFromDrawer(data: any): void {
    const docId = data?.id;
    if (!docId) return;

    this.notificationService.info('Fetching secure download link...');
    this.documentApiService.getDownloadUrl(Number(docId)).subscribe({
      next: (res) => {
        if (res?.downloadUrl) {
          window.open(res.downloadUrl, '_blank');
        } else {
          this.notificationService.error('Download link not provided.');
        }
      },
      error: () => this.notificationService.error('Failed to initiate download.'),
    });
  }

  onDownloadFolderFromDrawer(folderId: number): void {
    if (!folderId) return;
    this.notificationService.info('Preparing folder archive download...');
    this.folderApiService.downloadFolder(folderId).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `shared_folder_${folderId}.zip`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: () => this.notificationService.error('Failed to download folder archive.'),
    });
  }

  // ── Presentation Helpers ──
  getItemIcon(item: SharedItem): IconName {
    return getSharedItemIcon(item);
  }

  formatItemSize(bytes?: number | null): string {
    return formatBytes(bytes);
  }

  formatSharingDate(dateStr?: string | null): string {
    return formatSharingDate(dateStr);
  }

  getInitials(nameOrEmail?: string | null): string {
    if (!nameOrEmail) return 'U';
    const clean = nameOrEmail.trim();
    if (clean.includes('@')) {
      return clean.charAt(0).toUpperCase();
    }
    const parts = clean.split(' ').filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return clean.slice(0, 2).toUpperCase();
  }

  getLevelClass(level: string): string {
    if (level === 'EDIT' || level === 'ADMIN') return 'access-edit';
    if (level === 'DOWNLOAD') return 'access-download';
    return 'access-view';
  }

  getLevelIcon(level: string): IconName {
    if (level === 'EDIT' || level === 'ADMIN') return 'edit-3';
    if (level === 'DOWNLOAD') return 'download';
    return 'eye';
  }

  getOriginLabel(item: SharedItem): string {
    if (item.ownerOrgName) return item.ownerOrgName;
    if (item.ownerOrgCode) return item.ownerOrgCode;
    if (item.workspaceName) return item.workspaceName;
    return 'Personal Space';
  }

  getOriginTooltip(item: SharedItem): string {
    const parts: string[] = [];
    if (item.ownerOrgName) parts.push(`Org: ${item.ownerOrgName}`);
    if (item.workspaceName) parts.push(`Workspace: ${item.workspaceName}`);
    return parts.join(' • ') || 'Personal Space';
  }
}
