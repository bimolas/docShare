package com.subject77.docshare.modules.policy.services;

import com.subject77.docshare.modules.organization.entities.OrganizationEntity;
import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;
import com.subject77.docshare.modules.user.entities.UserEntity;

import java.util.Optional;

public interface WorkspacePolicyService {

    WorkspacePolicyEntity getOrCreateOrganizationPolicy(
            OrganizationEntity organization);

    WorkspacePolicyEntity getOrCreateOrganizationPolicy(
            String orgCode);

    WorkspacePolicyEntity getOrCreatePersonalPolicy(
            UserEntity user);

    Optional<WorkspacePolicyEntity> getPolicyForOrganization(
            String orgCode);

    Optional<WorkspacePolicyEntity> getPolicyForUser(
            String userId);

    WorkspacePolicyEntity updateOrganizationPolicy(
            String orgCode,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode);

    WorkspacePolicyEntity updateOrganizationPolicy(
            String orgCode,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests);

    WorkspacePolicyEntity updateOrganizationPolicy(
            String orgCode,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests,
            UserEntity currentUser);

    WorkspacePolicyEntity updatePersonalPolicy(
            String userId,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode);

    WorkspacePolicyEntity updatePersonalPolicy(
            String userId,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests);

    WorkspacePolicyEntity updatePersonalPolicy(
            String userId,
            ExternalSharingMode externalSharingMode,
            DiscoverabilityMode discoverabilityMode,
            Boolean allowAccessRequests,
            Boolean requireRequestReason,
            Boolean allowExternalAccessRequests,
            UserEntity currentUser);

    WorkspacePolicyEntity getEffectivePolicyForOrganization(
            OrganizationEntity organization);

    WorkspacePolicyEntity getEffectivePolicyForUser(
            UserEntity user);

    boolean isAccessRequestEnabledForFolder(
            com.subject77.docshare.modules.folder.entities.FolderEntity folder);
}