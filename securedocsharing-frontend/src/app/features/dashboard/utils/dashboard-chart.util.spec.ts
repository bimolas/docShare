import {
  buildStorageGaugeOptions,
  buildActivityTrendOptions,
  DASHBOARD_COLORS,
} from './dashboard-chart.util';
import { DailyActivitySummary } from '../models/dashboard.model';

describe('dashboard-chart.util', () => {
  describe('buildStorageGaugeOptions', () => {
    it('should build gauge options with semi-circular angles and no pointer', () => {
      const options = buildStorageGaugeOptions(42.5, '4.25 GB', true);
      const series = (options.series as any[])[0];

      expect(series.type).toBe('gauge');
      expect(series.startAngle).toBe(210);
      expect(series.endAngle).toBe(-30);
      expect(series.pointer.show).toBeFalse();
      expect(series.progress.show).toBeTrue();
      expect(series.data[0].value).toBe(42.5);
      expect(series.progress.itemStyle.color).toBe(DASHBOARD_COLORS.primaryAmber);
    });

    it('should use warning rose color when percentage exceeds 90%', () => {
      const options = buildStorageGaugeOptions(94.2, '9.42 GB', true);
      const series = (options.series as any[])[0];

      expect(series.progress.itemStyle.color).toBe(DASHBOARD_COLORS.warningRose);
    });

    it('should clamp negative values to 0 and values over 100 to 100', () => {
      const clampedLow = buildStorageGaugeOptions(-15, '0 B', true);
      expect((clampedLow.series as any[])[0].data[0].value).toBe(0);

      const clampedHigh = buildStorageGaugeOptions(150, '15 GB', true);
      expect((clampedHigh.series as any[])[0].data[0].value).toBe(100);
    });

    it('should format detail string with percentage and used label', () => {
      const options = buildStorageGaugeOptions(25.0, '2.5 GB', true);
      const series = (options.series as any[])[0];
      const text = series.detail.formatter();

      expect(text).toContain('25.0%');
      expect(text).toContain('2.5 GB used');
    });
  });

  describe('buildActivityTrendOptions', () => {
    it('should map dates to MM/DD category labels and counts to series data', () => {
      const mockTrend: DailyActivitySummary[] = [
        { date: '2026-09-07', count: 3, intensity: 1 },
        { date: '2026-09-08', count: 7, intensity: 2 },
        { date: '2026-09-09', count: 12, intensity: 3 },
      ];

      const options = buildActivityTrendOptions(mockTrend, true);
      const xAxis = options.xAxis as any;
      const series = (options.series as any[])[0];

      expect(xAxis.type).toBe('category');
      expect(xAxis.data).toEqual(['09/07', '09/08', '09/09']);
      expect(series.type).toBe('line');
      expect(series.data).toEqual([3, 7, 12]);
      expect(series.lineStyle.color).toBe(DASHBOARD_COLORS.primaryAmber);
    });

    it('should handle empty trend gracefully without errors', () => {
      const options = buildActivityTrendOptions([], true);
      const xAxis = options.xAxis as any;
      const series = (options.series as any[])[0];

      expect(xAxis.data).toEqual([]);
      expect(series.data).toEqual([]);
    });

    it('should generate informative tooltip markup', () => {
      const options = buildActivityTrendOptions([
        { date: '2026-09-09', count: 15, intensity: 3 },
      ], true);

      const tooltip = options.tooltip as any;
      const html = tooltip.formatter([{ name: '09/09', value: 15 }]);

      expect(html).toContain('09/09');
      expect(html).toContain('15');
    });
  });
});
