package com.subject77.docshare.modules.trust.services;

import com.subject77.docshare.exceptions.ResourceNotFoundException;
import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.entities.PermissionLevel;
import com.subject77.docshare.modules.trust.dto.CreateTrustRequestDto;
import com.subject77.docshare.modules.trust.dto.TrustRelationshipResponseDto;
import com.subject77.docshare.modules.trust.entities.OrgTrustRelationshipEntity;
import com.subject77.docshare.modules.trust.entities.TrustStatus;
import com.subject77.docshare.modules.trust.repository.OrgTrustRelationshipRepository;
import com.subject77.docshare.modules.organization.entities.MembershipStatus;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import com.subject77.docshare.modules.user.entities.Role;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.entities.UserOrganizationEntity;
import com.subject77.docshare.modules.user.repository.UserOrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrgTrustRelationshipServiceImpl implements OrgTrustRelationshipService {

    private final OrgTrustRelationshipRepository trustRepository;
    private final OrganizationRepository organizationRepository;
    private final UserOrganizationRepository userOrganizationRepository;
    private final AuditLogService auditLogService;
    private final CapabilityEvaluatorService capabilityEvaluatorService;

    public OrgTrustRelationshipServiceImpl(
            OrgTrustRelationshipRepository trustRepository,
            OrganizationRepository organizationRepository,
            UserOrganizationRepository userOrganizationRepository,
            @Lazy AuditLogService auditLogService,
            @Lazy CapabilityEvaluatorService capabilityEvaluatorService) {
        this.trustRepository = trustRepository;
        this.organizationRepository = organizationRepository;
        this.userOrganizationRepository = userOrganizationRepository;
        this.auditLogService = auditLogService;
        this.capabilityEvaluatorService = capabilityEvaluatorService;
    }

    @Override
    @Transactional
    public TrustRelationshipResponseDto createTrustRequest(CreateTrustRequestDto dto, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        String sourceOrgCode = dto.getSourceOrgCode();
        String targetOrgCode = dto.getTargetOrgCode();

        if (sourceOrgCode == null || sourceOrgCode.isBlank() || targetOrgCode == null || targetOrgCode.isBlank()) {
            throw new IllegalArgumentException("Source and target organization codes are required");
        }

        if (sourceOrgCode.equalsIgnoreCase(targetOrgCode)) {
            throw new IllegalArgumentException("An organization cannot establish a trust relationship with itself");
        }

        // Canonical deterministic locking order to prevent deadlocks and race conditions
        String firstOrg = sourceOrgCode.compareToIgnoreCase(targetOrgCode) < 0 ? sourceOrgCode : targetOrgCode;
        String secondOrg = sourceOrgCode.compareToIgnoreCase(targetOrgCode) < 0 ? targetOrgCode : sourceOrgCode;

        OrganizationEntity lockedFirst = organizationRepository.findByOrgCodeForUpdate(firstOrg)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + firstOrg));
        OrganizationEntity lockedSecond = organizationRepository.findByOrgCodeForUpdate(secondOrg)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + secondOrg));

        OrganizationEntity sourceOrg = sourceOrgCode.equalsIgnoreCase(firstOrg) ? lockedFirst : lockedSecond;
        OrganizationEntity targetOrg = targetOrgCode.equalsIgnoreCase(firstOrg) ? lockedFirst : lockedSecond;

        // Validate that caller is an active org admin in sourceOrg
        validateOrgAdminPermission(currentUser.getId(), sourceOrgCode, currentUser);

        // Check for existing active or pending relationships between source and target
        List<OrgTrustRelationshipEntity> existing = trustRepository.findByOrganizationPairAndStatusIn(
                sourceOrgCode, targetOrgCode, List.of(TrustStatus.PENDING, TrustStatus.ACTIVE));

        boolean hasActiveOrPending = existing.stream().anyMatch(t -> {
            if (t.getStatus() == TrustStatus.PENDING) {
                return t.getExpiresAt() == null || t.getExpiresAt().isAfter(Instant.now());
            }
            return t.isActive();
        });

        if (hasActiveOrPending) {
            throw new IllegalStateException("An active or pending trust relationship already exists between these organizations");
        }

        OrgTrustRelationshipEntity trust = OrgTrustRelationshipEntity.builder()
                .sourceOrganization(sourceOrg)
                .targetOrganization(targetOrg)
                .status(TrustStatus.PENDING)
                .requestedBy(currentUser)
                .expiresAt(dto.getExpiresAt())
                .build();

        OrgTrustRelationshipEntity saved = trustRepository.save(trust);
        log.info("Trust request created from {} to {} by user {}", sourceOrgCode, targetOrgCode, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "TRUST_REQUESTED",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    sourceOrg.getOrgCode(),
                    currentUser,
                    null,
                    targetOrg.getOrgCode(),
                    PermissionLevel.VIEW,
                    null,
                    dto.getExpiresAt()
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public TrustRelationshipResponseDto approveTrustRequest(Long trustId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        OrgTrustRelationshipEntity trust = trustRepository.findByIdForUpdate(trustId)
                .orElseThrow(() -> new ResourceNotFoundException("Trust relationship not found with ID: " + trustId));

        if (trust.getStatus() != TrustStatus.PENDING) {
            throw new IllegalStateException("Trust request is not in PENDING state");
        }

        if (trust.getExpiresAt() != null && trust.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Trust request has expired");
        }

        String targetOrgCode = trust.getTargetOrganization().getOrgCode();
        validateOrgAdminPermission(currentUser.getId(), targetOrgCode, currentUser);

        trust.setStatus(TrustStatus.ACTIVE);
        trust.setApprovedBy(currentUser);

        OrgTrustRelationshipEntity saved = trustRepository.save(trust);
        log.info("Trust request ID {} approved by user {} for target org {}", trustId, currentUser.getId(), targetOrgCode);

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "TRUST_APPROVED",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    trust.getTargetOrganization().getOrgCode(),
                    currentUser,
                    null,
                    trust.getSourceOrganization().getOrgCode(),
                    PermissionLevel.VIEW,
                    null,
                    trust.getExpiresAt()
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional
    public TrustRelationshipResponseDto revokeTrustRelationship(Long trustId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        OrgTrustRelationshipEntity trust = trustRepository.findByIdForUpdate(trustId)
                .orElseThrow(() -> new ResourceNotFoundException("Trust relationship not found with ID: " + trustId));

        if (trust.getStatus() == TrustStatus.REVOKED) {
            throw new IllegalStateException("Trust relationship is already revoked");
        }

        String sourceOrgCode = trust.getSourceOrganization().getOrgCode();
        String targetOrgCode = trust.getTargetOrganization().getOrgCode();

        boolean isSourceAdmin = isOrgAdmin(currentUser.getId(), sourceOrgCode, currentUser);
        boolean isTargetAdmin = isOrgAdmin(currentUser.getId(), targetOrgCode, currentUser);

        if (!isSourceAdmin && !isTargetAdmin) {
            throw new AccessDeniedException("Admin privileges in either source or target organization required to revoke trust");
        }

        trust.setStatus(TrustStatus.REVOKED);
        OrgTrustRelationshipEntity saved = trustRepository.save(trust);
        log.info("Trust relationship ID {} revoked by user {}", trustId, currentUser.getId());

        if (auditLogService != null) {
            auditLogService.logPermissionChange(
                    "TRUST_REVOKED",
                    "ORGANIZATION",
                    null,
                    null,
                    null,
                    null,
                    isSourceAdmin ? sourceOrgCode : targetOrgCode,
                    currentUser,
                    null,
                    isSourceAdmin ? targetOrgCode : sourceOrgCode,
                    PermissionLevel.VIEW,
                    null,
                    null
            );
        }

        return mapToResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustRelationshipResponseDto> getTrustRelationshipsForOrganization(String orgCode, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        organizationRepository.findById(orgCode)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + orgCode));

        validateUserInOrganization(currentUser.getId(), orgCode, currentUser);

        List<OrgTrustRelationshipEntity> relationships = trustRepository.findByOrganizationCode(orgCode);
        return relationships.stream()
                .map(this::mapToResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TrustRelationshipResponseDto getTrustRelationship(Long trustId, UserEntity currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required");
        }

        OrgTrustRelationshipEntity trust = trustRepository.findById(trustId)
                .orElseThrow(() -> new ResourceNotFoundException("Trust relationship not found with ID: " + trustId));

        String sourceOrgCode = trust.getSourceOrganization().getOrgCode();
        String targetOrgCode = trust.getTargetOrganization().getOrgCode();

        boolean isSourceMember = isUserInOrg(currentUser.getId(), sourceOrgCode, currentUser);
        boolean isTargetMember = isUserInOrg(currentUser.getId(), targetOrgCode, currentUser);

        if (!isSourceMember && !isTargetMember) {
            throw new AccessDeniedException("Access denied to trust relationship");
        }

        return mapToResponseDto(trust);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveTrust(String orgCode1, String orgCode2) {
        if (orgCode1 == null || orgCode2 == null || orgCode1.equalsIgnoreCase(orgCode2)) {
            return false;
        }

        List<OrgTrustRelationshipEntity> activeTrusts = trustRepository.findActiveTrustBetween(
                orgCode1, orgCode2, Instant.now());

        return !activeTrusts.isEmpty();
    }

    private void validateOrgAdminPermission(String userId, String orgCode, UserEntity currentUser) {
        if (currentUser != null && currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        UserOrganizationEntity membership = userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(userId, orgCode)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of organization: " + orgCode));

        if (membership.getStatus() != MembershipStatus.ACTIVE) {
            throw new AccessDeniedException("User organization membership is not active in organization: " + orgCode);
        }

        if (membership.getRole() != Role.ROLE_ORG_ADMIN && membership.getRole() != Role.ROLE_SUPER_ADMIN) {
            throw new AccessDeniedException("Admin privileges required in organization: " + orgCode);
        }
    }

    private boolean isOrgAdmin(String userId, String orgCode, UserEntity currentUser) {
        if (currentUser != null && currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        return userOrganizationRepository.findByUser_IdAndOrganization_OrgCode(userId, orgCode)
                .map(m -> m.getStatus() == MembershipStatus.ACTIVE && (m.getRole() == Role.ROLE_ORG_ADMIN || m.getRole() == Role.ROLE_SUPER_ADMIN))
                .orElse(false);
    }

    private void validateUserInOrganization(String userId, String orgCode, UserEntity currentUser) {
        if (currentUser != null && currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return;
        }
        boolean active = userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(userId, orgCode, MembershipStatus.ACTIVE);
        if (!active) {
            throw new AccessDeniedException("User is not an active member of organization: " + orgCode);
        }
    }

    private boolean isUserInOrg(String userId, String orgCode, UserEntity currentUser) {
        if (currentUser != null && currentUser.getRole() == Role.ROLE_SUPER_ADMIN) {
            return true;
        }
        return userOrganizationRepository.existsByUser_IdAndOrganization_OrgCodeAndStatus(userId, orgCode, MembershipStatus.ACTIVE);
    }

    private TrustRelationshipResponseDto mapToResponseDto(OrgTrustRelationshipEntity entity) {
        return TrustRelationshipResponseDto.builder()
                .id(entity.getId())
                .sourceOrgCode(entity.getSourceOrganization().getOrgCode())
                .sourceOrgName(entity.getSourceOrganization().getName())
                .targetOrgCode(entity.getTargetOrganization().getOrgCode())
                .targetOrgName(entity.getTargetOrganization().getName())
                .status(entity.getStatus())
                .requestedByUserId(entity.getRequestedBy() != null ? entity.getRequestedBy().getId() : null)
                .requestedByUserEmail(entity.getRequestedBy() != null ? entity.getRequestedBy().getEmail() : null)
                .approvedByUserId(entity.getApprovedBy() != null ? entity.getApprovedBy().getId() : null)
                .approvedByUserEmail(entity.getApprovedBy() != null ? entity.getApprovedBy().getEmail() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .expiresAt(entity.getExpiresAt())
                .active(entity.isActive())
                .build();
    }
}
