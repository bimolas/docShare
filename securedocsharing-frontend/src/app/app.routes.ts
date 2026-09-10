import { Routes } from '@angular/router';
import { authGuard, orgSpaceGuard, orgMembersGuard } from './core/guards/auth.guard';
import {
  orgGuard,
  workspaceGuard,
  personalGuard,
  personalWorkspaceGuard,
  legacyRouteRedirectGuard,
} from './core/guards/workspace.guard';

// ── Reusable Route Feature Definitions ───────────────────────────────────────

const workspaceContentRoutes: Routes = [
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/pages/dashboard.page').then(
        (m) => m.DashboardPageComponent
      ),
    title: 'Dashboard — SecureDocSharing',
  },
  {
    path: 'explorer',
    loadComponent: () =>
      import('./features/explorer/pages/explorer.page').then(
        (m) => m.ExplorerPageComponent
      ),
    title: 'File Explorer — SecureDocSharing',
  },
  {
    path: 'explorer/folder/:id',
    loadComponent: () =>
      import('./features/explorer/pages/folder-detail.page').then(
        (m) => m.FolderDetailPageComponent
      ),
    title: 'Folder — SecureDocSharing',
  },
  {
    path: 'explorer/file/:id',
    loadComponent: () =>
      import('./features/explorer/pages/file-detail.page').then(
        (m) => m.FileDetailPageComponent
      ),
    title: 'File — SecureDocSharing',
  },
  {
    path: 'permissions',
    loadComponent: () =>
      import('./features/permissions/pages/permissions.page').then(
        (m) => m.PermissionsPageComponent
      ),
    title: 'Permissions — SecureDocSharing',
  },
  {
    path: 'access-requests',
    loadComponent: () =>
      import('./features/access-requests/pages/access-requests.page').then(
        (m) => m.AccessRequestsPageComponent
      ),
    title: 'Access Requests — SecureDocSharing',
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./features/audit/pages/audit.page').then(
        (m) => m.AuditPageComponent
      ),
    title: 'Audit Trail — SecureDocSharing',
  },
];

const sharedAccountRoutes: Routes = [
  {
    path: 'access-requests',
    loadComponent: () =>
      import('./features/access-requests/pages/access-requests.page').then(
        (m) => m.AccessRequestsPageComponent
      ),
    title: 'Access Requests — SecureDocSharing',
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./features/audit/pages/audit.page').then(
        (m) => m.AuditPageComponent
      ),
    title: 'Audit Trail — SecureDocSharing',
  },
  {
    path: 'invitations',
    loadComponent: () =>
      import('./features/invitations/pages/my-invitations.page').then(
        (m) => m.MyInvitationsPageComponent
      ),
    title: 'My Invitations — SecureDocSharing',
  },
  {
    path: 'settings',
    loadComponent: () =>
      import('./features/settings/pages/settings.page').then(
        (m) => m.SettingsPageComponent
      ),
    title: 'Settings — SecureDocSharing',
  },
  {
    path: 'profile',
    loadComponent: () =>
      import('./features/profile/pages/profile.page').then(
        (m) => m.ProfilePageComponent
      ),
    title: 'Profile — SecureDocSharing',
  },
];

// ── Application Routes ───────────────────────────────────────────────────────

export const routes: Routes = [
  // ── Root Redirect ──────────────────────────────────────────────────────────
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'w/personal/dashboard',
  },
  {
    path: 'notifications',
    redirectTo: 'w/personal/notifications',
    pathMatch: 'full',
  },

  // ── OIDC Callback ──────────────────────────────────────────────────────────
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('./features/authentication/pages/auth-callback.page').then(
        (m) => m.AuthCallbackPageComponent
      ),
    title: 'Signing in… — SecureDocSharing',
  },

  // ── Public Routes ──────────────────────────────────────────────────────────
  {
    path: 'login',
    redirectTo: 'sign-in',
    pathMatch: 'full',
  },
  {
    path: 'sign-in',
    loadComponent: () =>
      import('./features/authentication/pages/sign-in.page').then(
        (m) => m.SignInPageComponent
      ),
    title: 'Sign In — SecureDocSharing',
  },
  {
    path: 'sign-up',
    loadComponent: () =>
      import('./features/authentication/pages/sign-up.page').then(
        (m) => m.SignUpPageComponent
      ),
    title: 'Create Account — SecureDocSharing',
  },

  // ── Authenticated Application Shell ────────────────────────────────────────
  {
    path: '',
    loadComponent: () =>
      import('./layouts/app-shell/app-shell.component').then(
        (m) => m.AppShellComponent
      ),
    canActivate: [authGuard],
    children: [
      // ── 1. Canonical Personal Space Routes ─────────────────────────────────
      {
        path: 'w/personal',
        canActivate: [personalGuard],
        children: [
          // Personal Space Home (zero workspaces resilient)
          {
            path: 'dashboard',
            loadComponent: () =>
              import('./features/dashboard/pages/dashboard.page').then(
                (m) => m.DashboardPageComponent
              ),
            title: 'Personal Space — SecureDocSharing',
          },

          // Workspace-scoped content routes (/w/personal/w/:workspaceId/...)
          {
            path: 'w/:workspaceId',
            canActivate: [personalWorkspaceGuard],
            children: [...workspaceContentRoutes],
          },

          // Canonical Personal Discovery Views
          {
            path: 'shared-with-me',
            loadComponent: () =>
              import('./features/sharing/pages/shared-with-me.page').then(
                (m) => m.SharedWithMePageComponent
              ),
            title: 'Shared With Me — SecureDocSharing',
          },
          {
            path: 'shared-by-me',
            loadComponent: () =>
              import('./features/sharing/pages/shared-by-me.page').then(
                (m) => m.SharedByMePageComponent
              ),
            title: 'Shared By Me — SecureDocSharing',
          },

          // Canonical Personal Notifications Center
          {
            path: 'notifications',
            loadComponent: () =>
              import(
                './features/notifications/pages/notification-center.page'
              ).then((m) => m.NotificationCenterPageComponent),
            title: 'Notification Center — SecureDocSharing',
          },

          // Redirect legacy workspace-scoped shared paths to canonical personal discovery views
          { path: 'w/:workspaceId/shared-with-me', redirectTo: 'shared-with-me', pathMatch: 'full' },
          { path: 'w/:workspaceId/shared-by-me', redirectTo: 'shared-by-me', pathMatch: 'full' },

          // Compatibility redirects for unmigrated callers navigating to /w/personal/:feature
          { path: 'explorer', redirectTo: 'w/default/explorer', pathMatch: 'full' },
          { path: 'permissions', redirectTo: 'w/default/permissions', pathMatch: 'full' },

          // Shared account routes
          ...sharedAccountRoutes,

          // Default personal root redirect -> Personal Space Home
          { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
        ],
      },

      // ── 2. Canonical Organization Context Routes ───────────────────────────
      {
        path: 'org/:orgCode',
        canActivate: [orgGuard],
        children: [
          // Canonical redirect: notifications are global personal inbox
          { path: 'notifications', redirectTo: '/w/personal/notifications', pathMatch: 'full' },

          // Redirect legacy/direct shared discovery requests to canonical personal discovery views
          { path: 'shared-with-me', redirectTo: '/w/personal/shared-with-me', pathMatch: 'full' },
          { path: 'shared-by-me', redirectTo: '/w/personal/shared-by-me', pathMatch: 'full' },
          { path: 'w/:workspaceId/shared-with-me', redirectTo: '/w/personal/shared-with-me', pathMatch: 'full' },
          { path: 'w/:workspaceId/shared-by-me', redirectTo: '/w/personal/shared-by-me', pathMatch: 'full' },

          // Compatibility redirects for unmigrated callers navigating to /org/:orgCode/:feature
          { path: 'dashboard', redirectTo: 'w/default/dashboard', pathMatch: 'full' },
          { path: 'explorer', redirectTo: 'w/default/explorer', pathMatch: 'full' },
          { path: 'permissions', redirectTo: 'w/default/permissions', pathMatch: 'full' },

          // Workspace-scoped content routes
          {
            path: 'w/:workspaceId',
            canActivate: [workspaceGuard],
            children: [...workspaceContentRoutes],
          },

          // Organization-level administration routes
          {
            path: 'members',
            canActivate: [orgMembersGuard],
            loadComponent: () =>
              import('./features/members/pages/members.page').then(
                (m) => m.MembersPageComponent
              ),
            title: 'Members — SecureDocSharing',
          },
          {
            path: 'teams',
            canActivate: [orgSpaceGuard],
            loadComponent: () =>
              import('./features/teams/pages/teams.page').then(
                (m) => m.TeamsPageComponent
              ),
            title: 'Teams — SecureDocSharing',
          },

          // Shared features in organization context
          ...sharedAccountRoutes,

          // Default organization root redirect
          { path: '', redirectTo: 'w/default/dashboard', pathMatch: 'full' },
        ],
      },

      // ── 3. Legacy Route Compatibility Redirects ────────────────────────────
      // Handles /w/:workspaceId/s/:subspaceId/** and forwards to canonical routes
      {
        path: 'w/:workspaceId/s/:subspaceId',
        canActivate: [legacyRouteRedirectGuard],
        children: [
          ...workspaceContentRoutes,
          ...sharedAccountRoutes,
          {
            path: 'members',
            children: [],
          },
          {
            path: 'teams',
            children: [],
          },
          {
            path: '**',
            children: [],
          },
        ],
      },
    ],
  },

  // ── Wildcard ───────────────────────────────────────────────────────────────
  { path: '**', redirectTo: 'w/personal/dashboard' },
];
