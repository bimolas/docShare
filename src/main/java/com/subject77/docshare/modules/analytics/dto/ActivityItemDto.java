package com.subject77.docshare.modules.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityItemDto {
    private Long id;
    private String action;
    private Instant timestamp;
    private Long documentId;
    private String documentHash;
    private String accessorEmail;
    private String ipAddress;
    private String details;
}
