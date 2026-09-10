package com.subject77.docshare.modules.permission.entities;

/**
 * Represents the immutable institutional authority tier under which
 * a permission grant was originally chartered at creation time.
 */
public enum AuthorityTier {
    SUPER_ADMIN,
    ORG_ADMIN,
    WORKSPACE_MANAGER,
    COLLABORATION_MANAGER,
    PERSONAL_RESOURCE_OWNER,
    ORG_RESOURCE_CREATOR,
    TEAM_MANAGER,
    PROTECTED_LEGACY
}
