import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { KeycloakService } from '../auth/keycloak.service';
import { AuthStore } from '../../state/auth/auth.store';
import { WorkspaceContextStore } from '../../state/workspace/workspace-context.store';
import { hasOrganizationCapability } from '../models/organization-capability.model';

/**
 * authGuard: Functional route guard that verifies OIDC authentication via KeycloakService
 * and ensures a valid user profile exists in AuthStore.
 *
 * APP_INITIALIZER guarantees KeycloakService.init() and AuthStore.loadUserProfile()
 * complete before Angular routing processes its first URL.
 *
 * If unauthenticated or if the profile failed to load, redirects to the sign-in page,
 * preserving the originally requested URL as a `returnUrl` query parameter.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const keycloakService = inject(KeycloakService);
  const authStore = inject(AuthStore);
  const router = inject(Router);

  // Require Keycloak authenticated session, valid access token, AND valid currentUser profile.
  if (keycloakService.isLoggedIn() && keycloakService.getToken() && authStore.currentUser()) {
    return true;
  }

  // Phantom auth guard or failed profile load:
  // If Keycloak session exists but token or user profile is missing:
  if (keycloakService.isLoggedIn() && (!keycloakService.getToken() || !authStore.currentUser())) {
    console.warn('[AuthGuard] Authenticated session without valid profile/token. Clearing state and redirecting to sign-in.');
    keycloakService.clearTokensAndState();
  }

  // Preserve the intended destination so sign-in can redirect there after login.
  return router.createUrlTree(['/sign-in'], {
    queryParams: { returnUrl: state.url },
  });
};


/**
 * orgSpaceGuard: Functional guard protecting routes exclusive to Organization workspace context.
 * Example: Organization members or admin settings.
 */
export const orgSpaceGuard: CanActivateFn = (_route, _state) => {
  const workspaceContextStore = inject(WorkspaceContextStore);
  const router = inject(Router);

  if (workspaceContextStore.isOrganization()) {
    return true;
  }

  return router.createUrlTree(['/w/personal/dashboard']);
};

/**
 * orgMembersGuard: Functional guard protecting the administrative Members section.
 * Requires active organization context and administrative capabilities: ORG_MEMBERS_MANAGE or ORG_MEMBERS_INVITE.
 * If unauthorized, redirects to the workspace explorer or personal dashboard.
 */
export const orgMembersGuard: CanActivateFn = (_route, _state) => {
  const workspaceContextStore = inject(WorkspaceContextStore);
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (!workspaceContextStore.isOrganization()) {
    return router.createUrlTree(['/w/personal/dashboard']);
  }

  const user = authStore.currentUser();
  const orgCode =
    workspaceContextStore.activeOrganization()?.orgCode ||
    workspaceContextStore.activeWorkspace()?.orgCode ||
    '';

  if (user?.role === 'ROLE_SUPER_ADMIN') {
    return true;
  }

  if (hasOrganizationCapability(user, orgCode, 'ORG_MEMBERS_MANAGE')) {
    return true;
  }

  const wsId = workspaceContextStore.activeWorkspace()?.id || 'default';
  return router.createUrlTree(['/w', wsId, 'explorer']);
};

