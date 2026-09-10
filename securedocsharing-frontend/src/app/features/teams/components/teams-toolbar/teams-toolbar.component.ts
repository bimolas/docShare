import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LucideSearch, LucideX, LucideRefreshCw } from '@lucide/angular';
import { TeamStatusFilterComponent, TeamStatusFilter } from '../team-status-filter/team-status-filter.component';

export type { TeamStatusFilter };

/**
 * Teams Toolbar — search input + status tabs + refresh button.
 * Styled to match the Members page toolbar surface exactly.
 */
@Component({
  selector: 'sd-teams-toolbar',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    LucideSearch,
    LucideX,
    LucideRefreshCw,
    TeamStatusFilterComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="toolbar-surface">
      <!-- Search Input -->
      <div class="search-input-wrapper">
        <svg lucideSearch class="search-icon"></svg>
        <input
          type="text"
          class="search-input"
          placeholder="Search teams by name or description..."
          [ngModel]="searchQuery()"
          (ngModelChange)="searchChange.emit($event)"
          aria-label="Search teams"
        />
        @if (searchQuery()) {
          <button
            type="button"
            class="btn-clear-search"
            (click)="searchChange.emit('')"
            title="Clear search"
          >
            <svg lucideX class="clear-icon"></svg>
          </button>
        }
      </div>

      <!-- Status Filter Tabs -->
      <sd-team-status-filter
        [activeFilter]="activeFilter()"
        [allCount]="allCount()"
        [activeCount]="activeCount()"
        [archivedCount]="archivedCount()"
        (filterChange)="filterChange.emit($event)"
      />

      <!-- Refresh Button -->
      <button
        type="button"
        class="btn-refresh"
        [class.spinning]="isLoading()"
        (click)="refresh.emit()"
        title="Refresh teams"
      >
        <svg lucideRefreshCw class="refresh-icon"></svg>
      </button>
    </div>
  `,
  styles: [
    `
      .toolbar-surface {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.75rem 1rem;
        gap: 1rem;
        background: #ffffff;
        border-radius: 12px;
        border: 1px solid #e4e4e7;
        box-shadow: 0 1px 2px 0 rgba(0, 0, 0, 0.04);
        flex-wrap: wrap;
      }

      /* Search */
      .search-input-wrapper {
        position: relative;
        display: flex;
        align-items: center;
        flex: 1;
        min-width: 240px;
        max-width: 380px;
      }

      .search-icon {
        position: absolute;
        left: 0.75rem;
        width: 16px;
        height: 16px;
        color: #a1a1aa;
        pointer-events: none;
      }

      .search-input {
        width: 100%;
        padding: 0.5rem 2.25rem 0.5rem 2.25rem;
        font-size: 0.875rem;
        color: #09090b;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        outline: none;
        transition: all 0.15s ease;

        &::placeholder {
          color: #94a3b8;
        }

        &:focus {
          background: #ffffff;
          border-color: #fb923c;
          box-shadow: 0 0 0 3px rgba(251, 146, 60, 0.15);
        }
      }

      .btn-clear-search {
        position: absolute;
        right: 0.5rem;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 24px;
        height: 24px;
        border: none;
        background: transparent;
        color: #a1a1aa;
        border-radius: 50%;
        cursor: pointer;

        &:hover {
          color: #27272a;
          background: #e4e4e7;
        }
      }

      .clear-icon {
        width: 14px;
        height: 14px;
      }

      /* Refresh */
      .btn-refresh {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 34px;
        height: 34px;
        border: 1px solid #e4e4e7;
        background: #ffffff;
        color: #71717a;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
        flex-shrink: 0;

        &:hover {
          color: #09090b;
          border-color: #cbd5e1;
          background: #f8fafc;
        }

        &.spinning .refresh-icon {
          animation: spin 1s linear infinite;
        }
      }

      .refresh-icon {
        width: 16px;
        height: 16px;
      }

      @keyframes spin {
        from { transform: rotate(0deg); }
        to   { transform: rotate(360deg); }
      }

      @media (max-width: 640px) {
        .toolbar-surface {
          flex-direction: column;
          align-items: stretch;
        }

        .search-input-wrapper {
          max-width: 100%;
        }
      }
    `,
  ],
})
export class TeamsToolbarComponent {
  searchQuery = input<string>('');
  activeFilter = input<TeamStatusFilter>('ALL');
  allCount = input<number>(0);
  activeCount = input<number>(0);
  archivedCount = input<number>(0);
  isLoading = input<boolean>(false);

  searchChange = output<string>();
  filterChange = output<TeamStatusFilter>();
  refresh = output<void>();
}
