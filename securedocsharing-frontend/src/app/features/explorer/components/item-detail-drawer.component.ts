import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  signal,
  inject,
  effect,
  HostListener,
  ElementRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AccessRequestApiService } from '../../../core/services/access-request-api.service';
import { NotificationService } from '../../../core/services/notification.service';
import { FolderApiService } from '../../../core/services/folder-api.service';
import { DocumentApiService } from '../../../core/services/document-api.service';
import { AuditApiService } from '../../../core/services/audit-api.service';
import { UnifiedAuditApiService } from '../../../core/services/unified-audit-api.service';
import { UnifiedAuditEventResponseDto } from '../../../core/models/unified-audit.model';
import { PermissionApiService } from '../../permissions/services/permission-api.service';
import { ShareApiService } from '../../permissions/services/share-api.service';
import { UserApiService } from '../../../core/services/user-api.service';
import { AuthStore } from '../../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import {
  FolderPeekResponseDto,
  FolderContentResponseDto,
} from '../../../core/models/folder.model';
import { UserResponseDto } from '../../../core/models/user.model';
import { DocumentActivityLogResponse } from '../../../core/models/audit.model';
import {
  EffectivePermissionsResponse,
  UserAccessSummary,
  PermissionEntry,
  ShareLinkResponseDto,
  SharePermissionLevel,
  BulkShareWithExclusionsDto,
} from '../../permissions/interfaces/permission.interfaces';

import { ShareModalComponent } from './share-modal.component';
import { PermissionDetailModalComponent } from './permission-detail-modal.component';
import { ShareLinkDetailModalComponent } from './share-link-detail-modal.component';
import { isOrganizationResource, getResourceOwnerOrgCode } from '../../../core/utils/resource-classification.util';
import { isResourceOwner, getResourceOwnerEmail } from '../../../core/utils/resource-ownership.util';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';

export interface AccessEntryVM {
  id?: string | number;
  permissionId?: string | number;
  name: string;
  email?: string;
  subtext?: string;
  level: string;
  source:
    | 'OWNER'
    | 'DIRECT'
    | 'INHERITED'
    | 'ORGANIZATION'
    | 'TEAM'
    | 'WORKSPACE'
    | 'COLLABORATION';
  inheritedFrom?: string;
  expiresAt?: string;
  grantedAt?: string;
  createdAt?: string;
  canRevoke?: boolean;
  entityType: 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION';
  isExternal?: boolean;
  avatarText?: string;
}

export interface ResourceCapabilityVM {
  title: string;
  desc: string;
  allowed: boolean;
  icon: IconName;
}

@Component({
  selector: 'app-item-detail-drawer',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ShareModalComponent,
    PermissionDetailModalComponent,
    ShareLinkDetailModalComponent,
    IconComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isOpen() && item()) {
      <div class="drawer-backdrop" (click)="closed.emit()">
        <div class="drawer-panel" (click)="$event.stopPropagation()">
          <!-- Header -->
          <div class="drawer-header">
            <!-- Root-to-Leaf Location Tree Bar -->
            <div class="header-tree-bar">
              <div class="location-breadcrumb">
                <app-icon name="house" [size]="13" class="root-tree-icon" />
                <div class="crumb-list">
                  @for (crumb of getLocationBreadcrumbs(); track $index) {
                    @if (!$first) {
                      <app-icon
                        name="chevron-right"
                        [size]="11"
                        class="crumb-sep"
                      />
                    }
                    <span
                      class="crumb-item"
                      [class.is-leaf]="$last"
                      [title]="crumb"
                      >{{ crumb }}</span
                    >
                  }
                </div>
              </div>
              <button
                class="close-btn"
                (click)="closed.emit()"
                title="Close (Esc)"
                aria-label="Close detail drawer"
              >
                <app-icon name="x" [size]="16" />
              </button>
            </div>

            <!-- Hero Main Info Row -->
            <div class="header-main-row">
              <div class="header-hero-icon">
                @if (item()?.type === 'folder') {
                  <app-icon name="folder" [size]="22" />
                } @else {
                  <app-icon name="file-text" [size]="22" />
                }
              </div>

              <div class="header-title-meta">
                <h3 class="header-item-name" [title]="getItemName()">
                  {{ getItemName() }}
                </h3>

                <div class="header-badges-row">
                  <div
                    class="header-space-badge"
                    [class.org-space]="isOrgSpace()"
                  >
                    @if (isOrgSpace()) {
                      <app-icon name="building-2" [size]="12" />
                      <span>Organization Space</span>
                    } @else {
                      <app-icon name="user" [size]="12" />
                      <span>Personal Space</span>
                    }
                  </div>

                  <span class="type-meta-badge">
                    {{
                      item()?.type === 'folder'
                        ? 'Folder'
                        : getFileCategoryLabel()
                    }}
                  </span>

                  @if (!item()?.data?.isLocked) {
                    <span class="size-meta-badge">
                      @if (item()?.type === 'folder') {
                        {{ folderPeek()?.fileCount ?? 0 }} items •
                        {{
                          formatBytes(
                            folderPeek()?.totalSizeBytes || getItemSize()
                          )
                        }}
                      } @else {
                        {{ formatBytes(getItemSize()) }}
                      }
                    </span>
                  } @else {
                    <span class="type-meta-badge" style="display: inline-flex; align-items: center; gap: 4px; background: #ffedd5; color: #c2410c; border-color: #fed7aa; font-weight: 700;">
                      <app-icon name="lock-keyhole" [size]="12" />
                      <span>LOCKED</span>
                    </span>
                  }
                </div>
              </div>
            </div>
          </div>

          <!-- Navigation Tabs -->
          <div class="drawer-tabs-wrapper">
            <div class="drawer-tabs">
              <button
                type="button"
                class="tab-btn"
                [class.active]="activeTab() === 'overview'"
                (click)="selectTab('overview')"
              >
                <svg
                  viewBox="0 0 16 16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                >
                  <line x1="3.5" y1="9.5" x2="3.5" y2="13.5" />
                  <line x1="8" y1="6" x2="8" y2="13.5" />
                  <line x1="12.5" y1="2.5" x2="12.5" y2="13.5" />
                </svg>
                <span>Overview</span>
              </button>
              <button
                type="button"
                class="tab-btn"
                [class.active]="activeTab() === 'access'"
                (click)="selectTab('access')"
              >
                <svg
                  viewBox="0 0 16 16"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="1.8"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                >
                  <circle cx="5.5" cy="10.5" r="3.2" />
                  <line x1="7.8" y1="8.2" x2="13.5" y2="2.5" />
                  <line x1="11.2" y1="4.8" x2="13" y2="6.6" />
                </svg>
                <span>Access & Security</span>
              </button>
            </div>
          </div>

          <!-- Body -->
          <div class="drawer-body custom-scrollbar">
            <!-- OVERVIEW TAB -->
            @if (activeTab() === 'overview') {
              @if (item()?.data?.isLocked) {
                <div class="tab-pane overview-pane">
                  <div class="locked-drawer-hero">
                    <div class="locked-drawer-icon-wrap">
                      <app-icon name="lock-keyhole" [size]="32" />
                    </div>
                    <h4 class="locked-drawer-title">{{ getItemName() }}</h4>
                    <span class="locked-badge-pill" style="display: inline-flex; align-items: center; gap: 6px;">
                      <app-icon name="lock-keyhole" [size]="14" />
                      <span>ACCESS RESTRICTED</span>
                    </span>
                    <p class="locked-drawer-desc">
                      This {{ item()?.type === 'folder' ? 'folder' : 'document' }} is restricted by workspace access policy. You can request access from the workspace administrator to view its contents.
                    </p>

                    <div class="locked-drawer-action-box">
                      @if (item()?.data?.canRequestAccess && item()?.data?.requestStatus === 'NONE') {
                        <button type="button" class="btn-drawer-request" (click)="onRequestAccessFromDrawer()">
                          <app-icon name="key" [size]="16" />
                          <span>Request Access</span>
                        </button>
                      } @else if (item()?.data?.requestStatus === 'PENDING') {
                        <div class="pending-request-box">
                          <div class="drawer-pending-pill">
                            <app-icon name="clock" [size]="14" />
                            <span>Access Request Pending Review</span>
                          </div>
                          @if (item()?.data?.pendingRequestId) {
                            <button
                              type="button"
                              class="btn-withdraw-request"
                              [disabled]="isCancellingRequest()"
                              (click)="onCancelPendingRequest()"
                            >
                              @if (isCancellingRequest()) {
                                <span>Withdrawing...</span>
                              } @else {
                                <app-icon name="x" [size]="13" />
                                <span>Withdraw Request</span>
                              }
                            </button>
                          }
                        </div>
                      }
                    </div>
                  </div>

                  <div class="section-divider"></div>

                  <!-- Sanitized Resource Attributes -->
                  <div class="overview-section space-owner-card">
                    <div class="card-header-sm">
                      <div class="header-title-flex">
                        <app-icon name="info" [size]="15" />
                        <span class="card-title-sm">Resource Details</span>
                      </div>
                    </div>
                    <div class="sanitized-meta-grid">
                      <div class="meta-item">
                        <span class="meta-label">Type</span>
                        <span class="meta-value">{{ item()?.type === 'folder' ? 'Folder' : 'Document' }}</span>
                      </div>
                      <div class="meta-item">
                        <span class="meta-label">Location</span>
                        <span class="meta-value">{{ getLocationPath() }}</span>
                      </div>
                      <div class="meta-item">
                        <span class="meta-label">Date Added</span>
                        <span class="meta-value">{{ item()?.data?.createdAt ? formatRelativeTime(item()?.data?.createdAt) : '—' }}</span>
                      </div>
                      <div class="meta-item">
                        <span class="meta-label">Status</span>
                        <span class="meta-value" style="color: #ea580c; font-weight: 600;">Locked</span>
                      </div>
                    </div>
                  </div>
                </div>
              } @else {
                <div class="tab-pane overview-pane">
                <!-- 1. Primary Feature Card: Thumbnail Preview (File) or Folder Breakdown (Folder) -->
                @if (item()?.type === 'document') {
                  <div class="overview-section file-preview-card">
                    <div class="file-preview-banner">
                      <div class="preview-thumb-box">
                        @if (getFileCategory() === 'image') {
                          <app-icon name="image" [size]="28" />
                        } @else {
                          <app-icon name="file-text" [size]="28" />
                        }
                      </div>
                      <div class="preview-badge-row">
                        <span class="viewable-badge">
                          <app-icon name="check" [size]="12" />
                          Viewable Document
                        </span>
                        <span class="ext-tag">{{
                          (
                            getItemName().split('.').pop() || 'FILE'
                          ).toUpperCase()
                        }}</span>
                      </div>
                    </div>
                    <div class="file-preview-details">
                      <div class="preview-meta-info">
                        <span
                          class="preview-filename"
                          [title]="getItemName()"
                          >{{ getItemName() }}</span
                        >
                        <span class="preview-submeta">
                          {{ getFileCategoryLabel() }}
                          @if (getPageOrSlideCount()) {
                            • {{ getPageOrSlideCount() }}
                          }
                          • {{ formatBytes(getItemSize()) }}
                        </span>
                      </div>
                      <button
                        type="button"
                        class="btn-full-preview"
                        (click)="onOpenPreview()"
                      >
                        <app-icon name="eye" [size]="15" />
                        <span>Open Preview</span>
                      </button>
                    </div>
                  </div>
                } @else {
                  <div class="overview-section folder-overview-card">
                    <div class="folder-stats-header">
                      <div class="folder-stat-item">
                        <span class="stat-num">{{
                          folderPeek()?.fileCount ?? 0
                        }}</span>
                        <span class="stat-lbl">Files</span>
                      </div>
                      <div class="stat-divider"></div>
                      <div class="folder-stat-item">
                        <span class="stat-num">{{
                          folderPeek()?.subfolderCount ?? 0
                        }}</span>
                        <span class="stat-lbl">Subfolders</span>
                      </div>
                      <div class="stat-divider"></div>
                      <div class="folder-stat-item">
                        <span class="stat-num">{{
                          formatBytes(
                            folderPeek()?.totalSizeBytes || getItemSize()
                          )
                        }}</span>
                        <span class="stat-lbl">Total Size</span>
                      </div>
                    </div>

                    <!-- Mini Content Preview List -->
                    <div class="inner-items-container">
                      <div class="inner-items-header">
                        <span class="inner-items-title">Folder Contents</span>
                        <span class="inner-items-count">
                          @if (isLoadingFolderContents()) {
                            Loading...
                          } @else {
                            {{
                              (folderContents()?.subFolders?.length || 0) +
                                (folderContents()?.documents?.length || 0)
                            }}
                            item(s)
                          }
                        </span>
                      </div>

                      @if (isLoadingFolderContents()) {
                        <div class="inner-loading-state">
                          <div class="spinner-sm"></div>
                          <span>Fetching directory items...</span>
                        </div>
                      } @else if (
                        folderContents()?.subFolders?.length ||
                        folderContents()?.documents?.length
                      ) {
                        <div class="inner-items-list custom-scrollbar">
                          @for (
                            sf of folderContents()?.subFolders;
                            track sf.id
                          ) {
                            <div class="inner-item-row folder-row">
                              <div class="inner-item-icon folder-icon">
                                <app-icon name="folder" [size]="16" />
                              </div>
                              <div class="inner-item-details">
                                <span
                                  class="inner-item-name"
                                  [title]="sf.name"
                                  >{{ sf.name }}</span
                                >
                                <span class="inner-item-sub">Subfolder</span>
                              </div>
                            </div>
                          }
                          @for (
                            doc of folderContents()?.documents;
                            track doc.id
                          ) {
                            <div class="inner-item-row doc-row">
                              <div class="inner-item-icon doc-icon">
                                <app-icon name="file-text" [size]="16" />
                              </div>
                              <div class="inner-item-details">
                                <span
                                  class="inner-item-name"
                                  [title]="doc.name || doc.fileName"
                                  >{{
                                    doc.name || doc.fileName || 'Untitled File'
                                  }}</span
                                >
                                <span class="inner-item-sub">{{
                                  formatBytes(
                                    doc.fileSizeBytes || doc.fileSize || 0
                                  )
                                }}</span>
                              </div>
                            </div>
                          }
                        </div>
                      } @else {
                        <div class="inner-empty-state">
                          <app-icon name="folder-search" [size]="20" />
                          <span>This folder is currently empty</span>
                        </div>
                      }
                    </div>
                  </div>
                }

                @if (canManagePermissions()) {
                  <div class="section-divider"></div>

                  <!-- 2. Access / Usage Statistics Section -->
                  <div class="overview-section activity-chart-card">
                  <div class="card-header-sm">
                    <div class="header-title-flex">
                      <div class="header-icon-box">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="2"
                          stroke-linecap="round"
                          stroke-linejoin="round"
                        >
                          <path
                            d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"
                          ></path>
                          <path d="M3 3v5h5"></path>
                          <path d="M12 7v5l4 2"></path>
                        </svg>
                      </div>
                      <span class="card-title-sm">Activity & Engagement</span>
                    </div>
                    <span class="activity-rate-badge">
                      <span class="badge-pulse-dot"></span>
                      {{ getOpenRatePercentage() }}% Open Rate
                    </span>
                  </div>

                  <div class="activity-chart-body">
                    <!-- Circular Donut Ring Chart -->
                    <div class="chart-donut-wrapper">
                      <svg class="chart-donut-svg" viewBox="0 0 84 84">
                        <!-- Track Circle Background -->
                        <circle
                          cx="42"
                          cy="42"
                          r="32"
                          fill="none"
                          stroke="#f1f5f9"
                          stroke-width="7"
                        />

                        <!-- Opens Arc (Orange Accent #ff5500) -->
                        @if (getOpenRatePercentage() > 0) {
                          <circle
                            cx="42"
                            cy="42"
                            r="32"
                            fill="none"
                            stroke="#ff5500"
                            stroke-width="7"
                            stroke-linecap="round"
                            stroke-dasharray="201.06"
                            [attr.stroke-dashoffset]="getOpenStrokeDashoffset()"
                            transform="rotate(-90 42 42)"
                            class="donut-arc-animated"
                          />
                        }

                        <!-- Downloads Arc (Soft Peach-Orange #fb923c) -->
                        @if (getDownloadRatePercentage() > 0) {
                          <circle
                            cx="42"
                            cy="42"
                            r="32"
                            fill="none"
                            stroke="#fb923c"
                            stroke-width="7"
                            stroke-linecap="round"
                            stroke-dasharray="201.06"
                            [attr.stroke-dashoffset]="
                              getDownloadStrokeDashoffset()
                            "
                            [attr.transform]="
                              'rotate(' + getDownloadRotationAngle() + ' 42 42)'
                            "
                            class="donut-arc-animated"
                          />
                        }
                      </svg>
                      <!-- Center Info Text -->
                      <div class="donut-center-info">
                        <span class="donut-center-val">{{
                          getTotalActivityCount()
                        }}</span>
                        <span class="donut-center-lbl">ACTIONS</span>
                      </div>
                    </div>

                    <!-- Statistics & Breakdown Details -->
                    <div class="chart-stats-details">
                      <!-- Opens Metric Item -->
                      <div class="metric-row open-metric-row">
                        <div class="metric-info-left">
                          <div class="metric-icon-badge open-icon-bg">
                            <svg
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              stroke-width="2"
                              stroke-linecap="round"
                              stroke-linejoin="round"
                            >
                              <path
                                d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z"
                              />
                              <circle cx="12" cy="12" r="3" />
                            </svg>
                          </div>
                          <div class="metric-text-group">
                            <span class="metric-title">Opens & Views</span>
                            <span class="metric-sub"
                              >{{ getPreviewCount() }} views</span
                            >
                          </div>
                        </div>
                        <div class="metric-rate-badge open-badge">
                          {{ getOpenRatePercentage() }}%
                        </div>
                      </div>

                      <!-- Downloads Metric Item -->
                      <div class="metric-row download-metric-row">
                        <div class="metric-info-left">
                          <div class="metric-icon-badge download-icon-bg">
                            <svg
                              viewBox="0 0 24 24"
                              fill="none"
                              stroke="currentColor"
                              stroke-width="2"
                              stroke-linecap="round"
                              stroke-linejoin="round"
                            >
                              <path
                                d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"
                              />
                              <polyline points="7 10 12 15 17 10" />
                              <line x1="12" y1="15" x2="12" y2="3" />
                            </svg>
                          </div>
                          <div class="metric-text-group">
                            <span class="metric-title">Downloads</span>
                            <span class="metric-sub"
                              >{{ getDownloadCount() }} downloads</span
                            >
                          </div>
                        </div>
                        <div class="metric-rate-badge download-badge">
                          {{ getDownloadRatePercentage() }}%
                        </div>
                      </div>

                      <!-- Visual Stacked Bar -->
                      <div class="rate-progress-stacked">
                        <div
                          class="stacked-segment open-segment"
                          [style.width.%]="getOpenRatePercentage()"
                          title="Opens: {{ getOpenRatePercentage() }}%"
                        ></div>
                        <div
                          class="stacked-segment download-segment"
                          [style.width.%]="getDownloadRatePercentage()"
                          title="Downloads: {{ getDownloadRatePercentage() }}%"
                        ></div>
                      </div>
                    </div>
                  </div>
                  </div>
                }

                <div class="section-divider"></div>

                <!-- 3. Owner & Space Information Card -->
                <div class="overview-section space-owner-card">
                  <div class="card-header-sm">
                    <div class="header-title-flex">
                      <svg
                        viewBox="0 0 16 16"
                        fill="none"
                        stroke="currentColor"
                        stroke-width="1.5"
                      >
                        <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" />
                        <path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" />
                      </svg>
                      <span class="card-title-sm">Ownership & Location</span>
                    </div>
                  </div>

                  <div class="owner-card-body">
                    <!-- Owner Person Details Card Block -->
                    <div class="owner-person-row">
                      <div class="owner-avatar-lg">
                        <span>{{ getOwnerInitials() }}</span>
                      </div>
                      <div class="owner-person-info">
                        <div class="owner-name-line">
                          <span class="owner-name-bold">{{
                            getOwnerName()
                          }}</span>
                          <span class="owner-role-badge">Owner</span>
                        </div>
                        @if (getOwnerEmail()) {
                          <span class="owner-email-sub">{{
                            getOwnerEmail()
                          }}</span>
                        }
                        <div class="owner-timestamp">
                          <svg
                            viewBox="0 0 16 16"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.5"
                          >
                            <circle cx="8" cy="8" r="6" />
                            <polyline points="8 4 8 8 10.5 10.5" />
                          </svg>
                          <span>
                            @if (item()?.type === 'folder') {
                              Created {{ formatRelativeTime(getItemDate()) }}
                            } @else {
                              Uploaded {{ formatRelativeTime(getItemDate()) }}
                            }
                          </span>
                        </div>
                      </div>
                    </div>

                    <div class="owner-card-divider"></div>

                    <!-- Storage Space & Location Info -->
                    <div class="location-space-row">
                      <div
                        class="space-badge-box"
                        [class.org-space]="isOrgSpace()"
                      >
                        @if (isOrgSpace()) {
                          <svg
                            viewBox="0 0 16 16"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.5"
                          >
                            <rect x="3" y="2" width="10" height="12" rx="1" />
                            <line x1="6" y1="5" x2="10" y2="5" />
                            <line x1="6" y1="8" x2="10" y2="8" />
                            <line x1="6" y1="11" x2="10" y2="11" />
                          </svg>
                          <span>Organization Space</span>
                        } @else {
                          <svg
                            viewBox="0 0 16 16"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.5"
                          >
                            <path d="M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6z" />
                            <path d="M2 14c0-3.3 2.7-6 6-6s6 2.7 6 6" />
                          </svg>
                          <span>Personal Space</span>
                        }
                      </div>

                      <div
                        class="location-path-pill"
                        [title]="getLocationPath()"
                      >
                        <svg
                          viewBox="0 0 16 16"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                        >
                          <path
                            d="M2 4.5A1.5 1.5 0 0 1 3.5 3h3.172a1.5 1.5 0 0 1 1.06.44l1.329 1.328A1.5 1.5 0 0 0 10.12 5.25H12.5A1.5 1.5 0 0 1 14 6.75v5.75a1.5 1.5 0 0 1-1.5 1.5h-9A1.5 1.5 0 0 1 2 12.5V4.5z"
                          />
                        </svg>
                        <span class="path-text">{{ getLocationPath() }}</span>
                      </div>
                    </div>
                  </div>
                </div>

                @if (canManagePermissions()) {
                  <div class="section-divider"></div>

                  <!-- 4. Activity / Audit Intelligence Log Section with Cloudy Border -->
                  <div class="overview-section audit-card-cloudy">
                    <div class="card-header-sm">
                      <div class="header-title-flex">
                        <svg
                          viewBox="0 0 16 16"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                        >
                          <path d="M12 8v4H4V4h4" />
                          <path d="M8 2h6v6" />
                          <line x1="9" y1="7" x2="15" y2="1" />
                        </svg>
                        <span class="card-title-sm"
                          >Activity & Permissions Audit</span
                        >
                      </div>
                      <span class="audit-count-badge"
                        >{{ getActivityFeed().length }} log(s)</span
                      >
                    </div>

                    @if (isLoadingAudit()) {
                      <div class="audit-loading">
                        <div class="spinner-sm"></div>
                        <span>Loading activity...</span>
                      </div>
                    } @else if (getActivityFeed().length === 0) {
                      <div class="audit-empty-state">
                        <svg
                          viewBox="0 0 24 24"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                        >
                          <circle cx="12" cy="12" r="10" />
                          <polyline points="12 6 12 12 16 14" />
                        </svg>
                        <p>No activity logs recorded yet for this item.</p>
                      </div>
                    } @else {
                      <div class="activity-feed-scrollable custom-scrollbar">
                        @for (log of getActivityFeed(); track log.id || $index) {
                          <div class="feed-item-cloudy">
                            <div class="feed-avatar-cloudy">
                              <span>{{
                                getUserInitials(log.accessorEmail)
                              }}</span>
                            </div>
                            <div class="feed-content">
                              <div class="feed-text">
                                <span class="feed-user">{{
                                 getDisplayName(log.accessorEmail)
                                }}</span>
                                <span class="feed-action">{{
                                  formatActionText(log.action, log.details)
                                }}</span>
                              </div>
                              <div class="feed-meta">
                                <span class="meta-time">{{
                                  formatRelativeTime(log.timestamp)
                                }}</span>
                                <span class="meta-dot">•</span>
                                <span class="meta-ua">{{
                                  parseBrowser(log.userAgent)
                                }}</span>
                              </div>
                            </div>
                          </div>
                        }
                      </div>
                    }
                  </div>
                } @else {
                  <div class="section-divider"></div>

                  <!-- Safe Sharing Context Details Card for Normal Recipient (Section 4 & 5) -->
                  <div class="overview-section safe-sharing-card">
                    <div class="card-header-sm">
                      <div class="header-title-flex">
                        <app-icon name="info" [size]="15" />
                        <span class="card-title-sm">Sharing & Access Details</span>
                      </div>
                    </div>
                    <div class="safe-meta-grid">
                      <div class="meta-item">
                        <span class="meta-label">Resource Type</span>
                        <span class="meta-value">{{ item()?.type === 'folder' ? 'Folder' : 'Document' }}</span>
                      </div>
                      <div class="meta-item">
                        <span class="meta-label">Your Permission</span>
                        <span class="meta-value">
                          <span class="access-badge-sm" [ngClass]="getPermissionLevelClass(getCurrentUserAccess().level)">
                            {{ getCurrentUserAccess().level }}
                          </span>
                        </span>
                      </div>
                      <div class="meta-item">
                        <span class="meta-label">Sharing Channel</span>
                        <span class="meta-value">{{ getSharingContext().channel }}</span>
                      </div>
                      <div class="meta-item">
                        <span class="meta-label">Shared By</span>
                        <span class="meta-value">{{ getSharingContext().sharedBy }}</span>
                      </div>
                      @if (getSharingContext().grantedAt) {
                        <div class="meta-item">
                          <span class="meta-label">Shared On</span>
                          <span class="meta-value">{{ formatDate(getSharingContext().grantedAt) }}</span>
                        </div>
                      }
                      <div class="meta-item">
                        <span class="meta-label">Access Expiration</span>
                        <span class="meta-value">{{ getSharingContext().expiresAt ? formatDate(getSharingContext().expiresAt) : 'Never' }}</span>
                      </div>
                    </div>
                  </div>
                }
              </div>
              }
            }

            <!-- ACCESS & SECURITY TAB -->
            @if (activeTab() === 'access') {
              @if (item()?.data?.isLocked) {
                <div class="tab-pane access-pane">
                  <div class="perm-empty-state" style="padding: 3rem 1.5rem; text-align: center;">
                    <div class="empty-icon-wrapper" style="margin: 0 auto 1rem;">
                      <app-icon name="shield" [size]="28"></app-icon>
                    </div>
                    <p class="empty-title" style="font-size: 1.125rem; font-weight: 600; color: #0f172a; margin-bottom: 0.5rem;">Access Restricted</p>
                    <p class="empty-sub" style="font-size: 0.875rem; color: #64748b; max-width: 320px; margin: 0 auto 1.5rem;">
                      You do not have permission to view or manage access for this restricted resource.
                    </p>
                    @if (item()?.data?.canRequestAccess && item()?.data?.requestStatus === 'NONE') {
                      <button type="button" class="btn-drawer-request" style="margin-top: 0.5rem;" (click)="onRequestAccessFromDrawer()">
                        <app-icon name="key" [size]="16" />
                        <span>Request Access</span>
                      </button>
                    } @else if (item()?.data?.requestStatus === 'PENDING') {
                      <div class="pending-request-box" style="margin-top: 0.5rem;">
                        <div class="drawer-pending-pill">
                          <app-icon name="clock" [size]="14" />
                          <span>Access Request Pending Review</span>
                        </div>
                        @if (item()?.data?.pendingRequestId) {
                          <button
                            type="button"
                            class="btn-withdraw-request"
                            [disabled]="isCancellingRequest()"
                            (click)="onCancelPendingRequest()"
                          >
                            @if (isCancellingRequest()) {
                              <span>Withdrawing...</span>
                            } @else {
                              <app-icon name="x" [size]="13" />
                              <span>Withdraw Request</span>
                            }
                          </button>
                        }
                      </div>
                    }
                  </div>
                </div>
              } @else if (isNormalRecipient()) {
                <div class="tab-pane access-pane recipient-access-pane">
                  <!-- YOUR ACCESS CARD FOR RECIPIENT -->
                  <div class="access-header-card recipient-access-card">
                    <div class="access-card-header">
                      <div class="access-card-heading">
                        <div class="access-title-row">
                          <h3 class="access-header-title">Your Access</h3>
                          <span class="access-status">
                            <span class="access-status-dot"></span>
                            Active Grant
                          </span>
                        </div>
                        <p class="access-header-sub">
                          Resource permissions granted to you for this {{ item()?.type === 'folder' ? 'folder' : 'document' }}.
                        </p>
                      </div>
                    </div>

                    <!-- Your access Identity & Level -->
                    <div class="your-access-section">
                      <div class="your-access-section-header">
                        <span class="section-eyebrow">PERMISSION LEVEL</span>
                        <span class="access-level" [ngClass]="getPermissionLevelClass(getCurrentUserAccess().level)">
                          {{ getCurrentUserAccess().level }}
                        </span>
                      </div>

                      <div class="your-access-identity">
                        <div class="your-access-avatar">
                          {{ getUserInitials(getCurrentUserAccess().email) }}
                        </div>
                        <div class="your-access-identity-info">
                          <div class="your-access-name-row">
                            <span class="your-access-name">{{ getCurrentUserAccess().name }}</span>
                            <span class="you-indicator">You</span>
                          </div>
                          <span class="your-access-email">{{ getCurrentUserAccess().email }}</span>
                          <div class="your-access-origin">
                            <app-icon name="shield-check" [size]="13"></app-icon>
                            <span>{{ getSharingContext().isDirectShare ? 'Shared directly with you' : 'Granted access' }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div class="section-divider"></div>

                  <!-- RECIPIENT CAPABILITIES CARD -->
                  <div class="access-section recipient-capabilities-card">
                    <div class="section-header">
                      <div class="section-title-group">
                        <span class="section-title">Resource Capabilities</span>
                        <span class="section-subtitle">What you are permitted to do</span>
                      </div>
                    </div>

                    <div class="capabilities-list">
                      @for (cap of getRecipientCapabilities(); track cap.title) {
                        <div class="capability-item" [class.allowed]="cap.allowed" [class.disallowed]="!cap.allowed">
                          <div class="cap-icon-box">
                            <app-icon [name]="cap.icon" [size]="15"></app-icon>
                          </div>
                          <div class="cap-content">
                            <div class="cap-title-row">
                              <span class="cap-title">{{ cap.title }}</span>
                              @if (cap.allowed) {
                                <span class="cap-status-badge allowed">Allowed</span>
                              } @else {
                                <span class="cap-status-badge disallowed">Restricted</span>
                              }
                            </div>
                            <span class="cap-desc">{{ cap.desc }}</span>
                          </div>
                        </div>
                      }
                    </div>
                  </div>

                  <div class="section-divider"></div>

                  <!-- SHARING CONTEXT CARD -->
                  <div class="access-section recipient-context-card">
                    <div class="section-header">
                      <div class="section-title-group">
                        <span class="section-title">Sharing Details</span>
                        <span class="section-subtitle">Context & provenance</span>
                      </div>
                    </div>

                    <div class="recipient-context-grid">
                      <div class="ctx-item">
                        <span class="ctx-label">Shared By</span>
                        <span class="ctx-val">{{ getSharingContext().sharedBy }}</span>
                      </div>
                      <div class="ctx-item">
                        <span class="ctx-label">Granted On</span>
                        <span class="ctx-val">{{ getSharingContext().grantedAt ? formatDate(getSharingContext().grantedAt) : 'N/A' }}</span>
                      </div>
                      <div class="ctx-item">
                        <span class="ctx-label">Access Expiration</span>
                        <span class="ctx-val">{{ getSharingContext().expiresAt ? formatDate(getSharingContext().expiresAt) : 'Never' }}</span>
                      </div>
                      <div class="ctx-item">
                        <span class="ctx-label">Resource Type</span>
                        <span class="ctx-val">{{ item()?.type === 'folder' ? 'Folder' : 'Document' }}</span>
                      </div>
                    </div>
                  </div>
                </div>
              } @else {
              <div class="tab-pane access-pane">
                <!-- COMPONENT 1: ELEGANT STUDIO AI / CHATGPT ACCESS HEADER CARD -->
                <div class="access-header-card">
                  <!-- Header -->
                  <div class="access-card-header">
                    <div class="access-card-heading">
                      <div class="access-title-row">
                        <h3 class="access-header-title">
                          Access &amp; Security
                        </h3>

                        <span class="access-status">
                          <span class="access-status-dot"></span>
                          {{
                            isOrgSpace()
                              ? 'Organization policy'
                              : 'Restricted access'
                          }}
                        </span>
                      </div>

                      <p class="access-header-sub">
                        Manage access and security for this
                        {{ item()?.type === 'folder' ? 'folder' : 'document' }}.
                      </p>
                    </div>

                    @if (canShareAccess()) {
                      <button
                        type="button"
                        class="access-share-button"
                        (click)="openShareModal()"
                        aria-label="Share item and manage permissions"
                      >
                        <app-icon name="user-plus" [size]="15"></app-icon>
                        <span>Share</span>
                      </button>
                    }
                  </div>

                  <!-- Your access -->
                  <div class="your-access-section">
                    <div class="your-access-section-header">
                      <span class="section-eyebrow">YOUR ACCESS</span>

                      <span
                        class="access-level"
                        [ngClass]="
                          getPermissionLevelClass(getCurrentUserAccess().level)
                        "
                      >
                        {{ getCurrentUserAccess().level }}
                      </span>
                    </div>

                    <div class="your-access-identity">
                      <div class="your-access-avatar">
                        {{ getUserInitials(getCurrentUserAccess().email) }}
                      </div>

                      <div class="your-access-identity-info">
                        <div class="your-access-name-row">
                          <span class="your-access-name">
                            {{ getCurrentUserAccess().name }}
                          </span>

                          <span class="you-indicator">You</span>
                        </div>

                        <span class="your-access-email">
                          {{ getCurrentUserAccess().email }}
                        </span>

                        <div class="your-access-origin">
                          @if (getCurrentUserAccess().source === 'INHERITED') {
                            <app-icon name="layers" [size]="13"></app-icon>
                            <span>
                              Inherited from
                              {{
                                getCurrentUserAccess().inheritedFrom || 'parent'
                              }}
                            </span>
                          } @else if (
                            getCurrentUserAccess().source === 'ORGANIZATION'
                          ) {
                            <app-icon name="building-2" [size]="13"></app-icon>
                            <span>Organization policy</span>
                          } @else if (
                            getCurrentUserAccess().source === 'OWNER'
                          ) {
                            <app-icon name="crown" [size]="13"></app-icon>
                            <span>Creator &amp; owner</span>
                          } @else {
                            <app-icon name="user" [size]="13"></app-icon>
                            <span>Direct access</span>
                          }
                        </div>
                      </div>
                    </div>

                    <!-- Why access -->
                    <div class="why-access-disclosure">
                      <button
                        type="button"
                        class="why-access-button"
                        id="why-access-toggle-btn"
                        [attr.aria-expanded]="isWhyAccessExpanded()"
                        aria-controls="why-access-panel"
                        (click)="toggleWhyAccess()"
                      >
                        <span class="why-access-left">
                          <span class="why-access-icon">
                            <app-icon name="info" [size]="13"></app-icon>
                          </span>

                          <span>Why do I have access?</span>
                        </span>

                        <app-icon
                          name="chevron-down"
                          [size]="14"
                          class="why-access-chevron"
                          [class.expanded]="isWhyAccessExpanded()"
                        ></app-icon>
                      </button>

                      @if (isWhyAccessExpanded()) {
                        <div
                          id="why-access-panel"
                          class="why-access-panel"
                          role="region"
                          aria-labelledby="why-access-toggle-btn"
                        >
                          <div class="why-access-row">
                            <app-icon name="layers" [size]="14"></app-icon>

                            <div>
                              <span>Access origin</span>
                              <p>{{ getWhyAccessReasonText().sourceReason }}</p>
                            </div>
                          </div>

                          <div class="why-access-row">
                            <app-icon name="check" [size]="14"></app-icon>

                            <div>
                              <span>Permissions &amp; scope</span>
                              <p>
                                {{ getWhyAccessReasonText().capabilityReason }}
                              </p>
                            </div>
                          </div>

                          @if (getWhyAccessReasonText().governanceReason) {
                            <div class="why-access-row">
                              <app-icon name="shield" [size]="14"></app-icon>

                              <div>
                                <span>Policy governance</span>
                                <p>
                                  {{
                                    getWhyAccessReasonText().governanceReason
                                  }}
                                </p>
                              </div>
                            </div>
                          }
                        </div>
                      }
                    </div>
                  </div>
                </div>

                <div class="section-divider"></div>

                <!-- 2. People & Groups Section -->
                <div class="access-section">
                  <div class="section-header">
                    <div class="section-title-group">
                      <span class="section-title">People & Groups</span>
                      <span class="section-subtitle"
                        >{{ getAccessList().length }} direct & inherited
                        principal(s)</span
                      >
                    </div>
                  </div>

                  <!-- Search & Filter Controls Bar -->
                  <div class="access-controls-bar">
                    <div class="access-search-box">
                      <app-icon
                        name="search"
                        [size]="14"
                        class="search-icon"
                      ></app-icon>
                      <input
                        type="text"
                        class="access-search-input"
                        placeholder="Search access..."
                        [value]="accessSearchQuery()"
                        (input)="
                          accessSearchQuery.set($any($event.target).value)
                        "
                        aria-label="Search access entries"
                      />
                      @if (accessSearchQuery()) {
                        <button
                          type="button"
                          class="btn-clear-search"
                          (click)="clearAccessSearch()"
                          aria-label="Clear search"
                        >
                          <app-icon name="x" [size]="14"></app-icon>
                        </button>
                      }
                    </div>

                    <!-- Category Filter Tabs -->
                    <div
                      class="access-filter-tabs"
                      role="tablist"
                      aria-label="Filter access by principal type"
                    >
                      <button
                        type="button"
                        role="tab"
                        class="access-tab-btn"
                        [class.active]="activeAccessFilterCategory() === 'ALL'"
                        [attr.aria-selected]="
                          activeAccessFilterCategory() === 'ALL'
                        "
                        (click)="setAccessFilterCategory('ALL')"
                      >
                        <span>All</span>
                        <span class="tab-count-badge">{{
                          getAccessCategoryCount('ALL')
                        }}</span>
                      </button>

                      <button
                        type="button"
                        role="tab"
                        class="access-tab-btn"
                        [class.active]="activeAccessFilterCategory() === 'USER'"
                        [attr.aria-selected]="
                          activeAccessFilterCategory() === 'USER'
                        "
                        (click)="setAccessFilterCategory('USER')"
                      >
                        <app-icon
                          name="user"
                          [size]="14"
                          class="tab-icon"
                        ></app-icon>
                        <span>People</span>
                        <span class="tab-count-badge">{{
                          getAccessCategoryCount('USER')
                        }}</span>
                      </button>

                      <button
                        type="button"
                        role="tab"
                        class="access-tab-btn"
                        [class.active]="activeAccessFilterCategory() === 'TEAM'"
                        [attr.aria-selected]="
                          activeAccessFilterCategory() === 'TEAM'
                        "
                        (click)="setAccessFilterCategory('TEAM')"
                      >
                        <app-icon
                          name="users"
                          [size]="14"
                          class="tab-icon"
                        ></app-icon>
                        <span>Teams</span>
                        <span class="tab-count-badge">{{
                          getAccessCategoryCount('TEAM')
                        }}</span>
                      </button>

                      <button
                        type="button"
                        role="tab"
                        class="access-tab-btn"
                        [class.active]="
                          activeAccessFilterCategory() === 'ORGANIZATION'
                        "
                        [attr.aria-selected]="
                          activeAccessFilterCategory() === 'ORGANIZATION'
                        "
                        (click)="setAccessFilterCategory('ORGANIZATION')"
                      >
                        <app-icon
                          name="building-2"
                          [size]="14"
                          class="tab-icon"
                        ></app-icon>
                        <span>Organizations</span>
                        <span class="tab-count-badge">{{
                          getAccessCategoryCount('ORGANIZATION')
                        }}</span>
                      </button>

                      <button
                        type="button"
                        role="tab"
                        class="access-tab-btn"
                        [class.active]="
                          activeAccessFilterCategory() === 'COLLABORATION'
                        "
                        [attr.aria-selected]="
                          activeAccessFilterCategory() === 'COLLABORATION'
                        "
                        (click)="setAccessFilterCategory('COLLABORATION')"
                      >
                        <app-icon
                          name="layers"
                          [size]="14"
                          class="tab-icon"
                        ></app-icon>
                        <span>Collaborations</span>
                        <span class="tab-count-badge">{{
                          getAccessCategoryCount('COLLABORATION')
                        }}</span>
                      </button>
                    </div>
                  </div>

                  @if (isLoadingPermissions()) {
                    <div class="perm-loading">
                      <span class="loading-dots">Loading permissions...</span>
                    </div>
                  } @else if (permissionError()) {
                    <div class="perm-error">
                      <span>{{ permissionError() }}</span>
                      <button
                        type="button"
                        class="btn-retry"
                        (click)="fetchPermissions()"
                      >
                        Retry
                      </button>
                    </div>
                  } @else if (isPermissionAccessDenied()) {
                    <div class="perm-empty-state">
                      <div class="empty-icon-wrapper">
                        <app-icon name="shield" [size]="24"></app-icon>
                      </div>
                      <p class="empty-title">Restricted Access</p>
                      <p class="empty-sub">
                        Access list management is restricted to organization administrators and resource managers.
                      </p>
                    </div>
                  } @else {
                    <div class="perm-list">
                      @for (
                        perm of getFilteredAccessList();
                        track perm.permissionId ||
                          perm.id ||
                          perm.email ||
                          $index
                      ) {
                        <div
                          class="perm-card clickable"
                          [class.is-expiring]="
                            perm.expiresAt &&
                            formatRemainingTime(perm.expiresAt) !== 'Expired' &&
                            formatRemainingTime(perm.expiresAt) !== 'No limit'
                          "
                          [class.is-expired]="
                            perm.expiresAt &&
                            formatRemainingTime(perm.expiresAt) === 'Expired'
                          "
                          tabindex="0"
                          role="button"
                          (click)="openPermissionDetailModal(perm)"
                          (keydown.enter)="openPermissionDetailModal(perm)"
                          (keydown.space)="
                            openPermissionDetailModal(perm);
                            $event.preventDefault()
                          "
                        >
                          <div
                            class="perm-avatar"
                            [class.team-avatar]="perm.entityType === 'TEAM'"
                            [class.org-avatar]="
                              perm.entityType === 'ORGANIZATION'
                            "
                            [class.collab-avatar]="
                              perm.entityType === 'COLLABORATION'
                            "
                            [attr.aria-label]="perm.entityType + ' principal'"
                          >
                            @if (perm.entityType === 'TEAM') {
                              <app-icon name="users" [size]="14"></app-icon>
                            } @else if (perm.entityType === 'ORGANIZATION') {
                              <app-icon
                                name="building-2"
                                [size]="14"
                              ></app-icon>
                            } @else if (perm.entityType === 'COLLABORATION') {
                              <app-icon name="layers" [size]="14"></app-icon>
                            } @else {
                              <span>{{
                                perm.avatarText ||
                                  getUserInitials(perm.name || perm.email)
                              }}</span>
                            }
                          </div>

                          <div class="perm-info">
                            <div class="perm-name-row">
                              <span class="perm-name">{{ perm.name }}</span>
                              <div class="perm-badges-group">
                                @if (perm.isExternal) {
                                  <span class="perm-external-badge"
                                    >External</span
                                  >
                                }
                                <span
                                  class="perm-level-badge"
                                  [ngClass]="
                                    getPermissionLevelClass(perm.level)
                                  "
                                >
                                  {{ perm.level }}
                                </span>
                              </div>
                            </div>

                            <div class="perm-email">
                              {{
                                perm.subtext || perm.email || 'Workspace Member'
                              }}
                            </div>

                            <div class="perm-meta-row">
                              <span
                                class="perm-source-badge"
                                [ngClass]="getAccessSourceClass(perm.source)"
                              >
                                @if (
                                  perm.source === 'INHERITED' &&
                                  perm.inheritedFrom
                                ) {
                                  Inherited from {{ perm.inheritedFrom }}
                                } @else if (perm.source === 'ORGANIZATION') {
                                  Organization Policy
                                } @else if (perm.source === 'TEAM') {
                                  Team Permission
                                } @else if (perm.source === 'WORKSPACE') {
                                  Workspace Default
                                } @else if (perm.source === 'COLLABORATION') {
                                  Collaboration Group
                                } @else {
                                  Direct Permission
                                }
                              </span>
                              <span class="perm-meta-dot">•</span>
                              <span
                                class="perm-created-text"
                                [title]="
                                  'Added: ' +
                                  (perm.grantedAt ||
                                    perm.createdAt ||
                                    'Recently')
                                "
                              >
                                <app-icon
                                  name="calendar"
                                  [size]="12"
                                ></app-icon>
                                Added
                                {{
                                  formatRelativeTime(
                                    (perm.grantedAt || perm.createdAt)!
                                  )
                                }}
                              </span>
                              @if (perm.expiresAt) {
                                <span class="perm-meta-dot">•</span>
                                <span
                                  class="perm-expiration-text"
                                  [class.expiring-soon]="
                                    formatRemainingTime(perm.expiresAt) !==
                                      'Expired' &&
                                    formatRemainingTime(perm.expiresAt) !==
                                      'No limit'
                                  "
                                  [class.expired]="
                                    formatRemainingTime(perm.expiresAt) ===
                                    'Expired'
                                  "
                                >
                                  <app-icon name="clock" [size]="12"></app-icon>
                                  {{ formatRemainingTime(perm.expiresAt) }}
                                </span>
                              }
                            </div>
                          </div>

                          <!-- Row actions -->
                          <div
                            class="row-action-anchor"
                            (click)="$event.stopPropagation()"
                          >
                            @if (
                              perm.canRevoke ||
                              perm.source === 'DIRECT' ||
                              perm.permissionId != null
                            ) {
                              <button
                                type="button"
                                class="btn-row-delete"
                                title="Revoke permission"
                                aria-label="Revoke permission"
                                (click)="onRevokeCardPermission(perm, $event)"
                              >
                                <app-icon name="trash-2" [size]="14"></app-icon>
                              </button>
                            }
                            <button
                              type="button"
                              class="btn-row-action"
                              [class.is-active]="
                                openMenuPermId() === (perm.id ?? perm.name)
                              "
                              [attr.aria-label]="'Actions for ' + perm.name"
                              [attr.aria-expanded]="
                                openMenuPermId() === (perm.id ?? perm.name)
                              "
                              aria-haspopup="menu"
                              title="More actions"
                              tabindex="0"
                              (click)="
                                toggleRowMenu(perm.id ?? perm.name, $event)
                              "
                            >
                              <app-icon
                                name="more-vertical"
                                [size]="14"
                              ></app-icon>
                            </button>

                            <!-- Action menu popover -->
                            @if (openMenuPermId() === (perm.id ?? perm.name)) {
                              <div
                                class="row-action-menu"
                                role="menu"
                                [attr.aria-label]="'Actions for ' + perm.name"
                                (keydown)="onMenuKeydown($event)"
                              >
                                @for (
                                  action of getMenuActions(perm);
                                  track action.id
                                ) {
                                  <button
                                    type="button"
                                    class="menu-item"
                                    [class.is-destructive]="action.destructive"
                                    role="menuitem"
                                    tabindex="0"
                                    (click)="handleMenuAction(action.id, perm)"
                                  >
                                    <span
                                      class="menu-item-icon"
                                      [innerHTML]="action.icon"
                                    ></span>
                                    <span class="menu-item-label">{{
                                      action.label
                                    }}</span>
                                  </button>
                                }
                              </div>
                            }
                          </div>
                        </div>
                      } @empty {
                        <div class="perm-empty-state">
                          <div class="empty-icon-wrapper">
                            <app-icon name="search" [size]="24"></app-icon>
                          </div>
                          <p class="empty-title">No access entries found</p>
                          <p class="empty-sub">
                            @if (accessSearchQuery()) {
                              No results matching "{{ accessSearchQuery() }}"
                            } @else {
                              No entries in this category
                            }
                          </p>
                          @if (
                            accessSearchQuery() ||
                            activeAccessFilterCategory() !== 'ALL'
                          ) {
                            <button
                              type="button"
                              class="btn-reset-filters"
                              (click)="
                                clearAccessSearch();
                                setAccessFilterCategory('ALL')
                              "
                            >
                              Reset Filters
                            </button>
                          }
                        </div>
                      }
                    </div>
                  }
                </div>

                <!-- 3. Active Share Links Section (if any present) -->
                @if (effectivePermissions()?.activeShareLinks?.length) {
                  <div class="section-divider"></div>
                  <div class="access-section">
                    <div class="section-header">
                      <span class="section-title">Active Share Links</span>
                      <span class="section-subtitle"
                        >{{
                          effectivePermissions()?.activeShareLinks?.length
                        }}
                        active link(s)</span
                      >
                    </div>

                    <div class="share-links-list">
                      @for (
                        link of effectivePermissions()?.activeShareLinks;
                        track link.id
                      ) {
                        <div
                          class="share-link-card clickable"
                          tabindex="0"
                          role="button"
                          (click)="openShareLinkDetailModal(link)"
                          (keydown.enter)="openShareLinkDetailModal(link)"
                          (keydown.space)="
                            openShareLinkDetailModal(link);
                            $event.preventDefault()
                          "
                        >
                          <div class="link-icon-box">
                            <app-icon name="link" [size]="14"></app-icon>
                          </div>
                          <div class="link-details">
                            <div class="link-token-row">
                              <span class="link-token-text">
                                @if (link.token) {
                                  .../share/{{ link.token.substring(0, 8) }}...
                                } @else {
                                  Share Link #{{ link.id }}
                                }
                              </span>
                              <span class="link-badge">{{
                                link.permissionLevel
                              }}</span>
                              @if (link.passwordProtected) {
                                <span
                                  class="lock-badge"
                                  title="Password Protected"
                                >
                                  <app-icon
                                    name="lock-keyhole"
                                    [size]="12"
                                  ></app-icon>
                                </span>
                              }
                            </div>
                            <div class="link-meta">
                              <span>{{
                                formatRemainingTime(link.expiresAt)
                              }}</span>
                              @if (link.createdByUserEmail) {
                                <span> • {{ isLinkCreator(link) ? 'Created by You' : 'Created by ' + link.createdByUserEmail }}</span>
                              }
                            </div>
                          </div>
                          <div
                            class="link-actions"
                            (click)="$event.stopPropagation()"
                          >
                            @if (canRevealShareLink(link)) {
                              <button
                                type="button"
                                class="btn-copy-sm"
                                (click)="copyOrRevealShareLink(link)"
                                title="Copy Link"
                              >
                                @if (copiedShareToken() === (link.token || link.id?.toString())) {
                                  <app-icon name="check" [size]="12"></app-icon>
                                  <span>Copied!</span>
                                } @else {
                                  <app-icon name="copy" [size]="12"></app-icon>
                                  <span>Copy</span>
                                }
                              </button>
                            }
                            <button
                              type="button"
                              class="btn-revoke-sm"
                              (click)="revokeShareLinkById(link.id)"
                              title="Revoke Share Link"
                            >
                              <app-icon name="x" [size]="14"></app-icon>
                            </button>
                          </div>
                        </div>
                      }
                    </div>
                  </div>
                }

                <div class="section-divider"></div>

                @if (item()?.type === 'document' && item()?.data?.fileHash) {
                  <div class="section-divider"></div>
                  <div class="hash-section">
                    <div class="hash-header">
                      <span class="hash-title">
                        <svg
                          viewBox="0 0 16 16"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                        >
                          <rect x="3" y="7" width="10" height="7" rx="1" />
                          <path d="M5 7V5a3 3 0 0 1 6 0v2" />
                        </svg>
                        SHA-256 Checksum
                      </span>
                      <button
                        type="button"
                        class="copy-btn"
                        (click)="copyHash(item()?.data?.fileHash)"
                      >
                        @if (copiedHash()) {
                          <span>Copied!</span>
                        } @else {
                          <svg
                            viewBox="0 0 16 16"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.5"
                          >
                            <rect x="5" y="5" width="8" height="8" rx="1" />
                            <path d="M3 11V3a1 1 0 0 1 1-1h8" />
                          </svg>
                          <span>Copy</span>
                        }
                      </button>
                    </div>
                    <div class="hash-box">{{ item()?.data?.fileHash }}</div>
                  </div>
                } @else if (item()?.type === 'folder') {
                  <div class="section-divider"></div>
                  <div class="access-section">
                    <div class="section-header">
                      <span class="section-title"
                        >Folder Link Preview Exclusions</span
                      >
                      <span class="section-subtitle">
                        {{
                          getExcludedTotalCount() > 0
                            ? getExcludedTotalCount() +
                              ' item(s) configured for exclusion'
                            : 'No exclusions configured'
                        }}
                      </span>
                    </div>
                    <p class="exclusions-card-desc">
                      Child files or subfolders configured with exclusions will
                      be hidden and inaccessible through folder-level
                      permissions or links.
                    </p>

                    @if (isLoadingFolderContents()) {
                      <div class="ex-display-loading">
                        <div class="spinner-sm"></div>
                        <span>Loading folder contents...</span>
                      </div>
                    } @else if (getExcludedTotalCount() === 0) {
                      <div class="no-exclusions-note">
                        <svg
                          viewBox="0 0 16 16"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="1.5"
                        >
                          <path
                            d="M8 1.5l5.5 2.5v4.5c0 3.5-2.5 6.5-5.5 7.5-3-1-5.5-4-5.5-7.5V4L8 1.5z"
                          />
                          <path d="M6 7.5l1.5 1.5 3-3" />
                        </svg>
                        <span
                          >All child contents are currently included in folder
                          permissions.</span
                        >
                      </div>
                    } @else if (
                      folderContents()?.subFolders?.length ||
                      folderContents()?.documents?.length
                    ) {
                      <div class="active-exclusions-list">
                        @for (sf of folderContents()?.subFolders; track sf.id) {
                          @if (isSubfolderExcluded(sf.id)) {
                            <div class="ex-display-row">
                              <div
                                class="ex-display-icon-wrap folder-icon-wrap"
                              >
                                <svg
                                  viewBox="0 0 24 24"
                                  fill="none"
                                  stroke="currentColor"
                                  stroke-width="1.75"
                                  stroke-linecap="round"
                                  stroke-linejoin="round"
                                >
                                  <path
                                    d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"
                                  />
                                </svg>
                              </div>
                              <span class="ex-display-name">{{ sf.name }}</span>
                              <span class="ex-display-pill excluded"
                                >Excluded</span
                              >
                            </div>
                          }
                        }
                        @for (
                          doc of folderContents()?.documents;
                          track doc.id
                        ) {
                          @if (isDocumentExcluded(+doc.id)) {
                            <div class="ex-display-row">
                              <div class="ex-display-icon-wrap doc-icon-wrap">
                                <svg
                                  viewBox="0 0 24 24"
                                  fill="none"
                                  stroke="currentColor"
                                  stroke-width="1.75"
                                  stroke-linecap="round"
                                  stroke-linejoin="round"
                                >
                                  <path
                                    d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"
                                  />
                                  <polyline points="14 2 14 8 20 8" />
                                </svg>
                              </div>
                              <span class="ex-display-name">{{
                                doc.name || doc.fileName || 'Untitled File'
                              }}</span>
                              <span class="ex-display-pill excluded"
                                >Excluded</span
                              >
                            </div>
                          }
                        }
                      </div>
                    }
                  </div>

                  <div class="section-divider"></div>
                  <div class="info-note">
                    <svg
                      viewBox="0 0 16 16"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="1.5"
                    >
                      <circle cx="8" cy="8" r="6" />
                      <line x1="8" y1="8" x2="8" y2="11" />
                      <line x1="8" y1="5" x2="8.01" y2="5" />
                    </svg>
                    <span
                      >Folder contents inherit workspace security and access
                      control settings.</span
                    >
                  </div>
                }
              </div>
              }
            }
          </div>
        </div>

        <app-share-modal
          [isOpen]="isShareModalOpen()"
          [item]="item()"
          (closed)="closeShareModal()"
          (permissionGranted)="fetchPermissions()"
          (shareLinkCreated)="handleShareLinkCreated($event)"
        />

        <!-- PERMISSION DETAIL MODAL -->
        <app-permission-detail-modal
          [isOpen]="isPermissionDetailOpen()"
          [permission]="selectedPermissionForDetail()"
          [item]="item()"
          [folderContents]="folderContents()"
          (closed)="closePermissionDetailModal()"
          (updated)="handlePermissionUpdated()"
          (revoked)="handlePermissionRevoked()"
        />

        <!-- SHARE LINK DETAIL MODAL -->
        <app-share-link-detail-modal
          [isOpen]="isShareLinkDetailOpen()"
          [shareLink]="selectedShareLinkForDetail()"
          [item]="item()"
          (closed)="closeShareLinkDetailModal()"
          (revoked)="handleShareLinkRevoked()"
        />
      </div>
    }
  `,
  styles: [
    `
      .drawer-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(15, 23, 42, 0.35);
        backdrop-filter: blur(4px);
        z-index: var(--sd-z-drawer-backdrop, 2000);
        display: flex;
        justify-content: flex-end;
        animation: fadeIn 0.15s ease-out;
      }
      @keyframes fadeIn {
        from {
          opacity: 0;
        }
        to {
          opacity: 1;
        }
      }

      .drawer-panel {
        width: 100%;
        max-width: 400px;
        height: 100%;
        background: #ffffff;
        box-shadow: -8px 0 32px rgba(15, 23, 42, 0.14);
        display: flex;
        flex-direction: column;
        animation: slideLeft 0.18s cubic-bezier(0.16, 1, 0.3, 1);
      }
      @keyframes slideLeft {
        from {
          transform: translateX(100%);
        }
        to {
          transform: translateX(0);
        }
      }

      /* ── Header Redesign ── */
      .drawer-header {
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
        padding: 1.125rem 1.25rem 1rem;
        background: #ffffff;
        border-bottom: 1px solid #e2e8f0;
      }
      .header-tree-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
      }
      .location-breadcrumb {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        min-width: 0;
        flex: 1;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        padding: 0.35rem 0.625rem;
      }
      .root-tree-icon {
        color: #64748b;
        flex-shrink: 0;
      }
      .crumb-list {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        overflow: hidden;
        white-space: nowrap;
        font-size: 0.75rem;
        color: #64748b;
        font-weight: 500;
      }
      .crumb-sep {
        color: #94a3b8;
        flex-shrink: 0;
      }
      .crumb-item {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        color: #64748b;
      }
      .crumb-item.is-leaf {
        font-weight: 600;
        color: #0f172a;
      }
      .close-btn {
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        cursor: pointer;
        color: #64748b;
        padding: 0.375rem;
        border-radius: 0.5rem;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        transition: all 0.15s ease;
      }
      .close-btn:hover {
        background: #f1f5f9;
        color: #0f172a;
        border-color: #cbd5e1;
      }

      .header-main-row {
        display: flex;
        align-items: flex-start;
        gap: 0.875rem;
        min-width: 0;
      }
      .header-hero-icon {
        width: 2.75rem;
        height: 2.75rem;
        border-radius: 0.625rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        color: #334155;
      }
      .header-title-meta {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
        min-width: 0;
        flex: 1;
      }
      .header-item-name {
        font-size: 1.0625rem;
        font-weight: 700;
        color: #0f172a;
        margin: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        line-height: 1.3;
        letter-spacing: -0.01em;
      }
      .header-badges-row {
        display: flex;
        align-items: center;
        flex-wrap: wrap;
        gap: 0.375rem;
      }
      .header-space-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.3125rem;
        padding: 0.1875rem 0.5rem;
        border-radius: 9999px;
        background: #f8fafc;
        color: #475569;
        border: 1px solid #e2e8f0;
        font-size: 0.6875rem;
        font-weight: 600;
      }
      .header-space-badge.org-space {
        background: #f8fafc;
        color: #334155;
        border-color: #e2e8f0;
      }
      .type-meta-badge {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #475569;
        background: #f8fafc;
        padding: 0.1875rem 0.5rem;
        border-radius: 9999px;
        border: 1px solid #e2e8f0;
      }
      .size-meta-badge {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #64748b;
        background: #f8fafc;
        padding: 0.1875rem 0.5rem;
        border-radius: 9999px;
        border: 1px solid #e2e8f0;
      }

      /* ── Navigation Tabs (Pill Style) ── */
      .drawer-tabs-wrapper {
        padding: 0.5rem 1.25rem 0.25rem;
        background: #ffffff;
        border-bottom: 1px solid #f1f5f9;
      }
      .drawer-tabs {
        display: flex;
        background: #f1f5f9;
        border-radius: 0.625rem;
        padding: 0.1875rem;
        gap: 0.1875rem;
      }
      .tab-btn {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        padding: 0.5rem 0.75rem;
        font-size: 0.75rem;
        font-weight: 600;
        color: #64748b;
        border: none;
        background: transparent;
        border-radius: 0.5rem;
        cursor: pointer;
        transition: all 0.15s ease;
      }
      .tab-btn svg {
        width: 0.875rem;
        height: 0.875rem;
      }
      .tab-btn:hover {
        color: #0f172a;
      }
      .tab-btn.active {
        color: #ff5500;
        background: #ffffff;
        box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
        border: 1px solid #fde8db;
      }

      /* ── Body ── */
      .drawer-body {
        flex: 1;
        overflow-y: auto;
        padding: 1.25rem;
      }
      .tab-pane {
        display: flex;
        flex-direction: column;
      }
      .overview-pane {
        gap: 0;
      }

      .overview-section {
        display: flex;
        flex-direction: column;
        gap: 0.625rem;
      }

      .section-divider {
        height: 1px;
        background: #f1f5f9;
        margin: 1.125rem 0;
      }

      .section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.125rem;
      }
      .section-title {
        font-size: 0.6875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #94a3b8;
      }
      .section-subtitle {
        font-size: 0.6875rem;
        font-weight: 500;
        color: #64748b;
      }

      /* ── Overview Primary Cards: File Preview vs Folder Overview ── */

      /* 1A. File Preview Card */
      .file-preview-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.875rem;
        padding: 1rem;
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
        box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      }
      .file-preview-banner {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        background: #f8fafc;
        border: 1px solid #f1f5f9;
        border-radius: 0.625rem;
        padding: 0.75rem 0.875rem;
      }
      .preview-thumb-box {
        width: 3rem;
        height: 3rem;
        border-radius: 0.625rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        color: #334155;
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
      }
      .preview-thumb-box svg {
        width: 1.5rem;
        height: 1.5rem;
      }

      .preview-badge-row {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 0.375rem;
      }
      .viewable-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.2rem 0.5rem;
        border-radius: 9999px;
        background: #f0fdf4;
        color: #166534;
        border: 1px solid #bbf7d0;
        font-size: 0.6875rem;
        font-weight: 600;
      }
      .viewable-badge app-icon,
      .viewable-badge svg {
        display: inline-flex;
        width: 0.75rem;
        height: 0.75rem;
      }
      .ext-tag {
        font-size: 0.625rem;
        font-weight: 700;
        color: #475569;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        padding: 0.125rem 0.375rem;
        border-radius: 0.25rem;
        letter-spacing: 0.05em;
      }

      .file-preview-details {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.75rem;
        padding-top: 0.5rem;
        border-top: 1px solid #f1f5f9;
      }
      .preview-meta-info {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;
      }
      .preview-filename {
        font-size: 0.84375rem;
        font-weight: 700;
        color: #0f172a;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .preview-submeta {
        font-size: 0.71875rem;
        font-weight: 500;
        color: #64748b;
        margin-top: 0.125rem;
      }

      .btn-full-preview {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        background: #0f172a;
        color: #ffffff;
        border: 1px solid #0f172a;
        border-radius: 9999px;
        padding: 0.4375rem 0.875rem;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s cubic-bezier(0.16, 1, 0.3, 1);
        box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
        flex-shrink: 0;
      }
      .btn-full-preview:hover {
        background: #1e293b;
        border-color: #1e293b;
        transform: translateY(-1px);
        box-shadow: 0 3px 6px rgba(0, 0, 0, 0.1);
      }
      .btn-full-preview:active {
        transform: translateY(0);
      }
      .btn-full-preview app-icon,
      .btn-full-preview svg {
        width: 0.875rem;
        height: 0.875rem;
      }

      /* 1B. Folder Overview & Inner Items Card */
      .folder-overview-card {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.875rem;
        padding: 1rem;
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
        box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      }
      .folder-stats-header {
        display: flex;
        align-items: center;
        justify-content: space-around;
        padding: 0.625rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.625rem;
      }
      .folder-stat-item {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.125rem;
      }
      .stat-num {
        font-size: 1rem;
        font-weight: 700;
        color: #0f172a;
      }
      .stat-lbl {
        font-size: 0.6875rem;
        font-weight: 500;
        color: #64748b;
      }
      .stat-divider {
        width: 1px;
        height: 1.5rem;
        background: #e2e8f0;
      }

      .inner-items-container {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .inner-items-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .inner-items-title {
        font-size: 0.75rem;
        font-weight: 700;
        color: #0f172a;
      }
      .inner-items-count {
        font-size: 0.6875rem;
        font-weight: 500;
        color: #64748b;
      }

      .inner-items-list {
        max-height: 180px;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        padding: 0.375rem;
      }
      .inner-item-row {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.375rem 0.5rem;
        border-radius: 0.375rem;
        transition: background 0.12s ease;
      }
      .inner-item-row:hover {
        background: #f8fafc;
      }
      .inner-item-icon {
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 0.25rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      .inner-item-icon svg {
        width: 1rem;
        height: 1rem;
      }
      .folder-icon {
        color: #475569;
        background: #f1f5f9;
      }
      .doc-icon {
        color: #475569;
        background: #f1f5f9;
      }

      .inner-item-details {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;
      }
      .inner-item-name {
        font-size: 0.75rem;
        font-weight: 600;
        color: #0f172a;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .inner-item-sub {
        font-size: 0.65625rem;
        color: #94a3b8;
      }
      .inner-loading-state,
      .inner-empty-state {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        padding: 1.25rem;
        font-size: 0.75rem;
        color: #64748b;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
      }
      .inner-empty-state svg {
        width: 1.25rem;
        height: 1.25rem;
        color: #cbd5e1;
      }
      .spinner-sm {
        width: 0.875rem;
        height: 0.875rem;
        border: 2px solid #e2e8f0;
        border-top-color: #ff5500;
        border-radius: 50%;
        animation: spin 0.6s linear infinite;
      }
      @keyframes spin {
        to {
          transform: rotate(360deg);
        }
      }

      /* ── 2. Action Statistics & Circular Donut Chart ── */
      .activity-chart-card {
        background: #ffffff;
        border: 1px solid rgba(226, 232, 240, 0.8);
        border-radius: 0.875rem;
        padding: 0.9375rem;
        box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);
        position: relative;
        overflow: hidden;
      }
      .activity-rate-badge {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        background: rgba(255, 247, 237, 0.95);
        color: #c2410c;
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.2rem 0.6rem;
        border-radius: 9999px;
        border: 1px solid rgba(254, 215, 170, 0.9);
      }
      .badge-pulse-dot {
        display: inline-block;
        width: 0.375rem;
        height: 0.375rem;
        border-radius: 50%;
        background-color: #ff5500;
        box-shadow: 0 0 0 0 rgba(255, 85, 0, 0.7);
        animation: badgePulse 1.8s infinite cubic-bezier(0.66, 0, 0, 1);
        flex-shrink: 0;
      }
      @keyframes badgePulse {
        0% {
          transform: scale(0.95);
          box-shadow: 0 0 0 0 rgba(255, 85, 0, 0.7);
        }
        70% {
          transform: scale(1);
          box-shadow: 0 0 0 6px rgba(255, 85, 0, 0);
        }
        100% {
          transform: scale(0.95);
          box-shadow: 0 0 0 0 rgba(255, 85, 0, 0);
        }
      }
      .header-icon-box {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.5rem;
        height: 1.5rem;
        border-radius: 0.375rem;
        background: rgba(255, 85, 0, 0.1);
        border: 1px solid rgba(255, 85, 0, 0.18);
        color: #ff5500;
        flex-shrink: 0;
      }
      .header-icon-box svg {
        width: 0.875rem !important;
        height: 0.875rem !important;
        stroke: #ff5500;
      }
      .activity-chart-body {
        display: flex;
        align-items: center;
        gap: 1rem;
        margin-top: 0.375rem;
      }
      .chart-donut-wrapper {
        position: relative;
        width: 5.5rem;
        height: 5.5rem;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .chart-donut-svg {
        width: 100%;
        height: 100%;
        filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.03));
      }
      .donut-arc-animated {
        transition: stroke-dashoffset 0.6s cubic-bezier(0.4, 0, 0.2, 1);
      }
      .donut-center-info {
        position: absolute;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        pointer-events: none;
      }
      .donut-center-val {
        font-size: 1.125rem;
        font-weight: 700;
        color: #0f172a;
        line-height: 1;
        letter-spacing: -0.02em;
      }
      .donut-center-lbl {
        font-size: 0.5625rem;
        font-weight: 600;
        color: #64748b;
        letter-spacing: 0.06em;
        margin-top: 0.1875rem;
        text-transform: uppercase;
      }
      .chart-stats-details {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: 0.4375rem;
        min-width: 0;
      }
      .metric-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        padding: 0.4375rem 0.625rem;
        background: #f8fafc;
        border: 1px solid rgba(241, 245, 249, 0.9);
        border-radius: 0.625rem;
      }
      .metric-info-left {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        min-width: 0;
      }
      .metric-icon-badge {
        width: 1.875rem;
        height: 1.875rem;
        border-radius: 0.5rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      .metric-icon-badge svg {
        width: 0.9375rem;
        height: 0.9375rem;
      }
      .open-icon-bg {
        background: rgba(255, 237, 213, 0.85);
        border: 1px solid rgba(254, 215, 170, 0.9);
        color: #ea580c;
      }
      .download-icon-bg {
        background: rgba(254, 243, 199, 0.85);
        border: 1px solid rgba(253, 230, 138, 0.9);
        color: #d97706;
      }
      .metric-text-group {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }
      .metric-title {
        font-size: 0.75rem;
        font-weight: 600;
        color: #1e293b;
        line-height: 1.2;
      }
      .metric-sub {
        font-size: 0.6875rem;
        color: #64748b;
        line-height: 1.2;
      }
      .metric-rate-badge {
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.15rem 0.45rem;
        border-radius: 0.375rem;
        flex-shrink: 0;
      }
      .open-badge {
        background: rgba(255, 247, 237, 0.9);
        color: #ea580c;
        border: 1px solid rgba(254, 215, 170, 0.8);
      }
      .download-badge {
        background: rgba(254, 243, 199, 0.8);
        color: #d97706;
        border: 1px solid rgba(253, 230, 138, 0.8);
      }
      .rate-progress-stacked {
        height: 0.4375rem;
        width: 100%;
        background: #f1f5f9;
        border-radius: 9999px;
        overflow: hidden;
        display: flex;
        margin-top: 0.25rem;
        box-shadow: inset 0 1px 2px rgba(0, 0, 0, 0.04);
        padding: 1px;
      }
      .stacked-segment {
        height: 100%;
        border-radius: 9999px;
        transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
      }
      .open-segment {
        background: linear-gradient(90deg, #ff7733, #ff5500);
        box-shadow: 0 0 6px rgba(255, 85, 0, 0.3);
      }
      .download-segment {
        background: linear-gradient(90deg, #fdba74, #fb923c);
        box-shadow: 0 0 6px rgba(251, 146, 60, 0.3);
      }

      /* ── 3. Ownership & Location Card Styling ── */
      .space-owner-card {
        background: rgba(255, 255, 255, 0.85);
        backdrop-filter: blur(8px);
        -webkit-backdrop-filter: blur(8px);
        border: 1px solid rgba(226, 232, 240, 0.8);
        border-radius: 0.875rem;
        padding: 0.875rem;
        box-shadow:
          0 1px 3px rgba(15, 23, 42, 0.04),
          0 1px 2px rgba(15, 23, 42, 0.02);
        transition: all 0.2s ease-in-out;
      }
      .space-owner-card:hover {
        border-color: rgba(255, 85, 0, 0.3);
        box-shadow: 0 4px 12px rgba(255, 85, 0, 0.05);
      }
      .card-header-sm {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.75rem;
      }
      .card-header-sm svg {
        width: 0.9375rem;
        height: 0.9375rem;
        color: #ff5500;
      }
      .card-title-sm {
        font-size: 0.71875rem;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #475569;
        margin-left: 0.375rem;
      }
      .header-title-flex {
        display: flex;
        align-items: center;
      }

      .owner-card-body {
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
      }
      .owner-person-row {
        display: flex;
        align-items: center;
        gap: 0.75rem;
      }
      .owner-avatar-lg {
        width: 2.375rem;
        height: 2.375rem;
        border-radius: 50%;
        background: linear-gradient(135deg, #fff7f3 0%, #ffedd5 100%);
        color: #ff5500;
        border: 1.5px solid #fde8db;
        font-size: 0.8125rem;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        box-shadow: 0 2px 4px rgba(255, 85, 0, 0.1);
      }
      .owner-person-info {
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
        min-width: 0;
        flex: 1;
      }
      .owner-name-line {
        display: flex;
        align-items: center;
        gap: 0.375rem;
      }
      .owner-name-bold {
        font-size: 0.84375rem;
        font-weight: 700;
        color: #0f172a;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .owner-role-badge {
        font-size: 0.625rem;
        font-weight: 700;
        text-transform: uppercase;
        padding: 0.0625rem 0.375rem;
        border-radius: 0.25rem;
        background: #fff7f3;
        color: #ea580c;
        border: 1px solid #ffedd5;
      }
      .owner-email-sub {
        font-size: 0.71875rem;
        color: #64748b;
        font-weight: 400;
      }
      .owner-timestamp {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.6875rem;
        color: #94a3b8;
        font-weight: 500;
        margin-top: 0.125rem;
      }
      .owner-timestamp svg {
        width: 0.75rem;
        height: 0.75rem;
        color: #94a3b8;
      }

      .owner-card-divider {
        height: 1px;
        background: #f1f5f9;
        width: 100%;
      }

      .location-space-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        flex-wrap: wrap;
      }
      .space-badge-box {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.25rem 0.625rem;
        border-radius: 0.5rem;
        background: #f8fafc;
        color: #334155;
        font-size: 0.71875rem;
        font-weight: 600;
        border: 1px solid #e2e8f0;
      }
      .space-badge-box.org-space {
        background: #f0fdf4;
        color: #166534;
        border-color: #bbf7d0;
      }
      .space-badge-box svg {
        width: 0.8125rem;
        height: 0.8125rem;
      }

      .location-path-pill {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.25rem 0.625rem;
        border-radius: 0.5rem;
        background: #f8fafc;
        color: #475569;
        font-size: 0.71875rem;
        font-weight: 500;
        border: 1px solid #e2e8f0;
        max-width: 170px;
      }
      .location-path-pill svg {
        width: 0.8125rem;
        height: 0.8125rem;
        color: #94a3b8;
        flex-shrink: 0;
      }
      .location-path-pill .path-text {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      /* ── 4. Activity & Audit Intelligence Card (Cloudy Border & Scrollable) ── */
      .audit-card-cloudy {
        background: rgba(255, 255, 255, 0.85);
        backdrop-filter: blur(8px);
        -webkit-backdrop-filter: blur(8px);
        border: 1px solid rgba(226, 232, 240, 0.8);
        border-radius: 0.875rem;
        padding: 0.875rem;
        box-shadow:
          0 1px 3px rgba(15, 23, 42, 0.04),
          0 1px 2px rgba(15, 23, 42, 0.02);
        transition: all 0.2s ease-in-out;
      }
      .audit-card-cloudy:hover {
        border-color: rgba(255, 85, 0, 0.25);
      }
      .audit-count-badge {
        font-size: 0.625rem;
        font-weight: 700;
        color: #64748b;
        background: #f1f5f9;
        padding: 0.125rem 0.375rem;
        border-radius: 9999px;
        border: 1px solid #e2e8f0;
      }

      .activity-feed-scrollable {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        max-height: 220px;
        overflow-y: auto;
        padding-right: 0.25rem;
      }
      .feed-item-cloudy {
        display: flex;
        align-items: flex-start;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        background: #ffffff;
        border: 1px solid #f1f5f9;
        border-radius: 0.625rem;
        transition: all 0.15s ease;
      }
      .feed-item-cloudy:hover {
        background: #fff7f3;
        border-color: #fde8db;
      }
      .feed-avatar-cloudy {
        width: 1.625rem;
        height: 1.625rem;
        border-radius: 50%;
        background: #fff7f3;
        color: #ff5500;
        border: 1px solid #fde8db;
        font-size: 0.65625rem;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        margin-top: 0.125rem;
      }
      .feed-content {
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
        min-width: 0;
        flex: 1;
      }
      .feed-text {
        font-size: 0.78125rem;
        color: #0f172a;
        line-height: 1.35;
        word-break: break-word;
      }
      .feed-user {
        font-weight: 600;
        color: #0f172a;
        margin-right: 0.25rem;
      }
      .feed-action {
        color: #475569;
        font-weight: 400;
      }
      .feed-meta {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.6875rem;
        color: #94a3b8;
        font-weight: 500;
      }
      .meta-time {
        font-size: 0.6875rem;
        color: #64748b;
        font-weight: 500;
      }
      .meta-ua {
        font-size: 0.6875rem;
        color: #94a3b8;
      }
      .meta-dot {
        font-size: 0.5rem;
        color: #cbd5e1;
      }
      .audit-loading {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: 0.75rem;
        color: #94a3b8;
        padding: 0.5rem 0;
        font-style: italic;
      }
      .audit-empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 1.25rem 0.5rem;
        text-align: center;
        color: #94a3b8;
        gap: 0.5rem;
      }
      .audit-empty-state svg {
        width: 1.5rem;
        height: 1.5rem;
        color: #cbd5e1;
        stroke-width: 1.5;
      }
      .audit-empty-state p {
        margin: 0;
        font-size: 0.75rem;
        color: #64748b;
        font-weight: 500;
      }

      /* Custom scrollbar styling: hidden by default, handle appears on container hover */
      .custom-scrollbar {
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;
        transition: scrollbar-color 0.2s ease;
      }
      .custom-scrollbar:hover {
        scrollbar-color: #cbd5e1 transparent;
      }
      .custom-scrollbar::-webkit-scrollbar {
        width: 5px;
        height: 5px;
      }
      .custom-scrollbar::-webkit-scrollbar-track {
        background: transparent;
      }
      .custom-scrollbar::-webkit-scrollbar-thumb {
        background: transparent;
        border-radius: 9999px;
        transition: background-color 0.2s ease;
      }
      .custom-scrollbar:hover::-webkit-scrollbar-thumb {
        background: #cbd5e1;
      }
      .custom-scrollbar::-webkit-scrollbar-thumb:hover {
        background: #94a3b8;
      }

      /* ── Access & Security Tab Styling (Explorer Visual Language Alignment) ── */
      .access-pane {
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
      }
      .access-section {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      /* 1. Access Header & Your Access Card (Overview Visual Language Alignment) */
      /* =========================================================
   ACCESS & SECURITY — REDESIGNED
   ========================================================= */

.access-header-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 0.875rem;
  overflow: hidden;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.03),
    0 8px 24px rgba(15, 23, 42, 0.035);
}

/* Header -------------------------------------------------- */

.access-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1rem 0.9rem;
}

.access-card-heading {
  min-width: 0;
  flex: 1;
}

.access-title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.access-header-title {
  margin: 0;
  color: #111827;
  font-size: 0.95rem;
  line-height: 1.25;
  font-weight: 650;
  letter-spacing: -0.015em;
}

.access-header-sub {
  margin: 0.3rem 0 0;
  color: #6b7280;
  font-size: 0.72rem;
  line-height: 1.45;
}

/* Small contextual status instead of giant badge */
.access-status {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: #6b7280;
  font-size: 0.62rem;
  font-weight: 550;
  white-space: nowrap;
}

.access-status-dot {
  width: 0.38rem;
  height: 0.38rem;
  border-radius: 999px;
  background: #22c55e;
  flex-shrink: 0;
}

/* Share action -------------------------------------------- */

.access-share-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  flex-shrink: 0;

  min-height: 2rem;
  padding: 0.4rem 0.72rem;

  border: 1px solid #ff5500;
  border-radius: 0.5rem;

  background: #ff5500;
  color: #ffffff;

  font-size: 0.7rem;
  font-weight: 600;

  cursor: pointer;

  transition:
    background-color 0.16s ease,
    border-color 0.16s ease,
    box-shadow 0.16s ease,
    transform 0.12s ease;
}

.access-share-button:hover {
  background: #ed4f00;
  border-color: #ed4f00;
  box-shadow: 0 4px 12px rgba(255, 85, 0, 0.18);
  transform: translateY(-1px);
}

.access-share-button:active {
  transform: translateY(0);
  box-shadow: none;
}

.access-share-button:focus-visible {
  outline: 2px solid rgba(255, 85, 0, 0.35);
  outline-offset: 2px;
}

/* Your access --------------------------------------------- */

.your-access-section {
  margin: 0 0.75rem 0.75rem;
  padding: 0.8rem;
  border: 1px solid #eef0f2;
  border-radius: 0.7rem;
  background: #fafafa;
}

.your-access-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  margin-bottom: 0.7rem;
}

.section-eyebrow {
  color: #9ca3af;
  font-size: 0.6rem;
  line-height: 1;
  font-weight: 700;
  letter-spacing: 0.08em;
}

/* Permission level */
.access-level {
  display: inline-flex;
  align-items: center;
  min-height: 1.45rem;
  padding: 0.18rem 0.45rem;

  border: 1px solid #fed7aa;
  border-radius: 0.35rem;

  background: #fff7ed;
  color: #c2410c;

  font-size: 0.6rem;
  font-weight: 700;
  letter-spacing: 0.05em;
}

/* Identity ----------------------------------------------- */

.your-access-identity {
  display: flex;
  align-items: center;
  gap: 0.7rem;
  min-width: 0;
}

.your-access-avatar {
  width: 2.35rem;
  height: 2.35rem;
  flex: 0 0 auto;

  display: flex;
  align-items: center;
  justify-content: center;

  border: 1px solid #e5e7eb;
  border-radius: 0.65rem;

  background: #f3f4f6;
  color: #374151;

  font-size: 0.7rem;
  font-weight: 700;
}

.your-access-identity-info {
  min-width: 0;
  flex: 1;
}

.your-access-name-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
}

.your-access-name {
  min-width: 0;

  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  color: #111827;
  font-size: 0.78rem;
  font-weight: 650;
}

.you-indicator {
  display: inline-flex;
  align-items: center;

  padding: 0.12rem 0.32rem;

  border-radius: 0.3rem;
  background: #f1f5f9;
  color: #64748b;

  font-size: 0.56rem;
  font-weight: 650;
  line-height: 1.2;
}

.your-access-email {
  display: block;
  margin-top: 0.12rem;

  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;

  color: #6b7280;
  font-size: 0.66rem;
}

.your-access-origin {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.38rem;

  color: #6b7280;
  font-size: 0.62rem;
  line-height: 1.2;
}

.your-access-origin app-icon {
  color: #9ca3af;
  flex-shrink: 0;
}

/* Why access ---------------------------------------------- */

.why-access-disclosure {
  margin-top: 0.75rem;
  padding-top: 0.65rem;
  border-top: 1px solid #e5e7eb;
}

.why-access-button {
  width: 100%;

  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;

  padding: 0.35rem 0.15rem;

  border: 0;
  background: transparent;

  color: #4b5563;

  font-family: inherit;
  font-size: 0.67rem;
  font-weight: 600;

  cursor: pointer;
  border-radius: 0.35rem;

  transition:
    color 0.14s ease,
    background-color 0.14s ease;
}

.why-access-button:hover {
  color: #111827;
  background: #f3f4f6;
}

.why-access-button:focus-visible {
  outline: 2px solid rgba(255, 85, 0, 0.3);
  outline-offset: 1px;
}

.why-access-left {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.why-access-icon {
  display: flex;
  align-items: center;
  justify-content: center;

  color: #9ca3af;
}

.why-access-chevron {
  color: #9ca3af;

  transition:
    transform 0.2s cubic-bezier(0.16, 1, 0.3, 1),
    color 0.14s ease;
}

.why-access-chevron.expanded {
  transform: rotate(180deg);
  color: #6b7280;
}

/* Disclosure body */
.why-access-panel {
  margin-top: 0.45rem;

  padding: 0.55rem 0.65rem;

  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;

  background: #ffffff;

  animation: accessReveal 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes accessReveal {
  from {
    opacity: 0;
    transform: translateY(-3px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.why-access-row {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;

  padding: 0.42rem 0;
}

.why-access-row + .why-access-row {
  border-top: 1px solid #f1f2f4;
}

.why-access-row > app-icon {
  margin-top: 0.08rem;
  color: #9ca3af;
  flex: 0 0 auto;
}

.why-access-row > div {
  min-width: 0;
}

.why-access-row span {
  display: block;

  color: #374151;
  font-size: 0.64rem;
  font-weight: 650;
}

.why-access-row p {
  margin: 0.14rem 0 0;

  color: #6b7280;
  font-size: 0.63rem;
  line-height: 1.45;
}

/* Responsive --------------------------------------------- */

@media (max-width: 420px) {
  .access-card-header {
    align-items: stretch;
  }

  .access-share-button {
    align-self: flex-start;
  }
}

      /* ── People & Groups: Search + Filter Controls ──────────────── */
      .access-controls-bar {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }

      /* Search box */
      .access-search-box {
        position: relative;
        display: flex;
        align-items: center;
      }
      .search-icon {
        position: absolute;
        left: 0.5rem;
        width: 0.8125rem;
        height: 0.8125rem;
        color: #94a3b8;
        pointer-events: none;
        flex-shrink: 0;
      }
      .access-search-input {
        width: 100%;
        height: 2rem;
        padding: 0 2rem 0 1.875rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        font-size: 0.75rem;
        color: #0f172a;
        transition:
          border-color 0.15s ease,
          background 0.15s ease;
        font-family: inherit;
        outline: none;
      }
      .access-search-input::placeholder {
        color: #94a3b8;
      }
      .access-search-input:focus {
        border-color: #ff5500;
        background: #ffffff;
      }
      .btn-clear-search {
        position: absolute;
        right: 0.375rem;
        width: 1.25rem;
        height: 1.25rem;
        display: flex;
        align-items: center;
        justify-content: center;
        background: transparent;
        border: none;
        cursor: pointer;
        color: #94a3b8;
        border-radius: 0.25rem;
        padding: 0;
        transition: color 0.12s ease;
      }
      .btn-clear-search:hover {
        color: #475569;
      }
      .btn-clear-search svg {
        width: 0.75rem;
        height: 0.75rem;
      }

      /* Filter tab bar */
      .access-filter-tabs {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex-wrap: wrap;
      }
      .access-tab-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem 0.5rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        font-size: 0.6875rem;
        font-weight: 500;
        color: #64748b;
        cursor: pointer;
        white-space: nowrap;
        transition:
          background 0.12s ease,
          border-color 0.12s ease,
          color 0.12s ease;
        line-height: 1;
      }
      .access-tab-btn:hover {
        background: #f1f5f9;
        border-color: #cbd5e1;
        color: #334155;
      }
      .access-tab-btn.active {
        background: #fff7f3;
        border-color: #ffedd5;
        color: #c2410c;
        font-weight: 600;
      }
      .tab-icon {
        width: 0.75rem;
        height: 0.75rem;
        flex-shrink: 0;
      }
      .tab-count-badge {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 1rem;
        height: 1rem;
        padding: 0 0.25rem;
        background: #e2e8f0;
        color: #475569;
        border-radius: 0.25rem;
        font-size: 0.625rem;
        font-weight: 700;
        line-height: 1;
      }
      .access-tab-btn.active .tab-count-badge {
        background: #ffedd5;
        color: #c2410c;
      }

      /* ── Empty state ─────────────────────────────────────────────── */
      .perm-empty-state {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        padding: 1.25rem 1rem;
        text-align: center;
      }
      .empty-icon-wrapper {
        width: 2rem;
        height: 2rem;
        display: flex;
        align-items: center;
        justify-content: center;
        color: #cbd5e1;
        flex-shrink: 0;
      }
      .empty-icon-wrapper svg {
        width: 1.5rem;
        height: 1.5rem;
      }
      .empty-title {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #334155;
        margin: 0;
      }
      .empty-sub {
        font-size: 0.6875rem;
        color: #94a3b8;
        margin: 0;
      }
      .btn-reset-filters {
        margin-top: 0.25rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        padding: 0.25rem 0.625rem;
        font-size: 0.6875rem;
        font-weight: 600;
        color: #475569;
        cursor: pointer;
        transition: all 0.12s ease;
      }
      .btn-reset-filters:hover {
        background: #f8fafc;
        border-color: #cbd5e1;
        color: #0f172a;
      }

      .perm-loading,
      .perm-error {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        padding: 0.875rem;
        font-size: 0.75rem;
        color: #64748b;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
      }
      .perm-error {
        color: #ef4444;
        flex-direction: column;
      }
      .btn-retry {
        background: #ffffff;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        padding: 0.25rem 0.5rem;
        font-size: 0.6875rem;
        font-weight: 600;
        color: #334155;
        cursor: pointer;
        transition: all 0.15s ease;
      }
      .btn-retry:hover {
        background: #f8fafc;
        border-color: #94a3b8;
      }

      /* Section Headers */
      .section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        margin-bottom: 0.25rem;
      }
      .section-title {
        font-size: 0.6875rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #64748b;
      }
      .section-subtitle {
        font-size: 0.6875rem;
        color: #94a3b8;
        font-weight: 500;
      }

      /* 2. Active Permissions Rows */
      .perm-list {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
        padding: 0.0625rem 0.125rem;
      }
      .perm-card {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        position: relative;
        overflow: hidden;
        transition:
          background-color 0.12s ease,
          border-color 0.12s ease,
          box-shadow 0.12s ease;
      }
      .perm-card::before {
        content: '';
        position: absolute;
        top: 0;
        bottom: 0;
        left: 0;
        width: 3px;
        background: transparent;
        transition: background-color 0.15s ease;
        pointer-events: none;
      }
      .perm-card:hover {
        background: #f8fafc;
        border-color: #cbd5e1;
        box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
      }
      .perm-card.is-expiring::before {
        background: #f59e0b;
      }
      .perm-card.is-expired::before {
        background: #ef4444;
      }
      .perm-card.is-expired {
        opacity: 0.8;
      }

      /* Avatar - Sleek Neutral Palette */
      .perm-avatar {
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 50%;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #e2e8f0;
        font-size: 0.625rem;
        font-weight: 700;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        letter-spacing: 0.02em;
      }
      .perm-avatar svg {
        width: 0.875rem;
        height: 0.875rem;
        color: #475569;
      }
      .perm-avatar .avatar-crown {
        width: 0.8125rem;
        height: 0.8125rem;
        color: #334155;
      }
      .perm-avatar.team-avatar,
      .perm-avatar.org-avatar,
      .perm-avatar.collab-avatar {
        background: #f1f5f9;
        color: #334155;
        border-color: #e2e8f0;
      }

      /* External badge - Subtle Neutral */
      .perm-external-badge {
        font-size: 0.5625rem;
        font-weight: 600;
        padding: 0.0625rem 0.25rem;
        border-radius: 0.1875rem;
        background: #f8fafc;
        color: #475569;
        border: 1px solid #e2e8f0;
        letter-spacing: 0.03em;
        text-transform: uppercase;
      }

      /* Info column */
      .perm-info {
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
        min-width: 0;
        flex: 1;
      }
      .perm-name-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.375rem;
      }
      .perm-name {
        font-size: 0.78125rem;
        font-weight: 600;
        color: #0f172a;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        -webkit-font-smoothing: antialiased;
      }
      .perm-email {
        font-size: 0.6875rem;
        color: #64748b;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .perm-meta-row {
        display: flex;
        align-items: center;
        gap: 0.3125rem;
        margin-top: 0.125rem;
        flex-wrap: wrap;
      }
      .perm-meta-dot {
        font-size: 0.5rem;
        color: #cbd5e1;
        line-height: 1;
      }
      .perm-created-text {
        display: flex;
        align-items: center;
        gap: 0.1875rem;
        font-size: 0.625rem;
        color: #64748b;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
      }
      .perm-created-text svg {
        width: 0.6875rem;
        height: 0.6875rem;
        flex-shrink: 0;
        color: #94a3b8;
      }
      .perm-badges-group {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        flex-shrink: 0;
      }

      /* Level Badges - Clean Monochrome SaaS Style */
      .perm-level-badge {
        font-size: 0.5625rem;
        font-weight: 600;
        text-transform: uppercase;
        padding: 0.09375rem 0.3125rem;
        border-radius: 0.25rem;
        letter-spacing: 0.04em;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #e2e8f0;
        font-variant-numeric: tabular-nums;
      }
      .level-owner {
        background: #f1f5f9;
        color: #0f172a;
        border-color: #cbd5e1;
        font-weight: 700;
      }
      .level-edit {
        background: #f8fafc;
        color: #1e293b;
        border-color: #e2e8f0;
      }
      .level-download {
        background: #f8fafc;
        color: #334155;
        border-color: #e2e8f0;
      }
      .level-view {
        background: #f8fafc;
        color: #475569;
        border-color: #e2e8f0;
      }

      /* Source Badges - Neutral Slate Micro-Pills */
      .perm-source-badge {
        font-size: 0.5625rem;
        font-weight: 500;
        padding: 0.09375rem 0.3125rem;
        border-radius: 0.25rem;
        background: #f8fafc;
        color: #64748b;
        border: 1px solid #e2e8f0;
        max-width: 12rem;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
      .source-owner {
        background: #f8fafc;
        color: #475569;
        border-color: #e2e8f0;
      }
      .source-direct {
        background: #f8fafc;
        color: #475569;
        border-color: #e2e8f0;
      }
      .source-inherited {
        background: #f8fafc;
        color: #64748b;
        border-color: #e2e8f0;
      }
      .source-org {
        background: #f8fafc;
        color: #64748b;
        border-color: #e2e8f0;
      }
      .source-team {
        background: #f8fafc;
        color: #64748b;
        border-color: #e2e8f0;
      }
      .source-workspace {
        background: #f8fafc;
        color: #64748b;
        border-color: #e2e8f0;
      }
      .source-collaboration {
        background: #f8fafc;
        color: #64748b;
        border-color: #e2e8f0;
      }

      /* Expiration text */
      .perm-expiration-text {
        display: flex;
        align-items: center;
        gap: 0.1875rem;
        font-size: 0.625rem;
        color: #64748b;
        font-weight: 500;
        font-variant-numeric: tabular-nums;
      }
      .perm-expiration-text svg {
        width: 0.625rem;
        height: 0.625rem;
        flex-shrink: 0;
      }
      .perm-expiration-text.expiring-soon {
        color: #d97706;
        font-weight: 600;
      }
      .perm-expiration-text.expired {
        color: #dc2626;
        font-weight: 600;
      }

      /* Row action trigger container */
      .row-action-anchor {
        position: relative;
        flex-shrink: 0;
        display: flex;
        align-items: center;
        gap: 0.25rem;
      }

      /* Row delete button */
      .btn-row-delete {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.625rem;
        height: 1.625rem;
        min-width: 1.625rem;
        border: 1px solid transparent;
        border-radius: 0.375rem;
        background: transparent;
        color: #94a3b8;
        cursor: pointer;
        opacity: 0.7;
        transition:
          opacity 0.15s ease,
          background-color 0.15s ease,
          color 0.15s ease,
          border-color 0.15s ease,
          transform 0.08s ease;
      }
      .btn-row-delete svg {
        width: 0.875rem;
        height: 0.875rem;
        pointer-events: none;
      }
      .perm-card:hover .btn-row-delete,
      .perm-card:focus-within .btn-row-delete {
        opacity: 1;
        color: #64748b;
      }
      .btn-row-delete:hover {
        background: #fef2f2;
        border-color: #fee2e2;
        color: #dc2626;
        opacity: 1;
      }
      .btn-row-delete:active {
        transform: scale(0.95);
      }
      .btn-row-delete:focus-visible {
        outline: 2px solid #ef4444;
        outline-offset: 1px;
        opacity: 1;
        border-color: #fca5a5;
      }

      /* Row action button */
      .btn-row-action {
        flex-shrink: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.625rem;
        height: 1.625rem;
        min-width: 1.625rem;
        border: 1px solid transparent;
        border-radius: 0.375rem;
        background: transparent;
        color: #94a3b8;
        cursor: pointer;
        opacity: 0.7;
        transition:
          opacity 0.15s ease,
          background-color 0.15s ease,
          color 0.15s ease,
          border-color 0.15s ease,
          transform 0.08s ease;
      }
      .btn-row-action svg {
        width: 0.875rem;
        height: 0.875rem;
        pointer-events: none;
      }
      .perm-card:hover .btn-row-action,
      .perm-card:focus-within .btn-row-action {
        opacity: 1;
        color: #475569;
      }
      .btn-row-action:hover,
      .btn-row-action.is-active {
        background: #f1f5f9;
        border-color: #cbd5e1;
        color: #0f172a;
        opacity: 1;
      }
      .btn-row-action:active {
        transform: scale(0.95);
      }
      .btn-row-action:focus-visible {
        outline: 2px solid #0284c7;
        outline-offset: 1px;
        opacity: 1;
        border-color: #93c5fd;
      }

      /* ── Access Row Action Menu ─────────────────────────────────── */
      .row-action-menu {
        position: absolute;
        right: 0;
        top: calc(100% + 0.25rem);
        z-index: var(--sd-z-dropdown, 1000);
        min-width: 11rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        box-shadow:
          0 0 0 1px rgba(0, 0, 0, 0.04),
          0 2px 4px rgba(15, 23, 42, 0.08),
          0 8px 16px rgba(15, 23, 42, 0.08);
        padding: 0.25rem;
        /* Origin-aware animation: slides down from trigger */
        animation: menuOpen 0.15s cubic-bezier(0.2, 0, 0, 1) both;
        transform-origin: top right;
      }
      @keyframes menuOpen {
        from {
          opacity: 0;
          transform: translateY(-0.25rem) scale(0.98);
        }
        to {
          opacity: 1;
          transform: translateY(0) scale(1);
        }
      }

      .menu-item {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        width: 100%;
        padding: 0.4375rem 0.5rem;
        background: transparent;
        border: none;
        border-radius: 0.25rem;
        cursor: pointer;
        text-align: start;
        color: #1e293b;
        font-size: 0.75rem;
        font-weight: 500;
        line-height: 1.2;
        transition:
          background-color 0.08s ease,
          color 0.08s ease;
        -webkit-font-smoothing: antialiased;
      }
      .menu-item:hover {
        background: #f8fafc;
        color: #0f172a;
      }
      .menu-item:active {
        background: #f1f5f9;
        transform: scale(0.99);
      }
      .menu-item:focus-visible {
        outline: 2px solid #f97316;
        outline-offset: -1px;
      }
      .menu-item.is-destructive {
        color: #dc2626;
      }
      .menu-item.is-destructive:hover {
        background: #fef2f2;
        color: #b91c1c;
      }
      .menu-item-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1rem;
        height: 1rem;
        flex-shrink: 0;
        color: #64748b;
      }
      .menu-item.is-destructive .menu-item-icon {
        color: #dc2626;
      }
      .menu-item-icon svg {
        width: 0.75rem;
        height: 0.75rem;
      }
      .menu-item-label {
        flex: 1;
      }
      .menu-separator {
        height: 1px;
        background: #f1f5f9;
        margin: 0.25rem 0;
      }

      .btn-revoke {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        cursor: pointer;
        color: #64748b;
        padding: 0.25rem;
        border-radius: 0.375rem;
        transition: all 0.15s ease;
        display: flex;
        align-items: center;
        justify-content: center;
      }
      .btn-revoke:hover {
        background: #fef2f2;
        color: #ef4444;
        border-color: #fecaca;
      }
      .btn-revoke svg {
        width: 0.75rem;
        height: 0.75rem;
      }

      /* 3. Active Share Links */
      .share-links-list {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
      }
      .share-link-card {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 0.625rem 0.75rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.625rem;
        box-shadow: 0 1px 3px 0 rgba(15, 23, 42, 0.03);
        transition: all 0.18s cubic-bezier(0.16, 1, 0.3, 1);
        cursor: pointer;
      }
      .share-link-card:hover {
        background: #f8fafc;
        border-color: #cbd5e1;
        transform: translateY(-1px);
        box-shadow: 0 4px 12px -2px rgba(15, 23, 42, 0.06);
      }
      .link-icon-box {
        width: 2rem;
        height: 2rem;
        border-radius: 0.5rem;
        background: #f8fafc;
        color: #0f172a;
        border: 1px solid #e2e8f0;
        box-shadow: 0 1px 2px 0 rgba(15, 23, 42, 0.03);
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        transition: all 0.15s ease;
      }
      .share-link-card:hover .link-icon-box {
        background: #ffffff;
        border-color: #cbd5e1;
      }
      .link-icon-box app-icon {
        color: #334155;
      }
      .link-details {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        min-width: 0;
        flex: 1;
        overflow: hidden;
      }
      .link-token-row {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        flex-wrap: nowrap;
        white-space: nowrap;
        min-width: 0;
      }
      .link-token-text {
        font-family: var(--sd-font-mono, monospace);
        font-size: 0.75rem;
        color: #0f172a;
        font-weight: 600;
        letter-spacing: -0.01em;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        min-width: 0;
        flex-shrink: 1;
      }
      .link-badge {
        font-size: 0.625rem;
        font-weight: 600;
        background: #f1f5f9;
        color: #334155;
        border: 1px solid #e2e8f0;
        padding: 0.0625rem 0.375rem;
        border-radius: 0.375rem;
        text-transform: uppercase;
        letter-spacing: 0.02em;
        flex-shrink: 0;
        white-space: nowrap;
      }
      .lock-badge {
        display: inline-flex;
        align-items: center;
        padding: 0.0625rem 0.3125rem;
        border-radius: 0.25rem;
        background: #fffbeb;
        color: #b45309;
        border: 1px solid #fef3c7;
        flex-shrink: 0;
        white-space: nowrap;
      }
      .lock-badge app-icon {
        color: #b45309;
      }
      .link-meta {
        font-size: 0.6875rem;
        color: #64748b;
        display: flex;
        align-items: center;
        gap: 0.25rem;
      }
      .link-actions {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        flex-shrink: 0;
      }
      .btn-copy-sm,
      .btn-revoke-sm {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.5rem;
        padding: 0.3125rem 0.5rem;
        font-size: 0.6875rem;
        font-weight: 600;
        color: #475569;
        cursor: pointer;
        transition: all 0.15s ease;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 0.25rem;
        box-shadow: 0 1px 2px 0 rgba(15, 23, 42, 0.02);
        box-sizing: border-box;
      }
      .btn-copy-sm {
        min-width: 4.5rem;
        flex-shrink: 0;
      }
      .btn-copy-sm:hover {
        background: #f8fafc;
        color: #0f172a;
        border-color: #cbd5e1;
      }
      .btn-revoke-sm:hover {
        background: #fef2f2;
        color: #991b1b;
        border-color: #fca5a5;
      }

      /* 4. Security Overview Items */
      .section-heading {
        font-size: 0.6875rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #64748b;
        margin: 0 0 0.5rem;
      }
      .security-card {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }
      .sec-item {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        transition: background-color 0.12s;
      }
      .sec-item:hover {
        background: #f8fafc;
      }
      .sec-icon {
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.375rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      .sec-icon svg {
        width: 0.875rem;
        height: 0.875rem;
      }
      .sec-icon.green {
        background: #f0fdf4;
        color: #166534;
        border: 1px solid #bbf7d0;
      }
      .sec-icon.blue {
        background: #f0f9ff;
        color: #0369a1;
        border: 1px solid #bae6fd;
      }

      .sec-details {
        display: flex;
        flex-direction: column;
        gap: 0.125rem;
      }
      .sec-title {
        font-size: 0.75rem;
        font-weight: 600;
        color: #0f172a;
      }
      .sec-sub {
        font-size: 0.6875rem;
        color: #64748b;
      }

      /* Checksum & Hash Section */
      .hash-section {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }
      .hash-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .hash-title {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        font-size: 0.6875rem;
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
        color: #64748b;
      }
      .hash-title svg {
        width: 0.875rem;
        height: 0.875rem;
        color: #ff5500;
      }

      .copy-btn {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        font-size: 0.6875rem;
        font-weight: 600;
        color: #ff5500;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        cursor: pointer;
        padding: 0.2rem 0.4375rem;
        border-radius: 0.375rem;
        transition: all 0.15s ease;
      }
      .copy-btn:hover {
        background: #fff7f3;
        border-color: #ffedd5;
      }
      .copy-btn svg {
        width: 0.75rem;
        height: 0.75rem;
      }

      .hash-box {
        font-family:
          ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 0.6875rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        padding: 0.5rem 0.625rem;
        word-break: break-all;
        color: #334155;
        line-height: 1.4;
      }

      .info-note {
        display: flex;
        align-items: flex-start;
        gap: 0.5rem;
        padding: 0.625rem 0.75rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        font-size: 0.71875rem;
        color: #64748b;
        line-height: 1.4;
      }
      .info-note svg {
        width: 0.875rem;
        height: 0.875rem;
        color: #94a3b8;
        flex-shrink: 0;
        margin-top: 0.1rem;
      }

      /* Exclusions Styling */
      .exclusions-card-desc {
        font-size: 0.71875rem;
        color: #64748b;
        margin: 0 0 0.5rem;
        line-height: 1.4;
      }
      .active-exclusions-list {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      /* New: ex-display-row - matches share-modal ex-item-row pattern */
      .ex-display-row {
        display: flex;
        align-items: center;
        gap: 0.625rem;
        padding: 0.5rem 0.625rem;
        border-radius: 0.375rem;
        background: #fffbeb;
        border: 1px solid #fef3c7;
        font-size: 0.75rem;
        transition: background 0.12s ease;
      }
      .ex-display-row:hover {
        background: #fef9e7;
      }
      .ex-display-icon-wrap {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.375rem;
        height: 1.375rem;
        border-radius: 0.25rem;
        flex-shrink: 0;
      }
      .ex-display-icon-wrap svg {
        width: 0.875rem;
        height: 0.875rem;
      }
      .folder-icon-wrap {
        background: #f1f5f9;
        color: #475569;
      }
      .doc-icon-wrap {
        background: #f1f5f9;
        color: #475569;
      }
      .ex-display-name {
        flex: 1;
        font-weight: 500;
        color: #1e293b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .ex-display-pill {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        height: 1.25rem;
        padding: 0 0.5rem;
        font-size: 0.6875rem;
        font-weight: 600;
        line-height: 1;
        border-radius: 0.375rem;
        letter-spacing: 0.01em;
        flex-shrink: 0;
        color: #15803d;
        background: #f0fdf4;
        border: 1px solid #bbf7d0;
      }
      .ex-display-pill.excluded {
        color: #9a3412;
        background: #fff7ed;
        border-color: #ffedd5;
      }
      .ex-display-loading {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.625rem;
        font-size: 0.75rem;
        color: #64748b;
      }
      .no-exclusions-note {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 0.625rem;
        background: #ffffff;
        border: 1px dashed #cbd5e1;
        border-radius: 0.375rem;
        font-size: 0.71875rem;
        color: #64748b;
      }
      .no-exclusions-note svg {
        width: 0.875rem;
        height: 0.875rem;
        color: #10b981;
        flex-shrink: 0;
      }

      /* Exclusions Selector Card inside Share Modal */
      .exclusions-selector-card {
        display: flex;
        flex-direction: column;
        gap: 0.5rem;
        padding: 0.625rem 0.75rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        margin-top: 0.25rem;
      }
      .exclusions-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }
      .ex-header-title-box {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }
      .ex-header-title-box svg {
        width: 1rem;
        height: 1rem;
        color: #ff5500;
        flex-shrink: 0;
      }
      .ex-card-title {
        font-size: 0.78125rem;
        font-weight: 600;
        color: #0f172a;
        display: block;
      }
      .ex-card-subtitle {
        font-size: 0.6875rem;
        color: #64748b;
        display: block;
      }
      .ex-badge {
        font-size: 0.625rem;
        font-weight: 600;
        color: #64748b;
        background: #e2e8f0;
        padding: 0.0625rem 0.375rem;
        border-radius: 9999px;
        transition: all 0.15s;
      }
      .ex-badge.active {
        color: #c2410c;
        background: #fff7f3;
        border: 1px solid #ffedd5;
      }

      .exclusions-controls-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .ex-search-input-box {
        flex: 1;
        display: flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.25rem 0.5rem;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        font-size: 0.75rem;
      }
      .ex-search-input-box svg {
        width: 0.875rem;
        height: 0.875rem;
        color: #94a3b8;
        flex-shrink: 0;
      }
      .ex-search-input {
        width: 100%;
        border: none;
        outline: none;
        background: transparent;
        font-size: 0.75rem;
        color: #0f172a;
      }
      .ex-bulk-actions {
        display: flex;
        align-items: center;
        gap: 0.375rem;
      }
      .btn-ex-link {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #ff5500;
        background: none;
        border: none;
        cursor: pointer;
        padding: 0;
      }
      .btn-ex-link:hover {
        text-decoration: underline;
      }
      .action-sep {
        font-size: 0.6875rem;
        color: #cbd5e1;
      }

      .ex-loading-state,
      .ex-empty-state {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.5rem;
        padding: 0.875rem;
        font-size: 0.75rem;
        color: #64748b;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
      }
      .exclusions-list-container {
        max-height: 160px;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        padding: 0.375rem;
      }
      .ex-selectable-item {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.375rem 0.5rem;
        border-radius: 0.375rem;
        cursor: pointer;
        transition: background-color 0.12s;
        user-select: none;
      }
      .ex-selectable-item:hover {
        background: #f8fafc;
      }
      .ex-selectable-item.selected {
        background: #fff7f3;
      }

      .custom-checkbox-container {
        position: relative;
        display: inline-flex;
        align-items: center;
        cursor: pointer;
        margin: 0;
      }
      .custom-checkbox-container input {
        display: none;
      }
      .checkmark {
        width: 1rem;
        height: 1rem;
        border: 1.5px solid #cbd5e1;
        border-radius: 0.25rem;
        background: #ffffff;
        transition: all 0.12s;
        display: inline-block;
        position: relative;
      }
      .custom-checkbox-container input:checked ~ .checkmark {
        background: #ff5500;
        border-color: #ff5500;
      }
      .custom-checkbox-container input:checked ~ .checkmark:after {
        content: '';
        position: absolute;
        left: 0.3rem;
        top: 0.125rem;
        width: 0.25rem;
        height: 0.45rem;
        border: solid white;
        border-width: 0 2px 2px 0;
        transform: rotate(45deg);
      }

      .ex-item-icon {
        width: 1rem;
        height: 1rem;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
      }
      .ex-item-icon.folder {
        color: #ff5500;
      }
      .ex-item-icon.doc {
        color: #3b82f6;
      }
      .ex-item-info {
        flex: 1;
        min-width: 0;
        display: flex;
        flex-direction: column;
      }
      .ex-item-name {
        font-size: 0.75rem;
        font-weight: 500;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
      .ex-item-type {
        font-size: 0.6875rem;
        color: #94a3b8;
      }
      .ex-item-status {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #16a34a;
      }
      .ex-item-status.excluded {
        color: #c2410c;
      }

      /* ── Share Modal Backdrop & Dialog (Explorer Modal Style) ── */
      .modal-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(15, 23, 42, 0.4);
        backdrop-filter: blur(2px);
        z-index: var(--sd-z-modal-backdrop, 3000);
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1.5rem;
        animation: fadeIn 0.15s ease-out;
      }
      .share-modal-dialog {
        width: 100%;
        max-width: 460px;
        background: #ffffff;
        border-radius: 0.5rem;
        border: 1px solid #e2e8f0;
        box-shadow:
          0 10px 15px -3px rgba(0, 0, 0, 0.1),
          0 4px 6px -4px rgba(0, 0, 0, 0.05);
        display: flex;
        flex-direction: column;
        overflow: hidden;
        animation: scaleUp 0.15s cubic-bezier(0.16, 1, 0.3, 1);
      }
      @keyframes scaleUp {
        from {
          opacity: 0;
          transform: scale(0.97);
        }
        to {
          opacity: 1;
          transform: scale(1);
        }
      }

      .share-modal-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.875rem 1rem;
        background: #ffffff;
        border-bottom: 1px solid #e2e8f0;
      }
      .share-title-box {
        display: flex;
        align-items: center;
        gap: 0.625rem;
      }
      .share-title-box svg {
        width: 1.25rem;
        height: 1.25rem;
        color: #ff5500;
      }
      .share-modal-title {
        font-size: 0.9375rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0;
      }
      .share-modal-sub {
        font-size: 0.71875rem;
        color: #64748b;
        margin: 0.125rem 0 0;
      }

      .share-tabs {
        display: flex;
        background: #f8fafc;
        border-bottom: 1px solid #e2e8f0;
      }
      .share-tab-btn {
        flex: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        gap: 0.375rem;
        padding: 0.5rem;
        font-size: 0.78125rem;
        font-weight: 600;
        color: #64748b;
        background: transparent;
        border: none;
        border-bottom: 2px solid transparent;
        cursor: pointer;
        transition: all 0.15s ease;
      }
      .share-tab-btn svg {
        width: 0.875rem;
        height: 0.875rem;
      }
      .share-tab-btn.active {
        color: #ff5500;
        border-bottom-color: #ff5500;
        background: #ffffff;
      }

      .share-modal-body {
        padding: 1rem;
      }
      .share-form {
        display: flex;
        flex-direction: column;
        gap: 0.875rem;
      }
      .form-group {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
      }
      .form-row {
        display: flex;
        gap: 0.625rem;
      }
      .flex-1 {
        flex: 1;
      }
      .form-label {
        font-size: 0.75rem;
        font-weight: 600;
        color: #334155;
      }
      .form-input,
      .form-select {
        width: 100%;
        padding: 0.4375rem 0.625rem;
        font-size: 0.8125rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        background: #ffffff;
        color: #0f172a;
        outline: none;
        transition:
          border-color 0.15s,
          box-shadow 0.15s;
        box-sizing: border-box;
      }
      .form-input:focus,
      .form-select:focus {
        border-color: #ff5500;
        box-shadow: 0 0 0 2px rgba(255, 85, 0, 0.1);
      }
      .form-alert {
        padding: 0.5rem 0.625rem;
        border-radius: 0.375rem;
        font-size: 0.75rem;
        font-weight: 500;
      }
      .form-alert.success {
        background: #f0fdf4;
        color: #166534;
        border: 1px solid #bbf7d0;
      }
      .form-alert.error {
        background: #fef2f2;
        color: #991b1b;
        border: 1px solid #fecaca;
      }

      .link-created-box {
        display: flex;
        flex-direction: column;
        gap: 0.375rem;
        padding: 0.625rem;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
      }
      .created-label {
        font-size: 0.75rem;
        font-weight: 600;
        color: #166534;
      }
      .created-url-row {
        display: flex;
        gap: 0.375rem;
      }
      .created-url-input {
        flex: 1;
        font-family:
          ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
        font-size: 0.75rem;
        padding: 0.375rem 0.5rem;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        background: #ffffff;
      }
      .btn-copy-primary {
        background: #ff5500;
        color: #ffffff;
        border: none;
        border-radius: 0.375rem;
        padding: 0.375rem 0.75rem;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        transition: background 0.15s;
      }
      .btn-copy-primary:hover {
        background: #e04b00;
      }

      .form-actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.5rem;
        margin-top: 0.375rem;
      }
      .btn-cancel {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 0.375rem;
        padding: 0.4375rem 0.75rem;
        font-size: 0.8125rem;
        font-weight: 600;
        color: #475569;
        cursor: pointer;
        transition: all 0.15s ease;
      }
      .btn-cancel:hover {
        background: #f8fafc;
        color: #0f172a;
        border-color: #cbd5e1;
      }
      .btn-primary {
        background: #ff5500;
        border: 1px solid #ff5500;
        border-radius: 0.375rem;
        padding: 0.4375rem 0.875rem;
        font-size: 0.8125rem;
        font-weight: 600;
        color: #ffffff;
        cursor: pointer;
        transition: all 0.15s ease;
      }
      .btn-primary:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
      .btn-primary:not(:disabled):hover {
        background: #e04b00;
        border-color: #e04b00;
      }

      .locked-drawer-hero {
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        padding: 2rem 1.5rem;
        background: #fafaf9;
        border: 1px solid #f5f5f4;
        border-radius: 0.75rem;
      }
      .locked-drawer-icon-wrap {
        width: 4rem;
        height: 4rem;
        border-radius: 50%;
        background: #fff7ed;
        color: #ea580c;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-bottom: 1rem;
      }
      .locked-drawer-title {
        font-size: 1.125rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0 0 0.5rem;
        word-break: break-word;
      }
      .locked-badge-pill {
        font-size: 0.6875rem;
        font-weight: 700;
        letter-spacing: 0.05em;
        color: #c2410c;
        background: #ffedd5;
        padding: 0.2rem 0.6rem;
        border-radius: 9999px;
        margin-bottom: 0.875rem;
        display: inline-block;
      }
      .locked-drawer-desc {
        font-size: 0.875rem;
        color: #64748b;
        margin: 0 0 1.25rem;
        line-height: 1.5;
        max-width: 320px;
      }
      .btn-drawer-request {
        display: inline-flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.5rem 1.25rem;
        font-size: 0.875rem;
        font-weight: 600;
        color: #ffffff;
        background: #ea580c;
        border: none;
        border-radius: 0.5rem;
        cursor: pointer;
        transition: background 0.15s ease;
      }
      .btn-drawer-request:hover {
        background: #c2410c;
      }
      .drawer-pending-pill {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #9a3412;
        background: #ffedd5;
        padding: 0.375rem 0.875rem;
        border-radius: 0.5rem;
        display: inline-flex;
        align-items: center;
        gap: 0.375rem;
      }
      .pending-request-box {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 0.5rem;
      }
      .btn-withdraw-request {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.35rem 0.75rem;
        font-size: 0.75rem;
        font-weight: 500;
        color: #64748b;
        background: transparent;
        border: 1px solid #cbd5e1;
        border-radius: 0.375rem;
        cursor: pointer;
        transition: all 0.15s ease;
      }
      .btn-withdraw-request:hover:not(:disabled) {
        color: #ef4444;
        border-color: #fca5a5;
        background: #fef2f2;
      }
      .btn-withdraw-request:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .sanitized-meta-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 0.75rem;
        padding: 1rem;
      }
      .meta-item {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }
      .meta-label {
        font-size: 0.75rem;
        font-weight: 500;
        color: #64748b;
      }
      .meta-value {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #0f172a;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      /* Recipient Access Styles */
      .recipient-capabilities-card,
      .recipient-context-card,
      .safe-sharing-card {
        background: #ffffff;
        border: 1px solid #e5e7eb;
        border-radius: 0.875rem;
        padding: 1rem;
        box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
      }
      .capabilities-list {
        display: flex;
        flex-direction: column;
        gap: 0.625rem;
        margin-top: 0.75rem;
      }
      .capability-item {
        display: flex;
        align-items: flex-start;
        gap: 0.75rem;
        padding: 0.625rem 0.75rem;
        border-radius: 0.5rem;
        background: #f8fafc;
        border: 1px solid #f1f5f9;
        transition: background 0.15s ease, border-color 0.15s ease;
      }
      .capability-item.allowed {
        border-color: #dcfce7;
        background: #f0fdf4;
      }
      .capability-item.allowed .cap-icon-box {
        color: #16a34a;
        background: #dcfce7;
      }
      .capability-item.disallowed {
        border-color: #f1f5f9;
        background: #f8fafc;
        opacity: 0.75;
      }
      .capability-item.disallowed .cap-icon-box {
        color: #94a3b8;
        background: #e2e8f0;
      }
      .cap-icon-box {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.75rem;
        height: 1.75rem;
        border-radius: 0.375rem;
        flex-shrink: 0;
      }
      .cap-content {
        flex: 1;
        min-width: 0;
      }
      .cap-title-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }
      .cap-title {
        font-size: 0.8125rem;
        font-weight: 600;
        color: #1e293b;
      }
      .cap-status-badge {
        font-size: 0.6875rem;
        font-weight: 600;
        padding: 0.1rem 0.45rem;
        border-radius: 9999px;
      }
      .cap-status-badge.allowed {
        background: #dcfce7;
        color: #15803d;
      }
      .cap-status-badge.disallowed {
        background: #f1f5f9;
        color: #64748b;
      }
      .cap-desc {
        display: block;
        font-size: 0.75rem;
        color: #64748b;
        margin-top: 0.125rem;
        line-height: 1.35;
      }
      .recipient-context-grid {
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 0.75rem;
        margin-top: 0.75rem;
      }
      .ctx-item {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
        padding: 0.5rem 0.625rem;
        background: #f8fafc;
        border-radius: 0.375rem;
        border: 1px solid #f1f5f9;
      }
      .ctx-label {
        font-size: 0.6875rem;
        font-weight: 600;
        color: #64748b;
        text-transform: uppercase;
        letter-spacing: 0.03em;
      }
      .ctx-val {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #1e293b;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      /* Accessibility Focus Rings & Reduced Motion */
      .btn-manage-share:focus-visible,
      .btn-retry:focus-visible,
      .btn-revoke:focus-visible,
      .btn-copy-sm:focus-visible,
      .btn-revoke-sm:focus-visible,
      .copy-btn:focus-visible,
      .btn-cancel:focus-visible,
      .btn-primary:focus-visible,
      .close-btn:focus-visible,
      .share-tab-btn:focus-visible {
        outline: 2px solid #ff5500;
        outline-offset: 2px;
      }

      @media (prefers-reduced-motion: reduce) {
        .modal-backdrop,
        .share-modal-dialog,
        .drawer,
        .btn-manage-share,
        .perm-card,
        .share-link-card,
        .sec-item,
        .ex-display-row,
        .share-tab-btn,
        .row-action-menu,
        .menu-item {
          animation: none !important;
          transition: none !important;
        }
      }
    `,
  ],
})
export class ItemDetailDrawerComponent {
  private readonly folderApiService = inject(FolderApiService);
  private readonly auditApiService = inject(AuditApiService);
  private readonly unifiedAuditApiService = inject(UnifiedAuditApiService);
  private readonly permissionApiService = inject(PermissionApiService);
  private readonly shareApiService = inject(ShareApiService);
  private readonly userApiService = inject(UserApiService);
  private readonly authStore = inject(AuthStore);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly accessRequestApi = inject(AccessRequestApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly elRef = inject(ElementRef);

  currentTrackedResourceId: string | null = null;

  isOpen = input<boolean>(false);
  item = input<{
    type: 'folder' | 'document';
    data: any;
    tab?: 'overview' | 'access';
  } | null>(null);

  closed = output<void>();
  download = output<any>();
  downloadFolder = output<number>();
  delete = output<{ type: 'folder' | 'document'; id: number }>();
  openPreview = output<{ type: 'folder' | 'document'; data: any }>();
  requestAccess = output<{ type: 'folder' | 'document'; id: number; name: string }>();
  requestCancelled = output<{ type: 'folder' | 'document'; id: number }>();

  readonly isCancellingRequest = signal<boolean>(false);

  activeTab = signal<'overview' | 'access'>('overview');
  copiedHash = signal<boolean>(false);
  folderPeek = signal<FolderPeekResponseDto | null>(null);
  isLoadingPeek = signal<boolean>(false);

  auditLogs = signal<DocumentActivityLogResponse[]>([]);
  unifiedAuditLogs = signal<UnifiedAuditEventResponseDto[]>([]);
  isLoadingAudit = signal<boolean>(false);

  // ── Permissions State ──
  effectivePermissions = signal<EffectivePermissionsResponse | null>(null);
  isLoadingPermissions = signal<boolean>(false);
  permissionError = signal<string | null>(null);
  isPermissionAccessDenied = signal<boolean>(false);
  isWhyAccessExpanded = signal<boolean>(false);
  accessSearchQuery = signal<string>('');
  activeAccessFilterCategory = signal<
    'ALL' | 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION'
  >('ALL');

  // ── Access Row Action Menu ──
  openMenuPermId = signal<string | number | null>(null);

  // ── Folder Exclusions State ──
  folderContents = signal<FolderContentResponseDto | null>(null);
  isLoadingFolderContents = signal<boolean>(false);
  excludedSubfolderIds = signal<number[]>([]);
  excludedDocumentIds = signal<number[]>([]);
  exclusionSearchQuery = signal<string>('');

  get exclusionSearchQueryModel(): string {
    return this.exclusionSearchQuery();
  }
  set exclusionSearchQueryModel(val: string) {
    this.exclusionSearchQuery.set(val);
  }

  // ── Share Modal State ──
  isShareModalOpen = signal<boolean>(false);
  shareMode = signal<'collaborators' | 'link'>('collaborators');
  shareEmailInput = signal<string>('');
  sharePermissionLevel = signal<SharePermissionLevel>('VIEW');
  shareExpiresInHours = signal<number | null>(null);
  shareLinkPassword = signal<string>('');
  isSubmittingGrant = signal<boolean>(false);
  grantSuccessMsg = signal<string | null>(null);
  grantErrorMsg = signal<string | null>(null);
  isCreatingLink = signal<boolean>(false);
  createdShareLink = signal<ShareLinkResponseDto | null>(null);
  copiedShareToken = signal<string | null>(null);

  // ── Detail Modals State ──
  isPermissionDetailOpen = signal<boolean>(false);
  selectedPermissionForDetail = signal<AccessEntryVM | null>(null);
  isShareLinkDetailOpen = signal<boolean>(false);
  selectedShareLinkForDetail = signal<ShareLinkResponseDto | null>(null);

  // ── User Search & Selection State ──
  userSearchQuery = signal<string>('');
  userSearchResults = signal<UserResponseDto[]>([]);
  isSearchingUsers = signal<boolean>(false);
  selectedRecipients = signal<UserResponseDto[]>([]);
  showUserResultsDropdown = signal<boolean>(false);

  get shareEmailInputModel(): string {
    return this.shareEmailInput();
  }
  set shareEmailInputModel(val: string) {
    this.shareEmailInput.set(val);
  }

  get sharePermissionLevelModel(): SharePermissionLevel {
    return this.sharePermissionLevel();
  }
  set sharePermissionLevelModel(val: SharePermissionLevel) {
    this.sharePermissionLevel.set(val);
  }

  get shareExpiresInHoursModel(): number | null {
    return this.shareExpiresInHours();
  }
  set shareExpiresInHoursModel(val: number | null) {
    this.shareExpiresInHours.set(val);
  }

  get shareLinkPasswordModel(): string {
    return this.shareLinkPassword();
  }
  set shareLinkPasswordModel(val: string) {
    this.shareLinkPassword.set(val);
  }

  constructor() {
    effect(
      () => {
        const current = this.item();
        const open = this.isOpen();

        if (open && current) {
          const resourceKey = `${current.type}-${current.data?.id}`;
          const isNewItem = this.currentTrackedResourceId !== resourceKey;

          if (isNewItem) {
            this.currentTrackedResourceId = resourceKey;
            this.resetItemState();

            if (current.tab) {
              this.activeTab.set(current.tab);
            } else {
              this.activeTab.set('overview');
            }

            if (current.data?.isLocked) {
              return;
            }

            const isFolder = current.type === 'folder';
            const itemId = +current.data.id;

            // Fetch folder peek and contents if folder
            if (isFolder) {
              this.isLoadingPeek.set(true);
              this.folderApiService.getFolderPeek(itemId).subscribe({
                next: (peek) => {
                  this.folderPeek.set(peek);
                  this.isLoadingPeek.set(false);
                },
                error: () => {
                  this.folderPeek.set(null);
                  this.isLoadingPeek.set(false);
                },
              });
              this.loadFolderContents(itemId);
            }

            // Fetch audit activity logs ONLY if user can manage permissions
            if (this.canManagePermissions()) {
              this.isLoadingAudit.set(true);
              const docId = !isFolder ? itemId : undefined;
              const folId = isFolder ? itemId : undefined;

              this.unifiedAuditApiService
                .getEvents(
                  {
                    scopeType: isFolder ? 'FOLDER' : 'DOCUMENT',
                    folderId: folId,
                    documentId: docId,
                    includeDescendants: isFolder ? true : undefined,
                  },
                  0,
                  50
                )
                .subscribe({
                  next: (res) => {
                    this.unifiedAuditLogs.set(res?.content || []);
                    this.isLoadingAudit.set(false);
                  },
                  error: () => {
                    this.unifiedAuditLogs.set([]);
                    this.isLoadingAudit.set(false);
                  },
                });

              if (this.activeTab() === 'access') {
                this.fetchPermissions();
              }
            }
          }
        } else {
          this.currentTrackedResourceId = null;
          this.resetItemState();
        }
      },
      { allowSignalWrites: true },
    );
  }

  resetItemState(): void {
    this.isShareModalOpen.set(false);
    this.folderPeek.set(null);
    this.isLoadingPeek.set(false);
    this.folderContents.set(null);
    this.isLoadingFolderContents.set(false);
    this.excludedSubfolderIds.set([]);
    this.excludedDocumentIds.set([]);
    this.exclusionSearchQuery.set('');
    this.auditLogs.set([]);
    this.unifiedAuditLogs.set([]);
    this.isLoadingAudit.set(false);
    this.effectivePermissions.set(null);
    this.isLoadingPermissions.set(false);
    this.permissionError.set(null);
    this.isPermissionAccessDenied.set(false);
  }

  isNormalRecipient(): boolean {
    const current = this.item();
    if (!current || !current.data) return false;
    if (current.data.isLocked) return false;

    // An owner or super admin is not a normal recipient
    if (this.isCurrentResourceOwner()) return false;
    const currentUser = this.authStore.currentUser();
    if (currentUser?.role === 'ROLE_SUPER_ADMIN') return false;

    const resourceOrgCode = getResourceOwnerOrgCode(current.data, this.folderPeek());
    if (resourceOrgCode && this.isUserOrgAdmin(resourceOrgCode)) return false;

    const d = current.data;
    if (d.grantedByUserEmail || d.grantedByName || d.sharedBy || d.permissionLevel || d.sourceType) {
      return true;
    }

    return false;
  }

  canManagePermissions(): boolean {
    const current = this.item();
    const currentUser = this.authStore.currentUser();
    if (!current || !currentUser || current.data?.isLocked) {
      return false;
    }
    if (this.isNormalRecipient()) {
      return false;
    }
    if (this.isCurrentResourceOwner()) {
      return true;
    }
    if (currentUser.role === 'ROLE_SUPER_ADMIN') {
      return true;
    }
    const resourceOrgCode = getResourceOwnerOrgCode(current.data, this.folderPeek());
    if (resourceOrgCode && this.isUserOrgAdmin(resourceOrgCode)) {
      return true;
    }
    return this.effectivePermissions() !== null;
  }

  getSharingContext(): {
    channel: string;
    sharedBy: string;
    grantedAt?: string | null;
    expiresAt?: string | null;
    isDirectShare: boolean;
  } {
    const d = this.item()?.data;
    const sharedBy =
      d?.grantedByName ||
      d?.grantedByUserEmail ||
      d?.sharedBy ||
      d?.uploadedBy?.fullName ||
      d?.uploadedBy?.username ||
      'Resource Owner';
    const isDirect = d?.recipientType === 'USER' || !d?.recipientType;
    let channel = d?.sourceType;
    if (!channel) {
      if (this.isOrgSpace()) {
        channel = 'Organization Share';
      } else {
        channel = isDirect ? 'Direct Share' : 'Shared Resource';
      }
    }
    return {
      channel,
      sharedBy,
      grantedAt: d?.grantedAt || d?.createdAt,
      expiresAt: d?.expiresAt,
      isDirectShare: isDirect,
    };
  }

  getRecipientCapabilities(): ResourceCapabilityVM[] {
    const isFolder = this.item()?.type === 'folder';
    const access = this.getCurrentUserAccess();
    const level = (access.level || 'VIEW').toUpperCase();
    const canDownload =
      level === 'DOWNLOAD' ||
      level === 'EDIT' ||
      level === 'ADMIN' ||
      level === 'OWNER';
    const canEdit = level === 'EDIT' || level === 'ADMIN' || level === 'OWNER';

    if (isFolder) {
      return [
        {
          title: 'Browse Folder Contents',
          desc: 'Navigate subfolders and browse list of contained documents',
          allowed: true,
          icon: 'folder',
        },
        {
          title: 'Download Folder Items',
          desc: canDownload
            ? 'Download individual files and folders within this folder'
            : 'Downloads restricted by sender permissions',
          allowed: canDownload,
          icon: 'download',
        },
        {
          title: 'Upload & Create Items',
          desc: canEdit
            ? 'Upload new files and create nested subfolders'
            : 'Folder is read-only; uploading new files is restricted',
          allowed: canEdit,
          icon: 'plus',
        },
        {
          title: 'Edit & Modify Structure',
          desc: canEdit
            ? 'Rename or reorganize items within this folder'
            : 'Modifications and renaming are restricted',
          allowed: canEdit,
          icon: 'edit-3',
        },
      ];
    } else {
      return [
        {
          title: 'Read Metadata & Attributes',
          desc: 'Inspect document properties, size, and classification info',
          allowed: true,
          icon: 'file-text',
        },
        {
          title: 'Preview Document',
          desc: 'High-fidelity document preview and inline viewer',
          allowed: true,
          icon: 'eye',
        },
        {
          title: 'Download Document',
          desc: canDownload
            ? 'Download file to local storage in original format'
            : 'Downloads restricted; view-only mode enforced',
          allowed: canDownload,
          icon: 'download',
        },
        {
          title: 'Edit & Replace Document',
          desc: canEdit
            ? 'Upload new versions and edit document details'
            : 'Editing restricted by sender permissions',
          allowed: canEdit,
          icon: 'edit-3',
        },
      ];
    }
  }

  formatDate(dateStr?: string | null): string {
    if (!dateStr) return '—';
    try {
      const d = new Date(dateStr);
      return isNaN(d.getTime())
        ? dateStr
        : d.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
          });
    } catch {
      return dateStr;
    }
  }

  selectTab(tab: 'overview' | 'access'): void {
    this.activeTab.set(tab);
    if (tab === 'access') {
      if (this.item()?.data?.isLocked) {
        this.isPermissionAccessDenied.set(true);
        return;
      }
      if (this.isNormalRecipient()) {
        return;
      }
      if (!this.effectivePermissions() && !this.isLoadingPermissions()) {
        this.fetchPermissions();
      }
    }
  }

  fetchPermissions(): void {
    const current = this.item();
    if (!current || !this.isOpen() || current.data?.isLocked || this.isNormalRecipient()) return;

    const isFolder = current.type === 'folder';
    const resourceId = +current.data.id;
    const resourceType = isFolder ? 'FOLDER' : 'DOCUMENT';

    this.isLoadingPermissions.set(true);
    this.permissionError.set(null);
    this.isPermissionAccessDenied.set(false);

    this.permissionApiService
      .getEffectivePermissions(resourceId, resourceType)
      .subscribe({
        next: (resp) => {
          this.effectivePermissions.set(resp);
          this.isPermissionAccessDenied.set(false);
          this.isLoadingPermissions.set(false);
        },
        error: (err: any) => {
          this.isLoadingPermissions.set(false);
          const status = err?.status ?? err?.originalError?.status;
          if (status === 403) {
            // Expected authorization response: caller does not have permission administration authority.
            this.effectivePermissions.set(null);
            this.isPermissionAccessDenied.set(true);
            this.permissionError.set(null);
            return;
          }

          console.error('Error fetching effective permissions:', err);
          this.effectivePermissions.set(null);
          this.isPermissionAccessDenied.set(false);
          this.permissionError.set(
            'Unable to load permissions. Please try again.',
          );
        },
      });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement;
    if (
      this.openMenuPermId() !== null &&
      !target.closest('.row-action-anchor')
    ) {
      this.closeRowMenu();
    }
    if (
      this.showUserResultsDropdown() &&
      !target.closest('.recipient-search-group')
    ) {
      this.showUserResultsDropdown.set(false);
    }
  }

  @HostListener('window:keydown.escape')
  onEscape(): void {
    if (this.openMenuPermId() !== null) {
      this.closeRowMenu();
      return;
    }
    if (this.isPermissionDetailOpen()) {
      this.closePermissionDetailModal();
      return;
    }
    if (this.isShareLinkDetailOpen()) {
      this.closeShareLinkDetailModal();
      return;
    }
    if (this.isShareModalOpen()) {
      this.closeShareModal();
      return;
    }
    if (this.isOpen()) {
      this.closed.emit();
    }
  }

  onOpenPreview(): void {
    const current = this.item();
    if (current && !current.data?.isLocked) {
      this.openPreview.emit(current);
    }
  }

  onRequestAccessFromDrawer(): void {
    const it = this.item();
    if (!it) return;
    this.requestAccess.emit({
      type: it.type,
      id: +it.data.id,
      name: this.getItemName(),
    });
  }

  onCancelPendingRequest(): void {
    const it = this.item();
    const reqId = it?.data?.pendingRequestId;
    if (!it || !reqId) return;

    this.isCancellingRequest.set(true);
    this.accessRequestApi.cancelAccessRequest(Number(reqId)).subscribe({
      next: () => {
        this.isCancellingRequest.set(false);
        this.notificationService.success('Access request withdrawn successfully.');
        if (it.data) {
          it.data.requestStatus = 'NONE';
          it.data.pendingRequestId = null;
        }
        this.requestCancelled.emit({ type: it.type, id: +it.data.id });
      },
      error: (err) => {
        this.isCancellingRequest.set(false);
        const msg = err?.error?.message || err?.message || 'Failed to withdraw access request.';
        this.notificationService.error(msg);
      },
    });
  }

  getHeroIconClass(): string {
    return this.item()?.type === 'folder' ? 'hero-folder' : 'hero-doc';
  }

  getItemName(): string {
    const d = this.item()?.data;
    return d?.fileName || d?.name || 'Untitled';
  }

  getItemType(): string {
    const d = this.item()?.data;
    return d?.contentType || d?.mimeType || '';
  }

  getItemSize(): number {
    const d = this.item()?.data;
    return d?.fileSize ?? d?.fileSizeBytes ?? 0;
  }

  getItemDate(): string {
    const d = this.item()?.data;
    return d?.uploadedAt || d?.createdAt || '';
  }

  getLocationPath(): string {
    const d = this.item()?.data;
    if (!d) return 'Workspace';
    if (d.parentFolderPath) return d.parentFolderPath;
    if (d.path) return d.path;
    if (d.parentPath) return d.parentPath;
    if (d.folderName) return `Workspace / ${d.folderName}`;
    return 'Workspace';
  }

  getLocationBreadcrumbs(): string[] {
    const spaceName = this.isOrgSpace()
      ? 'Organization Space'
      : 'Personal Space';
    const p = this.getLocationPath();
    const raw = p
      .split('/')
      .map((s) => s.trim())
      .filter((s) => s.length > 0);

    let crumbs: string[] = [];
    if (
      raw.length === 0 ||
      raw[0].toLowerCase() === 'workspace' ||
      raw[0].toLowerCase() === 'root'
    ) {
      crumbs = [
        spaceName,
        ...raw.slice(
          raw[0]?.toLowerCase() === 'workspace' ||
            raw[0]?.toLowerCase() === 'root'
            ? 1
            : 0,
        ),
      ];
    } else {
      crumbs = [spaceName, ...raw];
    }

    const itemName = this.getItemName();
    if (itemName && crumbs[crumbs.length - 1] !== itemName) {
      crumbs.push(itemName);
    }
    return crumbs;
  }

  getFileCategory():
    | 'image'
    | 'pdf'
    | 'ppt'
    | 'spreadsheet'
    | 'code'
    | 'default' {
    const mime = (this.getItemType() || '').toLowerCase();
    const ext =
      (this.getItemName() || '').split('.').pop()?.toLowerCase() ?? '';
    if (mime.includes('pdf') || ext === 'pdf') return 'pdf';
    if (
      mime.includes('presentation') ||
      mime.includes('powerpoint') ||
      ['ppt', 'pptx'].includes(ext)
    )
      return 'ppt';
    if (
      mime.includes('image') ||
      ['png', 'jpg', 'jpeg', 'svg', 'webp', 'gif'].includes(ext)
    )
      return 'image';
    if (['js', 'ts', 'html', 'css', 'json', 'java', 'py', 'sh'].includes(ext))
      return 'code';
    if (
      mime.includes('spreadsheet') ||
      mime.includes('excel') ||
      ['csv', 'xlsx', 'xls'].includes(ext)
    )
      return 'spreadsheet';
    return 'default';
  }

  getFileCategoryLabel(): string {
    const cat = this.getFileCategory();
    if (cat === 'pdf') return 'PDF Document';
    if (cat === 'ppt') return 'Presentation';
    if (cat === 'image') return 'Image File';
    if (cat === 'code') return 'Code File';
    if (cat === 'spreadsheet') return 'Spreadsheet';
    return 'Document';
  }

  getPageOrSlideCount(): string | null {
    const d = this.item()?.data;
    if (d?.pageCount) return `${d.pageCount} pages`;
    if (d?.slideCount) return `${d.slideCount} slides`;

    const cat = this.getFileCategory();
    if (cat === 'pdf' && d?.metadata?.pageCount)
      return `${d.metadata.pageCount} pages`;
    if (cat === 'ppt' && d?.metadata?.slideCount)
      return `${d.metadata.slideCount} slides`;

    return null;
  }

  // ── Statistics Calculations ──
  getPreviewCount(): number {
    const unified = this.unifiedAuditLogs();
    if (unified && unified.length > 0) {
      const unifiedPreviews = unified.filter(
        (l) =>
          l.action === 'PREVIEW' ||
          l.action === 'VIEW' ||
          l.action.includes('PREVIEW') ||
          l.action.includes('VIEW')
      );
      if (unifiedPreviews.length > 0) return unifiedPreviews.length;
    }

    const logs = this.auditLogs();
    const previewLogs = logs.filter(
      (l) => l.action === 'PREVIEW' || l.action === 'VIEW',
    );
    if (previewLogs.length > 0) return previewLogs.length;

    const base =
      this.item()?.data?.previewCount ?? this.item()?.data?.viewsCount;
    return base ? +base : 0;
  }

  getDownloadCount(): number {
    const unified = this.unifiedAuditLogs();
    if (unified && unified.length > 0) {
      const unifiedDownloads = unified.filter(
        (l) => l.action === 'DOWNLOAD' || l.action.includes('DOWNLOAD')
      );
      if (unifiedDownloads.length > 0) return unifiedDownloads.length;
    }

    const logs = this.auditLogs();
    const downloadLogs = logs.filter((l) => l.action === 'DOWNLOAD');
    if (downloadLogs.length > 0) return downloadLogs.length;

    const base =
      this.item()?.data?.downloadCount ?? this.item()?.data?.downloadsCount;
    return base ? +base : 0;
  }

  getTotalActivityCount(): number {
    return this.getPreviewCount() + this.getDownloadCount();
  }

  getOpenRatePercentage(): number {
    const total = this.getTotalActivityCount();
    if (total === 0) return 0;
    return Math.round((this.getPreviewCount() / total) * 100);
  }

  getDownloadRatePercentage(): number {
    const total = this.getTotalActivityCount();
    if (total === 0) return 0;
    return Math.round((this.getDownloadCount() / total) * 100);
  }

  getOpenStrokeDashoffset(): number {
    const circumference = 201.06;
    const rate = this.getOpenRatePercentage();
    return circumference - (circumference * rate) / 100;
  }

  getDownloadStrokeDashoffset(): number {
    const circumference = 201.06;
    const rate = this.getDownloadRatePercentage();
    return circumference - (circumference * rate) / 100;
  }

  getDownloadRotationAngle(): number {
    const openRate = this.getOpenRatePercentage();
    return -90 + (openRate / 100) * 360;
  }

  // ── Ownership & Space Info ──
  isCurrentResourceOwner(): boolean {
    const current = this.item();
    const currentUser = this.authStore.currentUser();
    if (!current || !currentUser) return false;

    return isResourceOwner(
      currentUser,
      current.data,
      this.folderPeek(),
      this.effectivePermissions(),
      current.type
    );
  }

  isUserOrgAdmin(orgCode?: string | null): boolean {
    const currentUser = this.authStore.currentUser();
    if (!currentUser) return false;
    if (currentUser.role === 'ROLE_SUPER_ADMIN') return true;
    if (!orgCode) return false;

    const normalizedTarget = orgCode.trim().toLowerCase();
    const orgs = currentUser.activeOrganizations || [];
    return orgs.some((o) => {
      const matchOrg = o.orgCode && o.orgCode.trim().toLowerCase() === normalizedTarget;
      const isAdmin = o.role === 'ROLE_ORG_ADMIN' || o.role === 'ROLE_SUPER_ADMIN';
      const isActive = !o.status || o.status.toUpperCase() === 'ACTIVE';
      return matchOrg && isAdmin && isActive;
    });
  }

  getOwnerName(): string {
    const d = this.item()?.data;
    if (d?.uploadedBy?.fullName) return d.uploadedBy.fullName;
    if (d?.uploadedBy?.username) return d.uploadedBy.username;
    if (typeof d?.uploadedBy === 'string' && !d.uploadedBy.includes('@'))
      return d.uploadedBy;
    if (
      typeof d?.uploadedByUserId === 'string' &&
      !d.uploadedByUserId.includes('@')
    )
      return d.uploadedByUserId;

    if (d?.createdBy?.fullName) return d.createdBy.fullName;
    if (d?.createdBy?.username) return d.createdBy.username;

    if (this.isCurrentResourceOwner()) {
      const currentUser = this.authStore.currentUser();
      if (currentUser?.fullName) return currentUser.fullName;
      if (currentUser?.firstName)
        return `${currentUser.firstName} ${currentUser.lastName || ''}`.trim();
    }

    const email = this.getOwnerEmail();
    if (email) {
      const parts = email.split('@')[0].split(/[._-]/);
      return parts.map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
    }

    return 'Owner';
  }

  getOwnerEmail(): string {
    const current = this.item();
    return (
      getResourceOwnerEmail(
        current?.data,
        this.folderPeek(),
        this.effectivePermissions(),
        current?.type
      ) || ''
    );
  }

  getOwnerInitials(): string {
    const name = this.getOwnerName();
    const parts = name.trim().split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.substring(0, 2).toUpperCase();
  }

  isOrgSpace(): boolean {
    return isOrganizationResource(this.item()?.data, this.folderPeek());
  }

  // ── Activity Log Processing ──
  getActivityFeed(): Array<{
    id?: number | string;
    accessorEmail?: string;
    action: string;
    details?: string;
    timestamp: string;
    userAgent?: string;
  }> {
    const unified = this.unifiedAuditLogs();
    if (unified && unified.length > 0) {
      return unified.map((ev) => ({
        id: ev.id,
        accessorEmail: ev.actor?.email || ev.actor?.displayName || 'System',
        action: ev.actionDisplay || ev.action,
        details: ev.changes?.details || ev.changes?.reason || '',
        timestamp: ev.timestamp,
        userAgent: ev.metadata?.userAgent,
      }));
    }
    return this.auditLogs();
  }

  getDisplayName(email?: string): string {
    if (!email) return 'System User';
    const currentUser = this.authStore.currentUser();
    if (
      currentUser?.email &&
      email.toLowerCase() === currentUser.email.toLowerCase()
    ) {
      return (
        currentUser.fullName ||
        currentUser.firstName ||
        currentUser.email.split('@')[0]
      );
    }
    const parts = email.split('@')[0].split(/[._-]/);
    return parts.map((p) => p.charAt(0).toUpperCase() + p.slice(1)).join(' ');
  }

  getUserInitials(email?: string): string {
    const name = this.getDisplayName(email);
    const parts = name.trim().split(' ');
    if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
    return name.substring(0, 2).toUpperCase();
  }

  formatActionText(action: string, details?: string): string {
    if (details) return details;
    const act = (action || '').toUpperCase();
    switch (act) {
      case 'UPLOAD':
        return 'uploaded the resource';
      case 'PREVIEW':
      case 'VIEW':
        return 'viewed the preview';
      case 'DOWNLOAD':
        return 'downloaded the resource';
      case 'RENAME':
        return 'renamed the resource';
      case 'MOVE':
        return 'moved the resource';
      case 'UPDATE':
        return 'updated the resource';
      case 'SHARE':
        return 'shared the resource';
      case 'PERMISSION_CHANGE':
        return 'updated access permissions';
      default:
        return 'accessed the resource';
    }
  }

  formatRelativeTime(dateStr: string): string {
    if (!dateStr) return 'Recently';
    const now = Date.now();
    const past = new Date(dateStr).getTime();
    const diffMs = now - past;

    if (isNaN(past)) return 'Recently';

    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return 'Just now';
    if (mins < 60) return `${mins} min${mins === 1 ? '' : 's'} ago`;

    const hours = Math.floor(mins / 60);
    if (hours < 24) return `${hours} hour${hours === 1 ? '' : 's'} ago`;

    const days = Math.floor(hours / 24);
    if (days < 30) return `${days} day${days === 1 ? '' : 's'} ago`;

    return new Date(dateStr).toLocaleDateString(undefined, {
      month: 'short',
      day: 'numeric',
    });
  }

  formatRemainingTime(expiresAt?: string): string {
    if (!expiresAt) return 'No limit';
    const target = new Date(expiresAt).getTime();
    if (isNaN(target)) return 'No limit';

    const diff = target - Date.now();
    if (diff <= 0) return 'Expired';

    const mins = Math.floor(diff / 60000);
    if (mins < 60) return `Expires in ${mins} min${mins === 1 ? '' : 's'}`;

    const hours = Math.floor(mins / 60);
    if (hours < 24) return `Expires in ${hours} hour${hours === 1 ? '' : 's'}`;

    const days = Math.floor(hours / 24);
    if (days < 30) return `Expires in ${days} day${days === 1 ? '' : 's'}`;

    return `Expires on ${new Date(expiresAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}`;
  }

  parseBrowser(userAgent?: string): string {
    if (!userAgent) return 'Chrome';
    const ua = userAgent.toLowerCase();
    if (ua.includes('firefox')) return 'Firefox';
    if (ua.includes('edg')) return 'Edge';
    if (ua.includes('safari') && !ua.includes('chrome')) return 'Safari';
    if (ua.includes('chrome')) return 'Chrome';
    return 'Web App';
  }

  copyHash(hash?: string): void {
    if (!hash) return;
    navigator.clipboard.writeText(hash);
    this.copiedHash.set(true);
    setTimeout(() => this.copiedHash.set(false), 2000);
  }

  onDelete(): void {
    const current = this.item();
    if (current) {
      this.delete.emit({ type: current.type, id: +current.data.id });
    }
  }

  formatBytes(bytes: number): string {
    if (!bytes) return '0 B';
    const k = 1024,
      s = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + s[i];
  }

  // ── Access List Processor ──
  getAccessList(): AccessEntryVM[] {
    const eff = this.effectivePermissions();
    const ownerEmail = this.getOwnerEmail();
    const ownerName = this.getOwnerName();

    const list: AccessEntryVM[] = [];
    if (!eff) {
      if (ownerEmail || ownerName) {
        list.push({
          name: ownerName || 'Resource Owner',
          email: ownerEmail,
          subtext: ownerEmail || 'Resource Owner',
          level: 'Owner',
          source: 'OWNER',
          canRevoke: false,
          entityType: 'USER',
          isExternal: false,
          avatarText: this.getUserInitials(ownerName || ownerEmail),
        });
      }
      return list;
    }

    const resolveEntityType = (
      p: any,
    ): 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION' => {
      if (p.grantedToType === 'COLLABORATION' || p.collaborationId != null) {
        return 'COLLABORATION';
      }
      if (
        p.recipientType === 'GROUP' ||
        p.grantedToType === 'TEAM' ||
        p.grantedToType === 'GROUP' ||
        p.targetGroupId != null ||
        p.groupId != null ||
        p.teamId != null
      ) {
        return 'TEAM';
      }
      if (
        p.recipientType === 'ORGANIZATION' ||
        p.grantedToType === 'ORGANIZATION' ||
        (p.targetOrgCode && !p.targetUserEmail && !p.userEmail && !p.email)
      ) {
        return 'ORGANIZATION';
      }
      return 'USER';
    };

    // 1. Filter out share links from direct permissions (handled in activeShareLinks section)
    const rawDirect = (eff.directPermissions || []).filter((dp: any) => {
      if (!dp) return false;
      return !(
        dp.accessSource === 'SHARE_LINK' ||
        dp.grantedToType === 'SHARE_LINK' ||
        dp.recipientType === 'SHARE_LINK' ||
        Boolean(dp.shareLinkToken)
      );
    });

    const consumedDirectIds = new Set<number | string>();

    // 2. Process userAccessSummaries and enrich with matching direct permissions
    const summaries = eff.userAccessSummaries || [];
    summaries.forEach((u) => {
      if (
        !u ||
        u.accessSource === 'SHARE_LINK' ||
        (u as any).grantedToType === 'SHARE_LINK'
      ) {
        return;
      }

      const uEmail = (u.email || '').toLowerCase().trim();
      const uUserId = u.userId != null ? String(u.userId).trim() : '';
      const isOwner =
        u.accessSource === 'OWNER' ||
        (Boolean(ownerEmail) && uEmail === ownerEmail.toLowerCase().trim());

      if (isOwner) {
        list.push({
          id: u.userId,
          name:
            u.fullName || ownerName || this.getDisplayName(u.email) || 'Owner',
          email: u.email || ownerEmail,
          subtext: u.email || ownerEmail || 'Resource Owner',
          level: 'Owner',
          source: 'OWNER',
          canRevoke: false,
          entityType: 'USER',
          isExternal: false,
          avatarText: this.getUserInitials(
            u.fullName || ownerName || u.email || ownerEmail,
          ),
        });
        return;
      }

      // Match against direct permissions by userId or normalized email
      const matchingDp: any = rawDirect.find((dp: any) => {
        if (consumedDirectIds.has(dp.id)) return false;
        if (resolveEntityType(dp) !== 'USER') return false;

        const dpUserId = dp.userId != null ? String(dp.userId).trim() : '';
        if (uUserId && dpUserId && uUserId === dpUserId) {
          return true;
        }

        const dpEmail = (
          dp.targetUserEmail ||
          dp.userEmail ||
          dp.email ||
          ''
        )
          .toLowerCase()
          .trim();
        return Boolean(uEmail && dpEmail && uEmail === dpEmail);
      });

      if (matchingDp) {
        consumedDirectIds.add(matchingDp.id);

        const email =
          u.email ||
          matchingDp.targetUserEmail ||
          matchingDp.userEmail ||
          matchingDp.email ||
          '';
        const name =
          matchingDp.userFullName ||
          matchingDp.entityName ||
          matchingDp.targetName ||
          u.fullName ||
          (email ? this.getDisplayName(email) : '') ||
          'User';

        list.push({
          id: matchingDp.id ?? u.userId,
          permissionId: matchingDp.id,
          name,
          email,
          subtext:
            email ||
            (u.accessSource === 'ORGANIZATION'
              ? 'Organization Member'
              : 'Direct Permission'),
          level: u.effectiveAccessLevel || matchingDp.permissionLevel || 'VIEW',
          source: 'DIRECT',
          inheritedFrom: undefined,
          expiresAt: matchingDp.expiresAt,
          grantedAt:
            matchingDp.grantedAt ||
            matchingDp.createdAt ||
            (u as any).grantedAt ||
            (u as any).createdAt,
          createdAt:
            matchingDp.createdAt ||
            matchingDp.grantedAt ||
            (u as any).createdAt ||
            (u as any).grantedAt,
          canRevoke: true,
          entityType: 'USER',
          isExternal:
            Boolean(matchingDp.isExternal) ||
            matchingDp.recipientType === 'USER_PERSONAL' ||
            Boolean((u as any).isExternal),
          avatarText: this.getUserInitials(name || email),
        });
      } else {
        // Summary entry without matching direct permission (e.g. INHERITED or ORGANIZATION policy)
        const name =
          u.fullName || (u.email ? this.getDisplayName(u.email) : '') || 'User';
        const email = u.email || '';

        list.push({
          id: u.userId,
          permissionId: undefined,
          name,
          email,
          subtext:
            email ||
            (u.accessSource === 'ORGANIZATION'
              ? 'Organization Member'
              : u.accessSource === 'INHERITED'
                ? 'Inherited Access'
                : 'Workspace Member'),
          level: u.effectiveAccessLevel || 'VIEW',
          source: (u.accessSource as any) || 'INHERITED',
          inheritedFrom: u.inheritedFromFolderName,
          expiresAt: undefined,
          grantedAt: (u as any).grantedAt || (u as any).createdAt,
          createdAt: (u as any).createdAt || (u as any).grantedAt,
          canRevoke: false,
          entityType: 'USER',
          isExternal: Boolean((u as any).isExternal),
          avatarText: this.getUserInitials(name || email),
        });
      }
    });

    // 3. Process remaining unconsumed direct permissions (GROUP, ORGANIZATION, COLLABORATION, or un-summarized USER)
    rawDirect.forEach((dp: any) => {
      if (consumedDirectIds.has(dp.id)) {
        return;
      }
      consumedDirectIds.add(dp.id);

      const entityType = resolveEntityType(dp);

      if (entityType === 'TEAM') {
        const groupId = dp.targetGroupId ?? dp.groupId ?? dp.teamId;
        const displayName =
          dp.userFullName ||
          dp.entityName ||
          dp.targetName ||
          (dp as any).groupName ||
          (dp as any).teamName ||
          (groupId != null ? `Team #${groupId}` : 'Team Access');
        const email = dp.userEmail || dp.targetUserEmail || dp.email || '';

        list.push({
          id: dp.id,
          permissionId: dp.id,
          name: displayName,
          email,
          subtext: email || (groupId != null ? `Team ID: ${groupId}` : 'Team Access'),
          level: dp.permissionLevel || 'VIEW',
          source: 'TEAM',
          expiresAt: dp.expiresAt,
          grantedAt: dp.grantedAt || dp.createdAt,
          createdAt: dp.createdAt || dp.grantedAt,
          canRevoke: true,
          entityType: 'TEAM',
          isExternal: Boolean(dp.isExternal),
          avatarText: this.getUserInitials(displayName),
        });
      } else if (entityType === 'ORGANIZATION') {
        const orgCode = (
          dp.targetOrgCode ||
          dp.orgCode ||
          ''
        )
          .toUpperCase()
          .trim();
        const displayName =
          dp.orgName ||
          dp.userFullName ||
          dp.entityName ||
          dp.targetName ||
          (orgCode ? `Organization (${orgCode})` : 'Organization Access');
        const email = dp.userEmail || dp.targetUserEmail || dp.email || '';

        list.push({
          id: dp.id,
          permissionId: dp.id,
          name: displayName,
          email,
          subtext:
            email ||
            (orgCode ? `Organization: ${orgCode}` : 'Organization Access'),
          level: dp.permissionLevel || 'VIEW',
          source: 'ORGANIZATION',
          expiresAt: dp.expiresAt,
          grantedAt: dp.grantedAt || dp.createdAt,
          createdAt: dp.createdAt || dp.grantedAt,
          canRevoke: true,
          entityType: 'ORGANIZATION',
          isExternal: Boolean(dp.isExternal),
          avatarText: this.getUserInitials(displayName),
        });
      } else if (entityType === 'COLLABORATION') {
        const collabId = dp.collaborationId;
        const displayName =
          dp.entityName ||
          dp.targetName ||
          (collabId != null
            ? `Collaboration #${collabId}`
            : 'Collaboration Group');
        const email = dp.userEmail || dp.targetUserEmail || dp.email || '';

        list.push({
          id: dp.id,
          permissionId: dp.id,
          name: displayName,
          email,
          subtext: email || 'Collaboration Group',
          level: dp.permissionLevel || 'VIEW',
          source: 'COLLABORATION',
          expiresAt: dp.expiresAt,
          grantedAt: dp.grantedAt || dp.createdAt,
          createdAt: dp.createdAt || dp.grantedAt,
          canRevoke: true,
          entityType: 'COLLABORATION',
          isExternal: Boolean(dp.isExternal),
          avatarText: this.getUserInitials(displayName),
        });
      } else {
        // USER direct permission
        const email = dp.userEmail || dp.targetUserEmail || dp.email || '';
        const name =
          dp.userFullName ||
          dp.entityName ||
          dp.targetName ||
          (email ? this.getDisplayName(email) : '');
        const displayName = name || email || 'Direct Access';

        list.push({
          id: dp.id,
          permissionId: dp.id,
          name: displayName,
          email,
          subtext: email || 'Direct Permission',
          level: dp.permissionLevel || 'VIEW',
          source: 'DIRECT',
          expiresAt: dp.expiresAt,
          grantedAt: dp.grantedAt || dp.createdAt,
          createdAt: dp.createdAt || dp.grantedAt,
          canRevoke: true,
          entityType: 'USER',
          isExternal:
            Boolean(dp.isExternal) ||
            dp.recipientType === 'USER_PERSONAL',
          avatarText: this.getUserInitials(displayName),
        });
      }
    });

    // 4. Process inherited permissions (if not already represented in list)
    if (eff?.inheritedPermissions && eff.inheritedPermissions.length > 0) {
      eff.inheritedPermissions.forEach((ip: any) => {
        if (
          !ip ||
          ip.accessSource === 'SHARE_LINK' ||
          ip.grantedToType === 'SHARE_LINK' ||
          ip.recipientType === 'SHARE_LINK' ||
          Boolean(ip.shareLinkToken)
        ) {
          return;
        }

        const entityType = resolveEntityType(ip);
        const email = (
          ip.userEmail ||
          ip.targetUserEmail ||
          ip.email ||
          ''
        )
          .toLowerCase()
          .trim();
        const userId = ip.userId != null ? String(ip.userId).trim() : '';
        const groupId = ip.targetGroupId ?? ip.groupId ?? ip.teamId;
        const orgCode = (ip.targetOrgCode || ip.orgCode || '').toUpperCase().trim();

        // Check if already represented in list
        const alreadyExists = list.some((item) => {
          if (ip.id != null && item.permissionId === ip.id) return true;
          if (entityType === 'USER') {
            if (userId && item.id != null && String(item.id).trim() === userId) {
              return true;
            }
            if (email && item.email && item.email.toLowerCase().trim() === email) {
              return true;
            }
          } else if (entityType === 'TEAM' && groupId != null) {
            if (
              item.entityType === 'TEAM' &&
              item.subtext?.includes(`Team ID: ${groupId}`)
            ) {
              return true;
            }
          } else if (entityType === 'ORGANIZATION' && orgCode) {
            if (
              item.entityType === 'ORGANIZATION' &&
              item.subtext?.includes(`Organization: ${orgCode}`)
            ) {
              return true;
            }
          }
          return false;
        });

        if (alreadyExists) {
          return;
        }

        let displayName =
          ip.userFullName ||
          ip.entityName ||
          ip.targetName ||
          (email ? this.getDisplayName(email) : '');

        let subtext = email || 'Inherited Access';

        if (entityType === 'TEAM') {
          displayName =
            displayName ||
            (groupId != null ? `Team #${groupId}` : 'Team Access');
          subtext = email || (groupId != null ? `Team ID: ${groupId}` : 'Team Access');
        } else if (entityType === 'ORGANIZATION') {
          displayName =
            displayName ||
            (orgCode ? `Organization (${orgCode})` : 'Organization Access');
          subtext =
            email ||
            (orgCode ? `Organization: ${orgCode}` : 'Organization Access');
        } else {
          displayName = displayName || email || 'Inherited Access';
        }

        list.push({
          id: ip.id,
          permissionId: ip.id,
          name: displayName,
          email,
          subtext,
          level: ip.permissionLevel || 'VIEW',
          source: 'INHERITED',
          inheritedFrom:
            ip.inheritedFromFolderName || ip.folderName || 'Parent Folder',
          expiresAt: ip.expiresAt,
          grantedAt: ip.grantedAt || ip.createdAt,
          createdAt: ip.createdAt || ip.grantedAt,
          canRevoke: false,
          entityType,
          isExternal: Boolean(ip.isExternal),
          avatarText: this.getUserInitials(displayName),
        });
      });
    }

    // 5. Fallback owner if list has no entries
    if (list.length === 0 && (ownerEmail || ownerName)) {
      list.push({
        name: ownerName || 'Resource Owner',
        email: ownerEmail,
        subtext: ownerEmail || 'Resource Owner',
        level: 'Owner',
        source: 'OWNER',
        canRevoke: false,
        entityType: 'USER',
        isExternal: false,
        avatarText: this.getUserInitials(ownerName || ownerEmail),
      });
    }

    return list;
  }

  getFilteredAccessList(): AccessEntryVM[] {
    const allEntries = this.getAccessList();
    const query = this.accessSearchQuery().trim().toLowerCase();
    const category = this.activeAccessFilterCategory();

    return allEntries.filter((entry) => {
      // Exclude Owner permission card from list
      if (entry.source === 'OWNER') {
        return false;
      }
      if (category !== 'ALL') {
        if (entry.entityType !== category) {
          return false;
        }
      }
      if (query) {
        const matchName = entry.name?.toLowerCase().includes(query);
        const matchEmail = entry.email?.toLowerCase().includes(query);
        const matchSubtext = entry.subtext?.toLowerCase().includes(query);
        const matchLevel = entry.level?.toLowerCase().includes(query);
        return matchName || matchEmail || matchSubtext || matchLevel;
      }
      return true;
    });
  }

  getAccessCategoryCount(
    category: 'ALL' | 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION',
  ): number {
    const allEntries = this.getAccessList().filter((e) => e.source !== 'OWNER');
    if (category === 'ALL') return allEntries.length;
    return allEntries.filter((e) => e.entityType === category).length;
  }

  setAccessFilterCategory(
    category: 'ALL' | 'USER' | 'TEAM' | 'ORGANIZATION' | 'COLLABORATION',
  ): void {
    this.activeAccessFilterCategory.set(category);
  }

  clearAccessSearch(): void {
    this.accessSearchQuery.set('');
  }

  toggleWhyAccess(): void {
    this.isWhyAccessExpanded.update((v) => !v);
  }

  getCurrentUserAccess(): {
    name: string;
    email: string;
    level: string;
    source: 'OWNER' | 'DIRECT' | 'INHERITED' | 'ORGANIZATION';
    inheritedFrom?: string;
  } {
    const currentUser = this.authStore.currentUser();
    const email = currentUser?.email || '';
    const name =
      currentUser?.fullName ||
      currentUser?.firstName ||
      (email ? this.getDisplayName(email) : 'User');

    const isOwner = this.isCurrentResourceOwner();

    if (isOwner) {
      return {
        name,
        email,
        level: 'Owner',
        source: 'OWNER',
      };
    }

    // Direct recipient item grant if available (e.g. from Shared With Me)
    const itemPerm = this.item()?.data?.permissionLevel;
    if (itemPerm) {
      return {
        name,
        email,
        level: itemPerm,
        source: 'DIRECT',
      };
    }

    const eff = this.effectivePermissions();
    if (eff?.userAccessSummaries && eff.userAccessSummaries.length > 0) {
      const summary = eff.userAccessSummaries.find(
        (u) => u.email?.toLowerCase() === email?.toLowerCase(),
      );
      if (summary) {
        return {
          name: summary.fullName || name,
          email: summary.email || email,
          level: summary.effectiveAccessLevel || 'VIEW',
          source: (summary.accessSource as any) || 'DIRECT',
          inheritedFrom: summary.inheritedFromFolderName,
        };
      }
    }

    if (eff?.directPermissions && eff.directPermissions.length > 0) {
      const dp: any = eff.directPermissions.find(
        (p: any) =>
          (p.userEmail || p.targetUserEmail || p.email || '').toLowerCase() ===
          email?.toLowerCase(),
      );
      if (dp) {
        return {
          name,
          email,
          level: dp.permissionLevel || 'VIEW',
          source: 'DIRECT',
        };
      }
    }

    if (eff?.inheritedPermissions && eff.inheritedPermissions.length > 0) {
      const ip: any = eff.inheritedPermissions.find(
        (p: any) =>
          (p.userEmail || p.targetUserEmail || p.email || '').toLowerCase() ===
          email?.toLowerCase(),
      );
      if (ip) {
        return {
          name,
          email,
          level: ip.permissionLevel || 'VIEW',
          source: 'INHERITED',
          inheritedFrom: ip.inheritedFromFolderName || 'Parent Folder',
        };
      }
    }

    if (this.isOrgSpace()) {
      return {
        name,
        email,
        level: 'VIEW',
        source: 'ORGANIZATION',
      };
    }

    return {
      name,
      email,
      level: 'VIEW',
      source: 'DIRECT',
    };
  }

  canShareAccess(): boolean {
    const current = this.item();
    const currentUser = this.authStore.currentUser();
    if (!current || !currentUser) {
      return false;
    }

    const d = current.data;
    const isOrg = this.isOrgSpace();

    // A. PERSONAL RESOURCE
    if (!isOrg) {
      return this.isCurrentResourceOwner();
    }

    // B. ORGANIZATION RESOURCE
    // Fast-path 1: SUPER_ADMIN
    if (currentUser.role === 'ROLE_SUPER_ADMIN') {
      return true;
    }

    // Fast-path 2: ORG_ADMIN of resource.ownerOrgCode
    const resourceOrgCode = getResourceOwnerOrgCode(d, this.folderPeek());
    if (resourceOrgCode && this.isUserOrgAdmin(resourceOrgCode)) {
      return true;
    }

    // While authority request is loading: Share must NOT be optimistically shown
    if (this.isLoadingPermissions()) {
      return false;
    }

    // If authority was denied by backend (HTTP 403) or generic permission error:
    if (this.isPermissionAccessDenied() || this.permissionError()) {
      return false;
    }

    // If backend confirmed authority via HTTP 200 OK:
    // (Covers Workspace Manager, Collaboration Manager, and Creator + effective EDIT)
    if (this.effectivePermissions() !== null) {
      return true;
    }

    return false;
  }

  getWhyAccessReasonText(): {
    sourceReason: string;
    capabilityReason: string;
    governanceReason?: string;
  } {
    const access = this.getCurrentUserAccess();
    let sourceReason = 'Direct permission explicitly granted to your account.';
    if (access.source === 'OWNER' || access.level === 'Owner') {
      sourceReason = 'You are the owner and creator of this resource.';
    } else if (access.source === 'INHERITED') {
      sourceReason = `Access inherited from parent folder "${access.inheritedFrom || 'Parent Folder'}".`;
    } else if (access.source === 'ORGANIZATION') {
      sourceReason = 'Access granted through organization-wide policy.';
    }

    const isFolder = this.item()?.type === 'folder';
    const lvl = (access.level || '').toUpperCase();
    let capabilityReason: string;

    if (isFolder) {
      if (lvl === 'OWNER') {
        capabilityReason =
          'Full control including folder management, creating subfolders, uploading, and access management.';
      } else if (lvl === 'EDIT') {
        capabilityReason = this.canShareAccess()
          ? 'You can view folder, browse allowed contents, upload, and share.'
          : 'You can view folder, browse allowed contents, and upload.';
      } else if (lvl === 'DOWNLOAD') {
        capabilityReason =
          'You can view folder, browse allowed contents, and download allowed items.';
      } else {
        capabilityReason = 'You can view folder and browse allowed contents.';
      }
    } else {
      if (lvl === 'OWNER') {
        capabilityReason =
          'Full control including edit, delete, preview, download, and access management.';
      } else if (lvl === 'EDIT') {
        capabilityReason = this.canShareAccess()
          ? 'You can view metadata, preview, edit, download, and share.'
          : 'You can view metadata, preview, edit, and download.';
      } else if (lvl === 'DOWNLOAD') {
        capabilityReason =
          'You can view metadata, preview, and download this document.';
      } else {
        capabilityReason = 'You can view metadata and preview document contents.';
      }
    }

    let governanceReason: string | undefined = undefined;
    if (this.isOrgSpace()) {
      governanceReason =
        'Protected by organization domain policy & access monitoring.';
    }

    return {
      sourceReason,
      capabilityReason,
      governanceReason,
    };
  }

  getPermissionLevelClass(level: string): string {
    const l = (level || '').toUpperCase();
    if (l === 'OWNER') return 'level-owner';
    if (l === 'EDIT') return 'level-edit';
    if (l === 'DOWNLOAD') return 'level-download';
    return 'level-view';
  }

  getAccessSourceClass(source: string): string {
    const s = (source || '').toUpperCase();
    if (s === 'OWNER') return 'source-owner';
    if (s === 'INHERITED') return 'source-inherited';
    if (s === 'ORGANIZATION') return 'source-org';
    if (s === 'TEAM') return 'source-team';
    if (s === 'WORKSPACE') return 'source-workspace';
    if (s === 'COLLABORATION') return 'source-collaboration';
    return 'source-direct';
  }

  // ── Access Row Action Menu ──────────────────────────────────────

  /** Returns the action items visible for a given access entry. */
  getMenuActions(perm: AccessEntryVM): Array<{
    id: string;
    label: string;
    icon: string;
    destructive?: boolean;
  }> {
    const actions: Array<{
      id: string;
      label: string;
      icon: string;
      destructive?: boolean;
    }> = [];

    const isOwner = perm.source === 'OWNER';
    const isDirect =
      perm.source === 'DIRECT' ||
      perm.source === 'TEAM' ||
      Boolean(perm.canRevoke && perm.permissionId != null);
    const isInherited = perm.source === 'INHERITED';
    const isOrgOrWorkspace =
      !isDirect &&
      (perm.source === 'ORGANIZATION' ||
        perm.source === 'WORKSPACE' ||
        perm.source === 'COLLABORATION');

    // View details — always available
    actions.push({
      id: 'view-details',
      label: 'View details',
      icon: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><line x1="8" y1="7" x2="8" y2="11"/><circle cx="8" cy="5.5" r="0.5" fill="currentColor" stroke="none"/></svg>`,
    });

    if (isInherited) {
      // Why inherited
      actions.push({
        id: 'why-inherited',
        label: 'Why inherited?',
        icon: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 13V8a5 5 0 0 1 10 0v2"/><path d="M10 11v2"/><circle cx="10" cy="14" r="0.5" fill="currentColor" stroke="none"/></svg>`,
      });
      // View source if inheritedFrom is known
      if (perm.inheritedFrom) {
        actions.push({
          id: 'view-source',
          label: 'View source',
          icon: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 3h4v2H5v6h6V9h2v4H3z"/><path d="M9 3h4v4h-2V5.5L7.5 9 6 7.5 9.5 4H8z" fill="currentColor" stroke="none" opacity="0.7"/></svg>`,
        });
      }
    } else if (isDirect) {
      // Revoke access — only when canRevoke is true
      if (perm.canRevoke && perm.permissionId) {
        actions.push({
          id: 'revoke',
          label: 'Revoke access',
          icon: `<svg viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="8" cy="8" r="6"/><line x1="5" y1="8" x2="11" y2="8"/></svg>`,
          destructive: true,
        });
      }
    } else if (isOwner || isOrgOrWorkspace) {
      // No extra actions for owner/org/workspace — view details only
    }

    return actions;
  }

  toggleRowMenu(permId: string | number | undefined, event: MouseEvent): void {
    event.stopPropagation();
    const id = permId ?? null;
    if (this.openMenuPermId() === id) {
      this.openMenuPermId.set(null);
    } else {
      this.openMenuPermId.set(id);
      // Focus first menu item on next tick
      setTimeout(() => {
        const menu = this.elRef.nativeElement.querySelector('.row-action-menu');
        const first = menu?.querySelector('.menu-item') as HTMLElement | null;
        first?.focus();
      }, 50);
    }
  }

  closeRowMenu(): void {
    this.openMenuPermId.set(null);
  }

  onMenuKeydown(event: KeyboardEvent): void {
    const menu = event.currentTarget as HTMLElement;
    const items = Array.from(menu.querySelectorAll<HTMLElement>('.menu-item'));
    const focused = document.activeElement as HTMLElement;
    const idx = items.indexOf(focused);

    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        items[(idx + 1) % items.length]?.focus();
        break;
      case 'ArrowUp':
        event.preventDefault();
        items[(idx - 1 + items.length) % items.length]?.focus();
        break;
      case 'Escape':
        event.stopPropagation();
        this.closeRowMenu();
        // Return focus to trigger button
        setTimeout(() => {
          const btn = this.elRef.nativeElement.querySelector(
            '.btn-row-action.is-active',
          ) as HTMLElement | null;
          btn?.focus();
        }, 10);
        break;
      case 'Tab':
        // Tab away closes the menu
        this.closeRowMenu();
        break;
    }
  }

  handleMenuAction(actionId: string, perm: AccessEntryVM): void {
    this.closeRowMenu();

    switch (actionId) {
      case 'view-details':
        this.openPermissionDetailModal(perm);
        break;

      case 'why-inherited':
        // Expand the inherited access explanation
        this.isWhyAccessExpanded.set(true);
        break;

      case 'view-source':
        // Navigate to the source folder (no-op for now — source is shown in badge)
        // In a real flow this would navigate to the parent folder
        console.info('View source for', perm.inheritedFrom);
        break;

      case 'revoke':
        if (perm.permissionId != null) {
          this.revokePermission(perm.permissionId);
        }
        break;
    }
  }

  onRevokeCardPermission(perm: AccessEntryVM, event?: MouseEvent): void {
    if (event) {
      event.stopPropagation();
      event.preventDefault();
    }
    const targetId =
      perm.permissionId ?? (typeof perm.id === 'number' ? perm.id : null);
    if (targetId != null) {
      this.revokePermission(targetId);
    }
  }

  revokePermission(permissionId: string | number): void {
    if (!permissionId) return;
    this.permissionApiService.revokePermission(permissionId).subscribe({
      next: () => {
        this.fetchPermissions();
      },
      error: (err) => {
        console.error('Failed to revoke permission:', err);
        alert('Failed to revoke permission.');
      },
    });
  }

  revokeShareLink(token: string): void {
    if (!token) return;
    if (
      !confirm(
        'Are you sure you want to revoke this share link? Anyone with this link will immediately lose access.',
      )
    ) {
      return;
    }
    this.shareApiService.revokeShareLink(token).subscribe({
      next: () => {
        this.fetchPermissions();
      },
      error: (err) => {
        console.error('Failed to revoke share link:', err);
        alert('Failed to revoke share link.');
      },
    });
  }

  openShareModal(): void {
    if (!this.canShareAccess()) {
      return;
    }
    this.isShareModalOpen.set(false);
    this.isShareModalOpen.set(true);
    this.grantSuccessMsg.set(null);
    this.grantErrorMsg.set(null);
    this.createdShareLink.set(null);
    this.userSearchQuery.set('');
    this.userSearchResults.set([]);
    this.isSearchingUsers.set(false);
    this.selectedRecipients.set([]);
    this.showUserResultsDropdown.set(false);
  }

  onUserSearchInput(query: string): void {
    const trimmed = query.trim();
    this.userSearchQuery.set(query);
    if (!trimmed) {
      this.userSearchResults.set([]);
      this.isSearchingUsers.set(false);
      this.showUserResultsDropdown.set(false);
      return;
    }

    this.isSearchingUsers.set(true);
    this.showUserResultsDropdown.set(true);

    this.userApiService.searchUsers(trimmed).subscribe({
      next: (results) => {
        const selectedIds = new Set(this.selectedRecipients().map((u) => u.id));
        const filtered = (results || []).filter((u) => !selectedIds.has(u.id));
        this.userSearchResults.set(filtered);
        this.isSearchingUsers.set(false);
      },
      error: (err) => {
        console.error('Failed to search users:', err);
        this.userSearchResults.set([]);
        this.isSearchingUsers.set(false);
      },
    });
  }

  onUserSearchFocus(): void {
    if (this.userSearchQuery().trim().length > 0) {
      this.showUserResultsDropdown.set(true);
    }
  }

  selectRecipient(user: UserResponseDto): void {
    if (!this.selectedRecipients().some((u) => u.id === user.id)) {
      this.selectedRecipients.update((list) => [...list, user]);
    }
    this.userSearchQuery.set('');
    this.userSearchResults.set([]);
    this.showUserResultsDropdown.set(false);
  }

  removeRecipient(userId: string): void {
    this.selectedRecipients.update((list) =>
      list.filter((u) => u.id !== userId),
    );
  }

  isRecipientSelected(userId: string): boolean {
    return this.selectedRecipients().some((u) => u.id === userId);
  }

  closeShareModal(): void {
    this.isShareModalOpen.set(false);
  }

  // ── Permission & Share Link Detail Modal Handlers ──
  openPermissionDetailModal(entry: AccessEntryVM): void {
    this.selectedPermissionForDetail.set(entry);
    this.isPermissionDetailOpen.set(true);
  }

  closePermissionDetailModal(): void {
    this.isPermissionDetailOpen.set(false);
    this.selectedPermissionForDetail.set(null);
  }

  handlePermissionUpdated(): void {
    this.closePermissionDetailModal();
    this.fetchPermissions();
  }

  handlePermissionRevoked(): void {
    this.closePermissionDetailModal();
    this.fetchPermissions();
  }

  openShareLinkDetailModal(link: ShareLinkResponseDto): void {
    this.selectedShareLinkForDetail.set(link);
    this.isShareLinkDetailOpen.set(true);
  }

  closeShareLinkDetailModal(): void {
    this.isShareLinkDetailOpen.set(false);
    this.selectedShareLinkForDetail.set(null);
  }

  handleShareLinkRevoked(): void {
    this.closeShareLinkDetailModal();
    this.fetchPermissions();
  }

  handleShareLinkCreated(link: ShareLinkResponseDto): void {
    this.closeShareModal();
    this.fetchPermissions();
    this.openShareLinkDetailModal(link);
  }

  loadFolderContents(folderId: number): void {
    this.isLoadingFolderContents.set(true);
    this.folderApiService.getFolderContents(folderId).subscribe({
      next: (contents) => {
        this.folderContents.set(contents);
        this.isLoadingFolderContents.set(false);
      },
      error: (err) => {
        console.error('Failed to load folder contents for exclusions:', err);
        this.folderContents.set(null);
        this.isLoadingFolderContents.set(false);
      },
    });
  }

  toggleSubfolderExclusion(id: number): void {
    const list = [...this.excludedSubfolderIds()];
    const index = list.indexOf(id);
    if (index > -1) {
      list.splice(index, 1);
    } else {
      list.push(id);
    }
    this.excludedSubfolderIds.set(list);
  }

  toggleDocumentExclusion(id: number): void {
    const list = [...this.excludedDocumentIds()];
    const index = list.indexOf(id);
    if (index > -1) {
      list.splice(index, 1);
    } else {
      list.push(id);
    }
    this.excludedDocumentIds.set(list);
  }

  isSubfolderExcluded(id: number): boolean {
    return this.excludedSubfolderIds().includes(id);
  }

  isDocumentExcluded(id: number): boolean {
    return this.excludedDocumentIds().includes(id);
  }

  getExcludedTotalCount(): number {
    return (
      this.excludedSubfolderIds().length + this.excludedDocumentIds().length
    );
  }

  getFilteredSubfolders() {
    const sfs = this.folderContents()?.subFolders || [];
    const q = this.exclusionSearchQuery().toLowerCase().trim();
    if (!q) return sfs;
    return sfs.filter((sf) => sf.name.toLowerCase().includes(q));
  }

  getFilteredDocuments() {
    const docs = this.folderContents()?.documents || [];
    const q = this.exclusionSearchQuery().toLowerCase().trim();
    if (!q) return docs;
    return docs.filter((d) =>
      (d.name || d.fileName || '').toLowerCase().includes(q),
    );
  }

  selectAllExclusions(): void {
    const contents = this.folderContents();
    if (!contents) return;
    const subfolderIds = (contents.subFolders || []).map((sf) => sf.id);
    const documentIds = (contents.documents || []).map((doc) => +doc.id);
    this.excludedSubfolderIds.set(subfolderIds);
    this.excludedDocumentIds.set(documentIds);
  }

  clearExclusions(): void {
    this.excludedSubfolderIds.set([]);
    this.excludedDocumentIds.set([]);
  }

  grantCollaboratorPermission(): void {
    const current = this.item();
    const email = this.shareEmailInput().trim();
    if (!current || !email) return;

    this.isSubmittingGrant.set(true);
    this.grantErrorMsg.set(null);
    this.grantSuccessMsg.set(null);

    const isFolder = current.type === 'folder';
    const resourceId = current.data.id.toString();

    let expiresAt: string | undefined = undefined;
    const hours = this.shareExpiresInHours();
    if (hours && hours > 0) {
      expiresAt = new Date(Date.now() + hours * 3600000).toISOString();
    }

    if (isFolder) {
      const dto: BulkShareWithExclusionsDto = {
        folderId: +resourceId,
        targetUserEmail: email,
        permissionLevel: this.sharePermissionLevel(),
        excludedDocumentIds: this.excludedDocumentIds(),
        excludedSubfolderIds: this.excludedSubfolderIds(),
        expiresAt,
      };

      this.permissionApiService.grantFolderShareWithExclusions(dto).subscribe({
        next: () => {
          this.isSubmittingGrant.set(false);
          this.grantSuccessMsg.set(
            `Granted folder access to ${email} with exclusions`,
          );
          this.shareEmailInput.set('');
          this.fetchPermissions();
          setTimeout(() => this.grantSuccessMsg.set(null), 3000);
        },
        error: (err) => {
          console.error(
            'Error granting folder permission with exclusions:',
            err,
          );
          this.isSubmittingGrant.set(false);
          this.grantErrorMsg.set(
            err?.error?.message || 'Failed to grant folder permission.',
          );
        },
      });
    } else {
      this.permissionApiService
        .grantPermission({
          permissionType: 'ALLOW',
          permissionLevel: this.sharePermissionLevel() as any,
          documentId: current.data.id,
          documentFileHash: current.data.fileHash,
          targetUserEmail: email,
          expiresAt,
        })
        .subscribe({
          next: () => {
            this.isSubmittingGrant.set(false);
            this.grantSuccessMsg.set(`Granted access to ${email}`);
            this.shareEmailInput.set('');
            this.fetchPermissions();
            setTimeout(() => this.grantSuccessMsg.set(null), 3000);
          },
          error: (err) => {
            console.error('Error granting permission:', err);
            this.isSubmittingGrant.set(false);
            this.grantErrorMsg.set(
              err?.error?.message || 'Failed to grant permission.',
            );
          },
        });
    }
  }

  createSecureShareLink(): void {
    const current = this.item();
    if (!current) return;

    this.isCreatingLink.set(true);
    const isFolder = current.type === 'folder';

    const req = {
      documentId: !isFolder ? +current.data.id : undefined,
      folderId: isFolder ? +current.data.id : undefined,
      permissionLevel: this.sharePermissionLevel(),
      password: this.shareLinkPassword().trim() || undefined,
      expiresInHours: this.shareExpiresInHours() || undefined,
    };

    this.shareApiService.createShareLink(req).subscribe({
      next: (res) => {
        this.isCreatingLink.set(false);
        this.createdShareLink.set(res);
        this.fetchPermissions();
      },
      error: (err) => {
        console.error('Error creating share link:', err);
        this.isCreatingLink.set(false);
        alert('Failed to create share link.');
      },
    });
  }

  copyShareLinkUrl(token: string): void {
    const fullUrl = this.getShareLinkFullUrl(token);
    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
      navigator.clipboard.writeText(fullUrl).catch(() => {});
    }
    this.copiedShareToken.set(token);
    setTimeout(() => this.copiedShareToken.set(null), 2000);
  }

  getShareLinkFullUrl(token: string): string {
    return `${window.location.origin}/share/${token}`;
  }

  isLinkCreator(link: ShareLinkResponseDto): boolean {
    const user = this.authStore.currentUser();
    if (!user || !link.createdByUserId) return false;
    return link.createdByUserId.toString() === user.id?.toString();
  }

  canRevealShareLink(link: ShareLinkResponseDto): boolean {
    if (link.canReveal !== undefined && link.canReveal !== null) {
      return !!link.canReveal;
    }
    return !!link.token || this.isLinkCreator(link);
  }

  copyOrRevealShareLink(link: ShareLinkResponseDto): void {
    if (link.token) {
      this.copyShareLinkUrl(link.token);
      return;
    }
    const permId = link.id;
    if (!permId) return;

    this.shareApiService.revealShareLink(permId).subscribe({
      next: (res) => {
        link.token = res.shareLinkToken;
        this.copyShareLinkUrl(res.shareLinkToken);
      },
      error: (err) => {
        console.error('Failed to reveal share link:', err);
        alert('Failed to reveal share link. You may not have authority to reveal this bearer secret.');
      },
    });
  }

  revokeShareLinkById(permissionId: number): void {
    if (!permissionId) return;
    if (
      !confirm(
        'Are you sure you want to revoke this share link? Anyone with this link will immediately lose access.',
      )
    ) {
      return;
    }
    this.permissionApiService.revokePermission(permissionId).subscribe({
      next: () => {
        this.fetchPermissions();
      },
      error: (err) => {
        console.error('Failed to revoke share link:', err);
        alert('Failed to revoke share link.');
      },
    });
  }
}
