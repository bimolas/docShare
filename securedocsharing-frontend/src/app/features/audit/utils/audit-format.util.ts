import { UnifiedAuditEventResponseDto } from '../../../core/models/unified-audit.model';

/**
 * Enterprise Audit formatting utilities.
 * Ensures human-readable presentation, suppresses raw key-value tokens,
 * and maintains clean, consistent 2-line layout without row expansion.
 */

export function formatActionDisplay(event: UnifiedAuditEventResponseDto): string {
  const action = (event.action || '').toUpperCase();
  const display = (event.actionDisplay || '').trim();

  // 1. Resolve custom/legacy fallback actions
  if (display === 'Custom legacy action' || action === 'LEGACY_CUSTOM' || action === 'UNKNOWN') {
    const details = (event.changes?.details || '').toLowerCase();
    if (details.includes('organization policy') || details.includes('allowaccessrequests')) {
      return 'Updated organization policy';
    }
    if (details.includes('personal policy')) {
      return 'Updated personal policy';
    }
    if (details.includes('policy')) {
      return 'Updated security policy';
    }
    return 'Updated configuration';
  }

  // 2. Resolve specific policy actions
  if (action === 'ORGANIZATION_POLICY_UPDATED' || action === 'POLICY_UPDATE') {
    return 'Updated organization policy';
  }
  if (action === 'PERSONAL_POLICY_UPDATED') {
    return 'Updated personal policy';
  }
  if (action === 'POLICY_CREATE') {
    return 'Created security policy';
  }

  // 3. Clean fallback
  return display || event.action;
}

export function formatTargetName(event: UnifiedAuditEventResponseDto): string {
  const t = event.target;
  const rawName = (t?.name || '').trim();
  const details = (event.changes?.details || '').trim();

  // 1. Check if name is a raw key-value pair like allowAccessRequests=true / false
  if (rawName.includes('allowAccessRequests=')) {
    const isAllowed = rawName.includes('true');
    return isAllowed ? 'Access Requests Allowed' : 'Access Requests Restricted';
  }

  // 2. Check details if target name is missing or generic and details contains policy setting
  if ((!rawName || rawName === 'Access Policy') && details.includes('allowAccessRequests=')) {
    const isAllowed = details.includes('allowAccessRequests=true');
    return isAllowed ? 'Access Requests Allowed' : 'Access Requests Restricted';
  }

  // 3. Resolve Team names when target is generic (e.g. GROUP #76 or TEAM #76) but team name is in newValue
  if (
    (t?.type === 'GROUP' || t?.type === 'TEAM') &&
    (!rawName || rawName.startsWith('GROUP #') || rawName.startsWith('TEAM #'))
  ) {
    if (event.changes?.newValue) {
      return event.changes.newValue;
    }
  }

  // 4. Default target name resolution
  if (rawName) return rawName;
  if (t?.type && t.id) return `${t.type} #${t.id}`;
  if (t?.id) return `#${t.id}`;
  if (event.category === 'SECURITY') return 'System Security';
  return '—';
}

export function formatTargetSecondary(event: UnifiedAuditEventResponseDto): string | null {
  const t = event.target;
  const name = formatTargetName(event);

  // For teams: if name is custom team name (e.g. "jkkl"), show "Team #<id>" as secondary label
  if (
    (t?.type === 'GROUP' || t?.type === 'TEAM') &&
    t?.id &&
    name !== `GROUP #${t.id}` &&
    name !== `TEAM #${t.id}`
  ) {
    return `Team #${t.id}`;
  }

  // For workspaces: if name is workspace name, show "Workspace #<id>"
  if (t?.type === 'WORKSPACE' && t?.id && name !== `WORKSPACE #${t.id}`) {
    return `Workspace #${t.id}`;
  }

  // For policies: indicate scope
  if (event.category === 'SECURITY' || event.action?.includes('POLICY')) {
    if (event.location?.orgCode) {
      return `Org Policy (${event.location.orgCode})`;
    }
    return 'Security Policy';
  }

  return null;
}

export function hasRealTransitionDiff(event: UnifiedAuditEventResponseDto): boolean {
  const prev = event.changes?.previousValue;
  const next = event.changes?.newValue;
  // A real state transition requires BOTH previous and new values, and they must differ
  // e.g. VIEW -> EDIT, MEMBER -> MANAGER, ACTIVE -> SUSPENDED
  return !!(prev && next && prev.trim() !== next.trim());
}

export function formatDetailsText(details?: string): string {
  if (!details) return '';
  if (details.includes('allowAccessRequests=true')) {
    return details.replace('allowAccessRequests=true', 'Allow Access Requests: Enabled');
  }
  if (details.includes('allowAccessRequests=false')) {
    return details.replace('allowAccessRequests=false', 'Allow Access Requests: Disabled');
  }
  return details;
}
