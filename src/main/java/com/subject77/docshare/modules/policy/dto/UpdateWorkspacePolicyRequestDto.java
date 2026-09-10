package com.subject77.docshare.modules.policy.dto;

import com.subject77.docshare.modules.policy.entities.DiscoverabilityMode;
import com.subject77.docshare.modules.policy.entities.ExternalSharingMode;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateWorkspacePolicyRequestDto {
    private ExternalSharingMode externalSharingMode;
    private DiscoverabilityMode discoverabilityMode;
    private Boolean allowAccessRequests;
    private Boolean requireRequestReason;
    private Boolean allowExternalAccessRequests;
}

