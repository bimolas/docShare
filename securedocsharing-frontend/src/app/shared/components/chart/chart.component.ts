import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  ViewChild,
  ElementRef,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgxEchartsDirective } from 'ngx-echarts';
import { EChartsOption } from 'echarts';

/**
 * ChartComponent
 * Reusable enterprise ECharts wrapper for SecureDocSharing data visualizations.
 * Features:
 * - Angular 19 Signal inputs & outputs.
 * - OnPush change detection.
 * - Automatic responsive resizing.
 * - Dynamic loading overlay state.
 */
@Component({
  selector: 'app-chart',
  standalone: true,
  imports: [CommonModule, NgxEchartsDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="relative w-full overflow-hidden" [style.height]="height()">
      @if (loading()) {
        <div class="absolute inset-0 bg-white/70 backdrop-blur-xs flex items-center justify-center z-10 rounded-md">
          <div class="flex items-center gap-2 text-sm text-gray-500 font-medium">
            <svg class="animate-spin h-5 w-5 text-accent" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span>Loading analytics...</span>
          </div>
        </div>
      }
      @if (theme()) {
        <div
          echarts
          [options]="options()"
          [theme]="theme()!"
          [autoResize]="true"
          (chartInit)="onChartInit($event)"
          class="w-full h-full"
        ></div>
      } @else {
        <div
          echarts
          [options]="options()"
          [autoResize]="true"
          (chartInit)="onChartInit($event)"
          class="w-full h-full"
        ></div>
      }
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
    }
  `],
})
export class ChartComponent {
  /** Chart configuration options from ECharts */
  readonly options = input.required<EChartsOption>();

  /** Dynamic container height (CSS string e.g. '350px') */
  readonly height = input<string>('300px');

  /** Loading overlay state */
  readonly loading = input<boolean>(false);

  /** Optional chart theme override */
  readonly theme = input<string | object | null>(null);

  /** Emitted when chart instance is initialized */
  readonly chartInit = output<any>();

  onChartInit(ecInstance: any): void {
    this.chartInit.emit(ecInstance);
  }
}
