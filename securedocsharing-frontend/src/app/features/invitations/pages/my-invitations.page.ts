import {
  Component,
  ChangeDetectionStrategy,
  signal,
  computed,
  inject,
  OnInit,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import {
  LucideMail,
  LucideBuilding2,
  LucideUser,
  LucideShield,
  LucideClock,
  LucideCheck,
  LucideX,
  LucideRefreshCw,
  LucideArrowRight,
  LucideInbox,
  LucideLayers,
  LucideMessageSquare,
  LucideAlertCircle,
  LucideSparkles,
  LucideCheckCircle,
  LucideSearch,
} from '@lucide/angular';
import { OrganizationApiService } from '../../../core/services/organization-api.service';
import { WorkspaceContextStore } from '../../../state/workspace/workspace-context.store';
import { NotificationService } from '../../../core/services/notification.service';
import { OrganizationInvitationResponseDto } from '../../../core/models/organization.model';

type InboxTabFilter = 'PENDING' | 'ALL';

@Component({
  selector: 'app-my-invitations-page',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    LucideMail,
    LucideBuilding2,
    LucideUser,
    LucideShield,
    LucideClock,
    LucideCheck,
    LucideX,
    LucideRefreshCw,
    LucideArrowRight,
    LucideInbox,
    LucideLayers,
    LucideMessageSquare,
    LucideAlertCircle,
    LucideSparkles,
    LucideCheckCircle,
    LucideSearch,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="invitations-page-layout">
      <!-- ── Page Header ─────────────────────────────────────────────────── -->
      <header class="invitations-page-header">
        <div class="header-main-row">
          <div class="header-titles">
            <div class="title-with-icon">
              <div class="header-icon-badge">
                <svg lucideMail class="header-lucide-icon"></svg>
              </div>
              <div>
                <h1>My Invitations</h1>
                <p class="subtitle">
                  Review and respond to organization membership invitations offered to you.
                </p>
              </div>
            </div>
          </div>

          <div class="header-actions">
            <!-- Refresh Button -->
            <button
              type="button"
              class="btn-refresh"
              (click)="onRefresh()"
              [disabled]="isRefreshing() || isLoading()"
              title="Refresh invitations">
              <svg
                lucideRefreshCw
                class="btn-icon"
                [class.spin]="isRefreshing()"></svg>
              <span>Refresh</span>
            </button>
          </div>
        </div>

        <!-- Filter Tab Strip & Search Bar -->
        <div class="inbox-toolbar-row">
          <div class="inbox-tabs">
            <button
              type="button"
              class="tab-btn"
              [class.active]="activeTab() === 'PENDING'"
              (click)="setTab('PENDING')">
              <span>Action Required</span>
              <span class="tab-badge" [class.badge-active]="pendingCount() > 0">
                {{ pendingCount() }}
              </span>
            </button>
            <button
              type="button"
              class="tab-btn"
              [class.active]="activeTab() === 'ALL'"
              (click)="setTab('ALL')">
              <span>All History</span>
              <span class="tab-badge">{{ totalCount() }}</span>
            </button>
          </div>

          @if (totalCount() > 0) {
            <div class="search-box">
              <svg lucideSearch class="search-icon"></svg>
              <input
                type="text"
                class="search-input"
                placeholder="Filter by organization or inviter..."
                [value]="searchQuery()"
                (input)="onSearchInput($event)"
              />
              @if (searchQuery().length > 0) {
                <button
                  type="button"
                  class="btn-clear-search"
                  (click)="clearSearch()">
                  <svg lucideX class="clear-icon"></svg>
                </button>
              }
            </div>
          }
        </div>
      </header>

      <!-- ── Main Surface Area ───────────────────────────────────────────── -->
      <main class="invitations-container">
        <!-- Error Banner -->
        @if (error()) {
          <div class="error-alert-banner">
            <svg lucideAlertCircle class="alert-icon"></svg>
            <div class="alert-content">
              <strong>Unable to load invitations</strong>
              <p>{{ error() }}</p>
            </div>
            <button type="button" class="btn-retry" (click)="loadInvitations()">
              Retry
            </button>
          </div>
        }

        <!-- Pending Banner Banner Callout -->
        @if (!isLoading() && pendingCount() > 0 && activeTab() === 'PENDING') {
          <div class="pending-summary-banner">
            <div class="banner-icon-col">
              <svg lucideSparkles class="sparkle-icon"></svg>
            </div>
            <div class="banner-text-col">
              <h3>Pending Action</h3>
              <p>
                You have {{ pendingCount() }} pending organization invitation{{ pendingCount() > 1 ? 's' : '' }}.
                Accepting an invitation grants immediate access to the organization's workspaces and documents.
              </p>
            </div>
          </div>
        }

        <!-- Loading State -->
        @if (isLoading()) {
          <div class="loading-state-card">
            <div class="skeleton-header">
              <div class="skeleton-circle"></div>
              <div class="skeleton-lines">
                <div class="skeleton-line line-title"></div>
                <div class="skeleton-line line-sub"></div>
              </div>
            </div>
            <div class="skeleton-body">
              <div class="skeleton-line line-row"></div>
              <div class="skeleton-line line-row short"></div>
            </div>
          </div>
        }

        <!-- Empty State -->
        @if (!isLoading() && displayedInvitations().length === 0) {
          <div class="empty-inbox-surface">
            <div class="empty-icon-wrap">
              <svg lucideInbox class="empty-inbox-icon"></svg>
            </div>
            <h2 class="empty-title">
              {{ activeTab() === 'PENDING' ? 'No Pending Invitations' : 'No Invitations Found' }}
            </h2>
            <p class="empty-description">
              @if (searchQuery().length > 0) {
                No invitations matched your filter query "{{ searchQuery() }}".
              } @else if (activeTab() === 'PENDING') {
                You're all caught up! You don't have any pending organization invitations requiring your response.
              } @else {
                You haven't received or responded to any organization invitations yet.
              }
            </p>

            @if (searchQuery().length > 0) {
              <button
                type="button"
                class="btn-reset-filter"
                (click)="clearSearch()">
                Clear Search Filter
              </button>
            }
          </div>
        }

        <!-- Invitations List / Inbox Cards -->
        @if (!isLoading() && displayedInvitations().length > 0) {
          <div class="inbox-card-list">
            @for (inv of displayedInvitations(); track inv.token) {
              <article
                class="invitation-card"
                [class.card-accepted]="inv.status === 'ACCEPTED'"
                [class.card-declined]="inv.status === 'DECLINED'"
                [class.card-expired]="inv.status === 'EXPIRED' || isExpired(inv.expiresAt)">

                <!-- Card Header -->
                <div class="card-header-row">
                  <div class="org-identity">
                    <div class="org-avatar">
                      <svg lucideBuilding2 class="org-icon"></svg>
                    </div>
                    <div class="org-details">
                      <h2 class="org-name">
                        {{ inv.organizationName || getOrgDisplayName(inv.orgCode) }}
                      </h2>
                      <div class="org-meta-line">
                        <span class="org-code-badge">
                          <span class="code-label">CODE:</span>
                          <span class="code-val">{{ inv.orgCode }}</span>
                        </span>
                      </div>
                    </div>
                  </div>

                  <!-- Status Chip -->
                  <div class="status-chip-wrapper">
                    <span class="status-chip" [ngClass]="getStatusInfo(inv.status, inv.expiresAt).class">
                      <span class="status-dot"></span>
                      <span>{{ getStatusInfo(inv.status, inv.expiresAt).label }}</span>
                    </span>
                  </div>
                </div>

                <!-- Invitation Details Grid -->
                <div class="card-details-grid">
                  <!-- Inviter -->
                  <div class="detail-item">
                    <div class="detail-label">
                      <svg lucideUser class="item-icon"></svg>
                      <span>Invited By</span>
                    </div>
                    <div class="detail-value text-semibold">
                      {{ inv.inviterEmail || inv.inviterUserId || 'Organization Admin' }}
                    </div>
                  </div>

                  <!-- Role Offered -->
                  <div class="detail-item">
                    <div class="detail-label">
                      <svg lucideShield class="item-icon"></svg>
                      <span>Offered Role</span>
                    </div>
                    <div class="detail-value">
                      <span class="role-badge" [ngClass]="getRoleBadgeClass(inv.role)">
                        {{ formatRole(inv.role) }}
                      </span>
                    </div>
                  </div>

                  <!-- Created At -->
                  <div class="detail-item">
                    <div class="detail-label">
                      <svg lucideClock class="item-icon"></svg>
                      <span>Sent Date</span>
                    </div>
                    <div class="detail-value">
                      {{ inv.createdAt | date:'mediumDate' }}
                    </div>
                  </div>

                  <!-- Expiration -->
                  <div class="detail-item">
                    <div class="detail-label">
                      <svg lucideClock class="item-icon"></svg>
                      <span>Expiration</span>
                    </div>
                    <div
                      class="detail-value"
                      [class.text-urgent]="getExpirationInfo(inv.expiresAt).isUrgent"
                      [class.text-muted]="getExpirationInfo(inv.expiresAt).isExpired">
                      {{ getExpirationInfo(inv.expiresAt).text }}
                    </div>
                  </div>
                </div>

                <!-- Personal Message / Note Box -->
                @if (inv.note && inv.note.trim().length > 0) {
                  <div class="invitation-note-box">
                    <div class="note-header">
                      <svg lucideMessageSquare class="note-icon"></svg>
                      <span>Invitation Note</span>
                    </div>
                    <p class="note-body">"{{ inv.note }}"</p>
                  </div>
                }

                <!-- Onboarding Context Metadata (if provided by backend) -->
                @if (hasOnboardingContext(inv)) {
                  <div class="onboarding-context-box">
                    <div class="context-title">
                      <svg lucideLayers class="context-icon"></svg>
                      <span>Pre-configured Onboarding Assignments</span>
                    </div>
                    <div class="context-tags">
                      @if (inv.targetWorkspaceName) {
                        <span class="context-tag">
                          <span class="tag-label">Workspace:</span>
                          <span class="tag-value">{{ inv.targetWorkspaceName }}</span>
                        </span>
                      }
                      @if (inv.targetGroupName) {
                        <span class="context-tag">
                          <span class="tag-label">Group:</span>
                          <span class="tag-value">
                            {{ inv.targetGroupName }}
                            @if (inv.targetGroupRole) {
                              ({{ inv.targetGroupRole }})
                            }
                          </span>
                        </span>
                      }
                      @if (inv.onboardingPermissionLevel) {
                        <span class="context-tag">
                          <span class="tag-label">Permission:</span>
                          <span class="tag-value">{{ inv.onboardingPermissionLevel }}</span>
                        </span>
                      }
                    </div>
                  </div>
                }

                <!-- Action Footer (For Pending Invitations) -->
                @if (inv.status === 'PENDING' && !isExpired(inv.expiresAt)) {
                  <div class="card-action-footer">
                    <button
                      type="button"
                      class="btn-action btn-decline"
                      [disabled]="isProcessing(inv.token)"
                      (click)="onDecline(inv)">
                      @if (isProcessing(inv.token)) {
                        <svg lucideRefreshCw class="btn-icon spin"></svg>
                      } @else {
                        <svg lucideX class="btn-icon"></svg>
                      }
                      <span>Decline</span>
                    </button>

                    <button
                      type="button"
                      class="btn-action btn-accept"
                      [disabled]="isProcessing(inv.token)"
                      (click)="onAccept(inv)">
                      @if (isProcessing(inv.token)) {
                        <svg lucideRefreshCw class="btn-icon spin"></svg>
                      } @else {
                        <svg lucideCheck class="btn-icon"></svg>
                      }
                      <span>Accept Invitation</span>
                    </button>
                  </div>
                }

                <!-- Post-Acceptance Transition Callout -->
                @if (inv.status === 'ACCEPTED') {
                  <div class="card-success-banner">
                    <div class="success-banner-left">
                      <svg lucideCheckCircle class="success-icon"></svg>
                      <div class="success-text">
                        <strong>Invitation Accepted!</strong>
                        <span>You are now a member of {{ inv.organizationName || inv.orgCode }}.</span>
                      </div>
                    </div>
                    <button
                      type="button"
                      class="btn-switch-org"
                      (click)="onSwitchToOrganization(inv.orgCode)">
                      <span>Go to Organization</span>
                      <svg lucideArrowRight class="btn-icon"></svg>
                    </button>
                  </div>
                }

                <!-- Post-Declined Callout -->
                @if (inv.status === 'DECLINED') {
                  <div class="card-declined-banner">
                    <svg lucideX class="declined-icon"></svg>
                    <span>Invitation declined.</span>
                  </div>
                }
              </article>
            }
          </div>
        }
      </main>
    </div>
  `,
  styles: [`
    /* ── Layout & Typography ─────────────────────────────────────────────── */
    .invitations-page-layout {
      display: flex;
      flex-direction: column;
      gap: 20px;
      padding: 24px 32px 48px;
      max-width: 1040px;
      margin: 0 auto;
      width: 100%;
      box-sizing: border-box;
      color: #1E293B;
      font-family: 'Inter', system-ui, -apple-system, sans-serif;
    }

    /* ── Page Header ─────────────────────────────────────────────────────── */
    .invitations-page-header {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .header-main-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
    }

    .title-with-icon {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .header-icon-badge {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background-color: #FFF7ED;
      color: #EA580C;
      border: 1px solid #FFEDD5;
      flex-shrink: 0;
      box-shadow: 0 1px 2px rgba(234, 88, 12, 0.05);
    }

    .header-lucide-icon {
      width: 22px;
      height: 22px;
    }

    h1 {
      font-size: 1.5rem;
      font-weight: 700;
      line-height: 1.25;
      color: #0F172A;
      margin: 0;
      letter-spacing: -0.01em;
    }

    .subtitle {
      font-size: 0.875rem;
      color: #64748B;
      margin: 2px 0 0;
      line-height: 1.4;
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .btn-refresh {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 8px 14px;
      border-radius: 8px;
      background-color: #FFFFFF;
      border: 1px solid #E2E8F0;
      color: #334155;
      font-size: 0.84rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease-in-out;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
    }

    .btn-refresh:hover:not(:disabled) {
      background-color: #F8FAFC;
      border-color: #CBD5E1;
      color: #0F172A;
    }

    .btn-refresh:disabled {
      opacity: 0.6;
      cursor: not-allowed;
    }

    .btn-icon {
      width: 15px;
      height: 15px;
      flex-shrink: 0;
    }

    .spin {
      animation: spin 0.8s linear infinite;
    }

    @keyframes spin {
      100% { transform: rotate(360deg); }
    }

    /* ── Toolbar Row (Tabs & Search) ──────────────────────────────────────── */
    .inbox-toolbar-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding-bottom: 12px;
      border-bottom: 1px solid #E2E8F0;
      flex-wrap: wrap;
    }

    .inbox-tabs {
      display: flex;
      align-items: center;
      gap: 4px;
      background-color: #F1F5F9;
      padding: 3px;
      border-radius: 8px;
    }

    .tab-btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 14px;
      border-radius: 6px;
      border: none;
      background: transparent;
      color: #64748B;
      font-size: 0.83rem;
      font-weight: 500;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .tab-btn:hover:not(.active) {
      color: #334155;
    }

    .tab-btn.active {
      background-color: #FFFFFF;
      color: #0F172A;
      font-weight: 600;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
    }

    .tab-badge {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 1px 7px;
      border-radius: 10px;
      background-color: #E2E8F0;
      color: #475569;
      font-size: 0.75rem;
      font-weight: 600;
      min-width: 18px;
    }

    .tab-badge.badge-active {
      background-color: #FFEDD5;
      color: #C2410C;
    }

    .search-box {
      position: relative;
      display: flex;
      align-items: center;
      min-width: 260px;
    }

    .search-icon {
      position: absolute;
      left: 10px;
      width: 15px;
      height: 15px;
      color: #94A3B8;
      pointer-events: none;
    }

    .search-input {
      width: 100%;
      padding: 7px 32px 7px 32px;
      border-radius: 7px;
      border: 1px solid #E2E8F0;
      background-color: #FFFFFF;
      font-size: 0.83rem;
      color: #1E293B;
      outline: none;
      transition: all 0.15s ease;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
    }

    .search-input:focus {
      border-color: #F97316;
      box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
    }

    .btn-clear-search {
      position: absolute;
      right: 8px;
      background: none;
      border: none;
      color: #94A3B8;
      cursor: pointer;
      padding: 2px;
      display: flex;
      align-items: center;
    }

    .clear-icon {
      width: 14px;
      height: 14px;
    }

    /* ── Main Container ────────────────────────────────────────────────── */
    .invitations-container {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    /* ── Error & Pending Banners ───────────────────────────────────────── */
    .error-alert-banner {
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 12px 16px;
      border-radius: 10px;
      background-color: #FEF2F2;
      border: 1px solid #FECACA;
      color: #991B1B;
    }

    .alert-icon {
      width: 20px;
      height: 20px;
      flex-shrink: 0;
      color: #DC2626;
    }

    .alert-content {
      flex: 1;
      font-size: 0.84rem;
    }

    .alert-content p {
      margin: 2px 0 0;
      color: #B91C1C;
    }

    .btn-retry {
      padding: 6px 12px;
      border-radius: 6px;
      background-color: #FFFFFF;
      border: 1px solid #FCA5A5;
      color: #991B1B;
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
    }

    .pending-summary-banner {
      display: flex;
      align-items: flex-start;
      gap: 14px;
      padding: 16px 20px;
      border-radius: 12px;
      background: linear-gradient(135deg, #FFF7ED 0%, #FFEDD5 100%);
      border: 1px solid #FED7AA;
      color: #9A3412;
      box-shadow: 0 1px 3px rgba(234, 88, 12, 0.06);
    }

    .banner-icon-col {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: 8px;
      background-color: #FFFFFF;
      color: #EA580C;
      flex-shrink: 0;
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
    }

    .sparkle-icon {
      width: 18px;
      height: 18px;
    }

    .banner-text-col h3 {
      font-size: 0.95rem;
      font-weight: 700;
      margin: 0 0 2px;
      color: #9A3412;
    }

    .banner-text-col p {
      font-size: 0.84rem;
      margin: 0;
      color: #C2410C;
      line-height: 1.45;
    }

    /* ── Loading Skeleton ──────────────────────────────────────────────── */
    .loading-state-card {
      background-color: #FFFFFF;
      border-radius: 12px;
      border: 1px solid #E2E8F0;
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .skeleton-header {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .skeleton-circle {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: #F1F5F9;
    }

    .skeleton-lines {
      display: flex;
      flex-direction: column;
      gap: 6px;
      flex: 1;
    }

    .skeleton-line {
      height: 12px;
      border-radius: 4px;
      background: linear-gradient(90deg, #F1F5F9 25%, #E2E8F0 50%, #F1F5F9 75%);
      background-size: 200% 100%;
      animation: shimmer 1.5s infinite;
    }

    .line-title { width: 180px; height: 16px; }
    .line-sub { width: 110px; }
    .line-row { width: 100%; }
    .line-row.short { width: 60%; }

    @keyframes shimmer {
      0% { background-position: 200% 0; }
      100% { background-position: -200% 0; }
    }

    /* ── Empty State Surface ───────────────────────────────────────────── */
    .empty-inbox-surface {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      text-align: center;
      padding: 64px 24px;
      background-color: #FFFFFF;
      border-radius: 14px;
      border: 1px solid #E2E8F0;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
    }

    .empty-icon-wrap {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 56px;
      height: 56px;
      border-radius: 16px;
      background-color: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #94A3B8;
      margin-bottom: 16px;
    }

    .empty-inbox-icon {
      width: 28px;
      height: 28px;
    }

    .empty-title {
      font-size: 1.1rem;
      font-weight: 700;
      color: #0F172A;
      margin: 0 0 6px;
    }

    .empty-description {
      font-size: 0.875rem;
      color: #64748B;
      max-width: 440px;
      margin: 0 0 20px;
      line-height: 1.5;
    }

    .btn-reset-filter {
      padding: 8px 16px;
      border-radius: 7px;
      background-color: #F1F5F9;
      border: 1px solid #CBD5E1;
      color: #334155;
      font-size: 0.83rem;
      font-weight: 600;
      cursor: pointer;
    }

    /* ── Inbox Card List ────────────────────────────────────────────────── */
    .inbox-card-list {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .invitation-card {
      background-color: #FFFFFF;
      border-radius: 12px;
      border: 1px solid #E2E8F0;
      padding: 22px 24px;
      display: flex;
      flex-direction: column;
      gap: 18px;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 1px 2px rgba(0, 0, 0, 0.02);
      transition: all 0.2s ease-in-out;
    }

    .invitation-card:hover {
      border-color: #CBD5E1;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06), 0 1px 3px rgba(0, 0, 0, 0.03);
    }

    .invitation-card.card-accepted {
      border-color: #BBF7D0;
      background-color: #FAFDFB;
    }

    .invitation-card.card-declined {
      opacity: 0.75;
      background-color: #F8FAFC;
      border-color: #E2E8F0;
    }

    .invitation-card.card-expired {
      border-color: #FECACA;
      background-color: #FFFBFC;
    }

    /* Card Header */
    .card-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;

    }

    .org-identity {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .org-avatar {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background-color: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #334155;
      flex-shrink: 0;
    }

    .org-icon {
      width: 22px;
      height: 22px;
    }

    .org-details {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .org-name {
      font-size: 1.05rem;
      font-weight: 700;
      color: #0F172A;
      margin: 0;
      line-height: 1.3;
    }

    .org-meta-line {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .org-code-badge {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 1px 7px;
      border-radius: 5px;
      background-color: #F1F5F9;
      font-size: 0.72rem;
      font-weight: 600;
      color: #475569;
      letter-spacing: 0.02em;
    }

    .code-label {
      color: #94A3B8;
      font-weight: 500;
    }

    .code-val {
      font-family: monospace;
      color: #334155;
    }

    /* Status Chips */
    .status-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 4px 10px;
      border-radius: 20px;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1;
    }

    .status-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    .status-pending {
      background-color: #FFEDD5;
      color: #C2410C;
    }

    .status-pending .status-dot {
      background-color: #EA580C;
    }

    .status-accepted {
      background-color: #DCFCE7;
      color: #15803D;
    }

    .status-accepted .status-dot {
      background-color: #16A34A;
    }

    .status-declined {
      background-color: #F1F5F9;
      color: #64748B;
    }

    .status-declined .status-dot {
      background-color: #94A3B8;
    }

    .status-expired {
      background-color: #FEE2E2;
      color: #B91C1C;
    }

    .status-expired .status-dot {
      background-color: #DC2626;
    }

    /* Details Grid */
    .card-details-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      padding: 14px 16px;
      background-color: #F8FAFC;
      border-radius: 8px;
      border: 1px solid #F1F5F9;
    }

    .detail-item {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .detail-label {
      display: flex;
      align-items: center;
      gap: 5px;
      font-size: 0.75rem;
      font-weight: 600;
      color: #64748B;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .item-icon {
      width: 13px;
      height: 13px;
      color: #94A3B8;
    }

    .detail-value {
      font-size: 0.875rem;
      color: #1E293B;
      line-height: 1.3;
    }

    .text-semibold {
      font-weight: 600;
    }

    .text-urgent {
      color: #C2410C;
      font-weight: 600;
    }

    .text-muted {
      color: #94A3B8;
    }

    .role-badge {
      display: inline-block;
      padding: 2px 8px;
      border-radius: 5px;
      font-size: 0.78rem;
      font-weight: 600;
    }

    .role-admin {
      background-color: #EDE9FE;
      color: #6D28D9;
    }

    .role-manager {
      background-color: #E0F2FE;
      color: #0369A1;
    }

    .role-user {
      background-color: #F1F5F9;
      color: #334155;
    }

    /* Note Box */
    .invitation-note-box {
      padding: 12px 14px;
      border-radius: 8px;
      background-color: #FFFBEB;
      border: 1px solid #FDE68A;
      color: #92400E;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .note-header {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: #B45309;
    }

    .note-icon {
      width: 14px;
      height: 14px;
    }

    .note-body {
      font-size: 0.85rem;
      font-style: italic;
      margin: 0;
      line-height: 1.4;

    }

    /* Onboarding Context Box */
    .onboarding-context-box {
      display: flex;
      flex-direction: column;
      gap: 8px;
      padding: 12px 14px;
      background-color: #EFF6FF;
      border: 1px solid #BFDBFE;
      border-radius: 8px;
    }

    .context-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      color: #1D4ED8;
      text-transform: uppercase;
      letter-spacing: 0.03em;
    }

    .context-icon {
      width: 14px;
      height: 14px;
    }

    .context-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }

    .context-tag {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      padding: 3px 8px;
      border-radius: 5px;
      background-color: #FFFFFF;
      border: 1px solid #93C5FD;
      font-size: 0.78rem;
    }

    .tag-label {
      color: #3B82F6;
      font-weight: 600;
    }

    .tag-value {
      color: #1E3A8A;
      font-weight: 500;
    }

    /* Action Footer */
    .card-action-footer {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 12px;
      padding-top: 10px;
      border-top: 1px solid #F1F5F9;
    }

    .btn-action {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 9px 18px;
      border-radius: 8px;
      font-size: 0.875rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease-in-out;
    }

    .btn-decline {
      background-color: #FFFFFF;
      border: 1px solid #CBD5E1;
      color: #475569;
    }

    .btn-decline:hover:not(:disabled) {
      background-color: #FEF2F2;
      border-color: #FCA5A5;
      color: #991B1B;
    }

    .btn-accept {
      background: linear-gradient(180deg, #F97316 0%, #EA580C 100%);
      border: 1px solid #EA580C;
      color: #FFFFFF;
      box-shadow: 0 1px 3px rgba(234, 88, 12, 0.25);
    }

    .btn-accept:hover:not(:disabled) {
      background: linear-gradient(180deg, #EA580C 0%, #C2410C 100%);
      box-shadow: 0 2px 6px rgba(234, 88, 12, 0.35);
      transform: translateY(-1px);
    }

    .btn-action:disabled {
      opacity: 0.6;
      cursor: not-allowed;
      transform: none !important;
    }

    /* Post-Acceptance Success Banner */
    .card-success-banner {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      padding: 12px 16px;
      border-radius: 8px;
      background-color: #F0FDF4;
      border: 1px solid #DCFCE7;
      color: #166534;
      flex-wrap: wrap;
    }

    .success-banner-left {
      display: flex;
      align-items: center;
      gap: 10px;
    }

    .success-icon {
      width: 20px;
      height: 20px;
      color: #16A34A;
      flex-shrink: 0;
    }

    .success-text {
      display: flex;
      flex-direction: column;
      font-size: 0.84rem;
    }

    .success-text strong {
      color: #14532D;
      font-weight: 700;
    }

    .success-text span {
      color: #166534;
    }

    .btn-switch-org {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 7px 14px;
      border-radius: 6px;
      background-color: #166534;
      border: 1px solid #14532D;
      color: #FFFFFF;
      font-size: 0.81rem;
      font-weight: 600;
      cursor: pointer;
      transition: all 0.15s ease;
    }

    .btn-switch-org:hover {
      background-color: #14532D;
    }

    /* Post-Declined Banner */
    .card-declined-banner {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 10px 14px;
      border-radius: 8px;
      background-color: #F8FAFC;
      border: 1px solid #E2E8F0;
      color: #64748B;
      font-size: 0.84rem;
    }

    .declined-icon {
      width: 16px;
      height: 16px;
      color: #94A3B8;
    }
  `],
})
export class MyInvitationsPageComponent implements OnInit {
  private readonly orgApiService = inject(OrganizationApiService);
  private readonly workspaceContextStore = inject(WorkspaceContextStore);
  private readonly notificationService = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  // State Signals
  readonly invitations = signal<OrganizationInvitationResponseDto[]>([]);
  readonly isLoading = signal<boolean>(true);
  readonly isRefreshing = signal<boolean>(false);
  readonly error = signal<string | null>(null);
  readonly activeTab = signal<InboxTabFilter>('PENDING');
  readonly searchQuery = signal<string>('');

  // Processing state per token
  readonly processingTokens = signal<Set<string>>(new Set());

  // Computed totals
  readonly totalCount = computed(() => this.invitations().length);
  readonly pendingCount = computed(
    () => this.invitations().filter((i) => i.status === 'PENDING' && !this.isExpired(i.expiresAt)).length
  );

  // Displayed filtered list
  readonly displayedInvitations = computed(() => {
    const query = this.searchQuery().trim().toLowerCase();
    const tab = this.activeTab();

    return this.invitations().filter((inv) => {
      // Tab filter
      if (tab === 'PENDING') {
        if (inv.status !== 'PENDING' || this.isExpired(inv.expiresAt)) {
          return false;
        }
      }

      // Search query filter
      if (query.length > 0) {
        const orgName = (inv.organizationName || '').toLowerCase();
        const orgCode = (inv.orgCode || '').toLowerCase();
        const inviter = (inv.inviterEmail || inv.inviterUserId || '').toLowerCase();
        const role = (inv.role || '').toLowerCase();

        return (
          orgName.includes(query) ||
          orgCode.includes(query) ||
          inviter.includes(query) ||
          role.includes(query)
        );
      }

      return true;
    });
  });

  ngOnInit(): void {
    this.loadInvitations();
  }

  /**
   * Loads user pending invitations from API.
   * If a ?token= parameter is provided in the URL route, fetches details for that token as well.
   */
  loadInvitations(showRefreshing = false): void {
    if (showRefreshing) {
      this.isRefreshing.set(true);
    } else {
      this.isLoading.set(true);
    }
    this.error.set(null);

    const tokenFromUrl = this.route.snapshot.queryParamMap.get('token');

    this.orgApiService.getMyPendingInvitations().subscribe({
      next: (list) => {
        let allItems = list || [];
        const pendingCount = allItems.filter((item) => item.status === 'PENDING').length;
        this.orgApiService.pendingInvitationsCount.set(pendingCount);

        // If specific token in query params is missing from the pending list, attempt to fetch it
        if (tokenFromUrl && !allItems.some((item) => item.token === tokenFromUrl)) {
          this.orgApiService.getInvitationByToken(tokenFromUrl).subscribe({
            next: (specificInv) => {
              if (specificInv) {
                this.invitations.set([specificInv, ...allItems]);
              } else {
                this.invitations.set(allItems);
              }
              this.finishLoading();
            },
            error: () => {
              this.invitations.set(allItems);
              this.finishLoading();
            },
          });
        } else {
          this.invitations.set(allItems);
          this.finishLoading();
        }
      },
      error: (err) => {
        console.error('Failed to load pending invitations:', err);
        this.error.set(
          err?.error?.message || 'Unable to connect to the server to load invitations.'
        );
        this.finishLoading();
      },
    });
  }

  private finishLoading(): void {
    this.isLoading.set(false);
    this.isRefreshing.set(false);
  }

  onRefresh(): void {
    this.loadInvitations(true);
  }

  setTab(tab: InboxTabFilter): void {
    this.activeTab.set(tab);
  }

  onSearchInput(event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    this.searchQuery.set(val);
  }

  clearSearch(): void {
    this.searchQuery.set('');
  }

  isProcessing(token: string): boolean {
    return this.processingTokens().has(token);
  }

  /**
   * Action: Accept Invitation
   */
  onAccept(inv: OrganizationInvitationResponseDto): void {
    if (this.isProcessing(inv.token)) return;

    this.setProcessing(inv.token, true);

    this.orgApiService.acceptInvitation(inv.token).subscribe({
      next: (updatedInv) => {
        this.setProcessing(inv.token, false);

        // Update local item status
        this.invitations.update((list) =>
          list.map((item) =>
            item.token === inv.token ? { ...item, status: 'ACCEPTED' } : item
          )
        );

        const targetOrgName = inv.organizationName || inv.orgCode;
        this.notificationService.success(
          `Invitation accepted! You are now a member of ${targetOrgName}.`
        );

        // Reload user available workspaces in the workspace context store with explicit force reload
        this.workspaceContextStore.loadUserWorkspaces(true).subscribe();
      },
      error: (err) => {
        this.setProcessing(inv.token, false);
        console.error('Failed to accept invitation:', err);
        this.notificationService.error(
          err?.error?.message || 'Failed to accept invitation. Please try again.'
        );
      },
    });
  }

  /**
   * Action: Decline Invitation
   */
  onDecline(inv: OrganizationInvitationResponseDto): void {
    if (this.isProcessing(inv.token)) return;

    this.setProcessing(inv.token, true);

    this.orgApiService.declineInvitation(inv.token).subscribe({
      next: () => {
        this.setProcessing(inv.token, false);

        // Update local item status to DECLINED
        this.invitations.update((list) =>
          list.map((item) =>
            item.token === inv.token ? { ...item, status: 'DECLINED' } : item
          )
        );

        const targetOrgName = inv.organizationName || inv.orgCode;
        this.notificationService.info(
          `Invitation to join ${targetOrgName} has been declined.`
        );
      },
      error: (err) => {
        this.setProcessing(inv.token, false);
        console.error('Failed to decline invitation:', err);
        this.notificationService.error(
          err?.error?.message || 'Failed to decline invitation. Please try again.'
        );
      },
    });
  }

  /**
   * Navigates user to joined organization workspace dashboard
   */
  onSwitchToOrganization(orgCode: string): void {
    this.router.navigate(['/w', orgCode, 's', 'default', 'dashboard']);
  }

  private setProcessing(token: string, processing: boolean): void {
    this.processingTokens.update((set) => {
      const next = new Set(set);
      if (processing) {
        next.add(token);
      } else {
        next.delete(token);
      }
      return next;
    });
  }

  /* ── Formatting Helpers ─────────────────────────────────────────────── */

  getOrgDisplayName(orgCode: string): string {
    if (!orgCode) return 'Organization';
    return orgCode
      .split(/[-_]/)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
      .join(' ');
  }

  formatRole(role?: string): string {
    if (!role) return 'Member';
    const r = role.toUpperCase();
    if (r.includes('ADMIN')) return 'Administrator';
    if (r.includes('MANAGER')) return 'Manager';
    if (r.includes('USER') || r.includes('MEMBER')) return 'Member';
    return role.replace(/^ROLE_/i, '').toLowerCase().replace(/\b\w/g, (l) => l.toUpperCase());
  }

  getRoleBadgeClass(role?: string): string {
    if (!role) return 'role-user';
    const r = role.toUpperCase();
    if (r.includes('ADMIN')) return 'role-admin';
    if (r.includes('MANAGER')) return 'role-manager';
    return 'role-user';
  }

  getStatusInfo(
    status?: string,
    expiresAt?: string
  ): { label: string; class: string } {
    if (status === 'PENDING' && this.isExpired(expiresAt)) {
      return { label: 'Expired', class: 'status-expired' };
    }

    switch (status) {
      case 'PENDING':
        return { label: 'Action Required', class: 'status-pending' };
      case 'ACCEPTED':
        return { label: 'Accepted', class: 'status-accepted' };
      case 'DECLINED':
        return { label: 'Declined', class: 'status-declined' };
      case 'EXPIRED':
        return { label: 'Expired', class: 'status-expired' };
      case 'REVOKED':
        return { label: 'Revoked', class: 'status-declined' };
      default:
        return { label: status || 'Pending', class: 'status-pending' };
    }
  }

  isExpired(expiresAt?: string): boolean {
    if (!expiresAt) return false;
    return new Date(expiresAt).getTime() < Date.now();
  }

  getExpirationInfo(
    expiresAt?: string
  ): { text: string; isExpired: boolean; isUrgent: boolean } {
    if (!expiresAt) {
      return { text: 'No expiration limit', isExpired: false, isUrgent: false };
    }

    const expTime = new Date(expiresAt).getTime();
    const diffMs = expTime - Date.now();

    if (diffMs <= 0) {
      return { text: 'Expired', isExpired: true, isUrgent: false };
    }

    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffDays = Math.floor(diffHours / 24);

    if (diffHours < 48) {
      return {
        text: `Expires in ${diffHours} hr${diffHours === 1 ? '' : 's'}`,
        isExpired: false,
        isUrgent: true,
      };
    }

    return {
      text: `Expires in ${diffDays} day${diffDays === 1 ? '' : 's'}`,
      isExpired: false,
      isUrgent: false,
    };
  }

  hasOnboardingContext(inv: OrganizationInvitationResponseDto): boolean {
    return !!(
      inv.targetWorkspaceName ||
      inv.targetGroupName ||
      inv.onboardingPermissionLevel
    );
  }
}
