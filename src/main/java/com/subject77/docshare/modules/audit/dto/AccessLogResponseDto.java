package com.subject77.docshare.modules.audit.dto;

import com.subject77.docshare.modules.audit.entities.Action;
import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccessLogResponseDto {
    private Long id;
    private Action action;
    private Instant timestamp;
    private String documentFileHash;
    private Long folderId;
    private String accessorEmail;
    private String ipAddress;
    private String userAgent;
    private String txHash;
}
