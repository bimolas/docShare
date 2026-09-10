package com.subject77.docshare.modules.policy.mapper;

import com.subject77.docshare.modules.policy.dto.WorkspacePolicyDto;
import com.subject77.docshare.modules.policy.entities.WorkspacePolicyEntity;

public class WorkspacePolicyMapper {

    public static WorkspacePolicyDto toWorkspacePolicyDto(WorkspacePolicyEntity entity) {
        if (entity == null) {
            return null;
        }
        return WorkspacePolicyDto.builder()
                .workspaceType(entity.getWorkspaceType())
                .externalSharingMode(entity.getExternalSharingMode())
                .discoverabilityMode(entity.getDiscoverabilityMode())
                .allowAccessRequests(entity.getAllowAccessRequests())
                .requireRequestReason(entity.getRequireRequestReason())
                .allowExternalAccessRequests(entity.getAllowExternalAccessRequests())
                .reviewerScopePolicy(entity.getReviewerScopePolicy())
                .build();
    }
}
