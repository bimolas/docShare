import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { UnifiedAuditEventResponseDto } from '../../../core/models/unified-audit.model';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';
import { DropdownComponent } from '../../../shared/ui/dropdown/dropdown.component';
import {
  formatActionDisplay,
  formatTargetName,
  formatTargetSecondary,
  hasRealTransitionDiff,
} from '../utils/audit-format.util';

@Component({
  selector: 'app-audit-table',
  standalone: true,
  imports: [CommonModule, DatePipe, IconComponent, DropdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="audit-table-wrapper">
      <div class="table-container">
        <table class="audit-table" role="table" aria-label="Audit Events">
          <thead>
            <tr role="row">
              <th scope="col" class="th-cat">Category</th>
              <th scope="col" class="th-actor">Who (Actor)</th>
              <th scope="col" class="th-what">What (Action & Resource)</th>
              <th scope="col" class="th-where">Where</th>
              <th scope="col" class="th-when">When</th>
              <th scope="col" class="th-result">Result</th>
            </tr>
          </thead>
          <tbody>
            @for (event of events(); track event.id) {
              <tr
                role="row"
                class="table-row"
                tabindex="0"
                (click)="rowClick.emit(event)"
                (keydown.enter)="rowClick.emit(event)"
                (keydown.space)="rowClick.emit(event)"
                [attr.aria-label]="'View details for ' + (event.actionDisplay || event.action)"
              >
                <!-- 1. Category -->
                <td class="td-cat">
                  <span
                    class="category-badge"
                    [class.badge-accent]="event.category === 'ACCESS' || event.category === 'SECURITY'"
                    [class.badge-neutral]="event.category !== 'ACCESS' && event.category !== 'SECURITY'"
                  >
                    {{ event.category }}
                  </span>
                </td>

                <!-- 2. WHO (Actor) -->
                <td class="td-actor">
                  <div class="actor-cell">
                    <div class="actor-avatar">
                      {{ getActorInitials(event) }}
                    </div>
                    <div class="actor-meta">
                      <span class="actor-name" [title]="getActorDisplay(event)">
                        {{ getActorDisplay(event) }}
                      </span>
                      @if (event.actor?.email) {
                        <span class="actor-email" [title]="event.actor?.email">
                          {{ event.actor?.email }}
                        </span>
                      }
                      @if (event.actor?.impersonatedBy) {
                        <span class="impersonated-pill" title="Action performed via impersonation">
                          Impersonated
                        </span>
                      }
                    </div>
                  </div>
                </td>

                <!-- 3. WHAT (Action & Target) -->
                <td class="td-what">
                  <div class="action-cell">
                    <div class="action-header">
                      <span class="action-verb">{{ formatActionDisplay(event) }}</span>
                    </div>

                    <div class="target-row">
                      @if (event.target?.type) {
                        <app-icon [name]="getTargetIcon(event.target?.type)" [size]="13" class="target-icon" />
                      }
                      <span class="target-name" [title]="formatTargetName(event)">
                        {{ formatTargetName(event) }}
                      </span>
                      @if (formatTargetSecondary(event); as sec) {
                        <span class="target-secondary">{{ sec }}</span>
                      }
                      @if (hasRealTransitionDiff(event)) {
                        <span class="diff-chip">
                          <span class="diff-prev">{{ event.changes?.previousValue }}</span>
                          <span class="diff-sep">→</span>
                          <span class="diff-next">{{ event.changes?.newValue }}</span>
                        </span>
                      }
                    </div>
                  </div>
                </td>

                <!-- 4. WHERE (Location) -->
                <td class="td-where">
                  <div class="location-cell">
                    @if (event.location?.pathSnapshot) {
                      <span class="location-path" [title]="event.location?.pathSnapshot">
                        {{ event.location?.pathSnapshot }}
                      </span>
                    } @else if (event.location?.folderName) {
                      <span class="location-path" [title]="event.location?.folderName">
                        📁 {{ event.location?.folderName }}
                      </span>
                    } @else if (event.location?.workspaceName) {
                      <span class="location-path" [title]="event.location?.workspaceName">
                        🏢 {{ event.location?.workspaceName }}
                      </span>
                    } @else {
                      <span class="location-path text-slate-400">
                        {{ event.location?.spaceType || '—' }}
                      </span>
                    }

                    @if (event.location?.orgCode) {
                      <span class="org-sub-badge">{{ event.location?.orgCode }}</span>
                    }
                  </div>
                </td>

                <!-- 5. WHEN (Timestamp) -->
                <td class="td-when">
                  <div class="time-cell font-tabular" [title]="event.timestamp">
                    <span class="time-primary">
                      {{ (event.timestamp | date: 'MMM d, y') || '—' }}
                    </span>
                    <span class="time-secondary">
                      {{ (event.timestamp | date: 'h:mm a') || '' }}
                    </span>
                  </div>
                </td>

                <!-- 6. RESULT -->
                <td class="td-result">
                  <div class="result-cell">
                    <span
                      class="status-pill"
                      [ngClass]="'status-' + (event.result?.status || 'success').toLowerCase()"
                    >
                      <span class="status-dot"></span>
                      <span>{{ event.result?.status || 'SUCCESS' }}</span>
                    </span>

                    @if (event.result?.blockchainStatus === 'ANCHORED' || event.metadata?.recordHash) {
                      <span class="integrity-indicator" title="Cryptographically verified / anchored record">
                        <app-icon name="shield-check" [size]="12" class="text-emerald-600" />
                      </span>
                    }
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <!-- Pagination Toolbar -->
      <div class="pagination-toolbar">
        <div class="pagination-info font-tabular">
          Showing
          <strong>{{ paginationStart() }}</strong>
          to
          <strong>{{ paginationEnd() }}</strong>
          of
          <strong>{{ totalElements() }}</strong>
          events
        </div>

        <div class="pagination-controls">
          <div class="page-size-select font-tabular">
            <span>Per page:</span>
            <sd-dropdown class="filter-dropdown-host" placement="top-start">
              <button dropdown-trigger type="button" class="filter-select-trigger page-size-trigger" aria-label="Events per page">
                <span class="select-label-text">{{ pageSize() }}</span>
                <app-icon name="chevron-down" [size]="12" class="select-arrow" />
              </button>
              <div dropdown-menu class="filter-select-menu">
                @for (size of pageSizeOptions; track size) {
                  <button type="button" class="filter-select-option" [class.selected]="pageSize() === size" (click)="pageSizeChange.emit(size)">
                    <span>{{ size }}</span>
                    @if (pageSize() === size) { <app-icon name="check" [size]="13" class="option-check" /> }
                  </button>
                }
              </div>
            </sd-dropdown>
          </div>

          <div class="page-nav-buttons">
            <button
              type="button"
              class="btn-nav"
              [disabled]="currentPage() === 0"
              (click)="pageChange.emit(currentPage() - 1)"
              aria-label="Previous page"
            >
              <app-icon name="chevron-left" [size]="14" />
              <span>Previous</span>
            </button>

            <span class="page-current font-tabular">
              {{ currentPage() + 1 }} / {{ totalPages() || 1 }}
            </span>

            <button
              type="button"
              class="btn-nav"
              [disabled]="currentPage() >= totalPages() - 1 || totalPages() === 0"
              (click)="pageChange.emit(currentPage() + 1)"
              aria-label="Next page"
            >
              <span>Next</span>
              <app-icon name="chevron-right" [size]="14" />
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [
    `
      .audit-table-wrapper {
        display: flex;
        flex-direction: column;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        overflow: hidden;
      }

      .table-container {
        width: 100%;
        overflow-x: auto;
      }

      .audit-table {
        width: 100%;
        border-collapse: collapse;
        text-align: left;
        font-size: 0.8125rem;

        thead {
          background: #f8fafc;
          border-bottom: 1px solid #e2e8f0;

          th {
            padding: 0.65rem 0.85rem;
            font-size: 0.75rem;
            font-weight: 600;
            color: #475569;
            text-transform: uppercase;
            letter-spacing: 0.04em;
            white-space: nowrap;
          }
        }

        tbody {
          tr.table-row {
            border-bottom: 1px solid #f1f5f9;
            cursor: pointer;
            transition: background 0.12s ease;

            &:last-child {
              border-bottom: none;
            }

            &:hover {
              background: #f8fafc;
            }

            &:focus-visible {
              outline: none;
              background: #fff7ed;
            }
          }

          td {
            padding: 0.75rem 0.85rem;
            vertical-align: middle;
          }
        }
      }

      /* ── Column Sizing ─────────────────────────── */
      .th-cat, .td-cat { width: 90px; }
      .th-actor, .td-actor { width: 190px; }
      .th-what, .td-what { min-width: 260px; }
      .th-where, .td-where { width: 180px; }
      .th-when, .td-when { width: 110px; }
      .th-result, .td-result { width: 100px; text-align: right; }

      /* ── Category Badges ───────────────────────── */
      .category-badge {
        display: inline-block;
        padding: 0.15rem 0.4rem;
        font-size: 0.6875rem;
        font-weight: 600;
        border-radius: 4px;
        letter-spacing: 0.02em;

        &.badge-accent {
          background: #fff7ed;
          color: #c2410c;
          border: 1px solid #fed7aa;
        }

        &.badge-neutral {
          background: #f1f5f9;
          color: #475569;
          border: 1px solid #e2e8f0;
        }
      }

      /* ── Actor Cell ────────────────────────────── */
      .actor-cell {
        display: flex;
        align-items: flex-start;
        gap: 0.5rem;
      }

      .actor-avatar {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 26px;
        height: 26px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #475569;
        font-size: 0.6875rem;
        font-weight: 600;
        flex-shrink: 0;
        border: 1px solid #e2e8f0;
      }

      .actor-meta {
        display: flex;
        flex-direction: column;
        min-width: 0;
      }

      .actor-name {
        font-weight: 500;
        color: #0f172a;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 140px;
      }

      .actor-email {
        font-size: 0.72rem;
        color: #64748b;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 140px;
      }

      .impersonated-pill {
        display: inline-block;
        font-size: 0.625rem;
        font-weight: 600;
        color: #b45309;
        background: #fef3c7;
        padding: 0 0.3rem;
        border-radius: 3px;
        margin-top: 0.15rem;
      }

      /* ── Action Cell ───────────────────────────── */
      .action-cell {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
      }

      .action-verb {
        font-weight: 500;
        color: #0f172a;
        line-height: 1.3;
      }

      .target-row {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        font-size: 0.75rem;
        color: #475569;
      }

      .target-icon {
        color: #64748b;
        flex-shrink: 0;
      }

      .target-name {
        font-weight: 500;
        color: #334155;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 240px;
      }

      .target-secondary {
        font-size: 0.68rem;
        color: #94a3b8;
        font-weight: 400;
        white-space: nowrap;
        flex-shrink: 0;
      }

      .diff-chip {
        display: inline-flex;
        align-items: center;
        gap: 0.2rem;
        font-size: 0.68rem;
        font-family: monospace;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 4px;
        padding: 0.05rem 0.35rem;
        flex-shrink: 0;
        max-width: 160px;
        overflow: hidden;
      }

      .diff-prev {
        color: #94a3b8;
        text-decoration: line-through;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 60px;
      }

      .diff-sep {
        color: #cbd5e1;
        flex-shrink: 0;
      }

      .diff-next {
        color: #16a34a;
        font-weight: 500;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        max-width: 60px;
      }

      /* ── Location Cell ─────────────────────────── */
      .location-cell {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
      }

      .location-path {
        font-size: 0.75rem;
        color: #475569;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-width: 160px;
      }

      .org-sub-badge {
        display: inline-block;
        font-size: 0.65rem;
        color: #64748b;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        padding: 0 0.3rem;
        border-radius: 3px;
        width: fit-content;
      }

      /* ── Time Cell ─────────────────────────────── */
      .time-cell {
        display: flex;
        flex-direction: column;
      }

      .time-primary {
        font-weight: 500;
        color: #334155;
      }

      .time-secondary {
        font-size: 0.72rem;
        color: #94a3b8;
      }

      /* ── Result Cell ───────────────────────────── */
      .result-cell {
        display: inline-flex;
        align-items: center;
        justify-content: flex-end;
        gap: 0.35rem;
      }

      .status-pill {
        display: inline-flex;
        align-items: center;
        gap: 0.3rem;
        padding: 0.15rem 0.45rem;
        border-radius: 9999px;
        font-size: 0.6875rem;
        font-weight: 600;

        .status-dot {
          width: 5px;
          height: 5px;
          border-radius: 50%;
        }

        &.status-success {
          background: #f0fdf4;
          color: #166534;
          border: 1px solid #bbf7d0;
          .status-dot { background: #16a34a; }
        }

        &.status-denied, &.status-failed {
          background: #fef2f2;
          color: #991b1b;
          border: 1px solid #fecaca;
          .status-dot { background: #dc2626; }
        }

        &.status-pending {
          background: #fffbeb;
          color: #92400e;
          border: 1px solid #fde68a;
          .status-dot { background: #d97706; }
        }

        &.status-revoked {
          background: #f8fafc;
          color: #475569;
          border: 1px solid #cbd5e1;
          .status-dot { background: #64748b; }
        }
      }

      .integrity-indicator {
        display: inline-flex;
        align-items: center;
        justify-content: center;
      }

      /* ── Pagination Toolbar ────────────────────── */
      .pagination-toolbar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.65rem 1rem;
        background: #f8fafc;
        border-top: 1px solid #e2e8f0;
        font-size: 0.75rem;
        color: #64748b;
      }

      .pagination-controls {
        display: flex;
        align-items: center;
        gap: 1rem;
      }

      .page-size-select {
        display: flex;
        align-items: center;
        gap: 0.35rem;
      }

      ::ng-deep .filter-dropdown-host,
      ::ng-deep .filter-dropdown-host .sd-dropdown-container {
        display: inline-block;
        width: auto;
      }

      .filter-select-trigger {
        display: inline-flex;
        align-items: center;
        gap: 5px;
        padding: 4px 8px 4px 10px;
        border: 1px solid #cbd5e1;
        border-radius: 5px;
        background: #ffffff;
        color: #334155;
        font-size: 0.75rem;
        font-weight: 500;
        cursor: pointer;
        min-height: 26px;
        outline: none;
        transition: border-color 0.15s, box-shadow 0.15s;
        white-space: nowrap;
        font-family: inherit;

        &:hover {
          border-color: #94a3b8;
          background: #f8fafc;
        }

        &[aria-expanded="true"] {
          border-color: var(--sd-color-brand-focus, #fb923c);
          box-shadow: 0 0 0 2px rgba(251, 146, 60, 0.15);
        }

        &[aria-expanded="true"] .select-arrow {
          transform: rotate(180deg);
        }
      }

      .select-arrow {
        color: #64748b;
        transition: transform 0.15s ease;
        flex-shrink: 0;
      }

      ::ng-deep .filter-dropdown-host .sd-dropdown-menu {
        min-width: 80px;
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
        padding: 6px 10px;
        border: none;
        background: transparent;
        border-radius: 5px;
        cursor: pointer;
        text-align: left;
        font-size: 0.75rem;
        color: #334155;
        font-family: inherit;
        transition: background 0.1s;

        &:hover {
          background: #f1f5f9;
        }

        &.selected {
          background: rgba(249, 115, 22, 0.07);
          color: var(--sd-color-primary, #f97316);
          font-weight: 600;
        }
      }

      .option-check {
        color: var(--sd-color-primary, #f97316);
        flex-shrink: 0;
      }

      .page-nav-buttons {
        display: flex;
        align-items: center;
        gap: 0.4rem;
      }

      .btn-nav {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        padding: 0.25rem 0.5rem;
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 4px;
        color: #334155;
        font-size: 0.75rem;
        font-weight: 500;
        cursor: pointer;
        transition: all 0.12s ease;

        &:hover:not(:disabled) {
          background: #f1f5f9;
          color: #0f172a;
        }

        &:disabled {
          opacity: 0.45;
          cursor: not-allowed;
        }
      }

      .page-current {
        font-size: 0.75rem;
        color: #475569;
        padding: 0 0.25rem;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }
    `,
  ],
})
export class AuditTableComponent {
  events = input<UnifiedAuditEventResponseDto[]>([]);
  currentPage = input<number>(0);
  pageSize = input<number>(20);
  totalElements = input<number>(0);
  totalPages = input<number>(0);

  rowClick = output<UnifiedAuditEventResponseDto>();
  pageChange = output<number>();
  pageSizeChange = output<number>();

  readonly pageSizeOptions = [10, 20, 50, 100];

  paginationStart(): number {
    if (this.totalElements() === 0) return 0;
    return this.currentPage() * this.pageSize() + 1;
  }

  paginationEnd(): number {
    const end = (this.currentPage() + 1) * this.pageSize();
    return Math.min(end, this.totalElements());
  }

  getActorInitials(event: UnifiedAuditEventResponseDto): string {
    const name = event.actor?.displayName || event.actor?.email;
    if (!name) return 'SYS';
    const parts = name.split(/[\s@._-]+/).filter(Boolean);
    if (parts.length >= 2) {
      return (parts[0][0] + parts[1][0]).toUpperCase();
    }
    return name.slice(0, 2).toUpperCase();
  }

  getActorDisplay(event: UnifiedAuditEventResponseDto): string {
    if (event.actor?.displayName) return event.actor.displayName;
    if (event.actor?.email) return event.actor.email.split('@')[0];
    if (event.actor?.userId) return 'User ' + event.actor.userId.slice(0, 8);
    return 'System';
  }

  getTargetIcon(type?: string): IconName {
    if (!type) return 'file';
    switch (type.toUpperCase()) {
      case 'DOCUMENT':
        return 'file-text';
      case 'FOLDER':
        return 'folder';
      case 'TEAM':
        return 'users-round';
      case 'WORKSPACE':
        return 'layers';
      case 'COLLABORATION':
        return 'share-2';
      default:
        return 'file';
    }
  }

  formatActionDisplay(event: UnifiedAuditEventResponseDto): string {
    return formatActionDisplay(event);
  }

  formatTargetName(event: UnifiedAuditEventResponseDto): string {
    return formatTargetName(event);
  }

  formatTargetSecondary(event: UnifiedAuditEventResponseDto): string | null {
    return formatTargetSecondary(event);
  }

  hasRealTransitionDiff(event: UnifiedAuditEventResponseDto): boolean {
    return hasRealTransitionDiff(event);
  }
}
