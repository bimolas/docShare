package com.subject77.docshare.modules.policy.services;

import com.subject77.docshare.modules.document.entities.DocumentEntity;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.GroupEntity;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.dto.PermissionRequestDto;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.trust.services.OrgTrustRelationshipService;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import com.subject77.docshare.modules.permission.entities.PermissionEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalSharingPolicyEvaluator {

    private final WorkspacePolicyService workspacePolicyService;
    private final OrgTrustRelationshipService orgTrustRelationshipService;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;

    public void evaluateAndValidateSharingPolicy(FolderEntity folder,
                                                 DocumentEntity document,
                                                 PermissionRequestDto requestDto,
                                                 GroupEntity targetGroup) {
        // 1. Determine source workspace owner (Organization or Personal User)
        OrganizationEntity sourceOrg = null;
        UserEntity sourceUser = null;

        if (folder != null) {
            if (folder.getOwnerOrg() != null) {
                sourceOrg = folder.getOwnerOrg();
            } else {
                sourceUser = folder.getCreatedBy();
            }
        } else if (document != null) {
            if (document.getOwnerOrg() != null) {
                sourceOrg = document.getOwnerOrg();
            } else {
                sourceUser = document.getUploadedBy();
            }
        } else {
            return;
        }

        String sourceOrgCode = sourceOrg != null ? sourceOrg.getOrgCode() : null;

        // 2. Determine target user / org / group
        String targetUserEmail = requestDto != null ? requestDto.getTargetUserEmail() : null;
        String targetOrgCode = requestDto != null ? requestDto.getTargetOrgCode() : null;

        UserEntity targetUser = null;
        if (targetUserEmail != null && !targetUserEmail.isBlank()) {
            targetUser = userRepository.findByEmail(targetUserEmail).orElse(null);
        }

        String effectiveTargetOrgCode = targetOrgCode;
        if ((effectiveTargetOrgCode == null || effectiveTargetOrgCode.isBlank()) && targetUser != null) {
            if (targetUser.getOrganization() != null) {
                effectiveTargetOrgCode = targetUser.getOrganization().getOrgCode();
            }
        }
        if ((effectiveTargetOrgCode == null || effectiveTargetOrgCode.isBlank()) && targetGroup != null) {
            if (targetGroup.getOrganization() != null) {
                effectiveTargetOrgCode = targetGroup.getOrganization().getOrgCode();
            }
        }

        // 3. Classification: INTERNAL vs EXTERNAL
        boolean isExternal = false;

        if (sourceOrg != null) {
            // Organization-owned resource
            if (targetOrgCode != null && !targetOrgCode.isBlank()) {
                if (!targetOrgCode.equalsIgnoreCase(sourceOrgCode)) {
                    isExternal = true;
                }
            } else if (targetGroup != null) {
                String groupOrgCode = targetGroup.getOrganization() != null ? targetGroup.getOrganization().getOrgCode() : null;
                if (groupOrgCode != null && !groupOrgCode.equalsIgnoreCase(sourceOrgCode)) {
                    throw new IllegalArgumentException("Team/Group target must belong to the same organization as the resource: " + sourceOrgCode);
                }
            } else if (targetUser != null) {
                String targetUserOrgCode = targetUser.getOrganization() != null ? targetUser.getOrganization().getOrgCode() : null;
                boolean isSameOrg = (targetUserOrgCode != null && targetUserOrgCode.equalsIgnoreCase(sourceOrgCode))
                        || userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(targetUser.getId(), sourceOrgCode, MembershipStatus.ACTIVE);
                if (!isSameOrg) {
                    isExternal = true;
                }
            } else {
                // Public / anonymous target on organization resource is external sharing
                isExternal = true;
            }
        } else if (sourceUser != null) {
            // Personal workspace resource: only direct personal shares allowed
            if (targetOrgCode != null && !targetOrgCode.isBlank()) {
                throw new IllegalArgumentException("Personal resources can only receive direct personal shares (USER_PERSONAL)");
            } else if (targetGroup != null) {
                throw new IllegalArgumentException("Personal resources can only receive direct personal shares (USER_PERSONAL)");
            } else if (targetUser != null) {
                if (!targetUser.getId().equals(sourceUser.getId())) {
                    isExternal = true;
                }
            } else {
                // Public / anonymous share link on personal resource is external
                isExternal = true;
            }
        }

        // Internal sharing requires no policy gate checks
        if (!isExternal) {
            return;
        }

        // 4. Source Policy Validation
        WorkspacePolicyEntity sourcePolicy;
        if (sourceOrg != null) {
            sourcePolicy = workspacePolicyService.getOrCreateOrganizationPolicy(sourceOrg);
        } else {
            sourcePolicy = workspacePolicyService.getOrCreatePersonalPolicy(sourceUser);
        }

        if (sourcePolicy == null || sourcePolicy.getExternalSharingMode() == ExternalSharingMode.OFF) {
            throw new AccessDeniedException("External sharing is disabled for the source workspace");
        }

        // 5. Target Organization Policy Validation (if target belongs to an org)
        if (effectiveTargetOrgCode != null && !effectiveTargetOrgCode.isBlank()) {
            OrganizationEntity targetOrg = organizationRepository.findByOrgCode(effectiveTargetOrgCode).orElse(null);
            WorkspacePolicyEntity targetPolicy;
            if (targetOrg != null) {
                targetPolicy = workspacePolicyService.getOrCreateOrganizationPolicy(targetOrg);
            } else {
                targetPolicy = workspacePolicyService.getPolicyForOrganization(effectiveTargetOrgCode).orElse(null);
            }

            if (targetPolicy != null && targetPolicy.getExternalSharingMode() == ExternalSharingMode.OFF) {
                throw new AccessDeniedException("External sharing is disabled for target organization: " + effectiveTargetOrgCode);
            }
        }

        // 6. Organization Trust Validation (cross-org: source org -> target org)
        if (sourceOrgCode != null && effectiveTargetOrgCode != null && !effectiveTargetOrgCode.isBlank()) {
            if (!sourceOrgCode.equalsIgnoreCase(effectiveTargetOrgCode)) {
                boolean hasTrust = orgTrustRelationshipService.hasActiveTrust(sourceOrgCode, effectiveTargetOrgCode);
                if (!hasTrust) {
                    throw new AccessDeniedException("No active trust relationship between organization " + sourceOrgCode + " and " + effectiveTargetOrgCode);
                }
            }
        }
    }

    public boolean isExternalPermissionRuntimeValid(PermissionEntity permission, UserEntity accessor) {
        if (permission == null) {
            return true;
        }

        FolderEntity folder = permission.getFolder();
        DocumentEntity document = permission.getDocument();

        OrganizationEntity sourceOrg = null;
        UserEntity sourceUser = null;

        if (folder != null) {
            if (folder.getOwnerOrg() != null) {
                sourceOrg = folder.getOwnerOrg();
            } else {
                sourceUser = folder.getCreatedBy();
            }
        } else if (document != null) {
            if (document.getOwnerOrg() != null) {
                sourceOrg = document.getOwnerOrg();
            } else if (document.getFolder() != null && document.getFolder().getOwnerOrg() != null) {
                sourceOrg = document.getFolder().getOwnerOrg();
            } else {
                sourceUser = document.getUploadedBy();
            }
        }

        if (sourceOrg == null && sourceUser == null && permission.getGrantedBy() != null) {
            if (permission.getGrantedBy().getOrganization() != null) {
                sourceOrg = permission.getGrantedBy().getOrganization();
            } else {
                sourceUser = permission.getGrantedBy();
            }
        }

        if (sourceOrg == null && sourceUser == null) {
            return true;
        }

        String sourceOrgCode = sourceOrg != null ? sourceOrg.getOrgCode() : null;

        String targetUserEmail = permission.getTargetUserEmail();
        String targetOrgCode = permission.getTargetOrgCode();
        GroupEntity targetGroup = permission.getTargetGroup();

        UserEntity targetUser = null;
        if (targetUserEmail != null && !targetUserEmail.isBlank()) {
            if (accessor != null && accessor.getEmail() != null && accessor.getEmail().equalsIgnoreCase(targetUserEmail)) {
                targetUser = accessor;
            } else {
                targetUser = userRepository.findByEmail(targetUserEmail).orElse(null);
            }
        } else if (accessor != null) {
            targetUser = accessor;
        }

        String effectiveTargetOrgCode = targetOrgCode;
        if ((effectiveTargetOrgCode == null || effectiveTargetOrgCode.isBlank()) && targetUser != null) {
            if (targetUser.getOrganization() != null) {
                effectiveTargetOrgCode = targetUser.getOrganization().getOrgCode();
            }
        }
        if ((effectiveTargetOrgCode == null || effectiveTargetOrgCode.isBlank()) && targetGroup != null) {
            if (targetGroup.getOrganization() != null) {
                effectiveTargetOrgCode = targetGroup.getOrganization().getOrgCode();
            }
        }

        boolean isExternal = false;

        if (sourceOrg != null) {
            if (targetOrgCode != null && !targetOrgCode.isBlank()) {
                if (!targetOrgCode.equalsIgnoreCase(sourceOrgCode)) {
                    isExternal = true;
                }
            } else if (targetGroup != null) {
                String groupOrgCode = targetGroup.getOrganization() != null ? targetGroup.getOrganization().getOrgCode() : null;
                if (groupOrgCode != null && !groupOrgCode.equalsIgnoreCase(sourceOrgCode)) {
                    isExternal = true;
                }
            } else if (targetUser != null) {
                String targetUserOrgCode = targetUser.getOrganization() != null ? targetUser.getOrganization().getOrgCode() : null;
                boolean isSameOrg = (targetUserOrgCode != null && targetUserOrgCode.equalsIgnoreCase(sourceOrgCode))
                        || userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(targetUser.getId(), sourceOrgCode, MembershipStatus.ACTIVE);
                if (!isSameOrg) {
                    isExternal = true;
                }
            }
        } else if (sourceUser != null) {
            if (targetOrgCode != null && !targetOrgCode.isBlank()) {
                isExternal = true;
            } else if (targetGroup != null) {
                isExternal = true;
            } else if (targetUser != null) {
                if (!targetUser.getId().equals(sourceUser.getId())) {
                    isExternal = true;
                }
            }
        }

        if (!isExternal) {
            return true;
        }

        WorkspacePolicyEntity sourcePolicy;
        if (sourceOrg != null) {
            sourcePolicy = workspacePolicyService.getOrCreateOrganizationPolicy(sourceOrg);
        } else {
            sourcePolicy = workspacePolicyService.getOrCreatePersonalPolicy(sourceUser);
        }

        if (sourcePolicy == null || sourcePolicy.getExternalSharingMode() == ExternalSharingMode.OFF) {
            log.debug("Runtime permission invalid: source workspace external sharing is OFF");
            return false;
        }

        if (effectiveTargetOrgCode != null && !effectiveTargetOrgCode.isBlank()) {
            OrganizationEntity targetOrg = organizationRepository.findByOrgCode(effectiveTargetOrgCode).orElse(null);
            WorkspacePolicyEntity targetPolicy;
            if (targetOrg != null) {
                targetPolicy = workspacePolicyService.getOrCreateOrganizationPolicy(targetOrg);
            } else {
                targetPolicy = workspacePolicyService.getPolicyForOrganization(effectiveTargetOrgCode).orElse(null);
            }

            if (targetPolicy != null && targetPolicy.getExternalSharingMode() == ExternalSharingMode.OFF) {
                log.debug("Runtime permission invalid: target org {} external sharing is OFF", effectiveTargetOrgCode);
                return false;
            }
        }

        if (sourceOrgCode != null && effectiveTargetOrgCode != null && !effectiveTargetOrgCode.isBlank()) {
            if (!sourceOrgCode.equalsIgnoreCase(effectiveTargetOrgCode)) {
                boolean hasTrust = orgTrustRelationshipService.hasActiveTrust(sourceOrgCode, effectiveTargetOrgCode);
                if (!hasTrust) {
                    log.debug("Runtime permission invalid: no active trust between {} and {}", sourceOrgCode, effectiveTargetOrgCode);
                    return false;
                }
            }
        }

        return true;
    }
}
