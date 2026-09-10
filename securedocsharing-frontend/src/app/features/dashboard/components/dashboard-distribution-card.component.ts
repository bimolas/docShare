import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EChartsOption } from 'echarts';
import { ContentDistributionItem, DashboardScope } from '../models/dashboard.model';
import { IconComponent } from '../../../shared/components/icon/icon.component';
import { ChartComponent } from '../../../shared/components/chart/chart.component';

/**
 * DashboardDistributionCardComponent
 *
 * Space allocation and content health card.
 * Features the segmented semi-circular half-gauge chart matching the reference's "Financial health" style,
 * with 4 curved blocks, white gap dividers, warm amber/orange gradient fill, and category breakdown.
 */
@Component({
  selector: 'app-dashboard-distribution-card',
  standalone: true,
  imports: [CommonModule, IconComponent, ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs h-full">
      <!-- Header (Reference Design Style) -->
      <div class="flex items-start justify-between gap-3 mb-2">
        <div>
          <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
            Space Allocation & Health
          </span>
          <h3 class="text-base font-semibold text-slate-900 mt-0.5">
            Content & Storage Health
          </h3>
          <p class="text-xs text-slate-500 mt-0.5">
            {{ isPersonal() ? 'Personal files, shared items, and quota allocation' : 'Organization workspaces, members, and quota allocation' }}
          </p>
        </div>

        @if (totalCount() > 0) {
          <span class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-700 border border-slate-200/60 shrink-0">
            {{ totalCount().toLocaleString() }} Total Entities
          </span>
        }
      </div>

      <!-- Primary Metric & Status Badge (Matching $15,780 in Reference) -->
      <div class="flex items-baseline justify-between gap-2 mt-2 mb-1">
        <div>
          <div class="text-2xl font-bold tracking-tight text-slate-900">
            {{ formattedValue() }}
          </div>
          <div class="text-xs text-slate-500 mt-0.5 flex items-center gap-1.5">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-500"></span>
            <span>Allocated of {{ formattedLimit() }} quota</span>
          </div>
        </div>

        <span
          class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-tight"
          [ngClass]="statusBadgeClasses()"
        >
          {{ statusBadgeText() }}
        </span>
      </div>

      <!-- Segmented Semi-Circular Half Chart (Reference Style with Amber/Orange Gradient) -->
      @if (options()) {
        <div class="relative w-full my-auto py-1">
          <app-chart
            [options]="options()!"
            height="180px"
            class="w-full"
          />
        </div>
      } @else {
        <div class="py-8 text-center my-auto">
          <div class="w-10 h-10 rounded-xl bg-slate-100 text-slate-400 flex items-center justify-center mx-auto mb-2">
            <app-icon name="bar-chart-3" [size]="20" />
          </div>
          <p data-testid="empty-notice" class="text-xs text-slate-500 font-medium">
            No entities registered in this space yet
          </p>
        </div>
      }

      <!-- Context Note (Matching Reference Caption) -->
      <p class="text-[11px] text-slate-400 text-center mb-3">
        Based on verified storage allocation and active entity telemetry
      </p>

      <!-- Category Breakdown Grid (Preserved below the half chart) -->
      @if (items().length > 0) {
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-2 pt-3 border-t border-slate-100">
          @for (item of items(); track item.label) {
            <div class="flex items-start gap-2.5 p-2.5 rounded-xl bg-slate-50 border border-slate-100/90">
              <span
                class="w-2.5 h-2.5 rounded-full shrink-0 mt-1 shadow-2xs"
                [style.background-color]="item.color"
              ></span>
              <div class="min-w-0">
                <span class="text-[11px] text-slate-500 truncate block font-medium">
                  {{ item.label }}
                </span>
                <div class="flex items-baseline gap-1.5 mt-0.5">
                  <span class="text-xs font-bold text-slate-900">
                    {{ item.count.toLocaleString() }}
                  </span>
                  <span class="text-[10px] text-slate-400 font-medium">
                    {{ item.percentage }}%
                  </span>
                </div>
              </div>
            </div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }
  `],
})
export class DashboardDistributionCardComponent {
  readonly items = input<ContentDistributionItem[]>([]);
  readonly scope = input.required<DashboardScope>();
  readonly options = input<EChartsOption | null>(null);
  readonly percentage = input<number>(0);
  readonly formattedValue = input<string>('0 B');
  readonly formattedLimit = input<string>('Quota');

  readonly isPersonal = computed(() => this.scope() === 'PERSONAL');

  readonly totalCount = computed(() => {
    return this.items().reduce((acc, curr) => acc + (curr.count || 0), 0);
  });

  readonly statusBadgeText = computed(() => {
    const pct = this.percentage();
    if (pct >= 90) return 'Critical (>90%)';
    if (pct >= 75) return 'High (>75%)';
    return 'In Good Standing';
  });

  readonly statusBadgeClasses = computed(() => {
    const pct = this.percentage();
    if (pct >= 90) return 'bg-rose-50 text-rose-700 border border-rose-200/60';
    if (pct >= 75) return 'bg-amber-50 text-amber-700 border border-amber-200/60';
    return 'bg-emerald-50 text-emerald-700 border border-emerald-200/60';
  });
}
