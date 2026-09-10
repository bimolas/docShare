/**
 * Centralized feature route classification and scope definitions.
 * Defines whether a feature route belongs to:
 * - WORKSPACE_CONTENT: Scoped to a specific project workspace (/org/:orgCode/w/:workspaceId/:feature or /w/personal/w/:workspaceId/:feature)
 * - PERSONAL_SPACE: Personal Space root features (/w/personal/:feature, e.g. dashboard, settings, audit, invitations, profile)
 * - ORGANIZATION_ADMIN: Organization-wide administration (/org/:orgCode/:feature, e.g. members, teams)
 * - SHARED_ACCOUNT: Available in both personal context (/w/personal/:feature) and org context (/org/:orgCode/:feature)
 */

export const WORKSPACE_CONTENT_FEATURES = [
  'dashboard',
  'explorer',
  'permissions',
  'access-requests',
] as const;


export type WorkspaceContentFeature = (typeof WORKSPACE_CONTENT_FEATURES)[number];

export const PERSONAL_SPACE_FEATURES = [
  'dashboard',
  'shared-with-me',
  'shared-by-me',
  'settings',
  'audit',
  'invitations',
  'profile',
  'access-requests',
  'notifications',
] as const;

export type PersonalSpaceFeature = (typeof PERSONAL_SPACE_FEATURES)[number];

export const ORGANIZATION_ADMIN_FEATURES = [
  'members',
  'teams',
] as const;


export type OrganizationAdminFeature = (typeof ORGANIZATION_ADMIN_FEATURES)[number];

export const SHARED_ACCOUNT_FEATURES = [
  'audit',
  'invitations',
  'settings',
  'profile',
  'access-requests',
] as const;

export type SharedAccountFeature = (typeof SHARED_ACCOUNT_FEATURES)[number];

export function isWorkspaceContentFeature(feature: string): boolean {
  return (WORKSPACE_CONTENT_FEATURES as readonly string[]).includes(feature);
}

export function isPersonalSpaceFeature(feature: string): boolean {
  return (PERSONAL_SPACE_FEATURES as readonly string[]).includes(feature);
}

export function isOrganizationAdminFeature(feature: string): boolean {
  return (ORGANIZATION_ADMIN_FEATURES as readonly string[]).includes(feature);
}

export function isSharedAccountFeature(feature: string): boolean {
  return (SHARED_ACCOUNT_FEATURES as readonly string[]).includes(feature);
}

export function isOrgOnlyFeature(feature: string): boolean {
  return isOrganizationAdminFeature(feature);
}

/**
 * Builds canonical route array based on context and feature classification.
 *
 * Normal navigation strictly uses:
 * - Shared Discovery Views: /w/personal/:feature (always personal discovery, e.g. /w/personal/shared-with-me)
 * - Personal Space Root / Shared Features: /w/personal/:feature (e.g. /w/personal/dashboard)
 * - Personal Workspace Content: /w/personal/w/:numericWorkspaceId/:feature
 * - Org-Level Admin & Shared Account: /org/:orgCode/:feature
 * - Org Workspace Content: /org/:orgCode/w/:numericWorkspaceId/:feature
 *
 * Distinct Dashboard Scopes:
 * - /w/personal/dashboard = Personal Space Home (numericWorkspaceId absent/null)
 * - /w/personal/w/:numericWorkspaceId/dashboard = Personal Workspace Dashboard (numericWorkspaceId present)
 */
export function buildCanonicalRoute(
  feature: string,
  orgCode: string | null | undefined,
  numericWorkspaceId: number | null | undefined
): string[] {
  // ── Shared discovery & Global Personal views: ALWAYS personal routes ──────
  if (feature === 'shared-with-me' || feature === 'shared-by-me' || feature === 'notifications') {
    return ['/w', 'personal', feature];
  }

  // ── Personal Context ───────────────────────────────────────────────────────
  if (!orgCode) {
    // 1. If it's a workspace-scoped content feature and a valid numeric workspace ID is provided:
    if (
      typeof numericWorkspaceId === 'number' &&
      !isNaN(numericWorkspaceId) &&
      isWorkspaceContentFeature(feature)
    ) {
      return ['/w', 'personal', 'w', numericWorkspaceId.toString(), feature];
    }

    // 2. Personal Space features (dashboard, settings, audit, invitations, profile)
    // or fallback if no workspace ID is available:
    const targetFeature = isOrgOnlyFeature(feature) ? 'dashboard' : feature;
    return ['/w', 'personal', targetFeature];
  }

  // ── Organization Context ───────────────────────────────────────────────────
  // 1. Workspace-scoped policies or content features when inside a workspace
  if (
    typeof numericWorkspaceId === 'number' &&
    !isNaN(numericWorkspaceId) &&
    isWorkspaceContentFeature(feature)
  ) {
    return ['/org', orgCode, 'w', numericWorkspaceId.toString(), feature];
  }

  // 2. Organization-level administration or shared account features
  if (isOrganizationAdminFeature(feature) || isSharedAccountFeature(feature)) {
    return ['/org', orgCode, feature];
  }

  // 3. Fallback workspace-scoped content features without numeric ID
  if (isWorkspaceContentFeature(feature)) {
    return ['/org', orgCode, 'w', 'default', feature];
  }

  // Fallback
  return ['/org', orgCode, feature];
}
