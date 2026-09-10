import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';

export type TeamStatusFilter = 'ALL' | 'ACTIVE' | 'ARCHIVED';

/**
 * Team Status Filter — pill-style tab group matching the Members page status tabs.
 */
@Component({
  selector: 'sd-team-status-filter',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="status-filter-tabs" role="group" aria-label="Filter teams by status">
      <button
        type="button"
        class="filter-tab"
        [class.active]="activeFilter() === 'ALL'"
        (click)="onSelect('ALL')"
      >
        All
        <span class="tab-badge">{{ allCount() }}</span>
      </button>

      <button
        type="button"
        class="filter-tab"
        [class.active]="activeFilter() === 'ACTIVE'"
        (click)="onSelect('ACTIVE')"
      >
        <span class="status-dot dot-active"></span>
        Active
        <span class="tab-badge">{{ activeCount() }}</span>
      </button>

      <button
        type="button"
        class="filter-tab"
        [class.active]="activeFilter() === 'ARCHIVED'"
        (click)="onSelect('ARCHIVED')"
      >
        <span class="status-dot dot-archived"></span>
        Archived
        <span class="tab-badge">{{ archivedCount() }}</span>
      </button>
    </div>
  `,
  styles: [
    `
      .status-filter-tabs {
        display: flex;
        align-items: center;
        gap: 0.25rem;
        background: #f4f4f5;
        padding: 0.25rem;
        border-radius: 8px;
      }

      .filter-tab {
        display: flex;
        align-items: center;
        gap: 0.375rem;
        padding: 0.375rem 0.75rem;
        border: none;
        background: transparent;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 500;
        color: #71717a;
        cursor: pointer;
        transition: all 0.15s ease;
        white-space: nowrap;

        &:hover {
          color: #18181b;
        }

        &.active {
          background: #ffffff;
          color: #09090b;
          font-weight: 600;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
        }
      }

      .tab-badge {
        font-size: 0.7rem;
        padding: 0.1rem 0.35rem;
        border-radius: 9999px;
        background: #e4e4e7;
        color: #52525b;

        .active & {
          background: #f4f4f5;
          color: #18181b;
        }
      }

      .status-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        flex-shrink: 0;
      }

      .dot-active {
        background-color: #10b981;
      }

      .dot-archived {
        background-color: #9ca3af;
      }
    `,
  ],
})
export class TeamStatusFilterComponent {
  activeFilter = input<TeamStatusFilter>('ALL');
  allCount = input<number>(0);
  activeCount = input<number>(0);
  archivedCount = input<number>(0);

  filterChange = output<TeamStatusFilter>();

  onSelect(filter: TeamStatusFilter): void {
    if (this.activeFilter() !== filter) {
      this.filterChange.emit(filter);
    }
  }
}
