import { Component, ChangeDetectionStrategy, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuditViewType } from '../../../core/models/unified-audit.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';

@Component({
  selector: 'app-audit-view-tabs',
  standalone: true,
  imports: [CommonModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="view-tabs-container" role="tablist" aria-label="Audit View Categories">
      <button
        type="button"
        role="tab"
        class="tab-btn"
        [class.active]="activeView() === 'ALL'"
        [attr.aria-selected]="activeView() === 'ALL'"
        (click)="viewChange.emit('ALL')"
      >
        <app-icon name="layers" [size]="15" />
        <span>All Activity</span>
      </button>

      <button
        type="button"
        role="tab"
        class="tab-btn"
        [class.active]="activeView() === 'GOVERNANCE'"
        [attr.aria-selected]="activeView() === 'GOVERNANCE'"
        (click)="viewChange.emit('GOVERNANCE')"
      >
        <app-icon name="shield" [size]="15" />
        <span>Security &amp; Governance</span>
      </button>

      <button
        type="button"
        role="tab"
        class="tab-btn"
        [class.active]="activeView() === 'CONTENT'"
        [attr.aria-selected]="activeView() === 'CONTENT'"
        (click)="viewChange.emit('CONTENT')"
      >
        <app-icon name="folder" [size]="15" />
        <span>Content &amp; Access</span>
      </button>
    </div>
  `,
  styles: [
    `
      .view-tabs-container {
        display: inline-flex;
        align-items: center;
        gap: 0.25rem;
        background: #f1f5f9;
        padding: 0.25rem;
        border-radius: 8px;
        border: 1px solid #e2e8f0;
      }

      .tab-btn {
        display: inline-flex;
        align-items: center;
        gap: 0.4rem;
        padding: 0.35rem 0.75rem;
        border: none;
        background: transparent;
        color: #475569;
        font-size: 0.8125rem;
        font-weight: 500;
        border-radius: 6px;
        cursor: pointer;
        transition: all 0.15s ease;

        &:hover {
          color: #0f172a;
        }

        &.active {
          background: #ffffff;
          color: #0f172a;
          box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
          font-weight: 600;
        }
      }
    `,
  ],
})
export class AuditViewTabsComponent {
  activeView = input<AuditViewType>('ALL');
  viewChange = output<AuditViewType>();
}
