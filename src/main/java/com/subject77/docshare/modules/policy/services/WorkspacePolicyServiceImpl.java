package com.subject77.docshare.modules.policy.services;

import com.subject77.docshare.modules.audit.services.AuditLogService;
import com.subject77.docshare.modules.folder.entities.FolderEntity;
import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.organization.repository.OrganizationRepository;
import com.subject77.docshare.modules.permission.enums.OrganizationCapability;
import com.subject77.docshare.modules.permission.services.CapabilityEvaluatorService;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import com.subject77.docshare.modules.policy.repository.WorkspacePolicyRepository;
import com.subject77.docshare.modules.user.entities.UserEntity;
import com.subject77.docshare.modules.user.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.Optional;

@Service
public class WorkspacePolicyServiceImpl implements WorkspacePolicyService {

    private final WorkspacePolicyRepository workspacePolicyRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final CapabilityEvaluatorService capabilityEvaluatorService;
    private final AuditLogService auditLogService;

    public WorkspacePolicyServiceImpl(
            WorkspacePolicyRepository workspacePolicyRepository,
            OrganizationRepository organizationRepository,
            UserRepository userRepository,
            @Lazy CapabilityEvaluatorService capabilityEvaluatorService,
            @Lazy AuditLogService auditLogService) {
        this.workspacePolicyRepository = workspacePolicyRepository;
        this.organizationRepository = organizationRepository;
        this.userRepository = userRepository;
        this.capabilityEvaluatorService = capabilityEvaluatorService;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity getOrCreateOrganizationPolicy(
            OrganizationEntity organization) {

        return workspacePolicyRepository.findByOrganization(organization)
                .orElseGet(() -> {
                    WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                            .workspaceType(WorkspaceType.ORGANIZATION)
                            .organization(organization)
                            .externalSharingMode(ExternalSharingMode.OFF)
                            .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                            .allowAccessRequests(false)
                            .requireRequestReason(false)
                            .allowExternalAccessRequests(false)
                            .build();

                    if (TransactionSynchronizationManager.isActualTransactionActive()
                            && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                        return policy;
                    }

                    return workspacePolicyRepository.save(policy);
                });
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity getOrCreateOrganizationPolicy(
            String orgCode) {

        OrganizationEntity organization = organizationRepository
                .findById(orgCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found with orgCode: " + orgCode));

        return getOrCreateOrganizationPolicy(organization);
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity getOrCreatePersonalPolicy(
            UserEntity user) {

        return workspacePolicyRepository.findByUser(user)
                .orElseGet(() -> {
                    WorkspacePolicyEntity policy = WorkspacePolicyEntity.builder()
                            .workspaceType(WorkspaceType.PERSONAL)
                            .user(user)
                            .externalSharingMode(ExternalSharingMode.OFF)
                            .discoverabilityMode(DiscoverabilityMode.PRIVATE)
                            .allowAccessRequests(false)
                            .requireRequestReason(false)
                            .allowExternalAccessRequests(false)
                            .build();

                    if (TransactionSynchronizationManager.isActualTransactionActive()
                            && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                        return policy;
                    }

                    return workspacePolicyRepository.save(policy);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspacePolicyEntity> getPolicyForOrganization(
            String orgCode) {

        return workspacePolicyRepository.findByOrganization_OrgCode(orgCode);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WorkspacePolicyEntity> getPolicyForUser(
            String userId) {

        return workspacePolicyRepository.findByUser_Id(userId);
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity updateOrganizationPolicy(
            String orgCode,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode) {

        return updateOrganizationPolicy(
                orgCode,
                externalSharingMode,
                discoverabilityMode,
                null,
                null,
                null);
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity updateOrganizationPolicy(
            String orgCode,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests) {

        OrganizationEntity organization = organizationRepository
                .findById(orgCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found with orgCode: " + orgCode));

        WorkspacePolicyEntity policy = getOrCreateOrganizationPolicy(organization);

        if (externalSharingMode != null) {
            policy.setExternalSharingMode(externalSharingMode);
        }

        if (discoverabilityMode != null) {
            policy.setDiscoverabilityMode(discoverabilityMode);
        }

        if (allowAccessRequests != null) {
            policy.setAllowAccessRequests(allowAccessRequests);
        }

        if (requireRequestReason != null) {
            policy.setRequireRequestReason(requireRequestReason);
        }

        if (allowExternalAccessRequests != null) {
            policy.setAllowExternalAccessRequests(
                    allowExternalAccessRequests);
        }

        return workspacePolicyRepository.save(policy);
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity updateOrganizationPolicy(
            String orgCode,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests,
            UserEntity currentUser) {

        if (currentUser != null) {
            capabilityEvaluatorService.requireCapability(currentUser, orgCode, OrganizationCapability.ORG_POLICIES_MANAGE);
        }

        OrganizationEntity organization = organizationRepository
                .findById(orgCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Organization not found with orgCode: " + orgCode));

        WorkspacePolicyEntity policy = getOrCreateOrganizationPolicy(organization);

        if (externalSharingMode != null) {
            policy.setExternalSharingMode(externalSharingMode);
        }
        if (discoverabilityMode != null) {
            policy.setDiscoverabilityMode(discoverabilityMode);
        }
        if (allowAccessRequests != null) {
            policy.setAllowAccessRequests(allowAccessRequests);
        }
        if (requireRequestReason != null) {
            policy.setRequireRequestReason(requireRequestReason);
        }
        if (allowExternalAccessRequests != null) {
            policy.setAllowExternalAccessRequests(allowExternalAccessRequests);
        }

        WorkspacePolicyEntity saved = workspacePolicyRepository.save(policy);

        // Audit log
        auditLogService.logPermissionChange(
                com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand.builder()
                        .action("ORGANIZATION_POLICY_UPDATED")
                        .targetType("ORGANIZATION")
                        .orgCode(orgCode)
                        .grantedBy(currentUser)
                        .actorUserId(currentUser != null ? currentUser.getId() : null)
                        .details("Updated organization policy: allowAccessRequests=" + saved.getAllowAccessRequests())
                        .timestamp(Instant.now())
                        .build()
        );

        return saved;
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity updatePersonalPolicy(
            String userId,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode) {

        return updatePersonalPolicy(
                userId,
                externalSharingMode,
                discoverabilityMode,
                null,
                null,
                null);
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity updatePersonalPolicy(
            String userId,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests) {

        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with id: " + userId));

        WorkspacePolicyEntity policy = getOrCreatePersonalPolicy(user);

        if (externalSharingMode != null) {
            policy.setExternalSharingMode(externalSharingMode);
        }
        if (discoverabilityMode != null) {
            policy.setDiscoverabilityMode(discoverabilityMode);
        }
        if (allowAccessRequests != null) {
            policy.setAllowAccessRequests(allowAccessRequests);
        }
        if (requireRequestReason != null) {
            policy.setRequireRequestReason(requireRequestReason);
        }
        if (allowExternalAccessRequests != null) {
            policy.setAllowExternalAccessRequests(allowExternalAccessRequests);
        }

        return workspacePolicyRepository.save(policy);
    }

    @Override
    @Transactional
    public WorkspacePolicyEntity updatePersonalPolicy(
            String userId,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests,
            UserEntity currentUser) {

        if (currentUser != null && !currentUser.getId().equals(userId)) {
            throw new AccessDeniedException("Cannot update another user's personal policy");
        }

        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found with id: " + userId));

        WorkspacePolicyEntity policy = getOrCreatePersonalPolicy(user);

        if (externalSharingMode != null) {
            policy.setExternalSharingMode(externalSharingMode);
        }
        if (discoverabilityMode != null) {
            policy.setDiscoverabilityMode(discoverabilityMode);
        }
        if (allowAccessRequests != null) {
            policy.setAllowAccessRequests(allowAccessRequests);
        }
        if (requireRequestReason != null) {
            policy.setRequireRequestReason(requireRequestReason);
        }
        if (allowExternalAccessRequests != null) {
            policy.setAllowExternalAccessRequests(allowExternalAccessRequests);
        }

        WorkspacePolicyEntity saved = workspacePolicyRepository.save(policy);

        auditLogService.logPermissionChange(
                com.subject77.docshare.modules.audit.dto.AuditPermissionChangeCommand.builder()
                        .action("PERSONAL_POLICY_UPDATED")
                        .targetType("PERSONAL")
                        .grantedBy(currentUser)
                        .actorUserId(currentUser != null ? currentUser.getId() : null)
                        .details("Updated personal policy: allowAccessRequests=" + saved.getAllowAccessRequests())
                        .timestamp(Instant.now())
                        .build()
        );

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePolicyEntity getEffectivePolicyForOrganization(
            OrganizationEntity organization) {

        if (organization == null) {
            return WorkspacePolicyEntity.builder()
                    .workspaceType(WorkspaceType.ORGANIZATION)
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                    .allowAccessRequests(false)
                    .requireRequestReason(false)
                    .allowExternalAccessRequests(false)
                    .build();
        }

        return workspacePolicyRepository
                .findByOrganization(organization)
                .orElseGet(() -> WorkspacePolicyEntity.builder()
                        .workspaceType(WorkspaceType.ORGANIZATION)
                        .organization(organization)
                        .externalSharingMode(ExternalSharingMode.OFF)
                        .discoverabilityMode(DiscoverabilityMode.DISCOVERABLE)
                        .allowAccessRequests(false)
                        .requireRequestReason(false)
                        .allowExternalAccessRequests(false)
                        .build());
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspacePolicyEntity getEffectivePolicyForUser(
            UserEntity user) {

        if (user == null) {
            return WorkspacePolicyEntity.builder()
                    .workspaceType(WorkspaceType.PERSONAL)
                    .externalSharingMode(ExternalSharingMode.OFF)
                    .discoverabilityMode(DiscoverabilityMode.PRIVATE)
                    .allowAccessRequests(false)
                    .requireRequestReason(false)
                    .allowExternalAccessRequests(false)
                    .build();
        }

        WorkspacePolicyEntity personalPolicy = workspacePolicyRepository.findByUser(user)
                .orElseGet(() -> WorkspacePolicyEntity.builder()
                        .workspaceType(WorkspaceType.PERSONAL)
                        .user(user)
                        .externalSharingMode(ExternalSharingMode.OFF)
                        .discoverabilityMode(DiscoverabilityMode.PRIVATE)
                        .allowAccessRequests(false)
                        .requireRequestReason(false)
                        .allowExternalAccessRequests(false)
                        .build());

        if (user.getOrganization() != null) {
            WorkspacePolicyEntity orgPolicy = getEffectivePolicyForOrganization(user.getOrganization());
            boolean allowReq = Boolean.TRUE.equals(personalPolicy.getAllowAccessRequests());
            boolean reqReason = Boolean.TRUE.equals(personalPolicy.getRequireRequestReason()) || Boolean.TRUE.equals(orgPolicy.getRequireRequestReason());
            boolean allowExtReq = Boolean.TRUE.equals(personalPolicy.getAllowExternalAccessRequests()) && Boolean.TRUE.equals(orgPolicy.getAllowExternalAccessRequests());

            return WorkspacePolicyEntity.builder()
                    .workspaceType(WorkspaceType.PERSONAL)
                    .user(user)
                    .organization(user.getOrganization())
                    .externalSharingMode(orgPolicy.getExternalSharingMode() == ExternalSharingMode.OFF ? ExternalSharingMode.OFF : personalPolicy.getExternalSharingMode())
                    .discoverabilityMode(orgPolicy.getDiscoverabilityMode() == DiscoverabilityMode.PRIVATE ? DiscoverabilityMode.PRIVATE : personalPolicy.getDiscoverabilityMode())
                    .allowAccessRequests(allowReq)
                    .requireRequestReason(reqReason)
                    .allowExternalAccessRequests(allowExtReq)
                    .build();
        }

        return personalPolicy;
    }


    @Override
    @Transactional(readOnly = true)
    public boolean isAccessRequestEnabledForFolder(FolderEntity folder) {
        if (folder == null) {
            return false;
        }

        // 1. Personal Space
        if (folder.getOwnerOrg() == null) {
            UserEntity owner = folder.getCreatedBy();
            if (owner == null) return false;
            WorkspacePolicyEntity personalPolicy = getEffectivePolicyForUser(owner);
            return personalPolicy != null && Boolean.TRUE.equals(personalPolicy.getAllowAccessRequests());
        }

        // 2. Organization Space
        String orgCode = folder.getOwnerOrg().getOrgCode();
        WorkspacePolicyEntity orgPolicy = getPolicyForOrganization(orgCode)
                .orElseGet(() -> getOrCreateOrganizationPolicy(folder.getOwnerOrg()));

        return Boolean.TRUE.equals(orgPolicy.getAllowAccessRequests());
    }
}