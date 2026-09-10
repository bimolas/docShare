package com.subject77.docshare.modules.policy.dto;

import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import com.subject77.docshare.modules.policy.entities.ReviewerScopePolicy;
import com.subject77.docshare.modules.policy.entities.WorkspaceType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspacePolicyDto {
    private WorkspaceType workspaceType;
    private ExternalSharingMode externalSharingMode;
    private DiscoverabilityMode discoverabilityMode;
    private Boolean allowAccessRequests;
    private Boolean requireRequestReason;
    private Boolean allowExternalAccessRequests;
    private ReviewerScopePolicy reviewerScopePolicy;
}

