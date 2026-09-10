package com.subject77.docshare.modules.permission.services;

import com.subject77.docshare.modules.collaboration.entities.CollaborationEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationParticipantEntity;
import com.subject77.docshare.modules.collaboration.entities.CollaborationRole;
import com.subject77.docshare.modules.collaboration.entities.CollaborationStatus;
import com.subject77.docshare.modules.collaboration.repository.CollaborationParticipantRepository;
import com.subject77.docshare.modules.organization.entities.GroupMemberRole;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.repository.UserGroupRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.enums.ScopeType;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import com.subject77.docshare.modules.workspace.repository.WorkspaceManagerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CapabilityEvaluatorServiceImpl implements CapabilityEvaluatorService {

    private final UserOrganizationRepository userOrganizationRepository;
    private final UserGroupRepository userGroupRepository;
    private final WorkspaceManagerRepository workspaceManagerRepository;
    private final CollaborationParticipantRepository collaborationParticipantRepository;

    @Override
    public boolean hasCapability(UserEntity actor, String orgCode, OrganizationCapability capability) {
        if (actor == null || orgCode == null || capability == null) {
            return false;
        }

        // Global SUPER_ADMIN has all capabilities across all organizations
        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }

        Optional<UserOrganizationEntity> membershipOpt =
                userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(actor.getId(), orgCode);
        if (membershipOpt.isEmpty()) {
            return false;
        }

        UserOrganizationEntity membership = membershipOpt.get();
        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            return false;
        }

        Role role = membership.getRole();
        if (role == Role.ROLE_SUPER_ADMIN || role == Role.ROLE_ORG_ADMIN) {
            return true;
        }

        if (role == Role.ROLE_ORG_MANAGER) {
            return capability == OrganizationCapability.ORG_MEMBERS_MANAGE
                    || capability == OrganizationCapability.ORG_MEMBERS_INVITE
                    || capability == OrganizationCapability.ORG_WORKSPACES_MANAGE
                    || capability == OrganizationCapability.ORG_AUDIT_VIEW;
        }

        // Standard ROLE_USER possesses zero organization-wide administrative capabilities
        return false;
    }

    @Override
    public void requireCapability(UserEntity actor, String orgCode, OrganizationCapability capability) {
        if (!hasCapability(actor, orgCode, capability)) {
            throw new AccessDeniedException("User does not possess capability " + capability + " in organization: " + orgCode);
        }
    }

    @Override
    public boolean hasCapabilityInScope(UserEntity actor, OrganizationCapability capability, ScopeType scopeType, Long scopeId, String orgCode) {
        if (actor == null || capability == null || scopeType == null) {
            return false;
        }

        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }

        switch (scopeType) {
            case GLOBAL:
                return actor.getRole() == Role.ROLE_SUPER_ADMIN;

            case ORGANIZATION:
                return hasCapability(actor, orgCode, capability);

            case GROUP:
                if (orgCode == null || scopeId == null) {
                    return false;
                }
                // Org Admin has org-wide team management authority
                if (hasCapability(actor, orgCode, OrganizationCapability.ORG_TEAMS_MANAGE)) {
                    return true;
                }
                // Team-scoped capabilities: TEAM_ROSTER_MANAGE or TEAM_MANAGER_TRANSFER
                if (capability == OrganizationCapability.TEAM_ROSTER_MANAGE || capability == OrganizationCapability.TEAM_MANAGER_TRANSFER) {
                    Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(actor.getId(), orgCode);
                    if (uo.isEmpty() || uo.get().getStatus() != MembershipStatus.ACTIVE) {
                        return false;
                    }
                    return userGroupRepository != null
                            && userGroupRepository.existsByUser_IdAndGroup_IdAndRole(actor.getId(), scopeId, GroupMemberRole.MANAGER);
                }
                return false;

            case WORKSPACE:
                if (orgCode == null || scopeId == null) {
                    return false;
                }
                // Org Admin has org-wide authority over workspaces
                if (hasCapability(actor, orgCode, OrganizationCapability.ORG_SETTINGS_MANAGE)
                        || hasCapability(actor, orgCode, OrganizationCapability.ORG_WORKSPACE_MANAGERS_ASSIGN)) {
                    return true;
                }
                // Workspace-scoped capabilities: WORKSPACE_PERMISSIONS_MANAGE or WORKSPACE_REQUESTS_APPROVE
                if (capability == OrganizationCapability.WORKSPACE_PERMISSIONS_MANAGE || capability == OrganizationCapability.WORKSPACE_REQUESTS_APPROVE) {
                    Optional<UserOrganizationEntity> uo = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(actor.getId(), orgCode);
                    if (uo.isEmpty() || uo.get().getStatus() != MembershipStatus.ACTIVE) {
                        return false;
                    }
                    return workspaceManagerRepository != null
                            && workspaceManagerRepository.existsByWorkspace_IdAndUser_Id(scopeId, actor.getId());
                }
                return false;

            case COLLABORATION:
                if (scopeId == null || collaborationParticipantRepository == null) {
                    return false;
                }
                Optional<CollaborationParticipantEntity> participantOpt =
                        collaborationParticipantRepository.findByCollaboration_IdAndUser_Id(scopeId, actor.getId());
                if (participantOpt.isEmpty() || participantOpt.get().getCollaborationRole() != CollaborationRole.MANAGER) {
                    return false;
                }
                CollaborationEntity collab = participantOpt.get().getCollaboration();
                if (collab != null) {
                    if (collab.getStatus() != CollaborationStatus.ACTIVE) {
                        return false;
                    }
                    String ownerOrg = collab.getOwnerOrganization() != null ? collab.getOwnerOrganization().getOrgCode() : null;
                    String partnerOrg = collab.getPartnerOrganization() != null ? collab.getPartnerOrganization().getOrgCode() : null;
                    boolean activeInOwner = ownerOrg != null
                            && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(actor.getId(), ownerOrg, MembershipStatus.ACTIVE);
                    boolean activeInPartner = partnerOrg != null
                            && userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(actor.getId(), partnerOrg, MembershipStatus.ACTIVE);
                    if (!activeInOwner && !activeInPartner) {
                        return false;
                    }
                }
                return true;

            case PERSONAL:
                // Personal actions do not use organization capabilities
                return false;

            default:
                return false;
        }
    }

    @Override
    public void requireCapabilityInScope(UserEntity actor, OrganizationCapability capability, ScopeType scopeType, Long scopeId, String orgCode) {
        if (!hasCapabilityInScope(actor, capability, scopeType, scopeId, orgCode)) {
            throw new AccessDeniedException("User does not possess capability " + capability + " in scope " + scopeType + ":" + scopeId);
        }
    }

    @Override
    public void validateMemberManagementTarget(UserEntity actor, String orgCode, UserOrganizationEntity targetMembership) {
        requireCapability(actor, orgCode, OrganizationCapability.ORG_MEMBERS_MANAGE);

        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }

        UserOrganizationEntity callerMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(actor.getId(), orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of organization: " + orgCode));

        if (callerMembership.getRole() == Role.ROLE_SUPER_ADMIN || callerMembership.getRole() == Role.ROLE_ORG_ADMIN) {
            return;
        }

        if (callerMembership.getRole() == Role.ROLE_ORG_MANAGER) {
            if (targetMembership != null && (targetMembership.getRole() == Role.ROLE_ORG_ADMIN || targetMembership.getRole() == Role.ROLE_SUPER_ADMIN)) {
                throw new AccessDeniedException("Organization Managers cannot modify, suspend, or remove Organization Administrators.");
            }
            return;
        }

        throw new AccessDeniedException("Insufficient privileges to manage member in organization: " + orgCode);
    }

    @Override
    public void validateInvitationTargetRole(UserEntity actor, String orgCode, String requestedRole) {
        requireCapability(actor, orgCode, OrganizationCapability.ORG_MEMBERS_INVITE);

        if (actor.getRole() == Role.ROLE_SUPER_ADMIN) {
            if (requestedRole != null && requestedRole.equalsIgnoreCase("ROLE_SUPER_ADMIN")) {
                throw new IllegalArgumentException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
            }
            return;
        }

        UserOrganizationEntity callerMembership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(actor.getId(), orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not an active member of organization: " + orgCode));

        if (callerMembership.getRole() == Role.ROLE_ORG_MANAGER) {
            if (requestedRole != null && (requestedRole.equalsIgnoreCase("ROLE_ORG_ADMIN") || requestedRole.equalsIgnoreCase("ROLE_SUPER_ADMIN"))) {
                throw new AccessDeniedException("Organization Managers cannot invite users with administrative roles.");
            }
            return;
        }

        if (callerMembership.getRole() == Role.ROLE_SUPER_ADMIN || callerMembership.getRole() == Role.ROLE_ORG_ADMIN) {
            if (requestedRole != null && requestedRole.equalsIgnoreCase("ROLE_SUPER_ADMIN")) {
                throw new IllegalArgumentException("ROLE_SUPER_ADMIN cannot be assigned as an organization role.");
            }
            return;
        }

        throw new AccessDeniedException("Insufficient privileges to issue invitation in organization: " + orgCode);
    }
}
