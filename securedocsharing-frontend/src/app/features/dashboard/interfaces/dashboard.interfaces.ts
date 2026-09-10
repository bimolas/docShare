import { PermissionLevel } from '../../../core/config/app.constants';

// ── Dashboard KPI ──────────────────────────────────────────────────────────

export interface DashboardKpis {
  totalFiles: number;
  storageUsedBytes: number;
  storageQuotaBytes: number;
  activeShareCount: number;
  auditEventsLast24h: number;
}

// ── Storage Breakdown ──────────────────────────────────────────────────────

export interface StorageBreakdownItem {
  label: string;
  bytes: number;
  percentage: number;
  color?: string;
}

export interface StorageUsage {
  personalBytes: number;
  organizationBytes: number;
  quotaBytes: number;
  breakdown: StorageBreakdownItem[];
}

// ── Activity Heatmap ───────────────────────────────────────────────────────

export interface HeatmapCell {
  date: string;           // ISO date string YYYY-MM-DD
  count: number;
  level: 0 | 1 | 2 | 3 | 4; // intensity level
}

export interface HeatmapGrid {
  cells: HeatmapCell[];
  maxCount: number;
  startDate: string;
  endDate: string;
}

// ── Audit Stream ───────────────────────────────────────────────────────────

export type AuditAction =
  | 'UPLOAD'
  | 'DOWNLOAD'
  | 'DELETE'
  | 'VIEW'
  | 'SHARE'
  | 'REVOKE'
  | 'MOVE'
  | 'RENAME'
  | 'EDIT';

export interface AuditEvent {
  id: string;
  action: AuditAction;
  actorEmail: string;
  actorName: string;
  resourceId: string;
  resourceName: string;
  resourceType: 'FILE' | 'FOLDER';
  spaceId: string;
  timestamp: string;
  metadata?: Record<string, string>;
}

// ── Recent Grants ──────────────────────────────────────────────────────────

export interface RecentGrant {
  id: string;
  resourceName: string;
  resourceType: 'FILE' | 'FOLDER';
  grantedToEmail: string;
  grantedToName: string;
  permissionLevel: PermissionLevel;
  grantedAt: string;
}

// ── Share Stats ────────────────────────────────────────────────────────────

export interface ShareStats {
  totalActiveShares: number;
  recentGrants: RecentGrant[];
}
