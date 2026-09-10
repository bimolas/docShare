import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-audit-empty-state',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state-wrap">
      <div class="empty-icon-wrap">
        <app-icon [name]="hasActiveFilters() ? 'search' : 'clipboard-list'" [size]="28" />
      </div>
      <h4 class="empty-title">
        {{ hasActiveFilters() ? 'No Matching Audit Events' : 'No Audit Activity' }}
      </h4>
      <p class="empty-desc">
        @if (hasActiveFilters()) {
          No activity logs match your active search and filter criteria. Try adjusting your keyword, category, or date range.
        } @else {
          No activity has been logged in this scope yet. Actions such as uploads, permissions, access requests, and team changes will appear here in real time.
        }
      </p>
      @if (hasActiveFilters()) {
        <button type="button" class="btn-clear" (click)="clearFilters.emit()">
          <app-icon name="refresh-cw" [size]="14" />
          <span>Reset Filters</span>
        </button>
      }
    </div>
  `,
  styles: [
    `
      .empty-state-wrap {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        text-align: center;
        padding: 4rem 2rem;
        background: #ffffff;
        border: 1px dashed #cbd5e1;
        border-radius: 8px;
        margin: 1rem 0;
      }

      .empty-icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 52px;
        height: 52px;
        border-radius: 50%;
        background: #f8fafc;
        color: #64748b;
        margin-bottom: 1rem;
      }

      .empty-title {
        font-size: 1rem;
        font-weight: 600;
        color: #0f172a;
        margin: 0 0 0.5rem 0;
      }

      .empty-desc {
        max-width: 440px;
        font-size: 0.84rem;
        color: #64748b;
        line-height: 1.5;
        margin: 0 0 1.25rem 0;
      }

      .btn-clear {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.45rem 0.9rem;
        background: #f8fafc;
        border: 1px solid #cbd5e1;
        border-radius: 6px;
        font-size: 0.8125rem;
        font-weight: 500;
        color: #334155;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover {
          background: #f1f5f9;
          color: #0f172a;
        }
      }
    `,
  ],
})
export class AuditEmptyStateComponent {
  hasActiveFilters = input<boolean>(false);
  clearFilters = output<void>();
}
