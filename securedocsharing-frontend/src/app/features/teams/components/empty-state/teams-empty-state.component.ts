import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  computed,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideUsersRound, LucideSearch, LucidePlus } from '@lucide/angular';

export type TeamsEmptyStateType = 'no-teams' | 'no-results';

/**
 * Teams Empty State — minimal, intentional.
 * No-teams: icon + copy + optional Create Team link (secondary, not a big orange button).
 * No-results: icon + copy + clear filters link.
 */
@Component({
  selector: 'sd-teams-empty-state',
  standalone: true,
  imports: [CommonModule, LucideUsersRound, LucideSearch, LucidePlus],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-container">
      @if (effectiveType() === 'no-teams') {
        <div class="icon-wrap">
          <svg lucideUsersRound class="empty-icon"></svg>
        </div>
        <h3 class="empty-title">No teams yet</h3>
        <p class="empty-body">
          This organization doesn't have any teams configured. Teams let you
          group members and manage workspace access collectively.
        </p>
        @if (canCreate()) {
          <button
            type="button"
            class="btn-secondary"
            (click)="createTeam.emit()"
          >
            <svg lucidePlus class="btn-icon"></svg>
            Create the first team
          </button>
        }
      } @else {
        <div class="icon-wrap">
          <svg lucideSearch class="empty-icon"></svg>
        </div>
        <h3 class="empty-title">No matching teams</h3>
        <p class="empty-body">
          No teams matched your search or filter.
        </p>
        <button
          type="button"
          class="btn-secondary"
          (click)="onReset()"
        >
          Clear filters
        </button>
      }
    </div>
  `,
  styles: [
    `
      .empty-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 4.5rem 2rem;
        text-align: center;
      }

      .icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 56px;
        height: 56px;
        border-radius: 50%;
        background: #f4f4f5;
        color: #a1a1aa;
        margin-bottom: 1.25rem;
      }

      .empty-icon {
        width: 26px;
        height: 26px;
      }

      .empty-title {
        font-size: 1.125rem;
        font-weight: 600;
        color: #18181b;
        margin: 0 0 0.375rem 0;
      }

      .empty-body {
        font-size: 0.875rem;
        color: #71717a;
        margin: 0;
        max-width: 360px;
        line-height: 1.5;
      }

      .btn-secondary {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        margin-top: 1.25rem;
        padding: 0.5rem 1rem;
        font-size: 0.875rem;
        font-weight: 500;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.15s ease;
        background: #ffffff;
        border: 1px solid #e4e4e7;
        color: #3f3f46;

        &:hover {
          background: #f4f4f5;
          color: #09090b;
        }
      }

      .btn-icon {
        width: 15px;
        height: 15px;
      }
    `,
  ],
})
export class TeamsEmptyStateComponent {
  type = input<TeamsEmptyStateType>('no-teams');
  isFiltered = input<boolean>(false);
  canCreate = input<boolean>(false);

  clearFilters = output<void>();
  createTeam = output<void>();

  effectiveType = computed(() =>
    this.isFiltered() ? 'no-results' : this.type()
  );

  onReset(): void {
    this.clearFilters.emit();
  }
}
