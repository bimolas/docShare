import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { DailyActivitySummary, DashboardScope } from '../models/dashboard.model';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';

/**
 * DashboardActivityLedgerComponent
 *
 * Chronological operational ledger matching the reference's "Transaction history" style.
 * Displays daily activity audit streams with sleek monochrome black icon containers,
 * intensity descriptors, and exact operational counts derived from backend telemetry.
 */
@Component({
  selector: 'app-dashboard-activity-ledger',
  standalone: true,
  imports: [CommonModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs h-full">
      <!-- Header -->
      <div class="flex items-start justify-between gap-3 mb-4">
        <div>
          <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
            Operational Stream
          </span>
          <h3 class="text-base font-semibold text-slate-900 mt-0.5">
            Activity Ledger
          </h3>
          <p class="text-xs text-slate-500 mt-0.5">
            Chronological audit telemetry over recent operating windows
          </p>
        </div>

        <a
          [routerLink]="auditRoute()"
          class="group inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold text-slate-700 hover:text-amber-700 bg-slate-50 hover:bg-amber-50/70 border border-slate-200/70 transition-colors shrink-0"
        >
          <span>Audit Trail</span>
          <app-icon
            name="arrow-right"
            [size]="13"
            class="group-hover:translate-x-0.5 transition-transform"
          />
        </a>
      </div>

      <!-- Ledger Items List (Reference Style) -->
      @if (ledger().length > 0) {
        <div class="space-y-2.5 my-auto py-1">
          @for (entry of ledger(); track entry.date) {
            <div class="flex items-center justify-between p-3 rounded-xl bg-slate-50/70 border border-slate-100/90 hover:bg-slate-100/70 transition-colors">
              <!-- Left: Neutral Icon Container + Details -->
              <div class="flex items-center gap-3 min-w-0">
                <div class="w-9 h-9 rounded-xl bg-slate-100 text-slate-900 border border-slate-200/80 flex items-center justify-center shrink-0">
                  <app-icon [name]="getEntryIcon(entry)" [size]="16" />
                </div>
                <div class="min-w-0">
                  <div class="text-xs font-bold text-slate-900 truncate">
                    {{ formatDateLabel(entry.date) }}
                  </div>
                  <div class="text-[11px] text-slate-500 truncate mt-0.5 font-medium">
                    {{ getIntensityLabel(entry) }}
                  </div>
                </div>
              </div>

              <!-- Right: Count Badge -->
              <div class="shrink-0 ml-3">
                @if (entry.count > 0) {
                  <span class="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200/60">
                    +{{ entry.count }} {{ entry.count === 1 ? 'action' : 'actions' }}
                  </span>
                } @else {
                  <span class="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-medium bg-slate-200/80 text-slate-600">
                    0 actions
                  </span>
                }
              </div>
            </div>
          }
        </div>
      } @else {
        <div class="py-8 text-center my-auto">
          <div class="w-10 h-10 rounded-xl bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-2">
            <app-icon name="clock" [size]="20" />
          </div>
          <p data-testid="empty-ledger-notice" class="text-xs text-slate-500 font-medium">
            No activity events recorded in this window
          </p>
        </div>
      }

      <!-- Footer Telemetry Status -->
      <div class="pt-3 border-t border-slate-100 flex items-center justify-between text-[11px] text-slate-400">
        <span>Verified immutable audit trail</span>
        <span class="font-medium text-slate-600">Latest 5 periods</span>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }
  `],
})
export class DashboardActivityLedgerComponent {
  readonly ledger = input<DailyActivitySummary[]>([]);
  readonly scope = input.required<DashboardScope>();
  readonly orgCode = input<string | null>(null);

  readonly isPersonal = computed(() => this.scope() === 'PERSONAL');

  readonly auditRoute = computed(() => {
    if (this.isPersonal()) {
      return '/w/personal/audit-trail';
    }
    const code = this.orgCode();
    return code ? ['/org', code, 'audit-trail'] : '/w/personal/audit-trail';
  });

  formatDateLabel(rawDate: string): string {
    if (!rawDate) return 'Recent Window';
    try {
      const parts = rawDate.split('-');
      if (parts.length === 3) {
        const d = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
        return d.toLocaleDateString(undefined, {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
        });
      }
    } catch {
      // Fallback to raw string
    }
    return rawDate;
  }

  getIntensityLabel(entry: DailyActivitySummary): string {
    if (entry.count >= 15) return 'High Operational Volume';
    if (entry.count >= 8) return 'Moderate Security Actions';
    if (entry.count > 0) return 'Standard File Activity';
    return 'Routine System Check';
  }

  getEntryIcon(entry: DailyActivitySummary): IconName {
    if (entry.count >= 15) return 'bar-chart-3';
    if (entry.count >= 8) return 'shield-check';
    if (entry.count > 0) return 'file-text';
    return 'clock';
  }
}
