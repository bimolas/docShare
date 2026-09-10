import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EChartsOption } from 'echarts';
import { ActivityTrendViewModel } from '../models/dashboard.model';
import { ChartComponent } from '../../../shared/components/chart/chart.component';

/**
 * DashboardActivityCardComponent
 *
 * Polished ECharts visualization for the 14-day activity trend & velocity.
 * Displays volume, peak operations, and daily averages for the active context.
 */
@Component({
  selector: 'app-dashboard-activity-card',
  standalone: true,
  imports: [CommonModule, ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs h-full">
      <!-- Header -->
      <div class="flex flex-wrap items-start justify-between gap-3 mb-3">
        <div>
          <div class="flex items-center gap-2">
            <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
              Operational Telemetry
            </span>
          </div>
          <h3 class="text-base font-semibold text-slate-900 mt-0.5">
            14-Day Activity Velocity
          </h3>
          <p class="text-xs text-slate-500 mt-0.5">
            Trend of document actions, modifications, and access reviews
          </p>
        </div>

        <!-- Metric Badges -->
        @if (viewModel(); as vm) {
          <div class="flex items-center gap-2">
            <span class="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-50 text-amber-800 border border-amber-200/60">
              <span class="w-1.5 h-1.5 rounded-full bg-amber-500"></span>
              {{ vm.totalActions }} Total Actions
            </span>
            <span class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-600 border border-slate-200/60">
              Peak: {{ vm.peakCount }}/day
            </span>
          </div>
        }
      </div>

      <!-- Area Line Chart (Old Chart) -->
      <div class="relative w-full my-auto py-1">
        @if (options()) {
          <app-chart
            [options]="options()"
            height="215px"
            class="w-full"
          />
        }
      </div>

      <!-- Footer Insights -->
      @if (viewModel(); as vm) {
        <div class="pt-3 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
          <div class="flex items-center gap-4">
            <div>
              <span class="text-slate-400">Average: </span>
              <span class="font-semibold text-slate-700">{{ dailyAverage() }} actions/day</span>
            </div>
            <div class="hidden sm:block text-slate-300">•</div>
            <div class="hidden sm:block">
              <span class="text-slate-400">Peak Volume: </span>
              <span class="font-semibold text-slate-700">{{ vm.peakCount }} in 24h</span>
            </div>
          </div>
          <div class="text-[11px] text-slate-400">
            Past 14 Days Window
          </div>
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
export class DashboardActivityCardComponent {
  readonly viewModel = input<ActivityTrendViewModel | null>(null);
  readonly options = input.required<EChartsOption>();

  readonly dailyAverage = computed(() => {
    const vm = this.viewModel();
    if (!vm || vm.dates.length === 0) return '0.0';
    return (vm.totalActions / vm.dates.length).toFixed(1);
  });
}

