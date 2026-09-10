import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import {
  LucideHistory,
  LucideRefreshCw,
  LucideShield,
  LucideUsers,
  LucideHardDrive,
  LucideShieldCheck,
  LucideClock,
} from '@lucide/angular';
import { TeamActivityItem } from './team-overview-tab.component';

@Component({
  selector: 'sd-team-activity-tab',
  standalone: true,
  imports: [
    CommonModule,
    DatePipe,
    LucideHistory,
    LucideRefreshCw,
    LucideShield,
    LucideUsers,
    LucideHardDrive,
    LucideShieldCheck,
    LucideClock,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="activity-tab-container">
      <!-- Top Bar -->
      <div class="section-top-bar">
        <div class="section-title-group">
          <svg lucideHistory class="top-icon"></svg>
          <h4>Activity Timeline</h4>
          <span class="count-pill">{{ activities().length }}</span>
        </div>

        <button
          type="button"
          class="btn-refresh"
          (click)="refresh.emit()"
          title="Refresh activity timeline"
        >
          <svg lucideRefreshCw class="refresh-icon" [class.spinning]="isLoading()"></svg>
          <span>Refresh</span>
        </button>
      </div>

      <!-- Timeline Container -->
      <div class="timeline-surface">
        @if (activities().length === 0) {
          <div class="empty-timeline-card">
            <div class="empty-icon-wrap">
              <svg lucideClock class="empty-icon"></svg>
            </div>
            <h5>No Team Events Yet</h5>
            <p>
              Team creation, manager designation, workspace access allocation, and member delegations will appear here in chronological order.
            </p>
          </div>
        } @else {
          <div class="timeline-list">
            @for (event of activities(); track event.id; let last = $last) {
              <div class="timeline-item" [class.is-last]="last">
                <!-- Timeline track indicator -->
                <div class="timeline-track-col">
                  <div
                    class="timeline-node-icon"
                    [class.is-team]="event.iconType === 'TEAM'"
                    [class.is-member]="event.iconType === 'MEMBER'"
                    [class.is-workspace]="event.iconType === 'WORKSPACE'"
                    [class.is-access]="event.iconType === 'ACCESS'"
                  >
                    @if (event.iconType === 'TEAM') {
                      <svg lucideShield class="node-icon"></svg>
                    } @else if (event.iconType === 'MEMBER') {
                      <svg lucideUsers class="node-icon"></svg>
                    } @else if (event.iconType === 'WORKSPACE') {
                      <svg lucideHardDrive class="node-icon"></svg>
                    } @else {
                      <svg lucideShieldCheck class="node-icon"></svg>
                    }
                  </div>
                  @if (!last) {
                    <div class="timeline-line"></div>
                  }
                </div>

                <!-- Event Details Card -->
                <div class="timeline-event-card">
                  <div class="event-header-row">
                    <span class="event-desc">{{ event.description }}</span>
                    @if (event.badgeText) {
                      <span class="event-badge">{{ event.badgeText }}</span>
                    }
                  </div>

                  <div class="event-meta-row font-tabular">
                    <span class="event-time">
                      {{ (event.timestamp | date: 'MMMM d, yyyy · h:mm a') || '—' }}
                    </span>
                    @if (event.actorName) {
                      <span class="meta-sep">·</span>
                      <span class="event-actor">
                        By {{ event.actorName }}
                        @if (event.actorEmail) {
                          <span class="actor-email">({{ event.actorEmail }})</span>
                        }
                      </span>
                    }
                  </div>
                </div>
              </div>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .activity-tab-container {
        display: flex;
        flex-direction: column;
        gap: 1.25rem;
      }

      /* ── Top Bar ───────────────────────────────── */
      .section-top-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
      }

      .section-title-group {
        display: flex;
        align-items: center;
        gap: 0.5rem;

        h4 {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #0f172a;
        }
      }

      .top-icon {
        width: 18px;
        height: 18px;
        color: #f97316;
      }

      .count-pill {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: 0.6875rem;
        font-weight: 700;
        background: #f1f5f9;
        color: #475569;
        padding: 0.1rem 0.45rem;
        border-radius: 9999px;
      }

      .btn-refresh {
        display: inline-flex;
        align-items: center;
        gap: 0.35rem;
        padding: 0.35rem 0.65rem;
        background: #ffffff;
        color: #475569;
        border: 1px solid #e2e8f0;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 500;
        cursor: pointer;

        &:hover {
          background: #f8fafc;
          color: #0f172a;
        }
      }

      .refresh-icon {
        width: 13px;
        height: 13px;

        &.spinning {
          animation: spin 1s linear infinite;
        }
      }

      @keyframes spin {
        from { transform: rotate(0deg); }
        to { transform: rotate(360deg); }
      }

      /* ── Timeline Surface ──────────────────────── */
      .timeline-surface {
        background: #ffffff;
        border: 1px solid #e2e8f0;
        border-radius: 10px;
        padding: 1.5rem;
      }

      .timeline-list {
        display: flex;
        flex-direction: column;
      }

      .timeline-item {
        display: flex;
        align-items: stretch;
        gap: 1.25rem;
        min-height: 72px;

        &.is-last {
          min-height: auto;
        }
      }

      .timeline-track-col {
        display: flex;
        flex-direction: column;
        align-items: center;
        width: 32px;
        flex-shrink: 0;
      }

      .timeline-node-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 30px;
        height: 30px;
        border-radius: 50%;
        background: #f8fafc;
        border: 2px solid #e2e8f0;
        color: #64748b;
        flex-shrink: 0;
        z-index: 1;

        &.is-team {
          background: #fff7ed;
          border-color: #fed7aa;
          color: #ea580c;
        }

        &.is-member {
          background: #eff6ff;
          border-color: #bfdbfe;
          color: #2563eb;
        }

        &.is-workspace {
          background: #f0fdf4;
          border-color: #bbf7d0;
          color: #16a34a;
        }

        &.is-access {
          background: #faf5ff;
          border-color: #e9d5ff;
          color: #9333ea;
        }
      }

      .node-icon {
        width: 14px;
        height: 14px;
      }

      .timeline-line {
        flex: 1;
        width: 2px;
        background: #f1f5f9;
        margin: 4px 0;
      }

      .timeline-event-card {
        flex: 1;
        background: #f8fafc;
        border: 1px solid #e2e8f0;
        border-radius: 8px;
        padding: 0.75rem 1rem;
        margin-bottom: 1rem;
        display: flex;
        flex-direction: column;
        gap: 0.35rem;
      }

      .event-header-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
      }

      .event-desc {
        font-size: 0.8125rem;
        font-weight: 500;
        color: #0f172a;
        line-height: 1.35;
      }

      .event-badge {
        font-size: 0.65rem;
        font-weight: 700;
        padding: 0.1rem 0.4rem;
        border-radius: 4px;
        background: #ffffff;
        border: 1px solid #cbd5e1;
        color: #475569;
        flex-shrink: 0;
      }

      .event-meta-row {
        display: flex;
        align-items: center;
        gap: 0.4rem;
        font-size: 0.75rem;
        color: #64748b;
      }

      .meta-sep {
        color: #cbd5e1;
      }

      .actor-email {
        color: #94a3b8;
      }

      .font-tabular {
        font-variant-numeric: tabular-nums;
      }

      /* ── Empty State ───────────────────────────── */
      .empty-timeline-card {
        padding: 2.5rem 1.5rem;
        display: flex;
        flex-direction: column;
        align-items: center;
        text-align: center;
        gap: 0.75rem;

        h5 {
          margin: 0;
          font-size: 0.9375rem;
          font-weight: 600;
          color: #0f172a;
        }

        p {
          max-width: 440px;
          margin: 0;
          font-size: 0.8125rem;
          color: #64748b;
          line-height: 1.45;
        }
      }

      .empty-icon-wrap {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 46px;
        height: 46px;
        border-radius: 50%;
        background: #f1f5f9;
        color: #64748b;
      }

      .empty-icon {
        width: 22px;
        height: 22px;
      }
    `,
  ],
})
export class TeamActivityTabComponent {
  activities = input<TeamActivityItem[]>([]);
  isLoading = input<boolean>(false);

  refresh = output<void>();
}
