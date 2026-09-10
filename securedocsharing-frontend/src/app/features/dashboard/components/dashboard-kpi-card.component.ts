import { Component, ChangeDetectionStrategy, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { IconComponent, IconName } from '../../../shared/components/icon/icon.component';

export type KpiBadgeVariant = 'default' | 'amber' | 'emerald' | 'rose' | 'slate';

/**
 * DashboardKpiCardComponent
 *
 * Polished enterprise metric card for SecureDocSharing Dashboard.
 * Displays high-level KPIs with strong typography, iconography, and optional navigation.
 */
@Component({
  selector: 'app-dashboard-kpi-card',
  standalone: true,
  imports: [CommonModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (routerLink()) {
      <a
        [routerLink]="routerLink()"
        class="group relative flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs hover:border-amber-300/80 hover:shadow-sm transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500 h-full"
        [attr.aria-label]="title() + ': ' + value()"
      >
        <div class="flex items-start justify-between gap-3 mb-3">
          <div class="flex items-center gap-3 min-w-0">
            <div
              class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 transition-colors duration-150"
              [ngClass]="iconContainerClasses()"
            >
              <app-icon [name]="icon()" [size]="20" />
            </div>
            <div class="min-w-0">
              <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block truncate">
                {{ title() }}
              </span>
            </div>
          </div>

          <div class="flex items-center gap-1.5 shrink-0">
            @if (badgeText()) {
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold tracking-tight"
                [ngClass]="badgeClasses()"
              >
                {{ badgeText() }}
              </span>
            }
            <app-icon
              name="chevron-right"
              [size]="16"
              class="text-slate-300 group-hover:text-amber-500 group-hover:translate-x-0.5 transition-all duration-150"
            />
          </div>
        </div>

        <div>
          <div class="text-2xl lg:text-3xl font-bold tracking-tight text-slate-900 group-hover:text-amber-600 transition-colors">
            {{ formattedValue() }}
          </div>
          @if (subtext()) {
            <p class="text-xs text-slate-500 mt-1 truncate">
              {{ subtext() }}
            </p>
          }
        </div>
      </a>
    } @else {
      <div
        class="relative flex flex-col justify-between p-5 rounded-2xl bg-white border border-slate-200/80 shadow-xs h-full"
      >
        <div class="flex items-start justify-between gap-3 mb-3">
          <div class="flex items-center gap-3 min-w-0">
            <div
              class="w-10 h-10 rounded-xl flex items-center justify-center shrink-0"
              [ngClass]="iconContainerClasses()"
            >
              <app-icon [name]="icon()" [size]="20" />
            </div>
            <div class="min-w-0">
              <span class="text-[11px] font-bold text-slate-400 uppercase tracking-wider block truncate">
                {{ title() }}
              </span>
            </div>
          </div>

          @if (badgeText()) {
            <span
              class="inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-semibold tracking-tight shrink-0"
              [ngClass]="badgeClasses()"
            >
              {{ badgeText() }}
            </span>
          }
        </div>

        <div>
          <div class="text-2xl lg:text-3xl font-bold tracking-tight text-slate-900">
            {{ formattedValue() }}
          </div>
          @if (subtext()) {
            <p class="text-xs text-slate-500 mt-1 truncate">
              {{ subtext() }}
            </p>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    :host {
      display: block;
      height: 100%;
    }
  `],
})
export class DashboardKpiCardComponent {
  readonly title = input.required<string>();
  readonly value = input.required<number | string>();
  readonly icon = input.required<IconName>();

  readonly badgeText = input<string | null>(null);
  readonly badgeVariant = input<KpiBadgeVariant>('default');
  readonly subtext = input<string | null>(null);
  readonly routerLink = input<string | any[] | null>(null);
  readonly iconTone = input<'amber' | 'slate' | 'emerald' | 'rose'>('amber');

  readonly formattedValue = computed(() => {
    const val = this.value();
    if (typeof val === 'number') {
      return val.toLocaleString();
    }
    return val;
  });

  readonly iconContainerClasses = computed(() => {
    // Subtle neutral background with crisp dark/black icons across all metric cards
    return 'bg-slate-100 text-slate-900 border border-slate-200/70 group-hover:bg-slate-200/60';
  });

  readonly badgeClasses = computed(() => {
    switch (this.badgeVariant()) {
      case 'amber':
        return 'bg-amber-50 text-amber-700 border border-amber-200/60';
      case 'emerald':
        return 'bg-emerald-50 text-emerald-700 border border-emerald-200/60';
      case 'rose':
        return 'bg-rose-50 text-rose-700 border border-rose-200/60';
      case 'slate':
        return 'bg-slate-100 text-slate-600 border border-slate-200/60';
      case 'default':
      default:
        return 'bg-slate-50 text-slate-500 border border-slate-200/60';
    }
  });
}
