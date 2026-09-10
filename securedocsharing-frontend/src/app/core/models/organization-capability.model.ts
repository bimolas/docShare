import { UserProfile } from './user.model';

export type OrganizationCapability =
  | 'ORG_SETTINGS_MANAGE'
  | 'ORG_POLICIES_MANAGE'
  | 'ORG_TEAMS_MANAGE'
  | 'ORG_WORKSPACE_MANAGERS_ASSIGN'
  | 'ORG_MEMBERS_MANAGE'
  | 'ORG_MEMBERS_INVITE'
  | 'ORG_WORKSPACES_MANAGE'
  | 'ORG_AUDIT_VIEW'
  | 'TEAM_ROSTER_MANAGE'
  | 'TEAM_MANAGER_TRANSFER'
  | 'WORKSPACE_PERMISSIONS_MANAGE'
  | 'WORKSPACE_REQUESTS_APPROVE'
  | 'COLLAB_PARTICIPANTS_MANAGE'
  | 'COLLAB_PERMISSIONS_MANAGE';

/**
 * Checks if a user has a specific organization capability according to backend authority rules.
 * Enforces active membership check and strict role boundaries.
 */
export function hasOrganizationCapability(
  user: UserProfile | null | undefined,
  orgCode: string | null | undefined,
  capability: OrganizationCapability
): boolean {
  if (!user || !orgCode) return false;

  // Global SUPER_ADMIN has all capabilities across all organizations
  if (user.role === 'ROLE_SUPER_ADMIN') {
    return true;
  }

  // Find membership in the specified organization
  const membership = user.activeOrganizations?.find(
    (m) => m.orgCode?.toLowerCase() === orgCode.toLowerCase()
  );

  if (!membership) {
    if (user.orgCode && user.orgCode.toLowerCase() === orgCode.toLowerCase()) {
      const userRole = (user.role || '').toUpperCase();
      if (userRole === 'ROLE_SUPER_ADMIN' || userRole === 'ROLE_ORG_ADMIN' || userRole === 'ORG_ADMIN') {
        return true;
      }
      if (userRole === 'ROLE_ORG_MANAGER' || userRole === 'ORG_MANAGER') {
        return (
          capability === 'ORG_MEMBERS_MANAGE' ||
          capability === 'ORG_MEMBERS_INVITE' ||
          capability === 'ORG_WORKSPACES_MANAGE' ||
          capability === 'ORG_TEAMS_MANAGE' ||
          capability === 'ORG_AUDIT_VIEW'
        );
      }
    }
    return false;
  }

  // Suspended members have no capabilities
  if (membership.status && membership.status.toUpperCase() !== 'ACTIVE') {
    return false;
  }

  const role = (membership.role || '').toUpperCase();

  if (role === 'ROLE_SUPER_ADMIN' || role === 'ROLE_ORG_ADMIN' || role === 'ORG_ADMIN' || role === 'ADMIN') {
    return true;
  }

  if (role === 'ROLE_ORG_MANAGER' || role === 'ORG_MANAGER' || role === 'MANAGER') {
    return (
      capability === 'ORG_MEMBERS_MANAGE' ||
      capability === 'ORG_MEMBERS_INVITE' ||
      capability === 'ORG_WORKSPACES_MANAGE' ||
      capability === 'ORG_TEAMS_MANAGE' ||
      capability === 'ORG_AUDIT_VIEW'
    );
  }

  // Ordinary users possess no organization-wide administrative capabilities
  return false;
}
