package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.enums.ScopeType;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;

/**
 * Service responsible for evaluating and enforcing fine-grained capabilities.
 * Answers: "Can this actor administer this type of resource/action in this scope?"
 * Completely distinct from PermissionEvaluatorService, which answers:
 * "What content access/permission does this actor have on this document/folder?"
 */
public interface CapabilityEvaluatorService {

    /**
     * Evaluates whether an actor holds an organization-wide capability.
     */
    boolean hasCapability(UserEntity actor, String orgCode, OrganizationCapability capability);

    /**
     * Requires that an actor holds an organization-wide capability; throws AccessDeniedException if not.
     */
    void requireCapability(UserEntity actor, String orgCode, OrganizationCapability capability);

    /**
     * Evaluates whether an actor holds a capability within a specific container scope
     * (e.g. TEAM_ROSTER_MANAGE for groupId, or WORKSPACE_PERMISSIONS_MANAGE for workspaceId).
     */
    boolean hasCapabilityInScope(UserEntity actor, OrganizationCapability capability, ScopeType scopeType, Long scopeId, String orgCode);

    /**
     * Requires that an actor holds a capability within a specific container scope;
     * throws AccessDeniedException if not.
     */
    void requireCapabilityInScope(UserEntity actor, OrganizationCapability capability, ScopeType scopeType, Long scopeId, String orgCode);

    /**
     * Validates that an actor managing members has authority over the specific target membership.
     * Prevents ROLE_ORG_MANAGER from modifying, suspending, or removing an ROLE_ORG_ADMIN or ROLE_SUPER_ADMIN.
     */
    void validateMemberManagementTarget(UserEntity actor, String orgCode, UserOrganizationEntity targetMembership);

    /**
     * Validates that an actor issuing an invitation is authorized to invite the requested role.
     * Prevents ROLE_ORG_MANAGER from inviting users with role ROLE_ORG_ADMIN or ROLE_SUPER_ADMIN.
     */
    void validateInvitationTargetRole(UserEntity actor, String orgCode, String requestedRole);
}
