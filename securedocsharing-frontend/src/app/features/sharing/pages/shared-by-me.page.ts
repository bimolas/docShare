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
  SharedByMeResponse,
  SharingResourceTypeFilter,
  SharingLevelFilter,
  SharingStatusFilter,
  SharingViewMode,
} from '../models/sharing.model';
import { SharingApiService } from '../services/sharing-api.service';
import {
  DrawerItemAdapter,
  sharedItemToDrawerItem,
  getSharedItemIcon,
  formatBytes,
  formatSharingDate,
  isShareInactive,
} from '../utils/sharing-adapter.util';

import { ItemDetailDrawerComponent } from '../../explorer/components/item-detail-drawer.component';
import { FilePreviewModalComponent } from '../../explorer/components/file-preview-modal.component';
import { SharedFolderWindowComponent, SharedFolderRoot } from '../components/shared-folder-window.component';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';
import {
  SkeletonComponent,
  EmptyStateComponent,
  ErrorStateComponent,
  ConfirmationModalComponent,
} from '../../../shared/ui';

export type RecipientFilter = 'ALL' | 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION';

@Component({
  selector: 'app-shared-by-me-page',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    IconComponent,
    DropdownComponent,
    SkeletonComponent,
    EmptyStateComponent,
    ErrorStateComponent,
    ConfirmationModalComponent,
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
            <app-icon name="send" [size]="24" />
          </div>
          <div class="header-titles">
            <div class="title-with-pill">
              <h1 class="page-title">Shared By Me</h1>
              <span class="count-pill">{{ totalCount() }} shares</span>
            </div>
            <p class="page-subtitle">
              Outbound shares you have granted to individuals, teams, organizations, or external collaborations.
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
            aria-label="Refresh outbound shares"
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
            placeholder="Search by resource, recipient name or email..."
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

          <!-- Recipient Type Filter -->
          <sd-dropdown class="filter-dropdown-host" placement="bottom-start">
            <button dropdown-trigger type="button" class="filter-select-trigger" aria-label="Filter by recipient type">
              <span class="select-label-text">{{ getRecipientFilterLabel() }}</span>
              <app-icon name="chevron-down" [size]="13" class="select-arrow" />
            </button>
            <div dropdown-menu class="filter-select-menu">
              <button type="button" class="filter-select-option" [class.selected]="recipientFilter() === 'ALL'" (click)="recipientFilter.set('ALL')">
                <span>All Recipients</span>
                @if (recipientFilter() === 'ALL') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="recipientFilter() === 'USER'" (click)="recipientFilter.set('USER')">
                <span>User Shares</span>
                @if (recipientFilter() === 'USER') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="recipientFilter() === 'TEAM'" (click)="recipientFilter.set('TEAM')">
                <span>Team Shares</span>
                @if (recipientFilter() === 'TEAM') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="recipientFilter() === 'ORGANIZATION'" (click)="recipientFilter.set('ORGANIZATION')">
                <span>Organization Shares</span>
                @if (recipientFilter() === 'ORGANIZATION') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="recipientFilter() === 'COLLABORATION'" (click)="recipientFilter.set('COLLABORATION')">
                <span>Collaboration Shares</span>
                @if (recipientFilter() === 'COLLABORATION') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
            </div>
          </sd-dropdown>

          <!-- Status Filter -->
          <sd-dropdown class="filter-dropdown-host" placement="bottom-start">
            <button dropdown-trigger type="button" class="filter-select-trigger" aria-label="Filter by status">
              <span class="select-label-text">{{ getStatusFilterLabel() }}</span>
              <app-icon name="chevron-down" [size]="13" class="select-arrow" />
            </button>
            <div dropdown-menu class="filter-select-menu">
              <button type="button" class="filter-select-option" [class.selected]="statusFilter() === 'ALL'" (click)="statusFilter.set('ALL')">
                <span>All Statuses</span>
                @if (statusFilter() === 'ALL') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="statusFilter() === 'ACTIVE'" (click)="statusFilter.set('ACTIVE')">
                <span>Active Only</span>
                @if (statusFilter() === 'ACTIVE') { <app-icon name="check" [size]="14" class="option-check" /> }
              </button>
              <button type="button" class="filter-select-option" [class.selected]="statusFilter() === 'INACTIVE'" (click)="statusFilter.set('INACTIVE')">
                <span>Revoked / Expired</span>
                @if (statusFilter() === 'INACTIVE') { <app-icon name="check" [size]="14" class="option-check" /> }
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
          <div class="skeleton-container" aria-label="Loading outbound shares">
            <div class="skeleton-header-row">
              <sd-skeleton width="25%" height="24px" />
              <sd-skeleton width="20%" height="24px" />
              <sd-skeleton width="15%" height="24px" />
              <sd-skeleton width="15%" height="24px" />
              <sd-skeleton width="15%" height="24px" />
            </div>
            @for (i of [1, 2, 3, 4, 5]; track i) {
              <div class="skeleton-table-row">
                <sd-skeleton width="28%" height="18px" />
                <sd-skeleton width="22%" height="18px" />
                <sd-skeleton width="14%" height="18px" />
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
              title="Unable to load outbound shares"
              [message]="errorMessage()!"
              [showRetry]="true"
              (retry)="loadData()"
            />
          </div>
        }

        <!-- 3. Empty State (No shares or filtered out) -->
        @else if (filteredItems().length === 0) {
          @if (hasActiveFilters()) {
            <sd-empty-state
              title="No matching outbound shares found"
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
              title="No outbound shares created yet"
              description="When you share documents or folders with colleagues, teams, or external collaborators, you can audit and manage them here."
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
                    <th scope="col" class="th-recipient">Shared With (Recipient)</th>
                    <th scope="col" class="th-access">Permission Level</th>
                    <th scope="col" class="th-status">Lifecycle Status</th>
                    <th scope="col" class="th-date">Granted On</th>
                    <th scope="col" class="th-expiration">Expiration</th>
                    <th scope="col" class="th-actions text-right">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  @for (item of filteredItems(); track item.permissionId) {
                    <tr class="table-row" [class.is-inactive]="isItemInactive(item)" (click)="onRowClick(item)">
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

                      <!-- Recipient -->
                      <td class="td-recipient">
                        <div class="recipient-cell">
                          <span class="recipient-type-chip" [class]="getRecipientTypeClass(item)">
                            <app-icon [name]="getRecipientIcon(item)" [size]="12" />
                            <span>{{ getRecipientTypeLabel(item) }}</span>
                          </span>
                          <div class="recipient-details">
                            <span class="recipient-name" [title]="getRecipientName(item)">
                              {{ getRecipientName(item) }}
                            </span>
                            @if (getRecipientSubtext(item)) {
                              <span class="recipient-subtext">{{ getRecipientSubtext(item) }}</span>
                            }
                          </div>
                        </div>
                      </td>

                      <!-- Access Level -->
                      <td class="td-access">
                        <span class="access-badge" [class]="getLevelClass(item.permissionLevel)">
                          <app-icon [name]="getLevelIcon(item.permissionLevel)" [size]="12" />
                          <span>{{ item.permissionLevel }}</span>
                        </span>
                      </td>

                      <!-- Status -->
                      <td class="td-status">
                        <span class="status-badge" [class]="getStatusClass(item)">
                          <span class="status-dot"></span>
                          <span>{{ getStatusLabel(item) }}</span>
                        </span>
                      </td>

                      <!-- Granted Date -->
                      <td class="td-date">
                        <span class="date-text">{{ formatSharingDate(item.grantedAt) }}</span>
                      </td>

                      <!-- Expiration -->
                      <td class="td-expiration">
                        <span class="expiration-text" [class.expired]="isItemInactive(item)">
                          {{ item.expiresAt ? formatSharingDate(item.expiresAt) : 'Never' }}
                        </span>
                      </td>

                      <!-- Actions -->
                      <td class="td-actions text-right" (click)="$event.stopPropagation()">
                        <div class="row-actions">
                          <!-- Open -->
                          <button
                            type="button"
                            class="action-btn action-btn-open"
                            (click)="openItem(item)"
                            [title]="item.resourceType === 'FOLDER' ? 'Open Folder' : 'Preview Document'"
                          >
                            <app-icon [name]="item.resourceType === 'FOLDER' ? 'folder-open' : 'eye'" [size]="14" />
                            <span>Open</span>
                          </button>

                          <!-- Details -->
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

                          <!-- Manage Access -->
                          <button
                            type="button"
                            class="action-btn action-btn-manage"
                            (click)="openManageAccess(item)"
                            title="Manage Access in Detail Drawer"
                            aria-label="Manage Access"
                          >
                            <app-icon name="shield" [size]="14" />
                            <span>Manage</span>
                          </button>

                          <!-- Revoke (only if active) -->
                          @if (!isItemInactive(item)) {
                            <button
                              type="button"
                              class="action-btn action-btn-revoke"
                              (click)="confirmRevocation(item)"
                              title="Revoke this share"
                              aria-label="Revoke share"
                            >
                              <app-icon name="trash-2" [size]="14" />
                              <span>Revoke</span>
                            </button>
                          }
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
                    <span class="info-label">Recipient</span>
                    <div class="recipient-cell">
                      <span class="recipient-type-chip" [class]="getRecipientTypeClass(item)">
                        <app-icon [name]="getRecipientIcon(item)" [size]="11" />
                      </span>
                      <div class="recipient-details">
                        <span class="recipient-name">
                          {{ getRecipientName(item) }}
                        </span>
                        @if (getRecipientSubtext(item)) {
                          <span class="recipient-subtext">{{ getRecipientSubtext(item) }}</span>
                        }
                      </div>
                    </div>
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Channel</span>
                    <span class="channel-pill">{{ getRecipientTypeLabel(item) }}</span>
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Status</span>
                    <span class="status-badge" [class]="getStatusClass(item)">
                      <span class="status-dot"></span>
                      <span>{{ getStatusLabel(item) }}</span>
                    </span>
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Granted On</span>
                    <span class="info-value">{{ formatSharingDate(item.grantedAt) }}</span>
                  </div>

                  <div class="card-info-row">
                    <span class="info-label">Expires</span>
                    <span class="info-value" [class.expired]="isItemInactive(item)">
                      {{ item.expiresAt ? formatSharingDate(item.expiresAt) : 'Never' }}
                    </span>
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
                    class="action-btn action-btn-manage flex-1"
                    (click)="openManageAccess(item)"
                  >
                    <app-icon name="shield" [size]="14" />
                    <span>Manage</span>
                  </button>
                  @if (!isItemInactive(item)) {
                    <button
                      type="button"
                      class="action-btn action-btn-revoke"
                      (click)="confirmRevocation(item)"
                      title="Revoke Share"
                    >
                      <app-icon name="trash-2" [size]="14" />
                    </button>
                  }
                </div>
              </div>
            }
          </div>
        }
      </main>

      <!-- ── Modals & Drawers ── -->

      <!-- Revocation Confirmation Modal -->
      <sd-confirmation-modal
        [open]="isRevokeModalOpen()"
        title="Revoke Access Share"
        [message]="revokeConfirmationMessage()"
        confirmText="Revoke Access"
        cancelText="Cancel"
        variant="danger"
        [loading]="isRevoking()"
        (confirmed)="executeRevocation()"
        (cancelled)="cancelRevocation()"
      />

      <!-- Item Detail Drawer for Access Management -->
      <app-item-detail-drawer
        [isOpen]="isDrawerOpen()"
        [item]="selectedItemForDrawer()"
        (closed)="closeDrawer()"
        (openPreview)="onOpenPreviewFromDrawer($event)"
        (download)="onDownloadDocFromDrawer($event)"
        (downloadFolder)="onDownloadFolderFromDrawer($event)"
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
        [isOpen]="isFolderWindowOpen()"
        [rootFolder]="selectedFolderForWindow()"
        (closed)="isFolderWindowOpen.set(false)"
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

    .table-row.is-inactive {
      opacity: 0.65;
      background-color: #fafafa;
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

    /* Recipient Cell */
    .recipient-cell {
      display: flex;
      align-items: center;
      gap: 10px;
      min-width: 200px;
    }

    .recipient-type-chip {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 2px 7px;
      border-radius: 9999px;
      font-size: 10.5px;
      font-weight: 600;
      text-transform: uppercase;
      flex-shrink: 0;
    }

    .chip-user {
      background: #ecfdf5;
      color: #047857;
      border: 1px solid #a7f3d0;
    }

    .chip-team {
      background: #eff6ff;
      color: #1d4ed8;
      border: 1px solid #bfdbfe;
    }

    .chip-org {
      background: #fff7ed;
      color: #c2410c;
      border: 1px solid #fed7aa;
    }

    .chip-collab {
      background: #faf5ff;
      color: #7e22ce;
      border: 1px solid #e9d5ff;
    }

    .recipient-details {
      display: flex;
      flex-direction: column;
      gap: 1px;
      overflow: hidden;
    }

    .recipient-name {
      font-weight: 500;
      color: #1e293b;
      font-size: 12.5px;
      max-width: 180px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .recipient-subtext {
      font-size: 11px;
      color: #64748b;
      max-width: 180px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .recipient-strong {
      font-weight: 600;
    }

    /* Access Badge */
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

    /* Status Badge */
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      padding: 2px 8px;
      border-radius: 9999px;
      font-size: 11px;
      font-weight: 600;
    }

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .status--active {
      background: #ecfdf5;
      color: #065f46;
      border: 1px solid #a7f3d0;
    }
    .status--active .status-dot {
      background: #10b981;
    }

    .status--suspended {
      background: #fffbeb;
      color: #92400e;
      border: 1px solid #fde68a;
    }
    .status--suspended .status-dot {
      background: #f59e0b;
    }

    .status--revoked {
      background: #fef2f2;
      color: #991b1b;
      border: 1px solid #fecaca;
    }
    .status--revoked .status-dot {
      background: #ef4444;
    }

    .status--expired {
      background: #f1f5f9;
      color: #475569;
      border: 1px solid #cbd5e1;
    }
    .status--expired .status-dot {
      background: #94a3b8;
    }

    .date-text,
    .expiration-text {
      font-size: 12px;
      color: #64748b;
      white-space: nowrap;
    }

    .expiration-text.expired {
      color: #dc2626;
      font-weight: 500;
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
    .action-btn-manage {
      background: #ffffff;
      color: #475569;
      border-color: #cbd5e1;
    }

    .action-btn-details:hover,
    .action-btn-manage:hover {
      background: #f8fafc;
      color: #0f172a;
      border-color: #94a3b8;
    }

    .action-btn-revoke {
      background: #fef2f2;
      color: #dc2626;
      border-color: #fecaca;
    }

    .action-btn-revoke:hover {
      background: #fee2e2;
      color: #b91c1c;
      border-color: #fca5a5;
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

    .sharing-card.is-inactive {
      opacity: 0.7;
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
export class SharedByMePageComponent implements OnInit {
  private readonly sharingApiService = inject(SharingApiService);
  private readonly documentApiService = inject(DocumentApiService);
  private readonly folderApiService = inject(FolderApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  // ── State Signals ──
  isLoading = signal<boolean>(true);
  errorMessage = signal<string | null>(null);
  sharedFolders = signal<SharedItem[]>([]);
  sharedDocuments = signal<SharedItem[]>([]);

  // ── Filter Signals ──
  searchQuery = signal<string>('');
  resourceTypeFilter = signal<SharingResourceTypeFilter>('ALL');
  recipientFilter = signal<RecipientFilter>('ALL');
  statusFilter = signal<SharingStatusFilter>('ALL');
  levelFilter = signal<SharingLevelFilter>('ALL');
  viewMode = signal<SharingViewMode>('table');

  // ── Revoke Modal State ──
  itemToRevoke = signal<SharedItem | null>(null);
  isRevokeModalOpen = signal<boolean>(false);
  isRevoking = signal<boolean>(false);

  // ── Modals / Drawer State ──
  selectedItemForDrawer = signal<DrawerItemAdapter | null>(null);
  isDrawerOpen = signal<boolean>(false);

  previewItem = signal<{ type: 'folder' | 'document'; data: any } | null>(null);
  isPreviewOpen = signal<boolean>(false);

  isFolderWindowOpen = signal<boolean>(false);
  selectedFolderForWindow = signal<SharedFolderRoot | null>(null);

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
      this.recipientFilter() !== 'ALL' ||
      this.statusFilter() !== 'ALL' ||
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

    // 2. Recipient Type filter
    const recFilter = this.recipientFilter();
    if (recFilter !== 'ALL') {
      items = items.filter((i) => {
        const type = (i.recipientType || '').toUpperCase();
        if (recFilter === 'USER') {
          return type.includes('USER');
        }
        if (recFilter === 'TEAM') {
          return type.includes('GROUP') || type.includes('TEAM');
        }
        if (recFilter === 'ORGANIZATION') {
          return type.includes('ORGANIZATION') || type.includes('ORG');
        }
        if (recFilter === 'COLLABORATION') {
          return type.includes('COLLABORATION');
        }
        return true;
      });
    }

    // 3. Status filter
    const stFilter = this.statusFilter();
    if (stFilter === 'ACTIVE') {
      items = items.filter((i) => !this.isItemInactive(i));
    } else if (stFilter === 'INACTIVE') {
      items = items.filter((i) => this.isItemInactive(i));
    }

    // 4. Permission Level filter
    const lvlFilter = this.levelFilter();
    if (lvlFilter !== 'ALL') {
      items = items.filter((i) => i.permissionLevel === lvlFilter);
    }

    // 5. Search query
    const q = this.searchQuery().trim().toLowerCase();
    if (q) {
      items = items.filter((i) => {
        const nameMatch = (i.name || '').toLowerCase().includes(q);
        const recipientName = (this.getRecipientName(i) || '').toLowerCase().includes(q);
        const recipientSub = (this.getRecipientSubtext(i) || '').toLowerCase().includes(q);
        return nameMatch || recipientName || recipientSub;
      });
    }

    return items;
  });

  readonly revokeConfirmationMessage = computed(() => {
    const item = this.itemToRevoke();
    if (!item) return 'Are you sure you want to revoke this share?';
    const recipient = this.getRecipientName(item);
    return `Are you sure you want to revoke access to "${item.name}" for ${recipient}? The recipient will immediately lose their granted access permissions.`;
  });

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.sharingApiService.getSharedByMe().subscribe({
      next: (res: SharedByMeResponse) => {
        this.sharedFolders.set(res.sharedFolders || []);
        this.sharedDocuments.set(res.sharedDocuments || []);
        this.isLoading.set(false);
      },
      error: (err) => {
        console.error('[SharedByMe] Failed to load outbound shares:', err);
        this.isLoading.set(false);
        this.errorMessage.set(
          err?.userFriendlyMessage || err?.message || err?.error?.message || 'Failed to load outbound shares. Please check your connection and try again.'
        );
      },
    });
  }

  clearFilters(): void {
    this.searchQuery.set('');
    this.resourceTypeFilter.set('ALL');
    this.recipientFilter.set('ALL');
    this.statusFilter.set('ALL');
    this.levelFilter.set('ALL');
  }

  getRecipientFilterLabel(): string {
    const map: Record<string, string> = {
      ALL: 'All Recipients',
      USER: 'User Shares',
      TEAM: 'Team Shares',
      ORGANIZATION: 'Organization Shares',
      COLLABORATION: 'Collaboration Shares',
    };
    return map[this.recipientFilter()] ?? 'All Recipients';
  }

  getStatusFilterLabel(): string {
    const map: Record<string, string> = {
      ALL: 'All Statuses',
      ACTIVE: 'Active Only',
      INACTIVE: 'Revoked / Expired',
    };
    return map[this.statusFilter()] ?? 'All Statuses';
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
    this.openManageAccess(item);
  }

  openItem(item: SharedItem): void {
    if (item.resourceType === 'FOLDER') {
      this.selectedFolderForWindow.set({ id: item.resourceId, name: item.name });
      this.isFolderWindowOpen.set(true);
    } else {
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

  openManageAccess(item: SharedItem): void {
    const drawerItem = sharedItemToDrawerItem(item, 'access');
    this.selectedItemForDrawer.set(drawerItem);
    this.isDrawerOpen.set(true);
  }

  confirmRevocation(item: SharedItem): void {
    this.itemToRevoke.set(item);
    this.isRevokeModalOpen.set(true);
  }

  cancelRevocation(): void {
    this.isRevokeModalOpen.set(false);
    this.itemToRevoke.set(null);
  }

  executeRevocation(): void {
    const item = this.itemToRevoke();
    if (!item || !item.permissionId) {
      this.cancelRevocation();
      return;
    }

    this.isRevoking.set(true);
    this.sharingApiService.revokePermission(item.permissionId).subscribe({
      next: () => {
        this.isRevoking.set(false);
        this.isRevokeModalOpen.set(false);
        this.notificationService.success(`Successfully revoked access for "${item.name}".`);

        // Mark revoked reactively in list
        const updateList = (list: SharedItem[]) =>
          list.map((i) =>
            i.permissionId === item.permissionId
              ? { ...i, revoked: true, status: 'REVOKED' as const }
              : i
          );

        this.sharedFolders.update(updateList);
        this.sharedDocuments.update(updateList);
        this.itemToRevoke.set(null);
      },
      error: (err) => {
        this.isRevoking.set(false);
        this.notificationService.error(
          err?.error?.message || 'Failed to revoke access. Please try again.'
        );
      },
    });
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

  isItemInactive(item: SharedItem): boolean {
    return isShareInactive(item);
  }

  getRecipientTypeLabel(item: SharedItem): string {
    const type = (item.recipientType || '').toUpperCase();
    if (type.includes('USER')) return 'User';
    if (type.includes('GROUP') || type.includes('TEAM')) return 'Team';
    if (type.includes('ORGANIZATION') || type.includes('ORG')) return 'Org';
    if (type.includes('COLLABORATION')) return 'Collab';
    return 'Recipient';
  }

  getRecipientTypeClass(item: SharedItem): string {
    const type = (item.recipientType || '').toUpperCase();
    if (type.includes('USER')) return 'chip-user';
    if (type.includes('GROUP') || type.includes('TEAM')) return 'chip-team';
    if (type.includes('ORGANIZATION') || type.includes('ORG')) return 'chip-org';
    if (type.includes('COLLABORATION')) return 'chip-collab';
    return 'chip-user';
  }

  getRecipientIcon(item: SharedItem): IconName {
    const type = (item.recipientType || '').toUpperCase();
    if (type.includes('USER')) return 'user';
    if (type.includes('GROUP') || type.includes('TEAM')) return 'users-round';
    if (type.includes('ORGANIZATION') || type.includes('ORG')) return 'building-2';
    if (type.includes('COLLABORATION')) return 'share-2';
    return 'user';
  }

  getRecipientName(item: SharedItem): string {
    const type = (item.recipientType || '').toUpperCase();
    if (type.includes('TEAM') || type.includes('GROUP')) {
      return item.targetGroupName || `Team #${item.targetGroupId || ''}`;
    }
    if (type.includes('ORGANIZATION') || type.includes('ORG')) {
      return item.targetOrgName || item.targetOrgCode || 'Organization Member';
    }
    if (type.includes('COLLABORATION')) {
      return item.collaborationName || `Collaboration #${item.collaborationId || ''}`;
    }
    return item.targetUserName || item.targetUserEmail || 'Personal User';
  }

  getRecipientSubtext(item: SharedItem): string | null {
    const type = (item.recipientType || '').toUpperCase();
    if (type.includes('USER') && item.targetUserName && item.targetUserEmail) {
      return item.targetUserEmail;
    }
    if (type.includes('TEAM') && item.ownerOrgName) {
      return item.ownerOrgName;
    }
    if (type.includes('ORGANIZATION') && item.targetOrgCode && item.targetOrgName) {
      return item.targetOrgCode;
    }
    return null;
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

  getStatusClass(item: SharedItem): string {
    if (item.revoked || item.status === 'REVOKED') return 'status--revoked';
    if (item.suspended || item.status === 'SUSPENDED') return 'status--suspended';
    if (this.isItemInactive(item)) return 'status--expired';
    return 'status--active';
  }

  getStatusLabel(item: SharedItem): string {
    if (item.revoked || item.status === 'REVOKED') return 'Revoked';
    if (item.suspended || item.status === 'SUSPENDED') return 'Suspended';
    if (this.isItemInactive(item)) return 'Expired';
    return 'Active';
  }
}
