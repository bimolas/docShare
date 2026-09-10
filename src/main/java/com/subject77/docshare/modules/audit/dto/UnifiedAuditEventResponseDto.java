package com.subject77.docshare.modules.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.subject77.docshare.modules.audit.enums.AuditCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UnifiedAuditEventResponseDto {

    // 1. Identity & Origin
    private String id;              // "TEL-123" or "GOV-456"
    private Long rawId;             // Underlying database primary key
    private String source;          // "TELEMETRY" or "GOVERNANCE"

    // 2. Classification
    private AuditCategory category; // Canonical category enum
    private String action;          // Canonical action name (e.g. DOCUMENT_UPLOAD, TEAM_CREATE)
    private String actionDisplay;   // Human-readable verb (e.g. "Uploaded document", "Created team")

    // 3. WHO (Actor)
    private AuditActorDto actor;

    // 4. TO WHICH RESOURCE (Target Resource)
    private AuditTargetDto target;

    // 5. WHERE (Contextual Location & Scope)
    private AuditLocationDto location;

    // 6. WHAT CHANGED (Historical Context / State Transition)
    private AuditChangesDto changes;

    // 7. WHEN
    private Instant timestamp;

    // 8. WITH WHAT RESULT (Status & Technical Telemetry)
    private AuditResultDto result;

    // 9. TECHNICAL METADATA (Privileged, redacted for non-admins)
    private AuditMetadataDto metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuditActorDto {
        private String userId;
        private String email;
        private String displayName;
        private String impersonatedBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuditTargetDto {
        private Long id;
        private String type;
        private String name;
        private String fileHash;
        private Long fileSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuditLocationDto {
        private String spaceType; // "PERSONAL" or "ORGANIZATION"
        private String orgCode;
        private Long workspaceId;
        private String workspaceName;
        private Long folderId;
        private String folderName;
        private String pathSnapshot;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuditChangesDto {
        private String previousValue;
        private String newValue;
        private String sourceLocation;
        private String destinationLocation;
        private String reason;
        private String details;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuditResultDto {
        private String status; // "SUCCESS", "DENIED", "FAILED", "PENDING"
        private String blockchainStatus;
        private String blockchainTxHash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AuditMetadataDto {
        private String ipAddress;
        private String userAgent;
        private String recordHash;
    }
}
