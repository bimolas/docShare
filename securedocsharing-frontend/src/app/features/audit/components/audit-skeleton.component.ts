import { Component, ChangeDetectionStrategy, input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-audit-skeleton',
  standalone: true,
  imports: [CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="skeleton-table">
      @for (item of rowsArray(); track $index) {
        <div class="skeleton-row">
          <div class="skeleton-col col-cat">
            <div class="skeleton-pill animate-pulse"></div>
          </div>
          <div class="skeleton-col col-actor">
            <div class="skeleton-avatar animate-pulse"></div>
            <div class="skeleton-text-group">
              <div class="skeleton-line w-28 animate-pulse"></div>
              <div class="skeleton-line-sm w-36 animate-pulse"></div>
            </div>
          </div>
          <div class="skeleton-col col-summary">
            <div class="skeleton-line w-48 animate-pulse"></div>
            <div class="skeleton-line-sm w-64 animate-pulse"></div>
          </div>
          <div class="skeleton-col col-loc">
            <div class="skeleton-line w-32 animate-pulse"></div>
          </div>
          <div class="skeleton-col col-time">
            <div class="skeleton-line w-20 animate-pulse"></div>
          </div>
          <div class="skeleton-col col-status">
            <div class="skeleton-pill-sm animate-pulse"></div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [
    `
      .skeleton-table {
        display: flex;
        flex-direction: column;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        background: #ffffff;
        overflow: hidden;
      }

      .skeleton-row {
        display: grid;
        grid-template-columns: 88px 180px 1fr 160px 120px 90px;
        align-items: center;
        gap: 1rem;
        padding: 0.85rem 1rem;
        border-bottom: 1px solid #f1f5f9;

        &:last-child {
          border-bottom: none;
        }
      }

      .skeleton-col {
        display: flex;
        align-items: center;
        gap: 0.5rem;
      }

      .skeleton-text-group {
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }

      .skeleton-pill {
        width: 60px;
        height: 22px;
        border-radius: 4px;
        background: #f1f5f9;
      }

      .skeleton-pill-sm {
        width: 68px;
        height: 20px;
        border-radius: 9999px;
        background: #f1f5f9;
      }

      .skeleton-avatar {
        width: 28px;
        height: 28px;
        border-radius: 50%;
        background: #f1f5f9;
        flex-shrink: 0;
      }

      .skeleton-line {
        height: 12px;
        border-radius: 4px;
        background: #f1f5f9;
      }

      .skeleton-line-sm {
        height: 10px;
        border-radius: 3px;
        background: #f8fafc;
      }
    `,
  ],
})
export class AuditSkeletonComponent {
  count = input<number>(5);

  get rowsArray(): () => number[] {
    return () => Array.from({ length: this.count() });
  }
}
