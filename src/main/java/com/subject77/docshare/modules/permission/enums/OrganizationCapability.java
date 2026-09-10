package com.subject77.docshare.modules.permission.enums;

/**
 * Fine-grained organizational capabilities governing administrative and management actions.
 * Distinct from content permissions (VIEW/DOWNLOAD/EDIT) and AuthorityTier
 * (which governs delegation ceiling and grant provenance).
 */
public enum OrganizationCapability {
    ORG_SETTINGS_MANAGE,
    ORG_POLICIES_MANAGE,
    ORG_TEAMS_MANAGE,
    ORG_WORKSPACE_MANAGERS_ASSIGN,
    ORG_MEMBERS_MANAGE,
    ORG_MEMBERS_INVITE,
    ORG_WORKSPACES_MANAGE,
    ORG_AUDIT_VIEW,
    TEAM_ROSTER_MANAGE,
    TEAM_MANAGER_TRANSFER,
    WORKSPACE_PERMISSIONS_MANAGE,
    WORKSPACE_REQUESTS_APPROVE,
    COLLAB_PARTICIPANTS_MANAGE,
    COLLAB_PERMISSIONS_MANAGE
}
