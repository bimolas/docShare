import { EChartsOption } from 'echarts';
import { DailyActivitySummary } from '../models/dashboard.model';

/**
 * Enterprise Chart Utilities for SecureDocSharing Dashboard.
 *
 * Visual Rules:
 * - Light enterprise visual system by default.
 * - Warm amber/orange (#f97316) primary accent.
 * - Crisp slate neutrals, clean typography, subtle tracks.
 * - Keeps chart math & ECharts option creation completely out of presentation components.
 */

export const DASHBOARD_COLORS = {
  primaryAmber: '#f97316',
  amberGlow: 'rgba(249, 115, 22, 0.25)',
  amberLight: '#fed7aa',
  warningRose: '#f43f5e',
  successEmerald: '#10b981',
  lightTrack: '#f1f5f9',
  darkTrack: '#1e293b',
  textSlatePrimary: '#0f172a',
  textSlateSecondary: '#64748b',
  borderSlate: '#e2e8f0',
} as const;

/**
 * Builds ECharts options for the semi-circular/radial storage utilization gauge.
 * Uses a modern ring progress indicator without needle pointers.
 *
 * @param percentage Storage percentage consumed (0-100)
 * @param formattedUsed Human-readable used storage string (e.g. "2.4 GB")
 * @param isLightMode Theme indicator, defaults to true
 */
export function buildStorageGaugeOptions(
  percentage: number,
  formattedUsed: string,
  isLightMode = true
): EChartsOption {
  const safePercentage = Math.min(Math.max(Number(percentage) || 0, 0), 100);
  const isHighUsage = safePercentage >= 90;
  const progressColor = isHighUsage ? DASHBOARD_COLORS.warningRose : DASHBOARD_COLORS.primaryAmber;
  const trackColor = isLightMode ? DASHBOARD_COLORS.lightTrack : DASHBOARD_COLORS.darkTrack;
  const textColor = isLightMode ? DASHBOARD_COLORS.textSlatePrimary : '#f8fafc';
  const subTextColor = isLightMode ? DASHBOARD_COLORS.textSlateSecondary : '#94a3b8';

  return {
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max: 100,
        radius: '95%',
        center: ['50%', '58%'],
        pointer: { show: false },
        progress: {
          show: true,
          overlap: false,
          roundCap: true,
          clip: false,
          itemStyle: {
            color: progressColor,
          },
        },
        axisLine: {
          lineStyle: {
            width: 14,
            color: [[1, trackColor]],
          },
        },
        splitLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '10%'],
          formatter: () => {
            return `{value|${safePercentage.toFixed(1)}%}\n{sub|${formattedUsed} used}`;
          },
          rich: {
            value: {
              fontSize: 26,
              fontWeight: 700,
              color: textColor,
              lineHeight: 32,
              fontFamily: 'Inter, system-ui, sans-serif',
            },
            sub: {
              fontSize: 12,
              fontWeight: 500,
              color: subTextColor,
              padding: [4, 0, 0, 0],
              fontFamily: 'Inter, system-ui, sans-serif',
            },
          },
        },
        data: [{ value: safePercentage }],
      },
    ],
  };
}

/**
 * Builds ECharts options for the 14-day activity velocity/trend area chart.
 *
 * @param trend Array of daily activity points
 * @param isLightMode Theme indicator, defaults to true
 */
export function buildActivityTrendOptions(
  trend: DailyActivitySummary[] = [],
  isLightMode = true
): EChartsOption {
  const dates: string[] = [];
  const counts: number[] = [];

  for (const item of trend) {
    // Format date as "MM/DD" (e.g. "09/08")
    const shortDate = item.date ? item.date.slice(5).replace('-', '/') : '';
    dates.push(shortDate);
    counts.push(item.count || 0);
  }

  const textColor = isLightMode ? DASHBOARD_COLORS.textSlateSecondary : '#94a3b8';
  const splitLineColor = isLightMode ? '#f1f5f9' : '#1e293b';

  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: isLightMode ? '#ffffff' : '#0f172a',
      borderColor: isLightMode ? DASHBOARD_COLORS.borderSlate : '#334155',
      borderWidth: 1,
      textStyle: {
        color: isLightMode ? DASHBOARD_COLORS.textSlatePrimary : '#f8fafc',
        fontSize: 12,
      },
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return '';
        const item = params[0];
        return `
          <div style="font-weight: 600; margin-bottom: 2px;">${item.name}</div>
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #f97316;"></span>
            <span>Actions: <strong>${item.value}</strong></span>
          </div>
        `;
      },
    },
    grid: {
      top: 14,
      right: 12,
      bottom: 24,
      left: 32,
      containLabel: false,
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: textColor,
        fontSize: 11,
        interval: Math.max(Math.floor(dates.length / 5), 1),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: {
        lineStyle: {
          color: splitLineColor,
          type: 'dashed',
        },
      },
      axisLabel: {
        color: textColor,
        fontSize: 11,
      },
    },
    series: [
      {
        type: 'line',
        data: counts,
        smooth: 0.35,
        symbol: 'circle',
        symbolSize: 4,
        showSymbol: false,
        lineStyle: {
          color: DASHBOARD_COLORS.primaryAmber,
          width: 2.5,
        },
        itemStyle: {
          color: DASHBOARD_COLORS.primaryAmber,
          borderColor: '#ffffff',
          borderWidth: 1.5,
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(249, 115, 22, 0.28)' },
              { offset: 1, color: 'rgba(249, 115, 22, 0.0)' },
            ],
          },
        },
      },
    ],
  };
}

/**
 * Builds ECharts options for the rounded-pill column bar chart, matching the reference's balance overview style.
 * Displays daily activity counts with rounded pill silhouettes and background column tracks.
 *
 * @param trend Array of daily activity points
 * @param isLightMode Theme indicator, defaults to true
 */
export function buildActivityBarChartOptions(
  trend: DailyActivitySummary[] = [],
  isLightMode = true
): EChartsOption {
  const dates: string[] = [];
  const counts: number[] = [];

  for (const item of trend) {
    const shortDate = item.date ? item.date.slice(5).replace('-', '/') : '';
    dates.push(shortDate);
    counts.push(item.count || 0);
  }

  const textColor = isLightMode ? DASHBOARD_COLORS.textSlateSecondary : '#94a3b8';
  const splitLineColor = isLightMode ? '#f1f5f9' : '#1e293b';
  const trackColor = isLightMode ? '#f8fafc' : '#1e293b';

  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'none' },
      backgroundColor: isLightMode ? '#ffffff' : '#0f172a',
      borderColor: isLightMode ? DASHBOARD_COLORS.borderSlate : '#334155',
      borderWidth: 1,
      textStyle: {
        color: isLightMode ? DASHBOARD_COLORS.textSlatePrimary : '#f8fafc',
        fontSize: 12,
      },
      formatter: (params: any) => {
        if (!Array.isArray(params) || params.length === 0) return '';
        const item = params[0];
        return `
          <div style="font-weight: 600; margin-bottom: 2px;">${item.name}</div>
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="display: inline-block; width: 8px; height: 8px; border-radius: 2px; background: #f97316;"></span>
            <span>Actions: <strong>${item.value}</strong></span>
          </div>
        `;
      },
    },
    grid: {
      top: 16,
      right: 12,
      bottom: 24,
      left: 28,
      containLabel: false,
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: {
        color: textColor,
        fontSize: 11,
        interval: Math.max(Math.floor(dates.length / 7), 0),
      },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: {
        lineStyle: {
          color: splitLineColor,
          type: 'dashed',
        },
      },
      axisLabel: {
        color: textColor,
        fontSize: 11,
      },
    },
    series: [
      {
        type: 'bar',
        data: counts,
        barWidth: 18,
        showBackground: true,
        backgroundStyle: {
          color: trackColor,
          borderRadius: [6, 6, 6, 6],
        },
        itemStyle: {
          borderRadius: [6, 6, 6, 6],
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: '#f97316' },
              { offset: 1, color: '#fb923c' },
            ],
          },
        },
      },
    ],
  };
}

/**
 * Builds ECharts options for the segmented semi-circular half-gauge chart matching the reference design.
 * Features 4 curved quadrant blocks separated by clean white gap dividers,
 * our warm amber/orange gradient fill, and center percentage typography.
 *
 * @param percentage Metric percentage (0-100)
 * @param centerLabel Text displayed below percentage inside the arch (e.g. "Quota consumed")
 * @param isLightMode Theme indicator, defaults to true
 */
export function buildSegmentedHalfGaugeOptions(
  percentage: number,
  centerLabel = 'Quota consumed',
  isLightMode = true
): EChartsOption {
  const safePercentage = Math.min(Math.max(Number(percentage) || 0, 0), 100);
  const textColor = isLightMode ? DASHBOARD_COLORS.textSlatePrimary : '#f8fafc';
  const subTextColor = isLightMode ? DASHBOARD_COLORS.textSlateSecondary : '#94a3b8';
  const trackColor = isLightMode ? '#f1f5f9' : '#1e293b';
  const gapColor = isLightMode ? '#ffffff' : '#0f172a';

  return {
    series: [
      {
        type: 'gauge',
        startAngle: 180,
        endAngle: 0,
        min: 0,
        max: 100,
        radius: '100%',
        center: ['50%', '82%'],
        pointer: { show: false },
        progress: {
          show: true,
          overlap: false,
          roundCap: false,
          clip: false,
          itemStyle: {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 1,
              y2: 0,
              colorStops: [
                { offset: 0, color: '#fde047' }, // soft warm golden yellow
                { offset: 0.5, color: '#f97316' }, // primary brand orange
                { offset: 1, color: '#ea580c' }, // rich deep orange
              ],
            },
          },
        },
        axisLine: {
          lineStyle: {
            width: 26,
            color: [[1, trackColor]],
          },
        },
        splitNumber: 4,
        splitLine: {
          show: true,
          length: 28,
          distance: -27,
          lineStyle: {
            color: gapColor,
            width: 4,
          },
        },
        axisTick: { show: false },
        axisLabel: { show: false },
        title: { show: false },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '-26%'],
          formatter: () => {
            const formattedVal = Number.isInteger(safePercentage)
              ? `${safePercentage}%`
              : `${safePercentage.toFixed(1)}%`;
            return `{value|${formattedVal}}\n{sub|${centerLabel}}`;
          },
          rich: {
            value: {
              fontSize: 28,
              fontWeight: 700,
              color: textColor,
              lineHeight: 34,
              fontFamily: 'Inter, system-ui, sans-serif',
            },
            sub: {
              fontSize: 11,
              fontWeight: 500,
              color: subTextColor,
              padding: [4, 0, 0, 0],
              fontFamily: 'Inter, system-ui, sans-serif',
            },
          },
        },
        data: [{ value: safePercentage }],
      },
    ],
  };
}

