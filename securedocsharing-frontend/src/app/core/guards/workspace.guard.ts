import { inject } from '@angular/core';
import {
  CanActivateFn,
  Router,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { Observable, of, forkJoin } from 'rxjs';
import { map, catchError } from 'rxjs/operators';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import {
  isSharedAccountFeature,
  isOrganizationAdminFeature,
} from '../models/feature-route.model';

/**
 * Extracts route parameter by name, traversing parent routes if necessary.
 */
function getRouteParam(route: ActivatedRouteSnapshot, paramName: string): string | null {
  let current: ActivatedRouteSnapshot | null = route;
  while (current) {
    const val = current.paramMap.get(paramName);
    if (val) return val;
    current = current.parent;
  }
  return null;
}

/**
 * Extracts URL suffix path segments after a specified prefix segment.
 */
function extractSuffixSegments(state: RouterStateSnapshot, afterSegment: string): string[] {
  const urlPath = state.url.split('?')[0].split('#')[0];
  const segments = urlPath.split('/').filter(Boolean);
  const idx = segments.lastIndexOf(afterSegment);
  if (idx >= 0 && idx < segments.length - 1) {
    return segments.slice(idx + 1);
  }
  return [];
}

/**
 * Extracts query parameters and fragment from the router state snapshot.
 */
function extractNavigationExtras(router: Router, state: RouterStateSnapshot) {
  if (typeof router.parseUrl === 'function') {
    try {
      const parsed = router.parseUrl(state.url);
      const hasQueryParams = parsed.queryParams && Object.keys(parsed.queryParams).length > 0;
      const hasFragment = !!parsed.fragment;
      if (hasQueryParams || hasFragment) {
        return {
          queryParams: hasQueryParams ? parsed.queryParams : undefined,
          fragment: hasFragment ? parsed.fragment : undefined,
        };
      }
    } catch {
      // Fallback if URL parsing fails
    }
  }
  return undefined;
}

/**
 * Helper to create UrlTree with navigation extras only when extras exist.
 */
function createRedirectTree(router: Router, commands: any[], extras?: any): UrlTree {
  return extras ? router.createUrlTree(commands, extras) : router.createUrlTree(commands);
}

/**
 * orgGuard: Validates user membership in :orgCode and activates organization context in store.
 * Rejects unauthorized organizations by redirecting to /w/personal/dashboard.
 */
export const orgGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const workspaceContextStore = inject(WorkspaceContextStore);
  const router = inject(Router);
  const navExtras = extractNavigationExtras(router, state);

  const orgCode = getRouteParam(route, 'orgCode');
  if (!orgCode) {
    console.warn('[orgGuard] Missing orgCode route parameter');
    return of(createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras));
  }

  return workspaceContextStore.selectOrganization(orgCode).pipe(
    map(() => true),
    catchError((err) => {
      console.warn(`[orgGuard] Unauthorized or missing organization "${orgCode}":`, err);
      return of(createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras));
    })
  );
};

/**
 * workspaceGuard: Validates project workspace within an organization.
 * 1. If :workspaceId === 'default' -> resolves numeric ID and redirects.
 * 2. If :workspaceId is numeric -> validates workspace is ACTIVE and belongs to :orgCode.
 * 3. Rejects invalid or archived workspaces -> redirects to default workspace.
 * 4. Preserves organization zero-workspace behavior (redirects to /w/personal/dashboard when org has 0 active workspaces).
 */
export const workspaceGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const workspaceContextStore = inject(WorkspaceContextStore);
  const router = inject(Router);
  const navExtras = extractNavigationExtras(router, state);

  const orgCode = getRouteParam(route, 'orgCode');
  const workspaceIdParam = getRouteParam(route, 'workspaceId');

  if (!orgCode) {
    console.warn('[workspaceGuard] Missing orgCode in route hierarchy');
    return of(createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras));
  }

  // ── Default Workspace Resolution ───────────────────────────────────────────
  if (!workspaceIdParam || workspaceIdParam === 'default') {
    return workspaceContextStore.resolveDefaultWorkspace(orgCode).pipe(
      map((resolvedNumericId) => {
        const suffixSegments = extractSuffixSegments(state, 'default');
        const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
        return createRedirectTree(router, ['/org', orgCode, 'w', resolvedNumericId, ...targetSuffix], navExtras);
      }),
      catchError((err) => {
        console.error(`[workspaceGuard] Failed to resolve default workspace for org "${orgCode}":`, err);
        return of(createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras));
      })
    );
  }

  // ── Canonical Numeric Workspace Validation ─────────────────────────────────
  const numericWorkspaceId = Number(workspaceIdParam);
  if (isNaN(numericWorkspaceId) || numericWorkspaceId <= 0) {
    console.warn(`[workspaceGuard] Invalid non-numeric workspace ID "${workspaceIdParam}"`);
    const suffixSegments = extractSuffixSegments(state, workspaceIdParam);
    const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
    return of(createRedirectTree(router, ['/org', orgCode, 'w', 'default', ...targetSuffix], navExtras));
  }

  // Deduplicate: ensure organization workspaces are loaded in store
  const ensureWorkspaces$ =
    workspaceContextStore.activeOrgCode() === orgCode &&
    workspaceContextStore.availableWorkspaces().length > 0
      ? of(workspaceContextStore.availableWorkspaces())
      : workspaceContextStore.selectOrganization(orgCode);

  return ensureWorkspaces$.pipe(
    map(() => {
      const success = workspaceContextStore.selectWorkspace(numericWorkspaceId);
      if (success) {
        return true;
      }
      console.warn(
        `[workspaceGuard] Workspace #${numericWorkspaceId} is inaccessible, archived, or does not belong to "${orgCode}"`
      );
      const suffixSegments = extractSuffixSegments(state, workspaceIdParam);
      const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
      return createRedirectTree(router, ['/org', orgCode, 'w', 'default', ...targetSuffix], navExtras);
    }),
    catchError(() => {
      const suffixSegments = extractSuffixSegments(state, workspaceIdParam);
      const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
      return of(createRedirectTree(router, ['/org', orgCode, 'w', 'default', ...targetSuffix], navExtras));
    })
  );
};

/**
 * personalGuard: Activates Personal Space context and bootstraps personal workspaces and organizations.
 * Ensures both personal workspaces and available organizations are populated before allowing
 * /w/personal routes to render.
 *
 * If organizations fail, Personal Space remains usable with organizations as [].
 * If personal workspaces fail, the store's error state is preserved according to existing architecture.
 */
export const personalGuard: CanActivateFn = (): Observable<boolean> => {
  const workspaceContextStore = inject(WorkspaceContextStore);
  return forkJoin({
    workspaces: workspaceContextStore.loadPersonalWorkspaces(),
    organizations: workspaceContextStore.loadUserOrganizations(),
  }).pipe(
    map(() => true)
  );
};

/**
 * personalWorkspaceGuard: Validates project workspace within Personal Space.
 * 1. If :workspaceId === 'default' -> resolves default personal workspace.
 *    - If resolved: redirects to /w/personal/w/:numericId/:suffix (preserving deep-link suffix, queryParams, fragment).
 *    - If null (user has 0 personal workspaces): redirects to /w/personal/dashboard (Personal Space Home).
 * 2. If :workspaceId is numeric -> validates workspace is ACTIVE and belongs to Personal Space (organizationCode === null).
 * 3. Rejects invalid, archived, or foreign (org) workspaces -> redirects to /w/personal/w/default/:suffix.
 */
export const personalWorkspaceGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): Observable<boolean | UrlTree> => {
  const workspaceContextStore = inject(WorkspaceContextStore);
  const router = inject(Router);
  const navExtras = extractNavigationExtras(router, state);

  const workspaceIdParam = getRouteParam(route, 'workspaceId');

  // ── 1. Default Personal Workspace Resolution ───────────────────────────────
  if (!workspaceIdParam || workspaceIdParam === 'default') {
    return workspaceContextStore.resolveDefaultPersonalWorkspace().pipe(
      map((resolvedNumericId) => {
        const suffixSegments = extractSuffixSegments(state, 'default');
        const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];

        if (resolvedNumericId !== null) {
          return createRedirectTree(router, ['/w', 'personal', 'w', resolvedNumericId, ...targetSuffix], navExtras);
        }

        // Zero personal workspaces: redirect to Personal Space Home
        return createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras);
      }),
      catchError((err) => {
        console.error('[personalWorkspaceGuard] Failed to resolve default personal workspace:', err);
        return of(createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras));
      })
    );
  }

  // ── 2. Canonical Numeric Workspace Validation ───────────────────────────────
  const numericWorkspaceId = Number(workspaceIdParam);
  if (isNaN(numericWorkspaceId) || numericWorkspaceId <= 0) {
    console.warn(`[personalWorkspaceGuard] Invalid non-numeric personal workspace ID "${workspaceIdParam}"`);
    const suffixSegments = extractSuffixSegments(state, workspaceIdParam);
    const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
    return of(createRedirectTree(router, ['/w', 'personal', 'w', 'default', ...targetSuffix], navExtras));
  }

  // Ensure personal workspaces are loaded in store
  const ensureWorkspaces$ =
    workspaceContextStore.isPersonal() &&
    workspaceContextStore.availableWorkspaces().length > 0
      ? of(workspaceContextStore.availableWorkspaces())
      : workspaceContextStore.loadPersonalWorkspaces();

  return ensureWorkspaces$.pipe(
    map(() => {
      const success = workspaceContextStore.selectWorkspace(numericWorkspaceId);
      if (success) {
        return true;
      }
      console.warn(
        `[personalWorkspaceGuard] Workspace #${numericWorkspaceId} is inaccessible, archived, or not a personal workspace`
      );
      const suffixSegments = extractSuffixSegments(state, workspaceIdParam);
      const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
      return createRedirectTree(router, ['/w', 'personal', 'w', 'default', ...targetSuffix], navExtras);
    }),
    catchError(() => {
      const suffixSegments = extractSuffixSegments(state, workspaceIdParam);
      const targetSuffix = suffixSegments.length > 0 ? suffixSegments : ['dashboard'];
      return of(createRedirectTree(router, ['/w', 'personal', 'w', 'default', ...targetSuffix], navExtras));
    })
  );
};

/**
 * legacyRouteRedirectGuard: Translates legacy /w/:workspaceId/s/:subspaceId/... URLs
 * into canonical organization or personal routes.
 *
 * Rules:
 * 1. Legacy personal routes:
 *    - Shared account features -> /w/personal/:feature
 *    - Personal Space Home (/w/personal/s/default/dashboard) -> /w/personal/dashboard
 *    - Workspace content features (or subspaceId !== 'default'):
 *      NEVER interpret subspaceId as a workspaceId!
 *      Route through /w/personal/w/default/:feature so canonical default resolver chooses.
 * 2. Legacy org routes:
 *    - Org admin features (members, teams) or shared account -> /org/:orgCode/:feature
 *    - Org workspace content -> /org/:orgCode/w/default/:feature
 */
export const legacyRouteRedirectGuard: CanActivateFn = (
  route: ActivatedRouteSnapshot,
  state: RouterStateSnapshot
): UrlTree => {
  const router = inject(Router);
  const navExtras = extractNavigationExtras(router, state);

  const workspaceId = getRouteParam(route, 'workspaceId') || 'personal';
  const subspaceId = getRouteParam(route, 'subspaceId') || 'default';

  // Extract feature suffix after /w/:workspaceId/s/:subspaceId
  const suffix = extractSuffixSegments(state, subspaceId);
  const targetSuffix = suffix.length > 0 ? suffix : ['dashboard'];
  const primaryFeature = targetSuffix[0] || 'dashboard';

  // Shared discovery views: ALWAYS personal discovery routes
  if (primaryFeature === 'shared-with-me' || primaryFeature === 'shared-by-me') {
    return createRedirectTree(router, ['/w', 'personal', primaryFeature], navExtras);
  }

  if (workspaceId === 'personal') {
    // Shared account / Personal Space root features
    if (isSharedAccountFeature(primaryFeature)) {
      return createRedirectTree(router, ['/w', 'personal', ...targetSuffix], navExtras);
    }

    // Personal Space Home (subspaceId === 'default' and dashboard)
    if (primaryFeature === 'dashboard' && subspaceId === 'default') {
      return createRedirectTree(router, ['/w', 'personal', 'dashboard'], navExtras);
    }

    // Legacy subspace / root-folder reference:
    // NEVER treat subspaceId directly as a workspaceId.
    // Route through /w/personal/w/default/:feature and let canonical default resolver choose.
    return createRedirectTree(router, ['/w', 'personal', 'w', 'default', ...targetSuffix], navExtras);
  }

  // Check if primary feature is organization-level administration (members, teams) or shared account
  if (isOrganizationAdminFeature(primaryFeature) || isSharedAccountFeature(primaryFeature)) {
    return createRedirectTree(router, ['/org', workspaceId, ...targetSuffix], navExtras);
  }

  // Legacy org workspace route: /w/:orgCode/s/:subspaceId/... -> /org/:orgCode/w/default/...
  return createRedirectTree(router, ['/org', workspaceId, 'w', 'default', ...targetSuffix], navExtras);
};
