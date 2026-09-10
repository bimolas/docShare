import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EChartsOption } from 'echarts';
import { StorageGaugeViewModel } from '../models/dashboard.model';
import { ChartComponent } from '../../../shared/components/chart/chart.component';

/**
 * DashboardStorageCardComponent
 *
 * Visual radial/semi-circular storage gauge card for SecureDocSharing Dashboard.
 * Displays consumed vs total capacity, utilization percentage, and remaining quota.
 */
@Component({
  selector: 'app-dashboard-storage-card',
  standalone: true,
  imports: [CommonModule, ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs h-full">
      <!-- Header -->
      <div class="flex items-start justify-between gap-3 mb-2">
        <div>
          <div class="flex items-center gap-2">
            <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
              Storage Utilization
            </span>
          </div>
          <h3 class="text-base font-semibold text-slate-900 mt-0.5">
            Capacity Overview
          </h3>
        </div>

        @if (viewModel()) {
          <span
            class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold tracking-tight shrink-0"
            [ngClass]="statusBadgeClasses()"
          >
            {{ statusBadgeText() }}
          </span>
        }
      </div>

      <!-- Radial Gauge Visualization -->
      <div class="relative flex items-center justify-center my-auto py-1">
        @if (options()) {
          <app-chart
            [options]="options()"
            height="195px"
            class="w-full"
          />
        }
      </div>

      <!-- Capacity Breakdown -->
      @if (viewModel(); as vm) {
        <div class="pt-3 border-t border-slate-100">
          <!-- Linear bar -->
          <div class="w-full h-1.5 bg-slate-100 rounded-full overflow-hidden mb-3">
            <div
              class="h-full rounded-full transition-all duration-300"
              [style.width.%]="clampedPercentage()"
              [style.background-color]="vm.accentColor"
            ></div>
          </div>

          <!-- 3-Column Metrics Grid -->
          <div class="grid grid-cols-3 gap-2 text-center">
            <div class="p-2 rounded-xl bg-slate-50 border border-slate-100/80">
              <span class="text-[10px] uppercase font-semibold text-slate-400 block tracking-wider">Used</span>
              <span class="text-xs font-bold text-slate-800 block truncate mt-0.5">{{ vm.formattedUsed }}</span>
            </div>
            <div class="p-2 rounded-xl bg-slate-50 border border-slate-100/80">
              <span class="text-[10px] uppercase font-semibold text-slate-400 block tracking-wider">Free</span>
              <span class="text-xs font-bold text-emerald-700 block truncate mt-0.5">{{ formattedFree() }}</span>
            </div>
            <div class="p-2 rounded-xl bg-slate-50 border border-slate-100/80">
              <span class="text-[10px] uppercase font-semibold text-slate-400 block tracking-wider">Limit</span>
              <span class="text-xs font-bold text-slate-800 block truncate mt-0.5">{{ vm.formattedLimit }}</span>
            </div>
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
export class DashboardStorageCardComponent {
  readonly viewModel = input<StorageGaugeViewModel | null>(null);
  readonly options = input.required<EChartsOption>();

  readonly clampedPercentage = computed(() => {
    const vm = this.viewModel();
    if (!vm) return 0;
    return Math.min(Math.max(vm.percentage, 0), 100);
  });

  readonly formattedFree = computed(() => {
    const vm = this.viewModel();
    if (!vm) return '0 B';
    const freeBytes = Math.max(0, vm.limitBytes - vm.usedBytes);
    if (freeBytes >= 1073741824) {
      return `${(freeBytes / 1073741824).toFixed(1)} GB`;
    }
    if (freeBytes >= 1048576) {
      return `${(freeBytes / 1048576).toFixed(0)} MB`;
    }
    if (freeBytes >= 1024) {
      return `${(freeBytes / 1024).toFixed(0)} KB`;
    }
    return `${freeBytes} B`;
  });

  readonly statusBadgeText = computed(() => {
    const vm = this.viewModel();
    if (!vm) return 'Normal';
    if (vm.percentage >= 90) return 'Critical (>90%)';
    if (vm.percentage >= 75) return 'High (>75%)';
    return `${vm.percentage.toFixed(1)}% Used`;
  });

  readonly statusBadgeClasses = computed(() => {
    const vm = this.viewModel();
    if (!vm) return 'bg-slate-100 text-slate-600 border border-slate-200/60';
    if (vm.percentage >= 90) return 'bg-rose-50 text-rose-700 border border-rose-200/60';
    if (vm.percentage >= 75) return 'bg-amber-50 text-amber-700 border border-amber-200/60';
    return 'bg-emerald-50 text-emerald-700 border border-emerald-200/60';
  });
}
