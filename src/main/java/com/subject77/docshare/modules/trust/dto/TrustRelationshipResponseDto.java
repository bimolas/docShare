package com.subject77.docshare.modules.trust.dto;

import com.subject77.docshare.modules.trust.entities.TrustStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustRelationshipResponseDto {

    private Long id;
    private String sourceOrgCode;
    private String sourceOrgName;
    private String targetOrgCode;
    private String targetOrgName;
    private TrustStatus status;
    private String requestedByUserId;
    private String requestedByUserEmail;
    private String approvedByUserId;
    private String approvedByUserEmail;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
    private boolean active;
}
